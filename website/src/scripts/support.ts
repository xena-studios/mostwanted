// Tickets, applications and notifications: the shapes the API sends, and helpers the
// support, apply, account, ticket and staff pages share. Pages are built with DOM
// calls, never HTML strings (the CSP allows no innerHTML), so player text is safe.

export type Question = { id: string; label: string; hint: string; max: number; required: boolean; options: string[] };
export type Forms = {
  categories: Record<string, string>;
  subjectMax: number;
  bodyMax: number;
  applications: Record<string, Question[]>;
};
export type Ticket = {
  id: number;
  accountId: number;
  owner: string;
  category: string;
  subject: string;
  status: "open" | "answered" | "closed";
  createdAt: string;
  updatedAt: string;
};
export type Message = { id: number; author: string; staff: boolean; body: string; createdAt: string };
export type Application = {
  id: number;
  applicant: string;
  minecraft: string | null;
  kind: string;
  answers: Record<string, string>;
  status: "pending" | "accepted" | "denied";
  note: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  createdAt: string;
};
export type Notice = { id: number; text: string; link: string | null; createdAt: string; read: boolean };

/** What each state is called, for players. */
export const TICKET_STATES: Record<Ticket["status"], string> = {
  open: "Waiting for staff",
  answered: "Staff replied",
  closed: "Closed",
};
export const APPLICATION_STATES: Record<Application["status"], string> = {
  pending: "Waiting",
  accepted: "Accepted",
  denied: "Not this time",
};
export const KINDS: Record<string, string> = { guard: "Guard", staff: "Staff", creator: "Creator" };

/** An element with classes and children (text is always set as text). */
export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  className = "",
  ...children: (Node | string | null | undefined)[]
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  if (className) node.className = className;
  for (const child of children) if (child != null) node.append(child);
  return node;
}

/** Text with its https links made clickable: players paste screenshots and clips. */
export function linked(text: string): DocumentFragment {
  const out = document.createDocumentFragment();
  let last = 0;
  for (const match of text.matchAll(/https:\/\/[^\s<>"']+/g)) {
    out.append(text.slice(last, match.index));
    const a = el("a", "", match[0]);
    a.href = match[0];
    a.rel = "nofollow noopener ugc";
    a.target = "_blank";
    out.append(a);
    last = match.index! + match[0].length;
  }
  out.append(text.slice(last));
  return out;
}

const day = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });

export const when = (iso: string) => day.format(new Date(iso));

/** A coloured chip for a state. */
export function chip(state: string, label: string): HTMLSpanElement {
  return el("span", `chip ${state}`, label);
}
