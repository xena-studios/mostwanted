// The store's connection to Tebex, from the browser, through Tebex's Headless API
// (docs.tebex.io/developers/headless-api). Only the public token is used: it's meant
// for browsers. The private key never comes near the site.

const API = "https://headless.tebex.io/api";

export type Live = {
  /** Tebex's package ident, by package name. */
  idents: Map<string, string>;
  /** Tebex's price (with tax where it applies), by package name. */
  prices: Map<string, number>;
  currency: string;
};

type TebexPackage = { name: string; ident: string; total_price: number; currency: string };
type Basket = { ident: string; links?: { checkout?: string } };

async function call<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: init?.body ? { "Content-Type": "application/json" } : undefined,
    signal: AbortSignal.timeout(15_000),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const detail = typeof body.detail === "string" ? body.detail : typeof body.message === "string" ? body.message : "";
    throw new Error(detail || `The store answered ${response.status}.`);
  }
  return body as T;
}

/** Every package in the Tebex store, matched to ours by name. */
export async function loadLive(token: string): Promise<Live> {
  const { data } = await call<{ data: { packages?: TebexPackage[] }[] }>(
    `${API}/accounts/${token}/categories?includePackages=1`,
  );
  const live: Live = { idents: new Map(), prices: new Map(), currency: "USD" };
  for (const category of data) {
    for (const pkg of category.packages ?? []) {
      live.idents.set(pkg.name, pkg.ident);
      live.prices.set(pkg.name, pkg.total_price);
      live.currency = pkg.currency || live.currency;
    }
  }
  return live;
}

/**
 * Makes a basket for this Minecraft username with these packages, then sends the
 * browser on: to Tebex's sign-in if the store wants it (it comes back to
 * /store?checkout=<basket>), or straight to payment.
 */
export async function checkout(token: string, username: string, idents: string[], creatorCode: string) {
  const back = `${location.origin}/store`;
  const { data: basket } = await call<{ data: Basket }>(`${API}/accounts/${token}/baskets`, {
    method: "POST",
    body: JSON.stringify({
      username,
      complete_url: `${back}?thanks=1`,
      cancel_url: back,
      complete_auto_redirect: true,
    }),
  });
  for (const ident of idents) {
    await call(`${API}/baskets/${basket.ident}/packages`, {
      method: "POST",
      body: JSON.stringify({ package_id: ident, quantity: 1 }),
    });
  }
  if (creatorCode) {
    await call(`${API}/accounts/${token}/baskets/${basket.ident}/creator-codes`, {
      method: "POST",
      body: JSON.stringify({ creator_code: creatorCode }),
    });
  }
  const auth = await call<{ name: string; url: string }[]>(
    `${API}/accounts/${token}/baskets/${basket.ident}/auth?returnUrl=${encodeURIComponent(`${back}?checkout=${basket.ident}`)}`,
  ).catch(() => []);
  if (Array.isArray(auth) && auth.length > 0) {
    // Only this basket may continue to payment when Tebex sends the buyer back.
    try {
      sessionStorage.setItem(PENDING, basket.ident);
    } catch {
      // No storage: coming back just shows the store, and they check out again.
    }
    location.assign(auth[0].url);
    return;
  }
  await toPayment(token, basket.ident);
}

const PENDING = "mw-checkout";

/**
 * Back from Tebex's sign-in: on to payment, but only for the basket this browser
 * just made. A ?checkout= link from anyone else (a basket for their username) is
 * ignored, so nobody can be tricked into paying for someone else.
 */
export async function resumeCheckout(token: string, basketIdent: string) {
  let pending: string | null = null;
  try {
    pending = sessionStorage.getItem(PENDING);
    sessionStorage.removeItem(PENDING);
  } catch {
    // No storage: nothing to resume.
  }
  if (pending !== basketIdent) return;
  await toPayment(token, basketIdent);
}

/** On to payment for that basket. */
async function toPayment(token: string, basketIdent: string) {
  const { data } = await call<{ data: Basket }>(`${API}/accounts/${token}/baskets/${basketIdent}`);
  if (!data.links?.checkout) {
    throw new Error("That basket has already been paid for.");
  }
  location.assign(data.links.checkout);
}
