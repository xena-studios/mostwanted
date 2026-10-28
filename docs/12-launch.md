# Launch

From "bought the VPS" to "players can join and the API answers", in order. Each step is copy-paste; the scripts it runs are safe to run again, so if something fails, fix it and run the same step again. About an hour, most of it waiting for installs.

Why the setup looks like this: [tech](04-tech.md), [server stack](07-server-stack.md), [the API](08-website.md#the-api). The scripts: [`server/setup/`](../server/setup/).

**Values you'll fill in along the way** (keep them in a note on your PC, not in the repo):

| | Where it comes from |
| --- | --- |
| `VPS_IP` | OVH's email / control panel |
| `GW` | The address Raptor's servers reach the VPS on, printed by `provision.sh` (like `172.18.0.1`) |
| `GAME_ID`, `API_ID` | The two Raptor servers' IDs (the app shows them; `sudo ls /var/lib/raptor/volumes` lists them) |

Secrets (the database password, the bot token, the OAuth secret, the TLS key, Tebex's secret key) only ever go in `.env` files and `tls/` on the VPS, edited with `sudoedit`. Never in the repo, a commit, a chat or a screenshot.

## 1. The VPS (10 min)

1. In OVH's panel, install **Ubuntu 24.04** (the release Raptor supports) and add your **SSH public key** (or add it after with `ssh-copy-id ubuntu@VPS_IP` from your PC).
2. Log in and update:
   ```sh
   ssh ubuntu@VPS_IP
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

## 3. Provision (15 min)

```sh
git clone https://github.com/xena-studios/mostwanted /tmp/mostwanted
sudo install -D -m 600 /tmp/mostwanted/server/.env.example /etc/mostwanted/server.env
sudo /tmp/mostwanted/server/setup/provision.sh --dry-run    # read what it will do
sudo /tmp/mostwanted/server/setup/provision.sh
```

It installs Java 25 and PostgreSQL 18, makes the `mostwanted` database and user (a password is generated into `/etc/mostwanted/server.env`), turns on automatic security updates and fail2ban, switches SSH to keys only (only if your account has a key; otherwise it says how), and installs the nightly database dump. Read its **Warnings** at the end.

It prints `Raptor servers connect with DB_HOST=…`: that's `GW`. Put it in the server's `.env`:

```sh
sudoedit /etc/mostwanted/server.env      # DB_HOST=GW
```

**Check SSH still works** from a second terminal before closing the first.

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

Nothing comes in except SSH, the game (25565), the API (8443, **Cloudflare's addresses only**), and PostgreSQL from Raptor's servers. Cloudflare's list is refreshed daily (`systemctl list-timers 'mostwanted*'`). Check SSH from a second terminal again.

The game port is open to anyone until Minehut gives us their addresses. **TODO:** when they do, put them in `GAME_ALLOW` in `/etc/mostwanted/firewall.env` and run `firewall.sh` again. Raptor's SFTP (2022) stays closed unless you set `SFTP_PORT=2022` there.

## 5. The game server (10 min)

In the Raptor app, **Deploy server**:

- **Game:** Minecraft Paper (any Java Minecraft recipe works: `install.sh` replaces the jar). **Runtime: Java 25.**
- **Name** MostWanted, **port** 25565, **memory 9.5 GB** (`start.sh` gives Java 8 GB; the rest is Java's own overhead, and Raptor kills a server that goes over).
- **Startup** tab: the command `bash start.sh`. If the recipe won't let you change it, make it an org game with that command (Raptor's docs: eggs), or ask in Raptor's Discord.
- Stop it after the first start: our files go in next.

```sh
GAME=/var/lib/raptor/volumes/GAME_ID
cd /srv/mostwanted/repo
sudo install -m 600 /etc/mostwanted/server.env $GAME/.env
sudo server/install.sh $GAME
sudo cp core/build/libs/core-*.jar $GAME/plugins/
echo eula=true | sudo tee $GAME/eula.txt      # https://aka.ms/MinecraftEULA
sudo chown -R --reference=$GAME $GAME          # Raptor's container user owns its files
```

Start it in Raptor and watch the console for `Done (`. Then, once, paste the `lp` lines from [`server/setup/luckperms.txt`](../server/setup/luckperms.txt) into the console (store ranks included), and `lp user <your name> parent add owner` for yourself.

The whitelist is on until launch day ([step 11](#11-open-the-doors)).

## 6. Cloudflare (10 min)

In the dashboard for **mostwantedmc.com**:

1. **SSL/TLS → Origin Server → Create Certificate.** "Generate private key and CSR with Cloudflare", RSA (2048), hostnames `api.mostwantedmc.com`, validity 15 years, format **PEM**. Keep the page open: the **private key is shown once**.
2. **SSL/TLS → Overview → Full (strict).** (The website is a Worker, so this only changes how Cloudflare talks to the VPS.)
3. **SSL/TLS → Origin Server → Authenticated Origin Pulls: On.**
4. **DNS → Add record:** `A`, name `api`, IPv4 `VPS_IP`, **Proxied** (orange cloud).
5. **Rules → Origin Rules → Create rule:** "api to 8443", when **Hostname equals `api.mostwantedmc.com`**, **Destination Port → Rewrite to 8443**. (Raptor gives servers ports from 1024 up, so the backend can't take 443 itself.)

About step 3: the certificate Cloudflare shows the VPS is the same for **every** Cloudflare customer, so it proves a request came through Cloudflare, not through *our* zone. Someone with their own Cloudflare zone pointed at our IP could still get through. Closing that needs our own client certificate uploaded to the zone (Cloudflare's API only, no dashboard button: "zone-level Authenticated Origin Pulls with your own certificate" in their docs); then `TLS_CLIENT_CA` points at our CA instead of Cloudflare's. Fine to launch without, worth doing after.

## 7. The backend (10 min)

In Raptor, **Deploy server**: a generic **Java** app, **Runtime: Java 25**, name **MostWanted API**, **port 8443**, **memory 512 MB** (`start.sh` gives Java 256 MB), startup `bash start.sh`. Stop it after the first start.

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
| `DB_HOST` | `GW` |
| `DB_PASSWORD` | the same as in `/etc/mostwanted/server.env` (`sudo grep DB_PASSWORD /etc/mostwanted/server.env`) |
| `DISCORD_TOKEN` | the bot token (on your PC in `~/.config/mostwanted/discord.env`) |
| `DISCORD_CLIENT_SECRET` | from step 8 |
| the rest | as in [`backend/.env.example`](../backend/.env.example): `API_PORT=8443`, the three `TLS_` paths, `COOKIE_DOMAIN=.mostwantedmc.com` |

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

Follow [store: setting up Tebex](11-store.md#setting-up-tebex-whats-left-for-you): the webstore, the packages, and the **public token** as the `TEBEX_PUBLIC_TOKEN` repository variable (GitHub → Settings → Secrets and variables → Actions → Variables). Delivery in game needs the Tebex plugin, which is waiting for your approval ([11-store.md](11-store.md), step 5); its secret key goes in the game server's `.env`, never here.

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
- [ ] `sudo ufw status` and `sudo nft list table inet mostwanted` look right; `systemctl list-timers 'mostwanted*'` shows the firewall refresh and the database dump.
- [ ] `sudo raptor doctor` is happy.

## Backups

- **The worlds and the servers' files:** Raptor backs every server up daily (4-5 AM, the node's time zone). In Raptor, **Node → Backups**, add a destination **off the VPS** (Raptor Backup Storage, or any S3: Cloudflare R2, OVH Object Storage, Backblaze) and set both servers to back up there as well as to the node's disk. **Back up now** once and check it arrives.
- **The database:** `mostwanted-backup-db` dumps PostgreSQL every night at 03:30 into `/var/backups/mostwanted` (root only), checks each dump, and keeps 14 days (`/etc/mostwanted/backup.env`). To take those dumps off the VPS too, point `COPY_TO` at a folder in the backend's server: it copies the newest there as `mostwanted-latest.dump`, and Raptor's 4 AM backup takes it along.
  ```sh
  sudo install -d /var/lib/raptor/volumes/API_ID/db-backups
  sudo chown --reference=/var/lib/raptor/volumes/API_ID /var/lib/raptor/volumes/API_ID/db-backups
  sudoedit /etc/mostwanted/backup.env    # COPY_TO="/var/lib/raptor/volumes/API_ID/db-backups"
  sudo mostwanted-backup-db              # once now, to check
  ```
- **The whole VPS:** OVH's daily backup of the VPS, as a last resort.
- **Test a restore** before launch, not after: restore a few files from a Raptor backup into `.restore/`, and restore last night's dump (below) on a quiet evening.

## Rollback

- **A bad plugin or config update:** check out the last good commit (`sudo -u mostwanted git -C /srv/mostwanted/repo checkout <commit>`), run `install.sh` and copy the core jar as in step 5, restart. Or in Raptor, **Backups → Restore** the files from before the update.
- **The database:** stop the game and the backend in Raptor, then
  ```sh
  sudo ls -t /var/backups/mostwanted | head
  sudo mostwanted-backup-db --restore /var/backups/mostwanted/mostwanted-<when>.dump
  ```
  It dumps what's there now first (so a wrong pick can be undone) and restores in one transaction.
- **The API misbehaving:** stop the backend in Raptor. The website shows its "can't reach" states; set `open: false` in `site.ts` again if it should look closed. A 526 from Cloudflare means the certificate is wrong (step 6.1, `tls/origin.*`); a 525 or a failed handshake after turning on Authenticated Origin Pulls means the pull CA file is wrong: remove `TLS_CLIENT_CA` from the backend's `.env` to check, then put it back.
- **The firewall:** `sudo ufw disable` and `sudo nft delete table inet mostwanted` turn it all off (SSH included in what's then open); run `firewall.sh` to put it back.
- **Everything:** OVH's VPS backup, or a new VPS following this page, with the Raptor backups restored onto it and the latest dump restored with `--restore`. Update the IP in Minehut and in Cloudflare's `api` record.
