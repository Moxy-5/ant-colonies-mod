package com.antcolonies.animal;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.season.Seasons;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * One animal's life (the animal life kit, {@code docs/plans/animal-life-cycle.md}): its sex, its age and so its stage
 * (young, teen, grown; old kept apart), its own size, and whether it is ready to breed. Every animal holds one, made in
 * its constructor with a synced byte of its own ({@link #define}), and calls {@link #setUp} when it spawns,
 * {@link #tick} each server tick, and {@link #save} and {@link #load} with its other data. The numbers come from its
 * life file ({@link AnimalLives}). It draws the animal at its size through Minecraft's {@code SCALE} attribute: its
 * grown share for its age ({@link LifeNumbers#grownShare}) times its own size.
 *
 * <p>Saved as one compound, {@code "Life"}. An animal saved before the kit loads its old {@code "OwnSize"} and
 * {@code "Male"} (and the tilapia its old age, {@link #load(CompoundTag, String)}). A summon or a spawn egg may set
 * {@code sex:"female"} and {@code young:true} (for testing).
 */
public final class AnimalLife {
    /** How often a growing animal's size is drawn anew (ticks). */
    private static final int GROW_REFRESH = 200;

    private final Mob mob;
    private final EntityDataAccessor<Byte> data;
    private Sex sex;
    /** Ticks since it hatched, counted on to its grown age and kept there. */
    private int age;
    private float ownSize = 1.0F;
    /** Ticks until it is ready to breed again. */
    private int readyIn;
    private boolean old;
    /** The mate it is courting now, if any (not saved). */
    @Nullable
    private UUID partner;

    /** An animal's life, born grown at its usual size, its sex rolled by its own life file. */
    public AnimalLife(Mob mob, EntityDataAccessor<Byte> data) {
        this.mob = mob;
        this.data = data;
        LifeNumbers numbers = numbers();
        this.sex = rollSex(numbers, mob.getRandom());
        this.age = numbers.grownAge();
        sync();
    }

    /** A synced byte for an animal's life, for its {@code SynchedEntityData.defineId}. */
    public static EntityDataAccessor<Byte> dataFor(Class<? extends Mob> animal) {
        return SynchedEntityData.defineId(animal, EntityDataSerializers.BYTE);
    }

    /** Puts the life byte into the animal's synced data (in its {@code defineSynchedData}). */
    public static void define(SynchedEntityData.Builder builder, EntityDataAccessor<Byte> data) {
        builder.define(data, (byte) 0);
    }

    /** Its life numbers (its life file). */
    public LifeNumbers numbers() {
        return AnimalLives.get(mob.getType());
    }

    /**
     * Rolls a new life when it spawns: its sex, its own size within its sex's range, and its age (grown, or young at its
     * file's age for young spawns, {@link LifeNumbers#spawnYoungAge}). Hatched young set their age to 0 after.
     */
    public void setUp(boolean young, RandomSource random) {
        setUp(young, null, random);
    }

    /**
     * Rolls a new life as {@link #setUp(boolean, RandomSource)}, but of this sex when one is given (and its kind has it),
     * so its size is rolled in its own sex's range (a world nest's mother, code review 2026-11-21: made female after
     * the roll, half of them kept a male's size).
     */
    public void setUp(boolean young, @Nullable Sex asked, RandomSource random) {
        LifeNumbers numbers = numbers();
        sex = asked != null && (asked == Sex.NONE) != numbers.twoSexes() ? asked : rollSex(numbers, random);
        float[] size = numbers.sizeOf(sex);
        ownSize = size[0] + (size[1] - size[0]) * random.nextFloat();
        age = young ? numbers.spawnYoungAge(random) : numbers.grownAge();
        readyIn = 0;
        old = false;
        apply();
    }

    /** Whether the world's own spawns of it come young this time (its life file's {@code young_at_spawn}). */
    public boolean spawnsYoung(RandomSource random) {
        float share = numbers().youngAtSpawn();
        return share > 0 && random.nextFloat() < share;
    }

    private static Sex rollSex(LifeNumbers numbers, RandomSource random) {
        if (!numbers.twoSexes()) {
            return Sex.NONE;
        }
        return random.nextFloat() < numbers.maleShare() ? Sex.MALE : Sex.FEMALE;
    }

    /**
     * Its life for one server tick: growing (drawn anew every few seconds and the moment it is grown) and the wait
     * before it may breed again. True when its size or stage was drawn anew this tick (the animal may want to change
     * its look too).
     */
    public boolean tick() {
        if (mob.level().isClientSide) {
            return false;
        }
        if (readyIn > 0) {
            readyIn--;
        }
        int grownAge = numbers().grownAge();
        if (age < grownAge) {
            age++;
            if (age % GROW_REFRESH == 0 || age == grownAge) {
                apply();
                return true;
            }
        }
        return false;
    }

    /** Draws it at its size for its age and tells the client its sex and stage. Server side. */
    public void apply() {
        if (mob.level().isClientSide) {
            return;
        }
        float scale = numbers().grownShare(age) * ownSize;
        AttributeInstance attribute = mob.getAttribute(Attributes.SCALE);
        if (attribute != null && Math.abs(attribute.getBaseValue() - scale) > 0.005) {
            attribute.setBaseValue(scale);
        }
        sync();
    }

    private void sync() {
        if (!mob.level().isClientSide) {
            mob.getEntityData().set(data, (byte) (sex.ordinal() | stageNow().ordinal() << 2));
        }
    }

    private LifeStage stageNow() {
        return old ? LifeStage.OLD : numbers().stageAt(age);
    }

    /** Its sex (on the client as the server told it). */
    public Sex sex() {
        return mob.level().isClientSide ? Sex.values()[mob.getEntityData().get(data) & 3] : sex;
    }

    /** Its stage of life (on the client as the server told it). */
    public LifeStage stage() {
        return mob.level().isClientSide ? LifeStage.values()[mob.getEntityData().get(data) >> 2 & 3] : stageNow();
    }

    public boolean isYoung() {
        return stage() == LifeStage.YOUNG;
    }

    public boolean isMale() {
        return sex() == Sex.MALE;
    }

    /** Its own size, a share of its kind's usual size (its size when grown). */
    public float ownSize() {
        return ownSize;
    }

    /** Ticks since it hatched (kept at its grown age once grown). */
    public int age() {
        return age;
    }

    /** Sets its age (tests and commands), drawing it anew. */
    public void setAge(int ticks) {
        age = Mth.clamp(ticks, 0, numbers().grownAge());
        apply();
    }

    /** Makes it old or not (for the few kinds with an old stage), drawing it anew. */
    public void setOld(boolean isOld) {
        old = isOld;
        apply();
    }

    /**
     * Whether it is ready to breed: its kind breeds, it is grown, its wait since its last breeding is over, and the
     * season ({@link Seasons}), the weather and the time of day (the shared day clock) are its kind's. Room to breed ({@link RoomToBreed}) is asked apart.
     */
    public boolean readyToBreed() {
        LifeNumbers numbers = numbers();
        if (!ripe()) {
            return false;
        }
        boolean raining = rainForTests != null ? rainForTests : mob.level().isRaining();
        boolean weather = switch (numbers.weather()) {
            case ANY -> true;
            case RAIN -> raining;
            case DRY -> !raining;
        };
        if (!weather || numbers.time() == LifeNumbers.Time.ANY) {
            return weather;
        }
        boolean day = Colony.isSunUp(mob.level(), Colony.dayTimeAt(mob.level(), mob.blockPosition()));
        return day == (numbers.time() == LifeNumbers.Time.DAY);
    }

    /** A test's own weather for breeding (true: raining, false: dry, null: the world's). Tests only. */
    @Nullable
    private static volatile Boolean rainForTests;

    public static void setRainForTests(@Nullable Boolean raining) {
        rainForTests = raining;
    }

    /**
     * Whether it is ripe to breed, all but the weather and the time of day: its kind breeds, it is grown, its wait is
     * over and it is its kind's season (a catfish female ripe with eggs looks plump while she waits for a rainy night).
     */
    public boolean ripe() {
        LifeNumbers numbers = numbers();
        return numbers.breeds() && readyIn <= 0 && stageNow().grown()
                && numbers.seasons().contains(Seasons.now(mob.level(), mob.blockPosition()));
    }

    /** It has bred: the wait before it is ready again starts. */
    public void bred() {
        readyIn = numbers().breedEveryTicks();
        partner = null;
    }

    /** The mate it is courting now, if any. */
    @Nullable
    public UUID partner() {
        return partner;
    }

    public void setPartner(@Nullable UUID mate) {
        partner = mate;
    }

    /** Saves its life into the animal's data. */
    public void save(CompoundTag tag) {
        tag.put("Life", toTag());
    }

    /** Its life as a compound of its own (for a bucket too). */
    public CompoundTag toTag() {
        CompoundTag life = new CompoundTag();
        life.putString("Sex", sex.fileName());
        life.putInt("Age", age);
        life.putBoolean("Grown", age >= numbers().grownAge());
        life.putFloat("OwnSize", ownSize);
        if (readyIn > 0) {
            life.putInt("ReadyIn", readyIn);
        }
        if (old) {
            life.putBoolean("Old", true);
        }
        return life;
    }

    /** Loads its life from the animal's data (see {@link #load(CompoundTag, String)}). */
    public void load(CompoundTag tag) {
        load(tag, null);
    }

    /**
     * Loads its life from the animal's data: the kit's {@code "Life"} compound, or the old tags an animal saved before
     * the kit ({@code "OwnSize"}, {@code "Male"}, and its old age under {@code oldAgeKey} if it kept one); then a
     * summon's or spawn egg's {@code sex} and {@code young}. An animal with none of these keeps what it has. Draws it.
     */
    public void load(CompoundTag tag, @Nullable String oldAgeKey) {
        LifeNumbers numbers = numbers();
        if (tag.contains("Life")) {
            CompoundTag life = tag.getCompound("Life");
            Sex saved = Sex.fromFile(life.getString("Sex"));
            if (saved != null) {
                sex = saved;
            }
            age = life.getInt("Age");
            // saved grown, it stays grown though its kind's life file has since given it a young stage (or a longer one):
            // the crocodile was born grown until step 4, so every crocodile then saved its age as 0 (2026-11-20). An
            // age of 0 with no mark is one of those (every save since says whether it is grown).
            if (life.getBoolean("Grown") || !life.contains("Grown") && age == 0) {
                age = numbers.grownAge();
            }
            ownSize = life.getFloat("OwnSize");
            readyIn = life.getInt("ReadyIn");
            old = life.getBoolean("Old");
        } else {
            if (tag.contains("OwnSize")) {
                ownSize = tag.getFloat("OwnSize");
            }
            if (tag.contains("Male") && numbers.twoSexes()) {
                sex = tag.getBoolean("Male") ? Sex.MALE : Sex.FEMALE;
            }
            if (oldAgeKey != null && tag.contains(oldAgeKey)) {
                age = tag.getInt(oldAgeKey);
            }
        }
        if (tag.contains("sex")) {
            Sex asked = Sex.fromFile(tag.getString("sex"));
            if (asked != null && (asked == Sex.NONE) != numbers.twoSexes()) {   // never a sex its kind does not have
                sex = asked;
            }
        }
        if (tag.contains("young")) {
            age = tag.getBoolean("young") ? 0 : numbers.grownAge();
        }
        ownSize = Mth.clamp(ownSize, 0.5F, numbers.maxSize());
        age = Mth.clamp(age, 0, numbers.grownAge());
        apply();
    }
}
