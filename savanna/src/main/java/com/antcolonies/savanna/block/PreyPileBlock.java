package com.antcolonies.savanna.block;

import com.antcolonies.block.SeedPileBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The Matabele prey pile (M8 step 1e, {@code docs/DESIGN.md} "Step 1e"): a heap of stung, paralysed Macrotermes workers
 * and soldiers in the queen's room, a layer for each bunch (about 10 termites, one big ant's load). Real Megaponera eat
 * their prey fresh and keep no store (Yusuf 2026): a fresh pile twitches (its legs' texture moves); once the youngest
 * layer is three quarters of a day old the termites have died and the pile lies still and grey-brown ({@link #DEAD}),
 * and a layer a day old has rotted and is carried out ({@code colony.PreyPile}). Nothing collides with it, as with every heap.
 */
public class PreyPileBlock extends SeedPileBlock {
    /** The termites in it have died: still and grey-brown (the colony's pile part sets it on every heap). */
    public static final BooleanProperty DEAD = BooleanProperty.create("dead");

    public PreyPileBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1).setValue(DEAD, false));
    }

    /** This heap with the given look (fresh or dead), its layers kept. */
    public static BlockState withDead(BlockState state, boolean dead) {
        return state.getValue(DEAD) == dead ? state : state.setValue(DEAD, dead);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DEAD);
    }
}
