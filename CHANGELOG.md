# Changelog

All notable changes to MazeCraft will be documented in this file.

## [1.0.0-alpha.5] — 2026-10-06

### Added
- **Three music discs**, with their own items, their own songs and their own textures —
  nothing vanilla is overwritten. *Fil* turns up in any maze chest (10 %), *Airain* in the
  large and colossal ones (25 %), and *Astérion* waits in the hoard, guaranteed. The artist
  and title are shown in game when a disc plays, which is also where the CC-BY attribution
  lives.

### Fixed
- **Two of the three discs were silent.** Their OGG files carried a **Theora video stream**
  beside the audio: the source MP3s had cover art, the OGG container accepts video, and
  ffmpeg duly re-encoded the artwork into the file. Minecraft's loader expects a bare Vorbis
  stream and played nothing. The third track had no cover art, which is exactly why it was
  the only one that worked. Repaired by copying the Vorbis stream into a clean container —
  no re-encode, so no second generation of loss.
- **Disc textures now follow the vanilla silhouette.** They were drawn as full circles with a
  large coloured centre; a vanilla disc is an *ellipse* — a record seen in perspective — with
  a dark body and a small coloured label.
- **No maze over the world's spawn.** A maze covering it drops a new player inside a structure
  they may not be allowed to enter, and the barrier shoves them back out again on every death.
  Mazes now keep a cleared square around the origin, 198 blocks for a small one and 278 for a
  colossal. Kronos is exempt: it is forty blocks down, nobody spawns inside it. Honest limit:
  Minecraft chooses the spawn after worldgen, so this makes the case very unlikely, not
  impossible.

### Technical
- Levers were measured rather than assumed. One can sit two blocks from the gate it opens *as
  the crow flies* and look like a key taped to its own door — but the walk from that gate to
  that lever is **118 to 352 blocks at the median, and never under 6** across 6720 levers. A
  change to push levers further from gates was written, measured, found to move nothing, and
  reverted: it would have altered generation in every maze for no gain.

## [1.0.0-alpha.4] — 2026-10-03

### Changed
- **The Horn of Asterion has a voice of its own.** Both horns played the same sound event,
  so Asterion's announced itself with the subtitle "a bronze horn sounds". It now has its
  own event: a deeper call, with the beast's own breath layered under it.

## [1.0.0-alpha.3] — 2026-10-03

Kronos, part three: the arena.

### Added
- **The arena of Kronos.** The vault's central room grows from 3 × 3 cells to 5 × 5 — 19 × 19
  blocks — and is dug out of the floor: a 13 × 13 fighting floor two blocks down, reached by a
  ring of two steps, with the plaza door opening onto the rim rather than into the pit. The
  11 × 11 chest room of an ordinary maze leaves a boss that charges in a straight line no run-up
  at all; the extra headroom is for a boss taller than a corridor.
- **Ariadne's thread is the way out.** Used inside a maze that has been conquered, it does what
  it does in the myth and pulls you back to the entrance. Walking eight hundred blocks back
  through corridors you have already solved is not gameplay, it is a chore. Only once the maze
  is beaten — before that, finding the way is the whole point.
- **The hoard of Asterion.** Minecraft hands out treasure in chests: you walk to a box, open
  it, and the reward is a list. This one is meant to be *looked at*. Under the arena floor,
  sealed from the day the vault generates, a 13 × 13 chamber: a floor of gold, heaps of bronze
  and raw gold and emerald standing on it, bronze pilasters, four soul lanterns, and a double
  chest on a pedestal against the far wall.
  - When Asterion falls the arena floor gives way, twelve fireworks go up in gold and verdigris,
    and a spiral stair drops eight blocks into the chamber. Only the way down is cut at that
    moment — a room raised block by block from a tick would be watched appearing through the
    hole, so the room itself is built with the vault.
  - About 49 gold blocks in the floor and 570 ingots' worth all told, which is roughly a bastion
    treasure room. Diamond blocks were in the first draft at one in a hundred; doubled where
    heaps stack, the worst room came out at **ninety diamonds**, so they are gone — diamonds
    stay in the chest, where their count is bounded.
  - What is where is a pure function of the block's coordinates. Drawn from the `Random` handed
    to a build pass it would have rearranged itself the first time a chunk was repaired, because
    generation and repair are seeded differently.
