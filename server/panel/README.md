# Panel

What runs MostWanted's node: [Calagopus](https://calagopus.com), a game server panel with its node agent built in, and its database agent for the managed PostgreSQL. Set up once, by hand, on a fresh VPS ([docs/12-launch.md](../../docs/12-launch.md)); everything after that happens in the panel.

```
compose.yml                     the panel and its node agent (Wings), in /opt/calagopus
.env.example                    the panel's encryption key, as /opt/calagopus/.env
wings-config.yml                the node's starting configuration
Caddyfile                       HTTPS for panel.mostwantedmc.com, as /etc/caddy/Caddyfile
db-agent/compose.yml            the database agent, in /opt/calagopus-db-agent
db-agent/config.yml.example     its configuration, as /opt/calagopus-db-agent/config.yml
db-agent/postgres-template.yml  the managed PostgreSQL, imported in the panel
```

The eggs the panel installs the servers from are in [`../eggs/`](../eggs/); the panel syncs them from this repository.
