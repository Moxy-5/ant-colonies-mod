package com.antcolonies.watch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.colony.AwayLife;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The raid test bench, round 2, B4 ({@code docs/plans/raid-test-bench.md}): the soak run. A headless server
 * ({@code tools/soak.sh}, system property {@code antcolonies.soak}) on a real generated world finds land of the species
 * mod's kind ({@link Part#land}), keeps a patch of it loaded, picks the colonies to watch there ({@link Part#subjects}),
 * keeps them living as if a player stood by, and runs the game flat out for {@code antcolonies.soakDays} game days
 * while a pretend player pokes at them now and then (the land unloaded and loaded again, a colony saved and loaded, and
 * the species' own pokes, {@link Part#pokes}), and the whole server restarts about once a game day, while a raid is out
 * if one is. Every rule watches all the while ({@link Watch}, black boxes and reports as in a game). Its notes go into
 * the game folder as it runs ({@code soak-ticks.txt}, {@code soak-sizes.txt}, {@code soak-breaches.txt},
 * {@code soak-pokes.txt}) and are summed up in {@code soak-report.txt} each game day and at the end, when it writes
 * {@code soak-done} and stops. A restart keeps its place in {@code soak-state.nbt}; the script starts the server again
 * until {@code soak-done} is there.
 *
 * <p>The framework knows no species: a species mod signs up its {@link Part} ({@link #signUp}).
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID)
public final class Soak {
    /** What a species mod gives the soak. */
    public interface Part {
        /** The biomes the soak looks for. */
        TagKey<Biome> land();

        /** The colonies to watch round {@code site} (within {@code radius} blocks), once the land there is made. */
        List<Colony> subjects(ServerLevel level, BlockPos site, int radius);

        /** The species' pokes by name; each returns what it did, or null if there was nothing to poke. */
        Map<String, Poke> pokes();

        /** How much more often than the others this poke comes (1 for most). */
        default int weight(String poke) {
            return 1;
        }

        /** Whether something worth a restart in the middle of is going on (a raid out). */
        boolean busy(ServerLevel level, List<Colony> subjects);

        /** Once a second, for its counts. */
        void tick(ServerLevel level, List<Colony> subjects);

        /** Its counts as report lines. */
        List<String> tally();

        /** Its counts kept over a restart. */
        CompoundTag save();

        void load(CompoundTag tag);
    }

    /** One poke of the pretend player. */
    @FunctionalInterface
    public interface Poke {
        @Nullable
        String poke(ServerLevel level, List<Colony> subjects, RandomSource random);
    }

    /** The patch kept loaded: so many chunks each way from the site's chunk (17 x 17 chunks, 272 blocks; my pick). */
    static final int CHUNKS = 8;
    /** How long the land is given to be made and settle before the colonies are looked for (ticks; my pick). */
    static final int SETTLE = 2400;
    /** How many places are tried before the soak gives up finding colonies. */
    static final int PLACES = 12;
    /** Pokes come every 1 to 5 game minutes (my pick). */
    static final int POKE_MIN = 1200;
    static final int POKE_MAX = 6000;
    /** A restart waits at most this long for a raid to be out (ticks) before it goes anyway. */
    static final int RESTART_WAIT = 4800;
    /** How fast it asks the game to run (as fast as it can). */
    static final float TICK_RATE = 1000.0F;
    /** At most this many colonies of the species' own picking are watched (they and their neighbours; my pick). */
    public static final int MAX_SUBJECTS = 3;

    private static final List<Part> PARTS = new ArrayList<>();

    private static boolean active;
    private static CompoundTag state;
    private static RandomSource random;
    private static List<UUID> subjects = List.of();
    private static final Set<String> SEEN = new HashSet<>();
    private static long lastTickSample = -1;
    private static long lastTickSampleNanos;
    private static int restartCountdown;

    private Soak() {
    }

    /** A species mod signs up its part (in its constructor). */
    public static synchronized void signUp(Part part) {
        PARTS.add(part);
    }

    /** Whether this server is a soak run. */
    public static boolean on() {
        return Boolean.getBoolean("antcolonies.soak");
    }

    private static int days() {
        return Math.max(1, Integer.getInteger("antcolonies.soakDays", 1));
    }

    // --- start and stop -------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!on()) {
            return;
        }
        MinecraftServer server = event.getServer();
        active = true;
        SEEN.clear();
        lastTickSample = -1;
        restartCountdown = -1;
        Path file = file(server, "soak-state.nbt");
        state = new CompoundTag();
        if (Files.exists(file)) {
            try {
                state = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            } catch (IOException e) {
                ColonyFrameworkMod.LOGGER.error("[soak] the soak's state could not be read, starting afresh", e);
            }
        }
        long seed = server.overworld().getSeed();
        random = RandomSource.create(seed ^ 0x50A4L ^ (state.getInt("restarts") * 0x9E3779B97F4A7C15L));
        server.tickRateManager().setTickRate(TICK_RATE);
        subjects = new ArrayList<>();
        for (Tag id : state.getList("subjects", Tag.TAG_STRING)) {
            subjects.add(UUID.fromString(id.getAsString()));
        }
        if (!subjects.isEmpty()) {
            Set<UUID> ids = new HashSet<>(subjects);
            AwayLife.setKeptHere(colony -> ids.contains(colony.getId()));
        }
        for (Part part : PARTS) {
            part.load(state.getCompound("part"));
        }
        if (state.getBoolean("running")) {
            state.putInt("restarts", state.getInt("restarts") + 1);
            note(server, "soak-pokes.txt", line(server.overworld(), "restart " + state.getInt("restarts") + ": the server is back"));
            if (state.getLong("reloadAt") == 0) {
                holdLand(server.overworld(), true);
            }
        }
        ColonyFrameworkMod.LOGGER.info("[soak] on: {} game days, seed {}, {} restarts so far", days(), seed, state.getInt("restarts"));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        active = false;
        AwayLife.setKeptHere(null);
    }

    // --- every tick ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!active || !(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || PARTS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        if (now % 20 != 0) {
            return;
        }
        try {
            if (!state.getBoolean("running")) {
                find(level, now);
            } else {
                run(level, now);
            }
        } catch (RuntimeException e) {
            ColonyFrameworkMod.LOGGER.error("[soak] the soak driver failed", e);
            note(level.getServer(), "soak-pokes.txt", line(level, "the soak driver failed: " + e));
        }
    }

    /** Finding a place: savanna, made and settled, with colonies to watch. */
    private static void find(ServerLevel level, long now) {
        Part part = PARTS.get(0);
        if (!state.contains("siteX")) {
            int tried = state.getInt("placesTried");
            if (tried >= PLACES) {
                finish(level, "no place with colonies to watch found in " + PLACES + " tries");
                return;
            }
            BlockPos origin = new BlockPos(tried * 2048, 64, (tried % 2) * 2048);
            Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(biome -> biome.is(part.land()), origin, 6400, 32, 64);
            if (found == null) {
                finish(level, "no land of the kind (" + part.land().location() + ") found near " + origin.toShortString());
                return;
            }
            BlockPos site = found.getFirst();
            state.putInt("siteX", site.getX());
            state.putInt("siteZ", site.getZ());
            state.putLong("loadingSince", now);
            holdLand(level, true);
            note(level.getServer(), "soak-pokes.txt", line(level, "place " + (tried + 1) + ": " + site.getX() + ", " + site.getZ()
                    + " (" + found.getSecond().unwrapKey().map(k -> k.location().toString()).orElse("?") + "), land loading"));
            saveState(level.getServer());
            return;
        }
        if (now - state.getLong("loadingSince") < SETTLE) {
            return;
        }
        BlockPos site = site(level);
        List<Colony> picked = part.subjects(level, site, CHUNKS * 16);
        if (picked.isEmpty()) {
            note(level.getServer(), "soak-pokes.txt", line(level, "no colonies to watch at " + site.toShortString() + ", trying further on"));
            holdLand(level, false);
            state.remove("siteX");
            state.remove("siteZ");
            state.putInt("placesTried", state.getInt("placesTried") + 1);
            saveState(level.getServer());
            return;
        }
        subjects = new ArrayList<>();
        ListTag ids = new ListTag();
        StringBuilder who = new StringBuilder();
        for (Colony colony : picked) {
            subjects.add(colony.getId());
            ids.add(StringTag.valueOf(colony.getId().toString()));
            who.append("\n  ").append(describe(colony, level));
        }
        Set<UUID> keep = new HashSet<>(subjects);
        AwayLife.setKeptHere(colony -> keep.contains(colony.getId()));
        state.put("subjects", ids);
        // From now on the land round each watched colony is what is held (the site's patch let go first).
        holdLand(level, false);
        state.putLongArray("middles", picked.stream().mapToLong(colony -> colony.getCenter().asLong()).toArray());
        holdLand(level, true);
        state.putBoolean("running", true);
        state.putLong("start", now);
        state.putLong("end", now + days() * 24000L);
        state.putLong("nextPoke", now + POKE_MIN + random.nextInt(POKE_MAX - POKE_MIN));
        state.putLong("nextRestart", now + 6000 + random.nextInt(18000));
        state.putLong("nextDay", now);
        state.putString("startedWith", who.toString());
        note(level.getServer(), "soak-pokes.txt", line(level, "watching " + picked.size() + " colonies:" + who));
        saveState(level.getServer());
    }

    /** Running: counts, rules, pokes, restarts, the day's notes, the end. */
    private static void run(ServerLevel level, long now) {
        MinecraftServer server = level.getServer();
        List<Colony> watched = watched(level);
        for (Part part : PARTS) {
            part.tick(level, watched);
        }
        rulesBroken(level);
        if (lastTickSample < 0 || now - lastTickSample >= 1200) {
            long nanos = System.nanoTime();
            if (lastTickSample >= 0) {
                double realSeconds = (nanos - lastTickSampleNanos) / 1e9;
                double msNow = server.getAverageTickTimeNanos() / 1e6;
                note(server, "soak-ticks.txt", String.format(java.util.Locale.ROOT, "%s %.2f ms a tick, %.0f ticks a second, %d insects",
                        when(level), msNow, (now - lastTickSample) / Math.max(0.001, realSeconds), insects(level, watched)));
                state.putDouble("dayMsSum", state.getDouble("dayMsSum") + msNow); // (B5: the day's mean for the trend)
                state.putInt("dayMsN", state.getInt("dayMsN") + 1);
            }
            lastTickSample = now;
            lastTickSampleNanos = nanos;
        }
        if (now >= state.getLong("nextDay")) {
            sizes(level, watched);
            trend(level, watched);
            state.putLong("nextDay", now + 24000);
            report(level, false);
            saveState(server);
        }
        long reloadAt = state.getLong("reloadAt");
        if (reloadAt != 0 && now >= reloadAt) {
            holdLand(level, true);
            state.putLong("reloadAt", 0);
            note(server, "soak-pokes.txt", line(level, "the land is loaded again"));
        }
        if (now >= state.getLong("end")) {
            finish(level, null);
            return;
        }
        if (reloadAt == 0 && now >= state.getLong("nextPoke")) {
            poke(level, watched, now);
            state.putLong("nextPoke", now + POKE_MIN + random.nextInt(POKE_MAX - POKE_MIN));
        }
        long restartAt = state.getLong("nextRestart");
        if (reloadAt == 0 && now >= restartAt && (now >= restartAt + RESTART_WAIT || PARTS.stream().anyMatch(p -> p.busy(level, watched)))) {
            boolean busy = PARTS.stream().anyMatch(p -> p.busy(level, watched));
            note(server, "soak-pokes.txt", line(level, "restart: the server stops" + (busy ? " in the middle of a raid" : "")));
            state.putLong("nextRestart", now + 18000 + random.nextInt(12000));
            saveState(server);
            active = false;
            server.halt(false);
        }
    }

    private static void poke(ServerLevel level, List<Colony> watched, long now) {
        Map<String, Poke> all = new LinkedHashMap<>();
        all.put("unload", (l, w, r) -> {
            holdLand(l, false);
            int ticks = 600 + r.nextInt(1800);
            state.putLong("reloadAt", now + ticks);
            return "the land let go for " + ticks / 20 + " s (no one near: it unloads)";
        });
        all.put("save-and-load", (l, w, r) -> {
            if (w.isEmpty()) {
                return null;
            }
            Colony colony = w.get(r.nextInt(w.size()));
            ColonyManager.get(l).replaceForTests(Colony.load(colony.save()));
            return "saved and loaded " + describe(colony, l);
        });
        for (Part part : PARTS) {
            all.putAll(part.pokes());
        }
        List<String> names = new ArrayList<>();
        for (String name : all.keySet()) {
            int weight = 1;
            for (Part part : PARTS) {
                weight = Math.max(weight, part.weight(name));
            }
            for (int i = 0; i < weight; i++) {
                names.add(name);
            }
        }
        for (int tries = 0; tries < 4; tries++) {
            String name = names.get(random.nextInt(names.size()));
            String did = all.get(name).poke(level, watched, random);
            if (did != null) {
                CompoundTag counts = state.getCompound("pokes");
                counts.putInt(name, counts.getInt(name) + 1);
                state.put("pokes", counts);
                note(level.getServer(), "soak-pokes.txt", line(level, "poke " + name + ": " + did));
                return;
            }
        }
    }

    /** New rules broken since the last look: noted and counted by rule. */
    private static void rulesBroken(ServerLevel level) {
        CompoundTag byRule = state.getCompound("rules");
        for (Watch.Breach breach : Watch.allBreaches()) {
            String key = breach.colony() + "|" + breach.rule() + "|" + breach.tick() + "|" + breach.where().asLong();
            if (!SEEN.add(key)) {
                continue;
            }
            byRule.putInt(breach.rule(), byRule.getInt(breach.rule()) + 1);
            note(level.getServer(), "soak-breaches.txt", when(level) + " " + breach.rule() + " at " + breach.where().toShortString() + ": "
                    + breach.text() + (breach.report() == null ? "" : " (report " + breach.report() + ")"));
        }
        state.put("rules", byRule);
    }

    /** Each watched colony's size: its members and the biggest entries of its save. */
    private static void sizes(ServerLevel level, List<Colony> watched) {
        for (Colony colony : watched) {
            CompoundTag save = colony.save();
            List<Map.Entry<String, Integer>> entries = new ArrayList<>();
            for (String key : save.getAllKeys()) {
                entries.add(Map.entry(key, save.get(key).sizeInBytes()));
            }
            entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            StringBuilder out = new StringBuilder(when(level) + " " + describe(colony, level) + ", save " + save.sizeInBytes() / 1024 + " kB; biggest:");
            for (Map.Entry<String, Integer> entry : entries.subList(0, Math.min(5, entries.size()))) {
                out.append(' ').append(entry.getKey()).append(' ').append(entry.getValue() / 1024).append(" kB,");
            }
            note(level.getServer(), "soak-sizes.txt", out.substring(0, out.length() - 1));
        }
    }

    /**
     * The raid test bench, B5 (the 100-hour check): one line a game day in {@code soak-trend.txt} with the slow things a
     * long game shows: the day's mean milliseconds a tick, the world's real insects, the colonies by species, every
     * colony's save together, each watched colony's members and save, and the strays folded in so far (B7's safety net:
     * each one is an insect that was stuck for minutes, a bug to look at). {@link #slowThings} reads it back for the report.
     */
    private static void trend(ServerLevel level, List<Colony> watched) {
        int samples = state.getInt("dayMsN");
        double ms = samples == 0 ? 0 : state.getDouble("dayMsSum") / samples;
        state.putDouble("dayMsSum", 0);
        state.putInt("dayMsN", 0);
        Map<String, Integer> bySpecies = new java.util.TreeMap<>();
        long saves = 0;
        int strays = 0;
        for (Colony colony : ColonyManager.get(level).getColonies()) {
            bySpecies.merge(colony.getSpecies().getPath(), 1, Integer::sum);
            saves += colony.save().sizeInBytes();
            strays += colony.part(com.antcolonies.colony.ColonyParts.STRAYS).map(com.antcolonies.colony.Strays::folded).orElse(0);
        }
        int insects = 0;
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof com.antcolonies.entity.AbstractAntEntity) {
                insects++;
            }
        }
        StringBuilder row = new StringBuilder(String.format(java.util.Locale.ROOT, "day %d | ms %.2f | insects %d | saves %d kB | strays %d | colonies",
                state.getInt("trendDay") + 1, ms, insects, saves / 1024, strays));
        bySpecies.forEach((species, count) -> row.append(' ').append(species).append(' ').append(count));
        for (Colony colony : watched) {
            row.append(" | ").append(colony.getId().toString(), 0, 8).append(" members ").append(colony.getMemberCount())
                    .append(" save ").append(colony.save().sizeInBytes() / 1024).append(" kB");
        }
        state.putInt("trendDay", state.getInt("trendDay") + 1);
        note(level.getServer(), "soak-trend.txt", row.toString());
    }

    /**
     * B5: the slow things read back from {@code soak-trend.txt}: the first days against the last (ticks slower, saves and
     * insects growing, watched colonies dying out or blowing up, strays), each a plain line, "LOOK:" before one past its
     * limit (my picks: ticks 1.5 times slower and 2 ms more; saves still growing over 5 percent a day in the last half;
     * insects or a colony's members doubled or halved; any stray).
     */
    static List<String> slowThings(List<String> rows) {
        List<String> out = new ArrayList<>();
        if (rows.size() < 2) {
            out.add("  (fewer than 2 game days: nothing to compare yet)");
            return out;
        }
        int n = Math.min(3, rows.size() / 2);
        double msFirst = mean(rows.subList(0, n), "ms ");
        double msLast = mean(rows.subList(rows.size() - n, rows.size()), "ms ");
        boolean slower = msLast > msFirst * 1.5 && msLast > msFirst + 2;
        out.add(String.format(java.util.Locale.ROOT, "  %sms a tick: %.2f the first %d day(s), %.2f the last %d", slower ? "LOOK: " : "", msFirst, n, msLast, n));
        double savesHalf = number(rows.get(rows.size() / 2), "saves ");
        double savesLast = number(rows.get(rows.size() - 1), "saves ");
        int days = rows.size() - 1 - rows.size() / 2;
        double growth = days <= 0 || savesHalf <= 0 ? 0 : (Math.pow(savesLast / savesHalf, 1.0 / days) - 1) * 100;
        out.add(String.format(java.util.Locale.ROOT, "  %ssaves: %.0f kB on day 1, %.0f kB halfway, %.0f kB on the last day (%.1f percent a day in the last half)",
                growth > 5 ? "LOOK: " : "", number(rows.get(0), "saves "), savesHalf, savesLast, growth));
        double insectsFirst = number(rows.get(0), "insects ");
        double insectsLast = number(rows.get(rows.size() - 1), "insects ");
        boolean insectsOff = insectsFirst > 0 && (insectsLast > insectsFirst * 2 || insectsLast < insectsFirst / 2);
        out.add(String.format(java.util.Locale.ROOT, "  %sreal insects in the world: %.0f on day 1, %.0f on the last day", insectsOff ? "LOOK: " : "", insectsFirst, insectsLast));
        double strays = number(rows.get(rows.size() - 1), "strays ");
        out.add(String.format(java.util.Locale.ROOT, "  %sstrays folded in (stuck 3 minutes out of sight): %.0f", strays > 0 ? "LOOK: " : "", strays));
        Map<String, Double> firstMembers = members(rows.get(0));
        Map<String, Double> lastMembers = members(rows.get(rows.size() - 1));
        for (Map.Entry<String, Double> first : firstMembers.entrySet()) {
            Double last = lastMembers.get(first.getKey());
            if (last == null) {
                out.add("  LOOK: watched colony " + first.getKey() + " is gone (" + Math.round(first.getValue()) + " members on day 1)");
            } else {
                boolean off = last > first.getValue() * 2 || last < first.getValue() / 2;
                out.add("  " + (off ? "LOOK: " : "") + "colony " + first.getKey() + ": " + Math.round(first.getValue()) + " members on day 1, "
                        + Math.round(last) + " on the last day");
            }
        }
        return out;
    }

    private static double mean(List<String> rows, String key) {
        return rows.stream().mapToDouble(row -> number(row, key)).average().orElse(0);
    }

    /** The number after {@code key} in a trend line (0 if none). */
    static double number(String row, String key) {
        int at = row.indexOf(key);
        if (at < 0) {
            return 0;
        }
        int from = at + key.length();
        int to = from;
        while (to < row.length() && (Character.isDigit(row.charAt(to)) || row.charAt(to) == '.')) {
            to++;
        }
        try {
            return Double.parseDouble(row.substring(from, to));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Each watched colony's members in a trend line, by its short id. */
    static Map<String, Double> members(String row) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String part : row.split(" \\| ")) {
            int at = part.indexOf(" members ");
            if (at == 8) {
                out.put(part.substring(0, 8), number(part, " members "));
            }
        }
        return out;
    }

    // --- the end and the report ------------------------------------------------------------------------------------

    private static void finish(ServerLevel level, @Nullable String why) {
        MinecraftServer server = level.getServer();
        if (why != null) {
            state.putString("failed", why);
            note(server, "soak-pokes.txt", line(level, "the soak ends: " + why));
        }
        if (state.getBoolean("running")) {
            sizes(level, watched(level));
        }
        report(level, true);
        holdLand(level, false);
        state.putBoolean("done", true);
        saveState(server);
        try {
            Files.writeString(file(server, "soak-done"), "done\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            ColonyFrameworkMod.LOGGER.error("[soak] soak-done could not be written", e);
        }
        ColonyFrameworkMod.LOGGER.info("[soak] done");
        active = false;
        server.halt(false);
    }

    private static void report(ServerLevel level, boolean end) {
        MinecraftServer server = level.getServer();
        StringBuilder out = new StringBuilder();
        long start = state.getLong("start");
        long ran = state.getBoolean("running") ? level.getGameTime() - start : 0;
        out.append("Soak report, seed ").append(level.getSeed()).append(end ? "" : " (so far)").append('\n');
        out.append(String.format(java.util.Locale.ROOT, "Game days: %.2f of %d asked; restarts: %d%n", ran / 24000.0, days(), state.getInt("restarts")));
        if (state.contains("failed")) {
            out.append("FAILED: ").append(state.getString("failed")).append('\n');
        }
        if (state.contains("siteX")) {
            out.append("Place: ").append(state.getInt("siteX")).append(", ").append(state.getInt("siteZ")).append('\n');
        }
        out.append("Watched at the start:").append(state.getString("startedWith")).append('\n');
        out.append("Watched now:");
        for (Colony colony : watched(level)) {
            out.append("\n  ").append(describe(colony, level));
        }
        out.append('\n');
        for (Part part : PARTS) {
            for (String row : part.tally()) {
                out.append(row).append('\n');
            }
        }
        out.append("Pokes:");
        CompoundTag pokes = state.getCompound("pokes");
        for (String name : pokes.getAllKeys()) {
            out.append(' ').append(name).append(' ').append(pokes.getInt(name)).append(',');
        }
        out.append('\n');
        CompoundTag rules = state.getCompound("rules");
        int total = 0;
        for (String rule : rules.getAllKeys()) {
            total += rules.getInt(rule);
        }
        out.append("Rules broken: ").append(total).append(total == 0 ? "\n" : " (all in soak-breaches.txt, each with its report folder)\n");
        List<String> breaches = read(server, "soak-breaches.txt");
        for (String rule : rules.getAllKeys()) {
            out.append("  ").append(rule).append(": ").append(rules.getInt(rule)).append('\n');
            int shown = 0;
            for (String row : breaches) {
                if (row.contains(" " + rule + " at ") && shown++ < 3) {
                    out.append("    ").append(row).append('\n');
                }
            }
        }
        List<String> ticks = read(server, "soak-ticks.txt");
        out.append("Ms a tick (one line a game minute, in soak-ticks.txt): ");
        if (ticks.isEmpty()) {
            out.append("none yet\n");
        } else {
            List<Double> ms = new ArrayList<>();
            for (String row : ticks) {
                int at = row.indexOf(" ms a tick");
                if (at > 0) {
                    try {
                        ms.add(Double.parseDouble(row.substring(row.lastIndexOf(' ', at - 1) + 1, at)));
                    } catch (NumberFormatException ignored) {
                        // (a line of another shape)
                    }
                }
            }
            ms.sort(Double::compare);
            if (!ms.isEmpty()) {
                out.append(String.format(java.util.Locale.ROOT, "lowest %.2f, middle %.2f, highest %.2f; first %s; last %s%n", ms.get(0),
                        ms.get(ms.size() / 2), ms.get(ms.size() - 1), ticks.get(0), ticks.get(ticks.size() - 1)));
            } else {
                out.append('\n');
            }
        }
        out.append("Slow things (B5; one line a game day in soak-trend.txt):\n");
        for (String row : slowThings(read(server, "soak-trend.txt"))) {
            out.append(row).append('\n');
        }
        out.append("Sizes by day (soak-sizes.txt):\n");
        for (String row : read(server, "soak-sizes.txt")) {
            out.append("  ").append(row).append('\n');
        }
        try {
            Files.writeString(file(server, "soak-report.txt"), out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ColonyFrameworkMod.LOGGER.error("[soak] soak-report.txt could not be written", e);
        }
    }

    // --- small helpers --------------------------------------------------------------------------------------------

    private static BlockPos site(ServerLevel level) {
        int x = state.getInt("siteX");
        int z = state.getInt("siteZ");
        return new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z), z);
    }

    /**
     * Keeps (or lets go of) the land loaded and running: while finding, the patch round the site; once watching, the
     * land within {@link #ROUND} chunks of each watched colony's middle (their scouts and raids reach 96 blocks; an ant
     * walking out past the loaded land stands frozen there).
     */
    private static void holdLand(ServerLevel level, boolean hold) {
        if (!state.contains("siteX")) {
            return;
        }
        Set<Long> chunks = new HashSet<>();
        List<BlockPos> middles = new ArrayList<>();
        if (state.contains("middles")) {
            for (long middle : state.getLongArray("middles")) {
                middles.add(BlockPos.of(middle));
            }
        }
        int round = middles.isEmpty() ? CHUNKS : ROUND;
        if (middles.isEmpty()) {
            middles.add(new BlockPos(state.getInt("siteX"), 0, state.getInt("siteZ")));
        }
        for (BlockPos middle : middles) {
            int cx = middle.getX() >> 4;
            int cz = middle.getZ() >> 4;
            for (int x = cx - round; x <= cx + round; x++) {
                for (int z = cz - round; z <= cz + round; z++) {
                    chunks.add(net.minecraft.world.level.ChunkPos.asLong(x, z));
                }
            }
        }
        for (long chunk : chunks) {
            level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(chunk), net.minecraft.world.level.ChunkPos.getZ(chunk), hold);
        }
    }

    /** Chunks kept loaded each way round a watched colony's middle (8: 128 blocks past the scouts' 96; my pick). */
    static final int ROUND = 8;

    private static List<Colony> watched(ServerLevel level) {
        List<Colony> out = new ArrayList<>();
        ColonyManager manager = ColonyManager.get(level);
        for (UUID id : subjects) {
            Colony colony = manager.getColony(id);
            if (colony != null) {
                out.add(colony);
            }
        }
        return out;
    }

    private static int insects(ServerLevel level, List<Colony> watched) {
        Set<UUID> ids = new HashSet<>();
        watched.forEach(colony -> ids.add(colony.getId()));
        int count = 0;
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            UUID colony = ColonyManager.colonyOf(entity);
            if (colony != null && ids.contains(colony)) {
                count++;
            }
        }
        return count;
    }

    static String describe(Colony colony, ServerLevel level) {
        return colony.getSpecies().getPath() + " " + colony.getId().toString().substring(0, 8) + " at " + colony.getCenter().toShortString()
                + ", " + colony.getMemberCount() + " members";
    }

    private static String when(ServerLevel level) {
        long ran = level.getGameTime() - state.getLong("start");
        long day = level.getDayTime();
        return String.format(java.util.Locale.ROOT, "[day %.2f, %02d:%02d]", ran / 24000.0, (int) ((day / 1000 + 6) % 24), (int) (day % 1000 * 60 / 1000));
    }

    private static String line(ServerLevel level, String text) {
        return (state.getBoolean("running") ? when(level) + " " : "[tick " + level.getGameTime() + "] ") + text;
    }

    private static Path file(MinecraftServer server, String name) {
        return server.getServerDirectory().resolve(name);
    }

    private static void note(MinecraftServer server, String name, String row) {
        try {
            Files.writeString(file(server, name), row + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            ColonyFrameworkMod.LOGGER.error("[soak] {} could not be written", name, e);
        }
        ColonyFrameworkMod.LOGGER.info("[soak] {}", row);
    }

    private static List<String> read(MinecraftServer server, String name) {
        try {
            Path path = file(server, name);
            return Files.exists(path) ? Files.readAllLines(path, StandardCharsets.UTF_8) : List.of();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static void saveState(MinecraftServer server) {
        if (!PARTS.isEmpty()) {
            state.put("part", PARTS.get(0).save());
        }
        try {
            NbtIo.writeCompressed(state, file(server, "soak-state.nbt"));
        } catch (IOException e) {
            ColonyFrameworkMod.LOGGER.error("[soak] the soak's state could not be saved", e);
        }
    }
}
