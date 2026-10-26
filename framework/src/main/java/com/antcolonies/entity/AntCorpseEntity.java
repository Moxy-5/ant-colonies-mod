package com.antcolonies.entity;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The body of a dead ant or termite of one colony. It lies where it died (it falls if the floor goes away) until a worker
 * deals with it: ants carry it to the refuse chamber and set it down there (a corpse in the refuse chamber stays for
 * good); termites eat a fresh one in their fungus garden and wall an old one up with soil (see {@code Refuse}). A corpse that
 * nobody brings there crumbles to dust after a while (the species profile's corpse_decay_ticks), so nothing is left lying about for ever.
 * Saved with the world; disappears when its colony is gone.
 *
 * <p>M8 step 2f: termites drag an old body, or a stranger's (a dead raider), out of the nest ({@link #dragTo}: it goes
 * where the termite pulls it, no falling) and leave it by a hole ({@link #setOut}), where it fades after a while.
 */
public class AntCorpseEntity extends Entity {
    /** How long the body of an insect that belonged to no colony lies before it crumbles (as long as the species' bodies). */
    private static final int LONER_DECAY_TICKS = 6000;
    @Nullable
    private UUID colonyId;
    /** True once a worker has set the body down in the refuse chamber. Saved. */
    private boolean placed;
    /** How long (in ticks) the body has lain where it is (termites eat a fresh body, bury an old one). Saved. */
    private int age;
    /** The game time it fades, once dragged out of a nest and left by a hole ({@link #NOT_OUT}: not set out). Saved. */
    private long outUntil = NOT_OUT;
    /** The tick it was last pulled along ({@link #dragTo}); not saved. */
    private int draggedAt = -100;
    private static final long NOT_OUT = Long.MIN_VALUE;

    public AntCorpseEntity(EntityType<? extends AntCorpseEntity> type, Level level) {
        super(type, level);
    }

    @Nullable
    public UUID getColonyId() {
        return colonyId;
    }

    public void setColonyId(@Nullable UUID colonyId) {
        this.colonyId = colonyId;
    }

    public boolean isPlaced() {
        return placed;
    }

    public void setPlaced(boolean placed) {
        this.placed = placed;
    }

    public int getAge() {
        return age;
    }

    /** Tests: it has lain {@code ticks} already (an old body). */
    public void ageForTests(int ticks) {
        age = ticks;
    }

    /** Whether it was dragged out of a nest and left by a hole (nobody takes it again: it fades there). */
    public boolean isOut() {
        return outUntil != NOT_OUT;
    }

    /** It is left outside by a hole: it fades {@code fadeTicks} from now. */
    public void setOut(long now, int fadeTicks) {
        outUntil = now + fadeTicks;
    }

    /** A termite pulls it along: it is at {@code to} this tick (it does not fall while held). */
    public void dragTo(Vec3 to) {
        draggedAt = tickCount;
        setDeltaMovement(Vec3.ZERO);
        setPos(to);
    }

    /** Whether it is held and pulled along now. */
    public boolean isDragged() {
        return tickCount - draggedAt <= 2;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        colonyId = tag.hasUUID("colony") ? tag.getUUID("colony") : null;
        placed = tag.getBoolean("placed");
        age = tag.getInt("age");
        outUntil = tag.contains("out") ? tag.getLong("out") : NOT_OUT;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (colonyId != null) {
            tag.putUUID("colony", colonyId);
        }
        tag.putBoolean("placed", placed);
        tag.putInt("age", age);
        if (outUntil != NOT_OUT) {
            tag.putLong("out", outUntil);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!isDragged()) {
            Vec3 motion = getDeltaMovement();
            setDeltaMovement(0, onGround() ? 0 : (motion.y - 0.04) * 0.98, 0);
            move(MoverType.SELF, getDeltaMovement());
        }
        age++;
        // Once a second: is the colony still there, has the body lain about too long?
        if (level().isClientSide || tickCount % 20 != 0 || !(level() instanceof ServerLevel level)) {
            return;
        }
        if (isOut()) {
            if (level.getGameTime() >= outUntil) {
                discard(); // left by the hole: it fades
            }
            return;
        }
        if (colonyId == null) {
            if (age >= LONER_DECAY_TICKS) {
                discard(); // an insect of no colony: nobody deals with it, it crumbles to dust
            }
            return;
        }
        Colony colony = ColonyManager.get(level).getColony(colonyId);
        if (colony == null) {
            discard(); // the queen died
        } else if (!placed && colony.refuse().isInRefuseChamber(level, blockPosition())) {
            placed = true; // it lies where it belongs: it stays
        } else if (!placed && age >= colony.profile().refuse().corpseDecayTicks()) {
            discard(); // nobody carried it away: it crumbles to dust
        }
    }
}
