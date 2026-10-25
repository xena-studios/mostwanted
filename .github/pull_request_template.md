## What this changes

<!-- What changes in the game or the server, and why. Link the issue it fixes, if any: "Fixes #123". -->

## Checklist

- [ ] `./gradlew build` passes
- [ ] Tests cover the change, and a new command or feature has a line in the plugin's `boot-checks.txt`
- [ ] Works for Bedrock players too (no Floodgate forms, chest GUIs only, custom items are vanilla underneath)
- [ ] Nothing heavy on the main thread, and players stored by UUID ([CONTRIBUTING.md](https://github.com/xena-studios/prison-break/blob/main/CONTRIBUTING.md#what-a-change-has-to-keep-true))
- [ ] The docs that describe it are updated
- [ ] A line in the plugin's `CHANGELOG.md` under `## Unreleased`
