// Everything the store sells (docs/11-store.md). Cosmetics only: nothing here helps you
// win a fight, an escape or a robbery (Mojang's rules, and the Terms).
//
// Each package's `name` must match the package's name in Tebex exactly: the store page
// matches them up by name to get live prices and to add them to the basket. Prices
// here are what the page shows until Tebex is connected.

export type Category = "ranks" | "cosmetics" | "support";

export type Package = {
  /** Our id, for the page. */
  id: string;
  /** Its name in Tebex, exactly. */
  name: string;
  category: Category;
  /** For cosmetics: which kind, for the filter. */
  kind?: "Trails" | "Escape effects" | "Arrest effects" | "Gun skins" | "Chat";
  price: number;
  /** Pixel icon in public/store/, drawn in vanilla's style. */
  icon: string;
  blurb: string;
  /** For ranks: the tag's colour (from the in-game palette). */
  colour?: string;
  /** Shown on the card as a ribbon. */
  ribbon?: string;
  /** What it gives, for the card. */
  perks?: string[];
  /** For ranks: the cosmetics it includes (their ids), for value and the cart's suggestions. */
  includes?: string[];
  /** What Tebex runs on the server when it's bought ({username} is filled in). */
  commands: string[];
};

export const ranks: Package[] = [
  {
    id: "vip",
    name: "VIP",
    category: "ranks",
    price: 4.99,
    icon: "/store/rank-vip.png",
    colour: "#99ff33",
    blurb: "A tag by your name and your first effect.",
    perks: ["VIP tag in chat and the tab list", "Confetti escape effect", "VIP role on Discord", "Supporter badge on your player page"],
    includes: ["escape-confetti"],
    commands: ["lp user {username} parent add vip"],
  },
  {
    id: "vip-plus",
    name: "VIP+",
    category: "ranks",
    price: 9.99,
    icon: "/store/rank-vip-plus.png",
    colour: "#33ffcc",
    blurb: "Everything in VIP, plus a trail and chat colours.",
    perks: ["VIP+ tag in chat and the tab list", "Confetti escape effect", "Smoke trail", "Chat colours", "VIP+ role on Discord", "Supporter badge on your player page"],
    includes: ["escape-confetti", "trail-smoke", "chat-colours"],
    commands: ["lp user {username} parent add vip_plus"],
  },
  {
    id: "mvp",
    name: "MVP",
    category: "ranks",
    price: 19.99,
    icon: "/store/rank-mvp.png",
    colour: "#6633ff",
    blurb: "Every trail and effect, and the server knows when you arrive.",
    perks: ["MVP tag and a coloured name", "Every trail", "Every escape and arrest effect", "Chat colours", "Your joins are announced", "MVP role on Discord", "Supporter badge on your player page"],
    includes: [
      "trail-cash", "trail-ember", "trail-smoke", "trail-siren",
      "escape-fireworks", "escape-confetti", "escape-thunder",
      "arrest-spotlight", "arrest-siren", "chat-colours",
    ],
    commands: ["lp user {username} parent add mvp"],
  },
  {
    id: "legend",
    name: "Legend",
    category: "ranks",
    price: 39.99,
    icon: "/store/rank-legend.png",
    colour: "#ff3366",
    ribbon: "Everything",
    blurb: "Every cosmetic there is, now and later.",
    perks: ["Legend tag and a coloured name", "Every cosmetic, including gun skins", "Every cosmetic we add later", "Your own join message", "Legend role on Discord", "Supporter badge on your player page"],
    commands: ["lp user {username} parent add legend"],
  },
];

// Unlocked by a permission, so they work with LuckPerms and stack with ranks.
const cosmetic = (id: string, name: string, kind: Package["kind"], price: number, blurb: string): Package => ({
  id,
  name,
  category: "cosmetics",
  kind,
  price,
  icon: `/store/${id}.png`,
  blurb,
  commands: [`lp user {username} permission set mostwanted.cosmetic.${id} true`],
});

