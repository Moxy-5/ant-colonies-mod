package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.TermiteKingEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A founding pair closes its nest from inside (species with {@code seal_while_founding}, termites): once the royal
 * chamber is dug and while the colony has no workers yet, the king (the queen if there is no king) climbs up the shaft
 * to just under the hole and packs it shut with soil. If the hole is opened again (a player), it is closed again.
 */
public class SealEntranceGoal extends Goal {
    /** Packing the soil into the hole takes this long: 2 seconds. */
    private static final int PACK_TICKS = 40;
    private static final int PACK_INTERVAL = 10;
    /** If the founder can't get under the hole within 30 seconds it gives up for now. */
    private static final int GIVE_UP_TICKS = 600;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos stand;
    private int ticks;
    private int packed;

    public SealEntranceGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Whether this colony's entrance should be closed now, and this ant is the one to do it. */
    public static boolean wantsSealing(ServerLevel level, Colony colony, AbstractAntEntity ant) {
        if (!colony.profile().nest().sealWhileFounding()) {
            return false;
        }
        boolean mine = ant instanceof TermiteKingEntity || colony.getCount(TermiteKingEntity.CASTE) == 0;
        return colony.isFounding() && mine && colony.nest().getHomeChamber(level) != null && colony.nest().findDigTarget(level) == null
                && colony.nest().isEntranceOpen(level);
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || ant.getRandom().nextInt(20) != 0 || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || !wantsSealing(level, colony, ant)) {
            return false;
        }
        stand = colony.nest().getEntrance().below();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && stand != null && ticks < GIVE_UP_TICKS && colony.nest().isEntranceOpen(ant.level());
    }

    @Override
    public void start() {
        ticks = 0;
        packed = 0;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        stand = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (stand == null || colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        BlockPos hole = colony.nest().getEntrance();
        ant.getLookControl().setLookAt(Vec3.atCenterOf(hole));
        if (!ant.blockPosition().equals(stand)) {
            if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(stand, 1.0)) {
                ticks = GIVE_UP_TICKS; // no way up the shaft
            }
            return;
        }
        // Clinging just under the hole: pack soil into it.
        ant.getNavigator().stop();
        BlockState soil = colony.profile().nest().plugState();
        if (++packed % PACK_INTERVAL == 0) {
            level.playSound(null, hole, soil.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.4F, 1.2F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, soil),
                    hole.getX() + 0.5, hole.getY() + 0.1, hole.getZ() + 0.5, 4, 0.2, 0.05, 0.2, 0.05);
        }
        if (packed < PACK_TICKS) {
            return;
        }
        // Never shut someone into the hole: a player or animal standing in it has to move first.
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(hole), entity -> entity.isAlive() && entity != ant).isEmpty()) {
            packed = PACK_TICKS - PACK_INTERVAL;
            return;
        }
        level.setBlockAndUpdate(hole, soil);
    }
}
