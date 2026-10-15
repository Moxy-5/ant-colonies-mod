package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Bare patches and grass growing back (the user's plan of 2026-10-14, part 3 of the termite plan; numbers in the
 * profile's {@code food.regrowth}). Real Macrotermes cut the dry grass round their foraging holes down to the ground,
 * so a busy hole sits in a small patch of bare soil, while the grass farther out is only grazed; savanna grass grows
 * back from its roots, so nothing is stripped for good. Here every tuft the colony eats away is remembered and grows
 * back, green, some days later; one eaten near a hole leaves the grass block under it bare (coarse dirt) until then.
 * Grass never grows back into the nest, a hole or a runway: it waits until the spot is free again.
 */
public class BarePatches {
    /** At most this many eaten tufts are remembered (the oldest grow back first anyway). */
    private static final int MAX_TUFTS = 4096;
    /** A tuft whose spot is taken by the colony (a hole, a runway, the nest) tries again this much later (ticks). */
    private static final int RETRY_TICKS = 6000;
    /** The tufts are looked at this often (ticks): growing back is slow, so there is no hurry. */
    private static final int CHECK_TICKS = 200;

    private final Colony colony;

    /** A tuft eaten away at {@code spot}: when it grows back, and whether the ground under it was made bare. */
    private record Tuft(BlockPos spot, long growsAt, boolean madeBare) {
    }

    /** The tufts eaten away and not grown back yet, oldest first. Saved. */
    private final List<Tuft> tufts = new ArrayList<>();
    /** When the tufts are next looked at. Not saved. */
    private long nextCheck;

    BarePatches(Colony colony) {
        this.colony = colony;
    }

    /**
     * A plant ({@code eaten}, as it was) has just been eaten away at {@code pos}: if it is one that grows back, it is
     * remembered, and if it stood near a foraging hole the grass block under it turns bare.
     */
    void eatenAway(Level level, BlockPos pos, BlockState eaten) {
        eatenAway(level, pos, eaten, level.getGameTime());
    }

    /** As above; {@code eatenAt}: when it was eaten (the numbers may have eaten it while its land was not loaded). */
    void eatenAway(Level level, BlockPos pos, BlockState eaten, long eatenAt) {
        SpeciesProfile.Regrowth numbers = colony.profile().food().regrowth().orElse(null);
        if (numbers == null || !numbers.regrows(eaten)) {
            return;
        }
        // The bottom of the tuft (a tall one is eaten from its top half too): the first spot over solid ground.
        BlockPos spot = pos;
        for (int down = 0; down < 2 && level.getBlockState(spot.below()).isAir(); down++) {
            spot = spot.below();
        }
        BlockPos ground = spot.below();
        boolean bare = false;
        BlockState bareGround = numbers.bareGroundState();
        if (bareGround != null && level.getBlockState(ground).is(Blocks.GRASS_BLOCK) && nearAHole(level, spot, numbers.bareRadius())) {
            level.setBlock(ground, bareGround, 3);
            bare = true;
        }
        if (tufts.size() >= MAX_TUFTS) {
            tufts.remove(0);
        }
        tufts.add(new Tuft(spot.immutable(), eatenAt + numbers.ticks(), bare));
    }

    /** Whether the spot is within {@code radius} blocks (sideways, a few up or down) of one of the colony's holes. */
    private boolean nearAHole(Level level, BlockPos spot, int radius) {
        if (radius <= 0) {
            return false;
        }
        for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
            BlockPos hole = tunnel.hole();
            if (Math.abs(hole.getX() - spot.getX()) <= radius && Math.abs(hole.getZ() - spot.getZ()) <= radius
                    && Math.abs(hole.getY() + 1 - spot.getY()) <= 3) {
                return true;
            }
        }
        return false;
    }

    /** Called once a second: tufts whose time has come grow back where their spot is loaded and free. */
    void tick(ServerLevel level) {
        if (tufts.isEmpty() || level.getGameTime() < nextCheck) {
            return;
        }
        nextCheck = level.getGameTime() + CHECK_TICKS;
        SpeciesProfile.Regrowth numbers = colony.profile().food().regrowth().orElse(null);
        BlockState young = numbers == null ? null : numbers.growsBackState();
        long now = level.getGameTime();
        List<Tuft> later = new ArrayList<>();
        for (Iterator<Tuft> it = tufts.iterator(); it.hasNext(); ) {
            Tuft tuft = it.next();
            if (tuft.growsAt() > now || !level.isLoaded(tuft.spot()) || !level.isLoaded(tuft.spot().below())) {
                continue;
            }
            it.remove();
            if (young == null) {
                unbare(level, tuft);
                continue;
            }
            BlockPos spot = tuft.spot();
            if (takenByTheColony(level, spot)) {
                later.add(new Tuft(spot, now + RETRY_TICKS, tuft.madeBare()));
                continue;
            }
            unbare(level, tuft);
            if (!level.getBlockState(spot).isAir() || !level.getFluidState(spot).isEmpty() || !young.canSurvive(level, spot)) {
                continue; // something else is there now (a block, water), or the ground is gone: no tuft
            }
            level.setBlock(spot, young, 3);
        }
        tufts.addAll(later);
    }

    /** Whether the spot is one of the colony's own: in the nest, over a hole, in a runway, or a termite stands in it. */
    private boolean takenByTheColony(Level level, BlockPos spot) {
        if (colony.foragingHoles().isHole(level, spot.below()) || colony.foragingHoles().isHole(level, spot)
                || colony.nest().nestCells(level).contains(spot)) {
            return true;
        }
        for (FoodWays.Runway runway : colony.nest().foodWays().getRunways()) {
            if (runway.cells().contains(spot)) {
                return true;
            }
        }
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(spot)).isEmpty();
    }

    /** The ground made bare under a tuft turns back to grass (the grass spreads in again as the tuft grows back). */
    private void unbare(Level level, Tuft tuft) {
        SpeciesProfile.Regrowth numbers = colony.profile().food().regrowth().orElse(null);
        BlockState bareGround = numbers == null ? null : numbers.bareGroundState();
        BlockPos ground = tuft.spot().below();
        if (tuft.madeBare() && bareGround != null && level.getBlockState(ground).is(bareGround.getBlock())
                && level.getBlockState(tuft.spot()).isAir()) {
            level.setBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        }
    }

    /** How many eaten tufts are waiting to grow back, and how many of them left bare ground. For the list command and tests. */
    public String describe() {
        long bare = tufts.stream().filter(Tuft::madeBare).count();
        return tufts.size() + " eaten tufts growing back (" + bare + " on bare ground by the holes)";
    }

    /** How many eaten tufts are waiting to grow back. For tests. */
    public int waiting() {
        return tufts.size();
    }

    /** Every eaten tuft grows back on the next look (for tests). */
    /** When the tuft eaten away at this spot grows back, or -1 if none is remembered there. For tests. */
    public long growsAtForTests(BlockPos spot) {
        return tufts.stream().filter(tuft -> tuft.spot().equals(spot)).mapToLong(Tuft::growsAt).findFirst().orElse(-1);
    }

    public void growBackSoonForTests(Level level) {
        tufts.replaceAll(tuft -> new Tuft(tuft.spot(), level.getGameTime(), tuft.madeBare()));
        nextCheck = 0;
    }

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Tuft tuft : tufts) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("spot", tuft.spot().asLong());
            entry.putLong("growsAt", tuft.growsAt());
            entry.putBoolean("bare", tuft.madeBare());
            list.add(entry);
        }
        tag.put("regrowth", list);
    }

    void load(CompoundTag tag) {
        tufts.clear();
        for (Tag t : tag.getList("regrowth", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            tufts.add(new Tuft(BlockPos.of(entry.getLong("spot")), entry.getLong("growsAt"), entry.getBoolean("bare")));
        }
    }
}
