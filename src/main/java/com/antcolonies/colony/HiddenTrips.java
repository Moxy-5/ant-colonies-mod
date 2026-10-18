package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.Foothold;
import com.antcolonies.nav.HiddenWays;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Termites underground, step 2b: hidden trips (the user's decisions of 2026-10-17, {@code docs/plans/termites-underground.md}).
 * A real Macrotermes forager walks tens of metres through the galleries unseen. Here an insect whose route runs at least
 * the species file's {@code nest.galleries.hidden_trip} steps along a closed stretch of the galleries (nothing open
 * beside it but more gallery: no open hole, no break) leaves the world for the walk and comes back at the far end of
 * the stretch after the time the walk takes, at its own pace ({@link com.antcolonies.nav.AntNavigator}). It is the same
 * insect, with its job, its load and its route: it walks on from there. Short stretches are walked for real.
 *
 * <p>The user's rule: <b>if anything breaks into a gallery, every insect travelling in it shows up as a real one where it
 * would be along the way.</b> Once a second each trip's stretch is looked at: a block of it filled, a block beside it
 * opened (a player digging, an explosion, water, later an ant raider breaking the roof at a feeding spot), or a creature
 * that is not one of the colony's in it, and the insect is put back at the floor it would have reached by then. A hole
 * the termites open onto the stretch counts too (it is a way in): those insects simply walk on.
 *
 * <p>The travellers count among the colony's real mobs (their cap, {@link Population}), so none is made to take their
 * place and none is too many when they come back. If the colony goes out of range while they travel, they are folded
 * into the numbers with what they carry, as any insect left at the edge of the loaded land ({@link AwayLife}); if the
 * far end is not loaded when they arrive, they wait there for a minute, then the same. Saved with the colony: the
 * insect as it was when it went under (a game loaded again puts it back without its job, as after any load).
 *
 * <p>Species-neutral: any colony with galleries switched on; who travels is any insect the colony may count
 * ({@link AbstractAntEntity#nestCaste}). Nothing here decides where an insect goes: the mind chose its goal, the legs its
 * route; this only hides a stretch of it.
 */
public class HiddenTrips implements HiddenWays {
    /** How often (ticks) the hidden cells are worked out again from the colony's tunnels. */
    private static final int CELLS_TICKS = 200;
    /** How long (ticks) a traveller whose far end is not loaded waits there before it is folded into the numbers. */
    private static final int WAIT_UNLOADED = 1200;

    /** One insect travelling unseen along a stretch of the galleries. */
    private static final class Trip {
        final UUID id;
        final String caste;
        /** The insect itself while this game runs (it goes on with its route); null after a load. */
        @Nullable
        AbstractAntEntity insect;
        /** The insect as saved (filled when the colony is saved, or read from a save). */
        @Nullable
        CompoundTag body;
        final List<Foothold> run;
        final Set<BlockPos> cells;
        final long start;
        final long arrive;

        Trip(UUID id, String caste, @Nullable AbstractAntEntity insect, @Nullable CompoundTag body, List<Foothold> run, long start, long arrive) {
            this.id = id;
            this.caste = caste;
            this.insect = insect;
            this.body = body;
            this.run = List.copyOf(run);
            this.cells = new HashSet<>();
            for (Foothold foothold : run) {
                cells.add(foothold.cell());
            }
            this.start = start;
            this.arrive = arrive;
        }

        /** The place in the run (an index) it has reached at this time, gone back to the last floor (where it can be put). */
        int reached(long now) {
            double done = arrive <= start ? 1.0 : Math.min(1.0, Math.max(0.0, (now - start) / (double) (arrive - start)));
            int index = (int) Math.floor(done * (run.size() - 1));
            while (index > 0 && !run.get(index).isFloor()) {
                index--;
            }
            return index;
        }
    }

    private final Colony colony;
    private final List<Trip> trips = new ArrayList<>();
    /** The galleries' cells (every tunnel's, the holes not), worked out now and then. Not saved. */
    private Set<BlockPos> cells = Set.of();
    private long cellsAt = Long.MIN_VALUE;
    private int cellsFrom = -1;
    @Nullable
    private ServerLevel cellsLevel;
    /** Trips made, came back at the far end, put back by a break-in, folded into the numbers (since the game started; for the list and tests). */
    private int made;
    private int arrived;
    private int brokenInto;
    private int folded;
    /** Tests only: hidden trips on or off whatever the galleries' switch says (null: with the galleries). Not saved. */
    @Nullable
    private Boolean onForTests;

    HiddenTrips(Colony colony) {
        this.colony = colony;
    }

    /** Whether insects of this colony travel unseen: the galleries are switched on and the species file asks for it. */
    public boolean isOn() {
        if (onForTests != null) {
            return onForTests;
        }
        Galleries galleries = colony.nest().galleries();
        return galleries.isOn() && galleries.numbers().hiddenTrip() > 0;
    }

    /** Tests only: hidden trips on or off for this colony (null: with the galleries' switch). */
    public void setOnForTests(@Nullable Boolean on) {
        onForTests = on;
    }

    /** The colony's hidden ways for an insect's legs, or null while there are none (switched off). */
    @Nullable
    public HiddenWays ways(ServerLevel level) {
        if (!isOn()) {
            return null;
        }
        refreshCells(level);
        return cells.isEmpty() ? null : this;
    }

    private void refreshCells(ServerLevel level) {
        List<NestPlan.ForagingTunnel> tunnels = colony.foragingHoles().tunnels(level);
        long now = level.getGameTime();
        if (cellsLevel == level && tunnels.size() == cellsFrom && now - cellsAt < CELLS_TICKS && cellsAt != Long.MIN_VALUE) {
            return;
        }
        Set<BlockPos> found = new HashSet<>();
        Set<BlockPos> holes = new HashSet<>();
        for (NestPlan.ForagingTunnel tunnel : tunnels) {
            found.addAll(tunnel.tunnel());
            holes.add(tunnel.hole());
        }
        found.removeAll(holes);
        cells = found;
        cellsFrom = tunnels.size();
        cellsAt = now;
        cellsLevel = level;
    }

    // --- the legs ask (HiddenWays) ---

    @Override
    public boolean isHidden(BlockPos cell) {
        ServerLevel level = cellsLevel;
        return level != null && cells.contains(cell) && isClosed(level, cell);
    }

    @Override
    public int shortest() {
        return Math.max(1, colony.nest().galleries().numbers().hiddenTrip());
    }

    @Override
    public boolean goUnder(AbstractAntEntity insect, List<Foothold> run, long ticks) {
        if (!(insect.level() instanceof ServerLevel level) || insect.nestCaste() == null || !insect.isAlive() || insect.isPassenger()
                || insect.isVehicle() || insect.isLeashed() || run.size() < 2) {
            return false;
        }
        long now = level.getGameTime();
        trips.add(new Trip(insect.getUUID(), insect.nestCaste(), insect, null, run, now, now + Math.max(1, ticks)));
        made++;
        // Gone, not dead: not saved with its chunk either (the trip saves it), and nothing of the colony's counts changes.
        insect.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        ColonyManager.get(level).markChanged();
        AntColoniesMod.LOGGER.debug("[hidden] {} {} goes under at {} for {} steps, {} ticks, out at {}", insect.nestCaste(), insect.getId(),
                run.get(0).cell().toShortString(), run.size() - 1, ticks, run.get(run.size() - 1).cell().toShortString());
        return true;
    }

    // --- once a second ---

    /** Once a second while the nest is loaded (from {@link Colony#tick}): break-ins, arrivals. */
    void tick(ServerLevel level) {
        if (trips.isEmpty()) {
            return;
        }
        refreshCells(level);
        long now = level.getGameTime();
        boolean changed = false;
        for (Iterator<Trip> it = trips.iterator(); it.hasNext(); ) {
            Trip trip = it.next();
            String broken = brokenInto(level, trip);
            if (broken != null) {
                int index = trip.reached(now);
                if (level.isPositionEntityTicking(trip.run.get(index).cell()) && comeOut(level, trip, index)) {
                    brokenInto++;
                    AntColoniesMod.LOGGER.debug("[hidden] {} back at {} ({} of {} steps): {}", trip.caste, trip.run.get(index).cell().toShortString(),
                            index, trip.run.size() - 1, broken);
                } else {
                    foldIn(level, trip);
                }
                it.remove();
                changed = true;
            } else if (now >= trip.arrive) {
                Foothold end = trip.run.get(trip.run.size() - 1);
                if (level.isPositionEntityTicking(end.cell())) {
                    if (comeOut(level, trip, trip.run.size() - 1)) {
                        arrived++;
                    } else {
                        foldIn(level, trip);
                    }
                    it.remove();
                    changed = true;
                } else if (now - trip.arrive > WAIT_UNLOADED) {
                    foldIn(level, trip);
                    it.remove();
                    changed = true;
                }
            }
        }
        if (changed) {
            ColonyManager.get(level).markChanged();
        }
    }

    /** Once a second while the colony is out of range ({@link AwayLife#tickAway}): the travellers walk home as numbers. */
    void tickAway(ServerLevel level) {
        if (trips.isEmpty()) {
            return;
        }
        for (Trip trip : trips) {
            foldIn(level, trip);
        }
        trips.clear();
        ColonyManager.get(level).markChanged();
    }

    /** Why this trip's stretch is broken into now, or null if it is closed as it was. */
    @Nullable
    private String brokenInto(ServerLevel level, Trip trip) {
        for (BlockPos cell : trip.cells) {
            if (!level.isLoaded(cell)) {
                continue;
            }
            if (!isOpen(level, cell)) {
                return "filled at " + cell.toShortString();
            }
            BlockPos opened = openingBeside(level, cell);
            if (opened != null) {
                return "opened at " + opened.toShortString();
            }
        }
        AABB box = null;
        for (BlockPos cell : trip.cells) {
            AABB one = new AABB(cell);
            box = box == null ? one : box.minmax(one);
        }
        if (box != null) {
            for (Entity entity : level.getEntities((Entity) null, box, e -> e instanceof LivingEntity && e.isAlive() && !isNestmate(e)
                    && !(e instanceof Player player && player.isSpectator()) && trip.cells.contains(e.blockPosition()))) {
                return "a " + entity.getType().getDescription().getString() + " in it at " + entity.blockPosition().toShortString();
            }
        }
        return null;
    }

    private boolean isNestmate(Entity entity) {
        return entity instanceof AbstractAntEntity insect && colony.getId().equals(insect.getColonyId());
    }

    /** Whether this cell is closed all round: nothing open beside it but more of the galleries. */
    private boolean isClosed(ServerLevel level, BlockPos cell) {
        return openingBeside(level, cell) == null;
    }

    /** An open cell beside this one that is not one of the galleries (a break, an open hole, a cave), or null. */
    @Nullable
    private BlockPos openingBeside(ServerLevel level, BlockPos cell) {
        for (Direction side : Direction.values()) {
            BlockPos next = cell.relative(side);
            if (!cells.contains(next) && level.isLoaded(next) && isOpen(level, next)) {
                return next;
            }
        }
        return null;
    }

    /** Open to walk or see through: nothing solid in it (air, a plant, water). */
    private static boolean isOpen(ServerLevel level, BlockPos cell) {
        return level.getBlockState(cell).getCollisionShape(level, cell).isEmpty();
    }

    /** Puts the insect back in the world at this place of its run; it walks on from there. False if it could not be. */
    private boolean comeOut(ServerLevel level, Trip trip, int index) {
        Foothold where = trip.run.get(index);
        AbstractAntEntity insect = trip.insect;
        if (insect == null) {
            CompoundTag body = trip.body;
            Entity made = body == null ? null : EntityType.create(body, level).orElse(null);
            if (!(made instanceof AbstractAntEntity loaded)) {
                return false;
            }
            insect = loaded;
        } else {
            insect.revive();
        }
        if (level.getEntity(insect.getUUID()) != null) {
            return false; // already in the world (should not be): never two of one insect
        }
        BlockPos cell = where.cell();
        insect.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, insect.getYRot(), 0.0F);
        insect.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (!level.addFreshEntity(insect)) {
            return false;
        }
        insect.getNavigator().cameUp(index, where);
        return true;
    }

    /** The traveller becomes one of the numbers with what it carries (it has walked home), as one left in unloaded land. */
    private void foldIn(ServerLevel level, Trip trip) {
        AbstractAntEntity insect = trip.insect;
        if (insect == null && trip.body != null) {
            Entity made = EntityType.create(trip.body, level).orElse(null);
            insect = made instanceof AbstractAntEntity loaded ? loaded : null;
        }
        if (insect != null) {
            colony.away().foldInNow(level, insect);
        } else {
            colony.population().foldIn(level, trip.caste, level.getGameTime(), Colony.NOT_SET);
        }
        folded++;
    }

    // --- what others ask ---

    /** How many of this caste are travelling unseen now (they count as real mobs). */
    public int travelling(String caste) {
        int count = 0;
        for (Trip trip : trips) {
            if (trip.caste.equals(caste)) {
                count++;
            }
        }
        return count;
    }

    /** How many are travelling unseen now. */
    public int travelling() {
        return trips.size();
    }

    /** Who is travelling unseen now. */
    public List<UUID> travellers() {
        return trips.stream().map(trip -> trip.id).toList();
    }

    /** Whether this insect is travelling unseen now. */
    public boolean isTravelling(UUID id) {
        return trips.stream().anyMatch(trip -> trip.id.equals(id));
    }

    /** Tests: the cells of this insect's hidden stretch (empty if it is not travelling). */
    public List<BlockPos> runOf(UUID id) {
        for (Trip trip : trips) {
            if (trip.id.equals(id)) {
                return trip.run.stream().map(Foothold::cell).toList();
            }
        }
        return List.of();
    }

    /** Tests: where this insect would be on its stretch now (null if it is not travelling). */
    @Nullable
    public BlockPos whereNow(UUID id, long now) {
        for (Trip trip : trips) {
            if (trip.id.equals(id)) {
                return trip.run.get(trip.reached(now)).cell();
            }
        }
        return null;
    }

    /** Tests: the game times this insect's trip started and ends (null if it is not travelling). */
    @Nullable
    public long[] timesOf(UUID id) {
        for (Trip trip : trips) {
            if (trip.id.equals(id)) {
                return new long[] {trip.start, trip.arrive};
            }
        }
        return null;
    }

    public int made() {
        return made;
    }

    public int arrived() {
        return arrived;
    }

    public int brokenInto() {
        return brokenInto;
    }

    public int folded() {
        return folded;
    }

    /** For {@code /antcolonies list}. */
    public String describe() {
        if (!isOn()) {
            return "";
        }
        return " | hidden trips: " + trips.size() + " underway (" + made + " made, " + arrived + " out at the far end, " + brokenInto
                + " broken into, " + folded + " folded in)";
    }

    // --- saving ---

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Trip trip : trips) {
            CompoundTag body = trip.body;
            if (trip.insect != null) {
                body = trip.insect.saveWithoutId(new CompoundTag());
                body.putString("id", EntityType.getKey(trip.insect.getType()).toString());
            }
            if (body == null) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", trip.id);
            entry.putString("caste", trip.caste);
            entry.put("body", body);
            ListTag run = new ListTag();
            for (Foothold foothold : trip.run) {
                CompoundTag one = new CompoundTag();
                one.putInt("x", foothold.cell().getX());
                one.putInt("y", foothold.cell().getY());
                one.putInt("z", foothold.cell().getZ());
                one.putInt("surface", foothold.surface().get3DDataValue());
                one.putBoolean("channel", foothold.isChannel());
                run.add(one);
            }
            entry.put("run", run);
            entry.putLong("start", trip.start);
            entry.putLong("arrive", trip.arrive);
            list.add(entry);
        }
        tag.put("hiddenTrips", list);
    }

    void load(CompoundTag tag) {
        trips.clear();
        if (!tag.contains("hiddenTrips")) {
            return; // an older save: nobody travelling
        }
        ListTag list = tag.getList("hiddenTrips", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            List<Foothold> run = new ArrayList<>();
            ListTag steps = entry.getList("run", Tag.TAG_COMPOUND);
            for (int k = 0; k < steps.size(); k++) {
                CompoundTag one = steps.getCompound(k);
                run.add(new Foothold(new BlockPos(one.getInt("x"), one.getInt("y"), one.getInt("z")), Direction.from3DDataValue(one.getInt("surface")),
                        one.getBoolean("channel")));
            }
            if (run.size() >= 2) {
                trips.add(new Trip(entry.getUUID("uuid"), entry.getString("caste"), null, entry.getCompound("body"), run, entry.getLong("start"),
                        entry.getLong("arrive")));
            }
        }
    }
}
