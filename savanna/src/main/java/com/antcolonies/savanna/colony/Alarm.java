package com.antcolonies.savanna.colony;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ForagingHoles;
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
import com.antcolonies.fight.Fights;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank;
import com.antcolonies.species.SpeciesProfile;

import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * <p>M8 step 3S (the user's raid test of 2026-11-05, "the major soldiers stand guard in a line and don't swarm"): at a
 * raid's break into a feeding gallery ({@link #raidBreakIn}) every soldier the drumming reaches comes, majors too,
 * leaving its post; a major holds the gallery mouth and the rest spread round the opening facing out, each in a place of
 * its own ({@link #raidTasks}), and bite the raiders that come near (S1, {@code DefendGoal}). The colony's soldiers kept
 * as numbers in the galleries pour out at the break in waves ({@link #sendWave}), minors first, until a tenth of the
 * colony's soldiers have come (at least 1, at most {@link #MOST_IN_WAVES}: the user's pick, "waves, up to about 12");
 * real Macrotermes soldiers are drawn along the galleries to where the drumming comes from (Hager and Kirchner 2013).
 *
 * Saved: the level, when it was last raised, the soldier boost. Not saved: where the trouble is, who heard it, the
 * places and waves of a raid.
 */
public class Alarm {
    /**
     * What a soldier is to do about the alarm: go at an enemy, or stand in a cell facing the trouble. {@code guard}: the
     * place it guards (M8 step 3S, S1): standing, it snaps at a foe touching it and lunges at one it feels near, but
     * never one farther than {@link #STAND_LEASH} blocks from this place (its own cell, unless said).
     */
    public record Task(@Nullable LivingEntity enemy, @Nullable BlockPos cell, BlockPos facing, boolean drumOnArrival, @Nullable BlockPos guard) {
        public Task(@Nullable LivingEntity enemy, @Nullable BlockPos cell, BlockPos facing, boolean drumOnArrival) {
            this(enemy, cell, facing, drumOnArrival, cell);
        }
    }

    /**
     * M8 step 3S, S1: a soldier standing at the trouble lunges only at a foe within this many blocks of the place it
     * guards (the breach): blind soldiers defend the opening, they do not hunt (my pick; the chase at the foragers keeps
     * its 16).
     */
    public static final double STAND_LEASH = 4.0;

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
    /**
     * Soldiers go at an enemy only while it is this close (blocks) to where the trouble was: blind soldiers defend the
     * spot, they do not hunt a raider walking home (the lingering lag of 2026-11-05: minors chased Matabeles back to
     * their own nest for minutes, with no way there). The same reach as the Matabele defenders' (M8 step 2e).
     */
    private static final double CHASE_LEASH = 16.0;
    /** A minor this close (blocks) to an enemy goes at it even if it is inside the nest. */
    private static final double NEAR_ENEMY = 8.0;
    /** An enemy this close (blocks) to a forager outside, or to an open way out, raises the alarm. */
    private static final double ENEMY_NEAR_FORAGER = 4.0;
    private static final double ENEMY_NEAR_WAY_OUT = 3.0;
    /** Within this many blocks of the entrance (sideways) a player counts as on the mound, even without mound blocks. */
    private static final int ON_MOUND_RADIUS = 3;

    private record Heard(BlockPos at, long time, Rank rank) {
    }

    /** S3: the share of the colony's soldiers that come to a raid's break (a tenth), and the most (the user's pick). */
    private static final double WAVE_SHARE = 0.1;
    public static final int MOST_IN_WAVES = 12;
    /** S3: ticks between two waves (5 s), the first this long after the break (2 s), and how many a wave (2 or 3); my picks. */
    private static final int WAVE_EVERY = 100;
    private static final int FIRST_WAVE = 40;
    private static final int WAVE_LEAST = 2;
    private static final int WAVE_MOST = 3;
    /** S2: how far (blocks, sideways) round a raid's breach the soldiers' places are, and up or down. */
    private static final int RING = 3;
    private static final int RING_HEIGHT = 2;
    /** S2: a place round the breach a soldier found no way to is given to nobody this long (ticks). */
    private static final int BAD_PLACE_TICKS = 200;

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
    /** M8 step 2f: how far from the trouble the drumming of this wave reaches (0: no limit, through the whole nest). */
    private double waveReach;
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
    /** S2: each soldier's place round a raid's breach (kept, so one coming or going moves nobody), and places no way to. */
    private final Map<UUID, BlockPos> places = new HashMap<>();
    private final Map<BlockPos, Long> badPlaces = new HashMap<>();
    /** S2: the places round the breach, kept for a second. */
    private List<BlockPos> ring = List.of();
    @Nullable
    private BlockPos ringFor;
    private long ringAt = Long.MIN_VALUE;
    /** S3: how many soldiers come to this raid's break in all (-1: no raid), when the next wave comes, and who came by wave. */
    private int waveTarget = -1;
    private long nextWaveAt;
    private final Map<Rank, Integer> cameInWaves = new HashMap<>();

    Alarm(Colony colony) {
        this.colony = colony;
    }

    /** The alarm numbers of this colony's species, or null if it raises no alarm. */
    @Nullable
    public SavannaNumbers.AlarmNumbers numbers() {
        return colony.profile().section(SavannaNumbers.ALARM).orElse(null);
    }

    /** The alarm level now: 0 (calm) to 4. Drops a level each time its calm time has passed since it was last raised. */
    public int level(Level world) {
        SavannaNumbers.AlarmNumbers numbers = numbers();
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
            if (level < 3) {
                endRaidWave();
            }
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
        SavannaNumbers.AlarmNumbers numbers = numbers();
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
        places.clear();
        waveStart = time;
        List<TermiteSoldierEntity> soldiers = new ArrayList<>(colony.part(SavannaParts.SOLDIERS).map(part -> part.getSoldiers(world)).orElse(List.of()));
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
        SavannaNumbers.AlarmNumbers numbers = numbers();
        if (numbers == null || !isBreakIn(world, pos)) {
            return;
        }
        waveReach = 0;
        long now = world.getGameTime();
        breakIns.removeIf(time -> now - time > numbers.breakInWindowTicks() || time > now);
        breakIns.add(now);
        raise(world, breakIns.size() >= numbers.breakInsForLevel4() ? 4 : 3, pos, breaker);
    }

    /**
     * M8 step 2f: a raider broke a gallery open at {@code pos} ({@code colony.RaidDefence}): a break-in, the third level,
     * but the drumming only reaches the soldiers within {@code reach} blocks of it (real soldiers' drumming runs a way
     * down the gallery; the rest of the nest goes on). Those soldiers feel it at once.
     */
    public void raidBreakIn(ServerLevel world, BlockPos pos, @Nullable Entity raider, double reach) {
        if (numbers() == null) {
            return;
        }
        if (level(world) < 3) {
            waveReach = reach;
            startRaidWave(world);
        }
        raise(world, 3, pos, raider);
        long time = world.getGameTime();
        for (TermiteSoldierEntity soldier : colony.part(SavannaParts.SOLDIERS).map(part -> part.getSoldiers(world)).orElse(List.of())) {
            if (!heard.containsKey(soldier.getUUID()) && soldier.blockPosition().distSqr(pos) <= reach * reach) {
                hear(soldier, time);
            }
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
        Map<BlockPos, ?> parts = colony.moundParts(world);
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
        sendWave(world, now);
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

    /**
     * An enemy of the colony, by the shared fight rules ({@link Fights#isFoe}, M8 step 2a): an animal of the species
     * file's {@code fight.foe_types} (chickens eat termites), an insect of a species it lists (the Matabele), or an ant
     * or termite of another colony.
     */
    public boolean isEnemy(Entity entity) {
        return Fights.isFoe(colony.profile(), colony.getSpecies(), colony.getId(), entity);
    }

    // --- the drumming spreading ---------------------------------------------------------------------------------

    /** The soldier feels the alarm: it drums for a moment (the others round it feel that in turn), then acts. */
    private void hear(TermiteSoldierEntity soldier, long time) {
        heard.put(soldier.getUUID(), new Heard(soldier.blockPosition(), time, soldier.getRank()));
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
        if (now >= 3 && waveReach > 0 && spot != null && soldier.blockPosition().distSqr(spot) > waveReach * waveReach) {
            return; // a raid's break-in: the drumming does not reach this far
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
        List<TermiteSoldierEntity> soldiers = new ArrayList<>(colony.part(SavannaParts.SOLDIERS).map(part -> part.getSoldiers(world)).orElse(List.of()));
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
            if (foe == null || foe.blockPosition().distSqr(colony.getCenter()) > reach * reach
                    || foe.blockPosition().distSqr(spot) > CHASE_LEASH * CHASE_LEASH) {
                return Map.of(); // gone, beyond the colony's ground, or away from the trouble: the soldiers stay near home
            }
            for (TermiteSoldierEntity soldier : soldiers) {
                if (soldier.getRank() == Rank.MINOR && heard.containsKey(soldier.getUUID()) && !soldier.isDrumming()
                        && (!colony.nest().isInside(world, soldier.blockPosition()) || soldier.distanceTo(foe) <= NEAR_ENEMY)) {
                    out.put(soldier.getUUID(), new Task(foe, null, foe.blockPosition(), false));
                }
            }
            return out;
        }
        if (waveReach > 0) {
            return raidTasks(world, soldiers); // a raid's break into a feeding gallery (M8 step 3S)
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

    // --- M8 step 3S: a raid's break, the places round it and the waves ------------------------------------------

    /**
     * S2: every soldier that heard the drumming comes to the breach, majors too: the nearest major (else the nearest
     * minor) holds the gallery mouth itself, the others take the places round the opening (nearest the breach first,
     * minors before majors, the ones nearer coming first), facing out, each keeping its own place; any past the places
     * stand at the nest cells nearest the breach. Every one guards the breach (S1: bites raiders within the leash of it).
     */
    private Map<UUID, Task> raidTasks(ServerLevel world, List<TermiteSoldierEntity> soldiers) {
        long time = world.getGameTime();
        badPlaces.values().removeIf(until -> until <= time || until > time + BAD_PLACE_TICKS);
        List<BlockPos> cells = ringNear(world, spot);
        List<TermiteSoldierEntity> answering = new ArrayList<>();
        for (TermiteSoldierEntity soldier : soldiers) {
            if (heard.containsKey(soldier.getUUID())) {
                answering.add(soldier);
            }
        }
        Set<UUID> ids = new HashSet<>();
        answering.forEach(soldier -> ids.add(soldier.getUUID()));
        places.keySet().retainAll(ids);
        places.values().removeIf(cell -> !cells.contains(cell) || badPlaces.containsKey(cell));
        Set<BlockPos> taken = new HashSet<>(places.values());
        BlockPos mouth = !cells.isEmpty() && cells.get(0).equals(spot) ? spot : null;
        if (mouth != null && !taken.contains(mouth) && !badPlaces.containsKey(mouth)) {
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
        List<BlockPos> behind = null;
        int next = 0;
        Map<UUID, Task> out = new HashMap<>();
        for (Rank rank : new Rank[] {Rank.MINOR, Rank.MAJOR}) {
            for (TermiteSoldierEntity soldier : answering) {
                if (soldier.getRank() != rank) {
                    continue;
                }
                BlockPos place = places.get(soldier.getUUID());
                if (place == null) {
                    for (BlockPos cell : cells) {
                        if (!taken.contains(cell) && !badPlaces.containsKey(cell)) {
                            place = cell;
                            places.put(soldier.getUUID(), cell);
                            taken.add(cell);
                            break;
                        }
                    }
                }
                if (place != null) {
                    BlockPos facing = place.equals(spot) ? spot : place.offset(place.getX() - spot.getX(), 0, place.getZ() - spot.getZ());
                    out.put(soldier.getUUID(), new Task(null, place, facing, false, spot));
                    continue;
                }
                if (behind == null) {
                    behind = spotsNear(world, spot);
                }
                if (next < behind.size()) {
                    out.put(soldier.getUUID(), new Task(null, behind.get(next++), spot, false, spot));
                }
            }
        }
        return out;
    }

    /**
     * S2: the places round a raid's breach, nearest it first: the breach itself if one can stand in it (the gallery
     * mouth), then the open cells with a floor within {@link #RING} blocks sideways and {@link #RING_HEIGHT} up or down
     * that open into the breach (outside through the broken cover, or along the gallery; never the ground over a
     * gallery still roofed, which no soldier could reach). Kept for a second.
     */
    private List<BlockPos> ringNear(Level world, BlockPos at) {
        long now = world.getGameTime();
        if (!at.equals(ringFor) || ringAt == Long.MIN_VALUE || now - ringAt >= 20 || now < ringAt) {
            List<BlockPos> cells = new ArrayList<>();
            double most = (RING + 0.5) * (RING + 0.5);
            java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>();
            Set<BlockPos> seen = new HashSet<>();
            seen.add(at.immutable());
            if (passable(world, at)) {
                open.add(at.immutable());
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
                cells.add(0, at.immutable());
            }
            ring = List.copyOf(cells);
            ringFor = at.immutable();
            ringAt = now;
        }
        return ring;
    }

    /** S2: this soldier found no way to its place round the breach: nobody is given it for a while. */
    public void noWayTo(TermiteSoldierEntity soldier, BlockPos cell) {
        if (cell.equals(places.get(soldier.getUUID()))) {
            places.remove(soldier.getUUID());
            badPlaces.put(cell.immutable(), soldier.level().getGameTime() + BAD_PLACE_TICKS);
            tasksAt = Long.MIN_VALUE;
        }
    }

    /** S2: whether this soldier holds a place at a raid's breach now (it stands out to fight there, not sheltered). */
    public boolean holdsPlace(UUID soldier) {
        return places.containsKey(soldier);
    }

    /** S3: a raid broke in and the alarm was calm or lower: how many soldiers come to it (a tenth, 1 to 12). */
    private void startRaidWave(ServerLevel world) {
        int soldiers = colony.getCount(Rank.MINOR.caste()) + colony.getCount(Rank.MAJOR.caste());
        waveTarget = soldiers <= 0 ? 0 : Math.max(1, Math.min(MOST_IN_WAVES, (int) Math.round(soldiers * WAVE_SHARE)));
        nextWaveAt = world.getGameTime() + FIRST_WAVE;
        cameInWaves.clear();
    }

    /** The raid's break is over (the alarm dropped below 3): no more waves, no places. */
    private void endRaidWave() {
        waveTarget = -1;
        cameInWaves.clear();
        places.clear();
        badPlaces.clear();
        waveReach = 0;
    }

    /**
     * S3, once a second: while a raid's break is up and fewer soldiers than its share have come, every
     * {@link #WAVE_EVERY} ticks 2 or 3 more come out of the numbers in the gallery near the break (minors until a quarter
     * of the share is left for majors, my pick: 3 minors to a major, as the colony raises them), and answer at once.
     */
    private void sendWave(ServerLevel world, long now) {
        if (waveTarget <= 0 || spot == null || level < 3 || waveReach <= 0 || now < nextWaveAt) {
            return;
        }
        nextWaveAt = now + WAVE_EVERY;
        int missing = waveTarget - heard.size();
        if (missing <= 0) {
            return;
        }
        List<BlockPos> cells = colony.part(SavannaParts.RAID_DEFENCE).map(raid -> raid.waveCells(world, spot)).orElse(List.of());
        if (cells.isEmpty()) {
            return; // nowhere in the galleries near the break to come out from (or the seal is packed)
        }
        int majorsWanted = waveTarget / 4;
        int wave = Math.min(missing, WAVE_LEAST + colony.random().nextInt(WAVE_MOST - WAVE_LEAST + 1));
        for (int i = 0; i < wave; i++) {
            int majors = (int) heard.values().stream().filter(h -> h.rank() == Rank.MAJOR).count();
            int minors = heard.size() - majors;
            Rank rank = minors < waveTarget - majorsWanted ? Rank.MINOR : Rank.MAJOR;
            if (colony.population().inside(rank.caste()) <= 0) {
                rank = rank == Rank.MINOR ? Rank.MAJOR : Rank.MINOR; // (none of that kind left in the numbers)
            }
            if (colony.population().inside(rank.caste()) <= 0) {
                return;
            }
            BlockPos cell = cells.get(i % cells.size());
            if (colony.population().comeOutAt(world, rank.caste(), cell) instanceof TermiteSoldierEntity soldier) {
                heard.put(soldier.getUUID(), new Heard(cell, now, rank)); // (it comes running: it heard the drumming on its way)
                cameInWaves.merge(rank, 1, Integer::sum);
                tasksAt = Long.MIN_VALUE;
            }
        }
    }

    /** S3: how many soldiers of this kind came out of the numbers in waves at the raid's break now (kept real). */
    public int cameInWaves(Rank rank) {
        return cameInWaves.getOrDefault(rank, 0);
    }

    /** S3: whether more soldiers are still to come to a raid's break (the seal waits for them, as real workers do). */
    public boolean wavesComing(Level world) {
        return waveTarget > 0 && level(world) >= 3 && waveReach > 0 && heard.size() < waveTarget;
    }

    /** S3: how many soldiers come to this raid's break in all, or -1 (no raid's break). For the list and tests. */
    public int waveTarget() {
        return waveTarget;
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

    /** Open to walk through: loaded, nothing solid, no water (S2's ring round a breach). */
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
        String waves = waveTarget > 0 ? ", " + waveTarget + " to come to the raid" : "";
        return " | alarm " + now + " (" + what + (spot != null ? " at " + spot.toShortString() : "") + ", " + heard.size()
                + " soldiers alarmed" + waves + ")" + boost;
    }

    /** Tests only: a sample in every saved number (the save and load check, {@code AwayKit.everyPartSavesAndLoads}). */
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
        endRaidWave();
    }

    /** The soldiers that have felt the current alarm (for tests). */
    public Set<UUID> alarmedSoldiers() {
        return new HashSet<>(heard.keySet());
    }
}
