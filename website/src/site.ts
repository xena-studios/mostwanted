// Everything about the server the pages share. Flip `open` and fill in the
// links when the server launches; every page reads from here.

export const site = {
  name: "MostWanted",
  address: "play.mostwantedmc.com",
  // Until launch, live features (player search, leaderboards, sign-in) show
  // their "starts when the server opens" state.
  open: false,
  discord: null as string | null,
  // Tebex's public webstore token (creator.tebex.io > Developers > API keys), set as
  // the TEBEX_PUBLIC_TOKEN repository variable. Without it the store shows its
  // catalogue and prices from src/store/catalog.ts, and checkout says it opens soon.
  tebexToken: (import.meta.env.PUBLIC_TEBEX_TOKEN as string | undefined) || null,
  email: "contact@xenastudios.co",
  source: "https://github.com/xena-studios/mostwanted",
  // The backend (backend/). PUBLIC_API_URL points a local build at a local one.
  api: (import.meta.env.PUBLIC_API_URL as string | undefined) ?? "https://api.mostwantedmc.com",
};

// Sign-in works once the server opens; `astro dev` turns it on to try locally.
export const signInOpen = site.open || import.meta.env.DEV;

export const nav = [
  { href: "/news", label: "News" },
  { href: "/leaderboards", label: "Leaderboards" },
  { href: "/wiki", label: "Wiki" },
  { href: "/rules", label: "Rules" },
  { href: "/staff", label: "Staff" },
  { href: "/creators", label: "Creators" },
  { href: "/apply", label: "Apply" },
  { href: "/support", label: "Support" },
];

// The boards the API fills (docs/08-website.md#leaderboards).
export const boards = [
  { id: "most-wanted", name: "Most Wanted", stat: "Bounty", blurb: "The highest bounties on the server right now." },
  { id: "escapes", name: "Top Escapers", stat: "Escapes", blurb: "Most times over the wall." },
  { id: "fastest", name: "Fastest Escapes", stat: "Time", blurb: "Cell to freedom, quickest first." },
  { id: "guards", name: "Top Guards", stat: "Arrests", blurb: "Most criminals brought in." },
  { id: "heists", name: "Biggest Heists", stat: "Cash", blurb: "The largest single robberies." },
  { id: "richest", name: "Richest", stat: "Earned", blurb: "Most cash earned, all time." },
];
