package com.antcolonies.savanna.entity;

import net.minecraft.nbt.CompoundTag;

/**
 * What one Matabele scout remembers of its own searching (raid scouts search like real ants, 2026-11-04; the colony
 * keeps no map of searched land): its favourite direction out of the nest, which it goes back to trip after trip (route
 * and site fidelity in ponerine ants, Fresneau 1985), how far out it searches now (its reach), how many trips in a row it
 * came home empty, and how far out its last find was. Saved on the ant. The rules that change it are in
 * {@code colony.Raids} ({@code cameHomeEmpty}, {@code foundBy}); the walk that uses it is {@code MatabeleScoutGoal}.
 */
public final class ScoutMemory {
    private static final String TAG = "matabeleScout";

    /** Its favourite direction (radians, Minecraft's flat angle: 0 east, a quarter turn south); NaN before its first trip. */
    private double heading = Double.NaN;
    /** How far from the nest it searches now (blocks); 0 before its first trip. */
    private int reach;
    /** Trips in a row it searched and came home empty. */
    private int emptyTrips;
    /** How far from the nest its last find was (blocks), -1 none. */
    private int lastFind = -1;

    public boolean hasHeading() {
        return !Double.isNaN(heading);
    }

    public double heading() {
        return heading;
    }

    public void setHeading(double heading) {
        this.heading = wrap(heading);
    }

    /** Its reach, or {@code first} (the species' first range) before its first trip. */
    public int reach(int first) {
        return reach > 0 ? reach : first;
    }

    public void setReach(int reach) {
        this.reach = reach;
    }

    public int emptyTrips() {
        return emptyTrips;
    }

    public void setEmptyTrips(int emptyTrips) {
        this.emptyTrips = emptyTrips;
    }

    public int lastFind() {
        return lastFind;
    }

    public void setLastFind(int lastFind) {
        this.lastFind = lastFind;
    }

    /** An angle brought into 0 to a full turn. */
    public static double wrap(double angle) {
        double full = Math.PI * 2;
        double wrapped = angle % full;
        return wrapped < 0 ? wrapped + full : wrapped;
    }

    void save(CompoundTag tag) {
        if (!hasHeading() && reach <= 0) {
            return;
        }
        CompoundTag mine = new CompoundTag();
        if (hasHeading()) {
            mine.putDouble("heading", heading);
        }
        mine.putInt("reach", reach);
        mine.putInt("emptyTrips", emptyTrips);
        mine.putInt("lastFind", lastFind);
        tag.put(TAG, mine);
    }

    void load(CompoundTag tag) {
        if (!tag.contains(TAG)) {
            return;
        }
        CompoundTag mine = tag.getCompound(TAG);
        heading = mine.contains("heading") ? wrap(mine.getDouble("heading")) : Double.NaN;
        reach = mine.getInt("reach");
        emptyTrips = mine.getInt("emptyTrips");
        lastFind = mine.contains("lastFind") ? mine.getInt("lastFind") : -1;
    }
}
