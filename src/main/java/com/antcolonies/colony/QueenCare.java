package com.antcolonies.colony;

import java.util.UUID;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The queen's side of the colony: how much bread she has been fed, the egg she carries (or the burst of eggs after rich
 * bread), the workers on their way to feed her, and her hunger clock (she starves after days without food). A queen of
 * a species with {@code laying} in its profile (the termite queen) carries no egg: she lays a steady stream of them as
 * long as she is fed (see {@link #layInStream}), and what she has been fed is the food in her (up to the profile's meal).
 */
public class QueenCare {
    // Her numbers (meal size, rich meal and burst, how long she carries an egg, how long she lasts unfed) come from the
    // species profile: colony.profile().queen(). Rich bread is made from rich seeds, see antcolonies:rich_seeds.

    /** How long (in ticks) a worker on its way to feed the queen counts as feeding her. */
    private static final int FEED_CLAIM_TICKS = 1300;

    private final Colony colony;

    /** How many pieces of bread the workers have fed the queen for her next egg (the profile's meal = full). Saved. */
    private int queenBread;
    /** The game time when the egg she carries comes out (-1 = she carries none). During a burst: when the next egg is due. Saved. */
    private long eggDue = -1;
    /** How many pieces of rich bread the workers have fed the queen towards a burst (the profile's rich meal = it starts). Saved. */
    private int queenRich;
    /** Eggs the queen still has to lay in her burst (0 = no burst going on). Saved. */
    private int burstLeft;
    /** How many eggs the queen has laid in all. Saved. */
    private int eggsLaid;
    /** When the next egg of the founding batch comes ({@link Colony#NOT_SET} = not counting yet). Saved. */
    private long nextFoundingEgg = Colony.NOT_SET;
    /** A queen who lays a steady stream: the game time when her next egg comes ({@link Colony#NOT_SET} = not counting). Saved. */
    private long nextStreamEgg = Colony.NOT_SET;
    /** The game time of the queen's last piece of bread, or of the last time she needed none. Saved. */
    private long queenLastFed = Colony.NOT_SET;
    /** The workers on their way to feed the queen: ant -> game time when the claim runs out. Not saved. */
    private final Claims<UUID> feeders = new Claims<>();

    QueenCare(Colony colony) {
        this.colony = colony;
    }

    public int getQueenBread() {
        return queenBread;
    }

    public int getQueenRich() {
        return queenRich;
    }

    /** Eggs left in the queen's burst (0 = none going on). */
    public int getBurstLeft() {
        return burstLeft;
    }

    /** Whether the queen carries an egg right now. */
    public boolean isIncubating() {
        return eggDue >= 0;
    }

    /** The game time when the egg she carries comes out (-1 if she carries none). */
    public long getEggDue() {
        return eggDue;
    }

    public int getEggsLaid() {
        return eggsLaid;
    }

    public long getQueenLastFed() {
        return queenLastFed;
    }

    public void setQueenLastFed(long time) {
        queenLastFed = time;
    }

    /** True when the queen sits in her finished room (the workers feed her there, and her egg comes out there). */
    public boolean isQueenHome(ServerLevel level) {
        BlockPos room = colony.nest().getFinishedRoomCenter(level);
        AbstractAntEntity queen = room == null ? null : colony.getQueen(level);
        return queen != null && Math.abs(queen.getX() - (room.getX() + 0.5)) <= 2.5
                && Math.abs(queen.getZ() - (room.getZ() + 0.5)) <= 2.5 && Math.abs(queen.getY() - room.getY()) < 1.5;
    }

    /**
     * Whether the queen can take more bread: she is home and not carrying an egg, the nursery is not full of young, and
     * the workers already on their way with bread would not fill her.
     */
    public boolean needsFeeding(ServerLevel level) {
        feeders.prune(level.getGameTime());
        return eggDue < 0 && queenBread + feeders.size() < colony.profile().queen().meal() && isQueenHome(level)
                && !colony.brood().isFull(level);
    }

    /** A worker sets out to bring the queen bread. False if she needs no more food right now. */
    public boolean claimFeeding(ServerLevel level, UUID ant) {
        if (!needsFeeding(level)) {
            return false;
        }
        feeders.put(ant, level.getGameTime() + FEED_CLAIM_TICKS);
        return true;
    }

    /** The worker has fed her, or gave up. */
    public void releaseFeeding(UUID ant) {
        feeders.remove(ant);
    }

    /**
     * A worker gives the queen one piece of bread. With a full meal she starts carrying an egg, which comes out after
     * the incubation time. False if she takes no more right now (the worker keeps the bread).
     */
    public boolean feedQueen(ServerLevel level, boolean rich) {
        SpeciesProfile.QueenNumbers numbers = colony.profile().queen();
        if (eggDue >= 0 || queenBread >= numbers.meal()) {
            return false;
        }
        queenLastFed = level.getGameTime();
        if (colony.profile().laying().isPresent()) {
            queenBread++; // a queen who lays a steady stream just holds the food (see layInStream)
            return true;
        }
        if (rich) {
            // Rich bread: with a rich meal a burst of eggs starts, one every burst interval, and she needs no more food for it.
            if (++queenRich >= numbers.richMeal()) {
                queenRich = 0;
                queenBread = 0;
                burstLeft = numbers.burstEggs();
                eggDue = level.getGameTime() + numbers.burstIntervalTicks();
            }
            return true;
        }
        queenBread++;
        if (queenBread >= numbers.meal()) {
            eggDue = level.getGameTime() + numbers.incubationTicks();
        }
        return true;
    }

    /** Whether the egg the queen carries is ready to come out. */
    public boolean isEggDue(ServerLevel level) {
        if (eggDue < 0 || level.getGameTime() < eggDue) {
            return false;
        }
        return burstLeft == 0 || !colony.brood().isFull(level); // a burst egg waits while the nursery is full of young
    }

    /**
     * A queen founding her colony lays one egg of her founding batch, from her own reserves (no meal needed), at this
     * spot; it is put in its place at once (it hatches where it lies).
     */
    public void layFoundingEgg(ServerLevel level, double x, double y, double z) {
        eggsLaid++;
        colony.brood().spawnEgg(level, x, y, z).setPlaced(true);
    }

    /**
     * The founding batch (species with {@code founding} in their profile), called by the queen once a second. Once the
     * founders have sealed themselves in and she lies in her home, she lays the batch's eggs one by one from her own
     * reserves; and the founders feed the young on their own body fat, the hungriest one a piece every feed interval,
     * until the batch has grown into the first workers. Fat feeding stops once that many workers exist, or once she has
     * laid eggs beyond the batch (those are fed by the nurses). Returns true if anything changed that should be saved.
     */
    public boolean raiseFoundingBatch(ServerLevel level, AbstractAntEntity queen) {
        SpeciesProfile.Founding founding = colony.profile().founding().orElse(null);
        if (founding == null) {
            return false;
        }
        long now = level.getGameTime();
        boolean changed = false;
        boolean sealedIn = isQueenHome(level) && !colony.nest().isEntranceOpen(level);
        if (sealedIn && colony.isFounding() && eggsLaid < founding.eggs()) {
            if (nextFoundingEgg == Colony.NOT_SET) {
                nextFoundingEgg = now + founding.eggIntervalTicks();
                changed = true;
            } else if (now >= nextFoundingEgg) {
                BlockPos spot = colony.brood().pickEggSpot(level, queen.getRandom());
                Vec3 at = spot == null ? queen.position() : Vec3.atBottomCenterOf(spot);
                layFoundingEgg(level, at.x, at.y, at.z);
                nextFoundingEgg = now + founding.eggIntervalTicks();
                changed = true;
            }
        }
        boolean raising = eggsLaid <= founding.eggs() && colony.getCount(WorkerAntEntity.CASTE) < founding.eggs();
        if (raising && now % founding.feedIntervalTicks() < 20) {
            // The hungriest young one gets the next piece.
            colony.brood().getLarvae(level).stream().filter(young -> !young.isFull())
                    .min((a, b) -> Integer.compare(a.getFed(), b.getFed()))
                    .ifPresent(young -> {
                        young.feed();
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, young.getX(), young.getY() + 0.2, young.getZ(), 2, 0.1, 0.05, 0.1, 0.0);
                    });
        }
        return changed;
    }

    /**
     * A queen who lays a steady stream of eggs (the profile's {@code laying}; called by her once a second): while she is
     * in her room, has the food for an egg in her and the nursery is not full of young, an egg comes every interval
     * (shorter the more she has swollen: {@code size}, 0 to 1), using up that food. Not while the colony is founding
     * (that batch is laid from her own fat). True when an egg is due now: the queen then picks the spot behind her and
     * calls {@link #layStreamEgg}.
     */
    public boolean layInStream(ServerLevel level, float size) {
        SpeciesProfile.Laying laying = colony.profile().laying().orElse(null);
        if (laying == null) {
            return false;
        }
        boolean canLay = !colony.isFounding() && queenBread >= laying.foodPerEgg() && isQueenHome(level) && !colony.brood().isFull(level);
        long now = level.getGameTime();
        if (!canLay) {
            nextStreamEgg = Colony.NOT_SET; // the clock starts again once she can lay
            return false;
        }
        if (nextStreamEgg == Colony.NOT_SET) {
            nextStreamEgg = now + laying.intervalFor(size);
            return false;
        }
        return now >= nextStreamEgg;
    }

    /** The queen lays the egg of her steady stream at this spot (see {@link #layInStream}); it uses up its food. */
    public void layStreamEgg(ServerLevel level, double x, double y, double z, float size) {
        SpeciesProfile.Laying laying = colony.profile().laying().orElseThrow();
        eggsLaid++;
        queenBread = Math.max(0, queenBread - laying.foodPerEgg());
        nextStreamEgg = level.getGameTime() + laying.intervalFor(size);
        colony.brood().spawnEgg(level, x, y, z);
    }

    /** The game time of the next egg of the steady stream ({@link Colony#NOT_SET} if she cannot lay now). */
    public long getNextStreamEgg() {
        return nextStreamEgg;
    }

    /** The egg comes out of the queen at this spot; she starts over with an empty stomach. */
    public void layEgg(ServerLevel level, double x, double y, double z) {
        eggsLaid++;
        if (burstLeft > 0) {
            // A burst egg: the next one is due a minute later, until the burst is over.
            burstLeft--;
            eggDue = burstLeft > 0 ? level.getGameTime() + colony.profile().queen().burstIntervalTicks() : -1;
        } else {
            queenBread = 0;
            eggDue = -1;
        }
        colony.brood().spawnEgg(level, x, y, z);
    }

    /**
     * Called by the queen once a second. Her hunger clock only runs while she wants food and gets none: it starts over
     * every time she is fed and for as long as she needs nothing (she carries an egg, she has her full meal, or the
     * nursery is full of young). Returns true when she has gone the profile's starve time without it.
     */
    public boolean queenStarves(ServerLevel level) {
        long now = level.getGameTime();
        SpeciesProfile.QueenNumbers numbers = colony.profile().queen();
        // A founding queen lives on her own body fat (nobody could feed her yet).
        boolean satisfied = !colony.isHungerOn() || eggDue >= 0 || queenBread >= numbers.meal()
                || colony.brood().isFull(level) || colony.isFounding();
        if (queenLastFed == Colony.NOT_SET || satisfied) {
            queenLastFed = now;
            return false;
        }
        return now - queenLastFed >= numbers.starveTicks();
    }

    void save(CompoundTag tag) {
        tag.putInt("queenBread", queenBread);
        tag.putInt("queenRich", queenRich);
        tag.putInt("burstLeft", burstLeft);
        tag.putLong("queenLastFed", queenLastFed);
        tag.putLong("eggDue", eggDue);
        tag.putInt("eggsLaid", eggsLaid);
        tag.putLong("nextFoundingEgg", nextFoundingEgg);
        tag.putLong("nextStreamEgg", nextStreamEgg);
    }

    void load(CompoundTag tag) {
        queenBread = tag.contains("queenBread") ? tag.getInt("queenBread") : tag.getInt("queenSeeds"); // (old saves: seeds)
        queenRich = tag.getInt("queenRich");
        burstLeft = tag.getInt("burstLeft");
        queenLastFed = tag.contains("queenLastFed") ? tag.getLong("queenLastFed") : Colony.NOT_SET;
        eggDue = tag.contains("eggDue") ? tag.getLong("eggDue") : -1;
        eggsLaid = tag.getInt("eggsLaid");
        nextFoundingEgg = tag.contains("nextFoundingEgg") ? tag.getLong("nextFoundingEgg") : Colony.NOT_SET;
        nextStreamEgg = tag.contains("nextStreamEgg") ? tag.getLong("nextStreamEgg") : Colony.NOT_SET;
    }
}
