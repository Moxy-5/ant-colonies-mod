# Animation engine: review and ideas (2026-10-16)

Asked for by the user after the overnight build: read the code, say what I think, find every way to make it better,
with advice from real-ant research and game animation. Nothing here is decided or built; the user picks.

## What is good
- The three layers are kept apart: the goals (`NapGoal`, `NoticeGoal`, `GroomGoal`) only set a synced flag; the clips
  and the live layer (`client/BodyLanguage`) do the moving. A smarter mind can replace the goals without touching it.
- Legs are worked out backwards from where each foot must be (`tools/ant_motion.py`), the stride note lets the game
  play the walk at exactly the speed that keeps feet planted, and the lab checks every clip (slide, feet down,
  clipping, seams). Each insect has its own clock and pace, so no two move in step.
- The behaviour is from real studies (tripod gait, Cassill's naps, the strigil).

## Realism gaps (most visible first)
1. **The stride never grows with speed.** Real ants walk faster mostly by taking longer strides: in *Cataglyphis* the
   stride grows almost in a straight line with speed (3.5 mm when very slow, more than 4 times that at top speed), step
   frequency rises and then levels off, and the duty factor (share of time a foot is down) falls from about 0.66 when
   slow to below 0.5 when running (Wahl et al. 2015). Wood ants also lengthen both stride and frequency (Reinhardt &
   Blickhan 2014). Very slow ants walk a looser tripod (tetrapod and wave moments). Ours: one stride of 0.46 body
   lengths at every speed, so at the game's 2.75 blocks a second each leg steps about 12 times a second, where a real
   ant at that pace (about 5 body lengths a second) would take strides about twice as long, about 6 steps a second.
   **Fix:** walk clips at 3 speeds (slow: short stride, duty 0.66, looser tripod; medium; fast: long stride, duty
   about 0.5), each with its stride note, picked by speed with a short blend. Also settles "decision 1" (the
   flickering legs) by the real rule instead of by taste.
2. **The edge curve works by time, not distance.** The waist bends by how far the front turned in the last 4 ticks.
   An ant that stops halfway over a ledge straightens after 0.2 seconds while its gaster is still over the edge; a slow
   climber bends too little, a fast one too much. **Fix:** "follow the leader" by distance: the rear takes the angle the
   front had half a body length back along the path walked. Also: it has never been seen in the game; a climbing scene
   for the in-game camera (`tools/film/`) is needed to check which way it bends.
3. **Turning on the spot slides the feet.** The legs step only when the body moves over the ground
   (`measureWalk`), so an ant turning where it stands plays the idle while its feet skate round. Real ants step to turn.
   **Fix:** count turning as walking (the turn speed times half the body's width), or a turning clip.
4. **No death or attack clips** (CLAUDE.md: every mob needs idle, walk, attack, death). A dying ant now tips over the
   vanilla way. A dead insect lies with its legs folded under it (a common sight; the cause in insects is not settled
   by a source I found). Attack waits for M8 (sting and grip).
5. **Naps only on the floor.** Fire ant workers sleep on the chamber ceiling, against the wall or on the floor, and
   the ones on the ceiling or wall sleep longer; queens huddle together and their sleep falls into step (Cassill et
   al. 2009). Ours need `onGround` and not climbing.
6. **Grooming is only random.** Grooming keeps the antennae's sense of smell sharp (Böröczky et al. 2013, cockroaches,
   carpenter ants, houseflies), so real ants groom most after getting dirty. **Fix:** more cleaning after digging,
   hauling soil, rain or water. Later: cleaning each other (allogrooming; the queen's court), legs and gaster.
7. **The antennae walk the same everywhere.** Ours already sweep in opposite phase, as real ants do; but carpenter
   ants switch between wide side-to-side sweeps, probing, and low trail following that finds the trail's edges with
   both antennae (Draft et al. 2018). **Fix:** antenna moods on the head player: searching (wide sweeps, scouts),
   on a trail (low, close to the ground), carrying (high; built).
8. **Colonies have no personality of their own.** *Pogonomyrmex barbatus* colonies differ consistently, year after
   year, in how much they forage and how they react to dry air, and the difference is shared by all the colony's
   workers rather than coming from a few odd ones; older colonies behave more steadily (Gordon et al. 2013 and later).
   Ours are only individual. **Fix:** each colony draws its own small shift of the trait ranges (and the foraging
   numbers), old colonies steadier.
9. Ants feel the ground too (all insects have vibration sense organs in their legs), not only termites; but
   *Pogonomyrmex* alarm is mainly chemical (4-methyl-3-heptanone from the jaws' glands; plain touching does not
   start a strong alarm, Wilson 1958). So: a stomp near a harvester nest might make a few ants come out and look, no
   more. A small idea, for the user to decide.

## Facts for the user's new decisions (2026-10-16)
- **Soldiers' head bang (warning only):** disturbed *Macrotermes* soldiers drum their heads in pulses 10 to 20 times a
  second; soldiers who feel it drum too about 0.3 seconds later, so the warning runs through the nest at about 1.3 m a
  second without fading; workers hurry deeper into the nest and soldiers go toward the source (Hager & Kirchner
  2013). They tell the direction from the moment the shake reaches each leg. So: drumming that spreads soldier to
  soldier, workers retreating inside, soldiers turning toward the shake.
- **Busy foragers noticing (a few, only close):** matches real foragers, who stay on their task.

## The code
10. `AbstractAntEntity` grew by about 630 lines (walk measuring, the waist, naps, steam, the look, traits, three clip
    pickers). The client-side body state could move into its own class (for example `entity/body/BodyState`), keeping
    the Body layer in one place and easing the M2 model swap.
11. **The animation file is 400 KB**, because clips keep a key every 1/40 to 1/80 second with straight lines between
    (+14,500 lines for cleaning alone). GeckoLib 4.9.3 has smooth Catmull-Rom curves between keys (checked in the
    library: `EasingType.CATMULLROM`), so the same motion fits in a fraction of the keys. The lab and the pose viewer
    would have to learn that curve first (today they draw it as straight lines).
12. The head player plays the whole clip and throws the body's bones away (and the body player the head's): twice the
    work. Small at 60 insects; cheaper with clips split by player. Low priority.
13. Found and fixed today: the pose viewer had been blank since the motion library's clips went in (key frames written
    as plain numbers; commit 4aa482f).

