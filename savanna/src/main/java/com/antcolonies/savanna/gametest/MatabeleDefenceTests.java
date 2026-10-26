package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.UUID;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyParts;
import com.antcolonies.fight.DefenceNumbers;
import com.antcolonies.fight.NestDefence;
import com.antcolonies.gametest.TestIsolation;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.registry.ModEffects;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.NewLandMounds;
import com.antcolonies.savanna.command.MatabeleCommands;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Matabele nest's defence against a player (M8 step 2e, {@code fight.NestDefence}), one scene: a colony of 10 in a
 * dead mound and a player in survival mode. Standing near the nest provokes nobody, nor does a player in creative mode
 * on the entrance; a survival player on the entrance brings a few ants out to look, who sting only once the player has
 * stayed 3 s, jumping or not (BUGS 79) (the sting hurts and leaves the pain); far off, out of reach, the defence ends and the ants go home;
 * hitting one provokes the colony, and breaking the nest brings out a whole wave.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MatabeleDefenceTests {
    @GameTest(template = "arena", timeoutTicks = 4800, skyAccess = true)
    public static void matabeleDefendTheirNestAgainstAPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        Colony termites = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(termites != null, "no dead mound put in");
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(MIDDLE + 9, SURFACE, MIDDLE)), 10);
        helper.assertTrue(colony != null, "no Matabele colony made");
        NestDefence defence = colony.part(ColonyParts.NEST_DEFENCE).orElse(null);
        helper.assertTrue(defence != null, "a Matabele colony has no nest defence");
        DefenceNumbers numbers = DefenceNumbers.of(colony.profile()).orElseThrow();
        ServerPlayer player = survivalPlayer(helper);
        ServerPlayer builder = helper.makeMockServerPlayerInLevel(); // the game's test player plays in creative
        long[] since = new long[1];
        // BUGS 79: the player on the entrance jumps (high, now and then out of the entrance's height at the once-a-second
        // check) all the while; the ants still come out, and sting once it has stayed 3 s.
        BlockPos[] hopping = new BlockPos[1];
        helper.onEachTick(() -> {
            if (hopping[0] != null) {
                stand(player, helper.getTick() % 13 < 6 ? hopping[0].above(5) : hopping[0]);
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(colony.nest().getFinishedRoomCenter(level) != null, "the queen's room is not open yet"))
                .thenExecute(() -> {
                    // Near the nest, not on it, and a creative player on the entrance: nothing.
                    stand(player, colony.getCenter().offset(6, 0, 0));
                    stand(builder, colony.getCenter());
                    since[0] = level.getGameTime();
                })
                .thenWaitUntil(() -> helper.assertTrue(level.getGameTime() - since[0] >= 80, "waiting"))
                .thenExecute(() -> {
                    helper.assertTrue(defence.defendersOut() == 0, defence.defendersOut() + " defenders out with nobody provoking them");
                    helper.assertFalse(defence.isProvoking(player), "a player standing near the nest provokes it");
                    helper.assertFalse(defence.isProvoking(builder), "a creative player provokes the nest");
                    stand(builder, colony.getCenter().offset(30, 0, 30));
                    // On the entrance (beside the chimney's open top, on the ground): a few come out to look, and do
                    // not sting yet.
                    hopping[0] = beside(level, colony.getCenter());
                    stand(player, hopping[0]);
                    since[0] = level.getGameTime();
                })
                .thenWaitUntil(() -> helper.assertTrue(defence.defendersOut() > 0, "nobody came out to look at a player on the entrance"))
                .thenExecute(() -> {
                    helper.assertTrue(defence.defendersOut() <= numbers.look().ants(), defence.defendersOut() + " came out to look, more than " + numbers.look().ants());
                    if (level.getGameTime() - since[0] < numbers.look().stingAfter() - 25) {
                        helper.assertFalse(defence.isProvoking(player), "the lookers may sting at once");
                    }
                })
                // Still there after 3 s: they sting, it hurts and the pain lasts.
                .thenWaitUntil(() -> helper.assertTrue(defence.isProvoking(player), "a player staying on the entrance is not stung"))
                .thenExecute(() -> {
                    hopping[0] = null; // (lands: hopping 5 high, it is out of their reach half the time)
                    stand(player, beside(level, colony.getCenter()));
                })
                .thenWaitUntil(() -> helper.assertTrue(player.getHealth() < player.getMaxHealth() && player.hasEffect(ModEffects.STING_PAIN),
                        "the player on the entrance was not stung (health " + player.getHealth() + ")"))
                .thenExecute(() -> {
                    player.setHealth(player.getMaxHealth());
                    hopping[0] = null;
                    // Far off, out of reach: the defence ends, everyone goes home.
                    stand(player, colony.getCenter().offset(0, 0, 40));
                })
                .thenWaitUntil(() -> helper.assertTrue(!defence.isProvoking(player) && defence.defendersOut() == 0,
                        "the defence did not end with the player 40 blocks off (" + defence.defendersOut() + " out)"))
                .thenExecute(() -> {
                    // Hitting one provokes the colony.
                    MatabeleEntity ant = TestIsolation.insectsOf(level, colony, MatabeleEntity.class).stream().findFirst().orElseThrow();
                    stand(player, ant.blockPosition().above(2));
                    ant.hurt(level.damageSources().playerAttack(player), 0.5F);
                    helper.assertTrue(defence.isProvoking(player), "hitting a Matabele did not provoke its colony");
                    // Breaking the nest brings out a wave.
                    BlockPos broken = nestBlock(level, colony);
                    helper.assertTrue(broken != null, "no block of the nest to break");
                    stand(player, broken.above());
                    helper.assertTrue(player.gameMode.destroyBlock(broken), "the player could not break " + broken.toShortString());
                })
                .thenWaitUntil(() -> helper.assertTrue(defence.defendersOut() >= numbers.firstWave(),
                        "only " + defence.defendersOut() + " defenders out after the nest was broken"))
                .thenExecute(() -> {
                    player.discard();
                    builder.discard();
                })
                .thenSucceed();
    }

    /** A block of the nest a player could break: solid, just round the entrance. */
    private static BlockPos nestBlock(ServerLevel level, Colony colony) {
        BlockPos entrance = colony.getCenter();
        for (BlockPos pos : BlockPos.betweenClosed(entrance.offset(-2, -2, -2), entrance.offset(2, 1, 2))) {
            if (!level.getBlockState(pos).isAir() && level.getBlockState(pos).getDestroySpeed(level, pos) >= 0) {
                return pos.immutable();
            }
        }
        return null;
    }

    /** Where a player stands on the entrance: the ground next to the chimney's open top. */
    private static BlockPos beside(ServerLevel level, BlockPos entrance) {
        for (BlockPos pos : new BlockPos[] {entrance.east(), entrance.west(), entrance.north(), entrance.south()}) {
            BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos);
            if (Math.abs(top.getY() - entrance.getY()) <= 2) {
                return top;
            }
        }
        return entrance;
    }

    private static void stand(ServerPlayer player, BlockPos at) {
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
    }

    /** A mock player in survival mode (the game's own test player plays in creative, which the ants ignore). */
    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-survival-player"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        // A new player cannot be hurt for its first 3 s, counted down only while it ticks (a test player never does).
        net.neoforged.fml.util.ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, player, 0, "spawnInvulnerableTime");
        return player;
    }
}
