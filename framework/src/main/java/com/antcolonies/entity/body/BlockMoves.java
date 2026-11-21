package com.antcolonies.entity.body;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

/**
 * Moves of their own for Minecraft's fixed shapes (the Animal Maker Engine step B, 2026-11-20): any animal's moves made
 * once with their clips (a step up, a step down, into the water off a level bank or a high one, a dash in, out onto a
 * level bank or one a block up), played the same way. Every step is one block and every bank edge alike, so a move
 * made once fits them all; the game moves the animal along the move's own path (its box here, its drawing on each
 * player's screen from the same start), so the clip and its place never drift apart (the method the Nile monitor
 * proved, 2026-11-19: "it look perfect now").
 * <p>
 * Species-neutral: it never names an animal. Each animal hands it its own moves as a move file written by its maker
 * ({@code assets/<mod>/block_moves/<animal>.json}, by {@code tools/model_parts.py}'s {@code write_block_moves}), read
 * straight from the animal's own mod jar on both sides (the server moves the box, the client draws the model; not a
 * data pack, which a client in multiplayer would not have). Which move to start where, and whether there is room, stays
 * the animal's own (its body decides what it can climb); this plays a move once started, ends it cleanly from any
 * tick, and gives the rules every animal shares: square to the edge first, no rest between the moves of a hill.
 * <p>
 * A move file: {@code sizes} (the animal sizes a clip is made for, any number: the game plays the nearest; young of a
 * very different size get their own file or none, never an adult's scaled), and its {@code moves}, each with its clips
 * (one a size), how far ahead of its start the step's face or the edge is ({@code ahead_px}, model pixels), whether it
 * starts or ends afloat, whether it goes down (its box stays on the top it leaves until its back has passed the edge),
 * the ground it ends on ({@code end_level}, blocks from its start), the tick it meets or leaves the water
 * ({@code wet_tick}, below 0 for none), its length ({@code ticks}), and its path tick by tick for each size: {@code rise} (a share of a block) and {@code along}
 * (model pixels forward).
 */
public final class BlockMoves {
    /** Under this many degrees off square to the edge a move plays as it comes; more, it turns square on the spot first (my pick). */
    public static final double SQUARE = 30.0;
    /** More than this many degrees off it is going somewhere else: no move (my pick). */
    public static final double MOST_TURN = 135.0;
    /** How fast it turns square on the spot (degrees a tick; my pick: a quarter turn in 0.75 s). */
    public static final double TURN_RATE = 6.0;

    private static final Map<String, BlockMoves> LOADED = new ConcurrentHashMap<>();

    private final Class<?> owner;
    private final String path;
    private volatile Data data;

    private BlockMoves(Class<?> owner, String path) {
        this.owner = owner;
        this.path = path;
        this.data = read(owner, path);
    }

    /**
     * An animal's moves, read once from its mod's own files ({@code assets/<modId>/block_moves/<animal>.json}).
     *
     * @param owner a class of the animal's own mod (its jar is where the file is read from)
     */
    public static BlockMoves of(Class<?> owner, String modId, String animal) {
        String path = "/assets/" + modId + "/block_moves/" + animal + ".json";
        return LOADED.computeIfAbsent(path, p -> new BlockMoves(owner, p));
    }

    /** Reads every move file again (the film camera's watch mode, after a maker rewrote one: no relaunch). */
    public static void reloadAll() {
        for (BlockMoves moves : LOADED.values()) {
            moves.data = read(moves.owner, moves.path);
        }
    }

    // --- the file ------------------------------------------------------------------------------------------------------
    private record Data(double[] sizes, Move[] moves, Map<String, Integer> index, int lead, int rest, int turnEase) {
    }

    /** One move: its clips and path for every size (see the class comment for the fields). */
    public static final class Move {
        public final String name;
        private final String[] clips;
        public final double aheadPx;
        public final boolean startsAfloat, endsAfloat, goesDown;
        /**
         * A land move that may start or end in shallow water, walked on the bed (the hippo's, 2026-11-21: down a bank
         * into the shallows, out of them onto a bank): never cut for being in water. Optional in the file.
         */
        public final boolean wades;
        public final int endLevel, wetTick, ticks;
        private final float[][] rise, along;

