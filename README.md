# MazeCraft

> 🚧 **Work in progress** — alpha. Currently: 10 Overworld maze styles (hedge, desert, snow, jungle, badlands, dark forest, cherry, swamp, savanna, taiga) 5 enclosed Nether styles (fortress, crimson, warped, soul, basalt) and an End style, in 4 sizes, gates & levers, treasure chest at the center, anti-cheat protection and advancements.

A Minecraft 1.21.1 (Fabric) mod that generates **procedural mazes** across the
Overworld, the Nether and the End. Each maze takes the architectural style of the
biome it spawns in. Find your way to the center, pull levers to open the gates
blocking your path, and claim the treasure waiting at the heart of the maze.

## Planned features

- **Natural world generation** — mazes spawn like vanilla structures (villages, temples…), in new worlds and in not-yet-generated chunks of existing worlds. `/locate structure` works.
- **Random sizes** — Small, Medium, Large and a very rare Colossal maze. Bigger maze, better loot.
- **Biome styles** — hedge mazes in plains, sandstone in deserts, mossy ruins in jungles, nether bricks, crimson / warped, basalt, soul sand valley, end stone & purpur in the End…
- **Levers & gates** — gates block the path to the center (2 to 5 depending on size); each one is opened by a lever hidden in a dead end of the zone before it.
- **Anti-cheat** — walls can't be broken, blocks can't be placed and ender pearls don't work until the central chest has been opened (configurable).
- **Treasure** — hand-made loot tables per size and dimension, plus MazeCraft-exclusive items.
- **Ariadne's Thread** — points to the nearest maze, and inside one traces the way to the next lever. Found in maze chests and in some vanilla chests (dungeons, temples, fortresses...).
- **Enemies** — biome-themed guardians, patrols appearing out of sight while you explore, ambushes triggered by levers, and a Maze Champion guarding the treasure (the chest stays locked until it dies). A Minotaur boss is planned.

## Requirements

- Minecraft **1.21.1** with Fabric Loader
- **Fabric API** (required)
- **Cloth Config** and **Mod Menu** (optional, for the config GUI)
- Java 21

## Installation

1. Download `mazecraft-<version>.jar` from [Modrinth](https://modrinth.com/mod/mazecraft) or the [Releases](https://github.com/Remilulz91/MazeCraft/releases) page.
2. Drop it in your `mods/` folder along with Fabric API.
3. Launch Minecraft. `config/mazecraft.json` is created on first launch.

## Commands

| Command | OP | Description |
|---|---|---|
| `/maze version` | no | Show mod version + build type |
| `/maze reload` | yes | Reload `mazecraft.json` |
| `/locate structure #mazecraft:mazes` | yes | Find the nearest maze of any style (or a single style: `mazecraft:maze_<style>`) |

## Building from source

See [SETUP.md](SETUP.md).

## License

MIT — see [LICENSE](LICENSE).
