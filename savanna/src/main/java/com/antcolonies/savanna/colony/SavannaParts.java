package com.antcolonies.savanna.colony;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyHooks;
import com.antcolonies.colony.ColonyNews;
import com.antcolonies.colony.ColonyPart;
import com.antcolonies.colony.ColonyParts;
import com.antcolonies.colony.ColonyQuestions;
import com.antcolonies.colony.Population;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.antcolonies.colony.ColonyParts.Type;

import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The savanna's colony parts (the split, step S3, {@code docs/plans/mod-split.md}): the termites' galleries, mound,
 * fungus garden, soldiers, alarm and the rest, signed up in the shared parts registry ({@link ColonyParts#register})
 * after the shared parts, in today's order. A species file lists the ones its colonies have under {@code "parts"}.
 */
public final class SavannaParts {
    /** The split, step S2: termites underground, the web of galleries round the nest (was held by the nest; same ticks, same save). */
    public static final Type<Galleries> GALLERIES = listed("galleries", Galleries::new, part -> part
            .ticks(90, Galleries::tick)
            .ticksAway(20, Galleries::tickAway)
            .plugs(ColonyHooks.UNDERGROUND, 10, galleries -> galleries) // asked only while switched on (Colony#underground)
            .plugs(ColonyHooks.FEEDING_WAYS, 10, galleries -> FeedingGallery.WAYS) // (switched on or not, as before)
            .plugs(ColonyHooks.LIST_HOLES, 10, galleries -> galleries::describe)
            .hears(ColonyNews.COLONY_ENDED, 30, (galleries, level, news) -> // M8 1a part 3: its holes, mud and galleries weather
                    DeadWorks.colonyEnded(level, galleries.colony, news.center()))
            .saves(Galleries::save, Galleries::load));
    public static final Type<KingCare> KING_CARE = listed("king_care", KingCare::new, part -> part
            .plugs(ColonyHooks.CO_FOUNDERS, 10, king -> new ColonyHooks.CoFounders() { // he digs beside the queen and seals them in
                @Override
                public int count() {
                    return king.isAlive() ? 1 : 0;
                }

                @Override
                public boolean isOne(com.antcolonies.entity.AbstractAntEntity insect) {
                    return insect instanceof com.antcolonies.savanna.entity.TermiteKingEntity;
                }

                @Override
                public String caste() {
                    return com.antcolonies.savanna.entity.TermiteKingEntity.CASTE;
                }
            })
            .plugs(ColonyHooks.AWAY_BESIDE_QUEEN, 10, king -> king::liveAway) // his hunger clock runs on while away
            .plugs(ColonyHooks.NURSE_FEEDS, 10, king -> king::fedAway) // the nurses feed him while away, after the queen
            .saves(KingCare::save, KingCare::load));
    public static final Type<Mound> MOUND = listed("mound", Mound::new, part -> part
            .ticks(30, Mound::tick)
            .hears(ColonyNews.BLOCK_DUG, 10, (mound, level, news) -> mound.blockDug(level, news.where()))
            .hears(ColonyNews.COLONY_ENDED, 10, (mound, level, news) -> // M8 1a: it stays, dead, and wears down
                    DeadMounds.get(level).addMound(level, news.colony(), news.center(), mound.getParts(level)))
            .plugs(ColonyHooks.AWAY_BUILDER, 20, MoundAwayBuilding::new) // the dug soil and the mound built up, on return
            .plugs(ColonyHooks.DUG_SOIL, 10, mound -> new ColonyHooks.DugSoil() { // one pellet per layer of a mound block from each dug block
                @Override
                public boolean takesPellet(ServerLevel level, BlockPos target, java.util.UUID digger) {
                    return mound.numbers() != null && mound.takePellet(level, target, digger);
                }

                @Override
                public net.minecraft.world.level.block.state.BlockState pellet(net.minecraft.world.level.block.state.BlockState dug) {
                    return dug.getBlock() instanceof com.antcolonies.savanna.block.TermiteMoundBlock || dug.getBlock() instanceof com.antcolonies.savanna.block.TermiteHoleBlock
                            || dug.getBlock() instanceof com.antcolonies.savanna.block.MudCrustBlock
                            ? com.antcolonies.savanna.registry.SavannaBlocks.TERMITE_MOUND.get().defaultBlockState() : dug;
                }
            })
            .answers(ColonyQuestions.BUILDS_OVER_NEST, 10, (mound, level, nothing) -> mound.numbers() != null)
            .answers(ColonyQuestions.BUILDING_OVER_NEST, 10, (mound, level, nothing) -> mound.isActive())
            .answers(ColonyQuestions.CLEAR_JOB, 10, Mound::isClearJob) // its own soil where it must be hollow
            .answers(ColonyQuestions.STRUCTURE_TUNNEL, 10, (mound, level, pos) -> mound.getGalleries(level).contains(pos))
            .plugs(ColonyHooks.LIST_BUILDING, 10, mound -> mound::describe)
            .plugs(ColonyHooks.CRUST, 10, mound -> com.antcolonies.savanna.registry.SavannaBlocks.MUD_CRUST.get())
            .plugs(ColonyHooks.STRUCTURE, 10, mound -> new ColonyHooks.Structure() {
                @Override
                public Map<BlockPos, MoundPlan.Part> parts(Level level) {
                    return mound.getParts(level);
                }

                @Override
                public void addInside(Level level, Set<BlockPos> cells, Set<BlockPos> channels) {
                    mound.addInside(level, cells, channels);
                }

                @Override
                public float mouthTripChance() {
                    return mound.numbers() == null ? 1.0F : mound.numbers().chimneyTripChance(); // the mound's own numbers (T1)
                }

                @Override
                public boolean isChannel(net.minecraft.world.level.block.state.BlockState state) {
                    return MoundReach.channelKind(state) != null;
                }

                @Override
                public List<BlockPos> tunnels(Level level) {
                    return mound.getGalleries(level);
                }

                @Override
                public BlockPos digTarget(Level level, java.util.UUID digger) {
                    return mound.findDigTarget(level, digger);
                }
            })
            .saves(Mound::save, Mound::load)
            .sample(Mound::fillForTests));
    public static final Type<ClosedHoles> CLOSED_HOLES = listed("closed_holes", ClosedHoles::new, part -> part
            .ticks(180, ClosedHoles::tick)
            .answers(ColonyQuestions.KEEP_OFF, 20, ClosedHoles::keepsOff) // where members were attacked
            .answers(ColonyQuestions.HOLE_KEPT_SHUT, 10, ClosedHoles::isClosed) // closed for danger or left for good
            .answers(ColonyQuestions.ANY_HOLE_KEPT_SHUT, 10, (closed, level, nothing) -> closed.anyClosed(level))
            .answers(ColonyQuestions.NO_GALLERY_THROUGH, 10, ClosedHoles::keepsGalleriesOff) // not toward an attack
            .plugs(ColonyHooks.LIST_DANGER, 10, closed -> closed::describe)
            .saves(ClosedHoles::save, ClosedHoles::load)
            .sample(ClosedHoles::fillForTests));
    public static final Type<HiddenTrips> HIDDEN_TRIPS = listed("hidden_trips", HiddenTrips::new, part -> part
            .plugs(ColonyHooks.HIDDEN_AWAY, 10, trips -> new ColonyHooks.HiddenAway() {
                @Override
                public int travelling(String caste) {
                    return trips.travelling(caste);
                }

                @Override
                public boolean anyWalksThrough(Set<BlockPos> cells) {
                    return trips.anyWalksThrough(cells);
                }

                @Override
                public com.antcolonies.nav.HiddenWays ways(ServerLevel level) {
                    return trips.ways(level);
                }
            })
            .plugs(ColonyHooks.LIST_HOLES, 20, trips -> level -> trips.describe())
            .ticks(100, HiddenTrips::tick)
            .ticksAway(10, HiddenTrips::tickAway)
            .saves(HiddenTrips::save, HiddenTrips::load)
            .sample(HiddenTrips::fillForTests));
    public static final Type<FungusGarden> FUNGUS_GARDEN = listed("fungus_garden", FungusGarden::new, part -> part
            .ticks(20, FungusGarden::grow)
            .hears(ColonyNews.HEAP_GONE, 10, (garden, level, news) -> garden.loseNodules(news.nodules())) // a comb eaten right down: its nodules go with it
            .hears(ColonyNews.COLONY_ENDED, 20, (garden, level, news) -> // M8 1a: Xylaria takes the dead garden
                    DeadMounds.get(level).addCombs(level, news.colony(), news.center(), garden.getCombs(level)))
            .plugs(ColonyHooks.AWAY_AFTER_NURSES, 10, garden -> new GardenAway(garden)::grow) // nodules grow and the comb ages while away
            .answers(ColonyQuestions.NOTHING_TO_EAT_IN_STORE, 10, (garden, level, nothing) -> garden.oldKnown() <= 0) // no old layer as far as the brain knows
            .plugs(ColonyHooks.NURSE_FOOD, 10, garden -> garden) // fungus nodules
            .plugs(ColonyHooks.GARDEN, 10, garden -> new ColonyHooks.Garden() {
                @Override
                public List<BlockPos> beds(Level level) {
                    return garden.getCombs(level);
                }

                @Override
                public ColonyHooks.Ages age(ServerLevel level, List<net.minecraft.world.phys.Vec3> eyes, ColonyHooks.Ages owed, int most) {
                    return garden.age(level, eyes, owed, most);
                }
            })
            .plugs(ColonyHooks.LIST_FOOD, 10, garden -> garden::describe)
            .saves(FungusGarden::save, FungusGarden::load)
            .sample(FungusGarden::fillForTests));
    public static final Type<Mushrooms> MUSHROOMS = listed("mushrooms", Mushrooms::new, part -> part
            .ticks(50, Mushrooms::tick)
            .notSaved("only the weather at the last look and the last flush, for the colony list"));
    public static final Type<Soldiers> SOLDIERS = listed("soldiers", Soldiers::new, part -> part
            .plugs(ColonyHooks.CASTES, 10, soldiers -> new ColonyHooks.Castes() {
                @Override
                public List<String> castes() {
                    return List.of(com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank.MINOR.caste(), com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank.MAJOR.caste());
                }

                @Override
                public int mealFor(String caste) {
                    return soldiers.mealFor(com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank.byCaste(caste));
                }

                @Override
                public int mealIntervalTicks(String caste) {
                    return soldiers.numbers() == null ? 0 : soldiers.numbers().mealIntervalTicks(); // the soldiers' own numbers (T1)
                }

                @Override
                public String wanted(Level level) {
                    com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank rank = soldiers.rankForNewNymph(level);
                    return rank == null ? null : rank.caste();
                }

                @Override
                public String savedAs(String caste) {
                    com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank rank = com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank.byCaste(caste);
                    return rank == null ? null : rank.name(); // "MINOR", "MAJOR", as saved before step 9c
                }

                @Override
                public String fromSaved(String word) {
                    for (com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank rank : com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank.values()) {
                        if (rank.name().equals(word)) {
                            return rank.caste();
                        }
                    }
                    return null;
                }

                @Override
                public byte numberOf(String caste) {
                    com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank rank = com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank.byCaste(caste);
                    return rank == null ? 0 : rank.id(); // 1 minor, 2 major, as synced and saved before step 10
                }

                @Override
                public String casteOf(int number) {
                    com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank rank = com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank.byId(number);
                    return rank == null ? null : rank.caste();
                }

                @Override
                public int wantReal(ServerLevel level, boolean watched, int cap, java.util.Map<String, Integer> wanted) {
                    return soldiers.wantReal(level, watched, cap, wanted);
                }

                @Override
                public String inside(Population population) {
                    return soldiers.inside(population);
                }

                @Override
                public List<? extends net.minecraft.world.entity.Entity> fedByNurses(Level level) {
                    return soldiers.getSoldiers(level); // fed head to head like the queen
                }

                @Override
                public String addFor(int workers, long now) {
                    return soldiers.addFor(workers, now);
                }
            })
            .plugs(ColonyHooks.LIST_BROOD, 10, soldiers -> soldiers::describe)
            .notSaved("the soldiers simply take their posts again after a load"));
    public static final Type<Alarm> ALARM = listed("alarm", Alarm::new, part -> part
            .ticks(60, Alarm::tick)
            .hears(ColonyNews.MEMBER_HURT, 10, (alarm, level, news) -> alarm.memberHurt(level, news.by())) // level 2 at the attacker
            .answers(ColonyQuestions.KEEPS_EVERYONE_IN, 10, (alarm, level, nothing) -> alarm.keepsEveryoneIn(level)) // the foragers hurry home
            .plugs(ColonyHooks.LIST_BROOD, 20, alarm -> alarm::describe)
            .answers(ColonyQuestions.ALARMED, 10, (alarm, level, nothing) -> alarm.level(level) != 0) // no grooming, napping or idle looking round
            .hears(ColonyNews.PLAYER_STAMPED, 10, (alarm, level, news) -> alarm.footsteps(level, news.player())) // footsteps, the first level
            .hears(ColonyNews.BLOCK_BROKEN, 10, (alarm, level, news) -> alarm.blockBroken(level, news.where(), news.breaker())) // a break-in, the third level
            .saves(Alarm::save, Alarm::load)
            .sample(Alarm::fillForTests));
    /**
     * The split, step S3: eaten grass growing back and the bare patches round the holes (was held by the food store; same
     * tick place, same save).
     */
    public static final Type<BarePatches> BARE_PATCHES = listed("bare_patches", BarePatches::new, part -> part
            .ticks(110, BarePatches::tick)
            .hears(ColonyNews.PLANT_EATEN, 10, (patches, level, news) -> patches.eatenAway(level, news.where(), news.eaten(), news.eatenAt()))
            .plugs(ColonyHooks.LIST_HOLES, 30, patches -> level -> patches.colony().profile().section(SavannaNumbers.REGROWTH).isPresent() ? ", " + patches.describe() : "")
            .saves(BarePatches::save, BarePatches::load)
            .sample(BarePatches::fillForTests));

    /**
     * M8 step 1e: the Matabele prey pile, stung termites heaped in the queen's room, the workers', the queen's and the
     * larvae's food; rotting in a day, the rotten layers and the leftovers carried out to a heap by the entrance.
     */
    public static final Type<PreyPile> PREY_PILE = listed("prey_pile", PreyPile::new, part -> part
            .ticks(115, PreyPile::tick)
            .ticksAway(85, PreyPile::tickAway)
            .plugs(ColonyHooks.NURSE_FOOD, 30, pile -> pile) // the paralysed termites
            .plugs(ColonyHooks.LIST_STORE, 20, pile -> pile::describe)
            .saves(PreyPile::save, PreyPile::load)
            .sample(PreyPile::fillForTests));

    private SavannaParts() {
    }

    /** Signs the parts above up in the parts registry (called once at start; the first use of the class does it). */
    public static void signUp() {
    }

    private static <T> Type<T> listed(String name, Function<Colony, T> make, UnaryOperator<ColonyPart<T>> rows) {
        return ColonyParts.register(name, false, make, rows);
    }
}
