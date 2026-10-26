# Discord setup

`build.py` builds the MostWanted Discord server from the layout at the top of the file: server settings, roles, channels and their permissions, forum tags, the posts in #welcome, #rules, #link, #support and #apply, and AutoMod. It's how the server was built on 2026-10-26, and the way to change it: edit the layout, dry-run, apply.

```sh
python3 -I build.py --dry-run   # read-only: shows what's there and what would change
python3 -I build.py --apply     # makes the changes
python3 -I check.py             # read-only check of the app, bot profile, roles, invites and AutoMod
```

- **Safe to re-run.** Roles and channels are matched by name; it creates what's missing, updates what differs and edits its own posts in place. A second `--apply` should report no changes.
- **It deletes** channels and roles that aren't in the layout (the old server was wiped on purpose). Always dry-run first.
- **The token** is read from `~/.config/mostwanted/discord.env` (`DISCORD_TOKEN=...`, readable only by you) inside the script, never printed or passed on the command line. To save it without it showing on screen:
  ```sh
  mkdir -p ~/.config/mostwanted && (umask 077; read -rs "t?Bot token: " && printf 'DISCORD_TOKEN=%s\n' "$t" > ~/.config/mostwanted/discord.env; echo saved)
  ```
- `.build-state.json` remembers which images were uploaded, so reruns don't re-upload them. It holds no secrets and isn't committed.

What a bot can't do, so it's done by hand in Discord: its own role's colour, position and "shown separately"; Discord's built-in "Block Mention Spam" rule; requiring 2FA for moderators; the server banner and invite splash before boost levels 2 and 1 (the script sets them once the level allows).