export const cosmetics: Package[] = [
  cosmetic("trail-cash", "Cash Trail", "Trails", 3.99, "Leave a trail of bills behind you. Best on the run from the bank."),
  cosmetic("trail-ember", "Ember Trail", "Trails", 2.99, "Sparks and embers at your heels."),
  cosmetic("trail-smoke", "Smoke Trail", "Trails", 2.99, "A puff of smoke with every step."),
  cosmetic("trail-siren", "Siren Trail", "Trails", 3.99, "Red and blue lights, for guards on patrol."),
  cosmetic("escape-fireworks", "Fireworks Escape", "Escape effects", 2.99, "Fireworks go up the moment you clear the wall."),
  cosmetic("escape-confetti", "Confetti Escape", "Escape effects", 2.99, "A burst of confetti when you break out."),
  cosmetic("escape-thunder", "Thunder Escape", "Escape effects", 3.99, "A crack of lightning (harmless) as you escape."),
  cosmetic("arrest-spotlight", "Spotlight Arrest", "Arrest effects", 2.99, "A spotlight falls on everyone you cuff."),
  cosmetic("arrest-siren", "Siren Arrest", "Arrest effects", 2.99, "Sirens wail when you make an arrest."),
  cosmetic("skin-gilded-pistol", "Gilded Pistol", "Gun skins", 4.99, "A gold pistol. Same stats as everyone's."),
  cosmetic("skin-copper-shotgun", "Copper Shotgun", "Gun skins", 4.99, "A copper shotgun. Same stats as everyone's."),
  cosmetic("skin-midnight-rifle", "Midnight Rifle", "Gun skins", 4.99, "A netherite-black rifle. Same stats as everyone's."),
  cosmetic("skin-diamond-taser", "Diamond Taser", "Gun skins", 3.99, "A diamond taser, for guards. Same stats as everyone's."),
  cosmetic("chat-colours", "Chat Colours", "Chat", 2.99, "Write in any of 12 colours from the server's palette."),
];

export const support: Package[] = [
  {
    id: "server-boost",
    name: "Server Boost",
    category: "support",
    price: 4.99,
    icon: "/store/server-boost.png",
    blurb: "Fireworks over the whole map and a thank-you broadcast to everyone online. Helps pay for the server.",
    commands: ["mostwanted boost {username}"],
  },
  {
    id: "tip-jar",
    name: "Tip Jar",
    category: "support",
    price: 1.99,
    icon: "/store/tip-jar.png",
    blurb: "A tip toward the server, nothing more. Thank you.",
    commands: [],
  },
];

// Legend includes every cosmetic, including ones added later.
ranks.find((r) => r.id === "legend")!.includes = cosmetics.map((c) => c.id);

export const catalog: Package[] = [...ranks, ...cosmetics, ...support];

/** What a rank's included cosmetics cost on their own: the honest "worth" on its card. */
export const includedValue = (rank: Package) =>
  (rank.includes ?? []).reduce((sum, id) => sum + (cosmetics.find((c) => c.id === id)?.price ?? 0), 0);

/** The rank whose included cosmetics are worth the most for its price: worked out, so "Best value" stays true. */
export const bestValue = ranks
  .filter((r) => r.includes?.length)
  .reduce((best, r) => (includedValue(r) / r.price > includedValue(best) / best.price ? r : best));

/** The cheapest rank that includes a cosmetic, or none. */
export const firstRankWith = (id: string) => ranks.find((r) => r.includes?.includes(id));

// The rank table: each row's value per rank (true, false or text).
export const rankTable: { perk: string; values: (boolean | string)[] }[] = [
  { perk: "Tag in chat and the tab list", values: [true, true, true, true] },
  { perk: "Coloured name", values: [false, false, true, true] },
  { perk: "Discord role", values: [true, true, true, true] },
  { perk: "Supporter badge on your player page", values: [true, true, true, true] },
  { perk: "Escape effects", values: ["Confetti", "Confetti", "All", "All"] },
  { perk: "Trails", values: [false, "Smoke", "All", "All"] },
  { perk: "Arrest effects", values: [false, false, "All", "All"] },
  { perk: "Chat colours", values: [false, true, true, true] },
  { perk: "Gun skins", values: [false, false, false, "All"] },
  { perk: "Joins announced", values: [false, false, true, true] },
  { perk: "Your own join message", values: [false, false, false, true] },
  { perk: "Cosmetics added later", values: [false, false, false, true] },
];

export const never = ["Guns", "Ammo", "Armor", "Cash", "Shorter sentences", "The guard rank", "Crates or anything random"];

export const money = (n: number, currency = "USD") =>
  new Intl.NumberFormat("en-US", { style: "currency", currency }).format(n);
