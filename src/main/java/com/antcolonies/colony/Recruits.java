package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Recruiting to rich patches (long trails step 2, {@code docs/plans/long-trails.md}; the user's decisions of
 * 2026-10-17). Real red harvester ants (<i>Pogonomyrmex barbatus</i>, <i>P. rugosus</i>; Hölldobler 1976) forage alone
 * for scattered seeds, but a forager that finds a dense patch drags its sting tip on the way home, laying a scent line
 * from its poison gland. In the nest it runs about touching nestmates, and those follow the line out to the patch,
 * single file. The line fades within minutes unless it is laid again; each recruit that brings food back from a patch
 * still rich lays it again, so a big patch draws a growing stream, and heavy traffic wears it into a trunk trail
 * ({@link TrunkTrails}: the trips are counted there as any other).
 *
 * <p>Nothing is drawn: the scent is only seen in the line of ants streaming out (the user's decision). How rich a patch
 * must be grows with its distance home, and how many nestmates are called out grows with the patch (see
 * {@link SpeciesProfile.RecruitNumbers}). Recruits ignore poorer finds on the way and take food once at the patch.
 * The numbers (see {@link FieldLife}) recruit too: a recruiter's call that finds too few real ants waiting sends forager
 * numbers instead (they hold the patch as their spot, see {@link ForagerSpots}), and a patch the numbers find draws more
 * of them out.
 *
 * <p>Only for species whose profile has {@code food.search.recruit}. Not saved: a line fades within minutes, and a
 * recruit that loses its line still has the patch as its own spot.
 */
public class Recruits {
    /** The finder notes a point of its line about this often (ticks) while it walks home. */
    private static final int POINT_TICKS = 20;
    /** Points of a line are at least this far apart (blocks). */
    private static final double POINT_SPACING = 3.0;
    /** A finder not home with its find in this long (ticks) lays no line (it dropped its load, or got lost). */
    private static final int LAYING_TICKS = 3600;
    /** A recruit is told the way for this long (ticks); then it is an ordinary forager again. */
    private static final int RECRUIT_TICKS = 6000;
    /** A line weaker than this has faded and is forgotten. */
    private static final double FADED = 0.25;
    /** How long a recruiter home from a patch runs about the nest touching nestmates (ticks). */
    private static final int CALL_TICKS = 200;
    /** How close a recruiter must come to a nestmate to touch it (blocks). */
    private static final double TOUCH = 2.5;

    /** A finder on its way home from a rich patch: where, how much food it found there, and the points of its way home. */
    private static final class Laying {
        final BlockPos patch;
        final int food;
        final List<BlockPos> points = new ArrayList<>();
        final long since;
        long lastPoint;

        Laying(BlockPos patch, int food, long since) {
            this.patch = patch;
            this.food = food;
            this.since = since;
            points.add(patch);
        }
    }

    /** A scent line from the nest out to a patch: its points (nest end first), how strong, and when last laid. */
    public static final class Line {
        final BlockPos patch;
        List<BlockPos> points;
        double strength;
        long laidAt;

        Line(BlockPos patch, List<BlockPos> points, double strength, long laidAt) {
            this.patch = patch;
            this.points = points;
            this.strength = strength;
            this.laidAt = laidAt;
        }

        public BlockPos patch() {
            return patch;
        }

        /** The line's points, from the nest end out to the patch. */
        public List<BlockPos> points() {
            return points;
        }
    }

    /** A recruit told of a patch: where, and until when. */
    private record Told(BlockPos patch, long until) {
    }

    /** A recruiter home from a patch, calling nestmates out: where to, how many it still wants, until when, how many it got. */
    private static final class Calling {
        final BlockPos patch;
        final int food;
        int left;
        final long until;
        int real;

        Calling(BlockPos patch, int food, int left, long until) {
            this.patch = patch;
            this.food = food;
            this.left = left;
            this.until = until;
        }
    }

    private final Colony colony;
    private final Map<UUID, Laying> laying = new HashMap<>();
    private final List<Line> lines = new ArrayList<>();
    private final Map<UUID, Told> recruits = new HashMap<>();
    private final Map<UUID, Calling> calling = new HashMap<>();
    /** Calls made since the colony was loaded (for the colony list): finds recruited to, nestmates called out. */
    private int calls;
    private int calledOut;

    Recruits(Colony colony) {
        this.colony = colony;
    }

    /** The species' recruiting numbers, if its foragers recruit to rich patches. */
    public Optional<SpeciesProfile.RecruitNumbers> settings() {
        return colony.profile().food().search().flatMap(SpeciesProfile.SearchNumbers::recruit);
    }

    // --- finding a rich patch -----------------------------------------------------------------------------------------

    /**
     * A real forager found food at {@code where} (it cut a piece off a plant, or picked up a seed). If the patch round
     * the find is rich enough for its distance home, the forager lays a scent line on its way home. A recruit that has
     * reached its patch is an ordinary forager again (and lays the line again if the patch is still rich).
     */
    public void found(Level level, UUID ant, BlockPos where) {
        SpeciesProfile.RecruitNumbers numbers = settings().orElse(null);
        if (numbers == null) {
            return;
        }
        Told told = recruits.get(ant);
        if (told != null && near(told.patch(), where, numbers.patchRadius() + 2)) {
            recruits.remove(ant); // there: it found the patch it was told of
        }
        if (colony.keepsOff(level, where)) {
            laying.remove(ant); // remembered danger there (brain step 4g): no nestmates are called to it
            return;
        }
        int food = patchFood(level, where, numbers);
        double distance = flat(where);
        if (food < numbers.needed(distance)) {
            laying.remove(ant); // a poor find: no line (scattered seeds are never recruited to)
            return;
        }
        Laying already = laying.get(ant);
        if (already == null || !near(already.patch, where, numbers.patchRadius())) {
            laying.put(ant, new Laying(where.immutable(), food, level.getGameTime()));
        }
    }

    /**
     * The food in the patch round {@code where}: the pieces left on the plants that count ({@code recruit.plants}) within
     * {@code patch_radius} blocks, and the seeds lying there.
     */
    int patchFood(Level level, BlockPos where, SpeciesProfile.RecruitNumbers numbers) {
        int reach = numbers.patchRadius();
        FoodStore store = colony.food();
        int food = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    pos.set(where.getX() + dx, where.getY() + dy, where.getZ() + dz);
                    if (!level.hasChunkAt(pos)) {
                        continue;
                    }
                    SpeciesProfile.PlantFood plant = colony.profile().food().plantFor(level.getBlockState(pos));
                    if (plant != null && numbers.counts(plant)) {
                        food += Math.max(0, plant.pieces() - store.getPiecesCut(pos));
                    }
                }
            }
        }
        var seeds = colony.profile().food().itemTag();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(where).inflate(reach, 2, reach),
                item -> item.isAlive() && item.getItem().is(seeds))) {
            food += item.getItem().getCount();
        }
        return food;
    }

    // --- the way home and the call ------------------------------------------------------------------------------------

    /** Whether this real ant is laying a scent line home now, or calling nestmates out in the nest. */
    public boolean isLaying(UUID ant) {
        return laying.containsKey(ant) || calling.containsKey(ant);
    }

    /**
     * A finder walking home with food from a rich patch (called every few ticks while it carries): it notes its way, and
     * once inside the nest it calls nestmates out for a while.
     */
    public void layingTick(ServerLevel level, AbstractAntEntity ant) {
        Calling call = calling.get(ant.getUUID());
        if (call != null) {
            touch(level, ant, call, false);
            return;
        }
        Laying lay = laying.get(ant.getUUID());
        if (lay == null) {
            return;
        }
        long now = level.getGameTime();
        if (now - lay.since > LAYING_TICKS || now < lay.since || !ant.isCarryingSeed()) {
            laying.remove(ant.getUUID()); // it dropped its load or took too long: no line
            return;
        }
        if (colony.nest().isInside(level, ant.blockPosition())) {
            laying.remove(ant.getUUID());
            lay.points.add(colony.nest().getEntrance());
            call(level, ant, lay);
            return;
        }
        if (now - lay.lastPoint >= POINT_TICKS) {
            lay.lastPoint = now;
            BlockPos at = ant.blockPosition();
            if (!near(lay.points.get(lay.points.size() - 1), at, POINT_SPACING)) {
                lay.points.add(at.immutable());
            }
        }
    }

    /**
     * The finder is home: the line is laid (or laid again), and it starts calling: for a while it touches the nestmates
     * it meets, one wanted for each {@code pieces_per_recruit} of the patch (see {@link #touch}). Any it could not find
     * are forager numbers instead (see {@link #finish}).
     */
    private void call(ServerLevel level, AbstractAntEntity finder, Laying lay) {
        SpeciesProfile.RecruitNumbers numbers = settings().orElse(null);
        if (numbers == null) {
            return;
        }
        long now = level.getGameTime();
        List<BlockPos> way = new ArrayList<>(lay.points);
        java.util.Collections.reverse(way); // nest end first
        Line line = lineTo(lay.patch, numbers);
        if (line == null) {
            lines.add(new Line(lay.patch, way, 1.0, now));
        } else {
            line.strength = strength(line, now, numbers) + 1.0;
            line.laidAt = now;
            line.points = way; // the freshest way
        }
        Calling call = new Calling(lay.patch, lay.food, numbers.recruitsFor(lay.food), now + CALL_TICKS);
        calling.put(finder.getUUID(), call);
        touch(level, finder, call, true);
    }

    /**
     * The recruiter runs about the nest for a while ({@link #CALL_TICKS}) and touches the nestmates it meets that may go
     * out foraging (foragers, or any idle worker: real reserve workers switch to foraging when recruited, Gordon), the
     * nearest first; each is told the patch. At first it also reaches those waiting by the entrance.
     */
    private void touch(ServerLevel level, AbstractAntEntity finder, Calling call, boolean atTheDoor) {
        long now = level.getGameTime();
        if (call.left > 0 && finder.isAlive()) {
            AABB around = finder.getBoundingBox().inflate(TOUCH);
            if (atTheDoor) {
                double reach = Math.max(3, Math.max(colony.profile().worker().restNearEntrance(),
                        colony.profile().food().door().map(SpeciesProfile.DoorNumbers::touchReach).orElse(0.0)));
                around = around.minmax(new AABB(colony.nest().getEntrance()).inflate(reach));
            }
            Door door = colony.part(DesertParts.DOOR).orElse(null);
            List<AbstractAntEntity> met = new ArrayList<>(level.getEntitiesOfClass(AbstractAntEntity.class, around,
                    other -> other != finder && other.isAlive() && other.isColonyWorker() && colony.getId().equals(other.getColonyId())
                            && !other.hasFullMandibles() && !other.isCarryingSeed() && !(door != null && door.isOut(other.getUUID()))
                            && !recruits.containsKey(other.getUUID()) && !calling.containsKey(other.getUUID())
                            && colony.nest().isInside(level, other.blockPosition()) && other.mayDo(ColonyRole.FORAGER)));
            met.sort(Comparator.comparingDouble(other -> other.distanceToSqr(finder)));
            for (AbstractAntEntity other : met) {
                if (call.left <= 0) {
                    break;
                }
                recruits.put(other.getUUID(), new Told(call.patch, now + RECRUIT_TICKS));
                colony.part(DesertParts.FORAGER_SPOTS).ifPresent(spots -> spots.found(other.getUUID(), call.patch)); // the patch is its spot now
                call.real++;
                call.left--;
            }
        }
        if (call.left <= 0 || now >= call.until || !finder.isAlive()) {
            finish(level, finder.getUUID(), call);
        }
    }

    /** The call is over: any nestmates still wanted are forager numbers instead (they hold the patch as their spot and go out). */
    private void finish(ServerLevel level, UUID finder, Calling call) {
        calling.remove(finder);
        ForagerSpots spots = colony.part(DesertParts.FORAGER_SPOTS).orElse(null);
        int numberForagers = colony.field().foragersInside(level) - (spots == null ? 0 : spots.holders());
        int sent = Math.max(0, Math.min(call.left, numberForagers));
        if (sent > 0) {
            if (spots != null) {
                spots.addHeld(call.patch, sent, 0);
            }
            colony.part(DesertParts.DOOR).ifPresent(door -> door.recruitedNumbers(level, sent));
        }
        calls++;
        calledOut += call.real + sent;
        AntColoniesMod.LOGGER.info("[recruits] colony {}: a forager home from a rich patch ({} pieces, {} blocks out) called out {} ({} real, {} numbers)",
                colony.getId(), call.food, Math.round(flat(call.patch)), call.real + sent, call.real, sent);
    }

    /**
     * The numbers found a plant at {@code where} (see {@link FieldLife}): how many more of them the patch draws out (one
     * per {@code pieces_per_recruit}, less the finder), counting the food the colony remembers there ({@code food}); 0
     * for a patch not rich enough for its distance.
     */
    int numbersCalled(BlockPos where, int food) {
        SpeciesProfile.RecruitNumbers numbers = settings().orElse(null);
        if (numbers == null || food < numbers.needed(flat(where))) {
            return 0;
        }
        calls++;
        int more = numbers.recruitsFor(food) - 1;
        calledOut += more;
        return more;
    }

    // --- the recruits -------------------------------------------------------------------------------------------------

    /** Whether this real ant was called out to a patch and has not got there yet. */
    public boolean isRecruited(UUID ant) {
        return recruits.containsKey(ant);
    }

    /** The patch this recruit was called out to, or null. */
    @Nullable
    public BlockPos patchOf(UUID ant) {
        Told told = recruits.get(ant);
        return told == null ? null : told.patch();
    }

    /** The scent line this recruit follows out (nest end first), or null if it faded (it walks straight to its patch then). */
    @Nullable
    public List<BlockPos> lineFor(Level level, UUID ant) {
        Told told = recruits.get(ant);
        SpeciesProfile.RecruitNumbers numbers = settings().orElse(null);
        if (told == null || numbers == null) {
            return null;
        }
        Line line = lineTo(told.patch(), numbers);
        return line == null || strength(line, level.getGameTime(), numbers) < FADED ? null : line.points;
    }

    /**
     * Whether a recruit passes this food by: it is on its way to its patch and the food is not in it (recruits ignore
     * poorer finds on the way, step 3, value against distance).
     */
    public boolean passesBy(UUID ant, BlockPos food) {
        Told told = recruits.get(ant);
        if (told == null) {
            return false;
        }
        int reach = settings().map(SpeciesProfile.RecruitNumbers::patchRadius).orElse(4) + 2;
        return !near(told.patch(), food, reach);
    }

    /** The recruit's trip is over without its patch (it gave up, or the foraging time ended): an ordinary forager again. */
    public void tripOver(UUID ant) {
        recruits.remove(ant);
    }

    /** A forager died. */
    public void died(UUID ant) {
        recruits.remove(ant);
        laying.remove(ant);
        // (a recruiter that dies while calling: its call ends at the next tick, see tick)
    }

    /** Once a second: old calls and faded lines are forgotten. */
    void tick(ServerLevel level) {
        SpeciesProfile.RecruitNumbers numbers = settings().orElse(null);
        if (numbers == null) {
            return;
        }
        long now = level.getGameTime();
        recruits.values().removeIf(told -> told.until() <= now || told.until() > now + RECRUIT_TICKS);
        if (!colony.danger().isEmpty()) {
            // Brain step 4g: a patch that turned dangerous: its recruits are ordinary foragers again, its line is let go.
            recruits.values().removeIf(told -> colony.keepsOff(level, told.patch()));
            lines.removeIf(line -> colony.keepsOff(level, line.patch()));
        }
        for (Map.Entry<UUID, Calling> call : new ArrayList<>(calling.entrySet())) {
            if (now >= call.getValue().until || call.getValue().until > now + CALL_TICKS) {
                finish(level, call.getKey(), call.getValue()); // (its caller stopped ticking: died, or unloaded)
            }
        }
        for (Iterator<Line> it = lines.iterator(); it.hasNext(); ) {
            if (strength(it.next(), now, numbers) < FADED) {
                it.remove();
            }
        }
    }

    // --- helpers, the list and tests ----------------------------------------------------------------------------------

    @Nullable
    private Line lineTo(BlockPos patch, SpeciesProfile.RecruitNumbers numbers) {
        for (Line line : lines) {
            if (near(line.patch, patch, numbers.patchRadius())) {
                return line;
            }
        }
        return null;
    }

    private static double strength(Line line, long now, SpeciesProfile.RecruitNumbers numbers) {
        long age = Math.max(0, now - line.laidAt);
        return line.strength * Math.pow(0.5, age / (double) Math.max(1, numbers.halfLifeTicks()));
    }

    private double flat(BlockPos pos) {
        double dx = pos.getX() - colony.getCenter().getX();
        double dz = pos.getZ() - colony.getCenter().getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean near(BlockPos a, BlockPos b, double reach) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz <= reach * reach && Math.abs(a.getY() - b.getY()) <= 4;
    }

    /** For the tests: every scent line was last laid this many ticks earlier. */
    public void ageForTests(long ticks) {
        for (Line line : lines) {
            line.laidAt -= ticks;
        }
    }

    /** The scent lines the colony has now (for tests). */
    public List<Line> lines() {
        return lines;
    }

    /** How many real recruits are on their way to a patch now (for tests). */
    public int recruitsOut() {
        return recruits.size();
    }

    /** How many nestmates (real and numbers) have been called out since the colony was loaded (for tests). */
    public int calledOut() {
        return calledOut;
    }

    /** For the colony list: " | recruiting: 1 scent line (34 blocks out), 3 on their way, 12 called out", or empty. */
    public String describe(Level level) {
        if (settings().isEmpty() || calls == 0 && lines.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(" | recruiting: ").append(lines.size()).append(lines.size() == 1 ? " scent line" : " scent lines");
        if (!lines.isEmpty()) {
            out.append(" (");
            for (int i = 0; i < lines.size(); i++) {
                out.append(i > 0 ? ", " : "").append(Math.round(flat(lines.get(i).patch)));
            }
            out.append(" blocks out)");
        }
        return out.append(", ").append(recruits.size()).append(" on their way, ").append(calledOut).append(" called out").toString();
    }
}
