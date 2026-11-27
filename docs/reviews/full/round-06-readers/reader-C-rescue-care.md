# Round 6, reader C: rescue, wound care, clamps, lost legs and going home hurt (as the Matabele use them)

Read on `claude/review-round-6` at `b72cba68`. No code changed, nothing run or measured. Round 3's findings (R3-18 to
R3-45), round 1's R1-26, round 2's R2-37 and BUGS 349 to 363 were read first and are not repeated; where a finding
here is a new side of one, it says so.

## How the pieces meet (short)

- **At the raid spot** (`Raid.java:1437-1446`, `2088-2103`): a raider that `needsRescue` (a living clamp, or 1 to 4
  legs gone with the wound open) is dropped from the column and stands calling (immobile, `AbstractAntEntity:1501-1504`)
  until **REGROUP**, the only stage that hands out rescuers (`regroup`, `1948-1970`, size rule `RESCUER_SIZE` 0.8). The
  rescuer pulls a clamp (`fetchHurt`, game time, 200 ticks), carries one with legs gone; the column carries it home; at
  home `RescueGoal` (carrier branch) walks it to the hurt room.
- **Out of a column** (`tickRescue`, `:1277-1304`; `RescueGoal`): a hurt ant outside its nest with a "free" nestmate
  within 6 blocks stops and calls 10 s, again 40 s later; any free nestmate within 6 blocks walks to it.
- **In the nest:** `ClampCareGoal`, `WoundCareGoal`, one carer of each kind (BUGS 349's claims), the patient only once
  it rests on a room cell. `Wounds` runs the course on game time (decided at 6,000, smelt at 11,000, ends at 36,000,
  clean heals at 24,000), saved with the insect.
- **Going home by itself:** `WayHome` + `MatabeleWayHomeGoal` (priority 1), for raiders the column let go (hurt,
  stuck, its time up), scouts that gave up, ants the sweep finds outside. A carrier the column lets go walks home this
  way with the nestmate in its jaws (WayHome outranks `RescueGoal`), and `RescueGoal` takes over at the doorstep.

---

### C-1 (A, proven path; how often to measure): a hurt major is sent out scouting with its wound open, or badly wounded

- **What is wrong:** two places judge "fit to go out" and disagree. A raid never takes an ant with an open wound, a
  head on its leg or one badly wounded (`Raids.raiderFit`, `Raids.java:1039-1043`: `!fightsNoMore()`,
  `!wounds().isOpen()`, `!needsRescue()`, the comment "the hurt rest until the wound closes"). The scouts' own test
  asks only "a major, not crippled, no clamp, not downed, no fight spot, empty jaws" (`Scouts.fit`,
  `Scouts.java:314-316`). So a major with one or two legs gone and a fresh wound, or one at 40 percent of its health
  or less (`MatabeleEntity.badlyWounded`, `:362-364`), is a fit scout. The colony's old scouts go first
  (`mayScout`, `:281-288`, `veteranReady`), and the old scout is the one that led the last raid (`find.scout =
  leader`, `Raids.java:1035`), the ant most likely to come home hurt.
- **What the player sees:** the morning after a raid, a major limping on five legs walks out of the nest alone for a
  trip of up to 4 minutes (`trip_ticks` 4800). Out there nobody licks it, so when its infection is decided (6,000
  ticks after the wound) it gets the untended odds, 8 in 10 infected instead of 2 in 10; an infected one that is out
  past 11,000 ticks also misses its gland dab. The care the user saw built is skipped for exactly the ants it is for.
  A badly wounded scout fights nothing on the way (`FightGoal.canUse` says no while `fightsNoMore`).
- **Where:** `savanna/.../colony/Scouts.java:261-289` (`mayScout`), `314-316` (`fit`), `707` (the scout command uses
  `fit` too); `savanna/.../entity/goal/MatabeleScoutGoal.java:204` (asks `mayScout`), `330-333` (`fitToGoOn`: the same
  holes, so a wound opened while out does not end the trip either, which is right); against
  `savanna/.../colony/Raids.java:1039-1043`.
- **The proof:** `MatabeleScoutGoal.canUse` (190-210) asks `part.scouts().mayScout(level, ant)`; `mayScout` asks
  `fit(ant)` and the colony's hunger and hours; `fit` has no wound, `needsRescue` or `fightsNoMore` test. Nothing else
  on the way checks the wound (grep of `wounds` in `Scouts.java` and `MatabeleScoutGoal.java`: none).
- **Fix proposed:** one test of "may leave the nest for a colony errand", used by both: `Scouts.fit` calls
  `raiderFit` plus "a major" (fewer rules: one judge, not two). Then the scout command finds another major.

### C-2 (A, proven path; likely in play, the distances): a termite soldier clamped on a raider's leg is folded back into its mound's numbers and vanishes from the leg (a new side of R1-26)

- **What is wrong:** R1-26 found that a far colony's `foldLoaded` discards its members anywhere in loaded land with no
  sight check. For the clamp it is worse than "termites at a hole vanish": the soldier riding on a Matabele's leg is
  still a member of its termite colony, wherever the ant has dragged it. Once no player has been within 80 blocks of
  the mound for the grace minute, `foldLoaded` (every 400 ticks) takes it: alive, a caste, not downed, so `frozenOf`
  writes it down as an adult at home and `discard`s it. The ant's clamp then finds no holder (`Clamp.tick`, the
  holder is removed with `DISCARDED`, not `KILLED`) and after 100 ticks lets go.
