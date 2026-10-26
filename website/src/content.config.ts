import { defineCollection } from "astro:content";
import { glob } from "astro/loaders";
import { z } from "astro/zod";

// News posts: one Markdown file each in src/content/news/, newest first by date.
const news = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/news" }),
  schema: z.object({
    title: z.string(),
    date: z.coerce.date(),
    summary: z.string(),
  }),
});

// Wiki articles: src/content/wiki/, listed by section then order.
const wiki = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/wiki" }),
  schema: z.object({
    title: z.string(),
    summary: z.string(),
    section: z.enum(["Getting started", "Playing", "Guards", "Server"]),
    order: z.number(),
  }),
});

export const collections = { news, wiki };
