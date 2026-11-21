package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.antcolonies.savanna.block.AlgaeBlock;
import com.antcolonies.savanna.block.CatfishEggsBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A fish female scatters her eggs over the algae (M9, animal life cycle step 3, 2026-11-19, the catfish; shared with
 * the barb 2026-11-21; fun first, my picks; real catfish and barbs scatter sticky eggs over water plants and leave
 * them): after mating she swims from algae mat to algae mat near her, nosing down over each and leaving a few eggs stuck to it
 * ({@link CatfishEggsBlock#lay}), the nearest mats first, until her eggs are gone. Mats already with eggs are passed by.
 * With no mat near, or after {@link #GIVE_UP} ticks, the eggs left drift off and are lost. No care after.
 */
public class ScatterEggsGoal<T extends net.minecraft.world.entity.Mob & ScattersEggs> extends Goal {
    /** How far round her she looks for mats (blocks across; up or down). */
    static final int LOOK_ACROSS = 8, LOOK_UP_DOWN = 3;
    /** Eggs left on one mat (my picks), and how long she noses over it. */
    static final int EGGS_A_MAT = 2, EGGS_A_MAT_MORE = 2, LAY_TICKS = 20;
    /** She gives up after this long and the eggs left are lost. */
    static final int GIVE_UP = 900;

    private final T fish;
    private final Set<BlockPos> tried = new HashSet<>();
    @Nullable
    private BlockPos mat;
    private int ticks, over, toMat;

    public ScatterEggsGoal(T fish) {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return fish.eggsToScatter() > 0 && fish.isInWater();
    }

    @Override
    public boolean canContinueToUse() {
        return fish.eggsToScatter() > 0 && ticks < GIVE_UP;
    }

    @Override
    public void start() {
        ticks = 0;
        tried.clear();
        mat = null;
    }

    @Override
    public void stop() {
        if (fish.eggsToScatter() > 0 && ticks >= GIVE_UP) {
            fish.scattered(fish.eggsToScatter());     // the rest drift off
        }
        fish.getNavigation().stop();
        mat = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (fish.eggsToScatter() <= 0) {
            return;
        }
        ticks++;
        if (mat == null || !isMat(mat)) {
            mat = nextMat();
            over = 0;
            toMat = 0;
            if (mat == null) {
                fish.scattered(fish.eggsToScatter());   // no mat near: the eggs drift off
                return;
            }
        }
        double x = mat.getX() + 0.5, y = mat.getY() + 0.4, z = mat.getZ() + 0.5;
        fish.getLookControl().setLookAt(x, mat.getY(), z);
        double dx = fish.getX() - x, dz = fish.getZ() - z, dy = fish.getY() - y;
        if (dx * dx + dz * dz > 0.8 || Math.abs(dy) > 1.0) {
            if (++toMat > 200) {
                tried.add(mat);              // cannot get there: the next one
                mat = null;
                return;
            }
            if (dx * dx + dz * dz + dy * dy < 4.0) {
                // the last stretch straight over it (a fish's path ends short of its goal, as the tilapia's grazing)
                fish.getNavigation().stop();
                double far = Math.sqrt(dx * dx + dz * dz + dy * dy);
                double push = 0.02 / Math.max(far, 0.05);
                fish.setDeltaMovement(fish.getDeltaMovement().scale(0.8).add(-dx * push, -dy * push, -dz * push));
            } else if (toMat % 20 == 1 || fish.getNavigation().isDone()) {
                fish.getNavigation().moveTo(x, y, z, 0.8);
            }
            return;
        }
        fish.getNavigation().stop();
        fish.setDeltaMovement(fish.getDeltaMovement().scale(0.6));
        if (++over >= LAY_TICKS && fish.level() instanceof ServerLevel server) {
            int n = Math.min(fish.eggsToScatter(), EGGS_A_MAT + fish.getRandom().nextInt(EGGS_A_MAT_MORE + 1));
            if (CatfishEggsBlock.lay(server, mat, fish.getType(), n)) {
                AlgaeBlock.eggPuff(server, mat);
                fish.scattered(n);
            }
            tried.add(mat);
            mat = null;
        }
    }

    /** An algae mat with something on it to stick eggs to (not bare) and no eggs yet. */
    private boolean isMat(BlockPos pos) {
        BlockState state = fish.level().getBlockState(pos);
        return state.is(SavannaBlocks.ALGAE.get()) && state.getValue(AlgaeBlock.GROWTH) != AlgaeBlock.BARE;
    }

    /** The nearest mat round her she has not tried yet. */
    @Nullable
    private BlockPos nextMat() {
        BlockPos home = fish.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos at : BlockPos.betweenClosed(home.offset(-LOOK_ACROSS, -LOOK_UP_DOWN, -LOOK_ACROSS),
                home.offset(LOOK_ACROSS, LOOK_UP_DOWN, LOOK_ACROSS))) {
            if (tried.contains(at) || !isMat(at)) {
                continue;
            }
            double d = at.distSqr(home);
            if (d < bestDistance) {
                bestDistance = d;
                best = at.immutable();
            }
        }
        return best;
    }
}
