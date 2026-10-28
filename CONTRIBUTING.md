# Contributing

Thanks for helping build the server. This covers reporting problems, building the plugins, and getting a change merged.

Everyone taking part follows the [code of conduct](CODE_OF_CONDUCT.md). Security problems go to [SECURITY.md](SECURITY.md), never to a public issue.

## Reporting a bug or suggesting an idea

Open an [issue](https://github.com/xena-studios/mostwanted/issues/new/choose) and pick a form. For a bug, the version from `/mostwanted info`, what you did, and the console error matter most. Rule breakers and in-game reports go to the server's staff, not here.

## Building

Needs a JDK 25.

```sh
./gradlew build                   # compile and test every plugin
./gradlew :core:runServer         # a Paper server with the plugin, in core/run/
./gradlew compileJava -Pxena.newestPaper=<paper-api version>   # against a newer Paper, failing on removals
scripts/boot-test.sh --project leaf --version 26.3 \
  --checks core/boot-checks.txt core/build/libs/core-*.jar
```

`core` needs PostgreSQL, for `runServer` and the boot test alike: start one with the `docker run` line in [core/README.md](core/README.md#building-and-running). CI starts its own.

The jars land in `<module>/build/libs/`. The database tests in `common` need Docker (they run a real PostgreSQL); without it they're skipped.

## How the repo fits together

```
build-logic/   the Gradle conventions: xena.java for every Java module, xena.plugin for plugins
core/          the game plugin (MostWanted): every system the server runs
common/        the database tables (migrations), pool and message style, shared by core and backend
website/       the website and store
resource-pack/ source art and models for the custom items
server/        the game server: pinned plugins, install/start/test scripts, configs (never secrets)
backend/       the website API and Discord bot, and backend/discord-setup/ for the Discord server
brand/         the logo, key art and icons (scripts/brand-assets.py builds brand/out/)
docs/          the design: gameplay, tech, server stack, decisions
scripts/       boot-test.sh, changelog.sh, new-minecraft.sh
```

What the server runs, and why, is in [docs/04-tech.md](docs/04-tech.md) and [docs/07-server-stack.md](docs/07-server-stack.md). Decisions are logged in [docs/05-open-questions.md](docs/05-open-questions.md); read the parts your change touches.

## What a change has to keep true

The server has to hold a lot of players in one world on modest hardware, so:

- **Nothing heavy on the main thread.** Database and network calls run async. No work every tick unless a feature can't work without it, and then only while it's on.
- **Do the work once.** Parse config, messages and regions when they load, not on every event.
- **Java and Bedrock both work.** Bedrock players join through Minehut's Geyser, with no Floodgate, so: chest GUIs, no Bedrock forms, nothing that needs a Java-only client feature. Every custom item is a vanilla item underneath, so the game still works without the resource pack.
- **Colours come from the palette** in [docs/09-brand.md](docs/09-brand.md#in-game-colours). Anything players must tell apart uses hues that stay different on Bedrock.
- **Players are stored by UUID**, never by name.
- **Tables change through a new migration** in `common`, never by editing a merged one. See [common/README.md](common/README.md#changing-the-tables).
- **Paper API only.** No NMS, so the plugin keeps running on Leaf and on every Paper update.
- **The hot path stays in memory.** Gameplay reads player state, zones and config from memory; the database is only written behind, in batches, off the main thread. Movement handlers return early unless the player changed block. The full list: [How core stays fast](docs/04-tech.md#how-core-stays-fast).
- **Every message follows the [message style](docs/09-brand.md#messages)**: a coloured symbol per kind, grey text, banners only for big moments.

## Tests

- **Unit tests** (JUnit) for logic that doesn't need a server. Put them next to the code they test.
- **Boot checks.** Each plugin's `boot-checks.txt` lists console commands CI runs on real Leaf and Paper servers, with the output each must log (and `file:` lines for configs to put in place first). A new command or feature should add a line there.

## Pull requests

1. Make a branch from `main`, or fork the repo.
2. Make the change, with its tests, and update the docs that describe it.
3. Add a line for it to the plugin's `CHANGELOG.md` under `## Unreleased` (add the heading if it isn't there).
4. Run `./gradlew build` and open the PR. The template asks for the rest.

CI builds, tests, boot-tests on Leaf and Paper, and compiles against the newest Paper; CodeQL scans the change for security problems. `main` only takes changes through pull requests (no force pushes or deletion), and its required check, `build-ok`, has to pass. A Markdown-only PR skips the build. PRs are labelled by folder on their own.

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org), with the module or area as the scope: `feat(core): arrest a criminal with handcuffs`, `fix(core): ...`, `docs(tech): ...`, `build: ...`, `ci: ...`.

By contributing, you agree your contribution is licensed under the repo's licence, [GPL-3.0-only](LICENSE).

## Adding a plugin

Most features belong in `core`. A separate plugin is a folder with a `build.gradle.kts` that applies `id("xena.plugin")`, sets `version`, and names its main class in `paperPluginYaml`; add it to `settings.gradle.kts`. Give it a `README.md`, a `CHANGELOG.md` and a `boot-checks.txt` (CI picks that up on its own). `core` is the example to copy.

## Releasing

For maintainers. Bump the plugin's `version` in its `build.gradle.kts`, rename `## Unreleased` in its `CHANGELOG.md` to `## <version>`, merge, then push a tag such as `core-v0.2.0`. The release workflow checks the two match, then publishes the jar as a GitHub release with that changelog section as the notes.

## Dependency updates

[Renovate](https://docs.renovatebot.com) opens PRs on Monday mornings ([`renovate.json`](renovate.json), on top of the org's shared settings in [`xena-studios/.github`](https://github.com/xena-studios/.github/blob/main/renovate-config.json)). Minor and patch updates merge themselves once CI passes; majors wait for a review. The Paper version is never touched by Renovate.

New Minecraft versions come from [`minecraft.yml`](.github/workflows/minecraft.yml), which runs on Mondays or by hand from the Actions tab (**New Minecraft** → **Run workflow**). When Paper has a stable build of a newer version, it opens a PR that builds and boots the plugins on it. Merge it once the live server can move too: Leaf, the plugins in the server stack, and Minehut.
