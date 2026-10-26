package com.antcolonies.fight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyQuestions;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The nest's defence against a player (M8 step 2e, {@code docs/DESIGN.md}, "Steps 2e and 2f"; a colony part a species
 * lists as {@code nest_defence}, its numbers the species file's {@code defence}, {@link DefenceNumbers}). Real Matabele
 * hold no territory (nests as close as 2 m, Yusuf 2026): they defend the nest and their columns when disturbed, pouring
 * out of the entrance squeaking, and sting. So a player provokes the colony (the user's four calls, 2026-10-25) by
 * <ul>
 * <li>breaking a block of the nest ({@link #blockBroken});</li>
 * <li>hitting or killing a member ({@link #memberHurt});</li>
 * <li>standing in a column: touching a member out on a fight order for {@code column_touch_ticks} (the raids, step 4;
 * the skirmish command until then);</li>
 * <li>walking over the entrance, not sneaking: a few members come out to look, and sting only a player still there after
 * {@code look.sting_after} ticks (a light provocation: only those few attack).</li>
 * </ul>
 * The members within {@code scent_reach} of the trouble join in at once (the alarm scent); trouble at the nest brings
 * defenders out in waves from the real members nearest the entrance ({@code first_wave}, then {@code wave} every
 * {@code wave_every} ticks, at most {@code most}). A defender chases the player up to {@code reach} blocks from where it
 * was provoked, then its order ends and it goes home (back into the numbers in time). Each provocation keeps the defence
 * going {@code keep_going} ticks. The queen stings a provoking player within {@code queen_reach} of her (she never
 * leaves her room). Players in creative or spectator mode provoke nobody and are never attacked (as with every mob).
 * Defenders squeak as they come out (real Megaponera stridulate loudly when disturbed). Nothing saved: after a reload the
 * nest has calmed.
 */
public final class NestDefence {
    private final Colony colony;
    /** The players who provoked the colony, by id. */
    private final Map<UUID, Provoker> provokers = new HashMap<>();
    /** The members sent out to defend, by id. */
    private final Set<UUID> defenders = new HashSet<>();
    /** The players standing on the entrance and since when (game time). */
    private final Map<UUID, Long> onNest = new HashMap<>();
    /** When each player on the entrance was last seen on it (game time). */
    private final Map<UUID, Long> lastOnNest = new HashMap<>();
    /**
     * A player off the entrance for no longer than this (ticks) has not stepped off: a jump, a stumble (BUGS 79: a
     * player jumping on the mound was out of its height now and then, so the lookers were called off and the wait
     * before they sting began again at every landing; real ants feel a jumping player's thuds all the more).
     */
    private static final long OFF_FOR = 40;
    /** The players touching a column, and for how many ticks so far. */
    private final Map<UUID, Integer> touching = new HashMap<>();
    /** When the next wave comes out (game time), and where the trouble at the nest is. */
    private long nextWave;
    @Nullable
    private BlockPos trouble;

    /** One provoking player: where it happened, until when it holds, and for a light one, who may attack. */
    private static final class Provoker {
        BlockPos at;
        long until;
        boolean light;
        /** A light one: the player stayed on the entrance long enough, the lookers may sting. */
        boolean stinging;
        final Set<UUID> lookers = new HashSet<>();

        Provoker(BlockPos at, long until, boolean light) {
            this.at = at;
            this.until = until;
            this.light = light;
        }
    }

    public NestDefence(Colony colony) {
        this.colony = colony;
    }

    @Nullable
    private DefenceNumbers numbers() {
        return DefenceNumbers.of(colony.profile()).orElse(null);
    }

    /** Whether this player can provoke and be attacked: alive, in the world, in survival or adventure mode. */
    private static boolean fair(@Nullable Entity entity) {
        return entity instanceof Player player && player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.isCreative();
    }

    // --- the provocations ----------------------------------------------------------------------------------------------

    /** A member was hurt (the colony news): by a player, a provocation where it happened. */
    public void memberHurt(ServerLevel level, AbstractAntEntity member, Entity by) {
        if (fair(by)) {
            provoke(level, (Player) by, member.blockPosition());
        }
    }

    /** A block was broken (the colony news): by a player, at the nest, a provocation there. */
    public void blockBroken(ServerLevel level, BlockPos pos, @Nullable Player breaker) {
        DefenceNumbers numbers = numbers();
        if (numbers != null && fair(breaker) && isNest(level, pos, numbers)) {
            provoke(level, breaker, pos);
        }
    }

    /** Whether a block is the nest's: near the entrance, or touching a cell of the nest. */
    public boolean isNest(Level level, BlockPos pos, DefenceNumbers numbers) {
        if (Vec3.atCenterOf(pos).distanceTo(Vec3.atCenterOf(colony.getCenter())) <= numbers.nestRadius()) {
            return true;
        }
        if (colony.nest().isInside(level, pos)) {
            return true;
        }
        for (Direction side : Direction.values()) {
            if (colony.nest().isInside(level, pos.relative(side))) {
                return true;
            }
        }
        return false;
    }

    /** A provocation by this player at this place: the members near it join in, and trouble at the nest calls a wave. */
    private void provoke(ServerLevel level, Player player, BlockPos at) {
        DefenceNumbers numbers = numbers();
        if (numbers == null) {
            return;
        }
        long now = level.getGameTime();
        long until = now + numbers.keepGoing();
        Provoker provoker = provokers.get(player.getUUID());
        if (provoker == null) {
            provoker = new Provoker(at.immutable(), until, false);
            provokers.put(player.getUUID(), provoker);
        } else {
            provoker.at = at.immutable();
            provoker.until = provoker.until == Long.MAX_VALUE ? until : Math.max(provoker.until, until);
        }
        provoker.light = false;
        List<AbstractAntEntity> members = members(level);
        for (AbstractAntEntity member : members) {
            if (defenders.size() >= numbers.most()) {
                break;
            }
            if (mayDefend(member) && member.distanceToSqr(Vec3.atCenterOf(at)) <= numbers.scentReach() * numbers.scentReach()) {
                sendOut(level, member, at, numbers);
            }
        }
        if (atNest(at, numbers)) {
            trouble = at.immutable();
            if (nextWave <= now) {
                wave(level, members, numbers.firstWave(), numbers);
                nextWave = now + numbers.waveEvery();
            }
        }
    }

    /** Whether trouble here is at the nest: near the entrance, or under it (the rooms). */
    private boolean atNest(BlockPos at, DefenceNumbers numbers) {
        double near = numbers.nestRadius() + numbers.scentReach();
        BlockPos entrance = colony.getCenter();
        return at.distSqr(entrance) <= near * near || at.getY() < entrance.getY()
                && Math.abs(at.getX() - entrance.getX()) <= near && Math.abs(at.getZ() - entrance.getZ()) <= near;
    }

    /** Up to {@code count} more defenders, the real members nearest the entrance first. */
    private void wave(ServerLevel level, List<AbstractAntEntity> members, int count, DefenceNumbers numbers) {
        BlockPos entrance = colony.getCenter();
        List<AbstractAntEntity> may = new ArrayList<>();
        for (AbstractAntEntity member : members) {
            if (mayDefend(member) && !defenders.contains(member.getUUID())) {
                may.add(member);
            }
        }
        may.sort(Comparator.comparingDouble(member -> member.distanceToSqr(Vec3.atCenterOf(entrance))));
        for (AbstractAntEntity member : may) {
            if (count <= 0 || defenders.size() >= numbers.most()) {
                break;
            }
            sendOut(level, member, trouble != null ? trouble : entrance, numbers);
            count--;
        }
    }

    private void sendOut(ServerLevel level, AbstractAntEntity member, BlockPos at, DefenceNumbers numbers) {
        if (defenders.add(member.getUUID())) {
            member.squeak();
        }
        member.orderToFight(at, level.getGameTime() + numbers.keepGoing(), numbers.reach());
    }

    /** A worker that can fight (the queen keeps to her room, the young and the hurt stay in). */
    private static boolean mayDefend(AbstractAntEntity member) {
        return Caste.WORKER.equals(member.nestCaste()) && !member.fightsNoMore() && !member.isDowned() && member.fightBody() != null;
    }

    // --- once a second -------------------------------------------------------------------------------------------------

    public void tick(ServerLevel level) {
        DefenceNumbers numbers = numbers();
        if (numbers == null) {
            return;
        }
        long now = level.getGameTime();
        // The provocations run out; a player gone, or out of reach of where it was provoked, provokes no more.
        for (Iterator<Map.Entry<UUID, Provoker>> it = provokers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Provoker> entry = it.next();
            Player player = level.getPlayerByUUID(entry.getKey());
            if (entry.getValue().until <= now || !fair(player) || !inReach(player, entry.getValue(), numbers)) {
                it.remove();
            }
        }
        List<AbstractAntEntity> members = null;
        if (!provokers.isEmpty() || !defenders.isEmpty() || anyPlayerNear(level, numbers)) {
            members = members(level);
            lookAtTheNest(level, members, numbers, now);
            touchesAColumn(level, members, numbers);
        } else {
            onNest.clear();
            lastOnNest.clear();
            touching.clear();
        }
        // The defenders: their orders kept while a full provocation holds, the next wave out, the rest sent home.
        boolean fullTrouble = provokers.values().stream().anyMatch(provoker -> !provoker.light);
        if (fullTrouble && members != null) {
            if (trouble != null && now >= nextWave && provokers.values().stream().anyMatch(provoker -> !provoker.light && atNest(provoker.at, numbers))) {
                wave(level, members, numbers.wave(), numbers);
                nextWave = now + numbers.waveEvery();
            }
        } else {
            trouble = null;
        }
        if (!defenders.isEmpty()) {
            for (Iterator<UUID> it = defenders.iterator(); it.hasNext(); ) {
                Entity entity = level.getEntity(it.next());
                if (!(entity instanceof AbstractAntEntity defender) || !defender.isAlive()) {
                    it.remove();
                    continue;
                }
                Provoker nearest = nearestFor(level, defender);
                if (nearest == null) {
                    defender.endFightOrder(); // nothing left to defend against: home
                    it.remove();
                } else {
                    defender.orderToFight(nearest.at, now + numbers.keepGoing(), numbers.reach());
                }
            }
        }
        guardTheQueen(level, numbers, now);
    }

    private static boolean inReach(Player player, Provoker provoker, DefenceNumbers numbers) {
        return player.distanceToSqr(Vec3.atCenterOf(provoker.at)) <= numbers.reach() * numbers.reach();
    }

    /** Whether any fair player is near enough the nest to matter (its members can be out far, so the watch radius). */
    private boolean anyPlayerNear(ServerLevel level, DefenceNumbers numbers) {
        double near = Math.max(numbers.reach(), colony.profile().colony().watchRadius());
        Vec3 entrance = Vec3.atCenterOf(colony.getCenter());
        for (ServerPlayer player : level.players()) {
            if (fair(player) && player.distanceToSqr(entrance) <= near * near) {
                return true;
            }
        }
        return false;
    }

    /** The provocation a defender stands against: the one whose player is nearest it, or null for none (light ones only for their lookers). */
    @Nullable
    private Provoker nearestFor(ServerLevel level, AbstractAntEntity defender) {
        Provoker best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<UUID, Provoker> entry : provokers.entrySet()) {
            Provoker provoker = entry.getValue();
            Player player = level.getPlayerByUUID(entry.getKey());
            if (player == null || provoker.light && !provoker.lookers.contains(defender.getUUID())) {
                continue;
            }
            double distance = player.distanceToSqr(defender);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = provoker;
            }
        }
        if (best == null) {
            // Lookers of a player still on the entrance, not yet stinging: they stay out, looking.
            for (Map.Entry<UUID, Long> entry : onNest.entrySet()) {
                Provoker looking = provokers.get(entry.getKey());
                if (looking != null && looking.lookers.contains(defender.getUUID())) {
                    return looking;
                }
            }
        }
        return best;
    }

    /**
     * Players walking over the entrance (not sneaking): the first moment, a few members come out to look (a light
     * provocation that lets nobody attack yet); still there after {@code look.sting_after}, the lookers may sting.
     */
    private void lookAtTheNest(ServerLevel level, List<AbstractAntEntity> members, DefenceNumbers numbers, long now) {
        DefenceNumbers.Look look = numbers.look();
        Vec3 entrance = Vec3.atBottomCenterOf(colony.getCenter());
        Set<UUID> here = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - entrance.x;
            double dz = player.getZ() - entrance.z;
            if (!fair(player) || player.isShiftKeyDown() || dx * dx + dz * dz > look.radius() * look.radius()
                    || Math.abs(player.getY() - entrance.y) > look.height()) {
                continue;
            }
            here.add(player.getUUID());
            lastOnNest.put(player.getUUID(), now);
            Long since = onNest.get(player.getUUID());
            if (since == null) {
                onNest.put(player.getUUID(), now);
                Provoker provoker = provokers.get(player.getUUID());
                boolean fresh = provoker == null;
                if (fresh) {
                    // Not yet a provocation that lets anyone sting: the lookers' list only, held until they may.
                    provoker = new Provoker(colony.getCenter(), now + look.lasts(), true);
                    provoker.until = Long.MAX_VALUE; // (kept while the player stands there; set when it steps off)
                    provokers.put(player.getUUID(), provoker);
                }
                if (provoker.light) {
                    List<AbstractAntEntity> may = new ArrayList<>(members.stream().filter(NestDefence::mayDefend)
                            .filter(member -> !defenders.contains(member.getUUID())).toList());
                    may.sort(Comparator.comparingDouble(member -> member.distanceToSqr(entrance)));
                    for (AbstractAntEntity member : may.subList(0, Math.min(look.ants(), may.size()))) {
                        provoker.lookers.add(member.getUUID());
                        sendOut(level, member, colony.getCenter(), numbers);
                        member.orderToFight(colony.getCenter(), now + look.lasts(), look.radius() + 2.0);
                    }
                }
            }
        }
        for (Iterator<Map.Entry<UUID, Long>> it = onNest.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> entry = it.next();
            Provoker provoker = provokers.get(entry.getKey());
            if (!here.contains(entry.getKey())) {
                if (now - lastOnNest.getOrDefault(entry.getKey(), now - OFF_FOR - 1) <= OFF_FOR) {
                    continue; // only up in the air a moment (a jump): still on it
                }
                it.remove();
                lastOnNest.remove(entry.getKey());
                if (provoker != null && provoker.light) {
                    if (provoker.until == Long.MAX_VALUE && !provoker.stinging) {
                        provokers.remove(entry.getKey()); // stepped off in time: nothing happened
                    } else {
                        provoker.until = Math.min(provoker.until, now + look.lasts());
                    }
                }
            } else if (provoker != null && provoker.light && now - entry.getValue() >= look.stingAfter()) {
                provoker.stinging = true;
                provoker.until = Long.MAX_VALUE;
            }
        }
    }

    /** Players touching a member out on a fight order (a column) long enough provoke the colony there. */
    private void touchesAColumn(ServerLevel level, List<AbstractAntEntity> members, DefenceNumbers numbers) {
        Set<UUID> touchingNow = new HashSet<>();
        for (AbstractAntEntity member : members) {
            if (member.getFightSpot() == null || defenders.contains(member.getUUID())) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (!fair(player) || touchingNow.contains(player.getUUID())) {
                    continue;
                }
                if (player.getBoundingBox().inflate(0.2).intersects(member.getBoundingBox())) {
                    touchingNow.add(player.getUUID());
                    int ticks = touching.getOrDefault(player.getUUID(), 0) + 20;
                    touching.put(player.getUUID(), ticks);
                    if (ticks >= numbers.columnTouchTicks()) {
                        member.squeak();
                        provoke(level, player, member.blockPosition());
                    }
                } else if (player.distanceToSqr(member) <= 9.0 && member.getRandom().nextInt(4) == 0) {
                    member.squeak(); // a player close to a column: they squeak
                }
            }
        }
        touching.keySet().retainAll(touchingNow);
    }

    /** A provoking player near the queen: she stings too, from where she is (she never leaves her room). */
    private void guardTheQueen(ServerLevel level, DefenceNumbers numbers, long now) {
        if (numbers.queenReach() <= 0.0 || colony.getQueenId() == null || provokers.isEmpty()) {
            return;
        }
        if (!(level.getEntity(colony.getQueenId()) instanceof AbstractAntEntity queen) || !queen.isAlive()) {
            return;
        }
        for (Map.Entry<UUID, Provoker> entry : provokers.entrySet()) {
            Player player = level.getPlayerByUUID(entry.getKey());
            if (!entry.getValue().light && player != null && player.distanceToSqr(queen) <= numbers.queenReach() * numbers.queenReach()) {
                queen.orderToFight(queen.blockPosition(), now + 40, numbers.queenReach());
                return;
            }
        }
    }

    // --- the question --------------------------------------------------------------------------------------------------

    /**
     * Whether this member may attack this foe for provoking the colony ({@link ColonyQuestions#PROVOKED_BY}): a fair
     * player with a provocation that holds, within reach of where it happened; a light one only by its lookers once
     * they may sting; the queen only within her reach.
     */
    public boolean provokedBy(Level level, ColonyQuestions.Provoked asked) {
        DefenceNumbers numbers = numbers();
        if (numbers == null || !fair(asked.foe())) {
            return false;
        }
        Provoker provoker = provokers.get(asked.foe().getUUID());
        if (provoker == null || provoker.until <= level.getGameTime()) {
            return false;
        }
        if (provoker.light && (!provoker.stinging || !provoker.lookers.contains(asked.ant().getUUID()))) {
            return false;
        }
        if (!Caste.WORKER.equals(asked.ant().nestCaste())) {
            return provoker.light ? false : asked.foe().distanceToSqr(asked.ant()) <= numbers.queenReach() * numbers.queenReach();
        }
        return asked.foe().distanceToSqr(Vec3.atCenterOf(provoker.at)) <= numbers.reach() * numbers.reach();
    }

    /** The real members near the nest. */
    private List<AbstractAntEntity> members(ServerLevel level) {
        return colony.population().realMembers(level);
    }

    /** Lines for {@code /antcolonies list}. */
    public String describe(Level level) {
        if (provokers.isEmpty() && defenders.isEmpty()) {
            return "";
        }
        return "\n  defence: " + provokers.size() + " provoking player(s), " + defenders.size() + " defender(s) out";
    }

    /** For the game tests: how many members are out defending. */
    public int defendersOut() {
        return defenders.size();
    }

    /** For the game tests: whether this player is provoking the colony now (a light one only once the lookers may sting). */
    public boolean isProvoking(Player player) {
        Provoker provoker = provokers.get(player.getUUID());
        return provoker != null && (!provoker.light || provoker.stinging);
    }
}