        private Move(JsonObject o) {
            name = o.get("name").getAsString();
            clips = strings(o.getAsJsonArray("clips"));
            aheadPx = o.get("ahead_px").getAsDouble();
            startsAfloat = o.get("starts_afloat").getAsBoolean();
            endsAfloat = o.get("ends_afloat").getAsBoolean();
            goesDown = o.get("goes_down").getAsBoolean();
            wades = o.has("wades") && o.get("wades").getAsBoolean();
            endLevel = o.has("end_level") ? o.get("end_level").getAsInt() : 0;
            wetTick = o.get("wet_tick").getAsInt();
            ticks = o.get("ticks").getAsInt();
            rise = rows(o.getAsJsonArray("rise"));
            along = rows(o.getAsJsonArray("along"));
        }

        /** Its clip's name for this size (an index into the file's sizes). */
        public String clip(int size) {
            return clips[size];
        }

        /** How far up at this many ticks into the clip (a share of a block, below 0 going down; held before and after). */
        public double rise(int size, double t) {
            return at(rise[size], t);
        }

        /** How far forward at this many ticks into the clip (model pixels; held before, and after it goes on at its last speed). */
        public double along(int size, double t) {
            float[] row = along[size];
            int last = row.length - 1;
            if (t > last) {
                return row[last] + (row[last] - row[last - 1]) * (t - last);
            }
            return at(row, t);
        }

        /** How fast it goes at the end of the move (model pixels a tick). */
        public double endSpeed(int size) {
            float[] row = along[size];
            return row[row.length - 1] - row[row.length - 2];
        }

        /** Whether it meets or leaves the water at a tick of its own. */
        public boolean wet() {
            return wetTick >= 0;
        }
    }

