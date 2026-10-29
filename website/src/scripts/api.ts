// Calls to the backend from the browser, with the session cookie. Errors come back as
// {"error": "..."}; this turns them into a message to show.

export type Me = {
  id: number;
  discord: { id: string; name: string } | null;
  minecraft: { uuid: string; name: string } | null;
  linked: boolean;
  /** Their staff rank ("helper", "moderator", "admin", "owner"), or null. */
  staff: string | null;
  unread: number;
};

/** A path on this site to go to after signing in: only our own pages, never another site. */
export function afterSignIn(next: string | null): string {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/account";
}

/** Remembers on this device whether someone's signed in, for the header's link. */
export function signedIn(yes: boolean) {
  try {
    if (yes) localStorage.setItem("mw-signed-in", "1");
    else localStorage.removeItem("mw-signed-in");
  } catch {
    // Private browsing without storage: the header just keeps saying "Sign in".
  }
}

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

export async function call<T>(api: string, method: string, path: string, body?: unknown): Promise<T | null> {
  let response: Response;
  try {
    response = await fetch(api + path, {
      method,
      credentials: "include",
      headers: body === undefined ? {} : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(10_000),
    });
  } catch {
    throw new ApiError(0, "Couldn't reach MostWanted right now. Try again in a minute.");
  }
  // The header shows "Account" instead of "Sign in" from this, with no call on every page.
  if (path === "/v1/me" && method === "GET") signedIn(response.ok);
  if (path === "/v1/auth/logout" || (path === "/v1/me" && method === "DELETE")) signedIn(false);
  if (response.status === 204) return null;
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = typeof data.error === "string" ? data.error : "Something went wrong.";
    throw new ApiError(response.status, message.charAt(0).toUpperCase() + message.slice(1).replace(/\.?$/, "."));
  }
  return data as T;
}
