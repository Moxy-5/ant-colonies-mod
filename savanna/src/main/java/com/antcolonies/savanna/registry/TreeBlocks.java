package com.antcolonies.savanna.registry;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.BleachedBranchBlock;
import com.antcolonies.savanna.block.BracketFungusBlock;
import com.antcolonies.savanna.block.DeadTreeWoodBlock;
import com.antcolonies.savanna.block.FrassBlock;
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
    /** What a dead baobab slumps into: a soggy heap of fibres (it leaves no log). */
    public static final DeferredBlock<SoftDeadWoodBlock> BAOBAB_FIBRE = add(SavannaBlocks.BLOCKS.registerBlock("baobab_fibre", SoftDeadWoodBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.4F).sound(SoundType.WET_GRASS)));

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
        fire.setFlammable(THORNY_TWIG.get(), 60, 100);
        fire.setFlammable(CAMEL_THORN_PODS.get(), 30, 60);
        for (DeferredBlock<? extends Block> dead : List.of(BLEACHED_LOG, BLEACHED_WOOD, PEELING_BLEACHED_LOG, BLEACHED_BRANCH, ROTTEN_WOOD)) {
            fire.setFlammable(dead.get(), 10, 0);
        }
        for (DeferredBlock<? extends Block> charred : List.of(CHARRED_LOG, CHARRED_WOOD, CHARRED_BRANCH)) {
            fire.setFlammable(charred.get(), 5, 0);
        }
        fire.setFlammable(FRASS.get(), 60, 100);
        fire.setFlammable(BAOBAB_FIBRE.get(), 5, 5);
    }
}
