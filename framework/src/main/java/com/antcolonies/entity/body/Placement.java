package com.antcolonies.entity.body;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * An animal set on a place by the game, not walking there (the Motion Studio, the new phase 2, step 4, idea 51,
 * 2026-11-27): a holder on its prey, a feeder lying at its spot on a carcass, a catch carried in jaws. Before, each such
 * animal met the game's other forces one bug at a time: a cow shoving a lying feeder 0.05 blocks a tick, a place set
 * before the game's physics so the walk's leftovers moved it again, a rider drawn turned to its mount (BUGS 421). The
 * rule, one place for every animal:
 * <ul>
 * <li><b>Placed by its own game code</b> ({@link #place}, each tick it stays, on the server): set there after the game's
 * own physics ({@link #afterTick}, the end of its tick), so nothing of its walk or a push moves it off. Its height stays
 * the game's own: it lies on whatever is under it, as any animal does (one over a ledge's edge held by it, not dropped to
 * the ground under its middle; the review of 2026-11-27).</li>
 * <li><b>Carried by a mount that does not seat it</b> (Minecraft's {@code shouldRiderSit} false; the mount's
 * {@code positionRider} calls {@link #seat}): set there on the server and on every screen alike, after its own tick
 * (Minecraft places riders after they tick).</li>
 * <li>Either way: it pushes nothing and nothing pushes it (an animal asks {@link #placed} in its {@code isPushable} and
 * {@code doPush}; Minecraft never pushes a rider and its mount, nor a mount); its walking leftovers are cleared (its route,
 * its steering, its speed), and its body and head face the place's heading, so Minecraft's own body turning and looking
 * do not turn it.</li>
 * <li>It is drawn at its own heading ({@code client/BoneMeshRenderer}: GeckoLib draws every rider turned to its mount,
 * Minecraft only one its mount seats).</li>
 * </ul>
 * Species-neutral: the lion holds and feeds through it, the crocodile and the otter carry through it.
 */
public final class Placement {
    private final Mob mob;
    private double x, z;
    private float yaw;
    private int placedAt = Integer.MIN_VALUE / 2;

    public Placement(Mob mob) {
        this.mob = mob;
    }

    /**
     * Puts it on a place this tick (across the ground: its height is the game's), facing that way (its own game code, on
     * the server; asked every tick it stays).
     */
    public void place(double x, double z, float yaw) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
            return;                                     // (a broken sum never puts an animal out of the world)
        }
        this.x = x;
        this.z = z;
        this.yaw = yaw;
        this.placedAt = mob.tickCount;
    }

    /** Whether it is placed now: put on a place this tick or the last, or carried by a mount that does not seat it. */
    public boolean placed() {
        return mob.tickCount - placedAt <= 1 || carried(mob);
    }

    /** Whether a mount carries it without seating it (it places the rider itself, {@link #seat}). */
    public static boolean carried(Entity e) {
        Entity mount = e.getVehicle();
        return mount != null && !mount.shouldRiderSit();
    }

    /** At the end of its tick, after the game's own physics: on its place (put there this tick), still, facing its way. */
    public void afterTick() {
        if (mob.tickCount != placedAt || mob.isPassenger()) {
            return;
        }
        mob.setPos(x, mob.getY(), z);
        face(mob, yaw);
        still(mob);
    }

    /**
     * A mount sets a rider it carries (from its {@code positionRider}, on the server and every screen alike): there,
     * facing that way, still. A broken sum (not a number) leaves it where it was.
     */
    public static void seat(Entity rider, Entity.MoveFunction move, double x, double y, double z, float yaw) {
        if (!Float.isFinite(yaw)) {
            seat(rider, move, x, y, z);
            return;
        }
        seat(rider, move, x, y, z);
        face(rider, yaw);
    }

    /** The same for a rider whose heading is its own (a carried player looks where it likes). */
    public static void seat(Entity rider, Entity.MoveFunction move, double x, double y, double z) {
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
            move.accept(rider, x, y, z);
        }
        still(rider);
    }

    /** Its body, head and heading all one way (Minecraft's body turning and looking otherwise pull them apart). */
    private static void face(Entity e, float yaw) {
        e.setYRot(yaw);
        if (e instanceof LivingEntity living) {
            living.yBodyRot = yaw;
            living.yHeadRot = yaw;
        }
    }

    /** No walking left over: no speed, no route, no steering (it would walk on the tick it is let go). */
    private static void still(Entity e) {
        e.setDeltaMovement(Vec3.ZERO);
        if (e instanceof Mob m) {
            if (!m.level().isClientSide) {
                m.getNavigation().stop();
            }
            m.setZza(0.0F);
            m.setXxa(0.0F);
            m.setYya(0.0F);
            m.setSpeed(0.0F);
            m.setJumping(false);
        }
    }
}
