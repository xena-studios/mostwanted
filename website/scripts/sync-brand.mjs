// Copies the site's images from ../brand (this repo's brand folder, built by
// scripts/brand-assets.py), and the XENA logo from xena-studios/brand. The copies are committed, so the site only changes
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
  ["out/scene-mesa.png", "src/brand/scene-mesa.png"],
  ["out/scene-sun.png", "src/brand/scene-sun.png"],
  ["out/scene-rocks.png", "src/brand/scene-rocks.png"],
  ["out/scene-road.png", "src/brand/scene-road.png"],
  ["out/scene-cactus.png", "src/brand/scene-cactus.png"],
  ["out/scene-sky.png", "src/brand/scene-sky.png"],
  ["out/icon-512.png", "public/icon.png"],
  ["out/apple-touch-icon-180.png", "public/apple-touch-icon.png"],
  ["out/favicon-32.png", "public/favicon.png"],
  ["out/og-1200x630.png", "public/og.png"],
];

// From a checkout of xena-studios/brand: the XENA logo for the footer, as on
// raptorpanel.net and xenastudios.co. Expects ../../brand, or set XENA_BRAND_DIR.
const XENA = path.resolve(process.env.XENA_BRAND_DIR ?? path.join(ROOT, "../../brand"));
const xenaFiles = [["logo/xena.svg", "src/brand/xena.svg"]];

const copy = (base, list) => {
  for (const [from, to] of list) {
    const dest = path.join(ROOT, to);
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.copyFileSync(path.join(base, from), dest);
  }
};
copy(BRAND, files);
if (fs.existsSync(path.join(XENA, "logo/xena.svg"))) copy(XENA, xenaFiles);
else console.warn(`No xena-studios/brand checkout at ${XENA}: kept the current xena.svg.`);
console.log(`synced ${files.length + xenaFiles.length} files`);
