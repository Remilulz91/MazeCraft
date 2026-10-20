# MazeCraft

**Forty-eight mazes stand between you and what is buried under the Overworld.**

Somewhere in your world, a maze is waiting. Its walls take the style of the biome around
it: a hedge maze in the plains, carved sandstone in the desert, mossy ruins in the jungle,
packed ice in the frozen peaks, nether bricks, basalt or crimson stems below, end stone
and purpur beyond. Find your way in, pull the levers that open its gates, survive what
lives inside, and reach the chest at its heart.

Only one path leads to the centre. There are no shortcuts.

And the first maze is only the first rung.

---

## The ladder

Every style has to be earned in order: **small, then medium, then large.** Sixteen styles,
three steps each — **48 mazes** — and the door of a maze you have not earned will not let
you in. Walk at a medium desert maze before you have cleared a small one and the gateway
pushes you back, and tells you exactly what you still owe.

**Progression is per player.** Your friend clearing a maze does not clear it for you, and
being carried through the door is not possible: the gateway reads *your* record, not
whoever is standing next to you. Play the same world together and you each climb your own
ladder — open the same chest one after the other and you are each credited.

Clear all three steps of a style and that style yields a **key fragment**. Sixteen styles,
sixteen fragments.

Check where you stand at any time with `/maze progress`.

---

## How mazes generate

**Naturally, like vanilla structures.** They appear in new worlds **and** in chunks that
have not been explored yet in an existing one, so you can add MazeCraft to a world you
already play — just walk far enough. Each maze adapts to the ground it lands on: it levels
itself into the terrain in the Overworld, rises on fortress pillars over the Nether, and
floats on end stone in the End. It keeps its distance from other structures, so it will
not cut a village or an End City in half — and from other mazes, so two never overlap.

**16 styles**, each reading its biome and choosing its own walls, pillars, floor, lighting,
central plaza, even the banding on tall walls. Nether mazes get the full fortress treatment
— roof, plinth, cornice, hanging lights. End mazes get their own enclosed look.

**Three sizes, and a rare fourth.**

| Size | Footprint | Gates | Where |
|---|---|---|---|
| Small | ~61 × 61 blocks | 2 | everywhere |
| Medium | ~101 × 101 blocks | 3 | everywhere |
| Large | ~149 × 149 blocks | 4 | everywhere |
| Colossal | ~221 × 221 blocks | 5 | a rare upgrade of a large maze, Overworld and End |

A colossal maze counts as the large step of its style — the same rung, with a great deal
more wall between you and the middle, and one thing no other maze has: a vault. The Nether
has none; there is not enough room between the roof and the lava.

**Levers and gates.** Each maze is cut into zones around its centre. Every zone is closed
by a gate whose lever is hidden somewhere in the zone you are already in. Pull it and the
gate stays open for good — but pulling a lever also wakes the maze up.

**No shortcuts.** Until a maze has been conquered its walls cannot be broken, nothing can
be placed inside, ender pearls fizzle out and explosions do nothing. The central chest
refuses to open — or to be broken — until every lever has been pulled and whatever guards
it is down. Each protection can be switched off individually in the config. A colossal
maze's vault is the one thing that outlives the conquest: its walls and its door stay shut
to a pickaxe for good, because a four-digit code whose other answer is a pickaxe is not a
code.

---

## What lives inside

**Guardians** patrol from the moment the maze generates, themed after its style: spiders
and husks in the sand, skeletons in the ruins, blazes and wither skeletons in the Nether,
endermen and shulkers in the End. Their gear and their numbers scale with the size of the
maze and how deep into it you have come.

**Ambushes** trigger when you pull a lever. The deeper the zone, the worse it gets.

**Patrols** roam between zones, so a maze you have walked through once never stays quiet.

**The Champion** holds the central plaza of most mazes, and the chest stays shut until it
falls — even if you log out, leave, and come back a week later.

**The Bronze Guardian** waits in the large and colossal ones: one of the bull-wardens
Daedalus forged for his lesser labyrinths. A custom boss with its own model, a charge down
narrow corridors that leaves it stunned when it misses and hits a wall, a second wind under
half health, a boss bar, its own music, and its own drop — the **Bronze Horn**.

---

## The vault under a colossal maze

A colossal maze hides one more thing, and it is nowhere near the way to the centre.

At the end of its deepest dead end the floor opens on a spiral stair. Ten blocks down is a
room nobody built for a chest: twelve keys set into the wall — the ten digits, a reset and a
validate — a sealed bronze door, and whatever stands behind it. The room is cut in the
maze's own stone, so it is mossy brick under the hedges, sandstone in the desert, end stone
brick beyond.

The door wants four digits. They are not in the room.

Each of the maze's first four zones keeps one of them, on a plaque at the back of a dead
end, with a row of tally marks above it saying which of the four it is. So the code is
collected the way the maze is walked, one gate at a time — and never handed over, because a
dead end has to be entered to be read. A plaque is the same thing as a key on the wall,
which is the only instruction you get and the only one you need.

Type it wrong and the keys go dead for three seconds. Type it right and the door comes down
for everyone on the server, once and for good.

