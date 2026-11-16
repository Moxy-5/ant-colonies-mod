package com.antcolonies.entity.life;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * An animal growing up (the animal life cycle, step 1, 2026-11-16; `docs/plans/animal-life-cycle.md`): its age since it
 * was born, how grown it is (0 at birth to 1 grown) and the size that gives. One helper every animal holds, whatever
 * Minecraft base it stands on (a fish, a plain mob), in place of each animal counting its own age.
 *
 * <p>Species-neutral: it knows nothing of any animal. An animal gives it a {@link Plan} (how long to grow up, its size at
 * birth), ticks it, and refreshes its own look and size when {@link #tick} says so. Later steps read {@link #grown()} for
 * the young shape (bigger head, shorter legs, blended to the grown shape) and for what a young animal may do.
 */
public final class GrowingUp {
    /** How often a young animal's size is brought up to date while it grows (ticks; small steps, never seen popping). */
    public static final int REFRESH_TICKS = 200;
    /** A size change smaller than this is not worth sending to the players (Minecraft's size setting). */
    private static final double SIZE_STEP = 0.005;

    /**
     * How an animal grows up.
     *
     * @param growUpTicks ticks from birth to grown (24,000 a game day)
     * @param birthSize   its size at birth against a grown one's (it grows to 1 by {@code growUpTicks})
     */
    public record Plan(int growUpTicks, float birthSize) {
        public Plan {
            if (growUpTicks <= 0 || birthSize <= 0 || birthSize > 1) {
                throw new IllegalArgumentException("growUpTicks " + growUpTicks + ", birthSize " + birthSize);
            }
        }
    }

    private final Plan plan;
    /** Ticks since it was born, counted on to the plan's {@link Plan#growUpTicks} and no further. */
    private int age;

    /** A grown animal (set young with {@link #setAge}). */
    public GrowingUp(Plan plan) {
        this.plan = plan;
        this.age = plan.growUpTicks();
    }

    public Plan plan() {
        return plan;
    }

    /** Ticks since it was born (the plan's grow-up time once grown). */
    public int age() {
        return age;
    }

    /** Sets its age (ticks since birth), kept between 0 and grown. */
    public void setAge(int ticks) {
        age = Mth.clamp(ticks, 0, plan.growUpTicks());
    }

    /** Makes it grown now. */
    public void setGrown() {
        age = plan.growUpTicks();
    }

    /** Makes it this many ticks from grown. */
    public void setTicksToGrow(int ticks) {
        setAge(plan.growUpTicks() - ticks);
    }

    public boolean isYoung() {
        return age < plan.growUpTicks();
    }

    /** How grown it is: 0 at birth, 1 grown. */
    public float grown() {
        return age / (float) plan.growUpTicks();
    }

    /** Its size now against a grown one of its own size: the plan's birth size at birth, growing evenly to 1. */
    public float size() {
        return isYoung() ? plan.birthSize() + (1 - plan.birthSize()) * age / (float) plan.growUpTicks() : 1.0F;
    }

    /**
     * One tick older (server side). True when the animal should bring its size and look up to date: every
     * {@link #REFRESH_TICKS} while young, and on the tick it grows up.
     */
    public boolean tick() {
        if (!isYoung()) {
            return false;
        }
        age++;
        return age % REFRESH_TICKS == 0 || age == plan.growUpTicks();
    }

    /** Saves its age under this name (each animal keeps its own name, so old saves and buckets still load). */
    public void save(CompoundTag tag, String name) {
        tag.putInt(name, age);
    }

    /** Loads its age saved under this name; false (age unchanged) when there is none. */
    public boolean load(CompoundTag tag, String name) {
        if (!tag.contains(name)) {
            return false;
        }
        setAge(tag.getInt(name));
        return true;
    }

    /**
     * Draws and boxes the animal at this size through Minecraft's size setting ({@link Attributes#SCALE}), skipping
     * changes too small to see. {@code size} is the whole size: how grown it is times its own length.
     */
    public static void applySize(LivingEntity animal, float size) {
        AttributeInstance scale = animal.getAttribute(Attributes.SCALE);
        if (scale != null && Math.abs(scale.getBaseValue() - size) > SIZE_STEP) {
            scale.setBaseValue(size);
        }
    }
}
