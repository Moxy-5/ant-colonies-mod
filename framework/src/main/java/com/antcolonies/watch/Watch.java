package com.antcolonies.watch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The raid test bench, B1 (the rules, {@code docs/plans/raid-test-bench.md}): the game checks itself, once a second for
 * each colony that is here (loaded, a player near), against a list of rules that must always be true ("a raid stage
 * never lasts past its own limit", "no insect stays walled in"). The framework signs up the rules every species shares
 * ({@link GeneralRules}); a species mod signs up its own ({@link #signUp}). A rule broken is written in the log
 * ({@code [rule] <name> broken ...}), in the colony's black box ({@link BlackBox}), told in the chat of the players in
 * that world (unless quiet), and the first time a rule breaks for a colony in 10 minutes a report is saved
 * ({@link Reports}: the place, the insects, the last 3 minutes).
 *
 * <p>The rules only watch: they never change what an insect does. A rule that throws is switched off for the rest of
 * the game (written in the log), so a checker's own bug never crashes the game.
 *
 * <p>On the game test server the rules watch only the colonies a test asks for ({@link #watchForTests}).
 */
public final class Watch {
    private static final Logger LOG = LoggerFactory.getLogger("antcolonies/rules");

    /** On: checked, logged, told in the chat; quiet: checked and logged, no chat; off: not checked. */
    public enum Mode { ON, QUIET, OFF }

    /** What a rule checks, once a second for a colony: it calls {@link Look#breach} for each thing wrong. */
    @FunctionalInterface
    public interface Check {
        void check(Look look);
    }

    /** A rule: its short name for the log, what it means in words, and its check. */
    public record Rule(String name, String means, Check check) {
    }

    /** A rule broken: which, the colony, where, the insects it is about, the words, when, and the report saved (or null). */
    public record Breach(String rule, UUID colony, BlockPos where, List<UUID> who, String text, long tick, @Nullable String report) {
    }

    /** The same rule broken by the same insect (or at the same place) is told again only after this long (ticks): 5 minutes. */
    static final long REPEAT_AFTER = 6000;
    /** A report is saved for the same rule and colony at most this often (ticks): 10 minutes. */
    static final long REPORT_EVERY = 12000;
    /** The most reports saved by the rules in one game (the player's own reports are not counted). */
    static final int MOST_REPORTS = 20;
    /** Insects of the colony looked at: this near its middle (blocks sideways, up and down). Raids reach 96. */
    static final double REACH = 120.0;
    static final double REACH_UP = 48.0;
    /** Each insect's line goes into the black box this often (seconds). */
    static final int BOX_EVERY_SECONDS = 3;
    /** A broken rule's insects show red labels this long (ticks). */
    static final long FLAGGED_FOR = 1200;

    private static final List<Rule> RULES = new CopyOnWriteArrayList<>();
    private static final Set<String> SWITCHED_OFF = new HashSet<>();
    private static final Map<UUID, ColonyWatch> WATCHED = new HashMap<>();
    private static final Set<UUID> FOR_TESTS = new HashSet<>();
    private static volatile Mode mode = Mode.ON;
    private static int reportsThisGame;

    private Watch() {
    }

    // --- signing up -------------------------------------------------------------------------------------------------

    /** Signs up a rule (while the mod loads; a rule's name is unique). */
    public static synchronized void signUp(String name, String means, Check check) {
        if (RULES.stream().anyMatch(rule -> rule.name().equals(name))) {
            throw new IllegalStateException("A rule named " + name + " is signed up twice");
        }
        RULES.add(new Rule(name, means, check));
    }

    public static List<Rule> rules() {
        return List.copyOf(RULES);
    }

    public static Mode mode() {
        return mode;
    }

    public static void setMode(Mode to) {
        mode = to;
    }

    /** The server stopped: everything forgotten (a new world starts afresh, the rules on). */
    public static synchronized void clear() {
        WATCHED.clear();
        FOR_TESTS.clear();
        SWITCHED_OFF.clear();
        reportsThisGame = 0;
        mode = Mode.ON;
        GeneralRules.clear();
    }

    // --- each colony's watch ----------------------------------------------------------------------------------------

    /** One colony's watch: its black box, where its insects stood still since, what was told when, the breaches. */
    static final class ColonyWatch {
        final BlackBox box = new BlackBox();
        record Still(Vec3 pos, long since) {
        }

        final Map<UUID, Still> still = new HashMap<>();
        final Map<String, Long> told = new HashMap<>();
        final Map<String, Long> reported = new HashMap<>();
        final Map<String, Long> lasting = new HashMap<>();
        final Set<String> lastingTouched = new HashSet<>();
        final Map<UUID, Long> flagged = new HashMap<>();
        final List<Breach> breaches = new ArrayList<>();
        long checkedAt = -1;
    }

    private static synchronized ColonyWatch of(Colony colony) {
        return WATCHED.computeIfAbsent(colony.getId(), id -> new ColonyWatch());
    }

    /** Whether the rules watch this colony now (off on the game test server unless a test asked). */
    public static boolean watches(ServerLevel level, Colony colony) {
        if (mode == Mode.OFF) {
            return false;
        }
        return !(level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer) || FOR_TESTS.contains(colony.getId());
    }

    /** Tests: the rules watch this colony (on the game test server, where they are off by themselves). */
    public static synchronized void watchForTests(Colony colony) {
        FOR_TESTS.add(colony.getId());
    }

    /** The rules this colony broke, oldest first (tests, the report). */
    public static synchronized List<Breach> breaches(Colony colony) {
        ColonyWatch watch = WATCHED.get(colony.getId());
        return watch == null ? List.of() : List.copyOf(watch.breaches);
    }

    /** Every rule broken in this game, oldest first (the rules command). */
    public static synchronized List<Breach> allBreaches() {
        List<Breach> all = new ArrayList<>();
        for (ColonyWatch watch : WATCHED.values()) {
            all.addAll(watch.breaches);
        }
        all.sort(java.util.Comparator.comparingLong(Breach::tick));
        return all;
    }

    /** The colony's black box (the report). */
    public static BlackBox boxOf(Colony colony) {
        return of(colony).box;
    }

    /**
     * A line in the colony's black box (a diary line: the raids' {@code [raid]} lines and the like), if the rules watch
     * it; {@code level} may be null when the writer does not know it (the line then has no tick of its own).
     */
    public static void note(@Nullable ServerLevel level, Colony colony, String line) {
        if (level == null || !watches(level, colony)) {
            return;
        }
        of(colony).box.add(level.getGameTime(), line);
    }

    /** How long (ticks) this insect has stood within a third of a block of one spot, as last seen by the rules (0 if not known). */
    public static long stillFor(AbstractAntEntity insect) {
        Colony colony = insect.getColony();
        if (colony == null) {
            return 0;
        }
        ColonyWatch watch;
        synchronized (Watch.class) {
            watch = WATCHED.get(colony.getId());
        }
        if (watch == null) {
            return 0;
        }
        ColonyWatch.Still still = watch.still.get(insect.getUUID());
        return still == null || watch.checkedAt < 0 ? 0 : watch.checkedAt - still.since();
    }

    /** Whether a rule broke for this insect lately (its label shows red). */
    public static boolean isFlagged(AbstractAntEntity insect, long now) {
        Colony colony = insect.getColony();
        if (colony == null) {
            return false;
        }
        synchronized (Watch.class) {
            ColonyWatch watch = WATCHED.get(colony.getId());
            Long at = watch == null ? null : watch.flagged.get(insect.getUUID());
            return at != null && now - at < FLAGGED_FOR;
        }
    }

    // --- the check, once a second ---------------------------------------------------------------------------------

    /** Each tick: the colonies whose turn it is this tick of the second (the brain's own slots) are checked. */
    public static void tick(ServerLevel level) {
        if (mode == Mode.OFF || RULES.isEmpty()) {
            return;
        }
        int slot = (int) (level.getGameTime() % 20);
        for (Colony colony : List.copyOf(ColonyManager.get(level).getColonies())) {
            if (ColonyManager.tickSlot(colony) != slot || !watches(level, colony) || !colony.away().isHere(level)
                    || !colony.isQueenless() && colony.getQueen(level) == null) {
                continue; // (the brain's own rule: only the colonies it ticked this second)
            }
            check(level, colony);
        }
    }

    /** The colony's insects in the world near its middle (the real ones; the numbers have no place to check). */
    public static List<AbstractAntEntity> insectsOf(ServerLevel level, Colony colony) {
        AABB around = new AABB(colony.getCenter()).inflate(REACH, REACH_UP, REACH);
        return level.getEntitiesOfClass(AbstractAntEntity.class, around, insect -> colony.getId().equals(insect.getColonyId()));
    }

    static void check(ServerLevel level, Colony colony) {
        ColonyWatch watch = of(colony);
        long now = level.getGameTime();
        List<AbstractAntEntity> insects = insectsOf(level, colony);
        Set<UUID> seen = new HashSet<>();
        for (AbstractAntEntity insect : insects) {
            seen.add(insect.getUUID());
            ColonyWatch.Still before = watch.still.get(insect.getUUID());
            if (before == null || before.pos().distanceTo(insect.position()) > 0.3) {
                watch.still.put(insect.getUUID(), new ColonyWatch.Still(insect.position(), now));
            }
        }
        watch.still.keySet().retainAll(seen);
        watch.checkedAt = now;
        watch.lastingTouched.clear();
        Look look = new Look(level, colony, now, insects, watch);
        for (Rule rule : RULES) {
            if (SWITCHED_OFF.contains(rule.name())) {
                continue;
            }
            look.rule = rule.name();
            try {
                rule.check().check(look);
            } catch (RuntimeException e) {
                SWITCHED_OFF.add(rule.name());
                LOG.error("[rule] the rule {} threw while checking the colony at {}: switched off for this game",
                        rule.name(), colony.getCenter().toShortString(), e);
            }
        }
        watch.lasting.keySet().retainAll(watch.lastingTouched);
        // The black box: each insect every few seconds (kept small: well under a megabyte a colony).
        if ((now / 20) % BOX_EVERY_SECONDS != 0) {
            return;
        }
        for (AbstractAntEntity insect : insects) {
            watch.box.add(now, InsectStories.line(level, insect, now - watch.still.get(insect.getUUID()).since()));
        }
    }

    /** What a rule sees: the world, the colony, the time, its insects, and how to say something is wrong. */
    public static final class Look {
        private final ServerLevel level;
        private final Colony colony;
        private final long now;
        private final List<AbstractAntEntity> insects;
        private final ColonyWatch watch;
        String rule = "";

        Look(ServerLevel level, Colony colony, long now, List<AbstractAntEntity> insects, ColonyWatch watch) {
            this.level = level;
            this.colony = colony;
            this.now = now;
            this.insects = insects;
            this.watch = watch;
        }

        public ServerLevel level() {
            return level;
        }

        public Colony colony() {
            return colony;
        }

        public long now() {
            return now;
        }

        /** The colony's insects in the world now (near its middle). */
        public List<AbstractAntEntity> insects() {
            return insects;
        }

        /** How long (ticks) this insect has stood within a third of a block of one spot. */
        public long stillFor(AbstractAntEntity insect) {
            ColonyWatch.Still still = watch.still.get(insect.getUUID());
            return still == null ? 0 : now - still.since();
        }

        /**
         * How long (ticks) {@code holds} has been true for this key at every check in a row (0 when it is not true now).
         * A rule's own clock for "this has been so for a while"; a key not asked about at a check is forgotten.
         */
        public long lasting(String key, boolean holds) {
            String full = rule + "|" + key;
            if (!holds) {
                watch.lasting.remove(full);
                return 0;
            }
            watch.lastingTouched.add(full);
            return now - watch.lasting.computeIfAbsent(full, k -> now);
        }

        /** Something is wrong: told, written down, and (now and then) a report saved. */
        public void breach(BlockPos where, List<UUID> who, String text) {
            Watch.breach(level, colony, watch, rule, where, who, text, now);
        }

        /** Something is wrong about these insects (at the first one's place). */
        public void breach(List<? extends AbstractAntEntity> who, String text) {
            if (who.isEmpty()) {
                return;
            }
            breach(who.get(0).blockPosition(), who.stream().map(AbstractAntEntity::getUUID).toList(), text);
        }
    }

    private static void breach(ServerLevel level, Colony colony, ColonyWatch watch, String rule, BlockPos where, List<UUID> who,
                               String text, long now) {
        String key = rule + "|" + (who.isEmpty() ? where.toShortString() : who.get(0).toString());
        synchronized (Watch.class) {
            Long told = watch.told.get(key);
            if (told != null && now - told < REPEAT_AFTER) {
                return;
            }
            watch.told.put(key, now);
            for (UUID id : who) {
                watch.flagged.put(id, now);
            }
        }
        watch.box.add(now, "[rule] " + rule + " broken at " + where.toShortString() + ": " + text);
        LOG.warn("[rule] {} broken at {} (colony {} at {}): {}", rule, where.toShortString(), colony.getId().toString().substring(0, 8),
                colony.getCenter().toShortString(), text);
        String report = null;
        boolean save;
        synchronized (Watch.class) {
            Long reported = watch.reported.get(rule);
            save = (reported == null || now - reported >= REPORT_EVERY) && reportsThisGame < MOST_REPORTS;
            if (save) {
                watch.reported.put(rule, now);
                reportsThisGame++;
            }
        }
        if (save) {
            report = Reports.save(level, where, rule, rule + " broken: " + text, null);
        }
        Breach breach = new Breach(rule, colony.getId(), where, List.copyOf(who), text, now, report);
        synchronized (Watch.class) {
            watch.breaches.add(breach);
            if (watch.breaches.size() > 200) {
                watch.breaches.remove(0);
            }
        }
        if (mode == Mode.ON && !(level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) {
            String said = text.length() > 160 ? text.substring(0, 157) + "..." : text;
            Component line = Component.literal("[ant rules] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(rule + " at " + where.toShortString() + ": " + said).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(report != null ? " (report saved: " + report + ")" : "").withStyle(ChatFormatting.GRAY));
            for (ServerPlayer player : level.players()) {
                player.sendSystemMessage(line);
            }
        }
    }
}
