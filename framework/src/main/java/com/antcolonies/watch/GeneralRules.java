package com.antcolonies.watch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * The raid test bench, B1: the rules every colony of every species shares (the raid rules are the savanna mod's,
 * {@code RaidRules}). Limits start loose (the plan's pick: better a missed rare bug than a flood of false alarms).
 */
public final class GeneralRules {
    /** not-stuck: walking (a route planned) yet still this long (ticks): 30 s. */
    static final long STUCK_TICKS = 600;
    /** not-in-a-block: inside a solid block this long (ticks): 3 s. */
    static final long IN_BLOCK_TICKS = 60;
    /** not-walled-in: still this long (ticks) before its pocket is looked at: 60 s. */
    static final long WALLED_STILL_TICKS = 1200;
    /** not-walled-in: insects this near the nest's middle are never looked at (they are home). */
    static final double HOME_REACH = 6;
    /** not-walled-in: the pocket looked through at most this far (blocks) and this many cells; past either, it is open. */
    static final int POCKET_REACH = 32;
    static final int POCKET_CELLS = 4000;
    /** not-walled-in: pockets looked through a check, at most (each is a few thousand cells at worst). */
    static final int POCKETS_A_CHECK = 3;
    /** prey-ends: paralysed this long past its paralysis' own end (ticks): a minute. */
    static final long PREY_GRACE = 1200;
    /** save-size: a colony's save looked at this often (ticks): 5 minutes. */
    static final long SAVE_EVERY = 6000;
    /** save-size: a colony's save bigger than this (bytes) breaks the rule: 2 MB. */
    static final int SAVE_MOST = 2_000_000;

    /**
     * holds-its-face: an insect its legs say holds a wall or a ceiling has the face this near its box (blocks; loose: a
     * body over an edge or round a chimney's rim is a little off for a moment), and one going up or down a channel is
     * at least {@link #CHANNEL_OFF} from the channel's middle line (it holds a side wall, docs/BUGS.md 129).
     */
    static final double FACE_GAP = 0.15;
    static final double CHANNEL_OFF = 0.08;
    /** holds-its-face: off its face this long (ticks): 5 s (a look round from a chimney is 2.8 s). */
    static final long FLOAT_TICKS = 100;

    private static final Map<UUID, Long> SAVE_CHECKED = new ConcurrentHashMap<>();

    private GeneralRules() {
    }

    public static void signUp() {
        Watch.signUp("not-stuck", "no insect stands still for 30 s while walking a route", GeneralRules::notStuck);
        Watch.signUp("not-in-a-block", "no insect sits inside a solid block for more than 3 s", GeneralRules::notInABlock);
        Watch.signUp("holds-its-face", "no insect holding a wall, a ceiling or a channel's side floats off it for more than 5 s",
                GeneralRules::holdsItsFace);
        Watch.signUp("not-walled-in", "no insect stays a minute in a pocket with no way to its nest or the open air", GeneralRules::notWalledIn);
        Watch.signUp("prey-ends", "a paralysed insect is carried, eaten or dies by its paralysis' end", GeneralRules::preyEnds);
        Watch.signUp("save-size", "a colony's save stays under 2 MB", GeneralRules::saveSize);
    }

    static void notStuck(Watch.Look look) {
        List<AbstractAntEntity> stuck = new ArrayList<>();
        for (AbstractAntEntity insect : look.insects()) {
            // Still counts only from when it got its route: an ant standing rightly and just sent off is not stuck.
            long walking = look.lasting(insect.getUUID().toString(), !insect.getNavigator().isDone());
            if (walking > 0 && !insect.isDowned() && !insect.isPassenger() && Math.min(walking, look.stillFor(insect)) >= STUCK_TICKS) {
                stuck.add(insect);
            }
        }
        if (!stuck.isEmpty()) {
            look.breach(stuck.subList(0, Math.min(10, stuck.size())), stuck.size() + " insect(s) with a route but not moving for "
                    + STUCK_TICKS / 20 + " s or more: " + who(stuck));
        }
    }

    static void notInABlock(Watch.Look look) {
        for (AbstractAntEntity insect : look.insects()) {
            long inside = look.lasting(insect.getUUID().toString(), insect.isInWall() && !insect.isPassenger());
            if (inside > IN_BLOCK_TICKS) {
                look.breach(List.of(insect), InsectStories.kind(insect) + " " + InsectStories.shortId(insect) + " inside "
                        + look.level().getBlockState(insect.blockPosition()).getBlock().getName().getString() + " for " + inside / 20 + " s");
            }
        }
    }

    static void holdsItsFace(Watch.Look look) {
        List<AbstractAntEntity> floating = new ArrayList<>();
        for (AbstractAntEntity insect : look.insects()) {
            long off = look.lasting(insect.getUUID().toString() + "/face", floats(insect));
            if (off > FLOAT_TICKS) {
                floating.add(insect);
            }
        }
        if (!floating.isEmpty()) {
            look.breach(floating.subList(0, Math.min(10, floating.size())), floating.size() + " insect(s) floating off the face their legs hold for "
                    + FLOAT_TICKS / 20 + " s or more: " + who(floating) + "; " + floatingDetail(floating.get(0)));
        }
    }

    /** What the first floating insect's legs hold and what is there (so a report says why without the world). */
    private static String floatingDetail(AbstractAntEntity insect) {
        com.antcolonies.nav.AntNavigator legs = insect.getNavigator();
        Direction face = legs.getSurface();
        BlockPos held = insect.blockPosition().relative(face);
        return String.format(java.util.Locale.ROOT, "%s at %.3f, %.3f, %.3f holds %s%s, there %s, its own cell %s",
                insect.getUUID().toString().substring(0, 8), insect.getX(), insect.getY(), insect.getZ(), face.getSerializedName(),
                legs.isInChannel() ? " in a channel" : "", insect.level().getBlockState(held),
                insect.level().getBlockState(insect.blockPosition()));
    }

    /**
     * Whether an insect's legs hold a wall, a ceiling or a channel's side wall that its body is not at (real sizes,
     * 2026-11-06: small bodies placed where a big one filled the space float, docs/BUGS.md 129 and 131).
     */
    public static boolean floats(AbstractAntEntity insect) {
        com.antcolonies.nav.AntNavigator legs = insect.getNavigator();
        Direction face = legs.getSurface();
        if (!legs.isAttached() || face == Direction.DOWN || insect.isPassenger() || insect.isDowned() || insect.isDeadOrDying()) {
            return false;
        }
        if (legs.isInChannel()) {
            if (!face.getAxis().isHorizontal()) {
                return false;
            }
            BlockPos cell = insect.blockPosition();
            double off = face.getAxis() == Direction.Axis.X ? insect.getX() - (cell.getX() + 0.5) : insect.getZ() - (cell.getZ() + 0.5);
            return Math.abs(off) < CHANNEL_OFF;
        }
        net.minecraft.world.phys.AABB box = insect.getBoundingBox();
        net.minecraft.world.phys.AABB slab = switch (face) {
            case UP -> new net.minecraft.world.phys.AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + FACE_GAP, box.maxZ);
            case NORTH -> new net.minecraft.world.phys.AABB(box.minX, box.minY, box.minZ - FACE_GAP, box.maxX, box.maxY, box.minZ);
            case SOUTH -> new net.minecraft.world.phys.AABB(box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ + FACE_GAP);
            case WEST -> new net.minecraft.world.phys.AABB(box.minX - FACE_GAP, box.minY, box.minZ, box.minX, box.maxY, box.maxZ);
            default -> new net.minecraft.world.phys.AABB(box.maxX, box.minY, box.minZ, box.maxX + FACE_GAP, box.maxY, box.maxZ);
        };
        return insect.level().noCollision(insect, slab.inflate(0.01));
    }

    static void notWalledIn(Watch.Look look) {
        Colony colony = look.colony();
        ServerLevel level = look.level();
        List<AbstractAntEntity> candidates = new ArrayList<>();
        for (AbstractAntEntity insect : look.insects()) {
            if (look.stillFor(insect) < WALLED_STILL_TICKS || insect.isParalysed() || insect.getUUID().equals(colony.getQueenId())
                    || insect.isPassenger() || insect.blockPosition().closerThan(colony.getCenter(), HOME_REACH)
                    || colony.nest().isInside(level, insect.blockPosition())) {
                continue;
            }
            candidates.add(insect);
        }
        if (candidates.isEmpty()) {
            return;
        }
        int start = (int) ((look.now() / 20) % candidates.size());
        for (int i = 0; i < Math.min(POCKETS_A_CHECK, candidates.size()); i++) {
            AbstractAntEntity insect = candidates.get((start + i) % candidates.size());
            int cells = closedPocket(level, colony, insect.blockPosition());
            if (cells > 0) {
                look.breach(List.of(insect), InsectStories.kind(insect) + " " + InsectStories.shortId(insect) + " walled in: still "
                        + look.stillFor(insect) / 20 + " s in a closed pocket of " + cells + " open cells, no way to the nest or the sky");
            }
        }
    }

    /**
     * The open cells an insect at {@code from} can reach, walking and climbing (any cell not a full block), if none of
     * them sees the sky, is the nest, or is unloaded land: their count; 0 when the pocket is open (or too big to tell).
     */
    public static int closedPocket(ServerLevel level, Colony colony, BlockPos from) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        seen.add(from);
        open.add(from);
        while (!open.isEmpty()) {
            BlockPos cell = open.poll();
            if (!level.isLoaded(cell) || level.canSeeSky(cell) || colony.nest().isInside(level, cell)) {
                return 0;
            }
            if (seen.size() > POCKET_CELLS) {
                return 0;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = cell.relative(direction);
                if (seen.contains(next) || Math.abs(next.getX() - from.getX()) > POCKET_REACH || Math.abs(next.getY() - from.getY()) > POCKET_REACH
                        || Math.abs(next.getZ() - from.getZ()) > POCKET_REACH) {
                    if (!seen.contains(next)) {
                        return 0; // as far as it looks and still going: call it open
                    }
                    continue;
                }
                if (!level.isLoaded(next)) {
                    return 0;
                }
                if (level.getBlockState(next).isCollisionShapeFullBlock(level, next)) {
                    continue;
                }
                seen.add(next);
                open.add(next);
            }
        }
        return seen.size();
    }

    static void preyEnds(Watch.Look look) {
        for (AbstractAntEntity insect : look.insects()) {
            long over = insect.paralysedForTicks() - insect.paralysisLasts();
            if (insect.isParalysed() && over > PREY_GRACE) {
                look.breach(List.of(insect), InsectStories.kind(insect) + " " + InsectStories.shortId(insect) + " paralysed "
                        + insect.paralysedForTicks() / 20 + " s, " + over / 20 + " s past its paralysis' end, not carried, eaten or dead");
            }
        }
    }

    static void saveSize(Watch.Look look) {
        Long checked = SAVE_CHECKED.get(look.colony().getId());
        if (checked != null && look.now() - checked < SAVE_EVERY && look.now() >= checked) {
            return;
        }
        SAVE_CHECKED.put(look.colony().getId(), look.now());
        int bytes = look.colony().save().sizeInBytes();
        if (bytes > SAVE_MOST) {
            look.breach(look.colony().getCenter(), List.of(), "the colony's save is " + bytes / 1024 + " kB (most " + SAVE_MOST / 1024 + " kB)");
        }
    }

    /** Up to 10 insects' kinds and ids, for a breach's words. */
    static String who(List<AbstractAntEntity> insects) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(10, insects.size()); i++) {
            AbstractAntEntity insect = insects.get(i);
            out.append(i == 0 ? "" : ", ").append(InsectStories.kind(insect)).append(' ').append(InsectStories.shortId(insect)).append(" at ")
                    .append(insect.blockPosition().toShortString());
        }
        if (insects.size() > 10) {
            out.append(", ...");
        }
        return out.toString();
    }

    static void clear() {
        SAVE_CHECKED.clear();
    }
}
