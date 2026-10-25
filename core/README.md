# PrisonBreak (core)

The game plugin: every system the server runs. What it will do is in [what we build](../docs/04-tech.md#what-we-build-ourselves).

Runs on Paper 26.3 and Leaf 26.3, Java 25.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/prisonbreak info` (`/pb info`) | `prisonbreak.admin` | The plugin version and the server it's on |

## Building

```sh
./gradlew :core:build       # core/build/libs/core-<version>.jar
./gradlew :core:runServer   # a Paper server with the plugin, in core/run/
```
