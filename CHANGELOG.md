# Changelog

All notable changes to MazeCraft will be documented in this file.

## [0.9.0-alpha.1] — Unreleased

Sealed gateways: the progression is now enforced, not just tracked.

### Added
- **Sealed Gateway** (`mazecraft:sealed_gateway`) — the mod's first block. A shimmering
  membrane closing the entrance of every medium and large maze: amber for the medium step,
  violet for the large one. Small mazes are the entry step and are never sealed.
  Indestructible, drops nothing, never obtainable, and it **stays after the maze is
  conquered** — it still gates the other players on the server.
- **Per-player passage.** You walk through a gateway only once you have cleared the previous
  step of that same style. A friend who has not cannot follow you in, and clearing the forest
  mazes opens nothing in the jungle.
- **Key fragments** (`mazecraft:key_fragment`): clearing all three steps of a style grants a
  fragment carrying that style's name. Sixteen of them will open the Labyrinth of Kronos
  (1.0.0). They are ordinary tradeable items on purpose — a collection, not a credential:
  the door reads your own progression, so borrowed fragments get nobody in.
- Existing mazes are **retro-fitted**: a maze generated before 0.9.0 gets its gateway placed
  the first time a player comes near, so 0.8.0 worlds carry over. No new world needed.

### Fixed
- **The central chest now requires every lever to have been pulled.** The only lock used to be
  "a champion is alive", and the champion is summoned by the *last* lever — so reaching the
  plaza without pulling any lever (flying in, or any future hole in the walls) opened the chest,
  granted the advancement and cleared the progression step for free. The rule is now the one the
  maze is built around, and it applies to everyone rather than special-casing a game mode.

- **Mazes no longer cut through villages.** Structure avoidance assumed every other structure
  fitted within 5 chunks of its start chunk — a big plains village or a nether fortress reaches
  well past that. Sprawl is now per structure set (fortresses 9, villages and End cities 8,
  mansions 7, monuments 6) and, in the other direction, compact one-building structures drop to
  1–3 chunks, so sterilising ground around an igloo no longer costs maze spots.
- The same check sampled the biome **only at sea level**, while vanilla validates a surface
  structure at terrain height; on a plateau the two differ, so a village that did generate was
  read as "no village here". Both heights are now sampled. Every vanilla structure set name used
  by the check is verified against the game jar, so a typo cannot silently disable an entry.

- **Two mazes could generate on top of each other.** 0.8.0 gave every style and size its own
  spread grid, but structure avoidance skipped our own namespace wholesale — so nothing stopped
  a small hedge maze from landing inside a medium one. Mazes now avoid each other, with a rule
  that lets exactly one of the pair step aside (the bigger footprint wins, ties broken by
  style); if both backed off, neither would generate. Verified over all 1128 pairs of the 48
  maze kinds. Two mazes of the *same* kind were never at risk: their shared grid guarantees a
  gap of 160 / 208 / 256 blocks, wider than the footprint it has to hold.
- **Trees are no longer sliced flat along the maze edge** — and nothing is removed to achieve it.
  The cut was self-inflicted: generation forcibly cleared the air above the whole box, margin ring
  included, which sawed through any canopy leaning over it. Nothing can take root in the ring (its
  floor is never a soil block) and the maze proper sits further from the nearest possible trunk
  than a canopy can reach, so no tree ever grew into the maze — generation simply cut the ones
  that were already there. The clearing now skips logs and leaves over the ring, and overhanging
  branches stay whole.
  - Three removal-based attempts were tried and discarded first: clipping the overhang leaves bare
    poles, flood-filling the connected clump does nothing in a dense forest where every canopy
    touches its neighbour's, and uprooting whole trunks within a band works but shaves a 10-block
    bald ring around every maze. Not cutting in the first place beats all three, leaves the
    surroundings untouched, and deletes code rather than adding it.
