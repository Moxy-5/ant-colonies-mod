package com.antcolonies.gametest;

import java.util.List;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Watches an insect walking in a game test (the split, step S4: from the ant's walking tests, as the termites' climbing
 * and tunnel tests use it too): it never hangs in the air, leans on a wall it has not reached, or floats while climbing.
 */
final class TestWatch {
    private TestWatch() {
    }

    /** Most climbing ticks in a row an ant may hang in mid-air, touching nothing (a short moment at the very top is fine). */
    static final int MAX_FLOATING_TICKS = 6;
    /** Most ticks in a row an ant may lean on a wall while standing on the floor without touching it (a moment at the very top is fine). */
    static final int MAX_LEANING_TICKS = 8;
    /** Most ticks in a row a walking ant may hang in the air on a wall without rising or sliding on anything (just a moment at a corner). */
    static final int MAX_HOVER_TICKS = 4;

    /** Watches the insect every tick; with {@code verbose}, logs its position and what its walker is doing every 4 ticks. */
    static void watch(GameTestHelper helper, AbstractAntEntity ant, boolean verbose) {
        Vec3 origin = Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO));
        int[] floating = {0};
        int[] leaning = {0};
        int[] hovering = {0};
        Vec3[] lastPos = {null};
        helper.onEachTick(() -> {
            // An ant on its way up that neither rises (nor crawls sideways along its wall) nor has anything under it
            // hangs in the air: it must always be moving on.
            boolean moved = lastPos[0] != null && ant.position().distanceTo(lastPos[0]) >= 0.01;
            lastPos[0] = ant.position();
            boolean hovers = ant.getNavigator().isClimbing() && !ant.getNavigator().isDone() && !moved
                    && !ant.getNavigator().isMovingExactly() // holding still in a chimney mouth's tube, looking out
                    && helper.getLevel().noCollision(ant, ant.getBoundingBox().move(0.0, -0.05, 0.0))
                    && helper.getLevel().noCollision(ant, ant.getBoundingBox().move(0.0, 0.05, 0.0)); // a ceiling holds it too
            hovering[0] = hovers ? hovering[0] + 1 : 0;
            helper.assertTrue(hovering[0] <= MAX_HOVER_TICKS, "the ant hangs in the air without climbing at "
                    + ant.position().subtract(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))) + ", on " + ant.getNavigator().getFoothold()
                    + ", step " + ant.getNavigator().getNextWaypoint() + " of route " + ant.getNavigator().getPlannedRoute());
        });
        helper.onEachTick(() -> {
            // An ant standing on the floor away from any wall must walk, not lean against the wall: that looks like hovering.
            boolean leansOnTheFloor = ant.getNavigator().isClimbing() && ant.onGround() && !ant.horizontalCollision;
            leaning[0] = leansOnTheFloor ? leaning[0] + 1 : 0;
            helper.assertTrue(leaning[0] <= MAX_LEANING_TICKS, "the ant leans against a wall it has not reached, at "
                    + ant.position().subtract(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))) + ", on " + ant.getNavigator().getFoothold()
                    + ", route " + ant.getNavigator().getPlannedRoute());
        });
        helper.onEachTick(() -> {
            // Holding nothing: no block's shape (as it is drawn) near the body. The drawn shape, not the collision: a
            // termite climbs the outside of a chimney mouth it could also pass through. Not while it goes in or out by a
            // chimney mouth's tube: that motion is exact, and the tube sticks up out of the block.
            boolean hanging = ant.getNavigator().isClimbing() && !ant.getNavigator().isMovingExactly()
                    && !touchesABlock(helper, ant.getBoundingBox().inflate(0.08));
            floating[0] = hanging ? floating[0] + 1 : 0;
            helper.assertTrue(floating[0] <= MAX_FLOATING_TICKS, "the ant is floating while climbing, at "
                    + ant.position().subtract(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))) + ", on " + ant.getNavigator().getFoothold()
                    + ", route " + ant.getNavigator().getPlannedRoute());
        });
        helper.onEachTick(() -> {
            if (verbose && helper.getTick() % 4 == 0 && helper.getTick() >= 8) {
                Vec3 at = ant.position().subtract(origin);
                List<BlockPos> route = ant.getNavigator().getRoute();
                String next = route == null || ant.getNavigator().getNextWaypoint() >= route.size()
                        ? "-" : route.get(ant.getNavigator().getNextWaypoint()).subtract(BlockPos.containing(origin)).toShortString();
                AntColoniesMod.LOGGER.info("[nav-test] #{} t{} pos {},{},{} cell {} next {} climbing={} collided={} done={} failed={} attached={} motion={} y={}",
                        ant.getId(), helper.getTick(), r(at.x), r(at.y), r(at.z),
                        ant.blockPosition().subtract(BlockPos.containing(origin)).toShortString(), next,
                        ant.getNavigator().isClimbing(), ant.horizontalCollision,
                        ant.getNavigator().isDone(), ant.getNavigator().hasFailed(), ant.getNavigator().isAttached(), ant.getNavigator().getClimbMotion(), ant.getY());
            }
        });
    }

    /** Whether any block's shape (as it is drawn, not its collision for someone) reaches into the box. */
    static boolean touchesABlock(GameTestHelper helper, net.minecraft.world.phys.AABB box) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            for (net.minecraft.world.phys.AABB part : level.getBlockState(pos).getShape(level, pos).toAabbs()) {
                if (part.move(pos).intersects(box)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String r(double value) {
        return String.format("%.3f", value);
    }
}
