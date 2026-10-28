// A small test map to walk around in (`npm run world`): a prison with cells, a
// breakable wall, a keycard gate and the riot alarm, then the police station, a
// hideout, the bank and a safe town square, each zoned. Not the real map: just enough
// of each thing to try it by hand. The ground is a flat world's, at y -61.

/** [name, kind, min, max, spawn or null], as in the zones table. */
export const ZONES = [
  ["prison", "prison", [-40, -64, -20], [-10, -40, 20], [-25.5, -60, 0.5]],
  ["cell-1", "cell", [-38, -61, -19], [-36, -57, -16], [-36.5, -60, -17.5]],
  ["cell-2", "cell", [-34, -61, -19], [-32, -57, -16], [-32.5, -60, -17.5]],
  ["cell-3", "cell", [-30, -61, -19], [-28, -57, -16], [-28.5, -60, -17.5]],
  ["cell-4", "cell", [-26, -61, -19], [-24, -57, -16], [-24.5, -60, -17.5]],
  ["yard-alarm", "alarm", [-27, -61, 8], [-23, -57, 12], null],
  ["east-wall", "wall", [-10, -60, 5], [-10, -58, 7], null],
  ["east-gate", "gate", [-10, -60, -2], [-10, -58, 0], null],
  ["square", "safe", [-6, -64, -6], [6, -40, 6], null],
  ["police", "police", [0, -64, -32], [16, -40, -18], [8.5, -60, -24.5]],
  ["hideout", "hideout", [20, -64, 10], [32, -40, 22], [26.5, -60, 16.5]],
  ["bank", "robbery", [0, -64, 20], [12, -40, 32], null],
];

const box = (x1, y1, z1, x2, y2, z2, block) => `fill ${x1} ${y1} ${z1} ${x2} ${y2} ${z2} minecraft:${block}`;

/** Four walls (no roof) from y1 to y2, and a floor. */
const building = (x1, z1, x2, z2, y2, wall, floor) => [
  box(x1, -61, z1, x2, -61, z2, floor),
  box(x1, -60, z1, x2, y2, z1, wall),
  box(x1, -60, z2, x2, y2, z2, wall),
  box(x1, -60, z1, x1, y2, z2, wall),
  box(x2, -60, z1, x2, y2, z2, wall),
];

const door = (x, z) => box(x, -60, z, x, -59, z, "air");
const sign = (x, y, z, facing, text) =>
  `setblock ${x} ${y} ${z} minecraft:oak_wall_sign[facing=${facing}]{front_text:{messages:["","${text}","",""]}}`;

export const BUILD = [
  "forceload add -48 -40 40 40",
  "time set day",

  // The prison: stone brick walls, a gravel yard, four cells along the north wall.
  ...building(-40, -20, -10, 20, -56, "stone_bricks", "gravel"),
  ...[-38, -34, -30, -26].flatMap((x) => [
    box(x - 1, -60, -19, x - 1, -57, -15, "stone_bricks"),
    box(x + 3, -60, -19, x + 3, -57, -15, "stone_bricks"),
    box(x, -61, -19, x + 2, -61, -16, "polished_andesite"),
    box(x, -57, -19, x + 2, -57, -15, "stone_bricks"),
    box(x, -60, -15, x + 2, -58, -15, "iron_bars"),
    `setblock ${x} -60 -19 minecraft:white_bed[facing=south,part=foot]`,
    `setblock ${x} -60 -18 minecraft:white_bed[facing=south,part=head]`,
  ]),
  // The breakable wall looks cracked; the gate is iron bars.
  box(-10, -60, 5, -10, -58, 7, "cracked_stone_bricks"),
  box(-10, -60, -2, -10, -58, 0, "iron_bars"),
  // The riot alarm: a bell on a post in the yard.
  box(-25, -60, 10, -25, -59, 10, "stone_brick_wall"),
  "setblock -25 -58 10 minecraft:bell[attachment=floor,facing=east]",
  sign(-26, -60, 10, "west", "Riot alarm"),

  // The town square: safe ground between everything.
  box(-6, -61, -6, 6, -61, 6, "smooth_stone"),
  "setblock 0 -60 0 minecraft:lantern",

  // The police station, with the armory sign.
  ...building(0, -32, 16, -18, -56, "light_blue_terracotta", "polished_diorite"),
  door(8, -18),
  sign(9, -59, -17, "south", "Police"),

  // The hideout.
  ...building(20, 10, 32, 22, -57, "dark_oak_planks", "spruce_planks"),
  door(20, 16),
  sign(19, -59, 17, "west", "Hideout"),

  // The bank: stand inside to rob it.
  ...building(0, 20, 12, 32, -55, "quartz_bricks", "polished_blackstone"),
  door(6, 20),
  box(4, -60, 29, 8, -60, 31, "gold_block"),
  sign(7, -59, 19, "north", "Bank"),

  "setworldspawn 0 -60 0",
];

/** Leaderboards around the square, written to boards.yml before the server starts. */
export const BOARDS = `boards:
  most-wanted:
    board: most-wanted
    period: all
    world: world
    x: -4.5
    y: -58.0
    z: -4.5
  escapes:
    board: escapes
    period: week
    world: world
    x: 4.5
    y: -58.0
    z: -4.5
  guards:
    board: guards
    period: week
    world: world
    x: 4.5
    y: -58.0
    z: 4.5
`;
