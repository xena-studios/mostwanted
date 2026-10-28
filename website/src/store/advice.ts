// Honest suggestions for the cart: never pay twice for something, and when a rank
// gives you what's in the cart for less, say so. Prices are passed in, so live
// Tebex prices are used once the store is connected.

type Item = { id: string; category: string; includes?: string[] };

export type Advice = {
  /** In the cart but already part of something else in it: [item, what covers it]. */
  redundant: [string, string][];
  /**
   * A rank that would replace things in the cart: cheaper (saves > 0), or only a little
   * more (saves down to -UPSELL) for everything it adds on top.
   */
  swap: { rank: string; replaces: string[]; saves: number } | null;
};

/** The most a suggested rank may cost over what it replaces. */
export const UPSELL = 5;

export function advise(cart: string[], items: Item[], price: (id: string) => number): Advice {
  const ranks = items.filter((i) => i.category === "ranks");
  const order = (id: string) => ranks.findIndex((r) => r.id === id);
  const inCart = ranks.filter((r) => cart.includes(r.id));
  const top = inCart.reduce<Item | null>((best, r) => (!best || order(r.id) > order(best.id) ? r : best), null);

  const redundant: [string, string][] = [];
  if (top) {
    // A higher rank includes the lower ones, and every cosmetic the rank includes.
    for (const r of inCart) if (r !== top) redundant.push([r.id, top.id]);
    for (const id of cart) if (top.includes?.includes(id)) redundant.push([id, top.id]);
  }

  // A higher rank that would replace what it covers in the cart, for less.
  let swap: Advice["swap"] = null;
  const covered = new Set(redundant.map(([id]) => id));
  for (const rank of ranks) {
    if (top && order(rank.id) <= order(top.id)) continue;
    const replaces = cart.filter(
      (id) => !covered.has(id) && (rank.includes?.includes(id) || (ranks.some((r) => r.id === id) && order(id) < order(rank.id))),
    );
    const cosmetics = replaces.filter((id) => !ranks.some((r) => r.id === id));
    if (cosmetics.length < 2) continue;
    const saves = replaces.reduce((sum, id) => sum + price(id), 0) - price(rank.id);
    if (saves > -UPSELL && (!swap || saves > swap.saves)) swap = { rank: rank.id, replaces, saves };
  }
  return { redundant, swap };
}
