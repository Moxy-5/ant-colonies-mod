# Plan: the otter plays, and begs for a fish (2026-11-22)

The user, 2026-11-22: "we gotta make a plan for the otter playing what triggers the animation to start what about
holding out a fish and what if the otter could actually prop up on the player looking up at them when they pull out a
fish". Animals rule: fun first, inspired by real life (numbers are my picks). This replaces step 3's play part of
`docs/plans/otter-and-bull-shark.md` and leads into its step 4 (the pet).

## Where the otter is now

Built: moving, swimming, diving, the five block moves, shake dry, resting, fleeing, following its mother (pups), and
hunting crabs, fish and frogs with carry and eat (steps 1 and 2). It already has a "fed" state (`isFed`, about 5 to 7
minutes after a meal), a "doing" state (`CALM`, `REST`, `EAT` ...), a scare state, and eyes that pick what to look at
(`Attention`: a player within 14 blocks, or 5 if crouching). The three play clips (`play_up`, `play_rear`,
`play_down`) are made and approved but nothing plays them yet.

Not built: play, curiosity about players, anything to do with an item in the player's hand.

## What real otters do (the inspiration)

- **Play comes after a good meal, somewhere safe.** Otters play when they are full, rested and nothing is threatening
  them. Pups play the most, mostly pestering their mother. Grown otters play when they meet (wrestle, chase, rear up
  face to face and bat at each other).
- **They play alone too.** They roll and fiddle with pebbles in their paws, and slide down wet mud banks over and over.
  Clawless otters have very handy, nearly human front paws.
- **They stand up to look.** An otter that sees something interesting sits up on its hind legs, tail behind for
  balance, nose working, the "periscope" look. Tame and zoo otters stand up and reach for food held over them, paws on
  the keeper's legs, looking up. That is the moment the user wants.

## 1. What starts play (one mood, one partner, one roll)

One rule, read in one place: an otter is **playful** when it is fed, calm (not eating, resting, hunting, fleeing or
scared), not soaked from a dive (no shaking dry still to do), and it is daytime or dusk. My pick: no other flags.

- **A partner within 6 blocks** who is also playful: its mother or pup, another otter, or later its owner (the pet).
  Every 10 seconds a playful otter with a partner has 1 in 4 to start (my picks, about one bout a minute or so).
- **The invitation (real):** the one starting bounds to the other and pokes it with its nose (a short `bite` with no
  damage, the "play bite"). The other turns to face it and both go `play_up`, then `play_rear` face to face about 1.2
  blocks apart, batting, mouths open. In water 1 deep, splash drops on the batting beats.
- **A bout** is 3 to 6 rounds of rearing, with short chases between (one breaks off and bounds 3 to 5 blocks, the other
  follows, they rear again), about 10 to 30 seconds in all. In water deeper than 1 they chase swimming and diving
  instead of rearing (no clip needed: the swim and dive moves already exist).
- **It ends** when one stops being playful (scared, hungry again), when the bout is done, or by chance. Both
  `play_down`, then a third of the time a shake dry and a short rest together (my pick, it looks lovely).
- **Pups:** a pup always counts its mother as a partner and is twice as eager. The mother sometimes answers only with a
  short rear and goes back to what she was doing (realistic: a tired mother).

**A balance note:** with the cap now 2 otters round a player (ones and twos), most play you will see is mother and
pup. Two grown otters meeting will be rare. I think that is right (real clawless otters live mostly alone; pups play
most) and it keeps the river calm. Hand feeding (below) makes play more common round the player, because a fed otter
is a playful otter.

**Alone (later, needs new clips):** a playful otter with no partner sometimes plays alone: rolls a pebble in its
paws on the bank (`pebble_play`), or, on a wet mud bank by water (the savanna mud is built), slides down it on its
belly into the water and climbs back up two or three times (`belly_slide`). Both are real otter behaviour and would
use the mud we just made. Listed here, but built after the fish step below unless you want it sooner.

## 2. Holding out a fish

**What counts as a fish:** a data tag `antcolonies:otter_treats` (raw cod, raw salmon, tropical fish, raw tilapia;
cooked fish too, my pick, fun first). Holding one in either hand is "holding out a fish". No new item.

**What the otter does, one stage leading to the next (one state, `BEG`, with stages):**

1. **It notices.** A hungry or calm otter within 12 blocks that can see you (line of sight) stops what it was doing
   (not mid-hunt with prey in its jaws, not fleeing) and stands up to look: the periscope, `stand_look`, head
   tracking the fish in your hand, not your face. A full otter does only this and stays where it is (it is not hungry,
   but it is curious). In the water it does the same treading water, head and shoulders up.
2. **It comes over.** If it is not full it comes: bounding to 3 blocks, then walking the last bit. Your movement
   decides how brave it is (my picks): standing still or crouching, it comes all the way; walking, it follows behind
   at 2 to 3 blocks; sprinting toward it, it slips back into the water and the whole thing ends for 30 seconds. A
   wild otter you have never fed waits at 3 blocks for 3 seconds of you standing still before the last steps (it
   is making up its mind); one that has eaten from your hand before comes straight in.
3. **It props up on you.** At your feet it rears up (`beg_up`), leans forward and rests its front paws on your
   legs (`beg_prop`), head tipped up, nose at the fish, whiskers going, now and then a quick double paw pat on your
   leg and a little chirp. It keeps facing you as you turn. Move the fish (swing your hand up and down or switch
   between hands) and its head follows. Put the fish away (change slot) and it drops down (`beg_down`), sniffs round
   your feet for a few seconds, and wanders off.
