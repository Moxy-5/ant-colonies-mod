package com.antcolonies.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.neoforged.fml.loading.FMLEnvironment;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * The camera's watch mode (development only, 2026-10-18): a film session that stays open. Started by
 * {@code tools/film.sh watch <scenes>} (or {@code ./gradlew runFilm -PfilmWatch -PfilmScene=...}). After its scenes the
 * camera does not quit but waits in the world, and shoots the scenes again (pictures, {@code times.txt} and
 * {@code checks.txt} exactly as a fresh launch makes them) when:
 * <ul>
 * <li>a file in {@code src/main/resources/assets/antcolonies} changes (the motion library rewriting the animation and
 * model files): the changed files are copied to where the running game reads them ({@code build/resources/main}), then
 * reloaded: animations and models only through GeckoLib's own cache (about a second), anything else (textures, ...)
 * through the game's full resource reload;</li>
 * <li>a shot list it films changes (no reload needed);</li>
 * <li>{@code tools/film.sh shoot [scenes]} asks for it (a file {@code run-film/film-watch/shoot-<id>.txt}, holding the
 * scenes to film from now on, or empty for the same ones).</li>
 * </ul>
 * {@code tools/film.sh stop} (the file {@code run-film/film-watch/stop}) or closing the window ends it. The camera writes
 * how it is doing to {@code run-film/film-watch/status.txt} (shooting or ready, how long the last shoot took, which
 * requests it answered, a heartbeat twice a second) for the script to wait on.
 *
 * <p>Java code cannot be reloaded this way (only the files the game reads); for that, the script's {@code swap} swaps
 * changed method bodies into the running game through the debugger, or a fresh launch starts straight into the scene.
 */
final class FilmWatch {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ENABLED = !FMLEnvironment.production && Boolean.getBoolean("antcolonies.film.watch");
    /** The mod's resources as written by hand or by the tools, and the copy the running game reads. */
    private static final Path SRC = path("antcolonies.film.src");
    private static final Path OUT = path("antcolonies.film.out");
    /** The project folder (shot lists named in a request are found from here). */
    private static final Path PROJECT = path("antcolonies.film.project");
    private static final String ASSETS = "assets/antcolonies";
    /** How often the files are looked at (client ticks), and how many looks in a row they must stay still first. */
    private static final int POLL = 10;
    private static final int STILL = 2;

    /** The files last copied (assets) or filmed (shot lists): their time and size. */
    private static Map<Path, String> assets;
    private static Map<Path, String> sceneFiles;
    /** A change seen and how many looks it has stayed still for; -1 when nothing changed. */
    private static int stillFor = -1;
    private static int ticks;
    /** Requests answered by the shoot now running, and those waiting for the next one. */
    private static final List<String> answering = new ArrayList<>();
    private static final List<String> waiting = new ArrayList<>();
    /** The scenes to film from now on, when a request named new ones (null: the same as before). */
    private static String nextScenes;
    private static boolean shooting;
    private static CompletableFuture<?> reload;
    private static String reloadKind = "none";
    private static long shootStart, reloadMillis;
    private static int shoots;
    private static String state = "starting", lastTook = "-", lastError = "";

    private FilmWatch() {
    }

    static boolean enabled() {
        return ENABLED;
    }

    private static Path path(String property) {
        String value = System.getProperty(property);
        return value == null ? null : Path.of(value);
    }

