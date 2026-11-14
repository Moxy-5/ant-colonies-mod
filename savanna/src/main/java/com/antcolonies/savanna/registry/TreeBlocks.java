package com.antcolonies.savanna.registry;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.BleachedBranchBlock;
import com.antcolonies.savanna.block.BracketFungusBlock;
import com.antcolonies.savanna.block.DeadTreeWoodBlock;
import com.antcolonies.savanna.block.DoumLeafBaseBlock;
import com.antcolonies.savanna.block.DoumPiece;
import com.antcolonies.savanna.block.DoumPieceBlock;
import com.antcolonies.savanna.block.DoumPlantBlock;
import com.antcolonies.savanna.block.DoumRootBlock;
import com.antcolonies.savanna.block.DoumWoodBlock;
import com.antcolonies.savanna.block.FigBranchBlock;
import com.antcolonies.savanna.block.FigLeavesBlock;
import com.antcolonies.savanna.block.FigRootBlock;
import com.antcolonies.savanna.block.FigWoodBlock;
import com.antcolonies.savanna.block.TrunkFigsBlock;
import com.antcolonies.savanna.block.FallenFruitBlock;
import com.antcolonies.savanna.block.FigBunchBlock;
import com.antcolonies.savanna.block.HangingFruit;
import com.antcolonies.savanna.block.FrassBlock;
import com.antcolonies.savanna.block.PulpHeapBlock;
import com.antcolonies.savanna.block.HangingPodsBlock;
import com.antcolonies.savanna.block.SoftDeadWoodBlock;
import com.antcolonies.savanna.block.TaperingBranchBlock;
import com.antcolonies.savanna.block.ThornyTwigBlock;
import com.antcolonies.savanna.block.TreeBranchBlock;
import com.antcolonies.savanna.block.TreeLeavesBlock;
import com.antcolonies.savanna.block.TreeSeedlingBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The blocks of the giant savanna trees (M7.5 step 1, docs/plans/m7.5-giant-trees.md): for each kind (umbrella thorn,
 * camel thorn, baobab) its log, wood (bark on every side), thin branch, leaves and seedling; and the shared ones: thorny
 * twigs, camel thorn pods, the dead tree's bleached silver wood and peeling bark, rotten wood, frass and the baobab's
 * fibre heap. Their looks are the user's picks in the tree viewer (tools/tree_art.py writes their files). Nothing
 * places them in the world yet: that is the shape maker (step 2) and new land (step 3).
 */
public final class TreeBlocks {
    /** Every block here, in creative tab order (each gets an item, see ModItems). */
    public static final List<DeferredBlock<? extends Block>> ALL = new ArrayList<>();

    /** Tree wood a branch joins: every log, wood and branch here. */
    public static final TagKey<Block> TREE_WOOD = tag("tree_wood");
    /** Every tree's leaves here. */
    public static final TagKey<Block> TREE_LEAVES = tag("tree_leaves");
    /** Dead tree wood termites may eat (wired into the termites' food in M7.5 step 6). */
    public static final TagKey<Block> TERMITE_WOOD = tag("termite_wood");

    /** One kind of tree's own blocks. */
    public record Kind(String name, DeferredBlock<RotatedPillarBlock> log, DeferredBlock<RotatedPillarBlock> wood,
                       DeferredBlock<TreeBranchBlock> branch, DeferredBlock<TreeLeavesBlock> leaves,
                       DeferredBlock<TreeSeedlingBlock> seedling, DeferredBlock<TreeLeavesBlock> deadLeaves) {
    }

    // Real wood: umbrella and camel thorn wood is very hard (camel thorn the hardest); a baobab's is soft, spongy and watery.
    // The two acacias' branches taper as the player builds them out (TaperingBranchBlock); the baobab's stay one size.
    public static final Kind UMBRELLA_THORN = kind("umbrella_thorn", MapColor.COLOR_GRAY, 2.0F, MapColor.PLANT, true);
    public static final Kind CAMEL_THORN = kind("camel_thorn", MapColor.TERRACOTTA_GRAY, 2.5F, MapColor.TERRACOTTA_GREEN, true);
    public static final Kind BAOBAB = kind("baobab", MapColor.TERRACOTTA_LIGHT_GRAY, 1.0F, MapColor.COLOR_GREEN, false);
    public static final List<Kind> KINDS = List.of(UMBRELLA_THORN, CAMEL_THORN, BAOBAB);

