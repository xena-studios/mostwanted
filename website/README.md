# Website

[mostwantedmc.com](https://mostwantedmc.com): the server's website. A static [Astro](https://astro.build) site, served by Cloudflare as a Worker with static assets only, the same setup as [xenastudios.co](https://github.com/xena-studios/website). The plan, including the store and player stats still to come, is in [docs/08-website.md](../docs/08-website.md).

## Develop

Needs Node 24.

```sh
npm install
npm run dev          # http://localhost:4321, with hot reload
npm run build        # static site in dist/
npm run preview      # serve dist/ locally
npm run brand:sync   # copy the logo, background and icons in from ../brand,
                     # and the XENA footer logo from a xena-studios/brand checkout at ../../brand
```

Pages are Astro components rendered to HTML at build time. The JavaScript is small and per page: the address copy button, the player lookup, sign-in and the account page, and the store's cart and checkout.

**The store** ([docs/11-store.md](../docs/11-store.md)) reads its catalogue from `src/store/catalog.ts` and, when the `PUBLIC_TEBEX_TOKEN` build variable is set (the `TEBEX_PUBLIC_TOKEN` repository variable in CI), live prices and checkout from Tebex's Headless API (`src/store/tebex.ts`). Without it the store shows everything and says checkout opens at launch.

## Layout

```
src/
  site.ts              the server address, Discord/store links, nav, the leaderboards;
                       flip `open` at launch and every page updates
  pages/
    index.astro        home: hero, explore cards, player search, how it plays, news
    news/              news list and posts (from src/content/news/)
    wiki/              wiki index and articles (from src/content/wiki/)
    leaderboards.astro the boards (filled by the stats feed once it exists)
    player.astro       player lookup (/player?name=…)
    store.astro        the store: ranks, comparison, cosmetics, cart and Tebex checkout
    rules, staff, creators, apply, support, login, account, terms, privacy, 404, sitemap.xml
  store/               the catalogue (catalog.ts), the cart and the Tebex client
  content/             news posts and wiki articles in Markdown: add a file to publish
  components/          PageHero (the desert header on inner pages), CopyAddress
  layouts/Base.astro   <head>, header, footer
  styles/global.css    tokens, panels, buttons, forms, long-form text
  brand/               synced from ../brand by npm run brand:sync: don't edit
public/                _headers (CSP and security headers), _redirects, robots.txt, icons, og.png,
                       store/ (the store's pixel icons, in vanilla's style)
```

## Writing news and wiki pages

Add a Markdown file to `src/content/news/` (`title`, `date`, `summary`) or `src/content/wiki/` (`title`, `summary`, `section`, `order`) and open a pull request. The sitemap picks it up on its own.

## Before launch

Player search, the leaderboards and sign-in show a "starts when the server opens" state until the backend exists: the stats feed into Cloudflare D1 ([docs/08-website.md](../docs/08-website.md#player-stats)) and Discord sign-in. At launch, set `open`, `discord` and `store` in `src/site.ts`.

## Look

From [docs/09-brand.md](../docs/09-brand.md) and MostWanted's section in [xena-studios/brand](https://github.com/xena-studios/brand#mostwanted): the blurred pixel desert behind the block wordmark, Desert Orange `#FF9933` on dusk, chunky bevelled panels and buttons like blocks in the game. **Lilita One** for headings, **Rubik** for text and **VT323** (a pixel face) for labels, all self-hosted from Fontsource. Icons are [Lucide](https://lucide.dev), inlined as SVG at build time.

## Checks

Before a release, run Lighthouse against `npx wrangler dev` (it applies `public/_headers`, so the CSP is real). On 2026-10-26 every page scored 100 for performance, accessibility, best practices and SEO, on mobile and desktop. The one exception is `/login` at SEO 66, on purpose: it's `noindex`, and so is `/player`.

## Deploy

[`website.yml`](../.github/workflows/website.yml) builds every pull request that touches `website/`, and deploys `main` to Cloudflare with `wrangler deploy`. Deploying needs, in the repo's `production` environment:

- the secret `CLOUDFLARE_API_TOKEN`: an API token with **Account → Workers Scripts → Edit**
- the variable `CLOUDFLARE_ACCOUNT_ID`

The custom domain (`mostwantedmc.com`) is attached to the `mostwanted-website` Worker once, by hand, in the Cloudflare dashboard.
