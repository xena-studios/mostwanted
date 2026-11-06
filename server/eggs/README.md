# Eggs

The two servers the panel creates:

| Egg | Server |
| --- | --- |
| `egg-mostwanted.json` | The game server |
| `egg-mostwanted-api.json` | The website API and the Discord bot |

Each installs the latest build of `main` from the [`server` release](https://github.com/xena-studios/mostwanted/releases/tag/server). **Reinstall** a server to update it. Settings, including the managed database's details, go on the server's **Startup** tab.

The panel syncs these files from this repo, so a change here shows up as **Update Available** on the egg ([launch, step 5](../../docs/12-launch.md#5-the-eggs-2-min)).
