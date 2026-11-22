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
}
