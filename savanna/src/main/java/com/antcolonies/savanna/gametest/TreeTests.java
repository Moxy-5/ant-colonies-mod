package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestAnts;
import static com.antcolonies.savanna.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.savanna.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.FallenBranchBlock;
import com.antcolonies.savanna.block.MudCrustBlock;
import com.antcolonies.savanna.block.TreeBranchBlock;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.TreeBlocks;
import com.antcolonies.savanna.tree.TreeStage;
import com.antcolonies.savanna.worldgen.TreePlacer;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The giant savanna trees (M7.5). Step 1, the blocks: every one places, branches join wood and leaves, leaves never
 * decay, and thorny twigs prick a big animal pushing through them but never an insect. Step 3, new land: Minecraft's
 * savanna trees are gone from savannas and ours are there as structures; a giant put in chunk by chunk (as world
 * generation does) is the whole tree, its trunk going down where the ground dips under its foot; termite mud on a dead
 * tree stays (it is over wood the termites eat).
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class TreeTests {
    /**
     * Every tree block stands where it is placed (on the arena's ground), the leaves do not tick (they never decay), and
     * a branch joins the log, the branch and the leaves beside it but not the plain ground under it.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void treeBlocksPlaceAndBranchesJoin(GameTestHelper helper) {
        int x = 2, z = 2;
        for (DeferredBlock<? extends Block> block : TreeBlocks.ALL) {
            if (x > 60) {
                x = 2; // the next row
                z += 3;
            }
            if (z > 11) {
                helper.fail("too many tree blocks for four rows");
            }
            BlockPos pos = new BlockPos(x, SURFACE, z);
            BlockState state = block.get().defaultBlockState();
            if (block == TreeBlocks.CAMEL_THORN_PODS) {
                helper.setBlock(pos.above(), TreeBlocks.CAMEL_THORN.leaves().get()); // pods hang from the crown
            }
            if (block == TreeBlocks.BRACKET_FUNGUS) {
                helper.setBlock(pos.south(), TreeBlocks.UMBRELLA_THORN.log().get()); // a bracket grows out of the bark behind it
            }
            if (block == TreeBlocks.FIG_BUNCH || block == TreeBlocks.FIG_LEAF_SPRAY) {
                helper.setBlock(pos.above(), TreeBlocks.FIG_LEAVES.get()); // they hang under a leaf pad
            }
            if (block == TreeBlocks.TRUNK_FIGS) {
                helper.setBlock(pos.south(), TreeBlocks.FIG_WOOD.get()); // figs grow on the bark behind them (facing north)
            }
            helper.setBlock(pos, state);
            x += 2;
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
        planksFromTheWood(helper);
        helper.succeedWhen(() -> {
            for (int i = 0; i < TreeBlocks.ALL.size(); i++) {
                BlockPos pos = new BlockPos(2 + 2 * (i % 30), SURFACE, 2 + 3 * (i / 30)); // 30 to a row, as placed
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

    /**
     * The sycamore fig's blocks (floodplain trees step 3, 2026-11-13): a fig branch joins fig wood, other branches and
     * fig leaves; branches a player builds out from a trunk taper (the first the thickest, one thickness thinner every 2
     * placed on); fig bunches and leaf sprays drop when the leaves above them go, trunk figs when the bark behind them goes
     * and cannot be set where no fig wood is behind; the root steps are 8 and 5 pixels high; the leaves never decay.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void figBlocksPlaceJoinAndHang(GameTestHelper helper) {
        int y = SURFACE + 1;
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        net.minecraft.world.item.ItemStack branches = new net.minecraft.world.item.ItemStack(TreeBlocks.FIG_BRANCH.get(), 64); // a test player still uses up what it places
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, branches);
        BlockPos trunk = new BlockPos(10, y, 10);
        helper.setBlock(trunk, TreeBlocks.FIG_WOOD.get());
        BlockPos[] limb = new BlockPos[5];
        BlockPos against = trunk;
        for (int i = 0; i < limb.length; i++) {
            helper.placeAt(player, branches, against, Direction.EAST); // built out east, each against the last
            limb[i] = against.east();
            against = limb[i];
        }
        int[] wantedStep = {0, 1, 2, 3, 4};
        int[] wantedWidth = {14, 14, 12, 12, 10};
        for (int i = 0; i < limb.length; i++) {
            BlockState state = helper.getBlockState(limb[i]);
            if (!state.is(TreeBlocks.FIG_BRANCH.get())) {
                helper.fail("no fig branch placed at " + limb[i]);
                return;
            }
            int step = state.getValue(com.antcolonies.savanna.block.FigBranchBlock.STEP);
            int width = com.antcolonies.savanna.block.FigBranchBlock.width(step / 2);
            if (step != wantedStep[i] || width != wantedWidth[i]) {
                helper.fail("placed fig branch " + (i + 1) + " is step " + step + " (" + width + " pixels), not step "
                        + wantedStep[i] + " (" + wantedWidth[i] + ")");
            }
        }
        helper.setBlock(limb[1].north(), TreeBlocks.FIG_LEAVES.get());
        BlockState first = joined(helper, limb[0]);
        BlockState second = joined(helper, limb[1]);
        check(helper, first, Direction.WEST, true, "the first fig branch joins the fig wood");
        check(helper, first, Direction.EAST, true, "the first fig branch joins the second");
        check(helper, first, Direction.DOWN, false, "a fig branch does not join the air under it");
        check(helper, second, Direction.NORTH, true, "a fig branch joins the fig leaves");
        if (TreeBlocks.FIG_LEAVES.get().defaultBlockState().isRandomlyTicking()) {
            helper.fail("fig leaves tick (they would decay)");
        }
        // Hanging things: under leaves, on the bark.
        BlockPos bunch = new BlockPos(20, y, 10), spray = new BlockPos(22, y, 10), figs = new BlockPos(24, y, 10);
        helper.setBlock(bunch.above(), TreeBlocks.FIG_LEAVES.get());
        helper.setBlock(bunch, TreeBlocks.FIG_BUNCH.get());
        helper.setBlock(spray.above(), TreeBlocks.FIG_LEAVES.get());
        helper.setBlock(spray, TreeBlocks.FIG_LEAF_SPRAY.get());
        helper.setBlock(figs.south(), TreeBlocks.FIG_LOG.get());
        helper.setBlock(figs, TreeBlocks.TRUNK_FIGS.get());
        BlockPos lonely = new BlockPos(26, y, 10);
        if (TreeBlocks.TRUNK_FIGS.get().defaultBlockState().canSurvive(helper.getLevel(), helper.absolutePos(lonely))) {
            helper.fail("trunk figs could stand with no fig wood behind them");
        }
        helper.assertBlockPresent(TreeBlocks.FIG_BUNCH.get(), bunch);
        helper.assertBlockPresent(TreeBlocks.TRUNK_FIGS.get(), figs);
        helper.setBlock(bunch.above(), Blocks.AIR);
        helper.setBlock(spray.above(), Blocks.AIR);
        helper.setBlock(figs.south(), Blocks.AIR);
        // Root steps.
        var root = TreeBlocks.FIG_ROOT.get().defaultBlockState();
        var level = helper.getLevel();
        double high = root.getShape(level, helper.absolutePos(trunk)).max(Direction.Axis.Y);
        double low = root.setValue(com.antcolonies.savanna.block.FigRootBlock.LOW, true).getShape(level, helper.absolutePos(trunk)).max(Direction.Axis.Y);
        if (Math.abs(high - 0.5) > 1e-6 || Math.abs(low - 5 / 16.0) > 1e-6) {
            helper.fail("fig root steps are " + high + " and " + low + " high, not 8 and 5 pixels");
        }
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(TreeBlocks.FIG_BUNCH.get(), bunch);
            helper.assertBlockNotPresent(TreeBlocks.FIG_LEAF_SPRAY.get(), spray);
            helper.assertBlockNotPresent(TreeBlocks.TRUNK_FIGS.get(), figs);
        });
    }

    /**
     * The doum palm's blocks (floodplain trees step 5, 2026-11-14): a crown keeps its recipe in its block entity, and a
     * recipe set by a growing palm survives a save and load; the crown's cap is solid and 6 pixels high, its fronds and a
     * nut bunch can be walked through; a bunch starts ripe; a placed leaf-base stub faces away from the stem it was put
     * against; the stem wood has a dead grey look.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void doumBlocksPlaceAndKeepTheirLook(GameTestHelper helper) {
        var level = helper.getLevel();
        int y = SURFACE + 1;
        BlockPos stem = new BlockPos(10, y, 10), crown = stem.above();
        helper.setBlock(stem, TreeBlocks.DOUM_LOG.get());
        helper.setBlock(crown, TreeBlocks.DOUM_CROWN.get());
        if (!(level.getBlockEntity(helper.absolutePos(crown)) instanceof com.antcolonies.savanna.block.DoumBlockEntity piece)) {
            helper.fail("a doum crown has no block entity to keep its look");
            return;
        }
        var byHand = piece.look();
        if (byHand.grown() || byHand.bits(com.antcolonies.savanna.block.DoumPiece.Piece.CROWN, false).size() < 300) {
            helper.fail("a hand-placed doum crown draws no crown of its own: " + byHand);
        }
        var planned = new com.antcolonies.savanna.block.DoumLook(true, 12345, 0.5, 9.5, 0.5, 0, 10, 1, 1.06F, true, false, 3, 2);
        piece.setLook(planned);
        var saved = piece.saveWithFullMetadata(level.registryAccess()); // with its id and place, as a chunk saves it
        var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(helper.absolutePos(crown), helper.getBlockState(crown), saved,
                level.registryAccess());
        if (!(loaded instanceof com.antcolonies.savanna.block.DoumBlockEntity again) || !planned.equals(again.look())) {
            helper.fail("a doum crown's planned look is lost in a save and load: " + planned + " became "
                    + (loaded instanceof com.antcolonies.savanna.block.DoumBlockEntity d ? d.look() : loaded));
            return;
        }
        BlockPos at = helper.absolutePos(crown);
        double cap = helper.getBlockState(crown).getCollisionShape(level, at).max(Direction.Axis.Y);
        if (Math.abs(cap - 6 / 16.0) > 1e-6) {
            helper.fail("a doum crown's solid cap is " + cap * 16 + " pixels high, not 6");
        }
        BlockPos bunch = new BlockPos(14, y + 2, 10);
        helper.setBlock(bunch, TreeBlocks.DOUM_NUT_BUNCH.get());
        BlockState bunchState = helper.getBlockState(bunch);
        if (!bunchState.getValue(com.antcolonies.savanna.block.DoumPieceBlock.RIPE)) {
            helper.fail("a doum nut bunch starts green");
        }
        if (!bunchState.getCollisionShape(level, helper.absolutePos(bunch)).isEmpty()) {
            helper.fail("a doum nut bunch can be stood on");
        }
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        net.minecraft.world.item.ItemStack stubs = new net.minecraft.world.item.ItemStack(TreeBlocks.DOUM_LEAF_BASE.get(), 4);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stubs);
        helper.placeAt(player, stubs, stem, Direction.SOUTH);
        BlockState stub = helper.getBlockState(stem.south());
        if (!stub.is(TreeBlocks.DOUM_LEAF_BASE.get())
                || stub.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) != Direction.SOUTH) {
            helper.fail("a leaf-base stub put on a stem's south side is " + stub + ", not facing south");
        }
        BlockState dead = TreeBlocks.DOUM_LOG.get().defaultBlockState().setValue(com.antcolonies.savanna.block.DoumWoodBlock.DEAD, true);
        helper.setBlock(new BlockPos(16, y, 10), dead);
        helper.assertBlockProperty(new BlockPos(16, y, 10), com.antcolonies.savanna.block.DoumWoodBlock.DEAD, true);
        helper.succeed();
    }

    /**
     * The sycamore fig's shapes as the game's blocks (floodplain trees step 4, 2026-11-13; the shapes themselves are
     * checked against the model viewer in {@code FigShapeTest}): a grown fig set down in the arena is every block of its
     * shape, its branches join what they touch, and every fig bunch, leaf spray and bark fig can hang where it is; then
     * the same seed breaking up (dead, hollow, limbs fallen) has its bracket fungi holding on to the dead wood. Every age
     * and dead stage of a few seeds turns into blocks with every small block finding its cell.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void figTreesStandAsTheViewerDrewThem(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int seed = 1; seed <= 3; seed++) {
            for (com.antcolonies.savanna.tree.FigShape.Age age : com.antcolonies.savanna.tree.FigShape.Age.values()) {
                var shape = com.antcolonies.savanna.tree.FigShape.make(seed, age);
                var states = shape.states();
                if (states.size() != shape.cells().size() + shape.marks().size()) {
                    helper.fail("fig seed " + seed + " " + age + ": " + (shape.cells().size() + shape.marks().size() - states.size())
                            + " small blocks land on a cell already taken");
                }
            }
        }
        BlockPos foot = helper.absolutePos(new BlockPos(TREE_MIDDLE, SURFACE + 1, TREE_MIDDLE));
        var grown = com.antcolonies.savanna.tree.FigShape.make(1, com.antcolonies.savanna.tree.FigShape.Age.GROWN).states();
        setFig(level, foot, grown);
        int bunches = 0, barkFigs = 0, joined = 0, branches = 0;
        for (var e : grown.long2ObjectEntrySet()) {
            BlockPos pos = foot.offset(BlockPos.of(e.getLongKey()));
            BlockState want = e.getValue(), there = level.getBlockState(pos);
            if (there.is(Blocks.BARRIER)) {
                continue; // the arena's ceiling, which the tree grows up through
            }
            if (!there.equals(want)) {
                helper.fail("the grown fig's block at " + BlockPos.of(e.getLongKey()).toShortString() + " is " + there + ", not " + want);
                return;
            }
            if (want.is(TreeBlocks.FIG_BUNCH.get()) || want.is(TreeBlocks.FIG_LEAF_SPRAY.get()) || want.is(TreeBlocks.TRUNK_FIGS.get())) {
                if (!want.canSurvive(level, pos)) {
                    helper.fail(want.getBlock() + " cannot hang at " + BlockPos.of(e.getLongKey()).toShortString());
                }
                bunches += want.is(TreeBlocks.FIG_BUNCH.get()) ? 1 : 0;
                barkFigs += want.is(TreeBlocks.TRUNK_FIGS.get()) ? 1 : 0;
            }
            if (want.getBlock() instanceof com.antcolonies.savanna.block.FigBranchBlock) {
                branches++;
                for (Direction d : Direction.values()) {
                    if (want.getValue(TreeBranchBlock.PROPERTY_BY_DIRECTION.get(d))) {
                        joined++;
                        break;
                    }
                }
            }
        }
        if (bunches < 10 || barkFigs < 3) {
            helper.fail("the grown fig has " + bunches + " fig bunches and " + barkFigs + " bark figs (the viewer draws 22 and 8 at most)");
        }
        if (branches == 0 || joined < branches) {
            helper.fail((branches - joined) + " of " + branches + " fig branches join nothing");
        }
        for (long k : grown.keySet()) {
            if (!level.getBlockState(foot.offset(BlockPos.of(k))).is(Blocks.BARRIER)) {
                level.setBlock(foot.offset(BlockPos.of(k)), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        var dead = com.antcolonies.savanna.tree.FigShape.make(1, com.antcolonies.savanna.tree.FigShape.Age.BREAKING_UP).states();
        setFig(level, foot, dead);
        int brackets = 0;
        for (var e : dead.long2ObjectEntrySet()) {
            if (e.getValue().is(TreeBlocks.BRACKET_FUNGUS.get())) {
                brackets++;
                if (!e.getValue().canSurvive(level, foot.offset(BlockPos.of(e.getLongKey())))) {
                    helper.fail("a bracket fungus on the dead fig has no dead wood behind it at " + BlockPos.of(e.getLongKey()).toShortString());
                }
            }
        }
        if (brackets == 0) {
            helper.fail("the fig breaking up has no bracket fungi");
        }
        helper.succeed();
    }

    /**
     * The doum palm's shapes as the game's blocks (floodplain trees step 6, 2026-11-14; the shapes themselves are checked
     * against the model viewer in {@code DoumShapeTest}): every age and dead stage of a few seeds finds a block for every
     * sculpted piece; a grown palm set down in the arena is every block of its shape, every crown, bunch and frond heap
     * keeps the recipe it was given, and every leaf-base stub leans on doum wood; the same seed dead has only dead wood.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void doumTreesStandAsTheViewerDrewThem(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int seed = 1; seed <= 3; seed++) {
            for (com.antcolonies.savanna.tree.DoumShape.Age age : com.antcolonies.savanna.tree.DoumShape.Age.values()) {
                var shape = com.antcolonies.savanna.tree.DoumShape.make(seed, age);
                var placed = shape.placed();
                if (placed.looks().size() != shape.pieces()) {
                    helper.fail("doum seed " + seed + " " + age + ": " + (shape.pieces() - placed.looks().size()) + " of "
                            + shape.pieces() + " sculpted pieces find no free block");
                }
                if (shape.cells().size() + shape.stubs().size() + shape.pieces() - placed.blocks().size() > shape.stubs().size() / 4) {
                    helper.fail("doum seed " + seed + " " + age + ": too many leaf-base stubs land on a taken block ("
                            + placed.blocks().size() + " blocks for " + shape.cells().size() + " cells, " + shape.stubs().size()
                            + " stubs and " + shape.pieces() + " pieces)");
                }
            }
        }
        BlockPos foot = helper.absolutePos(new BlockPos(TREE_MIDDLE, SURFACE + 1, TREE_MIDDLE));
        var grown = com.antcolonies.savanna.tree.DoumShape.make(1, com.antcolonies.savanna.tree.DoumShape.Age.GROWN).placed();
        grown.setDown(level, foot);
        int crowns = 0, bunches = 0, stubs = 0;
        for (var e : grown.blocks().long2ObjectEntrySet()) {
            BlockPos pos = foot.offset(BlockPos.of(e.getLongKey()));
            BlockState want = e.getValue(), there = level.getBlockState(pos);
            if (there.is(Blocks.BARRIER)) {
                continue; // the arena's ceiling
            }
            if (!there.equals(want)) {
                helper.fail("the grown doum's block at " + BlockPos.of(e.getLongKey()).toShortString() + " is " + there + ", not " + want);
                return;
            }
            var look = grown.looks().get(e.getLongKey());
            if (look != null) {
                if (!(level.getBlockEntity(pos) instanceof com.antcolonies.savanna.block.DoumBlockEntity piece) || !look.equals(piece.look())) {
                    helper.fail("the grown doum's " + want.getBlock() + " at " + BlockPos.of(e.getLongKey()).toShortString()
                            + " lost the recipe it was given");
                    return;
                }
                crowns += want.is(TreeBlocks.DOUM_CROWN.get()) ? 1 : 0;
                bunches += want.is(TreeBlocks.DOUM_NUT_BUNCH.get()) ? 1 : 0;
            }
            if (want.is(TreeBlocks.DOUM_LEAF_BASE.get())) {
                stubs++;
                Direction facing = want.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
                BlockState behind = level.getBlockState(pos.relative(facing.getOpposite()));
                if (!behind.is(TreeBlocks.DOUM_WOOD.get()) && !behind.is(TreeBlocks.DOUM_ROOT.get()) && !behind.is(Blocks.BARRIER)) {
                    helper.fail("a leaf-base stub at " + BlockPos.of(e.getLongKey()).toShortString() + " leans on " + behind + ", not doum wood");
                }
            }
        }
        if (crowns < 3 || bunches == 0 || stubs == 0) {
            helper.fail("the grown doum has " + crowns + " crowns, " + bunches + " nut bunches and " + stubs + " leaf-base stubs");
        }
        for (long k : grown.blocks().keySet()) {
            if (!level.getBlockState(foot.offset(BlockPos.of(k))).is(Blocks.BARRIER)) {
                level.setBlock(foot.offset(BlockPos.of(k)), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        var dead = com.antcolonies.savanna.tree.DoumShape.make(1, com.antcolonies.savanna.tree.DoumShape.Age.SNAGS).placed();
        for (var st : dead.blocks().values()) {
            if (st.is(TreeBlocks.DOUM_WOOD.get()) && !st.getValue(com.antcolonies.savanna.block.DoumWoodBlock.DEAD)) {
                helper.fail("the dead doum's snags have living wood");
                return;
            }
        }
        helper.succeed();
    }

    /** A fig's blocks set down around its foot, in their order (wood before what hangs on it), never over the arena's ceiling. */
    private static void setFig(net.minecraft.server.level.ServerLevel level, BlockPos foot,
            it.unimi.dsi.fastutil.longs.Long2ObjectMap<BlockState> states) {
        for (var e : states.long2ObjectEntrySet()) {
            BlockPos pos = foot.offset(BlockPos.of(e.getLongKey()));
            if (!level.getBlockState(pos).is(Blocks.BARRIER)) {
                level.setBlock(pos, e.getValue(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Planks (the user, 2026-11-11): every trunk block of a kind, the heart rot marks too, saws into 4 planks: the
     * umbrella thorn into Minecraft's acacia planks, the camel thorn and baobab into their own, which count as planks.
     */
    private static void planksFromTheWood(GameTestHelper helper) {
        var level = helper.getLevel();
        Object[][] cases = {
                {"acacia_planks_from_umbrella_thorn", "umbrella_thorn_wood_wound", net.minecraft.world.item.Items.ACACIA_PLANKS},
                {"camel_thorn_planks", "camel_thorn_wood_spores", TreeBlocks.CAMEL_THORN_PLANKS.get().asItem()},
                {"baobab_planks", "baobab_log", TreeBlocks.BAOBAB_PLANKS.get().asItem()}};
        for (Object[] c : cases) {
            var id = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, (String) c[0]);
            var recipe = level.getRecipeManager().byKey(id).orElse(null);
            if (recipe == null) {
                helper.fail("no recipe " + id);
                return;
            }
            var out = recipe.value().getResultItem(level.registryAccess());
            if (out.getItem() != c[2] || out.getCount() != 4) {
                helper.fail(id + " makes " + out + ", not 4 " + c[2]);
            }
            var wood = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, (String) c[1]));
            if (!recipe.value().getIngredients().get(0).test(new net.minecraft.world.item.ItemStack(wood))) {
                helper.fail(c[1] + " does not saw into planks by " + id);
            }
        }
        for (DeferredBlock<Block> planks : List.of(TreeBlocks.CAMEL_THORN_PLANKS, TreeBlocks.BAOBAB_PLANKS)) {
            if (!planks.get().defaultBlockState().is(net.minecraft.tags.BlockTags.PLANKS)
                    || !new net.minecraft.world.item.ItemStack(planks.get()).is(net.minecraft.tags.ItemTags.PLANKS)) {
                helper.fail(planks.getId() + " does not count as planks");
            }
        }
        // Everything planks make (the user, 2026-11-11): each has its recipe, and joins Minecraft's wooden ones.
        for (TreeBlocks.WoodSet set : TreeBlocks.WOOD_SETS) {
            for (DeferredBlock<? extends Block> made : List.of(set.stairs(), set.slab(), set.fence(), set.fenceGate(), set.door(),
                    set.trapdoor(), set.pressurePlate(), set.button())) {
                var recipe = level.getRecipeManager().byKey(made.getId()).orElse(null);
                if (recipe == null || recipe.value().getResultItem(level.registryAccess()).getItem() != made.get().asItem()) {
                    helper.fail("no recipe making " + made.getId());
                }
            }
            if (!set.fence().get().defaultBlockState().is(net.minecraft.tags.BlockTags.WOODEN_FENCES)
                    || !set.fenceGate().get().defaultBlockState().is(net.minecraft.tags.BlockTags.FENCE_GATES)
                    || !set.door().get().defaultBlockState().is(net.minecraft.tags.BlockTags.WOODEN_DOORS)) {
                helper.fail(set.planks().getId() + "'s fence, gate or door is not in Minecraft's wooden tags");
            }
        }
        // Used as Minecraft's own (the user, 2026-11-11): planks make sticks and burn; the trunk wood burns, smelts into
        // charcoal and makes a campfire (the item tags, not only the block ones).
        for (String name : List.of("camel_thorn_planks", "baobab_planks", "umbrella_thorn_log", "camel_thorn_wood", "baobab_log")) {
            var stack = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, name)));
            if (stack.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING) <= 0) {
                helper.fail(name + " does not burn in a furnace");
            }
            boolean log = name.contains("log") || name.endsWith("_wood");
            var input = new net.minecraft.world.item.crafting.SingleRecipeInput(stack);
            if (log && level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, input, level)
                    .map(r -> r.value().getResultItem(level.registryAccess()).getItem()).orElse(null) != net.minecraft.world.item.Items.CHARCOAL) {
                helper.fail(name + " does not smelt into charcoal");
            }
            if (log && !stack.is(net.minecraft.tags.ItemTags.LOGS)) {
                helper.fail(name + " is no log for campfires and smokers");
            }
            if (!log) {
                var sticks = net.minecraft.world.item.crafting.CraftingInput.of(1, 2, List.of(stack, stack));
                if (level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, sticks, level)
                        .map(r -> r.value().getResultItem(level.registryAccess()).getItem()).orElse(null) != net.minecraft.world.item.Items.STICK) {
                    helper.fail(name + " does not make sticks");
                }
            }
        }
        // Signs and boats (the user, 2026-11-11): recipes, hanging signs cut from the trunk wood, the signs hold their
        // writing (Minecraft's sign block entity), and a boat breaks back into its own item.
        int x = 40;
        for (var items : com.antcolonies.savanna.registry.SavannaItems.WOOD_ITEMS) {
            for (var made : items.all()) {
                var recipe = level.getRecipeManager().byKey(made.getId()).orElse(null);
                if (recipe == null || recipe.value().getResultItem(level.registryAccess()).getItem() != made.get()) {
                    helper.fail("no recipe making " + made.getId());
                }
            }
            var boatType = items.boat() == com.antcolonies.savanna.registry.SavannaItems.CAMEL_THORN_ITEMS.boat()
                    ? com.antcolonies.savanna.registry.SavannaBoats.CAMEL_THORN.getValue() : com.antcolonies.savanna.registry.SavannaBoats.BAOBAB.getValue();
            if (boatType.getPlanks() == net.minecraft.world.level.block.Blocks.OAK_PLANKS
                    || !boatType.getName().startsWith(SavannaMod.MODID + ":")) {
                helper.fail(items.boat().getId() + " is not a boat kind of its own: " + boatType.getName());
            }
            var boat = new net.minecraft.world.entity.vehicle.Boat(level, 0, 0, 0);
            boat.setVariant(boatType);
            if (boat.getDropItem() != items.boat().get()) {
                helper.fail(items.boat().getId() + " breaks into " + boat.getDropItem());
            }
        }
        for (TreeBlocks.WoodSet set : TreeBlocks.WOOD_SETS) {
            var hanging = level.getRecipeManager().byKey(set.hangingSign().getId()).orElseThrow().value();
            var log = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, set.name() + "_log"));
            if (hanging.getIngredients().stream().noneMatch(i -> i.test(new net.minecraft.world.item.ItemStack(log)))) {
                helper.fail(set.name() + " hanging signs are not cut from its logs");
            }
            for (var sign : List.<DeferredBlock<? extends Block>>of(set.sign(), set.hangingSign())) {
                BlockPos pos = new BlockPos(x, SURFACE + 2, 40);
                x += 2;
                helper.setBlock(pos, sign.get());
                if (!(helper.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity)) {
                    helper.fail(sign.getId() + " has no sign to write on");
                }
            }
        }
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
     * A pig pushed through a row of thorny twigs is slowed and pricked (it loses health); a termite worker pushed through
     * the same row is never hurt (the spines are far bigger than an insect; a harvester ant until the split, step S4).
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void thornyTwigsPrickBigAnimalsButNotInsects(GameTestHelper helper) {
        for (int x = MIDDLE - 4; x <= MIDDLE + 4; x++) {
            helper.setBlock(new BlockPos(x, SURFACE, MIDDLE), TreeBlocks.THORNY_TWIG.get());
            helper.setBlock(new BlockPos(x, SURFACE, MIDDLE + 4), TreeBlocks.THORNY_TWIG.get());
        }
        Pig pig = helper.spawn(EntityType.PIG, MIDDLE - 4, SURFACE, MIDDLE);
        TermiteWorkerEntity ant = TestAnts.loneWithAMind(helper, SavannaEntities.TERMITE_WORKER.get(), MIDDLE - 4, SURFACE, MIDDLE + 4);
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
                helper.fail("the thorns hurt an insect");
            }
        });
        helper.runAfterDelay(160, () -> {
            if (pig.getHealth() >= pigHealth) {
                helper.fail("the pig went through the thorns unhurt");
            }
            if (ant.getHealth() < antHealth) {
                helper.fail("the thorns hurt an insect");
            }
            helper.succeed();
        });
    }

    /** Where the new land tests put their tree: the middle of the whole 65-block arena, room for a crown 46 wide. */
    private static final int TREE_MIDDLE = 32;

    /**
     * New land: the savanna has none of Minecraft's savanna trees, rain falls there, and our two structures exist; a grown camel thorn put
     * in chunk by chunk (each chunk only its own part, as world generation does) is the whole tree, and where a pit
     * dips under one side of its foot the trunk goes down to the pit's floor.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void giantTreesStandInNewLand(GameTestHelper helper) {
        var level = helper.getLevel();
        Biome savanna = level.registryAccess().registryOrThrow(Registries.BIOME).getOrThrow(Biomes.SAVANNA);
        for (var step : savanna.getGenerationSettings().features()) {
            for (var feature : step) {
                String id = feature.unwrapKey().map(k -> k.location().toString()).orElse("");
                if (id.equals("minecraft:trees_savanna") || id.equals("minecraft:trees_windswept_savanna")) {
                    helper.fail("the savanna still grows Minecraft's trees (" + id + ")");
                }
            }
        }
        // It rains in every savanna (Minecraft's never do; real ones have a rainy season), and they stay warm (rain, not snow).
        for (var key : java.util.List.of(Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA)) {
            Biome biome = level.registryAccess().registryOrThrow(Registries.BIOME).getOrThrow(key);
            if (!biome.hasPrecipitation()) {
                helper.fail(key.location() + " never rains");
            }
            if (biome.getBaseTemperature() < 1) {
                helper.fail(key.location() + " is no longer hot: " + biome.getBaseTemperature());
            }
        }
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (String name : new String[] {"big_savanna_tree", "young_savanna_tree"}) {
            if (!structures.containsKey(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, name))) {
                helper.fail("no structure " + name);
            }
        }
        // A pit 3 deep under the east side of the foot.
        for (int x = TREE_MIDDLE + 1; x <= TREE_MIDDLE + 3; x++) {
            for (int z = TREE_MIDDLE - 1; z <= TREE_MIDDLE + 1; z++) {
                for (int y = SURFACE - 3; y < SURFACE; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        BlockPos foot = helper.absolutePos(new BlockPos(TREE_MIDDLE, SURFACE, TREE_MIDDLE));
        TreePlacer.Placed tree = TreePlacer.placed("camel_thorn", 5, 1, TreeStage.LIVING);
        BoundingBox box = tree.box(foot);
        long placing = 0, longestChunk = 0;
        int chunks = 0;
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                BoundingBox chunk = new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15);
                long start = System.nanoTime();
                TreePlacer.place(level, tree, foot, chunk, Block.UPDATE_CLIENTS);
                long took = System.nanoTime() - start;
                placing += took;
                longestChunk = Math.max(longestChunk, took);
                chunks++;
            }
        }
        newLandSpeed(helper, placing, longestChunk, chunks);
        int missing = 0, above = 0;
        String first = "";
        for (int i = 0; i < tree.size(); i++) {
            if (tree.ys()[i] < 0 || tree.states()[i].getBlock() instanceof FallenBranchBlock) {
                continue;
            }
            above++;
            BlockPos pos = foot.offset(tree.xs()[i], tree.ys()[i], tree.zs()[i]);
            if (level.getBlockState(pos).is(Blocks.BARRIER)) {
                continue; // the test arena's ceiling, which the tree grows up through
            }
            if (!level.getBlockState(pos).is(tree.states()[i].getBlock())) {
                if (missing++ == 0) {
                    first = tree.xs()[i] + "," + tree.ys()[i] + "," + tree.zs()[i] + " " + tree.states()[i].getBlock() + " is " + level.getBlockState(pos);
                }
            }
        }
        if (above < 3000) {
            helper.fail("a grown camel thorn of only " + above + " blocks");
        }
        if (missing > 0) {
            helper.fail(missing + " of " + above + " blocks of the tree missing, the first " + first);
        }
        // The trunk over the pit goes down to the pit floor.
        for (int y = SURFACE - 3; y < SURFACE; y++) {
            BlockState there = helper.getBlockState(new BlockPos(TREE_MIDDLE + 1, y, TREE_MIDDLE));
            if (!there.is(TreeBlocks.TREE_WOOD)) {
                helper.fail("the trunk does not reach down into the pit at y " + y + ": " + there);
            }
        }
        newLandLitter(helper, foot);
        helper.succeed();
    }

    /**
     * Step 7: how long new land's trees take. Trees of both structures are picked as new land picks them (the real
     * spread of kinds, ages and dead stages) and worked out from nothing, and the grown camel thorn above was put down
     * chunk by chunk; the times go to the log. The limits are far over what they take (a test server shares the PC with
     * others), so only something badly slower fails: a tree's shape over half a second on average, a chunk's part of a
     * tree over a tenth of a second.
     */
    private static void newLandSpeed(GameTestHelper helper, long placing, long longestChunk, int chunks) {
        RandomSource random = RandomSource.create(7);
        long shaping = 0, longestShape = 0;
        int trees = 0;
        String slowest = "";
        for (String size : new String[] {"big", "young"}) {
            for (int i = 0; i < 30; i++) {
                var pick = com.antcolonies.savanna.worldgen.GiantTreeStructure.pick(size, random);
                if (com.antcolonies.savanna.worldgen.GiantTreePiece.fallenYoung(pick)) {
                    continue;
                }
                long start = System.nanoTime();
                TreePlacer.placed(pick.kind(), pick.seed() + 1_000_000_007L * (i + 1), pick.age(), pick.stage()); // a seed no other test has worked out
                long took = System.nanoTime() - start;
                shaping += took;
                trees++;
                if (took > longestShape) {
                    longestShape = took;
                    slowest = pick.kind() + " age " + pick.age() + " " + pick.stage();
                }
            }
        }
        double shapeMs = shaping / 1e6 / trees, chunkMs = placing / 1e6 / chunks;
        SavannaMod.LOGGER.info("[trees speed] {} new-land trees worked out, {} ms each, longest {} ms ({}); a grown camel thorn put down in {} chunks, {} ms each, longest {} ms",
                trees, String.format("%.1f", shapeMs), String.format("%.1f", longestShape / 1e6), slowest, chunks,
                String.format("%.2f", chunkMs), String.format("%.1f", longestChunk / 1e6));
        if (shapeMs > 500) {
            helper.fail("new land's trees take " + String.format("%.0f", shapeMs) + " ms each to work out (the slowest " + slowest + ")");
        }
        if (chunkMs > 100) {
            helper.fail("putting a grown tree down takes " + String.format("%.0f", chunkMs) + " ms a chunk");
        }
    }

    /**
     * Step 6c: the dead wood new land lays round its trees. Under the grown camel thorn lie fallen branches, on the land
     * (the pit's floor too), never in the trunk; old trees often have a fallen giant limb, baobabs never; a fallen young
     * tree is dead wood only, with its stem.
     */
    private static void newLandLitter(GameTestHelper helper, BlockPos foot) {
        var level = helper.getLevel();
        var pick = new com.antcolonies.savanna.worldgen.GiantTreeStructure.Pick("camel_thorn", 5, 1, TreeStage.LIVING);
        TreePlacer.Placed litter = com.antcolonies.savanna.worldgen.GiantTreePiece.litter(pick, foot, TreePlacer.ground(level, foot));
        int branch = 0;
        for (int i = 0; i < litter.size(); i++) {
            branch += litter.states()[i].getBlock() instanceof FallenBranchBlock ? 1 : 0;
        }
        if (branch < 6 || branch > 40) {
            helper.fail("new land put " + branch + " blocks of fallen branch under a grown camel thorn (3 to 6 branches 2 or 3 long, more with a limb)");
        }
        int placed = TreePlacer.place(level, litter, foot, litter.box(foot), Block.UPDATE_CLIENTS);
        if (placed == 0) {
            BlockPos first = foot.offset(litter.xs()[0], litter.ys()[0], litter.zs()[0]);
            helper.fail("none of the " + litter.size() + " blocks of dead wood under the camel thorn was put down; the first, "
                    + litter.states()[0] + " at " + litter.xs()[0] + "," + litter.ys()[0] + "," + litter.zs()[0] + ", finds "
                    + level.getBlockState(first) + " over " + level.getBlockState(first.below()));
        }
        for (int i = 0; i < litter.size(); i++) {
            BlockPos pos = foot.offset(litter.xs()[i], litter.ys()[i], litter.zs()[i]);
            BlockState there = level.getBlockState(pos);
            if (there.getBlock() instanceof FallenBranchBlock && !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                helper.fail("a fallen branch of new land floats at " + pos);
            }
        }
        int limbs = 0;
        for (long seed = 0; seed < 40; seed++) {
            if (hasDeadWood(TreePlacer.litter(com.antcolonies.savanna.tree.Litter.underTree(seed, 1.25, 20, 2, false)))) {
                limbs++;
            }
            if (hasDeadWood(TreePlacer.litter(com.antcolonies.savanna.tree.Litter.underTree(seed, 1.25, 20, 2, true)))) {
                helper.fail("a baobab of new land has a fallen giant limb (seed " + seed + ")");
            }
        }
        if (limbs < 20 || limbs == 40) {
            helper.fail(limbs + " of 40 ancient trees have a fallen giant limb under them (most, not all)");
        }
        var young = new com.antcolonies.savanna.worldgen.GiantTreeStructure.Pick("umbrella_thorn", 3, 0.3, TreeStage.FALLEN);
        if (!com.antcolonies.savanna.worldgen.GiantTreePiece.fallenYoung(young)
                || com.antcolonies.savanna.worldgen.GiantTreePiece.fallenYoung(new com.antcolonies.savanna.worldgen.GiantTreeStructure.Pick("umbrella_thorn", 3, 1, TreeStage.FALLEN))) {
            helper.fail("a fallen young tree of new land is not told from a fallen giant");
        }
        if (!hasDeadWood(com.antcolonies.savanna.worldgen.GiantTreePiece.litter(young, foot, (x, z) -> foot.getY()))) {
            helper.fail("a fallen young tree of new land has no stem");
        }
    }

    /** Whether some of the dead wood is more than fallen branches (a limb or a stem). */
    private static boolean hasDeadWood(TreePlacer.Placed placed) {
        for (int i = 0; i < placed.size(); i++) {
            if (!(placed.states()[i].getBlock() instanceof FallenBranchBlock)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A weathered dead umbrella thorn (the "mud" stage) has no termite mud of its own (the user, 2026-11-13: mud is the
     * sign termites were there); mud plastered over its dead bark, as termites put it there, stays however long it
     * ticks: it is over wood the termites eat, so it is still needed.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void termiteMudOnADeadTreeStays(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos foot = helper.absolutePos(new BlockPos(TREE_MIDDLE, SURFACE, TREE_MIDDLE));
        TreePlacer.Placed tree = TreePlacer.placed("umbrella_thorn", 3, 1, TreeStage.MUD);
        TreePlacer.place(level, tree, foot, tree.box(foot), Block.UPDATE_CLIENTS);
        int made = 0;
        for (BlockPos pos : BlockPos.betweenClosed(foot.offset(-30, -2, -30), foot.offset(30, 40, 30))) {
            made += MudCrustBlock.isCrust(level.getBlockState(pos)) ? 1 : 0;
        }
        if (made > 0) {
            helper.fail(made + " mud plates on a dead tree no termite fed on");
        }
        // Termites plaster its trunk: a plate on every open side of the dead wood low down.
        List<BlockPos> crusts = new ArrayList<>();
        for (int i = 0; i < tree.size(); i++) {
            BlockPos pos = foot.offset(tree.xs()[i], tree.ys()[i], tree.zs()[i]);
            if (tree.ys()[i] < 1 || tree.ys()[i] > 6 || !level.getBlockState(pos).is(TreeBlocks.TERMITE_WOOD)) {
                continue;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos out = pos.relative(d);
                if (level.getBlockState(out).isAir()) {
                    level.setBlock(out, SavannaBlocks.MUD_CRUST.get().withFace(Blocks.AIR.defaultBlockState(), d.getOpposite()), Block.UPDATE_CLIENTS);
                    crusts.add(out.immutable());
                }
            }
        }
        if (crusts.size() < 20) {
            helper.fail("only " + crusts.size() + " mud plates put on the dead tree's trunk");
        }
        for (BlockPos pos : crusts) {
            boolean onWood = false;
            BlockState crust = level.getBlockState(pos);
            for (Direction d : Direction.values()) {
                if (crust.getValue(MudCrustBlock.FACES.get(d)) && level.getBlockState(pos.relative(d)).is(TreeBlocks.TERMITE_WOOD)) {
                    onWood = true;
                }
            }
            if (!onWood) {
                helper.fail("mud plate at " + pos + " not on dead wood: " + crust);
            }
        }
        var random = level.getRandom();
        for (int round = 0; round < 40; round++) {
            for (BlockPos pos : crusts) {
                BlockState state = level.getBlockState(pos);
                if (state.is(SavannaBlocks.MUD_CRUST.get())) {
                    state.randomTick(level, pos, random);
                }
            }
        }
        int gone = 0;
        for (BlockPos pos : crusts) {
            gone += MudCrustBlock.isCrust(level.getBlockState(pos)) ? 0 : 1;
        }
        if (gone > 0) {
            helper.fail(gone + " of " + crusts.size() + " mud plates crumbled off the dead tree");
        }
        helper.succeed();
    }
}
