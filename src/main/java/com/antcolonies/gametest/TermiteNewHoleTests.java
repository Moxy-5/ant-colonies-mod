package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.FoodWays;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.colony.Nest;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * New foraging holes near rich food (the user's decision, 2026-10-14; {@link ForagingHoles}, {@link
 * com.antcolonies.colony.TunnelRoute}): the colony digs a tunnel underground out to rich food far from its holes and
 * opens a hole beside it; once the food is gone the hole is plugged for good.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteNewHoleTests {
    /** How far east of the nest's middle the hay bale lies (beyond both planned foraging holes, 8 blocks out). */
    private static final int HAY_EAST = 22;

    /**
     * A big colony (500 workers in the numbers, so its tunnels reach 40 blocks; the brain is off, so they stay inside)
     * with its nest dug out and six middle-aged real workers (builders, who dig). The colony knows of a hay bale 22
     * blocks east. It plans a tunnel to it, the workers dig it to the end and open a hole beside the hay at foraging
     * time. Then the hay is taken away: the hole is spent, plugged when the foraging is over and never opened again.
     * On the test clock (roadmap 3a): real nights and days from 18:30; the spent hole is plugged at the end of the night
     * and stays shut the next night. The bale is kept from running out until the new hole has been used (the workers
     * eat it from the tunnel's end by day), as a bigger stack of hay would last. When the hay goes a worker is kept in
     * the feeding gallery for 30 seconds (one taking the last bite): the spent gallery must never be sealed off with a
     * worker in it, and once let go the worker gets out (the full run of 2026-10-16: one was walled in alive, see
     * docs/BUGS.md 39).
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
    public static void aBigColonyDigsATunnelToFarHayAndPlugsItsHoleWhenTheHayIsGone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        // Ground east of the old arena too, out past the hay.
        for (int x = 0; x <= MIDDLE + HAY_EAST + 6; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 6; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 3 + i, SURFACE, MIDDLE - 2);
            }
        });
        StuckWatcher.watch(helper, () -> TermiteMoundTests.colony(helper));
        BlockPos hay = helper.absolutePos(new BlockPos(MIDDLE + HAY_EAST, SURFACE, MIDDLE));
        int[] phase = {0}; // 0 setting up, 1 waiting for the plan, 2 digging, 5 foraging by the new hole, 3 hay gone, 6 plugged, 4 the next night
        TestClock clock = TestClock.moving(helper, 18.5);
        int[] cutWhenOpened = {0};
        boolean[] usedTheHole = {false};
        FoodWays.NewTunnel[] tunnel = {null};
        int[] since = {0};
        long[] overSince = {-1}; // when the foraging was over after the hay went
        TermiteWorkerEntity[] kept = {null}; // the worker kept in the gallery when the hay goes
        long[] keptUntil = {0};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            // The food lies a set distance out, past the old 2 holes: with the arms of termites underground (step 1)
            // a hole along an arm may land near it, and the colony then uses that hole (a runway) and digs no new
            // tunnel. This scene is about the tunnel out to far food, so the arms are switched off here.
            colony.nest().galleries().setSwitchForTests(false);
            long middleAged = level.getGameTime() - colony.profile().worker().nurseUntilTicks() - 1000;
            for (TermiteWorkerEntity worker : level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds())) {
                worker.setGrownUpAt(middleAged);
            }
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - 1000, 500);
            level.setBlock(hay, Blocks.HAY_BLOCK.defaultBlockState(), 3);
            colony.food().rescanPlantsSoon();
            colony.foodMap().knowForTests(hay);
            phase[0] = 1;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || phase[0] == 0) {
                return;
            }
            if (phase[0] != 5 || colony.isForagingTime(level)) {
                since[0]++; // (foraging by the new hole: night time only)
            }
            if ((phase[0] == 2 || phase[0] == 5) && colony.food().getPiecesCut(hay) > 24) {
                // A bale holds 48 pieces, and once the tunnel reaches it the workers eat it from inside by day: until the
                // new hole has been used it stands for a bigger stack of hay, which lasts (cuts since the hole opened kept).
                cutWhenOpened[0] -= colony.food().getPiecesCut(hay);
                colony.food().setPiecesCutForTests(hay, 0);
            }
            if (helper.getTick() % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[new-hole-test] tick {} {} phase {}: {}{}", helper.getTick(), clock.describe(), phase[0],
                        tunnel[0] == null ? "no tunnel yet" : "dug " + tunnel[0].tunnel().tunnel().stream().filter(c -> Nest.isDugOut(level, c)).count()
                                + " of " + tunnel[0].tunnel().tunnel().size() + ", hole " + level.getBlockState(tunnel[0].tunnel().hole()),
                        colony.foragingHoles().describeNewHoles(level));
            }
            if (kept[0] != null) {
                // Never a worker in the spent gallery with its mouth and its roof (where the hay lay) both shut: walled in alive.
                java.util.List<BlockPos> gallery = tunnel[0].gallery();
                BlockPos mouth = galleryMouth(tunnel[0]);
                boolean shut = level.getBlockState(mouth).isCollisionShapeFullBlock(level, mouth)
                        && level.getBlockState(hay).isCollisionShapeFullBlock(level, hay);
                for (TermiteWorkerEntity worker : TestIsolation.insectsOf(level, colony, TermiteWorkerEntity.class)) {
                    helper.assertTrue(!shut || !gallery.contains(worker.blockPosition()) || worker.blockPosition().equals(mouth),
                            "worker " + worker.getId() + " was walled in in the spent gallery at " + helper.relativePos(worker.blockPosition())
                                    + " (its mouth " + level.getBlockState(mouth) + ")");
                }
                if (helper.getTick() == keptUntil[0]) {
                    kept[0].setNoAi(false); // let go: it must find its way out
                }
            }
            switch (phase[0]) {
                case 1 -> {
                    if (!colony.nest().foodWays().getNewTunnels().isEmpty()) {
                        tunnel[0] = colony.nest().foodWays().getNewTunnels().get(0);
                        BlockPos hole = tunnel[0].tunnel().hole();
                        helper.assertTrue(tunnel[0].food().equals(hay), "the tunnel is for " + tunnel[0].food() + ", not the hay");
                        helper.assertTrue(Math.max(Math.abs(hole.getX() - hay.getX()), Math.abs(hole.getZ() - hay.getZ())) <= 2,
                                "the hole " + hole.toShortString() + " is not beside the hay");
                        AntColoniesMod.LOGGER.info("[new-hole-test] planned: {} blocks, hole at {}", tunnel[0].tunnel().tunnel().size(), hole.toShortString());
                        phase[0] = 2;
                        since[0] = 0;
                    } else {
                        helper.assertTrue(since[0] < 600, "no tunnel was planned to the hay: " + colony.foragingHoles().describeNewHoles(level));
                    }
                }
                case 2 -> {
                    BlockPos hole = tunnel[0].tunnel().hole();
                    if (ForagingHoles.isDug(level, tunnel[0].tunnel()) && ForagingHoles.isOpen(level, hole)) {
                        helper.assertTrue(colony.foragingHoles().waysOut(level).stream().anyMatch(way -> way.mouth().equals(hole.above())),
                                "the new hole is open but not a way out");
                        AntColoniesMod.LOGGER.info("[new-hole-test] dug and opened at tick {}", helper.getTick());
                        cutWhenOpened[0] = colony.food().getPiecesCut(hay);
                        phase[0] = 5;
                        since[0] = 0;
                    }
                }
                case 5 -> {
                    // Foragers come and go by the new hole: one is seen at the far end of the tunnel or in the hole.
                    java.util.List<BlockPos> farEnd = new java.util.ArrayList<>(tunnel[0].tunnel().tunnel());
                    farEnd = farEnd.subList(Math.max(0, farEnd.size() - 2), farEnd.size());
                    farEnd.add(tunnel[0].tunnel().hole());
                    for (BlockPos cell : farEnd) {
                        usedTheHole[0] |= !level.getEntitiesOfClass(TermiteWorkerEntity.class, new net.minecraft.world.phys.AABB(cell),
                                worker -> colony.getId().equals(worker.getColonyId())).isEmpty();
                    }
                    int cut = colony.food().getPiecesCut(hay) - cutWhenOpened[0];
                    if (usedTheHole[0] && cut >= 2) {
                        AntColoniesMod.LOGGER.info("[new-hole-test] foragers use the new hole: {} pieces cut since it opened", cut);
                        level.setBlock(hay, Blocks.AIR.defaultBlockState(), 3);
                        colony.food().rescanPlantsSoon();
                        // One worker taking the last bite: kept at the top of the gallery under the hay for 30 seconds.
                        java.util.List<BlockPos> gallery = tunnel[0].gallery();
                        helper.assertTrue(gallery != null && !gallery.isEmpty(), "the tunnel to the hay has no feeding gallery");
                        BlockPos top = gallery.get(gallery.size() - 1);
                        kept[0] = TestIsolation.insectsOf(level, colony, TermiteWorkerEntity.class).stream()
                                .min(java.util.Comparator.comparingDouble(w -> w.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(top))))
                                .orElseThrow();
                        kept[0].getNavigator().stop();
                        kept[0].teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
                        kept[0].setNoAi(true);
                        keptUntil[0] = helper.getTick() + 600;
                        phase[0] = 3;
                        since[0] = 0;
                    }
                    helper.assertTrue(since[0] < 8000, "the foragers do not use the new hole: seen in it " + usedTheHole[0] + ", "
                            + cut + " pieces cut off the hay since it opened");
                }
                case 3 -> {
                    BlockPos hole = tunnel[0].tunnel().hole();
                    if (colony.nest().foodWays().isSpent(hole)) {
                        // The foraging is over at the end of the night: the holes are plugged.
                        // (Not "by sunrise": the holes are plugged once everybody is in, or PLUG_GRACE_TICKS after the
                        // foraging ends, and when the hay went just before dawn that runs past sunrise; the 8-server run of
                        // 2026-10-17 failed so. The rule: plugged within the grace, plus a while for a worker to get there.)
                        if (!ForagingHoles.isOpen(level, hole)) {
                            phase[0] = 6;
                            since[0] = 0;
                        } else if (colony.isForagingTime(level)) {
                            overSince[0] = -1;
                        } else {
                            if (overSince[0] < 0) {
                                overSince[0] = helper.getTick();
                            }
                            helper.assertTrue(helper.getTick() - overSince[0] <= ForagingHoles.PLUG_GRACE_TICKS + 1200,
                                    "the spent hole was not plugged " + (helper.getTick() - overSince[0]) + " ticks after the foraging ended: "
                                            + level.getBlockState(hole));
                        }
                    } else {
                        helper.assertTrue(since[0] < 6000, "the hole beside the gone hay was not spent: " + level.getBlockState(hole));
                    }
                }
                case 6 -> {
                    if (clock.phase(colony) == TestClock.Phase.NIGHT) { // and a new night: the spent one stays shut
                        phase[0] = 4;
                        since[0] = 0;
                    }
                }
                case 4 -> {
                    BlockPos hole = tunnel[0].tunnel().hole();
                    helper.assertTrue(!ForagingHoles.isOpen(level, hole), "the spent hole was opened again");
                    helper.assertTrue(colony.foragingHoles().waysOut(level).stream().noneMatch(way -> way.mouth().equals(hole.above())),
                            "the spent hole is still a way out");
                    if (since[0] > 1200 && helper.getTick() > keptUntil[0] + 1200) {
                        helper.assertTrue(colony.nest().isInside(level, tunnel[0].tunnel().end()), "the tunnel is no longer part of the nest");
                        helper.assertTrue(!kept[0].isAlive() || !tunnel[0].gallery().contains(kept[0].blockPosition())
                                        || kept[0].blockPosition().equals(galleryMouth(tunnel[0])),
                                "the worker kept in the gallery never got out: at " + helper.relativePos(kept[0].blockPosition()));
                        BlockPos mouth = galleryMouth(tunnel[0]);
                        AntColoniesMod.LOGGER.info("[new-hole-test] the spent gallery's mouth at the end: {}; the kept worker at {}",
                                level.getBlockState(mouth), helper.relativePos(kept[0].blockPosition()));
                        helper.succeed();
                    }
                }
                default -> {
                }
            }
        });
    }

    /** How far east of the nest's middle the hay lies in the long walk (inside a big colony's 40-block reach). */
    private static final int LONG_HAY_EAST = 38;
    /** How many trips out of the far hole and back into the nest the long walk wants. */
    private static final int LONG_TRIPS = 3;
    /** The most (ticks) a trip home from the far hole may take (about 45 blocks of tunnel and shaft; my pick, generous). */
    private static final int LONG_TRIP_TICKS = 2400;

    /**
     * The termites-underground safety net, step 0 (the user's plan of 2026-10-17): the long walk. A big colony knows of
     * hay 38 blocks east; once it has planned its tunnel there, the tunnel is dug out at once (a ready-made gallery, so
     * the test is about walking, not digging); the builders dig the feeding gallery and open the hole, and 13 old
     * workers forage by it, both ways through the 1-block tunnel. A trip counts when a worker is at the far end (the
     * tunnel's last blocks, the hole, or the feeding gallery up under the hay: once it is dug the workers eat the hay
     * from below, as real Macrotermes do, and seldom use the hole), is then seen in the middle of the tunnel (seen on the
     * open ground, it starts over: foragers walking to the hay over the ground pass the hole's spot), and gets home (anywhere in the nest within 8 blocks of its middle, out of this tunnel: the garden
     * rooms, where foragers take their load), within {@value #LONG_TRIP_TICKS} ticks (a worker that keeps
     * moving but goes round in circles is caught by this, not by the stuck watcher). {@value #LONG_TRIPS} trips are
     * wanted, the stuck watcher fails the test if any insect gets stuck under the ground, and it must have seen
     * workers walking under the ground (a watcher that saw nothing proves nothing).
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void foragersWalkALongGalleryOutAndBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // The legs walking the whole gallery (what a break-in into a hidden trip leaves them to do): no hidden trips here.
        LongGallery scene = new LongGallery(helper, false);
        StuckWatcher.Tracker watcher = StuckWatcher.watch(helper, () -> TermiteMoundTests.colony(helper));
        TestClock clock = TestClock.moving(helper, 18.5);
        FoodWays.NewTunnel[] tunnel = scene.tunnel;
        // Each worker on its way home: 1 came out of the far hole, 2 seen in the middle of the tunnel since; and when it was last outside.
        java.util.Map<java.util.UUID, Integer> stage = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, Integer> outAt = new java.util.HashMap<>();
        java.util.List<Integer> tripTicks = new java.util.ArrayList<>();
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (!scene.tick(colony)) {
                return;
            }
            java.util.List<BlockPos> cells = tunnel[0].tunnel().tunnel();
            java.util.List<BlockPos> middle = cells.subList(cells.size() * 2 / 5, cells.size() * 3 / 5);
            java.util.Set<BlockPos> farEnd = new java.util.HashSet<>(cells.subList(Math.max(0, cells.size() - 2), cells.size()));
            farEnd.add(tunnel[0].tunnel().hole());
            if (tunnel[0].gallery() != null) {
                farEnd.addAll(tunnel[0].gallery());
            }
            BlockPos center = colony.getCenter();
            for (TermiteWorkerEntity worker : TestIsolation.insectsOf(level, colony, TermiteWorkerEntity.class)) {
                BlockPos at = worker.blockPosition();
                java.util.UUID id = worker.getUUID();
                if (farEnd.contains(at)) {
                    stage.put(id, 1); // at the far end: the tunnel's last blocks, the gallery under the hay, or the hole
                    outAt.put(id, (int) helper.getTick());
                } else if (stage.containsKey(id) && level.canSeeSky(at)) {
                    stage.remove(id); // out on the ground again: not on its way home
                } else if (stage.getOrDefault(id, 0) == 1 && middle.contains(at)) {
                    stage.put(id, 2); // on its way home underground
                } else if (stage.getOrDefault(id, 0) == 2 && !cells.contains(at) && colony.nest().isInside(level, at)
                        && Math.max(Math.abs(at.getX() - center.getX()), Math.abs(at.getZ() - center.getZ())) <= 8) {
                    // Home: anywhere in the nest near its middle (the garden rooms, where foragers take their load), out of this tunnel.
                    stage.remove(id);
                    int took = (int) helper.getTick() - outAt.get(id);
                    tripTicks.add(took);
                    AntColoniesMod.LOGGER.info("[long-walk] trip {} home in {} ticks, back in at tick {} ({}) at {}", tripTicks.size(), took,
                            helper.getTick(), clock.describe(), helper.relativePos(at));
                    helper.assertTrue(took <= LONG_TRIP_TICKS, "a worker took " + took + " ticks to get home from the far hole (at most "
                            + LONG_TRIP_TICKS + "): lost on the way?");
                }
            }
            for (java.util.Map.Entry<java.util.UUID, Integer> going : stage.entrySet()) {
                // Seen in the tunnel on its way home and still not in, long after: lost underground.
                helper.assertTrue(going.getValue() < 2 || helper.getTick() - outAt.get(going.getKey()) <= 2 * LONG_TRIP_TICKS,
                        "a worker out of the far hole at tick " + outAt.get(going.getKey()) + " went into the tunnel and never got home");
            }
            if (helper.getTick() % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[long-walk] tick {} {}: hole {}, {} on their way home, trips {}, {} insect-ticks watched underground, {}",
                        helper.getTick(), clock.describe(), level.getBlockState(tunnel[0].tunnel().hole()), stage.size(), tripTicks,
                        watcher.watchedTicks(), colony.foragingHoles().describeNewHoles(level));
            }
            if (tripTicks.size() >= LONG_TRIPS) {
                helper.assertTrue(watcher.watchedTicks() > 0, "the stuck watcher never saw an insect walking under the ground");
                AntColoniesMod.LOGGER.info("[long-walk] done: trips {} ticks, {} insect-ticks watched underground", tripTicks, watcher.watchedTicks());
                helper.succeed();
            }
        });
    }

    /** How many hidden trips must come out at the far end of their stretch (both ways) before the save and the break-in. */
    private static final int HIDDEN_ARRIVALS = 3;

    /**
     * Termites underground, step 2b: hidden trips (the user's rule of 2026-10-17). In the long gallery, a termite whose
     * route runs along a long closed stretch of the tunnel leaves the world and comes back at the far end after the
     * walk's time: while it travels it is in no place in the world; its stretch is at least the species file's
     * {@code hidden_trip} steps; its time is a walk's (2 to 20 ticks a step); when its trip ends it is back in the world
     * where its stretch ends (or, if something broke in on the way, where it was by then); trips go both ways. Then the
     * game is saved and loaded while some travel (they come out as they were, each once). Then a player breaks into the
     * gallery (the roof over a traveller's stretch, ahead of it): every termite travelling there shows up where it would
     * be by then, at once (within the colony's second), none left hidden; and they walk on (the stuck watcher).
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void hiddenTripsGoUnseenAndShowUpWhereTheyAreWhenTheGalleryIsBrokenInto(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LongGallery scene = new LongGallery(helper, true);
        StuckWatcher.Tracker watcher = StuckWatcher.watch(helper, () -> TermiteMoundTests.colony(helper));
        TestClock clock = TestClock.moving(helper, 18.5);
        // Each traveller seen: where its stretch ends, whether it goes outward, when it is due, and where it would be last tick.
        java.util.Map<java.util.UUID, BlockPos> endOf = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, Boolean> outwardOf = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, Long> dueAt = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, BlockPos> lastWhere = new java.util.HashMap<>();
        java.util.Set<java.util.UUID> putBack = new java.util.HashSet<>();
        java.util.Set<java.util.UUID> stillHidden = new java.util.HashSet<>();
        int[] stage = {0}; // 0 trips both ways, 1 the save and load, 2 the break-in, 3 walking on
        int[] arrivals = {0};
        boolean[] outward = {false};
        boolean[] homeward = {false};
        java.util.List<java.util.UUID> loaded = new java.util.ArrayList<>();
        long[] stageAt = {0};
        int[] brokenBefore = {0};
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (!scene.tick(colony)) {
                return;
            }
            com.antcolonies.colony.HiddenTrips trips = colony.hiddenTrips();
            long now = level.getGameTime();
            BlockPos center = colony.getCenter();
            int shortest = trips.shortest();
            for (java.util.UUID id : trips.travellers()) {
                helper.assertTrue(level.getEntity(id) == null, "a termite travelling unseen is still in the world");
                java.util.List<BlockPos> run = trips.runOf(id);
                if (!endOf.containsKey(id)) {
                    long[] times = trips.timesOf(id);
                    double perStep = (times[1] - times[0]) / (double) (run.size() - 1);
                    helper.assertTrue(run.size() - 1 >= shortest, "a hidden stretch of only " + (run.size() - 1) + " steps (at least " + shortest + ")");
                    helper.assertTrue(perStep >= 2 && perStep <= 20, "a hidden trip of " + String.format("%.1f", perStep) + " ticks a step: not a walk's pace");
                    BlockPos end = run.get(run.size() - 1);
                    endOf.put(id, end);
                    outwardOf.put(id, end.distSqr(center) > run.get(0).distSqr(center));
                    dueAt.put(id, times[1]);
                    AntColoniesMod.LOGGER.info("[hidden-test] tick {}: a trip of {} steps, {} ticks a step, from {} to {}", helper.getTick(),
                            run.size() - 1, String.format("%.1f", perStep), helper.relativePos(run.get(0)), helper.relativePos(end));
                }
                helper.assertTrue(now <= dueAt.get(id) + 60, "a hidden trip due out at " + dueAt.get(id) + " is still under way at " + now);
                lastWhere.put(id, trips.whereNow(id, now));
            }
            // Every trip that has ended: the termite is back in the world, where its stretch ends, or where it was by then
            // (a hole opening on its way or the player's break-in put it back early).
            for (java.util.Iterator<java.util.Map.Entry<java.util.UUID, BlockPos>> it = endOf.entrySet().iterator(); it.hasNext(); ) {
                java.util.Map.Entry<java.util.UUID, BlockPos> entry = it.next();
                java.util.UUID id = entry.getKey();
                if (trips.isTravelling(id)) {
                    continue;
                }
                net.minecraft.world.entity.Entity back = level.getEntity(id);
                helper.assertTrue(back != null, "a termite whose hidden trip ended is not back in the world");
                BlockPos at = back.blockPosition();
                BlockPos was = lastWhere.get(id);
                boolean atEnd = at.distManhattan(entry.getValue()) <= 2;
                helper.assertTrue(atEnd || (was != null && at.distManhattan(was) <= 2), "a termite came out of its hidden trip at "
                        + helper.relativePos(at) + ", neither at its stretch's end " + helper.relativePos(entry.getValue())
                        + " nor where it was by then " + (was == null ? "(unknown)" : helper.relativePos(was)));
                if (atEnd && !putBack.contains(id)) {
                    arrivals[0]++;
                    if (outwardOf.get(id)) {
                        outward[0] = true;
                    } else {
                        homeward[0] = true;
                    }
                }
                loaded.remove(id);
                it.remove();
            }
            switch (stage[0]) {
                case 0 -> {
                    if (arrivals[0] >= HIDDEN_ARRIVALS && outward[0] && homeward[0] && trips.travelling() > 0) {
                        // The game saved and loaded while some travel: they come out as they were, each once.
                        loaded.addAll(trips.travellers());
                        Colony copy = Colony.load(colony.save());
                        com.antcolonies.colony.ColonyManager.get(level).replaceForTests(copy);
                        copy.hiddenTrips().setOnForTests(true);
                        AntColoniesMod.LOGGER.info("[hidden-test] tick {} {}: saved and loaded with {} travelling", helper.getTick(), clock.describe(), loaded.size());
                        stage[0] = 1;
                        stageAt[0] = helper.getTick();
                    }
                }
                case 1 -> {
                    if (loaded.isEmpty()) {
                        stage[0] = 2;
                        stageAt[0] = helper.getTick();
                    }
                }
                case 2 -> {
                    // A traveller a quarter to two thirds of the way: the player digs out the roof over its stretch, ahead of it.
                    for (java.util.UUID id : trips.travellers()) {
                        long[] times = trips.timesOf(id);
                        double done = (now - times[0]) / (double) (times[1] - times[0]);
                        java.util.List<BlockPos> run = trips.runOf(id);
                        if (done < 0.25 || done > 0.66 || run.size() < 16) {
                            continue;
                        }
                        BlockPos under = null;
                        for (int i = run.size() * 3 / 4; i < run.size() - 1 && under == null; i++) {
                            if (!level.getBlockState(run.get(i).above()).isAir()) {
                                under = run.get(i); // a cell with a roof of its own (not more tunnel above it)
                            }
                        }
                        if (under == null) {
                            continue;
                        }
                        level.setBlock(under.above(), Blocks.AIR.defaultBlockState(), 3);
                        brokenBefore[0] = trips.brokenInto();
                        for (java.util.UUID other : trips.travellers()) {
                            if (trips.runOf(other).contains(under)) {
                                putBack.add(other);
                                stillHidden.add(other);
                            }
                        }
                        AntColoniesMod.LOGGER.info("[hidden-test] tick {}: the roof over {} broken, {} travelling there", helper.getTick(),
                                helper.relativePos(under), putBack.size());
                        stage[0] = 3;
                        stageAt[0] = helper.getTick();
                        break;
                    }
                }
                case 3 -> {
                    // Once out, a termite put back may start a new hidden trip of its own later: only the trip broken into counts.
                    stillHidden.removeIf(id -> !trips.isTravelling(id));
                    helper.assertTrue(stillHidden.isEmpty() || helper.getTick() - stageAt[0] <= 25,
                            "a termite still travelling unseen through a gallery broken into " + (helper.getTick() - stageAt[0]) + " ticks ago");
                    if (helper.getTick() - stageAt[0] >= 400) {
                        helper.assertTrue(trips.brokenInto() > brokenBefore[0], "no trip was counted as broken into");
                        AntColoniesMod.LOGGER.info("[hidden-test] done: {} arrivals, {} put back by the break-in{}", arrivals[0], putBack.size(),
                                trips.describe());
                        helper.succeed();
                    }
                }
                default -> {
                }
            }
            if (helper.getTick() % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[hidden-test] tick {} {}: stage {}, {} arrivals (out {}, home {}){}, {} insect-ticks watched underground",
                        helper.getTick(), clock.describe(), stage[0], arrivals[0], outward[0], homeward[0], trips.describe(), watcher.watchedTicks());
            }
        });
    }

    /**
     * The long gallery (the long walk and the hidden trips): a big colony (16 real workers, 3 of them builders, the rest
     * old foragers, and 500 numbers) knows of hay {@value #LONG_HAY_EAST} blocks east; once it has planned its tunnel
     * there, the tunnel is dug out at once (a ready-made gallery). The hay lasts the whole test.
     */
    static final class LongGallery {
        final GameTestHelper helper;
        final BlockPos hay;
        final FoodWays.NewTunnel[] tunnel = {null};

        LongGallery(GameTestHelper helper, boolean hiddenTrips) {
            this.helper = helper;
            ServerLevel level = helper.getLevel();
            level.getServer().tickRateManager().setTickRate(2000.0F);
            for (int x = 0; x <= MIDDLE + LONG_HAY_EAST + 6; x++) {
                for (int z = 0; z < 25; z++) {
                    for (int y = 0; y < SURFACE - 1; y++) {
                        helper.setBlock(x, y, z, Blocks.DIRT);
                    }
                    helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
                }
            }
            helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
            helper.runAfterDelay(5, () -> {
                for (int i = 0; i < 16; i++) {
                    helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 4 + i % 8, SURFACE, MIDDLE - 2 - i / 8);
                }
            });
            hay = helper.absolutePos(new BlockPos(MIDDLE + LONG_HAY_EAST, SURFACE, MIDDLE));
            helper.runAfterDelay(10, () -> {
                Colony colony = TermiteMoundTests.colony(helper);
                helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
                long now = level.getGameTime();
                int i = 0;
                for (TermiteWorkerEntity worker : level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds())) {
                    // 3 builders (they dig the feeding gallery), the rest old foragers.
                    worker.setGrownUpAt(now - (i++ < 3 ? colony.profile().worker().nurseUntilTicks() : colony.profile().worker().builderUntilTicks()) - 1000);
                }
                TermiteSoldierTests.digNestAtOnce(level, colony);
                colony.population().addNewMembers(WorkerAntEntity.CASTE, now - 1000, 500);
                colony.hiddenTrips().setOnForTests(hiddenTrips);
                level.setBlock(hay, Blocks.HAY_BLOCK.defaultBlockState(), 3);
                colony.food().rescanPlantsSoon();
                colony.foodMap().knowForTests(hay);
            });
        }

        /** Each tick: the hay kept, the tunnel dug at once once it is planned. True once the tunnel is there. */
        boolean tick(@Nullable Colony colony) {
            ServerLevel level = helper.getLevel();
            if (colony == null || helper.getTick() < 20) {
                return false;
            }
            if (colony.food().getPiecesCut(hay) > 24) {
                colony.food().setPiecesCutForTests(hay, 0); // a bigger stack of hay: it lasts the whole test
            }
            if (tunnel[0] == null) {
                if (!colony.nest().foodWays().getNewTunnels().isEmpty()) {
                    tunnel[0] = colony.nest().foodWays().getNewTunnels().get(0);
                    for (BlockPos cell : tunnel[0].tunnel().tunnel()) {
                        level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3); // the ready-made gallery
                    }
                    AntColoniesMod.LOGGER.info("[long-walk] a tunnel of {} blocks dug at once, hole at {}", tunnel[0].tunnel().tunnel().size(),
                            helper.relativePos(tunnel[0].tunnel().hole()));
                } else {
                    helper.assertTrue(helper.getTick() < 1000, "no tunnel was planned to the hay: " + colony.foragingHoles().describeNewHoles(level));
                }
                return false;
            }
            return true;
        }
    }

    /** The cell of a feeding gallery next to the end of its tunnel (the gallery's way in). */
    private static BlockPos galleryMouth(FoodWays.NewTunnel tunnel) {
        BlockPos end = tunnel.tunnel().end();
        return tunnel.gallery().stream().filter(c -> c.distManhattan(end) == 1).findFirst().orElse(tunnel.gallery().get(0));
    }

    /**
     * The user's bugs (2026-10-14): a 5-block fallen tree lying up a slope, 5 to 7 blocks higher than the nest. The
     * colony must see it and plan a tunnel to its lowest block; then that block is eaten away (taken out here), but the
     * rest of the tree is still there, so the hole must not be given up; and the tunnel, climbing the slope, is dug to
     * its last block (dug upward) and the hole beside the tree opened.
     */
    @GameTest(template = "arena", timeoutTicks = 30000, skyAccess = true)
    public static void aFallenTreeUpASlopeKeepsItsHoleWhenItsLowestBlockIsEaten(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        int treeStart = MIDDLE + 20;
        // The ground rises east of the nest, a block a step: 5 blocks up at the tree's foot (higher than the plants the colony
        // sees round the nest; the user's tree on a terrace was never seen), 7 under its far end.
        java.util.function.IntUnaryOperator rise = x -> x <= treeStart ? Math.max(0, x - (treeStart - 5)) : Math.min(7, 5 + (x - treeStart));
        for (int x = 0; x <= treeStart + 8; x++) {
            for (int z = 0; z < 25; z++) {
                int top = SURFACE - 1 + rise.applyAsInt(x);
                for (int y = 0; y < top; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, top, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 6; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 3 + i, SURFACE, MIDDLE - 2);
            }
        });
        java.util.List<BlockPos> tree = new java.util.ArrayList<>();
        for (int x = treeStart; x < treeStart + 5; x++) {
            tree.add(helper.absolutePos(new BlockPos(x, SURFACE + rise.applyAsInt(x), MIDDLE)));
        }
        int[] phase = {0}; // 0 setting up, 1 waiting for the plan, 2 digging and opening
        FoodWays.NewTunnel[] tunnel = {null};
        int[] since = {0};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            // The food lies a set distance out, past the old 2 holes: with the arms of termites underground (step 1)
            // a hole along an arm may land near it, and the colony then uses that hole (a runway) and digs no new
            // tunnel. This scene is about the tunnel out to far food, so the arms are switched off here.
            colony.nest().galleries().setSwitchForTests(false);
            long middleAged = level.getGameTime() - colony.profile().worker().nurseUntilTicks() - 1000;
            for (TermiteWorkerEntity worker : level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds())) {
                worker.setGrownUpAt(middleAged);
            }
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - 1000, 500);
            TestClock.still(helper, 0.0); // midnight: foraging time
            for (BlockPos wood : tree) {
                level.setBlock(wood, com.antcolonies.registry.ModBlocks.DEAD_WOOD.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X), 3);
            }
            colony.food().rescanPlantsSoon();
            colony.foodMap().knowForTests(tree.get(0));
            phase[0] = 1;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || phase[0] == 0) {
                return;
            }
            since[0]++;
            if (phase[0] == 1) {
                if (colony.nest().foodWays().getNewTunnels().isEmpty()) {
                    helper.assertTrue(since[0] < 600, "no tunnel was planned to the tree: " + colony.foragingHoles().describeNewHoles(level)
                            + ", tree seen " + colony.food().plants(level).contains(tree.get(0)) + ", known " + colony.foodMap().knows(tree.get(0), null) + ", tree " + (tree.get(0).getY() - colony.getCenter().getY()) + " above the nest middle, " + level.getBlockState(tree.get(0)));
                    return;
                }
                tunnel[0] = colony.nest().foodWays().getNewTunnels().get(0);
                helper.assertTrue(tunnel[0].food().equals(tree.get(0)), "the tunnel is for " + tunnel[0].food() + ", not the tree's lowest block");
                level.setBlock(tree.get(0), Blocks.AIR.defaultBlockState(), 3); // eaten away
                colony.food().rescanPlantsSoon();
                phase[0] = 2;
                since[0] = 0;
                return;
            }
            BlockPos hole = tunnel[0].tunnel().hole();
            if (since[0] % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[new-hole-test] slope: dug {} of {}, hole {}, may open {}, foraging time {}{}", tunnel[0].tunnel().tunnel().stream().filter(c -> Nest.isDugOut(level, c)).count(),
                        tunnel[0].tunnel().tunnel().size(), level.getBlockState(hole), colony.foragingHoles().mayOpen(level, hole), colony.isForagingTime(level),
                        colony.alarm().describe(level));
                java.util.List<BlockPos> gallery = tunnel[0].gallery() == null ? java.util.List.of() : tunnel[0].gallery();
                BlockPos next = colony.nest().findDigTarget(level);
                java.util.List<BlockPos> cells = tunnel[0].tunnel().tunnel();
                AntColoniesMod.LOGGER.info("[new-hole-test] slope: gallery dug {} of {}, next dig {} ({}, stand spots {}), hole {}, tunnel from {} to {}, workers at {}",
                        gallery.stream().filter(c -> Nest.isDugOut(level, c)).count(), gallery.size(),
                        next == null ? null : helper.relativePos(next), next == null ? null : level.getBlockState(next),
                        next == null ? null : colony.nest().standSpotsFor(level, next).stream().map(p -> helper.relativePos(p).toShortString()).toList(),
                        helper.relativePos(hole),
                        helper.relativePos(cells.get(0)), helper.relativePos(cells.get(cells.size() - 1)),
                        TestIsolation.insectsOf(level, colony, TermiteWorkerEntity.class).stream()
                                .map(w -> helper.relativePos(w.blockPosition()).toShortString() + " " + colony.roleOf(level, w)
                                        + (w.isCarrying() ? " carrying" : "") + " " + w.goalSelector.getAvailableGoals().stream()
                                                .filter(g -> g.isRunning()).map(g -> g.getGoal().getClass().getSimpleName()).toList())
                                .toList());
            }
            helper.assertTrue(!colony.nest().foodWays().isSpent(hole), "the hole was given up while the rest of the tree is still there");
            if (ForagingHoles.isDug(level, tunnel[0].tunnel()) && ForagingHoles.isOpen(level, hole)) {
                helper.succeed();
            }
            helper.assertTrue(since[0] < 28000, "the tunnel to the tree was not dug and opened: " + colony.foragingHoles().describeNewHoles(level));
        });
    }
    /**
     * Like {@link #aFallenLogAndTheStumpOnItAreEatenFromBelowByDay}, with the log lying on the edge of a step: the ground beside it on
     * one side is 2 blocks lower (the user's tree in the savanna, whose gallery was left out, 2026-10-14). The open side
     * of the gallery is walled with a mud crust as it is dug (wall first), and the log is eaten from below by day.
     * Brain part 3: by day the brain remembers the log as food that can be eaten from inside, and the forager numbers go
     * to it then too (they forage by day only what they can eat from inside).
     */
    @GameTest(template = "arena", timeoutTicks = 90000, skyAccess = true)
    public static void aFallenLogOnTheEdgeOfAStepIsEatenFromBelowByDay(GameTestHelper helper) {
        eatenFromBelow(helper, true, Food.LOG);
    }

    /**
     * Eating dead wood from below (the user's idea, 2026-10-14): a fallen log lies on level ground 20 blocks east of the
     * nest. At night the workers dig a tunnel out to it, then a feeding gallery up under it; by day (no foraging) they
     * still eat the log, from the gallery under it, without going out. Mud casts (the user's choice, 2026-10-14): a block
     * of the log eaten away leaves a mud cast joined to the feeding gallery, and the termites eat on from inside it: a
     * block of stump standing on the log, which no gallery cell touches, is eaten by day from the cast under it, and is a
     * cast itself afterwards.
     */
    @GameTest(template = "arena", timeoutTicks = 90000, skyAccess = true)
    public static void aFallenLogAndTheStumpOnItAreEatenFromBelowByDay(GameTestHelper helper) {
        eatenFromBelow(helper, false, Food.STUMP);
    }

    /**
     * A fallen branch lies on the ground, thin (nothing bumps into it), so it is no roof by itself: the workers case it
     * in mud as they dig the feeding gallery under it (wall first), the mud holds it up over the gallery, and it is
     * eaten from below by day. It lies on the edge of a step (the ground beside it 2 blocks lower, the user's branch of
     * 2026-10-14 whose casing and gallery stayed open on that side, 2 blocks high): the gallery's open side is walled
     * and the branch is cased on that side too.
     */
    @GameTest(template = "arena", timeoutTicks = 90000, skyAccess = true)
    public static void aFallenBranchOnTheEdgeOfAStepIsCasedInMudAndEatenFromBelowByDay(GameTestHelper helper) {
        eatenFromBelow(helper, true, Food.BRANCH);
    }

    /**
     * M7.5 step 6d: a fallen giant's log, 3 blocks thick (silver bark round a bleached core), is eaten the same way: a
     * tunnel out, a feeding gallery under its underside, eaten from below by day, and a block eaten away leaves a mud
     * cast (the bottom row is nearly eaten at dawn, as the stump's log is, so the test is quick).
     */
    @GameTest(template = "arena", timeoutTicks = 90000, skyAccess = true)
    public static void aFallenGiantsLogIsEatenFromBelowByDayAndLeavesCasts(GameTestHelper helper) {
        eatenFromBelow(helper, false, Food.GIANT);
    }

    private enum Food { LOG, STUMP, BRANCH, GIANT }

    /**
     * (60000 ticks, was 40000: once the stump was still on its last bitten stage when the time ran out, 2026-10-14; 90000
     * on the test clock, roadmap 3a: real nights and days from 18:30 for the log and the stump, the gallery dug through
     * the nights, then eaten from below by day, over more than one day if need be; the branch on a still midday.)
     */
    private static void eatenFromBelow(GameTestHelper helper, boolean onAStep, Food kind) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        int logStart = MIDDLE + 20;
        for (int x = 0; x <= logStart + 8; x++) {
            for (int z = 0; z < 25; z++) {
                int top = onAStep && x >= logStart - 3 && z > MIDDLE ? SURFACE - 3 : SURFACE - 1;
                for (int y = 0; y < top; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, top, z, Blocks.GRASS_BLOCK);
                for (int y = top + 1; y < SURFACE; y++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 6; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 3 + i, SURFACE, MIDDLE - 2);
            }
        });
        StuckWatcher.watch(helper, () -> TermiteMoundTests.colony(helper));
        java.util.List<BlockPos> log = new java.util.ArrayList<>();
        for (int x = logStart; x < logStart + (kind == Food.BRANCH ? 3 : 5); x++) {
            log.add(helper.absolutePos(new BlockPos(x, SURFACE, MIDDLE)));
        }
        BlockPos stump = log.get(0).above(); // put on the log at dawn (Food.STUMP)
        int[] phase = {0}; // 0 setting up, 1 tunnel and gallery dug (by night), 2 day: eaten from below
        // A branch is small: by night the foragers outside would eat it all before the gallery is dug, so it is day.
        TestClock clock = kind == Food.BRANCH ? TestClock.still(helper, 12.0) : TestClock.moving(helper, 18.5);
        boolean[] night = {false}; // (phase 2: a night came between the days)
        int[] eatenAtDawn = {0};
        boolean[] numbersAte = {kind != Food.LOG}; // (brain part 3, the log test only)
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            // The food lies a set distance out, past the old 2 holes: with the arms of termites underground (step 1)
            // a hole along an arm may land near it, and the colony then uses that hole (a runway) and digs no new
            // tunnel. This scene is about the tunnel out to far food, so the arms are switched off here.
            colony.nest().galleries().setSwitchForTests(false);
            long middleAged = level.getGameTime() - colony.profile().worker().nurseUntilTicks() - 1000;
            for (TermiteWorkerEntity worker : level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds())) {
                worker.setGrownUpAt(middleAged);
            }
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - 1000, 500);
            if (kind == Food.GIANT) {
                // The rest of the giant's log round the bottom row: 3 wide, 3 high, bark outside, the core inside.
                for (BlockPos bottom : log) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dy = 0; dy <= 2; dy++) {
                            boolean core = dz == 0 && dy == 1;
                            level.setBlock(bottom.offset(0, dy, dz), (core ? com.antcolonies.registry.TreeBlocks.BLEACHED_LOG
                                    : com.antcolonies.registry.TreeBlocks.BLEACHED_WOOD).get().defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X), 3);
                        }
                    }
                }
            }
            for (int i = 0; kind != Food.GIANT && i < log.size(); i++) {
                level.setBlock(log.get(i), kind == Food.BRANCH
                        ? com.antcolonies.registry.ModBlocks.FALLEN_BRANCH.get().defaultBlockState()
                                .setValue(com.antcolonies.block.FallenBranchBlock.AXIS, net.minecraft.core.Direction.Axis.X)
                                .setValue(com.antcolonies.block.FallenBranchBlock.PART, i == 0 ? com.antcolonies.block.FallenBranchBlock.Part.BASE
                                        : i == log.size() - 1 ? com.antcolonies.block.FallenBranchBlock.Part.TIP : com.antcolonies.block.FallenBranchBlock.Part.MIDDLE)
                        : com.antcolonies.registry.ModBlocks.DEAD_WOOD.get().defaultBlockState()
                                .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X), 3);
            }
            if (kind == Food.BRANCH) {
                // Tall grass round the branch, 2 blocks high, green and dry by turns (the savanna, the user's question of
                // 2026-10-14): on both sides of it and down on the step.
                for (int x = logStart - 2; x <= logStart + 3; x++) {
                    for (int z : new int[] {MIDDLE - 1, MIDDLE + 1}) {
                        BlockPos foot = helper.absolutePos(new BlockPos(x, (onAStep && x >= logStart - 3 && z > MIDDLE ? SURFACE - 2 : SURFACE), z));
                        if (level.getBlockState(foot).isAir() && level.getBlockState(foot.above()).isAir()) {
                            net.minecraft.world.level.block.DoublePlantBlock.placeAt(level, x % 2 == 0 ? Blocks.TALL_GRASS.defaultBlockState()
                                    : com.antcolonies.registry.ModBlocks.TALL_DRY_GRASS.get().defaultBlockState(), foot, 3);
                        }
                    }
                }
            }
            colony.food().rescanPlantsSoon();
            colony.foodMap().knowForTests(log.get(0));
            phase[0] = 1;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || phase[0] == 0) {
                return;
            }
            int eaten = 0;
            for (BlockPos wood : log) {
                BlockState state = level.getBlockState(wood);
                eaten += state.hasProperty(com.antcolonies.block.DeadWoodBlock.EATEN) ? state.getValue(com.antcolonies.block.DeadWoodBlock.EATEN) * 10
                        + colony.food().getPiecesCut(wood)
                        : state.getBlock() instanceof com.antcolonies.block.FallenBranchBlock ? state.getValue(com.antcolonies.block.FallenBranchBlock.EATEN) * 10
                        + colony.food().getPiecesCut(wood) : 100;
            }
            FoodWays.NewTunnel tunnel = colony.nest().foodWays().getNewTunnels().isEmpty() ? null : colony.nest().foodWays().getNewTunnels().get(0);
            if (helper.getTick() % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[new-hole-test] log from below: {}, phase {}, tunnel {}, gallery {}, eaten {}{}", clock.describe(), phase[0],
                        tunnel == null ? "none" : tunnel.tunnel().tunnel().stream().filter(c -> Nest.isDugOut(level, c)).count() + "/" + tunnel.tunnel().tunnel().size(),
                        tunnel == null || tunnel.gallery() == null ? "none" : tunnel.gallery().stream().filter(c -> Nest.isDugOut(level, c)).count() + "/" + tunnel.gallery().size(),
                        eaten, kind == Food.STUMP && phase[0] == 2 ? ", under the stump " + level.getBlockState(log.get(0)) + " (cut " + colony.food().getPiecesCut(log.get(0))
                                + "), stump " + level.getBlockState(stump) + " (cut " + colony.food().getPiecesCut(stump) + ")" : "");
            }
            if (tunnel != null) {
                // Planned with the tunnel, as one job (the user's decision, 2026-10-14), not once the tunnel is dug.
                helper.assertTrue(tunnel.gallery() != null && !tunnel.gallery().isEmpty(), "the tunnel was planned without its feeding gallery");
                // Wall first: no wall, floor or roof of a dug tunnel or gallery block is ever open to the outside, not even
                // for a tick (the roof of the gallery is the log, and a block of it eaten away leaves a mud cast).
                java.util.Set<BlockPos> nest = new java.util.HashSet<>(tunnel.gallery());
                nest.addAll(tunnel.tunnel().tunnel());
                nest.add(tunnel.tunnel().hole());
                java.util.List<BlockPos> dug = new java.util.ArrayList<>(tunnel.tunnel().tunnel());
                dug.addAll(tunnel.gallery());
                for (BlockPos cell : dug) {
                    if (!Nest.isDugOut(level, cell)) {
                        continue;
                    }
                    for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                        BlockPos next = cell.relative(side);
                        helper.assertTrue(nest.contains(next) || colony.nest().nestCells(level).contains(next)
                                || !com.antcolonies.colony.NestWalls.isOpenToward(level, next, side.getOpposite()),
                                "wall first: the dug block " + cell.toShortString() + " is open to the outside at " + next.toShortString() + " (" + level.getBlockState(next) + ")");
                        // A branch over it (no roof by itself) is cased in mud all round.
                        if (com.antcolonies.colony.NestWalls.isWalkThroughFood(level, next)) {
                            for (net.minecraft.core.Direction round : net.minecraft.core.Direction.values()) {
                                BlockPos beside = next.relative(round);
                                helper.assertTrue(beside.equals(cell) || nest.contains(beside) || colony.nest().nestCells(level).contains(beside)
                                        || com.antcolonies.colony.NestWalls.isWalkThroughFood(level, beside)
                                        || !com.antcolonies.colony.NestWalls.isOpenToward(level, beside, round.getOpposite()),
                                        "wall first: the branch " + next.toShortString() + " over the dug block " + cell.toShortString()
                                                + " is not cased in mud at " + beside.toShortString());
                            }
                        }
                    }
                }
            }
            if (phase[0] == 1) {
                if (tunnel != null) {
                    long under = log.stream().filter(wood -> tunnel.gallery().contains(wood.below())).count();
                    helper.assertTrue(under >= log.size() - 1, "the feeding gallery runs under only " + under + " of the " + log.size() + " blocks of food: " + tunnel.gallery());
                    if (tunnel.gallery().stream().allMatch(c -> Nest.isDugOut(level, c)) && clock.phase(colony) == TestClock.Phase.DAY) {
                        eatenAtDawn[0] = eaten;
                        if (kind == Food.LOG) {
                            colony.field().setOnForTests(true); // (brain part 3: the 500 numbers forage too, by day only from inside)
                        }
                        phase[0] = 2;
                        if (kind == Food.GIANT) {
                            // The giant's bottom row nearly eaten (quicker): a cast comes soon.
                            for (BlockPos block : log) {
                                BlockState wood = level.getBlockState(block);
                                if (wood.hasProperty(com.antcolonies.block.DeadWoodBlock.EATEN)) {
                                    level.setBlock(block, wood.setValue(com.antcolonies.block.DeadWoodBlock.EATEN, com.antcolonies.block.DeadWoodBlock.MAX_EATEN), 3);
                                    // (all but 2 of its pieces: eating from below by day is slow, and a cast must come within the day)
                                    colony.food().setPiecesCutForTests(block, colony.food().piecesOf(wood) - 2);
                                }
                            }
                            colony.food().rescanPlantsSoon();
                        }
                        if (kind == Food.STUMP) {
                            // The stump on the log; it and every block of the log nearly eaten (quicker, and alike, so the
                            // block under the stump, nearest the hole, goes first). The stump only from the log's cast.
                            BlockState wood = com.antcolonies.registry.ModBlocks.DEAD_WOOD.get().defaultBlockState()
                                    .setValue(com.antcolonies.block.DeadWoodBlock.EATEN, com.antcolonies.block.DeadWoodBlock.MAX_EATEN);
                            for (BlockPos block : log) {
                                if (level.getBlockState(block).getBlock() instanceof com.antcolonies.block.DeadWoodBlock) {
                                    level.setBlock(block, wood.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X), 3);
                                    colony.food().forgetCutsForTests(block);
                                }
                            }
                            level.setBlock(stump, wood, 3);
                            colony.food().rescanPlantsSoon();
                        }
                    }
                }
                helper.assertTrue(helper.getTick() < 45000, "the tunnel and the feeding gallery under the log were not dug");
                return;
            }
            if (clock.phase(colony) != TestClock.Phase.DAY) {
                night[0] = true; // (the foragers outside may eat it from above by night: only the days count)
                return;
            }
            if (night[0]) {
                night[0] = false;
                eatenAtDawn[0] = eaten;
            }
            // By day: eaten from below, and on the step the gallery's open side is a wall of mud crust.
            int crusts = 0;
            for (BlockPos cell : tunnel.gallery()) {
                for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    crusts += com.antcolonies.block.MudCrustBlock.hasFace(level.getBlockState(cell.relative(side)), side.getOpposite()) ? 1 : 0;
                }
            }
            helper.assertTrue(!colony.isForagingTime(level), "foraging time");
            if (!numbersAte[0] && colony.field().foragersOut() > 0) {
                helper.assertTrue(log.stream().anyMatch(colony.field()::remembersFromInside),
                        "by day the numbers should only go to food they can eat from inside");
                numbersAte[0] = true;
            }
            BlockState under = level.getBlockState(log.get(0));
            BlockState top = level.getBlockState(stump);
            if (kind == Food.STUMP) {
                // A cast joined to the gallery, and the stump on it eaten from it by day: a cast too.
                if (com.antcolonies.block.MudCastBlock.isCast(under) && com.antcolonies.block.MudCastBlock.isCast(top)) {
                    helper.assertTrue(under.getValue(com.antcolonies.block.TermiteTunnelBlock.OPEN.get(net.minecraft.core.Direction.DOWN))
                            && under.getValue(com.antcolonies.block.TermiteTunnelBlock.OPEN.get(net.minecraft.core.Direction.UP)),
                            "the cast of the log does not join the gallery and the cast of the stump: " + under);
                    helper.succeed();
                }
            } else if (kind == Food.GIANT) {
                // A block of the giant's underside eaten away by day is a mud cast, the rest of the log over it.
                if (log.stream().anyMatch(b -> com.antcolonies.block.MudCastBlock.isCast(level.getBlockState(b)))) {
                    helper.succeed();
                }
            } else if (kind == Food.BRANCH) {
                // Cased in mud (a plate over each block of it), held up over the gallery, eaten from below.
                int cased = 0;
                for (BlockPos wood : log) {
                    cased += com.antcolonies.block.MudCrustBlock.hasFace(level.getBlockState(wood.above()), net.minecraft.core.Direction.DOWN) ? 1 : 0;
                }
                if (eaten > eatenAtDawn[0] && cased > 0) {
                    helper.succeed();
                }
            } else if (eaten > eatenAtDawn[0] && (!onAStep || crusts > 0) && numbersAte[0]) {
                helper.succeed();
            }
            helper.assertTrue(helper.getTick() < 89000, "by day: eaten " + eaten + " (at dawn " + eatenAtDawn[0] + "), numbers ate " + numbersAte[0]
                    + " (" + colony.field().describe() + ", from inside " + log.stream().filter(colony.field()::remembersFromInside).count() + "), " + crusts
                    + " crust walls beside the gallery, " + colony.walls().getLeaks(level).size() + " leaks, log " + under + ", stump " + top
                    + ": first log block seen " + colony.food().plants(level).contains(log.get(0)) + ", gallery under it "
                    + tunnel.gallery().contains(log.get(0).below()) + ", eaten from inside " + colony.food().isFeedableFromInside(level, log.get(0))
                    + ", cut " + colony.food().getPiecesCut(log.get(0)) + ", hole " + tunnel.tunnel().hole().toShortString() + ", gallery " + tunnel.gallery());
        });
    }
}
