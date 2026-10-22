package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.entity.TermiteSoldierEntity.Rank;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The colony's alarm (species with {@code alarm} in their profile: termites). Species-neutral: it keeps the level and
 * where the trouble is; what the soldiers do about it is worked out here as a task for each soldier ({@link #taskFor}).
 * Four levels (the user's alarm ladder), each dropping one level a while after the last thing that raised it (the
 * profile's {@code calm_ticks}):
 * <ol>
 * <li><b>Footsteps:</b> a player sprinting or jumping on the mound (not sneaking). The two minors nearest come to the
 * nearest spot of the nest, look and drum once.</li>
 * <li><b>An enemy at the foragers:</b> a termite is hurt by something, or an enemy (a chicken, an ant or termite of
 * another colony) comes near a forager outside or an open way out. The minors that feel it (the drumming spreads minor
 * to minor along the sentry line) rush at it, and bite it if it is a chicken; the foragers hurry home (no foraging while
 * the alarm is at 2 or more).</li>
 * <li><b>A break-in:</b> a player breaks a block of the mound or a wall of the nest. The drumming spreads through the
 * whole nest, soldier to soldier; the minors rush to the breach (one into the hole itself, the others to the nest cells
 * nearest it) and the majors stand behind them; the workers seal the breach as always, even if that shuts a minor out
 * (see {@link NestWalls#plugLeak}).</li>
 * <li><b>Repeated break-ins</b> ({@code break_ins_for_level_4} within {@code break_in_window_ticks}): as 3, and everybody
 * stays in for a whole day; the colony wants more soldiers for a while ({@code soldier_boost}, {@code boost_ticks}).</li>
 * </ol>
 * Saved: the level, when it was last raised, the soldier boost. Not saved: where the trouble is, who heard it.
 */
public class Alarm {
    /** What a soldier is to do about the alarm: go at an enemy, or stand in a cell facing the trouble. */
    public record Task(@Nullable LivingEntity enemy, @Nullable BlockPos cell, BlockPos facing, boolean drumOnArrival) {
    }

    /** How long (ticks) a soldier drums when it feels the alarm, before it acts. */
    public static final int DRUM_ON_HEARING = 24;
    /** How long (ticks) after a soldier starts drumming the ones round it feel it. */
    private static final int SPREAD_DELAY = 6;
    /** At a break-in, every soldier has felt it after this long (ticks) even if the chain of drummers is broken. */
    private static final int WAVE_FALLBACK_TICKS = 100;
    /** How many minors come to look at footsteps. */
    private static final int LOOKERS = 2;
    /** How far (blocks) from the trouble the soldiers' spots may be. */
    private static final double BREACH_REACH = 24.0;
    /** A minor this close (blocks) to an enemy goes at it even if it is inside the nest. */
    private static final double NEAR_ENEMY = 8.0;
    /** An enemy this close (blocks) to a forager outside, or to an open way out, raises the alarm. */
    private static final double ENEMY_NEAR_FORAGER = 4.0;
    private static final double ENEMY_NEAR_WAY_OUT = 3.0;
    /** Within this many blocks of the entrance (sideways) a player counts as on the mound, even without mound blocks. */
    private static final int ON_MOUND_RADIUS = 3;

    private record Heard(BlockPos at, long time) {
    }

    private final Colony colony;
    private int level;
    /** When the current level was last raised (it drops one level after its calm time from then). */
    private long raisedAt = Colony.NOT_SET;
    /** Until when (game time) the colony wants more soldiers (after a level 4). Saved. */
    private long boostUntil = Colony.NOT_SET;
    /** Where the trouble is, and the enemy (null: none, or not one to go at). */
    @Nullable
    private BlockPos spot;
    @Nullable
    private UUID enemy;
    /** In a game test: its arena (see {@link #ofThisTest}). */
    @Nullable
    private AABB testArea;
    /** When the break-ins of late happened (for level 4). */
    private final List<Long> breakIns = new ArrayList<>();
    /** When the current wave of drumming started, and who has felt it (where and when it started drumming). */
    private long waveStart;
    private final Map<UUID, Heard> heard = new HashMap<>();
    /** The minors sent to look at footsteps. */
    private final Set<UUID> lookers = new LinkedHashSet<>();
    /** Each soldier's task, worked out every half second. */
    private Map<UUID, Task> tasks = Map.of();
    private long tasksAt = Long.MIN_VALUE;
    private long tickedAt = Long.MIN_VALUE;
    /** The spots near the trouble, kept for a second. */
    private List<BlockPos> spots = List.of();
    @Nullable
    private BlockPos spotsFor;
    private long spotsAt = Long.MIN_VALUE;

    Alarm(Colony colony) {
        this.colony = colony;
    }

    /** The alarm numbers of this colony's species, or null if it raises no alarm. */
    @Nullable
    public SpeciesProfile.AlarmNumbers numbers() {
        return colony.profile().alarm().orElse(null);
    }

    /** The alarm level now: 0 (calm) to 4. Drops a level each time its calm time has passed since it was last raised. */
    public int level(Level world) {
        SpeciesProfile.AlarmNumbers numbers = numbers();
        if (numbers == null) {
            return 0;
        }
        long now = world.getGameTime();
        if (raisedAt > now) {
            raisedAt = now;
        }
        while (level > 0 && now - raisedAt >= numbers.calmTicksFor(level)) {
            raisedAt += numbers.calmTicksFor(level);
            level--;
            if (level < 2) {
                heard.clear();
                enemy = null;
            }
            if (level == 0) {
                spot = null;
                lookers.clear();
            }
            tasksAt = Long.MIN_VALUE;
        }
        return level;
    }

    /** Whether the alarm keeps the colony in: no foraging while it is at 2 or more (the foragers hurry home). */
    public boolean keepsEveryoneIn(Level world) {
        return level(world) >= 2;
    }

    /** How many times as many soldiers the colony wants now (more for a while after a level 4). */
    public float soldierBoost(Level world) {
        SpeciesProfile.AlarmNumbers numbers = numbers();
        return numbers != null && boostUntil != Colony.NOT_SET && world.getGameTime() < boostUntil ? numbers.soldierBoost() : 1.0F;
    }

    /** Where the trouble is (null when calm). */
    @Nullable
    public BlockPos getSpot() {
        return spot;
    }

    // --- raising it ---------------------------------------------------------------------------------------------

    /**
     * Raises the alarm to {@code to} at {@code at} ({@code by}: the enemy, if there is one). A higher level starts a new
     * wave of drumming; the same level only keeps it up (and moves the trouble to the newest spot).
     */
    public void raise(ServerLevel world, int to, BlockPos at, @Nullable Entity by) {
        if (numbers() == null || to < 1) {
            return;
        }
        int now = level(world);
        long time = world.getGameTime();
        if (to < now) {
            return;
        }
        raisedAt = time;
        spot = at.immutable();
        if (by instanceof LivingEntity living && to == 2) {
            enemy = living.getUUID();
        }
        tasksAt = Long.MIN_VALUE;
        if (to == now) {
            return;
        }
        level = to;
        heard.clear();
        lookers.clear();
        waveStart = time;
        List<TermiteSoldierEntity> soldiers = new ArrayList<>(colony.part(ColonyParts.SOLDIERS).map(part -> part.getSoldiers(world)).orElse(List.of()));
        soldiers.sort(Comparator.comparingDouble(soldier -> soldier.blockPosition().distSqr(at)));
        if (to == 1) {
            for (TermiteSoldierEntity soldier : soldiers) {
                if (soldier.getRank() == Rank.MINOR && lookers.size() < LOOKERS) {
                    lookers.add(soldier.getUUID());
                }
            }
            return;
        }
        // The soldier nearest the trouble feels it first (and the minors that see it, at an enemy outside).
        double see = numbers().feelRadius();
        for (TermiteSoldierEntity soldier : soldiers) {
            boolean first = heard.isEmpty() && (to >= 3 || soldier.getRank() == Rank.MINOR);
            boolean sees = to == 2 && soldier.getRank() == Rank.MINOR && soldier.blockPosition().distSqr(at) <= see * see;
            if (first || sees) {
                hear(soldier, time);
            }
        }
        if (to == 4) {
            boostUntil = time + numbers().boostTicks();
        }
    }

    /** A member of the colony was hurt by {@code attacker}: level 2 at the attacker. */
    public void memberHurt(ServerLevel world, Entity attacker) {
        raise(world, 2, attacker.blockPosition(), attacker);
    }

    /**
     * A player broke this block (before it is gone). A block of the mound, or one touching a dug-out nest cell, is a
     * break-in: level 3 there; the {@code break_ins_for_level_4}-th within {@code break_in_window_ticks} makes it 4.
     */
    public void blockBroken(ServerLevel world, BlockPos pos, @Nullable Entity breaker) {
        SpeciesProfile.AlarmNumbers numbers = numbers();
        if (numbers == null || !isBreakIn(world, pos)) {
            return;
        }
        long now = world.getGameTime();
        breakIns.removeIf(time -> now - time > numbers.breakInWindowTicks() || time > now);
        breakIns.add(now);
        raise(world, breakIns.size() >= numbers.breakInsForLevel4() ? 4 : 3, pos, breaker);
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

    /** A player jumped or is sprinting: footsteps (level 1) if it is on the mound and not sneaking. */
    public void footsteps(ServerLevel world, Player player) {
        if (numbers() == null || player.isSpectator() || player.isShiftKeyDown() || !isOnMound(world, player)) {
            return;
        }
        raise(world, 1, player.blockPosition(), player);
    }

    /** Whether the player stands on the mound: on a block of it, or right round the entrance. */
    private boolean isOnMound(Level world, Player player) {
        BlockPos feet = player.blockPosition();
        Map<BlockPos, MoundPlan.Part> parts = colony.moundParts(world);
        if (parts.containsKey(feet) || parts.containsKey(feet.below())) {
            return true;
        }
        BlockPos center = colony.getCenter();
        return Math.abs(feet.getX() - center.getX()) <= ON_MOUND_RADIUS && Math.abs(feet.getZ() - center.getZ()) <= ON_MOUND_RADIUS
                && Math.abs(feet.getY() - center.getY()) <= 3;
    }

    /**
     * Once a second (called by the queen): players sprinting on the mound (footsteps), and enemies near a forager outside
     * or an open way out (level 2).
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
        level(world);
        BlockPos center = colony.getCenter();
        for (Player player : world.getEntitiesOfClass(Player.class, new AABB(center).inflate(16, 12, 16),
                player -> player.isSprinting() && player.onGround())) {
            footsteps(world, player);
        }
        double reach = colony.foragingRadius() + 8.0;
        AABB area = new AABB(center).inflate(reach, 24.0, reach);
        List<LivingEntity> enemies = world.getEntitiesOfClass(LivingEntity.class, area, this::isEnemy);
        if (enemies.isEmpty()) {
            return;
        }
        List<BlockPos> ways = colony.foragingHoles().waysOut(world).stream().map(ForagingHoles.WayOut::mouth).toList();
        List<AbstractAntEntity> outside = world.getEntitiesOfClass(AbstractAntEntity.class, area,
                ant -> ant.isAlive() && ant.isColonyWorker() && colony.getId().equals(ant.getColonyId())
                        && !colony.nest().isInside(world, ant.blockPosition()));
        for (LivingEntity foe : enemies) {
            if (!ofThisTest(world, foe)) {
                continue;
            }
            boolean near = ways.stream().anyMatch(way -> way.distSqr(foe.blockPosition()) <= ENEMY_NEAR_WAY_OUT * ENEMY_NEAR_WAY_OUT)
                    || outside.stream().anyMatch(ant -> ant.distanceToSqr(foe) <= ENEMY_NEAR_FORAGER * ENEMY_NEAR_FORAGER);
            if (near) {
                raise(world, 2, foe.blockPosition(), foe);
                return;
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

    /** An enemy of the colony: a chicken (they eat termites), or an ant or termite of another colony. */
    public boolean isEnemy(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        if (entity instanceof Chicken) {
            return true;
        }
        return entity instanceof AbstractAntEntity ant && ant.getColonyId() != null && !colony.getId().equals(ant.getColonyId());
    }

    /** Whether a soldier may bite this (for now only chickens; fighting players and ants comes with M8). */
    public static boolean mayBite(Entity entity) {
        return entity instanceof Chicken && entity.isAlive();
    }

    // --- the drumming spreading ---------------------------------------------------------------------------------

    /** The soldier feels the alarm: it drums for a moment (the others round it feel that in turn), then acts. */
    private void hear(TermiteSoldierEntity soldier, long time) {
        heard.put(soldier.getUUID(), new Heard(soldier.blockPosition(), time));
        soldier.drum(DRUM_ON_HEARING);
        tasksAt = Long.MIN_VALUE;
    }

    /**
     * Asked by each soldier now and then: does it feel the drumming? At level 2 a minor feels a minor drumming within
     * {@code feel_radius} blocks; at 3 and 4 every soldier feels any soldier drumming within it (through the walls), and
     * everyone has felt it a few seconds after the break-in even if the chain is broken.
     */
    public void listen(ServerLevel world, TermiteSoldierEntity soldier) {
        int now = level(world);
        if (now < 2 || heard.containsKey(soldier.getUUID()) || (now == 2 && soldier.getRank() != Rank.MINOR)) {
            return;
        }
        long time = world.getGameTime();
        if (now >= 3 && time - waveStart >= WAVE_FALLBACK_TICKS) {
            hear(soldier, time);
            return;
        }
        double feel = numbers().feelRadius();
        for (Heard drummer : heard.values()) {
            if (time - drummer.time() >= SPREAD_DELAY && drummer.at().distSqr(soldier.blockPosition()) <= feel * feel) {
                hear(soldier, time);
                return;
            }
        }
    }

    /** Whether this soldier has felt the current alarm (tests and the list). */
    public boolean hasHeard(UUID soldier) {
        return heard.containsKey(soldier);
    }

    /** The game time this soldier felt the current alarm, or {@link Colony#NOT_SET}. */
    public long heardAt(UUID soldier) {
        Heard at = heard.get(soldier);
        return at == null ? Colony.NOT_SET : at.time();
    }

    // --- what each soldier does ---------------------------------------------------------------------------------

    /** What this soldier is to do about the alarm now, or null (nothing: back to its post). */
    @Nullable
    public Task taskFor(ServerLevel world, TermiteSoldierEntity soldier) {
        long now = world.getGameTime();
        if (tasksAt == Long.MIN_VALUE || now - tasksAt >= 10 || now < tasksAt) {
            tasksAt = now;
            tasks = workOutTasks(world);
        }
        return tasks.get(soldier.getUUID());
    }

    /** Whether this soldier has something to do about the alarm (a minor may then be outside). */
    public boolean isBusy(ServerLevel world, TermiteSoldierEntity soldier) {
        return taskFor(world, soldier) != null;
    }

    private Map<UUID, Task> workOutTasks(ServerLevel world) {
        int now = level(world);
        if (now == 0 || spot == null) {
            return Map.of();
        }
        List<TermiteSoldierEntity> soldiers = new ArrayList<>(colony.part(ColonyParts.SOLDIERS).map(part -> part.getSoldiers(world)).orElse(List.of()));
        soldiers.sort(Comparator.comparingDouble(soldier -> soldier.blockPosition().distSqr(spot)));
        Map<UUID, Task> out = new HashMap<>();
        if (now == 1) {
            List<BlockPos> cells = spotsNear(world, spot);
            int i = 0;
            for (UUID id : lookers) {
                if (i < cells.size() && world.getEntity(id) instanceof TermiteSoldierEntity) {
                    out.put(id, new Task(null, cells.get(i++), spot, true));
                }
            }
            return out;
        }
        if (now == 2) {
            LivingEntity foe = enemy != null && world.getEntity(enemy) instanceof LivingEntity living && living.isAlive() ? living : null;
            double reach = colony.foragingRadius() + 8.0;
            if (foe == null || foe.blockPosition().distSqr(colony.getCenter()) > reach * reach) {
                return Map.of(); // gone, or beyond the colony's ground: the soldiers stay near home
            }
            for (TermiteSoldierEntity soldier : soldiers) {
                if (soldier.getRank() == Rank.MINOR && heard.containsKey(soldier.getUUID()) && !soldier.isDrumming()
                        && (!colony.nest().isInside(world, soldier.blockPosition()) || soldier.distanceTo(foe) <= NEAR_ENEMY)) {
                    out.put(soldier.getUUID(), new Task(foe, null, foe.blockPosition(), false));
                }
            }
            return out;
        }
        // A break-in: the minors to the breach and the nest cells nearest it, the majors behind them.
        List<BlockPos> cells = spotsNear(world, spot);
        int next = 0;
        for (Rank rank : new Rank[] {Rank.MINOR, Rank.MAJOR}) {
            for (TermiteSoldierEntity soldier : soldiers) {
                if (soldier.getRank() == rank && heard.containsKey(soldier.getUUID()) && next < cells.size()) {
                    out.put(soldier.getUUID(), new Task(null, cells.get(next++), spot, false));
                }
            }
        }
        return out;
    }

    /**
     * The spots soldiers stand at near the trouble, nearest first: the breach itself if it is open with a floor and opens
     * into the nest (the hole a player made in a wall), then the dug-out nest cells joined to the shaft that have a floor,
     * within {@link #BREACH_REACH} blocks.
     */
    public List<BlockPos> spotsNear(Level world, BlockPos at) {
        long now = world.getGameTime();
        if (!at.equals(spotsFor) || spotsAt == Long.MIN_VALUE || now - spotsAt >= 20 || now < spotsAt) {
            spots = workOutSpots(world, at);
            spotsFor = at;
            spotsAt = now;
        }
        return spots;
    }

    private List<BlockPos> workOutSpots(Level world, BlockPos at) {
        Set<BlockPos> joined = colony.walls().joinedCells(world, colony.nest().nestCells(world));
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos cell : joined) {
            if (cell.distSqr(at) <= BREACH_REACH * BREACH_REACH && standable(world, cell)) {
                cells.add(cell);
            }
        }
        cells.sort(Comparator.comparingDouble((BlockPos cell) -> cell.distSqr(at)).thenComparingLong(BlockPos::asLong));
        if (!joined.contains(at) && standable(world, at) && touchesAny(at, joined)) {
            cells.add(0, at);
        }
        return cells;
    }

    private static boolean standable(Level world, BlockPos cell) {
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

    // --- the list and saving ------------------------------------------------------------------------------------

    /** For the colony list: " | alarm 3 (break-in at 1,2,3, 4 soldiers drumming or alarmed), more soldiers wanted" or " | calm". */
    public String describe(Level world) {
        if (numbers() == null) {
            return "";
        }
        int now = level(world);
        String boost = soldierBoost(world) > 1.0F ? ", more soldiers wanted for " + (boostUntil - world.getGameTime()) / 1200 + " min" : "";
        if (now == 0) {
            return " | calm" + boost;
        }
        String what = switch (now) {
            case 1 -> "footsteps";
            case 2 -> "enemy at the foragers";
            case 3 -> "break-in";
            default -> "repeated break-ins, everyone stays in";
        };
        return " | alarm " + now + " (" + what + (spot != null ? " at " + spot.toShortString() : "") + ", " + heard.size()
                + " soldiers alarmed)" + boost;
    }

    /** Tests only: a sample in every saved number (the save and load check, {@code AwayTests.everyPartSavesAndLoads}). */
    public void fillForTests(ServerLevel world) {
        level = 1;
        raisedAt = world.getGameTime();
        boostUntil = world.getGameTime() + 200;
    }

    void save(CompoundTag tag) {
        tag.putInt("alarmLevel", level);
        tag.putLong("alarmRaisedAt", raisedAt);
        tag.putLong("soldierBoostUntil", boostUntil);
    }

    void load(CompoundTag tag) {
        level = tag.getInt("alarmLevel");
        raisedAt = tag.contains("alarmRaisedAt") ? tag.getLong("alarmRaisedAt") : Colony.NOT_SET;
        boostUntil = tag.contains("soldierBoostUntil") ? tag.getLong("soldierBoostUntil") : Colony.NOT_SET;
        if (raisedAt == Colony.NOT_SET) {
            level = 0;
        }
    }

    /** Forgets the alarm (calm, no boost): for tests and the command {@code /antcolonies alarm 0}. */
    public void calmForTests() {
        level = 0;
        spot = null;
        enemy = null;
        heard.clear();
        lookers.clear();
        breakIns.clear();
        boostUntil = Colony.NOT_SET;
        tasksAt = Long.MIN_VALUE;
    }

    /** The soldiers that have felt the current alarm (for tests). */
    public Set<UUID> alarmedSoldiers() {
        return new HashSet<>(heard.keySet());
    }
}
