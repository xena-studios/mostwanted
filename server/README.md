# Server

Everything the MostWanted game server runs, so setting up (or rebuilding) the VPS is a few commands, and every change goes through a pull request. Why each piece is here: [docs/07-server-stack.md](../docs/07-server-stack.md).

```
manifest.json         Leaf and every plugin, pinned to a version with its checksum
install.sh            downloads and checks them, then copies in the configs
start.sh              starts the server: Java heap, Paper's G1 flags, Minehut's login flags
test.sh               boots a test copy and fails if a plugin or config didn't load (CI runs it)
files/                configs copied into the server (only what we change from the defaults)
  server.properties   whitelist on until launch, adventure mode, 100 slots, view distance 8
  config/             Paper: Minehut's PROXY protocol, world performance settings
  spigot.yml          entity activation ranges, item merging
  bukkit.yml          low natural mob spawning
  plugins/            LuckPerms (a template), xUtilities and zChat in the message style
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
cd /srv/mostwanted && ./start.sh                        # MEMORY=9G by default
```

Then, once:

1. Run the `lp` lines from [`setup/luckperms.txt`](setup/luckperms.txt) in the console.
2. In Minehut's panel, set the server to **External** and point it at the VPS ([docs/04-tech.md](../docs/04-tech.md#how-players-connect)).
3. Firewall port 25565 to Minehut's addresses only, once Minehut tells us them.

## Update the server

Pull, run `install.sh` again, restart. It replaces the plugin jars it installed, removes ones that left the manifest, and overwrites the config files this repo tracks. The world, plugin data and other configs are left alone.

## Change a plugin or a config

- **A plugin version:** edit `manifest.json` with the new version's file, URL and sha512 from Modrinth, in a pull request. CI installs and boots the server with it.
- **A config:** edit the file under `files/` in a pull request. Plugins fill in any setting we leave out, so files only hold what we change. xUtilities and zChat are full files on purpose: every message there is in the [message style](../docs/09-brand.md#messages).
- **A secret:** never here. Add a `${NAME}` to a `.tmpl` file and to `.env.example`; the real value goes in the server's `.env`.

## Test locally

```sh
cp server/.env.example /tmp/mw/.env && sed -i '' 's/^LUCKPERMS_STORAGE=.*/LUCKPERMS_STORAGE=h2/; s/^DB_PASSWORD=.*/DB_PASSWORD=test/' /tmp/mw/.env
server/install.sh /tmp/mw && server/test.sh /tmp/mw
```

`test.sh` accepts the EULA in the folder you give it, so only point it at a test copy. It passes when every plugin enabled and none turned itself off. Expected noise in the log: ViaVersion saying there's nothing newer to translate yet, and `[FancyAnalytics] Failed to create event` from the Fancy plugins' statistics upload.