- **The Horn of Asterion**, one guaranteed in the chest. Strength II, Speed II and Resistance
  for two minutes to every player within 40 blocks. The Bronze Horn of a large maze is a good
  item; this is the same gesture at the far end of 48 mazes and a 400 HP boss, so it is plainly
  better rather than ten per cent better.
- **No chest at the centre of Kronos.** What is at the centre is the Minotaur, and the hoard is
  only opened once it is down.
- **The Minotaur of Kronos.** Not the colossal Minotaur with a longer health bar: 400 HP, 14
  damage, 12 armour, immovable, 600 XP, and **three phases** instead of its lesser kin's single
  rage.
  - **Phase 1**, above two thirds — the fight as it has always been, charging a little oftener.
  - **Phase 2**, at two thirds — it calls the labyrinth. The shifting walls of the whole vault
    drop from 45 seconds to 10, and two guards of the tomb come up out of the floor beside it.
  - **Phase 3**, at one third — it puts the lights out in pulses and gains another third of
    speed. The darkness is a status effect on the player, not lanterns taken out of the arena:
    removing blocks from a protected structure means putting them back on every path the fight
    can end by, and one missed path leaves the arena dark for good.
  - Phases are derived from health and only ever go forwards, so healing it cannot walk them
    back, and nothing is saved — a reload recomputes the phase on the first tick and can never
    disagree with the boss's own health.
