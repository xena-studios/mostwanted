import type { APIRoute } from "astro";
import { catalog } from "../store/catalog";

// The catalogue for the store menu in game (core's StoreCatalog), so the game and the
// website always sell the same things at the same prices. What a package runs stays here.
export const GET: APIRoute = () => {
  const packages = catalog.map(({ id, name, category, kind, price, blurb, perks, includes }) => ({
    id,
    name,
    category,
    kind: kind ?? null,
    price,
    blurb,
    perks: perks ?? [],
    includes: includes ?? [],
  }));
  return new Response(JSON.stringify({ packages }), { headers: { "Content-Type": "application/json" } });
};