    private static Data read(Class<?> owner, String path) {
        try (InputStream in = owner.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no move file " + path + " in " + owner.getName() + "'s mod");
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray sizes = root.getAsJsonArray("sizes");
            double[] s = new double[sizes.size()];
            for (int i = 0; i < s.length; i++) {
                s[i] = sizes.get(i).getAsDouble();
            }
            JsonArray list = root.getAsJsonArray("moves");
            Move[] moves = new Move[list.size()];
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < moves.length; i++) {
                moves[i] = new Move(list.get(i).getAsJsonObject());
                index.put(moves[i].name, i);
                if (moves[i].clips.length != s.length || moves[i].rise.length != s.length || moves[i].along.length != s.length) {
                    throw new IllegalStateException(path + ": move " + moves[i].name + " has not one clip and path for each size");
                }
            }
            // the timings: easing into the clip (the animal's clip player's own easing), standing after a move ending on
            // land (so every player's screen has its box at its new height before it walks on), turning onto the
            // move's heading (ticks; my picks, the Nile monitor's 0.2, 0.4 and 0.4 s)
            return new Data(s, moves, index, intOr(root, "lead_ticks", 4), intOr(root, "rest_ticks", 8), intOr(root, "turn_ticks", 8));
        } catch (Exception e) {
            throw new IllegalStateException("cannot read the move file " + path, e);
        }
    }

    private static int intOr(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }

    private static String[] strings(JsonArray a) {
        String[] out = new String[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsString();
        }
        return out;
    }

    private static float[][] rows(JsonArray a) {
        float[][] out = new float[a.size()][];
        for (int i = 0; i < out.length; i++) {
            JsonArray row = a.get(i).getAsJsonArray();
            out[i] = new float[row.size()];
            int j = 0;
            for (JsonElement v : row) {
                out[i][j++] = v.getAsFloat();
            }
        }
        return out;
    }

    private static double at(float[] row, double t) {
        int last = row.length - 1;
        if (t <= 0) {
            return row[0];
        }
        if (t >= last) {
            return row[last];
        }
        int i = (int) t;
        double f = t - i;
        return row[i] + (row[i + 1] - row[i]) * f;
    }

    // --- asking --------------------------------------------------------------------------------------------------------
    public int count() {
        return data.moves.length;
    }

    public Move move(int i) {
        return data.moves[i];
    }

    /** A move's number by its name, or -1. */
    public int index(String name) {
        Integer i = data.index.get(name);
        return i == null ? -1 : i;
    }

    public double[] sizes() {
        return data.sizes;
    }

    /** The clip made for the size nearest this one (an index into {@link #sizes}). */
    public int nearest(double size) {
        double[] sizes = data.sizes;
        int best = 0;
        for (int i = 1; i < sizes.length; i++) {
            if (Math.abs(sizes[i] - size) < Math.abs(sizes[best] - size)) {
                best = i;
            }
        }
        return best;
    }

    /** Ticks the clip eases in over (the animal's clip player's own easing). */
    public int lead() {
        return data.lead;
    }

    /** Ticks it stands still after a move ending on land, when its route does not go straight on into another. */
    public int rest() {
        return data.rest;
    }

    // --- one move played ------------------------------------------------------------------------------------------------
    /**
     * A move being played (synced to every player's screen as a tag): where it started (its box squared to the step's
     * face, the edge or the bank's face, {@link Move#aheadPx} short of it; its height the top it starts on, or afloat the
     * water's block's top), which way it goes (a yaw straight at the face or edge), the yaw it had when it started, the
     * game tick it started, the clip's size and the move, and how many ticks it first turns square on the spot
     * ({@code turn}, 0 if it came near enough square). It turns onto the heading (over the turn, or the file's turn time, whichever
     * is longer), eases into the clip ({@link #lead}), plays it, then, ending on land, may stand a moment
     * ({@link #rest}); ending afloat it glides straight on into the swim.
     */
    public record Play(double x, double y, double z, float yaw, float yaw0, long start, int size, int move, int turn) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("x", x);
            tag.putDouble("y", y);
            tag.putDouble("z", z);
            tag.putFloat("yaw", yaw);
            tag.putFloat("yaw0", yaw0);
            tag.putLong("start", start);
            tag.putInt("size", size);
            tag.putInt("move", move);
            tag.putInt("turn", turn);
            return tag;
        }

        @Nullable
        public static Play of(CompoundTag tag) {
            return tag.isEmpty() ? null : new Play(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getFloat("yaw"),
                    tag.getFloat("yaw0"), tag.getLong("start"), tag.getInt("size"), tag.getInt("move"), tag.getInt("turn"));
        }

        /** Which way it goes (a unit step along the ground). */
        public double dirX() {
            return -Mth.sin(yaw * Mth.DEG_TO_RAD);
        }

        public double dirZ() {
            return Mth.cos(yaw * Mth.DEG_TO_RAD);
        }

        /** Whether it is still turning square on the spot at this game time (its walk plays, the move's clip after). */
        public boolean turning(double gameTime) {
            return gameTime - start < turn;
        }
    }

    /** The clip's name of a move played. */
    public String clip(Play p) {
        return move(p.move()).clip(p.size());
    }

    /** How far into its clip a move is at this game time (ticks of the clip; below 0 while it turns or eases in). */
    public double ticks(Play p, double gameTime) {
        return gameTime - p.start() - p.turn() - data.lead;
    }

    /** Where it faces at this game time: turning from where it faced to the move's heading, then the heading. */
    public float facing(Play p, double gameTime) {
        double f = Mth.clamp((gameTime - p.start()) / Math.max(p.turn(), data.turnEase), 0.0, 1.0);
        return p.yaw0() + Mth.wrapDegrees(p.yaw() - p.yaw0()) * (float) (f * f * (3 - 2 * f));
    }

    /** Where it is drawn at this many ticks into the clip: on the move's path from where it started (blocks). */
    public Vec3 drawnAt(Play p, double clipTicks, double pxToBlocks) {
        Move m = move(p.move());
        double along = m.along(p.size(), clipTicks) * pxToBlocks;
        return new Vec3(p.x() + p.dirX() * along, p.y() + m.rise(p.size(), clipTicks), p.z() + p.dirZ() * along);
    }

    /**
     * How many ticks it turns square on the spot before a move toward this heading, from the way it faces now; -1 if it
     * faces too far off to be going there ({@link #SQUARE}, {@link #MOST_TURN}, {@link #TURN_RATE}).
     */
    public static int turnFor(float bodyYaw, float heading) {
        double off = Math.abs(Mth.wrapDegrees(bodyYaw - heading));
        if (off > MOST_TURN) {
            return -1;
        }
        return off <= SQUARE ? 0 : Mth.ceil(off / TURN_RATE);
    }

    /** A new play of a move from (x, y, z) toward this heading, after turning square on the spot for {@code turn} ticks. */
    public Play start(Mob mob, int move, double x, double y, double z, float heading, int turn) {
        return new Play(x, y, z, heading, mob.yBodyRot, mob.level().getGameTime(), nearest(mob.getScale()), move, turn);
    }

    /** What a tick of {@link #step} came to. */
    public enum Step {
        /** Still on the move: the box placed on its path, nothing else to move it this tick. */
        ON,
        /** Ended afloat: it glides on at the move's last speed (the animal ends the play, its swim takes over). */
        GLIDE,
        /** Ended on its own (the clip and its rest played); nothing moved this tick. */
        DONE,
        /** Cut short (dead, in water a land move does not expect, the bank or step gone, a block in its way): set down where it was. */
        CUT
    }

    /**
     * Moves the animal on along its move this tick (server; the animal clears its play on any answer but {@link Step#ON}).
     * Its box goes along the path: climbing up (or out) on the lower level (or in the water at the path's height) until
     * its front would reach the face, then on top; going down (or in) on top until its back has passed the edge, then on
     * the lower level (or at the path's height, into the water). The drawing works its own place out from the path, so
     * the box only has to stay out of the blocks.
     *
     * @param pxToBlocks how long one model pixel is drawn (blocks)
     * @param rest       whether it stands {@link #rest} ticks after a move ending on land (false when its route goes
     *                   straight on into another move: a hill)
     */
    public Step step(Mob mob, Play p, double pxToBlocks, boolean rest) {
        double now = mob.level().getGameTime();
        double ticks = ticks(p, now);
        Move m = move(p.move());
        double back = m.aheadPx * pxToBlocks;
        boolean water = m.endsAfloat || m.startsAfloat || m.wades;
        // the bank's or step's block it relies on: going down or in, the one it leaves; up or out, the one past the face
        double to = m.goesDown ? back - 0.5 : back + 0.5;
        double blockY = m.goesDown ? -0.5 : m.endLevel - 0.5;
        BlockPos step = BlockPos.containing(p.x() + p.dirX() * to, p.y() + blockY, p.z() + p.dirZ() * to);
        int end = m.ticks + (m.endsAfloat || !rest ? 0 : data.rest);
        if (ticks > end) {
            return Step.DONE;
        }
        if (mob.isDeadOrDying() || !water && mob.isInWater()
                || !mob.level().getBlockState(step).isCollisionShapeFullBlock(mob.level(), step)) {
            cut(mob);
            return Step.CUT;
        }
        double along = m.along(p.size(), ticks) * pxToBlocks, rise = m.rise(p.size(), ticks);
        double half = mob.getBbWidth() / 2.0;
        boolean passed = m.goesDown ? along - half > back : along + half > back;
        double height = passed ? (m.endsAfloat ? rise : m.endLevel) : (m.startsAfloat ? rise : 0.0);
        Vec3 to2 = new Vec3(p.x() + p.dirX() * along, p.y() + height, p.z() + p.dirZ() * along);
        AABB box = mob.getDimensions(mob.getPose()).makeBoundingBox(to2).deflate(1.0E-3);
        if (!mob.level().noCollision(mob, box)) {
            cut(mob);                           // a block put in its way: it stops where it was
            return Step.CUT;
        }
        mob.setPos(to2.x, to2.y, to2.z);
        mob.fallDistance = 0.0F;
        float yaw = facing(p, now);
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.yHeadRot = yaw;
        if (ticks >= m.ticks && m.endsAfloat) {
            // afloat: it glides on at the move's last speed into the swim
            double v = m.endSpeed(p.size()) * pxToBlocks;
            mob.setDeltaMovement(p.dirX() * v, 0.0, p.dirZ() * v);
            mob.setOnGround(false);
            return Step.GLIDE;
        }
        mob.setDeltaMovement(Vec3.ZERO);
        return Step.ON;
    }

    /**
     * Ends a move cleanly from any tick (hurt, the ground gone, a block in the way): the box set down where it really is,
     * pushed up out of any block it ended in, and left to the game's own walking, falling and floating from there; its
     * drawing slides from the path back onto it, and its clip player eases back to its walk or swim by itself.
     */
    public static void cut(Mob mob) {
        mob.setDeltaMovement(Vec3.ZERO);
        for (int i = 0; i < 16 && !mob.level().noCollision(mob, mob.getBoundingBox().deflate(1.0E-3)); i++) {
            mob.setPos(mob.getX(), mob.getY() + 0.1, mob.getZ());
        }
        mob.fallDistance = 0.0F;
    }
}
