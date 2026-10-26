# Website

[mostwantedmc.com](https://mostwantedmc.com): the server's website. A static [Astro](https://astro.build) site, served by Cloudflare as a Worker with static assets only, the same setup as [xenastudios.co](https://github.com/xena-studios/website). The plan, including the store and player stats still to come, is in [docs/08-website.md](../docs/08-website.md).

## Develop

Needs Node 24.

```sh
npm install
npm run dev          # http://localhost:4321, with hot reload
npm run build        # static site in dist/
npm run preview      # serve dist/ locally
npm run brand:sync   # copy the logo, background and icons in from ../brand
```

The site ships no JavaScript. Pages are Astro components rendered to HTML at build time.

## Layout

```
src/
  pages/
    index.astro        home: the hero, how it plays, how to join
    guards.astro       becoming a guard, and the guard code
    rules.astro        the rules (draft until launch)
    404.astro          served for unknown paths
    sitemap.xml.ts     /sitemap.xml (add new routes to its list)
  layouts/Base.astro   <head>, header, footer
  styles/global.css    MostWanted's tokens (from xena-studios/brand), type, buttons, long-form pages
  brand/               synced from ../brand by npm run brand:sync: don't edit
public/                copied as-is: _headers (CSP and security headers), robots.txt, icons, og.png
scripts/sync-brand.mjs copies the brand images in
```

## Look

From [docs/09-brand.md](../docs/09-brand.md) and MostWanted's section in [xena-studios/brand](https://github.com/xena-studios/brand#mostwanted): the blurred pixel desert behind the block wordmark, Desert Orange `#FF9933` on dusk, **Lilita One** for headings (self-hosted from Fontsource) and the system font for body text.

## Deploy

[`website.yml`](../.github/workflows/website.yml) builds every pull request that touches `website/`, and deploys `main` to Cloudflare with `wrangler deploy`. Deploying needs, in the repo's `production` environment:

- the secret `CLOUDFLARE_API_TOKEN`: an API token with **Account → Workers Scripts → Edit**
- the variable `CLOUDFLARE_ACCOUNT_ID`

The custom domain (`mostwantedmc.com`) is attached to the `mostwanted-website` Worker once, by hand, in the Cloudflare dashboard.
