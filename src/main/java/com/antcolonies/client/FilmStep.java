package com.antcolonies.client;

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
 * A burst's steps of the frozen game, as fast as the pictures can be taken and the same every run (development only,
 * 2026-10-18). A burst steps the game a tick between its pictures; the camera used to send {@code tick step} and wait
 * a fixed 3 client ticks more for it to come through: about two thirds of a scene's time, and the pictures still
 * depended on timing in two ways. Now:
 * <ul>
 * <li>the game's side, at the end of each stepped tick, sends a hidden marker message; messages arrive in the order
 * they were sent, so once the marker is here, so is everything the game sent before it;</li>
 * <li>this side runs each of its own stepped ticks (a frozen client moves its insects, particles and textures only on
 * those) only once that tick's marker is here. With the fixed wait the client ran its tick before the game's news
 * arrived on some ticks and after on others, by timing luck;</li>
 * <li>within a burst the game holds still at the end of each stepped tick until the camera has its picture, and then
 * goes straight on to the next step. A frozen game still runs empty ticks, and on each it may send the insects' state
 * to the client (each insect counts its own sends); a real game has no empty ticks, and how many came between two
 * pictures depended on timing (about 5 with the fixed wait);</li>
 * <li>the picture is taken on the first still client tick after the step (on a stepped tick the game is drawn blended
 * by real time, different every run).</li>
 * </ul>
 * If the step is not seen arriving in time the camera takes its picture anyway and logs it; the game never holds
 * longer than {@link #HOLD_MS}.
 */
@EventBusSubscriber(modid = AntColoniesMod.MODID, value = Dist.CLIENT)
final class FilmStep {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MARK = "antcolonies:film-step ";
    /** The longest the game holds still for the camera (it lets go by itself after this). */
    private static final long HOLD_MS = 3000;

    /** A step asked for: its number, its length, and whether another step of the burst follows its picture. */
    private record Order(int step, int length, boolean more) {
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
     * Between the two sides, guarded by {@link #LOCK}: whether the game holds still (or is about to, its marker sent)
     * for the camera's next order, and that order once given.
     */
    private static final Object LOCK = new Object();
    private static boolean holding;
    private static Order next;
    /** The game's side: the order being run and how many of its ticks are still to run. */
    private static Order running;
    private static int ticksLeft;

    private FilmStep() {
    }

    /**
     * Steps the frozen game {@code ticks} ticks for a burst's next picture; {@code more} says whether another step
     * follows that picture. False when it cannot (not a frozen game in this window): the camera sends {@code tick step}.
     */
    static boolean step(Minecraft mc, int ticks, boolean more) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.level == null || !mc.level.tickRateManager().isFrozen() || ticks <= 0) {
            cancel();
            return false;
        }
        Order order = new Order(++step, ticks, more);
        length = ticks;
        ran = 0;
        held = 0;
        stepping = false;
        waiting = true;
        synchronized (LOCK) {
            if (holding) {
                next = order;
                LOCK.notifyAll();
                return true;
            }
        }
        server.execute(() -> start(server, order));
        return true;
    }

    /** The burst was dropped (the watch mode starts again): a game holding still goes on as normal. */
    static void cancel() {
        waiting = false;
        synchronized (LOCK) {
            if (holding) {
                next = new Order(0, 0, false);
                LOCK.notifyAll();
            }
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

    /** The game's side: an order's ticks start (the next server tick is its first). */
    private static void start(MinecraftServer server, Order order) {
        running = order;
        ticksLeft = order.length();
        if (order.length() > 0) {
            server.tickRateManager().stepGameIfPaused(order.length());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (running == null || ticksLeft <= 0 || !FilmDirector.active() || !server.tickRateManager().runsNormally()) {
            return;
        }
        ticksLeft--;
        if (ticksLeft == 0 && running.more()) {
            synchronized (LOCK) {
                holding = true;                     // before the marker: the camera's answer may come at once
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(MARK + running.step() + " " + (running.length() - ticksLeft)), true);
        }
        if (ticksLeft == 0 && running.more()) {
            Order order = holdForNext();
            if (order != null) {
                start(server, order);
            } else {
                running = null;
                LOG.warn("Film: the game held still {} ms for the camera's next step and went on without it", HOLD_MS);
            }
        }
    }

    /** The game's side: holds still until the camera's next order comes (null if it does not come in time). */
    private static Order holdForNext() {
        long until = System.currentTimeMillis() + HOLD_MS;
        synchronized (LOCK) {
            while (next == null) {
                long left = until - System.currentTimeMillis();
                if (left <= 0) {
                    break;
                }
                try {
                    LOCK.wait(left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            Order order = next;
            next = null;
            holding = false;
            return order;
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