- **What the player sees:** following a raid home (a Matabele nest is 60 to 90 blocks from a feeding spot, the mound
  a further 10 to 50 past it), a raider crawling home at a quarter speed with a soldier on its leg suddenly walks free,
  the soldier gone with no body; the soldier is back alive in its mound's count. The same for a soldier the clamp let
  go alive inside the ants' nest (R3-41's "living termite soldier walking free among the ants"): it vanishes. No
  care is needed and no leg is lost for those ants: near and away disagree.
- **Where:** `framework/.../colony/AwayLife.java:293-317` (`foldLoaded`, `level.getAllEntities()` at 300, `discard` at
  315), `270-276` (`frozenOf`: only the downed are kept out), `413-415`; `framework/.../entity/Clamp.java:146-154`
  (holder missing, `letGo` after `MISSING_TICKS` 100); `AwayLife.java:65-67` (`NEAR` 64, `NEAR_KEEP` 80);
  `SpeciesProfile.java:134` (`numbers_when_far` on by default, so for Macrotermes too).
- **Unchecked link:** how often the mound really is 80 blocks or more from the player while a clamped raider walks
  home in sight: the distances say often, not measured.
- **Fix proposed:** `frozenOf` keeps out an insect that is clamping on (`clampingOn() != null`), as it keeps out the
  downed: its fate belongs to the ant it holds now. One line, beside R1-26's sight check.

### C-3 (B, proven): a hurt ant stops and calls beside nestmates that can never come

