# Changelog

All notable changes to MazeCraft will be documented in this file.

## [0.6.0-alpha.1] — Unreleased

Ariadne's Thread.

### Added
- **Ariadne's Thread** (`mazecraft:ariadne_thread`, Tools tab): 8 uses, 5 s cooldown, a golden particle thread visible only to its user for 8 seconds.
  - **Inside a maze**: traces the shortest way along the corridors (48 blocks shown) to the current objective — the lever of the next closed gate, then the chest once every gate is open. Closed gates are taken into account.
  - **Outside**: points toward the nearest maze of the current dimension (search radius 64 chunks, like explorer maps), with its distance and direction in the action bar.
- Found in maze chests (50% in small, 25% in medium mazes, every dimension) and in vanilla chests to find a first maze: dungeons 15%, mineshafts 8%, desert pyramids 15%, jungle temples 20%, shipwreck map chests 20%, stronghold corridors 15%, nether fortresses 15%, bastions 10%, end cities 15% (added through the Fabric loot event, datapack-friendly).

## [0.5.0-alpha.1] — 2026-09-20

Enemies.

### Added
- **Guardians**: 2–4 persistent mobs per zone, placed when the maze generates, dead ends first (never on the plaza, the entrance cell or a lever cell). Deeper zones get better armor, capped by maze size: small none → leather, medium none → iron, large leather → iron, colossal iron → diamond.
- **Lever ambushes**: pulling a lever spawns a wave in the corridors 6–14 blocks around the player (small 1 → 3 mobs, medium 2 → 4, large 2 → 5, colossal 3 → 6; always at least a leather helmet so they don't burn in daylight), with smoke and an evoker sound. The mobs target the player.
- **Maze Champion**: the last lever (plaza gate) also summons a named champion — small: 2× health, iron gear (enchant 5); medium: 2.5×, diamond (12); large: 3×, diamond (20); colossal: 4×, diamond (30) — boss bar within 48 blocks.
- **Patrols**: while a survival/adventure player is inside an unconquered maze, a mob of the maze's style appears in a corridor 12–24 blocks away, out of the player's line of sight — day and night, in every dimension. Every 30 s in a small maze (medium ×0.75, large ×0.6, colossal ×0.5), capped at 3/4/5/6 patrol mobs alive around the player; stops once the maze is conquered. Config: `enablePatrols`, `patrolIntervalSeconds`.
- Guardians and patrol mobs without armor still get a leather helmet: zombies and skeletons no longer burn in daylight.
- The champion appears **on the central plaza**, next to the chest, and **the chest stays locked while it is alive** — saved with the maze, so it holds even if the champion wanders into unloaded chunks or the server restarts (`/maze debug relock` resets it).
- Mob pools per style: zombies / skeletons / spiders (hedge, cherry, savanna, taiga), + witches (dark forest), husks (desert, badlands), strays (snow), cave spiders (jungle), bogged & slimes (swamp), wither skeletons & blazes (fortress), hoglins & piglin brutes (crimson), endermen & wither skeletons (warped), wither skeletons & skeletons (soul), magma cubes & blazes (basalt), endermites, shulkers & endermen (End).
- No enemies in peaceful. Config: `enableGuardians`, `enableAmbushes`, `enableChampion`, `enemyMultiplier` (0–5), in Mod Menu under "Enemies".
- Advancement *Champion Slayer* (defeat a Maze Champion).

## [0.4.0-alpha.1] — 2026-09-20

The End.

### Added
- **End maze** (`mazecraft:maze_end`, end highlands & midlands — the outer islands): open-air, like Overworld mazes. End stone brick walls, purpur pillar posts, purpur floor, obsidian plaza, end rods on the posts, iron-bar gates, 6-block ring (purpur, end stone bricks, purpur pillars, obsidian — never end stone, so no chorus grows in or next to the maze). Mazes never generate over the void.
- **Bigger mazes in the End**: 20% small, 30% medium, 30% large, 20% colossal (a size that doesn't fit the island falls back to a smaller one). Own structure set (one maze per ~512 blocks).
- **End loot** (`mazecraft:chests/end_maze_<size>`): ender pearls, diamonds, shulker shells, end crystals, top-tier enchanted gear; dragon head in large and colossal mazes; **elytra**, netherite upgrade template and 4–6 shulker shells guaranteed in colossal ones.
- **Mazes avoid other structures** (all dimensions, vanilla and modded): a maze doesn't generate where an end city, village, temple, igloo, mansion, outpost, fortress or bastion could start within ~5 chunks of its footprint — a smaller size is tried first. Ruined portals and beached shipwrecks are avoided too, with a tighter radius. Not avoided: mineshafts, strongholds, ancient cities, trial chambers, buried treasures (underground, never reached by a maze), ocean ruins (mazes never generate over water) and nether fossils (one every 2 chunks in soul sand valleys: avoiding them would remove every soul maze).
- Advancements *The End of the Line* (enter an End maze) and *Master of the Labyrinth* (conquer a colossal End maze, challenge, 1000 XP).

## [0.3.0-alpha.1] — 2026-09-20

The Nether.

### Added
- **Nether mazes**, buried in the rock like a fortress between Y=40 and Y=80, at the height where the surrounding rock is the most solid (never hanging over the lava sea or in the middle of a cavern; smaller sizes are tried if nothing fits): a sealed outer wall keeps lava and netherrack out, a roof covers the whole maze with lights set into it, and a single doorway opens on the side where the surrounding rock is the most open (usually a cave). Found with `/locate structure #mazecraft:mazes` or by exploring, like fortresses. Own structure set (one maze per ~512 blocks).
  - **Fortress** (nether wastes): nether bricks, red nether brick posts, cracked brick floor, glowstone, nether brick fence gates.
  - **Crimson** (crimson forest): nether wart walls, crimson stems and planks, shroomlights.
  - **Warped** (warped forest): warped wart walls, warped stems and planks, shroomlights.
  - **Soul** (soul sand valley): bone walls, polished blackstone, soul soil floor, hanging soul lanterns (darker: more mobs).
  - **Basalt** (basalt deltas): polished basalt walls, blackstone, gilded blackstone plaza, glowstone.
- Nether loot tables per size (`mazecraft:chests/nether_maze_<size>`): ancient debris, netherite scraps, gold blocks, ghast tears; netherite upgrade template in large and colossal mazes, 2 netherite ingots in colossal ones.
- **Fortress look for Nether mazes**: no more netherrack blob under the maze — a styled underside slab, 2×2 support pillars every 12 blocks going down through air and lava to the ground (like vanilla fortresses), and an outer wall with a plinth and cornice in the post block. No colossal mazes in the Nether (a colossal roll becomes large).
- **One-time clean-up of every maze chunk** (all dimensions) the first time it is fully loaded: anything neighbouring chunks' decorations spilled into the maze afterwards (basalt columns, lava deltas, tree branches...) is removed. Opened gates, the chest and snow layers are kept; conquered mazes are never touched.
- Advancements *Brick by Brick*, *Seeing Red*, *Warped Perspective*, *Lost Souls*, *Delta Force*.
- Nether mazes are built at the last decoration step: basalt columns, deltas, mushrooms or soul fire generated in the same chunk are cleared by the maze. Roofs are never netherrack, nether wart, blackstone or basalt, so no weeping vines or glowstone grow under them.

## [0.2.0-alpha.1] — 2026-09-19

New biome styles.

### Added
- **Desert maze** (`mazecraft:maze_desert`, desert): cut sandstone walls, chiseled sandstone posts, smooth sandstone floor, terracotta plaza, iron-bar gates.
- **Snow maze** (`mazecraft:maze_snow`, snowy plains & snowy taiga): packed ice walls, spruce posts, snow floor, spruce plaza, spruce gates.
- **Jungle maze** (`mazecraft:maze_jungle`, jungle, sparse & bamboo jungle): mossy cobblestone walls, jungle log posts, mossy stone brick floor, bamboo gates, and a wider 8-block ring (jungle trees are huge).
- **Badlands maze** (badlands, wooded & eroded badlands): terracotta walls striped by height (red / orange / white) with a cut red sandstone coping, red sandstone posts and floor, iron-bar gates.
- **Dark forest maze** (dark forest): dark oak hedges, soul lanterns, dark oak gates, 8-block ring.
- **Cherry maze** (cherry grove): cherry leaf hedges, cherry wood posts, plaza and gates, 6-block ring.
- **Swamp maze** (swamp, mangrove swamp): mangrove hedges, mangrove log posts, packed mud floor, mangrove gates, 8-block ring. Rare: mazes never generate over water.
- **Savanna maze** (savanna, savanna plateau): acacia hedges and gates, 8-block ring (acacias spread far).
- **Taiga maze** (taiga, old growth pine & spruce taiga): spruce hedges and gates, 8-block ring (giant spruces).
- No vines on the mazes: natural vines don't generate in chunks crossed by a maze (a maze can straddle a jungle or swamp border) and existing vines don't spread into a maze.
- Advancements *Lost in the Dunes*, *Frozen Paths*, *Overgrown Ruins*, *Painted Walls*, *Afraid of the Dark*, *Petal Path*, *Muddy Waters*, *Savanna Stroll* and *Needle in a Maze* (enter each maze type).
- **Varied ring floor**: the ring around each maze is now a weighted mix of blocks per style (hedge: dirt path, gravel, packed mud, mossy cobblestone; desert: smooth, regular, cut and chiseled sandstone; jungle: mossy / cracked / plain stone bricks, cobblestones, andesite; snow: snow, packed ice, spruce planks). The pattern is derived from block positions, so it's identical across chunks and reloads. The approach path in front of the entrance stays plain.
- Maze structures now take a `"style"` field in their worldgen JSON; all styles share the `mazecraft:mazes` structure set (one maze per ~640 blocks, whichever style fits the biome).

## [0.1.0-alpha.1] — 2026-09-19

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
