package com.antcolonies.savanna.colony;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.MatabeleQueenEntity;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A whole Matabele colony made at once (M8 steps 1c and 1f): by the command ({@code /antcolonies matabele <workers>},
 * the ants round the player, walking to the nest) and in new land ({@link #inNewLand}: a colony that has lived in a
 * dead termite mound for a while, the queen in her room, every worker inside as the brain's numbers, coming out as real
 * ants up to the real-mob cap like any colony's).
 *
 * <p>New land, the real ants (Yusuf 2026, Annual Review of Entomology 71): colonies of 100 to 300, up to 2,300 workers;
 * in a Nigerian survey almost half the nests lay in deserted Macrotermes mounds; real nests sit among the termite
 * colonies they raid (nests 2 to 30 m apart, 4 to 22 a hectare, far more than the dead mounds). So most dead mounds of
 * new land in a savanna hold a colony ({@link #SHARE}), and the sizes lean small ({@link #sizeFor}).
 */
public final class MatabeleColonies {
    /** The share of new land's dead mounds (with a way in) that hold a Matabele colony (my pick, to confirm). */
    public static final double SHARE = 2.0 / 3.0;
    /** A new-land colony's size, the real range (Yusuf 2026). */
    public static final int FEWEST = 100, MOST = 2300;

    private MatabeleColonies() {
    }

    /**
     * How many workers a new-land colony has: 100 to 2,300, evenly on a log scale (my pick, to confirm), so small
     * colonies are common and big ones rare, as in the real counts: half are under about 480, one in five over 1,200.
     */
    public static int sizeFor(RandomSource random) {
        return (int) Math.round(FEWEST * Math.pow((double) MOST / FEWEST, random.nextDouble()));
    }

    /**
     * New land: by the dice ({@link #SHARE}, from {@code seed}) a Matabele colony settled in the dead mound whose old
     * entrance was {@code middle}, its queen in her room, all its workers numbers inside, the prey pile holding a day's
     * food (as if a raid came home this morning: real colonies keep no store, Yusuf 2026). With {@code always} (the
     * command) the dice are not asked. Null when the dice say no, the mound has no way in or no rooms, or the queen
     * could not be made.
     */
    @Nullable
    public static Colony inNewLand(ServerLevel level, BlockPos middle, long seed, boolean always) {
        RandomSource random = dice(seed);
        if (random.nextDouble() >= SHARE && !always) {
            return null;
        }
        MatabeleNest.Site site = MatabeleNest.fromMound(level, middle);
        if (site == null) {
            SavannaMod.LOGGER.info("[new-land] no Matabele colony in the dead mound at {}: no way in", middle.toShortString());
            return null;
        }
        NestPlan.Chamber home = site.rooms().get(0); // (the queen's room comes first)
        Colony colony = make(level, Vec3.atBottomCenterOf(home.center()), site, sizeFor(random), false);
        if (colony == null) {
            return null;
        }
        int workers = colony.getCount(Caste.WORKER);
        int laid = colony.part(SavannaParts.PREY_PILE).map(pile -> pile.stock(level, dayOfFood(workers))).orElse(0);
        SavannaMod.LOGGER.info("[new-land] {}", String.format(Locale.ROOT,
                "a Matabele colony of %d workers in the dead mound at %s, %d layers of prey", workers, site.entrance().toShortString(), laid));
        return colony;
    }

    /** Whether the dead mound of new land picked with this seed gets a Matabele colony (if it has a way in). */
    public static boolean wouldSettle(long seed) {
        return dice(seed).nextDouble() < SHARE;
    }

    private static RandomSource dice(long seed) {
        return RandomSource.create(seed ^ 0x6D617461L);
    }

    /** Layers of prey for a day's meals: one termite an ant, about 10 termites a layer (and a layer for the queen). */
    public static int dayOfFood(int workers) {
        return Mth.ceil(workers / 10.0) + 1;
    }

    /**
     * A Matabele queen at {@code queenAt} with her colony, settled in {@code site}, and {@code workers} workers of every
     * age from just grown up to old, all just fed. With {@code realRound}, real ones round her up to the real-mob cap
     * (the command: they walk to the nest), the rest numbers; without, all of them numbers inside (new land). Null if
     * the queen could not be made.
     */
    @Nullable
    public static Colony make(ServerLevel level, Vec3 queenAt, MatabeleNest.Site site, int workers, boolean realRound) {
        MatabeleQueenEntity queen = SavannaEntities.MATABELE_QUEEN.get().create(level);
        if (queen == null) {
            return null;
        }
        Colony colony = MatabeleNest.settle(level, site, queen.getUUID());
        queen.moveTo(queenAt.x, queenAt.y, queenAt.z, colony.random().nextFloat() * 360.0F, 0.0F);
        queen.becomeQueenOf(colony);
        level.addFreshEntity(queen);
        long now = level.getGameTime();
        long oldest = Math.max(1, Math.round(colony.profile().worker().lifespanTicks() * 0.8));
        int real = realRound ? Math.min(workers, colony.profile().colony().realCap()) : 0;
        BlockPos at = BlockPos.containing(queenAt);
        for (int i = 0; i < workers; i++) {
            long grownUpAt = now - oldest * i / Math.max(1, workers);
            if (i >= real) {
                colony.population().addNewMembers(Caste.WORKER, grownUpAt, 1);
                continue;
            }
            MatabeleEntity ant = MatabeleEntity.pickBody(colony.random()).create(level);
            if (ant == null) {
                continue;
            }
            // Round the queen, 1.5 to 4 blocks out, on the ground.
            float angle = colony.random().nextFloat() * Mth.TWO_PI;
            double distance = 1.5 + colony.random().nextDouble() * 2.5;
            int x = Mth.floor(at.getX() + 0.5 + Math.cos(angle) * distance);
            int z = Mth.floor(at.getZ() + 0.5 + Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            ant.moveTo(x + 0.5, y, z + 0.5, colony.random().nextFloat() * 360.0F, 0.0F);
            ant.joinColony(colony);
            ant.setGrownUpAt(grownUpAt);
            ant.setLastMeal(now);
            level.addFreshEntity(ant);
        }
        ColonyManager.get(level).markChanged();
        return colony;
    }
}
