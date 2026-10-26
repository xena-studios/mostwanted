# Server

Everything the MostWanted game server runs, so setting up (or rebuilding) the VPS is a few commands, and every change goes through a pull request. Why each piece is here: [docs/07-server-stack.md](../docs/07-server-stack.md).

```
manifest.json         Leaf and every plugin, pinned to a version with its checksum
install.sh            downloads and checks them, then copies in the configs
start.sh              starts the server: Java heap, Paper's G1 flags, Minehut's login flags
test.sh               boots a test copy and fails if a plugin or config didn't load (CI runs it)
files/                configs copied into the server; changes from the defaults say "MostWanted:"
  server.properties   whitelist on until launch, adventure mode, 100 slots, view distance 8, lz4 regions
  config/             Leaf, Gale and Paper: async pathfinding/saves/entity tracking, DAB, Minehut's PROXY protocol
  purpur.yml          no phantoms/patrols/traders, quieter logs
  spigot.yml          entity activation and tracking ranges, item merging, despawn rates
  bukkit.yml          low natural mob spawning, no End
  plugins/            LuckPerms and LibertyBans (templates), Grim, ViaVersion, Fancy*, FAWE,
                      xUtilities and zChat in the message style
setup/luckperms.txt   the ranks, run once in the console
.env.example          the secrets the templates need; copy to <server-dir>/.env
```

## Set up the server

On the VPS, with Java 25, PostgreSQL, `curl`, `jq` and `git` installed (Raptor's Java 25 image has the first; see [docs/04-tech.md](../docs/04-tech.md) for the rest).

```sh
git clone https://github.com/xena-studios/mostwanted.git
cp mostwanted/server/.env.example /srv/mostwanted/.env   # then fill it in
mostwanted/server/install.sh /srv/mostwanted
echo eula=true > /srv/mostwanted/eula.txt               # Minecraft's EULA: https://aka.ms/MinecraftEULA
cd /srv/mostwanted && ./start.sh                        # MEMORY=8G by default
```

Then, once:

1. Run the `lp` lines from [`setup/luckperms.txt`](setup/luckperms.txt) in the console.
2. In Minehut's panel, set the server to **External** and point it at the VPS ([docs/04-tech.md](../docs/04-tech.md#how-players-connect)).
3. Firewall port 25565 to Minehut's addresses only, once Minehut tells us them.

## Update the server

Pull, run `install.sh` again, restart. It replaces the plugin jars it installed, removes ones that left the manifest, and overwrites the config files this repo tracks. The world, plugin data and other configs are left alone.

## Change a plugin or a config

- **A plugin version:** edit `manifest.json` with the new version's file, URL and sha512 from Modrinth, in a pull request. CI installs and boots the server with it.
- **A config:** edit the file under `files/` in a pull request. The server and plugin configs are full files, taken from a fresh boot of the pinned versions, so every setting is visible; lines we changed for speed or the game carry a `MostWanted:` comment where the reason isn't obvious. Configs with nothing to change (Grim's database and punishments, LibertyBans' scope, FAWE's worldedit-config) aren't tracked. LuckPerms' template only holds what we change: it fills in the rest itself. When a plugin update adds settings, boot a test copy and copy its new file back over ours, keeping our changes.
- **A secret:** never here. Add a `${NAME}` to a `.tmpl` file and to `.env.example`; the real value goes in the server's `.env`.

## Test locally

```sh
cp server/.env.example /tmp/mw/.env && sed -i '' 's/^LUCKPERMS_STORAGE=.*/LUCKPERMS_STORAGE=h2/; s/^LIBERTYBANS_DATABASE=.*/LIBERTYBANS_DATABASE=HSQLDB/; s/^DB_PASSWORD=.*/DB_PASSWORD=test/' /tmp/mw/.env
server/install.sh /tmp/mw && server/test.sh /tmp/mw
```

`test.sh` accepts the EULA in the folder you give it, so only point it at a test copy. It passes when every plugin enabled and none turned itself off. Expected noise in the log: ViaVersion saying there's nothing newer to translate yet, and `[FancyAnalytics] Failed to create event` from the Fancy plugins' statistics upload.
