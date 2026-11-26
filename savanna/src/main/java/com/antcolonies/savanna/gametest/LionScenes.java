package com.antcolonies.savanna.gametest;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.animal.Sex;
import com.antcolonies.savanna.animal.BuffaloEntity;
import com.antcolonies.savanna.animal.BuffaloGoals;
import com.antcolonies.savanna.animal.LionEntity;
import com.antcolonies.savanna.animal.LionHunt;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Lion and buffalo scenes shared by the game tests and the in-game camera (the Motion Studio, phase 0, 2026-11-25,
 * {@code docs/plans/motion-studio.md}): the same animals set up the same way, so a film shows what a test checks. Places
 * are given from the scene's corner (x and z as the tests write them); the field's grass is one block above the corner,
 * the animals' feet two.
 */
public final class LionScenes {
    static final int GROUND = 1, FEET = 2;

    private LionScenes() {
    }

    /** The camera's scenes, signed up at start (development use only; nothing is built unless the camera asks). */
    public static void signUpFilms() {
        com.antcolonies.debug.FilmStages.signUp("lion_hunt", (level, corner) -> {
            DuskHunt hunt = duskHunt(level, corner);
            hunt.cow().setCustomName(Component.literal("film-cow"));
            hunt.calf().setCustomName(Component.literal("film-calf"));
            String[] names = {"film-elder", "film-lioness-2", "film-lioness-3", "film-male", "film-cub"};
            for (int i = 0; i < hunt.pride().size(); i++) {
                hunt.pride().get(i).setCustomName(Component.literal(names[i]));
            }
            return hunt::tick;
        });
    }

    /**
     * The dusk hunt ({@code LionHuntTests.atDuskAHungryPrideTakesACalfAndFeeds}): a 65 by 65 field, a cow and her calf at
     * its east side (the cow kept where she is, grazing whenever calm), and a hungry pride at its west side by its den:
     * three lionesses (the first the elder), the male and a cub. Run at 20:00.
     */
    public record DuskHunt(BuffaloEntity cow, BuffaloEntity calf, List<LionEntity> pride) {
        /** Each tick: the cow grazes while she is calm and untroubled (her head down: a stalk can creep up). */
        public void tick() {
            if (cow.isAlive() && cow.doing() == BuffaloEntity.CALM && cow.temper() == BuffaloGoals.Temper.NONE) {
                cow.setDoing(BuffaloEntity.GRAZE);
            }
        }
    }

    public static DuskHunt duskHunt(ServerLevel level, BlockPos corner) {
        field(level, corner, 0, 0, 64, 64);
        BlockPos den = corner.offset(6, GROUND, 32);
        BlockPos home = corner.offset(50, GROUND, 32);
        BuffaloEntity cow = buffalo(level, corner, 46.5, 32.5, 90, Sex.FEMALE, false, home);
        BuffaloEntity calf = buffalo(level, corner, 46.5, 34.5, 90, Sex.FEMALE, true, home);
        cow.adopt(calf);
        cow.led = true;                                     // she grazes where she stands (her day would take her off the field)
        List<LionEntity> pride = new ArrayList<>();
        pride.add(lion(level, corner, SavannaEntities.LIONESS.get(), 8.5, 28.5, den, LionHunt.HUNGRY));
        pride.get(0).setElder(true);
        pride.add(lion(level, corner, SavannaEntities.LIONESS.get(), 8.5, 31.5, den, LionHunt.HUNGRY));
        pride.add(lion(level, corner, SavannaEntities.LIONESS.get(), 8.5, 34.5, den, LionHunt.HUNGRY));
        pride.add(lion(level, corner, SavannaEntities.LION.get(), 5.5, 38.5, den, LionHunt.HUNGRY));
        pride.add(lion(level, corner, SavannaEntities.LION_CUB.get(), 4.5, 26.5, den, LionHunt.HUNGRY));
        return new DuskHunt(cow, calf, pride);
    }

    /** Stone under grass from (x0, z0) to (x1, z1). */
    static void field(ServerLevel level, BlockPos corner, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                level.setBlock(corner.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(corner.offset(x, GROUND, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
        }
    }

    /** A buffalo standing at (x, z) from the corner, facing yaw: grown or a newborn calf, of the herd with that home. */
    static BuffaloEntity buffalo(ServerLevel level, BlockPos corner, double x, double z, float yaw, Sex sex, boolean young, BlockPos home) {
        BuffaloEntity b = spawn(level, SavannaEntities.BUFFALO.get(), Vec3.atLowerCornerOf(corner).add(x, FEET, z));
        b.life().setUp(young, sex, RandomSource.create((long) (x * 31 + z)));
        if (young) {
            b.life().setAge(0);
        }
        b.life().apply();
        b.fitHealth();
        b.setHealth(b.getMaxHealth());
        b.setHome(home, 0);
        b.setYRot(yaw);
        b.yBodyRot = yaw;
        b.yHeadRot = yaw;
        return b;
    }

    /** A grown lion of that kind standing at (x, z) from the corner, facing west, of the pride with that den, this hungry. */
    static LionEntity lion(ServerLevel level, BlockPos corner, EntityType<LionEntity> type, double x, double z, BlockPos den, int sinceMeal) {
        LionEntity l = spawn(level, type, Vec3.atLowerCornerOf(corner).add(x, FEET, z));
        Sex sex = type == SavannaEntities.LION.get() ? Sex.MALE : type == SavannaEntities.LIONESS.get() ? Sex.FEMALE : null;
        l.life().setUp(false, sex, RandomSource.create((long) (x * 31 + z)));
        l.life().apply();
        l.setDen(den, -90.0F, null, 0);
        l.setSinceMeal(sinceMeal);
        l.setYRot(-90.0F);
        l.yBodyRot = -90.0F;
        l.yHeadRot = -90.0F;
        return l;
    }

    /** As a game test's {@code helper.spawn}: made, kept (never despawns), put there and added. */
    static <E extends Entity> E spawn(ServerLevel level, EntityType<E> type, Vec3 at) {
        E e = type.create(level);
        if (e == null) {
            throw new IllegalStateException("cannot make " + type);
        }
        if (e instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        e.moveTo(at.x, at.y, at.z, e.getYRot(), e.getXRot());
        level.addFreshEntity(e);
        return e;
    }
}
