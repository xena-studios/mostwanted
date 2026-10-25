# Prison Break (working title)

A Jailbreak-style cops vs. prisoners Minecraft server (Java + Bedrock) by [Xena Studios](https://xenastudios.co). Break out, rob the city, and stay ahead of the guards. Or be the guard who brings them back.

There's no final name yet. `prison-break` is a placeholder, and the repo can be renamed once we pick one.

This repo holds everything the server needs: the game plugin, its build and CI, and the design docs.

## What's here

```
core/          PrisonBreak, the game plugin: every system the server runs
build-logic/   the Gradle conventions every plugin applies
docs/          the design: gameplay, tech, server stack, decisions
ideas/         raw ideas and name candidates
scripts/       boot tests, changelogs and the new-Minecraft check
```

| Doc | What's in it |
| --- | --- |
| [`docs/01-vision.md`](docs/01-vision.md) | What the server is, who it's for, and what makes it different |
| [`docs/02-gameplay.md`](docs/02-gameplay.md) | Jailbreak-style loop: teams, escapes, robberies, arrests, riots, guns, economy |
| [`docs/03-features.md`](docs/03-features.md) | Feature backlog, sorted into must-have / nice-to-have / later |
| [`docs/04-tech.md`](docs/04-tech.md) | Decided stack: server software, hosting, what we build, build order |
| [`docs/05-open-questions.md`](docs/05-open-questions.md) | Decisions made, and the ones still open |
| [`docs/06-resource-pack.md`](docs/06-resource-pack.md) | Custom textures and items on Java + Bedrock |
| [`docs/07-server-stack.md`](docs/07-server-stack.md) | Install list: server software, every plugin + version, start command, config |
| [`ideas/names.md`](ideas/names.md) | Server name candidates |

## Building

Needs a JDK 25.

```sh
./gradlew build                # every plugin, tested, into <module>/build/libs/
./gradlew :core:runServer      # a Paper server with the plugin
```

[CONTRIBUTING.md](CONTRIBUTING.md) has the rest: tests, boot checks, the rules a change keeps, pull requests and releases.

## Status

Planning done, plugin skeleton building. Next: the server foundation and the core game loop ([build order](docs/04-tech.md#build-order)).

## Help

- **Bugs and ideas:** [open an issue](https://github.com/xena-studios/prison-break/issues/new/choose).
- **Security problems:** privately, as [SECURITY.md](SECURITY.md) describes.
- **Anything else:** [contact@xenastudios.co](mailto:contact@xenastudios.co).

Everyone taking part follows the [code of conduct](CODE_OF_CONDUCT.md).

## Licence

GPL-3.0-only: the GNU General Public License, version 3 only, not later versions. See [LICENSE](LICENSE).