Behind it is the best chest in the maze — better than the one at its centre on every count,
and the surest place outside the Labyrinth of Kronos to turn up a music disc.

---

## Items

- **Ariadne's Thread** — traces the corridors to your next objective: the lever of the
  first closed gate, or the chest once they are all open. In a maze you have already
  conquered it does what it does in the myth instead, and pulls you back to the entrance.
  Found in maze chests.
- **Ariadne's Compass** — crafted from a compass, gold and string. It knows which step you
  owe next in each of the sixteen styles and locates the one you pick. Right-click to find
  it, sneak-click to change target.
- **Key Fragment** — one for each style cleared from end to end.
- **Bronze Horn** — dropped by the Bronze Guardian. Blow it for Strength and Speed to
  every player near you.
- **Three music discs** — *Fil* turns up in any maze chest at one in ten, *Airain* only in
  the large and colossal ones at one in four, and *Astérion* is what the hoard at the bottom
  of the Labyrinth of Kronos always gives up. The vault of a colossal maze holds the best
  odds on the first two.

Nothing here comes out of a recipe-book grind. You find them, or you earn them.

---

## And something under the Overworld

There is a weathered well-head out there, standing open from the first day of a world,
with a brick shaft dropping into the dark. At the bottom of it is a door.

It will not open for you. It will tell you how far off you are.

What is behind it is called the **Labyrinth of Kronos**, and it is what the sixteen
fragments are for. That is all you get here.

---

## Advancements

A full tree tracks the climb: your first maze, your first gate, a centre reached, each of
the 48 steps in its own branch, every style finished, a vault opened on four digits — and a
tab of its own for what is waiting at the bottom of that shaft.

---

## Commands

| Command | OP | Effect |
|---|---|---|
| `/maze progress` | no | How far up the ladder you are, style by style |
| `/maze version` | no | Mod version and build type |
| `/maze reload` | yes | Reload the configuration |

Vanilla `/locate structure #mazecraft:mazes` works too.

---

## Configuration

Stored in `config/mazecraft.json`, editable in game with **Mod Menu** + **Cloth Config**
(both optional):

- **Generation** — each dimension, maze rarity, and the weight of each size in the roll
- **Protection** — block breaking, block placing, wall walking, ender pearls and
  explosions, each separately
- **Enemies** — guardians, ambushes, patrols and champions, each separately, plus an
  overall difficulty multiplier

---

## Requirements

- Minecraft **1.21.1**, Fabric Loader 0.16.5+
- **Java 21**
- **Fabric API** (required)
- **Cloth Config** and **Mod Menu** (optional, for the config screen)
- Install on **both client and server**: the mod adds an entity, blocks, items and sounds
  of its own.

Not compatible with Forge/NeoForge. Works alongside other worldgen mods, though mazes will
simply avoid the structures they can detect.

---

## Feedback & ideas

Got a bug, a balance complaint, or an idea for a maze style, a trap or a boss?

👉 **[Fill in the MazeCraft feedback form](https://forms.gle/Frecu3mG1zPK9v928)**

It takes a minute and it is the fastest way to get something looked at. Bug reports can
also go to the issue tracker linked in the sidebar.

---

## Music credits

The three music discs use tracks by Kevin MacLeod, licensed under Creative Commons
Attribution 4.0. The track title and artist are also shown in game whenever a disc is
played.

```
Dark Walk Kevin MacLeod (incompetech.com)
Licensed under Creative Commons: By Attribution 4.0
https://creativecommons.org/licenses/by/4.0/

Ancient Rite Kevin MacLeod (incompetech.com)
Licensed under Creative Commons: By Attribution 4.0
https://creativecommons.org/licenses/by/4.0/

Final Battle of the Dark Wizards Kevin MacLeod (incompetech.com)
Licensed under Creative Commons: By Attribution 4.0
https://creativecommons.org/licenses/by/4.0/
```

The tracks were converted to mono OGG Vorbis for in-game use; no other change was made.

---

## How this mod is made

MazeCraft is built in an iterative loop with Claude (Anthropic): I design the structures
and the mechanics, I play-test every build and report what breaks, and the AI writes and
fixes the code. Most of the entries in the changelog started as something I ran into in
game. The project carries Modrinth's AI disclosure accordingly.

The icon, the banner and the screenshots are mine. The derived entity and item textures
were produced by script from my own original artwork, not by an image generator.

---

## Development status

MazeCraft is in **alpha**: everything below is implemented and playable, but balance is
still being tuned and world-save compatibility between alpha versions is not guaranteed.
Back up your world before updating.

- [x] Generation, 16 styles, four sizes, three dimensions
- [x] Levers, gates and anti-cheat protection
- [x] Guardians, ambushes, patrols, Champions
- [x] The Bronze Guardian
- [x] Ariadne's Thread and Ariadne's Compass
- [x] Three music discs
- [x] Per-player progression across all 48 mazes
- [x] Sealed gateways and key fragments
- [x] The vault of the colossal mazes, and its four-digit code
- [x] The Labyrinth of Kronos, and what sleeps at its centre

Planned: traps, more styles, custom textures and an audio pass, and whatever the feedback
form brings in.

---

License: MIT
