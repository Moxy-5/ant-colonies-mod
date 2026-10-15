package com.antcolonies.entity.goal;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Mouth-to-mouth feeding (trophallaxis), for the nurses' feeding goals ({@link FeedQueenGoal}, {@link FeedLarvaGoal})
 * of a species whose profile says {@code worker.diet.mouth_to_mouth} (Macrotermes). The real thing: a nurse eats fungus
 * nodules, part-digests them in its crop and brings the food up for the queen, the king, a soldier or a nymph, the two
 * head to head. Here: the nurse eats the nodule at the comb (it vanishes, crumbs, a soft chewing sound) and carries it in
 * its crop (nothing in its jaws, {@link AbstractAntEntity#swallowToPassOn}), walks to the other's head, the two face each
 * other with their heads together for a moment, tiny pale droplets pass between their mouths, and the food is handed
 * over. Only the look and the walk change: what is fed and when is the same as when the food is carried.
 */
final class MouthToMouth {
    /** How long (ticks) the nurse takes to eat the nodule at the comb (my pick, to confirm). */
    static final int SWALLOW_TICKS = 20;
    /** How long (ticks) the two stay head to head while the food is passed (my pick, to confirm). */
    static final int FEED_TICKS = 40;
    /** How close (sideways, in blocks) the nurse's head must be to the other's mouth. */
    private static final double MOUTH_REACH = 0.45;

    private MouthToMouth() {
    }

    /** Whether this colony's nurses feed mouth to mouth. */
    static boolean on(Colony colony) {
        return colony.profile().worker().diet().mouthToMouth();
    }

    /** Where the other's mouth is: an insect's head, or the middle of a nymph (it lies on the comb). */
    static Vec3 mouthOf(Entity receiver) {
        return receiver instanceof AbstractAntEntity insect ? insect.headPoint() : receiver.position();
    }

    /** The cell the nurse walks to, to reach the other's mouth. */
    static BlockPos cellAtMouth(Entity receiver) {
        return BlockPos.containing(mouthOf(receiver).add(0, 0.1, 0));
    }

    /**
     * Whether the nurse is head to head with the other: its head at the other's mouth; or, where it cannot get closer
     * (its walk is over), within {@code reach} of the other's middle.
     */
    static boolean atMouth(AbstractAntEntity nurse, Entity receiver, double reach) {
        if (Math.abs(nurse.getY() - receiver.getY()) >= 1.5) {
            return false;
        }
        Vec3 head = nurse.headPoint();
        Vec3 mouth = mouthOf(receiver);
        double hx = head.x - mouth.x;
        double hz = head.z - mouth.z;
        if (hx * hx + hz * hz <= MOUTH_REACH * MOUTH_REACH) {
            return true;
        }
        double dx = nurse.getX() - receiver.getX();
        double dz = nurse.getZ() - receiver.getZ();
        return nurse.getNavigator().isDone() && dx * dx + dz * dz <= reach * reach;
    }

    /** The two turn to face each other (the queen too big to turn turns her head). */
    static void faceEachOther(AbstractAntEntity nurse, Entity receiver) {
        Vec3 mouth = mouthOf(receiver);
        float yaw = (float) (Mth.atan2(mouth.z - nurse.getZ(), mouth.x - nurse.getX()) * Mth.RAD_TO_DEG) - 90.0F;
        nurse.setYRot(yaw);
        nurse.setYBodyRot(yaw);
        nurse.setYHeadRot(yaw);
        nurse.getLookControl().setLookAt(mouth.x, mouth.y, mouth.z);
        if (receiver instanceof Mob mob) {
            mob.getLookControl().setLookAt(nurse);
        }
    }

    /** Eating the nodule at the comb, every few ticks: crumbs of it and a soft chewing sound. */
    static void swallowing(ServerLevel level, AbstractAntEntity nurse, int tick) {
        if (tick % 10 == 0) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, nurse.getCarriedSeed().copy()), nurse.getX(), nurse.getY() + 0.1,
                    nurse.getZ(), 3, 0.05, 0.03, 0.05, 0.02);
            level.playSound(null, nurse.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.12F, 1.9F);
        }
    }

    /** Head to head, every few ticks: a tiny pale droplet between the two mouths. */
    static void passing(ServerLevel level, AbstractAntEntity nurse, Entity receiver, ItemStack food, int tick) {
        if (tick % 8 == 0) {
            Vec3 between = nurse.headPoint().add(mouthOf(receiver)).scale(0.5);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, food.copy()), between.x, between.y + 0.08, between.z,
                    1, 0.01, 0.01, 0.01, 0.0);
        }
        if (tick == 0) {
            level.playSound(null, nurse.blockPosition(), SoundEvents.HONEY_DRINK, SoundSource.NEUTRAL, 0.08F, 2.0F);
        }
    }
}
