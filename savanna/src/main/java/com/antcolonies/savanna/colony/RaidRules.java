package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.watch.InsectStories;
import com.antcolonies.watch.Watch;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * The raid test bench, B1 ({@code docs/plans/raid-test-bench.md}): the raid rules, checked once a second for every
 * colony that is here, beside the framework's general ones ({@code com.antcolonies.watch.GeneralRules}). Each would
 * have caught a raid bug we had: BUGS 111 (the attack never ending), the frozen column of in-game test 5, ants left out
 * at night, soldiers waiting for ever by a seal. The rules only watch; they never change what an insect does.
 *
 * <p>Also the savanna's words about one insect, for the black box and the labels ({@link #story}).
 */
public final class RaidRules {
    /** stage-limit: a stage this long past its own longest (ticks): 30 s. */
    static final long STAGE_GRACE = 600;
    /** heartbeat: a raid not worked out for this long while its colony is ticked (ticks): 10 s (it is meant to be 1 s). */
    static final long HEARTBEAT = 200;
    /** raider-stuck: a raider this far (blocks) from where it was told to go, still for this long (ticks). */
    static final double ORDER_OFF = 3.0;
    static final long RAIDER_STILL = 600;
    /** raider-stuck: an ant going home by itself, still this long (ticks) and not home. */
    static final long HOMER_STILL = 1200;
    /** home-by-night: from 21:00 to 5:00 (day time ticks, 0 is 6:00): raids end by 19:30, an hour and a half to walk home. */
    static final long NIGHT_FROM = 15000;
    static final long NIGHT_TO = 23000;
    /** home-by-night: outside this long in the night (ticks) breaks it: a minute. */
    static final long OUT_AT_NIGHT = 1200;
    /** home-by-night: an ant this near the nest's middle is home (blocks). */
    static final double HOME = 10.0;
    /** soldiers-go-back: a termite soldier out in the open by day, with nothing about, this long (ticks): 3 minutes. */
    static final long SOLDIER_OUT = 3600;
    /** soldiers-go-back: a soldier this near its mound's middle is at home (blocks; the cathedral's guards stand round it). */
    static final double MOUND_REACH = 14.0;
    /** soldiers-go-back and the label: a Matabele this near counts as a foe about (the drumming's reach). */
    static final double FOE_NEAR = 16.0;
    /** seals-open: a seal standing this long since the last foe near it (ticks): a day and a quarter (the design: next night). */
    static final long SEAL_MOST = 30000;
    /** raid-lists: the most scent trails, finds and ants going home a colony should hold. */
    static final int MOST_TRAILS = 64;
    static final int MOST_FINDS = 64;
    static final int MOST_GOING_HOME = 300;

    private RaidRules() {
    }

    public static void signUp() {
        Watch.signUp("stage-limit", "no raid stage lasts past its own longest time (plus 30 s)", RaidRules::stageLimit);
        Watch.signUp("heartbeat", "a raid not done is worked out at least every 10 s while its colony is here", RaidRules::heartbeat);
        Watch.signUp("raider-stuck", "no raider stands 30 s still away from where it was told to go; none going home stands a minute", RaidRules::raiderStuck);
        Watch.signUp("home-by-night", "from 21:00 to 5:00 every Matabele is home, fighting, or walking home", RaidRules::homeByNight);
        Watch.signUp("soldiers-go-back", "no termite soldier waits 3 minutes out in the open by day with nothing about", RaidRules::soldiersGoBack);
        Watch.signUp("seals-open", "a raid seal opens by the next night once the raiders are gone", RaidRules::sealsOpen);
        Watch.signUp("raid-lists", "a colony's trails, finds and ants going home stay few", RaidRules::raidLists);
        InsectStories.signUp(RaidRules::story);
    }

    private static Raids raidsOf(Colony colony) {
        return colony.part(SavannaParts.RAIDS).orElse(null);
    }

    static void stageLimit(Watch.Look look) {
        Raids raids = raidsOf(look.colony());
        RaidColumn raid = raids == null ? null : raids.column();
        if (raid == null || raid.stage() == RaidColumn.Stage.DONE) {
            return;
        }
        long age = look.now() - raid.stageSince();
        long most = raid.stageMostTicks();
        if (age > most + STAGE_GRACE) {
            look.breach(raid.find().at(), raid.members(), "the raid has been " + raid.stage().name().toLowerCase() + " for " + age / 20
                    + " s, its longest is " + most / 20 + " s; " + raid.members().size() + " real raider(s)");
        }
    }

    static void heartbeat(Watch.Look look) {
        Raids raids = raidsOf(look.colony());
        RaidColumn raid = raids == null ? null : raids.column();
        if (raid == null || raid.stage() == RaidColumn.Stage.DONE) {
            return;
        }
        // (Counted only from the colony's coming back: while away the column is not worked out, and the rule may look
        // before the raid's first second back; docs/BUGS.md 147. Nor before a new raid's first working out: its clock
        // starts below zero, BUGS 159.)
        long since = look.now() - Math.max(Math.max(raid.updatedAt(), raid.startedAt()), look.colony().away().backAt());
        if (since > HEARTBEAT) {
            look.breach(raid.find().at(), raid.members(), "the raid (" + raid.stage().name().toLowerCase() + ") was last worked out "
                    + since / 20 + " s ago while its colony is here");
        }
    }