    /**
     * Called by {@code FilmDirector} every client tick in the world: {@code filming} is true while it still has steps to
     * play. Notes requests and changes, and starts a new shoot once the last one is done.
     */
    static void tick(Minecraft mc, boolean filming) {
        if (!ENABLED) {
            return;
        }
        Path control = control(mc);
        if (assets == null) {
            // the first shoot (the scenes named at launch) is already running: remember what it films
            assets = stamps(SRC == null ? null : SRC.resolve(ASSETS));
            sceneFiles = stamps(FilmDirector.sceneFiles());
            shooting = true;
            shootStart = System.currentTimeMillis();
            state = "shooting";
            try {
                Files.createDirectories(control);
                Files.deleteIfExists(control.resolve("stop"));
            } catch (IOException e) {
                LOG.warn("Film watch: cannot make {}", control, e);
            }
        }
        ticks++;
        if (reload != null) {
            if (!reload.isDone()) {
                return;
            }
            if (reload.isCompletedExceptionally()) {
                lastError = "the " + reloadKind + " reload failed, filmed with the old files (see the game log)";
                LOG.error("Film watch: {}", lastError);
            }
            reloadMillis = System.currentTimeMillis() - shootStart;
            reload = null;
            // FilmChecks asks each model's bones to keep where they were drawn once per model, and a reload makes new
            // bones that would not (their checks then read frozen numbers: 57 false jumps in the climb scene)
            GeckoLibCache.getBakedModels().values().forEach(model -> model.topLevelBones().forEach(FilmWatch::track));
            film(mc);
            return;
        }
        if (shooting && !filming) {
            shooting = false;
            lastTook = String.format(Locale.ROOT, "%.1f", (System.currentTimeMillis() - shootStart) / 1000.0);
            state = "ready";
            answered.clear();
            answered.addAll(answering);
            answering.clear();
            LOG.info("Film watch: shoot {} done in {} s (reload {} {} ms), answering {}", shoots, lastTook, reloadKind,
                    reloadMillis, answered);
            writeStatus(mc);
        }
        if (ticks % POLL != 0) {
            return;
        }
        if (Files.exists(control.resolve("stop"))) {
            LOG.info("Film watch: asked to stop");
            state = "stopped";
            writeStatus(mc);
            mc.stop();
            return;
        }
        readRequests(control);
        writeStatus(mc);
        // a change is filmed once the files have stayed the same for a few looks (a tool has finished writing them);
        // one that comes while a shoot is playing drops that shoot and starts again (its pictures would be old)
        Map<Path, String> now = allStamps();
        if (!now.equals(filmed())) {
            stillFor = now.equals(lastLook) ? stillFor + 1 : 0;
        } else {
            stillFor = -1;
        }
        lastLook = now;
        if (shooting && stillFor >= STILL) {
            LOG.info("Film watch: files changed during shoot {}: starting again", shoots);
            FilmDirector.halt();
            startShoot(mc, true);
        } else if (!shooting && (stillFor >= STILL || (!waiting.isEmpty() && stillFor < 0))) {
            startShoot(mc, false);
        }
    }

    /** The files as the last look saw them, and the requests the last finished shoot answered. */
    private static Map<Path, String> lastLook;
    private static final List<String> answered = new ArrayList<>();

    private static Map<Path, String> allStamps() {
        Map<Path, String> now = stamps(SRC == null ? null : SRC.resolve(ASSETS));
        now.putAll(stamps(FilmDirector.sceneFiles()));
        return now;
    }

    private static Map<Path, String> filmed() {
        Map<Path, String> filmed = new HashMap<>(assets);
        filmed.putAll(sceneFiles);
        return filmed;
    }

    /** Requests ({@code shoot-<id>.txt}): their ids, and new scenes if named. Each file is removed once read. */
    private static void readRequests(Path control) {
        try (Stream<Path> files = Files.list(control)) {
            for (Path file : files.filter(f -> f.getFileName().toString().startsWith("shoot-")).sorted().toList()) {
                String id = file.getFileName().toString().replace("shoot-", "").replace(".txt", "");
                String scenes = Files.readString(file, StandardCharsets.UTF_8).trim();
                Files.deleteIfExists(file);
                if (!scenes.isEmpty()) {
                    nextScenes = resolve(scenes);
                    waiting.add(id);
                } else if (shooting && sameFiles()) {
                    answering.add(id);   // the shoot now running already films what this asks for
                } else {
                    waiting.add(id);
                }
                LOG.info("Film watch: request {} {}", id, scenes.isEmpty() ? "" : scenes);
            }
        } catch (IOException e) {
            LOG.warn("Film watch: cannot read the requests", e);
        }
    }

    /** True when no file changed since the running shoot started. */
    private static boolean sameFiles() {
        return allStamps().equals(filmed());
    }

