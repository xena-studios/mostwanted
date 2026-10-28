// The cart, kept in this browser between visits. Each package once: everything in the
// store is one-time and permanent, so there's nothing to buy twice.

const KEY = "mw-cart";

export function read(): string[] {
  try {
    const ids = JSON.parse(localStorage.getItem(KEY) ?? "[]");
    return Array.isArray(ids) ? ids.filter((id) => typeof id === "string") : [];
  } catch {
    return [];
  }
}

export function write(ids: string[]) {
  localStorage.setItem(KEY, JSON.stringify([...new Set(ids)]));
  window.dispatchEvent(new CustomEvent("cart-changed"));
}

export function toggle(id: string): boolean {
  const ids = read();
  const has = ids.includes(id);
  write(has ? ids.filter((x) => x !== id) : [...ids, id]);
  return !has;
}

export function clear() {
  write([]);
}
