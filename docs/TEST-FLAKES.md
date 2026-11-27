# Test flakes

Tests that fail now and then, mostly beside other tests, and pass when run alone. As far as is known the
game is right and the test is not; each is looked into when its part of the game is worked on. Kept apart
from the list of bugs (`docs/OPEN-BUGS.md`) on purpose. **This file is written by
`python tools/open_bugs.py`; never edit it by hand.** Each number is an entry in `docs/BUGS.md` tagged
`[flake]`, one per test: a new failure of a listed test is a line on its entry.

**49 flaky tests.**

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
- **255** The monitor's mid-climb test (`aMonitorWhoseStepIsBrokenMidClimbFallsBack`) is fixed for the monitor wandering off the test's route (2026-11-25); its other message, "on a move 93 ticks, highest 0.00", is still not explained (review R9-39).
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
- **R9-39** The mid-climb tests fail now and then because the monitor's own idle goals take it off the test's route (BUGS 255, 290, 292) *(code review; half fixed)*
