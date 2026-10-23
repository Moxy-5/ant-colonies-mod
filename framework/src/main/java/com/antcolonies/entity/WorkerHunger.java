package com.antcolonies.entity;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * A worker's own hunger (species-neutral: ant and termite workers alike). It eats once every meal interval of its
 * species' profile ({@code worker}); without a meal it slows down to half speed and then starves to death and leaves its
 * body. The clock only runs while its colony's worker hunger runs ({@code colony.WorkerFood#hungerRuns}): never for a
 * worker of no colony, nor in a termite colony that has had no comb yet.
 */
public class WorkerHunger {
    private static final ResourceLocation STARVING_SLOWDOWN = ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "starving");

    private final AbstractAntEntity worker;
    /** The game time of its last meal ({@link Colony#NOT_SET}: not yet, it counts from the first moment its clock runs). Saved. */
    private long lastAte = Colony.NOT_SET;

    public WorkerHunger(AbstractAntEntity worker) {
        this.worker = worker;
    }

    @Nullable
    private Colony colony() {
        return worker.getColonyId() == null || !(worker.level() instanceof ServerLevel level) ? null
                : ColonyManager.get(level).getColony(worker.getColonyId());
    }

    /** Whether it has gone a whole meal interval without eating. */
    public boolean isHungry() {
        Colony colony = colony();
        return colony != null && lastAte != Colony.NOT_SET
                && worker.level().getGameTime() - lastAte >= colony.profile().worker().mealIntervalTicks();
    }

    /** It has eaten: full for another meal interval. */
    public void eat() {
        lastAte = worker.level().getGameTime();
        updateStarving(false);
    }

    public long getLastAte() {
        return lastAte;
    }

    public void setLastAte(long gameTime) {
        lastAte = gameTime;
    }

    /** Once a second (server): slow after the profile's slow time without a meal, dead after its starve time. */
    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        Colony colony = colony();
        if (lastAte == Colony.NOT_SET || colony == null || !colony.workerFood().hungerRuns()) {
            lastAte = now; // the clock only runs for a worker in a colony whose workers get hungry
        }
        if (colony == null) {
            updateStarving(false); // no colony, no hunger (its clock was just set to now)
            return;
        }
        long hungry = now - lastAte;
        SpeciesProfile.WorkerNumbers numbers = colony.profile().worker();
        updateStarving(hungry >= numbers.slowAfterTicks());
        if (hungry >= numbers.starveAfterTicks()) {
            worker.hurt(worker.damageSources().starve(), 1000.0F);
        }
    }

    private void updateStarving(boolean slow) {
        var speed = worker.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (slow) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(STARVING_SLOWDOWN, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            speed.removeModifier(STARVING_SLOWDOWN);
        }
    }

    public void save(CompoundTag tag) {
        tag.putLong("lastAte", lastAte);
    }

    public void load(CompoundTag tag) {
        lastAte = tag.contains("lastAte") ? tag.getLong("lastAte") : Colony.NOT_SET;
    }
}
