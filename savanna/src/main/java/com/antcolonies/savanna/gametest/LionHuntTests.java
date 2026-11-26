package com.antcolonies.savanna.gametest;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.animal.Sex;
import com.antcolonies.gametest.TestClock;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.BuffaloEntity;
import com.antcolonies.savanna.animal.BuffaloGoals;
import com.antcolonies.savanna.animal.HomeGroup;
import com.antcolonies.savanna.animal.LionEntity;
import com.antcolonies.savanna.animal.LionGoals;
import com.antcolonies.savanna.animal.LionHunt;
import com.antcolonies.savanna.animal.RemainsEntity;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Lion step 2, the hunt (2026-11-25, {@code docs/plans/lion-hunt.md}): buffalo calves, the carcass, the herd and lions, the
 * hunt and the feast. Category {@code land}.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class LionHuntTests {
    static final int GROUND = 1, FEET = 2;

    // the scene kit is shared with the in-game camera (LionScenes, the Motion Studio, 2026-11-25)
    private static void field(GameTestHelper helper, int x0, int z0, int x1, int z1) {
        LionScenes.field(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), x0, z0, x1, z1);
    }

    private static BuffaloEntity buffalo(GameTestHelper helper, double x, double z, float yaw, Sex sex, boolean young, BlockPos home) {
        return LionScenes.buffalo(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), x, z, yaw, sex, young, home);
    }

    private static LionEntity lion(GameTestHelper helper, EntityType<LionEntity> type, double x, double z, BlockPos den, int sinceMeal) {
        return LionScenes.lion(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), type, x, z, den, sinceMeal);
    }

    private static Vec3 rel(GameTestHelper helper, Vec3 abs) {
        return abs.subtract(helper.absoluteVec(Vec3.ZERO));
    }

    private static String at(GameTestHelper helper, net.minecraft.world.entity.Entity e) {
        Vec3 r = rel(helper, e.position());
        return String.format("(%.1f, %.1f, %.1f)", r.x, r.y, r.z);
    }

    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.world.entity.Entity> T reload(GameTestHelper helper, T e) {
        CompoundTag tag = e.saveWithoutId(new CompoundTag());
        T copy = (T) e.getType().create(helper.getLevel());
        copy.load(tag);
        return copy;
    }

    // --- calves --------------------------------------------------------------------------------------------------------

    /**
     * A grown cow with her herd's bull carries and gives birth: the calf is young, small, of her herd, at her side, its
     * health its size's share of a bull's; it keeps at her side as she walks.
     */
    @GameTest(template = "arena", timeoutTicks = 1400, skyAccess = true, batch = "lion_hunt_calf")
    public static void aCowWithHerBullCarriesAndHerCalfKeepsAtHerSide(GameTestHelper helper) {
        TestClock.still(helper, 8.0);
        field(helper, 0, 0, 64, 64);
        BlockPos home = helper.absolutePos(new BlockPos(32, GROUND, 32));
        BuffaloEntity bull = buffalo(helper, 28.5, 32.5, 0, Sex.MALE, false, home);
        BuffaloEntity cow = buffalo(helper, 34.5, 32.5, 0, Sex.FEMALE, false, home);
        BuffaloEntity[] calf = {null};
        boolean[] carried = {false};
        Vec3 walkTo = helper.absoluteVec(new Vec3(46.5, FEET, 44.5));
        helper.onEachTick(() -> {
            if (calf[0] != null) {
                cow.getMoveControl().setWantedPosition(walkTo.x, cow.getY(), walkTo.z, BuffaloEntity.TRAVEL);   // she walks off: it keeps at her side
            }
            if (calf[0] == null && cow.carrying() && cow.carryingLeft() > 40) {
                carried[0] = true;
                cow.setCarrying(40);                            // two game days are shortened to two seconds
            }
            if (calf[0] == null && carried[0] && !cow.carrying()) {
                for (BuffaloEntity b : helper.getLevel().getEntitiesOfClass(BuffaloEntity.class, cow.getBoundingBox().inflate(6.0), BuffaloEntity::isAlive)) {
                    if (cow.motherOf(b)) {
                        calf[0] = b;
                    }
                }
                helper.assertTrue(calf[0] != null, "no calf born; cow: " + cow.about() + "; bull: " + bull.about());
                helper.assertTrue(!calf[0].grownUp() && calf[0].getScale() < 0.5F && home.equals(calf[0].home()),
                        "the calf is not a small young one of her herd: " + calf[0].about());
                helper.assertTrue(Math.abs(calf[0].getMaxHealth() - 60.0 * calf[0].getScale()) < 1.0,
                        "the calf's health " + calf[0].getMaxHealth() + " is not its size's share (scale " + calf[0].getScale() + ")");
                cow.led = true;
            }
        });
        helper.runAtTickTime(1300, () -> {
            helper.assertTrue(calf[0] != null, "no calf born");
            double d = calf[0].distanceTo(cow);
            helper.assertTrue(HomeGroup.flat(cow.position(), walkTo.x, walkTo.z) < 3.0, "the cow did not walk off: at " + at(helper, cow));
            helper.assertTrue(d < 5.0, "the calf is " + String.format("%.1f", d) + " from its mother: " + calf[0].about()
                    + " at " + at(helper, calf[0]) + "; cow at " + at(helper, cow));
            helper.succeed();
        });
    }

    /** No breeding past the local limit: a cow among seven buffalo never carries. */
    @GameTest(template = "arena", timeoutTicks = 700, skyAccess = true, batch = "lion_hunt_calf")
    public static void aCowAmongSevenBuffaloNeverCarries(GameTestHelper helper) {
        TestClock.still(helper, 8.0);
        field(helper, 0, 0, 64, 64);
        BlockPos home = helper.absolutePos(new BlockPos(32, GROUND, 32));
        List<BuffaloEntity> herd = new ArrayList<>();
        herd.add(buffalo(helper, 32.5, 32.5, 0, Sex.MALE, false, home));
        for (int i = 0; i < 6; i++) {                       // all six cows within 16 of the bull: only the limit stops them
            double a = Math.toRadians(i * 60.0);
            herd.add(buffalo(helper, 32.5 + Math.cos(a) * 7.0, 32.5 + Math.sin(a) * 7.0, 0, Sex.FEMALE, false, home));
        }
        helper.runAtTickTime(650, () -> {
            for (BuffaloEntity b : herd) {
                helper.assertFalse(b.carrying(), "a cow carries with seven buffalo near: " + b.about());
            }
            helper.succeed();
        });
    }

    // --- the carcass ---------------------------------------------------------------------------------------------------

    /** A dead buffalo leaves a carcass of its size (38 bites times its size); bites eat it; it keeps through a save. */
    @GameTest(template = "arena", timeoutTicks = 200, skyAccess = true)
    public static void aDeadBuffaloLeavesACarcassEatenBiteByBite(GameTestHelper helper) {
        TestClock.still(helper, 8.0);
        field(helper, 0, 0, 24, 24);
        BlockPos home = helper.absolutePos(new BlockPos(12, GROUND, 12));
        BuffaloEntity cow = buffalo(helper, 12.5, 12.5, 0, Sex.FEMALE, false, home);
        cow.led = true;
        float size = cow.getScale();
        BlockPos den = helper.absolutePos(new BlockPos(2, GROUND, 2));
        LionEntity she = lion(helper, SavannaEntities.LIONESS.get(), 4.5, 4.5, den, 0);
        she.led = true;
        helper.runAtTickTime(5, () -> cow.hurt(helper.getLevel().damageSources().mobAttack(she), Float.MAX_VALUE));
        helper.runAtTickTime(80, () -> {
            List<RemainsEntity> found = helper.getLevel().getEntitiesOfClass(RemainsEntity.class, new AABB(helper.absolutePos(new BlockPos(12, GROUND, 12))).inflate(4),
                    r -> r.kind() == RemainsEntity.Kind.BUFFALO);
            helper.assertTrue(found.size() == 1, "no carcass (or " + found.size() + ")");
            RemainsEntity carcass = found.get(0);
            int full = Math.round(38 * size);
            helper.assertTrue(carcass.bitesLeft() == full && Math.abs(carcass.size() - size) < 0.01F && den.equals(carcass.killedBy()),
                    "the carcass has " + carcass.bitesLeft() + " bites (want " + full + "), size " + carcass.size() + ", killed by " + carcass.killedBy());
            carcass.bite(cow);
            carcass.bite(cow);
            helper.assertTrue(carcass.bitesLeft() == full - 2, "two bites took " + (full - carcass.bitesLeft()));
            RemainsEntity copy = reload(helper, carcass);
            helper.assertTrue(copy.kind() == RemainsEntity.Kind.BUFFALO && copy.bitesLeft() == full - 2 && den.equals(copy.killedBy()),
                    "the carcass did not keep through a save: " + copy.kind() + ", " + copy.bitesLeft() + ", " + copy.killedBy());
            helper.succeed();
        });
    }

    // --- the herd and lions --------------------------------------------------------------------------------------------

    /** A galloping lion sends a buffalo running away (FLEE), never at it; a hurt calf brings its mother's charge. */
    @GameTest(template = "arena", timeoutTicks = 400, skyAccess = true, batch = "lion_hunt_gallop")
    public static void aGallopingLionSendsTheHerdRunningAndAHurtCalfBringsItsMother(GameTestHelper helper) {
        TestClock.still(helper, 8.0);
        field(helper, 0, 0, 64, 64);
        BlockPos home = helper.absolutePos(new BlockPos(40, GROUND, 32));
        BuffaloEntity bull = buffalo(helper, 40.5, 40.5, 0, Sex.MALE, false, home);
        bull.led = true;
        BlockPos den = helper.absolutePos(new BlockPos(2, GROUND, 32));
        LionEntity she = lion(helper, SavannaEntities.LIONESS.get(), 20.5, 40.5, den, 0);
        she.led = true;
        double startX = bull.getX();
        boolean[] fled = {false};
        helper.onEachTick(() -> {
            if (helper.getTick() < 60) {
                she.getMoveControl().setWantedPosition(bull.getX(), bull.getY(), bull.getZ(), LionEntity.GALLOP);
            }
            fled[0] |= bull.temper() == BuffaloGoals.Temper.FLEE && bull.temperOn() == she;
            if (helper.getTick() < 100) {
                helper.assertFalse(bull.temper() == BuffaloGoals.Temper.CHARGE, "the bull charged a lion it should run from: " + bull.about());
            }
        });
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(fled[0] && bull.getX() - startX > 3.0, "the bull did not run from a galloping lion: moved "
                    + String.format("%.1f", bull.getX() - startX) + "; " + bull.about());
            // a hurt calf brings its mother's charge (another herd's: the bull far off)
            BlockPos home2 = helper.absolutePos(new BlockPos(50, GROUND, 8));
            BuffaloEntity cow = buffalo(helper, 50.5, 10.5, 0, Sex.FEMALE, false, home2);
            BuffaloEntity calf = buffalo(helper, 52.5, 10.5, 0, Sex.FEMALE, true, home2);
            cow.adopt(calf);
            cow.led = true;
            she.setPos(helper.absoluteVec(new Vec3(56.5, FEET, 10.5)));
            calf.hurt(helper.getLevel().damageSources().mobAttack(she), 1.0F);
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(cow.temper() == BuffaloGoals.Temper.CHARGE && cow.temperOn() == she,
                        "the mother did not charge the lion that hurt her calf: " + cow.about());
                helper.succeed();
            });
        });
    }

    /**
     * A held cow pulls away from her holder and never turns on it (no spinning brawl), slowed; her herd's bull charges
     * the lioness; the lioness, hit by the bull, lets go.
     */
    @GameTest(template = "arena", timeoutTicks = 300, skyAccess = true, batch = "lion_hunt_gallop")
    public static void aHeldCowPullsAwayNeverAtHerHolderAndHerBullComes(GameTestHelper helper) {
        TestClock.still(helper, 8.0);
        field(helper, 0, 0, 64, 64);
        BlockPos home = helper.absolutePos(new BlockPos(32, GROUND, 32));
        BuffaloEntity bull = buffalo(helper, 24.5, 42.5, 0, Sex.MALE, false, home);
        BuffaloEntity cow = buffalo(helper, 32.5, 32.5, 0, Sex.FEMALE, false, home);
        cow.led = true;                                     // their days off: only their tempers
        bull.led = true;
        LionEntity she = lion(helper, SavannaEntities.LIONESS.get(), 32.5, 29.0, helper.absolutePos(new BlockPos(4, GROUND, 4)), 0);
        helper.runAtTickTime(2, () -> LionHunt.gripForTests(she, cow));
        int[] held = {0};
        boolean[] fled = {false}, bullCame = {false};
        helper.onEachTick(() -> {
            if (helper.getTick() < 3) {
                return;
            }
            if (she.holding()) {
                held[0]++;
                helper.assertFalse(cow.temper() == BuffaloGoals.Temper.CHARGE && cow.temperOn() == she,
                        "the held cow turned on her holder: " + cow.about());
                fled[0] |= cow.temper() == BuffaloGoals.Temper.FLEE;
                helper.assertTrue(she.distanceTo(cow) < 5.0, "holding from " + String.format("%.1f", she.distanceTo(cow)) + ": " + she.about());
            }
            bullCame[0] |= bull.temper() == BuffaloGoals.Temper.CHARGE && bull.temperOn() == she;
            if (held[0] >= 20 && fled[0] && bullCame[0]) {
                helper.succeed();
            }
        });
        helper.runAtTickTime(290, () -> helper.fail("held " + held[0] + " ticks, the cow pulled away " + fled[0] + ", the bull came "
                + bullCame[0] + "; she " + she.about() + "; cow " + cow.about() + "; bull " + bull.about()));
    }

    /** A lioness hurt by a buffalo is never angry at it: she backs off. */
    @GameTest(template = "arena", timeoutTicks = 200, skyAccess = true)
    public static void aLionessHurtByABuffaloBacksOffNeverAtIt(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        field(helper, 0, 0, 48, 48);
        BlockPos home = helper.absolutePos(new BlockPos(24, GROUND, 24));
        BuffaloEntity bull = buffalo(helper, 24.5, 24.5, 90, Sex.MALE, false, home);
        bull.led = true;
        BlockPos den = helper.absolutePos(new BlockPos(44, GROUND, 24));   // her den beyond the bull: her day takes her toward it
        LionEntity she = lion(helper, SavannaEntities.LIONESS.get(), 20.5, 24.5, den, 0);
        double startD = she.distanceTo(bull);
        helper.runAtTickTime(5, () -> she.hurt(helper.getLevel().damageSources().mobAttack(bull), 2.0F));
        helper.onEachTick(() -> helper.assertFalse(she.temper() == LionGoals.Temper.ATTACK,
                "she turned on the buffalo: " + she.about()));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(she.distanceTo(bull) > startD + 2.5, "she did not back off: " + String.format("%.1f", she.distanceTo(bull))
                    + " from it (was " + String.format("%.1f", startD) + "); " + she.about());
            helper.succeed();
        });
    }

    // --- the hunt ------------------------------------------------------------------------------------------------------

    /**
     * At dusk a hungry pride's lionesses spread, stalk a herd with a calf, take the calf; the pride comes to the carcass and
     * feeds; at least the male and two lionesses eat their fill (their hunger back to 0); the full lie by the kill.
     */
    @GameTest(template = "arena", timeoutTicks = 7000, skyAccess = true, batch = "lion_hunt_dusk")
    public static void atDuskAHungryPrideTakesACalfAndFeeds(GameTestHelper helper) {
        TestClock.still(helper, 20.0);
        // the scene is LionScenes.duskHunt, which the in-game camera films too (tools/film/lion_hunt.film)
        LionScenes.DuskHunt scene = LionScenes.duskHunt(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        BuffaloEntity cow = scene.cow();
        BuffaloEntity calf = scene.calf();
        List<LionEntity> pride = scene.pride();
        boolean[] stalked = {false};
        long[] killedAt = {-1};
        helper.onEachTick(() -> {
            scene.tick();                                   // the cow grazes whenever calm
            for (LionEntity l : pride) {
                stalked[0] |= l.doing() == LionEntity.STALK || l.doing() == LionEntity.FREEZE;
            }
            if (killedAt[0] < 0 && !calf.isAlive()) {
                killedAt[0] = helper.getTick();
            }
            if (killedAt[0] >= 0) {
                int full = 0;
                boolean maleFull = false;
                for (LionEntity l : pride) {
                    if (l.fedFull()) {
                        full++;
                        maleFull |= l.form() == LionEntity.Form.MALE;
                    }
                }
                int lying = 0;
                for (LionEntity l : pride) {
                    if (l.fedFull() && (l.doing() == LionEntity.REST || l.doing() == LionEntity.REST_DOWN) && l.huntStage() == LionHunt.Stage.FEAST) {
                        lying++;                                // full, lying by the kill
                    }
                }
                if (full >= 3 && maleFull && lying >= 2) {
                    helper.assertTrue(stalked[0], "a kill with no stalk");
                    for (LionEntity l : pride) {
                        if (l.fedFull()) {
                            helper.assertTrue(l.sinceMeal() < LionHunt.HUNGRY / 10, "a full lion is still hungry: " + l.about());
                            LionEntity copy = reload(helper, l);    // its feast keeps through a save: full, its kill known
                            helper.assertTrue(copy.fedFull() && copy.feastKnown(), "a full lion's feast did not keep through a save: " + copy.about());
                        }
                    }
                    helper.succeed();
                }
            }
        });
        helper.runAtTickTime(6900, () -> {
            StringBuilder b = new StringBuilder();
            for (LionEntity l : pride) {
                b.append("\n  ").append(l.about()).append(" at ").append(at(helper, l));
            }
            helper.fail("no kill and feast: stalked " + stalked[0] + ", killed at " + killedAt[0] + "; calf " + (calf.isAlive() ? calf.about() : "dead")
                    + "; cow " + cow.about() + b);
        });
    }

    /**
     * A creeping lioness never moves while a grown buffalo's head is up near her: she freezes outside its sight, creeps
     * while its head is down grazing, and is never seen (the herd never runs).
     */
    @GameTest(template = "arena", timeoutTicks = 2600, skyAccess = true, batch = "lion_hunt_freeze")
    public static void aStalkingLionessFreezesWhileAHeadIsUpAndCreepsWhileItIsDown(GameTestHelper helper) {
        TestClock.still(helper, 20.0);
        field(helper, 0, 0, 64, 64);
        BlockPos den = helper.absolutePos(new BlockPos(4, GROUND, 32));
        BlockPos home = helper.absolutePos(new BlockPos(50, GROUND, 32));
        BuffaloEntity cow = buffalo(helper, 50.5, 30.5, 90, Sex.FEMALE, false, home);
        cow.led = true;
        BuffaloEntity calf = buffalo(helper, 50.5, 34.5, 90, Sex.FEMALE, true, home);
        calf.led = true;
        LionEntity she = lion(helper, SavannaEntities.LIONESS.get(), 8.5, 32.5, den, LionHunt.HUNGRY);
        boolean[] froze = {false}, creptNear = {false};
        int[] grazeOn = {-1};
        helper.onEachTick(() -> {
            helper.assertFalse(cow.headUp() && she.distanceTo(cow) <= BuffaloEntity.LION_STALK_SEEN && she.stalking(),
                    "she crept in sight of a head that was up: " + she.about() + "; cow " + cow.about());
            helper.assertFalse(cow.temper() == BuffaloGoals.Temper.FLEE, "the cow saw her and ran: " + she.about() + " at "
                    + at(helper, she) + ", " + String.format("%.1f", she.distanceTo(cow)) + " from the cow; cow " + cow.about());
            double d = she.distanceTo(cow);
            if (she.doing() == LionEntity.FREEZE && cow.headUp() && d <= BuffaloEntity.LION_STALK_SEEN + 1.5) {
                froze[0] = true;
                if (grazeOn[0] < 0) {
                    grazeOn[0] = (int) helper.getTick();
                }
            }
            if (grazeOn[0] >= 0 && cow.doing() == BuffaloEntity.CALM && helper.getTick() > grazeOn[0] + 20) {
                cow.setDoing(BuffaloEntity.GRAZE);                     // she grazes on and on: heads down most of the time
            }
            if (froze[0] && d < BuffaloEntity.LION_STALK_SEEN - 2.0 && she.huntStage() == LionHunt.Stage.STALK) {
                creptNear[0] = true;
                helper.succeed();
            }
        });
        helper.runAtTickTime(2500, () -> helper.fail("froze " + froze[0] + ", crept near " + creptNear[0] + "; " + she.about() + " at "
                + at(helper, she) + ", " + String.format("%.1f", she.distanceTo(cow)) + " from the cow; cow " + cow.about()));
    }

    /** A fed pride does not hunt; a hungry one does not take a grown buffalo from a herd smaller than five grown. */
    @GameTest(template = "arena", timeoutTicks = 700, skyAccess = true, batch = "lion_hunt_none")
    public static void aFedPrideAndASmallHerdOfGrownMeanNoHunt(GameTestHelper helper) {
        TestClock.still(helper, 20.0);
        field(helper, 0, 0, 64, 64);
        BlockPos den = helper.absolutePos(new BlockPos(6, GROUND, 20));
        BlockPos home = helper.absolutePos(new BlockPos(50, GROUND, 20));
        buffalo(helper, 50.5, 18.5, 90, Sex.MALE, false, home);
        buffalo(helper, 50.5, 22.5, 90, Sex.FEMALE, false, home);
        buffalo(helper, 46.5, 20.5, 90, Sex.FEMALE, false, home);
        LionEntity hungry = lion(helper, SavannaEntities.LIONESS.get(), 8.5, 20.5, den, LionHunt.HUNGRY);
        BlockPos den2 = helper.absolutePos(new BlockPos(6, GROUND, 50));
        BlockPos home2 = helper.absolutePos(new BlockPos(50, GROUND, 50));
        BuffaloEntity cow2 = buffalo(helper, 50.5, 50.5, 90, Sex.FEMALE, false, home2);
        BuffaloEntity calf2 = buffalo(helper, 50.5, 53.5, 90, Sex.FEMALE, true, home2);
        cow2.adopt(calf2);
        LionEntity fed = lion(helper, SavannaEntities.LIONESS.get(), 8.5, 50.5, den2, 100);
        helper.onEachTick(() -> {
            helper.assertTrue(hungry.huntStage() == LionHunt.Stage.NONE, "she hunts a herd of three grown: " + hungry.about());
            helper.assertTrue(fed.huntStage() == LionHunt.Stage.NONE, "a fed lioness hunts: " + fed.about());
        });
        helper.runAtTickTime(650, () -> {
            helper.assertTrue(hungry.huntTried(), "the hungry lioness never came to her try (the window): " + hungry.about());
            helper.succeed();
        });
    }
}
