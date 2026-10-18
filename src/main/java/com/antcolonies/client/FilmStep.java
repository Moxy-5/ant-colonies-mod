package com.antcolonies.client;

import java.util.ArrayDeque;
import java.util.Deque;

import com.antcolonies.AntColoniesMod;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.TickRateManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The frozen game stepped for the camera, as fast as the pictures can be taken and the same every run (development
 * only, 2026-10-18). A burst steps the game a tick between its pictures; the camera used to send {@code tick step} and
 * wait a fixed 3 client ticks more for it to come through: about two thirds of a scene's time, and the pictures still
 * depended on timing in three ways. Now:
 * <ul>
 * <li>the game's side, at the end of each stepped tick, sends a hidden marker message; messages arrive in the order
 * they were sent, so once the marker is here, so is everything the game sent before it;</li>
 * <li>this side runs each of its own stepped ticks (a frozen client moves its insects, particles and textures only on
 * those) only once that tick's marker is here. With the fixed wait the client ran its tick before the game's news
 * arrived on some ticks and after on others, by timing luck;</li>
 * <li>while it is frozen the game never runs an empty tick: its thread holds still and does only what the camera asks,
 * in the order asked (the camera's commands and the walker's orders, {@link #serve}, and the steps). A frozen game
 * would otherwise run empty ticks, and on each it may send the insects' state to the client (each insect counts its own
 * sends, and the count decides when it sends); a real game has no empty ticks, and how many came between two pictures,
 * between two bursts or between the freeze and the first step depended on timing (one launch of the climbing scene
 * parted from another just after its first burst, at the walk to the far wall). The hold starts as soon as the freeze
 * is done and ends when the camera unfreezes the game;</li>
 * <li>the picture is taken on the first still client tick after the step (on a stepped tick the game is drawn blended
 * by real time, different every run).</li>
 * </ul>
 * If the step is not seen arriving in time the camera takes its picture anyway and logs it; the frozen game lets go by
 * itself when the camera has asked nothing of it for {@link #HOLD_MS} (and holds again with the camera's next order).
 */
@EventBusSubscriber(modid = AntColoniesMod.MODID, value = Dist.CLIENT)
final class FilmStep {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MARK = "antcolonies:film-step ";
    /** The longest the frozen game holds still with nothing asked of it (it lets go by itself after this). */
    private static final long HOLD_MS = 3000;

    /** A step asked for: its number and its length. */
    private record Order(int step, int length) {
    }

    /** This side: the step waited for (its number and length), whether one is, and its ticks run here so far. */
    private static int step, length;
    private static boolean waiting;
    private static int ran;
    /** Whether this client tick was one of the step's. */
    private static boolean stepping;
    /** The step's ticks the client was told to run but is holding back until their markers are here. */
    private static int held;
    /** The last marker seen: its step and which of that step's ticks it ends. */
    private static int markStepSeen, markTickSeen;
    private static int missed;

    /**
     * Between the two sides, guarded by {@link #LOCK}: what the camera asked of the game and the game has not done yet,
     * in order (a {@link Runnable} or an {@link Order}); whether the game's thread reads that list before it runs a tick
     * of its own (it is holding still, or running a step whose last tick holds again); and whether a task to read it is
     * already on its way to the game's thread.
     */
    private static final Object LOCK = new Object();
    private static final Deque<Object> asked = new ArrayDeque<>();
    private static boolean holding;
    private static boolean readPosted;
    /** The camera is closing the game: the game never holds again (it must see the player leave). */
    private static boolean closing;
    /** The game's side: the step being run and how many of its ticks are still to run. */
    private static Order running;
    private static int ticksLeft;

    private FilmStep() {
    }

    /**
     * Runs this on the game's side, after everything the camera asked before it and, while the game is frozen, before
     * the game runs another tick. False when there is no game in this window (the caller does it another way).
     */
    static boolean serve(Minecraft mc, Runnable job) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return false;
        }
        ask(server, job);
        return true;
    }

    /**
     * Steps the frozen game {@code ticks} ticks for a burst's next picture. False when it cannot (not a frozen game in
     * this window): the camera sends {@code tick step}.
     */
    static boolean step(Minecraft mc, int ticks) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.level == null || !mc.level.tickRateManager().isFrozen() || ticks <= 0) {
            cancel();
            return false;
        }
        Order order = new Order(++step, ticks);
        length = ticks;
        ran = 0;
        held = 0;
        stepping = false;
        waiting = true;
        ask(server, order);
        return true;
    }

    /** The burst was dropped (the watch mode starts again): steps asked for and not started yet are forgotten. */
    static void cancel() {
        waiting = false;
        synchronized (LOCK) {
            asked.removeIf(job -> job instanceof Order);
        }
    }

    /** The camera is done and closes the game: a game holding still goes on, and holds no more. */
    static void letGo() {
        synchronized (LOCK) {
            closing = true;
            LOCK.notifyAll();
        }
    }

    private static void ask(MinecraftServer server, Object job) {
        synchronized (LOCK) {
            asked.add(job);
            if (holding) {
                LOCK.notifyAll();               // the game's thread reads it before its next tick
                return;
            }
            if (readPosted) {
                return;
            }
            readPosted = true;
        }
        server.execute(() -> {
            synchronized (LOCK) {
                readPosted = false;
                holding = true;
            }
            hold(server);
        });
    }

    /**
     * The game's side: does what the camera asked, in order, and while the game is frozen holds still for more. Ends
     * when a step is started (its last tick holds again), when the game is not frozen and nothing more is asked, or
     * when nothing is asked for {@link #HOLD_MS}.
     */
    private static void hold(MinecraftServer server) {
        long until = System.currentTimeMillis() + HOLD_MS;
        while (true) {
            Object job;
            synchronized (LOCK) {
                while (asked.isEmpty() && !closing && server.isRunning() && FilmDirector.active()
                        && server.tickRateManager().isFrozen()) {
                    long left = until - System.currentTimeMillis();
                    if (left <= 0) {
                        LOG.info("Film: the frozen game held still {} ms with nothing asked of it and goes on", HOLD_MS);
                        break;
                    }
                    try {
                        LOCK.wait(Math.min(left, 100));     // short waits: the game may be closing
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                job = asked.poll();
                if (job == null) {
                    holding = false;
                    return;
                }
            }
            if (job instanceof Order order) {
                if (server.tickRateManager().stepGameIfPaused(order.length())) {
                    running = order;
                    ticksLeft = order.length();
                    return;                             // still holding: the step's last tick reads the list again
                }
            } else {
                ((Runnable) job).run();
            }
            until = System.currentTimeMillis() + HOLD_MS;
        }
    }

    /**
     * True (once) when the step asked for has fully reached this side and the game stands still again (not on the tick
     * that ran the step's last tick).
     */
    static boolean arrived() {
        if (waiting && !stepping && ran >= length && markStepSeen == step && markTickSeen >= length) {
            waiting = false;
            return true;
        }
        return false;
    }

    /** The step's ticks this side has run so far while waiting for it (0 when not waiting). */
    static int ranSoFar() {
        return waiting ? ran : 0;
    }

    /** The camera's longest wait ran out: the held ticks are let go and the step counts as arrived anyway. */
    static void gaveUp() {
        if (waiting) {
            waiting = false;
            missed++;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && held > 0) {
                TickRateManager ticks = mc.level.tickRateManager();
                ticks.setFrozenTicksToRun(ticks.frozenTicksToRun() + held);
            }
            held = 0;
            LOG.warn("Film: step {} not seen arriving (marker {} tick {}, ran {} of {}); took the picture anyway ({} so far)",
                    step, markStepSeen, markTickSeen, ran, length, missed);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (running == null || ticksLeft <= 0 || !FilmDirector.active() || !server.tickRateManager().runsNormally()) {
            return;
        }
        ticksLeft--;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(MARK + running.step() + " " + (running.length() - ticksLeft)), true);
        }
        if (ticksLeft == 0) {
            running = null;
            hold(server);                               // the camera's next order may come at once
        }
    }

    /**
     * Before the client decides whether this tick is a stepped one: it may run one only when the game's marker for that
     * tick is here (so the game's news is too); otherwise the step's ticks are held back.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!waiting || mc.level == null) {
            return;
        }
        TickRateManager ticks = mc.level.tickRateManager();
        int pending = ticks.frozenTicksToRun() + held;
        if (pending <= 0) {
            return;
        }
        boolean marked = markStepSeen == step && markTickSeen > ran;
        held = marked ? pending - 1 : pending;
        ticks.setFrozenTicksToRun(marked ? 1 : 0);
    }

    /** Counts this side's stepped ticks, before the camera's own tick looks at them. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        stepping = waiting && mc.level != null && mc.level.tickRateManager().runsNormally();
        if (stepping) {
            ran++;
        }
    }

    @SubscribeEvent
    public static void onMessage(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (FilmDirector.active() && text.startsWith(MARK)) {
            String[] parts = text.substring(MARK.length()).trim().split(" ");
            markStepSeen = Integer.parseInt(parts[0]);
            markTickSeen = Integer.parseInt(parts[1]);
            event.setCanceled(true);
        }
    }
}
