# Contributing

Thanks for helping build the server. Everyone follows the [code of conduct](https://github.com/xena-studios/.github/blob/main/CODE_OF_CONDUCT.md), and security problems go to [SECURITY.md](SECURITY.md), never a public issue.

## Issues

[Open an issue](https://github.com/xena-studios/mostwanted/issues/new/choose) and pick a form. For a bug, include the version from `/mostwanted info`, what you did, and the console error. Rule breakers and in-game reports go to the server's staff, not here.

## Building

Needs a JDK 25.

```sh
./gradlew build                   # compile and test everything
./gradlew :core:runServer         # a Paper server with the plugin, in core/run/
./gradlew compileJava -Pxena.newestPaper=<paper-api version>   # check against a newer Paper
scripts/boot-test.sh --project leaf --version 26.3 \
  --checks core/boot-checks.txt core/build/libs/core-*.jar
```

- `core` needs PostgreSQL, for `runServer` and the boot test. Start one with the `docker run` line in [core/README.md](core/README.md#building-and-running); CI starts its own.
- The database tests in `common` need Docker, and are skipped without it.
- Jars land in `<module>/build/libs/`.

Each folder's README covers its part, and the [design docs](README.md#design-docs) say what the server runs and why. Decisions are logged in [docs/05-open-questions.md](docs/05-open-questions.md): read the parts your change touches.

## Rules every change keeps

The server has to hold a lot of players in one world on modest hardware.

- **Nothing heavy on the main thread.** Database and network calls run async. No per-tick work unless a feature can't work without it, and then only while it's on.
- **Keep the hot path in memory.** Gameplay reads player state, zones and config from memory, and the database is written behind, in batches, off the main thread. Movement handlers return early unless the player changed block. More in [how core stays fast](docs/04-tech.md#how-core-stays-fast).
- **Do the work once.** Parse config, messages and regions when they load, not on every event.
- **Java and Bedrock both work.** Bedrock players join through Minehut's Geyser, with no Floodgate: chest GUIs only, no Bedrock forms, nothing Java-only. Every custom item is a vanilla item underneath, so the game works without the resource pack.
- **Paper API only.** No NMS, so the plugin keeps running on Leaf and through Paper updates.
- **Players are stored by UUID**, never by name.
- **Tables change through a new migration** in `common`, never by editing a merged one ([how](common/README.md#changing-the-tables)).
- **Colours come from the [palette](docs/09-brand.md#in-game-colours)**, and anything players must tell apart stays different on Bedrock.
- **Messages follow the [message style](docs/09-brand.md#messages).**

## Tests

- **Unit tests** (JUnit) for logic that doesn't need a server, next to the code they test.
- **Boot checks:** each plugin's `boot-checks.txt` lists console commands CI runs on real Leaf and Paper servers, with the output each must log (`file:` lines put configs in place first). A new command or feature adds a line.

## Pull requests

1. Branch from `main` (or fork), and make the change with its tests and docs.
2. Add a line to the plugin's `CHANGELOG.md` under `## Unreleased`.
3. Run `./gradlew build` and open the PR.

CI builds, tests, boot-tests on Leaf and Paper, compiles against the newest Paper, and runs CodeQL. The required checks are `build-ok`, `server-ok` and `website-ok`; each passes on its own when its part didn't change, so Markdown-only PRs skip the builds. PRs are labelled by folder automatically.

Commits follow [Conventional Commits](https://www.conventionalcommits.org), with the module as the scope: `feat(core): arrest a criminal with handcuffs`, `fix(core): ...`, `docs(tech): ...`, `build: ...`, `ci: ...`.

Contributions are licensed under [GPL-3.0-only](LICENSE).

## Adding a plugin

Most features belong in `core`. A new plugin is a folder with a `build.gradle.kts` that applies `id("xena.plugin")`, sets `version`, and names its main class in `paperPluginYaml`. Add it to `settings.gradle.kts`, and give it a `README.md`, a `CHANGELOG.md` and a `boot-checks.txt` (CI finds it on its own). Copy `core`.

## Releasing

1. Bump `version` in the plugin's `build.gradle.kts`, and rename `## Unreleased` in its `CHANGELOG.md` to `## <version>`.
2. Merge, then push a tag such as `core-v0.2.0`.

The release workflow checks the two match, then publishes the jar with that changelog section as the notes.

## Dependency updates

- [Renovate](https://docs.renovatebot.com) opens PRs on Monday mornings ([`renovate.json`](renovate.json), plus the [org's shared settings](https://github.com/xena-studios/.github/blob/main/renovate-config.json)). Minor and patch updates merge themselves once CI passes; majors wait for a review. Renovate never touches the Paper version.
- A merge rebuilds the server release, but the live server only takes it at its next reinstall.
- **New Minecraft versions:** [`minecraft.yml`](.github/workflows/minecraft.yml) runs on Mondays (or **Actions → New Minecraft → Run workflow**). When Paper has a stable build of a newer version, it opens a PR that builds and boots the plugins on it. Merge it once Leaf, the plugins in the server stack and Minehut can move too.
