package com.antcolonies.client;

import java.io.IOException;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.flag.FeatureFlags;
import net.neoforged.api.distmarker.Dist;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.event.GeoRenderEvent;
import software.bernie.geckolib.model.GeoModel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The in-game camera (development only, the animation lab's eyes in the real game; 2026-10-15). Started by
 * {@code ./gradlew runFilm} (or {@code -PfilmScene=tools/film/<scene>.film}; several scenes in one launch:
 * {@code -PfilmScene=tools/film/climb.film,tools/film/work.film}, or a folder, or {@code all} for every scene in
 * {@code tools/film}): it makes a fresh flat creative world, plays each shot list in turn (the floor made new and every
 * insect cleared between them) and saves each scene's screenshots to {@code run-film/screenshots/<scene>/}, then quits.
 * Claude reads the pictures (or a contact sheet made by {@code python tools/anim_lab.py sheet run-film/screenshots/<scene>}).
 *
 * <p>With {@code -PfilmWatch} ({@code tools/film.sh watch}) the camera stays open after its scenes and films them again
 * when the clips change or it is asked to ({@link FilmWatch}).
 *
 * <p>Every picture is also checked ({@link FilmChecks}): body parts drawn inside blocks and parts that suddenly jump go
 * into the scene's {@code checks.txt}, and every scene's summary line into {@code screenshots/checks.txt}, so only the
 * pictures named there need looking at.
 *
 * <p>A shot list is a text file, one step a line ({@code #} starts a comment):
 * <pre>
 * cmd summon antcolonies:matabele_minor 0.5 -60 0.5 {NoAI:1b,Rotation:[90f,0f],CustomName:'"film:walk"'}
 * camera 1.4 -59.7 0.5 90 15      the camera's x y z, turn and tilt (the player, a spectator, is put there: y is its
 *                                 feet, the eye 1.62 above)
 * lens 30                         the scene's lens (narrow for small insects); "fov" changes it for a moment only
 * wait 20                         client ticks
 * shot walk_side                  one screenshot, saved as walk_side.png
 * burst walk_side 10 1            10 screenshots, the game stepped 1 tick between them (after "cmd tick freeze")
 * frames 4                        from now, every burst tick is drawn 4 times, at 0, 1/4, 2/4 and 3/4 of the way to
 *                                 the next tick (as a fast screen shows it between ticks); "frames 1" goes back
 * ant worker_ant walk             clear the floor, then one insect at the middle playing that clip, facing west
 * views walk 10 1.1               side (a burst of 10 frames), front, above and 3/4 shots of it, 1.1 blocks away
 *                                 (side and front level and from further off, like the lab's drawings)
 * walker worker_ant 0.5 -60 0.5 -90   (an optional sixth word adds saved data, e.g. BodyLength:15f)
 *                                 clear the floor, then one insect that walks where it is sent (its goals taken
 *                                 away), standing at x y z facing that way; the game is left frozen
 * step 5                          the frozen game stepped 5 ticks, no picture
 * go 3 -58 0 1.0                  the walker walks its own route (the real legs: AntNavigator) to that cell
 * look 0 -60 0.5                  the walker keeps looking at that point (head and body turn as in the game); "look off"
 * follow 0 0.1 -7 0 0             the camera rides along with the walker, this far off it, with this turn and tilt,
 *                                 for every shot from now ("follow off" stops it)
 * log climb                       one line a tick about the walker's drawn body into climb.txt ("log off" ends it)
 * record on 3                     every moment the walker is drawn (at most 3 a tick) into body.jsonl.gz, for
 *                                 tools/body_course.py ({@link BodyRecorder}); "record off" ends it. The walker may be
 *                                 any animal (an animal's "forget" takes all its goals away, "go" uses its own legs)
 * repeat 100 ... end              the lines between written out 100 times, {i} in them 000, 001, ... (shot side_{i})
 * quit
 * </pre>
 * The Motion Studio's steps (phase 0, 2026-11-25, {@code docs/plans/motion-studio.md}): animals with their minds on, filmed
 * as the game really plays them.
 * <pre>
 * stage lion_hunt -46 -62 -32      a scene a species mod signed up ({@link com.antcolonies.debug.FilmStages}), built on the
 *                                 game's side at that spot (its animals with their own minds; its job, if any, run each tick)
 * seed 7                          the game's and every animal's dice set, so the scene plays the same way each run
 * until stage=FIGHT 3000          the game runs (unfrozen) until some animal's fact reads so
 *                                 ({@link com.antcolonies.debug.FilmFacts}), at most 3000 client ticks
 * aim film-calf                   the cameras below follow the animal of that name ("aim fact holding=true": the first
 *                                 animal with that fact; "aim off")
 * cam south 0 2 -9                a camera this far off the aimed animal's middle, looking at it; every "shot" and
 *                                 every burst picture is taken from each camera in turn, one take (named
 *                                 &lt;burst&gt;_south_00, ...); "cam off" drops them all. While the game runs the first
 *                                 camera keeps it in view
 * record on 1 all                 every animal drawn recorded, each into its own file ({@link BodyRecorder})
 * </pre>
 * An insect named {@code film:<clip>} plays that clip and nothing else ({@code AbstractAntEntity.filmClip}); name tags
 * are hidden while filming. The walker is named {@code film-walker}, so it plays its usual clips. Never active in a real game (only with the {@code antcolonies.film} system property, and
 * never in production).
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID, value = Dist.CLIENT)
public final class FilmDirector {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String WORLD = "film";
    /** The shot lists (comma-separated files or folders), or null when not filming. */
    private static final String SCENE = FMLEnvironment.production ? null : System.getProperty("antcolonies.film");

    private static final Deque<String> steps = new ArrayDeque<>();
    private static boolean worldMade;
    private static boolean inWorld;
    private static int waitTicks;
    /** A screenshot to take on the next drawn frame, or null. */
    private static String pendingShot;
    /** A running burst: its name, how many shots are left, how many ticks between them, the next number. */
    private static String burstName;
    private static int burstLeft, burstGap, burstIndex;
    /** The scene's own lens (its "lens" step), which the "views" step goes back to after its narrow shots. */
    private static int sceneFov = 30;
    /** The lens now (degrees, top to bottom of the picture); set straight on the camera, so below the game's 30 too. */
    private static double lens = 30;

    private FilmDirector() {
    }

    public static boolean active() {
        return SCENE != null;
    }

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Init.Post event) {
        if (!active() || worldMade || !(event.getScreen() instanceof TitleScreen title)) {
            return;
        }
        worldMade = true;
        load();
        Minecraft mc = Minecraft.getInstance();
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                new WorldDataConfiguration(DataPackConfig.DEFAULT, FeatureFlags.DEFAULT_FLAGS));
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(),
                title);
    }

    /** Every shot list named (a folder means each {@code .film} file in it, by name), each starting with a "scene" step. */
    private static void load() {
        java.util.List<Path> scenes = new java.util.ArrayList<>();
        for (String name : sceneList.split(",")) {
            Path path = Path.of(name.trim());
            if (Files.isDirectory(path)) {
                try (var files = Files.list(path)) {
                    files.filter(f -> f.toString().endsWith(".film")).sorted().forEach(scenes::add);
                } catch (IOException e) {
                    LOG.error("Film: cannot read the folder {}", path, e);
                }
            } else {
                scenes.add(path);
            }
        }
        for (Path scene : scenes) {
            try {
                java.util.List<String> lines = Files.readAllLines(scene, StandardCharsets.UTF_8);
                steps.add("scene " + scene.getFileName().toString().replace(".film", ""));
                java.util.List<String> own = new java.util.ArrayList<>();
                for (String line : lines) {
                    String step = line.contains("#") && !line.trim().startsWith("cmd") ? line.substring(0, line.indexOf('#')) : line;
                    if (!step.isBlank()) {
                        own.add(step.trim());
                    }
                }
                steps.addAll(repeats(own));
            } catch (IOException e) {
                LOG.error("Film: cannot read the shot list {}", scene, e);
            }
        }
        LOG.info("Film: {} steps from {} scene(s): {}", steps.size(), scenes.size(), scenes);
    }

    /**
     * A shot list with its {@code repeat n} ... {@code end} blocks written out n times over, {@code {i}} in their lines
     * the time's number from 000 (2026-11-08, the raid march film: three cameras every other tick, round and round,
     * without a shot list thousands of lines long). Blocks do not nest.
     */
    static java.util.List<String> repeats(java.util.List<String> lines) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int at = 0; at < lines.size(); at++) {
            String line = lines.get(at);
            if (!line.startsWith("repeat ")) {
                out.add(line);
                continue;
            }
            int times = Integer.parseInt(line.substring(7).trim());
            int end = at + 1;
            while (end < lines.size() && !lines.get(end).equals("end")) {
                end++;
            }
            java.util.List<String> body = lines.subList(at + 1, end);
            for (int i = 0; i < times; i++) {
                String number = String.format(java.util.Locale.ROOT, "%03d", i);
                for (String step : body) {
                    out.add(step.replace("{i}", number));
                }
            }
            at = end;
        }
        return out;
    }

    /** The scene being filmed (its pictures and logs go into a folder of that name), and its checks. */
    private static String sceneName = "film";
    private static FilmChecks checks = new FilmChecks();
    /** The summary line of each scene's checks so far, for screenshots/checks.txt. */
    private static final StringBuilder summaries = new StringBuilder();
    /** Pictures drawn each burst tick ("frames"), the one being drawn now, and the in-between time insects are drawn at. */
    private static int framesPerTick = 1;
    private static int burstSub;
    private static float drawPartial;

    /** The scene being filmed now (for {@link FilmFrameMeter}). */
    static String sceneName() {
        return sceneName;
    }

    /**
     * The in-between time (0 to 1, from the last tick to the next) insects are drawn at while filming. While a step is
     * on its way the drawn time stays where the last picture was (the step's ticks already run here are taken off):
     * the screen draws frames in between, and each moves the insects' clip clocks on to its time. Without this, after
     * a stepped tick at 2/3 ("frames 3") the frames drew the next tick's 2/3, past the next picture's moment, so the
     * clip ran on too far, but only when a frame came between that tick and the picture's (timing luck).
     */
    public static float drawPartial() {
        return drawPartial - FilmStep.ranSoFar();
    }

    /** Whether the game stands still for the camera's takes (frozen, stepped tick by tick). */
    private static boolean frozen() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.tickRateManager().isFrozen();
    }

    /**
     * The in-between time an insect is drawn at while filming: the camera's own in a frozen take and for any picture
     * (a frozen game's own one jitters, and a picture's moment must be the same every run), the game's own while the
     * game runs with no picture due (code review R12-49: it stayed at the last take's, so a running scene drew and
     * recorded every frame of a tick at one moment).
     */
    static float insectPartial(float gamePartial) {
        return frozen() || pendingShot != null ? drawPartial() : gamePartial;
    }

    /**
     * The in-between time any other animal is drawn at while filming: the game's own (a frozen game draws the tick's
     * end, as every film of one picture a tick was approved), but with "frames n" in a frozen take the n moments up to
     * the tick's end, the last the game's (code review R12-43: only the insects were drawn at the camera's time, so
     * "frames 4" gave an animal four pictures of one moment).
     */
    static float animalPartial(float gamePartial) {
        return framesPerTick > 1 && frozen() ? drawPartial() + 1.0F / framesPerTick : gamePartial;
    }

    /** The "scene" step: the last scene's log and checks written, then everything set back for the next one. */
    private static void startScene(String name) {
        endScene();
        sceneName = name;
        checks = new FilmChecks();
        lens = sceneFov = 30;
        follow = null;
        lookAt = null;
        cams.clear();
        aimName = null;
        aimFact = null;
        untilLeft = 0;
        FilmFactsBoard.setStageJob(null);
        framesPerTick = 1;
        drawPartial = 0.0F;
        clipTimes.clear();
        LOG.info("Film: scene {}", name);
        Deque<String> now = new ArrayDeque<>();
        now.add("cmd tick unfreeze");
        now.add("cmd kill @e[type=!player]");
        now.add("wait 25");
        now.add("cmd kill @e[type=!player]");
        // the flat world's floor made new round the middle (an earlier scene may have built or dug there)
        now.add("cmd fill -12 -63 -12 12 -62 12 dirt");
        now.add("cmd fill -12 -61 -12 12 -61 12 grass_block");
        now.add("cmd fill -12 -60 -12 12 -48 12 air");
        now.add("wait 5");
        pushFront(now);
    }

    /** Writes the scene's log and its checks (checks.txt beside its pictures, the summary into screenshots/checks.txt). */
    private static void endScene() {
        Minecraft mc = Minecraft.getInstance();
        writeLog(mc);
        BodyRecorder.stop();
        if (checks.isEmpty()) {
            return;
        }
        java.util.List<String> lines = checks.report();
        String summary = sceneName + ": " + lines.get(lines.size() - 1);
        LOG.info("Film checks, {}", summary);
        summaries.append(summary).append(System.lineSeparator());
        try {
            Path folder = folder(mc);
            Files.createDirectories(folder);
            Files.write(folder.resolve("checks.txt"), lines, StandardCharsets.UTF_8);
            Files.writeString(folder.getParent().resolve("checks.txt"), summaries.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Film: cannot write the checks", e);
        }
        checks = new FilmChecks();
    }

    /** The scene's folder of pictures and logs. */
    private static Path folder(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve("screenshots").resolve(sceneName);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) {
            return;
        }
        if (!inWorld) {
            inWorld = true;
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            mc.options.pauseOnLostFocus = false;
            // 60 frames a second, no screen lock (the user, 2026-11-25: unlimited, the camera drew 1,000 to 1,600 frames a
            // second and lagged the whole PC for hours). The pictures go by game ticks, and the frame-time meter times each
            // frame's drawing alone (FilmFrameMeter), so neither changes; only its whole-frame column now holds the wait
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(60);
            send(mc, "gamemode spectator");
            send(mc, "time set noon");
            waitTicks = 60;                     // let the land load and settle
            return;
        }
        FilmWatch.tick(mc, !(filmed && steps.isEmpty() && waitTicks == 0 && pendingShot == null && burstLeft == 0));
        if (BodyRecorder.recording()) {
            followWalker(mc);                  // recording, the camera rides along every tick (it must see it)
        }
        if (pendingShot != null) {
            return;                            // waiting for the frame to be drawn
        }
        if (untilLeft > 0) {
            if (!cams.isEmpty()) {
                aimCamera(mc, cams.get(0));    // the game runs: the first camera keeps the scene in view (so it is drawn)
            }
            java.util.UUID met = FilmFactsBoard.find(untilKey, untilValue);
            if (met != null) {
                LOG.info("Film: until {}={} met after {} ticks (game tick {})", untilKey, untilValue, untilAsked - untilLeft, FilmFactsBoard.tick());
                untilLeft = 0;
            } else if (--untilLeft == 0) {
                LOG.warn("Film: until {}={} NOT met in {} ticks; the scene goes on", untilKey, untilValue, untilAsked);
            } else {
                return;
            }
        }
        if (waitTicks > 0) {
            if (!FilmStep.arrived()) {         // a burst's step seen arriving ends its wait at once
                if (--waitTicks == 0) {
                    FilmStep.gaveUp();
                }
                return;
            }
            waitTicks = 0;
            // the pictures from the next tick: Minecraft's drawing learns the game stands still a frame late, so the first
            // frame after a step still draws the animals as stepping, at the tick's start; every camera of a take then
            // shot the next frame, at its end (BUGS 381). Ants and termites are drawn at the camera's own time, which
            // until the next picture is that picture's (in a burst of several frames a tick, its first: left at the last
            // frame's, their clips ran a tick ahead and never came back)
            drawPartial = (float) burstSub / framesPerTick;
            return;
        }
        if (burstLeft > 0) {
            drawPartial = (float) burstSub / framesPerTick;
            followWalker(mc);
            String number = String.format(framesPerTick > 1 ? "%03d" : "%02d", burstIndex);
            if (!cams.isEmpty()) {
                // one take from every camera: the frozen game is not stepped until each has its picture
                Cam cam = cams.get(burstCam);
                aimCamera(mc, cam);
                pendingShot = burstName + "_" + cam.name() + "_" + number;
                if (++burstCam < cams.size()) {
                    return;
                }
                burstCam = 0;
            } else {
                pendingShot = burstName + "_" + number;
            }
            burstIndex++;
            if (++burstSub < framesPerTick) {
                return;                        // the same tick again, a little further on
            }
            burstSub = 0;
            burstLeft--;
            if (burstLeft > 0 && burstGap > 0) {
                if (FilmStep.step(mc, burstGap)) {
                    waitTicks = 20 + burstGap; // at most: the step is seen arriving long before
                } else {
                    send(mc, "tick step " + burstGap);
                    waitTicks = 3 + burstGap;  // the stepped ticks reach the client
                }
            }
            return;
        }
        drawPartial = 0.0F;
        while (!steps.isEmpty() && waitTicks == 0 && pendingShot == null && burstLeft == 0 && untilLeft == 0) {
            run(mc, steps.poll());
        }
        boolean done = steps.isEmpty() && waitTicks == 0 && pendingShot == null && burstLeft == 0 && untilLeft == 0;
        if (done && !filmed) {
            filmed = true;
            endScene();
            LOG.info("Film: done");
        }
        if (done && !FilmWatch.enabled()) {
            FilmStep.letGo();
            mc.stop();
        }
    }

    /** The shot lists filmed (comma-separated, changed by the watch mode), and whether they have all been played. */
    private static String sceneList = SCENE;
    private static boolean filmed;

    /** The watch mode ({@link FilmWatch}): the shot lists filmed now, as named and as files and folders. */
    static String scenes() {
        return sceneList;
    }

    static java.util.List<Path> sceneFiles() {
        return java.util.Arrays.stream(sceneList.split(",")).map(name -> Path.of(name.trim())).toList();
    }

    static void setScenes(String scenes) {
        sceneList = scenes;
    }

    /** The watch mode: every scene played again from the start, its old pictures and checks cleared first. */
    static void reshoot() {
        Minecraft mc = Minecraft.getInstance();
        Path screenshots = mc.gameDirectory.toPath().resolve("screenshots");
        halt();
        summaries.setLength(0);
        load();
        for (String step : steps) {
            if (step.startsWith("scene ")) {
                deleteFolder(screenshots.resolve(step.substring(6)));
            }
        }
        try {
            Files.deleteIfExists(screenshots.resolve("checks.txt"));
        } catch (IOException e) {
            LOG.warn("Film: cannot clear the checks", e);
        }
        filmed = false;
    }

    /** The watch mode: the shoot now playing dropped where it is (its files changed), nothing of it written. */
    static void halt() {
        steps.clear();
        waitTicks = 0;
        pendingShot = null;
        burstLeft = 0;
        FilmStep.cancel();
        logName = null;
        logLines.setLength(0);
        BodyRecorder.stop();
        untilLeft = 0;
        FilmFactsBoard.setStageJob(null);
        checks = new FilmChecks();
        filmed = true;
    }

    private static void deleteFolder(Path folder) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (var files = Files.walk(folder)) {
            for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        } catch (IOException e) {
            LOG.warn("Film: cannot clear {}", folder, e);
        }
    }

    private static void run(Minecraft mc, String step) {
        String[] w = step.split("\\s+");
        switch (w[0]) {
            case "cmd" -> send(mc, step.substring(3).trim());
            case "wait" -> waitTicks = Integer.parseInt(w[1]);
            case "camera" -> {
                send(mc, String.format("tp @s %s %s %s %s %s", w[1], w[2], w[3], w[4], w[5]));
                waitTicks = 3;
            }
            case "fov" -> lens = Double.parseDouble(w[1]);
            case "hitboxes" -> mc.getEntityRenderDispatcher().setRenderHitBoxes(w[1].equals("on"));   // F3+B, e.g. for body hit boxes
            case "lens" -> lens = sceneFov = Integer.parseInt(w[1]);
            case "shot" -> {
                if (cams.isEmpty()) {
                    followWalker(mc);
                    pendingShot = w[1];
                } else {
                    burstName = w[1];          // a one-picture burst: one take from every camera
                    burstLeft = 1;
                    burstGap = 0;
                    burstIndex = 0;
                    burstSub = 0;
                    burstCam = 0;
                }
            }
            case "burst" -> {
                burstName = w[1];
                burstLeft = Integer.parseInt(w[2]);
                burstGap = w.length > 3 ? Integer.parseInt(w[3]) : 1;
                burstIndex = 0;
                burstSub = 0;
                burstCam = 0;
            }
            case "step" -> {
                int ticks = Integer.parseInt(w[1]);
                if (FilmStep.step(mc, ticks)) {
                    waitTicks = 20 + ticks;     // at most: the step is seen arriving long before
                } else {
                    send(mc, "tick step " + ticks);
                    waitTicks = 3 + ticks;
                }
            }
            case "frames" -> framesPerTick = Math.max(1, Integer.parseInt(w[1]));
            case "scene" -> startScene(w[1]);
            case "ant" -> putAnt(w[1], w[2]);
            case "views" -> views(w[1], w.length > 2 ? Integer.parseInt(w[2]) : 1,
                    w.length > 3 ? Double.parseDouble(w[3]) : 1.1);
            case "walker" -> putWalker(w[1], w[2], w[3], w[4], w[5], w.length > 6 ? w[6] : null);
            case "freshclocks" -> freshClocks();
            case "forget" -> onWalker(mc, mob -> {
                if (mob instanceof AbstractAntEntity ant) {
                    ant.forgetGoalsForFilm();
                    ant.getNavigator().stop();
                } else {
                    forgetGoals(mob);
                }
            });
            case "go" -> {
                BlockPos goal = new BlockPos(Integer.parseInt(w[1]), Integer.parseInt(w[2]), Integer.parseInt(w[3]));
                double speed = w.length > 4 ? Double.parseDouble(w[4]) : 1.0;
                onWalker(mc, mob -> LOG.info("Film: walker to {}: {}", goal, (mob instanceof AbstractAntEntity ant
                        ? ant.getNavigator().moveTo(goal, speed)
                        : mob.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, speed)) ? "route found" : "NO ROUTE"));
            }
            case "look" -> lookAt = w[1].equals("off") ? null
                    : new Vec3(Double.parseDouble(w[1]), Double.parseDouble(w[2]), Double.parseDouble(w[3]));
            case "follow" -> follow = w[1].equals("off") ? null : new double[] {Double.parseDouble(w[1]),
                    Double.parseDouble(w[2]), Double.parseDouble(w[3]), Double.parseDouble(w[4]), Double.parseDouble(w[5])};
            case "log" -> {
                writeLog(mc);
                logName = w[1].equals("off") ? null : w[1];
            }
            case "record" -> {
                if (w[1].equals("off")) {
                    BodyRecorder.stop();
                } else {
                    BodyRecorder.start(folder(mc), w.length > 2 ? Integer.parseInt(w[2]) : 3, w.length > 3 && w[3].equals("all"));
                }
            }
            case "stage" -> stage(mc, w[1], Integer.parseInt(w[2]), Integer.parseInt(w[3]), Integer.parseInt(w[4]));
            case "seed" -> seed(mc, Long.parseLong(w[1]));
            case "until" -> {
                String[] kv = w[1].split("=", 2);
                untilKey = kv[0];
                untilValue = kv.length > 1 ? kv[1] : "true";
                untilAsked = untilLeft = Math.max(1, Integer.parseInt(w[2]));
            }
            case "aim" -> {
                aimName = null;
                aimFact = null;
                if (w[1].equals("fact")) {
                    aimFact = w[2].split("=", 2);
                } else if (!w[1].equals("off")) {
                    aimName = step.substring(4).trim();
                }
            }
            case "cam" -> {
                if (w[1].equals("off")) {
                    cams.clear();
                } else {
                    cams.add(new Cam(w[1], Double.parseDouble(w[2]), Double.parseDouble(w[3]), Double.parseDouble(w[4])));
                }
            }
            case "quit" -> {
                // the end of this scene: on to the next one, if any
                while (!steps.isEmpty() && !steps.peek().startsWith("scene ")) {
                    steps.poll();
                }
            }
            default -> LOG.warn("Film: unknown step '{}'", step);
        }
    }

    // --- the Motion Studio's steps (phase 0, 2026-11-25) ------------------------------------------------------------------

    /** A camera of the "cam" step: its name and where it is from the aimed animal's middle (blocks). */
    private record Cam(String name, double dx, double dy, double dz) {
    }

    private static final java.util.List<Cam> cams = new java.util.ArrayList<>();
    /** The camera taking the burst's picture now. */
    private static int burstCam;
    /** The aimed animal: by name, or by a fact (key, value); both null when none. */
    private static String aimName;
    private static String[] aimFact;
    /** The "until" step: the fact waited for, the client ticks left and asked. */
    private static String untilKey, untilValue;
    private static int untilLeft, untilAsked;

    /** The animal the cameras follow, as this side draws it, or null. */
    @Nullable
    private static Entity aimed(Minecraft mc) {
        java.util.UUID byFact = aimFact == null ? null : FilmFactsBoard.find(aimFact[0], aimFact.length > 1 ? aimFact[1] : "true");
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (aimName != null && entity.getCustomName() != null && aimName.equals(entity.getCustomName().getString())
                    || byFact != null && byFact.equals(entity.getUUID())) {
                return entity;
            }
        }
        return null;
    }

    /** Puts the camera (the player, on this side, so at once) at this camera's place, looking at the aimed animal's middle. */
    private static void aimCamera(Minecraft mc, Cam cam) {
        Entity target = aimed(mc);
        if (target == null) {
            return;                             // nothing to aim at (not come yet, or gone): the camera stays
        }
        Vec3 middle = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        Vec3 eye = middle.add(cam.dx(), cam.dy(), cam.dz());
        Vec3 to = middle.subtract(eye);
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        mc.player.moveTo(eye.x, eye.y - EYE, eye.z, yaw, pitch);
        mc.player.setOldPosAndRot();
    }

    /** The "stage" step: the species' scene built on the game's side at x y z; its job (if any) run each game tick. */
    private static void stage(Minecraft mc, String name, int x, int y, int z) {
        com.antcolonies.debug.FilmStages.Stage stage = com.antcolonies.debug.FilmStages.get(name);
        if (stage == null) {
            LOG.warn("Film: no scene '{}' (signed up: {})", name, com.antcolonies.debug.FilmStages.names());
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            LOG.warn("Film: no game server for the scene {}", name);
            return;
        }
        FilmStep.serve(mc, () -> {
            FilmFactsBoard.setStageJob(stage.build(server.overworld(), new BlockPos(x, y, z)));
            LOG.info("Film: scene {} built at {} {} {}", name, x, y, z);
        });
        waitTicks = 3;
    }

    /**
     * The "seed" step: the game's dice and every animal's set from this number (each animal by the order it came in),
     * so a scene with its minds on plays the same way each run (as far as the game's own timing allows).
     */
    private static void seed(Minecraft mc, long seed) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        FilmStep.serve(mc, () -> {
            var level = server.overworld();
            level.getRandom().setSeed(seed);
            java.util.List<Entity> all = new java.util.ArrayList<>();
            level.getAllEntities().forEach(all::add);
            all.sort(java.util.Comparator.comparingInt(Entity::getId));   // the order they came in: the same each run
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i) instanceof Mob mob) {
                    mob.getRandom().setSeed(seed * 31 + i);
                }
            }
            LOG.info("Film: dice set from {} ({} animals)", seed, all.size());
        });
    }

    /**
     * Every animal drawn with GeckoLib but not an insect (the insects come through {@link #noteRendered}): its model's
     * clock kept for "freshclocks", and its parts checked for each picture (the Motion Studio, phase 0).
     */
    @SubscribeEvent
    public static void onAnimalDrawn(GeoRenderEvent.Entity.Post event) {
        if (!active() || event.getEntity() instanceof AbstractAntEntity) {
            return;
        }
        models.add(event.getRenderer().getGeoModel());
        if (pendingShot != null) {
            try {
                checks.record(event.getEntity(), event.getModel(), event.getPartialTick(), pendingShot,
                        BoneMeshRenderer.heldFrame(), BoneMeshRenderer.heldAt());
            } catch (RuntimeException e) {
                LOG.warn("Film: cannot check {}", event.getEntity(), e);
            }
        }
    }

    /** Where the "ant" step puts its insect: the middle of a block on the flat world's grass, facing west. */
    private static final double AX = 0.5, AY = -60, AZ = 0.5;
    private static final double EYE = 1.62;

    /** The "ant" step, written as plain steps at the front of the list (so the game runs while it lands). */
    private static void putAnt(String type, String clip) {
        Deque<String> now = new ArrayDeque<>();
        now.add("cmd tick unfreeze");
        now.add("cmd kill @e[type=!player]");
        now.add("wait 25");                     // the death animation ends and the body (a corpse) is left
        now.add("cmd kill @e[type=!player]");   // the corpse too
        now.add(String.format("cmd summon %s %s %s %s {NoAI:1b,Rotation:[90f,0f],CustomName:'\"film:%s\"'}",
                insect(type), AX, AY, AZ, clip));
        now.add(camera(AX + 0.8, AY + 0.55, AZ + 0.8, 135, 22));   // seen while the game runs, so its clip starts
        now.add("wait 60");                     // (and the smoke of the kills has gone)
        now.add("cmd tick freeze");
        pushFront(now);
    }

    /**
     * An insect's full name from a shot list's short one ({@code matabele_minor} is {@code antcolonies:matabele_minor},
     * {@code termite_king} is {@code antcolonies:termite_king}): each species mod signs its insects up under its own
     * name since the split (step S7), so the camera looks the name up; a full name is used as it is.
     */
    private static String insect(String type) {
        if (type.contains(":")) {
            return type;
        }
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .filter(id -> id.getPath().equals(type)).map(Object::toString).sorted().findFirst().orElse(type);
    }

    /** The walker's name: not {@code film:...}, so it plays its usual clips (walking, climbing, turning). */
    private static final String WALKER = "film-walker";
    /**
     * The walker's id, the same every run: an insect's own pace for standing about and for its head and antennae comes
     * from its id ({@code AbstractAntEntity.ownPace}), and a new random one each run made those clips differ from run to
     * run. This one gives both paces 1.0, the clips as made.
     */
    private static final String WALKER_ID = "[I;1179208781,2147450879,0,1]";

    /**
     * The "walker" step: like "ant", but an insect with its AI (its goals then taken away) that the camera sends about.
     * It comes into a frozen game, which is stepped through its first ticks and left frozen: the game sends an insect's
     * place to the client every so many of its ticks, counted from when it came, so a walker that stood a few ticks
     * more or less in a running game (by timing luck) walked the same way but was drawn differently all scene.
     */
    private static void putWalker(String type, String x, String y, String z, String yaw, @Nullable String extra) {
        Deque<String> now = new ArrayDeque<>();
        now.add("cmd tick unfreeze");
        now.add("cmd kill @e[type=!player]");
        now.add("wait 25");
        now.add("cmd kill @e[type=!player]");
        now.add("cmd tick freeze");     // its first ticks stepped, so they are the same every run (see the javadoc)
        now.add("freshclocks");
        // seen from its first tick, so its clips start then (filmed after another scene the camera may already be
        // looking there, filmed alone it is not)
        now.add(camera(Double.parseDouble(x) + 0.8, Double.parseDouble(y) + 0.55, Double.parseDouble(z) + 0.8, 135, 22));
        // (an optional sixth word is more of the insect's saved data, e.g. BodyLength:15f for a Matabele, so an insect
        // that rolls its own size gets the same one every launch: BUGS 137)
        now.add(String.format("cmd summon %s %s %s %s {PersistenceRequired:1b,Rotation:[%sf,0f],CustomName:'\"%s\"',UUID:%s%s}",
                insect(type), x, y, z, yaw, WALKER, WALKER_ID, extra == null ? "" : "," + extra));
        now.add("step 2");
        now.add("forget");
        now.add("step 40");
        pushFront(now);
    }

    /** Every insect model drawn so far (each one GeckoLib model, shared by all insects drawn with it). */
    private static final java.util.Set<GeoModel<?>> models =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /**
     * The walker's step, before it comes: every insect model's animation clock and bones set back to how a fresh game
     * has them. GeckoLib keeps one clock per model, moved on by whichever insect was drawn last, and one set of bones,
     * left in the last insect's pose, so the earlier scenes' insects could change how the walker is drawn (the climb
     * filmed alone and after other scenes differed by a pixel).
     */
    private static void freshClocks() {
        try {
            Field animTime = GeoModel.class.getDeclaredField("animTime");
            Field lastTick = GeoModel.class.getDeclaredField("lastGameTickTime");
            Field lastDrawn = GeoModel.class.getDeclaredField("lastRenderedInstance");
            animTime.setAccessible(true);
            lastTick.setAccessible(true);
            lastDrawn.setAccessible(true);
            for (GeoModel<?> model : models) {
                animTime.setDouble(model, 0.0);
                lastTick.setDouble(model, 0.0);
                lastDrawn.setLong(model, -1L);
                for (software.bernie.geckolib.cache.object.GeoBone bone : model.getAnimationProcessor().getRegisteredBones()) {
                    software.bernie.geckolib.animation.state.BoneSnapshot first = bone.getInitialSnapshot();
                    bone.setRotX(first.getRotX());
                    bone.setRotY(first.getRotY());
                    bone.setRotZ(first.getRotZ());
                    bone.setPosX(first.getOffsetX());
                    bone.setPosY(first.getOffsetY());
                    bone.setPosZ(first.getOffsetZ());
                    bone.setScaleX(first.getScaleX());
                    bone.setScaleY(first.getScaleY());
                    bone.setScaleZ(first.getScaleZ());
                    bone.resetStateChanges();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("Film: cannot set the models' clocks back", e);
        }
    }

    /** The walker: an insect or any other animal (a mob) with the walker's name. */
    static boolean isWalker(Entity entity) {
        return entity instanceof Mob && entity.getCustomName() != null && WALKER.equals(entity.getCustomName().getString());
    }

    /**
     * An animal walker's "forget": every goal and target goal taken away (an insect has its own way), so it only goes
     * where it is sent, with its own legs (its navigation and move control). Its goal lists are read by name: the
     * camera runs only in development, where the game's names are Mojang's.
     */
    private static void forgetGoals(Mob mob) {
        try {
            for (String name : new String[] {"goalSelector", "targetSelector"}) {
                Field field = Mob.class.getDeclaredField(name);
                field.setAccessible(true);
                ((net.minecraft.world.entity.ai.goal.GoalSelector) field.get(mob)).removeAllGoals(goal -> true);
            }
        } catch (ReflectiveOperationException e) {
            LOG.warn("Film: cannot take the walker's goals away", e);
        }
        mob.getNavigation().stop();
    }

    /** Runs this on the game's own (server) side, for the walker there: its mind and legs live on that side. */
    private static void onWalker(Minecraft mc, java.util.function.Consumer<Mob> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            LOG.warn("Film: no game server for the walker");
            return;
        }
        FilmStep.serve(mc, () -> {
            boolean found = false;
            for (Entity entity : server.overworld().getAllEntities()) {
                if (isWalker(entity)) {
                    action.accept((Mob) entity);
                    found = true;
                }
            }
            if (!found) {
                LOG.warn("Film: no walker");
            }
        });
    }

    /** The walker as this side draws it, if it is an insect, or null. */
    private static AbstractAntEntity drawnWalker(Minecraft mc) {
        return drawnWalkerOfAnyKind(mc) instanceof AbstractAntEntity ant ? ant : null;
    }

    /** The walker as this side draws it (an insect or an animal), or null. */
    private static Entity drawnWalkerOfAnyKind(Minecraft mc) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (isWalker(entity)) {
                return entity;
            }
        }
        return null;
    }

    /** The point the walker keeps looking at ("look"), or null. */
    private static volatile Vec3 lookAt;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        Vec3 target = lookAt;
        if (!active() || target == null) {
            return;
        }
        for (Entity entity : event.getServer().overworld().getAllEntities()) {
            if (isWalker(entity)) {
                ((Mob) entity).getLookControl().setLookAt(target.x, target.y, target.z);
            }
        }
    }

    /** The "follow" camera: x y z off the walker, turn and tilt; null when the camera stays where it is put. */
    private static double[] follow;

    /** Puts the camera (the player, on this side, so at once) where "follow" says, for the next shot. */
    private static void followWalker(Minecraft mc) {
        Entity walker = follow == null ? null : drawnWalkerOfAnyKind(mc);
        if (walker == null) {
            return;
        }
        Vec3 at = walker.getPosition(drawPartial);
        mc.player.moveTo(at.x + follow[0], at.y + follow[1] - EYE, at.z + follow[2], (float) follow[3], (float) follow[4]);
        mc.player.setOldPosAndRot();
    }

    /** The "log" step: the file's name and its lines so far, and the walker's last logged tick. */
    private static String logName;
    private static final StringBuilder logLines = new StringBuilder();
    private static int loggedTick = -1;

    @SubscribeEvent
    public static void onLogTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || logName == null || mc.level == null) {
            return;
        }
        AbstractAntEntity walker = drawnWalker(mc);
        if (walker == null || walker.tickCount == loggedTick) {
            return;                                     // frozen: no new tick
        }
        loggedTick = walker.tickCount;
        logLines.append("tick ").append(walker.tickCount).append(' ').append(walker.filmReport()).append(" clip ")
                .append(clipTimes.getOrDefault(walker.getId(), "- -")).append(System.lineSeparator());
    }

    private static void writeLog(Minecraft mc) {
        if (logName == null) {
            return;
        }
        Path file = folder(mc).resolve(logName + ".txt");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, logLines.toString(), StandardCharsets.UTF_8);
            LOG.info("Film: log written to {}", file);
        } catch (IOException e) {
            LOG.warn("Film: cannot write {}", file, e);
        }
        logLines.setLength(0);
        logName = null;
    }

    /**
     * The "views" step: the standard shots round the insect at distance d (the side one a burst). The side and front
     * shots are taken level with the insect through a narrow lens from further off, so they look like the lab's flat
     * drawings (no perspective, the floor seen edge on); above and three-quarter use the scene's own lens.
     */
    private static void views(String name, int frames, double d) {
        double far = d * 3.3;
        double eye = AY + 0.1 * d;
        Deque<String> now = new ArrayDeque<>();
        now.add("fov 10");
        now.add(camera(AX, eye, AZ - far, 0, 0));        // head on the right, as in the lab
        now.add(frames > 1 ? String.format("burst %s_side %d 1", name, frames) : "shot " + name + "_side");
        now.add(camera(AX - far, eye, AZ, -90, 0));
        now.add("shot " + name + "_front");
        now.add("fov " + sceneFov);
        now.add(camera(AX, AY + 2 * d, AZ, 90, 90));       // head up, as in the lab
        now.add("shot " + name + "_above");
        now.add(camera(AX + 0.73 * d, AY + 0.5 * d, AZ + 0.73 * d, 135, 22));
        now.add("shot " + name + "_34");
        now.add("fov " + sceneFov);
        pushFront(now);
    }

    /** A camera step for an eye at x y z (the player's feet are 1.62 lower). */
    private static String camera(double x, double y, double z, int yaw, int pitch) {
        return String.format(java.util.Locale.ROOT, "camera %.2f %.2f %.2f %d %d", x, y - EYE, z, yaw, pitch);
    }

    private static void pushFront(Deque<String> now) {
        while (!now.isEmpty()) {
            steps.addFirst(now.pollLast());
        }
    }

    /**
     * Runs a command as the player. In this window's own game it goes straight to the game's thread with the walker's
     * orders and the steps ({@link FilmStep#serve}), so they are done in the order given and, while frozen, between
     * the same ticks every run (a command sent as a chat message could come before or after a walker's order).
     */
    private static void send(Minecraft mc, String command) {
        LOG.info("Film: /{}", command);
        java.util.UUID player = mc.player.getUUID();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || !FilmStep.serve(mc, () -> {
            net.minecraft.server.level.ServerPlayer source = server.getPlayerList().getPlayer(player);
            server.getCommands().performPrefixedCommand(
                    source != null ? source.createCommandSourceStack() : server.createCommandSourceStack(), command);
        })) {
            mc.player.connection.sendCommand(command);
        }
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        if (!active() || pendingShot == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String name = pendingShot + ".png";
        pendingShot = null;
        writeTime(mc, name);
        try {
            Files.createDirectories(folder(mc));
        } catch (IOException e) {
            LOG.warn("Film: cannot make {}", folder(mc), e);
        }
        String file = sceneName + "/" + name;
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), message -> LOG.info("Film: saved {}", file));
    }

    /**
     * The clip moment each filmed insect was last drawn at: its clip's name and the seconds into it (then, for checking
     * against the log, the tick it was drawn at and the waist bend it was drawn with).
     */
    private static final java.util.Map<Integer, String> clipTimes = new java.util.HashMap<>();

    /**
     * Called by {@code AntRenderer} after drawing an insect while filming (at that in-between time): works out where in its clip the insect was
     * drawn (GeckoLib keeps that inside, so it is read by reflection; development only), for {@code times.txt}.
     */
    public static void noteRendered(AbstractAntEntity ant, GeoModel<?> model, float partial) {
        models.add(model);
        if (pendingShot != null) {
            try {
                checks.record(ant, model, partial, pendingShot);
            } catch (RuntimeException e) {
                LOG.warn("Film: cannot check {}", ant, e);
            }
        }
        try {
            AnimationController<?> controller = ant.getAnimatableInstanceCache().getManagerForId(ant.getId())
                    .getAnimationControllers().get(AbstractAntEntity.BODY_PLAYER);
            if (controller == null || controller.getCurrentAnimation() == null
                    || controller.getAnimationState() != AnimationController.State.RUNNING) {
                clipTimes.put(ant.getId(), "- -");
                return;
            }
            Field animTime = GeoModel.class.getDeclaredField("animTime");
            animTime.setAccessible(true);
            Method adjust = AnimationController.class.getDeclaredMethod("adjustTick", double.class);
            adjust.setAccessible(true);
            double ticks = (double) adjust.invoke(controller, animTime.getDouble(model));
            var anim = controller.getCurrentAnimation().animation();
            double seconds = (ticks % anim.length()) / 20.0;
            // then the tick it was drawn at and the waist bend it was drawn with, to set beside the log
            StringBuilder time = new StringBuilder(String.format(java.util.Locale.ROOT, "%s %.4f drawn %d waist %.1f", anim.name(),
                    seconds, ant.tickCount, ant.getWaistPitch(partial)));
            // a longer spine (a termite's soft abdomen): every piece's bend
            for (int p = 1; p < ant.getSpinePieces(); p++) {
                time.append(String.format(java.util.Locale.ROOT, " %.1f", ant.getSpinePitch(p, partial)));
            }
            clipTimes.put(ant.getId(), time.toString());
        } catch (ReflectiveOperationException | RuntimeException e) {
            clipTimes.put(ant.getId(), "- -");
        }
    }

    /** One line in screenshots/times.txt: the picture, then each filmed insect's clip and moment in seconds. */
    private static void writeTime(Minecraft mc, String picture) {
        StringBuilder line = new StringBuilder(picture);
        clipTimes.keySet().removeIf(id -> mc.level.getEntity(id) == null || !mc.level.getEntity(id).isAlive());
        clipTimes.forEach((id, time) -> line.append(' ').append(time));
        Path file = folder(mc).resolve("times.txt");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warn("Film: cannot write {}", file, e);
        }
        LOG.info("Film: {}", line);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (active() && inWorld) {
            event.setFOV(lens);
        }
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (active()) {
            event.setCanRender(TriState.FALSE);
        }
    }

}
