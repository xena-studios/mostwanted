// Copies the site's images from ../brand (this repo's brand folder, built by
// scripts/brand-assets.py). The copies are committed, so the site only changes
// when someone syncs on purpose.
//
//   npm run brand:sync
import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(import.meta.dirname, "..");
const BRAND = path.resolve(ROOT, "../brand");

// [from (in brand/), to (in website/)]
const files = [
  ["source/logo-wordmark.png", "src/brand/wordmark.png"],
  ["source/logo-monogram.png", "src/brand/monogram.png"],
  ["out/background-1920x1080.png", "src/brand/background.png"],
  ["out/icon-512.png", "public/icon.png"],
  ["out/apple-touch-icon-180.png", "public/apple-touch-icon.png"],
  ["out/favicon-32.png", "public/favicon.png"],
  ["out/og-1200x630.png", "public/og.png"],
];

for (const [from, to] of files) {
  const dest = path.join(ROOT, to);
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.copyFileSync(path.join(BRAND, from), dest);
}
console.log(`synced ${files.length} files from brand/`);
