// Everything about the server the pages share. Flip `open` and fill in the
// links when the server launches; every page reads from here.

export const site = {
  name: "MostWanted",
  address: "play.mostwantedmc.com",
  // Live features: sign-in and accounts, player search, leaderboards, Support and
  // Apply. Open since 2026-10-28, ahead of the game's own launch.
  open: true,
  // The game itself, open to everyone (the whitelist off, Minehut set up): the header
  // then shows whether it's up and how many are playing, live from the API.
  gameOpen: false,
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

// Player pages and leaderboards read the stats API once the server opens; `astro dev`
// turns them on too, against PUBLIC_API_URL.
export const statsOpen = site.open || import.meta.env.DEV;

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

// The boards the API serves (backend/.../stats/Board.java; docs/08-website.md#leaderboards).
// `periods: false` is a board with no all-time/monthly/weekly split; `money` shows values as $.
export const boards = [
  { id: "most-wanted", name: "Most Wanted", stat: "Bounty", blurb: "The highest bounties on the server right now.", periods: false, money: true },
  { id: "escapes", name: "Top Escapers", stat: "Escapes", blurb: "Most times over the wall.", periods: true, money: false },
  { id: "guards", name: "Top Guards", stat: "Arrests", blurb: "Most criminals brought in.", periods: true, money: false },
  { id: "robberies", name: "Top Robbers", stat: "Robberies", blurb: "Most banks, jewelers and gas stations hit.", periods: true, money: false },
  { id: "bounty-earned", name: "Bounty Hunters", stat: "Earned", blurb: "Most bounty collected from arrests.", periods: true, money: true },
  { id: "most-arrested", name: "Most Arrested", stat: "Arrests", blurb: "Caught the most times. Back to the cell.", periods: true, money: false },
];

// What a player's page shows, in order, from the API's stat keys.
export const statLabels = [
  { key: "escapes", label: "Escapes", money: false },
  { key: "robberies", label: "Robberies", money: false },
  { key: "cash_stolen", label: "Cash stolen", money: true },
  { key: "times_arrested", label: "Times arrested", money: false },
  { key: "arrests", label: "Arrests made", money: false },
  { key: "bounty_earned", label: "Bounty earned", money: true },
  { key: "guards_killed", label: "Guards killed", money: false },
  { key: "riots", label: "Riots", money: false },
];

export const periods = [
  { id: "all", label: "All time" },
  { id: "month", label: "Monthly" },
  { id: "week", label: "Weekly" },
];
