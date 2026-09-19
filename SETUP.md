# MazeCraft — Developer Setup

## Prerequisites

- **Java 21** (Temurin recommended)
- A Java IDE (IntelliJ IDEA has first-class Fabric Loom support)

## Build

```bash
./gradlew build                          # PUBLIC JAR
./gradlew clean build -PbuildType=debug  # DEBUG JAR
```

Output in `build/libs/mazecraft-<version>[-debug].jar`.

## Run in dev

```bash
./gradlew runClient   # client with the mod loaded
./gradlew runServer   # dedicated server with the mod loaded
```

Only resource changes hot-reload; restart Minecraft for Java changes.

## Project conventions

- Debug features are gated on `MazeCraft.isDebugBuild()` — always guard behind this.
- Localization keys live in `assets/mazecraft/lang/{en_us,fr_fr}.json` — keep both in sync.
- Cloth Config classes are only referenced from `client/config/MazeCraftConfigScreen` (Cloth Config is optional).

## Build variants (Public vs Debug)

| | Public | Debug |
|---|---|---|
| `/maze debug ...` available | ❌ | ✓ |
| Debug flags editable via JSON | forced to `false` | editable |

In PUBLIC builds, debug flags in `mazecraft.json` are ignored at runtime — a warning is logged. Install the DEBUG JAR to get debug features.

## Project layout

```
src/main/
├── java/fr/mazecraft/
│   ├── MazeCraft.java               (main entry, build detection)
│   ├── config/MazeCraftConfig.java  (config/mazecraft.json)
│   ├── commands/                    (MazeCommand, DebugCommand)
│   └── client/                      (MazeCraftClient, ModMenuIntegration, MazeCraftConfigScreen)
└── resources/
    ├── fabric.mod.json
    ├── mazecraft.build.properties
    └── assets/mazecraft/            (icon.png, lang/en_us.json, lang/fr_fr.json)
```

## Releasing a new version

1. Bump `mod_version` in `gradle.properties`.
2. Add a `CHANGELOG.md` entry with the heading `## [<version>] — <date>`.
3. Commit + push, then tag: `git tag v<version> && git push origin v<version>`.
4. The `Release` workflow creates the GitHub Release and publishes to Modrinth.

**One-time setup before the first release:**

- Modrinth project ID (`GEfPlo7m`) is already set in `build.gradle`.
- Add the `MODRINTH_TOKEN` secret in the GitHub repo (Settings → Secrets and variables → Actions).

## Troubleshooting

**`Could not resolve net.fabricmc:yarn...`** — if you changed `minecraft_version`, also update `yarn_mappings` to a matching version from https://fabricmc.net/develop.

**`/maze debug` unavailable** — you're running the PUBLIC build. Install the DEBUG JAR (produced by `-PbuildType=debug`).

**DEBUG commands (in DEBUG build only)**

| Command | Description |
|---|---|
| `/maze debug info` | Print runtime debug info |
