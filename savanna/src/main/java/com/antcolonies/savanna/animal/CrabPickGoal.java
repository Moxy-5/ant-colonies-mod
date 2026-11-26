package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.antcolonies.savanna.block.AlgaeBlock;
import com.antcolonies.savanna.block.CrabShellBlock;
import com.antcolonies.savanna.block.ScrapsBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A hungry crab walks to the best food near it and picks at it (M9 river life, 2026-11-15, the user's ask "make it
 * actually pick the algae into its mouth"): remains first, then a scraps film, then algae (the nearest of the best
 * kind). Standing over it, facing it, it plays the pick clip once: the left claw pinches a bit off and carries it to
 * the mouth, then the right; each bit going into the mouth is one bite off the food ({@link #BITE_TICKS}). Then it is
 * fed for 2 to 4 minutes. With nothing to eat near it strolls on: a crab never starves (the balance rule). My picks.
 *
 * <p>Snails (2026-11-16, the user: "Yes, crabs eat snails"): after remains, a snail within {@link #LOOK_ACROSS} is the
 * meal one time in {@link #SNAIL_ODDS} when scraps or algae are near too, every time when nothing else is (so a crab
 * never clears a bed of snails while it has plants to pick). It walks up to it (a snail pulled into its shell is caught
 * all the same), grabs it (the snail is gone, its shell now in the claws) and plays the crack clip: squeezing, the shell
 * cracks with a few chips flying ({@link #CRACK_TICK}), the right claw picks the snail out into the mouth twice, and the
 * broken shell is put down on the bed ({@link CrabShellBlock#leave}). Cut short after the grab, it still drops the shell
 * and counts as fed. My picks.
 */
public class CrabPickGoal extends Goal {
    /** How far it smells remains, and looks for snails, scraps and algae (blocks across; up or down). */
    static final double SMELL = 8.0;
    static final int LOOK_ACROSS = 6, LOOK_UP_DOWN = 2;
    /** How close (blocks) eggs must lie for a crab to pick at them (my pick). */
    static final int EGG_REACH = 3;
    /** The pick clip's length, and when each claw's bit goes into the mouth (ticks; {@code crab_models.py} PICK_LEN, PICK_BITE, plus the clip's blend-in). */
    static final int PICK_TICKS = 80;
    static final int[] BITE_TICKS = {37, 65};
    /** How long it may take to walk there before giving up. */
    static final int GIVE_UP_TICKS = 300;
    /** One meal in this many is a snail when other food is near (else every one). */
    static final int SNAIL_ODDS = 3;
    /** The crack clip's length and when the shell gives (ticks; {@code crab_models.py} CRACK_LEN, CRACK_AT, plus the clip's blend-in). */
    static final int CRACK_TICKS = 84, CRACK_TICK = 28;
    /** How far in front of the crab the shell is held (blocks at size 1; {@code crab_models.py} HELD_AT). */
    static final double HELD_AHEAD = 0.3;
    /** How long until it is hungry again after a meal (ticks; 2 to 4 minutes). */
    static final int FED_MIN = 2400, FED_MORE = 2400;

    private final CrabEntity crab;
    @Nullable
    private RemainsEntity remains;
    @Nullable
    private BlockPos block;
    @Nullable
    private SnailEntity snail;
    /** Where the shell is held while it cracks one (null: not cracking). */
    @Nullable
    private Vec3 shellAt;
    private int ticks, picking;
    private boolean fed, shellLeft;
    /** It got at least one bite of this meal down (review R10-13). */
    private boolean ate;

    public CrabPickGoal(CrabEntity crab) {
        this.crab = crab;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (crab.hungryIn > 0 || crab.getRandom().nextInt(10) != 0) {
            return false;
        }
        if (!find()) {
            crab.hungryIn = 200;   // nothing near: look again in 10 s, strolling on meanwhile
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !fed && food() != null && (picking > 0 || shellAt != null || ticks < GIVE_UP_TICKS);
    }

    @Override
    public void start() {
        ticks = picking = 0;
        fed = shellLeft = ate = false;
        shellAt = null;
    }

    @Override
    public void stop() {
        if (shellAt != null && !shellLeft) {
            dropShell();   // cut short after the grab: the snail is eaten all the same
            fed = true;
            crab.hungryIn = FED_MIN + crab.getRandom().nextInt(FED_MORE);
        }
        shellAt = null;
        snail = null;
        crab.setDoing(CrabEntity.CALM);
        crab.getNavigation().stop();
        if (!fed) {
            // a meal that ended early because the food ran out counts all the same, whichever bite took the last of it
            // (review R10-13: food gone at the first bite left the crab hungry again in seconds, stripping mat after mat)
            crab.hungryIn = ate ? FED_MIN + crab.getRandom().nextInt(FED_MORE) : 100 + crab.getRandom().nextInt(200);
        }
        remains = null;
        block = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Vec3 food = food();
        if (food == null) {
            return;
        }
        ticks++;
        if (shellAt != null) {
            crack();
            return;
        }
        double dx = food.x - crab.getX(), dz = food.z - crab.getZ();
        double far = Math.sqrt(dx * dx + dz * dz);
        // its claws reach about a third of a block in front of it; at remains it walks to the nearest part of the fish
        // (food() gives the nearest point along its spine) and stops with its claws over the fish's side
        double reach = 0.45 * crab.getScale() + (remains != null ? 0.1 * remains.size() : snail != null ? 0.1 : 0.0);
        if (picking == 0 && (far > reach + 0.15 || Math.abs(food.y - crab.getY()) > 1.2)) {
            if (far < 1.5) {       // the last stretch: a route counts as walked a little short of its end
                crab.getNavigation().stop();
                crab.getMoveControl().setWantedPosition(food.x, crab.getY(), food.z, 0.6);
            } else if (ticks % 20 == 1 || crab.getNavigation().isDone()) {
                crab.getNavigation().moveTo(food.x, food.y, food.z, 0.8);
            }
            return;
        }
        crab.getNavigation().stop();
        crab.face(food.x, food.z);
        if (snail != null) {
            grab();
            return;
        }
        crab.setDoing(remains != null || isScraps() || isSnailEggs() ? CrabEntity.PICK_MEAT : CrabEntity.PICK_ALGAE);
        picking++;
        for (int at : BITE_TICKS) {
            if (picking == at) {
                if (bite()) {
                    ate = true;
                } else {
                    remains = null;
                    block = null;
                    return;
                }
            }
        }
        if (picking >= PICK_TICKS) {
            fed = true;
        }
        if (fed) {
            crab.hungryIn = FED_MIN + crab.getRandom().nextInt(FED_MORE);
        }
    }

    /** Takes the snail into its claws: the snail is gone, its shell held in front of the crab. */
    private void grab() {
        double yaw = Math.toRadians(crab.getYRot());
        double ahead = HELD_AHEAD * crab.getScale();
        shellAt = new Vec3(crab.getX() - Math.sin(yaw) * ahead, crab.getY(), crab.getZ() + Math.cos(yaw) * ahead);
        snail.discard();
        snail = null;
        picking = 0;
        crab.setDoing(CrabEntity.CRACK);
    }

    /** One tick of cracking the shell and eating the snail. */
    private void crack() {
        crab.getNavigation().stop();
        crab.setDoing(CrabEntity.CRACK);
        picking++;
        if (picking == CRACK_TICK && crab.level() instanceof ServerLevel server) {
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(SavannaItems.CRAB_SHELL.get())),
                    shellAt.x, shellAt.y + 0.25 * crab.getScale(), shellAt.z, 6, 0.08, 0.05, 0.08, 0.04);
            server.playSound(null, shellAt.x, shellAt.y, shellAt.z, SoundEvents.TURTLE_EGG_CRACK, SoundSource.NEUTRAL,
                    0.5F, 1.4F + 0.2F * crab.getRandom().nextFloat());
        }
        if (picking >= CRACK_TICKS) {
            dropShell();
            fed = true;
            crab.hungryIn = FED_MIN + crab.getRandom().nextInt(FED_MORE);
        }
    }

    private void dropShell() {
        if (!shellLeft && shellAt != null && crab.level() instanceof ServerLevel server) {
            CrabShellBlock.leave(server, shellAt);
        }
        shellLeft = true;
    }

    private boolean isSnailEggs() {
        return block != null && EggMeals.isSnailEggs(crab.level().getBlockState(block));
    }

    private boolean isScraps() {
        return block != null && crab.level().getBlockState(block).is(SavannaBlocks.SCRAPS.get());
    }

    /** One bit into the mouth: a bite off the food. Whether there was anything to take. */
    private boolean bite() {
        if (remains != null) {
            return remains.isAlive() && remains.bite(crab);
        }
        if (block == null) {
            return false;
        }
        return ScrapsBlock.nibble(crab.level(), block) || EggMeals.bite(crab.level(), block);
    }

    /** Where the food is, or null once it is gone. */
    @Nullable
    private Vec3 food() {
        if (shellAt != null) {
            return shellAt;
        }
        if (snail != null) {
            return snail.isAlive() ? snail.position() : null;
        }
        if (remains != null) {
            return remains.isAlive() && remains.bitesLeft() > 0 ? nearestOnSpine(remains) : null;
        }
        if (block == null) {
            return null;
        }
        BlockState state = crab.level().getBlockState(block);
        if (!state.is(SavannaBlocks.SCRAPS.get()) && AlgaeBlock.bitesLeft(state) == 0 && !EggMeals.forCrab(state)
                && !com.antcolonies.savanna.block.HippoGrassBlock.dyingBite(crab.level(), block, state)) {
            return null;
        }
        return new Vec3(block.getX() + 0.5, block.getY(), block.getZ() + 0.5);
    }

    /**
     * The point of the fish's spine nearest the crab (2026-11-15: walking to the remains' middle left it picking at the
     * air beside a long fish). The fish lies along its heading; the spine counted is the part sure to be under meat
     * either way round (half the shorter end's length from the middle, in blocks at size 1; from the remains' models).
     */
    private Vec3 nearestOnSpine(RemainsEntity r) {
        double half = switch (r.kind()) {
            case CATFISH -> 0.8;
            case CATFISH_TORN -> 0.12;
            case TILAPIA -> 0.25;
            case TILAPIA_HALF -> 0.04;
            case BUFFALO -> 1.5;                    // a buffalo's carcass (lion step 2): crabs pick at it too
        } * r.size();
        double yaw = Math.toRadians(r.getYRot());
        double ax = -Math.sin(yaw), az = Math.cos(yaw);
        double along = (crab.getX() - r.getX()) * ax + (crab.getZ() - r.getZ()) * az;
        along = Math.max(-half, Math.min(half, along));
        return new Vec3(r.getX() + ax * along, r.getY(), r.getZ() + az * along);
    }

    /**
     * Picks the food: the nearest remains with meat on them; else the nearest eggs (catfish eggs on the algae or a snail
     * clutch, a treat: animal life cycle step 3); else the nearest snail now and then (always with nothing else near);
     * else the nearest scraps, else the nearest algae.
     */
    private boolean find() {
        remains = null;
        block = null;
        snail = null;
        double best = Double.MAX_VALUE;
        for (RemainsEntity r : crab.level().getEntitiesOfClass(RemainsEntity.class, crab.getBoundingBox().inflate(SMELL, 3.0, SMELL),
                r -> r.bitesLeft() > 0)) {
            double d = r.distanceToSqr(crab);
            if (d < best) {
                best = d;
                remains = r;
            }
        }
        if (remains != null) {
            return true;
        }
        BlockPos home = crab.blockPosition();
        // eggs it all but stands on, the nearest (animal life cycle step 3: catfish eggs on the algae, snail clutches;
        // the user's call, 2026-11-26, review R10-16: it took eggs first from its whole search box, so no snail clutch
        // near a crab ever hatched)
        for (BlockPos at : BlockPos.betweenClosed(home.offset(-EGG_REACH, -LOOK_UP_DOWN, -EGG_REACH),
                home.offset(EGG_REACH, LOOK_UP_DOWN, EGG_REACH))) {
            // only eggs its claws reach from the bed (a clutch high on a bank would hold it there for good)
            if (Math.abs(at.getY() - home.getY()) <= 1 && EggMeals.forCrab(crab.level().getBlockState(at))) {
                double d = at.distSqr(home);
                if (d < best) {
                    best = d;
                    block = at.immutable();
                }
            }
        }
        if (block != null) {
            return true;
        }
        boolean scraps = false;
        for (BlockPos at : BlockPos.betweenClosed(home.offset(-LOOK_ACROSS, -LOOK_UP_DOWN, -LOOK_ACROSS),
                home.offset(LOOK_ACROSS, LOOK_UP_DOWN, LOOK_ACROSS))) {
            BlockState state = crab.level().getBlockState(at);
            boolean isScraps = state.is(SavannaBlocks.SCRAPS.get());
            // dying hippo grass, the food web's step 3: a dying mat's outer edge its claws reach (a block over it at most)
            boolean dying = at.getY() <= home.getY() + 1 && com.antcolonies.savanna.block.HippoGrassBlock.dyingBite(crab.level(), at, state);
            if (!isScraps && (scraps || AlgaeBlock.bitesLeft(state) == 0 && !dying)) {
                continue;
            }
            double d = at.distSqr(home);
            if ((isScraps && !scraps) || d < best) {
                best = d;
                block = at.immutable();
                scraps = isScraps;
            }
        }
        SnailEntity prey = null;
        best = Double.MAX_VALUE;
        for (SnailEntity s : crab.level().getEntitiesOfClass(SnailEntity.class,
                crab.getBoundingBox().inflate(LOOK_ACROSS, LOOK_UP_DOWN, LOOK_ACROSS), SnailEntity::isAlive)) {
            double d = s.distanceToSqr(crab);
            if (d < best) {
                best = d;
                prey = s;
            }
        }
        if (prey != null && (block == null || crab.getRandom().nextInt(SNAIL_ODDS) == 0)) {
            snail = prey;
            block = null;
            return true;
        }
        return block != null;
    }
}
