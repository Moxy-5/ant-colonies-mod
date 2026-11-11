package com.antcolonies.savanna.colony;

import com.antcolonies.colony.LandReady;
import com.antcolonies.colony.GroundTop;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import com.antcolonies.gametest.TestIsolation;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyHooks;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.registry.ModTags;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.savanna.entity.TermiteKingEntity;
import com.antcolonies.savanna.entity.TermiteQueenEntity;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.species.SavannaNumbers;
import com.antcolonies.savanna.species.SavannaNumbers.MoundNumbers;
import com.antcolonies.species.SpeciesProfiles;
import com.antcolonies.savanna.tree.Forest;
import com.antcolonies.savanna.tree.TreeStage;
import com.antcolonies.savanna.worldgen.MoundPlaces;
import com.antcolonies.savanna.worldgen.TermiteMoundPiece;
import com.antcolonies.savanna.worldgen.TermiteMoundStructure;
import com.antcolonies.savanna.worldgen.TermiteMoundStructure.Pick;
import com.antcolonies.savanna.worldgen.TreePlacer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * The termite mounds of new land (M8 step 1b, {@code docs/DESIGN.md}, "World generation, details"): world generation
 * picks a mound's place and what stands there ({@link TermiteMoundStructure}); this book puts it in on the server once
 * the land round it is loaded, with the termites' own code, so a mound of new land is a mound like any other.
 *
 * <p><b>A living colony</b> is a colony that has lived there for its age: the queen (swollen by its size) and the king
 * in the royal chamber, workers of every age and the soldiers so many workers keep in its numbers, the nest dug, the
 * gardens full of comb, the mound standing at its stage. Its galleries are left to its builders' time, spent out of
 * sight by the usual catch-up ({@code BuildLife}), as if the player had been away.
 *
 * <p><b>A dead mound</b> is a colony of the size it had, ended at once ({@code COLONY_ENDED}: the step 1a dead mound
 * book writes it down), its clock set back by the days it has been dead, and the wear brought up to that day. On some
 * old humps trees or a thicket grow, an aardvark has dug in, and the grass is greener (my picks, to confirm).
 *
 * <p>Places waiting for their land are saved; at most one mound is put in each look ({@link #LOOK_TICKS}).
 */
public class NewLandMounds extends SavedData {
    private static final String DATA_NAME = SavannaMod.MODID + "_new_land_mounds";
    /** How often (ticks) the book looks for a place whose land is loaded. */
    static final int LOOK_TICKS = 10;
    /**
     * The land this far round a place (blocks) must be loaded before its mound goes in: a full mound and the soil washed
     * round it reach about 13, its nest's rooms about as far (was 64, the dead tree's land too, so a mound waited for 9 by
     * 9 chunks: docs/plans/savanna-loading.md, step 3).
     */
    static final int LOADED_ROUND = 24;
    /**
     * The land this far round a mound (blocks) must be loaded before its dead tree goes in: its foot up to 30 past the
     * mound's foot and the tree's crown or log about 20 more (my pick). The tree goes in on its own, after the mound.
     */
    static final int TREE_ROUND = 64;
    /** No mound goes in this near (blocks) another colony or dead mound (a player's own colony, say). */
    public static final int KEEP_APART = 40;

    /** Places handed over by world generation (any thread), taken into the book on the next look. */
    private static final ConcurrentLinkedQueue<Queued> QUEUE = new ConcurrentLinkedQueue<>();

    private record Queued(ResourceKey<Level> dimension, Site site) {
    }

    /**
     * A mound's place: its middle (world generation's guess of the ground), what stands there, and the id its colony
     * will have (picked beforehand, so its mound's shape can be grown in the background while it waits).
     */
    public record Site(BlockPos middle, Pick pick, UUID colony) {
    }

    private final List<Site> waiting = new ArrayList<>();
    /** Mounds put in whose dead tree waits for its land ({@link #TREE_ROUND}): the mound's ground in the middle, what stands there, its colony. */
    private final List<Site> treesWaiting = new ArrayList<>();
    /** The game tests: where the last old hump's burrow mouth and trees went (not saved). */
    private BlockPos lastBurrow;
    private double lastMillis;
    private final List<BlockPos> lastTrees = new ArrayList<>();
    /** The game tests: the foot of the last mound's dead tree (null if it found no room), and a box its low parts must stay inside. */
    private BlockPos lastDeadTree, lastDeadWood;
    /** The game tests: how many pieces of rich food the last living colony learnt round its mound (F3). */
    private int learntWood;
    /** The game tests: how far (blocks) the last mound's foot reached from its middle, as its dead tree was placed. */
    private int lastDeadTreeReach;
    private static BoundingBox fence;
    private int living, dead, skipped, ants;
    /** The Matabele colony the last dead mound got (null if none; not saved). */
    private Colony lastAnts;

    public static NewLandMounds get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(NewLandMounds::new, NewLandMounds::load, null), DATA_NAME);
    }

    /** World generation made a mound's chunk (any thread): its place goes into the book on the next look. */
    public static void queue(ServerLevel level, BlockPos middle, Pick pick) {
        UUID colony = new UUID(pick.seed() * 0x9E3779B97F4A7C15L ^ middle.asLong(), middle.asLong() * 0xC2B2AE3D27D4EB4FL ^ pick.seed());
        QUEUE.add(new Queued(level.dimension(), new Site(middle.immutable(), pick, colony)));
    }

    /**
     * Every {@link #LOOK_TICKS}: new places into the book; the first whose land is loaded and whose mound's shape is
     * grown gets its mound (the shapes of at most {@link #GROWING_AT_ONCE} are grown in the background meanwhile).
     */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % LOOK_TICKS != 0) {
            return;
        }
        NewLandMounds book = null;
        for (Queued q : QUEUE) {
            if (q.dimension().equals(level.dimension()) && QUEUE.remove(q)) {
                book = book == null ? get(level) : book;
                book.waiting.add(q.site());
                book.setDirty();
            }
        }
        book = book == null ? get(level) : book;
        MoundNumbers numbers = SpeciesProfiles.get(SavannaSpecies.MACROTERMES).section(SavannaNumbers.MOUND).orElse(null);
        int growing = 0;
        for (int i = 0; i < book.waiting.size() && growing < GROWING_AT_ONCE; i++) {
            Site site = book.waiting.get(i);
            BlockPos m = site.middle();
            if (!loadedRound(level, m, LOADED_ROUND)) {
                continue;
            }
            deadTreeShapesSoon(site.pick().seed()); // (its dead tree's shapes too, ready by the time its land is)
            if (numbers != null && !MoundPlan.grownSoon(numbers, moundSeed(site.colony()))) {
                growing++;
                continue; // its shape is being grown: it waits
            }
            book.waiting.remove(i);
            book.setDirty();
            book.putWithAnts(level, m, site.pick(), site.colony(), false, false);
            return; // one a look: each is a few thousand blocks
        }
        for (int i = 0; i < book.treesWaiting.size(); i++) {
            Site site = book.treesWaiting.get(i);
            if (!loadedRound(level, site.middle(), TREE_ROUND) || !deadTreeShapesSoon(site.pick().seed())) {
                continue;
            }
            book.treesWaiting.remove(i);
            book.setDirty();
            book.treeBy(level, site.middle(), site.pick(), ColonyManager.get(level).getColony(site.colony()));
            return;
        }
    }

    /**
     * The soak (BUGS 139): how many places within {@code radius} blocks of {@code site} wait to be put in though their
     * land is loaded (their shapes still growing), counting places world generation queued and not looked at yet.
     */
    public static int stillWaiting(ServerLevel level, BlockPos site, int radius) {
        int count = 0;
        for (Queued q : QUEUE) {
            if (q.dimension().equals(level.dimension()) && near(q.site().middle(), site, radius)) {
                count++;
            }
        }
        for (Site s : get(level).waiting) {
            if (near(s.middle(), site, radius) && loadedRound(level, s.middle(), LOADED_ROUND)) {
                count++;
            }
        }
        return count;
    }

    private static boolean near(BlockPos a, BlockPos b, int radius) {
        return Math.abs(a.getX() - b.getX()) <= radius && Math.abs(a.getZ() - b.getZ()) <= radius;
    }

    private static boolean loadedRound(ServerLevel level, BlockPos middle, int round) {
        return LandReady.round(level, middle, round);
    }

    /** How many places' mound shapes are grown in the background at once (each about a second of one thread's work). */
    static final int GROWING_AT_ONCE = 2;

    /** The seed a colony's mound is shaped by ({@code Mound}'s own). */
    static long moundSeed(UUID colony) {
        return colony.getMostSignificantBits() ^ colony.getLeastSignificantBits();
    }

    /**
     * Puts the mound of a place in now (the command, the tests, and {@link #tick} once its land is loaded). Returns the
     * colony made (for a dead mound already ended: its id is the dead mound's), or null if the place did not suit (water,
     * no soil, another colony too near).
     */
    public Colony put(ServerLevel level, BlockPos middle, Pick pick) {
        return put(level, middle, pick, UUID.randomUUID());
    }

    /** As above, the colony made with this id (if one has it already, nothing is put in). */
    public Colony put(ServerLevel level, BlockPos middle, Pick pick, UUID colonyId) {
        return put(level, middle, pick, colonyId, true);
    }

    /**
     * As above; its dead tree goes in now ({@code treeNow}: the command, the tests) or once its land is loaded
     * ({@link #tick}, {@link #TREE_ROUND}).
     */
    private Colony put(ServerLevel level, BlockPos middle, Pick pick, UUID colonyId, boolean treeNow) {
        int x = middle.getX(), z = middle.getZ();
        BlockPos center = GroundTop.cell(level, x, z);
        BlockState ground = level.getBlockState(center.below());
        String why = !level.getFluidState(center.below()).isEmpty() || !level.getFluidState(center).isEmpty() ? "water"
                : !ground.is(ModTags.DIGGABLE) ? "no soil (" + ground.getBlock().getName().getString() + ")"
                : crowded(level, center) ? "another colony or dead mound within " + KEEP_APART
                : ColonyManager.get(level).getColony(colonyId) != null || DeadMounds.get(level).get(colonyId) != null ? "already put in" : null;
        if (why != null) {
            skipped++;
            setDirty();
            SavannaMod.LOGGER.info("[new-land] no termite mound at {}: {}", center.toShortString(), why);
            return null;
        }
        long start = System.nanoTime();
        Colony colony = pick.living() ? putLiving(level, center, pick, colonyId) : putDead(level, center, pick, colonyId);
        if (colony != null) {
            if (treeNow) {
                treeBy(level, center, pick, colony);
            } else {
                treesWaiting.add(new Site(center, pick, colony.getId()));
            }
            if (pick.living()) {
                living++;
            } else {
                dead++;
            }
            setDirty();
            lastMillis = (System.nanoTime() - start) / 1.0e6;
            SavannaMod.LOGGER.info("[new-land] {} at {} in {} ms", describe(pick), center.toShortString(),
                    String.format(Locale.ROOT, "%.1f", lastMillis));
        }
        return colony;
    }

    /**
     * As {@link #put}, and a dead mound gets its Matabele colony by the dice ({@link MatabeleColonies#inNewLand}; with
     * {@code alwaysAnts}, the command's, always): new land's mounds, M8 step 1f.
     */
    public Colony putWithAnts(ServerLevel level, BlockPos middle, Pick pick, UUID colonyId, boolean alwaysAnts) {
        return putWithAnts(level, middle, pick, colonyId, alwaysAnts, true);
    }

    private Colony putWithAnts(ServerLevel level, BlockPos middle, Pick pick, UUID colonyId, boolean alwaysAnts, boolean treeNow) {
        Colony colony = put(level, middle, pick, colonyId, treeNow);
        lastAnts = null;
        if (colony != null && !pick.living()) {
            // M8 step 3F, F4: a lonely dead mound, no living termites within a raid's reach, stays empty (the command's always not asked)
            if (!alwaysAnts && MatabeleColonies.wouldSettle(level, pick.seed()) && !livingMoundNear(level, colony.getCenter())) {
                SavannaMod.LOGGER.info("[new-land] no Matabele colony in the dead mound at {}: no living termite mound within {} blocks",
                        colony.getCenter().toShortString(), antsNearLiving(level));
                return colony;
            }
            lastAnts = MatabeleColonies.inNewLand(level, colony.getCenter(), pick.seed(), alwaysAnts);
            if (lastAnts != null) {
                ants++;
                setDirty();
            }
        }
        return colony;
    }

    /**
     * M8 step 3F, F4: a dead mound of new land holds a Matabele colony only with a living termite mound's foot within this
     * many blocks (the mound numbers' {@code matabele.living_near}, 100 since 2026-11-07, 90 since the commonness plan, 2026-11-06, was 70; real
     * Matabele nests lie among the termite mounds they raid and forage out to about 95 m, Yusuf 2026; a colony with no
     * termites in reach would starve). One number for world making and this check ({@link TermiteMoundStructure.Matabele}).
     */
    public static int antsNearLiving(ServerLevel level) {
        return TermiteMoundStructure.numbersOf(level.registryAccess()).matabele().livingNear();
    }

    /**
     * Whether a living termite mound's foot lies within {@link #antsNearLiving} blocks of {@code center}: a living
     * Macrotermes colony there now, or a living mound new land will put at a place of the pattern
     * ({@link MoundPlaces}), made yet or not (the same answer world generation gives, or a kinder one: this measures to
     * the living mound's edge, world making middle to middle).
     */
    public static boolean livingMoundNear(ServerLevel level, BlockPos center) {
        int reach = antsNearLiving(level);
        for (Colony other : ColonyManager.get(level).getColonies()) {
            if (other.getSpecies().equals(SavannaSpecies.MACROTERMES)) {
                int stage = other.part(SavannaParts.MOUND).map(mound -> mound.getStage(level)).orElse(0);
                if (sideways(other.getCenter(), center) - MoundPlaces.footReach(stage) <= reach) {
                    return true;
                }
            }
        }
        if (!(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .get(MoundPlaces.STRUCTURE) instanceof TermiteMoundStructure mounds)) {
            return false;
        }
        return livingPlaceNear(MoundPlaces.Land.of(level, mounds.biomes()), mounds.numbers(), center);
    }

    /**
     * Whether new land puts a living mound whose foot lies within the numbers' {@code matabele.living_near} blocks of
     * {@code center}, by the place pattern and the ground alone (made yet or not): the half of {@link #livingMoundNear}
     * world generation answers, also asked of land no world holds (the survey game test, BUGS 104).
     */
    public static boolean livingPlaceNear(MoundPlaces.Land land, TermiteMoundStructure.Numbers numbers, BlockPos center) {
        long seed = land.seed();
        MoundPlaces.Numbers places = numbers.places();
        int reach = numbers.matabele().livingNear();
        int look = reach + (int) Math.ceil(MoundPlaces.footReach(TermiteMoundStructure.FULL_STAGE));
        for (MoundPlaces.Place place : MoundPlaces.near(seed, center.getX(), center.getZ(), look, places)) {
            BlockPos foot = MoundPlaces.holds(land, place, places);
            if (foot == null) {
                continue;
            }
            Pick pick = TermiteMoundStructure.pickAt(numbers, land, place, MoundPlaces.sizeQuantile(land, place, places));
            if (pick.living() && sideways(foot, center) - MoundPlaces.footReach(pick.stage()) <= reach) {
                return true;
            }
        }
        return false;
    }

    private static double sideways(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean crowded(ServerLevel level, BlockPos center) {
        double apart = (double) KEEP_APART * KEEP_APART;
        for (Colony other : ColonyManager.get(level).getColonies()) {
            if (other.getCenter().distSqr(center) < apart && !TestIsolation.inDifferentTests(other.getCenter(), center)) {
                return true;
            }
        }
        for (DeadMounds.DeadMound other : DeadMounds.get(level).all()) {
            if (other.center.distSqr(center) < apart && !TestIsolation.inDifferentTests(other.center, center)) {
                return true;
            }
        }
        return false;
    }

    /** The pick in words, for the log and the command. */
    public static String describe(Pick pick) {
        String size = switch (pick.size()) {
            case TermiteMoundStructure.FULL -> "full";
            case TermiteMoundStructure.HALF -> "half-grown";
            default -> "young";
        };
        if (pick.living()) {
            return String.format(Locale.ROOT, "living %s termite colony (stage %d, %.0f days old)", size, pick.stage(), pick.days());
        }
        String woods = switch (pick.woods()) {
            case TermiteMoundStructure.THICKET -> ", a thicket on it";
            case TermiteMoundStructure.GROWN_TREE -> ", a tree on it";
            case TermiteMoundStructure.YOUNG_BAOBAB -> ", a young baobab on it";
            default -> "";
        };
        return String.format(Locale.ROOT, "dead %s termite mound (stage %d, %.1f days dead%s%s)", size, pick.stage(), pick.days(), woods,
                pick.burrow() ? ", an aardvark burrow" : "");
    }

    /**
     * A pick by hand (the command): {@code full} (stage 30), {@code half} (16) or {@code young} (6) living colonies, of
     * the age their stage gives; {@code dead} a full mound dead so many days, and once an old hump (35 days) with woods
     * by the dice, an aardvark burrow and the green grass, to see every extra at once.
     */
    public static Pick pickFor(String kind, double days, long seed) {
        return switch (kind) {
            case "full" -> livingAt(30, seed);
            case "half" -> livingAt(16, seed);
            case "young" -> livingAt(6, seed);
            default -> {
                boolean old = days >= 35;
                int woods = !old ? TermiteMoundStructure.NO_WOODS : 1 + (int) Math.floorMod(seed, 3L);
                yield new Pick(false, TermiteMoundStructure.FULL, 30.0 / TermiteMoundStructure.FULL_STAGE, days, woods, old, old, seed);
            }
        };
    }

    private static Pick livingAt(int stage, long seed) {
        double age = 5 + Math.pow(stage, 1.6); // (the middle of TermiteMoundStructure.ageFor)
        return new Pick(true, TermiteMoundStructure.sizeOf(stage), (double) stage / TermiteMoundStructure.FULL_STAGE, age,
                TermiteMoundStructure.NO_WOODS, false, false, seed);
    }

    // ---------------------------------------------------------------- the colony

    /**
     * How many workers a colony with a mound of this stage has (my picks, to confirm: real mature Macrotermes colonies
     * are a million or more; these are kept to what the colony's food here can feed, and it grows on from them): about
     * 50 at stage 6, 200 at 15, 700 at the full 32, give or take 15 percent, the mound growing with the workers who build
     * it.
     */
    static int workersFor(Pick pick, RandomSource random) {
        double share = (double) pick.stage() / TermiteMoundStructure.FULL_STAGE;
        // Honest sizes (the user, 2026-11-11): never more than the species' limit (200), so mounds of stage 15 and up hold it.
        return Math.min(SpeciesProfiles.get(SavannaSpecies.MACROTERMES).colony().maxWorkers(),
                Math.max(20, (int) Math.round(700 * Math.pow(share, 1.6) * (0.85 + 0.3 * random.nextDouble()))));
    }

    /** The members a colony of that size has, the nest dug, the mound up, the gardens filled (living or about to die). */
    private static Mound raise(ServerLevel level, Colony colony, Pick pick, RandomSource random, boolean comb) {
        colony.setAge(level, pick.living() ? pick.days() : Math.max(10, pick.days()));
        // Workers of every age from just grown up to old, never older than the colony; and its soldiers.
        int workers = workersFor(pick, random);
        long now = level.getGameTime();
        long oldest = Math.max(1, Math.min(Math.round(colony.profile().worker().lifespanTicks() * 0.8), Math.round(pick.days() * 24000) - 24000));
        for (int i = 0; i < workers; i++) {
            colony.population().addNewMembers(Caste.WORKER, now - oldest * i / workers, 1);
        }
        for (ColonyHooks.Castes castes : colony.hooks().all(ColonyHooks.CASTES)) {
            castes.addFor(workers, now); // (the termite soldiers so many workers keep)
        }
        if (pick.stage() >= 10) {
            colony.nest().unlockAllLevels(); // an old colony dug them all long ago
        }
        Mound mound = colony.part(SavannaParts.MOUND).orElse(null);
        if (mound != null && mound.numbers() != null) {
            int h = Math.max(2, Math.min(mound.numbers().maxHeight(), (int) Math.round(pick.height() * mound.numbers().maxHeight())));
            mound.setWorkStage(level, h);
            clearForMound(level, mound);
            mound.buildInstantly(level, h);
        }
        digNest(level, colony);
        if (comb) {
            fillGardens(level, colony, random);
        }
        return mound;
    }

    /** Whatever new land put where the mound goes and is not soil (a fallen branch, a bush) is cleared first. */
    private static void clearForMound(ServerLevel level, Mound mound) {
        for (BlockPos pos : mound.getParts(level).keySet()) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.canBeReplaced() && !state.is(ModTags.DIGGABLE) && !Mound.isMoundBlock(state)
                    && !state.is(Blocks.BEDROCK)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Every block of the shaft and of every room the plan has now dug out (as in the game tests). Through anything but
     * bedrock: real savanna soil is metres deep, Minecraft's stone a few blocks down is the game's shortcut (my pick).
     */
    private static void digNest(ServerLevel level, Colony colony) {
        BlockPos bottom = colony.nest().getShaftBottom(level);
        for (BlockPos cell = colony.getCenter().below(); bottom != null && cell.getY() >= bottom.getY(); cell = cell.below()) {
            dig(level, cell);
        }
        List<NestPlan.Chamber> rooms = new ArrayList<>(colony.nest().getPlannedChambers(level));
        rooms.addAll(colony.nest().getSideRooms(level));
        rooms.addAll(colony.nest().getGardens(level));
        if (colony.nest().getFoundingChamber(level) != null) {
            rooms.add(colony.nest().getFoundingChamber(level));
        }
        for (NestPlan.Chamber room : rooms) {
            for (BlockPos cell : room.cells()) {
                dig(level, cell);
            }
        }
    }

    private static void dig(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
            level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** The garden rooms' floors covered in comb, 2 or 3 layers on each (the food store's own count kept). */
    private static void fillGardens(ServerLevel level, Colony colony, RandomSource random) {
        for (BlockPos cell : colony.nest().getGardenCells(level)) {
            if (!level.getBlockState(cell).isAir() || !level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), net.minecraft.core.Direction.UP)) {
                continue; // (comb lies only on a firm floor)
            }
            int layers = 2 + random.nextInt(2);
            for (int i = 0; i < layers; i++) {
                if (!colony.food().depositSeed(level, cell)) {
                    break;
                }
            }
        }
    }

    private Colony putLiving(ServerLevel level, BlockPos center, Pick pick, UUID colonyId) {
        RandomSource random = RandomSource.create(pick.seed());
        TermiteQueenEntity queen = SavannaEntities.TERMITE_QUEEN.get().create(level);
        TermiteKingEntity king = SavannaEntities.TERMITE_KING.get().create(level);
        if (queen == null || king == null) {
            return null;
        }
        ColonyManager manager = ColonyManager.get(level);
        Colony colony = manager.createColony(SavannaSpecies.MACROTERMES, colonyId, queen.getUUID(), center);
        queen.becomeQueenOf(colony);
        raise(level, colony, pick, random, true);
        // The royal pair in their chamber, the queen swollen by the colony's size (as she grows in play).
        NestPlan.Chamber home = colony.nest().getHomeChamber(level);
        Vec3 spot = Vec3.atBottomCenterOf(home != null ? home.center() : center);
        colony.profile().section(SavannaNumbers.QUEEN_BODY).ifPresent(body ->
                queen.setSize(Math.min(1.0F, (colony.getMemberCount()) / (float) Math.max(1, body.fullSizeAtMembers()))));
        queen.moveTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
        king.moveTo(spot.x + 0.6, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(queen);
        level.addFreshEntity(king);
        king.becomeKingOf(level, colony);
        manager.markChanged();
        return colony; // (its galleries are grown once its dead tree is in, treeBy)
    }

    /**
     * A mound's dead tree put in ({@link #deadTree}), and then a living colony's galleries: its builders' time spent out
     * of sight, as after a long time away (most of it is dropped once the galleries are as long as its workers allow),
     * after the tree, so they reach the wood it knows first. {@code colony} is the living colony (null if gone).
     */
    private void treeBy(ServerLevel level, BlockPos center, Pick pick, Colony colony) {
        lastDeadWood = null;
        Colony living = pick.living() ? colony : null;
        lastDeadTree = deadTree(level, center, pick, living);
        if (living != null) {
            // M8 step 3F, F3: a colony of years knows the dead wood round its mound (the branches under the trees in its
            // reach), so its galleries go out to it in the catch-up below, a feeding place at each (my pick).
            int reach = living.profile().section(SavannaNumbers.GALLERIES).map(SavannaNumbers.GalleryNumbers::reach).orElse(50);
            List<BlockPos> wood = living.food().richFoodAround(level, reach);
            for (BlockPos pos : wood) {
                living.foodMap().learn(pos);
            }
            learntWood = wood.size();
            living.building().addWork(5_000_000L);
            ColonyManager.get(level).markChanged();
        }
    }

    // ---------------------------------------------------------------- dead mounds

    private Colony putDead(ServerLevel level, BlockPos center, Pick pick, UUID colonyId) {
        RandomSource random = RandomSource.create(pick.seed());
        ColonyManager manager = ColonyManager.get(level);
        Colony colony = manager.createColony(SavannaSpecies.MACROTERMES, colonyId, UUID.randomUUID(), center);
        raise(level, colony, pick, random, pick.days() < DeadMounds.GONE_DAYS);
        manager.colonyEnded(level, colony); // its parts write the dead mound down, then it is gone
        DeadMounds.DeadMound mound = DeadMounds.get(level).agedForNewLand(level, colony.getId(), pick.days());
        lastBurrow = null;
        lastTrees.clear();
        if (mound != null && pick.isOldHump()) {
            if (pick.grass()) {
                humpGrass(level, center, random);
            }
            if (pick.burrow()) {
                lastBurrow = burrow(level, center, random);
            }
            lastTrees.addAll(woods(level, center, pick, random));
        }
        return colony;
    }

    /** The top of the ground (or hump) at x, z: the first free cell over it. */
    private static BlockPos top(ServerLevel level, int x, int z) {
        return GroundTop.cell(level, x, z);
    }

    /**
     * Extra (my pick, to confirm): an old hump's grass is greener and taller than the savanna's round it. Real mound
     * islands: the clay soil holds water and the termites' leftovers made it rich, so they stay green into the dry season.
     */
    private static void humpGrass(ServerLevel level, BlockPos center, RandomSource random) {
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                if (dx * dx + dz * dz > 81 || Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    continue; // (the old chimney's hole in the middle stays open)
                }
                BlockPos at = top(level, center.getX() + dx, center.getZ() + dz);
                if (!level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK) || !level.getBlockState(at).isAir()) {
                    continue;
                }
                float roll = random.nextFloat();
                if (roll < 0.1F && level.getBlockState(at.above()).isAir()) {
                    DoublePlantBlock.placeAt(level, Blocks.TALL_GRASS.defaultBlockState(), at, Block.UPDATE_CLIENTS);
                } else if (roll < 0.45F) {
                    level.setBlock(at, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * Extra (my pick, to confirm): an aardvark's burrow into an old hump. Real aardvarks dig into termite mounds, and a
     * dead mound's old burrows are homes for warthogs, porcupines and many smaller animals; the Matabele ants can use it
     * too. A tunnel two blocks wide and two high runs from the hump's foot down to the old nest's shaft, with the soil
     * thrown out in a fan at its mouth.
     */
    private static BlockPos burrow(ServerLevel level, BlockPos center, RandomSource random) {
        double bearing = random.nextDouble() * Math.PI * 2;
        double cos = Math.cos(bearing), sin = Math.sin(bearing);
        int depth = 4; // its floor this far under the ground at the nest
        for (double r = 10; r >= 0; r -= 0.5) {
            int floor = center.getY() - 1 - (int) Math.round((10 - r) * depth / 10.0);
            BlockPos cell = BlockPos.containing(center.getX() + 0.5 + cos * r, floor, center.getZ() + 0.5 + sin * r);
            BlockPos side = BlockPos.containing(center.getX() + 0.5 + cos * r - sin, floor, center.getZ() + 0.5 + sin * r + cos);
            for (BlockPos pos : new BlockPos[] {cell, cell.above(), side, side.above()}) {
                BlockState state = level.getBlockState(pos);
                if (state.is(ModTags.DIGGABLE) || Mound.isMoundBlock(state) || state.canBeReplaced() && !state.isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // The spoil: loose earth thrown out in front of the mouth.
        for (int i = 0; i < 8; i++) {
            double r = 11 + random.nextDouble() * 2.5, off = (random.nextDouble() - 0.5) * 3;
            BlockPos at = top(level, (int) Math.floor(center.getX() + 0.5 + cos * r - sin * off), (int) Math.floor(center.getZ() + 0.5 + sin * r + cos * off));
            if (level.getBlockState(at).canBeReplaced() && level.getBlockState(at.below()).is(BlockTags.DIRT)) {
                level.setBlock(at.below(), Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return BlockPos.containing(center.getX() + 0.5 + cos * 10, center.getY() - 1, center.getZ() + 0.5 + sin * 10);
    }

    /**
     * The trees on an old hump (the user's pick: real mound islands carry trees and thickets on their rich soil): a
     * thicket of 1 to 3 young thorn trees, one grown thorn tree, or a young baobab (my picks, to confirm), a few blocks
     * off the old chimney, into the tree book so they grow on from there.
     */
    private static List<BlockPos> woods(ServerLevel level, BlockPos center, Pick pick, RandomSource random) {
        List<BlockPos> feet = new ArrayList<>();
        int count = pick.woods() == TermiteMoundStructure.THICKET ? 1 + random.nextInt(3) : pick.woods() == TermiteMoundStructure.NO_WOODS ? 0 : 1;
        double bearing = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            String kind;
            double age;
            if (pick.woods() == TermiteMoundStructure.YOUNG_BAOBAB) {
                kind = "baobab";
                age = 0.1 + random.nextDouble() * 0.2;
            } else {
                kind = random.nextInt(3) < 2 ? "umbrella_thorn" : "camel_thorn";
                age = pick.woods() == TermiteMoundStructure.GROWN_TREE ? 0.55 + random.nextDouble() * 0.25 : 0.08 + random.nextDouble() * 0.27;
            }
            age = Math.round(age * 100) / 100.0;
            double b = bearing + i * (2 * Math.PI / 3) + (random.nextDouble() - 0.5);
            double r = (count > 1 ? 3 : 2.5) + random.nextDouble() * 2.5;
            BlockPos foot = top(level, (int) Math.floor(center.getX() + 0.5 + Math.cos(b) * r), (int) Math.floor(center.getZ() + 0.5 + Math.sin(b) * r));
            BlockState under = level.getBlockState(foot.below());
            if (!(under.is(BlockTags.DIRT) || Mound.isMoundBlock(under)) || Forest.get(level).at(foot) != null) {
                continue;
            }
            long seed = random.nextLong() & 0xFFFFFFFFL;
            TreePlacer.Placed tree = TreePlacer.placed(kind, seed, age, TreeStage.LIVING);
            level.removeBlock(foot, false); // (a tuft of grass where the trunk goes)
            TreePlacer.place(level, tree, foot, tree.box(foot), Block.UPDATE_CLIENTS);
            Forest.get(level).add(new Forest.Tree(kind, foot, seed, age, TreeStage.LIVING, age, Forest.today(level))); // (into the tree book, grows on from here)
            feet.add(foot);
        }
        return feet;
    }

    // ---------------------------------------------------------------- the dead tree by the mound

    /**
     * The dead tree's foot stands this far (blocks) past the mound's foot, spread evenly between the two (the user's ask,
     * 2026-10-25: 10 to 30 blocks away, so the galleries have a way to dig to it).
     */
    public static final int DEAD_TREE_NEAREST = 10;
    public static final int DEAD_TREE_FARTHEST = 30;
    /** How many dead trees are tried, and how many places each (the ring is wide, much of it may be uneven or unloaded). */
    static final int DEAD_TREE_TRIES = 10;
    static final int DEAD_TREE_SPOTS = 3;
    /**
     * The ground round a standing dead tree's trunk may rise and fall this much (its foot on the lowest of it, the trunk
     * standing into the higher side as new land's giant trees do; was 2, and with the trunk's base counted as in the way it
     * was in truth 0: {@code docs/BUGS.md} 81; 4 my pick, the giants take 6); under a fallen log, this much from its foot.
     */
    public static final int DEAD_TREE_UNEVEN = 4;
    static final int DEAD_LOG_UNEVEN = 3;

    /**
     * The dead wood for the raids, part 3 ({@code docs/plans/dead-wood-for-raids.md}): every mound of new land, living or
     * dead, gets one dead thorn tree near it, a standing dead tree (bare, mud-climbed or shed of its limbs) or a fallen
     * log, half and half (my picks), into the tree book so it wears on from there. Real: a fungus-growing colony hollows
     * and kills the trees round it (M7.5 step 5f), so a dead tree by a mound is a common sight, and it is where the
     * termites feed by day under their mud, the spots Matabele scouts look for. Its foot 10 to 30 blocks past the
     * mound's foot, any distance between alike (the user's ask, 2026-10-25; even spread my pick: a full cathedral's foot
     * reaches about 12, so there it stands 22 to 42 off the middle, inside the galleries' reach of 50; real Macrotermes
     * forage tens of metres out through their galleries), on fairly flat soil, with nothing in its way, all of it on loaded land. A living colony knows the wood at
     * once (it has lived beside it), so its galleries reach it first. Returns its foot, or null if no place suited.
     */
    private BlockPos deadTree(ServerLevel level, BlockPos center, Pick pick, Colony living) {
        long start = System.nanoTime();
        List<DeadTreeTry> tries = deadTreeTries(pick.seed());
        CompletableFuture<List<TreePlacer.Placed>> worked = DEAD_TREE_SHAPES.remove(pick.seed());
        List<TreePlacer.Placed> shapes = worked != null && worked.isDone() && !worked.isCompletedExceptionally() ? worked.join() : null;
        RandomSource random = RandomSource.create(pick.seed() * 37 + 11);
        Mound mound = living == null ? null : living.part(SavannaParts.MOUND).orElse(null);
        LongOpenHashSet footprint = new LongOpenHashSet();
        int reach = 0;
        if (mound != null) {
            for (BlockPos part : mound.getParts(level).keySet()) {
                footprint.add(BlockPos.asLong(part.getX(), 0, part.getZ()));
                double dx = part.getX() - center.getX(), dz = part.getZ() - center.getZ();
                reach = Math.max(reach, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz)));
            }
        } else {
            reach = (int) Math.round(pick.height() * 12); // (a dead mound's shape is worn: its old foot by its stage)
        }
        lastDeadTreeReach = reach;
        int[] noGround = new int[NO_GROUND_WHY.length];
        int unloaded = 0, inTheWay = 0;
        String why = "";
        for (int spot = 0; spot < DEAD_TREE_TRIES * DEAD_TREE_SPOTS; spot++) {
            int attempt = spot / DEAD_TREE_SPOTS;
            DeadTreeTry t = tries.get(attempt);
            String kind = t.kind();
            double age = t.age();
            long seed = t.seed();
            TreeStage stage = t.stage();
            double bearing = random.nextDouble() * Math.PI * 2, r = reach + DEAD_TREE_NEAREST + random.nextDouble() * (DEAD_TREE_FARTHEST - DEAD_TREE_NEAREST);
            int x = (int) Math.floor(center.getX() + 0.5 + Math.cos(bearing) * r), z = (int) Math.floor(center.getZ() + 0.5 + Math.sin(bearing) * r);
            if (fence != null && (x < fence.minX() || x > fence.maxX() || z < fence.minZ() || z > fence.maxZ())) {
                noGround[0]++;
                continue;
            }
            BlockPos foot = deadTreeFoot(level, x, z, (int) Math.ceil(TreePlacer.kind(kind).trunk().radius() * 1.5), center.getY(), noGround);
            if (foot == null) {
                continue;
            }
            if (Forest.get(level).at(foot) != null) {
                noGround[4]++;
                continue;
            }
            TreePlacer.Placed tree = shapes != null ? shapes.get(attempt) : TreePlacer.placed(kind, seed, age, stage);
            if (tree.lying() != null) {
                tree = TreePlacer.rest(tree, foot, (gx, gz) -> top(level, gx, gz).getY());
            }
            BoundingBox box = tree.box(foot);
            if (!LandReady.box(level, box.minX() - 1, box.minZ() - 1, box.maxX() + 1, box.maxZ() + 1)) {
                unloaded++;
                continue;
            }
            String blocked = blocked(level, tree, foot, footprint, center, reach);
            if (blocked != null) {
                why = blocked;
                inTheWay++;
                continue;
            }
            level.removeBlock(foot, false); // (a tuft of grass where the trunk goes)
            TreePlacer.place(level, tree, foot, box, Block.UPDATE_CLIENTS);
            Forest.get(level).add(new Forest.Tree(kind, foot, seed, age, stage, age, Forest.today(level))); // (wears on from here)
            lastDeadWood = living == null ? null : learnWood(level, living, tree, foot);
            SavannaMod.LOGGER.info("[new-land] a dead {} ({}) by the mound at {}, {} blocks off ({} past its foot), try {}, in {} ms", kind, stage.id, foot.toShortString(),
                    String.format(Locale.ROOT, "%.0f", Math.sqrt(foot.distSqr(center))), String.format(Locale.ROOT, "%.0f", Math.sqrt(foot.distSqr(center)) - reach), spot + 1,
                    String.format(Locale.ROOT, "%.1f", (System.nanoTime() - start) / 1.0e6));
            return foot;
        }
        StringBuilder noGroundWhy = new StringBuilder();
        for (int w = 0; w < noGround.length; w++) {
            if (noGround[w] > 0) {
                noGroundWhy.append(noGroundWhy.isEmpty() ? "" : ", ").append(NO_GROUND_WHY[w]).append(' ').append(noGround[w]);
            }
        }
        SavannaMod.LOGGER.info("[new-land] no room for a dead tree by the mound at {} ({} places tried, {} to {} blocks off: no ground {} ({}), land not loaded {}, something in the way {}, the last {}; {} ms)",
                center.toShortString(), DEAD_TREE_TRIES * DEAD_TREE_SPOTS, reach + DEAD_TREE_NEAREST, reach + DEAD_TREE_FARTHEST, java.util.Arrays.stream(noGround).sum(),
                noGroundWhy, unloaded, inTheWay, why, String.format(Locale.ROOT, "%.1f", (System.nanoTime() - start) / 1.0e6));
        return null;
    }

    /** One dead tree to try by a mound: its kind, seed, age and stage. */
    record DeadTreeTry(String kind, long seed, double age, TreeStage stage) {
    }

    /**
     * The dead trees a mound tries, in order, by its own seed alone (so their shapes can be worked out before the mound
     * goes in): umbrella thorn 2 in 3, camel thorn 1 in 3, grown (age 0.75 to 1.1); half the mounds try fallen logs
     * first, the last tries standing dead trees (bare, mud-climbed or shed), as new land's own fallen giants stand up
     * when no flat bed is found (my picks).
     */
    static List<DeadTreeTry> deadTreeTries(long pickSeed) {
        RandomSource random = RandomSource.create(pickSeed * 31 + 7);
        boolean fallen = random.nextBoolean();
        List<DeadTreeTry> tries = new ArrayList<>();
        for (int attempt = 0; attempt < DEAD_TREE_TRIES; attempt++) {
            String kind = random.nextInt(3) < 2 ? "umbrella_thorn" : "camel_thorn";
            double age = Math.round((0.75 + random.nextDouble() * 0.35) * 100) / 100.0;
            long seed = random.nextLong() & 0xFFFFFFFFL;
            TreeStage stage = fallen && attempt < DEAD_TREE_TRIES / 2 ? TreeStage.FALLEN
                    : new TreeStage[] {TreeStage.SNAG, TreeStage.MUD, TreeStage.SHED}[random.nextInt(3)];
            tries.add(new DeadTreeTry(kind, seed, age, stage));
        }
        return tries;
    }

    /** Dead trees' shapes being worked out in the background, by their mound's seed (the game never stops for them). */
    private static final Map<Long, CompletableFuture<List<TreePlacer.Placed>>> DEAD_TREE_SHAPES = new ConcurrentHashMap<>();

    /** Starts working out a waiting mound's dead tree shapes in the background; true once they are ready. */
    static boolean deadTreeShapesSoon(long pickSeed) {
        return DEAD_TREE_SHAPES.computeIfAbsent(pickSeed, s -> CompletableFuture.supplyAsync(() -> deadTreeTries(s).stream()
                .map(t -> TreePlacer.placed(t.kind(), t.seed(), t.age(), t.stage())).toList())).isDone();
    }

    /** Why a place had no ground for a dead tree, for the log (counted in {@link #deadTreeFoot}'s {@code why}). */
    private static final String[] NO_GROUND_WHY = {"past a test's fence", "not soil (water, sand, rock or something lying)", "too steep",
            "too far above or below the mound", "another tree's place", "land round it not loaded"};

    /**
     * A dead tree's foot near (x, z): the lowest ground round its trunk, on soil near the mound's own ground (within twice
     * {@link #DEAD_TREE_UNEVEN}: the galleries follow the land up and down, so a tree on a rise or in a dip is still
     * theirs); null on water, a steep slope or no soil, counting why in {@code why} (see {@link #NO_GROUND_WHY}).
     */
    private static BlockPos deadTreeFoot(ServerLevel level, int x, int z, int reach, int groundY, int[] why) {
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        int[][] around = {{0, 0}, {reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}};
        for (int[] o : around) {
            if (!LandReady.at(level, x + o[0], z + o[1])) {
                why[5]++;
                return null;
            }
            BlockPos at = top(level, x + o[0], z + o[1]);
            if (!level.getFluidState(at.below()).isEmpty() || !level.getBlockState(at.below()).is(BlockTags.DIRT)) {
                why[1]++;
                return null;
            }
            lowest = Math.min(lowest, at.getY());
            highest = Math.max(highest, at.getY());
        }
        if (highest - lowest > DEAD_TREE_UNEVEN) {
            why[2]++;
            return null;
        }
        if (Math.abs(lowest - groundY) > 2 * DEAD_TREE_UNEVEN) {
            why[3]++;
            return null;
        }
        return new BlockPos(x, lowest, z);
    }

    /**
     * Whether the dead tree has its room (its trunk's foot already stands past the mound's foot): a fallen log not over
     * the mound's foot (or, for a dead mound, inside its old reach); its limbs free to reach over the mound as a real
     * dead giant's do and its broken limbs free to lie on it, in open air; its roots in soil, never filling a tunnel or room;
     * every block of it above the ground in open air (no other tree, hump wood, bush or mound in its way), save the
     * trunk's (or stump's) lowest {@link #DEAD_TREE_UNEVEN} blocks, which may stand into the higher side of the ground
     * round its foot as new land's giant trees do (the placer leaves those blocks out; {@code docs/BUGS.md} 81); and a fallen
     * log lying on land within {@link #DEAD_LOG_UNEVEN} of its foot. Returns what is in the way, or null if it fits.
     */
    private static String blocked(ServerLevel level, TreePlacer.Placed tree, BlockPos foot, LongOpenHashSet footprint, BlockPos center, int reach) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < tree.size(); i++) {
            int x = foot.getX() + tree.xs()[i], z = foot.getZ() + tree.zs()[i];
            double dx = x - center.getX(), dz = z - center.getZ();
            int slice = tree.lying() == null ? TreePlacer.FIXED : tree.lying().slice()[i];
            boolean low = slice >= 0; // (a fallen log; the trunk's foot already stands past the mound's foot)
            if (low && (footprint.contains(BlockPos.asLong(x, 0, z)) || dx * dx + dz * dz <= (double) reach * reach
                    || fence != null && (x < fence.minX() || x > fence.maxX() || z < fence.minZ() || z > fence.maxZ()))) {
                return "the mound (" + tree.xs()[i] + ", " + tree.ys()[i] + ", " + tree.zs()[i] + ")";
            }
            pos.set(x, foot.getY() + tree.ys()[i], z);
            if (tree.ys()[i] < 0) {
                if (fence != null && (x < fence.minX() || x > fence.maxX() || z < fence.minZ() || z > fence.maxZ())) {
                    return "a test's fence (a root at " + pos.toShortString() + ")"; // (past a test arena is empty space, no soil)
                }
                if (level.getBlockState(pos).isAir()) {
                    int overGround = pos.getY() - top(level, x, z).getY();
                    if (overGround < 0) {
                        return "a root in a tunnel or room at " + pos.toShortString(); // (roots run on through the soil only)
                    }
                    if (overGround > 1) {
                        return "a root hanging over a dip at " + pos.toShortString();
                    }
                    // (over a dip in the land, out in the open on the ground: an exposed root, as on a real tree; BUGS 81)
                }
                continue;
            }
            BlockState there = level.getBlockState(pos);
            if (!TreePlacer.isOpen(there) && !(slice == TreePlacer.FIXED && tree.ys()[i] <= DEAD_TREE_UNEVEN && isGround(there))) {
                return there.getBlock().getName().getString() + " at " + pos.toShortString();
            }
            if (slice >= 0 && Math.abs(top(level, x, z).getY() - foot.getY()) > DEAD_LOG_UNEVEN) {
                return "uneven land under the log at " + pos.toShortString();
            }
        }
        return null;
    }

    /** The land itself (soil, sand, gravel or rock), which a dead tree's foot may stand into; never a mound, a tree or a player's block. */
    private static boolean isGround(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.BASE_STONE_OVERWORLD);
    }

    /** A living colony learns its dead tree: the first block of dead wood put in, measured, its foot into the food map. */
    private static BlockPos learnWood(ServerLevel level, Colony colony, TreePlacer.Placed tree, BlockPos foot) {
        DeadWoodCall call = new DeadWoodCall(colony);
        for (int i = 0; i < tree.size(); i++) {
            BlockPos pos = foot.offset(tree.xs()[i], tree.ys()[i], tree.zs()[i]);
            BlockPos learnt = tree.ys()[i] >= 0 ? call.learnNow(level, pos) : null;
            if (learnt != null) {
                return learnt;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- the command and saving

    /** For the command: how many mounds new land has had, and how many places wait for their land. */
    public String describeBook() {
        return String.format(Locale.ROOT, "Termite mounds of new land: %d living colonies and %d dead mounds put in (%d with a Matabele colony), %d places skipped, %d waiting for their land to load.",
                living, dead, ants, skipped, waiting.size());
    }

    /** The game tests: the last old hump's burrow mouth (null if none) and its trees' feet. */
    public BlockPos lastBurrowForTests() {
        return lastBurrow;
    }

    /** The Matabele colony the last dead mound got (null if none). */
    public Colony lastAnts() {
        return lastAnts;
    }

    /** The game tests: how long the last mound took to put in (milliseconds). */
    public double lastMillisForTests() {
        return lastMillis;
    }

    /** The game tests: how many mounds put in wait for their dead tree's land. */
    public int treesWaitingForTests() {
        return treesWaiting.size();
    }

    /** The game tests: how many pieces of rich food the last living colony of new land knew from the start (F3). */
    public int learntWoodForTests() {
        return learntWood;
    }

    /** The game tests: the foot of the last mound's dead tree (null if it found no room). */
    public BlockPos lastDeadTreeForTests() {
        return lastDeadTree;
    }

    /** The game tests: how far the last mound's foot reached from its middle (the dead tree stands 10 to 30 past it). */
    public int lastDeadTreeReachForTests() {
        return lastDeadTreeReach;
    }

    /** The game tests: the foot of the dead wood the last living colony learnt (null if none). */
    public BlockPos lastDeadWoodForTests() {
        return lastDeadWood;
    }

    /** The game tests: the dead tree's foot and its parts on the ground (a fallen log, lying limbs) must lie inside this box (null: anywhere on loaded land). */
    public static void fenceForTests(BoundingBox box) {
        fence = box;
    }

    public List<BlockPos> lastTreesForTests() {
        return List.copyOf(lastTrees);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Site site : waiting) {
            CompoundTag s = new CompoundTag();
            TermiteMoundPiece.save(site.pick(), s);
            s.putLong("middle", site.middle().asLong());
            s.putUUID("colony", site.colony());
            list.add(s);
        }
        tag.put("waiting", list);
        ListTag trees = new ListTag();
        for (Site site : treesWaiting) {
            CompoundTag s = new CompoundTag();
            TermiteMoundPiece.save(site.pick(), s);
            s.putLong("middle", site.middle().asLong());
            s.putUUID("colony", site.colony());
            trees.add(s);
        }
        tag.put("trees_waiting", trees);
        tag.putInt("living", living);
        tag.putInt("dead", dead);
        tag.putInt("skipped", skipped);
        tag.putInt("ants", ants);
        return tag;
    }

    private static NewLandMounds load(CompoundTag tag, HolderLookup.Provider registries) {
        NewLandMounds book = new NewLandMounds();
        for (Tag t : tag.getList("waiting", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            book.waiting.add(new Site(BlockPos.of(s.getLong("middle")), TermiteMoundPiece.load(s), s.getUUID("colony")));
        }
        for (Tag t : tag.getList("trees_waiting", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            book.treesWaiting.add(new Site(BlockPos.of(s.getLong("middle")), TermiteMoundPiece.load(s), s.getUUID("colony")));
        }
        book.living = tag.getInt("living");
        book.dead = tag.getInt("dead");
        book.skipped = tag.getInt("skipped");
        book.ants = tag.getInt("ants");
        return book;
    }
}
