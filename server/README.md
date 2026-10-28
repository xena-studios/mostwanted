# Server

Everything the MostWanted game server runs, so every change goes through a pull request. On the VPS, Raptor installs it: the MostWanted egg (`raptor/`) downloads the latest build of `main` (the `server` release) and runs `install.sh`. Why each piece is here: [docs/07-server-stack.md](../docs/07-server-stack.md).

```
manifest.json         Leaf and every plugin, pinned to a version with its checksum
install.sh            downloads and checks them, then copies in the configs (Raptor runs it on install)
start.sh              fills the templates in from the environment, then starts the server: Java heap
                      from Raptor's memory setting, Paper's G1 flags, Minehut's login flags
test.sh               boots a test copy and fails if a plugin or config didn't load (CI runs it)
files/                configs copied into the server; changes from the defaults say "MostWanted:"
  server.properties   whitelist on until launch, adventure mode, 100 slots, view distance 8, lz4 regions
  config/             Leaf, Gale and Paper: async pathfinding/saves/entity tracking, DAB, Minehut's PROXY protocol
  purpur.yml          no phantoms/patrols/traders, quieter logs
  spigot.yml          entity activation and tracking ranges, item merging, despawn rates
  bukkit.yml          low natural mob spawning, no End
  plugins/            LuckPerms and LibertyBans (templates), Grim, ViaVersion, Fancy*, FAWE,
                      xUtilities and zChat in the message style
setup/luckperms.txt   the ranks, run once in the console
raptor/               the Raptor eggs for the game server and the API (import them on Raptor's Eggs page)
.env.example          the values the templates need, for running outside Raptor; copy to <server-dir>/.env
```

## Set up the server

On the VPS, through Raptor only: [docs/12-launch.md](../docs/12-launch.md) (the eggs, the database, the settings on each server's Startup tab). Then, once, run the `lp` lines from [`setup/luckperms.txt`](setup/luckperms.txt) in the console.

Outside Raptor (your PC), with Java 25, `curl`, `jq` and `shasum`:

```sh
cp server/.env.example /tmp/mw/.env                      # then fill it in
server/install.sh /tmp/mw
echo eula=true > /tmp/mw/eula.txt                       # Minecraft's EULA: https://aka.ms/MinecraftEULA
cd /tmp/mw && MEMORY=4G ./start.sh
```

## Update the server

Merge to `main`; the server release workflow builds it. Then **Reinstall** the server in Raptor and start it. `install.sh` replaces the plugin jars it installed, removes ones that left the manifest, and overwrites the config files this repo tracks. The world, plugin data and other configs are left alone.

## Change a plugin or a config

- **A plugin version:** edit `manifest.json` with the new version's file, URL and sha512 from Modrinth, in a pull request. CI installs and boots the server with it.
- **A config:** edit the file under `files/` in a pull request. The server and plugin configs are full files, taken from a fresh boot of the pinned versions, so every setting is visible; lines we changed for speed or the game carry a `MostWanted:` comment where the reason isn't obvious. Configs with nothing to change (Grim's database and punishments, LibertyBans' scope, FAWE's worldedit-config) aren't tracked. LuckPerms' template only holds what we change: it fills in the rest itself. When a plugin update adds settings, boot a test copy and copy its new file back over ours, keeping our changes.
- **A secret or setting:** never here. Add a `${NAME}` to a `.tmpl` file, a variable to `raptor/egg-mostwanted.json` (then re-import the egg in Raptor) and a line to `.env.example`; the real value goes on the server's Startup tab.

## Test locally

```sh
cp server/.env.example /tmp/mw/.env && sed -i '' 's/^LUCKPERMS_STORAGE=.*/LUCKPERMS_STORAGE=h2/; s/^LIBERTYBANS_DATABASE=.*/LIBERTYBANS_DATABASE=HSQLDB/; s/^DB_PASSWORD=.*/DB_PASSWORD=test/' /tmp/mw/.env
server/install.sh /tmp/mw && server/test.sh /tmp/mw
```

`test.sh` accepts the EULA in the folder you give it, so only point it at a test copy. It passes when every plugin enabled and none turned itself off. Expected noise in the log: ViaVersion saying there's nothing newer to translate yet, and `[FancyAnalytics] Failed to create event` from the Fancy plugins' statistics upload.
