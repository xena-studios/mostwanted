// MostWanted playtest: scripted bot players play the game on a throwaway local server,
// and every scenario checks the game did what it should, from what the bots see (chat,
// blocks, health, inventory) and what the database holds.
//
//   cd tools/playtest && npm install && npm run playtest            (all scenarios)
//   npm run playtest -- robbery cuffs                                 (only those)
//
// It builds core, starts PostgreSQL in Docker on port 5433 and a Paper server in offline
// mode on port 25580 under .cache/playtest/, builds a small arena with console commands,
// marks its zones straight in the database, and speeds every timer up (config below).
// The bots speak 26.1; ViaBackwards on the test server (only there) lets them join 26.3.

import { execFileSync, spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import mineflayer from "mineflayer";
import { Vec3 } from "vec3";
import * as WORLD from "./world.mjs";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const DIR = join(ROOT, ".cache/playtest");
const PLUGINS = join(ROOT, ".cache/playtest-plugins");
const PORT = 25580;
const PG = { container: "mw-playtest-pg", port: 5433, password: "playtest" };
// The Minecraft version the plugin is built against (gradle/libs.versions.toml).
const MINECRAFT = readFileSync(join(ROOT, "gradle/libs.versions.toml"), "utf8").match(/^paper = "([\d.]+)\.build/m)[1];

// Only for the test server, so old-protocol bots can join. Never on the real server.
const VIA = [
  {
    file: "ViaVersion-5.12.2.jar",
    url: "https://cdn.modrinth.com/data/P1OZGk5p/versions/Fkfcr89K/ViaVersion-5.12.2.jar",
    sha512: "c6af11f50e14bc1ba239f5a90be653cce85feee30c473a4b53336b4d5527d790369cf43843f7cb06d3ac3a34fc207e7203436737ca719070644101472453401b",
  },
  {
    file: "ViaBackwards-5.12.2.jar",
    url: "https://cdn.modrinth.com/data/NpvuJQoq/versions/WFO37e8T/ViaBackwards-5.12.2.jar",
    sha512: "78bc7e5fd33ecf13aee31c62e1de46a854e066a1936f294347c6798371357497fe2cc6f23f1d42003ad3050f94ac5382db95e60ffa90d765fada0322b59b0ef9",
  },
];

// Fast timers, so a scenario takes seconds instead of minutes.
const CONFIG = `database:
  host: localhost
  port: ${PG.port}
  name: mostwanted
  user: mostwanted
  password: ${PG.password}
  pool-size: 2
save-every-seconds: 1
rules:
  sentence:
    base-seconds: 4
    seconds-per-step: 2
    bounty-per-step: 2500
    max-seconds: 8
  combat-tag-seconds: 2
robberies:
  defaults:
    seconds: 4
    payout: 1000
    bounty: 500
    closed-minutes: 1
escapes:
  wall-hits: 5
  hit-gap-ms: 3000
  wall-regrow-seconds: 4
  gate-open-seconds: 3
riots:
  inmates-needed: 2
  press-window-seconds: 30
  length-minutes: 1
  cooldown-minutes: 0
  min-guards: 1
resource-pack:
  url: ""
`;

// The arena, on a flat world (players stand at y -60). x runs west to east:
// prison, police station, hideout, bank.
const ZONES = [
  ["prison", "prison", [-30, -64, -15], [0, -40, 15], [-15.5, -60, 0.5]],
  ["cell-1", "cell", [-28, -61, -13], [-26, -57, -11], [-26.5, -60, -11.5]],
  ["alarm", "alarm", [-20, -61, 8], [-18, -57, 10], null],
  ["east-wall", "wall", [-5, -60, 10], [-5, -59, 10], null],
  ["east-gate", "gate", [-2, -60, -10], [-2, -59, -10], null],
  ["police", "police", [20, -64, -5], [30, -40, 5], [25.5, -60, 0.5]],
  ["hideout", "hideout", [40, -64, -5], [50, -40, 5], null],
  ["bank", "robbery", [60, -64, -3], [66, -40, 3], null],
];
const BUILD = [
  // No spawn chunks any more: load the arena so the blocks can be placed.
  "forceload add -32 -16 70 16",
  "setblock -5 -60 10 minecraft:stone",
  "setblock -5 -59 10 minecraft:stone",
  "setblock -2 -60 -10 minecraft:iron_bars",
  "setblock -2 -59 -10 minecraft:iron_bars",
  "setworldspawn -15 -60 0",
  "time set day",
];

const BOTS = ["Inmate1", "Inmate2", "Crim1", "Guard1"];

// ---------------------------------------------------------------- helpers

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** Thrown by a scenario that can't run here: reported, not failed. */
class Skip extends Error {}
const SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡxʏᴢ";
/** Messages are in small caps; this turns plain text into what the bots see. */
const sc = (text) => [...text].map((c) => (c >= "a" && c <= "z" ? SMALL[c.charCodeAt(0) - 97] : c)).join("");

function sh(cmd, args, opts = {}) {
  return execFileSync(cmd, args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"], ...opts }).trim();
}

function sql(query) {
  return sh("docker", ["exec", PG.container, "psql", "-U", "mostwanted", "-tAc", query]);
}

/** A player's row, after their last change has been saved (save-every-seconds is 1). */
async function row(name) {
  await sleep(1600);
  const [role, cash, bank, bounty, jailed] = sql(
    `select role, cash, bank, bounty, jailed_until is not null from mostwanted.players where name = '${name}'`,
  ).split("|");
  return { role, cash: +cash, bank: +bank, bounty: +bounty, jailed: jailed === "t" };
}

/** The file's bytes, or null if it isn't there. */
function readIfThere(path) {
  try {
    return readFileSync(path);
  } catch {
    return null;
  }
}

const digest = (algorithm, data) => createHash(algorithm).update(data).digest("hex");

/**
 * Paper's latest build for our Minecraft version, cached with the boot test's jars.
 * Downloads are checked before they're written, and a cached copy is checked before it's used.
 */
async function paperJar() {
  const build = await (await fetch(`https://fill.papermc.io/v3/projects/paper/versions/${MINECRAFT}/builds/latest`)).json();
  const server = Object.values(build.downloads).find((d) => d.name.endsWith(".jar"));
  if (!/^[\w.-]+\.jar$/.test(server.name) || !/^[0-9a-f]{64}$/.test(server.checksums.sha256)) {
    throw new Error(`unexpected download from Paper: ${server.name}`);
  }
  const path = join(ROOT, ".cache/servers", server.name);
  const cached = readIfThere(path);
  if (cached && digest("sha256", cached) === server.checksums.sha256) return path;
  console.log(`Downloading ${server.name}…`);
  const data = Buffer.from(await (await fetch(server.url)).arrayBuffer());
  if (digest("sha256", data) !== server.checksums.sha256) throw new Error(`${server.name} doesn't match its checksum`);
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, data);
  return path;
}

/** A plugin pinned by URL and SHA-512: checked before it's written or used. */
async function download(plugin) {
  const path = join(PLUGINS, plugin.file);
  const cached = readIfThere(path);
  if (cached && digest("sha512", cached) === plugin.sha512) return path;
  const data = Buffer.from(await (await fetch(plugin.url)).arrayBuffer());
  if (digest("sha512", data) !== plugin.sha512) throw new Error(`${plugin.file} doesn't match its checksum`);
  mkdirSync(PLUGINS, { recursive: true });
  writeFileSync(path, data);
  return path;
}

class Server {
  constructor() {
    this.lines = [];
    this.waiters = [];
  }

  async start(paper) {
    const jars = await Promise.all(VIA.map(download));
    const core = join(ROOT, "core/build/libs/core-0.1.0.jar");
    this.proc = spawn("java", ["-Xmx2G", "-jar", paper, "--nogui", `--add-plugin=${core}`, ...jars.map((j) => `--add-plugin=${j}`)], {
      cwd: DIR,
    });
    const onData = (chunk) => {
      for (const line of chunk.toString().split("\n")) {
        if (!line.trim()) continue;
        this.lines.push(line);
        if (process.env.PLAYTEST_VERBOSE) console.log("  [server]", line);
        this.waiters = this.waiters.filter((w) => !(w.re.test(line) && (w.resolve(line), true)));
      }
    };
    this.proc.stdout.on("data", onData);
    this.proc.stderr.on("data", onData);
    return this.waitFor(/Done \(/, 180_000);
  }

  waitFor(re, ms = 10_000) {
    return new Promise((resolve, reject) => {
      const waiter = { re, resolve };
      this.waiters.push(waiter);
      setTimeout(() => {
        this.waiters = this.waiters.filter((w) => w !== waiter);
        reject(new Error(`server didn't log ${re} within ${ms} ms`));
      }, ms);
    });
  }

  cmd(text) {
    this.proc.stdin.write(text + "\n");
  }

  async stop() {
    const proc = this.proc;
    if (!proc || proc.exitCode !== null) return;
    this.cmd("stop");
    // This process only, and only if it hasn't stopped: a timer left running would kill
    // the next server started on this object.
    await new Promise((r) => {
      const force = setTimeout(() => (proc.kill("SIGKILL"), r()), 30_000);
      proc.on("exit", () => (clearTimeout(force), r()));
    });
  }

  /** Any warnings or errors from our plugin since the server started. */
  problems() {
    return this.lines.filter((l) => /(WARN|ERROR|SEVERE)\]: \[MostWanted\]|Exception/.test(l));
  }
}

function joinBot(username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: "127.0.0.1", port: PORT, username, version: process.env.PLAYTEST_VERSION ?? "1.21.11", auth: "offline",
      // No physics at all: through the protocol translation some blocks look like air to
      // Mineflayer, so its physics would drop the bot into the ground and get it kicked.
      // Bots are placed with /tp, which is all the scenarios need.
      physicsEnabled: false });
    bot.chatLog = [];
    // Through ViaBackwards, the server misreads a bot's position packets ("moved too
    // quickly", then a kick). The server places the bots with /tp anyway, so they never
    // report a position: only which way they face (for aiming) and teleport confirmations.
    const write = bot._client.write.bind(bot._client);
    bot._client.write = (name, params) => {
      if (name === "position" || name === "position_look") {
        if (name === "position_look") write("look", { yaw: params.yaw, pitch: params.pitch, onGround: params.onGround, flags: params.flags });
        return;
      }
      return write(name, params);
    };
    if (process.env.PLAYTEST_DEBUG) {
      const write = bot._client.write.bind(bot._client);
      let n = 0;
      bot._client.write = (name, params) => {
        if (/position|look|move/i.test(name) && n++ < 8) console.log(`  [${username} →] ${name} ${JSON.stringify(params)}`);
        return write(name, params);
      };
    }
    bot.on("messagestr", (message) => bot.chatLog.push(message));
    bot.once("spawn", () => resolve(bot));
    bot.once("kicked", (reason) => reject(new Error(`${username} was kicked: ${JSON.stringify(reason)}`)));
    bot.once("error", reject);
  });
}

