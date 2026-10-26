# Server

The configs the live server runs, so every change goes through a pull request and the server can be rebuilt from this repo. Not filled in yet.

What goes here:

- `server.properties`, Leaf/Paper configs (`paper-global.yml`, `leaf-global.yml`, …)
- Plugin configs (LuckPerms, WorldGuard, TAB, FancyNpcs, …)
- The start script

What never goes here: passwords, tokens, database URLs with credentials, the Tebex secret key, the Discord bot token. Those live on the server only, or in GitHub secrets.

The install list (software, plugins, versions, start command) is in [docs/07-server-stack.md](../docs/07-server-stack.md).