- **What is wrong:** whether to call and whether to answer are judged in two places. The hurt ant calls when a
  nestmate within 6 blocks is "free" (`helpWithin`: `RescueGoal.mayHelp` and `answersHelpCalls`). Out in the field the
  nestmates that pass that test are scouts on a trip and ants walking home by themselves (healthy ones the column let
  go with it: "not at the halt in time", stuck, "its time is up: all let go", BUGS 352's 27). Both run a priority-1
  goal holding MOVE (`MatabeleScoutGoal`, `MatabeleWayHomeGoal`, `MatabeleEntity.java:213-215`), and `RescueGoal`
  sits at priority 2 (`:208`), so it can never start for them. `answersHelpCalls` says no only inside a raid column
  (`MatabeleEntity.java:970-976`). `RescueGoal.canUse` (`:80-85`) does not ask `answersHelpCalls` at all.
- **What the player sees:** a limper and a healthy nestmate walk home together; every 40 seconds the limper stops
  dead and squeaks for 10 seconds (calling freezes its whole mind, `isImmobile`, `AbstractAntEntity.java:1504`) while
  the other walks on. A quarter of its way home is spent standing, in the open, among whatever hunts there.
- **Where:** `framework/.../entity/AbstractAntEntity.java:1284-1304` (`tickRescue`, `helpWithin`);
  `RescueGoal.java:53-57`, `73-86`; `savanna/.../entity/MatabeleEntity.java:891-896` (`onRaidErrand`), `970-976`.
- **Fix proposed:** one judge: `answersHelpCalls` is false while the ant is on a raid errand (`!onRaidErrand()`:
  scouting, in a column, going home), and `RescueGoal.canUse` asks it too. Then out in the field a hurt ant calls only
  near the nest, where idle nestmates really come out to it (which works today: `helpWithin`'s box reaches the ants
  in the rooms under the entrance).

### C-4 (B, proven): out of a column any nestmate carries any hurt ant; the size rule lives only in the raid

- **What is wrong:** the design says "minors carry minors, majors anyone" (`docs/DESIGN.md`, "Step 5, as built"), and
  the raid keeps it (`Raid.java:138`, `RESCUER_SIZE` 0.8, used at `1961-1962`). `RescueGoal` and `pickUpHurt` have no
  size test, so at the doorstep a 7.5 mm minor (`InsectSizes.java:48`) picks up and carries an 18 mm major. Two places
  should judge one thing; one forgot.
- **What the player sees:** a small minor walking into the nest with a major more than twice its length hanging
  under its head.
- **Where:** `framework/.../entity/AbstractAntEntity.java:1221-1232` (`pickUpHurt`); `RescueGoal.java:60-70`, `149`;
  against `savanna/.../colony/Raid.java:1961-1962`.
- **Fix proposed:** the size rule moves into `pickUpHurt` (one place, the raid's filter then asks it); `RescueGoal`'s
  `callers` skips a caller it could not carry unless it only needs a clamp pulled. As far as I know the papers do not
  say how rescuer size matches the hurt one's; the 0.8 is the user-era pick and is kept.

### C-5 (B, proven): in a raid the hurt are fetched only after the attack, so a clamp squeezes all through it (a correction to R3-41)

- **What is wrong:** R3-41 says "in a raid the column pulls clamps off in 10 s, before the first squeeze, so there the
  leg is kept". It does not: rescuers are handed out only in `regroup` (`Raid.java:1948-1970`, the only callers of
  `hurtWaiting` and `taking.put`). A raider clamped early in the attack stands calling (`tendHurt`, `2101`, re-called
  every update, so immobile) until the attack ends, which is up to `fight_ticks` 2400 (2 minutes) or 300 ticks after
  the last termite. A living major soldier squeezes every 400 ticks (`macrotermes.json`), each squeeze rolling its
  `leg_cut` 0.30 (`Fights.squeeze`): up to six rolls, the leg kept about 12 times in 100, before the 200-tick pull even
  starts. Meanwhile the caller stands still among the soldiers, which may bite it again (it cannot dodge or answer).
- **Real side:** waiting till the raid gathers to leave fits what I know of Frank et al. 2017 (the hurt are helped
  when the raiders regroup at the hunting site), but I could not check the paper's wording; the leg loss does not
  fit their "9 in 10 kept".
- **Where:** `savanna/.../colony/Raid.java:1404-1405`, `1941-1970`, `2088-2103`; `framework/.../entity/Clamp.java:169-175`.
- **Fix proposed:** R3-41's own (the squeeze does damage only; `clean` alone decides the leg). No new rule here; R3-41's
  numbers should be read with this in mind.

### C-6 (B, likely): the nest defence drafts an ant carrying a hurt nestmate, and the hurt resting in their room

- **What is wrong:** `NestDefence.mayDefend` asks only "a worker, fights on, not downed, has a fight body, not kept by
  the species" (`NestDefence.java:211-213`; the Matabele keep out only scouts and raiders, `Raids.java:655-658`). No
  test of the jaws or the wound. `FightGoal` (priority 0) has no test of a load either (grep of `hasFullMandibles`,
  `isVehicle`, `getLoad` in `FightGoal.java`, `fight/*`: none). `RescueGoal.stop` keeps the nestmate unless its 4
  minutes ran out (`:106-112`). And an ant with one or two legs gone and an open wound fights on (`fightsNoMore` is only
  3+ legs, a living clamp or 40 percent health).
- **What the player sees:** break into a Matabele nest just after a raid: an ant with a nestmate hanging under its
  head runs at you and stings with it; the hurt get up from the hurt room and come out too, so their care stops (a
  lick needs them back at rest on a room cell) while the infection's clock runs.
- **Where:** `framework/.../fight/NestDefence.java:159`, `185`, `203`, `211-213`, `347-353`; `FightGoal.java:137-146`;
  `RescueGoal.java:106-112`; `AbstractAntEntity.java:965-967`.
- **Unchecked link:** not run; whether the attack clip with a passenger looks as bad as I think.
- **Fix proposed:** `mayDefend` leaves out an insect with full jaws and one that `wounds().isOpen()`. Owner's call:
  should the hurt (legs gone, wound open) defend the nest? Today they do; my pick: they do not (they rest; nothing I
  know of says hurt Megaponera defend).

### C-7 (C, proven): the "busy with a chore" list misses wound care and rescue

- **Where:** `savanna/.../entity/MatabeleEntity.java:899-902` (`isBusyWithChore`: `ClampCareGoal` is there,
  `WoundCareGoal` and `RescueGoal` are not); used by the raid muster's order (`Raids.java:995`) and the scouts
  (`Scouts.java:274`, `710`).
- **What is wrong:** a nestmate halfway through a 30 s lick or a dab is called to a raid or sent scouting as readily
  as an idle one; the lick is lost (`WoundCareGoal.stop` releases the claim), and the patient waits for another carer
  inside its 5-minute lick window.
- **Fix proposed:** add the two goals to the list.

### C-8 (C, to measure): every idle ant in the nest searches for callers and patients when nobody is hurt

- **Where:** `RescueGoal.java:80-85` (once a second, a mob search 6 blocks round each free ant), `WoundCareGoal.java:47-61`
  and `ClampCareGoal.java:50-61` (every 2 s, 1 in 2 and 1 in 3, 8 blocks).
- **What is wrong:** in a nest every ant is within reach of every other, so the work grows with the square of the real
  ants (about 60 x 60 cheap predicate checks a second for rescue alone). Each check is cheap (the colony id and a time
  first), so probably well under 0.1 ms a tick.
- **Fix proposed:** only if it measures: the colony keeps a count of members calling or wanting care (set by
  `callForHelp`, `Wounds.open`, `Clamp.clampOn`), and the three `canUse`s return at once while it is 0.

### C-9 (C, proven): comments that disagree with the code

- `RescueGoal.java:27-28` ("pulls a clamped foe off ... a living one killed first") and `Raid.java:2036-2038`
  ("Freed and whole, the hurt one walks home"): a living holder is killed and its head stays on; `needsRescue` turns
  false (`AbstractAntEntity.java:1129`, a head does not count) and the rescuer stops, so the ant walks home with the
  head, which `ClampCareGoal` takes off in the nest. The behaviour fits the design (BUGS 173); the words do not.
- `MatabeleEntity.java:347`: "wound care by nestmates is step 5" (built). `LostLegs.java:15`: "step 5's rescue carries
  it, later" (built).
- `megaponera_analis.json:95`: "5 or 6 never happen in the game". They can, rarely: a crippled ant (3 or more gone) is
  still bitten by soldiers it passes, a living clamp on it still squeezes, and `ClampCareGoal`'s failed `clean` takes a
  leg (`Clamp.java:249`). Then `needsRescue` is false and it walks home uncalled, which is the paper's triage, so only
  the note is wrong.
- `ClampCareGoal.java:17-19`: "removed ... in 9 cases out of 10 within a day, the leg kept"; see R3-41 and C-5.

### C-10 (C, proven): the sting's pain list is kept across worlds

- **Where:** `framework/.../fight/Pain.java:24` (a static map by player id, never cleared on a world change; an entry
  is pruned only when that player is stung again).
- **What is wrong:** stings in one world count toward a longer pain in the next world opened in the same sitting if
  its clock happens to sit within 600 ticks after the old one (the "from the future" check at `:50` catches the rest).
  Harmless and rare; a leftover between worlds (kind 3c).
- **Fix proposed:** clear the map on server stop, or keep it on the level.

### C-11 (C): test gaps

- No game test of the rescue out of a column (a hurt ant at the doorstep calling, a nestmate coming out, carrying it
  to the hurt room); `RescueGoal`'s field branch is guarded by nothing. grep of `isCallingForHelp`, `RescueGoal` in the
  game tests: none.
- `MatabeleRaidTests.java:491-494` passes when the hurt raider died (`!hurt[0].isAlive() ||`), so a rescue that kills
  it would pass; the wound-care test (`MatabeleFoodTests.java:79-106`) has the same escape and sets the wound's times
  by hand.
- No test that a hurt ant is not sent out (C-1), not drafted by the defence (C-6), or keeps its clamp while its
  mound is far (C-2).

---

## Balance (the numbers against each other)

- **Can care come in time?** Yes, narrowly, for a raider the column carries home. The wound is decided 6,000 ticks
  after it opens. Worst case: hurt at the attack's start, attack 2,400, regroup up to 600, a march of 60 to 90 blocks at
  2.5 a second (about 500 to 750), the carry down to the hurt room (about 200), a carer found (each free ant looks every
  2 s, 1 in 2) and one lick of `lick_ticks` 300 goal ticks (600 game ticks, BUGS 357): about 4,600 to 4,900. About a
  minute to spare. It breaks if the attack grows, and C-1, C-3 and C-7 each eat into it. The limper left at the spot
  (fresh slowing 30 percent, about 1.75 blocks a second) gets home in about a minute from 90 blocks: fits. A
  living-clamped ant (a quarter speed, 0.6 a second) takes about 150 s from 90 blocks, inside WayHome's 6,000-tick
  `limp_ticks`.
- **The dab window** (11,000 to 36,000 ticks, about 21 real minutes) is wide: fits.
- **The odds against the papers:** untended 0.8 x 0.9 = 72 in 100 die (real: about 80 to 90); licked and never dabbed
  0.2 x 0.9 = 18; licked and dabbed 0.2 x 0.2 = 4 (real with care about 10 to 22, Frank 2018, 2023). Close enough,
  a little kind to the cared-for; no change proposed.
- **Two time scales meet here and nowhere says so:** the wound runs on the TIME scale (a game day a real day,
  `megaponera_analis.json:95`), the raid's stages on game lengths ("a real attack of 10 to 20 minutes would be 8 to 17
  game seconds", `:101`). The lick window is 5 real minutes only because that sum happens to fit; a comment tying
  `infect_ticks` to the raid's longest attack would keep the next tuner from breaking it.

## Solid in this slice (do not "fix")

- **Reloads:** wounds, lost legs and their time, the clamp and its holder's id are saved (`AbstractAntEntity.java:2913-2918`,
  `2950-2953`; `Wounds.save/load`, `Clamp.save/load`); the slowings are put back on load (`Clamp.load` calls
  `changed`, `updateLimp` every second); a carried nestmate is saved inside its carrier and rides on after the load,
  and the carrier's `HURT` load is kept; the raid's hurt waiting at the spot are saved (`Raid.java:1121`, `1206`).
  Care claims and "held still" are not saved and need not be (40 ticks).
