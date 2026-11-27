# Bugs left in the game

Every bug still left, graded by how much it hurts play; then the tests that fail now and then, and the
faults in the tools. **This file is written by `python tools/open_bugs.py`; never edit it by hand.** It
reads the tags in `docs/BUGS.md` (a number like **306**: the entry there has the whole story) and the
`**In play:**` lines of the code review files (a number like **R6-20**: the finding in
`docs/reviews/full/`, its state in `docs/CODE-REVIEW-BUGS.md`).

**In the game: 49 (1 A, 17 B, 31 C). Waiting for your call: 19. Flaky tests: 47. Tool faults: 5. Behind the scenes (code review findings no player sees): 186.**

## A: it breaks play (1)

Something a player meets goes wrong in a way that matters: a crash, a freeze, a system that does not do its job.

- **306** The raids were rewritten from scratch (2026-11-24) because they did not work reliably in play. The rewrite is built but not proven yet: a long unattended run and you watching raids are left.

## B: it looks or plays wrong (17)

A player will likely see it, and the game goes on.

- **176** A hurt ant carried down a wall (the way into every Matabele nest) sticks straight out from its carrier's head instead of lying along the wall.
- **183** Minecraft's own, not the mod's: flying fast over new land, the game can freeze 8 to 13 seconds near a trial chamber.
- **202** A raid can march to a feeding place where no termite is out and come home empty (at most twice for one place; to be checked in the long unattended run).
- **237** A Nile monitor diving off a bank 2 blocks high: its tail stands straight up the bank's face for about a second.
- **253** The bullfrog's feet jump at the start of its hops, its hind feet sink into the ground when it sits, and its walk flickers in between hops.
- **256** Young crocodiles draw their tails and heads deep inside blocks at steps and banks.
- **313** A raid's leader can stand still 30 seconds on a ridge before the raid goes on (found by a test).
- **361** Matabele majors that chase termites into their tunnels during an attack cannot find their way back to the column: they stand 30 seconds, then walk home alone.
- **362** Raiders are sometimes sent to stand on top of a termite mud-crust plate, which they cannot reach: they stand 30 seconds before going on.
- **378** A buffalo calf's feet slide when it runs with the herd.
- **411** A raid's leader can end up in a pond with no way out and stand there 30 seconds (found by a test that fails alone 4 runs in 9).
- **427** The bullfrog's feet and hands whip the long way round when it climbs out of the water or starts to swim.
- **R3-38** A Matabele that lost a leg has all six again after it goes into the nest out of sight and comes out. *(half fixed)*
- **R4-18** A busy river costs frames: about 6.5 ms a frame for 103 river animals in view (it was 13.3). *(half fixed)*
- **R4-26** A bullfrog (or a land animal later) caught by a crocodile hangs upright and still while the crocodile rolls it.
- **R6-20** Likely, not proven: one block you put in a Matabele nest's chimney (a torch) makes the colony lose all its rooms.
- **R8-3** Likely, not proven: with a biome mod that is a data pack (Terralith, say) its biomes may not appear at all.

## C: small or rare (31)

Hard to notice, or it needs an unusual case.

