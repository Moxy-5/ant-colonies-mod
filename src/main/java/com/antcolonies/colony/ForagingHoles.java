package com.antcolonies.colony;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.registry.ModTags;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The foraging holes of one colony (species whose profile has {@code nest.foraging_holes}: termites): small holes in
 * the ground a few blocks out from the nest, at the end of shallow tunnels from the entrance shaft (see
 * {@link NestPlan#foragingTunnels}). The workers dig the tunnels like the rest of the nest; the holes are opened when it
 * is time to forage ({@link Colony#isForagingTime}) and plugged with soil from inside when it is over, once everybody is
 * back in (or after a while: a straggler finds another way in, such as a chimney).
 *
 * <p>The middle hole: while a young mound is too low to be roofed over, the entrance in its middle (the top of the
 * nest's shaft) is open to the sky. A colony that keeps to a schedule plugs it by day like the foraging holes (the
 * user's request: real mounds are closed) and opens it again at night ({@link Nest#mayOpenEntrance}). Once the mound
 * covers it, it stays open: it is then only the way up into the mound's own shaft.
 *
 * <p>New holes near food (the user's decision, 2026-10-14; species whose profile has {@code nest.new_holes}): when the
 * colony knows a rich plant (a fallen branch, a fallen tree, a hay bale) more than {@code min_distance} blocks from
 * every way out and within its tunnels' reach (growing with the colony), the workers dig a tunnel underground out to
 * it ({@link TunnelRoute}), one at a time, and open a hole beside the food, so the foragers need not cross the open
 * ground (real Macrotermes forage from holes opened near the food at the end of long galleries). The soil goes into
 * the mound like all digging. Once the food is gone the hole is plugged for good; the tunnel stays, as real galleries do.
 *
 * <p>Mud sheeting (the user's plan of 2026-10-14; species whose profile has {@code nest.sheeting}): rich food the colony
 * knows near one of its holes (no new hole is dug for it) gets a runway of sheeting from the hole to it ({@link
 * FoodWays.Runway}, {@link RunwayRoute}): the workers plaster mud over it at night, and once it is closed all round its hole
 * stays open by day too and the food at its end is eaten from under it, unseen. A hole with a runway is no way out for
 * the foragers outside (it leads into the runway), so one hole is always left without. Once the food is gone the
 * runway's mud crumbles away bit by bit.
 */
public class ForagingHoles {
    /** After the foraging time is over the holes are plugged at the latest this long after (ticks), even with workers still outside. */
    public static final int PLUG_GRACE_TICKS = 600;
    /** A worker's claim on a hole it plugs lasts this long (ticks). */
    private static final int CLAIM_TICKS = 600;
    /** How often (ticks) the colony checks whether any of its members are still outside. */
    private static final int OUTSIDE_CHECK_TICKS = 20;

    private final Colony colony;
    private final Claims<UUID> plugClaims = new Claims<>();
    /** Which hole each worker that plugs one is on (one worker to a hole). */
    private final java.util.Map<UUID, BlockPos> plugging = new java.util.HashMap<>();
    /** Game time the foraging time was first seen to be over (not saved: after a reload the grace starts again). */
    private long closedSince = Colony.NOT_SET;
    private long outsideCheckedAt = Long.MIN_VALUE;
    private boolean anyoneOutside;
    /** How often (ticks) the colony looks for rich food to dig a new tunnel to, and for new holes whose food is gone. */
    private static final int NEW_HOLE_CHECK_TICKS = 200;
    /** How long (ticks) the colony leaves a plant it found no tunnel route to before it tries again. */
    private static final int NO_ROUTE_TICKS = 6000;
    /** How near (blocks) a rich plant must still be to the food a new tunnel was dug for, or its hole is spent. */
    private static final int FOOD_STILL_THERE = 4;
    private long newHolesCheckedAt = Long.MIN_VALUE;
    /** Plants the colony found no tunnel route to, and until when it leaves them. Not saved. */
    private final java.util.Map<BlockPos, Long> noRouteUntil = new java.util.HashMap<>();
    /**
     * Plants the colony found no runway route to, and until when it leaves them; kept apart from {@link #noRouteUntil}
     * so food a runway cannot reach may still get a gallery piece (the user's bug, 2026-10-17). Not saved.
     */
    private final java.util.Map<BlockPos, Long> noRunwayUntil = new java.util.HashMap<>();

    ForagingHoles(Colony colony) {
        this.colony = colony;
    }

    /**
     * A way out of the nest that is open now: {@code mouth} is the cell an insect steps out into (the one above a
     * foraging hole, or the open entrance itself), {@code outward} the way out from the nest there (the way a foraging
     * tunnel runs; null for the entrance, which lies in the middle).
     */
    public record WayOut(BlockPos mouth, @Nullable Direction outward) {
    }

    /**
     * The colony's ways out that are open now: every open foraging hole whose tunnel is dug, and the entrance while it
     * is open to the sky (a young mound has no roof over it yet; an ant nest's hole is always one).
     */
    public List<WayOut> waysOut(Level level) {
        List<WayOut> ways = new java.util.ArrayList<>();
        for (NestPlan.ForagingTunnel tunnel : tunnels(level)) {
            // A hole with a runway of sheeting over it leads into the runway, not outside; a hole closed for danger (brain
            // step 4g) is no way out even before it is plugged.
            if (isDug(level, tunnel) && isOpen(level, tunnel.hole()) && !colony.nest().foodWays().isSpent(tunnel.hole())
                    && colony.nest().foodWays().runwayAt(tunnel.hole()) == null && !ClosedHoles.isClosedIn(colony, level, tunnel.hole())) {
                ways.add(new WayOut(tunnel.hole().above(), tunnel.side()));
            }
        }
        BlockPos entrance = colony.nest().getEntrance();
        if (isOpen(level, entrance) && Nest.isDugOut(level, entrance.below()) && !colony.nest().isEntranceCovered(level)) {
            ways.add(new WayOut(entrance, null));
        }
        return ways;
    }

    /** The foraging tunnels of the nest's plan (none until the plan is made, or if the species has none). */
    public List<NestPlan.ForagingTunnel> tunnels(Level level) {
        return colony.nest().getForagingTunnels(level);
    }

    /** Whether this block is one of the colony's foraging holes. */
    public boolean isHole(Level level, BlockPos pos) {
        for (NestPlan.ForagingTunnel tunnel : tunnels(level)) {
            if (tunnel.hole().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The middle hole as a hole to plug from the top of the shaft under it, or null when it needs no plugging: a species
     * that keeps no schedule or builds no mound, a colony still founding (the founders seal it themselves), the shaft
     * not dug, or the mound already covers it.
     */
    @Nullable
    NestPlan.ForagingTunnel middleHole(Level level) {
        BlockPos entrance = colony.nest().getEntrance();
        if (!colony.keepsASchedule() || !colony.part(ColonyParts.MOUND).map(Mound::isActive).orElse(false) || !level.isLoaded(entrance)
                || !Nest.isDugOut(level, entrance.below()) || colony.nest().isEntranceCovered(level)) {
            return null;
        }
        return new NestPlan.ForagingTunnel(List.of(entrance.below()), entrance, Direction.UP);
    }

    /**
     * The foraging holes, and the middle hole while it needs plugging by day. Not a hole under a finished runway of
     * sheeting: it stays open (the runway covers it), unless it is closed for danger (brain step 4g).
     */
    private List<NestPlan.ForagingTunnel> holesToPlug(Level level) {
        List<NestPlan.ForagingTunnel> all = new java.util.ArrayList<>();
        for (NestPlan.ForagingTunnel tunnel : tunnels(level)) {
            if (!hasFinishedRunway(level, tunnel.hole()) || ClosedHoles.isClosedIn(colony, level, tunnel.hole())) {
                all.add(tunnel);
            }
        }
        NestPlan.ForagingTunnel middle = middleHole(level);
        if (middle != null) {
            all.add(middle);
        }
        return all;
    }

    /** Whether a runway of sheeting leaves from this hole and is finished (closed all round). */
    public boolean hasFinishedRunway(Level level, BlockPos hole) {
        FoodWays.Runway runway = colony.nest().foodWays().runwayAt(hole);
        return runway != null && colony.nest().foodWays().isRunwayClosed(level, runway);
    }

    /** Whether the whole tunnel is dug out, so its hole leads somewhere. */
    public static boolean isDug(Level level, NestPlan.ForagingTunnel tunnel) {
        for (BlockPos pos : tunnel.tunnel()) {
            if (!Nest.isDugOut(level, pos)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the hole is open: nothing solid in it, no water. */
    public static boolean isOpen(Level level, BlockPos hole) {
        BlockState state = level.getBlockState(hole);
        return level.isLoaded(hole) && state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /** Whether the hole holds the plug of soil the workers packed into it. */
    public boolean isPlug(Level level, BlockPos pos) {
        return isHole(level, pos) && colony.profile().nest().isPlug(level.getBlockState(pos));
    }

    /**
     * Whether a worker may bite this hole open now: it is foraging time, it is the hole's turn (termites underground, see
     * {@link Galleries#isInTurn}), and the hole is closed with soil or its plug. A hole under a finished runway is always open.
     * Never a hole closed for danger (brain step 4g, {@link ClosedHoles}), runway or not.
     */
    public boolean mayOpen(Level level, BlockPos hole) {
        if (ClosedHoles.isClosedIn(colony, level, hole)) {
            return false;
        }
        boolean runway = hasFinishedRunway(level, hole);
        if (!(colony.isForagingTime(level) && colony.nest().galleries().isInTurn(hole) || runway) || !level.isLoaded(hole)) {
            return false;
        }
        BlockState state = level.getBlockState(hole);
        return (state.is(ModTags.DIGGABLE) || colony.profile().nest().isPlug(state)) && !touchesFluid(level, hole);
    }

    /** A closed hole to open now (its tunnel is dug and it is foraging time), or null. */
    @Nullable
    BlockPos holeToOpen(Level level) {
        for (NestPlan.ForagingTunnel tunnel : tunnels(level)) {
            if (isDug(level, tunnel) && !isOpen(level, tunnel.hole()) && !colony.nest().foodWays().isSpent(tunnel.hole()) && mayOpen(level, tunnel.hole())) {
                return tunnel.hole();
            }
        }
        return null;
    }

    /** How many holes are open now (for the colony list). */
    public int countOpen(Level level) {
        int open = 0;
        for (NestPlan.ForagingTunnel tunnel : tunnels(level)) {
            if (isDug(level, tunnel) && isOpen(level, tunnel.hole())) {
                open++;
            }
        }
        return open;
    }

    /**
     * Whether open holes should be plugged now: the foraging time is over, and either every member of the colony is
     * back inside or {@link #PLUG_GRACE_TICKS} have passed since.
     */
    public boolean wantsPlugging(Level level) {
        long now = level.getGameTime();
        if (colony.isForagingTime(level)) {
            closedSince = Colony.NOT_SET;
            return false;
        }
        if (closedSince == Colony.NOT_SET) {
            closedSince = now;
        }
        return now - closedSince >= PLUG_GRACE_TICKS || !isAnyoneOutside(level);
    }

    /**
     * Whether this open hole should be plugged now: one closed for danger at once, whatever the hour (brain step 4g: real
     * termites pack an attacked hole with soil straight away, even if that shuts some foragers out: they come home by
     * another), the others once the foraging time is over ({@link #wantsPlugging}).
     */
    public boolean wantsPlugged(Level level, BlockPos hole) {
        return ClosedHoles.isClosedIn(colony, level, hole) || wantsPlugging(level);
    }

    /** Whether any member of the colony is outside the nest and the mound (checked once a second). */
    public boolean isAnyoneOutside(Level level) {
        long now = level.getGameTime();
        if (outsideCheckedAt == Long.MIN_VALUE || now - outsideCheckedAt >= OUTSIDE_CHECK_TICKS || now < outsideCheckedAt) {
            outsideCheckedAt = now;
            AABB area = colony.nestArea().inflate(8);
            anyoneOutside = !level.getEntitiesOfClass(AbstractAntEntity.class, area,
                    ant -> ant.isAlive() && colony.getId().equals(ant.getColonyId()) && !colony.nest().isInside(level, ant.blockPosition())).isEmpty();
        }
        return anyoneOutside;
    }

    /** An open hole for this worker to plug now (claimed for it, one worker to a hole), or null. */
    @Nullable
    public NestPlan.ForagingTunnel pickHoleToPlug(Level level, AbstractAntEntity ant) {
        boolean all = wantsPlugging(level);
        if (!all && !colony.part(ColonyParts.CLOSED_HOLES).map(closed -> closed.anyClosed(level)).orElse(false)) {
            return null;
        }
        long now = level.getGameTime();
        plugClaims.prune(now);
        plugging.keySet().removeIf(id -> !plugClaims.contains(id));
        for (NestPlan.ForagingTunnel tunnel : holesToPlug(level)) {
            if (!isDug(level, tunnel) || !isOpen(level, tunnel.hole()) || !all && !ClosedHoles.isClosedIn(colony, level, tunnel.hole())) {
                continue;
            }
            boolean taken = false;
            for (java.util.Map.Entry<UUID, BlockPos> entry : plugging.entrySet()) {
                taken |= !entry.getKey().equals(ant.getUUID()) && entry.getValue().equals(tunnel.hole());
            }
            if (!taken) {
                plugClaims.put(ant.getUUID(), now + CLAIM_TICKS);
                plugging.put(ant.getUUID(), tunnel.hole());
                return tunnel;
            }
        }
        return null;
    }

    public void releasePlug(UUID ant) {
        plugClaims.remove(ant);
        plugging.remove(ant);
    }

    /**
     * Packs the hole shut with soil (the species' plug block), from {@code inside} (the cell under it, in the nest).
     * Nestmates standing in the hole are nudged down into {@code inside} first (they do not push each other, and the
     * hole is where everybody coming home passes: with a few idling in it, it was never plugged, the user's bug of
     * 2026-10-13, like bug 8, the hill jam). False if anything else is in the hole (a player, a chicken, an insect of
     * another colony): nobody is buried.
     */
    public boolean plug(Level level, BlockPos hole, BlockPos inside) {
        if (!isOpen(level, hole)) {
            return false;
        }
        List<LivingEntity> inHole = level.getEntitiesOfClass(LivingEntity.class, new AABB(hole), LivingEntity::isAlive);
        for (LivingEntity living : inHole) {
            if (!(living instanceof AbstractAntEntity ant) || !colony.getId().equals(ant.getColonyId())) {
                return false;
            }
        }
        if (!inHole.isEmpty() && !level.getBlockState(inside).getCollisionShape(level, inside).isEmpty()) {
            return false; // nowhere to nudge them to
        }
        for (LivingEntity ant : inHole) {
            ant.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
            ant.setDeltaMovement(0, 0, 0);
            ((AbstractAntEntity) ant).getNavigator().stop();
        }
        return level.setBlockAndUpdate(hole, colony.profile().nest().plugState());
    }

    // --- new holes near food ----------------------------------------------------------------------------------

    /**
     * Whether rich food the colony knows waits for a gallery piece the workers do not allow yet (termites underground,
     * step 3: food leads, so the galleries grow no other way meanwhile). Not saved: worked out again on the next check.
     */
    private boolean foodWaiting;

    /** See {@link #foodWaiting}. */
    public boolean isFoodWaiting() {
        return foodWaiting;
    }

    /** The check below now, whatever the clock (termites underground step 4: the builders' time spent on return). */
    void checkNewHolesNow(ServerLevel level) {
        newHolesCheckedAt = Long.MIN_VALUE;
        tick(level);
    }

    /**
     * Every few seconds (from {@link Colony#tick}): a new hole whose food is gone is spent (plugged for good), and if
     * every new tunnel is dug and there may be more, a new tunnel is planned out to the nearest rich plant the colony
     * knows that is far from every way out.
     */
    void tick(ServerLevel level) {
        SpeciesProfile.NewHoles numbers = colony.profile().nest().newHoles().orElse(null);
        long now = level.getGameTime();
        if (numbers == null || colony.isFounding() || tunnels(level).isEmpty()
                || (newHolesCheckedAt != Long.MIN_VALUE && now - newHolesCheckedAt < NEW_HOLE_CHECK_TICKS && now >= newHolesCheckedAt)) {
            return;
        }
        newHolesCheckedAt = now;
        foodWaiting = false;
        List<BlockPos> plants = colony.food().plants(level);
        tickRunways(level, plants, numbers);
        int active = 0;
        boolean digging = false;
        for (FoodWays.NewTunnel tunnel : colony.nest().foodWays().getNewTunnels()) {
            BlockPos hole = tunnel.tunnel().hole();
            if (colony.nest().foodWays().isSpent(hole)) {
                // Spent by mistake (the bug below) while the food is still there: back in use.
                if (level.isLoaded(tunnel.food()) && richBlockNear(level, tunnel.food(), FOOD_STILL_THERE) != null) {
                    colony.nest().foodWays().unmarkSpent(hole);
                    ColonyManager.get(level).markChanged();
                } else {
                    continue;
                }
            }
            // The real blocks round the food, not the food list: the list only holds plants near the nest's height, so a
            // fallen tree up a slope with one block eaten away counted as gone (the user's bug, 2026-10-14).
            if (level.isLoaded(tunnel.food()) && richBlockNear(level, tunnel.food(), FOOD_STILL_THERE) == null) {
                colony.nest().foodWays().markSpent(hole);
                ColonyManager.get(level).markChanged();
                AntColoniesMod.LOGGER.debug("[new-hole] the food at {} is gone: the hole at {} is plugged for good",
                        tunnel.food().toShortString(), hole.toShortString());
                continue;
            }
            active++;
            boolean dug = isDug(level, tunnel.tunnel());
            digging |= !dug;
            if (dug && tunnel.gallery() == null && level.isLoaded(tunnel.food())) {
                // A tunnel of an older save, planned without its gallery: the gallery up under the food now.
                List<BlockPos> gallery = planGallery(level, tunnel.tunnel(), tunnel.food());
                colony.nest().foodWays().setGallery(tunnel.food(), gallery);
                ColonyManager.get(level).markChanged();
                AntColoniesMod.LOGGER.debug("[new-hole] a feeding gallery of {} blocks up under the food at {}", gallery.size(),
                        tunnel.food().toShortString());
            }
        }
        if (active >= numbers.max() || digging) {
            return; // one new tunnel at a time
        }
        noRouteUntil.values().removeIf(until -> until <= now || until > now + NO_ROUTE_TICKS);
        BlockPos food = pickFood(level, plants, numbers);
        if (food == null) {
            return;
        }
        Galleries galleries = colony.nest().galleries();
        if (galleries.isOn()) {
            // Step 3 of termites underground: the way to the food is a piece of the galleries, from the nearest dug one.
            Galleries.Piece piece = galleries.pieceTo(level, food);
            if (piece == null) {
                noRouteUntil.put(food.immutable(), now + NO_ROUTE_TICKS);
                AntColoniesMod.LOGGER.debug("[new-hole] no gallery piece to the food at {}", food.toShortString());
                return;
            }
            if (!galleries.affords(level, piece, food)) {
                foodWaiting = true; // food leads: the galleries grow no other way until the workers allow this piece
                return;
            }
            List<BlockPos> gallery = planGallery(level, piece.tunnel(), food);
            colony.nest().foodWays().addNewTunnel(new FoodWays.NewTunnel(piece.tunnel(), food.immutable(), gallery));
            galleries.addFoodPiece(level, piece);
            ColonyManager.get(level).markChanged();
            AntColoniesMod.LOGGER.debug("[new-hole] a gallery piece to the food at {}, hole at {}, with a feeding gallery of {} blocks",
                    food.toShortString(), piece.tunnel().hole().toShortString(), gallery.size());
            return;
        }
        List<BlockPos> starts = startsFor(level);
        TunnelRoute.Plan plan = starts.isEmpty() ? null : TunnelRoute.plan(starts, food, ground(level), numbers.reachMax() + 16);
        if (plan == null) {
            noRouteUntil.put(food.immutable(), now + NO_ROUTE_TICKS);
            AntColoniesMod.LOGGER.debug("[new-hole] no tunnel route to the food at {} from {} dug tunnel blocks", food.toShortString(), starts.size());
            return;
        }
        BlockPos center = colony.getCenter();
        Direction side = Direction.getNearest(plan.hole().getX() - center.getX(), 0, plan.hole().getZ() - center.getZ());
        // The feeding gallery up under the food is planned with the tunnel, as one job (the user's decision, 2026-10-14:
        // real termites tunnel for the food from below from the start; the foragers outside carry on by the old holes
        // meanwhile). The workers dig the tunnel, then the gallery, then open the hole beside the food.
        NestPlan.ForagingTunnel tunnel = new NestPlan.ForagingTunnel(plan.cells(), plan.hole(), side);
        List<BlockPos> gallery = planGallery(level, tunnel, food);
        colony.nest().foodWays().addNewTunnel(new FoodWays.NewTunnel(tunnel, food.immutable(), gallery));
        ColonyManager.get(level).markChanged();
        AntColoniesMod.LOGGER.debug("[new-hole] a tunnel of {} blocks to the food at {}, hole at {}, with a feeding gallery of {} blocks",
                plan.cells().size(), food.toShortString(), plan.hole().toShortString(), gallery.size());
    }

    // --- mud sheeting ------------------------------------------------------------------------------------------

    /** On each check a plate of a crumbling runway falls away with a chance of 1 in this (my pick: about half a day). */
    private static final int CRUMBLE_CHANCE = 8;

    /**
     * Runways of mud sheeting (with the new holes' check): a runway whose food is gone (or whose hole is spent) is left
     * to crumble; the mud of the crumbling ones falls away bit by bit; and while there may be more, a runway is planned
     * from a hole to the nearest rich food the colony knows near one of its holes.
     */
    private void tickRunways(ServerLevel level, List<BlockPos> plants, SpeciesProfile.NewHoles numbers) {
        SpeciesProfile.Sheeting sheeting = colony.profile().nest().sheeting().orElse(null);
        if (sheeting == null) {
            return;
        }
        long checked = level.getGameTime();
        noRunwayUntil.values().removeIf(until -> until <= checked || until > checked + NO_ROUTE_TICKS);
        for (FoodWays.Runway runway : colony.nest().foodWays().getRunways()) {
            boolean spent = colony.nest().foodWays().isSpent(runway.hole());
            if (spent || level.isLoaded(runway.food()) && richBlockNear(level, runway.food(), FOOD_STILL_THERE) == null) {
                colony.nest().foodWays().dropRunway(runway);
                ColonyManager.get(level).markChanged();
                AntColoniesMod.LOGGER.debug("[runway] {}: the runway from {} to the food at {} crumbles",
                        spent ? "its hole is plugged for good" : "the food is gone", runway.hole().toShortString(), runway.food().toShortString());
            }
        }
        crumble(level);
        List<FoodWays.Runway> runways = colony.nest().foodWays().getRunways();
        if (runways.size() >= sheeting.max()) {
            return;
        }
        // The holes a runway may leave from: dug, open for use, without a runway yet. One is always left for the
        // foragers outside (a hole with a runway leads into it).
        List<BlockPos> free = runwayHoles(level);
        if (free.size() <= 1) {
            return;
        }
        long now = level.getGameTime();
        List<BlockPos> taken = new java.util.ArrayList<>(runways.stream().map(FoodWays.Runway::food).toList());
        colony.nest().foodWays().getNewTunnels().forEach(tunnel -> taken.add(tunnel.food()));
        BlockPos center = colony.getCenter();
        int reach = foodReach(numbers);
        BlockPos food = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos plant : plants) {
            if (!isRich(level, plant) || !colony.foodMap().knows(plant, null) || noRunwayUntil.containsKey(plant)
                    || sideways(plant, center) > reach || nearAny(plant, taken, FOOD_STILL_THERE)
                    || colony.food().isFeedableFromInside(level, plant) || colony.danger().avoids(level, plant)) {
                continue;
            }
            double distance = Double.MAX_VALUE;
            for (BlockPos hole : free) {
                distance = Math.min(distance, sideways(plant, hole));
            }
            // Near a hole: far food gets a new hole instead.
            if (distance <= numbers.minDistance() && distance < bestDistance) {
                food = plant;
                bestDistance = distance;
            }
        }
        if (food == null) {
            return;
        }
        java.util.Set<BlockPos> foodBlocks = FeedingGallery.foodBlocks(level, food, pos -> isRich(level, pos));
        BlockPos target = food;
        free.sort(java.util.Comparator.comparingDouble(hole -> sideways(hole, target)));
        for (BlockPos hole : free) {
            List<BlockPos> cells = RunwayRoute.plan(hole.above(), foodBlocks, runwayGround(level, hole), sheeting.maxLength());
            if (cells != null) {
                colony.nest().foodWays().addRunway(new FoodWays.Runway(hole.immutable(), food.immutable(), List.copyOf(cells)));
                ColonyManager.get(level).markChanged();
                AntColoniesMod.LOGGER.debug("[runway] a runway of {} blocks from the hole at {} to the food at {}", cells.size(),
                        hole.toShortString(), food.toShortString());
                return;
            }
        }
        noRunwayUntil.put(food.immutable(), now + NO_ROUTE_TICKS);
        AntColoniesMod.LOGGER.debug("[runway] no runway to the food at {}", food.toShortString());
    }

    /**
     * The ground as the runway planner sees it: the real ground of each column, and where a runway may run (open air or
     * grass, no water beside it, not the colony's own space, its mound or another runway, and clear of its other holes,
     * so no wall goes up over one: only the cell over its own {@code hole} may be beside it).
     */
    private RunwayRoute.Ground runwayGround(Level level, BlockPos hole) {
        GroundReader reader = new GroundReader(level, colony, colony.nest().nestCells(level));
        java.util.Set<BlockPos> nest = colony.nest().nestCells(level);
        java.util.Map<BlockPos, ?> mound = colony.moundParts(level);
        java.util.Set<BlockPos> used = new java.util.HashSet<>();
        for (FoodWays.Runway runway : colony.nest().foodWays().getRunways()) {
            for (BlockPos cell : runway.cells()) {
                used.add(cell);
                for (Direction side : Direction.values()) {
                    used.add(cell.relative(side));
                }
            }
        }
        java.util.Set<BlockPos> mouths = new java.util.HashSet<>();
        for (NestPlan.ForagingTunnel tunnel : tunnels(level)) {
            mouths.add(tunnel.hole().above());
        }
        mouths.add(colony.nest().getEntrance());
        mouths.add(colony.nest().getEntrance().above());
        return new RunwayRoute.Ground() {
            @Override
            public Integer groundTop(int x, int z) {
                return reader.groundTop(x, z);
            }

            @Override
            public boolean isFree(BlockPos cell) {
                if (!level.isLoaded(cell) || nest.contains(cell) || mound.containsKey(cell) || used.contains(cell)
                        || isHole(level, cell) || isHole(level, cell.below())) {
                    return false;
                }
                BlockState state = level.getBlockState(cell);
                if (!(state.isAir() || state.canBeReplaced()) || !state.getFluidState().isEmpty() || touchesFluid(level, cell)) {
                    return false;
                }
                // No wall of it beside another hole's mouth (only its own, the cell right over its hole).
                if (mouths.contains(cell)) {
                    return false;
                }
                for (Direction side : Direction.values()) {
                    BlockPos next = cell.relative(side);
                    if (mouths.contains(next) && !next.equals(hole.above())) {
                        return false;
                    }
                }
                return true;
            }
        };
    }

    /** The mud of the crumbling runways falls away, a plate at a time; a runway with no mud left is forgotten. */
    private void crumble(ServerLevel level) {
        java.util.Iterator<List<BlockPos>> it = colony.nest().foodWays().getCrumblingRunways().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            List<BlockPos> cells = it.next();
            boolean any = false;
            boolean loaded = true;
            for (BlockPos cell : cells) {
                for (Direction side : Direction.values()) {
                    BlockPos wall = cell.relative(side);
                    if (cells.contains(wall)) {
                        continue;
                    }
                    if (!level.isLoaded(wall)) {
                        loaded = false;
                        continue;
                    }
                    BlockState state = level.getBlockState(wall);
                    if (!com.antcolonies.block.MudCrustBlock.hasFace(state, side.getOpposite())) {
                        continue;
                    }
                    any = true;
                    if (colony.random().nextInt(CRUMBLE_CHANCE) == 0) {
                        BlockState less = state.setValue(com.antcolonies.block.MudCrustBlock.FACES.get(side.getOpposite()), false);
                        boolean empty = com.antcolonies.block.MudCrustBlock.FACES.values().stream().noneMatch(less::getValue);
                        level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_DESTROY_BLOCK, wall,
                                net.minecraft.world.level.block.Block.getId(state));
                        if (empty) {
                            level.removeBlock(wall, false);
                        } else {
                            level.setBlock(wall, less, net.minecraft.world.level.block.Block.UPDATE_ALL);
                        }
                    }
                }
            }
            if (!any && loaded) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            ColonyManager.get(level).markChanged();
        }
    }

    /** For the colony list: " | runways 2 (1 finished)". Empty for species without sheeting. */
    public String describeRunways(Level level) {
        if (colony.profile().nest().sheeting().isEmpty()) {
            return "";
        }
        List<FoodWays.Runway> runways = colony.nest().foodWays().getRunways();
        long finished = runways.stream().filter(runway -> colony.nest().foodWays().isRunwayClosed(level, runway)).count();
        return " | runways " + runways.size() + " (" + finished + " finished)";
    }

    /**
     * Tests and debugging: which of the checks of {@link #tickRunways} stop a runway being planned to {@code food} now,
     * for example " | why no runway: not in the plant scan, 1 free hole".
     */
    public String whyNoRunway(ServerLevel level, BlockPos food) {
        List<String> why = new java.util.ArrayList<>();
        SpeciesProfile.NewHoles numbers = colony.profile().nest().newHoles().orElse(null);
        if (numbers == null || colony.profile().nest().sheeting().isEmpty()) {
            return " | why no runway: the species has no runways";
        }
        if (colony.isFounding()) {
            why.add("still founding");
        }
        if (!colony.food().plants(level).contains(food)) {
            why.add("not in the plant scan");
        }
        if (!isRich(level, food)) {
            why.add("not rich food");
        }
        if (!colony.foodMap().knows(food, null)) {
            why.add("the colony does not know it");
        }
        if (noRunwayUntil.containsKey(food)) {
            why.add("no route found earlier");
        }
        if (sideways(food, colony.getCenter()) > foodReach(numbers)) {
            why.add("out of reach");
        }
        if (colony.food().isFeedableFromInside(level, food)) {
            why.add("eaten from inside already");
        }
        why.add(runwayHoles(level).size() + " free holes (needs 2)");
        return " | why no runway: " + String.join(", ", why);
    }

    /**
     * The feeding gallery from this tunnel up under the food (see {@link FeedingGallery}): empty if it cannot be reached
     * from below. Under whole blocks of food (a fallen tree, hay) and branches lying on the ground (cased in mud first).
     */
    private List<BlockPos> planGallery(Level level, NestPlan.ForagingTunnel tunnel, BlockPos food) {
        return FeedingGallery.plan(level, tunnel, food, ground(level),
                pos -> isRich(level, pos) && (level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)
                        || NestWalls.isWalkThroughFood(level, pos)));
    }

    /**
     * The rich plant to dig a new tunnel to, or null: one the colony knows, within its tunnels' reach of the nest's
     * middle, more than {@code min_distance} blocks from every way out (planned or open) and from the food of every new
     * tunnel; the nearest to the nest first. With the galleries on, food near a hole is left only while a runway may
     * still come to it (fewer than the most runways, a free hole near it, no failed runway route).
     */
    @Nullable
    private BlockPos pickFood(Level level, List<BlockPos> plants, SpeciesProfile.NewHoles numbers) {
        BlockPos center = colony.getCenter();
        int reach = foodReach(numbers);
        List<BlockPos> holes = new java.util.ArrayList<>();
        holes.add(colony.nest().getEntrance());
        for (NestPlan.ForagingTunnel tunnel : tunnels(level)) {
            if (!colony.nest().foodWays().isSpent(tunnel.hole())) {
                holes.add(tunnel.hole());
            }
        }
        List<BlockPos> dugFor = new java.util.ArrayList<>(colony.nest().foodWays().getNewTunnels().stream().map(FoodWays.NewTunnel::food).toList());
        // With the galleries, holes lie every few blocks, so most food lies near one: food near a hole waits for a
        // runway only while one may still come; else it gets a short gallery piece with its feeding gallery (the user's
        // bug, 2026-10-17: known hay 15 to 25 blocks out with the 3 runways in use got neither, and was only bitten).
        boolean galleriesOn = colony.nest().galleries().isOn();
        SpeciesProfile.Sheeting sheeting = colony.profile().nest().sheeting().orElse(null);
        List<BlockPos> runwayHoles = galleriesOn && sheeting != null
                && colony.nest().foodWays().getRunways().size() < sheeting.max() ? runwayHoles(level) : List.of();
        if (galleriesOn) {
            colony.nest().foodWays().getRunways().forEach(runway -> dugFor.add(runway.food()));
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos plant : plants) {
            if (!isRich(level, plant) || !colony.foodMap().knows(plant, null) || noRouteUntil.containsKey(plant)
                    || colony.danger().avoids(level, plant)) { // (no gallery is dug toward where they were attacked: brain step 4g)
                continue;
            }
            double fromNest = sideways(plant, center);
            if (fromNest > reach || fromNest >= bestDistance || nearAny(plant, dugFor, FOOD_STILL_THERE)) {
                continue;
            }
            if (galleriesOn) {
                boolean runwayMayCome = runwayHoles.size() > 1 && !noRunwayUntil.containsKey(plant)
                        && nearAny(plant, runwayHoles, numbers.minDistance());
                if (runwayMayCome || colony.food().isFeedableFromInside(level, plant)) {
                    continue;
                }
            } else if (nearAny(plant, holes, numbers.minDistance())) {
                continue;
            }
            best = plant;
            bestDistance = fromNest;
        }
        return best;
    }

    /** The holes a runway may leave from: dug, open for use, without a runway yet, loaded. */
    private List<BlockPos> runwayHoles(Level level) {
        List<BlockPos> free = new java.util.ArrayList<>();
        for (NestPlan.ForagingTunnel tunnel : tunnels(level)) {
            if (isDug(level, tunnel) && !colony.nest().foodWays().isSpent(tunnel.hole()) && colony.nest().foodWays().runwayAt(tunnel.hole()) == null
                    && level.isLoaded(tunnel.hole().above())) {
                free.add(tunnel.hole());
            }
        }
        return free;
    }

    /**
     * How far (blocks, sideways from the middle) food may lie to be dug out to or sheeted over: with the galleries on, as
     * far as their tips may grow (step 3: the reach grows with the web); else the reach for the colony's size.
     */
    private int foodReach(SpeciesProfile.NewHoles numbers) {
        Galleries galleries = colony.nest().galleries();
        return galleries.isOn() ? galleries.foodReach() : numbers.reach(colony.getCount(com.antcolonies.entity.WorkerAntEntity.CASTE));
    }

    /** The blocks of the dug foraging tunnels, where a new tunnel may start (the route planner picks the best). */
    private List<BlockPos> startsFor(Level level) {
        List<BlockPos> starts = new java.util.ArrayList<>();
        for (NestPlan.ForagingTunnel tunnel : tunnels(level)) {
            if (isDug(level, tunnel)) {
                starts.addAll(tunnel.tunnel());
            }
        }
        return starts;
    }

    /** The world as the tunnel route planner sees it: the ground, where a tunnel may be dug and a hole opened. */
    private TunnelRoute.Ground ground(Level level) {
        return new GroundReader(level, colony, colony.nest().nestCells(level));
    }

    /** Whether the plant at this spot is a rich one (its profile entry says {@code rich}). */
    private boolean isRich(Level level, BlockPos pos) {
        SpeciesProfile.PlantFood plant = colony.profile().food().plantFor(level.getBlockState(pos));
        return plant != null && plant.rich();
    }

    /** A rich plant block within {@code within} blocks of the spot (the spot itself first), looking at the blocks themselves, or null. */
    @Nullable
    private BlockPos richBlockNear(Level level, BlockPos spot, int within) {
        if (isRich(level, spot)) {
            return spot;
        }
        for (BlockPos pos : BlockPos.betweenClosed(spot.offset(-within, -within, -within), spot.offset(within, within, within))) {
            if (level.isLoaded(pos) && isRich(level, pos)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static boolean nearAny(BlockPos pos, List<BlockPos> others, double within) {
        for (BlockPos other : others) {
            if (sideways(pos, other) <= within) {
                return true;
            }
        }
        return false;
    }

    private static double sideways(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** For the colony list: " | new holes 2 (1 spent, 1 being dug), reach 24". Empty for species without new holes. */
    public String describeNewHoles(Level level) {
        if (colony.profile().nest().newHoles().isEmpty()) {
            return "";
        }
        int spent = 0;
        int digging = 0;
        List<FoodWays.NewTunnel> tunnels = colony.nest().foodWays().getNewTunnels();
        for (FoodWays.NewTunnel tunnel : tunnels) {
            if (colony.nest().foodWays().isSpent(tunnel.tunnel().hole())) {
                spent++;
            } else if (!isDug(level, tunnel.tunnel())) {
                digging++;
            }
        }
        return " | new holes " + tunnels.size() + " (" + spent + " spent, " + digging + " being dug), reach " + colony.foragingRadius();
    }

    /** Whether this cell is a way in or out of the nest (a foraging hole, or the entrance): not a place to stop in. */
    public boolean isDoorway(Level level, BlockPos cell) {
        return cell.equals(colony.nest().getEntrance()) || isHole(level, cell);
    }

    private static boolean touchesFluid(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!level.getFluidState(pos.relative(direction)).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