/** Resolves when the bot is told something containing the text (in small caps). */
/** Each scenario only looks at what was said since it started (see the runner). */
function heard(bot, text, ms = 6000) {
  const want = sc(text);
  const start = bot.since ?? 0;
  return new Promise((resolve, reject) => {
    const check = () => bot.chatLog.slice(start).some((m) => m.includes(want));
    const timer = setInterval(() => {
      if (check()) {
        clearInterval(timer);
        resolve();
      }
    }, 100);
    setTimeout(() => {
      clearInterval(timer);
      check() ? resolve() : reject(new Error(`${bot.username} wasn't told "${text}". Last: ${bot.chatLog.slice(-3).join(" | ")}`));
    }, ms);
  });
}

async function until(what, test, ms = 5000) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    if (await test()) return;
    await sleep(100);
  }
  throw new Error(`timed out waiting for ${what}`);
}

function expect(ok, what) {
  if (!ok) throw new Error(what);
}

const near = (bot, x, z, d = 2) => Math.hypot(bot.entity.position.x - x, bot.entity.position.z - z) < d;

/** Whether the server has this block there: the bots' own copy of the world isn't reliable through the protocol translation. */
let checks = 0;
async function blockIs(server, pos, name) {
  const id = `PLAYTEST-${++checks}`;
  const seen = server.waitFor(new RegExp(id), 1500).then(() => true, () => false);
  server.cmd(`execute if block ${pos.x} ${pos.y} ${pos.z} minecraft:${name} run say ${id}`);
  return seen;
}

