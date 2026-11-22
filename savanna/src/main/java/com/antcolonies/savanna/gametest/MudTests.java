package com.antcolonies.savanna.gametest;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.SavannaMudBlock;
import com.antcolonies.savanna.block.SavannaMudBlock.Tone;
import com.antcolonies.savanna.block.SavannaMudBlock.Wetness;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Savanna mud (2026-11-21, {@code docs/plans/savanna-mud.md}, section 5, step 1): how far things sink, who is slowed,
 * walking out of a wallow's deep middle, and the mud getting wet and drying.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MudTests {
    private static SavannaMudBlock mud() {
        return SavannaBlocks.SAVANNA_MUD.get();
    }

    /**
     * Lays a stone floor (relative y 0 and 1) under clear air over the box from {@code x0, z0} to {@code x1, z1}, under a
     * glass roof (y 5) so rain on the test server never wets the mud.
     */
    private static void ground(GameTestHelper helper, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = 0; y <= 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), y <= 1 ? Blocks.STONE : y == 5 ? Blocks.GLASS : Blocks.AIR);
                }
            }
        }
    }

    private static Mob still(GameTestHelper helper, EntityType<? extends Mob> type, BlockPos rel) {
        Mob mob = helper.spawnWithNoFreeWill(type, rel.getX() + 0.5F, rel.getY() + 1, rel.getZ() + 0.5F);
        mob.setDeltaMovement(Vec3.ZERO);
        return mob;
    }

    /**
     * How far things sink (the user, 2026-11-21: "0.25 to 0.50 depending on how heavy they are"): in soupy mud a crab a
     * quarter block, a hippo half a block, a husk (a player's body) between; sticky mud half as far; drying and cracked
     * none; whatever the mud's depth in its wallow. Then the soupy mud under the hippo dries and lifts it onto the new floor.
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void lightThingsSinkAQuarterHeavyOnesHalfABlock(GameTestHelper helper) {
        ground(helper, 1, 1, 22, 10);
        BlockState[] kinds = {
                mud().with(Wetness.CRACKED, 4, Tone.RED), mud().with(Wetness.DRYING, 4, Tone.OLIVE),
                mud().with(Wetness.STICKY, 4, Tone.GOLDEN), mud().with(Wetness.SOUPY, 1, Tone.OLIVE),
                mud().with(Wetness.SOUPY, 4, Tone.PALE)};
        Mob[] husks = new Mob[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            BlockPos at = new BlockPos(2 + 2 * i, 1, 3);
            helper.setBlock(at, kinds[i]);
            husks[i] = still(helper, EntityType.HUSK, at);
        }
        // a husk (a little taller than a player) is between light and heavy: its soupy sink lies between 4 and 8 pixels
        int husk = SavannaMudBlock.sinkPixels(kinds[4], husks[0]);
        helper.assertTrue(husk > 4 && husk < 8, "a husk should sink between a quarter and half a block, " + husk + " pixels");
        int[] pixels = {0, 0, (husk + 1) / 2, husk, husk};
        BlockState soupy = mud().with(Wetness.SOUPY, 4, Tone.OLIVE);
        helper.setBlock(new BlockPos(14, 1, 3), soupy);
        Mob crab = still(helper, SavannaEntities.CRAB.get(), new BlockPos(14, 1, 3));
        for (int dx = -1; dx <= 1; dx++) {                           // a hippo's 2-block body needs a patch
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(new BlockPos(18 + dx, 1, 7 + dz), soupy);
            }
        }
        Mob hippo = still(helper, SavannaEntities.HIPPO.get(), new BlockPos(18, 1, 7));
        helper.runAfterDelay(30, () -> {
            double floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
            for (int i = 0; i < kinds.length; i++) {
                double expect = floor - pixels[i] / 16.0;
                helper.assertTrue(Math.abs(husks[i].getY() - expect) < 0.02,
                        "a husk on " + kinds[i] + " should sink " + pixels[i] + " pixels, stands at " + (husks[i].getY() - floor));
            }
            helper.assertTrue(Math.abs(crab.getY() - (floor - 0.25)) < 0.02, "a crab should sink a quarter block in soupy mud, at " + (crab.getY() - floor));
            helper.assertTrue(Math.abs(hippo.getY() - (floor - 0.5)) < 0.02, "a hippo should sink half a block in soupy mud, at " + (hippo.getY() - floor));
            // the mud under the hippo dries one step to sticky: the hippo is lifted to its new floor, a quarter block down
            ServerLevel level = helper.getLevel();
            BlockPos under = helper.absolutePos(new BlockPos(18, 1, 7));
            RandomSource random = RandomSource.create(7);
            for (int tick = 0; tick < 400 && level.getBlockState(under).getValue(SavannaMudBlock.WETNESS) == Wetness.SOUPY; tick++) {
                level.getBlockState(under).randomTick(level, under, random);
            }
            helper.assertTrue(level.getBlockState(under).getValue(SavannaMudBlock.WETNESS) == Wetness.STICKY,
                    "soupy mud with no water near should dry to sticky, is " + level.getBlockState(under));
            helper.assertTrue(level.getBlockState(under).getValue(SavannaMudBlock.DEPTH) == 4, "drying mud keeps its depth");
            helper.assertTrue(hippo.getY() >= floor - 0.25 - 1.0E-3, "the hippo should be lifted onto the firmer mud, at " + (hippo.getY() - floor));
            helper.succeed();
        });
    }

    /**
     * The mud drags a pig's movement by how far it sinks, once a tick whichever blocks it touches; a crab, a mud-adapted
     * river animal, moves on unslowed.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void mudSlowsOthersButNotTheRiverAnimals(GameTestHelper helper) {
        ground(helper, 2, 2, 12, 6);
        BlockPos pigRel = new BlockPos(4, 1, 4), crabRel = new BlockPos(8, 1, 4);
        BlockState soupy = mud().with(Wetness.SOUPY, 4, Tone.OLIVE);
        helper.setBlock(pigRel, soupy);
        helper.setBlock(crabRel, soupy);
        Mob pig = still(helper, EntityType.PIG, pigRel);
        Mob crab = still(helper, SavannaEntities.CRAB.get(), crabRel);
        helper.runAfterDelay(20, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pigAt = helper.absolutePos(pigRel);
            double dragged = 0.2 * SavannaMudBlock.drag(SavannaMudBlock.sinkPixels(soupy, pig));
            pig.setDeltaMovement(0.2, 0, 0);
            level.getBlockState(pigAt).entityInside(level, pigAt, pig);
            helper.assertTrue(dragged < 0.16 && Math.abs(pig.getDeltaMovement().x - dragged) < 1.0E-6,
                    "soupy mud should drag a pig's push to " + dragged + ", left " + pig.getDeltaMovement().x);
            // a neighbouring block the pig's box touches does not slow it again
            BlockPos beside = pigAt.east();
            level.setBlock(beside, soupy, 2);
            level.getBlockState(beside).entityInside(level, beside, pig);
            helper.assertTrue(Math.abs(pig.getDeltaMovement().x - dragged) < 1.0E-6, "only the block under the pig's feet slows it");
            BlockPos crabAt = helper.absolutePos(crabRel);
            crab.setDeltaMovement(0.2, 0, 0);
            level.getBlockState(crabAt).entityInside(level, crabAt, crab);
            helper.assertTrue(crab.getDeltaMovement().x == 0.2, "a crab should not be slowed by mud");
            helper.succeed();
        });
    }

    /**
     * A pig walks out of the middle of a wallow (soupy mud, sticky round it) onto firm ground: never trapped.
     */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void aPigWalksOutOfAWallowsMiddle(GameTestHelper helper) {
        ground(helper, 1, 1, 20, 13);
        BlockPos middle = new BlockPos(7, 1, 7);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                BlockState state = ring == 3 ? mud().with(Wetness.STICKY, 1, Tone.OLIVE) : mud().with(Wetness.SOUPY, 4 - ring, Tone.OLIVE);
                helper.setBlock(middle.offset(dx, 0, dz), state);
            }
        }
        Mob pig = helper.spawn(EntityType.PIG, middle.above());
        BlockPos goal = new BlockPos(16, 2, 7);
        helper.onEachTick(() -> {
            if (pig.getNavigation().isDone()) {
                BlockPos to = helper.absolutePos(goal);
                pig.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.0);
            }
        });
        helper.succeedWhen(() -> {
            // out of the wallow (its 7 by 7 left behind) and standing on firm ground, not in any mud
            BlockPos mid = helper.absolutePos(middle);
            double firm = helper.absolutePos(middle.above()).getY();
            boolean out = Math.abs(pig.getX() - (mid.getX() + 0.5)) > 4.0 || Math.abs(pig.getZ() - (mid.getZ() + 0.5)) > 4.0;
            helper.assertTrue(out && pig.onGround() && Math.abs(pig.getY() - firm) < 1.0E-3,
                    "the pig should walk out of the wallow onto firm ground at y " + firm + ", at " + pig.position());
        });
    }

    /**
     * Mud with water within 4 blocks gets wet and stays wet (the river mud areas); mud 6 blocks off dries, step by step,
     * cracked last, the shallow rim faster than the deep middle.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void mudByWaterStaysWetAndMudAwayFromItDriesFromTheRim(GameTestHelper helper) {
        ground(helper, 1, 1, 20, 8);
        helper.setBlock(new BlockPos(2, 1, 4), Blocks.WATER);
        BlockPos nearRel = new BlockPos(5, 1, 4), rimRel = new BlockPos(12, 1, 4), deepRel = new BlockPos(14, 1, 4);
        helper.setBlock(nearRel, mud().with(Wetness.CRACKED, 2, Tone.OLIVE));
        helper.setBlock(rimRel, mud().with(Wetness.SOUPY, 1, Tone.RED));
        helper.setBlock(deepRel, mud().with(Wetness.SOUPY, 4, Tone.RED));
        ServerLevel level = helper.getLevel();
        RandomSource random = RandomSource.create(11);
        BlockPos near = helper.absolutePos(nearRel), rim = helper.absolutePos(rimRel), deep = helper.absolutePos(deepRel);
        int rimCracked = -1, deepCracked = -1;
        Wetness rimWas = Wetness.SOUPY;
        for (int tick = 1; tick <= 400; tick++) {
            for (BlockPos pos : new BlockPos[] {near, rim, deep}) {
                level.getBlockState(pos).randomTick(level, pos, random);
            }
            Wetness rimNow = level.getBlockState(rim).getValue(SavannaMudBlock.WETNESS);
            helper.assertTrue(rimNow == rimWas || rimNow == rimWas.drier(), "mud dries one step at a time: " + rimWas + " to " + rimNow);
            rimWas = rimNow;
            if (rimCracked < 0 && rimNow == Wetness.CRACKED) {
                rimCracked = tick;
            }
            if (deepCracked < 0 && level.getBlockState(deep).getValue(SavannaMudBlock.WETNESS) == Wetness.CRACKED) {
                deepCracked = tick;
            }
        }
        helper.assertTrue(level.getBlockState(near).getValue(SavannaMudBlock.WETNESS) == Wetness.SOUPY,
                "mud 3 blocks from water should have got soupy and stayed so, is " + level.getBlockState(near));
        helper.assertTrue(rimCracked > 0 && deepCracked > 0, "both the rim and the deep middle should crack in 400 dry ticks (rim "
                + rimCracked + ", deep " + deepCracked + ")");
        helper.assertTrue(rimCracked < deepCracked, "the rim should crack before the deep middle (rim " + rimCracked + ", deep " + deepCracked + ")");
        SavannaMod.LOGGER.info("[mud] random ticks to crack: rim {}, deep middle {}", rimCracked, deepCracked);
        helper.succeed();
    }

    /**
     * The ants' legs and wet mud (step 2): a route goes round a band of wet mud through the gap at its end, but straight
     * across the same band once it has dried; an ant set down in the middle of wet mud still finds its way out (a cost,
     * never a wall); and an ant stands on soupy mud without sinking (a few milligrams).
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void antsGoRoundWetMudButCrossItDry(GameTestHelper helper) {
        ground(helper, 1, 1, 20, 12);
        ServerLevel level = helper.getLevel();
        // a band of wet mud 3 wide across the way, from z 2 to 10: the ground at z 1 and 11 to 12 goes round it
        for (int x = 8; x <= 10; x++) {
            for (int z = 2; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, 1, z), mud().with(Wetness.SOUPY, 2, Tone.OLIVE));
            }
        }
        BlockPos start = helper.absolutePos(new BlockPos(3, 2, 6)), goal = helper.absolutePos(new BlockPos(15, 2, 6));
        com.antcolonies.nav.Route wet = com.antcolonies.nav.AntPathFinder.find(new com.antcolonies.nav.LevelCellMap(level), start, goal, 20000);
        helper.assertTrue(wet != null, "an ant should find a way round the wet mud");
        helper.assertTrue(stepsOnMud(level, wet) == 0, "an ant's route should go round wet mud, it steps on it " + stepsOnMud(level, wet) + " times: " + wet);
        // set down in the middle of the wet band, it still gets out
        BlockPos inside = helper.absolutePos(new BlockPos(9, 2, 6));
        com.antcolonies.nav.Route out = com.antcolonies.nav.AntPathFinder.find(new com.antcolonies.nav.LevelCellMap(level), inside, goal, 20000);
        helper.assertTrue(out != null, "an ant in the middle of wet mud should still find a way out");
        // the band dries: the route goes straight over it
        for (int x = 8; x <= 10; x++) {
            for (int z = 2; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, 1, z), mud().with(Wetness.CRACKED, 2, Tone.OLIVE));
            }
        }
        com.antcolonies.nav.Route dry = com.antcolonies.nav.AntPathFinder.find(new com.antcolonies.nav.LevelCellMap(level), start, goal, 20000);
        helper.assertTrue(dry != null && stepsOnMud(level, dry) >= 3, "an ant's route should cross dried mud straight, " + dry);
        helper.assertTrue(dry.size() < wet.size(), "the dry way across should be shorter than the way round (" + dry.size() + " against " + wet.size() + ")");
        // an ant on soupy mud stands on its surface
        helper.setBlock(new BlockPos(9, 1, 6), mud().with(Wetness.SOUPY, 4, Tone.OLIVE));
        Mob ant = still(helper, SavannaEntities.MATABELE_MAJOR.get(), new BlockPos(9, 1, 6));
        helper.runAfterDelay(20, () -> {
            double floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
            helper.assertTrue(Math.abs(ant.getY() - floor) < 0.02, "an ant should not sink in soupy mud, at " + (ant.getY() - floor));
            helper.succeed();
        });
    }

    /** How many of a route's footholds hold on to savanna mud. */
    private static int stepsOnMud(ServerLevel level, com.antcolonies.nav.Route route) {
        int on = 0;
        for (com.antcolonies.nav.Route.Step step : route.steps()) {
            com.antcolonies.nav.Foothold at = step.to();
            if (!at.isChannel() && level.getBlockState(at.cell().relative(at.surface())).getBlock() instanceof SavannaMudBlock) {
                on++;
            }
        }
        return on;
    }

    /**
     * New land's river mud (step 3): on a bank flush with the water, a strip of mud at the waterline, sticky round its
     * edge and soupy inside, every block close enough to the water to stay wet; never on the sand bank beside it nor up on
     * the floodplain a block higher.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void riverMudLiesAtTheWaterlineAndStaysWet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // the river x 2 to 6, the low bank x 7 to 12 flush with it (sand at z 1 to 4), the floodplain a block up from x 13
        for (int x = 1; x <= 19; x++) {
            for (int z = 0; z <= 17; z++) {
                for (int y = 0; y <= 4; y++) {
                    BlockState state = y == 0 ? Blocks.STONE.defaultBlockState()
                            : y == 1 ? (x <= 6 ? Blocks.WATER : x <= 12 && z <= 4 ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState()
                            : y == 2 && x >= 13 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    if (y == 1 && x >= 13) {
                        state = Blocks.DIRT.defaultBlockState();
                    }
                    helper.setBlock(new BlockPos(x, y, z), state);
                }
            }
        }
        BlockPos corner = helper.absolutePos(new BlockPos(2, 1, 1));
        int top = corner.getY();
        int laid = com.antcolonies.savanna.worldgen.RiverMudFeature.lay(level, RandomSource.create(5), corner.getX(), corner.getZ(), top);
        int soupy = 0, sticky = 0;
        for (int x = 1; x <= 19; x++) {
            for (int z = 0; z <= 17; z++) {
                for (int y = 1; y <= 2; y++) {
                    BlockPos at = helper.absolutePos(new BlockPos(x, y, z));
                    BlockState state = level.getBlockState(at);
                    if (!(state.getBlock() instanceof SavannaMudBlock)) {
                        continue;
                    }
                    helper.assertTrue(y == 1 && x >= 7 && x <= 12, "river mud only on the low bank, not at " + x + " " + y + " " + z);
                    helper.assertTrue(z > 4, "river mud never on the sand bank, at " + x + " " + z);
                    helper.assertTrue(state.getValue(SavannaMudBlock.TONE) == Tone.OLIVE, "river mud is olive silt");
                    helper.assertTrue(SavannaMudBlock.wetted(level, at), "every block of river mud should be kept wet by the river, not at " + x + " " + z);
                    soupy += state.getValue(SavannaMudBlock.WETNESS) == Wetness.SOUPY ? 1 : 0;
                    sticky += state.getValue(SavannaMudBlock.WETNESS) == Wetness.STICKY ? 1 : 0;
                }
            }
        }
        SavannaMod.LOGGER.info("[mud] river mud laid {} blocks: {} soupy, {} sticky", laid, soupy, sticky);
        helper.assertTrue(laid >= 15, "a river mud area should be laid, " + laid + " blocks");
        helper.assertTrue(soupy > 0 && sticky > 0, "river mud should be soupy inside and sticky round its edge (" + soupy + " soupy, " + sticky + " sticky)");
        helper.succeed();
    }

    /**
     * New land's dry wallow (step 3): an oval of dry mud 4 to 7 across laid level with the grass in the land's colour,
     * deepest in its middle and cracked round its edge; none where a log lies within 4 blocks or the ground is uneven.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aDryWallowLiesFlatInOpenGrassOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 60; x++) {
            for (int z = 0; z <= 20; z++) {
                for (int y = 0; y <= 6; y++) {
                    helper.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.STONE : y == 1 ? Blocks.GRASS_BLOCK : Blocks.AIR);
                }
            }
        }
        helper.setBlock(new BlockPos(32, 2, 10), Blocks.OAK_LOG);                   // a log beside the second spot
        for (int x = 44; x <= 60; x++) {                                             // the third spot's ground steps up 2
            for (int z = 0; z <= 20; z++) {
                if (x >= 52) {
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
                    helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
                }
            }
        }
        int lookFrom = helper.absolutePos(new BlockPos(0, 6, 0)).getY();         // under the arena's barrier roof
        BlockPos open = helper.absolutePos(new BlockPos(10, 1, 10)), byLog = helper.absolutePos(new BlockPos(26, 1, 10)),
                step = helper.absolutePos(new BlockPos(51, 1, 10));
        int laid = com.antcolonies.savanna.worldgen.DryWallowFeature.lay(level, RandomSource.create(3), open.getX(), open.getZ(), Tone.RED, lookFrom);
        helper.assertTrue(laid >= 12 && laid <= 40, "a dry wallow 4 to 7 across should be laid in open grass, " + laid + " blocks");
        BlockState middle = level.getBlockState(open);
        helper.assertTrue(middle.getBlock() instanceof SavannaMudBlock && middle.getValue(SavannaMudBlock.DEPTH) >= 3,
                "the wallow's middle should be its deepest mud, is " + middle);
        int edge = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockState state = level.getBlockState(open.offset(dx, 0, dz));
                helper.assertTrue(!(level.getBlockState(open.offset(dx, 1, dz)).getBlock() instanceof SavannaMudBlock), "a dry wallow lies level with the grass");
                if (state.getBlock() instanceof SavannaMudBlock) {
                    helper.assertTrue(state.getValue(SavannaMudBlock.TONE) == Tone.RED, "a dry wallow takes the land's colour");
                    helper.assertTrue(state.getValue(SavannaMudBlock.WETNESS) == Wetness.CRACKED || state.getValue(SavannaMudBlock.WETNESS) == Wetness.DRYING,
                            "new land lays dry wallows dry");
                    edge += state.getValue(SavannaMudBlock.DEPTH) == 1 ? 1 : 0;
                }
            }
        }
        helper.assertTrue(edge > 0, "a dry wallow's edge should be its shallowest mud");
        helper.assertTrue(com.antcolonies.savanna.worldgen.DryWallowFeature.lay(level, RandomSource.create(3), byLog.getX(), byLog.getZ(), Tone.RED, lookFrom) == 0,
                "no dry wallow with a log within 4 blocks");
        helper.assertTrue(com.antcolonies.savanna.worldgen.DryWallowFeature.lay(level, RandomSource.create(3), step.getX(), step.getZ(), Tone.RED, lookFrom) == 0,
                "no dry wallow on uneven ground");
        helper.succeed();
    }

    /** A pan on the floodplain gets a wallow of soupy savanna mud at one end, kept wet by the pan's water. */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aPanHasAWallowAtOneEnd(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 40; x++) {
            for (int z = 0; z <= 40; z++) {
                for (int y = 0; y <= 6; y++) {
                    helper.setBlock(new BlockPos(x, y, z), y <= 1 ? Blocks.DIRT : y == 2 ? Blocks.GRASS_BLOCK : Blocks.AIR);
                }
            }
        }
        BlockPos middle = helper.absolutePos(new BlockPos(20, 2, 20));
        boolean made = com.antcolonies.savanna.worldgen.FloodplainPoolFeature.place(level, middle, RandomSource.create(9), false, middle.getY() - 1);
        helper.assertTrue(made, "a pan should be made on level floodplain");
        int mud = 0, wet = 0;
        for (int dx = -20; dx <= 20; dx++) {
            for (int dz = -20; dz <= 20; dz++) {
                BlockPos at = middle.offset(dx, 0, dz);
                BlockState state = level.getBlockState(at);
                if (state.getBlock() instanceof SavannaMudBlock) {
                    mud++;
                    wet += SavannaMudBlock.wetted(level, at) ? 1 : 0;
                }
            }
        }
        helper.assertTrue(mud >= 5, "a pan should have a wallow of savanna mud at one end, " + mud + " blocks");
        helper.assertTrue(wet == mud, "the pan's water should keep its whole wallow wet (" + wet + " of " + mud + ")");
        helper.succeed();
    }
}
