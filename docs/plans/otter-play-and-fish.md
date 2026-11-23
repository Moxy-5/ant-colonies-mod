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
5. **The pet**

(The order changed with section 6: the face, the skitter walks and the three games come first; see 6e.) (old plan step 4), built on the hand-feeding memory, once you have made the call below.

After step 3: the `river` and `life` test categories; a full run before merging into `master` (every-3-steps rule).
No guard films: animal-only work touches no ant or termite.

## 6. Making it feel alive: skitter walks, a face that shows feelings, three play games (added 2026-11-22)

The user, 2026-11-22: "I want to add new walk animations like skittering around facial emotions and a new animation
of 3 different ways for them to play with other otters ... I think its whats been missing to make it feel really
alive." Hard to put into words, so here is my reading of it as three pieces. They share one idea: **the otter has a
mood, and the mood shows everywhere at once**: in how it moves, in its face, and in what games it starts. Today it has
states (calm, eating, resting, scared) but its face is the same in all of them and it moves at an even pace, which is
why it reads as a machine.

### 6a. One mood, read in one place (the base for everything below)

A small mood on the otter, worked out each second from what it already knows (my picks): **calm**, **curious**
(something new nearby, a player holding a fish), **playful** (fed and safe, part 1's rule), **excited** (in a game,
about to be fed), **wary** (a player came fast, a splash nearby), **scared** (fleeing), **content** (eating, resting,
just dried off), **cross** (hurt, or its pup hurt). It is synced to the client so the drawing can show it. The walk,
the face and play all read this one value; none of them judges the mood on its own (fewer rules, one place).

### 6b. Skittering: how it moves, not only which clip

Real otters on land rarely walk at an even pace. They **scurry in short bursts**: a quick dash of a block or three, a
sudden stop, head up or nose down to sniff, a turn, another dash. The burst-and-stop pattern is most of the "alive"
look, so this is two parts:

