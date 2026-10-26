# Backend

The services around the game, in one Java app: the website's API (`api.mostwantedmc.com`) and the Discord bot. Not built yet: the plan is in [docs/08-website.md](../docs/08-website.md#accounts-and-the-api) and [docs/10-discord-bot.md](../docs/10-discord-bot.md).

```
discord-setup/   builds the Discord server (roles, channels, posts, AutoMod) from a layout in code
```

Its secrets (the bot token, the OAuth client secret, the database password) never go in this repo: they live in the backend's `.env` on the VPS.
