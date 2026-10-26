// @ts-check
import { defineConfig } from "astro/config";

export default defineConfig({
  site: "https://mostwantedmc.com",
  output: "static",
  trailingSlash: "never",
  build: {
    format: "file",
    // Hashed CSS, fonts and images go to /assets/ (Astro's default is /_astro/).
    assets: "assets",
    // The CSP (public/_headers) allows only same-origin styles: no inline <style>.
    inlineStylesheets: "never",
  },
  vite: {
    // Nor data: URIs, so small fonts and images must stay real files.
    build: { assetsInlineLimit: 0 },
  },
});