- **The sealed gateway closes itself again.** The check that keeps it whole probed only one
  corner of the plane, so a hole knocked out of the middle stayed open for good. All of its dozen
  blocks are checked now. Breaking it in creative is still possible — creative breaks bedrock too
  — it just seals back up a moment later, and it never granted progression anyway: the chest
  requires every lever regardless of game mode.
- **Legacy structure ids removed from the tags.** The pre-0.8.0 `mazecraft:maze_<style>` entries
  were referenced by no structure set, so `/locate structure mazecraft:maze_snow` searched for
  something that can never generate and reported nothing nearby. Only `maze_<style>_<size>` is
  listed now.

- **Large mazes no longer cut hillsides into an escarpment.** The relief a spot may have was
  raised to 18–20 blocks in 0.8.0 so large mazes would still be findable; a flat 149-block
  platform on 18 blocks of relief is a mesa. Back to 10 / 12 / 14 / 16, with the rarity paid for
  by tighter grids instead (medium 36 → 32, large 52 → 40 chunks) — density is the knob that
  does not hurt the look.

### Technical
- The gateway has **no collision**, like a nether portal. Minecraft has no block that is solid
  for one player and not another, and faking one with per-player block packets produces ghost
  blocks and suffocation. So passage is decided in `MazeBarrier`, in three layers: the block is
  the visible signal, `onEntityCollision` shoves back a player who may not pass (naming the step
  they owe, since a bare "access denied" leaves no way to work the rule out in game), and a
  periodic sweep teleports out anyone who got in anyway. The sweep is the real guarantee — a
  one-block-thick plane without collision can be crossed in a single tick at elytra speed.
- Players in a boat or on a horse are handled: the vehicle collides, not the rider.
- Mobs, items and projectiles pass freely; only players are gated.
- The anti-climb teleport already dropped players 2 blocks *outside* the entrance, so it lands
  them on the correct side of the gateway with no change needed.
- `MazeLayout.entranceGap()` / `entranceAlongX()` expose the carved doorway. Verified over
  2400 generated mazes (4 sizes × 4 sides × 150 seeds): all 7200 doorway cells sit on the outer
  wall line, are carved open, are contiguous, and have the approach path directly outside.

## [0.8.0-alpha.1] — 2026-10-01

Per-player progression: small → medium → large, one branch per style.

> ⚠️ **Breaks existing alpha worlds.** Mazes already generated keep their blocks and still
> work (their legacy structure stays registered), but every new maze now comes from a new
> structure, and progression starts from zero. A fresh world is recommended.

### Added
- **Progression per style and per player.** Each of the 16 styles has to be cleared one step at
  a time — small, then medium, then large — and progress is tracked **per player**, not per
  world: on a server, nobody inherits anyone else's progress. Clearing the forest mazes unlocks
  nothing in the jungle.
- **48 new structures and structure sets** (`maze_<style>_<size>`), one spread grid per style
  *and* per size, so a given style/size pair is always findable instead of depending on a size
  roll. `/locate structure mazecraft:maze_hedge_medium` now works, as do the tags
  `#mazecraft:style/<style>`, `#mazecraft:size/<size>` and `#mazecraft:step/<style>_<size>`.
- **48 advancements** `conquer_<style>_<size>`, forming 16 branches of three steps hanging off
  each `enter_<style>`. Granted when the central chest is opened; they *are* the progression
  (same source of truth as the gate checks coming in 0.9.0), so the tree can never disagree
  with the game state. 50 / 150 / 400 XP per step.
- **Ariadne's Compass** (`mazecraft:ariadne_compass`, Tools tab): lists the steps you still owe
  in the dimension you are standing in and locates the one you pick. Right-click to search
  (coordinates, distance, a line of particles pointing the way; 5 s cooldown, 100-chunk radius),
  sneak + right-click to switch target. Crafted from a compass, 4 gold ingots and 4 string.
  With 48 mazes to clear, finding one specific style and size by flying around is not a game —
  this makes it a decision instead.
- `/maze progress`: your own progression, 16 lines, cleared / next / locked per step.