    static void raiderStuck(Watch.Look look) {
        Raids raids = raidsOf(look.colony());
        if (raids == null) {
            return;
        }
        RaidColumn raid = raids.column();
        List<AbstractAntEntity> stuck = new ArrayList<>();
        StringBuilder why = new StringBuilder();
        boolean walking = raid != null && switch (raid.stage()) {
            case MARCHING, GATHERING, COLLECTING, REGROUPING, HOMING -> true;
            default -> false;
        };
        Set<UUID> homing = raids.goingHomeIds();
        BlockPos center = look.colony().getCenter();
        for (AbstractAntEntity insect : look.insects()) {
            if (!(insect instanceof MatabeleEntity ant) || ant.isDowned() || ant.getFightSpot() != null) {
                continue;
            }
            UUID id = ant.getUUID();
            long still = look.stillFor(ant);
            if (walking && raid.has(id) && !raid.isCasting(id)) {
                RaidColumn.Order order = raid.orderNow(id);
                double off = order == null || order.to() == null ? 0 : Math.sqrt(ant.blockPosition().distSqr(order.to()));
                // Still counts only from when it was put off its place: an ant that stood rightly at its place and was
                // just given a new one is not stuck (the false alarm of the first run, 2026-11-05).
                still = Math.min(still, look.lasting("off:" + id, off > ORDER_OFF));
                if (off > ORDER_OFF && still >= RAIDER_STILL) {
                    stuck.add(ant);
                    why.append(stuck.size() == 1 ? "" : "; ").append(InsectStories.shortId(ant)).append(" told to ")
                            .append(order.to().toShortString()).append(String.format(" (%.0f off)", off)).append(", still ").append(still / 20)
                            .append(" s");
                }
            } else if (homing.contains(id) && still >= HOMER_STILL && !ant.blockPosition().closerThan(center, HOME)
                    && !look.colony().nest().isInside(look.level(), ant.blockPosition())) {
                stuck.add(ant);
                why.append(stuck.size() == 1 ? "" : "; ").append(InsectStories.shortId(ant)).append(" going home (")
                        .append(raids.whyGoingHome(id)).append("), still ").append(still / 20).append(" s");
            }
        }
        if (!stuck.isEmpty()) {
            look.breach(stuck.subList(0, Math.min(10, stuck.size())), stuck.size() + " raider(s) stuck: " + why);
        }
    }

    static void homeByNight(Watch.Look look) {
        Colony colony = look.colony();
        Raids raids = raidsOf(colony);
        ServerLevel level = look.level();
        long time = Math.floorMod(colony.timeOfDay(level), 24000L);
        if (raids == null || time < NIGHT_FROM || time >= NIGHT_TO) {
            return;
        }
        AbstractAntEntity queen = colony.getQueen(level);
        if (queen == null || !colony.nest().isInside(level, queen.blockPosition())) {
            return; // no settled nest (a queen looking for one, or moving house): nowhere to be home
        }
        RaidColumn raid = raids.column();
        List<AbstractAntEntity> out = new ArrayList<>();
        for (AbstractAntEntity insect : look.insects()) {
            if (!(insect instanceof MatabeleEntity ant)) {
                continue;
            }
            UUID id = ant.getUUID();
            boolean away = !ant.blockPosition().closerThan(colony.getCenter(), HOME) && !colony.nest().isInside(level, ant.blockPosition());
            boolean excused = ant.getFightSpot() != null || ant.isParalysed()
                    || raid != null && raid.keepsNoHours() && raid.has(id)
                    || raids.wasSentAnyHour(id) // (BUGS 123: a scout sent "anytime", by the command or the soak, keeps no hours)
                    || raids.isGoingHome(id) && look.stillFor(ant) < RAIDER_STILL
                    // BUGS 142: a scout whose search ended at 19:30 runs home straight on its own goal, not along a trail;
                    // still walking (not standing), it is on its way home like the others (one standing is raider-stuck's)
                    || ant.runsGoal(com.antcolonies.savanna.entity.goal.MatabeleScoutGoal.class) && look.stillFor(ant) < RAIDER_STILL;
            if (look.lasting(id.toString(), away && !excused) > OUT_AT_NIGHT) {
                out.add(ant);
            }
        }
        if (!out.isEmpty()) {
            look.breach(out.subList(0, Math.min(10, out.size())), out.size() + " Matabele out at " + com.antcolonies.species.SpeciesProfile.DayClock.clock(time)
                    + ", not walking home: " + who(look, out));
        }
    }

