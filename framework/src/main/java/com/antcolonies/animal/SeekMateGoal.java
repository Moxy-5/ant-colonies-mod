package com.antcolonies.animal;

import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Finding a mate (the animal life kit, {@code docs/plans/animal-life-cycle.md}): an animal ready to breed
 * ({@link AnimalLife#readyToBreed}), with room for its kind to grow ({@link RoomToBreed}), goes to the nearest ready one
 * of its own kind it can mate with ({@link Sex#matesWith}) within its life file's mate reach, and the pair court side by
 * side for the file's courtship time (each animal's {@link HasLife#courting} hook plays its own part). Then both have
 * bred: {@link HasLife#mated} is called once on each and their wait before the next breeding starts. A kind whose life
 * file does not breed never starts it.
 */
public class SeekMateGoal<T extends Mob & HasLife> extends Goal {
    /** How often a ready animal looks round for a mate (ticks). */
    private static final int LOOK_EVERY = 40;
    /** Give up a mate it has not reached in this long (ticks). */
    private static final int GIVE_UP = 1200;

    private final T animal;
    private final double speed;
    @Nullable
    private Mob mate;
    private int courted;
    private int walked;

    public SeekMateGoal(T animal, double speed) {
        this.animal = animal;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if ((animal.tickCount + animal.getId()) % LOOK_EVERY != 0 || !animal.life().readyToBreed()) {
            return false;
        }
        if (!RoomToBreed.here(animal, animal.life().numbers().localLimit())) {
            return false;
        }
        mate = findMate();
        return mate != null;
    }

    @Override
    public boolean canContinueToUse() {
        return mate != null && mate.isAlive() && walked < GIVE_UP && animal.life().readyToBreed()
                && mate instanceof HasLife other && other.life().readyToBreed();
    }

    @Override
    public void start() {
        courted = 0;
        walked = 0;
        if (mate != null) {
            animal.life().setPartner(mate.getUUID());
        }
    }

    @Override
    public void stop() {
        animal.life().setPartner(null);
        mate = null;
        animal.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (mate == null) {
            return;
        }
        animal.getLookControl().setLookAt(mate, 30, 30);
        double touch = (animal.getBbWidth() + mate.getBbWidth()) * 0.5 + 0.5;
        if (animal.distanceTo(mate) > touch) {
            walked++;
            if (animal.getNavigation().isDone() || walked % 20 == 0) {
                animal.getNavigation().moveTo(mate, speed);
            }
            return;
        }
        animal.getNavigation().stop();
        animal.courting(mate, courted);
        if (++courted >= animal.life().numbers().courtshipTicks()) {
            Mob partner = mate;
            HasLife other = (HasLife) partner;
            animal.life().bred();
            other.life().bred();
            animal.mated(partner);
            other.mated(animal);
            mate = null;
        }
    }

    /** The nearest ready mate within reach that is not courting another. */
    @Nullable
    private Mob findMate() {
        LifeNumbers numbers = animal.life().numbers();
        Sex sex = animal.life().sex();
        List<Mob> near = animal.level().getEntitiesOfClass(Mob.class, animal.getBoundingBox().inflate(numbers.mateReach()),
                m -> m != animal && m.getType() == animal.getType() && m instanceof HasLife other
                        && other.life().readyToBreed() && sex.matesWith(other.life().sex())
                        && (other.life().partner() == null || other.life().partner().equals(animal.getUUID())));
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob m : near) {
            double d = animal.distanceToSqr(m);
            if (d < bestDistance) {
                bestDistance = d;
                best = m;
            }
        }
        return best;
    }
}