### Changed
- **A maze's size is now fixed by its structure** and is never silently downgraded when the
  terrain is poor. A "medium" structure that quietly built a small maze would hand the player
  the wrong progression step; a spot that doesn't fit simply gets no maze.
- To compensate, the relief a spot may have grows with the size (10 / 14 / 18 / 20 blocks
  instead of a flat 12), otherwise large mazes would be nearly impossible to place.
- **Colossal mazes are no longer a step of their own**: one large maze in 8 is upgraded to a
  colossal one (Overworld and End; never in the Nether), with the same loot tables as before.
  A colossal maze counts as the large step of its style.
- The four size-weight config options are gone: how often each size appears is now datapack
  territory (`spacing` / `separation` per structure set). Spacing is 32 for small, 44 for
  medium, 52 for large (first in-game `/locate` run put the nearest small hedge maze almost
  3000 blocks out, which is too far for the entry step of a branch).
- `enter_<style>` advancements now trigger on the style tag, so any of the three sizes counts.

### Fixed
- Ambush mobs no longer attack a player in creative or spectator mode: the ambush forced a
  target on them, which overrode the vanilla rule that hostile mobs ignore creative players.
  The mobs still spawn, they just behave normally around that player (patrols already did this).
- Ariadne's Compass can actually be crafted: the recipe was written in the 1.21.2 ingredient
  format (`"S": "minecraft:string"`), which 1.21.1 does not accept — it needs the object form
  (`"S": {"item": "minecraft:string"}`), so the recipe silently never loaded. It also had no
  unlock advancement, so it would not have shown in the recipe book either. Both fixed against
  vanilla's own `compass.json`.

### Technical
- New `fr.mazecraft.progression.MazeProgress`: progression is read from the player's own
  advancement tracker rather than a side table — already per-player, already saved, already
  synced to the client, and nothing to migrate.
- The three pre-0.8.0 structure sets (`mazes`, `nether_mazes`, `end_mazes`) are emptied rather
  than deleted, and the 16 legacy `maze_<style>` structures stay registered: old chunks keep
  deserializing, but no new maze is ever placed from them.

## [0.7.0-alpha.1] — 2026-09-20

The Minotaur.

### Added
- **Minotaur** (`mazecraft:minotaur`): guards the central plaza of **large and colossal** mazes — summoned by the last lever instead of the Maze Champion (small and medium mazes keep the Champion). 150 HP in large mazes, 250 in colossal ones; armor 8, heavy knockback-resistant melee with strong knockback, purple boss bar, chest locked until it dies.
  - **Charge**: roars for 1 s (rears up, arms raised), then rushes in a straight line without turning. Hitting the player deals 16 damage with strong knockback; hitting a wall **stuns** it for 3 s (head spinning, crit particles, +50% damage taken).
  - **Rage** under 50% health: +30% speed, charges twice as often, faster blows.
  - Custom model (bull head, horns, axe) and placeholder texture; walk, attack, wind-up, charge and stun animations.
- Minotaur spawn egg (Spawn Eggs tab), for testing.
- **Minotaur sounds** (`mazecraft:entity.minotaur.*`: ambient, hurt, death, step, roar, charge hit, stun) with subtitles, and **boss music** (`mazecraft:music.minotaur`, currently music disc "5") played to players within 48 blocks while it lives, on the Music volume slider, stopped when you leave or it dies. All sounds are defined in `sounds.json` on top of vanilla sounds, so a resource pack can replace them with custom audio.
- **Minotaur Horn** (`mazecraft:minotaur_horn`, epic): always dropped by the Minotaur. Blowing it gives Strength I and Speed I for 30 s to every player within 16 blocks; 2 min cooldown. The Minotaur also drops 4–8 beef (cooked if it burns) and 3–6 leather; 100 XP (200 in colossal mazes).
- Advancement **Theseus** (*Thésée*): slay the Minotaur (challenge, 500 XP).

## [0.6.0-alpha.1] — 2026-09-20

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
