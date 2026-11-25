package com.antcolonies.savanna.gametest;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.animal.Sex;
import com.antcolonies.gametest.TestClock;
import com.antcolonies.gametest.TestIsolation;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.BuffaloEntity;
import com.antcolonies.savanna.animal.BuffaloGoals;
import com.antcolonies.savanna.animal.CrocodileEntity;
import com.antcolonies.savanna.block.DryGrassBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.worldgen.BuffaloHerds;
import com.antcolonies.savanna.worldgen.OxbowFeature;
import com.antcolonies.savanna.worldgen.PanFeature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Cape buffalo (2026-11-25, {@code docs/plans/buffalo.md}, step 1's tests): its herds laid with the pans, its day
 * (grazing apart and cropping, drinking at midday, lying down at night), its temper (the stare, the warning and the charge,
 * the answer to a hurt), its paces measured (the charge and the travelling walk), floating, dying, a summoned one joining
 * a herd, and crocodiles leaving it be. Each test lays its own flat grass field (stone at relative y 0, grass at y 1; a
 * buffalo stands at y 2) and finds its buffalo by holding them, or (the herd laid by the land) through the kit.
 * <p>Every test has sky access: without it the arena's barrier roof (relative y 16) is the top of every column for the
 * height map, so the buffalo's ground reader ({@code GroundTop}) found the roof and no stand anywhere (the first run's
 * buffalo stood on their spawn blocks). Every test keeps its own still clock ({@link TestClock}), and every herd's
 * members are given one home, set before their first tick.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class BuffaloTests {
    /** The ground's top block (relative y), and where a buffalo's feet stand. */
    static final int GROUND = 1, FEET = 2;

    // --- the kit -----------------------------------------------------------------------------------------------------------

    /** A flat field from x0, z0 to x1, z1 (relative): stone under grass, open air over it. */
    private static void field(GameTestHelper helper, int x0, int z0, int x1, int z1) {
        ServerLevel level = helper.getLevel();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(helper.absolutePos(new BlockPos(x, GROUND, z)), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
        }
    }

    /** A round pan of water a block deep, its top level with the grass, this far across round x, z (relative); how many water blocks. */
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

    /** A grown buffalo (a bull or a cow) at x, z (relative), facing that way, its herd's home given (absolute). */
    private static BuffaloEntity buffalo(GameTestHelper helper, double x, double z, float yaw, boolean bull, BlockPos home, int across) {
        BuffaloEntity b = helper.spawn(SavannaEntities.BUFFALO.get(), new Vec3(x, FEET, z));
        b.life().setUp(false, bull ? Sex.MALE : Sex.FEMALE, RandomSource.create((long) (x * 31 + z)));
        b.life().apply();
        b.setHome(home, across);
        b.setYRot(yaw);
        b.yBodyRot = yaw;
        b.yHeadRot = yaw;
        return b;
    }

    /** A survival player standing at x, z (relative). */
    private static ServerPlayer player(GameTestHelper helper, double x, double z) {
        ServerPlayer p = MatabeleDefenceTests.survivalPlayer(helper);
        hold(helper, p, x, z);
        return p;
    }

    /** Holds a test player at x, z (relative; a test player never moves by itself). */
    private static void hold(GameTestHelper helper, ServerPlayer p, double x, double z) {
        Vec3 at = helper.absoluteVec(new Vec3(x, FEET, z));
        p.moveTo(at.x, at.y, at.z, p.getYRot(), 0.0F);
        p.setDeltaMovement(Vec3.ZERO);
    }

    /** Gone from the world (a player left behind changes later tests). */
    private static void remove(GameTestHelper helper, ServerPlayer p) {
        if (!p.isRemoved()) {
            helper.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    /** Where something is, relative to the arena. */
    private static Vec3 rel(GameTestHelper helper, Vec3 abs) {
        return abs.subtract(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)));
    }

    private static String at(GameTestHelper helper, BuffaloEntity b) {
        Vec3 r = rel(helper, b.position());
        return String.format("(%.1f, %.1f, %.1f)", r.x, r.y, r.z);
    }

    private static double flat(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * A home (absolute, at ground level) in the arena's middle whose grazing place for this bout lies within lo to hi
     * (relative x and z), so the herd grazes on this test's field.
     */
    private static BlockPos homeGrazingWithin(GameTestHelper helper, long bout, int across, int lo, int hi) {
        for (int x = 24; x <= 40; x++) {
            for (int z = 24; z <= 40; z++) {
                BlockPos home = helper.absolutePos(new BlockPos(x, GROUND, z));
                Vec3 c = rel(helper, BuffaloGoals.grazeCentre(home, across, bout));
                if (c.x >= lo && c.x <= hi && c.z >= lo && c.z <= hi) {
                    return home;
                }
            }
        }
        throw new IllegalStateException("no home in the arena's middle grazes within " + lo + " to " + hi);
    }

    // --- herds laid with the land ------------------------------------------------------------------------------------------

    /**
     * A herd is laid with a pan in new land (as {@code RiverLifeTests.hippoPodsAreLaidWithTheLandAndKeptForGood} lays a
     * pod): 3 to 5, one bull, all sharing the pan's middle as their home, each on dry grass with its whole body clear,
     * kept for good; the buffalo is in no biome's spawn list; after 10 s there they are unhurt and inside no block.
     */
    @GameTest(template = "arena", timeoutTicks = 300, skyAccess = true)
    public static void aHerdIsLaidWithItsPanAndKeptForGood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestClock.still(helper, 7.0);
        field(helper, 0, 0, 64, 64);
        int across = 9;
        int water = pan(helper, 32, 32, across);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos middle = helper.absolutePos(new BlockPos(32, GROUND, 32));
        PanFeature.Laid laid = new PanFeature.Laid(middle, across, false, 1, 2, water, 100);
        int[] bounds = {origin.getX(), origin.getZ(), origin.getX() + 64, origin.getZ() + 64};
        BuffaloHerds.Herd herd = BuffaloHerds.lay(level, RandomSource.create(7), laid, bounds, OxbowFeature.Keepout.NONE);
        List<BuffaloEntity> found = TestIsolation.insectsOf(helper, BuffaloEntity.class);
        List<BuffaloEntity> grown = found.stream().filter(BuffaloEntity::grownUp).toList();
        helper.assertTrue(herd.buffalo() >= 3 && herd.buffalo() <= 5 && grown.size() == herd.buffalo(),
                "a herd of " + herd.buffalo() + " laid (" + grown.size() + " grown found, " + herd.spots() + " spots), not 3 to 5");
        helper.assertTrue(grown.stream().filter(b -> b.life().isMale()).count() == 1, "a herd is one bull and his cows");
        // lion step 2: a herd of 4 or 5 may have one calf, laid young beside its mother, on a laying spot like the rest
        helper.assertTrue(found.size() - grown.size() <= (herd.buffalo() >= BuffaloHerds.CALF_HERD ? 1 : 0), "too many calves laid: "
                + (found.size() - grown.size()) + " with a herd of " + herd.buffalo());
        for (BuffaloEntity calf : found) {
            if (!calf.grownUp()) {
                helper.assertTrue(calf.mother() != null && calf.distanceTo(calf.mother()) <= 2.5 + BuffaloEntity.CALF_SIDE,
                        "a laid calf is not by its mother: " + calf.about());
            }
        }
        for (BuffaloEntity b : found) {
            b.led = true;                                   // they stand where they were laid
            helper.assertTrue(middle.equals(b.home()) && b.homeAcross() == across, "a herd member's home " + b.home() + " (across "
                    + b.homeAcross() + ") is not its pan's middle " + middle);
            BlockPos under = BlockPos.containing(b.getX(), b.getY() - 0.5, b.getZ());
            helper.assertTrue(level.getBlockState(under).is(Blocks.GRASS_BLOCK) && !b.isInWater(), "a buffalo was laid off dry grass at "
                    + at(helper, b) + " on " + level.getBlockState(under));
            helper.assertTrue(level.noCollision(b, b.getBoundingBox().deflate(0.01)), "a buffalo was laid inside a block at " + at(helper, b));
            helper.assertFalse(b.removeWhenFarAway(1.0E9), "a herd buffalo would despawn far from a player");
        }
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        biomes.entrySet().forEach(e -> {
            for (MobCategory group : MobCategory.values()) {
                helper.assertTrue(e.getValue().getMobSettings().getMobs(group).unwrap().stream().noneMatch(d -> d.type == SavannaEntities.BUFFALO.get()),
                        "the buffalo is in " + e.getKey().location() + "'s " + group.getName() + " spawn list");
            }
        });
        helper.runAtTickTime(200, () -> {
            for (BuffaloEntity b : found) {
                helper.assertTrue(b.isAlive() && b.getHealth() >= b.getMaxHealth(), "a laid buffalo was hurt standing: health " + b.getHealth());
                helper.assertTrue(level.noCollision(b, b.getBoundingBox().deflate(0.01)), "a laid buffalo is inside a block at " + at(helper, b));
            }
            helper.succeed();
        });
    }

    // --- its day -----------------------------------------------------------------------------------------------------------

    /**
     * Mates graze apart and crop the grass short (the plan's day): on a field of tall grass round their grazing place,
     * no two grazing buffalo ever have their middles under {@link BuffaloGoals#SPACE} apart; tall grass is cropped to short
     * grass, never to bare ground; dry tufts are left alone (the termites' food).
     */
    @GameTest(template = "arena", timeoutTicks = 2400, skyAccess = true)
    public static void matesGrazeApartAndCropTallGrassShort(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestClock clock = TestClock.still(helper, 7.0);
        field(helper, 0, 0, 64, 64);
        long bout = BuffaloGoals.boutAt(clock.dayTime());
        BlockPos home = homeGrazingWithin(helper, bout, 0, 18, 46);
        Vec3 c = rel(helper, BuffaloGoals.grazeCentre(home, 0, bout));
        int cx = Mth.floor(c.x), cz = Mth.floor(c.z);
        BlockState dryTuft = SavannaBlocks.DRY_GRASS.get().defaultBlockState().setValue(DryGrassBlock.GROWTH, DryGrassBlock.Growth.DRY);
        List<BlockPos> plants = new ArrayList<>(), dry = new ArrayList<>();
        for (int dx = -14; dx <= 14; dx++) {
            for (int dz = -14; dz <= 14; dz++) {
                int x = cx + dx, z = cz + dz;
                if (x < 1 || x > 63 || z < 1 || z > 63) {
                    continue;
                }
                BlockPos p = new BlockPos(x, FEET, z);
                if (Math.floorMod(dx + dz * 3, 5) == 0) {
                    level.setBlock(helper.absolutePos(p), dryTuft, 2);
                    dry.add(p);
                } else {
                    level.setBlock(helper.absolutePos(p), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), 2);
                    level.setBlock(helper.absolutePos(p.above()), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 2);
                    plants.add(p);
                }
            }
        }
        List<BuffaloEntity> herd = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2.0 / 3.0;
            herd.add(buffalo(helper, c.x + Math.cos(a) * 6.0, c.z + Math.sin(a) * 6.0, i * 120.0F, i == 0, home, 0));
        }
        helper.onEachTick(() -> {
            for (int i = 0; i < herd.size(); i++) {
                for (int j = i + 1; j < herd.size(); j++) {
                    BuffaloEntity a = herd.get(i), b = herd.get(j);
                    if (a.doing() == BuffaloEntity.GRAZE && b.doing() == BuffaloEntity.GRAZE) {
                        helper.assertTrue(flat(a.position(), b.position()) >= BuffaloGoals.SPACE - 0.05, "two buffalo graze "
                                + String.format("%.2f", flat(a.position(), b.position())) + " apart, at " + at(helper, a) + " and " + at(helper, b));
                    }
                }
            }
            if (helper.getTick() % 20 == 0) {
                for (BlockPos p : dry) {
                    helper.assertTrue(helper.getBlockState(p).equals(dryTuft), "a dry tuft was grazed at " + p + ": " + helper.getBlockState(p));
                }
                for (BlockPos p : plants) {
                    BlockState s = helper.getBlockState(p);
                    helper.assertTrue(s.is(Blocks.TALL_GRASS) || s.is(Blocks.SHORT_GRASS), "grass grazed to bare ground at " + p + ": " + s);
                }
            }
        });
        helper.succeedWhen(() -> {
            long cropped = plants.stream().filter(p -> helper.getBlockState(p).is(Blocks.SHORT_GRASS)).count();
            helper.assertTrue(helper.getTick() >= 400 && cropped >= 3, cropped + " tall grass cropped short after " + helper.getTick()
                    + " ticks: " + herd.get(0).about());
        });
    }

    /**
     * At midday the herd walks to its pan, each to its own shore stand, drinks facing the water, and lies down by it.
     */
    @GameTest(template = "arena", timeoutTicks = 2000, skyAccess = true)
    public static void atMiddayTheHerdDrinksAtItsPanAndLiesDownByIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestClock.still(helper, 12.0);
        field(helper, 8, 8, 56, 56);
        int across = 7;
        pan(helper, 32, 32, across);
        BlockPos home = helper.absolutePos(new BlockPos(32, GROUND, 32));
        List<BuffaloEntity> herd = List.of(buffalo(helper, 32.5, 20.5, 0.0F, true, home, across),
                buffalo(helper, 44.5, 33.5, 90.0F, false, home, across), buffalo(helper, 23.5, 43.5, 180.0F, false, home, across));
        boolean[] drank = new boolean[herd.size()];
        helper.onEachTick(() -> {
            for (int i = 0; i < herd.size(); i++) {
                BuffaloEntity b = herd.get(i);
                if (b.doing() == BuffaloEntity.DRINK && !drank[i]) {
                    drank[i] = true;
                    helper.assertTrue(waterNear(level, b.position(), 3.0), "a buffalo drinks with no water near it at " + at(helper, b));
                }
            }
        });
        helper.succeedWhen(() -> {
            for (int i = 0; i < herd.size(); i++) {
                BuffaloEntity b = herd.get(i);
                helper.assertTrue(drank[i], "a buffalo has not drunk: " + b.about() + " at " + at(helper, b));
                helper.assertTrue(b.doing() == BuffaloEntity.REST, "a buffalo is not lying down after drinking: " + b.about());
                helper.assertTrue(waterNear(level, b.position(), 4.5), "a buffalo lies down away from the water at " + at(helper, b));
            }
        });
    }

    /** Whether there is water at its ground's level within this far of a place (blocks, flat). */
    private static boolean waterNear(ServerLevel level, Vec3 at, double within) {
        int r = Mth.ceil(within);
        BlockPos feet = BlockPos.containing(at);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= within * within && level.getFluidState(feet.offset(dx, -1, dz)).is(FluidTags.WATER)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * At night (23:00, the evening's bout) the herd lies down round where the evening's grazing was
     * ({@link BuffaloGoals#grazeCentre}), not by the water.
     */
    @GameTest(template = "arena", timeoutTicks = 2400, skyAccess = true)
    public static void atNightTheHerdLiesDownWhereItGrazed(GameTestHelper helper) {
        TestClock clock = TestClock.still(helper, 23.0);
        field(helper, 0, 0, 64, 64);
        int across = 5;
        pan(helper, 32, 32, across);
        BlockPos home = helper.absolutePos(new BlockPos(32, GROUND, 32));
        Vec3 c = BuffaloGoals.grazeCentre(home, across, BuffaloGoals.boutAt(clock.dayTime()));
        List<BuffaloEntity> herd = List.of(buffalo(helper, 32.5, 24.5, 0.0F, true, home, across),
                buffalo(helper, 40.5, 33.5, 90.0F, false, home, across), buffalo(helper, 26.5, 38.5, 180.0F, false, home, across));
        double near = 3.0 + 4.0 + 1.5;                     // its spot's radius lying, its stand's reach, and a little
        helper.succeedWhen(() -> {
            for (BuffaloEntity b : herd) {
                helper.assertTrue(b.doing() == BuffaloEntity.REST && flat(b.position(), c) <= near, "a buffalo is not lying where the herd grazed ("
                        + String.format("%.1f", flat(b.position(), c)) + " from it, " + rel(helper, c) + "): " + b.about() + " at " + at(helper, b));
            }
        });
    }

    // --- its temper --------------------------------------------------------------------------------------------------------

    /**
     * A survival player standing 10 blocks off a grazing herd: the buffalo near enough stare (mood_alert), then each is
     * grazing again within about 15 s of its stare; none charges.
     */
    @GameTest(template = "arena", timeoutTicks = 2400, skyAccess = true)
    public static void aHerdStaresAtAStandingPlayerThenGrazesAgain(GameTestHelper helper) {
        TestClock clock = TestClock.still(helper, 7.0);
        field(helper, 0, 0, 64, 64);
        long bout = BuffaloGoals.boutAt(clock.dayTime());
        BlockPos home = homeGrazingWithin(helper, bout, 0, 24, 40);
        Vec3 c = rel(helper, BuffaloGoals.grazeCentre(home, 0, bout));
        List<BuffaloEntity> herd = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2.0 / 3.0;
            herd.add(buffalo(helper, c.x + Math.cos(a) * 6.0, c.z + Math.sin(a) * 6.0, i * 120.0F, i == 0, home, 0));
        }
        boolean[] grazing = new boolean[herd.size()];
        long[] stared = {-1, -1, -1}, again = {-1, -1, -1};
        ServerPlayer[] player = {null};
        double[] spot = new double[2];
        long[] placed = {-1};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (player[0] == null) {
                for (int i = 0; i < herd.size(); i++) {
                    grazing[i] |= herd.get(i).doing() == BuffaloEntity.GRAZE;
                }
                if (grazing[0] && grazing[1] && grazing[2]) {
                    // 10 blocks out from the herd's nearest member, on the far side from the herd's middle
                    BuffaloEntity first = herd.get(0);
                    Vec3 m = rel(helper, first.position());
                    double dx = m.x - c.x, dz = m.z - c.z, len = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                    spot[0] = Mth.clamp(m.x + dx / len * 10.0, 2.0, 62.0);
                    spot[1] = Mth.clamp(m.z + dz / len * 10.0, 2.0, 62.0);
                    player[0] = player(helper, spot[0], spot[1]);
                    placed[0] = now;
                }
                return;
            }
            hold(helper, player[0], spot[0], spot[1]);
            for (int i = 0; i < herd.size(); i++) {
                BuffaloEntity b = herd.get(i);
                helper.assertFalse(b.temper() == BuffaloGoals.Temper.CHARGE, "a buffalo charged a player standing still "
                        + String.format("%.1f", b.distanceTo(player[0])) + " off");
                if (stared[i] < 0 && b.doing() == BuffaloEntity.MOOD_ALERT) {
                    stared[i] = now;
                } else if (stared[i] >= 0 && again[i] < 0 && b.doing() == BuffaloEntity.GRAZE) {
                    again[i] = now;
                }
            }
            boolean anyStared = false, allBack = true;
            for (int i = 0; i < herd.size(); i++) {
                anyStared |= stared[i] >= 0;
                if (stared[i] >= 0) {
                    helper.assertTrue(again[i] >= 0 || now - stared[i] <= 330, "a buffalo has not grazed again 16 s after it stared: "
                            + herd.get(i).about());
                    allBack &= again[i] >= 0;
                }
            }
            if (anyStared && allBack && now - placed[0] >= 340) {
                remove(helper, player[0]);
                helper.succeed();
            }
        });
        helper.runAtTickTime(2390, () -> {
            if (player[0] != null) {
                remove(helper, player[0]);
            }
            helper.fail("no stare and graze again: stared " + java.util.Arrays.toString(stared) + ", again " + java.util.Arrays.toString(again)
                    + ", grazed " + java.util.Arrays.toString(grazing) + ", player placed at tick " + placed[0] + "; " + herd.get(0).about()
                    + " | " + herd.get(1).about() + " at " + at(helper, herd.get(1)) + " | " + herd.get(2).about() + " at " + at(helper, herd.get(2)));
        });
    }

    /**
     * A player walking up to a herd (standing still at 4 blocks): the herd's nearest member warns them (a snort, pawing),
     * then charges and its hit lands. A second cow stands 6.4 blocks from where the player stops (inside the warning's
     * reach of 7) but always farther than the nearest member: she may stare, never warns or charges (the nearest-member
     * rule). Then, once the charger's calm is over, a second player walks up to 5.5 blocks of where it stands now (inside
     * the warning's reach, outside the charge's 5): they get their own warning before any charge (review R9-13: a warning
     * is remembered per player, not per animal), and stepping in to 4 blocks after it, they are charged.
     */
    @GameTest(template = "arena", timeoutTicks = 1600, skyAccess = true)
    public static void aWalkingPlayerIsWarnedThenChargedAndASecondGetsTheirOwnWarning(GameTestHelper helper) {
        TestClock.still(helper, 7.0);
        field(helper, 0, 20, 64, 44);
        BlockPos home = helper.absolutePos(new BlockPos(6, GROUND, 32));
        BuffaloEntity near = buffalo(helper, 20.5, 32.5, -90.0F, false, home, 0);
        // 6.4 from the first player's stop (24.5, 32.5), and from every point of the walk farther than `near` is
        BuffaloEntity second = buffalo(helper, 19.5, 36.5, -90.0F, false, home, 0);
        List<BuffaloEntity> herd = List.of(near, second, buffalo(helper, 12.5, 26.5, -90.0F, true, home, 0),
                buffalo(helper, 8.5, 37.5, -90.0F, false, home, 0));
        herd.forEach(b -> b.led = true);                    // its day off: only its temper moves it
        sameHome(helper, herd);
        ServerPlayer[] walker = {player(helper, 40.5, 32.5), null};
        // the walk: from x[0] toward x[1] along z[0] (relative), 0.2 a tick (about 4 blocks a second)
        double[] x = {40.5, 24.5}, z = {32.5};
        float[] health = {walker[0].getHealth()};
        // 0 the first player; 1 waiting for the charger's calm to end; 2 the second player walking to 5.5 off; 3 stepping in to 4
        int[] stage = {0};
        boolean[] warned = new boolean[2], hit = new boolean[2], calmed = {false};
        double[] nearAt = new double[2];
        helper.onEachTick(() -> {
            for (BuffaloEntity b : herd) {
                helper.assertTrue(b == near || b.temper() != BuffaloGoals.Temper.WARN && b.temper() != BuffaloGoals.Temper.CHARGE,
                        "a buffalo that is not the herd's nearest warned or charged: " + b.about() + " at " + at(helper, b));
            }
            if (stage[0] == 1) {
                calmed[0] |= near.temper() == BuffaloGoals.Temper.CALM;
                if (calmed[0] && near.temper() == BuffaloGoals.Temper.NONE) {
                    // its calm is over: the second player walks up to 5.5 blocks of where it stands now
                    Vec3 n = rel(helper, near.position());
                    nearAt[0] = n.x;
                    nearAt[1] = n.z;
                    x[0] = n.x + 16.0;
                    x[1] = n.x + 5.5;
                    z[0] = n.z;
                    walker[1] = player(helper, x[0], z[0]);
                    health[0] = walker[1].getHealth();
                    stage[0] = 2;
                }
                return;
            }
            int who = stage[0] == 0 ? 0 : 1;
            ServerPlayer p = walker[who];
            x[0] = Math.max(x[1], x[0] - 0.2);
            hold(helper, p, x[0], z[0]);
            if (near.temperOn() == p) {
                warned[who] |= near.temper() == BuffaloGoals.Temper.WARN;
                if (near.temper() == BuffaloGoals.Temper.CHARGE) {
                    helper.assertTrue(warned[who], "player " + (who + 1) + " was charged without a warning, "
                            + String.format("%.1f", near.distanceTo(p)) + " off: " + near.about());
                }
            }
            if (stage[0] == 2 && warned[1] && near.temper() != BuffaloGoals.Temper.WARN) {
                stage[0] = 3;                               // warned, and the warning played out: one step in, to 4 off
                x[1] = nearAt[0] + 4.0;
            }
            if (p.getHealth() < health[0] && !hit[who]) {
                hit[who] = true;
                remove(helper, p);
                if (who == 0) {
                    stage[0] = 1;
                } else {
                    helper.succeed();
                }
            }
        });
        helper.runAtTickTime(1590, () -> {
            for (ServerPlayer p : walker) {
                if (p != null) {
                    remove(helper, p);
                }
            }
            int who = stage[0] == 0 ? 0 : 1;
            helper.fail("stage " + stage[0] + ", player " + (who + 1) + ": warned " + warned[who] + ", hit " + hit[who] + ", calm seen "
                    + calmed[0] + "; " + near.about() + " at " + at(helper, near));
        });
    }

    /** Every member of a herd shares one home (set before the first tick: buffalo with none would each take their own). */
    private static void sameHome(GameTestHelper helper, List<BuffaloEntity> herd) {
        for (BuffaloEntity b : herd) {
            helper.assertTrue(b.home() != null && b.home().equals(herd.get(0).home()) && b.homeAcross() == herd.get(0).homeAcross(),
                    "a herd member's home " + b.home() + " (across " + b.homeAcross() + ") is not its herd's " + herd.get(0).home());
        }
    }

    /**
     * A cow shot by a player 20 blocks off charges the archer; the herd's bull joins her charge; the other cows never
     * charge but bunch round where she was hurt and face the archer ({@code mood_angry}), all while her anger lasts (one
     * shot: {@link BuffaloGoals#REVENGE_TICKS}). The archer has 1000 health, so the cow's and the bull's butts never kill
     * it (a player's 20 went in three hits, and a dead archer ends the anger before the cows have bunched).
     */
    @GameTest(template = "arena", timeoutTicks = 800, skyAccess = true)
    public static void aCowShotFrom20BlocksChargesTheBullJoinsAndTheCowsBunch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestClock.still(helper, 7.0);
        field(helper, 0, 16, 64, 48);
        BlockPos home = helper.absolutePos(new BlockPos(6, GROUND, 32));
        BuffaloEntity shot = buffalo(helper, 20.5, 32.5, -90.0F, false, home, 0);
        BuffaloEntity bull = buffalo(helper, 20.5, 25.5, -90.0F, true, home, 0);
        List<BuffaloEntity> cows = List.of(buffalo(helper, 13.5, 29.5, -90.0F, false, home, 0), buffalo(helper, 13.5, 36.5, -90.0F, false, home, 0));
        List.of(shot, bull, cows.get(0), cows.get(1)).forEach(b -> b.led = true);
        sameHome(helper, List.of(shot, bull, cows.get(0), cows.get(1)));
        ServerPlayer archer = player(helper, 40.5, 32.5);
        archer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        archer.setHealth(1000.0F);
        Vec3 hurtAt = shot.position();
        boolean[] shotCharged = {false}, bullJoined = {false};
        boolean[][] cowSeen = new boolean[2][2];          // [cow][bunched, bunched and facing]
        helper.runAtTickTime(3, () -> {
            Arrow arrow = new Arrow(EntityType.ARROW, level);
            arrow.setOwner(archer);
            shot.hurt(level.damageSources().arrow(arrow, archer), 1.0F);
        });
        helper.onEachTick(() -> {
            hold(helper, archer, 40.5, 32.5);
            shotCharged[0] |= shot.temper() == BuffaloGoals.Temper.CHARGE && shot.temperOn() == archer;
            bullJoined[0] |= bull.temper() == BuffaloGoals.Temper.CHARGE && bull.temperOn() == archer;
            for (int i = 0; i < 2; i++) {
                BuffaloEntity cow = cows.get(i);
                helper.assertFalse(cow.temper() == BuffaloGoals.Temper.CHARGE, "a cow that was not hurt charged: " + cow.about());
                if (cow.temper() == BuffaloGoals.Temper.BUNCH) {
                    cowSeen[i][0] = true;
                    float want = (float) Math.toDegrees(Math.atan2(archer.getZ() - cow.getZ(), archer.getX() - cow.getX())) - 90.0F;
                    if (cow.doing() == BuffaloEntity.MOOD_ANGRY && flat(cow.position(), hurtAt) <= BuffaloGoals.SPACE + 2.5
                            && Math.abs(Mth.wrapDegrees(want - cow.getYRot())) < 45.0F) {
                        cowSeen[i][1] = true;
                    }
                }
            }
            if (shotCharged[0] && bullJoined[0] && cowSeen[0][1] && cowSeen[1][1]) {
                remove(helper, archer);
                helper.succeed();
                return;
            }
            if (helper.getTick() > 5 && shot.angryAt() == null) {
                remove(helper, archer);                     // her anger is over: what was not seen by now never will be
                helper.fail("the shot cow's anger ended at tick " + helper.getTick() + " (archer alive " + archer.isAlive()
                        + ", health " + archer.getHealth() + "): she charged " + shotCharged[0] + ", the bull joined " + bullJoined[0]
                        + ", cows bunched " + cowSeen[0][0] + "/" + cowSeen[1][0] + " and faced " + cowSeen[0][1] + "/" + cowSeen[1][1]
                        + "; a cow: " + cows.get(0).about() + " at " + at(helper, cows.get(0)) + "; the bull: " + bull.about());
            }
        });
        helper.runAtTickTime(790, () -> {
            remove(helper, archer);
            helper.fail("the shot cow charged " + shotCharged[0] + ", the bull joined " + bullJoined[0] + ", cows bunched "
                    + cowSeen[0][0] + "/" + cowSeen[1][0] + " and faced " + cowSeen[0][1] + "/" + cowSeen[1][1] + "; a cow: "
                    + cows.get(0).about() + " at " + at(helper, cows.get(0)));
        });
    }

    // --- its paces (measured, never worked out: BUGS 246) -----------------------------------------------------------------

    /**
     * Its charge runs at about 5 blocks a second (4.5 to 5.5: faster than a walking player, slower than a sprint), measured
     * over 12 blocks of a gallop at a player 28 blocks off who hurt it; its run-up butt's hit lands on the player standing
     * still. The pace is logged ({@code [buffalo] charge pace}) for tuning {@code BuffaloEntity.CHARGE}.
     */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true)
    public static void aChargeRunsAtAboutFiveBlocksASecondAndItsHitLands(GameTestHelper helper) {
        TestClock.still(helper, 7.0);
        field(helper, 0, 26, 64, 38);
        BlockPos home = helper.absolutePos(new BlockPos(2, GROUND, 32));
        BuffaloEntity b = buffalo(helper, 4.5, 32.5, -90.0F, true, home, 0);
        b.led = true;
        ServerPlayer target = player(helper, 32.5, 32.5);
        float health = target.getHealth();
        long[] from = {-1}, to = {-1};
        boolean[] runUp = {false};
        double startX = helper.absoluteVec(new Vec3(4.5, 0, 0)).x;
        helper.runAtTickTime(5, () -> b.hurt(helper.getLevel().damageSources().playerAttack(target), 1.0F));
        helper.onEachTick(() -> {
            hold(helper, target, 32.5, 32.5);
            double ran = b.getX() - startX;
            if (from[0] < 0 && ran >= 4.0) {
                from[0] = helper.getTick();
            }
            if (to[0] < 0 && ran >= 16.0) {
                to[0] = helper.getTick();
            }
            runUp[0] |= b.doing() == BuffaloEntity.CHARGE_HEADBUTT;
            if (to[0] > 0 && target.getHealth() < health) {
                double pace = 12.0 / ((to[0] - from[0]) / 20.0);
                SavannaMod.LOGGER.info("[buffalo] charge pace {} blocks a second (CHARGE {}), the run-up butt {}", String.format("%.2f", pace),
                        BuffaloEntity.CHARGE, runUp[0]);
                remove(helper, target);
                helper.assertTrue(pace >= 4.5 && pace <= 5.5, "the charge ran at " + String.format("%.2f", pace)
                        + " blocks a second, not 4.5 to 5.5 (tune BuffaloEntity.CHARGE, now " + BuffaloEntity.CHARGE + ")");
                helper.assertTrue(runUp[0], "the hit landed without the run-up butt");
                helper.succeed();
            }
        });
        helper.runAtTickTime(590, () -> {
            remove(helper, target);
            helper.fail("no charge's hit landed: ran " + String.format("%.1f", b.getX() - startX) + " blocks, from " + from[0] + " to " + to[0]
                    + ", run-up " + runUp[0] + "; " + b.about());
        });
    }

    /**
     * Its travelling walk (a trip to a new place) runs at about 1 block a second (0.8 to 1.25), measured over 16 blocks.
     * The pace is logged ({@code [buffalo] travel pace}) for tuning {@code BuffaloEntity.TRAVEL}.
     */
    @GameTest(template = "arena", timeoutTicks = 900, skyAccess = true)
    public static void aBuffaloTravelsAtAboutOneBlockASecond(GameTestHelper helper) {
        TestClock.still(helper, 7.0);
        field(helper, 0, 26, 64, 38);
        BlockPos home = helper.absolutePos(new BlockPos(2, GROUND, 32));
        BuffaloEntity b = buffalo(helper, 4.5, 32.5, -90.0F, false, home, 0);
        b.led = true;
        BlockPos goal = helper.absolutePos(new BlockPos(32, FEET, 32));
        long[] from = {-1}, to = {-1};
        double startX = helper.absoluteVec(new Vec3(4.5, 0, 0)).x;
        helper.onEachTick(() -> {
            if (helper.getTick() >= 5 && b.getNavigation().isDone() && b.getX() - startX < 20.0) {
                b.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, BuffaloEntity.TRAVEL);
            }
            double walked = b.getX() - startX;
            if (from[0] < 0 && walked >= 4.0) {
                from[0] = helper.getTick();
            }
            if (to[0] < 0 && walked >= 20.0) {
                to[0] = helper.getTick();
                double pace = 16.0 / ((to[0] - from[0]) / 20.0);
                SavannaMod.LOGGER.info("[buffalo] travel pace {} blocks a second (TRAVEL {})", String.format("%.2f", pace), BuffaloEntity.TRAVEL);
                helper.assertTrue(pace >= 0.8 && pace <= 1.25, "the travelling walk ran at " + String.format("%.2f", pace)
                        + " blocks a second, not about 1 (tune BuffaloEntity.TRAVEL, now " + BuffaloEntity.TRAVEL + ")");
                helper.succeed();
            }
        });
        helper.runAtTickTime(890, () -> helper.fail("the buffalo has not walked 20 blocks in 44 s: walked "
                + String.format("%.1f", b.getX() - startX) + " (from tick " + from[0] + "); " + b.about()));
    }

    // --- its body ----------------------------------------------------------------------------------------------------------

    /**
     * A buffalo knocked into water 3 deep floats low (off the bed, its back out) and swims out onto the grass on its own day,
     * unhurt.
     */
    @GameTest(template = "arena", timeoutTicks = 1600, skyAccess = true)
    public static void aBuffaloInDeepWaterFloatsAndSwimsOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestClock clock = TestClock.still(helper, 7.0);
        // land 3 blocks high (grass on top at y 3), a pool 9 by 9 in its middle, water 3 deep over a stone bed
        int top = 3;
        for (int x = 0; x <= 64; x++) {
            for (int z = 0; z <= 64; z++) {
                boolean pool = x >= 28 && x <= 36 && z >= 28 && z <= 36;
                for (int y = 0; y <= top; y++) {
                    BlockState s = y == 0 ? Blocks.STONE.defaultBlockState() : pool ? Blocks.WATER.defaultBlockState()
                            : y == top ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), s, 2);
                }
            }
        }
        long bout = BuffaloGoals.boutAt(clock.dayTime());
        BlockPos home = null;
        for (int x = 10; x <= 54 && home == null; x += 4) {
            for (int z = 10; z <= 54 && home == null; z += 4) {
                BlockPos h = helper.absolutePos(new BlockPos(x, top, z));
                Vec3 c = rel(helper, BuffaloGoals.grazeCentre(h, 0, bout));
                boolean inLand = c.x >= 12 && c.x <= 52 && c.z >= 12 && c.z <= 52;
                boolean offPool = Math.abs(c.x - 32.5) > 12 || Math.abs(c.z - 32.5) > 12;
                if (inLand && offPool && (x < 26 || x > 38 || z < 26 || z > 38)) {
                    home = h;
                }
            }
        }
        helper.assertTrue(home != null, "no home grazing on this test's land");
        BuffaloEntity b = helper.spawn(SavannaEntities.BUFFALO.get(), new Vec3(32.5, 1.2, 32.5));
        b.life().setUp(false, Sex.MALE, RandomSource.create(3));
        b.life().apply();
        b.setHome(home, 0);
        boolean[] floated = {false};
        double bed = helper.absoluteVec(new Vec3(0, 1, 0)).y;
        helper.onEachTick(() -> {
            if (b.isInWater() && !b.onGround() && b.getY() > bed + 0.6) {
                floated[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            BlockPos under = BlockPos.containing(b.getX(), b.getY() - 0.5, b.getZ());
            helper.assertTrue(floated[0], "the buffalo never floated: " + b.about() + " at " + at(helper, b));
            helper.assertTrue(b.isAlive() && !b.isInWater() && b.onGround() && level.getBlockState(under).is(Blocks.GRASS_BLOCK),
                    "the buffalo has not swum out onto the grass: " + b.about() + " at " + at(helper, b));
            helper.assertTrue(b.getHealth() >= b.getMaxHealth(), "the buffalo was hurt in the water: health " + b.getHealth());
        });
    }

    /** Its death clip (1.4 s) plays out before its body goes: still there a second after it died, gone by two. */
    @GameTest(template = "arena", timeoutTicks = 100, skyAccess = true)
    public static void aBuffalosDeathClipPlaysOutBeforeItsBodyGoes(GameTestHelper helper) {
        TestClock.still(helper, 7.0);
        field(helper, 0, 0, 12, 12);
        BuffaloEntity b = buffalo(helper, 6.5, 6.5, 0.0F, false, helper.absolutePos(new BlockPos(6, GROUND, 6)), 0);
        b.led = true;
        helper.runAtTickTime(5, () -> b.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE));
        helper.runAtTickTime(25, () -> helper.assertTrue(b.isDeadOrDying() && !b.isRemoved(), "the dead buffalo's body went before its death clip played out"));
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(b.isRemoved(), "the dead buffalo's body is still there 2 s after it died");
            helper.succeed();
        });
    }

    /**
     * A buffalo from a spawn egg or a summon joins the herd of the nearest buffalo within 16 blocks (takes its home and
     * across); with none near, its home is where it stands (across 0).
     */
    @GameTest(template = "arena", timeoutTicks = 40, skyAccess = true)
    public static void aSummonedBuffaloNextToAHerdTakesItsHome(GameTestHelper helper) {
        TestClock.still(helper, 7.0);
        field(helper, 0, 0, 64, 20);
        BlockPos home = helper.absolutePos(new BlockPos(2, GROUND, 10));
        BuffaloEntity member = buffalo(helper, 6.5, 10.5, 0.0F, true, home, 9);
        BuffaloEntity near = helper.spawn(SavannaEntities.BUFFALO.get(), new Vec3(16.5, FEET, 10.5));
        BuffaloEntity far = helper.spawn(SavannaEntities.BUFFALO.get(), new Vec3(46.5, FEET, 10.5));
        List.of(member, near, far).forEach(b -> b.led = true);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(home.equals(near.home()) && near.homeAcross() == 9, "a summoned buffalo 10 blocks from a herd did not join it: home "
                    + near.home() + " (across " + near.homeAcross() + ")");
            helper.assertTrue(far.home() != null && !home.equals(far.home()) && far.homeAcross() == 0
                    && far.home().distSqr(far.blockPosition()) <= 2, "a summoned buffalo 40 blocks from a herd took home " + far.home()
                    + " (across " + far.homeAcross() + "), not where it stands");
            helper.succeed();
        });
    }

    /**
     * Crocodiles never take a grown buffalo: a hungry grown crocodile in a pool takes a cow at its edge, never the buffalo
     * standing nearer it at the edge, which stays unhurt.
     */
    @GameTest(template = "arena", timeoutTicks = 2400, skyAccess = true)
    public static void aCrocodileNeverTakesAGrownBuffalo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestClock.still(helper, 0.0);                      // midnight: no wish to bask
        // land 3 high round a pool 8 by 8, water 3 deep, the bank flush with the water's top
        int top = 3;
        for (int x = 4; x <= 28; x++) {
            for (int z = 4; z <= 28; z++) {
                boolean pool = x >= 10 && x <= 17 && z >= 10 && z <= 17;
                for (int y = 0; y <= top; y++) {
                    BlockState s = y == 0 ? Blocks.STONE.defaultBlockState() : pool ? Blocks.WATER.defaultBlockState()
                            : y == top ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), s, 2);
                }
            }
        }
        CrocodileEntity croc = helper.spawn(SavannaEntities.CROCODILE.get(), new Vec3(13.5, 1.0, 13.5));
        croc.life().setUp(false, RandomSource.create(5));
        croc.life().apply();
        croc.makeHungry();
        croc.strikeLuck = 1;
        BuffaloEntity b = helper.spawn(SavannaEntities.BUFFALO.get(), new Vec3(13.5, top + 1, 19.0));
        b.life().setUp(false, Sex.FEMALE, RandomSource.create(6));
        b.life().apply();
        b.setHome(helper.absolutePos(new BlockPos(13, top, 24)), 0);
        b.led = true;
        b.setYRot(180.0F);
        b.yBodyRot = 180.0F;
        net.minecraft.world.entity.animal.Cow cow = helper.spawn(EntityType.COW, new Vec3(19.5, top + 1, 13.5));
        cow.setNoAi(true);
        helper.onEachTick(() -> {
            helper.assertFalse(croc.holds(b), "the crocodile took a grown buffalo");
            helper.assertTrue(b.getHealth() >= b.getMaxHealth(), "the crocodile hurt a grown buffalo: health " + b.getHealth());
        });
        helper.succeedWhen(() -> helper.assertTrue(croc.holds(cow) || !cow.isAlive(), "the crocodile has not taken the cow yet (hungry "
                + croc.isHungry() + "); the buffalo " + at(helper, b)));
    }
}
