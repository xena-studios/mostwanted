import type { APIRoute } from "astro";
import { getCollection } from "astro:content";

const pages = ["/", "/news", "/leaderboards", "/wiki", "/rules", "/staff", "/creators", "/apply", "/support", "/store"];

export const GET: APIRoute = async ({ site }) => {
  const news = (await getCollection("news")).map((p) => `/news/${p.id}`);
  const wiki = (await getCollection("wiki")).map((a) => `/wiki/${a.id}`);
  const urls = [...pages, ...news, ...wiki].map((p) => `  <url><loc>${new URL(p, site)}</loc></url>`).join("\n");
  const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`;
  return new Response(xml, { headers: { "Content-Type": "application/xml" } });
};
