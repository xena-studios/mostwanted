// The public stats API (backend/.../api/Api.java): player pages and leaderboards.
// No cookies: these responses are the same for everyone and cached at Cloudflare.

import { ApiError } from "./api";

export type Period = "all" | "month" | "week";

export type Profile = {
  uuid: string;
  name: string;
  bedrock: boolean;
  role: string;
  bounty: number;
  firstJoined: string;
  lastSeen: string;
  stats: Record<Period, Record<string, number>>;
};

export type Entry = { rank: number; uuid: string; name: string; value: number };
export type Board = { board: string; period: Period; entries: Entry[] };

/** Java names, plus what Bedrock names through Minehut can have; the API's own rule. */
const NAME = /^[A-Za-z0-9_.* -]{1,32}$/;

/** Whether the API would take this as a name (all dots would be a path, not a name). */
export const validName = (name: string) => NAME.test(name) && !/^\.+$/.test(name);

async function get<T>(api: string, path: string): Promise<T> {
  let response: Response;
  try {
    response = await fetch(api + path, { headers: { Accept: "application/json" } });
  } catch {
    throw new ApiError(0, "Couldn't reach MostWanted. Check your connection and try again.");
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = typeof data.error === "string" ? data.error : "Something went wrong.";
    throw new ApiError(response.status, message.charAt(0).toUpperCase() + message.slice(1).replace(/\.?$/, "."));
  }
  return data as T;
}

export const player = (api: string, name: string) =>
  get<Profile>(api, `/v1/players/${encodeURIComponent(name)}`);

export const board = (api: string, id: string, period: Period, limit = 10) =>
  get<Board>(api, `/v1/leaderboards/${encodeURIComponent(id)}?period=${period}&limit=${limit}`);

const count = new Intl.NumberFormat("en-US");

/** Money is whole dollars, shown like the game does: $1,200. */
export const format = (value: number, money: boolean) =>
  (money ? (value < 0 ? "-$" : "$") : "") + count.format(money ? Math.abs(value) : value);

// Floodgate gives Bedrock players a UUID with zeroes up front and no Java skin.
const bedrockUuid = (uuid: string) => uuid.startsWith("00000000-0000-0000-");

/** The player's head from mc-heads.net (allowed by the CSP); Steve for Bedrock players. */
export const head = (uuid: string, size: number, bedrock = false) =>
  `https://mc-heads.net/avatar/${bedrock || bedrockUuid(uuid) ? "MHF_Steve" : uuid}/${size}`;

export const playerHref = (name: string) => `/player?name=${encodeURIComponent(name)}`;
