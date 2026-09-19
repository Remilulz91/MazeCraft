# MazeCraft

> 🚧 **Work in progress** — early alpha. Currently: hedge mazes (4 sizes), treasure chest at the center, anti-cheat protection and advancements.

A Minecraft 1.21.1 (Fabric) mod that generates **procedural mazes** across the
Overworld, the Nether and the End. Each maze takes the architectural style of the
biome it spawns in. Find your way to the center, pull levers to open the gates
blocking your path, and claim the treasure waiting at the heart of the maze.

## Planned features

- **Natural world generation** — mazes spawn like vanilla structures (villages, temples…), in new worlds and in not-yet-generated chunks of existing worlds. `/locate structure` works.
- **Random sizes** — Small, Medium, Large and a very rare Colossal maze. Bigger maze, better loot.
- **Biome styles** — hedge mazes in plains, sandstone in deserts, mossy ruins in jungles, nether bricks, crimson / warped, basalt, soul sand valley, end stone & purpur in the End…
- **Levers & gates** — the maze is split into rings; each ring's gate is opened by a lever hidden somewhere in the previous ring.
- **Anti-cheat** — walls can't be broken, blocks can't be placed and ender pearls don't work until the central chest has been opened (configurable).
- **Treasure** — hand-made loot tables per size and dimension, plus MazeCraft-exclusive items.
- **Enemies** — biome-themed guardians, ambushes triggered by levers and, later, a Minotaur boss.

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
| `/locate structure mazecraft:maze_hedge` | yes | Find the nearest hedge maze (vanilla command) |

## Building from source

See [SETUP.md](SETUP.md).

## License

MIT — see [LICENSE](LICENSE).
