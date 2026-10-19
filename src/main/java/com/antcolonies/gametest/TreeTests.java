package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.TreeBranchBlock;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.registry.TreeBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The giant savanna trees (M7.5). Step 1, the blocks: every one places, branches join wood and leaves, leaves never
 * decay, and thorny twigs prick a big animal pushing through them but never an insect.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TreeTests {
    /**
     * Every tree block stands where it is placed (on the arena's ground), the leaves do not tick (they never decay), and
     * a branch joins the log, the branch and the leaves beside it but not the plain ground under it.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void treeBlocksPlaceAndBranchesJoin(GameTestHelper helper) {
        int x = 2;
        for (DeferredBlock<? extends Block> block : TreeBlocks.ALL) {
            BlockPos pos = new BlockPos(x, SURFACE, 2);
            BlockState state = block.get().defaultBlockState();
            if (block == TreeBlocks.CAMEL_THORN_PODS) {
                helper.setBlock(pos.above(), TreeBlocks.CAMEL_THORN.leaves().get()); // pods hang from the crown
            }
            helper.setBlock(pos, state);
            x += 2;
            if (x > 60) {
                helper.fail("too many tree blocks for one row");
            }
        }
        for (TreeBlocks.Kind kind : TreeBlocks.KINDS) {
            if (kind.leaves().get().defaultBlockState().isRandomlyTicking()) {
                helper.fail(kind.name() + " leaves tick (they would decay)");
            }
        }
        BlockPos log = new BlockPos(MIDDLE, SURFACE, MIDDLE);
        BlockPos first = log.east();
        BlockPos second = first.east();
        helper.setBlock(log, TreeBlocks.UMBRELLA_THORN.log().get());
        helper.setBlock(first, TreeBlocks.UMBRELLA_THORN.branch().get());
        helper.setBlock(second, TreeBlocks.UMBRELLA_THORN.branch().get());
        helper.setBlock(second.north(), TreeBlocks.UMBRELLA_THORN.leaves().get());
        helper.succeedWhen(() -> {
            for (int i = 0; i < TreeBlocks.ALL.size(); i++) {
                BlockPos pos = new BlockPos(2 + 2 * i, SURFACE, 2);
                Block block = TreeBlocks.ALL.get(i).get();
                helper.assertBlockPresent(block, pos);
            }
            // Blocks set by hand do not join by themselves; a neighbour change does it, as when placed by a player.
            BlockState a = joined(helper, first);
            BlockState b = joined(helper, second);
            check(helper, a, Direction.WEST, true, "the first branch joins the log");
            check(helper, a, Direction.EAST, true, "the first branch joins the second");
            check(helper, a, Direction.DOWN, false, "a branch does not join the ground");
            check(helper, b, Direction.WEST, true, "the second branch joins the first");
            check(helper, b, Direction.NORTH, true, "the second branch joins the leaves");
            check(helper, b, Direction.EAST, false, "a branch does not join the air");
        });
    }

    private static BlockState joined(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof TreeBranchBlock branch)) {
            helper.fail("no branch at " + relative);
            return state;
        }
        return branch.joined(helper.getLevel(), pos, state);
    }

    private static void check(GameTestHelper helper, BlockState branch, Direction direction, boolean wanted, String what) {
        if (branch.getValue(TreeBranchBlock.PROPERTY_BY_DIRECTION.get(direction)) != wanted) {
            helper.fail("wrong: " + what);
        }
    }

    /**
     * A pig pushed through a row of thorny twigs is slowed and pricked (it loses health); a harvester ant pushed through
     * the same row is never hurt (the spines are far bigger than an insect).
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void thornyTwigsPrickBigAnimalsButNotInsects(GameTestHelper helper) {
        for (int x = MIDDLE - 4; x <= MIDDLE + 4; x++) {
            helper.setBlock(new BlockPos(x, SURFACE, MIDDLE), TreeBlocks.THORNY_TWIG.get());
            helper.setBlock(new BlockPos(x, SURFACE, MIDDLE + 4), TreeBlocks.THORNY_TWIG.get());
        }
        Pig pig = helper.spawn(EntityType.PIG, MIDDLE - 4, SURFACE, MIDDLE);
        WorkerAntEntity ant = helper.spawn(ModEntities.WORKER_ANT.get(), MIDDLE - 4, SURFACE, MIDDLE + 4);
        ant.keepOutOfColonies();
        float pigHealth = pig.getHealth();
        float antHealth = ant.getHealth();
        helper.onEachTick(() -> {
            // Pushed east along the row, back to the start once at the end.
            for (var mob : new net.minecraft.world.entity.Mob[] {pig, ant}) {
                Vec3 at = mob.position();
                double startX = helper.absoluteVec(new Vec3(MIDDLE - 4 + 0.5, 0, 0)).x;
                if (at.x > startX + 8) {
                    mob.teleportTo(startX, at.y, at.z);
                }
                mob.setDeltaMovement(0.15, mob.getDeltaMovement().y, 0);
            }
            if (ant.getHealth() < antHealth) {
                helper.fail("the thorns hurt an ant");
            }
        });
        helper.runAfterDelay(160, () -> {
            if (pig.getHealth() >= pigHealth) {
                helper.fail("the pig went through the thorns unhurt");
            }
            if (ant.getHealth() < antHealth) {
                helper.fail("the thorns hurt an ant");
            }
            helper.succeed();
        });
    }
}
