package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.WorkerAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The brain, part 2, step 2d: the builders among the numbers. The user's decision (2026-10-14): building while away is
 * applied when the player returns. While the colony is away its land is not loaded, so no block can change: the brain
 * only counts the builders' working time ({@link #tickAway}: every builder-age number works every second). When the
 * nest is loaded again, that time is spent ({@link #catchUp}), out of every player's sight, on exactly the work the real
 * builders would have done, in their order and by their rules: the nest's plan dug (the shaft, the rooms the colony may
 * dig now, the side rooms, the gardens, the foraging tunnels and the feeding galleries; walls of mud first where the
 * species plasters them), the soil heaped on the ant hill, and the termite mound built up stage by stage (soil from the
 * galleries under it, its pieces, its tunnels carved). What was built while away has had time to dry: a mound block
 * goes in finished and set.
 *
 * <p><b>How much.</b> The builders work at the real workers' pace: a bite of soil every {@link #BITE_TICKS}, a pellet
 * carried {@link #TICKS_PER_BLOCK} a block there and back (as the forager numbers walk), a mound block shaped in
 * {@link #SHAPE_TICKS}. The plan itself still sets how far they may go (the nest's stages by the colony's size, the
 * mound's stage by what the colony has earned), so time left over once nothing more may be built is dropped: the
 * builders had nothing to do.
 *
 * <p>Species-neutral: any species with a nest plan; the ant hill for species without a mound, the mound for those with one.
 */
public class BuildLife {
    /** How long (ticks) a bite of soil takes (as {@code DigGoal}). */
    static final int BITE_TICKS = 10;
    /** How long (ticks) a builder takes for each block of the way with a pellet, there or back (the forager numbers' pace). */
    static final int TICKS_PER_BLOCK = 8;
    /** How long (ticks) shaping a block of the mound takes (carving a tunnel, reshaping a piece; as {@code ShapeMoundGoal}). */
    static final int SHAPE_TICKS = 30;
    /** At most this many blocks change a second (a long time away can owe a lot; the rest comes the next second). */
    private static final int PER_SECOND = 200;
    /** At most this many times a second the whole mound plan is looked over. */
    private static final int MOUND_PASSES = 8;

    private final Colony colony;
    /** The builders' working time (ticks of one builder's work) from while away, not spent yet. Saved. */
    private long work;
    /** Pellets of soil dug and not built into the mound yet (termites). Saved. */
    private int soil;
    /** Whether the last second's catch-up waited for something (a player looking, an insect in the way). Not saved. */
    private boolean waited;
    /** This second's catch-up: the players' eyes and how many blocks changed so far. Not saved. */
    private List<Vec3> eyes = List.of();
    private int changes;

    BuildLife(Colony colony) {
        this.colony = colony;
    }

    /** Whether there is building time from while away still to spend. */
    public boolean hasWork() {
        return work > 0;
    }

    /** The builders' time still to spend (ticks of one builder's work), for tests and the colony list. */
    public long work() {
        return work;
    }

    /** How many of the worker numbers are builders: the middle-aged ones (the colony shares out its jobs, see {@link Colony#shareOut}). */
    public int buildersInside(ServerLevel level) {
        int workers = colony.population().inside(WorkerAntEntity.CASTE);
        if (workers <= 0) {
            return 0;
        }
        int[] ages = colony.population().insideByAge(level.getGameTime());
        return Colony.shareOut(workers, colony.profile().colony().nurses(workers), ages)[ColonyRole.BUILDER.ordinal()];
    }

    /** Once a second while away (from {@link AwayLife#tickAway}): every builder number works the second. Never touches the world. */
    void tickAway(ServerLevel level) {
        if (colony.isFounding()) {
            // The founders (the queen alone, or the royal pair) dig their own chamber at the real pace (the user's bug,
            // 2026-10-15: a founding nest dug nothing while away).
            if (colony.life().foundersAtWork()) {
                work += colony.life().founders() * 20L;
            }
            return;
        }
        int builders = buildersInside(level);
        if (builders > 0) {
            work += builders * 20L;
        }
    }

    // --- coming back ----------------------------------------------------------------------------------------------

    /**
     * Once a second while the whole nest is loaded and there is building time left (from {@link AwayLife#settle}): the
     * work is done, out of every player's sight ({@code eyes}). True if any block changed. Once nothing more may be
     * built (or dug) the time left over is dropped.
     */
    boolean catchUp(ServerLevel level, List<Vec3> eyes) {
        boolean founders = colony.life().foundedAway();
        if (work <= 0 && !founders) {
            return false;
        }
        waited = false;
        this.eyes = eyes;
        changes = 0;
        List<ColonyHooks.AwayBuilder> builders = colony.hooks().all(ColonyHooks.AWAY_BUILDER);
        boolean nestLeft = work > 0 && (firstWork(level, builders) || digNest(level));
        boolean moundLeft = work > 0 && afterWork(level, builders); // (the termite mound built up)
        if (!nestLeft && founders) {
            settleFounders(level);
        }
        // Termites underground step 4 (the user's pick, 2026-10-17): the time left once the nest and the mound are done
        // grows the galleries, as the real builders never stop.
        boolean galleriesLeft = work > 0 && !nestLeft && !moundLeft && !waited && !colony.isFounding() && growGalleries(level);
        if (!nestLeft && !moundLeft && !galleriesLeft && !waited) {
            if (work > 0) {
                log("is done");
            }
            work = 0; // nothing more to do: the builders had time to spare
            soil = 0; // (soil nobody needed was left in the galleries)
        }
        return changes > 0;
    }

    /** The species' work before the nest is dug further (the harvester's night soil carried up first). True if some still waits. */
    private boolean firstWork(ServerLevel level, List<ColonyHooks.AwayBuilder> builders) {
        for (ColonyHooks.AwayBuilder builder : builders) {
            if (builder.first(level, this)) {
                return true;
            }
        }
        return false;
    }

    /** The species' work once the nest is dug as far as it may be (the termite mound built up). True if some is left. */
    private boolean afterWork(ServerLevel level, List<ColonyHooks.AwayBuilder> builders) {
        for (ColonyHooks.AwayBuilder builder : builders) {
            if (builder.after(level, this)) {
                return true;
            }
        }
        return false;
    }

    private void log(String what) {
        AntColoniesMod.LOGGER.info("[away-build] colony {} {}: {} ticks of work left over, {} soil", colony.getId(), what, work, soil);
    }

    /** The nest's plan, dug in the real diggers' order. True if there is more to dig (time ran out, or it waits). */
    private boolean digNest(ServerLevel level) {
        List<ColonyHooks.AwayBuilder> builders = colony.hooks().all(ColonyHooks.AWAY_BUILDER);
        while (work > 0) {
            if (changes >= PER_SECOND) {
                return true;
            }
            BlockPos target = colony.nest().findPlannedDigTarget(level);
            if (target == null) {
                return false; // dug as far as the colony may dig now
            }
            if (!Population.outOfSight(level, target, eyes)) {
                waited = true;
                return true;
            }
            if (target.equals(colony.getCenter()) && colony.nest().isPlantInEntrance(level)) {
                colony.nest().clearPlantInEntrance(level, null); // (a plant in the entrance: bitten off, no soil)
                changes++;
                work -= BITE_TICKS;
                continue;
            }
            // Where the soil goes is the species' (ants: onto the hill, its spot looked at before the block is dug; termites:
            // into the mound's soil).
            for (ColonyHooks.AwayBuilder builder : builders) {
                if (!builder.readyToDig(level, this, target)) {
                    waited = true;
                    return true;
                }
            }
            if (!colony.walls().wallFirst(level, target)) {
                waited = true; // a plate of mud could not go in yet
                return true;
            }
            int bites = bites(level, target);
            dig(level, target);
            changes++;
            work -= (long) bites * BITE_TICKS;
            colony.away().report().add(AwayReport.What.DUG, 1);
            for (ColonyHooks.AwayBuilder builder : builders) {
                builder.dug(level, this, target, bites);
            }
        }
        return true;
    }

    /** At most this many gallery pieces are planned a second on return (each is dug before the next is planned). */
    private static final int PIECES_PER_SECOND = 4;

    /**
     * Termites underground step 4: the galleries grow out of sight with the builders' time, a piece planned by the same
     * rules as here ({@link Galleries#growNow}: food first, then the even spreading, within the workers' budget and the
     * reach) and dug before the next. True if there is more (time ran out this second, or it waits); false once nothing
     * more may grow now (the budget is spent, or the ground out there is not loaded: the real workers grow it later).
     */
    private boolean growGalleries(ServerLevel level) {
        Galleries galleries = colony.nest().galleries();
        if (!galleries.isOn()) {
            return false;
        }
        for (int i = 0; i < PIECES_PER_SECOND; i++) {
            if (work <= 0 || changes >= PER_SECOND) {
                return true;
            }
            int[] grown = galleries.growNow(level);
            if (grown[0] == 0) {
                return false;
            }
            AwayReport report = colony.away().report();
            report.add(AwayReport.What.GALLERY_PIECES, grown[0]);
            report.add(AwayReport.What.GALLERY_TO_FOOD, grown[1]);
            report.add(AwayReport.What.GALLERY_BLOCKS, grown[2]);
            if (digNest(level)) {
                return true; // (time ran out, or a player can see where it goes)
            }
        }
        return true;
    }

    /** How many bites a block takes (as {@code DigGoal}: plain dirt about 6). */
    static int bites(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return Math.max(1, (int) Math.ceil(state.getDestroySpeed(level, pos) * 12.0F));
    }

    /**
     * The founders finished their chamber and closed themselves in while away (see {@link NestLife}): now, out of every
     * player's sight, what is left of their digging is done (the brain counted it dug), the queen (and the king) are
     * put in their chamber, and, while they are still alone, the entrance is packed shut behind them. Sets
     * {@link #waited} if a player can see it or something is in the way.
     */
    private void settleFounders(ServerLevel level) {
        Nest nest = colony.nest();
        List<BlockPos> left = nest.foundersDigLeft(level);
        if (left == null) {
            waited = true;
            return;
        }
        for (BlockPos pos : left) {
            if (!Population.outOfSight(level, pos, eyes)) {
                waited = true;
                return;
            }
            if (pos.equals(colony.getCenter()) && nest.isPlantInEntrance(level)) {
                nest.clearPlantInEntrance(level, null);
            } else if (colony.walls().wallFirst(level, pos)) {
                dig(level, pos);
                colony.away().report().add(AwayReport.What.DUG, 1);
            } else {
                waited = true;
                return;
            }
            changes++;
        }
        NestPlan.Chamber home = nest.getHomeChamber(level);
        if (home == null) {
            waited = true;
            return;
        }
        BlockPos middle = home.center();
        Vec3 spot = Vec3.atBottomCenterOf(middle);
        List<LivingEntity> founders = new ArrayList<>();
        LivingEntity queen = colony.getQueen(level);
        if (queen != null) {
            founders.add(queen);
        }
        founders.addAll(level.getEntitiesOfClass(com.antcolonies.entity.TermiteKingEntity.class, colony.nestArea().inflate(16.0),
                king -> king.isAlive() && colony.getId().equals(king.getColonyId())));
        for (int i = 0; i < founders.size(); i++) {
            LivingEntity founder = founders.get(i);
            Vec3 to = spot.add(i == 0 ? 0.0 : 0.6, 0.0, 0.0); // (the king at her side)
            if (founder.position().distanceToSqr(to) < 0.25) {
                continue;
            }
            if (!Population.outOfSight(level, founder.getEyePosition(), eyes) || !Population.outOfSight(level, middle, eyes)) {
                waited = true;
                return;
            }
            if (founder instanceof com.antcolonies.entity.AbstractAntEntity ant) {
                ant.getNavigator().stop();
            }
            founder.moveTo(to.x, to.y, to.z, founder.getYRot(), 0.0F);
            changes++;
        }
        BlockPos hole = nest.getEntrance();
        if (colony.isFounding() && colony.profile().nest().sealWhileFounding() && nest.isEntranceOpen(level)) {
            if (!freeAndHidden(level, hole)) {
                waited = true;
                return;
            }
            level.setBlock(hole, colony.profile().nest().plugState(), 3);
            changes++;
        }
        colony.life().foundersSettled();
        AntColoniesMod.LOGGER.info("[away-build] colony {}: the founders are in their chamber at {}{}", colony.getId(), middle.toShortString(),
                colony.isFounding() ? ", the hole shut" : "");
    }

    // --- for the species' own building on return (ColonyHooks.AwayBuilder: the hill, the mound) -------------------------

    /** The colony building. */
    Colony colony() {
        return colony;
    }

    /** Whether there is building time left to spend. */
    boolean timeLeft() {
        return work > 0;
    }

    /** Whether as many blocks have changed this second as may (the rest comes the next second). */
    boolean secondFull() {
        return changes >= PER_SECOND;
    }

    /** How many blocks have changed this second. */
    int changesNow() {
        return changes;
    }

    /** One more block changed this second. */
    void changed() {
        changes++;
    }

    /** {@code ticks} of the builders' time spent. */
    void spend(long ticks) {
        work -= ticks;
    }

    /** Whether a player can see {@code pos}: if so the catch-up waits (it is done out of every player's sight). */
    boolean seen(ServerLevel level, BlockPos pos) {
        if (!Population.outOfSight(level, pos, eyes)) {
            waited = true;
            return true;
        }
        return false;
    }

    /** Whether this second's catch-up has waited for something (a player looking, an insect in the way). */
    boolean waited() {
        return waited;
    }

    /** The catch-up waits this second (for something only the species' code saw). */
    void waits() {
        waited = true;
    }

    /** Pellets of soil dug and not built in yet (termites: for the mound). */
    int soil() {
        return soil;
    }

    /** {@code pellets} of soil more (negative: used). */
    void addSoil(int pellets) {
        soil += pellets;
    }

    /** The block is dug out, quietly (nobody is there to hear it). */
    void dig(ServerLevel level, BlockPos pos) {
        colony.nest().clearDigProgress(pos);
        colony.news().tell(level, ColonyNews.BLOCK_DUG, new ColonyNews.BlockDug(pos)); // (the mound counts its soil)
        level.removeBlock(pos, false);
    }

    /** The time (ticks) a pellet takes from where it was dug to where it goes, and the builder back. */
    static long trip(BlockPos from, BlockPos to) {
        return 2L * TICKS_PER_BLOCK * from.distManhattan(to);
    }

    /** Whether nobody stands in this cell (nobody is ever buried, docs/BUGS.md 9 and 6g) and no player can see it. */
    boolean freeAndHidden(ServerLevel level, BlockPos pos) {
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty()
                || !Population.outOfSight(level, pos, eyes)) {
            waited = true;
            return false;
        }
        return true;
    }

    /** For the colony list: "builders' work owed: 2.5 builder-hours" (empty if none). */
    public String describe() {
        if (work <= 0) {
            return "";
        }
        return String.format(java.util.Locale.ROOT, "%.1f builder-hours of building", work / 1000.0);
    }

    // --- tests ----------------------------------------------------------------------------------------------------

    /** Tests only: the builders' time to spend, as if they had worked that long while away. */
    public void setWorkForTests(long ticks) {
        work = ticks;
    }

    // --- saving ---------------------------------------------------------------------------------------------------

    void save(CompoundTag tag) {
        CompoundTag building = new CompoundTag();
        building.putLong("work", work);
        building.putInt("soil", soil);
        tag.put("awayBuilding", building);
    }

    void load(CompoundTag tag) {
        CompoundTag building = tag.getCompound("awayBuilding"); // (an older save: none, nothing owed)
        work = building.getLong("work");
        soil = building.getInt("soil");
    }
}
