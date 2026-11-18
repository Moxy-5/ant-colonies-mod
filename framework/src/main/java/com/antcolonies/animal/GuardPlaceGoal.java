package com.antcolonies.animal;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/**
 * A care hook (the animal life kit, {@code docs/plans/animal-life-cycle.md}): guard a place, a nest or a crèche. While
 * the animal has a place to guard ({@code place} gives one, or null for none) it keeps within {@code stayWithin} blocks
 * of it, and anything its {@code intruder} test picks that comes within {@code seeOff} blocks of the place becomes its
 * target, for its own attack goals to see off.
 */
public class GuardPlaceGoal extends Goal {
    private final Mob animal;
    private final Supplier<BlockPos> place;
    private final double stayWithin;
    private final double seeOff;
    private final Predicate<LivingEntity> intruder;
    private final double speed;

    public GuardPlaceGoal(Mob animal, Supplier<BlockPos> place, double stayWithin, double seeOff, Predicate<LivingEntity> intruder, double speed) {
        this.animal = animal;
        this.place = place;
        this.stayWithin = stayWithin;
        this.seeOff = seeOff;
        this.intruder = intruder;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        BlockPos at = place.get();
        return at != null && (tooFar(at) || intruderNear(at) != null);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        BlockPos at = place.get();
        if (at == null) {
            return;
        }
        LivingEntity foe = intruderNear(at);
        if (foe != null) {
            animal.setTarget(foe);
            return;
        }
        if (tooFar(at) && (animal.getNavigation().isDone() || animal.tickCount % 20 == 0)) {
            animal.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, speed);
        }
    }

    private boolean tooFar(BlockPos at) {
        return animal.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) > stayWithin * stayWithin;
    }

    @Nullable
    private LivingEntity intruderNear(BlockPos at) {
        List<LivingEntity> near = animal.level().getEntitiesOfClass(LivingEntity.class, new AABB(at).inflate(seeOff),
                e -> e != animal && e.isAlive() && intruder.test(e));
        return near.isEmpty() ? null : near.get(0);
    }
}