- **The pattern (code, in the otter's wandering):** when curious, playful or excited, it moves in bursts of 1 to 3
  blocks with 0.5 to 2 second stops, often changing direction 30 to 90 degrees at a stop; at each stop a short look
  (head up, `stand_look` now and then) or a sniff (nose down). Calm and content keep today's even walk. Scared keeps the
  bound straight away.
- **New clips:**
  - `scurry`: quick choppy steps, body low and long, tail straight out behind, head forward (faster than the walk,
    slower than the bound; the clip speed follows the real speed as the walk does).
  - `skid_stop`: the little stop at the end of a burst, front paws braced, body rocking forward and back, head
    coming up (about 0.3 s).
  - `sniff_walk`: a slow nose-down zigzag, whiskers forward, for the curious wander and for hunting along a bank.
  - `hop_turn`: a quick little hop to face a new way at a stop (instead of turning on the spot).

### 6c. A face that shows feelings

Otters cannot frown or smile like a cartoon, but they show a lot with the parts they do have, and the model already
has all of them as moving parts: ears, eyes, eyelids, whiskers, jaw and tongue. My pick: **real otter signals pushed a
little bigger so they read at Minecraft distance, no cartoon eyebrows.**

| Mood | Ears | Eyes and lids | Whiskers | Mouth |
|---|---|---|---|---|
| calm | relaxed, a little out | open, slow blinks | relaxed, slightly back | closed |
| curious | pricked forward, turning to sounds | wide | **bristled forward** (the real otter tell) | closed, nose twitching |
| playful / excited | forward | wide, quick blinks | forward | **loose open "play face"**, tongue tip showing |
| content | soft, back | **half shut, squinting** | relaxed back | closed or a slow chew |
| wary | turning about | wide, few blinks | forward and stiff | closed tight |
| scared | **flat back** | wide | flat back against the face | closed |
| cross | flat back | narrowed | forward | **open, teeth showing**, a snarl |

How it works in the game: a **face layer** in `OtterModel`, added on top of whatever clip is playing, the way the
head already turns to look on top of every clip. So the face shows while walking, swimming, eating or playing, without a
clip for every mood times every action. It eases between moods over about half a second and adds small life of its
own: blinks at random times, ear flicks, a nose twitch when curious. Where a clip moves a face part itself (the jaw in
`bite` and `eat`, the lids in `rest`), the clip wins on that part.

This is the engine's planned "live layer for animals" (G4 in `docs/plans/animal-kit-tools-review.md`); built for the
otter first, written so other animals can use it later (shared code, each animal its own face table).

May need from the model (checked in the workshop's detail stage, the user's OK): a **nose** part that can twitch and
wrinkle, and maybe **whisker pads** that puff out; today the whiskers move as one piece per side. The faces are shown
in a viewer page first (each mood side by side, close up) for the user to pick from before any go into the game.

### 6d. Three new ways to play together

Kept: the rear-up and box (`play_up`, `play_rear`, `play_down`), now the usual opening of a game. Three new games, each
a different kind of real otter play (my picks of which three):

1. **Tumble wrestle** (shallows and banks). They grab each other and roll over: one ends on its back pawing up at the
   other, who mock-bites its neck and shoulders. Then they roll and swap, two or three times, with splashes. The
   wrestling everyone thinks of when they think of otters.
2. **Chase and tag** (water or land). One nips the other's tail and runs. In water the chase goes in **porpoise
   leaps**, arcing out of the water and back in; on land it is a scurry chase in loops. When the chaser catches up it
   tags with a pounce, and they swap who chases.
3. **Keep-away** (with a pebble or a crab shell). One picks up a pebble or an empty crab shell (the crab remains are
   built) and runs off with it, turning to show it off. The other chases and tries to snatch it. The winner rolls onto
   its back and fiddles the pebble between its front paws, until the other comes for it again.

Which game they play: the place picks first (deep water: chase; shallows or bank: any; a shell or pebble nearby:
keep-away more likely), then a random roll. A game runs 10 to 30 seconds and can lead into another.

**New clips:**
- `wrestle_top` and `wrestle_bottom`, a pair made together so the two bodies fit, plus `wrestle_roll`, a swap where
  both turn over.
- `porpoise`: a leap out of the water and back in.
- `pounce_tag`.
- `belly_up_fiddle`: lying on its back, the pebble between the paws.
- `snatch`.

**The hard part, honestly: two animals moving as one.** In the wrestle the two bodies hold each other. If each otter
moved on its own, they would drift apart, sink into each other, or push each other away (Minecraft pushes touching
mobs). So this needs a new shared piece, **paired moves**:
- One otter leads. The other is set at a fixed place and facing beside it every tick, and plays its half of the clip
  pair on the same clock (the leader's), so the bites and rolls line up exactly.
- No pushing between the two while paired.
- Either one being hurt, scared or pushed breaks the pair at once.
- A player joining mid-wrestle sees both at the right point.

It is species-neutral (in the framework). Later it could also serve a crocodile with prey in its jaws, a mother
carrying a pup, or ants carrying hurt nestmates. The chase and keep-away need no pairing: each otter runs its own part,
and only the tag and the snatch are a quick touch. So the wrestle is the hardest of the three and comes last.

### 6e. The order (each a step with the user's OK in the viewer before it goes into the game)

1. **The mood and the face layer** (6a, 6c): the faces in a viewer page, then in the game on every clip. The biggest
   change for the least work, because it shows on everything the otter already does.
2. **Skitter walks** (6b): the four clips through the workshop, then the burst-and-stop wandering.
3. **Play part 1** (section 1 above), with the rear-up opening, so the games have a start and an end.
4. **Chase and tag**, then **keep-away** (no pairing needed).
5. **Paired moves and the tumble wrestle.**
6. Then the fish begging (sections 2 to 4), which gets the face for free (curious, then excited, then content).

Every new clip follows the hard rule: `clip_check.py otter --switches` offline, then filmed frozen and stepped in the
game from three sides, before the user sees it. The faces need close-up films (a new short film scene, head only).

## Open call for the user

- **The pet, how tame starts.** My pick: an otter you have hand fed 5 times (pups 3) turns tame (hearts), from then on
  follows you, swims beside you, and plays with you (the play rules above with you as its partner: it rears up at you
  and bats, chases you). Wild grown otters keep the 3-second wait every first time. Say if it should be fewer or more
  feeds, or pups only.

**Progress (2026-11-22):** the faces page is built: `python tools/otter_models.py faces` writes
`references/workshop/otter/faces.html` (template `tools/face_viewer.template.html`). It shows the seven moods
(calm, curious, playful, content, wary, scared, cross) with their own blinks and twitches, one at a time with the half-second
blend, or side by side. The face numbers are `FACES` in `tools/otter_models.py`. Waiting for the user to pick before the
game's face layer is built.

**Progress (2026-11-22, round 2):** the user: "make the ears a little bigger or redesign them ... 3 variations", and
"scared and wary need more body language ... include [the tail]". The faces page now has an **Ears** row: Now (v6), A
bigger and poking up over the crown, B as A with a pale rim round the dark hollow, C rounder, set out and tipped out with
a pale inside (`EAR_STYLE` in `tools/otter_models.py`; the game keeps v6 until the user picks). Every mood got a **tail**
(lift, curl, swish, held stiff; a tail pressed down lies along the ground) and a **stance** (crouch, lean, neck, hunched
back, head turning, trembling, a lifted front paw for wary; legs re-solved so the feet stay planted). In the game the
stance goes on the standing-still clip only; the face and tail go on every clip. A "Whole body" camera shows it.

**Built (2026-11-22, round 3):** the user: "those are perfect and do A bigger for the ears". Ears A in the game model
(v7); the mood, face, tail and stances in the game (`OtterEntity.workOutMood`, the shared `client/FaceLayer`,
`stand_<mood>` clips); filmed in the game (`tools/film/otter_moods.film`). Next in 6e: the skitter walks (6b).

**Progress (2026-11-22, 6e step 2, the skitter clips):** `scurry` (quick choppy trot, 1.57 b/s), `skid_stop` (braced stop,
rock, head up), `sniff_walk` (nose-down zigzag, 0.59 b/s) and `hop_turn` (a small hop; the game turns the body while the feet
are up, ticks 3 to 7) made in `tools/otter_models.py`, checked offline (`clip_check.py otter --switches`, none flagged) and
filmed in the game (`tools/film/otter_skitter_frames.film`). On the versions page's clip buttons. Not played by the game yet:
next, after the user's look, the burst-and-stop wandering (6b's first bullet): curious, playful or excited, it scurries 1 to 3
blocks, skid-stops, looks or sniffs 0.5 to 2 s, hop-turns 30 to 90 degrees, and goes again; calm and content keep the walk.

**Built (2026-11-22, the skitter in the game):** `OtterEntity.SkitterGoal`, curious or playful on land: bursts of scurry, skid stop,
look or nose-down sniff, hop turn (speeds measured against the clips: scurry 1.60 b/s, sniff 0.60). The nose has its own bone and
sniffs in quick puffs (sniff walk, forage feel, the curious stance), at the user's word. Next in 6e: play part 1 (section 1).

**Built (2026-11-22, play part 1, 6e step 3):** `OtterEntity.Bout` and `PlayGoal`: the nose poke, rearing face to face 1.69 blocks apart
(measured on the clip, not the 1.2 guessed above), chases between rounds, swimming chases in deep water, splashes in the shallows, the
short rest after. Play uses its own rule (fed, calm, safe, day), not the mood. Next in 6e: chase and tag, then keep-away (step 4).
