package com.antcolonies.gametest;

import static com.antcolonies.gametest.TestScenes.*;
import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.colony.AwayReport;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyParts;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesBodies;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;


/**
 * The away tests' kit (the split, step S4: the helpers that were in the mixed away tests, none naming a species):
 * freezing and loading insects as their chunk goes and comes, a colony saved and loaded while away with every part
 * checked, and founding while away. Each species' scenes are in its own files ({@code TermiteAwayTests},
 * {@code HarvesterAwayTests}), its own part checks signed up by its tests ({@link #addPartsCheck}).
 */
public final class AwayKit {
    /**
     * Each species' own checks of a colony's parts (the split, step S4), run by {@link #partsAreTheSpecies} on every
     * colony saved and loaded in a test (of any species: a check says what it expects of a colony without its parts).
     */
    private static final List<BiConsumer<GameTestHelper, Colony>> PARTS_CHECKS = new ArrayList<>();

    /**
     * Who founds a colony in {@link #foundsWhileAway}: the species, how the founders are put down, the partner who
     * founds with the queen (its body and caste; null for a lone queen), and whether they go away at once, before
     * digging anything (the termite pair), or once the queen is sealed in, staying away until the first workers have dug
     * the store and foraged (the lone ant queen).
     */
    public record Founders(ResourceLocation species, Consumer<GameTestHelper> spawn, @Nullable Class<? extends AbstractAntEntity> partner,
            @Nullable String partnerCaste, boolean awayAtOnce) {
    }

    private AwayKit() {
    }

    /** A species' own check of a colony's parts, run on every colony saved and loaded in a test (its tests' sign-up). */
    public static synchronized void addPartsCheck(BiConsumer<GameTestHelper, Colony> check) {
        PARTS_CHECKS.add(check);
    }

    /** What a frozen insect looked like on disk: saved before it leaves the world, so it can be loaded again. */
    public static CompoundTag freezeAndUnload(Colony colony, ServerLevel level, Entity entity) {
        colony.away().freeze(level, entity); // what the game does when the insect's chunk goes out of range
        CompoundTag tag = new CompoundTag();
        entity.save(tag);
        entity.discard();
        return tag;
    }

    /** The insect's chunk is loaded again: the same insect (same id) is back in the world. */
    public static void loadAgain(ServerLevel level, CompoundTag tag) {
        Entity entity = EntityType.loadEntityRecursive(tag, level, loaded -> loaded);
        if (entity != null) {
            level.addFreshEntity(entity);
        }
    }

