package com.antcolonies.savanna.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Whether a clip goes round and round is said in one place, its file (code review R12-32, 2026-11-27). GeckoLib plays
 * a clip the way the Java asks ({@code thenLoop}, {@code thenPlay}, {@code thenPlayAndHold}), whatever the file says,
 * while every checking tool ({@code tools/clip_check.py}, {@code tools/anim_lab.py}) reads the file: a clip the Java
 * loops and the file plays once has its loop's seam never judged, and the other way round a seam judged that is never
 * drawn. So wherever the Java names a clip in such a call, the file must say the same: loop or not (once and held on
 * its last frame are both "not": the tools judge them alike). A clip wanted both ways is two clips (the barb's and
 * the tadpole's {@code scatter} and {@code struggle}).
 *
 * <p>An animal's own files are those named after its class ({@code BullSharkEntity}: {@code bull_shark*}); a class
 * with none (the insects' shared one) is held against every file with the clip. Names put together while the game
 * runs (a gait's, a film's) cannot be read here.
 */
class ClipLoopsTest {
    private static final Pattern ASKED = Pattern.compile("then(Loop|Play|PlayAndHold)\\(\\s*\"([^\"]+)\"\\s*\\)");

    /** The project's folder: the one holding both mods. */
    private static Path project() {
        for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
            if (Files.isDirectory(at.resolve("savanna/src/main/java")) && Files.isDirectory(at.resolve("framework/src/main/java"))) {
                return at;
            }
        }
        throw new AssertionError("no project folder above " + Path.of("").toAbsolutePath());
    }

    private static List<Path> files(Path under, String ending) throws IOException {
        if (!Files.isDirectory(under)) {
            return List.of();
        }
        try (Stream<Path> all = Files.walk(under)) {
            return all.filter(p -> p.getFileName().toString().endsWith(ending)).sorted().toList();
        }
    }

    /** Each animation file's clips (by the file's name before the first dot): whether the file says the clip loops. */
    private static Map<String, Map<String, Boolean>> clipFiles(Path project) throws IOException {
        Map<String, Map<String, Boolean>> out = new HashMap<>();
        for (String mod : new String[] {"savanna", "framework"}) {
            for (Path file : files(project.resolve(mod).resolve("src/main/resources/assets"), ".animation.json")) {
                JsonObject clips;
                try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    clips = JsonParser.parseReader(in).getAsJsonObject().getAsJsonObject("animations");
                }
                if (clips == null) {
                    continue;
                }
                String name = file.getFileName().toString();
                Map<String, Boolean> loops = out.computeIfAbsent(name.substring(0, name.indexOf('.')), n -> new HashMap<>());
                for (Map.Entry<String, JsonElement> clip : clips.entrySet()) {
                    JsonElement loop = clip.getValue().getAsJsonObject().get("loop");
                    loops.put(clip.getKey(), loop != null && loop.getAsJsonPrimitive().isBoolean() && loop.getAsBoolean());
                }
            }
        }
        return out;
    }

    /** BullSharkEntity.java: bull_shark. */
    private static String animalOf(Path java) {
        String name = java.getFileName().toString().replace(".java", "").replace("Entity", "");
        return name.replaceAll("(?<!^)([A-Z])", "_$1").toLowerCase(Locale.ROOT);
    }

    @Test
    void theJavaAsksEveryClipToLoopAsItsFileSays() throws IOException {
        Path project = project();
        Map<String, Map<String, Boolean>> clipFiles = clipFiles(project);
        assertFalse(clipFiles.isEmpty(), "no animation files found under " + project);
        List<String> wrong = new ArrayList<>();
        int read = 0;
        for (String mod : new String[] {"savanna", "framework"}) {
            for (Path java : files(project.resolve(mod).resolve("src/main/java"), ".java")) {
                String source = Files.readString(java, StandardCharsets.UTF_8);
                String animal = animalOf(java);
                List<String> own = clipFiles.keySet().stream().filter(f -> f.startsWith(animal)).toList();
                Matcher asked = ASKED.matcher(source);
                while (asked.find()) {
                    boolean loop = asked.group(1).equals("Loop");
                    String clip = asked.group(2);
                    List<String> holders = own.stream().filter(f -> clipFiles.get(f).containsKey(clip)).toList();
                    if (holders.isEmpty()) {
                        holders = clipFiles.keySet().stream().filter(f -> clipFiles.get(f).containsKey(clip)).sorted().toList();
                    }
                    for (String file : holders) {
                        read++;
                        if (clipFiles.get(file).get(clip) != loop) {
                            wrong.add(java.getFileName() + " plays \"" + clip + "\" " + (loop ? "round and round" : "once")
                                    + ", " + file + ".animation.json says " + (loop ? "once" : "it loops"));
                        }
                    }
                }
            }
        }
        assertTrue(read > 100, "too few clips read from the Java (" + read + "): the pattern no longer finds them");
        assertTrue(wrong.isEmpty(), "a clip's loop is said two ways (make the file and the Java agree, or make it two "
                + "clips): " + String.join("; ", wrong));
    }
}
