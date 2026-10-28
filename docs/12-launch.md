# Launch

From "bought the VPS" to "players can join and the API answers", in order. Each step is copy-paste; the scripts it runs are safe to run again, so if something fails, fix it and run the same step again. About an hour, most of it waiting for installs.

Why the setup looks like this: [tech](04-tech.md), [server stack](07-server-stack.md), [the API](08-website.md#the-api). The scripts: [`server/setup/`](../server/setup/).

**Values you'll fill in along the way** (keep them in a note on your PC, not in the repo):

| | Where it comes from |
| --- | --- |
| `VPS_IP` | netcup's Server Control Panel (the server's page, Network) |
| `YOU` | The user you made when installing Ubuntu (step 1) |
| The database's host, name, user and password | The game server's **Databases** tab in Raptor (step 5) |
| `GAME_ID`, `API_ID` | The two Raptor servers' IDs (the app shows them; `sudo ls /var/lib/raptor/volumes` lists them) |

Secrets (the database password, the bot token, the OAuth secret, the TLS key, Tebex's secret key) only ever go in `.env` files and `tls/` on the VPS (edited with `sudoedit`, or in Raptor's file editor), and in Raptor. Never in the repo, a commit, a chat or a screenshot.

## 1. The VPS (10 min)

The server is a netcup **RS 2000** in **Manassas, Virginia** (US East): 8 dedicated cores, 16 GB RAM, 256 GB NVMe. Ordered 2026-10-28.

1. In netcup's Server Control Panel, **Media → Images**: **Ubuntu 24.04 UEFI amd64**, Minimal (the release Raptor supports; not 26.04, not "cloudimg"), one large partition, locale **en_US.UTF-8**, any time zone (it's set below), **create an additional user** with sudo and your **SSH public key** (or add the key after with `ssh-copy-id YOU@VPS_IP` from your PC). No custom script.
2. Leave netcup's own firewall with no policies: `firewall.sh` (step 4) does it on the server, and two firewalls disagreeing is how you get locked out.
3. Log in and update:
   ```sh
   ssh YOU@VPS_IP
   sudo apt-get update && sudo apt-get full-upgrade -y
   sudo timedatectl set-timezone America/New_York   # backups then run at night for US players
   sudo reboot
   ```

## 2. Raptor (5 min)