## The big idea: live legs
Port the leg solver to Java so the feet are placed live on the real blocks: stairs, slabs, block edges, turning,
ramps and the corners between floor and wall. Games do it by stepping a foot when it gets too far from its spot, with
gait rules (only one leg per side lifted at a time, tripods), and by looking ahead so a foot reaches for a new surface
before the body gets there (Sumo Digital's *Spyder*). Most realistic, but a big job: only for insects near the camera,
with the baked clips kept for everything else. The plan already lists it as "later" (feet that follow uneven ground);
best after items 1 to 3, maybe with the M2 model.

## Order I would suggest
1, 2 (with the climbing film scene) and 3 first (every walking ant gets better), then 4, 7, 6, 5 and 8, then 11, then
the live legs.

## Sources
- Wahl, Pfeffer & Wittlinger 2015, *Cataglyphis* walking and running: https://pmc.ncbi.nlm.nih.gov/articles/PMC4439428
- Reinhardt & Blickhan 2014, wood ants: https://cob.silverchair.com/jeb/article-split/217/13/2358/12278/Level-locomotion-in-wood-ants-evidence-for
- Zollikofer 1994, stepping patterns in ants (loads change neither stride nor frequency): https://cob.silverchair.com/jeb/article-pdf/192/1/107/2854414/jexbio_192_1_107.pdf
- Hager & Kirchner 2013, termite vibration alarm: https://cob.silverchair.com/jeb/article/216/17/3249/11623/Vibrational-long-distance-communication-in-the
- Draft et al. 2018, carpenter ant antennae: https://www.biorxiv.org/content/10.1101/327379v1
- Böröczky et al. 2013, grooming and smell: https://pmc.ncbi.nlm.nih.gov/articles/PMC3587213/
- Cassill et al. 2009, fire ant sleep: https://link.springer.com/article/10.1007%2Fs10905-009-9173-4
- Gordon et al. 2013, harvester colony differences: https://pmc.ncbi.nlm.nih.gov/articles/PMC3662670/
- Wilson 1958 / McGurk 1966, *Pogonomyrmex* alarm: https://groups.csail.mit.edu/mac/projects/psyche/65/65-041.html
- Sumo Digital, *Spyder* leg animation: https://www.unrealengine.com/en-US/tech-blog/sumo-digital-shares-how-it-created-spyder-s-sophisticated-animation-systems
