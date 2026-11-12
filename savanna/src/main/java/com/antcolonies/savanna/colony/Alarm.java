package com.antcolonies.savanna.colony;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ForagingHoles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.fight.Fights;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank;

import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The colony's alarm (species with {@code alarm} in their profile: termites). The termite alarm rework, step 3
 * (2026-11-12, {@code docs/plans/termite-alarm-rework.md}): the alarm is local. It keeps a short list of
 * <b>troubles</b>: a place, what kind it is, and a strength that fades on its own. Real Macrotermes soldiers that feel
 * trouble drum their heads on the ground; soldiers near drum back and walk toward it, so the alarm runs along the
 * gallery at about 1.3 m a second and fades with distance, the workers there run deeper, and the rest of the colony
 * carries on (Hager and Kirchner 2013).
 * <ul>
 * <li><b>Kinds</b> ({@link Kind}): something <i>felt</i> (a player sprinting or jumping on the mound), a <i>foe</i> (a
 * member hurt by something, an enemy at a forager outside or at an open way out), a <i>breach</i> (a player breaks a
 * block of the mound or a wall of the nest, a raider bites a gallery open).</li>
 * <li><b>Strength</b> is how far (blocks) the trouble is felt now: it starts at the kind's number in the species file
 * and shrinks by {@code fade_per_second} from the last thing that fed it; at nothing the trouble is over. Something
 * new within {@code merge_within} blocks feeds the trouble that is there (a foe's trouble follows the foe; a breach
 * stays where the wall was broken).</li>
 * <li><b>Soldiers</b>: the drumming runs out from the trouble at {@code drum_speed} blocks a second as far as its
 * strength; a soldier it reaches drums a moment and comes ({@link #listen}), near ones first, far ones later, none past
 * its reach. At something felt they come to the nest cells nearest it and drum once; at a foe or a breach they take
 * places round it facing out, a major in the opening itself, and bite what comes near ({@link #taskFor},
 * {@code DefendGoal}). Soldiers kept as numbers in the nest come out when the drumming reaches the nest
 * ({@link #reachesNest}).</li>
 * <li><b>Workers</b>: only where a foe or a breach is felt ({@link #keepsIn}) the foragers go in, no food is fetched
 * and the holes are shut behind them ({@link #keepsShut}); everywhere else the colony forages on.</li>
 * </ul>
 * Gone with the rework: the four levels and their calm times, "everyone in" for the whole colony, the footsteps'
 * two lookers, the raid mode beside the player mode, the waves' tenth, the soldier boost after repeated break-ins
 * (a colony short of soldiers raises more anyway, {@link Soldiers#rankForNewNymph}).
 *
 * <p>Nothing is saved: a trouble lasts a minute or two.
 */
public class Alarm {
    /** What a trouble is: its strength at the start comes from the species file ({@link SavannaNumbers.AlarmNumbers}). */
    public enum Kind {
        /** Felt through the ground (footsteps on the mound): the soldiers near come to look and drum; the workers carry on. */
        FELT,
        /** A member hurt, or an enemy at the foragers or at an open way out. */
        FOE,
        /** A wall broken: the mound, the nest, a gallery's cover. */
        BREACH
    }

    /**
     * What a soldier is to do about a trouble: stand in {@code cell} (at {@code spot} in it, if said) facing
     * {@code facing}, drumming once on arrival if asked. {@code guard}: the place it guards (the trouble's): standing,
     * it snaps at a foe touching it and lunges at one it feels near, but never one farther than {@link #STAND_LEASH}
     * blocks from that place.
     */
    public record Task(BlockPos cell, BlockPos facing, boolean drumOnArrival, BlockPos guard, @Nullable Vec3 spot) {
    }

    /**
     * Real sizes, step 3: a place round a trouble is a spot inside a cell, several to a cell ({@link #SLOT_GAP} apart,
     * a 3 by 3 grid), so the soldiers pack an opening shoulder to shoulder; {@code slot} 0 to 8, 4 the middle.
     */
    private record Place(BlockPos cell, int slot) {
        Vec3 spot() {
            return Vec3.atBottomCenterOf(cell).add((slot % 3 - 1) * SLOT_GAP, 0.0, (slot / 3 - 1) * SLOT_GAP);
        }
    }

    /** One trouble. */
    private static final class Trouble {
        Kind kind;
        BlockPos at;
        /** Its strength (blocks) when it was last fed, and when that was. */
        double base;
        long fedAt;
        /** When its drumming started running out (at its start, and again when it grew into a stronger kind). */
        long startedAt;
        /** The soldiers the drumming has reached, and when. */
        final Map<UUID, Long> heard = new HashMap<>();
        /** Each soldier's place round it (kept, so one coming or going moves nobody), and places no way to. */
        final Map<UUID, Place> places = new HashMap<>();
        final Map<BlockPos, Long> badPlaces = new HashMap<>();
        /** The places round it and the nest cells near it, kept for a second. */
        List<BlockPos> ring = List.of();
        List<Place> slots = List.of();
        List<BlockPos> spots = List.of();
        @Nullable
        BlockPos cellsFor;
        long cellsAt = Long.MIN_VALUE;

        Trouble(Kind kind, BlockPos at, double base, long now) {
            this.kind = kind;
            this.at = at;
            this.base = base;
            this.fedAt = now;
            this.startedAt = now;
        }
    }

    /** The gap (blocks) between the soldiers' spots in a cell round a trouble (real-size soldiers side by side). */
    private static final double SLOT_GAP = 0.3;
    /**
     * A soldier standing at a trouble lunges only at a foe within this many blocks of the place it guards: blind
     * soldiers defend the place, they do not hunt (my pick).
     */
    public static final double STAND_LEASH = 4.0;
    /** How long (ticks) a soldier drums when it feels the alarm, before it acts. */
    public static final int DRUM_ON_HEARING = 24;
    /** How far (blocks) from a trouble the nest cells the soldiers stand in may be. */
    private static final double SPOTS_REACH = 24.0;
    /** An enemy this close (blocks) to a forager outside, or to an open way out, is a trouble. */
    private static final double ENEMY_NEAR_FORAGER = 4.0;
    private static final double ENEMY_NEAR_WAY_OUT = 3.0;
    /** A foe this near (blocks) a trouble's place keeps it up. */
    private static final double FOE_AT_TROUBLE = 4.0;
    /** Within this many blocks of the entrance (sideways) a player counts as on the mound, even without mound blocks. */
    private static final int ON_MOUND_RADIUS = 3;
    /** How far (blocks, sideways) round a foe or a breach the soldiers' places are, and up or down. */
    private static final int RING = 3;
    private static final int RING_HEIGHT = 2;
    /** A place round a trouble a soldier found no way to is given to nobody this long (ticks). */
    private static final int BAD_PLACE_TICKS = 200;
    /** At most this many troubles at once (the weakest is forgotten). */
    private static final int MOST = 8;

    private final Colony colony;
    private final List<Trouble> troubles = new ArrayList<>();
    /** In a game test: its arena (see {@link #ofThisTest}). */
    @Nullable
    private AABB testArea;
    /** Each soldier's task, worked out every half second. */
    private Map<UUID, Task> tasks = Map.of();
    private long tasksAt = Long.MIN_VALUE;
    private long tickedAt = Long.MIN_VALUE;

    Alarm(Colony colony) {
        this.colony = colony;
    }

    /** The alarm numbers of this colony's species, or null if it raises no alarm. */
    @Nullable
    public SavannaNumbers.AlarmNumbers numbers() {
        return colony.profile().section(SavannaNumbers.ALARM).orElse(null);
    }

    // --- the troubles -------------------------------------------------------------------------------------------

    private double strength(Trouble trouble, long now) {
        SavannaNumbers.AlarmNumbers numbers = numbers();
        double fade = numbers == null ? 0.2 : numbers.fadePerSecond();
        return trouble.base - fade * Math.max(0, now - trouble.fedAt) / 20.0;
    }

    /** The troubles still felt now (the faded ones dropped), strongest first. */
    private List<Trouble> live(Level world) {
        if (troubles.isEmpty()) {
            return troubles;
        }
        long now = world.getGameTime();
        if (troubles.removeIf(trouble -> strength(trouble, now) <= 0 || trouble.fedAt > now)) {
            tasksAt = Long.MIN_VALUE;
        }
        return troubles;
    }

    /**
     * Something troubling at {@code at}. It feeds the trouble already within {@code merge_within} blocks (its strength
     * back up to this kind's, never down; a stronger kind makes it that kind and its drumming runs out anew; the place
     * moves to the newest, but a breach stays where the wall was broken), or starts a new one.
     */
    public void trouble(ServerLevel world, Kind kind, BlockPos at) {
        SavannaNumbers.AlarmNumbers numbers = numbers();
        if (numbers == null) {
            return;
        }
        long now = world.getGameTime();
        double strength = numbers.strength(kind);
        double merge = numbers.mergeWithin();
        Trouble near = null;
        for (Trouble trouble : live(world)) {
            if (trouble.at.distSqr(at) <= merge * merge && (near == null || trouble.at.distSqr(at) < near.at.distSqr(at))) {
                near = trouble;
            }
        }
        if (near == null) {
            troubles.add(new Trouble(kind, at.immutable(), strength, now));
            while (troubles.size() > MOST) {
                troubles.remove(troubles.stream().min(Comparator.comparingDouble(trouble -> strength(trouble, now))).orElseThrow());
            }
            tasksAt = Long.MIN_VALUE;
            return;
        }
        near.base = Math.max(strength(near, now), strength);
        near.fedAt = now;
        if (kind.ordinal() > near.kind.ordinal()) {
            near.kind = kind;
            near.startedAt = now;
            near.at = at.immutable();
            tasksAt = Long.MIN_VALUE;
        } else if (kind == near.kind && kind != Kind.BREACH && !near.at.equals(at)) {
            near.at = at.immutable();
        }
    }

    /** A member of the colony was hurt by {@code attacker}: a foe where the attacker is. */
    public void memberHurt(ServerLevel world, Entity attacker) {
        trouble(world, Kind.FOE, attacker.blockPosition());
    }

    /** A player broke this block (before it is gone). A block of the mound, or one touching a dug-out nest cell, is a breach. */
    public void blockBroken(ServerLevel world, BlockPos pos, @Nullable Entity breaker) {
        if (numbers() != null && isBreakIn(world, pos)) {
            trouble(world, Kind.BREACH, pos);
        }
    }

    /** Whether breaking this block breaks into the colony's home: a block of the mound, or a wall of the dug-out nest. */
    public boolean isBreakIn(Level world, BlockPos pos) {
        if (colony.moundParts(world).containsKey(pos)) {
            return true;
        }
        Set<BlockPos> planned = colony.nest().nestCells(world);
        boolean nearNest = planned.contains(pos);
        for (Direction side : Direction.values()) {
            nearNest |= planned.contains(pos.relative(side));
        }
        if (!nearNest) {
            return false; // nowhere near the nest: no need to look through the whole of it
        }
        Set<BlockPos> joined = colony.walls().joinedCells(world, planned);
        if (joined.contains(pos)) {
            return false; // already open: nothing is broken into
        }
        for (Direction side : Direction.values()) {
            if (joined.contains(pos.relative(side))) {
                return true;
            }
        }
        return false;
    }

    /** A player jumped or is sprinting: something felt, if it is on the mound and not sneaking. */
    public void footsteps(ServerLevel world, Player player) {
        if (numbers() == null || player.isSpectator() || player.isShiftKeyDown() || !isOnMound(world, player)) {
            return;
        }
        trouble(world, Kind.FELT, player.blockPosition());
    }

    /** Whether the player stands on the mound: on a block of it, or right round the entrance. */
    private boolean isOnMound(Level world, Player player) {
        BlockPos feet = player.blockPosition();
        Map<BlockPos, ?> parts = colony.moundParts(world);
        if (parts.containsKey(feet) || parts.containsKey(feet.below())) {
            return true;
        }
        BlockPos center = colony.getCenter();
        return Math.abs(feet.getX() - center.getX()) <= ON_MOUND_RADIUS && Math.abs(feet.getZ() - center.getZ()) <= ON_MOUND_RADIUS
                && Math.abs(feet.getY() - center.getY()) <= 3;
    }

    /**
     * Once a second (called by the queen): players sprinting on the mound (felt), enemies near a forager outside or an
     * open way out (a foe), and a foe still at a trouble (it keeps it up).
     */
    public void tick(ServerLevel world) {
        if (numbers() == null) {
            return;
        }
        long now = world.getGameTime();
        if (tickedAt != Long.MIN_VALUE && now - tickedAt < 20 && now >= tickedAt) {
            return;
        }
        tickedAt = now;
        BlockPos center = colony.getCenter();
        for (Player player : world.getEntitiesOfClass(Player.class, new AABB(center).inflate(16, 12, 16),
                player -> player.isSprinting() && player.onGround())) {
            footsteps(world, player);
        }
        double reach = colony.foragingRadius() + 8.0;
        AABB area = new AABB(center).inflate(reach, 24.0, reach);
        List<LivingEntity> enemies = world.getEntitiesOfClass(LivingEntity.class, area, foe -> isEnemy(foe) && ofThisTest(world, foe));
        List<AbstractAntEntity> outside = enemies.isEmpty() ? List.of() : world.getEntitiesOfClass(AbstractAntEntity.class, area,
                ant -> ant.isAlive() && !ant.isDowned() && ant.isColonyWorker() && colony.getId().equals(ant.getColonyId())
                        && !colony.nest().isInside(world, ant.blockPosition()));
        if (!enemies.isEmpty()) {
            List<BlockPos> ways = colony.foragingHoles().waysOut(world).stream().map(ForagingHoles.WayOut::mouth).toList();
            for (LivingEntity foe : enemies) {
                BlockPos at = foe.blockPosition();
                boolean near = ways.stream().anyMatch(way -> way.distSqr(at) <= ENEMY_NEAR_WAY_OUT * ENEMY_NEAR_WAY_OUT)
                        || outside.stream().anyMatch(ant -> ant.distanceToSqr(foe) <= ENEMY_NEAR_FORAGER * ENEMY_NEAR_FORAGER);
                if (!near) {
                    for (Trouble trouble : live(world)) {
                        near |= trouble.kind != Kind.FELT && trouble.at.distSqr(at) <= FOE_AT_TROUBLE * FOE_AT_TROUBLE;
                    }
                }
                if (near) {
                    trouble(world, Kind.FOE, at);
                }
            }
        }
    }

    /**
     * Always, except in a game test: only an enemy inside the test's arena, and an ant or termite only if its own colony
     * lives in the same arena (workers of the tests next door wandered over and kept a colony from foraging).
     */
    private boolean ofThisTest(ServerLevel world, LivingEntity foe) {
        if (testArea == null) {
            return true;
        }
        if (!testArea.contains(foe.position())) {
            return false;
        }
        if (foe instanceof AbstractAntEntity ant && ant.getColonyId() != null) {
            Colony other = ColonyManager.get(world).getColony(ant.getColonyId());
            return other != null && testArea.contains(other.getCenter().getCenter());
        }
        return true;
    }

    /** Only enemies in this box count (see {@link #ofThisTest}). For the game tests. */
    public void setTestArea(@Nullable AABB area) {
        testArea = area;
    }

    /**
     * An enemy of the colony, by the shared fight rules ({@link Fights#isFoe}, M8 step 2a): an animal of the species
     * file's {@code fight.foe_types} (chickens eat termites), an insect of a species it lists (the Matabele), or an ant
     * or termite of another colony.
     */
    public boolean isEnemy(Entity entity) {
        return Fights.isFoe(colony.profile(), colony.getSpecies(), colony.getId(), entity);
    }

    // --- asked by the colony ------------------------------------------------------------------------------------

    /** Whether no trouble is felt anywhere now. */
    public boolean isCalm(Level world) {
        return live(world).isEmpty();
    }

    /** Whether a foe or a breach is felt anywhere now (a quick check before asking of each place). */
    public boolean anyKeptIn(Level world) {
        for (Trouble trouble : live(world)) {
            if (trouble.kind != Kind.FELT) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private Trouble keeping(Level world, BlockPos pos) {
        if (troubles.isEmpty()) {
            return null;
        }
        long now = world.getGameTime();
        for (Trouble trouble : live(world)) {
            double felt = strength(trouble, now);
            if (trouble.kind != Kind.FELT && trouble.at.distSqr(pos) <= felt * felt) {
                return trouble;
            }
        }
        return null;
    }

    /**
     * Whether a foe or a breach is felt at this place now: the workers there go in and stay in, no food is fetched
     * there, nobody grooms or naps. As the trouble fades its reach shrinks, so the places farthest from it are free
     * first.
     */
    public boolean keepsIn(Level world, BlockPos pos) {
        return keeping(world, pos) != null;
    }

    /**
     * Whether a foe's or a breach's drumming has reached the nest now: the colony's soldiers kept as numbers in it
     * come out ({@link Soldiers#wantReal}).
     */
    public boolean reachesNest(Level world) {
        if (troubles.isEmpty()) {
            return false;
        }
        long now = world.getGameTime();
        AABB nest = colony.nestArea();
        for (Trouble trouble : live(world)) {
            if (trouble.kind == Kind.FELT) {
                continue;
            }
            Vec3 at = Vec3.atCenterOf(trouble.at);
            double dx = Math.max(0, Math.max(nest.minX - at.x, at.x - nest.maxX));
            double dy = Math.max(0, Math.max(nest.minY - at.y, at.y - nest.maxY));
            double dz = Math.max(0, Math.max(nest.minZ - at.z, at.z - nest.maxZ));
            if (reached(trouble, Math.sqrt(dx * dx + dy * dy + dz * dz), now)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the trouble's drumming has got {@code distance} blocks out by now, and is still felt that far. */
    private boolean reached(Trouble trouble, double distance, long now) {
        SavannaNumbers.AlarmNumbers numbers = numbers();
        double speed = numbers == null ? 1.3 : numbers.drumSpeed();
        return distance <= strength(trouble, now) && (now - trouble.startedAt) * speed / 20.0 >= distance;
    }

    // --- the drumming spreading ---------------------------------------------------------------------------------

    /**
     * Asked by each soldier now and then: has a trouble's drumming reached it? Then it drums a moment (the others feel
     * that in turn) and answers that trouble until it is over.
     */
    public void listen(ServerLevel world, TermiteSoldierEntity soldier) {
        if (troubles.isEmpty() || hasHeard(soldier.getUUID())) {
            return;
        }
        long now = world.getGameTime();
        for (Trouble trouble : live(world)) {
            if (reached(trouble, Math.sqrt(soldier.blockPosition().distSqr(trouble.at)), now)) {
                trouble.heard.put(soldier.getUUID(), now);
                soldier.drum(DRUM_ON_HEARING);
                tasksAt = Long.MIN_VALUE;
                return;
            }
        }
    }

    /** Whether this soldier is answering a trouble now. */
    public boolean hasHeard(UUID soldier) {
        for (Trouble trouble : troubles) {
            if (trouble.heard.containsKey(soldier)) {
                return true;
            }
        }
        return false;
    }

    /** The game time the drumming reached this soldier, or {@link Colony#NOT_SET}. */
    public long heardAt(UUID soldier) {
        for (Trouble trouble : troubles) {
            Long at = trouble.heard.get(soldier);
            if (at != null) {
                return at;
            }
        }
        return Colony.NOT_SET;
    }

    // --- what each soldier does ---------------------------------------------------------------------------------

    /** What this soldier is to do about a trouble now, or null (nothing: back to its beat). */
    @Nullable
    public Task taskFor(ServerLevel world, TermiteSoldierEntity soldier) {
        if (troubles.isEmpty()) {
            return null;
        }
        long now = world.getGameTime();
        if (tasksAt == Long.MIN_VALUE || now - tasksAt >= 10 || now < tasksAt) {
            tasksAt = now;
            tasks = workOutTasks(world);
        }
        return tasks.get(soldier.getUUID());
    }

    /** Whether this soldier has something to do about a trouble (it may then be outside). */
    public boolean isBusy(ServerLevel world, TermiteSoldierEntity soldier) {
        return taskFor(world, soldier) != null;
    }

    private Map<UUID, Task> workOutTasks(ServerLevel world) {
        Map<UUID, Task> out = new HashMap<>();
        if (live(world).isEmpty()) {
            return out;
        }
        List<TermiteSoldierEntity> soldiers = colony.part(SavannaParts.SOLDIERS).map(part -> part.getSoldiers(world)).orElse(List.of());
        for (Trouble trouble : troubles) {
            List<TermiteSoldierEntity> answering = new ArrayList<>();
            for (TermiteSoldierEntity soldier : soldiers) {
                if (trouble.heard.containsKey(soldier.getUUID())) {
                    answering.add(soldier);
                }
            }
            answering.sort(Comparator.comparingDouble(soldier -> soldier.blockPosition().distSqr(trouble.at)));
            cellsNear(world, trouble);
            if (trouble.kind == Kind.FELT) {
                // Something felt: they come as near as the nest goes, and drum once there.
                int next = 0;
                for (TermiteSoldierEntity soldier : answering) {
                    if (next < trouble.spots.size()) {
                        BlockPos cell = trouble.spots.get(next++);
                        out.put(soldier.getUUID(), new Task(cell, trouble.at, true, cell, null));
                    }
                }
            } else {
                places(world, trouble, answering, out);
            }
        }
        return out;
    }

    /**
     * A foe or a breach: every soldier that heard comes, majors too: the nearest major (else the nearest minor) holds
     * the place itself (a gallery's mouth), the others take the places round it (nearest first, minors before majors,
     * the ones nearer coming first), facing out, each keeping its own place; any past the places stand at the nest cells
     * nearest it. Every one guards the trouble's place (it bites foes within the leash of it).
     */
    private void places(ServerLevel world, Trouble trouble, List<TermiteSoldierEntity> answering, Map<UUID, Task> out) {
        long time = world.getGameTime();
        BlockPos spot = trouble.at;
        Map<UUID, Place> places = trouble.places;
        Map<BlockPos, Long> badPlaces = trouble.badPlaces;
        badPlaces.values().removeIf(until -> until <= time || until > time + BAD_PLACE_TICKS);
        List<BlockPos> cells = trouble.ring;
        Set<BlockPos> cellSet = new HashSet<>(cells);
        Set<UUID> ids = new HashSet<>();
        answering.forEach(soldier -> ids.add(soldier.getUUID()));
        places.keySet().retainAll(ids);
        places.values().removeIf(place -> !cellSet.contains(place.cell()) || badPlaces.containsKey(place.cell()));
        Set<Place> taken = new HashSet<>(places.values());
        Place mouth = !cells.isEmpty() && cells.get(0).equals(spot) ? new Place(spot, 4) : null;
        if (mouth != null && !taken.contains(mouth) && !badPlaces.containsKey(spot)) {
            TermiteSoldierEntity holder = null;
            for (TermiteSoldierEntity soldier : answering) {
                if (!places.containsKey(soldier.getUUID()) && (holder == null || holder.getRank() != Rank.MAJOR && soldier.getRank() == Rank.MAJOR)) {
                    holder = soldier; // (the nearest major; the nearest minor if no major is free)
                }
            }
            if (holder != null) {
                places.put(holder.getUUID(), mouth);
                taken.add(mouth);
            }
        }
        int next = 0;
        for (Rank rank : new Rank[] {Rank.MINOR, Rank.MAJOR}) {
            for (TermiteSoldierEntity soldier : answering) {
                if (soldier.getRank() != rank) {
                    continue;
                }
                Place place = places.get(soldier.getUUID());
                if (place == null) {
                    for (Place slot : trouble.slots) {
                        if (!taken.contains(slot) && !badPlaces.containsKey(slot.cell())) {
                            place = slot;
                            places.put(soldier.getUUID(), slot);
                            taken.add(slot);
                            break;
                        }
                    }
                }
                if (place != null) {
                    BlockPos cell = place.cell();
                    BlockPos facing = cell.equals(spot) ? spot : cell.offset(cell.getX() - spot.getX(), 0, cell.getZ() - spot.getZ());
                    out.put(soldier.getUUID(), new Task(cell, facing, false, spot, place.spot()));
                } else if (next < trouble.spots.size()) {
                    out.put(soldier.getUUID(), new Task(trouble.spots.get(next++), spot, false, spot, null));
                }
            }
        }
    }

    /**
     * The cells soldiers stand in at this trouble, kept for a second. The nest cells near it ({@code spots}, nearest
     * first): the place itself if it is open with a floor and opens into the nest (the hole a player made in a wall),
     * then the dug-out nest cells joined to the shaft that have a floor, within {@link #SPOTS_REACH} blocks. And for a
     * foe or a breach the places round it ({@code ring}, nearest first): the place itself if one can stand in it (a
     * gallery's mouth), then the open cells with a floor within {@link #RING} blocks sideways and {@link #RING_HEIGHT}
     * up or down that open into it (outside through the broken cover, or along the gallery; never the ground over a
     * gallery still roofed, which no soldier could reach), each with its spots ({@code slots}).
     */
    private void cellsNear(Level world, Trouble trouble) {
        long now = world.getGameTime();
        BlockPos at = trouble.at;
        if (at.equals(trouble.cellsFor) && trouble.cellsAt != Long.MIN_VALUE && now - trouble.cellsAt < 20 && now >= trouble.cellsAt) {
            return;
        }
        trouble.cellsFor = at;
        trouble.cellsAt = now;
        Set<BlockPos> joined = colony.walls().joinedCells(world, colony.nest().nestCells(world));
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos cell : joined) {
            if (cell.distSqr(at) <= SPOTS_REACH * SPOTS_REACH && standable(world, cell)) {
                spots.add(cell);
            }
        }
        spots.sort(Comparator.comparingDouble((BlockPos cell) -> cell.distSqr(at)).thenComparingLong(BlockPos::asLong));
        if (!joined.contains(at) && standable(world, at) && touchesAny(at, joined)) {
            spots.add(0, at);
        }
        trouble.spots = List.copyOf(spots);
        if (trouble.kind == Kind.FELT) {
            trouble.ring = List.of();
            trouble.slots = List.of();
            return;
        }
        List<BlockPos> cells = new ArrayList<>();
        double most = (RING + 0.5) * (RING + 0.5);
        java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(at);
        if (passable(world, at)) {
            open.add(at);
        } else {
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (passable(world, next) && seen.add(next)) {
                    open.add(next);
                }
            }
        }
        while (!open.isEmpty()) {
            BlockPos cell = open.poll();
            if (!cell.equals(at) && standable(world, cell)) {
                cells.add(cell);
            }
            for (Direction side : Direction.values()) {
                BlockPos next = cell.relative(side);
                if (Math.abs(next.getY() - at.getY()) <= RING_HEIGHT && next.distSqr(at) <= most && passable(world, next) && seen.add(next)) {
                    open.add(next);
                }
            }
        }
        cells.sort(Comparator.comparingDouble((BlockPos cell) -> cell.distSqr(at)).thenComparingLong(BlockPos::asLong));
        if (standable(world, at)) {
            cells.add(0, at);
        }
        trouble.ring = List.copyOf(cells);
        // The spots: the place itself holds one (its middle), every other cell SLOT_GAP-apart spots, so the first dozen
        // ring an opening shoulder to shoulder.
        Vec3 middle = Vec3.atBottomCenterOf(at);
        List<Place> slots = new ArrayList<>();
        for (BlockPos cell : cells) {
            if (cell.equals(at)) {
                slots.add(new Place(cell, 4));
                continue;
            }
            for (int slot = 0; slot < 9; slot++) {
                slots.add(new Place(cell, slot));
            }
        }
        slots.sort(Comparator.comparingDouble((Place place) -> flatSqr(place.spot(), middle))
                .thenComparingInt(place -> Math.abs(place.cell().getY() - at.getY()))
                .thenComparingLong(place -> place.cell().asLong()).thenComparingInt(Place::slot));
        trouble.slots = List.copyOf(slots);
    }

    private static double flatSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    /** This soldier found no way to its place round a trouble: nobody is given it for a while. */
    public void noWayTo(TermiteSoldierEntity soldier, BlockPos cell) {
        for (Trouble trouble : troubles) {
            Place place = trouble.places.get(soldier.getUUID());
            if (place != null && cell.equals(place.cell())) {
                trouble.places.remove(soldier.getUUID());
                trouble.badPlaces.put(cell.immutable(), soldier.level().getGameTime() + BAD_PLACE_TICKS);
                tasksAt = Long.MIN_VALUE;
            }
        }
    }

    /** Whether this soldier holds a place at a foe or a breach now (it stands out to fight there, not sheltered). */
    public boolean holdsPlace(UUID soldier) {
        for (Trouble trouble : troubles) {
            if (trouble.places.containsKey(soldier)) {
                return true;
            }
        }
        return false;
    }

    /** Open to walk through: loaded, nothing solid, no water. */
    private static boolean passable(Level world, BlockPos cell) {
        return world.isLoaded(cell) && world.getBlockState(cell).getCollisionShape(world, cell).isEmpty()
                && world.getBlockState(cell).getFluidState().isEmpty();
    }

    static boolean standable(Level world, BlockPos cell) {
        BlockPos floor = cell.below();
        return world.isLoaded(cell) && world.getBlockState(cell).getCollisionShape(world, cell).isEmpty()
                && world.getBlockState(cell).getFluidState().isEmpty() && world.getBlockState(floor).isFaceSturdy(world, floor, Direction.UP);
    }

    private static boolean touchesAny(BlockPos cell, Set<BlockPos> cells) {
        for (Direction side : Direction.values()) {
            if (cells.contains(cell.relative(side))) {
                return true;
            }
        }
        return false;
    }

    // --- the list and the tests ---------------------------------------------------------------------------------

    /** What is felt at one place now (tests and the list). */
    public record Felt(Kind kind, BlockPos at, double strength, int soldiers) {
    }

    /** The troubles felt now, strongest first. */
    public List<Felt> felt(Level world) {
        long now = world.getGameTime();
        List<Felt> out = new ArrayList<>();
        for (Trouble trouble : live(world)) {
            out.add(new Felt(trouble.kind, trouble.at, strength(trouble, now), trouble.heard.size()));
        }
        out.sort(Comparator.comparingDouble((Felt felt) -> -felt.strength()));
        return out;
    }

    /** The strongest kind of trouble felt now, or null (calm). */
    @Nullable
    public Kind worst(Level world) {
        Kind worst = null;
        for (Trouble trouble : live(world)) {
            if (worst == null || trouble.kind.ordinal() > worst.ordinal()) {
                worst = trouble.kind;
            }
        }
        return worst;
    }

    /** For the colony list: " | calm" or " | trouble: breach at 1, 2, 3 (felt 14 blocks, 4 soldiers); foe at ...". */
    public String describe(Level world) {
        if (numbers() == null) {
            return "";
        }
        List<Felt> all = felt(world);
        if (all.isEmpty()) {
            return " | calm";
        }
        StringBuilder text = new StringBuilder(" | trouble: ");
        for (int i = 0; i < all.size(); i++) {
            Felt felt = all.get(i);
            String what = switch (felt.kind()) {
                case FELT -> "something felt";
                case FOE -> "foe";
                case BREACH -> "breach";
            };
            text.append(i == 0 ? "" : "; ").append(what).append(" at ").append(felt.at().toShortString()).append(" (felt ")
                    .append(Math.round(felt.strength())).append(" blocks, ").append(felt.soldiers()).append(" soldiers)");
        }
        return text.toString();
    }

    /** Forgets every trouble: for tests and the command {@code /antcolonies alarm calm}. */
    public void calmForTests() {
        troubles.clear();
        tasksAt = Long.MIN_VALUE;
    }

    /** The soldiers answering a trouble now (for tests). */
    public Set<UUID> alarmedSoldiers() {
        Set<UUID> out = new HashSet<>();
        for (Trouble trouble : troubles) {
            out.addAll(trouble.heard.keySet());
        }
        return out;
    }
}