    /**
     * Planks sawn from the trees' wood (the user, 2026-11-11, look A of tools/plank_art.py for both): the camel thorn's
     * dark red-brown boards, hard like its wood, and the baobab's pale soft ones. The umbrella thorn's logs make
     * Minecraft's own acacia planks (the user's pick: so acacia planks stay in the game), so it has no planks block here.
     */
    public static final DeferredBlock<Block> CAMEL_THORN_PLANKS = add(SavannaBlocks.BLOCKS.registerBlock("camel_thorn_planks", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).strength(2.5F, 3.0F).sound(SoundType.WOOD).ignitedByLava()));
    /** What camel thorn planks make (the user, 2026-11-11: "all the rest of things"), as Minecraft's woods. */
    public static final WoodSet CAMEL_THORN_SET = woodSet("camel_thorn", CAMEL_THORN_PLANKS, MapColor.TERRACOTTA_RED, 2.5F);
    public static final DeferredBlock<Block> BAOBAB_PLANKS = add(SavannaBlocks.BLOCKS.registerBlock("baobab_planks", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.0F, 2.0F).sound(SoundType.WOOD).ignitedByLava()));
    public static final WoodSet BAOBAB_SET = woodSet("baobab", BAOBAB_PLANKS, MapColor.SAND, 1.0F);
    public static final List<WoodSet> WOOD_SETS = List.of(CAMEL_THORN_SET, BAOBAB_SET);

    /**
     * The things a kind's planks are made into, each as Minecraft's own wooden one. The signs are not in {@link #ALL}:
     * a sign item places a standing or a wall sign, so their items are made by hand in {@link SavannaItems}.
     * The boats are in {@link SavannaBoats}.
     */
    public record WoodSet(String name, WoodType type, DeferredBlock<Block> planks, DeferredBlock<StairBlock> stairs, DeferredBlock<SlabBlock> slab,
                          DeferredBlock<FenceBlock> fence, DeferredBlock<FenceGateBlock> fenceGate, DeferredBlock<DoorBlock> door,
                          DeferredBlock<TrapDoorBlock> trapdoor, DeferredBlock<PressurePlateBlock> pressurePlate,
                          DeferredBlock<ButtonBlock> button, DeferredBlock<StandingSignBlock> sign, DeferredBlock<WallSignBlock> wallSign,
                          DeferredBlock<CeilingHangingSignBlock> hangingSign, DeferredBlock<WallHangingSignBlock> wallHangingSign) {
        /** The blocks that burn like Minecraft's wooden ones (doors, trapdoors, plates and buttons do not burn there either). */
        public List<DeferredBlock<? extends Block>> burning() {
            return List.of(planks, stairs, slab, fence, fenceGate);
        }
    }

    /** Thorny acacia twigs at the crown's edge and on young trees: slow and prick anything big, never insects. */
    public static final DeferredBlock<ThornyTwigBlock> THORNY_TWIG = add(SavannaBlocks.BLOCKS.registerBlock("thorny_twig", ThornyTwigBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2F).sound(SoundType.AZALEA).noCollission().noOcclusion()
                    .ignitedByLava().pushReaction(PushReaction.DESTROY)));
    /** Camel thorn pods hanging under the crown (looks only for now). */
    public static final DeferredBlock<HangingPodsBlock> CAMEL_THORN_PODS = add(SavannaBlocks.BLOCKS.registerBlock("camel_thorn_pods", HangingPodsBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).strength(0.2F).sound(SoundType.AZALEA_LEAVES)
                    .noCollission().noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY)));

    /** A dead tree's trunk with the bark weathered to silver (the user's pick, look E); termites eat it in stages (step 6d). */
    public static final DeferredBlock<DeadTreeWoodBlock> BLEACHED_LOG = add(SavannaBlocks.BLOCKS.registerBlock("bleached_log", DeadTreeWoodBlock::new, deadWood()));
    public static final DeferredBlock<DeadTreeWoodBlock> BLEACHED_WOOD = add(SavannaBlocks.BLOCKS.registerBlock("bleached_wood", DeadTreeWoodBlock::new, deadWood()));
    /** A dead trunk with the bark lifting off in strips. */
    public static final DeferredBlock<DeadTreeWoodBlock> PEELING_BLEACHED_LOG = add(SavannaBlocks.BLOCKS.registerBlock("peeling_bleached_log", DeadTreeWoodBlock::new, deadWood()));
    public static final DeferredBlock<BleachedBranchBlock> BLEACHED_BRANCH = add(SavannaBlocks.BLOCKS.registerBlock("bleached_branch", BleachedBranchBlock::new,
            branchProperties(MapColor.COLOR_LIGHT_GRAY, 1.0F)));
    /**
     * Bark burnt black (step 5d): a fire's scar on a living tree, and the whole of a tree killed by fire. Charcoal does
     * not burn away again.
     */
    public static final DeferredBlock<RotatedPillarBlock> CHARRED_LOG = add(pillar("charred_log", wood(MapColor.COLOR_BLACK, 1.5F)));
    public static final DeferredBlock<RotatedPillarBlock> CHARRED_WOOD = add(pillar("charred_wood", wood(MapColor.COLOR_BLACK, 1.5F)));
    public static final DeferredBlock<TreeBranchBlock> CHARRED_BRANCH = add(SavannaBlocks.BLOCKS.registerBlock("charred_branch", TreeBranchBlock::new,
            branchProperties(MapColor.COLOR_BLACK, 0.8F)));
    /** Heart-rot: soft crumbly wood inside an old trunk; insects dig it fast. */
    public static final DeferredBlock<SoftDeadWoodBlock> ROTTEN_WOOD = add(SavannaBlocks.BLOCKS.registerBlock("rotten_wood", SoftDeadWoodBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.4F).sound(SoundType.WOOD).ignitedByLava()));
    /** Wood dust and droppings, a thin layer on the floor of hollows and galleries. */
    public static final DeferredBlock<FrassBlock> FRASS = add(SavannaBlocks.BLOCKS.registerBlock("frass", FrassBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.1F).sound(SoundType.SAND).ignitedByLava()
                    .pushReaction(PushReaction.DESTROY)));
    /**
     * Dead wood for the raids, part 4: the shelf of a heart rot fungus on the bark of a tree rotting inside (its look my
     * pick: a <i>Ganoderma</i> shelf, banded brown on top with a pale growing rim, cream pores underneath).
     */
    public static final DeferredBlock<BracketFungusBlock> BRACKET_FUNGUS = add(SavannaBlocks.BLOCKS.registerBlock("bracket_fungus", BracketFungusBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.6F).sound(SoundType.WOOD).noCollission()
                    .noOcclusion().pushReaction(PushReaction.DESTROY)));
    /**
     * Heart rot: the crumbly rotten heartwood spilled where a hollow tree snapped, a heap in layers like snow that
     * termites eat and that sinks into the soil ({@code block/PulpHeapBlock}).
     */
    public static final DeferredBlock<PulpHeapBlock> PULP_HEAP = add(SavannaBlocks.BLOCKS.registerBlock("pulp_heap", PulpHeapBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.2F).sound(SoundType.ROOTED_DIRT).randomTicks()
                    .pushReaction(PushReaction.DESTROY)));
    /**
     * Heart rot marks on the bark (the looks picked by the user 2026-10-26, tools/rot_looks_art.py), one pair for each bark
     * a rotting acacia can have, on its wood (the trunk's outside, bark all round): spore-dusted, the bark under a grown bracket stained cocoa-brown by its spores; and the
     * wound, the sunken scar where the rot got in, beaded with amber gum on a living tree. They are the same wood as the
     * plain wood block ({@code tree/RotMarks}) and keep their mark as the tree dies or burns.
     */
    public static final List<RotBark> ROT_BARKS = List.of(
            rotBark(UMBRELLA_THORN.wood(), "umbrella_thorn_wood", () -> wood(MapColor.COLOR_GRAY, 2.0F), RotatedPillarBlock::new),
            rotBark(CAMEL_THORN.wood(), "camel_thorn_wood", () -> wood(MapColor.TERRACOTTA_GRAY, 2.5F), RotatedPillarBlock::new),
            rotBark(BLEACHED_WOOD, "bleached_wood", TreeBlocks::deadWood, DeadTreeWoodBlock::new),
            rotBark(CHARRED_WOOD, "charred_wood", () -> wood(MapColor.COLOR_BLACK, 1.5F), RotatedPillarBlock::new));

    /** A plain wood block (the trunk's bark) and its two heart rot marks. */
    public record RotBark(DeferredBlock<? extends RotatedPillarBlock> base, DeferredBlock<? extends RotatedPillarBlock> spores,
                          DeferredBlock<? extends RotatedPillarBlock> wound) {
    }

    /** What a dead baobab slumps into: a soggy heap of fibres (it leaves no log). */
    public static final DeferredBlock<SoftDeadWoodBlock> BAOBAB_FIBRE = add(SavannaBlocks.BLOCKS.registerBlock("baobab_fibre", SoftDeadWoodBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.4F).sound(SoundType.WET_GRASS)));

    /*
     * The sycamore fig (floodplain trees step 3, 2026-11-13, docs/plans/floodplain-trees.md; shape B and bark A, the
     * user's picks in the model viewer; textures from tools/floodplain_tree_art.py). Its own blocks, not a Kind: the
     * dry trees' systems that walk KINDS (the tree book, fire, rot, new land) stay as they are until the fig's own steps.
     * Soft, light wood (my pick: a little softer than the umbrella thorn's).
     */
    /** The fig's log, turned by how it was placed; its bark one of {@link com.antcolonies.savanna.block.FigBark}. */
    public static final DeferredBlock<FigWoodBlock> FIG_LOG = add(SavannaBlocks.BLOCKS.registerBlock("fig_log", FigWoodBlock::new,
            wood(MapColor.SAND, 1.5F)));
    /** The fig's wood, bark all round: what a grown fig's fluted trunk and thick limbs are built of. */
    public static final DeferredBlock<FigWoodBlock> FIG_WOOD = add(SavannaBlocks.BLOCKS.registerBlock("fig_wood", FigWoodBlock::new,
            wood(MapColor.SAND, 1.5F)));
    /** The fig's thinner limbs, tapering through six thicknesses. */
    public static final DeferredBlock<FigBranchBlock> FIG_BRANCH = add(SavannaBlocks.BLOCKS.registerBlock("fig_branch", FigBranchBlock::new,
            branchProperties(MapColor.SAND, 0.75F)));
    /** The root steps at the foot of the buttresses. */
    public static final DeferredBlock<FigRootBlock> FIG_ROOT = add(SavannaBlocks.BLOCKS.registerBlock("fig_root", FigRootBlock::new,
            wood(MapColor.SAND, 1.5F)));
    /** The fig's leaves, in three tones. */
    public static final DeferredBlock<FigLeavesBlock> FIG_LEAVES = add(SavannaBlocks.BLOCKS.registerBlock("fig_leaves", FigLeavesBlock::new,
            leaves(MapColor.PLANT)));
    /** A spray of leaves hanging under a leaf pad. */
    public static final DeferredBlock<HangingPodsBlock> FIG_LEAF_SPRAY = add(SavannaBlocks.BLOCKS.registerBlock("fig_leaf_spray", HangingPodsBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.1F).sound(SoundType.AZALEA_LEAVES).noCollission()
                    .noOcclusion().replaceable().ignitedByLava().pushReaction(PushReaction.DESTROY)));
    /** A bunch of figs hanging under a limb, ripening in stages and dropping its figs (step 4b). */
    public static final DeferredBlock<FigBunchBlock> FIG_BUNCH = add(SavannaBlocks.BLOCKS.registerBlock("fig_bunch", FigBunchBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.2F).sound(SoundType.AZALEA_LEAVES)
                    .noCollission().noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY)));
    /** Figs growing straight on the trunk's bark, ripening like a bunch (step 4b). */
    public static final DeferredBlock<TrunkFigsBlock> TRUNK_FIGS = add(SavannaBlocks.BLOCKS.registerBlock("trunk_figs", TrunkFigsBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.2F).sound(SoundType.AZALEA_LEAVES)
                    .noCollission().noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY)));
    /** Ripe figs fallen under a fig, rotting in about 2 days (step 4b). */
    public static final DeferredBlock<FallenFruitBlock> FALLEN_FIGS = add(SavannaBlocks.BLOCKS.registerBlock("fallen_figs",
            p -> new FallenFruitBlock(HangingFruit.Fruit.FIG, p), BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.1F)
                    .sound(SoundType.AZALEA_LEAVES).noCollission().noOcclusion().pushReaction(PushReaction.DESTROY)));
    /** A fig seedling (a player's does not grow yet: growing in play is step 8). */
    public static final DeferredBlock<TreeSeedlingBlock> FIG_SEEDLING = add(SavannaBlocks.BLOCKS.registerBlock("fig_seedling", TreeSeedlingBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS)
                    .pushReaction(PushReaction.DESTROY)));

    /*
     * The doum palm (floodplain trees step 5, 2026-11-14; shape B and bark A, the user's picks in the model viewer).
     * Stems are plain blocks; a crown, a nut bunch, the seedling and the rosette are each one block drawing the viewer's
     * whole sculpted piece from its recipe (block/DoumLook, the client's DoumModel), as fronds that overlap a dozen deep
     * cannot be cut into blocks. Hard, fibrous stems (my pick: a little harder than the fig).
     */
    /** The doum's stem as a log, living or dead grey ({@link DoumWoodBlock#DEAD}). */
    public static final DeferredBlock<DoumWoodBlock> DOUM_LOG = add(SavannaBlocks.BLOCKS.registerBlock("doum_log", DoumWoodBlock::new,
            wood(MapColor.TERRACOTTA_BROWN, 2.0F)));
    /** The doum's stem, bark all round: what a palm's two-block-wide stems are built of. */
    public static final DeferredBlock<DoumWoodBlock> DOUM_WOOD = add(SavannaBlocks.BLOCKS.registerBlock("doum_wood", DoumWoodBlock::new,
            wood(MapColor.TERRACOTTA_BROWN, 2.0F)));
    /** A crown of fan fronds round its spear leaf, on top of a stem; only its cap is solid. */
    public static final DeferredBlock<DoumPieceBlock> DOUM_CROWN = add(SavannaBlocks.BLOCKS.registerBlock("doum_crown",
            p -> new DoumPieceBlock(DoumPiece.Piece.CROWN, p), BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.6F)
                    .sound(SoundType.AZALEA_LEAVES).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY)));
    /** A bunch of doum nuts hanging under a crown, ripening in stages and dropping its nuts (step 6b). */
    public static final DeferredBlock<DoumPieceBlock> DOUM_NUT_BUNCH = add(SavannaBlocks.BLOCKS.registerBlock("doum_nut_bunch",
            p -> new DoumPieceBlock(DoumPiece.Piece.BUNCH, p), BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE)
                    .strength(0.3F).sound(SoundType.WOOD).noCollission().noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY)));
    /** Ripe doum nuts fallen under a palm, lasting about 20 days (step 6b). */
    public static final DeferredBlock<FallenFruitBlock> FALLEN_DOUM_NUTS = add(SavannaBlocks.BLOCKS.registerBlock("fallen_doum_nuts",
            p -> new FallenFruitBlock(HangingFruit.Fruit.DOUM, p), BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE)
                    .strength(0.2F).sound(SoundType.WOOD).noCollission().noOcclusion().pushReaction(PushReaction.DESTROY)));
    /** A doum seedling (a player's does not grow yet: growing in play is step 8). */
    public static final DeferredBlock<DoumPlantBlock> DOUM_SEEDLING = add(SavannaBlocks.BLOCKS.registerBlock("doum_seedling",
            p -> new DoumPlantBlock(DoumPiece.Piece.SEEDLING, p), BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission()
                    .instabreak().sound(SoundType.GRASS).noOcclusion().pushReaction(PushReaction.DESTROY)));
    /** A young doum with no stem yet: fans straight out of the ground. */
    public static final DeferredBlock<DoumPlantBlock> DOUM_ROSETTE = add(SavannaBlocks.BLOCKS.registerBlock("doum_rosette",
            p -> new DoumPlantBlock(DoumPiece.Piece.ROSETTE, p), BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission()
                    .strength(0.2F).sound(SoundType.AZALEA_LEAVES).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY)));
    /** An old leaf's base left on the stem's side. */
    public static final DeferredBlock<DoumLeafBaseBlock> DOUM_LEAF_BASE = add(SavannaBlocks.BLOCKS.registerBlock("doum_leaf_base",
            DoumLeafBaseBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.3F).sound(SoundType.WOOD)
                    .noCollission().noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY)));

    /** The root steps at the clump's swollen foot, living or dead grey (step 6). */
    public static final DeferredBlock<DoumRootBlock> DOUM_ROOT = add(SavannaBlocks.BLOCKS.registerBlock("doum_root", DoumRootBlock::new,
            wood(MapColor.TERRACOTTA_BROWN, 2.0F).noOcclusion()));
    /** An old frond lying on the ground round a clump, brown or bleached grey (step 6). */
    public static final DeferredBlock<DoumPieceBlock> DOUM_FRONDS = add(SavannaBlocks.BLOCKS.registerBlock("doum_fronds",
            p -> new DoumPieceBlock(DoumPiece.Piece.FRONDS, p), BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN)
                    .strength(0.2F).sound(SoundType.AZALEA_LEAVES).noCollission().noOcclusion().ignitedByLava()
                    .pushReaction(PushReaction.DESTROY)));

    private TreeBlocks() {
    }

    /** Called by ModBlocks so these blocks are registered with the rest. */
    static void init() {
    }

    private static Kind kind(String name, MapColor bark, float hardness, MapColor leafColour, boolean tapering) {
        DeferredBlock<RotatedPillarBlock> log = add(pillar(name + "_log", wood(bark, hardness)));
        DeferredBlock<RotatedPillarBlock> wood = add(pillar(name + "_wood", wood(bark, hardness)));
        DeferredBlock<TreeBranchBlock> branch = add(SavannaBlocks.BLOCKS.<TreeBranchBlock>registerBlock(name + "_branch",
                tapering ? TaperingBranchBlock::new : TreeBranchBlock::new,
                branchProperties(bark, hardness / 2)));
        DeferredBlock<TreeLeavesBlock> leaves = add(SavannaBlocks.BLOCKS.registerBlock(name + "_leaves", TreeLeavesBlock::new,
                leaves(leafColour)));
        // A dead tree's withered leaves (step 5b): brown, dry, they drop within a day.
        DeferredBlock<TreeLeavesBlock> deadLeaves = add(SavannaBlocks.BLOCKS.registerBlock(name + "_dead_leaves", TreeLeavesBlock::new,
                leaves(MapColor.TERRACOTTA_BROWN)));
        DeferredBlock<TreeSeedlingBlock> seedling = add(SavannaBlocks.BLOCKS.registerBlock(name + "_seedling", TreeSeedlingBlock::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS)
                        .pushReaction(PushReaction.DESTROY)));
        return new Kind(name, log, wood, branch, leaves, seedling, deadLeaves);
    }

    private static WoodSet woodSet(String name, DeferredBlock<Block> planks, MapColor colour, float hardness) {
        BlockSetType set = BlockSetType.register(new BlockSetType(SavannaMod.MODID + ":" + name));
        WoodType type = WoodType.register(new WoodType(SavannaMod.MODID + ":" + name, set));
        java.util.function.Supplier<BlockBehaviour.Properties> solid = () -> BlockBehaviour.Properties.of().mapColor(colour)
                .strength(hardness, 3.0F).sound(SoundType.WOOD).ignitedByLava();
        var stairs = add(SavannaBlocks.BLOCKS.registerBlock(name + "_stairs",
                p -> new StairBlock(planks.get().defaultBlockState(), p), solid.get()));
        var slab = add(SavannaBlocks.BLOCKS.registerBlock(name + "_slab", SlabBlock::new, solid.get()));
        var fence = add(SavannaBlocks.BLOCKS.registerBlock(name + "_fence", FenceBlock::new, solid.get()));
        var gate = add(SavannaBlocks.BLOCKS.registerBlock(name + "_fence_gate", p -> new FenceGateBlock(type, p), solid.get()));
        var door = add(SavannaBlocks.BLOCKS.registerBlock(name + "_door", p -> new DoorBlock(set, p),
                BlockBehaviour.Properties.of().mapColor(colour).strength(3.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava()
                        .pushReaction(PushReaction.DESTROY)));
        var trapdoor = add(SavannaBlocks.BLOCKS.registerBlock(name + "_trapdoor", p -> new TrapDoorBlock(set, p),
                BlockBehaviour.Properties.of().mapColor(colour).strength(3.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava()
                        .isValidSpawn((state, level, pos, entity) -> false)));
        var plate = add(SavannaBlocks.BLOCKS.registerBlock(name + "_pressure_plate", p -> new PressurePlateBlock(set, p),
                BlockBehaviour.Properties.of().mapColor(colour).noCollission().strength(0.5F).sound(SoundType.WOOD).ignitedByLava()
                        .pushReaction(PushReaction.DESTROY)));
        var button = add(SavannaBlocks.BLOCKS.registerBlock(name + "_button", p -> new ButtonBlock(set, 30, p),
                BlockBehaviour.Properties.of().noCollission().strength(0.5F).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY)));
        // Signs as Minecraft's: the wall ones drop the standing or hanging sign.
        java.util.function.Supplier<BlockBehaviour.Properties> signs = () -> BlockBehaviour.Properties.of().mapColor(colour)
                .forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollission().strength(1.0F).ignitedByLava();
        var sign = SavannaBlocks.BLOCKS.registerBlock(name + "_sign", p -> new StandingSignBlock(type, p), signs.get());
        var wallSign = SavannaBlocks.BLOCKS.registerBlock(name + "_wall_sign",
                p -> new WallSignBlock(type, p.dropsLike(sign.get())), signs.get());
        var hanging = SavannaBlocks.BLOCKS.registerBlock(name + "_hanging_sign", p -> new CeilingHangingSignBlock(type, p), signs.get());
        var wallHanging = SavannaBlocks.BLOCKS.registerBlock(name + "_wall_hanging_sign",
                p -> new WallHangingSignBlock(type, p.dropsLike(hanging.get())), signs.get());
        return new WoodSet(name, type, planks, stairs, slab, fence, gate, door, trapdoor, plate, button, sign, wallSign, hanging, wallHanging);
    }

    private static RotBark rotBark(DeferredBlock<? extends RotatedPillarBlock> base, String name,
                                   java.util.function.Supplier<BlockBehaviour.Properties> properties,
                                   java.util.function.Function<BlockBehaviour.Properties, ? extends RotatedPillarBlock> make) {
        return new RotBark(base, add(SavannaBlocks.BLOCKS.registerBlock(name + "_spores", make, properties.get())),
                add(SavannaBlocks.BLOCKS.registerBlock(name + "_wound", make, properties.get())));
    }

    private static BlockBehaviour.Properties leaves(MapColor colour) {
        return BlockBehaviour.Properties.of().mapColor(colour).strength(0.2F).sound(SoundType.GRASS).noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false).isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false).isRedstoneConductor((state, level, pos) -> false)
                .ignitedByLava().pushReaction(PushReaction.DESTROY);
    }

    private static DeferredBlock<RotatedPillarBlock> pillar(String name, BlockBehaviour.Properties properties) {
        return SavannaBlocks.BLOCKS.registerBlock(name, RotatedPillarBlock::new, properties);
    }

    private static BlockBehaviour.Properties wood(MapColor colour, float hardness) {
        return BlockBehaviour.Properties.of().mapColor(colour).strength(hardness).sound(SoundType.WOOD).ignitedByLava();
    }

    private static BlockBehaviour.Properties deadWood() {
        return wood(MapColor.COLOR_LIGHT_GRAY, 1.5F);
    }

    private static BlockBehaviour.Properties branchProperties(MapColor colour, float hardness) {
        return BlockBehaviour.Properties.of().mapColor(colour).strength(hardness).sound(SoundType.WOOD).noOcclusion().ignitedByLava()
                .pushReaction(PushReaction.DESTROY);
    }

    private static <T extends Block> DeferredBlock<T> add(DeferredBlock<T> block) {
        ALL.add(block);
        return block;
    }

    private static TagKey<Block> tag(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, name));
    }

    /**
     * How easily each block catches fire and burns (the first number: how much it feeds a fire next to it; the second:
     * how likely it burns away). Step 5d: a giant tree's wood and green leaves never burn away (0), they char and scorch
     * instead ({@code tree/TreeFire}); withered leaves, twigs, pods and frass still burn like Minecraft's.
     */
    public static void setFlammable() {
        FireBlock fire = (FireBlock) Blocks.FIRE;
        for (Kind kind : KINDS) {
            fire.setFlammable(kind.log().get(), 5, 0);
            fire.setFlammable(kind.wood().get(), 5, 0);
            fire.setFlammable(kind.branch().get(), 5, 0);
            fire.setFlammable(kind.leaves().get(), 30, 0);
            fire.setFlammable(kind.deadLeaves().get(), 60, 100);
        }
        // Planks burn like Minecraft's own (sawn boards burn away; only a living tree chars instead).
        for (WoodSet set : WOOD_SETS) {
            for (DeferredBlock<? extends Block> block : set.burning()) {
                fire.setFlammable(block.get(), 5, 20);
            }
        }
        fire.setFlammable(THORNY_TWIG.get(), 60, 100);
        fire.setFlammable(CAMEL_THORN_PODS.get(), 30, 60);
        for (DeferredBlock<? extends Block> dead : List.of(BLEACHED_LOG, BLEACHED_WOOD, PEELING_BLEACHED_LOG, BLEACHED_BRANCH, ROTTEN_WOOD)) {
            fire.setFlammable(dead.get(), 10, 0);
        }
        for (DeferredBlock<? extends Block> charred : List.of(CHARRED_LOG, CHARRED_WOOD, CHARRED_BRANCH)) {
            fire.setFlammable(charred.get(), 5, 0);
        }
        for (RotBark r : ROT_BARKS) {
            // Burn like the plain log they mark.
            int feeds = r.base() == BLEACHED_LOG ? 10 : 5;
            fire.setFlammable(r.spores().get(), feeds, 0);
            fire.setFlammable(r.wound().get(), feeds, 0);
        }
        fire.setFlammable(FRASS.get(), 60, 100);
        fire.setFlammable(PULP_HEAP.get(), 30, 60);
        fire.setFlammable(BAOBAB_FIBRE.get(), 5, 5);
        // The fig burns like Minecraft's own trees until its life in play (step 8) gives it its own fire rules.
        for (DeferredBlock<? extends Block> wood : List.of(FIG_LOG, FIG_WOOD, FIG_BRANCH, FIG_ROOT)) {
            fire.setFlammable(wood.get(), 5, 5);
        }
        fire.setFlammable(FIG_LEAVES.get(), 30, 60);
        fire.setFlammable(FIG_LEAF_SPRAY.get(), 60, 100);
        fire.setFlammable(FIG_BUNCH.get(), 30, 60);
        fire.setFlammable(TRUNK_FIGS.get(), 30, 60);
        fire.setFlammable(FALLEN_FIGS.get(), 30, 60);
        fire.setFlammable(FALLEN_DOUM_NUTS.get(), 30, 60);
        // The doum the same, its dry fronds and leaf bases quick to catch.
        fire.setFlammable(DOUM_LOG.get(), 5, 5);
        fire.setFlammable(DOUM_WOOD.get(), 5, 5);
        fire.setFlammable(DOUM_CROWN.get(), 30, 60);
        fire.setFlammable(DOUM_ROSETTE.get(), 30, 60);
        fire.setFlammable(DOUM_NUT_BUNCH.get(), 30, 60);
        fire.setFlammable(DOUM_LEAF_BASE.get(), 60, 100);
        fire.setFlammable(DOUM_ROOT.get(), 5, 5);
        fire.setFlammable(DOUM_FRONDS.get(), 60, 100);
    }
}