4. **You feed it.** Use (right click) with the fish while it props: it takes the fish from your hand with a quick
   gentle grab (`take_food`; the fish appears crosswise in its jaws through the `carry` socket that hunting already
   uses), one fish leaves your stack (not in creative), it drops down, trots 2 to 4 blocks away (to the bank if near
   water), lies down and eats (`eat`, the hunt's eating already built). It is now fed: no river hunting for 5 to 7
   minutes, and playful.
5. **It remembers you.** Each hand feeding adds you to its memory (your player id and how many times, saved with the
   otter). That is the road to the pet in step 4 of the old plan (see the open call below).

**Dropping a fish** (Q) within 8 blocks: a hungry otter goes and picks it up off the ground like caught prey (the
carry and eat it already has). Small, uses only what exists. My pick, listed so you can say no.

**Only one otter props on you at a time.** A second one stands and looks at 2 blocks. A pup and its mother: the pup
goes first, the mother watches (my pick, it looks sweet and avoids two bodies fighting for your legs).

## 3. The prop-up, done right in Minecraft (the coder's side)

- **Where it stands.** The otter stands so its front paws land on the front of your box: your box is 0.6 wide, so
  the paw point sits 0.3 from your middle, and the otter's back feet are that distance plus the clip's reach behind.
  I measure the reach from the `beg_prop` clip (the paw bones' position) and compute the spot from it, never a
  guessed number.
- **Height (honest note).** Our otter is big (2.6 blocks long, much larger than a real one beside a 1.8-block
  player), so standing up its head will come to about 1.3 blocks, near your hand, not far below it. In first person
  you look down a little at its face tipped up to the fish. I will make the clip so the paws rest on your thighs
  (about 0.8 high) and its body leans in, which keeps the "looking up at you" look. I measure this in the clip check
  first and show you a film before the gameplay goes in.
- **Its head looks at the fish, not your eyes.** A new look target in the otter's `Attention`: the hand position of
  the player holding the fish (the side holding it, about chest high in front of you). The eyes and head already
  turn through `Attention`, so this is one new target, not a new system.
- **No pushing.** While propped, the otter and you do not push each other (otherwise Minecraft's entity push
  shoves you both apart). Only between the otter and that one player, only while it props.
- **When it cannot prop:** you are more than half a block above or below it (on a step, a slab, a ledge), in water,
  riding, flying, or in a boat. Then it does the periscope stand at your feet instead (`stand_look`, close). One check,
  in one place.
- **Server side, so everyone sees the same.** The stage (looking, coming, propping, taking) is synced to clients;
  the head target is drawn only. Other players see it propping on you.
- **Pups** (0.55 scale) prop too, the same clip scaled: their paws reach only your shins and they look straight up.
  Probably the cutest version. Their spot is computed from the scaled reach.

## 4. New clips (made with `tools/otter_models.py`, checked offline and filmed in-game from three sides, the hard rule)

| Clip | Loop | What it shows |
|---|---|---|
| `stand_look` | yes | Periscope: upright on hind legs and tail, front paws tucked to chest, nose twitching, head free to turn (the code turns it) |
| `beg_up` | no | From all fours up into the lean, paws reaching forward (about 0.5 s) |
| `beg_prop` | yes | Leaning in, paws resting at the measured height, head tipped up, whiskers, a double paw pat and a chirp mouth every 2 s or so |
| `beg_down` | no | Back down to all fours (about 0.5 s) |
| `take_food` | no | Quick gentle grab from the prop: head forward and up, jaws close on tick 6 (the fish appears then), drop to all fours |
| later: `pebble_play`, `belly_slide` | | Alone play (part 1's later bit) |

`play_up`, `play_rear`, `play_down`, `bite`, `eat`, `shake_dry`, `rest` are already made. The handoff (`docs/HANDOFF.md`)
gets a request card for these clips, or I make them in the same session with the workshop tools; both go through
`clip_check.py otter --switches` (the blends `idle -> stand_look -> beg_up -> beg_prop -> take_food -> idle`, and
`beg_prop -> beg_down`) before any Java plays them.

## 5. Steps

1. **Play with each other** (part 1, no new clips): the playful mood, the invitation, bouts in the shallows and the
   chase swims, mother and pup. Game tests in `river`: two fed otters in a pool start a bout within 2 minutes; a
   scared or hungry otter never does; a bout ends. Film a bout from three sides.
2. **The begging clips** (part 4's table): made, checked offline, filmed in the game with a player stand-in, measured
   paw height and reach shown to the user before step 3.
3. **Holding out a fish** (parts 2 and 3): the treat tag, notice, come, prop, feed, memory, dropped fish. Game tests:
   an otter comes to a still player holding cod and props within 20 s; it keeps clear of a sprinting one; feeding
   takes one fish and makes it fed; a full otter only looks; two otters, one props. Film the prop from first person
   (the shot that matters) and from the side.
4. **Alone play** (`pebble_play`, `belly_slide` on wet mud), if wanted.
5. **The pet** (old plan step 4), built on the hand-feeding memory, once you have made the call below.

After step 3: the `river` and `life` test categories; a full run before merging into `master` (every-3-steps rule).
No guard films: animal-only work touches no ant or termite.

## Open call for the user

- **The pet, how tame starts.** My pick: an otter you have hand fed 5 times (pups 3) turns tame (hearts), from then on
  follows you, swims beside you, and plays with you (the play rules above with you as its partner: it rears up at you
  and bats, chases you). Wild grown otters keep the 3-second wait every first time. Say if it should be fewer or more
  feeds, or pups only.
