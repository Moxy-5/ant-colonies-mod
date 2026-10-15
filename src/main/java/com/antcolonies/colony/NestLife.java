package com.antcolonies.colony;

import java.util.List;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.FungusCombBlock;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.TermiteQueenEntity;
import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * The brain, part 2, step 2b: life in the nest as numbers. While the colony is away everybody is a number (the queen is
 * frozen in her room), so the brain does what the real insects would: the nurses (the young workers among the numbers)
 * mill seeds into bread and feed the queen and the young, the queen lays, the eggs hatch, the larvae grow up into
 * workers or soldiers or starve, and the fungus grows nodules on the comb. The young that are numbers grow up the same
 * way while the colony is here too, until they come out into the nursery ({@link Brood#comeOut}). The numbers' own
 * meals are {@link Meals}.
 *
 * <p>Never reads or changes a block: the counts change at once, the blocks later, out of sight ({@link NestLedger}).
 * What the brain cannot read while away (where the queen sits, how swollen she is, what the garden looks like) is what
 * it last saw while the nest was loaded.
 *
 * <p>Species-neutral: the nurses' food is ant bread or fungus nodules ({@link NurseFood}); a species with a fungus
 * garden grows its own nurse food, one that mills bread makes it from its seeds.
 */
public class NestLife {
    /**
     * How long (ticks) one job takes a nurse: milling one seed into bread, or carrying one piece of food to the queen or
     * a larva, with the walk (half a minute; my pick, to confirm).
     */
    static final int NURSE_JOB_TICKS = 600;

    private final Colony colony;
    /** The nurses' work not done yet (jobs, a fraction of one carried over to the next second). Not saved. */
    private double nurseWork;
    /** Nodule growth not come yet (a fraction of one carried over). Not saved. */
    private double noduleGrowth;
    /** What the brain last saw of the queen while the nest was loaded: in her room, sealed in with the founders, how swollen. Saved. */
    private boolean queenHome;
    private boolean sealedIn;
    private float queenSize;

    NestLife(Colony colony) {
        this.colony = colony;
    }

    /** Once a second while the nest is loaded: what the brain will need to know about the queen while the colony is away. */
    void watch(ServerLevel level) {
        AbstractAntEntity queen = colony.getQueen(level);
        if (queen == null) {
            return; // (her part of the world is not loaded: keep what was seen last)
        }
        queenHome = colony.queenCare().isQueenHome(level);
        sealedIn = queenHome && !colony.nest().isEntranceOpen(level);
        queenSize = queen instanceof TermiteQueenEntity termiteQueen ? termiteQueen.getSize() : 0.0F;
    }

    /**
     * Once a second, here ({@code away} false, from {@link Colony#tick}) or away (from {@link AwayLife#tickAway}): the
     * young that are numbers grow up; while away also the queen, the nurses' work and the fungus. True if anything changed.
     */
    boolean tick(ServerLevel level, boolean away) {
        boolean changed = growUp(level);
        if (away) {
            changed |= colony.queenCare().liveAway(level, queenHome, sealedIn, queenSize);
            changed |= nurse(level);
            changed |= growNodules(level);
        } else {
            nurseWork = 0;
            changed |= feedYoungInside(level);
        }
        return changed;
    }

    // --- the young ----------------------------------------------------------------------------------------------

    /**
     * The young that are numbers, a second on: an egg's clock runs (one a worker was carrying is put in the nursery at
     * once and starts its clock), and it hatches into a larva (a termite colony short of soldiers makes it a nymph that
     * grows into one). A larva's hunger clock runs while it still has to eat: with the profile's larva starve time it dies
     * and leaves a body. A full one grows up into one of the numbers, a worker or the soldier it was growing into (not
     * past the colony's worker cap: it waits, and a termite nymph waiting then grows into a soldier if they are short).
     */
    private boolean growUp(ServerLevel level) {
        List<Brood.Young> young = colony.brood().young();
        if (young.isEmpty()) {
            return false;
        }
        SpeciesProfile.BroodNumbers numbers = colony.profile().brood();
        long now = level.getGameTime();
        boolean changed = false;
        for (int i = 0; i < young.size(); i++) {
            Brood.Young one = young.get(i);
            if (one.isEgg()) {
                long left = one.left() < 0 ? numbers.hatchTicks() : one.left() - 20;
                if (left <= 0) {
                    young.set(i, Brood.Young.larva(0, 0, colony.soldiers().rankForNewNymph(level)));
                } else {
                    young.set(i, Brood.Young.egg(left));
                }
                changed = true;
                continue;
            }
            TermiteSoldierEntity.Rank rank = one.rank();
            if (one.fed() < colony.soldiers().mealFor(rank)) {
                long hungry = colony.isHungerOn() ? one.left() + 20 : 0;
                if (hungry >= numbers.larvaStarveTicks()) {
                    young.remove(i--);
                    colony.ledger().addBodies(1);
                    AntColoniesMod.LOGGER.info("[nest] colony {}: a young one that is a number starved", colony.getId());
                } else {
                    young.set(i, Brood.Young.larva(one.fed(), hungry, rank));
                }
                changed = true;
                continue;
            }
            if (rank == null && colony.getCount(WorkerAntEntity.CASTE) >= colony.profile().colony().maxWorkers()) {
                TermiteSoldierEntity.Rank wanted = colony.soldiers().rankForNewNymph(level);
                if (wanted != null) {
                    young.set(i, Brood.Young.larva(one.fed(), 0, wanted)); // it eats the soldier's bigger meal first
                    changed = true;
                }
                continue;
            }
            young.remove(i--);
            colony.population().addNewMembers(rank == null ? WorkerAntEntity.CASTE : rank.caste(), now, 1);
            changed = true;
        }
        return changed;
    }

    /**
     * While the colony is here the real nurses look after the real young; the young that are numbers (waiting for a
     * free spot out of sight in the nursery) are fed by the nurses inside, the hungriest first, as fast as they work.
     */
    private boolean feedYoungInside(ServerLevel level) {
        if (colony.brood().insideCount() == 0) {
            return false;
        }
        boolean changed = false;
        nurseWork = Math.min(nurseWork + nursesInside(level) * 20.0 / NURSE_JOB_TICKS, Math.max(1, nursesInside(level)));
        while (nurseWork >= 1) {
            int larva = colony.brood().hungriestInside();
            if (larva < 0 || !takeNurseFood(level)) {
                break;
            }
            colony.brood().feedInside(larva);
            nurseWork--;
            changed = true;
        }
        return changed;
    }

    // --- the nurses, while away ---------------------------------------------------------------------------------

    /**
     * How many of the worker numbers are nurses: the young ones, at least as many as {@code nurses_from_workers} asks
     * for (the colony moves the next youngest over when it is short, as with the real workers, see
     * {@link Colony#shareOut}).
     */
    private int nursesInside(ServerLevel level) {
        int workers = colony.population().inside(WorkerAntEntity.CASTE);
        int[] ages = colony.population().insideByAge(level.getGameTime());
        return Colony.shareOut(workers, colony.profile().colony().nurses(workers), ages)[ColonyRole.NURSE.ordinal()];
    }

    /**
     * The nurses' work while away, one job each {@link #NURSE_JOB_TICKS}: first food for the queen when she wants some
     * (rich bread first), then the hungriest young, then milling a seed into bread while there is less than is wanted
     * (a rich seed when the queen wants rich bread).
     */
    private boolean nurse(ServerLevel level) {
        int nurses = nursesInside(level);
        nurseWork = Math.min(nurseWork + nurses * 20.0 / NURSE_JOB_TICKS, Math.max(1, nurses));
        boolean changed = false;
        QueenCare queen = colony.queenCare();
        Bakery bakery = colony.bakery();
        while (nurseWork >= 1) {
            if (queen.wantsFoodAway(level, queenHome) && nurseFoodLeft(level) > 0) {
                queen.feedQueen(level, takePiece(true));
            } else if (colony.brood().hungriestInside() >= 0 && nurseFoodLeft(level) > 0) {
                int larva = colony.brood().hungriestInside();
                takePiece(false);
                colony.brood().feedInside(larva);
            } else if (bakery.needsBreadAway(level) && colony.food().getFood() > 0) {
                boolean rich = colony.food().takeCounted(bakery.wantsRichBread());
                bakery.countIn(rich);
                colony.ledger().addNurseFood(1);
            } else {
                break;
            }
            nurseWork--;
            changed = true;
        }
        return changed;
    }

    // --- the nurses' food ---------------------------------------------------------------------------------------

    /**
     * How many pieces of the nurses' food there are as far as the brain knows: the bread counted; or the nodules on the
     * comb (in the world while the nest is loaded, as last seen while away) and what the ledger still owes the world.
     */
    int nurseFoodLeft(ServerLevel level) {
        if (colony.profile().food().makesBread()) {
            return colony.bakery().getBread();
        }
        if (!colony.garden().exists()) {
            return 0;
        }
        int nodules = colony.away().isHere(level) ? colony.garden().available(level) : colony.garden().seenNodules();
        return Math.max(0, nodules + colony.ledger().nurseFood());
    }

    /**
     * One of the numbers takes a piece of the nurses' food (for itself or a young one). Its block comes off later, out of
     * sight. False if there was none.
     */
    boolean takeNurseFood(ServerLevel level) {
        if (nurseFoodLeft(level) <= 0) {
            return false;
        }
        takePiece(false);
        return true;
    }

    /** Takes a piece the brain knows is there; returns whether it was rich bread ({@code wantRich}: rich first, for the queen). */
    private boolean takePiece(boolean wantRich) {
        boolean rich = colony.profile().food().makesBread() && colony.bakery().takeCounted(wantRich);
        colony.ledger().addNurseFood(-1);
        return rich;
    }

    // --- the fungus garden, while away --------------------------------------------------------------------------

    /**
     * The fungus grows nodules on the comb while away, as in {@link FungusGarden#grow}: each comb block with a layer over
     * its bed and room for another nodule grows one about every {@code nodule_ticks}, using up that layer (the garden as
     * last seen, with what is owed to it since).
     */
    private boolean growNodules(ServerLevel level) {
        SpeciesProfile.GardenNumbers numbers = colony.profile().garden().orElse(null);
        FungusGarden garden = colony.garden();
        if (numbers == null || garden.seenCombs() == 0) {
            return false;
        }
        NestLedger ledger = colony.ledger();
        int spare = garden.seenSpareLayers() + ledger.store();
        int room = garden.seenCombs() * FungusCombBlock.MAX_NODULES - (garden.seenNodules() + ledger.nurseFood());
        int canGrow = Math.min(garden.seenCombs(), Math.min(spare, room));
        if (canGrow <= 0) {
            noduleGrowth = 0;
            return false;
        }
        noduleGrowth += canGrow * FungusGarden.chancePerSecond(numbers);
        boolean changed = false;
        while (noduleGrowth >= 1 && canGrow-- > 0) {
            noduleGrowth--;
            ledger.addStore(-1);
            ledger.addNurseFood(1);
            colony.food().used(1);
            changed = true;
        }
        return changed;
    }

    void save(CompoundTag tag) {
        CompoundTag life = new CompoundTag();
        life.putBoolean("queenHome", queenHome);
        life.putBoolean("sealedIn", sealedIn);
        life.putFloat("queenSize", queenSize);
        tag.put("nestLife", life);
    }

    void load(CompoundTag tag) {
        CompoundTag life = tag.getCompound("nestLife");
        queenHome = !tag.contains("nestLife") || life.getBoolean("queenHome"); // (older saves: she sat in her room)
        sealedIn = life.getBoolean("sealedIn");
        queenSize = life.getFloat("queenSize");
    }
}
