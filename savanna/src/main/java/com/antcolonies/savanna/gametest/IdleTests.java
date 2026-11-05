package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.gametest.IdleWatch;
import com.antcolonies.gametest.TestClock;
import com.antcolonies.gametest.TestIsolation;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.NewLandMounds;
import com.antcolonies.savanna.colony.PreyPile;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.command.MatabeleCommands;
import com.antcolonies.savanna.entity.MatabeleEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M8 step 3Q, still when idle: in a calm nest, insects with nothing to do stand (antennae moving, cleaning, napping,
 * grooming a nestmate) far more than they walk, as real ants and termites do (Charbonneau and Dornhaus 2015: idle
 * workers are mostly motionless). Each scene counts, per kind, the share of idle time spent standing
 * ({@link IdleWatch}) and asks for at least {@link #STILL} of it.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class IdleTests {
    /** The target: idle insects still at least 3 quarters of the time (the plan, Q1 and Q6). */
    static final double STILL = 0.75;
    /** Ticks let pass before counting (the colony settles in), then ticks counted. */
    private static final int SETTLE = 600;
    private static final int COUNT = 2400;

    /** A Matabele colony of 20 in a dead mound at night, its prey pile stocked: nothing to do but rest. */
    @GameTest(template = "arena", timeoutTicks = 6000, skyAccess = true, batch = "idle")
    public static void aCalmMatabeleNestStandsStillMostOfTheTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestClock.still(helper, 22);
        TestScenes.deepGround(helper);
        Colony termites = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(termites != null, "no dead mound put in");
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(MIDDLE + 9, SURFACE, MIDDLE)), 20);
        helper.assertTrue(colony != null, "no Matabele colony made");
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElse(null);
        helper.assertTrue(pile != null, "a Matabele colony has no prey pile");
        IdleWatch watch = new IdleWatch("calm Matabele nest");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(colony.nest().getFinishedRoomCenter(level) != null, "the queen's room is not open yet"))
                .thenExecute(() -> pile.stock(level, 6))
                .thenIdle(SETTLE)
                .thenExecute(() -> count(helper, level, colony, watch))
                .thenIdle(COUNT)
                .thenExecute(() -> {
                    check(helper, watch);
                    // Q2: the idle ones settled by the queen and the prey pile (real Megaponera crowd the brood chamber).
                    BlockPos room = colony.nest().getFinishedRoomCenter(level);
                    List<MatabeleEntity> idle = TestIsolation.insectsOf(level, colony, MatabeleEntity.class).stream()
                            .filter(AbstractAntEntity::isIdle).toList();
                    long near = idle.stream().filter(ant -> ant.blockPosition().distSqr(room) <= 6 * 6).count();
                    helper.assertTrue(!idle.isEmpty() && near * 2 >= idle.size(),
                            "only " + near + " of " + idle.size() + " idle Matabele settled by the queen's room at " + room.toShortString());
                })
                .thenSucceed();
    }

    /** A full new-land termite colony by day (the termites stay in): its workers, soldiers and royals at rest. */
    @GameTest(template = "arena", timeoutTicks = 6000, skyAccess = true, batch = "idle")
    public static void aCalmTermiteNestStandsStillMostOfTheTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestClock.still(helper, 12);
        TestScenes.deepGround(helper);
        TestScenes.shapeGround(helper, 65, (x, z) -> 0);
        net.minecraft.world.phys.AABB b = helper.getBounds();
        NewLandMounds.fenceForTests(new net.minecraft.world.level.levelgen.structure.BoundingBox((int) b.minX, (int) b.minY, (int) b.minZ,
                (int) b.maxX - 1, (int) b.maxY - 1, (int) b.maxZ - 1));
        Colony colony = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(32, SURFACE, 32)), NewLandMounds.pickFor("full", 0, 1234L));
        NewLandMounds.fenceForTests(null);
        helper.assertTrue(colony != null, "no colony put in");
        colony.population().setOnForTests(true); // (real mobs out of the numbers, as in a game; off on the test server)
        IdleWatch watch = new IdleWatch("calm termite nest");
        helper.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> count(helper, level, colony, watch))
                .thenIdle(COUNT)
                .thenExecute(() -> check(helper, watch))
                .thenSucceed();
    }

    /** Counts every tick from now (the colony's insects looked up once a second). */
    private static void count(GameTestHelper helper, ServerLevel level, Colony colony, IdleWatch watch) {
        List<AbstractAntEntity> insects = new ArrayList<>();
        int[] tick = {0};
        helper.onEachTick(() -> {
            if (tick[0]++ % 20 == 0) {
                insects.clear();
                insects.addAll(TestIsolation.insectsOf(level, colony, AbstractAntEntity.class));
            }
            watch.tick(insects);
        });
    }

    /** Logs the shares and asks for {@link #STILL} of every kind counted long enough (a few seconds of one insect's idling is noise). */
    private static void check(GameTestHelper helper, IdleWatch watch) {
        watch.log();
        helper.assertTrue(watch.waitingTicks(null) > 0, "nobody was idle: " + watch.report());
        for (String kind : watch.kinds()) {
            if (watch.waitingTicks(kind) >= 600) {
                helper.assertTrue(watch.stillShare(kind) >= STILL, "idle insects walk too much: " + watch.report());
            }
        }
    }
}