Raptor wants a freshly installed machine, so it goes first. In the [Raptor app](https://app.raptorpanel.net): **Nodes → Connect node → Make a join token**, sign with your passkey, and run the command it shows (it holds the token, so copy it from there):

```sh
curl -fsSL https://get.raptorpanel.net | sudo bash -s -- -token rpt_join_…
```

Take the default **image** storage. When the app says the node is linked, carry on. (It also needs **Settings → Alerts** set up to tell you when a server crashes: do that whenever.)

## 3. Provision (10 min)

Raptor runs the database (step 5), so the machine itself only needs Java (to build), the security settings, and the repo:

```sh
git clone https://github.com/xena-studios/mostwanted /tmp/mostwanted
sudo install -D -m 600 /tmp/mostwanted/server/.env.example /etc/mostwanted/server.env
sudo /tmp/mostwanted/server/setup/provision.sh --no-postgres --dry-run    # read what it will do
sudo /tmp/mostwanted/server/setup/provision.sh --no-postgres
```

It installs Java 25, turns on automatic security updates and fail2ban, and switches SSH to keys only (only if your account has a key; otherwise it says how). Read its **Warnings** at the end. **Check SSH still works** from a second terminal before closing the first.

Then the checkout everything is built from, owned by the `mostwanted` user:

```sh
sudo -u mostwanted git clone https://github.com/xena-studios/mostwanted /srv/mostwanted/repo
rm -rf /tmp/mostwanted
sudo -u mostwanted -H bash -c 'cd /srv/mostwanted/repo && ./gradlew :core:build :backend:build -x test'
```

(CI already ran the tests on `main`.)

## 4. Firewall (5 min)

```sh
sudo /srv/mostwanted/repo/server/setup/firewall.sh --dry-run
sudo /srv/mostwanted/repo/server/setup/firewall.sh
```

Nothing comes in except SSH, the game (25565) and the API (8443, **Cloudflare's addresses only**); the database is only on Raptor's network, behind Raptor's own firewall. Cloudflare's list is refreshed daily (`systemctl list-timers 'mostwanted*'`). Check SSH from a second terminal again.

The game port is open to anyone until Minehut gives us their addresses. **TODO:** when they do, put them in `GAME_ALLOW` in `/etc/mostwanted/firewall.env` and run `firewall.sh` again. Raptor's SFTP (2022) stays closed unless you set `SFTP_PORT=2022` there.

## 5. Raptor: the network, the database and the game server (20 min)

Everything here is in the [Raptor app](https://app.raptorpanel.net).

**The network.** On the node's **Networks** tab, **New network**: name it **MostWanted**. The game, the API and the database go on it, apart from anything else ever put on the node.

**The database.** On the node's **Databases** tab, **New instance**:
- **Engine** PostgreSQL (the recommended version), **name** MostWanted, **memory** 1 GB, **network** MostWanted.
- **Advanced:** **time zone** America/New_York, and under **Engine settings** `log_min_duration_statement = 500ms` (slow queries show in its log).

Raptor's backups of the game server hold this database (below), and its alerts cover it.

**The eggs.** On the org's **Eggs** page, **Import an egg** from these two links, read what they run, and import them:
- `https://github.com/xena-studios/mostwanted/blob/main/server/raptor/egg-mostwanted.json`: the game server (Java 25, `bash start.sh`; its install does nothing, so **Reinstall** can't replace Leaf).
- `https://github.com/xena-studios/mostwanted/blob/main/server/raptor/egg-mostwanted-api.json`: the API and bot (step 7).

**The game server.** **Deploy server**:
- **Game** MostWanted (your org's), **runtime** Java 25, **network** MostWanted.
- **Name** MostWanted, **port** 25565, **memory 11.5 GB** (`start.sh` gives Java 10 GB; the rest is Java's own overhead, and Raptor kills a server that goes over). Accept the EULA when it asks: Raptor writes `eula.txt`.
- Once it's made, before starting it: its **Databases** tab, **New database** named `mostwanted`. Copy the **Host**, **Database**, **User** and **password** it shows into the server's settings:
  ```sh
  sudoedit /etc/mostwanted/server.env
  # DB_HOST=<Host, like mostwanted.db.raptor>  DB_PORT=5432
  # DB_NAME=<Database, like s3e4f5a6b7c8d_mostwanted>  DB_USER=<the same>  DB_PASSWORD=<the password>
  ```
  LuckPerms, LibertyBans and the game plugin all use this one database. (Raptor also gives the server `DB_*` variables; ours come from this file, which `install.sh` writes into the configs.)
- **Network** tab: **Shown address** `play.mostwantedmc.com`.
- **Settings** tab: **Force stop after** 120 seconds, so a big world always finishes saving.

Then our files go in (`GAME_ID` is the server's ID, on its **Settings → Details**):

```sh
GAME=/var/lib/raptor/volumes/GAME_ID
cd /srv/mostwanted/repo
sudo install -m 600 /etc/mostwanted/server.env $GAME/.env
sudo server/install.sh $GAME
sudo cp core/build/libs/core-*.jar $GAME/plugins/
sudo chown -R --reference=$GAME $GAME          # Raptor's container user owns its files
```

Start it in Raptor and watch the console for `Done (`. Then, once, paste the `lp` lines from [`server/setup/luckperms.txt`](../server/setup/luckperms.txt) into the console (store ranks included), and `lp user <your name> parent add owner` for yourself.

The whitelist is on until launch day ([step 11](#11-open-the-doors)).

**A nightly restart.** On its **Schedules** tab, **New schedule** at 5:30 AM, America/New_York, every day:

```
Command   say Restarting in 5 minutes
Wait      4 minutes
Command   say Restarting in 1 minute
Wait      1 minute
Power     restart
```

(The **Daily backup** schedule is already there, between 4 and 5 AM.)

## 6. Cloudflare (10 min)

In the dashboard for **mostwantedmc.com**:

1. **SSL/TLS → Origin Server → Create Certificate.** "Generate private key and CSR with Cloudflare", RSA (2048), hostnames `api.mostwantedmc.com`, validity 15 years, format **PEM**. Keep the page open: the **private key is shown once**.
2. **SSL/TLS → Overview → Full (strict).** (The website is a Worker, so this only changes how Cloudflare talks to the VPS.)
3. **SSL/TLS → Origin Server → Authenticated Origin Pulls: On.**
4. **DNS → Add record:** `A`, name `api`, IPv4 `VPS_IP`, **Proxied** (orange cloud).
5. **Rules → Origin Rules → Create rule:** "api to 8443", when **Hostname equals `api.mostwantedmc.com`**, **Destination Port → Rewrite to 8443**. (Raptor gives servers ports from 1024 up, so the backend can't take 443 itself.)
6. **Caching → Cache Rules → Create rule:** "api stats", when **Hostname equals `api.mostwantedmc.com`** and **URI Path starts with `/v1/players/`** or **starts with `/v1/leaderboards/`**, **Eligible for cache**, Edge TTL **Use cache-control header if present**. Without it Cloudflare caches nothing from the API (it only caches file extensions by default), and every page view reaches the VPS.

About step 3: the certificate Cloudflare shows the VPS is the same for **every** Cloudflare customer, so it proves a request came through Cloudflare, not through *our* zone. Someone with their own Cloudflare zone pointed at our IP could still get through. Closing that needs our own client certificate uploaded to the zone (Cloudflare's API only, no dashboard button: "zone-level Authenticated Origin Pulls with your own certificate" in their docs); then `TLS_CLIENT_CA` points at our CA instead of Cloudflare's. Fine to launch without, worth doing after.

## 7. The backend (10 min)

In Raptor, **Deploy server**: **Game** MostWanted API (your org's), **runtime** Java 25, **network** MostWanted, **name** MostWanted API, **port 8443**, **memory 512 MB** (`start.sh` gives Java 256 MB).

Before starting it, on its **Databases** tab make a database named `api`. Nothing uses it: Raptor's database only answers servers that have a database on it, and this is how the API gets in. The API signs in to the **game's** database (step 5), with the game's user.

Its files, from the build in step 3 (or upload `backend.jar` and `backend/start.sh` on its **Files** tab, and write `.env` and the `tls/` files in the file editor):

```sh
API=/var/lib/raptor/volumes/API_ID
cd /srv/mostwanted/repo
sudo install -m 644 backend/build/libs/backend.jar backend/start.sh $API/
sudo install -m 600 backend/.env.example $API/.env
sudo install -d -m 700 $API/tls
sudoedit $API/tls/origin.pem           # paste the Origin Certificate from step 6.1
sudoedit $API/tls/origin.key           # paste its private key
sudo curl -fsSL -o $API/tls/cloudflare-origin-pull-ca.pem \
  https://developers.cloudflare.com/ssl/static/authenticated_origin_pull_ca.pem
sudo chmod 600 $API/tls/*
sudoedit $API/.env
sudo chown -R --reference=$API $API
```

In the backend's `.env`:

| Setting | Value |
| --- | --- |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | the **game's** database, as in `/etc/mostwanted/server.env` (`sudo grep ^DB_ /etc/mostwanted/server.env`) |
| `DISCORD_TOKEN` | the bot token (on your PC in `~/.config/mostwanted/discord.env`) |
| `DISCORD_CLIENT_SECRET` | from step 8 |
| the rest | as in [`backend/.env.example`](../backend/.env.example): `API_PORT=8443`, the three `TLS_` paths, `COOKIE_DOMAIN=.mostwantedmc.com` |

If the database's password is ever reset in Raptor, put the new one in both `/etc/mostwanted/server.env` (then run `install.sh` again) and this `.env`.

The URL for the pull CA is the one on Cloudflare's [Authenticated Origin Pulls setup page](https://developers.cloudflare.com/ssl/origin-configuration/authenticated-origin-pull/set-up/zone-level/): check it's still the one they give before downloading. Start the server in Raptor. It logs `https with client certificates` and `API listening`; a bad path or a key that doesn't match the certificate stops it with the reason. All of it is explained in [backend/README.md](../backend/README.md#https-and-authenticated-origin-pulls).

## 8. Discord (5 min)

In the [developer portal](https://discord.com/developers/applications), the **MostWanted** app:

1. **OAuth2 → Redirects:** add `https://api.mostwantedmc.com/v1/auth/discord/callback`.
2. **OAuth2 → Client Secret → Reset Secret**, and put it in the backend's `.env` as `DISCORD_CLIENT_SECRET` (`sudoedit`), then restart the backend in Raptor.
3. **General Information:** Terms of Service `https://mostwantedmc.com/terms`, Privacy Policy `https://mostwantedmc.com/privacy`.

## 9. Minehut (5 min)

In the [Minehut panel](https://minehut.com), on our server ([tech](04-tech.md#how-players-connect)):

1. Plan: **External**.
2. External Server Settings: IP `VPS_IP`, proxy type **Other**, DNS record type **Port**, port **25565**, TCPShield **Not Configured**.

The server already speaks Minehut's PROXY protocol and checks logins with Minehut, so direct connections can't join anyway. Ask Minehut for their IP ranges for `GAME_ALLOW` (step 4), and how Bedrock players are marked.

## 10. Tebex

Follow [store: setting up Tebex](11-store.md#setting-up-tebex-whats-left-for-you): the webstore, the packages, and the **public token** as the `TEBEX_PUBLIC_TOKEN` repository variable (GitHub → Settings → Secrets and variables → Actions → Variables). Delivery in game is built into core: put the game server's Tebex secret key in its `.env` as `TEBEX_SECRET` ([11-store.md](11-store.md), step 5), never here.

## 11. Open the doors

1. **Website**, in a pull request: `open: true` and the permanent Discord invite in `discord` in [`website/src/site.ts`](../website/src/site.ts). Merging deploys it (the website workflow), with the Tebex token from step 10.
2. **Game**, in a pull request: `white-list=false` and `enforce-whitelist=false` in [`server/files/server.properties`](../server/files/server.properties); after merging, on the VPS:
   ```sh
   sudo -u mostwanted git -C /srv/mostwanted/repo pull
   sudo /srv/mostwanted/repo/server/install.sh /var/lib/raptor/volumes/GAME_ID
   sudo chown -R --reference=/var/lib/raptor/volumes/GAME_ID /var/lib/raptor/volumes/GAME_ID
   ```
   and restart it in Raptor. (`whitelist off` in the console works at once, but the next `install.sh` would turn it back on without the PR.)

## 12. Smoke test

From your PC:

- [ ] `curl -sS https://api.mostwantedmc.com/health` → `{"status":"ok","database":"up","discord":"up"}`
- [ ] `curl -sS https://api.mostwantedmc.com/v1/leaderboards/escapes` → JSON
- [ ] The origin can't be reached around Cloudflare: `curl -k --max-time 10 --resolve api.mostwantedmc.com:8443:VPS_IP https://api.mostwantedmc.com:8443/health` times out.
- [ ] Join through Minehut on **Java** and on **Bedrock**; walk around, `/duty` if you're a guard, check the scoreboard.
- [ ] `/website` in game, then sign in at mostwantedmc.com/login with the code; link Discord from /account; you get the **Linked** role in Discord.
- [ ] `/profile <you>` in Discord shows your card.
- [ ] Your player page and the leaderboards on the website show data.
- [ ] A **test purchase** (Tebex's test payments, or a real cheap one you refund): the command runs in the console and the rank shows in game.

On the VPS:

- [ ] `curl -sk https://127.0.0.1:8443/health` fails with a certificate error: the backend really wants Cloudflare's certificate.
- [ ] `sudo ufw status` and `sudo nft list table inet mostwanted` look right; `systemctl list-timers 'mostwanted*'` shows the firewall refresh.
- [ ] `sudo raptor doctor` is happy.

## Alerts

On **Settings → Alerts** (new orgs already tell their owners about crash loops, an offline node and low disk), add:

- **Crashed**, **Backup failed** and **Restore finished or failed**, on both servers.
- **Memory above 90% for 10 minutes** on the game server: the heap is too small, or something leaks.
- The database instance: **down or restarting**, and **connections above 80%**.
- A **team channel**: a Discord webhook into a staff-only channel, so the team sees them, not only you.

Then the bell's settings: how alerts reach **you** (email is on; Discord or ntfy on your phone are quicker).

## Backups

- **Where:** on the node's **Backups** tab, add **Raptor Backup Storage** (offsite, nothing to set up), or any S3 (Cloudflare R2, Backblaze), and **Test** it. On each server's **Backups** tab, back up to it **and** the node's disk (fast restores), keeping **Standard** (the last 3, a week of dailies, a month of weeklies).
- **What:** the **Daily backup** schedule (4–5 AM) backs up the game's files and, dumped just before, its **database**: the whole game, consistent. Minecraft is told to save first. The API's backup holds its `.env` and `tls/`.
- **Back up now** on both once, and check they arrive offsite.
- **The whole VPS:** a netcup snapshot (Server Control Panel → Snapshots) before big changes, as a last resort. It's kept on netcup's side, so it's no substitute for the offsite backups.
- **Test a restore** before launch, not after: on a backup, **Restore → a few files** into `.restore/`, and on the Databases tab, **Restore from a backup** on a quiet evening.

## Rollback

- **A bad plugin or config update:** check out the last good commit (`sudo -u mostwanted git -C /srv/mostwanted/repo checkout <commit>`), run `install.sh` and copy the core jar as in step 5, restart. Or in Raptor, **Backups → Restore** the files from before the update.
- **The database:** stop the game and the backend in Raptor, then on the game's **Databases** tab, **Restore from a backup** on `mostwanted`. Raptor exports what's there now first, so a wrong pick can be undone. **Restore everything** on a backup puts back the files and the database together.
- **The API misbehaving:** stop the backend in Raptor. The website shows its "can't reach" states; set `open: false` in `site.ts` again if it should look closed. A 526 from Cloudflare means the certificate is wrong (step 6.1, `tls/origin.*`); a 525 or a failed handshake after turning on Authenticated Origin Pulls means the pull CA file is wrong: remove `TLS_CLIENT_CA` from the backend's `.env` to check, then put it back.
- **The firewall:** `sudo ufw disable` and `sudo nft delete table inet mostwanted` turn it all off (SSH included in what's then open); run `firewall.sh` to put it back.
- **Everything:** a netcup snapshot, or a new server following this page, with the Raptor backups (files and database) restored onto it. Update the IP in Minehut and in Cloudflare's `api` record.