/** Right-clicks a block by its position, whatever the bot thinks is there. */
async function clickBlock(bot, pos) {
  await bot.lookAt(pos.offset(0.5, 0.5, 0.5), true);
  await bot.activateBlock({ position: pos, name: "unknown" });
}

async function equip(bot, name) {
  const item = bot.inventory.items().find((i) => i.name === name);
  if (!item) throw new Error(`${bot.username} has no ${name}: ${bot.inventory.items().map((i) => i.name).join(", ")}`);
  await bot.equip(item, "hand");
}

// ---------------------------------------------------------------- scenarios

/** Each is ({ server, bots: {Inmate1, ...}, tp, heal }) => Promise. */
const scenarios = {
  async escape({ server, bots, tp }) {
    const bot = bots.Inmate1;
    server.cmd("mw set Inmate1 role inmate");
    await tp("Inmate1", -1.5, -60, 0.5);
    // Out past the prison's edge (x 0).
    await tp("Inmate1", 3.5, -60, 0.5);
    await heard(bot, "you escaped");
    expect((await row("Inmate1")).role === "criminal", "the inmate didn't become a criminal");
    server.cmd("mw set Inmate1 role inmate");
    await tp("Inmate1", -15.5, -60, 0.5);
  },

  async banking({ server, bots, tp }) {
    server.cmd("mw set Crim1 role criminal");
    server.cmd("mw set Crim1 bank 0");
    server.cmd("mw set Crim1 cash 500");
    await tp("Crim1", 45.5, -60, 0.5);
    await heard(bots.Crim1, "banked");
    const r = await row("Crim1");
    expect(r.cash === 0 && r.bank === 500, `cash ${r.cash}, bank ${r.bank}: the cash wasn't banked`);
  },

  async robbery({ server, bots, tp }) {
    server.cmd("mw set Crim1 role criminal");
    server.cmd("mw set Crim1 cash 0");
    server.cmd("mw set Crim1 bounty 0");
    await tp("Crim1", 63.5, -60, 0.5);
    await heard(bots.Crim1, "you're robbing");
    await heard(bots.Guard1, "alarm!");
    await heard(bots.Crim1, "you got", 8000);
    const r = await row("Crim1");
    expect(r.cash >= 1000, `cash ${r.cash}: no payout`);
    expect(r.bounty >= 500, `bounty ${r.bounty}: no bounty for robbing`);
    await tp("Crim1", 10.5, -60, 30.5);
  },

  async combat({ server, bots, tp, heal }) {
    // Outside every zone: a criminal can hurt a guard.
    server.cmd("mw set Crim1 role criminal");
    await tp("Guard1", 10.5, -60, 20.5);
    await tp("Crim1", 11.5, -60, 20.5);
    await heal("Guard1");
    await sleep(500);
    const guard = bots.Crim1.players.Guard1?.entity;
    expect(guard, "Crim1 can't see Guard1");
    await bots.Crim1.lookAt(guard.position.offset(0, 1.6, 0), true);
    bots.Crim1.attack(guard);
    await until("Guard1 to take damage", () => bots.Guard1.health < 20, 3000);

    // Inside the prison, no riot: an inmate can't.
    await heal("Guard1");
    await tp("Guard1", -10.5, -60, -3.5);
    await tp("Inmate2", -9.5, -60, -3.5);
    await sleep(800);
    const health = bots.Guard1.health;
    const target = bots.Inmate2.players.Guard1?.entity;
    expect(target, "Inmate2 can't see Guard1");
    await bots.Inmate2.lookAt(target.position.offset(0, 1.6, 0), true);
    bots.Inmate2.attack(target);
    await sleep(1200);
    expect(bots.Guard1.health >= health, "an inmate hurt a guard with no riot on");
  },

  async cuffs({ server, bots, tp }) {
    server.cmd("mw set Crim1 role criminal");
    server.cmd("mw set Crim1 bounty 2500");
    await tp("Guard1", 10.5, -60, 20.5);
    await tp("Crim1", 11.5, -60, 20.5);
    await sleep(800);
    await equip(bots.Guard1, "tripwire_hook");
    const crim = bots.Guard1.players.Crim1?.entity;
    expect(crim, "Guard1 can't see Crim1");
    await bots.Guard1.lookAt(crim.position.offset(0, 1, 0), true);
    // Like a real client: "interact at" a point on them, then "interact".
    await bots.Guard1.activateEntityAt(crim, crim.position.offset(0, 1, 0));
    bots.Guard1.activateEntity(crim);
    if (process.env.PLAYTEST_DEBUG) {
      await sleep(1500);
      console.log("  guard held:", bots.Guard1.heldItem?.name, "slot", bots.Guard1.quickBarSlot, "told:", bots.Guard1.chatLog.slice(-4));
    }
    await heard(bots.Crim1, "arrested you");
    await heard(bots.Guard1, "you arrested");
    await until("Crim1 to be put in the cell", () => near(bots.Crim1, -26.5, -11.5), 5000);
    const r = await row("Crim1");
    expect(r.role === "inmate" && r.jailed && r.bounty === 0, `after the arrest: ${JSON.stringify(r)}`);
  },

  async jail({ server, bots }) {
    // Still serving the cuffs scenario's sentence: getting out puts them straight back,
    // so there's no arriving to wait for.
    server.cmd("tp Crim1 -20.5 -60 0.5");
    await heard(bots.Crim1, "back in your cell");
    await until("Crim1 to be back in the cell", () => near(bots.Crim1, -26.5, -11.5), 4000);
    try {
      await heard(bots.Crim1, "your sentence is over", 10_000);
    } catch (e) {
      const r = await row("Crim1");
      throw new Error(`${e.message} (database: ${JSON.stringify(r)}, bot at ${bots.Crim1.entity.position})`);
    }
    expect(!(await row("Crim1")).jailed, "still jailed after the sentence");
  },

  async guns({ server, bots, tp, heal }) {
    server.cmd("mw give Guard1 gun pistol");
    server.cmd("mw give Guard1 ammo pistol 24");
    server.cmd("mw set Crim1 role criminal");
    await tp("Guard1", 10.5, -60, 20.5);
    await tp("Crim1", 10.5, -60, 26.5);
    await heal("Crim1");
    await sleep(800);
    await equip(bots.Guard1, "iron_hoe");
    const crim = bots.Guard1.players.Crim1?.entity;
    expect(crim, "Guard1 can't see Crim1");
    await bots.Guard1.lookAt(crim.position.offset(0, 1.2, 0), true);
    bots.Guard1.activateItem();
    await until("Crim1 to be shot", () => bots.Crim1.health < 20, 3000);
  },

  async taser({ server, bots, tp, heal }) {
    server.cmd("mw set Crim1 role criminal");
    await tp("Guard1", 10.5, -60, 20.5);
    await tp("Crim1", 10.5, -60, 24.5);
    await heal("Crim1");
    await sleep(800);
    await equip(bots.Guard1, "breeze_rod");
    const crim = bots.Guard1.players.Crim1?.entity;
    await bots.Guard1.lookAt(crim.position.offset(0, 1.2, 0), true);
    bots.Guard1.activateItem();
    await heard(bots.Crim1, "tased you");
    expect(bots.Crim1.health >= 19.5, "the taser did damage");
  },

  async wall({ server, bots, tp }) {
    server.cmd("mw set Inmate2 role inmate");
    await tp("Inmate2", -6.5, -60, 10.5);
    await sleep(500);
    const block = new Vec3(-5, -60, 10);
    await bots.Inmate2.lookAt(block.offset(0.5, 0.5, 0.5), true);
    for (let i = 0; i < 6; i++) {
      bots.Inmate2.swingArm("right");
      await sleep(250);
    }
    await until("the wall block to break", () => blockIs(server, block, "air"), 3000);
    await heard(bots.Guard1, "has been broken");
    await until("the wall to grow back", () => blockIs(server, block, "stone"), 9000);
  },

  async gate({ server, bots, tp }) {
    // A bot's right-click on a block (use_item_on) doesn't reach the server through
    // ViaBackwards: the server only sees an arm swing. Until Mineflayer speaks 26.3
    // itself, gates are checked by hand. The code below is right for when it does.
    if (!process.env.PLAYTEST_GATES) throw new Skip("bots can't right-click blocks through the protocol translation (PLAYTEST_GATES=1 to try)");
    const bars = new Vec3(-2, -60, -10);
    await tp("Inmate2", -3.5, -60, -9.5);
    await sleep(500);
    await clickBlock(bots.Inmate2, bars);
    await heard(bots.Inmate2, "locked");
    expect(await blockIs(server, bars, "iron_bars"), "the gate opened without a keycard");

    await tp("Guard1", -3.5, -60, -8.5);
    await sleep(500);
    await clickBlock(bots.Guard1, bars);
    await until("the gate to open", () => blockIs(server, bars, "air"), 3000);
    await until("the gate to close", () => blockIs(server, bars, "iron_bars"), 8000);
  },

  async shop({ server, bots, tp }) {
    server.cmd("mw set Crim1 role criminal");
    server.cmd("mw set Crim1 cash 0");
    server.cmd("mw set Crim1 bank 5000");
    await tp("Crim1", 45.5, -60, 0.5);
    await sleep(800);
    const window = new Promise((r) => bots.Crim1.once("windowOpen", r));
    bots.Crim1.chat("/shop");
    await window;
    await bots.Crim1.clickWindow(0, 0, 0);
    await heard(bots.Crim1, "bought");
    bots.Crim1.closeWindow(bots.Crim1.currentWindow);
    await until("the pistol in Crim1's inventory", () => bots.Crim1.inventory.items().some((i) => i.name === "iron_hoe"), 3000);
    const r = await row("Crim1");
    expect(r.bank === 3500, `bank ${r.bank}: expected 3500 after a $1,500 pistol`);
  },

  async riot({ server, bots, tp, heal }) {
    server.cmd("mw set Inmate1 role inmate");
    server.cmd("mw set Inmate2 role inmate");
    await tp("Inmate1", -19.5, -60, 9.5);
    await tp("Inmate2", -18.5, -60, 9.5);
    await sleep(600);
    // Right-click with something in hand, as a player pressing the alarm would.
    for (const name of ["Inmate1", "Inmate2"]) {
      server.cmd(`give ${name} minecraft:stick`);
    }
    await sleep(600);
    for (const name of ["Inmate1", "Inmate2"]) {
      await equip(bots[name], "stick");
      bots[name].activateItem();
    }
    await heard(bots.Guard1, "the inmates have taken the yard");

    // During the riot, an inmate can hurt a guard inside the prison.
    await tp("Guard1", -10.5, -60, -3.5);
    await tp("Inmate1", -9.5, -60, -3.5);
    await heal("Guard1");
    await sleep(800);
    const guard = bots.Inmate1.players.Guard1?.entity;
    await bots.Inmate1.lookAt(guard.position.offset(0, 1.6, 0), true);
    bots.Inmate1.attack(guard);
    await until("Guard1 to take damage in the riot", () => bots.Guard1.health < 20, 3000);
  },

  async duty({ server, bots, tp }) {
    // Guard money stays apart: off duty shows inmate money, on duty the guard's again.
    await tp("Guard1", 25.5, -60, 0.5);
    server.cmd("mw set Guard1 cash 777");
    await sleep(3000); // past the combat tag
    bots.Guard1.chat("/duty");
    await heard(bots.Guard1, "you're off duty");
    const off = await row("Guard1");
    expect(off.role === "inmate" && off.cash !== 777, `off duty: ${JSON.stringify(off)}`);
    bots.Guard1.chat("/duty");
    await heard(bots.Guard1, "you're on duty");
    const on = await row("Guard1");
    expect(on.role === "guard" && on.cash === 777, `back on duty: ${JSON.stringify(on)}`);
  },

  async cosmetics({ server, bots }) {
    // Crim1 owns nothing: the menu says where to get it, and nothing is saved.
    const open = async (bot) => {
      const window = new Promise((r) => bot.once("windowOpen", r));
      bot.chat("/cosmetics");
      await window;
    };
    await open(bots.Crim1);
    await bots.Crim1.clickWindow(2, 0, 0);
    await heard(bots.Crim1, "you don't have");
    bots.Crim1.closeWindow(bots.Crim1.currentWindow);

    // Guard1 is an op, so owns every cosmetic: pick the Cash Trail and a chat colour.
    await open(bots.Guard1);
    await bots.Guard1.clickWindow(2, 0, 0); // trails row: Cash Trail
    await bots.Guard1.clickWindow(38, 0, 0); // chat colour: the first hue
    bots.Guard1.closeWindow(bots.Guard1.currentWindow);
    const choices = () => sql(
      "select slot || '=' || choice from mostwanted.cosmetic_choices c join mostwanted.players p using (uuid)"
        + " where p.name = 'Guard1' order by slot",
    ).split("\n").filter(Boolean);
    await until("Guard1's choices to be saved", () => choices().join() === "chat=hue:0,trail=trail-cash", 4000);

    // The Server Boost reaches everyone.
    server.cmd("mw boost Crim1");
    await heard(bots.Inmate1, "boosted the server");
  },

  async boards({ server, bots, tp }) {
    // A board placed where Guard1 stands lists Inmate1, who escaped earlier.
    await tp("Guard1", 10.5, -60, 40.5);
    bots.Guard1.chat("/mw board add escapes");
    await heard(bots.Guard1, "placed the");
    // The board's text, as the server has it: read off the main thread, so ask a few times.
    const shown = server.waitFor(/TOP ESCAPES.*Inmate1/, 8000);
    for (let i = 0; i < 6; i++) {
      await sleep(1000);
      server.cmd("data get entity @e[type=minecraft:text_display,limit=1,sort=nearest,x=10,y=-60,z=40] text");
    }
    await shown;
    bots.Guard1.chat("/mw board remove");
    await heard(bots.Guard1, "took away");
  },
};

