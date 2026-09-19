# Changelog

All notable changes to MazeCraft will be documented in this file.

## [0.1.0-alpha.1] — Unreleased

First mazes in the world: the hedge maze.

### Added
- **Hedge maze structure** (`mazecraft:maze_hedge`) generated naturally in plains, sunflower plains, meadows, forests, flower forests and birch forests — in new worlds and in not-yet-generated chunks of existing worlds. Findable with `/locate structure mazecraft:maze_hedge`.
- **Four random sizes**: Small (61×61), Medium (101×101), Large (149×149), Colossal (221×221). Relative weights configurable (`weightSmall`, `weightMedium`, `weightLarge`, `weightColossal`, default 45/35/15/5).
- Terrain-aware size: if the rolled size doesn't fit the terrain, a smaller one is tried.
- "Growing tree" maze layout (mix of long winding corridors and many side branches): exactly **one path** from the entrance to the center, every other branch is a dead end. One entrance on a random side; the 3×3-cell central plaza has a single door, on the side facing away from the entrance.
- **Central chest** with dedicated loot tables per size (`mazecraft:chests/maze_<size>`): diamonds, enchanted books and gear, golden apples; totems and netherite in large and colossal mazes.
- Terrain handling: mazes skip spots with water or more than 12 blocks of height difference, clear hills inside the footprint and fill gaps underneath.
- Corridor floor is dirt path, so no trees or flowers grow inside the maze.
- **Entrance placement**: the entrance faces the side where the surrounding terrain is closest to the maze floor, and a flattened 4-block ring with a path leads to it — no more entrance opening into a cliff.
- **Protection until conquered** (survival/adventure; creative and spectator are exempt): blocks can't be broken or placed inside a maze until its central chest is opened. Refused block placements are immediately given back in the inventory. Players on top of the walls or flying over the maze are sent back to the entrance. Both rules configurable (`protectUntilSolved`, `preventWallWalking`).
- **Maze conquered**: opening the central chest lifts the protections for everyone (saved per world).
- **Advancements**: *MazeCraft* (enter a maze), *Hedge Your Bets* (enter a hedge maze), *Maze Runner* (conquer a maze), *Minotaur's Nightmare* (conquer a colossal maze, challenge, 500 XP).
- Debug commands (DEBUG build, OP): `/maze debug place <size> [style]`, `/maze debug where`, `/maze debug unlock`, `/maze debug relock`. `runClient` now defaults to the DEBUG build.

### Base
- Fabric 1.21.1 project base (Loom, Yarn, Java 21) with PUBLIC / DEBUG build variants.
- `config/mazecraft.json` configuration, Mod Menu + Cloth Config screen (optional).
- `/maze version`, `/maze reload` and `/maze debug info` (DEBUG build only).
- GitHub Actions: build on push, release + Modrinth publishing on `v*` tags.