- **A hide of its own.** Same model, same bones, same animations, a different skin: the brown
  of a beast replaced by the slate of the tomb, horns and hooves gone to verdigris like
  everything else down there, eyes left burning — on a slate-coloured animal a brown eye
  disappears. It also stands a head taller (rendering only; its hitbox is the Minotaur's).
- **The door of the arena shuts behind you.** Step in with the Minotaur alive and the plaza door
  closes; it opens when the Minotaur is dead, or when you are.

### Changed
- **The Key of Kronos now opens the door, and is spent doing it.** The door used to read the
  player's advancements directly and the key only pointed at the vault — so everything the key
  was for had already happened by the time you held it, and it was a souvenir. Turning it in
  the lock is now the act itself: the key dissolves, and that door is open to that player for
  good. Entry is gated on having turned it, not on the 48 steps; the steps are what earn the
  key.
  - Spending it is only safe **because finding Kronos moved to the compass** in the same
    change. A key that both opened the door and was the only way to find the place again would
    have become a trap the moment it was consumed — this is the one thing that made the idea
    workable rather than dangerous.
  - And a key lost before it is turned cannot lock anyone out of the endgame: a player who has
    all 48 steps and no key is handed another at the door.
  - New advancement **The Key Turns**, now the root of the Kronos tab, with **Theseus** hanging
    off it — the tab reads door, then Asterion.
- **The compass does not die at the endgame.** Once every step is cleared its list of owed
  mazes is empty and it had nothing left to say. Kronos becomes its last target.
- **There is one Minotaur, and he has a name.** The beast guarding a large or colossal maze was
  called a Minotaur too, which the myth does not allow and which left two different creatures
  sharing a name. It is now a **Bronze Guardian** — one of the bull-wardens Daedalus forged for
  his lesser labyrinths — and the one at the bottom of Kronos is **Asterion, the Minotaur**.
  - The Bronze Guardian keeps everything it had, renamed in place: its drop is the **Bronze
    Horn** (same item, same recipe, same effect) and its advancement is **Daedalus' Herd**.
    Nothing moves to the endgame: Kronos needs all 48 mazes, so putting the horn there would
    have emptied the middle of the game to fill an ending that is already full.
  - **Theseus** moves up one rung, onto the only fight that earns it: slaying Asterion. It is
    the final challenge, in a tab of its own, 2000 XP.
  - Asterion gets a purple boss bar; the Bronze Guardian's turns bronze. Two identical purple
    bars would have said they were the same thing.
  - All of this is text. The identifiers do not move — `mazecraft:minotaur_horn`,
    `mazecraft:defeat_minotaur`, the entity `mazecraft:minotaur` — so there is no migration and
    horns already sitting in chests keep working.
  - Theseus is granted from code to everyone still standing in the arena, not only to whoever
    landed the last blow: the Guardian and Asterion are the same entity type, told apart by a
    tracked field no advancement predicate can read — and a fight that long should not hand its
    only reward to the last hit.

### Fixed
- **Phases could be skipped.** They were entered by jumping straight to whichever one the
  Minotaur's health fell into, so a blow that crossed both thresholds at once skipped phase two
  entirely — no guards of the tomb, no frenzy in the walls — and anything that killed Asterion
  outright skipped the lot. Phases now step one at a time, so each one is certain to have
  happened. (It is also why a test kill shows nothing: phase three's darkness is a status
  effect, and a creative player is rightly immune to it.)
- **Phase three is now visible to everyone**, not only to whoever it blinds: each pulse throws
  a cloud of smoke and soul particles across the arena with a low sound under it. Without that,
  the whole phase was invisible to anyone testing in creative.
- **The hoard's chest was destroyed on every chunk load, before anyone ever reached it.** The
  repair pass rebuilds the chamber with `placeChest` false — so it filled the chest's own
  position with air, which scatters a chest's contents across the floor and leaves no chest,
  and then never put one back. Teleporting to a fresh vault already showed a smashed chest and
  items lying on the ground. The two blocks the chest stands on are now never written by a
  repair at all, and the room's air is cleared with `SKIP_DROPS` so emptying it can never spill
  anything whatever ends up standing there later.
- **The double chest was not double.** Its halves were placed one on each side of the
  pedestal's middle block, a block apart — which is two single chests that cannot see each
  other, not a double chest. They are adjacent now (LEFT at the pedestal, RIGHT to its east,
  which is the partner side for a chest facing north).
- Both are caught by a test that walks the chamber through a generation and three repairs and
  fails if anything is ever written over the chest. Run against the old code it reports the
  overwrite twice and ends with air where the chest was.
- **Co-op progression was quietly broken.** Conquering hung off `markSolved`, which is the
  *maze's* state and is true only the first time anybody opens the chest. So the second player
  to open the same chest got nothing at all — no step, no key fragment — even though
  progression is per player by design. The two are now separate: the maze is marked solved
  once, and every player who opens the chest is credited, however late. Opening it twice still
  gives one player nothing twice.
- **Breaking the central chest skipped the maze entirely.** It fell through to the generic
  protection, which exempts creative players and can be turned off in the config — so the chest
  could be smashed, the loot taken, and the maze never conquered. Breaking it is now opening it
  by other means: same rule (every lever pulled, no champion alive), same reward, and the same
  refusal if they are not met.
- **Champions wander off, and the chest will not open while one is alive** — which left the
  player hunting a 101 × 101 maze for a stray zombie before they could finish. Every champion is
  now leashed to the plaza it guards, the way Asterion is to its arena: alone within 14 blocks,
  walked back past that, put back past 28. Nothing is stored for it — the plaza is found from
  where the champion is standing, so one that is genuinely outside its maze is left alone rather
  than dragged somewhere wrong.
- **Ariadne's thread now takes the shortcuts.** It was tracing the path over the layout, which
  says every movable segment is a wall, so it walked the player the long way round past a
  shortcut standing wide open in front of them. It reads the world now. The path is worked out
  afresh on every use, so a shortcut that shuts again is not a trap — the next use routes round
  it — and it cannot strand anyone, because these segments only ever add loops to a tree.
- **The Minotaur of Kronos stays in its arena.** It has a wander goal like any mob, and with no
  target it walked out of the open door and went for a stroll in the labyrinth, leaving the
  arena door with no boss behind it and a fight that never starts. Visible in creative, where
  vanilla rightly refuses to let it target the player, but not a creative-mode quirk: in
  survival the same thing happens whenever it loses its target for long enough. It is now
  leashed to the centre of its arena — left completely alone within 11 blocks so the fight is
  never nudged, walked back past that, and simply put back past 20, because a boss that spends
  five minutes pathing home through a maze is worse than one that reappears. The centre is
  saved with the entity, so a reload does not set it loose.

### Technical
- **The vault sits higher: floor range -50…-36 becomes -44…-36.** The hoard is dug ten blocks
  under the arena, and at -50 its own floor landed at **-60 — inside the bedrock**, which runs
  to -59. Worlds already holding a Kronos vault will place new ones at the new depth.
- The hoard stair is checked the way the surface shaft is, and for the same reason: both of its
  ends are where the two earlier mistakes were made. 135 step-to-step transitions over all nine
  possible vault depths — first step reachable from the chamber floor, last step flush with the
  arena, never more than half a block of rise, and the chamber clear of bedrock every time.
- **The arena door is computed, never stored.** Every pass works out what the door should be
  from two live facts — is a Minotaur of Kronos standing in the arena, and is a player in there
  with it — and makes the blocks match. A seal that is written down has to be cleared on every
  path the fight can end by, and one missed path walls a player in for good; this way a crash or
  a logout mid-swing can at worst leave the door briefly wrong.
  - The boss must be **in the room**, not merely alive. The saved "this vault has a champion"
    flag would have been enough to shut the door, and that is exactly the trap: the Minotaur
    wanders, so it can leave the arena through the open door before the player walks in, and the
    flag would then have sealed them into an empty room with the boss outside.
  - The pass refuses to touch a door whose lever has not been pulled. Without that it would have
    cheerfully *opened* the arena for any player merely standing in the vault.
  - The whole truth table of (boss in the arena × player inside × lever pulled) is enumerated in
    a test: there is no state in which the door is shut without the boss in there.
- `MazeLayout` takes a plaza radius. At radius 1 every expression is what it was, draw for draw:
  verified by hashing the block grid, the gates and the levers of **640 mazes** (4 sizes × 4
  entrance sides × 40 seeds) before and after — the two digests are identical, so the sixteen
  ordinary styles generate exactly as before.
- The plaza door was cut at a fixed offset of one cell. At radius 2 that is a wall line which no
  longer exists, so the room stayed sealed and the progression could not be computed at all
  (0 gates, 0 zones). It scales with the radius now.
- Verified over 200 colossal vaults at radius 2: floor fully connected with nothing walled off,
  5 gates and 5 levers, all 5 zones populated, the full 160 shifting walls still drawn. And over
  100 more: the dug-out area never reaches outside the plaza, and no doorway opens into the pit.

## [1.0.0-alpha.2] — 2026-10-03

Kronos, part two: the labyrinth moves.

### Added
- **The shifting walls.** Every 45 seconds the Labyrinth of Kronos rearranges: a third of its
  160 movable segments stand open, the rest are shut, and which third it is rotates. Shortcuts
  appear and are taken away while you are still inside. Two seconds of particles and a rising
  chime warn of each shift, and a segment is never closed on top of anyone — it simply waits for
  the next pass.
- **The shifting walls are chiselled deepslate.** They were built from the pillar block, which
  was wrong twice over: that block *is* what the structural pillars are made of, so a shifting
  wall could not be told from a corner, and a hundred and sixty bronze segments turned a
  deepslate tomb into a copper mine. A bronze rail let into the floor was tried next and hid the
  mechanism well, but drew a cross on the ground at every segment. The tell is now the wall
  itself, in a stone of the same family: at a glance down a corridor it is one more dark wall,
  looked at, the chiselled face is not the polished one.
- Config: **Shifting walls (Kronos)**, on by default.

### Changed
- **A wall only moves where it is worth moving.** The segments used to be drawn at random among
  every valid wall, which meant most of them opened onto a corridor three steps away — the wall
  ground open, and nothing happened. Each candidate is now scored by how far apart its two sides
  are along the maze, and only those past a bar that scales with the maze (18 cells for Kronos,
  one cell being four blocks) are eligible. Measured over 960 mazes: the old draw put a quarter
  of the segments between corridors three cells apart and over half under twelve; the new one
  gives Kronos forty segments that save **19 cells at the very least and 41 at the median** —
  over 150 blocks of walking, removed by one step through a wall.

- **Four times as many shifting walls: 40 → 160.** A colossal maze is 3025 cells and the walk
  from the entrance to the plaza crosses about 200 of them. At forty segments only **2.9** of
  them touched that walk, so a player could cross the whole of Kronos without ever watching a
  wall move — which is exactly what happened in testing. At 160 it is **11.3**, roughly one
  every seventy blocks of corridor, drawn from the ~880 walls that clear the detour bar, so the
  selection stays varied and the median detour is unchanged at 41 cells.
- The tick now tests distance against a single block before building a segment's full block
  list. With 160 segments, nearly all of them far from everyone, the far ones cost one
  allocation instead of fifteen.

### Fixed
- **The bottom step of the shaft is now on the doorway's side.** The spiral always began on the
  shaft's east face, whichever way the vault's doorway faced, so for three orientations out of
  four a player walking back out of the vault met a full block with nothing to step on and had
  to place one. The spiral now starts in the cell immediately inside the doorway. Checked over
  the four orientations and every shaft depth: 12 900 step-to-step transitions, none rising more
  than half a block or dropping more than one, and the old code fails that same check 45 times.

### Technical
- **The shifting walls give nothing away about the route.** 6.6 % of a colossal maze's cells lie
  on the direct walk to the plaza, and 7.1 % of the chosen segments border it — the selection is
  indistinguishable from uniform, so a copper wall is never a hint that you are going the right
  way. (The detour filter actually nudges them slightly *away*: 8.9 % of all eligible walls touch
  the route.)
- **Nothing can be sealed off, by construction.** The maze is a tree — exactly one path between
  any two cells — so opening a wall only adds a loop and closing it restores the tree. No
  connectivity check is performed anywhere, because none is needed.
- **No shortcut past a gate.** A loop crossing a zone boundary would hand the player the next
  zone without its lever, so a segment only qualifies when the cells on both sides sit in the
  same zone. Plaza cells, gates and the walls carrying levers are excluded outright.
- Both properties are **verified, not argued**: over 960 generated mazes (4 sizes × 4 entrance
  sides × 60 seeds) and 145 745 movable segments, opening every one of them never removed a
  reachable cell and never let a flood from the entrance zone reach a deeper zone or the plaza.
  Every colossal maze still yields the full 160 segments after the detour filter — it has
  around 880 eligible walls to draw them from, 720 in the worst case seen.
- Detours are measured on the cell tree by walking both sides up to their common ancestor, and
  checked by a second harness that rebuilds the cell graph from the block grid and runs its own
  breadth-first search — so an error in the scoring cannot vouch for itself.
- Which segments are open is a **pure function of the world time**, so nothing is saved and a
  reload cannot disagree with what the blocks say. The tick makes the world match the schedule
  rather than replaying events, which is also why a blocked closure simply happens later.

## [1.0.0-alpha.1] — 2026-10-02

Kronos, part one: the vault exists, and it can be found and opened.

### Added
- **The Labyrinth of Kronos** (`mazecraft:maze_kronos`): one of a kind, colossal (221 × 221,
  5 gates), buried between Y -50 and -36 under the Overworld, in a style of its own — polished
  deepslate and blackstone for the tomb, oxidised copper for the bronze, soul lanterns under a
  deepslate-tile roof, oxidised copper grates for the gates. Roughly one every 3200 blocks.
- **The way down**: a weathered ruin on the surface — a cracked slab, four bronze-capped columns,
  a lantern over the mouth — and a brick-lined shaft with a ladder, dropping straight onto the
  vault's doorway. The ruin is there from the first day of a world: finding the door long before
  being able to open it is the point.
- **The door of Kronos**: a third tier of the sealed gateway, verdigris bronze, which opens only
  for a player who has conquered all 48 mazes. It reports progress when it refuses.
- **Key of Kronos** (`mazecraft:kronos_key`): granted the moment the sixteenth fragment is earned,
  bound to the player who earned it, and useless in anyone else's hands. Right-click points the
  way to the vault — buried that deep, it would otherwise never be found.
- `/maze debug kronos` (debug builds): locates the vault and teleports to its ruin, so none of
  this has to wait on 48 cleared mazes to be testable.

### Fixed
- **The shaft no longer drops its ladders on the floor.** A ladder needs the wall behind it to
  exist already, and a structure is built one chunk at a time — wherever a chunk boundary ran
  between a ladder and its wall, the ladder was placed against nothing and popped off as an item.
  The climb is now a spiral stair of slabs, which stand on their own; verified that consecutive
  steps stay adjacent and inside the shaft, so it is walkable all the way up.
- **The shaft no longer gets plugged by its own vault.** Its foot sat inside the maze's
  bounding box, and both generation and the repair pass rewrite every column in that box, roof
  included — so a ceiling slab was dropped straight across the shaft, every time. The shaft now
  starts two blocks clear of the box and lands on the doorway the maze already leaves in its
  outer wall, which until now opened onto bare rock.
- **The climb needs no jumping.** The spiral was one full block per step, which is a jump each
  time — forty of them to get out. Two half-steps per block now (a bottom slab, then a top one),
  and the first starts on the shaft floor rather than a block above it, where it sat a block and
  a half out of reach. Checked end to end over a 117-block climb — getting onto the first step
  and off the last one included, which is where both mistakes were: 236 steps, half a block
  each, every pair adjacent and inside the shaft.
- **The lantern hangs from something.** The ruin is now a proper well-head — four bronze-capped
  posts around the mouth carrying a roof, with the lantern hanging under it.
- **The foot of the shaft opens onto the door.** The brick lining went all the way round at the
  bottom too, so the climb down ended in a sealed box with the vault's doorway walled off behind
  it. The face looking at the vault is now left open, three wide and three high — and only that
  face, not the corners beside it.

### Changed
- `MazeStyle.PROGRESSION` now backs everything that counts steps, lists branches or builds the 48
  structures, instead of `values()`. Kronos is a style but not a step: without this the target
  would have quietly slid from 48 to 51 and the door could never have opened.
- Structure avoidance knows the difference between a surface maze and a buried one. Kronos avoids
  ancient cities and mineshafts, which share its depth and which a surface maze rightly ignores,
  and skips the surface-only sets that can never reach it.
- Colossal is accepted as a declared size for Kronos alone; for every other style it stays what
  it was, a rare upgrade of a large maze.

### Notes
- The second level and the moving walls come in the next step, and the arena, the Minotaur of
  Kronos and the real hoard in the one after. The chest at the centre is a placeholder so the
  vault is solvable and testable now.
- Stacking two levels was planned for this step and deferred on purpose: it needs flags threaded
  through `MazePiece` for the chest, the support pillars and the lower level's doorway-to-nowhere,
  and that is shared code the sixteen ordinary styles depend on. Kronos as it stands required no
  change to `MazePiece` at all.

## [0.9.0-alpha.1] — 2026-10-01

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
