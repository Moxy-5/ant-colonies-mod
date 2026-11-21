# The bullfrog's polish (planned 2026-11-20 with the user; built the same day, every step: change log in `docs/HISTORY.md`)

The user's asks after the bullfrog went into the game: does it hop up a block, how should it use its lunge, does it
slip smoothly into the water; the tongue should really aim at one fly of the cloud; the blink did not show in the films.
Animals are fun first (CLAUDE.md): real frog habits used where they make the game better. Every number is my pick.
Every clip change follows the animation checks rule (offline check, then filmed in the game frozen and stepped, three
sides). Animal-only work: no guard films.

## What is there now (read from the code, 2026-11-20)
- **Hop up a block:** built, never filmed or tested. When its route climbs more than half a block the hop kicks up 0.45
  a tick instead of 0.24 (a peak of about 1.2 blocks) and 0.8 as far forward (`BullfrogEntity.hopTick`). Risk seen in the
  code: its step height is 1 block, so in the slow last-bit crawl, or while a hop is resting, it can slide straight up a
  full block with no hop at all. Down a block it simply hops off and drops (no harm: falls hurt only past 3 blocks).
- **Lunge:** an ambush only. Hungry, it waits; a barb in the water, a snail or a young crab within 1.5 blocks of its
  mouth along a clear line gets a turn, a push of up to 0.3 blocks and the jaws shut on tick 6 (`LungeGoal`). The same
  lunge is its bite at a player who stays close after it puffs up (`ThreatGoal`). It never moves toward prey it sees
  farther off.
- **Into the water:** the leap from a monitor or crocodile has its own clip (a dive, the eyes shut as it goes in). An
  ordinary hop into the water just ends when it touches the water and blends to the float in 3 ticks; no dive. Out of
  the water it climbs the bank by the step-height rule, no clip.
- **Tongue:** the server picks the cloud's nearest edge (the flies fly only on players' computers) and flicks at that
  point; each player's computer then pulls the nearest fly onto the pad. So the tongue goes at the cloud, and a fly is
  dragged to it, never the other way round. A fly "comes in reach" by chance, about once every 2 s.
- **Blink:** in the idle clip (at 2.2 s and 4.9 s of its 6 s), floating and dug in: the eyes sink 1.8 pixels. Filmed
  in the game after the eye fix it works, but it reads as the eye ducking under the brow, never closing. And it plays
  only in those clips: a frog that is puffed up (any player in survival within 3 blocks), hopping, calling or swimming
  never blinks. Before the eye fix the eyes' paint faced inward, so the old films showed nothing at all.

## Steps

**Step 1. Film what is there (no new code but small fixes).** A film scene: a frog hops up a block, down a block, into
the water off a low bank, out of the water onto it, lunges at a barb; frozen and stepped, three sides. Fix only what it
shows wrong. Likely fix: step height down to about 0.6 so only a hop gets it up a full block (check it still gets out
of the water onto a 1-block bank; if not, the heave-out of step 5 is the answer, not the step height). A game test:
a frog walks a route that climbs a block and gets there by hopping.

**Step 2. A real blink (a model change: the workshop's detail and paint stages, the user's OK in the viewer first).**
Real frogs blink by pulling the eyeballs down into the head (it even helps push food down) while a see-through lower lid
rises over the eye. Minecraft version: the eye sinks as now and a lower lid (a thin plate in the head's skin, its top
edge the pale lid line) slides up over the eye in 2 ticks, holds 1, goes down in 3. The lids are new bones moved only
by a second animation layer of their own, so the frog blinks in every pose (sitting, puffed, floating, calling,
swimming) every 4 to 10 s at random, both eyes together; the swallow and the dive keep their own blinks. Viewer page
with the blink playing, the user's OK, then into the game and filmed.

**Step 3. The tongue aims at one fly (the user's main ask).** The server still decides when it flicks and whether it
hits (one in four misses); each player's computer picks the real fly:
1. Fixing on a fly: as the frog turns to the cloud, its head tips up toward the nearest fly in reach and follows it for
   a few ticks (frogs face prey square on, both eyes on it, before they strike).
2. The flick: the tongue's tip goes to where that fly really is every frame while it shoots out (0.08 s), so it lands on
   the fly even mid-dash; the fly is no longer dragged to the tongue.
3. A hit: the fly sticks to the pad and rides back into the mouth (as now). A miss: that fly darts away a moment before
   the pad arrives, and the pad slaps empty air.
4. Flick after flick: while flies stay in reach and it is hungry, the next flick comes 1 to 2 s later at another fly,
   until 3 flies fill it (now one every few seconds by chance).
5. Flies landed on the ground or water within reach can be tongued too (easier: one in eight missed).
Two players may see different flies caught; each sees a clean catch. Balance: a frog still stops after 3 flies and
rests 1 to 2 minutes, and a cloud refills a fly a minute, so two frogs at a 4-fly cloud can empty it for a while (fine:
clouds of 4 to 12).

**Step 4. Stalk, then lunge.** Real frogs sit and wait, but prey seen just out of reach gets a slow creep, body low,
then the lunge. Prey (barb, snail, young crab) within 4 blocks but past the 1.5 reach: it creeps toward it at the crawl
(on land or at the surface), turns to keep facing it, and lunges once in reach; gives up after 5 s or if the prey goes
farther than 5 blocks. A new `stalk` clip (low crouch, slow alternate steps, eyes fixed). The lunge and bite stay as
they are.

**Step 5. Into and out of the water.** A hop that will land in the water becomes a dive: in the air it tips nose down,
arms back, legs trailing, a splash, a short sink, then it rises to float (a new `dive` clip, the hop's kick kept).
Out of the water at a bank up to a block high: it heaves out (arms on the edge, a pull and a kick of the back legs, onto
the bank; a new `climb_out` clip), instead of gliding up by the step-height rule.

**Order:** 1, then 2's viewer look (waits on the user), 3, 4, 5. Tests: `river` after each step.

## Progress
Built 2026-11-20, all five steps, on `claude/bullfrog-polish`. Changes from the plan, at the user's word: hops go up
to 1.1 blocks (was 0.55) and keep their push through the flight (a hop up from right against a step rose straight up
and fell back for good); the haul-out is only onto banks about level with the water (African bullfrogs are too heavy to
climb: at a bank a block high it swims to a low spot); the creep runs at 0.13 blocks a second (the clip's own pace).
The user watched the course and the frame films: "everything looks good". Not yet tried by the user in a real world.
