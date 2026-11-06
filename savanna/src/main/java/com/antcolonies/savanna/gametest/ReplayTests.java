package com.antcolonies.savanna.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.antcolonies.gametest.ReplayRun;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.watch.Replay;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * The raid test bench, round 2, B3 ({@code docs/plans/raid-test-bench.md}): one game test for each saved report copied
 * into {@code data/antcolonies/replays/<name>/} by {@code tools/report_to_test.py} (its {@code place.nbt},
 * {@code colonies.nbt}, {@code about.txt} and {@code replay.json}). Each puts the report's place, insects and colonies
 * back and runs them with every rule watching ({@link ReplayRun}): a bug that came from the place shows again, and once
 * fixed it stays tested. The tests are named {@code replay_<name>}, each in a batch of its own; category {@code replays}.
 *
 * <p>{@code replay.json}: {@code runs} (how many times, each with fresh dice; 3 if not given), {@code seconds} (game
 * seconds a run; 300), {@code required} (false for a bug not fixed yet, so it is listed but does not fail the run).
 */
@GameTestHolder(SavannaMod.MODID)
public class ReplayTests {
    /** Where the replays live in the mod (data/antcolonies/replays). */
    static final String FOLDER = "replays";

    @GameTestGenerator
    public static Collection<TestFunction> replays() {
        List<TestFunction> tests = new ArrayList<>();
        Path root;
        try {
            root = ModList.get().getModFileById(SavannaMod.MODID).getFile().findResource("data", SavannaMod.MODID, FOLDER);
        } catch (RuntimeException e) {
            return tests;
        }
        if (root == null || !Files.isDirectory(root)) {
            return tests;
        }
        List<Path> folders;
        try (Stream<Path> all = Files.list(root)) {
            folders = all.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            SavannaMod.LOGGER.error("[replay] could not list the replays in {}", root, e);
            return tests;
        }
        for (Path folder : folders) {
            String name = folder.getFileName().toString().replace("/", "");
            int runs = 3;
            int seconds = 300;
            boolean required = true;
            Path json = folder.resolve("replay.json");
            if (Files.exists(json)) {
                try {
                    JsonObject options = JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8)).getAsJsonObject();
                    runs = options.has("runs") ? Math.max(1, options.get("runs").getAsInt()) : runs;
                    seconds = options.has("seconds") ? Math.max(10, options.get("seconds").getAsInt()) : seconds;
                    required = !options.has("required") || options.get("required").getAsBoolean();
                } catch (IOException | RuntimeException e) {
                    SavannaMod.LOGGER.error("[replay] {}: replay.json could not be read, its defaults used", name, e);
                }
            }
            String testName = "replay_" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
            int ticks = seconds * 20;
            int runsFinal = runs;
            tests.add(new TestFunction(testName, testName, SavannaMod.MODID + ":arena", Rotation.NONE,
                    ReplayRun.timeoutFor(runs, ticks), 0, required, false, 1, 1, true,
                    helper -> ReplayRun.run(helper, () -> read(folder, name), runsFinal, ticks)));
        }
        return tests;
    }

    private static Replay read(Path folder, String name) {
        try (InputStream place = Files.newInputStream(folder.resolve("place.nbt"));
             InputStream colonies = Files.newInputStream(folder.resolve("colonies.nbt"))) {
            Path about = folder.resolve("about.txt");
            return Replay.read(name, place, colonies, Files.exists(about) ? Files.readString(about, StandardCharsets.UTF_8) : "");
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
