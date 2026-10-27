package com.antcolonies.entity;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyNews;
import com.antcolonies.entity.body.LostLegs;
import com.antcolonies.fight.FightNumbers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * A foe clamped on to one of an insect's legs (M8 step 2c, {@code docs/DESIGN.md} "Step 2c, clamping"): the termite
 * soldiers' locking jaws on a Matabele's leg. Real (Frank et al. 2017, "Saving the injured"): most raiders carried home
 * had a termite clinging to a leg; it is the worst handicap measured, the ant crawling; nestmates remove the termites in
 * the nest, 9 in 10 without the leg being lost.
 *
 * <p>A <b>living</b> clamp: the foe ({@link #holder}) is dragged along beside the leg, holding still, and squeezes now
 * and then ({@link FightNumbers.Hold}); the insect is slowed ({@link FightNumbers.Clamped#slowing}), fights no more and
 * goes home. A nestmate's sting that fells the holder kills it: its body drops, its <b>head</b> stays locked on the leg
 * (drawn there, {@code client.ClampedHeadLayer}), slowing a little; the insect may fight again. In the nest a nestmate
 * takes it off ({@code entity.goal.ClampCareGoal}, {@link #takeOff}). One clamp at a time (my pick).
 *
 * <p>One per insect, the server's side of it; what the client draws is synced by the insect ({@link #shown}).
 */
public final class Clamp {
    /** The slowing of an insect with a clamp on. */
    private static final ResourceLocation CLAMPED = ResourceLocation.fromNamespaceAndPath("colonyframework", "clamped");
    /** How long (ticks) a living holder may be missing (its land not loaded yet after a reload) before the clamp is let go. */
    private static final int MISSING_TICKS = 100;

    private final AbstractAntEntity insect;
    /** The leg held (its index in {@link LostLegs#LEGS}), or -1 for none. */
    private int leg = -1;
    /** Whether what holds it is a dead head (else a living foe, {@link #holderId}). */
    private boolean dead;
    @Nullable
    private UUID holderId;
    @Nullable
    private AbstractAntEntity holder;
    private ResourceLocation headLook = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/brown_terracotta.png");
    private float headSize = 0.1F;
    private int missing;
    private int squeezeIn;
    /** The nestmate working on it and until when it holds still for it (server, not saved). */
    @Nullable
    private UUID carer;
    private long heldUntil;

    Clamp(AbstractAntEntity insect) {
        this.insect = insect;
    }

    /** Whether anything is clamped on (living or a dead head). */
    public boolean isOn() {
        return leg >= 0;
    }

    /** Whether a living foe is clamped on. */
    public boolean isLiving() {
        return leg >= 0 && !dead;
    }

    public boolean isHead() {
        return leg >= 0 && dead;
    }

    /** The leg held, or -1. */
    public int leg() {
        return leg;
    }

    /** The living foe holding on, if it is in the world. */
    @Nullable
    public AbstractAntEntity holder() {
        return holder;
    }

    /**
     * M8 step 2c: a blow from {@code striker} that landed on {@code victim} clamps on, by the striker's body's
     * {@code clamp} chance, if the victim's species can be clamped ({@code fight.clamped}), its legs can be bitten
     * ({@code loses_legs}), nothing holds it yet, and the striker holds nothing. True if it clamped on (no leg is cut then).
     */
    public static boolean tryClampOn(AbstractAntEntity striker, AbstractAntEntity victim) {
        double chance = striker.clampChance();
        if (chance <= 0.0 || striker.clampingOn() != null || striker.clamp().isOn() || victim.clamp().isOn()
                || !victim.canLoseLegs() || clampedNumbers(victim) == null || striker.getRandom().nextDouble() >= chance) {
            return false;
        }
        boolean on = victim.clamp().clampOn(striker);
        if (on) {
            victim.squeak(); // M8 step 2e: it squeaks (real Megaponera stridulate when held)
        }
        return on;
    }

    /** The holder's jaws close on one of its legs left, by the dice. False if it has none left. */
    public boolean clampOn(AbstractAntEntity by) {
        int lost = insect.lostLegs();
        java.util.List<Integer> left = new java.util.ArrayList<>();
        for (int i = 0; i < LostLegs.LEGS.size(); i++) {
            if (!LostLegs.isGone(lost, i)) {
                left.add(i);
            }
        }
        if (left.isEmpty()) {
            return false;
        }
        clampOn(by, left.get(insect.getRandom().nextInt(left.size())));
        return true;
    }

    /** The holder's jaws close on this leg. */
    public void clampOn(AbstractAntEntity by, int whichLeg) {
        leg = whichLeg;
        dead = false;
        holder = by;
        holderId = by.getUUID();
        missing = 0;
        FightNumbers.Hold hold = holdOf(by);
        squeezeIn = hold != null ? hold.squeezeTicks() : Integer.MAX_VALUE;
        if (hold != null) {
            headLook = hold.headLook();
            headSize = (float) (by.getBbWidth() * hold.headSize());
        }
        by.startClamping(insect);
        insect.endFightOrder(); // it fights no more: its usual work takes it home
        changed();
    }

    /** Each server tick: the holder dragged along, its squeeze, and its death leaving the head. */
    void tick(ServerLevel level) {
        if (!isOn() || dead) {
            return;
        }
        if (holder == null || holder.isRemoved() && holder.getRemovalReason() != Entity.RemovalReason.KILLED) {
            Entity found = holderId == null ? null : level.getEntity(holderId);
            holder = found instanceof AbstractAntEntity ant && ant.isAlive() ? ant : null;
            if (holder == null) {
                if (++missing > MISSING_TICKS) {
                    letGo(); // it left the world (the land it was in unloaded): the clamp is gone with it
                }
                return;
            }
            holder.startClamping(insect);
        }
        if (!holder.isAlive() || holder.isDowned()) {
            if (holder.isAlive()) {
                holder.kill(); // a nestmate's sting felled it: it dies at once, its jaws locked
            }
            becomeHead();
            return;
        }
        if (holder.clampingOn() != insect) {
            letGo();
            return;
        }
        dragHolder();
        if (--squeezeIn <= 0) {
            FightNumbers.Hold hold = holdOf(holder);
            squeezeIn = hold != null ? hold.squeezeTicks() : Integer.MAX_VALUE;
            if (hold != null) {
                com.antcolonies.fight.Fights.squeeze(holder, insect, hold.squeezeShare());
            }
        }
    }

    /** Puts the holder beside the leg it holds, facing it (on the flat; on a wall it hangs beside the body). */
    private void dragHolder() {
        float yaw = insect.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 left = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
        double along = (1 - leg / 2) * 0.3 * insect.getBbWidth(); // front, middle, hind pair
        double side = (leg % 2 == 0 ? 1.0 : -1.0) * (insect.getBbWidth() * 0.5 + holder.getBbWidth() * 0.4);
        Vec3 at = insect.position().add(forward.scale(along)).add(left.scale(side));
        holder.setPos(at.x, insect.getY(), at.z);
        holder.setDeltaMovement(Vec3.ZERO);
        double dx = insect.getX() - at.x;
        double dz = insect.getZ() - at.z;
        float face = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        holder.setYRot(face);
        holder.setYBodyRot(face);
        holder.setYHeadRot(face);
    }

    /** The living holder died on the leg: its head stays locked on, a light slowing; it may fight again. */
    public void becomeHead() {
        if (holder != null) {
            holder.stopClamping();
        }
        holder = null;
        holderId = null;
        dead = true;
        changed();
    }

    /** The clamp is gone (the holder let go or left the world, or the leg was lost with it); nothing left on the leg. */
    public void letGo() {
        if (holder != null && holder.clampingOn() == insect) {
            holder.stopClamping();
        }
        holder = null;
        holderId = null;
        leg = -1;
        dead = false;
        carer = null;
        changed();
    }

    /** The leg it held came off (2d): a living holder drops off with it, a head goes with it. */
    void legLost(int whichLeg) {
        if (whichLeg == leg) {
            letGo();
        }
    }

    /**
     * A nestmate's work on it in the nest is done (M8 step 2c): a living holder is killed first, its head staying on (the
     * work starts over on the head); a head comes off, clean with the species' {@code clean} chance, else the leg comes
     * off with it. The head goes on the colony's leftovers ({@link ColonyNews#SCRAPS_LEFT}). True once nothing is left on.
     */
    public boolean takeOff(ServerLevel level) {
        if (!isOn()) {
            return true;
        }
        if (isLiving()) {
            if (holder != null && holder.isAlive()) {
                holder.kill();
            }
            becomeHead();
            return false;
        }
        FightNumbers.Clamped numbers = clampedNumbers(insect);
        double clean = numbers != null ? numbers.clean() : 1.0;
        int held = leg;
        if (insect.getRandom().nextDouble() < clean) {
            letGo();
        } else {
            insect.loseLeg(held); // the leg comes off with it (this lets the head go too)
        }
        Colony colony = insect.getColony();
        if (colony != null) {
            colony.news().tell(level, ColonyNews.SCRAPS_LEFT, new ColonyNews.ScrapsLeft(insect.blockPosition(), 1));
        }
        return true;
    }

    /** A nestmate works on it: it holds still for it until then (game time). */
    public void holdStillFor(UUID nestmate, long until) {
        carer = nestmate;
        heldUntil = until;
    }

    /** The nestmate working on it, or null if none is now. */
    @Nullable
    public UUID carer() {
        return carer != null && insect.level().getGameTime() < heldUntil ? carer : null;
    }

    /** Whether it holds still for a nestmate working on it now. */
    public boolean isHeldStill() {
        return carer() != null;
    }

    /** The slowing and the synced look, after any change. */
    private void changed() {
        insect.showClamp(leg, dead, headLook.toString(), headSize);
        AttributeInstance speed = insect.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(CLAMPED);
        FightNumbers.Clamped numbers = clampedNumbers(insect);
        double slowing = !isOn() || numbers == null ? 0.0 : dead ? numbers.headSlowing() : numbers.slowing();
        if (slowing > 0.0) {
            speed.addTransientModifier(new AttributeModifier(CLAMPED, -slowing, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** The insect's species' numbers for a clamp on it, or null if nothing can clamp on it. */
    @Nullable
    static FightNumbers.Clamped clampedNumbers(AbstractAntEntity insect) {
        return FightNumbers.of(insect.profile()).flatMap(FightNumbers::clamped).orElse(null);
    }

    /** How the holder's species holds on, or null if it does not clamp. */
    @Nullable
    private static FightNumbers.Hold holdOf(AbstractAntEntity holder) {
        return FightNumbers.of(holder.profile()).flatMap(FightNumbers::clamp).orElse(null);
    }

    void save(CompoundTag tag) {
        if (!isOn()) {
            return;
        }
        tag.putByte("ClampLeg", (byte) leg);
        tag.putBoolean("ClampDead", dead);
        if (holderId != null) {
            tag.putUUID("ClampBy", holderId);
        }
        tag.putString("ClampLook", headLook.toString());
        tag.putFloat("ClampSize", headSize);
    }

    void load(CompoundTag tag) {
        if (!tag.contains("ClampLeg")) {
            return;
        }
        leg = tag.getByte("ClampLeg");
        dead = tag.getBoolean("ClampDead");
        holderId = tag.hasUUID("ClampBy") ? tag.getUUID("ClampBy") : null;
        if (!dead && holderId == null) {
            leg = -1;
        }
        ResourceLocation look = ResourceLocation.tryParse(tag.getString("ClampLook"));
        if (look != null) {
            headLook = look;
        }
        headSize = tag.getFloat("ClampSize");
        squeezeIn = 400;
        changed();
    }
}