// ---------------------------------------------------------------- run

/** The test map's settings: the shipped defaults, but robberies short enough to try by hand. */
const WORLD_CONFIG = `database:
  host: localhost
  port: ${PG.port}
  name: mostwanted
  user: mostwanted
  password: ${PG.password}
robberies:
  bank:
    name: Bank
    seconds: 30
    payout: 10000
    bounty: 5000
    closed-minutes: 1
resource-pack:
  url: ""
`;

async function main() {
  const world = process.argv.includes("--world");
  const only = process.argv.slice(2).filter((a) => !a.startsWith("--"));
  const server = new Server();
  const bots = {};
  let failed = 0;
  try {
    console.log("Building core…");
    sh("./gradlew", [":core:jar", "-q"], { cwd: ROOT });
    const paper = await paperJar();

    console.log("Starting PostgreSQL…");
    try { sh("docker", ["rm", "-f", PG.container]); } catch {}
    sh("docker", ["run", "--rm", "-d", "--name", PG.container, "-p", `127.0.0.1:${PG.port}:5432`,
      "-e", "POSTGRES_USER=mostwanted", "-e", `POSTGRES_PASSWORD=${PG.password}`, "postgres:18-alpine"]);
    await until("PostgreSQL", () => { try { sh("docker", ["exec", PG.container, "pg_isready", "-U", "mostwanted", "-q"]); return true; } catch { return false; } }, 30_000);

    console.log("Preparing the server…");
    rmSync(DIR, { recursive: true, force: true });
    mkdirSync(join(DIR, "plugins/MostWanted"), { recursive: true });
    writeFileSync(join(DIR, "eula.txt"), "eula=true\n");
    writeFileSync(join(DIR, "server.properties"), [
      `server-port=${PORT}`, "online-mode=false", "level-type=minecraft\\:flat", "gamemode=adventure", "force-gamemode=true",
      "difficulty=peaceful", "spawn-monsters=false", "pvp=true", "allow-flight=true", "spawn-protection=0",
      "generate-structures=false", "white-list=false", "enforce-whitelist=false", "view-distance=4", "simulation-distance=4", "motd=MostWanted playtest",
    ].join("\n") + "\n");
    writeFileSync(join(DIR, "bukkit.yml"), "settings:\n  connection-throttle: -1\n");
    writeFileSync(join(DIR, "plugins/MostWanted/config.yml"), world ? WORLD_CONFIG : CONFIG);
    if (world) writeFileSync(join(DIR, "plugins/MostWanted/boards.yml"), WORLD.BOARDS);

    // First start: the world and the tables. Build the arena, then stop to add the zones.
    await server.start(paper);
    for (const c of world ? WORLD.BUILD : BUILD) server.cmd(c);
    await sleep(world ? 4000 : 1500);
    await server.stop();
    for (const [name, kind, min, max, spawn] of world ? WORLD.ZONES : ZONES) {
      const sp = spawn ? `${spawn[0]}, ${spawn[1]}, ${spawn[2]}, 0, 0` : "null, null, null, null, null";
      sql(`insert into mostwanted.zones (name, kind, world, min_x, min_y, min_z, max_x, max_y, max_z,
           spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch) values ('${name}', '${kind}', 'world',
           ${min.join(", ")}, ${max.join(", ")}, ${sp})`);
    }

    console.log("Starting the server…");
    server.lines = [];
    await server.start(paper);
    if (world) {
      server.lines = [];
      console.log(`\nThe test map is up: join 127.0.0.1:${PORT} (offline mode, any name).`);
      console.log("Type server commands here (op <name> first). Ctrl+C stops it and throws it away.\n");
      process.stdin.on("data", (d) => server.cmd(d.toString().trim()));
      server.proc.stdout.on("data", (d) => process.stdout.write(d));
      await new Promise((r) => process.once("SIGINT", r));
    } else {
      for (const name of BOTS) {
        bots[name] = await joinBot(name);
        await sleep(400);
      }
      server.cmd("op Guard1");
      await sleep(500);
      bots.Guard1.chat("/duty");
      await heard(bots.Guard1, "you're on duty");

      const tp = async (name, x, y, z) => {
        server.cmd(`tp ${name} ${x} ${y} ${z}`);
        await until(`${name} to arrive`, () => near(bots[name], x, z, 1.5), 4000);
      };
      const heal = async (name) => {
        server.cmd(`effect give ${name} minecraft:instant_health 1 5`);
        await until(`${name} to heal`, () => bots[name].health >= 20, 3000);
      };

      for (const [name, run] of Object.entries(scenarios)) {
        if (only.length && !only.includes(name)) continue;
        const start = Date.now();
        for (const bot of Object.values(bots)) bot.since = bot.chatLog.length;
        try {
          await run({ server, bots, tp, heal });
          console.log(`  ✔ ${name} (${((Date.now() - start) / 1000).toFixed(1)} s)`);
        } catch (e) {
          if (e instanceof Skip) {
            console.log(`  - ${name}: skipped, ${e.message}`);
            continue;
          }
          failed++;
          console.log(`  ✖ ${name}: ${e.message}`);
          if (process.env.PLAYTEST_DEBUG) {
            // Where the server's main thread is, in case it's stuck.
            const before = server.lines.length;
            process.kill(server.proc.pid, "SIGQUIT");
            await sleep(2000);
            const dump = server.lines.slice(before);
            const at = dump.findIndex((l) => l.startsWith('"Server thread"'));
            console.log(at < 0 ? `no thread dump (${dump.length} lines after SIGQUIT)` : dump.slice(at, at + 25).join("\n"));
          }
        }
      }

      const problems = server.problems();
      if (problems.length) {
        failed++;
        console.log("  ✖ the plugin logged problems:\n    " + problems.join("\n    "));
      }
    }
  } catch (e) {
    failed++;
    console.error("Playtest couldn't run:", e.message);
    if (server.lines.length) console.error(server.lines.slice(-15).join("\n"));
  } finally {
    for (const bot of Object.values(bots)) bot.quit();
    await server.stop();
    try { sh("docker", ["stop", PG.container]); } catch {}
  }
  console.log(world ? "Stopped." : failed ? `${failed} failed` : "All passed");
  process.exit(failed ? 1 : 0);
}

main();
