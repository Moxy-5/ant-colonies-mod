package com.antcolonies.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.antcolonies.AntColoniesMod;
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
 * cmd summon antcolonies:worker_ant 0.5 -60 0.5 {NoAI:1b,Rotation:[90f,0f],CustomName:'"film:walk"'}
 * camera 1.4 -59.7 0.5 90 15      the camera's x y z, turn and tilt (the player, a spectator, is put there)
 * lens 30                         the scene's lens (narrow for small insects); "fov" changes it for a moment only
 * wait 20                         client ticks
 * shot walk_side                  one screenshot, saved as walk_side.png
 * burst walk_side 10 1            10 screenshots, the game stepped 1 tick between them (after "cmd tick freeze")
 * frames 4                        from now, every burst tick is drawn 4 times, at 0, 1/4, 2/4 and 3/4 of the way to
 *                                 the next tick (as a fast screen shows it between ticks); "frames 1" goes back
 * ant worker_ant walk             clear the floor, then one insect at the middle playing that clip, facing west
 * views walk 10 1.1               side (a burst of 10 frames), front, above and 3/4 shots of it, 1.1 blocks away
 *                                 (side and front level and from further off, like the lab's drawings)
 * walker worker_ant 0.5 -60 0.5 -90   clear the floor, then one insect that walks where it is sent (its goals taken
 *                                 away), standing at x y z facing that way; the game is left frozen
 * step 5                          the frozen game stepped 5 ticks, no picture
 * go 3 -58 0 1.0                  the walker walks its own route (the real legs: AntNavigator) to that cell
 * look 0 -60 0.5                  the walker keeps looking at that point (head and body turn as in the game); "look off"
 * follow 0 0.1 -7 0 0             the camera rides along with the walker, this far off it, with this turn and tilt,
 *                                 for every shot from now ("follow off" stops it)
 * log climb                       one line a tick about the walker's drawn body into climb.txt ("log off" ends it)
 * quit
 * </pre>
 * An insect named {@code film:<clip>} plays that clip and nothing else ({@code AbstractAntEntity.filmClip}); name tags
 * are hidden while filming. The walker is named {@code film-walker}, so it plays its usual clips. Never active in a real game (only with the {@code antcolonies.film} system property, and
 * never in production).
 */
@EventBusSubscriber(modid = AntColoniesMod.MODID, value = Dist.CLIENT)
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
                for (String line : lines) {
                    String step = line.contains("#") && !line.trim().startsWith("cmd") ? line.substring(0, line.indexOf('#')) : line;
                    if (!step.isBlank()) {
                        steps.add(step.trim());
                    }
                }
            } catch (IOException e) {
                LOG.error("Film: cannot read the shot list {}", scene, e);
            }
        }
        LOG.info("Film: {} steps from {} scene(s): {}", steps.size(), scenes.size(), scenes);
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

    /** The "scene" step: the last scene's log and checks written, then everything set back for the next one. */
    private static void startScene(String name) {
        endScene();
        sceneName = name;
        checks = new FilmChecks();
        lens = sceneFov = 30;
        follow = null;
        lookAt = null;
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
        if (checks.isEmpty()) {
            return;
        }
        java.util.List<String> lines = checks.report(framesPerTick);
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
            send(mc, "gamemode spectator");
            send(mc, "time set noon");
            waitTicks = 60;                     // let the land load and settle
            return;
        }
        FilmWatch.tick(mc, !(filmed && steps.isEmpty() && waitTicks == 0 && pendingShot == null && burstLeft == 0));
        if (pendingShot != null) {
            return;                            // waiting for the frame to be drawn
        }
        if (waitTicks > 0) {
            if (!FilmStep.arrived()) {         // a burst's step seen arriving ends its wait at once
                if (--waitTicks == 0) {
                    FilmStep.gaveUp();
                }
                return;
            }
            waitTicks = 0;
        }
        if (burstLeft > 0) {
            drawPartial = (float) burstSub / framesPerTick;
            followWalker(mc);
            pendingShot = String.format(framesPerTick > 1 ? "%s_%03d" : "%s_%02d", burstName, burstIndex++);
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
        while (!steps.isEmpty() && waitTicks == 0 && pendingShot == null && burstLeft == 0) {
            run(mc, steps.poll());
        }
        boolean done = steps.isEmpty() && waitTicks == 0 && pendingShot == null && burstLeft == 0;
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
            case "lens" -> lens = sceneFov = Integer.parseInt(w[1]);
            case "shot" -> {
                followWalker(mc);
                pendingShot = w[1];
            }
            case "burst" -> {
                burstName = w[1];
                burstLeft = Integer.parseInt(w[2]);
                burstGap = w.length > 3 ? Integer.parseInt(w[3]) : 1;
                burstIndex = 0;
                burstSub = 0;
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
            case "walker" -> putWalker(w[1], w[2], w[3], w[4], w[5]);
            case "freshclocks" -> freshClocks();
            case "forget" -> onWalker(mc, ant -> {
                ant.forgetGoalsForFilm();
                ant.getNavigator().stop();
            });
            case "go" -> {
                BlockPos goal = new BlockPos(Integer.parseInt(w[1]), Integer.parseInt(w[2]), Integer.parseInt(w[3]));
                double speed = w.length > 4 ? Double.parseDouble(w[4]) : 1.0;
                onWalker(mc, ant -> LOG.info("Film: walker to {}: {}", goal,
                        ant.getNavigator().moveTo(goal, speed) ? "route found" : "NO ROUTE"));
            }
            case "look" -> lookAt = w[1].equals("off") ? null
                    : new Vec3(Double.parseDouble(w[1]), Double.parseDouble(w[2]), Double.parseDouble(w[3]));
            case "follow" -> follow = w[1].equals("off") ? null : new double[] {Double.parseDouble(w[1]),
                    Double.parseDouble(w[2]), Double.parseDouble(w[3]), Double.parseDouble(w[4]), Double.parseDouble(w[5])};
            case "log" -> {
                writeLog(mc);
                logName = w[1].equals("off") ? null : w[1];
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
        now.add(String.format("cmd summon antcolonies:%s %s %s %s {NoAI:1b,Rotation:[90f,0f],CustomName:'\"film:%s\"'}",
                type, AX, AY, AZ, clip));
        now.add(camera(AX + 0.8, AY + 0.55, AZ + 0.8, 135, 22));   // seen while the game runs, so its clip starts
        now.add("wait 60");                     // (and the smoke of the kills has gone)
        now.add("cmd tick freeze");
        pushFront(now);
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
    private static void putWalker(String type, String x, String y, String z, String yaw) {
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
        now.add(String.format("cmd summon antcolonies:%s %s %s %s {PersistenceRequired:1b,Rotation:[%sf,0f],CustomName:'\"%s\"',UUID:%s}",
                type, x, y, z, yaw, WALKER, WALKER_ID));
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

    private static boolean isWalker(Entity entity) {
        return entity instanceof AbstractAntEntity && entity.getCustomName() != null
                && WALKER.equals(entity.getCustomName().getString());
    }

    /** Runs this on the game's own (server) side, for the walker there: its mind and legs live on that side. */
    private static void onWalker(Minecraft mc, java.util.function.Consumer<AbstractAntEntity> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            LOG.warn("Film: no game server for the walker");
            return;
        }
        FilmStep.serve(mc, () -> {
            boolean found = false;
            for (Entity entity : server.overworld().getAllEntities()) {
                if (isWalker(entity)) {
                    action.accept((AbstractAntEntity) entity);
                    found = true;
                }
            }
            if (!found) {
                LOG.warn("Film: no walker");
            }
        });
    }

    /** The walker as this side draws it, or null. */
    private static AbstractAntEntity drawnWalker(Minecraft mc) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (isWalker(entity)) {
                return (AbstractAntEntity) entity;
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
                ((AbstractAntEntity) entity).getLookControl().setLookAt(target.x, target.y, target.z);
            }
        }
    }

    /** The "follow" camera: x y z off the walker, turn and tilt; null when the camera stays where it is put. */
    private static double[] follow;

    /** Puts the camera (the player, on this side, so at once) where "follow" says, for the next shot. */
    private static void followWalker(Minecraft mc) {
        AbstractAntEntity walker = follow == null ? null : drawnWalker(mc);
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
     * Called by {@code AntRenderer} after drawing an insect while filming: works out where in its clip the insect was
     * drawn (GeckoLib keeps that inside, so it is read by reflection; development only), for {@code times.txt}.
     */
    public static void noteRendered(AbstractAntEntity ant, GeoModel<?> model) {
        models.add(model);
        if (pendingShot != null) {
            try {
                checks.record(ant, model, drawPartial, pendingShot);
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
            clipTimes.put(ant.getId(), String.format(java.util.Locale.ROOT, "%s %.4f drawn %d waist %.1f", anim.name(), seconds,
                    ant.tickCount, ant.getWaistPitch(drawPartial)));
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