- **No one held in the air:** a carrier that dies drops its nestmate (`FrameworkLoads.HURT` row: `ejectPassengers`); a
  rider whose carrier lost its load gets off (`tickRescue:1281-1283`); a carrier whose rider is gone lets go of the
  load (`:1278-1280`). A carrier or a carried ant never goes into the numbers near a player (`mayGoIntoNest`: full jaws,
  riding).
- **One carer of a kind** (BUGS 349's claims) holds: picked and claimed in `canUse`, renewed each tick, released on
  `stop` and on done; a patient that dies, is carried or heals ends the care at once (`canContinueToUse`).
- **The wound's course** is on game time, so it runs on while the land is unloaded and is settled on the first
  second back; a second leg lost renews it (the stated pick).
- **The carrier home:** a carrier the column lets go walks home by `MatabeleWayHomeGoal` (priority 1, ahead of
  `RescueGoal`), and `RescueGoal` takes the last stretch to the hurt room; `CARRY_TICKS` sets it down if the room is
  out of reach, and BUGS 175's hold stops the pick-up loop.
- **Lost-leg pieces cannot pile up:** each lies 6,000 of its own ticks (saved) and goes; they are made only per leg
  lost. Drawn from a synced species and length; the renderer falls back to a plain look on a bad save.
- **Sync:** lost legs and the clamp's leg, kind, look and size are synced entity data; the wound is server-only and
  nothing draws it yet (the darker stump is not built, as `docs/DESIGN.md` says).
- **Triage as the paper has it:** 5 or more legs gone never calls and is never carried (`needsRescue`,
  `call_legs_max` 4); a dead head does not call (BUGS 173).
- `MatabeleBiteOutGoal`: read in full; for a hurt ant it is sound: it never runs while the ant is carried (riding is
  immobile), never in its own nest's rooms, lets go for 30 s when nothing may be bitten (BUGS 113), and its route
  tries are spaced (`NO_WAY_TICKS`). Its per-second shut-in look is the raid round's to weigh.

**Read in full:** `RescueGoal`, `WoundCareGoal`, `ClampCareGoal`, `Wounds`, `Clamp`, `LostLegEntity`, `LostLegs`,
`LostLegRenderer`, `Pain`, `FightNumbers`, `MatabeleBiteOutGoal`, `WayHome`, `MatabeleWayHomeGoal`;
`AbstractAntEntity` lines 540-700, 790-1430, 1430-1530 and the save lines 2913-2953; `MatabeleEntity` 194-223,
330-364, 860-989; the species file's `fight` block. **Followed only as far as needed:** `Raid.java` (1420-1490,
1930-2120, 940-1000), `Raids.java` (420-660, 984-1043), `Scouts.java` (255-320, 695-720), `MatabeleScoutGoal`
(`canUse`, `fitToGoOn`), `AwayLife` (148-170, 240-420), `NestDefence.mayDefend`, `FrameworkLoads.HURT`, the rescue and
wound parts of `MatabeleRaidTests` and `MatabeleFoodTests`. **Not read line by line:** the rest of
`MatabeleFightTests`, `MatabeleTests` and `MatabeleBodyTests` (searched for rescue, wound, clamp and leg tests only).
