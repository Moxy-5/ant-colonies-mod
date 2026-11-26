package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.antcolonies.savanna.block.AlgaeBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A hungry tilapia swims down to the thickest river algae near it, noses into the mat and grazes it (M9 river life,
 * 2026-11-14): a few pecks, then {@link AlgaeBlock#graze} bites 2 strands off it (or, with none left, tears the mat off down to the bare bed; it grows back), and it swims on. Hungry again
 * after 2 to 4 minutes (my picks). Eggs near come first (animal life cycle step 3, {@link EggMeals}). With nothing to eat near it simply swims on: a fish never starves, so the river's
 * food chain can never crash (the balance rule).
 *
 * <p>Hippo grass (the river food web's step 3, 2026-11-23): with no algae worth more near, it nibbles a mat's outer edge
 * from under it ({@code HippoGrassBlock.livingBite}: worth the least of all, so algae always comes first).
 */
public class GrazeAlgaeGoal extends Goal {
    /** How far it looks for algae (blocks across and up or down; my picks). */
    static final int LOOK_ACROSS = 8, LOOK_UP_DOWN = 4;
    /** How long it noses at the mat before the bite counts (two pecks of the graze clip). */
    static final int GRAZE_TICKS = 36;
    /** How long it may take to swim there before giving up. */
    static final int GIVE_UP_TICKS = 200;
    /** How long until it is hungry again after a meal (ticks; 2 to 4 minutes). */
    static final int FED_MIN = 2400, FED_MORE = 2400;

    private final TilapiaEntity fish;
    @Nullable
    private BlockPos mat;
    private int ticks, grazing;

    public GrazeAlgaeGoal(TilapiaEntity fish) {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (fish.hungryIn > 0 || !fish.isInWater() || fish.mouthFull() || fish.holdsNest() || fish.getRandom().nextInt(10) != 0) {
            return false;
        }
        mat = findMat();
        if (mat == null) {
            fish.hungryIn = 200;   // nothing near: look again in 10 s, swimming on meanwhile
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return mat != null && ticks < GIVE_UP_TICKS + GRAZE_TICKS && fish.isInWater() && growth(mat) > AlgaeBlock.BARE;
    }

    @Override
    public void start() {
        ticks = 0;
        grazing = 0;
        swimTo();
    }

    @Override
    public void stop() {
        fish.setGrazing(false);
        fish.getNavigation().stop();
        if (grazing < GRAZE_TICKS) {
            fish.hungryIn = 100 + fish.getRandom().nextInt(200);
        }
        mat = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (mat == null) {
            return;   // done (a bite or given up) this tick; the goal ends at its next check
        }
        ticks++;
        double x = mat.getX() + 0.5, y = spotY(mat), z = mat.getZ() + 0.5;
        fish.getLookControl().setLookAt(x, mat.getY(), z);
        double dx = fish.getX() - x, dz = fish.getZ() - z, dy = fish.getY() - y;
        boolean there = dx * dx + dz * dz < 0.36 && Math.abs(dy) < 0.6;
        if (!there) {
            fish.setGrazing(false);
            if (ticks > GIVE_UP_TICKS) {
                mat = null;
                return;
            }
            if (dx * dx + dz * dz + dy * dy < 4.0) {
                // the last stretch: straight at the mat (a path counts as walked up to ~0.8 blocks short of it, and
                // Minecraft's fish swim only along a path, so it is nudged there itself, facing the mat)
                fish.getNavigation().stop();
                double far = Math.sqrt(dx * dx + dz * dz + dy * dy);
                double push = Math.min(0.025, 0.02 + far * 0.01) / Math.max(far, 0.05);
                fish.setDeltaMovement(fish.getDeltaMovement().scale(0.8).add(-dx * push, -dy * push, -dz * push));
                if (dx * dx + dz * dz > 0.01) {
                    float yaw = (float) Math.toDegrees(Math.atan2(-dz, -dx)) - 90.0F;
                    fish.setYRot(net.minecraft.util.Mth.approachDegrees(fish.getYRot(), yaw, 20.0F));
                    fish.yBodyRot = fish.getYRot();
                }
            } else if (ticks % 20 == 0 || fish.getNavigation().isDone()) {
                swimTo();
            }
            return;
        }
        fish.getNavigation().stop();
        fish.setDeltaMovement(fish.getDeltaMovement().scale(0.6));
        fish.setGrazing(true);
        if (++grazing >= GRAZE_TICKS) {
            EggMeals.bite(fish.level(), mat);
            fish.hungryIn = FED_MIN + fish.getRandom().nextInt(FED_MORE);
            mat = null;
        }
    }

    private void swimTo() {
        fish.getNavigation().moveTo(mat.getX() + 0.5, spotY(mat), mat.getZ() + 0.5, 1.0);
    }

    /** The height it grazes at: just over an algae mat on the bed, or just under a hippo grass mat on the surface. */
    private double spotY(BlockPos pos) {
        return fish.level().getBlockState(pos).getBlock() instanceof com.antcolonies.savanna.block.HippoGrassBlock
                ? pos.getY() - 0.45 : pos.getY() + 0.15;
    }

    /**
     * How much there is to eat here: the bites left (strand pairs, then the mat; 0 bare or no algae); eggs it can reach
     * (catfish eggs on a mat, a snail clutch under water: animal life cycle step 3) are a find, not a magnet: they add
     * nothing to their mat, and alone they are worth a thin mat ({@link #EGGS}).
     */
    private int growth(BlockPos pos) {
        BlockState state = fish.level().getBlockState(pos);
        int g = AlgaeBlock.bitesLeft(state) - (AlgaeBlock.hasEggs(state) ? 1 : 0);
        if (EggMeals.forFish(state)) {
            g = Math.max(g, EGGS);
        }
        return g == 0 && com.antcolonies.savanna.block.HippoGrassBlock.livingBite(fish.level(), pos, state) ? HIPPO_GRASS : g;
    }

    /** What the outer edge of a hippo grass mat is worth: the least of all, so any algae comes first. */
    static final int HIPPO_GRASS = 1;

    /**
     * What a spot with eggs is worth at least: a thin mat's (my pick). The user's call, 2026-11-26, review R10-4: eggs
     * were worth 20 against a lush mat's 8, so every hungry tilapia within 8 blocks went for them first and a whole
     * catfish spawning was eaten (by the numbers) long before its day to hatch; now eggs go with the grazing of the
     * mat they lie on, and those on thin mats last while better grazing is near.
     */
    static final int EGGS = 2;

    /** The thickest grazeable mat near it (the nearest of the thickest), or none. */
    @Nullable
    private BlockPos findMat() {
        BlockPos home = fish.blockPosition();
        BlockPos best = null;
        int bestGrowth = AlgaeBlock.BARE;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos at : BlockPos.betweenClosed(home.offset(-LOOK_ACROSS, -LOOK_UP_DOWN, -LOOK_ACROSS),
                home.offset(LOOK_ACROSS, LOOK_UP_DOWN, LOOK_ACROSS))) {
            int g = growth(at);
            if (g <= AlgaeBlock.BARE || g < bestGrowth) {
                continue;
            }
            double d = at.distSqr(home);
            if (g > bestGrowth || d < bestDist) {
                best = at.immutable();
                bestGrowth = g;
                bestDist = d;
            }
        }
        return best;
    }
}
