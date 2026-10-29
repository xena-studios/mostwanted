# Launch

From "a fresh VPS" to "players can join and the API answers", in order. **[Calagopus](https://calagopus.com) runs everything**: the game server, the API and Discord bot, the database, backups and schedules, all from the panel at **panel.mostwantedmc.com**. The machine itself only gets Docker, the panel and Caddy, once (steps 1–4). About an hour and a half, most of it waiting for installs.

Why the setup looks like this: [tech](04-tech.md), [server stack](07-server-stack.md), [the API](08-website.md#the-api). What the panel installs: [`server/eggs/`](../server/eggs/) (the eggs), built from `main` by the [server release](../.github/workflows/server-release.yml) workflow. The panel's own files: [`server/panel/`](../server/panel/).

**Values you'll fill in along the way** (keep them in a note on your PC, not in the repo):

| | Where it comes from |
| --- | --- |
| `VPS_IP` | netcup's Server Control Panel (the server's page, Network). Today `159.195.233.108`. |
| `YOU` | The user you made when installing Ubuntu (step 1) |

Secrets (the panel's encryption key, the database password, the bot token, the OAuth secret, the TLS key, Tebex's secret key) only ever go on the VPS or in the panel: a server's **Startup** tab or its files. Never in the repo, a commit, a chat or a screenshot. Keep the encryption key and the database password in 1Password too.

**Moving from Raptor?** Do step 0 first. On a brand-new machine, skip it.

## 0. Out of Raptor (15 min)

Rebuilding the VPS wipes it, and the website has been open since 2026-10-28, so the database already holds accounts, tickets and applications. Take them with you:

1. In Raptor, **stop** the API, then the game server, so nothing writes while you copy.
2. Dump the database on the node, and copy it to your PC:
   ```sh
   ssh mostwanted
   sudo docker ps --format '{{.Names}}  {{.Image}}' | grep -i postgres   # the database's container
   sudo docker exec <container> pg_dump -U <user> -d <database> --no-owner --no-privileges > mostwanted.sql
   exit
   scp mostwanted:mostwanted.sql ~/Downloads/
   ```
   `<user>` and `<database>` are on the game server's **Databases** tab in Raptor. Open the file and check it has `CREATE TABLE` and `COPY` lines in it.
3. Only if the world has builds worth keeping: on the game server's **Files** tab, compress `world`, `world_nether` and `world_the_end` and download the archive. Plugin data lives in the database, so nothing else is needed.
4. Keep the API's `tls/origin.pem` and `tls/origin.key` (Files tab), or make new ones in step 6.

The IP stays the same, so Cloudflare's `api` record and Minehut need no changes.

## 1. The VPS (15 min)

The server is a netcup **RS 2000** in **Manassas, Virginia** (US East): 8 dedicated cores, 16 GB RAM, 256 GB NVMe.

1. In netcup's Server Control Panel, **Media → Images**: **Ubuntu 24.04 UEFI amd64**, Minimal, one large partition, locale **en_US.UTF-8**, **create an additional user** with sudo and your **SSH public key**. No custom script.
2. Your PC already has `ssh mostwanted` (in the dotfiles) as `root`; Ubuntu's image makes your own user instead, so set `User YOU` there, or log in as `YOU` until then. A reinstalled machine has a new host key: `ssh-keygen -R 159.195.233.108` first.
3. Log in, update, and turn on automatic security updates:
   ```sh
   ssh YOU@VPS_IP
   sudo apt-get update && sudo apt-get full-upgrade -y
   sudo apt-get install -y unattended-upgrades && sudo dpkg-reconfigure -f noninteractive unattended-upgrades
   sudo timedatectl set-timezone America/New_York
   sudo reboot
   ```
   New York time: logs match the game's clock, and the 4 AM backups land when US players are asleep.
4. SSH by key only, once a key works (check from a second terminal before closing the first):
   ```sh
   echo "PasswordAuthentication no" | sudo tee /etc/ssh/sshd_config.d/10-keys-only.conf
   sudo systemctl reload ssh
   ```
5. **Firewall**, in netcup's Server Control Panel (the server's **Network → Firewall**): a policy that allows these TCP ports in and drops everything else, applied to the server's public interface. Docker opens ports around the machine's own firewall (ufw), so netcup's is the one that counts.

   | Port | For |
   | --- | --- |
   | 22 | SSH |
   | 80, 443 | The panel (Caddy; 80 only to get its certificate) |
   | 2022 | SFTP to the servers' files (optional) |
   | 25565 | The game |
   | 8443 | The API; it refuses anything without Cloudflare's client certificate anyway (step 6) |

   PostgreSQL and the panel's internals listen only inside the machine (step 4), so they need nothing here.

