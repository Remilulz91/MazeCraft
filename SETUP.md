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
./gradlew runClient   # client with the mod loaded (DEBUG build by default)
./gradlew runServer   # dedicated server with the mod loaded (DEBUG build by default)
```

Only resource changes hot-reload; restart Minecraft for Java changes.

## Project conventions

- Debug features are gated on `MazeCraft.isDebugBuild()` — always guard behind this.
- Localization keys live in `assets/mazecraft/lang/{en_us,fr_fr}.json` — keep both in sync.
- Maze layout is pure Java (`MazeLayout`, no Minecraft classes) and fully determined by `(cells, seed)`: every chunk rebuilds the same maze and only writes inside its own chunk box.
- Structure rarity/biomes are data-driven (JSON under `data/mazecraft/worldgen`), the maze shape is code-driven.
- Cloth Config classes are only referenced from `client/config/MazeCraftConfigScreen` (Cloth Config is optional).

## Build variants (Public vs Debug)

| | Public | Debug |
|---|---|---|
| `/maze debug ...` available | ❌ | ✓ (OP) |

The build type is baked into the JAR (`mazecraft.build.properties`); nothing in the config file can enable debug commands in a PUBLIC build. `runClient` / `runServer` default to DEBUG.

## Project layout

```
src/main/
├── java/fr/mazecraft/
│   ├── MazeCraft.java               (main entry, build detection)
│   ├── config/MazeCraftConfig.java  (config/mazecraft.json)
│   ├── commands/                    (MazeCommand, DebugCommand)
│   ├── protection/                  (MazeProtection, MazeState — anti-cheat until the chest is opened)
│   ├── mixin/                       (BlockItemMixin — no block placement in unconquered mazes)
│   ├── structure/                   (MazeStructure, MazePiece, MazeLayout, MazeSize, MazeStyle, ModStructures)
│   └── client/                      (MazeCraftClient, ModMenuIntegration, MazeCraftConfigScreen)
└── resources/
    ├── fabric.mod.json
    ├── mazecraft.build.properties
    ├── assets/mazecraft/            (icon.png, lang/en_us.json, lang/fr_fr.json)
    └── data/mazecraft/
        ├── worldgen/structure/      (maze_hedge.json — style + biome tag + terrain adaptation)
        ├── worldgen/structure_set/  (mazes.json — spacing 40 / separation 16 chunks)
        ├── tags/worldgen/biome/has_structure/  (biomes per style)
        ├── tags/worldgen/structure/ (mazes.json — every maze structure)
        ├── advancement/             (root, enter_hedge, conquer_maze, conquer_colossal)
        └── loot_table/chests/       (maze_small/medium/large/colossal.json)
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

**`/maze debug` unavailable (red in chat)** — you're running the PUBLIC build, or you're not OP (open the world to LAN with cheats on). Install the DEBUG JAR (produced by `-PbuildType=debug`).

**DEBUG commands (in DEBUG build only)**

| Command | Description |
|---|---|
| `/maze debug info` | Print runtime debug info |
| `/maze debug place <size> [style]` | Build a maze centered on you (sizes: small, medium, large, colossal). Not a real structure: no protection, no advancements |
| `/maze debug where` | Info about the natural maze you're in (size, conquered or not, chest, entrance) |
| `/maze debug unlock` / `relock` | Mark the natural maze you're in as conquered / not conquered |

## Testing world generation

Structures only appear in **newly generated chunks**. To test: create a new world (or fly far away), then
`/locate structure mazecraft:maze_hedge` and `/tp` to the coordinates. Mazes only spawn in their biome tag
(plains, meadow, forests for the hedge style).
