package com.antcolonies.savanna.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.antcolonies.animal.Sex;
import com.antcolonies.gametest.TestClock;
import com.antcolonies.gametest.TestIsolation;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.BuffaloEntity;
import com.antcolonies.savanna.animal.BuffaloGoals;
import com.antcolonies.savanna.animal.LionEntity;
import com.antcolonies.savanna.animal.LionGoals;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.worldgen.BuffaloHerds;
import com.antcolonies.savanna.worldgen.LionPrides;
import com.antcolonies.savanna.worldgen.OxbowFeature;
import com.antcolonies.savanna.worldgen.PanFeature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The lion pride (2026-11-25, {@code docs/plans/lion.md}, step 1's tests): prides laid with the pans, the day (lying in a
 * row at the den, the cubs playing, waking and greeting, drinking, the roar chorus and the dawn's after a bed), the temper
 * (a lying pride's stare, the warning, the bluff that never hurts a player standing still, the strike for coming in, the
 * chase reflex, the pride's answer to a hurt), the paces measured, water, death, a summoned lion joining, the coat kept
 * through a save, and the buffalo looking up at a passing lion. Each test lays its own flat grass field (stone at relative y
 * 0, grass at y 1; a lion stands at y 2) with sky access (as the buffalo's: the barrier roof hides the ground otherwise).
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class LionTests {
    static final int GROUND = 1, FEET = 2;

    // --- the kit -----------------------------------------------------------------------------------------------------------

    private static void field(GameTestHelper helper, int x0, int z0, int x1, int z1) {
        ServerLevel level = helper.getLevel();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(helper.absolutePos(new BlockPos(x, GROUND, z)), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
        }
    }

    private static int pan(GameTestHelper helper, int x, int z, int across) {
        ServerLevel level = helper.getLevel();
        double r = across / 2.0;
        int n = 0;
        for (int dx = -across; dx <= across; dx++) {
            for (int dz = -across; dz <= across; dz++) {
                if (dx * dx + dz * dz <= r * r) {
                    level.setBlock(helper.absolutePos(new BlockPos(x + dx, GROUND, z + dz)), Blocks.WATER.defaultBlockState(), 2);
                    n++;
                }
            }
        }
        return n;
    }

    /** A lion of this type at x, z (relative), facing that way, of the pride with this den (absolute) and water (or null). */
    private static LionEntity lion(GameTestHelper helper, EntityType<LionEntity> type, double x, double z, float yaw, BlockPos den,
                                   float denYaw, BlockPos water, int across) {
        LionEntity l = helper.spawn(type, new Vec3(x, FEET, z));
        Sex sex = type == SavannaEntities.LION.get() ? Sex.MALE : type == SavannaEntities.LIONESS.get() ? Sex.FEMALE : null;
        l.life().setUp(false, sex, RandomSource.create((long) (x * 31 + z)));
        l.life().apply();
        l.setDen(den, denYaw, water, across);
        l.setYRot(yaw);
        l.yBodyRot = yaw;
        l.yHeadRot = yaw;
        return l;
    }

    private static ServerPlayer player(GameTestHelper helper, double x, double z) {
        ServerPlayer p = MatabeleDefenceTests.survivalPlayer(helper);
        hold(helper, p, x, z);
        return p;
    }

    private static void hold(GameTestHelper helper, ServerPlayer p, double x, double z) {
        Vec3 at = helper.absoluteVec(new Vec3(x, FEET, z));
        p.moveTo(at.x, at.y, at.z, p.getYRot(), 0.0F);
        p.setDeltaMovement(Vec3.ZERO);
        p.invulnerableTime = 0;                         // a stand-in player never ticks: without this a second strike would not show
    }

    private static void remove(GameTestHelper helper, ServerPlayer p) {
        if (!p.isRemoved()) {
            helper.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    private static Vec3 rel(GameTestHelper helper, Vec3 abs) {
        return abs.subtract(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)));
    }

    private static String at(GameTestHelper helper, LionEntity l) {
        Vec3 r = rel(helper, l.position());
        return String.format("(%.1f, %.1f, %.1f)", r.x, r.y, r.z);
    }

    private static double flat(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A small pride at its den (relative dx, dz from x0, z0): the elder lioness, two more, a male and two cubs. */
    private static List<LionEntity> pride(GameTestHelper helper, double x0, double z0, BlockPos den, float denYaw, BlockPos water, int across) {
        List<LionEntity> pride = new ArrayList<>();
        LionEntity elder = lion(helper, SavannaEntities.LIONESS.get(), x0, z0, 0, den, denYaw, water, across);
        elder.setElder(true);
        pride.add(elder);
        pride.add(lion(helper, SavannaEntities.LIONESS.get(), x0 + 4, z0, 0, den, denYaw, water, across));
        pride.add(lion(helper, SavannaEntities.LIONESS.get(), x0 - 4, z0, 0, den, denYaw, water, across));
        pride.add(lion(helper, SavannaEntities.LION.get(), x0, z0 + 5, 0, den, denYaw, water, across));
        pride.add(lion(helper, SavannaEntities.LION_CUB.get(), x0 + 2, z0 - 4, 0, den, denYaw, water, across));
        pride.add(lion(helper, SavannaEntities.LION_CUB.get(), x0 - 2, z0 - 4, 0, den, denYaw, water, across));
        return pride;
    }

    /** A lion's body as lying, for overlaps: its length (nose to hips) and width (blocks, at size 1). */
    private static double[] body(LionEntity l) {
        return switch (l.form()) {
            case MALE -> new double[] {3.3, 1.3};
            case LIONESS -> new double[] {2.9, 0.9};
            case CUB -> new double[] {1.6, 0.6};
        };
    }

    // --- prides laid with the land -----------------------------------------------------------------------------------------

    /**
     * A pride is laid with a pan in new land (after the pan's buffalo herd): one or two males, two to four lionesses, at
     * most 9, exactly one elder (a lioness); each on grass with its body clear, 3 or more from every buffalo and from each
     * other, all sharing one den and the pan as their water, kept for good; no lion is in a biome's spawn list.
     */
    @GameTest(template = "arena", timeoutTicks = 300, skyAccess = true)
    public static void aPrideIsLaidWithItsPanAndKeptForGood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestClock.still(helper, 12.0);
        field(helper, 0, 0, 64, 64);
        int across = 9;
        int water = pan(helper, 32, 32, across);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos middle = helper.absolutePos(new BlockPos(32, GROUND, 32));
        PanFeature.Laid laid = new PanFeature.Laid(middle, across, false, 1, 2, water, 100);
        int[] bounds = {origin.getX(), origin.getZ(), origin.getX() + 64, origin.getZ() + 64};
        BuffaloHerds.Herd herd = BuffaloHerds.lay(level, RandomSource.create(7), laid, bounds, OxbowFeature.Keepout.NONE);
        LionPrides.Pride pride = LionPrides.layPride(level, RandomSource.create(11), laid, bounds, OxbowFeature.Keepout.NONE, herd.at());
        List<LionEntity> found = TestIsolation.insectsOf(helper, LionEntity.class);
        found.forEach(l -> l.led = true);
        long males = found.stream().filter(l -> l.form() == LionEntity.Form.MALE).count();
        long lionesses = found.stream().filter(l -> l.form() == LionEntity.Form.LIONESS).count();
        helper.assertTrue(pride.lions() == found.size() && found.size() <= 9 && males >= 1 && males <= 2 && lionesses >= 2 && lionesses <= 4,
                "a pride of " + found.size() + " (" + males + " males, " + lionesses + " lionesses; " + pride.spots() + " spots)");
        helper.assertTrue(found.stream().filter(LionEntity::isElder).count() == 1
                && found.stream().filter(LionEntity::isElder).allMatch(l -> l.form() == LionEntity.Form.LIONESS), "a pride has one elder, a lioness");
        for (LionEntity l : found) {
            helper.assertTrue(pride.den().equals(l.den()) && middle.equals(l.water()) && l.waterAcross() == across, "a lion's den " + l.den()
                    + " or water " + l.water() + " is not its pride's");
            BlockPos under = BlockPos.containing(l.getX(), l.getY() - 0.5, l.getZ());
            helper.assertTrue(level.getBlockState(under).is(Blocks.GRASS_BLOCK) && !l.isInWater(), "a lion was laid off dry grass at " + at(helper, l));
            helper.assertTrue(level.noCollision(l, l.getBoundingBox().deflate(0.01)), "a lion was laid inside a block at " + at(helper, l));
            helper.assertFalse(l.removeWhenFarAway(1.0E9), "a pride lion would despawn far from a player");
            for (BlockPos b : herd.at()) {
                helper.assertTrue(flat(l.position(), Vec3.atBottomCenterOf(b)) >= 2.9, "a lion was laid on a buffalo at " + at(helper, l));
            }
            for (LionEntity o : found) {
                helper.assertTrue(o == l || flat(o.position(), l.position()) >= 2.9, "two lions were laid on each other at " + at(helper, l));
            }
        }
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        biomes.entrySet().forEach(e -> {
            for (MobCategory group : MobCategory.values()) {
                helper.assertTrue(e.getValue().getMobSettings().getMobs(group).unwrap().stream().noneMatch(d -> d.type == SavannaEntities.LION.get()
                        || d.type == SavannaEntities.LIONESS.get() || d.type == SavannaEntities.LION_CUB.get()),
                        "a lion is in " + e.getKey().location() + "'s " + group.getName() + " spawn list");
            }
        });
        helper.runAtTickTime(200, () -> {
            for (LionEntity l : found) {
                helper.assertTrue(l.isAlive() && l.getHealth() >= l.getMaxHealth(), "a laid lion was hurt standing: health " + l.getHealth());
            }
            helper.succeed();
        });
    }

    // --- its day -----------------------------------------------------------------------------------------------------------

    /**
     * At midday the pride lies at its den in a row, all one way: no two bodies (as lying, nose to hips) overlap; then the
     * cubs get up to play now and then.
     */
    @GameTest(template = "arena", timeoutTicks = 2400, skyAccess = true)
    public static void atMiddayThePrideLiesInARowAndTheCubsPlay(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        field(helper, 0, 0, 64, 64);
        BlockPos den = helper.absolutePos(new BlockPos(32, GROUND, 40));
        List<LionEntity> pride = pride(helper, 32.5, 24.5, den, 0.0F, null, 0);
        boolean[] lay = {false}, played = {false};
        helper.onEachTick(() -> {
            if (!lay[0] && pride.stream().allMatch(l -> l.doing() == LionEntity.REST)) {
                lay[0] = true;
                for (LionEntity a : pride) {
                    for (LionEntity b : pride) {
                        if (a == b) {
                            continue;
                        }
                        // all lie one way (the den's): side apart or along apart
                        float yaw = a.denYaw() * Mth.DEG_TO_RAD;
                        double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
                        double along = Math.abs(-Mth.sin(yaw) * dx + Mth.cos(yaw) * dz), side = Math.abs(Mth.cos(yaw) * dx + Mth.sin(yaw) * dz);
                        double[] ba = body(a), bb = body(b);
                        helper.assertTrue(side >= (ba[1] + bb[1]) / 2.0 - 0.05 || along >= (ba[0] + bb[0]) / 2.0 - 0.05,
                                "two lions lie inside each other: " + a.form() + " at " + at(helper, a) + ", " + b.form() + " at " + at(helper, b));
                    }
                    helper.assertTrue(flat(a.position(), Vec3.atBottomCenterOf(den)) < 10.0, "a lion lies away from the den at " + at(helper, a));
                    helper.assertTrue(Math.abs(Mth.wrapDegrees(a.getYRot() - a.denYaw())) < 10.0F,
                            "a lion lies crossways: yaw " + a.getYRot() + ", the den's " + a.denYaw() + "; " + a.about());
                }
            }
            for (LionEntity l : pride) {
                byte d = l.doing();
                played[0] |= l.isCub() && (d == LionEntity.PLAY_POUNCE || d == LionEntity.PLAY_WRESTLE || d == LionEntity.NUZZLE);
            }
            if (lay[0] && played[0]) {
                helper.succeed();
            }
        });
        helper.runAtTickTime(2390, () -> helper.fail("all lying " + lay[0] + ", a cub played " + played[0] + "; the elder: " + pride.get(0).about()
                + " at " + at(helper, pride.get(0)) + "; a cub: " + pride.get(4).about()));
    }

    /**
     * At dusk the pride wakes and greets (two standing lions playing their greeting at once), sits; at 17:30 walks to its
     * pan and drinks; at 18:30 every member roars within 8 s of the first (its own 1 to 5 s delay, and a lion lying or sitting gets up first; the face layer off while it roars, so the jaw
     * opens); and once all have drunk the night begins.
     */
    @GameTest(template = "arena", timeoutTicks = 4000, skyAccess = true)
    public static void atDuskThePrideWakesGreetsDrinksAndRoars(GameTestHelper helper) {
        TestClock clock = TestClock.still(helper, 16.0);
        field(helper, 0, 0, 64, 64);
        int across = 7;
        pan(helper, 32, 50, across);
        BlockPos water = helper.absolutePos(new BlockPos(32, GROUND, 50));
        BlockPos den = helper.absolutePos(new BlockPos(32, GROUND, 16));
        List<LionEntity> pride = pride(helper, 32.5, 20.5, den, 180.0F, water, across);
        boolean[] greet = {false}, sat = {false}, drank = new boolean[pride.size()];
        Map<LionEntity, Long> roared = new HashMap<>();
        int[] stage = {0};
        helper.onEachTick(() -> {
            int greeting = 0;
            for (int i = 0; i < pride.size(); i++) {
                LionEntity l = pride.get(i);
                if (l.doing() == LionEntity.GREET) {
                    greeting++;
                }
                drank[i] |= l.doing() == LionEntity.DRINK;
                if (l.doing() == LionEntity.ROAR && !roared.containsKey(l)) {
                    roared.put(l, (long) helper.getTick());
                }
            }
            greet[0] |= greeting >= 2;
            if (stage[0] == 0 && greet[0] && pride.stream().allMatch(l -> l.doing() == LionEntity.SIT)) {
                sat[0] = true;
                stage[0] = 1;
                clock.skipTo(17.5);                         // to the water
            } else if (stage[0] == 1 && allTrue(drank, pride, true)) {
                stage[0] = 2;
                clock.skipTo(18.5);                         // the dusk chorus
            } else if (stage[0] == 2 && roared.size() == pride.size()) {
                long first = roared.values().stream().mapToLong(Long::longValue).min().orElse(0);
                long last = roared.values().stream().mapToLong(Long::longValue).max().orElse(0);
                helper.assertTrue(last - first <= 160, "the chorus took " + (last - first) + " ticks from the first roar to the last");
                helper.succeed();
            }
        });
        helper.runAtTickTime(3990, () -> helper.fail("stage " + stage[0] + ": a greeting seen " + greet[0] + ", all sat " + sat[0] + ", drank "
                + java.util.Arrays.toString(drank) + ", roared " + roared.size() + " of " + pride.size() + "; the elder: " + pride.get(0).about()
                + " at " + at(helper, pride.get(0)) + "; the male: " + pride.get(3).about() + " at " + at(helper, pride.get(3))));
    }

    private static boolean allTrue(boolean[] seen, List<LionEntity> pride, boolean adultsOnly) {
        for (int i = 0; i < seen.length; i++) {
            if (!seen[i] && (!adultsOnly || pride.get(i).isAdult())) {
                return false;
            }
        }
        return true;
    }

    /** Waking from a bed (the clock jumped from 4:00 to 6:00): the pride roars the dawn chorus once, each member once. */
    @GameTest(template = "arena", timeoutTicks = 800, skyAccess = true)
    public static void afterABedThePrideRoarsTheDawnChorusOnce(GameTestHelper helper) {
        TestClock clock = TestClock.still(helper, 4.0);
        field(helper, 0, 0, 64, 64);
        BlockPos den = helper.absolutePos(new BlockPos(32, GROUND, 32));
        List<LionEntity> pride = pride(helper, 32.5, 28.5, den, 0.0F, null, 0);
        Map<LionEntity, Integer> roars = new HashMap<>();
        byte[] was = new byte[pride.size()];
        helper.runAtTickTime(100, () -> clock.skipTo(6.0));
        helper.onEachTick(() -> {
            for (int i = 0; i < pride.size(); i++) {
                byte d = pride.get(i).doing();
                if (d == LionEntity.ROAR && was[i] != LionEntity.ROAR) {
                    helper.assertTrue(helper.getTick() > 100, "a lion roared before the dawn: " + pride.get(i).about());
                    roars.merge(pride.get(i), 1, Integer::sum);
                }
                was[i] = d;
            }
        });
        helper.runAtTickTime(700, () -> {
            for (LionEntity l : pride) {
                helper.assertTrue(roars.getOrDefault(l, 0) == 1, "a " + l.form() + " roared " + roars.getOrDefault(l, 0) + " times at dawn: " + l.about());
            }
            helper.succeed();
        });
    }

    // --- its temper --------------------------------------------------------------------------------------------------------

    /** A survival player standing 16 off a lying pride: the lions stay down, staring (their mood alert), none gets up to warn. */
    @GameTest(template = "arena", timeoutTicks = 1600, skyAccess = true)
    public static void aLyingPrideStaresAtAPlayerAndStaysDown(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        field(helper, 0, 0, 64, 64);
        BlockPos den = helper.absolutePos(new BlockPos(20, GROUND, 32));
        List<LionEntity> pride = pride(helper, 20.5, 28.5, den, 90.0F, null, 0);
        ServerPlayer[] watcher = {null};
        double[] wx = {0};
        boolean[] stared = new boolean[pride.size()];
        helper.onEachTick(() -> {
            if (watcher[0] == null) {
                if (pride.stream().allMatch(l -> l.doing() == LionEntity.REST)) {
                    wx[0] = pride.stream().mapToDouble(l -> rel(helper, l.position()).x).max().orElse(20.5) + 16.0;
                    watcher[0] = player(helper, wx[0], 32.5);                // 16 from the nearest
                }
                return;
            }
            hold(helper, watcher[0], wx[0], 32.5);
            for (int i = 0; i < pride.size(); i++) {
                LionEntity l = pride.get(i);
                helper.assertTrue(l.doing() == LionEntity.REST || l.doing() == LionEntity.GROOM || l.isCub(), "a lying lion got up for a player 16 off: " + l.about());
                helper.assertTrue(l.temper() == LionGoals.Temper.NONE || l.isCub(), "a lying lion's temper went to " + l.temper());
                stared[i] |= l.mood() == LionEntity.ALERT || l.isCub();
            }
            if (helper.getTick() > 400 + 200 && allTrue(stared, pride, false)) {
                remove(helper, watcher[0]);
                helper.succeed();
            }
        });
        helper.runAtTickTime(1590, () -> {
            if (watcher[0] != null) {
                remove(helper, watcher[0]);
            }
            helper.fail("stared " + java.util.Arrays.toString(stared) + "; the elder: " + pride.get(0).about());
        });
    }

    /**
     * A player walking up to a lioness (no cubs in her pride): she warns at 10, bluffs at 6 and the bluff never hurts the
     * player standing still; the player then steps in to her head: exactly one strike lands, and she calms down.
     */
    @GameTest(template = "arena", timeoutTicks = 1600, skyAccess = true)
    public static void aWalkingPlayerIsWarnedBluffedThenStruckForSteppingIn(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        field(helper, 0, 20, 64, 44);
        BlockPos den = helper.absolutePos(new BlockPos(6, GROUND, 32));
        LionEntity she = lion(helper, SavannaEntities.LIONESS.get(), 20.5, 32.5, -90.0F, den, -90.0F, null, 0);
        she.led = true;
        ServerPlayer p = player(helper, 40.5, 32.5);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        p.setHealth(100.0F);
        double[] x = {40.5, 20.5 + 5.6};
        boolean[] warned = {false}, bluffed = {false};
        int[] stage = {0}, hits = {0};
        float[] health = {p.getHealth()};
        helper.onEachTick(() -> {
            x[0] = Math.max(x[1], x[0] - 0.2);
            hold(helper, p, x[0], 32.5);
            LionGoals.Temper t = she.temper();
            warned[0] |= t == LionGoals.Temper.WARN;
            if (t == LionGoals.Temper.BLUFF) {
                helper.assertTrue(warned[0], "bluffed without a warning: " + she.about());
                bluffed[0] = true;
            }
            if (p.getHealth() < health[0]) {
                hits[0]++;
                health[0] = p.getHealth();
                helper.assertTrue(stage[0] == 1, "the player was hurt standing still (stage " + stage[0] + "): " + she.about());
            }
            if (stage[0] == 0 && bluffed[0] && t == LionGoals.Temper.NONE) {
                stage[0] = 1;                               // the bluff and her calm played out: step in to her head
                Vec3 r = rel(helper, she.position());
                x[1] = r.x + 2.1;
            }
            if (stage[0] == 1 && hits[0] >= 1 && (t == LionGoals.Temper.CALM || t == LionGoals.Temper.NONE)) {
                stage[0] = 2;
            }
            if (stage[0] == 2 && helper.getTick() % 20 == 0 && t == LionGoals.Temper.NONE) {
                helper.assertTrue(hits[0] == 1, "she struck " + hits[0] + " times for one step in");
                remove(helper, p);
                helper.succeed();
            }
        });
        helper.runAtTickTime(1590, () -> {
            remove(helper, p);
            helper.fail("stage " + stage[0] + ": warned " + warned[0] + ", bluffed " + bluffed[0] + ", hits " + hits[0] + "; " + she.about());
        });
    }

    /** The chase reflex: a player warned from 6 who sprints away (5.6 a second) is caught and struck once. */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true)
    public static void aPlayerRunningFromSixIsCaughtOnce(GameTestHelper helper) {
        runAway(helper, 6.5, true);
    }

    /** The chase reflex gives up after 3 s: a player warned at 9.5 who sprints away gets away unhurt. */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true)
    public static void aPlayerRunningFromNineAndAHalfGetsAway(GameTestHelper helper) {
        runAway(helper, 9.5, false);
    }

    private static void runAway(GameTestHelper helper, double from, boolean caught) {
        TestClock.still(helper, 12.0);
        field(helper, 0, 26, 64, 38);
        BlockPos den = helper.absolutePos(new BlockPos(2, GROUND, 32));
        LionEntity she = lion(helper, SavannaEntities.LIONESS.get(), 4.5, 32.5, -90.0F, den, -90.0F, null, 0);
        she.led = true;
        double[] x = {4.5 + from};
        ServerPlayer p = player(helper, x[0], 32.5);
        float health = p.getHealth();
        int[] hits = {0}, ranAt = {-1};
        float[] last = {health};
        helper.onEachTick(() -> {
            if (ranAt[0] < 0 && she.temper() == LionGoals.Temper.WARN && she.temperOn() == p) {
                ranAt[0] = (int) helper.getTick();
            }
            if (ranAt[0] >= 0 && x[0] < 62.0) {
                x[0] += 0.28;                               // sprinting away: 5.6 blocks a second
            }
            hold(helper, p, x[0], 32.5);
            if (p.getHealth() < last[0]) {
                hits[0]++;
                last[0] = p.getHealth();
            }
            if (ranAt[0] >= 0 && helper.getTick() > ranAt[0] + 200) {
                remove(helper, p);
                helper.assertTrue(caught ? hits[0] == 1 : hits[0] == 0, "a player running from " + from + " was struck " + hits[0]
                        + " times; " + she.about());
                helper.succeed();
            }
        });
        helper.runAtTickTime(590, () -> {
            remove(helper, p);
            helper.fail("never warned (ran at " + ranAt[0] + "), hits " + hits[0] + "; " + she.about());
        });
    }

    /**
     * A lioness shot by a player 20 blocks off fights the archer; the male joins (one mate); the other lionesses ring her
     * facing the archer and never strike; the cub hides by a lioness. Never more than two attack.
     */
    @GameTest(template = "arena", timeoutTicks = 800, skyAccess = true)
    public static void aLionessShotFightsTheMaleJoinsTheRestRingAndTheCubHides(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestClock.still(helper, 12.0);
        field(helper, 0, 16, 64, 48);
        BlockPos den = helper.absolutePos(new BlockPos(6, GROUND, 32));
        List<LionEntity> pride = pride(helper, 16.5, 32.5, den, -90.0F, null, 0);
        pride.forEach(l -> l.led = true);
        LionEntity shot = pride.get(1), male = pride.get(3);
        ServerPlayer archer = player(helper, 40.5, 32.5);
        archer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        archer.setHealth(1000.0F);
        boolean[] shotFights = {false}, maleJoins = {false}, ringed = {false}, hid = {false};
        helper.runAtTickTime(3, () -> {
            Arrow arrow = new Arrow(EntityType.ARROW, level);
            arrow.setOwner(archer);
            shot.hurt(level.damageSources().arrow(arrow, archer), 1.0F);
        });
        helper.onEachTick(() -> {
            hold(helper, archer, 40.5, 32.5);
            int attacking = 0;
            for (LionEntity l : pride) {
                if (l.temper() == LionGoals.Temper.ATTACK) {
                    attacking++;
                    helper.assertTrue(l == shot || l == male, "a lion that was neither hurt nor the second fighter attacked: " + l.about());
                }
                ringed[0] |= l.temper() == LionGoals.Temper.RING && l.form() == LionEntity.Form.LIONESS;
                hid[0] |= l.temper() == LionGoals.Temper.HIDE && l.isCub();
            }
            helper.assertTrue(attacking <= 2, attacking + " lions attack at once");
            shotFights[0] |= shot.temper() == LionGoals.Temper.ATTACK && shot.temperOn() == archer;
            maleJoins[0] |= male.temper() == LionGoals.Temper.ATTACK && male.temperOn() == archer;
            if (shotFights[0] && maleJoins[0] && ringed[0] && hid[0]) {
                remove(helper, archer);
                helper.succeed();
            }
        });
        helper.runAtTickTime(790, () -> {
            remove(helper, archer);
            helper.fail("the shot one fought " + shotFights[0] + ", the male joined " + maleJoins[0] + ", a lioness ringed " + ringed[0]
                    + ", a cub hid " + hid[0] + "; the male: " + male.about());
        });
    }

    // --- its paces (measured, never worked out: BUGS 246) -----------------------------------------------------------------

    /** Its gallop runs at about 7.5 blocks a second (6.5 to 8.5), measured over 12 blocks of a run at a player 30 off who hurt it. */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true)
    public static void aLionGallopsAtAboutSevenAndAHalfBlocksASecond(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        field(helper, 0, 26, 64, 38);
        BlockPos den = helper.absolutePos(new BlockPos(2, GROUND, 32));
        LionEntity he = lion(helper, SavannaEntities.LION.get(), 4.5, 32.5, -90.0F, den, -90.0F, null, 0);
        he.led = true;
        ServerPlayer target = player(helper, 30.5, 32.5);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        target.setHealth(1000.0F);
        long[] from = {-1}, to = {-1};
        double startX = helper.absoluteVec(new Vec3(4.5, 0, 0)).x;
        helper.runAtTickTime(5, () -> he.hurt(helper.getLevel().damageSources().playerAttack(target), 1.0F));
        helper.onEachTick(() -> {
            hold(helper, target, 30.5, 32.5);
            double ran = he.getX() - startX;
            if (from[0] < 0 && ran >= 4.0) {
                from[0] = helper.getTick();
            }
            if (to[0] < 0 && ran >= 16.0) {
                to[0] = helper.getTick();
                double pace = 12.0 / ((to[0] - from[0]) / 20.0);
                SavannaMod.LOGGER.info("[lion] gallop pace {} blocks a second (GALLOP {})", String.format("%.2f", pace), LionEntity.GALLOP);
                remove(helper, target);
                helper.assertTrue(pace >= 6.5 && pace <= 8.5, "the gallop ran at " + String.format("%.2f", pace)
                        + " blocks a second, not 6.5 to 8.5 (tune LionEntity.GALLOP, now " + LionEntity.GALLOP + ")");
                helper.succeed();
            }
        });
        helper.runAtTickTime(590, () -> {
            remove(helper, target);
            helper.fail("no gallop measured: ran " + String.format("%.1f", he.getX() - startX) + "; " + he.about());
        });
    }

    /** Its walk to the den runs at about 1.2 blocks a second (1.0 to 1.45), measured over 9 blocks of a 13.5-block trip. */
    @GameTest(template = "arena", timeoutTicks = 900, skyAccess = true)
    public static void aLionWalksAtAboutOneAndAFifthBlocksASecond(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        field(helper, 0, 26, 64, 38);
        BlockPos den = helper.absolutePos(new BlockPos(20, GROUND, 32));
        LionEntity she = lion(helper, SavannaEntities.LIONESS.get(), 6.5, 32.5, -90.0F, den, -90.0F, null, 0);
        she.setElder(true);
        long[] from = {-1}, to = {-1};
        double startX = she.getX();
        helper.onEachTick(() -> {
            double ran = she.getX() - startX;
            if (from[0] < 0 && ran >= 2.0) {
                from[0] = helper.getTick();
            }
            if (to[0] < 0 && ran >= 11.0) {
                to[0] = helper.getTick();
                double pace = 9.0 / ((to[0] - from[0]) / 20.0);
                SavannaMod.LOGGER.info("[lion] walk pace {} blocks a second (WALK {})", String.format("%.2f", pace), LionEntity.WALK);
                helper.assertTrue(pace >= 1.0 && pace <= 1.45, "the walk ran at " + String.format("%.2f", pace)
                        + " blocks a second, not 1.0 to 1.45 (tune LionEntity.WALK, now " + LionEntity.WALK + ")");
                helper.succeed();
            }
        });
        helper.runAtTickTime(890, () -> helper.fail("no walk measured: ran " + String.format("%.1f", she.getX() - startX) + "; " + she.about()));
    }

    // --- water, death, joining, saving -----------------------------------------------------------------------------------

    /** A lion knocked into deep water floats and swims out onto the grass, unhurt. */
    @GameTest(template = "arena", timeoutTicks = 1200, skyAccess = true)
    public static void aLionInDeepWaterSwimsOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestClock.still(helper, 12.0);
        for (int x = 0; x <= 24; x++) {
            for (int z = 0; z <= 24; z++) {
                boolean pool = x >= 6 && x <= 14 && z >= 6 && z <= 14;
                for (int y = 0; y <= 3; y++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), y == 0 ? Blocks.STONE.defaultBlockState()
                            : pool ? Blocks.WATER.defaultBlockState() : y == 3 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2);
                }
            }
        }
        LionEntity l = helper.spawn(SavannaEntities.LIONESS.get(), new Vec3(10.5, 2.0, 10.5));
        l.led = true;
        l.setDen(helper.absolutePos(new BlockPos(20, 3, 20)), 0, null, 0);
        helper.succeedWhen(() -> {
            double bank = helper.absoluteVec(new Vec3(0, 4, 0)).y;      // the grass's top
            helper.assertTrue(l.isAlive() && !l.isInWater() && l.onGround() && l.getY() >= bank - 0.01,
                    "the lion has not climbed out onto the bank: " + l.about() + " at " + at(helper, l));
        });
    }

    /** Its death clip (1.2 s) plays out before its body goes. */
    @GameTest(template = "arena", timeoutTicks = 100, skyAccess = true)
    public static void aLionsDeathClipPlaysOutBeforeItsBodyGoes(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        field(helper, 0, 0, 12, 12);
        LionEntity l = lion(helper, SavannaEntities.LION.get(), 6.5, 6.5, 0.0F, helper.absolutePos(new BlockPos(6, GROUND, 6)), 0, null, 0);
        l.led = true;
        helper.runAtTickTime(5, () -> l.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE));
        helper.runAtTickTime(20, () -> helper.assertTrue(l.isDeadOrDying() && !l.isRemoved(), "the dead lion's body went before its death clip played out"));
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(l.isRemoved(), "the dead lion's body is still there 2 s after it died");
            helper.succeed();
        });
    }

    /**
     * A lion from a spawn egg or a summon joins the pride of the nearest lion within 16 (its den and water); with none near,
     * its den is where it stands. Its coat and the elder survive a save and a load.
     */
    @GameTest(template = "arena", timeoutTicks = 40, skyAccess = true)
    public static void aSummonedLionJoinsAPrideAndItsCoatSurvivesASave(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        field(helper, 0, 0, 64, 20);
        BlockPos den = helper.absolutePos(new BlockPos(2, GROUND, 10));
        BlockPos water = helper.absolutePos(new BlockPos(2, GROUND, 4));
        LionEntity member = lion(helper, SavannaEntities.LIONESS.get(), 6.5, 10.5, 0.0F, den, 45.0F, water, 7);
        member.setElder(true);
        LionEntity near = helper.spawn(SavannaEntities.LION.get(), new Vec3(16.5, FEET, 10.5));
        LionEntity far = helper.spawn(SavannaEntities.LION_CUB.get(), new Vec3(46.5, FEET, 10.5));
        List.of(member, near, far).forEach(l -> l.led = true);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(den.equals(near.den()) && water.equals(near.water()) && near.waterAcross() == 7 && near.denYaw() == 45.0F,
                    "a summoned lion 10 blocks from a pride did not join it: den " + near.den());
            helper.assertTrue(far.den() != null && !den.equals(far.den()) && far.water() == null, "a summoned cub 40 blocks off took den " + far.den());
            CompoundTag tag = new CompoundTag();
            member.saveWithoutId(tag);
            LionEntity copy = SavannaEntities.LIONESS.get().create(helper.getLevel());
            copy.load(tag);
            helper.assertTrue(copy.isElder() && copy.coat() == LionEntity.COAT_THIRD && den.equals(copy.den()) && water.equals(copy.water())
                    && copy.denYaw() == 45.0F, "the elder's coat, flag or den did not survive a save: elder " + copy.isElder() + ", coat " + copy.coat());
            helper.succeed();
        });
    }

    /** A buffalo grazing stops and faces a lion walking past 10 blocks off (and never warns or charges it). */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true)
    public static void aBuffaloStopsAndFacesALionWalkingPast(GameTestHelper helper) {
        TestClock.still(helper, 7.0);
        field(helper, 0, 10, 64, 54);
        BlockPos home = helper.absolutePos(new BlockPos(32, GROUND, 20));
        BuffaloEntity b = helper.spawn(SavannaEntities.BUFFALO.get(), new Vec3(32.5, FEET, 20.5));
        b.setHome(home, 0);
        b.led = true;
        LionEntity l = lion(helper, SavannaEntities.LIONESS.get(), 8.5, 30.5, -90.0F, helper.absolutePos(new BlockPos(56, GROUND, 30)), 0, null, 0);
        l.led = true;
        Vec3 goal = helper.absoluteVec(new Vec3(56.5, FEET, 30.5));
        helper.runAtTickTime(2, () -> l.getNavigation().moveTo(goal.x, goal.y, goal.z, LionEntity.WALK));
        helper.onEachTick(() -> {
            helper.assertFalse(b.temper() == BuffaloGoals.Temper.WARN || b.temper() == BuffaloGoals.Temper.CHARGE, "a buffalo warned or charged a lion");
            if (b.temper() == BuffaloGoals.Temper.WATCH && b.temperOn() == l && b.doing() == BuffaloEntity.MOOD_ALERT) {
                helper.succeed();
            }
        });
        helper.runAtTickTime(590, () -> helper.fail("the buffalo never watched the lion: " + b.about()));
    }
}