## 2. DNS (2 min)

In Cloudflare, **DNS → Add record**: `A`, name `panel`, IPv4 `VPS_IP`, **DNS only** (grey cloud). Caddy gets its certificate straight from Let's Encrypt, and the panel's live console works without Cloudflare in the way.

## 3. The panel (15 min)

1. Docker, and the panel's files from this repo:
   ```sh
   curl -fsSL https://get.docker.com | sudo sh
   sudo mkdir -p /opt/calagopus && cd /opt/calagopus
   for f in compose.yml wings-config.yml .env.example; do
     sudo curl -fsSLO "https://raw.githubusercontent.com/xena-studios/mostwanted/main/server/panel/$f"
   done
   echo "APP_ENCRYPTION_KEY=$(openssl rand -hex 16)" | sudo tee .env
   sudo chmod 600 .env
   ```
   Copy the key it printed into 1Password now: without it, a backup of the panel can't be read.
2. Start it:
   ```sh
   sudo docker compose up -d
   sudo docker compose ps          # web, db and cache: running
   ```
3. Make the admin account **before** the panel is public. From your PC, in a second terminal, tunnel to it:
   ```sh
   ssh -N -L 8000:127.0.0.1:8000 YOU@VPS_IP
   ```
   Open `http://localhost:8000`, **Get Started**, and make your account. Under **Application Settings**: **Application URL** `https://panel.mostwantedmc.com` (no slash at the end), **Enable Registration** off. Then close the tunnel.
4. HTTPS, with Caddy:
   ```sh
   sudo apt-get install -y caddy
   sudo curl -fsSL -o /etc/caddy/Caddyfile https://raw.githubusercontent.com/xena-studios/mostwanted/main/server/panel/Caddyfile
   sudo systemctl reload caddy
   ```
   Open **https://panel.mostwantedmc.com** and sign in. Under your **Account**, add a **passkey** and an authenticator app.