- **243** No crocodile can climb out onto a bank a full block above the water (the mod's own rivers have banks level with the water, so it is rare).
- **250** The river snail flickers between crawling and standing as it goes.
- **254** Small looks on the catfish, barb, crab and snail: the crab's planted feet skate as it walks sideways, and a few smaller ones.
- **269** A hippo walking from a level bank into deep water: its hips show inside the bank for a moment.
- **308** Small faults on the Matabele minor's body: a leg pops at the change to the fast walk, the head jumps at the top of a climb, the body dips into the roof going onto a ceiling.
- **330** A monitor can stop in front of a step for a moment when its climb is cut on its first tick (the main cause is fixed). *(half fixed)*
- **342** Giant trees in land made just before the game crashes (not a normal quit) stay frozen for good (a normal quit is fixed). *(half fixed)*
- **357** Wound care takes twice as long as the species file says: a 30-second care round takes a minute (still within real Megaponera times).
- **358** In a dead mound that keeps wearing down, the Matabele nest's entrance stays where it was, up to 5 blocks up in the air; nothing seen to break yet.
- **359** Matabele coming home rest (and are tended) at the edge of their first room instead of spreading through it.
- **385** A lioness holding or eating prey has a back paw 1 to 2 pixels into the grass.
- **426** Some ant and termite clips push feet or antennae under the floor: grooming, a bite, the termites' peek and their paralysed fall (some may be meant).
- **428** A few animal loops jump back at the end of each round (crocodile eating, crab cracking, a barb, a distichodus), and some feet hang in the air (bullfrog, hippo, lion and lioness clips).
- **432** When the game lags, an otter's block move can show late and then jump forward (it mends itself within a second).
- **R3-10** Big fights cost the server more than they need: a fighter with no foe near still looks round once a second. *(half fixed)*
- **R3-30** Some ants lose one try (a short pause) when they set off to go in, wait at the nest or follow the queen. *(half fixed)*
- **R4-13** Fly clouds go silent after F3+T or a new sound device, until you walk 48 blocks away and back.
- **R4-27** The termite queen's walk and turn lose their body bob.
- **R4-28** After F3+T the monitor's tail rest pose and the otter's faces stay as they were.
- **R4-30** Coming within 48 or 64 blocks of a crocodile or monitor, its whole pose snaps into place in one frame.
- **R6-4** A termite soldier clamped on a raider's leg vanishes when its mound is far from every player.
- **R6-8** The ants tending the wounded are the first taken for a raid.
- **R6-9** A shut-in Matabele with a termite soldier on it bites the wall instead of fighting.
- **R6-11** In a raid a clamped ant is fetched only after the attack, so the clamp squeezes all through it.
- **R6-15** Beside a raid column you are squeaked at by two rules at once.
- **R6-16** A Matabele with no colony lives for ever; one whose colony is gone joins any colony near it.
- **R6-19** The prey pile can show fewer termites than the colony counts (after you break a heap, say).
- **R7-5** A big tree falling in sight may stutter the game (up to 2,500 pieces made in one tick; to measure).
- **R7-33** A palm's crown might blink out at the edge of the screen (never seen).
- **R9-18** When a hippo pool dries up, each hippo picks its own new spot instead of the pod going together. *(half fixed)*
- **R9-23** A bull shark that spawned in a hole too small to swim in lies still. *(half fixed)*

## Waiting for your call (19)

Code review findings whose fix changes how the game looks or plays, so nothing is built until you say. The
question, what happens today and my pick for each are in `docs/CODE-REVIEW-BUGS.md`, "Waiting for your
call".

- **R3-20** The work goals run at half the speed their numbers say, and the numbers copied from them do not
- **R3-21** The insects never ask the `mobGriefing` game rule
- **R3-33** Goals count their own ticks at half pace, so most written times are double (the list BUGS 35 and 357 did not name)
- **R3-40** Only one bite in half a second lands on one body
- **R3-41** A living clamp nearly always costs the leg before care can save it
- **R3-45** In Peaceful the nest defence runs but can do nothing; the sting is not "2 hearts" off Normal
- **R4-9** A mud coat on one of Minecraft's own animals is kept and sent but never drawn
- **R4-29** A crocodile's or monitor's drawn head over a bank is up to about a block from its head hit box
- **R4-32** A dying buffalo or lion on a step drops its tilt at once and dies with its legs hanging in the air
- **R6-5** A limping ant stops to call for helpers who can never come
- **R6-6** The nest's defence calls out the wounded first, and ants carrying a hurt nestmate or carried themselves
- **R6-12** One continuous size spread, but two bodies decide everything, and 77 in 100 workers get the full major's numbers
- **R6-13** The sting can kill an unarmoured player standing at the nest in about 2.5 seconds
- **R6-17** The colony's hunger counts only the adults, while a new worker costs 11 termites
- **R6-18** Egg and brood times are minutes on a scale where a game day is a real day
- **R6-33** A dead Matabele worker leaves no body
- **R9-19** Several small things, listed in the review file
- **R9-44** Small things in the monitor
- **R11-22** Numbers

## Test flakes (47)

Tests that fail now and then, mostly beside other tests, and pass when run alone. As far as is known the game
is right and the test is not; each is looked into when its part of the game is worked on. One entry per test.

- **62** The fallen tree up a bowl fails beside other tests (`aFallenTreeLiesOnTheSlopeUphill`)
- **75** The minors at the holes test failed once beside the fight tests (`minorsGuardTheHolesAndLineTheForagersTrailThenGoInLast`)
- **77** Three Matabele fight tests flake about 1 run in 5 together (`tenMatabelesAtAFeedingSpotTakeMinutesAndManyTermitesEscape`, `matabeleStingParalysesATermiteAndASoldierBitesBack`)
- **93** The whole-chain raid test from a dead mound flakes about 1 run in 2 (`aBigColonyInADeadMoundRaidsFromTheScoutsOwnFindToThePileWithNoStageHanging`)
- **94** The Matabele nest defence test once had no defenders out after the nest was broken (`matabeleDefendTheirNestAgainstAPlayer`)
- **97** The ten-Matabele skirmish test flakes, a Matabele standing half a minute in a foraging hole (`tenMatabelesAtAFeedingSpotTakeMinutesAndManyTermitesEscape`)
- **98** A Matabele scout test could not put its dead mound in, a neighbouring test's living mound within 40 blocks (`aHungryMatabeleColonySendsAScoutWhoFindsTermitesAndComesHomeWithAScentLine`)
- **108** The ridge-and-pond raid test's scent line once ran straight across the pond's edge (`aScoutsTrailOverARidgeAndRoundAPondLeadsTheRaidThereAndHome`)
- **118** The dead baobab test's new-land snag once got a death day that moved it out of its mud-climbed stage (`aDeadBaobabSlumpsIntoAHeapAndIsGone`)
- **119** The termite eating test's new comb check flakes about 1 run in 4, a worker left sunk in the comb (`termiteWorkersEatNodulesWhenYoungAndOldCombWhenOlder`)
- **126** The Matabele break-in test's stop count runs close to its limit (`matabeleMajorsBreakIntoAFeedingGalleryUnderALog`)
- **136** A fallen log on the edge of a step was not tunnelled under (`aFallenLogOnTheEdgeOfAStepIsEatenFromBelowByDay`)
- **151** The test of your half-built stage 14 mound (`termitesFinishTheUsersHalfBuiltStageFourteen`) now and then leaves a few pieces unbuilt at its end.
- **153** The scout ringing a living mound (`aScoutRingsALivingMoundItComesNearAndFollowsATermiteTunnelOutToTheFeeding`) sometimes makes its find another way than by following the termites' tunnel.
- **167** The test `thegalleriesgrowtipbytipwiththecolony` once found a 38-worker termite colony's galleries longer than it allows, 70 blocks against 58
- **171** The whole-chain raid test's march check fails about 1 run in 6 to 8: "on the march others ran ahead of the scout" 45 to 62 ticks of 190 to 220, the limit a fifth (`aScoutLeadsARaidColumnAlongItsTrailThatStopsShortAttacksTheWorkersAndComesHome`)
- **197** The whole-chain raid test now and then finds a trail point still broken after the column crossed
- **207** The foragers' hurry-in test (`foragersHurryInThroughAYoungMoundsOpenShaftAtAFoe`, which took over from the chicken tests) fails often, alone too: the foragers take too long to get in, or none reaches the bush.
- **211** The gallery seal test flakes, "the soldiers by the break should be shut out by the seal" (`aRaidBreakingIntoAGalleryIsSealedBehindTheSoldiers`)
- **215** The young cathedral test failed once beside others, "stage 5 is not finished: {carve=3, piece=10, reshape=9, soil=3}" (`termitesBuildTheYoungCathedralFromInside`)
- **226** The school test failed once beside others, "tilapia stood still after the hunt" (`theSchoolSwimsOnAfterAHunt`)
- **227** The tilapia breeding test failed once beside others, "stuck at stage 1" (`tilapiaBreedInANestPitAndTheMotherKeepsHerFryInHerMouth`)
- **242** The snail breeding test failed once beside others, "snails stuck: laying true" (`snailsMeetLayAtTheWaterlineAndHatch`)
- **244** The crocodile basking test failed once beside others, "the crocodile has not basked on the sand bank: sand spot none" (`aCrocodileSwimsToASandBankToBask`)
- **251** The bullfrog's leap test fails alone now, "the bullfrog leapt but never came down in the water" (`aBullfrogLeapsIntoTheWaterFromAMonitor`)
- **298** Two catfish tests flaky under load (`aHungryCatfishHuntsAndSwallowsATilapia`, `aCatfishTakesABarbAndTheSchoolBursts`)
- **315** A second raid, begun right after the first turned home with its scout killed, stood in its muster 25 s (deadline 20 s) (`aRaidWhoseScoutIsKilledOnTheMarchOutTurnsHomeAndNobodyIsLeftOut`)
- **332** `aTadpoleBesideFourFrogsDoesNotBecomeAFifth` failed twice beside others, "5 bullfrogs (4 wanted: no froglet beside the 4)"
- **338** Three breeding tests failed together beside other tests, and the catfish's hidden-barb test failed once alone (`tilapiaBreedInANestPitAndTheMotherKeepsHerFryInHerMouth`, `crocodilesCourtNestAndTheMotherCarriesHerYoungToTheWater`)
- **363** The low-mound stream march test (`aRaidFromALowDeadMoundStreamsAlongItsTrail`) fails now and then beside other tests, each time on a different check
- **364** The otters' nose-touch test failed once beside others (`playingOttersTouchNosesAndRestSideBySideWithoutOverlapping`)
- **367** `crocodilesSpawnApartAndBaskTogether` failed beside others
- **420** `antsGoUnderAnOverhangAndOutTheOtherSide` failed beside others
- **424** The lioness stalking test (`aStalkingLionessFreezesWhileAHeadIsUpAndCreepsWhileItIsDown`) failed once beside others: she never began to hunt.
- **430** `anOtterSlidesIntoAPoolAndClimbsOutAndShakes` failed beside others, "the otter never climbed out with its move (swum 786 ticks)"
- **436** The test `catfishSpawnOnARainyNightAndScatterEggsOnTheAlgae` failed beside others, stuck at stage 0 with the female not ready
- **437** The test `aCrocodileMotherSeesOffAMonitorFromHerNest` fails now and then beside others
- **438** The test `aCrocodileAfloatClimbsOverAnUnderwaterLedge` fails now and then, "never got over the ledge onto the bank"
- **439** The test `aMonitorWatchesTrailsAndBitesAPlayer` failed beside others, "the monitor never watched the player"
- **440** The test `aQueuedMoundIsGrownAsideThenPutInQuickly` failed beside others, "its dead tree still waits"
- **441** The test `aCalmTermiteNestStandsStillMostOfTheTime` failed beside others, "idle insects walk too much: termite_worker still 73%"
- **442** The test `fishCrowdRoundAHipposDungInItsPool` failed beside others, "0 barbs and 2 tilapia"
- **443** The test `aRaidFromATallDeadMoundStreamsBehindItsScoutAndNeverStops` fails now and then beside others
- **444** The test `aDistichodusBitesOnlyTheOuterEdgeOfHippoGrass` failed beside others
- **445** The test `aScoutOutAsNumbersIsShownWhenAWatcherComesNear` failed beside others
- **446** The test `twoFedOttersPlayAboutAHungryPairDoesNot` fails now and then beside others, "the bout never ended"
- **447** The test `holesTakeTurnsNearFoodFirstAndMoreInTheRain` fails now and then beside others, "with the switch off the colony did not go back to its 2 holes"

## Tool faults (5)

Faults in the test and film tools, not in the game.

- **114** The side-by-side test run hangs after every server has finished, one server stuck saving its world as it shuts down
- **170** The guard film `r_crowd_space` (eight Matabele majors in a pen) comes out different on every launch again
- **295** Frozen, stepped films draw a clip's clock two ticks a picture while the body moves one
- **429** On the course the monitor, the bullfrog and the crocodiles stop short of where a scene sends them
- **435** The six walker guard scenes come out different on every launch

## Behind the scenes (186)

Code review findings a player does not see (speed not yet measured, code that will cost us when building on
it, tidy-ups). They are listed in `docs/CODE-REVIEW-BUGS.md`, "Left to do".