    static void soldiersGoBack(Watch.Look look) {
        Colony colony = look.colony();
        if (colony.part(SavannaParts.SOLDIERS).isEmpty()) {
            return;
        }
        ServerLevel level = look.level();
        boolean day = !colony.isForagingTime(level);
        boolean calm = colony.part(SavannaParts.ALARM).map(alarm -> alarm.level(level) == 0).orElse(true);
        RaidDefence defence = colony.part(SavannaParts.RAID_DEFENCE).orElse(null);
        List<AbstractAntEntity> out = new ArrayList<>();
        for (AbstractAntEntity insect : look.insects()) {
            if (!(insect instanceof TermiteSoldierEntity soldier)) {
                continue;
            }
            boolean waiting = day && calm && !soldier.isDowned() && level.canSeeSky(soldier.blockPosition())
                    && !soldier.blockPosition().closerThan(colony.getCenter(), MOUND_REACH)
                    && !colony.nest().isInside(level, soldier.blockPosition())
                    && (defence == null || !defence.isShutOut(soldier.getUUID()))
                    && level.getEntitiesOfClass(MatabeleEntity.class, new AABB(soldier.blockPosition()).inflate(FOE_NEAR), MatabeleEntity::isAlive).isEmpty();
            if (look.lasting(soldier.getUUID().toString(), waiting) > SOLDIER_OUT) {
                out.add(soldier);
            }
        }
        if (!out.isEmpty()) {
            look.breach(out.subList(0, Math.min(10, out.size())), out.size() + " termite soldier(s) out in the open by day for over "
                    + SOLDIER_OUT / 1200 + " min with no foe about: " + who(look, out));
        }
    }

    static void sealsOpen(Watch.Look look) {
        RaidDefence defence = look.colony().part(SavannaParts.RAID_DEFENCE).orElse(null);
        if (defence == null) {
            return;
        }
        for (RaidDefence.SealAge seal : defence.sealAges(look.now())) {
            if (seal.sinceFoe() > SEAL_MOST) {
                look.breach(seal.plug(), List.of(), "a raid seal shut " + seal.sinceDue() / 20 + " s, no foe near for " + seal.sinceFoe() / 20
                        + " s, still shut" + (seal.shutOut() > 0 ? " (" + seal.shutOut() + " soldier(s) shut out)" : ""));
            }
        }
    }

    static void raidLists(Watch.Look look) {
        Raids raids = raidsOf(look.colony());
        if (raids == null) {
            return;
        }
        int trails = raids.trails().all().size();
        int finds = raids.findsCount();
        int homing = raids.goingHomeCount();
        if (trails > MOST_TRAILS || finds > MOST_FINDS || homing > MOST_GOING_HOME) {
            look.breach(look.colony().getCenter(), List.of(), "the raid lists grew: " + trails + " scent trails (most " + MOST_TRAILS + "), "
                    + finds + " finds (most " + MOST_FINDS + "), " + homing + " going home (most " + MOST_GOING_HOME + ")");
        }
    }

    private static String who(Watch.Look look, List<AbstractAntEntity> insects) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(6, insects.size()); i++) {
            AbstractAntEntity insect = insects.get(i);
            out.append(i == 0 ? "" : ", ").append(InsectStories.shortId(insect)).append(" at ").append(insect.blockPosition().toShortString());
        }
        return out.append(insects.size() > 6 ? ", ..." : "").toString();
    }

    /** The savanna's words about one insect (the black box and the labels): a Matabele's raid, a termite soldier's seal. */
    static String story(ServerLevel level, AbstractAntEntity insect) {
        Colony colony = insect.getColony();
        if (colony == null) {
            return null;
        }
        UUID id = insect.getUUID();
        if (insect instanceof MatabeleEntity) {
            Raids raids = raidsOf(colony);
            if (raids == null) {
                return null;
            }
            RaidColumn raid = raids.column();
            if (raid != null && raid.stage() != RaidColumn.Stage.DONE && raid.has(id)) {
                StringBuilder words = new StringBuilder(raid.stage().name().toLowerCase());
                if (id.equals(raid.leader())) {
                    words.append(" (leads)");
                }
                if (raid.isDrafted(id)) {
                    words.append(", called to defend");
                }
                if (raid.isCasting(id)) {
                    words.append(", casting about");
                }
                RaidColumn.Order order = raid.orderNow(id);
                if (order != null && order.to() != null) {
                    words.append(String.format(", place %.0f off", Math.sqrt(insect.blockPosition().distSqr(order.to()))));
                } else if (order != null) {
                    words.append(", told to stand");
                }
                if (order != null && order.take() != null) {
                    words.append(", picking up");
                }
                return words.toString();
            }
            if (raids.isScouting(id)) {
                return "scouting";
            }
            if (raids.isGoingHome(id)) {
                return "going home: " + raids.whyGoingHome(id);
            }
            return null;
        }
        if (insect instanceof TermiteSoldierEntity) {
            RaidDefence defence = colony.part(SavannaParts.RAID_DEFENCE).orElse(null);
            if (defence != null && defence.isShutOut(id)) {
                return "shut out by a seal";
            }
        }
        return null;
    }
}