5. **Admin → Nodes →** the **Integrated Node** the panel made:
   - **General:** **Memory** 15360 MiB and **Disk** 200 GiB (what servers may be given; the rest is the system's), **SFTP Host** `VPS_IP`. Save.
   - **Overview:** **Wings Version** shows a number.
   - **Allocations → Create:** IP `0.0.0.0`, IP Alias `VPS_IP`, ports `25565` and `8443`.
6. Check the servers' network exists, with the address step 4 needs:
   ```sh
   sudo docker network inspect calagopus_nw --format '{{(index .IPAM.Config 0).Gateway}}'   # 172.18.0.1
   ```

## 4. The database (10 min)

Calagopus runs PostgreSQL itself, through its **database agent**: a database of the game server's own, in its own container, with dumps in the server's backups and an explorer in the panel.

1. In the panel, **Admin → Database Agent Hosts → Create**:
   - **Name** `MostWanted node`, **URL** `http://host.docker.internal:8090`.
   - **PostgreSQL** enabled, **Public Host** `172.18.0.1`, **Public Port** `5432` (where the servers reach it). MariaDB, MongoDB and Redis off.
   - Save, then **Configuration → Reveal Configuration**, and copy the **token** from it.
2. On the VPS, the agent:
   ```sh
   sudo mkdir -p /opt/calagopus-db-agent && cd /opt/calagopus-db-agent
   sudo curl -fsSLO https://raw.githubusercontent.com/xena-studios/mostwanted/main/server/panel/db-agent/compose.yml
   sudo curl -fsSL -o config.yml https://raw.githubusercontent.com/xena-studios/mostwanted/main/server/panel/db-agent/config.yml.example
   sudo nano config.yml            # PASTE_THE_TOKEN → the token
   sudo chmod 600 config.yml
   sudo docker compose up -d
   ```
   Its API listens on `172.17.0.1:8090` and PostgreSQL on `172.18.0.1:5432`: Docker's own bridges, nothing public.
3. Back in the panel, **Configuration → Verify Connection**: it should say connected.
4. **Admin → Database Agent Templates → Import** [`server/panel/db-agent/postgres-template.yml`](../server/panel/db-agent/postgres-template.yml) (download it from GitHub first): PostgreSQL 18, 1 GB, slow queries in its log.
5. **Admin → Locations →** the **Integrated Location → Database Agent Hosts → Add** the host, so the node's servers can use it.

## 5. The eggs (2 min)

1. **Admin → Nests → Create:** name `MostWanted`.
2. **Admin → Egg Repositories → Create:** name `MostWanted`, **Git Repository** `https://github.com/xena-studios/mostwanted`, **Default Nest** MostWanted, **Automatic Sync** on.
3. On its **Eggs** tab (give the first sync a minute), install both: **MostWanted** (the game server) and **MostWanted API** (the API and Discord bot).

Each installs the latest build of `main` from the [`server` release](https://github.com/xena-studios/mostwanted/releases/tag/server). When an egg changes in the repo, the panel syncs it overnight and shows **Update Available** on **Admin → Egg Library**: **Update** there, and the servers use it at their next start or reinstall. Nothing to re-import, ever.

## 6. Cloudflare for the API (10 min)

In the dashboard for **mostwantedmc.com**. Moving from Raptor, all of this is already done: skip to step 7.

1. **SSL/TLS → Origin Server → Create Certificate.** "Generate private key and CSR with Cloudflare", RSA (2048), hostnames `api.mostwantedmc.com`, validity 15 years, format **PEM**. Keep the page open: the **private key is shown once**.
2. **SSL/TLS → Overview → Full (strict).** (The website is a Worker, so this only changes how Cloudflare talks to the VPS.)
3. **SSL/TLS → Origin Server → Authenticated Origin Pulls: On**, the **global** setting for the whole zone. Without it Cloudflare sends no client certificate, the API refuses the connection, and visitors get a 520.
4. **DNS → Add record:** `A`, name `api`, IPv4 `VPS_IP`, **Proxied** (orange cloud).
5. **Rules → Origin Rules → Create rule:** "api to 8443", when **Hostname equals `api.mostwantedmc.com`**, **Destination Port → Rewrite to 8443**. (443 is Caddy's, for the panel.)
6. **Caching → Cache Rules → Create rule:** "api stats", when **Hostname equals `api.mostwantedmc.com`** and **URI Path starts with `/v1/players/`** or **starts with `/v1/leaderboards/`**, **Eligible for cache**, Edge TTL **Use cache-control header if present**. Without it Cloudflare caches nothing from the API, and every page view reaches the VPS. Check it with `curl -sI https://api.mostwantedmc.com/v1/leaderboards/escapes | grep -i cf-cache-status` twice: `HIT` the second time. Still `DYNAMIC`? **Caching → Configuration → Development Mode** must be off.

About step 3: the certificate Cloudflare shows the VPS is the same for **every** Cloudflare customer, so it proves a request came through Cloudflare, not through *our* zone. Closing that needs our own client certificate uploaded to the zone (Cloudflare's API only: "zone-level Authenticated Origin Pulls with your own certificate"); then `TLS_CLIENT_CA` points at our CA instead of Cloudflare's. Fine to launch without, worth doing after.

## 7. The game server (15 min)

1. **Admin → Servers → Create:**
   - **Name** MostWanted, **Owner** you, **Node** Integrated Node.
   - **Nest** MostWanted, **Egg** MostWanted, **Docker Image** Java 25.
   - **Memory 11776 MiB** (11.5 GB: Java gets 10 GB of it, the rest is Java's own overhead; `start.sh` works it out), **CPU** 0 (no limit), **Disk** 100 GiB.
   - **Primary Allocation** `25565`.
   - **Timezone** America/New_York.
   - **Feature Limits:** **Databases** 1, **Backups** 40, **Schedules** 10 (the default 5 backups is a single week).
   - **Variables:** leave them for now. Create.
2. Open it (**View in Client Area**). The install runs: Leaf, every plugin we use (pinned, checksums checked), our configs and the core plugin.
3. **Databases → Managed Databases → Create:** name `mostwanted`, template **MostWanted PostgreSQL**. Open it, **Databases → Create** `mostwanted` with **Create a user for this database** on. Copy the **user** and **password** it shows once into 1Password.
4. Moving from Raptor: right-click the `mostwanted` database → **Import**, and pick `mostwanted.sql` from step 0.
5. **Startup:**
   - **Database host** `172.18.0.1`, **port** `5432`, **name** `mostwanted`, **user** and **password** from step 3.
   - **Tebex secret key** stays `none` until the store is set up (step 11).
   - **Join through Minehut** is `true`. Until Minehut is set up (step 10), set it to `false` to join the server's own address. Set it back to `true` once Minehut is: off, anyone could join around Minehut, and Minehut's players couldn't join.
6. **Settings:** **Auto-Kill** on, after 120 seconds: a big world gets time to finish saving, and a hung server still stops.
7. Moving from Raptor with a world: **Files → Upload** the archive from step 0, and **Decompress** it in place.
8. **Start** it and accept the EULA when the panel asks. Watch the console for `Done (`. Then, once, on a new database (not one imported from Raptor):
   - paste the `lp` lines from [`server/setup/luckperms.txt`](../server/setup/luckperms.txt) into the console (store ranks included),
   - `lp user <your name> parent add owner`,
   - `whitelist add <your name>`: the whitelist is on until launch day ([step 12](#12-open-the-doors)).

Configs come from the repo: change them there, not in the file manager, or the next update puts them back. The three with settings filled in (`plugins/*/config.yml`, LibertyBans' `sql.yml`) are written fresh from their `.tmpl` at every start.

## 8. The API and Discord bot (10 min)

1. **Admin → Servers → Create:** **Name** MostWanted API, **Egg** MostWanted API, **Docker Image** Java 25, **Memory 512 MiB** (Java gets 256 MB), **Disk** 5 GiB, **Primary Allocation** `8443`, **Timezone** America/New_York, **Backups** 10, **Schedules** 2.
2. **Startup:**
   - **Database host, port, name, user** and **password**: the game server's, from step 7. The API shares its database.
   - **Discord bot token**: on your PC in `~/.config/mostwanted/discord.env`.
   - **Discord client secret**: from step 9.
   - The rest are already right for mostwantedmc.com.
3. **Files:** in the `tls` folder, **New file** `origin.pem` with the Origin Certificate from step 6.1 (or Raptor's, step 0), and `origin.key` with its private key. Cloudflare's pull CA is already there: the install downloads it.
4. **Start** it. It logs `https with client certificates` and `API listening`; a bad file or a key that doesn't match the certificate stops it with the reason ([backend/README.md](../backend/README.md#https-and-authenticated-origin-pulls)).

If the database password is ever reset, put the new one on **both** servers' Startup tabs.

## 9. Discord (5 min)

In the [developer portal](https://discord.com/developers/applications), the **MostWanted** app:

1. **OAuth2 → Redirects:** add `https://api.mostwantedmc.com/v1/auth/discord/callback`.
2. **OAuth2 → Client Secret → Reset Secret**, and put it on the API's **Startup** tab as **Discord client secret**, then restart the API.
3. **General Information:** Terms of Service `https://mostwantedmc.com/terms`, Privacy Policy `https://mostwantedmc.com/privacy`.

## 10. Minehut (5 min)

In the [Minehut panel](https://minehut.com), on our server ([tech](04-tech.md#how-players-connect)):

1. Plan: **External**.
2. External Server Settings: IP `VPS_IP`, proxy type **Other**, DNS record type **Port**, port **25565**, TCPShield **Not Configured**.
3. Back in our panel, the game server's **Join through Minehut** to `true`, and restart.
4. **`play.mostwantedmc.com`**, the address players type: point it at **Minehut**, not the VPS (Minehut's custom-domain settings say which record; in Cloudflare it's **DNS only**). Pointed at the VPS, the server refuses the connection, since it only takes players who come through Minehut.

The server speaks Minehut's PROXY protocol and checks logins with Minehut, so direct connections can't join. Ask Minehut for their IP ranges (so netcup's firewall can limit 25565 to them) and how Bedrock players are marked.

## 11. Tebex

Follow [store: setting up Tebex](11-store.md#setting-up-tebex-whats-left-for-you): the webstore, the packages, and the **public token** as the `TEBEX_PUBLIC_TOKEN` repository variable (GitHub → Settings → Secrets and variables → Actions → Variables). Delivery in game is built into core: put the game server's Tebex secret key on its **Startup** tab as **Tebex secret key**, and restart.

## 12. Open the doors

1. **Website**, in a pull request, in [`website/src/site.ts`](../website/src/site.ts):
   - `open: true` turns on sign-in and accounts, player pages, the leaderboards, Support and Apply. **On since 2026-10-28**, ahead of the game, so people can sign up and apply first. "Sign in with Discord" shows itself once the API has its client secret (step 9).
   - `gameOpen: true` once the game is open to everyone: the header then shows live whether the server is up and how many are playing (`/v1/status`), instead of "Opening soon".
   - `discord`: the permanent invite.

   Merging deploys it (the website workflow), with the Tebex token from step 11.
2. **Game**, in a pull request: `white-list=false` and `enforce-whitelist=false` in [`server/files/server.properties`](../server/files/server.properties). Once it's merged and the server release has run, **Reinstall** the game server and start it. (`whitelist off` in the console works at once, but the next update would turn it back on without the PR.)

## 13. Smoke test

From your PC:

- [ ] `curl -sS https://api.mostwantedmc.com/health` → `{"status":"ok","database":"up","discord":"up"}`
- [ ] `curl -sS https://api.mostwantedmc.com/v1/leaderboards/escapes` → JSON
- [ ] The origin can't be reached around Cloudflare: `curl -k --max-time 10 --resolve api.mostwantedmc.com:8443:VPS_IP https://api.mostwantedmc.com:8443/health` fails.
- [ ] PostgreSQL isn't reachable from outside: `nc -vz -w 5 VPS_IP 5432` times out.
- [ ] Join through Minehut on **Java** and on **Bedrock**; walk around, `/duty` if you're a guard, check the scoreboard.
- [ ] `/website` in game, then sign in at mostwantedmc.com/login with the code; link Discord from /account; you get the **Linked** role in Discord.
- [ ] `/profile <you>` in Discord shows your card.
- [ ] Your player page and the leaderboards on the website show data.
- [ ] A **test purchase** (Tebex's test payments, or a real cheap one you refund): the command runs in the console and the rank shows in game.

## Updating

- **The game and the API:** every change merged to `main` is built into the [`server` release](https://github.com/xena-studios/mostwanted/releases/tag/server) a few minutes later. On the server, **Settings → Reinstall Server** (the world, plugin data and settings stay), then start it. The install's log names the build (`MostWanted build <commit>`). Take a backup first if the change is big.
- **The eggs:** **Admin → Egg Library → Update** when it says an update is available (step 5).
- **The panel and the database agent:** `cd /opt/calagopus && sudo docker compose pull && sudo docker compose up -d`, the same in `/opt/calagopus-db-agent`. Calagopus's [releases](https://calagopus.com/docs/releases) say what changed. Servers keep running while the panel restarts.
- **Ubuntu:** security updates install themselves; `sudo apt-get full-upgrade` and a reboot now and then, at a quiet hour. Servers start again by themselves.

## Backups

1. **Where:** a Cloudflare **R2** bucket, `mostwanted-backups` (R2 → Create bucket, then **Manage API tokens → Create**, Object Read & Write on that bucket only). In the panel, **Admin → Backup Configurations → Create**, **Backup Disk S3**: the token's access key and secret, the bucket, region `auto`, the endpoint `https://<account id>.r2.cloudflarestorage.com`. **Test** it on the node, save, and pick it on the **Integrated Node** (General).
2. **When:** on the game server, **Backups → Create Backup Group** `Daily`, with **Keep daily** 7 and **Keep weekly** 4. Then **Schedules → Create** `Nightly backup`, cron daily at **4:00**, with these steps:

   | Step | Settings |
   | --- | --- |
   | Send Command | `save-off` |
   | Send Command | `save-all flush` |
   | Wait for Console Line | `Saved the game`, timeout 300000 |
   | Create Backup | group Daily, **Wait for Completion** on, **Ignore Failure** on |
   | Send Command | `save-on` |
   | Create Database Backup | `mostwanted`, group Daily, **Wait for Completion** on |

   Minecraft stops writing while the files are copied, so the backup never catches a half-written region file, and always saves again after, even if the backup fails. The database dump is the whole game's data: stats, ranks, bans, the website's accounts and tickets.
3. The same on the API, without the save steps: a **Create Backup** at 4:30 (its `tls/`).
4. **Run Now** once on each, and check the backups arrive in R2.
5. **The panel itself:** its database and `.env`. Once a week, and before panel updates:
   ```sh
   cd /opt/calagopus && sudo docker compose exec -T db pg_dump -U panel -d panel | gzip > ~/panel-$(date +%F).sql.gz
   ```
   and copy it off the machine (`scp`). The encryption key is in 1Password (step 3).
6. **The whole VPS:** a netcup snapshot (Server Control Panel → Snapshots) before big changes, as a last resort. It's kept on netcup's side, so it's no substitute for R2.
7. **Test a restore** before launch, not after: **Browse** a backup and download one file; restore the database dump into a scratch managed database.

## Alerts

Schedules can call a Discord webhook. Make one in a staff-only channel (channel settings → Integrations → Webhooks), then on the game server:

- **Schedules → Create** `Crash alert`, trigger **Crash**, one step **HTTP Request**: `POST` to the webhook URL, header `Content-Type: application/json`, body `{"content":"MostWanted crashed. Check the console."}`.
- `Memory alert`, trigger **Resource Usage**: Memory Usage above 95% for 600 seconds, the same kind of step: the heap is too small, or something leaks.
- `Backup failed`, trigger **Backup Status: Failed**, and another for **Database Backup Status: Failed**.

The same **Crash** alert on the API. For the restart, add `Daily restart`, cron daily at **5:30**:

```
Send Command   say Restarting in 5 minutes
Sleep          240000
Send Command   say Restarting in 1 minute
Sleep          60000
Send Power Signal   Restart
```

## Rollback

- **A bad update:** on **Backups**, restore last night's backup (or the one you took before updating). Then revert the change on `main`, so the next update doesn't bring it back.
- **The database:** stop the game and the API, then on the managed database's **Backups** tab restore a dump into `mostwanted`. Restoring locks the database and drops connections; start the servers again after.
- **The API misbehaving:** stop it. The website shows its "can't reach" states; set `open: false` in `site.ts` again if it should look closed. A 526 from Cloudflare means the certificate is wrong (step 6.1, `tls/origin.*`); a 525, or a failed handshake after turning on Authenticated Origin Pulls, means the pull CA is wrong: clear **Cloudflare pull CA** on the API's Startup tab to check, then put it back.
- **The whole machine:** a netcup snapshot, or a fresh VPS following this page from step 1, with the R2 backups (files and database dumps) restored onto the new servers. A new IP goes in Cloudflare's `api` and `panel` records and in Minehut.
