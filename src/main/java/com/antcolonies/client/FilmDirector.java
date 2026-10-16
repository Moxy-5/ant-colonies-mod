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
 * {@code ./gradlew runFilm} (or {@code -PfilmScene=tools/film/<scene>.film}): it makes a fresh flat creative world,
 * plays a shot list and saves the screenshots to {@code run-film/screenshots/}, then quits. Claude reads the pictures
 * (or a contact sheet made by {@code python tools/anim_lab.py sheet run-film/screenshots}).
 *
 * <p>A shot list is a text file, one step a line ({@code #} starts a comment):
 * <pre>
 * cmd summon antcolonies:worker_ant 0.5 -60 0.5 {NoAI:1b,Rotation:[90f,0f],CustomName:'"film:walk"'}
 * camera 1.4 -59.7 0.5 90 15      the camera's x y z, turn and tilt (the player, a spectator, is put there)
 * lens 30                         the scene's lens (narrow for small insects); "fov" changes it for a moment only
 * wait 20                         client ticks
 * shot walk_side                  one screenshot, saved as walk_side.png
 * burst walk_side 10 1            10 screenshots, the game stepped 1 tick between them (after "cmd tick freeze")
 * ant worker_ant walk             clear the floor, then one insect at the middle playing that clip, facing west
 * views walk 10 1.1               side (a burst of 10 frames), front, above and 3/4 shots of it, 1.1 blocks away
 *                                 (side and front level and from further off, like the lab's drawings)
 * walker worker_ant 0.5 -60 0.5 -90   clear the floor, then one insect that walks where it is sent (its goals taken
 *                                 away), standing at x y z facing that way
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
    /** The shot list, or null when not filming. */
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

    private static void load() {
        try {
            for (String line : Files.readAllLines(Path.of(SCENE), StandardCharsets.UTF_8)) {
                String step = line.contains("#") && !line.trim().startsWith("cmd") ? line.substring(0, line.indexOf('#')) : line;
                if (!step.isBlank()) {
                    steps.add(step.trim());
                }
            }
            LOG.info("Film: {} steps from {}", steps.size(), SCENE);
        } catch (IOException e) {
            LOG.error("Film: cannot read the shot list {}", SCENE, e);
            steps.add("quit");
        }
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
        if (pendingShot != null) {
            return;                            // waiting for the frame to be drawn
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (burstLeft > 0) {
            followWalker(mc);
            pendingShot = String.format("%s_%02d", burstName, burstIndex++);
            burstLeft--;
            if (burstLeft > 0 && burstGap > 0) {
                send(mc, "tick step " + burstGap);
                waitTicks = 3 + burstGap;      // the stepped ticks reach the client
            }
            return;
        }
        while (!steps.isEmpty() && waitTicks == 0 && pendingShot == null && burstLeft == 0) {
            run(mc, steps.poll());
        }
        if (steps.isEmpty() && waitTicks == 0 && pendingShot == null && burstLeft == 0) {
            LOG.info("Film: done");
            mc.stop();
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
            }
            case "ant" -> putAnt(w[1], w[2]);
            case "views" -> views(w[1], w.length > 2 ? Integer.parseInt(w[2]) : 1,
                    w.length > 3 ? Double.parseDouble(w[3]) : 1.1);
            case "walker" -> putWalker(w[1], w[2], w[3], w[4], w[5]);
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
                writeLog(mc);
                steps.clear();
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

    /** The "walker" step: like "ant", but an insect with its AI (its goals then taken away) that the camera sends about. */
    private static void putWalker(String type, String x, String y, String z, String yaw) {
        Deque<String> now = new ArrayDeque<>();
        now.add("cmd tick unfreeze");
        now.add("cmd kill @e[type=!player]");
        now.add("wait 25");
        now.add("cmd kill @e[type=!player]");
        now.add(String.format("cmd summon antcolonies:%s %s %s %s {PersistenceRequired:1b,Rotation:[%sf,0f],CustomName:'\"%s\"'}",
                type, x, y, z, yaw, WALKER));
        now.add("wait 2");
        now.add("forget");
        now.add("wait 40");
        pushFront(now);
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
        server.execute(() -> {
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
        mc.player.moveTo(walker.getX() + follow[0], walker.getY() + follow[1] - EYE, walker.getZ() + follow[2],
                (float) follow[3], (float) follow[4]);
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
        Path file = mc.gameDirectory.toPath().resolve("screenshots").resolve(logName + ".txt");
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

    private static void send(Minecraft mc, String command) {
        LOG.info("Film: /{}", command);
        mc.player.connection.sendCommand(command);
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
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> LOG.info("Film: saved {}", name));
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
                    ant.tickCount, ant.getWaistPitch(0.0F)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            clipTimes.put(ant.getId(), "- -");
        }
    }

    /** One line in screenshots/times.txt: the picture, then each filmed insect's clip and moment in seconds. */
    private static void writeTime(Minecraft mc, String picture) {
        StringBuilder line = new StringBuilder(picture);
        clipTimes.keySet().removeIf(id -> mc.level.getEntity(id) == null || !mc.level.getEntity(id).isAlive());
        clipTimes.forEach((id, time) -> line.append(' ').append(time));
        Path file = mc.gameDirectory.toPath().resolve("screenshots").resolve("times.txt");
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
