package com.antcolonies.savanna.gametest;

import static com.antcolonies.savanna.gametest.TermiteMoundTests.SURFACE;

import com.antcolonies.gametest.TestClock;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.BullSharkEntity;
import com.antcolonies.savanna.animal.CrocodileEntity;
import com.antcolonies.savanna.animal.RemainsEntity;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The bull shark (2026-11-22, {@code docs/plans/otter-and-bull-shark.md} steps 5 to 7; its spawning is proved in
 * {@code RiverLifeTests.bullSharksSpawnInDeepRiverWaterByThemselves}). One pool: a deep middle 5 blocks deep, a shelf 2
 * deep along one side, a dry stone bank level with the water along the other, and a dry floor off the pool's end. Its
 * own batch, so no other test's shark is near the spawning test.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class BullSharkTests {
    /** The pool's inside, the shelf's side (z up to SHELF_TO) and the bank's (z from BANK_FROM), relative blocks. */
    static final int X0 = 6, X1 = 52, Z0 = 14, Z1 = 46, SHELF_TO = 19, BANK_FROM = 41;
    /** The top water block, and how deep the middle and the shelf are. */
    static final int WT = SURFACE + 2, DEEP = 5, SHELF = 2;
    /** The dry floor off the pool's end (x from DRY_X0), at the bank's height. */
    static final int DRY_X0 = 56, DRY_X1 = 62;
    static final String BATCH = "bull_shark";

    private static void pool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = X0 - 1; x <= DRY_X1 + 1; x++) {
            for (int z = Z0 - 1; z <= Z1 + 1; z++) {
                boolean inPool = x >= X0 && x <= X1 && z >= Z0 && z <= Z1;
                boolean dry = x >= DRY_X0 && x <= DRY_X1;
                for (int y = WT - DEEP - 1; y <= WT + 6; y++) {
                    BlockState state;
                    if (!inPool && !dry) {
                        state = (y <= WT + 1 ? Blocks.STONE : Blocks.AIR).defaultBlockState();       // the walls
                    } else if (dry || z >= BANK_FROM) {
                        state = (y <= WT ? Blocks.STONE : Blocks.AIR).defaultBlockState();           // the bank, the dry floor
                    } else {
                        int bed = z <= SHELF_TO ? WT - SHELF : WT - DEEP;
                        state = (y <= bed ? Blocks.STONE : y <= WT ? Blocks.WATER : Blocks.AIR).defaultBlockState();
                    }
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), state, 2);
                }
            }
        }
    }

    /** A shark of the usual size (5.5 blocks) in the deep water facing that way, hungry or fed for good. */
    private static BullSharkEntity shark(GameTestHelper helper, double x, double z, float yaw, boolean hungry) {
        BullSharkEntity shark = helper.spawn(SavannaEntities.BULL_SHARK.get(), new Vec3(x, WT - DEEP + 1.2, z));
        shark.setPersistenceRequired();
        shark.setLength(5.5F);
        shark.setYRot(yaw);
        shark.yBodyRot = yaw;
        shark.yHeadRot = yaw;
        if (hungry) {
            shark.makeHungry();
        } else {
            shark.feed(1_000_000);
        }
        return shark;
    }

    /** A still fish (no mind of its own) at a spot in the water. */
    private static <T extends LivingEntity> T stillFish(GameTestHelper helper, net.minecraft.world.entity.EntityType<T> type, double x, double y, double z) {
        T fish = helper.spawn(type, new Vec3(x, y, z));
        if (fish instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        return fish;
    }

    private static int depthUnder(BullSharkEntity shark) {
        return BullSharkEntity.depthAt(shark.level(), shark.getX(), shark.getY() + 0.5, shark.getZ());
    }

    /** By day and fed it patrols up and down the deep water, its middle never over water under 3 deep (the shelf). */
    @GameTest(template = "arena", timeoutTicks = 1000, batch = BATCH)
    public static void aBullSharkPatrolsTheDeepWaterAndKeepsOffTheShelf(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        pool(helper);
        BullSharkEntity shark = shark(helper, 20.5, 26.5, -90.0F, false);
        double[] travelled = {0};
        Vec3[] last = {shark.position()};
        helper.onEachTick(() -> {
            travelled[0] += shark.position().distanceTo(last[0]);
            last[0] = shark.position();
            helper.assertTrue(depthUnder(shark) >= BullSharkEntity.MIN_DEPTH, "the shark swam over water " + depthUnder(shark) + " deep at "
                    + shark.position().subtract(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))));
            if (helper.getTick() >= 900) {
                helper.assertTrue(travelled[0] > 20, "the shark hardly patrolled: " + String.format("%.1f", travelled[0]) + " blocks in 45 s");
                helper.succeed();
            }
        });
    }

    /**
     * At night a hungry shark hunts: it prowls with its fin out first, then bursts and bites; a tilapia is swallowed whole
     * and the shark is fed.
     */
    @GameTest(template = "arena", timeoutTicks = 1200, batch = BATCH)
    public static void aHungrySharkProwlsAtNightAndSwallowsATilapia(GameTestHelper helper) {
        TestClock.still(helper, 22.0);
        pool(helper);
        BullSharkEntity shark = shark(helper, 14.5, 30.5, -90.0F, true);
        shark.strikeLuck = 1;
        var tilapia = stillFish(helper, SavannaEntities.TILAPIA.get(), 34.5, WT - DEEP + 2.0, 30.5);
        boolean[] prowled = {false}, bit = {false};
        helper.onEachTick(() -> {
            prowled[0] |= shark.isProwling();
            if (shark.action() == BullSharkEntity.ACT_BITE) {
                helper.assertTrue(prowled[0], "it bit before it prowled");
                bit[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tilapia.isRemoved(), "the tilapia is still there: prowled " + prowled[0] + ", bit " + bit[0] + ", shark "
                    + String.format("%.1f", shark.distanceTo(tilapia)) + " off, action " + shark.action());
            helper.assertTrue(bit[0] && !shark.isHungry(), "swallowed without a bite, or not fed");
        });
    }

    /**
     * A catfish is too big to swallow: held across the jaws and shaken ({@code thrash}) until it tears, the front half
     * left as remains; the shark is fed and lets go.
     */
    @GameTest(template = "arena", timeoutTicks = 1400, batch = BATCH)
    public static void aSharkShakesACatfishApartAndLeavesRemains(GameTestHelper helper) {
        TestClock.still(helper, 22.0);
        pool(helper);
        BullSharkEntity shark = shark(helper, 14.5, 30.5, -90.0F, true);
        shark.strikeLuck = 1;
        var catfish = stillFish(helper, SavannaEntities.CATFISH.get(), 34.5, WT - DEEP + 2.0, 30.5);
        boolean[] held = {false}, thrashed = {false};
        helper.onEachTick(() -> {
            held[0] |= shark.isHolding();
            thrashed[0] |= shark.action() == BullSharkEntity.ACT_THRASH;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(catfish.isRemoved() && held[0] && thrashed[0], "the catfish was not taken and shaken: removed " + catfish.isRemoved()
                    + ", held " + held[0] + ", thrashed " + thrashed[0] + ", action " + shark.action());
            helper.assertTrue(!shark.isHolding() && !shark.isHungry(), "still holding, or not fed");
            var remains = helper.getLevel().getEntitiesOfClass(RemainsEntity.class, shark.getBoundingBox().inflate(16),
                    r -> r.kind() == RemainsEntity.Kind.CATFISH_TORN);
            helper.assertTrue(!remains.isEmpty(), "no torn catfish left");
        });
    }

    /** Prey near the surface, a breach: it leaps clean out at it and takes it in the air. */
    @GameTest(template = "arena", timeoutTicks = 1200, batch = BATCH)
    public static void aSharkBreachesAtPreyNearTheSurface(GameTestHelper helper) {
        TestClock.still(helper, 22.0);
        pool(helper);
        BullSharkEntity shark = shark(helper, 14.5, 30.5, -90.0F, true);
        shark.strikeLuck = 1;
        shark.breachNext = true;
        var tilapia = stillFish(helper, SavannaEntities.TILAPIA.get(), 34.5, WT - 0.6, 30.5);
        boolean[] breached = {false};
        helper.onEachTick(() -> breached[0] |= shark.action() == BullSharkEntity.ACT_BREACH);
        helper.succeedWhen(() -> {
            helper.assertTrue(breached[0], "no breach: action " + shark.action() + ", prowling " + shark.isProwling() + ", "
                    + String.format("%.1f", shark.distanceTo(tilapia)) + " off");
            helper.assertTrue(tilapia.isRemoved() && !shark.isHungry(), "the breach took nothing");
        });
    }

    /** By day a hungry shark leaves prey alone (it hunts at dusk and by night). */
    @GameTest(template = "arena", timeoutTicks = 400, batch = BATCH)
    public static void aSharkLeavesPreyAloneByDay(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        pool(helper);
        BullSharkEntity shark = shark(helper, 14.5, 30.5, -90.0F, true);
        var tilapia = stillFish(helper, SavannaEntities.TILAPIA.get(), 26.5, WT - DEEP + 2.0, 30.5);
        helper.onEachTick(() -> {
            helper.assertFalse(shark.isProwling() || shark.action() == BullSharkEntity.ACT_BITE, "it hunted by day");
            if (helper.getTick() >= 300) {
                helper.assertTrue(tilapia.isAlive() && !tilapia.isRemoved(), "the tilapia is gone");
                helper.succeed();
            }
        });
    }

    /** A survival player for these tests, swimming (held still) at a spot. */
    private static ServerPlayer swimmer(GameTestHelper helper, double x, double y, double z) {
        ServerPlayer player = MatabeleDefenceTests.survivalPlayer(helper);
        BlockPos at = helper.absolutePos(BlockPos.ZERO);
        player.moveTo(at.getX() + x, at.getY() + y, at.getZ() + z, 90.0F, 0.0F);
        return player;
    }

    private static void hold(GameTestHelper helper, ServerPlayer player, double x, double y, double z) {
        BlockPos at = helper.absolutePos(BlockPos.ZERO);
        player.moveTo(at.getX() + x, at.getY() + y, at.getZ() + z, player.getYRot(), 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * A player swimming in deep water: the shark bumps them first (no harm), and when they stay near it warns them off
     * (the hunch) and only then bites.
     */
    @GameTest(template = "arena", timeoutTicks = 1600, batch = BATCH)
    public static void aSharkBumpsASwimmerFirstThenWarnsAndBites(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        pool(helper);
        BullSharkEntity shark = shark(helper, 14.5, 30.5, -90.0F, false);
        shark.makeCurious();
        ServerPlayer player = swimmer(helper, 26.5, WT - 2, 30.5);
        float health = player.getHealth();
        boolean[] bumped = {false}, warned = {false};
        helper.onEachTick(() -> {
            hold(helper, player, 26.5, WT - 2, 30.5);
            byte a = shark.action();
            if (a == BullSharkEntity.ACT_BUMP) {
                bumped[0] = true;
            }
            if (a == BullSharkEntity.ACT_THREAT) {
                helper.assertTrue(bumped[0], "it warned before it bumped");
                warned[0] = true;
            }
            if (a == BullSharkEntity.ACT_BITE) {
                helper.assertTrue(warned[0], "it bit before its warning");
            }
            if (!warned[0]) {
                helper.assertTrue(player.getHealth() >= health, "the bump hurt the player");
            }
            if (warned[0] && player.getHealth() <= health - 4.0F) {
                helper.getLevel().getServer().getPlayerList().remove(player);
                helper.succeed();
            }
        });
        helper.runAtTickTime(1590, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.fail("no bite after the warning: bumped " + bumped[0] + ", warned " + warned[0] + ", health " + player.getHealth()
                    + ", " + shark.about() + ", " + String.format("%.1f", shark.distanceTo(player)) + " off");
        });
    }

    /** A player who hits it gets the warning at once (no bump) and then a bite. */
    @GameTest(template = "arena", timeoutTicks = 800, batch = BATCH)
    public static void aSharkHitByASwimmerWarnsThenBites(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        pool(helper);
        BullSharkEntity shark = shark(helper, 14.5, 30.5, -90.0F, false);
        ServerPlayer player = swimmer(helper, 24.5, WT - 2, 30.5);
        float health = player.getHealth();
        boolean[] warned = {false};
        helper.runAtTickTime(2, () -> shark.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F));
        helper.onEachTick(() -> {
            hold(helper, player, 24.5, WT - 2, 30.5);
            helper.assertFalse(shark.action() == BullSharkEntity.ACT_BUMP, "it bumped a player who had hit it");
            warned[0] |= shark.action() == BullSharkEntity.ACT_THREAT;
            if (warned[0] && player.getHealth() <= health - 4.0F) {
                helper.getLevel().getServer().getPlayerList().remove(player);
                helper.succeed();
            }
        });
        helper.runAtTickTime(790, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.fail("no warning and bite: warned " + warned[0] + ", health " + player.getHealth() + ", " + shark.about() + ", " + String.format("%.1f", shark.distanceTo(player)) + " off");
        });
    }

    /** A player on the shelf (2 deep) who hits it is not followed: no bite, and the shark keeps to the deep water. */
    @GameTest(template = "arena", timeoutTicks = 500, batch = BATCH)
    public static void aSharkNeverFollowsIntoShallowWater(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        pool(helper);
        BullSharkEntity shark = shark(helper, 24.5, 24.5, 180.0F, false);
        ServerPlayer player = swimmer(helper, 24.5, WT - 1, 16.5);
        float health = player.getHealth();
        helper.runAtTickTime(2, () -> shark.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F));
        helper.onEachTick(() -> {
            hold(helper, player, 24.5, WT - 1, 16.5);
            helper.assertTrue(depthUnder(shark) >= BullSharkEntity.MIN_DEPTH, "the shark followed into water " + depthUnder(shark) + " deep");
            helper.assertTrue(player.getHealth() >= health, "the shark bit a player in shallow water");
            if (helper.getTick() >= 400) {
                helper.getLevel().getServer().getPlayerList().remove(player);
                helper.succeed();
            }
        });
    }

    /** A grown crocodile close in the water gets the hunched warning. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = BATCH)
    public static void aSharkWarnsOffACrocodile(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        pool(helper);
        BullSharkEntity shark = shark(helper, 20.5, 30.5, -90.0F, false);
        shark.makeCurious();
        var croc = helper.spawn(SavannaEntities.CROCODILE.get(), new Vec3(26.5, WT - 1, 30.5));
        croc.life().setUp(false, com.antcolonies.animal.Sex.MALE, helper.getLevel().random);
        croc.life().apply();
        croc.hungerIn = 1_000_000;
        croc.setNoAi(true);
        helper.succeedWhen(() -> helper.assertTrue(shark.action() == BullSharkEntity.ACT_THREAT,
                "no warning at the crocodile " + String.format("%.1f", shark.distanceTo(croc)) + " off, in water " + croc.isInWater()));
    }

    /** A grown male crocodile (7 blocks or more) warned by a shorter shark faces it and gapes: it holds its ground. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = BATCH)
    public static void aBigCrocodileGapesBackAtAShorterShark(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        pool(helper);
        BullSharkEntity shark = shark(helper, 20.5, 30.5, -90.0F, false);
        shark.makeCurious();
        var croc = warnedCrocodile(helper, com.antcolonies.animal.Sex.MALE);
        helper.succeedWhen(() -> {
            helper.assertTrue(shark.action() == BullSharkEntity.ACT_THREAT, "no warning yet");
            helper.assertTrue(croc.jaws() == CrocodileEntity.JAWS_GAPE && croc.startled <= 0, "the crocodile ("
                    + String.format("%.1f", croc.bodyLength()) + " long) did not stand its ground against a " + shark.length()
                    + "-block shark: jaws " + croc.jaws() + ", startled " + croc.startled);
        });
    }

    /** A grown female crocodile (under 6.5 blocks) warned by a 7.5-block shark gives way: it sinks out of sight. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = BATCH)
    public static void aSmallCrocodileGivesWayToALongerShark(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        pool(helper);
        BullSharkEntity shark = shark(helper, 20.5, 30.5, -90.0F, false);
        shark.setLength(7.5F);
        shark.makeCurious();
        var croc = warnedCrocodile(helper, com.antcolonies.animal.Sex.FEMALE);
        helper.succeedWhen(() -> {
            helper.assertTrue(shark.action() == BullSharkEntity.ACT_THREAT, "no warning yet");
            helper.assertTrue(croc.startled > 0 && croc.jaws() != CrocodileEntity.JAWS_GAPE, "the crocodile ("
                    + String.format("%.1f", croc.bodyLength()) + " long) did not give way to a " + shark.length() + "-block shark");
        });
    }

    /** A grown crocodile of that sex, still, in the water a little way ahead of the shark. */
    private static CrocodileEntity warnedCrocodile(GameTestHelper helper, com.antcolonies.animal.Sex sex) {
        CrocodileEntity croc = helper.spawn(SavannaEntities.CROCODILE.get(), new Vec3(26.5, WT - 1, 30.5));
        croc.life().setUp(false, sex, helper.getLevel().random);
        croc.life().apply();
        croc.hungerIn = 1_000_000;
        croc.setNoAi(true);
        return croc;
    }

    /**
     * A fed shark cruising (not hunting) makes barbs and tilapia near it swim aside, all of them out past 5.5 blocks; a
     * hunting shark is not parted from (it stalks them unseen; the hunt tests above still catch fish).
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = BATCH)
    public static void fishPartRoundACruisingShark(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        pool(helper);
        BullSharkEntity shark = shark(helper, 28.5, 30.5, -90.0F, false);
        shark.setNoAi(true);                    // holds still in the middle: a big shape in the water, not hunting
        double y = WT - DEEP + 1.2;            // the shark's own height (relative to the test area)
        java.util.List<net.minecraft.world.entity.Mob> fish = new java.util.ArrayList<>();
        double[][] at = {{31.5, 30.5}, {28.5, 33.5}, {25.5, 30.5}, {28.5, 27.5}, {31.0, 33.0}};
        for (int i = 0; i < at.length; i++) {
            var type = i < 3 ? SavannaEntities.BARB.get() : SavannaEntities.TILAPIA.get();
            net.minecraft.world.entity.Mob f = helper.spawn(type, new Vec3(at[i][0], y, at[i][1]));
            f.setPersistenceRequired();
            fish.add(f);
            helper.assertTrue(f.distanceTo(shark) < 4.5, "a fish set down " + String.format("%.1f", f.distanceTo(shark)) + " from the shark");
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(BullSharkEntity.partsFish(shark), "a fed shark in the water does not part the fish (in water "
                    + shark.isInWater() + ")");
            for (net.minecraft.world.entity.Mob f : fish) {
                helper.assertTrue(f.distanceTo(shark) > 5.5, f.getType().toShortString() + " still "
                        + String.format("%.1f", f.distanceTo(shark)) + " blocks from the shark");
            }
        });
    }

    /** A hippo near in the water: the shark swims away until it is well clear. */
    @GameTest(template = "arena", timeoutTicks = 600, batch = BATCH)
    public static void aSharkKeepsClearOfAHippo(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        pool(helper);
        var hippo = helper.spawn(SavannaEntities.HIPPO.get(), new Vec3(16.5, WT - DEEP + 1, 30.5));
        hippo.setNoAi(true);
        BullSharkEntity shark = shark(helper, 25.5, 30.5, 90.0F, false);
        helper.succeedWhen(() -> helper.assertTrue(shark.distanceTo(hippo) > 18.0, "the shark is still " + String.format("%.1f", shark.distanceTo(hippo))
                + " blocks from the hippo (hippo in water " + hippo.isInWater() + ")"));
    }

    /** A player on the bank: the shark comes near, faces them and lifts its head out to look (the spy hop). */
    @GameTest(template = "arena", timeoutTicks = 800, batch = BATCH)
    public static void aSharkSpyHopsAtAPlayerOnTheBank(GameTestHelper helper) {
        TestClock.still(helper, 12.0);
        pool(helper);
        BullSharkEntity shark = shark(helper, 24.5, 32.5, -90.0F, false);
        shark.makeCurious();
        shark.spyNext = true;
        ServerPlayer player = swimmer(helper, 28.5, WT + 1, 43.5);
        helper.onEachTick(() -> {
            hold(helper, player, 28.5, WT + 1, 43.5);
            if (shark.action() == BullSharkEntity.ACT_SPY) {
                double off = Math.sqrt(Math.pow(shark.getX() - player.getX(), 2) + Math.pow(shark.getZ() - player.getZ(), 2));
                helper.assertTrue(off < 9.0, "it spy hopped " + String.format("%.1f", off) + " blocks from the player");
                helper.getLevel().getServer().getPlayerList().remove(player);
                helper.succeed();
            }
        });
        helper.runAtTickTime(790, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.fail("no spy hop: action " + shark.action() + ", " + String.format("%.1f", shark.distanceTo(player)) + " off");
        });
    }

    /** Stranded on dry ground far from water it thrashes and slowly suffocates (still alive after 30 s, hurt a little). */
    @GameTest(template = "arena", timeoutTicks = 700, batch = BATCH)
    public static void aStrandedSharkSuffocatesSlowly(GameTestHelper helper) {
        pool(helper);
        BullSharkEntity shark = helper.spawn(SavannaEntities.BULL_SHARK.get(), new Vec3(59.5, WT + 1, 30.5));
        shark.setPersistenceRequired();
        float full = shark.getHealth();
        helper.onEachTick(() -> helper.assertFalse(shark.isInWater(), "the stranded shark reached water"));
        helper.runAtTickTime(600, () -> {
            helper.assertTrue(shark.isAlive() && shark.getHealth() < full - 3 && shark.getHealth() > full - 12,
                    "after 30 s stranded its health is " + shark.getHealth() + " of " + full + "; " + shark.about());
            helper.succeed();
        });
    }

    /** Stranded on the bank a few blocks from the water, its flops carry it back in. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = BATCH)
    public static void aStrandedSharkFlopsBackIntoTheWater(GameTestHelper helper) {
        pool(helper);
        BullSharkEntity shark = helper.spawn(SavannaEntities.BULL_SHARK.get(), new Vec3(30.5, WT + 1, BANK_FROM + 2.5));
        shark.setPersistenceRequired();
        helper.succeedWhen(() -> helper.assertTrue(shark.isInWater(), "the stranded shark is still on the bank at "
                + shark.position().subtract(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)))));
    }
}
