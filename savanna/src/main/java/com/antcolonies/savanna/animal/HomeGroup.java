package com.antcolonies.savanna.animal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * A group of land animals that share a home (lifted out of the Cape buffalo when the lions came, 2026-11-25,
 * {@code docs/plans/lion.md}, "Shared with the buffalo"): a herd's home is its pan's middle, a pride's its den. The home is
 * the group's only name: a mate is one of its kind with the same home. Each member works everything out from its own
 * list of mates, never by being told: its rank (the order mates set off in and take spots round a place by), whether it
 * is its group's nearest to someone, a spot of its own round a place, and a place from dice (the same for every mate,
 * with no world read).
 */
public final class HomeGroup {
    private HomeGroup() {
    }

    /** Mates' spots round a place are spread by the golden angle (about 137.5 degrees) in their order. */
    public static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    /** The living ones of its kind within {@code reach} (16 up and down) with the same home, nearest first. */
    public static <T extends Mob> List<T> find(T self, Class<T> kind, @Nullable BlockPos home, Function<T, BlockPos> homeOf, double reach) {
        if (home == null) {
            return List.of();
        }
        List<T> found = new ArrayList<>(self.level().getEntitiesOfClass(kind, self.getBoundingBox().inflate(reach, 16.0, reach),
                o -> o != self && o.isAlive() && home.equals(homeOf.apply(o))));
        found.sort(Comparator.comparingDouble(self::distanceToSqr));
        return List.copyOf(found);
    }

    /** Its place in its group's order (0 first): how many of its mates come before it by {@code order}. */
    public static <T extends Entity> int rank(T self, List<? extends T> mates, Comparator<T> order) {
        int r = 0;
        for (T m : mates) {
            if (order.compare(m, self) < 0) {
                r++;
            }
        }
        return r;
    }

    /** The plain order: by UUID (kept through saves). */
    public static int byId(Entity a, Entity b) {
        return a.getUUID().compareTo(b.getUUID());
    }

    /** Whether it is its group's nearest member to {@code e} (of those {@code counts} lets in), ties by id. */
    public static boolean nearestTo(Entity self, List<? extends Entity> mates, Entity e) {
        double mine = self.distanceToSqr(e);
        for (Entity m : mates) {
            if (!m.isAlive()) {
                continue;
            }
            double theirs = m.distanceToSqr(e);
            if (theirs < mine - 1.0E-6 || Math.abs(theirs - mine) <= 1.0E-6 && m.getUUID().compareTo(self.getUUID()) < 0) {
                return false;
            }
        }
        return true;
    }

    /** The nearest of its kind within {@code reach} that has a home, or null (a spawn egg's animal joins its group). */
    @Nullable
    public static <T extends Mob> T nearestWithHome(T self, Class<T> kind, double reach, Function<T, BlockPos> homeOf) {
        T nearest = null;
        double best = reach * reach;
        for (T o : self.level().getEntitiesOfClass(kind, self.getBoundingBox().inflate(reach), o -> o != self && homeOf.apply(o) != null)) {
            double d = self.distanceToSqr(o);
            if (d <= best) {
                best = d;
                nearest = o;
            }
        }
        return nearest;
    }

    /** A mate's own spot round a place: its order turned by the golden angle, this far out (blocks). */
    public static Vec3 offset(int rank, double radius) {
        double a = rank * GOLDEN_ANGLE;
        return new Vec3(Math.cos(a) * radius, 0.0, Math.sin(a) * radius);
    }

    /**
     * A place from dice only (a home, a number such as a bout or a night, and a salt per use): an angle and a distance
     * {@code near} plus up to {@code spread} from the home, so every mate finds the same place whatever land is loaded.
     */
    public static Vec3 dicePoint(BlockPos home, long number, long salt, double near, double spread) {
        RandomSource dice = RandomSource.create(home.asLong() ^ number * salt);
        double angle = dice.nextDouble() * Math.PI * 2.0;
        double far = near + dice.nextDouble() * spread;
        return new Vec3(home.getX() + 0.5 + Math.cos(angle) * far, home.getY(), home.getZ() + 0.5 + Math.sin(angle) * far);
    }

    /** Flat distance between two points. */
    public static double flat(Vec3 a, double x, double z) {
        double dx = a.x - x, dz = a.z - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Flat distance from a point to a block's middle. */
    public static double flat(Vec3 a, BlockPos b) {
        return flat(a, b.getX() + 0.5, b.getZ() + 0.5);
    }
}
