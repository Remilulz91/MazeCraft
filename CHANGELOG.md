# Changelog

All notable changes to MazeCraft will be documented in this file.

## [0.2.0-alpha.1] — Unreleased

New biome styles.

### Added
- **Desert maze** (`mazecraft:maze_desert`, desert): cut sandstone walls, chiseled sandstone posts, smooth sandstone floor, terracotta plaza, iron-bar gates.
- **Snow maze** (`mazecraft:maze_snow`, snowy plains & snowy taiga): packed ice walls, spruce posts, snow floor, spruce plaza, spruce gates.
- **Jungle maze** (`mazecraft:maze_jungle`, jungle, sparse & bamboo jungle): mossy cobblestone walls, jungle log posts, mossy stone brick floor, bamboo gates, and a wider 8-block ring (jungle trees are huge).
- **Badlands maze** (badlands, wooded & eroded badlands): terracotta walls striped by height (red / orange / white / plain), red sandstone posts and floor, iron-bar gates.
- **Dark forest maze** (dark forest): dark oak hedges, soul lanterns, dark oak gates, 8-block ring.
- **Cherry maze** (cherry grove): cherry leaf hedges, cherry wood posts, plaza and gates, 6-block ring.
- **Swamp maze** (swamp, mangrove swamp): mangrove hedges, mangrove log posts, packed mud floor, mangrove gates, 8-block ring. Rare: mazes never generate over water.
- **Savanna maze** (savanna, savanna plateau): acacia hedges and gates, 8-block ring (acacias spread far).
- **Taiga maze** (taiga, old growth pine & spruce taiga): spruce hedges and gates, 8-block ring (giant spruces).
- Advancements *Lost in the Dunes*, *Frozen Paths*, *Overgrown Ruins*, *Painted Walls*, *Afraid of the Dark*, *Petal Path*, *Muddy Waters*, *Savanna Stroll* and *Needle in a Maze* (enter each maze type).
- **Varied ring floor**: the ring around each maze is now a weighted mix of blocks per style (hedge: dirt path, gravel, packed mud, mossy cobblestone; desert: smooth, regular, cut and chiseled sandstone; jungle: mossy / cracked / plain stone bricks, cobblestones, andesite; snow: snow, packed ice, spruce planks). The pattern is derived from block positions, so it's identical across chunks and reloads. The approach path in front of the entrance stays plain.
- Maze structures now take a `"style"` field in their worldgen JSON; all styles share the `mazecraft:mazes` structure set (one maze per ~640 blocks, whichever style fits the biome).

## [0.1.0-alpha.1] — Unreleased

First mazes in the world: the hedge maze.

### Added
- **Hedge maze structure** (`mazecraft:maze_hedge`) generated naturally in plains, sunflower plains, meadows, forests, flower forests and birch forests — in new worlds and in not-yet-generated chunks of existing worlds. Findable with `/locate structure mazecraft:maze_hedge`.
- **Four random sizes**: Small (61×61), Medium (101×101), Large (149×149), Colossal (221×221). Relative weights configurable (`weightSmall`, `weightMedium`, `weightLarge`, `weightColossal`, default 45/35/15/5).
- Terrain-aware size: if the rolled size doesn't fit the terrain, a smaller one is tried.
- "Growing tree" maze layout (mix of long winding corridors and many side branches): exactly **one path** from the entrance to the center, every other branch is a dead end. One entrance on a random side; the 3×3-cell central plaza has a single door, on the side facing away from the entrance.
- **Central chest** with dedicated loot tables per size (`mazecraft:chests/maze_<size>`): diamonds, enchanted books and gear, golden apples; totems and netherite in large and colossal mazes.
- Terrain handling: mazes skip spots with water or more than 12 blocks of height difference; the terrain is blended around the maze like a village (raised or lowered to the maze floor with a smooth slope, no cliffs, no caves), and any remaining gap under the floor is filled.
- No vegetation inside the maze: the maze is generated before trees and plants, and its corridor floor and surrounding 5-block ring are dirt path, where nothing can grow.
- **Gates & levers**: the unique path to the center is cut by gates (dark oak fences filling the opening): 2 in a small maze, 3 medium, 4 large, 5 colossal — the last one closes the central plaza. Each gate splits the maze into zones; the lever that opens a gate is hidden at the farthest dead end of the zone before it (hanging on a log set into the hedge). Pulling it opens the gate for good, for every player (saved per maze).
- **Entrance placement**: the entrance faces the side where the surrounding terrain is closest to the maze floor, and a flattened 5-block dirt-path ring surrounds the maze — no more entrance opening into a cliff.
- **Protection until conquered** (survival/adventure; creative and spectator are exempt): blocks can't be broken or placed inside a maze until its central chest is opened. Ender pearls landing in (or thrown from) the maze don't teleport; buckets, flint and steel, fire charges and chorus fruit can't be used in or near it; explosions don't break maze blocks. Refused block placements are immediately given back in the inventory. Players on top of the walls or flying over the maze are sent back to the entrance. Both rules configurable (`protectUntilSolved`, `preventWallWalking`).
- **Maze conquered**: opening the central chest lifts the protections for everyone (saved per world).
- **Advancements**: *MazeCraft* (enter a maze), *Hedge Your Bets* (enter a hedge maze), *Pull the Bobbin* (pull a maze lever), *Maze Runner* (conquer a maze), *Minotaur's Nightmare* (conquer a colossal maze, challenge, 500 XP).
- Debug commands (DEBUG build, OP): `/maze debug place <size> [style]`, `/maze debug where`, `/maze debug levers`, `/maze debug unlock`, `/maze debug relock`. `runClient` now defaults to the DEBUG build.

### Base
- Fabric 1.21.1 project base (Loom, Yarn, Java 21) with PUBLIC / DEBUG build variants.
- `config/mazecraft.json` configuration, Mod Menu + Cloth Config screen (optional).
- `/maze version`, `/maze reload` and `/maze debug info` (DEBUG build only).
- GitHub Actions: build on push, release + Modrinth publishing on `v*` tags.
