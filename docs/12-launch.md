# Launch

From "bought the VPS" to "players can join and the API answers", in order. **Raptor runs everything**: the game server, the API and Discord bot, the database, backups, schedules, alerts and the firewall, all in the [Raptor app](https://app.raptorpanel.net). Nothing of ours runs on the machine outside it. About an hour, most of it waiting for installs.

Why the setup looks like this: [tech](04-tech.md), [server stack](07-server-stack.md), [the API](08-website.md#the-api). What Raptor installs: [`server/raptor/`](../server/raptor/) (the eggs), built from `main` by the [server release](../.github/workflows/server-release.yml) workflow.

**Values you'll fill in along the way** (keep them in a note on your PC, not in the repo):

| | Where it comes from |
| --- | --- |
| `VPS_IP` | netcup's Server Control Panel (the server's page, Network) |
| `YOU` | The user you made when installing Ubuntu (step 1) |

Secrets (the database password, the bot token, the OAuth secret, the TLS key, Tebex's secret key) only ever go in Raptor: a server's **Startup** tab, its **Databases** tab, or its files. Never in the repo, a commit, a chat or a screenshot.

## 1. The VPS (10 min)

The server is a netcup **RS 2000** in **Manassas, Virginia** (US East): 8 dedicated cores, 16 GB RAM, 256 GB NVMe. Ordered 2026-10-28.

1. In netcup's Server Control Panel, **Media → Images**: **Ubuntu 24.04 UEFI amd64**, Minimal (the release Raptor supports; not 26.04, not "cloudimg"), one large partition, locale **en_US.UTF-8**, **create an additional user** with sudo and your **SSH public key** (or add the key after with `ssh-copy-id YOU@VPS_IP` from your PC). No custom script.
2. Leave netcup's own firewall with no policies of yours: Raptor's firewall does it (step 3).
3. Log in and update:
   ```sh
   ssh YOU@VPS_IP
   sudo apt-get update && sudo apt-get full-upgrade -y
   sudo reboot
   ```
4. SSH by key only, once a key works (check from a second terminal before closing the first):
   ```sh
   echo "PasswordAuthentication no" | sudo tee /etc/ssh/sshd_config.d/10-keys-only.conf
   sudo systemctl reload ssh
   ```

## 2. Raptor (5 min)

Raptor wants a freshly installed machine, so it goes first. In the Raptor app: **Nodes → Connect node → Make a join token**, sign with your passkey, and run the command it shows (it holds the token, so copy it from there):

```sh
curl -fsSL https://get.raptorpanel.net | sudo bash -s -- -token rpt_join_…
```

Take the default **image** storage. When the app says the node is linked, carry on.

## 3. The node (5 min)

On the node's **System** tab:

- **Time zone** America/New_York: logs match the game's clock, and the 4–5 AM backups land when US players are asleep.
- **Keep the clock in sync:** on.
- **Firewall:** turn on **Block everything not listed**. Raptor opens SSH and each server's port itself. The API's port only lets Cloudflare in anyway: it refuses any connection without Cloudflare's client certificate (step 6).

Then the **Networks** tab: **New network** named **MostWanted**. The game, the API and the database go on it, apart from anything else ever put on the node.

And the **Databases** tab: **New instance**:
- **Engine** PostgreSQL (the recommended version), **name** MostWanted, **memory** 1 GB, **network** MostWanted.
- **Advanced:** **time zone** America/New_York, and under **Engine settings** `log_min_duration_statement = 500ms` (slow queries show in its log).

## 4. The eggs (2 min)

On the org's **Eggs** page, **Import an egg** from each link, read what it runs, and import it:

- `https://github.com/xena-studios/mostwanted/blob/main/server/raptor/egg-mostwanted.json`: the game server.
- `https://github.com/xena-studios/mostwanted/blob/main/server/raptor/egg-mostwanted-api.json`: the API and Discord bot.

Each installs the latest build of `main` from the [`server` release](https://github.com/xena-studios/mostwanted/releases/tag/server), and has its settings on the server's **Startup** tab.

## 5. The game server (10 min)

**Deploy server**:
- **Game** MostWanted (your org's), **runtime** Java 25, **network** MostWanted.
- **Name** MostWanted, **port** 25565, **memory 11.5 GB**: Java gets 10 GB of it, the rest is Java's own overhead (`start.sh` works it out from this setting).
- Accept the EULA when it asks.

Raptor installs it: Leaf, every plugin we use (pinned, checksums checked), our configs and the core plugin. It then tries to start, and stops with "needs DB_HOST": it has no database yet. That's expected.

1. **Databases** tab: **New database** named `mostwanted`. Raptor gives the server its details at every start (`DB_HOST`, `DB_NAME`, …), and `start.sh` fills them into LuckPerms', LibertyBans' and the game plugin's configs. Nothing to copy. Note its **Database** and **User** names, and its password (**Password**), for the API (step 7).
2. **Startup** tab: **Tebex secret key** stays `none` until the store is set up (step 10).
3. **Network** tab: **Shown address** `play.mostwantedmc.com`.
4. **Settings** tab: **Force stop after** 120 seconds, so a big world always finishes saving.
5. **Start** it, and watch the console for `Done (`. Then, once, paste the `lp` lines from [`server/setup/luckperms.txt`](../server/setup/luckperms.txt) into the console (store ranks included), and `lp user <your name> parent add owner` for yourself.
6. **Schedules** tab: **New schedule**, 5:30 AM America/New_York, every day:
   ```
   Command   say Restarting in 5 minutes
   Wait      4 minutes
   Command   say Restarting in 1 minute
   Wait      1 minute
   Power     restart
   ```
   (The **Daily backup** schedule is already there, between 4 and 5 AM.)

The whitelist is on until launch day ([step 11](#11-open-the-doors)). Configs come from the repo: change them there, not in the file manager, or the next update puts them back. The three with settings filled in (`plugins/*/config.yml`, LibertyBans' `sql.yml`) are written fresh from their `.tmpl` at every start.

## 6. Cloudflare (10 min)

In the dashboard for **mostwantedmc.com**:

1. **SSL/TLS → Origin Server → Create Certificate.** "Generate private key and CSR with Cloudflare", RSA (2048), hostnames `api.mostwantedmc.com`, validity 15 years, format **PEM**. Keep the page open: the **private key is shown once**.
2. **SSL/TLS → Overview → Full (strict).** (The website is a Worker, so this only changes how Cloudflare talks to the VPS.)
3. **SSL/TLS → Origin Server → Authenticated Origin Pulls: On.**
4. **DNS → Add record:** `A`, name `api`, IPv4 `VPS_IP`, **Proxied** (orange cloud).
5. **Rules → Origin Rules → Create rule:** "api to 8443", when **Hostname equals `api.mostwantedmc.com`**, **Destination Port → Rewrite to 8443**. (Raptor gives servers ports from 1024 up, so the API can't take 443 itself.)
6. **Caching → Cache Rules → Create rule:** "api stats", when **Hostname equals `api.mostwantedmc.com`** and **URI Path starts with `/v1/players/`** or **starts with `/v1/leaderboards/`**, **Eligible for cache**, Edge TTL **Use cache-control header if present**. Without it Cloudflare caches nothing from the API (it only caches file extensions by default), and every page view reaches the VPS.

About step 3: the certificate Cloudflare shows the VPS is the same for **every** Cloudflare customer, so it proves a request came through Cloudflare, not through *our* zone. Someone with their own Cloudflare zone pointed at our IP could still get through. Closing that needs our own client certificate uploaded to the zone (Cloudflare's API only, no dashboard button: "zone-level Authenticated Origin Pulls with your own certificate" in their docs); then `TLS_CLIENT_CA` points at our CA instead of Cloudflare's. Fine to launch without, worth doing after.

## 7. The API and Discord bot (10 min)

**Deploy server**: **Game** MostWanted API (your org's), **runtime** Java 25, **network** MostWanted, **name** MostWanted API, **port 8443**, **memory 512 MB** (Java gets 256 MB). Like the game, its first start stops: it has no settings yet.

1. **Databases** tab: **New database** named `api`, and leave it empty. Raptor's database only lets in servers that have a database on it; the API really uses the game's.
2. **Startup** tab:
   - **Game database name**, **user** and **password**: the game server's `mostwanted` database, from step 5.
   - **Discord bot token**: the bot's token (on your PC in `~/.config/mostwanted/discord.env`).
   - **Discord client secret**: from step 8.
   - The rest are already right for mostwantedmc.com.
3. **Files** tab: in the `tls` folder, **New file** `origin.pem` with the Origin Certificate from step 6.1, and `origin.key` with its private key. (Cloudflare's pull CA is already there: the install downloads it.)
4. **Start** it. It logs `https with client certificates` and `API listening`; a bad file or a key that doesn't match the certificate stops it with the reason ([backend/README.md](../backend/README.md#https-and-authenticated-origin-pulls)).

If the game database's password is ever reset in Raptor, put the new one in the API's **Startup** tab too.

## 8. Discord (5 min)

In the [developer portal](https://discord.com/developers/applications), the **MostWanted** app:

1. **OAuth2 → Redirects:** add `https://api.mostwantedmc.com/v1/auth/discord/callback`.
2. **OAuth2 → Client Secret → Reset Secret**, and put it in the API's **Startup** tab as **Discord client secret**, then restart the API.
3. **General Information:** Terms of Service `https://mostwantedmc.com/terms`, Privacy Policy `https://mostwantedmc.com/privacy`.

## 9. Minehut (5 min)

In the [Minehut panel](https://minehut.com), on our server ([tech](04-tech.md#how-players-connect)):

1. Plan: **External**.
2. External Server Settings: IP `VPS_IP`, proxy type **Other**, DNS record type **Port**, port **25565**, TCPShield **Not Configured**.

The server already speaks Minehut's PROXY protocol and checks logins with Minehut, so direct connections can't join anyway. Ask Minehut for their IP ranges (for the firewall, once Raptor can limit a server's port to them) and how Bedrock players are marked.

## 10. Tebex

Follow [store: setting up Tebex](11-store.md#setting-up-tebex-whats-left-for-you): the webstore, the packages, and the **public token** as the `TEBEX_PUBLIC_TOKEN` repository variable (GitHub → Settings → Secrets and variables → Actions → Variables). Delivery in game is built into core: put the game server's Tebex secret key in its **Startup** tab as **Tebex secret key**, and restart.

## 11. Open the doors

1. **Website**, in a pull request: `open: true` and the permanent Discord invite in `discord` in [`website/src/site.ts`](../website/src/site.ts). Merging deploys it (the website workflow), with the Tebex token from step 10.
2. **Game**, in a pull request: `white-list=false` and `enforce-whitelist=false` in [`server/files/server.properties`](../server/files/server.properties). Once it's merged and the server release has run, **Reinstall** the game server in Raptor and start it. (`whitelist off` in the console works at once, but the next update would turn it back on without the PR.)

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

- [ ] `curl -sk https://127.0.0.1:8443/health` fails with a certificate error: the API really wants Cloudflare's certificate.
- [ ] `sudo raptor doctor` is happy, and the node's **System** tab shows no warnings.

## Updating

Every change merged to `main` is built into the [`server` release](https://github.com/xena-studios/mostwanted/releases/tag/server) a few minutes later. To put it on a server: Raptor, the server's **Settings → Reinstall** (it takes a safety backup first, and keeps the world, plugin data and settings), then start it. The install's log names the build (`MostWanted build <commit>`).

## Alerts

On **Settings → Alerts** (new orgs already tell their owners about crash loops, an offline node and low disk), add:

- **Crashed**, **Backup failed** and **Restore finished or failed**, on both servers.
- **Memory above 90% for 10 minutes** on the game server: the heap is too small, or something leaks.
- The database instance: **down or restarting**, and **connections above 80%**.
- A **team channel**: a Discord webhook into a staff-only channel, so the team sees them, not only you.

Then the bell's settings: how alerts reach **you** (email is on; Discord or ntfy on your phone are quicker).

## Backups

- **Where:** on the node's **Backups** tab, add **Raptor Backup Storage** (offsite, nothing to set up), or any S3 (Cloudflare R2, Backblaze), and **Test** it. On each server's **Backups** tab, back up to it **and** the node's disk (fast restores), keeping **Standard** (the last 3, a week of dailies, a month of weeklies).
- **What:** the **Daily backup** schedule (4–5 AM) backs up the game's files and, dumped just before, its **database**: the whole game, consistent. Minecraft is told to save first. The API's backup holds its `tls/`.
- **Back up now** on both once, and check they arrive offsite.
- **The whole VPS:** a netcup snapshot (Server Control Panel → Snapshots) before big changes, as a last resort. It's kept on netcup's side, so it's no substitute for the offsite backups.
- **Test a restore** before launch, not after: on a backup, **Restore → a few files** into `.restore/`, and on the Databases tab, **Restore from a backup** on a quiet evening.

## Rollback

- **A bad update:** the Reinstall took a safety backup: on **Backups**, **Restore everything** from it. Then revert the change on `main`, so the next update doesn't bring it back.
- **The database:** stop the game and the API in Raptor, then on the game's **Databases** tab, **Restore from a backup** on `mostwanted`. Raptor exports what's there now first, so a wrong pick can be undone. **Restore everything** on a backup puts back the files and the database together.
- **The API misbehaving:** stop it in Raptor. The website shows its "can't reach" states; set `open: false` in `site.ts` again if it should look closed. A 526 from Cloudflare means the certificate is wrong (step 6.1, `tls/origin.*`); a 525 or a failed handshake after turning on Authenticated Origin Pulls means the pull CA is wrong: clear **Cloudflare pull CA** on the API's Startup tab to check, then put it back.
- **Everything:** a netcup snapshot, or a new server following this page, with the Raptor backups (files and database) restored onto it. Update the IP in Minehut and in Cloudflare's `api` record.
