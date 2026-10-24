package com.antcolonies.savanna.registry;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.DeadWoodBlock;
import com.antcolonies.savanna.block.DryGrassBlock;
import com.antcolonies.savanna.block.FallenBranchBlock;
import com.antcolonies.savanna.block.FungusCombBlock;
import com.antcolonies.savanna.block.MudCastBlock;
import com.antcolonies.savanna.block.MudCrustBlock;
import com.antcolonies.savanna.block.GiantTermitomycesBlock;
import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.savanna.block.TallDryGrassBlock;
import com.antcolonies.savanna.block.TermiteHoleBlock;
import com.antcolonies.savanna.block.TermiteMoundBlock;
import com.antcolonies.savanna.block.TermiteMouthBlock;
import com.antcolonies.savanna.block.TermiteRampBlock;
import com.antcolonies.savanna.block.TermiteSpireBlock;
import com.antcolonies.savanna.block.TermiteTunnelBlock;
import com.antcolonies.savanna.block.TermitomycesBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The savanna's blocks: the termites' mound, garden and food, the dry grass and dead wood, and the giant trees (the split, step S4). */
public class SavannaBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SavannaMod.MODID);

    /**
     * A heap of forage (pieces of dead grass, leaves and twigs) in the termites' food store: one layer per piece a
     * termite brings in. Same block as the seed pile, other look.
     */
    public static final DeferredBlock<SeedPileBlock> FORAGE_PILE = BLOCKS.registerBlock("forage_pile", SeedPileBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.2F).sound(SoundType.GRASS)
                    .noCollission().noOcclusion().pushReaction(PushReaction.DESTROY));

    /**
     * Fungus comb in the termites' garden rooms: built up from forage, one layer per piece, with white fungus nodules
     * growing on it (see FungusCombBlock). Solid: termites walk on it.
     */
    public static final DeferredBlock<FungusCombBlock> FUNGUS_COMB = BLOCKS.registerBlock("fungus_comb", FungusCombBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.3F).sound(SoundType.WART_BLOCK)
                    .noOcclusion().pushReaction(PushReaction.DESTROY));

    /**
     * A dead colony's fungus comb taken over by Xylaria (M8 step 1a, see DeadCombBlock): furred, black with stalks, then
     * rotting away. Solid: insects walk on it. Drops nothing.
     */
    public static final DeferredBlock<com.antcolonies.savanna.block.DeadCombBlock> DEAD_COMB = BLOCKS.registerBlock("dead_comb",
            com.antcolonies.savanna.block.DeadCombBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.2F).sound(SoundType.WART_BLOCK)
                    .noOcclusion().pushReaction(PushReaction.DESTROY));

    /** Termite mound soil: built up in layers, then dries and hardens with age (see TermiteMoundBlock). */
    public static final DeferredBlock<TermiteMoundBlock> TERMITE_MOUND = BLOCKS.registerBlock("termite_mound", TermiteMoundBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.6F, 3.0F).sound(SoundType.MUD)
                    .randomTicks());

    /** The termites' chimney crown: solid for everyone but termites (see TermiteHoleBlock). */
    public static final DeferredBlock<TermiteHoleBlock> TERMITE_HOLE = BLOCKS.registerBlock("termite_hole", TermiteHoleBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.5F, 3.0F).sound(SoundType.MUD_BRICKS)
                    .noOcclusion().isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false));

    /** A piece of a spire, in 5 thicknesses narrowing to a point (see TermiteSpireBlock). */
    public static final DeferredBlock<TermiteSpireBlock> TERMITE_SPIRE = BLOCKS.registerBlock("termite_spire", TermiteSpireBlock::new,
            pieceProperties());

    /** The chimney mouth at a spire's tip: solid for everyone but termites (see TermiteMouthBlock). */
    public static final DeferredBlock<TermiteMouthBlock> TERMITE_MOUTH = BLOCKS.registerBlock("termite_mouth", TermiteMouthBlock::new,
            pieceProperties().isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false));

    /** A ramp that hides the steps of the mound's sides, joining into corners like stairs (see TermiteRampBlock). */
    public static final DeferredBlock<TermiteRampBlock> TERMITE_RAMP = BLOCKS.registerBlock("termite_ramp", TermiteRampBlock::new,
            pieceProperties());

    /**
     * Mound soil with a termite-sized channel through it, joining up with the tunnel blocks beside it (see
     * TermiteTunnelBlock). Termite workers crawl inside it, so it must not suffocate them.
     */
    public static final DeferredBlock<TermiteTunnelBlock> TERMITE_TUNNEL = BLOCKS.registerBlock("termite_tunnel", TermiteTunnelBlock::new,
            pieceProperties().isSuffocating((state, level, pos) -> false));

    /** A Termitomyces mushroom that comes up round a termite mound after rain (see TermitomycesBlock). */
    public static final DeferredBlock<TermitomycesBlock> TERMITOMYCES = BLOCKS.registerBlock("termitomyces", TermitomycesBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).noCollission().instabreak().sound(SoundType.FUNGUS)
                    .replaceable().offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY).randomTicks());

    /** A rare giant Termitomyces, three blocks tall (see GiantTermitomycesBlock). */
    public static final DeferredBlock<GiantTermitomycesBlock> GIANT_TERMITOMYCES = BLOCKS.registerBlock("giant_termitomyces",
            GiantTermitomycesBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.4F)
                    .sound(SoundType.FUNGUS).noOcclusion().pushReaction(PushReaction.DESTROY).randomTicks());

    /** A tuft of savanna grass that grows green, then dries; termites only eat it dry (see DryGrassBlock). */
    public static final DeferredBlock<DryGrassBlock> DRY_GRASS = BLOCKS.registerBlock("dry_grass", DryGrassBlock::new,
            grassProperties());

    /** Savanna grass two blocks tall with seed heads; dries like the short tuft (see TallDryGrassBlock). */
    public static final DeferredBlock<TallDryGrassBlock> TALL_DRY_GRASS = BLOCKS.registerBlock("tall_dry_grass", TallDryGrassBlock::new,
            grassProperties());

    /** A block of a dead branch lying on the ground, eaten by termites in stages (see FallenBranchBlock). */
    public static final DeferredBlock<FallenBranchBlock> FALLEN_BRANCH = BLOCKS.registerBlock("fallen_branch", FallenBranchBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.5F).sound(SoundType.WOOD).noCollission()
                    .noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY));

    /** Dead acacia wood: a fallen tree's trunk and stump, eaten by termites in stages (see DeadWoodBlock). */
    public static final DeferredBlock<DeadWoodBlock> DEAD_WOOD = BLOCKS.registerBlock("dead_wood", DeadWoodBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(1.5F).sound(SoundType.WOOD).ignitedByLava());

    /**
     * What is left of a block of dead wood the termites ate away: a tunnel block of mud where the wood was, crumbling
     * once the log is gone (see MudCastBlock).
     */
    public static final DeferredBlock<MudCastBlock> MUD_CAST = BLOCKS.registerBlock("mud_cast", MudCastBlock::new,
            pieceProperties().isSuffocating((state, level, pos) -> false));

    /** A thin crust of termite mud on any face of a block: gallery walls, coated wood, sheeting (see MudCrustBlock). */
    public static final DeferredBlock<MudCrustBlock> MUD_CRUST = BLOCKS.registerBlock("mud_crust", MudCrustBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.2F).sound(SoundType.MUD).noOcclusion()
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)
                    .pushReaction(PushReaction.DESTROY).randomTicks());

    static {
        TreeBlocks.init(); // the giant savanna trees' blocks (M7.5), registered with the rest
    }


    private static BlockBehaviour.Properties grassProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SAND).replaceable().noCollission().instabreak().sound(SoundType.GRASS)
                .offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.DESTROY).randomTicks();
    }

    private static BlockBehaviour.Properties pieceProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.6F, 3.0F).sound(SoundType.MUD)
                .randomTicks();
    }
}
