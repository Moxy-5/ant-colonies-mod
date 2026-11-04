package com.antcolonies.savanna.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.goal.Errand;
import com.antcolonies.entity.load.CarriedLoad;
import com.antcolonies.savanna.colony.PreyPile;
import com.antcolonies.savanna.colony.RaidColumn;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.MatabeleLoads;
import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * M8 step 3c: a Matabele raider home with stung termites in its jaws takes them down into the nest and lays them on the
 * prey pile in the queen's room ({@link PreyPile#bringIn}), where the colony eats them (real Megaponera: the raid's prey
 * is carried straight into the nest and eaten within the day, nothing stored). It goes once it has left the column (at
 * the entrance); one that finds no way to the pile within {@code bring_ticks} has them laid there out of sight, as the
 * numbers would. Mind layer only: the walk is the errand helper's.
 */
public class BringPreyGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get to the pile. */
    private static final double REACH = 0.9;
    /** Time (in ticks) before it tries again after finding no way to the pile. */
    private static final int RETRY_TICKS = 100;
    /** It gives up (and the termites are laid out of sight) after this many tries with no way. */
    private static final int MOST_TRIES = 3;

    private final MatabeleEntity ant;
    private final Errand<BlockPos> errand;
    @Nullable
    private PreyPile pile;
    @Nullable
    private BlockPos target;
    private int ticks;
    private boolean done;
    private long nextTry;
    private int tries;

    public BringPreyGoal(MatabeleEntity ant) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ant.isCarrying(MatabeleLoads.TERMITES) || ant.isDowned() || !(ant.level() instanceof ServerLevel level)
                || level.getGameTime() < nextTry || ant.isInAColumn()) {
            return false;
        }
        Colony colony = ant.getColonyId() == null ? null : ColonyManager.get(level).getColony(ant.getColonyId());
        pile = colony == null ? null : colony.part(SavannaParts.PREY_PILE).orElse(null);
        if (pile == null) {
            ant.setLoad(CarriedLoad.NONE); // (no colony or no pile any more: nowhere for them)
            return false;
        }
        target = pile.pickReturnSpot(level, ant.getRandom());
        if (target == null || !errand.setOff(target, 1.0)) {
            noWay(level);
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return pile != null && !done && ticks < bringTicks() && ant.isCarrying(MatabeleLoads.TERMITES) && !ant.isDowned();
    }

    @Override
    public void start() {
        ticks = 0;
        done = false;
        errand.begin();
    }

    @Override
    public void stop() {
        errand.stop();
        if (ant.level() instanceof ServerLevel level && ticks >= bringTicks() && ant.isCarrying(MatabeleLoads.TERMITES)) {
            layOutOfSight(level); // (a long way and no pile reached: laid there as the numbers would)
        }
        pile = null;
        target = null;
    }

    @Override
    public void tick() {
        if (pile == null || target == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        if (!errand.within(target, REACH)) {
            if (!errand.walkOn(target, 1.0)) {
                done = true;
                noWay(level);
            }
            return;
        }
        // At the pile: the termites laid on it.
        int held = MatabeleLoads.termitesHeldBy(ant);
        pile.bringIn(level, target, held);
        ant.setLoad(CarriedLoad.NONE);
        level.playSound(null, target, SoundEvents.GRAVEL_HIT, SoundSource.BLOCKS, 0.2F, 1.6F);
        tries = 0;
        done = true;
    }

    /** No way to the pile now: it tries again a little later, and after a few tries the termites are laid out of sight. */
    private void noWay(ServerLevel level) {
        nextTry = level.getGameTime() + RETRY_TICKS;
        if (++tries >= MOST_TRIES) {
            layOutOfSight(level);
        }
    }

    private void layOutOfSight(ServerLevel level) {
        if (pile != null && ant.isCarrying(MatabeleLoads.TERMITES)) {
            pile.bringIn(level, null, MatabeleLoads.termitesHeldBy(ant));
        }
        ant.setLoad(CarriedLoad.NONE);
        tries = 0;
    }

    private int bringTicks() {
        return ant.profile().section(SavannaNumbers.CARRY).map(SavannaNumbers.CarryNumbers::bringTicks)
                .orElse(RaidColumn.DEFAULT_CARRY.bringTicks());
    }
}
