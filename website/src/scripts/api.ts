// Calls to the backend from the browser, with the session cookie. Errors come back as
// {"error": "..."}; this turns them into a message to show.

export type Me = {
  id: number;
  discord: { id: string; name: string } | null;
  minecraft: { uuid: string; name: string } | null;
  linked: boolean;
};

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
    });
  } catch {
    throw new ApiError(0, "Couldn't reach MostWanted. Check your connection and try again.");
  }
  if (response.status === 204) return null;
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = typeof data.error === "string" ? data.error : "Something went wrong.";
    throw new ApiError(response.status, message.charAt(0).toUpperCase() + message.slice(1) + ".");
  }
  return data as T;
}
