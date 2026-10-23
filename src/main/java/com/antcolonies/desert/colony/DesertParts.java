package com.antcolonies.colony;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.antcolonies.colony.ColonyParts.Type;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The desert's colony parts (the split, step S3, {@code docs/plans/mod-split.md}): the harvester ant's hill, bakery,
 * trunk trails, patrol, forager spots, door and recruits, signed up in the shared parts registry
 * ({@link ColonyParts#register}) after the shared parts, in today's order. A species file lists the ones its colonies
 * have under {@code "parts"}.
 */
public final class DesertParts {
    public static final Type<Hill> HILL = listed("hill", Hill::new, part -> part
            .plugs(ColonyHooks.AWAY_BUILDER, 10, HillAwayBuilding::new) // the night soil and the dug soil heaped on the hill, on return
            .plugs(ColonyHooks.LIST_DAY, 30, hill -> level -> hill.nightSoil().waiting(level) > 0 ? " | night soil in piles: " + hill.nightSoil().waiting(level)
                    + " of at most " + hill.nightSoil().limit(level) + " pellets" : "")
            .plugs(ColonyHooks.DUG_SOIL, 20, hill -> new ColonyHooks.DugSoil() { // a real digger's pellets go onto the hill while it has room
                @Override
                public boolean waits(ServerLevel level, com.antcolonies.entity.AbstractAntEntity digger) {
                    return hill.soilWaits(level, digger); // the morning clear-out
                }

                @Override
                public boolean takesPellet(ServerLevel level, BlockPos target, java.util.UUID digger) {
                    return hill.hasMoundRoom(level);
                }
            })
            .ticks(40, (hill, level) -> {
                if (!hill.colony().buildsMound()) {
                    hill.nightSoil().tick(level);
                }
            })
            .saves(Hill::save, Hill::load)
            .sample((hill, level) -> hill.nightSoil().fillForTests(level)));
    public static final Type<Bakery> BAKERY = listed("bakery", Bakery::new, part -> part
            .hears(ColonyNews.PIECE_TAKEN, 10, (bakery, level, news) -> {
                if (news.rich()) {
                    bakery.addRichMiller(news.ant()); // its miller makes rich bread
                }
            })
            .hears(ColonyNews.PIECE_EATEN, 10, (bakery, level, news) -> bakery.removeRichMiller(news.ant())) // eaten, not milled
            .plugs(ColonyHooks.NURSE_FOOD, 20, bakery -> bakery) // ant bread (after a garden's nodules, as before)
            .plugs(ColonyHooks.LIST_STORE, 10, bakery -> level -> " | bread " + bakery.getBread() + " (rich " + bakery.getRichBread() + ", milling " + bakery.getMilling() + ")")
            .plugs(ColonyHooks.MILL, 10, bakery -> new ColonyHooks.Mill() { // a forager's seed goes to the mill if the store has no room
                @Override
                public boolean hasPlace(ServerLevel level, java.util.UUID ant) {
                    return bakery.hasPlaceForASeed(level, ant);
                }

                @Override
                public boolean milling(java.util.UUID ant) {
                    return bakery.isMilling(ant);
                }
            })
            .plugs(ColonyHooks.NURSE_MAKES, 10, bakery -> (level, life) -> bakery.millAway(level)) // the nurses mill seeds into bread while away
            .answers(ColonyQuestions.MORE_FOOD_WANTED, 10, (bakery, level, nothing) -> bakery.needsBread((ServerLevel) level)) // bread still to mill
            .saves(Bakery::save, Bakery::load)
            .sample((bakery, level) -> {
                bakery.countIn(true);
                bakery.countIn(false);
            }));
    public static final Type<TrunkTrails> TRUNK_TRAILS = listed("trunk_trails", TrunkTrails::new, part -> part
            .ticks(210, (trunk, level) -> trunk.tick(level, false))
            .ticksAway(80, (trunk, level) -> trunk.tick(level, true))
            .answers(ColonyQuestions.KEEP_OFF, 40, TrunkTrails::keepsOff) // the trail out there passes danger
            .answers(ColonyQuestions.KEEPS_TRAILS, 10, (trunk, level, nothing) -> trunk.keepsTrails())
            .plugs(ColonyHooks.REACH, 10, trunk -> trunk::longestTip) // the range is the trails (long trails, 2026-10-17)
            .plugs(ColonyHooks.TRAILS, 10, trunk -> new ColonyHooks.Trails() {
                @Override
                public double walkTo(BlockPos pos) {
                    return trunk.walkTo(pos);
                }

                @Override
                public List<int[]> columnsNear(int side, int radius) {
                    return trunk.columnsNear(side, radius);
                }

                @Override
                public BlockPos setOut(ServerLevel level, BlockPos plant) {
                    return trunk.setOut(level, plant);
                }

                @Override
                public BlockPos wayOut(ServerLevel level, BlockPos toward) {
                    return trunk.wayOut(level, toward);
                }

                @Override
                public BlockPos wayHome(ServerLevel level, BlockPos from) {
                    return trunk.wayHome(level, from);
                }
            })
            .plugs(ColonyHooks.LIST_SEARCH, 30, trunk -> trunk::describe)
            .answers(ColonyQuestions.ON_TRAIL, 10, (trunk, level, pos) -> trunk.meetsAt(pos)) // a neighbour's ant on a trail meets this colony
            .hears(ColonyNews.NUMBERS_TRIP, 10, (trunk, level, news) -> trunk.tripByNumbers(level, news.plant())) // their trip out and back wears the trail too
            .saves(TrunkTrails::save, TrunkTrails::load)
            .sample(TrunkTrails::fillForTests));
    public static final Type<Patrol> PATROL = listed("patrol", Patrol::new, part -> part
            .ticks(140, (patrol, level) -> patrol.tick(level, false))
            .ticksAway(50, (patrol, level) -> patrol.tick(level, true))
            .hears(ColonyNews.MEMBER_HURT, 20, (patrol, level, news) -> patrol.danger(level, news.member(), "hurt by " + news.by().getType().toShortString())) // a patroller turns back
            .hears(ColonyNews.MEMBER_DIED, 10, (patrol, level, news) -> patrol.died(level, news.member())) // a patroller killed never comes back: its way stays shut today
            .answers(ColonyQuestions.NOT_CLEARED, 10, (patrol, level, pos) -> !patrol.isOpen(level, pos)) // no patroller back from that way yet
            .plugs(ColonyHooks.LIST_DAY, 10, patrol -> patrol::describe)
            .saves(Patrol::save, Patrol::load)
            .sample(Patrol::fillForTests));
    public static final Type<ForagerSpots> FORAGER_SPOTS = listed("forager_spots", ForagerSpots::new, part -> part
            .hears(ColonyNews.MEMBER_HURT, 40, (spots, level, news) -> spots.memberHurt(news.member().getUUID(), news.where())) // hurt near its spot: gives it up
            .hears(ColonyNews.MEMBER_DIED, 20, (spots, level, news) -> spots.died(news.member())) // its spot is lost with it
            .hears(ColonyNews.FOOD_FOUND, 10, (spots, level, news) -> spots.found(news.finder(), news.where())) // its spot now (site fidelity, species that search alone)
            .hears(ColonyNews.INTO_NUMBERS, 10, (spots, level, news) -> spots.intoNumbers(news.member())) // its spot goes into the numbers' pool
            .hears(ColonyNews.OUT_OF_NUMBERS, 10, (spots, level, news) -> spots.outOfNumbers(news.member())) // a forager takes a spot from the numbers' pool
            .hears(ColonyNews.FORAGER_NUMBERS, 10, (spots, level, news) -> spots.trimHeld(news.foragers())) // fewer forager numbers than spots held: some died or came out as real ones
            .plugs(ColonyHooks.NUMBERS_SEARCH, 10, SearchingAlone::new) // the numbers search alone and go back to their spots
            .plugs(ColonyHooks.LIST_SEARCH, 10, spots -> level -> spots.describe())
            .plugs(ColonyHooks.LONE_SEARCH, 10, ForagerSearch::new) // a real forager's search alone: its own spot, a trail or a new way
            .hears(ColonyNews.SEARCH_STARTED, 10, (spots, level, news) -> spots.setSearching(news.ant(), true))
            .hears(ColonyNews.SEARCH_OVER, 20, (spots, level, news) -> {
                spots.setSearching(news.ant().getUUID(), false);
                if (news.empty()) {
                    spots.cameHomeEmpty(news.ant().getUUID(), news.angle()); // its next search goes another way, from near the nest again
                }
            })
            .saves(ForagerSpots::save, ForagerSpots::load)
            .sample(ForagerSpots::fillForTests));
    public static final Type<Door> DOOR = listed("door", Door::new, part -> part
            .ticks(150, Door::tick)
            .hears(ColonyNews.NUMBERS_FORAGED, 10, (door, level, news) -> door.numbersSecond(level, news.foragers(), news.returned(), news.placed()))
            .plugs(ColonyHooks.DOOR_RULE, 10, door -> door::numbersFree) // Gordon's rule
            .plugs(ColonyHooks.LIST_DAY, 20, door -> door::describe)
            .answers(ColonyQuestions.MUST_WAIT, 10, (door, level, ant) -> !door.mayGo((ServerLevel) level, ant)) // waits for the homecomers' touches
            .hears(ColonyNews.SET_OUT, 10, (door, level, news) -> door.setOut(level, news.ant()))
            .hears(ColonyNews.SEARCH_OVER, 30, (door, level, news) -> {
                if (news.empty() && news.gaveUp()) {
                    door.endTrip(news.ant()); // its trip is over: it goes in and waits for touches again
                }
            })
            .hears(ColonyNews.MEMBER_ROUND, 10, (door, level, news) -> door.memberRound(level, news.member())) // a forager home with a seed touches the ants waiting inside
            .saves(Door::save, Door::load)
            .sample(Door::fillForTests));
    public static final Type<Recruits> RECRUITS = listed("recruits", Recruits::new, part -> part
            .ticks(160, Recruits::tick)
            .hears(ColonyNews.MEMBER_DIED, 30, (recruits, level, news) -> recruits.died(news.member()))
            .hears(ColonyNews.FOOD_FOUND, 20, (recruits, level, news) -> recruits.found(level, news.finder(), news.where())) // a rich patch: it lays a scent line home and calls nestmates out
            .answers(ColonyQuestions.PASSES_BY, 10, (recruits, level, asked) -> recruits.passesBy(asked.ant(), asked.food())) // called out: poorer food on the way is passed by
            .plugs(ColonyHooks.CALLED_OUT, 10, recruits -> recruits::lineFor)
            .plugs(ColonyHooks.LIST_SEARCH, 20, recruits -> recruits::describe)
            .hears(ColonyNews.SEARCH_OVER, 10, (recruits, level, news) -> recruits.tripOver(news.ant().getUUID())) // its call is used up
            .hears(ColonyNews.MEMBER_ROUND, 20, (recruits, level, news) -> {
                if (recruits.isLaying(news.member().getUUID())) {
                    recruits.layingTick(level, news.member()); // home from a rich patch: its scent line, then its call
                }
            })
            .notSaved("a scent line fades within minutes"));

    private DesertParts() {
    }

    /** Signs the parts above up in the parts registry (called once at start; the first use of the class does it). */
    public static void signUp() {
    }

    private static <T> Type<T> listed(String name, Function<Colony, T> make, UnaryOperator<ColonyPart<T>> rows) {
        return ColonyParts.register(name, false, make, rows);
    }
}