    /** Every real worker of the test's colonies leaves the world: only the numbers may forage in these tests. */
    public static void noRealWorkers(GameTestHelper helper) {
        for (AbstractAntEntity worker : helper.getLevel().getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds(),
                mob -> SpeciesBodies.all().stream().anyMatch(bodies -> mob.getType() == bodies.typeFor(Caste.WORKER)))) {
            worker.discard();
        }
    }

    // --- brain part 3: the brain's own tests (the user's plan, 2026-10-15) ----------------------------------------

    /** The save as text with every list sorted and every key in order, so two saves of the same colony read the same. */
    static String canon(net.minecraft.nbt.Tag tag) {
        if (tag instanceof CompoundTag compound) {
            List<String> keys = new ArrayList<>(compound.getAllKeys());
            java.util.Collections.sort(keys);
            StringBuilder text = new StringBuilder("{");
            for (String key : keys) {
                text.append(key).append(':').append(canon(compound.get(key))).append(',');
            }
            return text.append('}').toString();
        }
        if (tag instanceof net.minecraft.nbt.ListTag list) {
            List<String> items = new ArrayList<>();
            list.forEach(item -> items.add(canon(item)));
            java.util.Collections.sort(items); // (a map saved as a list: its order may change from one load to the next)
            return items.toString();
        }
        return tag.toString();
    }

    /**
     * The game is saved and loaded again while the colony is away (the player quit far from it and came back another
     * day): the colony is written to the save, read back as a new copy, and the copy takes its place. Checked: the copy
     * writes exactly the same save (nothing saved is lost or read back wrong); the parts that differ are named. The test
     * switches (never saved) must be set again on the copy by the caller.
     */
    public static Colony saveAndLoad(GameTestHelper helper, Colony colony) {
        partsAreTheSpecies(helper, colony);
        CompoundTag saved = colony.save();
        Colony copy = Colony.load(saved);
        CompoundTag again = copy.save();
        List<String> differ = new ArrayList<>();
        Set<String> keys = new HashSet<>(saved.getAllKeys());
        keys.addAll(again.getAllKeys());
        for (String key : keys) {
            net.minecraft.nbt.Tag before = saved.get(key);
            net.minecraft.nbt.Tag after = again.get(key);
            if (before == null || after == null || !canon(before).equals(canon(after))) {
                differ.add(key + ": " + before + " -> " + after);
            }
        }
        helper.assertTrue(differ.isEmpty(), "the colony loaded from its save should save the same: " + differ);
        everyPartSavesAndLoads(helper, saved);
        ColonyManager.get(helper.getLevel()).replaceForTests(copy);
        return copy;
    }

    /**
     * Project Apex, colony parts steps 2 and 3: the colony has the shared parts and the parts its species file lists, no
     * others, and asking for a part gives one exactly when the colony has it (nothing is made for a part it does not list).
     */
    static void partsAreTheSpecies(GameTestHelper helper, Colony colony) {
        Set<String> wanted = new HashSet<>(colony.profile().parts());
        com.antcolonies.colony.ColonyParts.all().stream().filter(com.antcolonies.colony.ColonyParts.Type::shared)
                .forEach(type -> wanted.add(type.name()));
        Set<String> has = new HashSet<>();
        colony.parts().forEach(part -> has.add(part.name()));
        helper.assertTrue(has.equals(wanted), "a " + colony.getSpecies() + " colony should have the shared parts and the ones its species lists: has "
                + has + ", wanted " + wanted);
        for (com.antcolonies.colony.ColonyParts.Type<?> type : com.antcolonies.colony.ColonyParts.all()) {
            helper.assertTrue(colony.part(type).isPresent() == wanted.contains(type.name()) && colony.has(type) == wanted.contains(type.name()),
                    "a " + colony.getSpecies() + " colony answers wrongly when asked for the part " + type.name());
        }
        // Colony parts step 4, the colony news: each kind is heard by the parts the colony has that used to be called by
        // hand, in the order they were called; a kind nobody hears is not sent at all.
        Map<com.antcolonies.colony.ColonyNews.Kind<?>, List<String>> order = Map.ofEntries(
                Map.entry(com.antcolonies.colony.ColonyNews.MEMBER_HURT, List.of("alarm", "patrol", "danger_map", "forager_spots")),
                Map.entry(com.antcolonies.colony.ColonyNews.MEMBER_DIED, List.of("patrol", "forager_spots", "recruits")),
                Map.entry(com.antcolonies.colony.ColonyNews.FOOD_FOUND, List.of("forager_spots", "recruits")),
                Map.entry(com.antcolonies.colony.ColonyNews.NEIGHBOUR_MET, List.of("neighbours")),
                // Step 8a: the rest of the shared code's tellings, each heard by the one part it used to name.
                Map.entry(com.antcolonies.colony.ColonyNews.INTO_NUMBERS, List.of("forager_spots")),
                Map.entry(com.antcolonies.colony.ColonyNews.OUT_OF_NUMBERS, List.of("forager_spots")),
                Map.entry(com.antcolonies.colony.ColonyNews.FORAGER_NUMBERS, List.of("forager_spots")),
                Map.entry(com.antcolonies.colony.ColonyNews.PIECE_TAKEN, List.of("bakery")),
                Map.entry(com.antcolonies.colony.ColonyNews.PIECE_EATEN, List.of("bakery")),
                Map.entry(com.antcolonies.colony.ColonyNews.HEAP_GONE, List.of("fungus_garden")),
                Map.entry(com.antcolonies.colony.ColonyNews.NUMBERS_TRIP, List.of("trunk_trails")),
                Map.entry(com.antcolonies.colony.ColonyNews.NUMBERS_FORAGED, List.of("door")),
                Map.entry(com.antcolonies.colony.ColonyNews.BLOCK_DUG, List.of("mound")),
                Map.entry(com.antcolonies.colony.ColonyNews.PLANT_EATEN, List.of("bare_patches")), // the split, step S3: was held by the food store
                // step 9a: the shared goals' tellings
                Map.entry(com.antcolonies.colony.ColonyNews.SET_OUT, List.of("door")),
                Map.entry(com.antcolonies.colony.ColonyNews.SEARCH_STARTED, List.of("forager_spots")),
                Map.entry(com.antcolonies.colony.ColonyNews.SEARCH_OVER, List.of("recruits", "forager_spots", "door")),
                Map.entry(com.antcolonies.colony.ColonyNews.MEMBER_ROUND, List.of("door", "recruits")),
                Map.entry(com.antcolonies.colony.ColonyNews.PLAYER_STAMPED, List.of("alarm")),
                Map.entry(com.antcolonies.colony.ColonyNews.BLOCK_BROKEN, List.of("alarm")));
        for (Map.Entry<com.antcolonies.colony.ColonyNews.Kind<?>, List<String>> kind : order.entrySet()) {
            List<String> hearing = kind.getValue().stream().filter(has::contains).toList();
            helper.assertTrue(colony.news().listenersOf(kind.getKey()).equals(hearing) && colony.news().listens(kind.getKey()) == !hearing.isEmpty(),
                    "a " + colony.getSpecies() + " colony's news of " + kind.getKey() + " should be heard by " + hearing + ", not "
                            + colony.news().listenersOf(kind.getKey()));
        }
        // Colony parts steps 6 and 7, the colony questions: each kind is answered by the parts the colony has that used
        // to be asked by name, in the order they were asked; a kind nobody answers is "no" at once.
        Map<com.antcolonies.colony.ColonyQuestions.Kind<?>, List<String>> asked = Map.ofEntries(
                Map.entry(com.antcolonies.colony.ColonyQuestions.KEEP_OFF, List.of("neighbours", "closed_holes", "danger_map", "trunk_trails")),
                Map.entry(com.antcolonies.colony.ColonyQuestions.NOT_CLEARED, List.of("patrol")),
                Map.entry(com.antcolonies.colony.ColonyQuestions.HOLE_KEPT_SHUT, List.of("closed_holes")),
                Map.entry(com.antcolonies.colony.ColonyQuestions.ANY_HOLE_KEPT_SHUT, List.of("closed_holes")),
                Map.entry(com.antcolonies.colony.ColonyQuestions.NO_GALLERY_THROUGH, List.of("closed_holes")),
                // Step 8a: the rest of the shared code's yes/no asks, each answered by the one part it used to name.
                Map.entry(com.antcolonies.colony.ColonyQuestions.KEEPS_EVERYONE_IN, List.of("alarm")),
                Map.entry(com.antcolonies.colony.ColonyQuestions.BUILDS_OVER_NEST, List.of("mound")),
                Map.entry(com.antcolonies.colony.ColonyQuestions.BUILDING_OVER_NEST, List.of("mound")),
                Map.entry(com.antcolonies.colony.ColonyQuestions.CLEAR_JOB, List.of("mound")),
                Map.entry(com.antcolonies.colony.ColonyQuestions.STRUCTURE_TUNNEL, List.of("mound")),
                Map.entry(com.antcolonies.colony.ColonyQuestions.MORE_FOOD_WANTED, List.of("bakery")),
                Map.entry(com.antcolonies.colony.ColonyQuestions.KEEPS_TRAILS, List.of("trunk_trails")),
                Map.entry(com.antcolonies.colony.ColonyQuestions.NOTHING_TO_EAT_IN_STORE, List.of("fungus_garden")),
                // step 9a: the shared goals' asks
                Map.entry(com.antcolonies.colony.ColonyQuestions.MUST_WAIT, List.of("door")),
                Map.entry(com.antcolonies.colony.ColonyQuestions.PASSES_BY, List.of("recruits")),
                Map.entry(com.antcolonies.colony.ColonyQuestions.ALARMED, List.of("alarm")),
                Map.entry(com.antcolonies.colony.ColonyQuestions.ON_TRAIL, List.of("trunk_trails")));
        for (Map.Entry<com.antcolonies.colony.ColonyQuestions.Kind<?>, List<String>> kind : asked.entrySet()) {
            List<String> answering = kind.getValue().stream().filter(has::contains).toList();
            helper.assertTrue(colony.questions().answerersOf(kind.getKey()).equals(answering) && colony.questions().answers(kind.getKey()) == !answering.isEmpty(),
                    "a " + colony.getSpecies() + " colony's question " + kind.getKey() + " should be answered by " + answering + ", not "
                            + colony.questions().answerersOf(kind.getKey()));
        }
        // Steps 8b and 8c, the colony hooks: each socket is plugged by the parts the colony has that the shared code used
        // to name, in the order it used them.
        Map<com.antcolonies.colony.ColonyHooks.Kind<?>, List<String>> plugged = Map.ofEntries(
                Map.entry(com.antcolonies.colony.ColonyHooks.REACH, List.of("trunk_trails")),
                Map.entry(com.antcolonies.colony.ColonyHooks.TRAILS, List.of("trunk_trails")),
                Map.entry(com.antcolonies.colony.ColonyHooks.DOOR_RULE, List.of("door")),
                Map.entry(com.antcolonies.colony.ColonyHooks.CO_FOUNDERS, List.of("king_care")),
                Map.entry(com.antcolonies.colony.ColonyHooks.NURSE_FOOD, List.of("fungus_garden", "bakery")),
                Map.entry(com.antcolonies.colony.ColonyHooks.CASTES, List.of("soldiers")),
                Map.entry(com.antcolonies.colony.ColonyHooks.HIDDEN_AWAY, List.of("hidden_trips")),
                Map.entry(com.antcolonies.colony.ColonyHooks.STRUCTURE, List.of("mound")),
                Map.entry(com.antcolonies.colony.ColonyHooks.GARDEN, List.of("fungus_garden")),
                Map.entry(com.antcolonies.colony.ColonyHooks.CRUST, List.of("mound")), // step 9c
                Map.entry(com.antcolonies.colony.ColonyHooks.AWAY_BUILDER, List.of("hill", "mound")),
                Map.entry(com.antcolonies.colony.ColonyHooks.NUMBERS_SEARCH, List.of("forager_spots")),
                Map.entry(com.antcolonies.colony.ColonyHooks.AWAY_BESIDE_QUEEN, List.of("king_care")),
                Map.entry(com.antcolonies.colony.ColonyHooks.AWAY_AFTER_NURSES, List.of("fungus_garden")),
                Map.entry(com.antcolonies.colony.ColonyHooks.NURSE_FEEDS, List.of("king_care")),
                Map.entry(com.antcolonies.colony.ColonyHooks.NURSE_MAKES, List.of("bakery")),
                // step 9a: the shared goals' sockets
                Map.entry(com.antcolonies.colony.ColonyHooks.LONE_SEARCH, List.of("forager_spots")),
                Map.entry(com.antcolonies.colony.ColonyHooks.CALLED_OUT, List.of("recruits")),
                Map.entry(com.antcolonies.colony.ColonyHooks.MILL, List.of("bakery")),
                Map.entry(com.antcolonies.colony.ColonyHooks.DUG_SOIL, List.of("mound", "hill")),
                // step 9b: the parts' words in the colony list, slot by slot, in the order the list wrote them
                Map.entry(com.antcolonies.colony.ColonyHooks.LIST_STORE, List.of("bakery")),
                Map.entry(com.antcolonies.colony.ColonyHooks.LIST_FOOD, List.of("fungus_garden")),
                Map.entry(com.antcolonies.colony.ColonyHooks.LIST_BROOD, List.of("soldiers", "alarm")),
                Map.entry(com.antcolonies.colony.ColonyHooks.LIST_SEARCH, List.of("forager_spots", "recruits", "trunk_trails")),
                Map.entry(com.antcolonies.colony.ColonyHooks.LIST_DANGER, List.of("closed_holes")),
                Map.entry(com.antcolonies.colony.ColonyHooks.LIST_BUILDING, List.of("mound")),
                Map.entry(com.antcolonies.colony.ColonyHooks.LIST_DAY, List.of("patrol", "door", "hill")),
                Map.entry(com.antcolonies.colony.ColonyHooks.LIST_HOLES, List.of("galleries", "hidden_trips", "bare_patches")),
                // split S2: the galleries a part of their own
                Map.entry(com.antcolonies.colony.ColonyHooks.UNDERGROUND, List.of("galleries")),
                Map.entry(com.antcolonies.colony.ColonyHooks.FEEDING_WAYS, List.of("galleries")));
        for (Map.Entry<com.antcolonies.colony.ColonyHooks.Kind<?>, List<String>> kind : plugged.entrySet()) {
            List<String> plugging = kind.getValue().stream().filter(has::contains).toList();
            helper.assertTrue(colony.hooks().pluggedInto(kind.getKey()).equals(plugging),
                    "a " + colony.getSpecies() + " colony's socket " + kind.getKey() + " should be plugged by " + plugging + ", not "
                            + colony.hooks().pluggedInto(kind.getKey()));
        }
        // Each species' own checks (the split, step S4: signed up by its tests, SavannaTests and DesertTests): its
        // nurses' food, mound, trails, alarm, king, soldiers, bodies and crust are today's.
        PARTS_CHECKS.forEach(check -> check.accept(helper, colony));
        // Step 8b: a colony whose nurse food socket nobody plugs has no nurse food.
        helper.assertTrue(!colony.hooks().pluggedInto(com.antcolonies.colony.ColonyHooks.NURSE_FOOD).isEmpty() || colony.nurseFood() == null,
                "a " + colony.getSpecies() + " colony's nurses' food should be nothing, not " + colony.nurseFood());
        helper.assertTrue(!colony.isOwnCaste(com.antcolonies.colony.Caste.WORKER) && !colony.isOwnCaste(null),
                "a worker is never one of a species' own castes beyond the worker");
        // Step 10: the young body keeps only a caste's number from the castes hook, the soldiers' as synced and saved
        // before (1 minor, 2 major); every caste its own number, there and back; a worker, or a species without, 0.
        java.util.Set<Byte> numbers = new java.util.HashSet<>();
        for (com.antcolonies.colony.ColonyHooks.Castes castes : colony.hooks().all(com.antcolonies.colony.ColonyHooks.CASTES)) {
            for (String caste : castes.castes()) {
                byte number = colony.casteNumber(caste);
                helper.assertTrue(number != 0 && numbers.add(number) && caste.equals(colony.casteOfNumber(number)),
                        "a " + colony.getSpecies() + " colony's " + caste + " should have its own caste number, not " + number);
            }
        }
        helper.assertTrue(colony.casteNumber(com.antcolonies.colony.Caste.WORKER) == 0 && colony.casteNumber(null) == 0
                && colony.casteOfNumber(0) == null, "a worker's caste number is always 0");
    }

    /** The parts of a colony saved into a tag of their own each, by name (the parts that are not saved left out). */
    static Map<String, String> partSaves(Colony colony) {
        Map<String, String> saves = new java.util.LinkedHashMap<>();
        for (com.antcolonies.colony.ColonyPart<?> part : colony.parts()) {
            if (part.isSaved()) {
                CompoundTag own = new CompoundTag();
                part.save(own);
                saves.put(part.name(), canon(own));
            }
        }
        return saves;
    }

    /**
     * Project Apex, colony parts step 1: every part is saved and loaded, with something in it. A copy of the colony is
     * loaded from its save (the live colony is left alone) and each part's test sample is put in, for the parts the
     * scene leaves empty ({@code ColonyPart.sample}, in {@code ColonyParts}); then no saved part may be empty (as a
     * new colony's), and each part of the copy, saved and loaded again, must save exactly the same. A part saving
     * nothing, or reading back something else, is named.
     */
    static void everyPartSavesAndLoads(GameTestHelper helper, CompoundTag saved) {
        Colony scratch = Colony.load(saved);
        for (com.antcolonies.colony.ColonyPart<?> part : scratch.parts()) {
            part.fillForTests(helper.getLevel());
        }
        Map<String, String> blank = partSaves(new Colony(scratch.getId(), scratch.getSpecies(), scratch.getQueenId(), scratch.getCenter()));
        Map<String, String> mine = partSaves(scratch);
        List<String> empty = new ArrayList<>();
        mine.forEach((name, save) -> {
            if (save.equals(blank.get(name))) {
                empty.add(name);
            }
        });
        helper.assertTrue(empty.isEmpty(), "every saved colony part should have something in it for the save and load check (give it a "
                + "sample in ColonyParts): empty " + empty);
        Map<String, String> again = partSaves(Colony.load(scratch.save()));
        List<String> differ = new ArrayList<>();
        mine.forEach((name, save) -> {
            if (!save.equals(again.get(name))) {
                differ.add(name + ": " + save + " -> " + again.get(name));
            }
        });
        helper.assertTrue(differ.isEmpty(), "every colony part loaded from its save should save the same: " + differ);
        ColonyFrameworkMod.LOGGER.info("[parts-test] {}: all {} saved parts saved and loaded the same", scratch.getSpecies(), mine.size());
    }

    /** The colony with this id (also while its queen is frozen, or dead). */
    public static Colony byId(GameTestHelper helper, UUID id) {
        Colony colony = ColonyManager.get(helper.getLevel()).getColony(id);
        helper.assertTrue(colony != null, "the colony is gone");
        return colony;
    }

    /**
     * Brain part 3, founding while away (species-neutral): a lone harvester ant queen, or a termite royal pair, digs the
     * founding chamber and closes the entrance (real insects, as in the founding tests). Then the colony goes away: the
     * queen lays the rest of her founding batch as numbers, the founders feed the young from their own fat, and they
     * grow up into the first workers, all while away; the queen does not starve (she lives on her fat). Back in range the
     * first workers come out as real ones and dig the entrance open, as in a real claustral founding.
     * <p>Grass in the entrance (the user's bug, 2026-10-15): the founders start on a tuft of short grass. The ant queen is
     * then put two blocks off it, with the grass grown back (before the fix she never dug: nobody could stand over the
     * shaft's first block), and she has to bite it away and dig as before.
     * <p>Founding while away (the user's bug, 2026-10-15: a founding nest dug nothing while away): the termite pair go
     * away at once, before they have dug anything. While away they dig their chamber and close themselves in at the real
     * pace (the report says so), and the queen lays her batch; back home they are in their chamber, the grass is gone.
     */
    public static void foundsWhileAway(GameTestHelper helper, Founders founders) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestScenes.deepGround(helper);
        helper.setBlock(MIDDLE, SURFACE, MIDDLE, net.minecraft.world.level.block.Blocks.SHORT_GRASS);
        founders.spawn().accept(helper);
        UUID[] id = new UUID[1];
        long[] at = {0, 0}; // when sealed in; when away (or back)
        int[] phase = {0}; // 0 founding (here), 1 away, 3 still away, living on (ants), 2 back
        int[] eggsBefore = new int[1];
        Map<UUID, CompoundTag> unloaded = new HashMap<>();
        helper.onEachTick(() -> {
            if (id[0] == null) {
                Colony colony = colony(helper, founders.species());
                if (colony == null) {
                    return;
                }
                id[0] = colony.getId();
                colony.away().setOnForTests(true); // (here: the nest's chunks are loaded)
                colony.population().setOnForTests(true);
                colony.population().setViewersForTests(List.of());
                if (!founders.awayAtOnce()) {
                    // She steps off her spot and the grass is still there (or grows back).
                    AbstractAntEntity queen = colony.getQueen(level);
                    BlockPos aside = helper.absolutePos(new BlockPos(MIDDLE + 2, SURFACE, MIDDLE));
                    queen.moveTo(aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5, 0.0F, 0.0F);
                    level.setBlock(colony.getCenter(), net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState(), 3);
                    helper.assertTrue(colony.nest().isPlantInEntrance(level), "the grass should be in the entrance");
                    // Grass round the nest the brain sees while here, for the first workers to forage while away.
                    for (int i = 0; i < 12; i++) {
                        BlockPos tuft = helper.absolutePos(new BlockPos(3 + 3 * (i % 4), SURFACE, 3 + 8 * (i / 4) - (i / 4 == 1 ? 1 : 0)));
                        if (tuft.distManhattan(colony.getCenter()) > 3) {
                            level.setBlock(tuft, net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState(), 3);
                        }
                    }
                    colony.food().rescanPlantsSoon();
                    colony.field().setOnForTests(true);
                }
            }
            Colony colony = byId(helper, id[0]);
            int eggs = colony.profile().founding().orElseThrow().eggs();
            if (phase[0] == 0 && founders.awayAtOnce()) {
                // The pair go away at once: the king has joined, the brain has seen what they have to dig.
                if (colony.getCount(founders.partnerCaste()) == 0 || colony.life().foundingWorkLeft() <= 0) {
                    return;
                }
                helper.assertTrue(colony.isFounding() && colony.nest().getHomeChamber(level) == null, "the pair should not have dug their chamber yet");
                ColonyFrameworkMod.LOGGER.info("[away-test] founding: the pair go away at tick {} with {} ticks of work left", helper.getTick(),
                        colony.life().foundingWorkLeft());
                List<Entity> all = new ArrayList<>();
                all.add(colony.getQueen(level));
                if (founders.partner() != null) {
                    all.addAll(level.getEntitiesOfClass(founders.partner(), helper.getBounds(),
                            king -> colony.getId().equals(king.getColonyId())));
                }
                for (Entity entity : all) {
                    unloaded.put(entity.getUUID(), freezeAndUnload(colony, level, entity));
                }
                colony.away().setGraceForTests(20L);
                colony.away().setAwayForTests(true);
                at[1] = helper.getTick();
                phase[0] = 1;
                return;
            }
            if (phase[0] == 0) {
                boolean sealed = !colony.nest().isEntranceOpen(level) && colony.queenCare().isQueenHome(level);
                if (!sealed) {
                    at[0] = 0;
                    return;
                }
                if (at[0] == 0) {
                    at[0] = helper.getTick();
                    ColonyFrameworkMod.LOGGER.info("[away-test] founding: sealed in at tick {}", helper.getTick());
                }
                if (helper.getTick() - at[0] < 60) {
                    return; // (the brain notes once a second that she is sealed in her room)
                }
                helper.assertTrue(colony.queenCare().getEggsLaid() < eggs, "the whole batch was laid before the colony went away");
                // Out of range: the founders and any eggs or young already laid are frozen.
                eggsBefore[0] = colony.queenCare().getEggsLaid();
                List<Entity> all = new ArrayList<>();
                all.add(colony.getQueen(level));
                if (founders.partner() != null) {
                    all.addAll(level.getEntitiesOfClass(founders.partner(), helper.getBounds(),
                            king -> colony.getId().equals(king.getColonyId())));
                }
                all.addAll(colony.brood().getEggs(level));
                all.addAll(colony.brood().getLarvae(level));
                for (Entity entity : all) {
                    unloaded.put(entity.getUUID(), freezeAndUnload(colony, level, entity));
                }
                colony.away().setGraceForTests(20L);
                colony.away().setAwayForTests(true);
                at[1] = helper.getTick();
                phase[0] = 1;
                return;
            }
            if (phase[0] == 1) {
                AwayReport report = colony.away().report();
                if (helper.getTick() % 400 == 0) {
                    ColonyFrameworkMod.LOGGER.info("[away-test] founding away: eggs laid {}, {}{}", colony.queenCare().getEggsLaid(),
                            colony.population().describe(level), colony.away().describe(level));
                }
                helper.assertTrue(helper.getTick() - at[1] < 15000, "the founding batch should grow up while away: eggs laid "
                        + colony.queenCare().getEggsLaid() + ", " + colony.population().describe(level) + colony.away().describe(level));
                if (colony.getCount(Caste.WORKER) < eggs || colony.brood().insideCount() > 0) {
                    return;
                }
                helper.assertTrue(colony.queenCare().getEggsLaid() == eggs && report.get(AwayReport.What.EGGS_LAID) == eggs - eggsBefore[0],
                        "the queen should lay the rest of her batch while away: " + colony.queenCare().getEggsLaid() + " laid, " + report.describe());
                helper.assertTrue(report.get(AwayReport.What.GREW_UP) == eggs && report.get(AwayReport.What.YOUNG_FED) > 0,
                        "the founders should feed the young from their fat until they grow up: " + report.describe());
                helper.assertTrue(colony.getCount(Caste.WORKER) == eggs && colony.population().inside(Caste.WORKER) == eggs,
                        "the first " + eggs + " workers should be numbers: " + colony.population().describe(level));
                helper.assertTrue(!report.describe().contains("the queen starved") && !colony.isFounding(), "the queen should live on her fat: "
                        + report.describe());
                helper.assertTrue(!founders.awayAtOnce() || report.didFoundersSeal(), "the pair should have dug their chamber and closed themselves in while away: "
                        + report.describe());
                if (!founders.awayAtOnce()) {
                    // The user's bug (2026-10-15): the first workers starved while away, as the brain saw no food store to
                    // forage into. They stay away past their starve time: they dig the store, forage and eat.
                    helper.assertTrue(colony.life().storeWorkLeft() > 0, "the brain should know the food store is not dug yet");
                    at[1] = helper.getTick();
                    phase[0] = 3;
                    return;
                }
                colony.away().setAwayForTests(false);
                unloaded.values().forEach(tag -> loadAgain(level, tag));
                at[1] = helper.getTick();
                phase[0] = 2;
                return;
            }
            if (phase[0] == 3) {
                AwayReport report = colony.away().report();
                long starve = colony.profile().worker().starveAfterTicks();
                if (helper.getTick() % 2000 == 0) {
                    ColonyFrameworkMod.LOGGER.info("[away-test] founding, living on: store work left {}, food {}{}{}", colony.life().storeWorkLeft(),
                            colony.food().getFood(), colony.away().describe(level), colony.field().describe());
                }
                helper.assertTrue(report.get(AwayReport.What.STARVED) == 0 && colony.getCount(Caste.WORKER) >= eggs,
                        "the first workers should not starve while away: " + report.describe() + ", store work left "
                                + colony.life().storeWorkLeft() + colony.field().describe());
                if (helper.getTick() - at[1] < starve + 12000) {
                    return;
                }
                helper.assertTrue(colony.life().storeWorkLeft() == 0, "the workers should have dug the food store while away: "
                        + colony.life().storeWorkLeft() + " ticks of work left");
                helper.assertTrue(report.get(AwayReport.What.FORAGED) > 0, "the first workers should forage while away: " + report.describe());
                colony.away().setAwayForTests(false);
                unloaded.values().forEach(tag -> loadAgain(level, tag));
                at[1] = helper.getTick();
                phase[0] = 2;
                return;
            }
            // Back: the first workers come out as real ones and open the entrance.
            int real = level.getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds(),
                    mob -> mob.isAlive() && colony.getId().equals(mob.getColonyId()) && Caste.WORKER.equals(mob.nestCaste())).size();
            if (colony.getQueen(level) != null && real == eggs && colony.nest().isEntranceOpen(level)) {
                helper.assertTrue(colony.getCount(Caste.WORKER) == eggs, "still " + eggs + " workers: " + colony.getCount(Caste.WORKER));
                helper.assertTrue(colony.queenCare().isQueenHome(level), "the queen should be in her chamber: " + colony.getQueen(level).blockPosition());
                helper.assertTrue(!colony.nest().isPlantInEntrance(level), "the grass in the entrance should be gone");
                if (founders.partner() != null) {
                    AbstractAntEntity queen = colony.getQueen(level);
                    helper.assertTrue(!level.getEntitiesOfClass(founders.partner(), queen.getBoundingBox().inflate(2.0),
                            king -> king.isAlive() && colony.getId().equals(king.getColonyId())).isEmpty(), "the king should be with the queen");
                }
                helper.succeed();
            }
            helper.assertTrue(helper.getTick() - at[1] < 15000, "back: " + real + " real workers, entrance open " + colony.nest().isEntranceOpen(level)
                    + ", queen " + colony.getQueen(level) + colony.population().describe(level));
        });
    }
}