    /**
     * A new shoot: changed files copied and reloaded (the shoot starts when the reload is done), then filmed; skipped
     * when nothing really changed, unless the shoot before it was {@code dropped} half-way.
     */
    private static void startShoot(Minecraft mc, boolean dropped) {
        shooting = true;
        shoots++;
        stillFor = -1;
        lastLook = null;
        lastError = "";
        shootStart = System.currentTimeMillis();
        answering.addAll(waiting);
        waiting.clear();
        state = "reloading";
        if (nextScenes != null) {
            FilmDirector.setScenes(nextScenes);
            nextScenes = null;
        }
        Map<Path, String> filmedScenes = sceneFiles;
        sceneFiles = stamps(FilmDirector.sceneFiles());
        Map<Path, String> now = stamps(SRC == null ? null : SRC.resolve(ASSETS));
        boolean full = false, gecko = false;
        for (Map.Entry<Path, String> file : now.entrySet()) {
            if (file.getValue().equals(assets.get(file.getKey()))) {
                continue;
            }
            Path relative = SRC.relativize(file.getKey());
            String name = relative.toString().replace('\\', '/');
            try {
                Path target = OUT.resolve(relative);
                if (Files.exists(target) && Files.mismatch(file.getKey(), target) < 0) {
                    continue;   // written again with the same contents (the model tool rewrites its textures too)
                }
                Files.createDirectories(target.getParent());
                Files.copy(file.getKey(), target, StandardCopyOption.REPLACE_EXISTING);
                LOG.info("Film watch: copied {}", name);
            } catch (IOException e) {
                LOG.warn("Film watch: cannot copy {}", name, e);
            }
            if (name.startsWith(ASSETS + "/animations/") || name.startsWith(ASSETS + "/geo/")) {
                gecko = true;
            } else {
                full = true;
            }
        }
        assets = now;
        reloadKind = full ? "full" : gecko ? "gecko" : "none";
        if (!dropped && !full && !gecko && answering.isEmpty() && sceneFiles.equals(filmedScenes)) {
            shooting = false;   // only the files' times changed: nothing new to film
            shoots--;
            state = "ready";
            LOG.info("Film watch: files written again unchanged, nothing to film");
            return;
        }
        LOG.info("Film watch: shoot {} ({} reload) for {}", shoots, reloadKind, answering);
        writeStatus(mc);
        if (full) {
            reload = mc.reloadResourcePacks();
        } else if (gecko) {
            // GeckoLib's own reload only: the animations and models read again, nothing else (a second or so)
            reload = GeckoLibCache.reload(new PreparableReloadListener.PreparationBarrier() {
                @Override
                public <T> CompletableFuture<T> wait(T result) {
                    return CompletableFuture.completedFuture(result);
                }
            }, mc.getResourceManager(), InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Util.backgroundExecutor(), mc);
        } else {
            reloadMillis = 0;
            film(mc);
        }
    }

    private static void track(GeoBone bone) {
        bone.setTrackingMatrices(true);
        bone.getChildBones().forEach(FilmWatch::track);
    }

    private static void film(Minecraft mc) {
        state = "shooting";
        writeStatus(mc);
        FilmDirector.reshoot();
    }

    /** Each file's time and size in a folder or file list (empty when there is none). */
    private static Map<Path, String> stamps(Path folder) {
        return stamps(folder == null ? List.of() : List.of(folder));
    }

    private static Map<Path, String> stamps(List<Path> roots) {
        Map<Path, String> stamps = new HashMap<>();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        stamps.put(file, Files.getLastModifiedTime(file).toMillis() + ":" + Files.size(file));
                    } catch (IOException e) {
                        stamps.put(file, "?");
                    }
                });
            } catch (IOException e) {
                LOG.warn("Film watch: cannot read {}", root, e);
            }
        }
        return stamps;
    }

    /**
     * The scenes a request names, as the camera's comma list of full paths: a file or folder (from the project folder),
     * or a short name: {@code climb} is {@code tools/film/climb.film}, {@code guard} the folder {@code tools/film/guard},
     * {@code all} every scene in {@code tools/film}.
     */
    static String resolve(String scenes) {
        List<String> paths = new ArrayList<>();
        for (String name : scenes.split(",")) {
            name = name.trim();
            if (name.isEmpty()) {
                continue;
            }
            Path base = PROJECT == null ? Path.of("") : PROJECT;
            Path path = base.resolve(name.equals("all") ? "tools/film" : name);
            if (!Files.exists(path) && Files.exists(base.resolve("tools/film/" + name + ".film"))) {
                path = base.resolve("tools/film/" + name + ".film");
            } else if (!Files.exists(path) && Files.isDirectory(base.resolve("tools/film/" + name))) {
                path = base.resolve("tools/film/" + name);
            }
            paths.add(path.toAbsolutePath().normalize().toString());
        }
        return String.join(",", paths);
    }

    private static Path control(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve("film-watch");
    }

    private static void writeStatus(Minecraft mc) {
        List<String> lines = List.of(
                "state=" + state,
                "heartbeat=" + System.currentTimeMillis(),
                "pid=" + ProcessHandle.current().pid(),
                "shoots=" + shoots,
                "answered=" + String.join(",", answered),
                "took=" + lastTook,
                "reload=" + reloadKind + " " + reloadMillis,
                "error=" + lastError,
                "scenes=" + FilmDirector.scenes());
        Path file = control(mc).resolve("status.txt");
        // written whole and then put in place, so the script never reads half of it; Windows refuses the swap while the
        // script is reading, so it is tried a few times (and again at the next heartbeat)
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Files.createDirectories(file.getParent());
                Path temp = file.resolveSibling("status.tmp");
                Files.write(temp, lines, StandardCharsets.UTF_8);
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException stop) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        LOG.debug("Film watch: cannot write the status now");
    }
}
