package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.fight.Fights;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * M8 step 2f, the termites' side of a raid ({@code docs/DESIGN.md}, "Steps 2e and 2f"): what real Macrotermes do when
 * Matabele ants break into a gallery. The soldiers near the break drum (their head bang, felt through the walls) and face
 * it, the drumming running {@code drum_reach} blocks along the gallery and no further; the workers in that reach between
 * the break and the nest go deeper, and the one furthest in packs the gallery shut {@code plug_back} blocks back from the
 * break toward the nest, behind the soldiers, shutting them out (most real ones die there). Those still alive once the
 * raiders have gone wait by the seal, and are let back in at night: in the foraging time, once no foe has come near the
 * break for a while, the seal is bitten open again.
 *
 * <p>Heard from the colony news ({@code BROKEN_OPEN}, told by a Matabele major biting the cover open); the drumming is the
 * alarm's ({@link Alarm#raidBreakIn}). The seals are saved (a seal left standing over a reload still opens at night); who
 * was shut out and who is retreating are not (after a reload the soldiers outside find their own way home once it opens).
 */
public class RaidDefence {
    /** How near (blocks) a break must be to a tunnel cell to break into that tunnel. */
    private static final double INTO_TUNNEL = 2.5;
    /** At most this many breaks are looked after at once (the oldest unsealed one is forgotten). */
    private static final int MOST = 8;
    /** How often (ticks) the breaks are looked at. */
    private static final int TICKS = 10;

    /** The numbers when the species file has none (a test giving this part to another species). */
    private static final SavannaNumbers.RaidNumbers DEFAULT = new SavannaNumbers.RaidNumbers(16, 4, 40, 200, 8, 600);

    /** One break into a gallery, and its seal. */
    private static final class Breach {
        /** Where the raider broke in. */
        final BlockPos at;
        /** The tunnel cell that is packed shut ({@code plug_back} cells back toward the nest). */
        final BlockPos plug;
        /** The cell just behind the plug (the nest side): where the worker packing it stands. */
        final BlockPos behind;
        /** Deeper still: where the other workers go. */
        final BlockPos deeper;
        /** The cell just outside the plug (the break's side): where the shut-out soldiers wait. */
        final BlockPos waitAt;
        /** When the plug is due (the workers' time to get behind it). */
        final long due;
        /** The worker that packs it (the furthest in of those retreating), or null for none real. */
        @Nullable
        UUID plugger;
        /** The workers going deeper (to {@link #deeper}, the plugger to {@link #behind}). */
        final Set<UUID> retreating = new LinkedHashSet<>();
        /** The soldiers shut out when it was packed. */
        final Set<UUID> shutOut = new LinkedHashSet<>();
        /** Whether it is packed; whether this part packed it (and so may bite it open: never a block that was there). */
        boolean sealed;
        boolean ours;
        /** The last time a foe was near the break. */
        long lastFoe;

        Breach(BlockPos at, BlockPos plug, BlockPos behind, BlockPos deeper, BlockPos waitAt, long due) {
            this.at = at;
            this.plug = plug;
            this.behind = behind;
            this.deeper = deeper;
            this.waitAt = waitAt;
            this.due = due;
            this.lastFoe = due;
        }
    }

    private final Colony colony;
    private final List<Breach> breaches = new ArrayList<>();
    private long tickedAt = -TICKS;

    RaidDefence(Colony colony) {
        this.colony = colony;
    }

    SavannaNumbers.RaidNumbers numbers() {
        return colony.profile().section(SavannaNumbers.RAID).orElse(DEFAULT);
    }

    // --- the break ------------------------------------------------------------------------------------------------

    /**
     * {@code by} broke the cover open at {@code where}: if it is a foe and the break opens one of the colony's tunnels, the
     * soldiers near drum, the workers between the break and the nest go deeper and the gallery is to be packed behind them.
     */
    public void brokenOpen(ServerLevel level, BlockPos where, LivingEntity by) {
        if (!Fights.isFoe(colony.profile(), colony.getSpecies(), colony.getId(), by)) {
            return;
        }
        SavannaNumbers.RaidNumbers numbers = numbers();
        colony.part(SavannaParts.ALARM).ifPresent(alarm -> alarm.raidBreakIn(level, where, by, numbers.drumReach()));
        long now = level.getGameTime();
        for (Breach breach : breaches) {
            if (breach.at.distSqr(where) <= INTO_TUNNEL * INTO_TUNNEL || breach.plug.distSqr(where) <= 4) {
                breach.lastFoe = now; // the same break, bitten again (or beside it): its seal is under way
                return;
            }
        }
        Breach breach = planSeal(level, where, now + numbers.plugAfterTicks());
        if (breach == null) {
            return; // not into a tunnel (a runway on the ground, a cast): only the drumming
        }
        // The workers in reach that are nearer the break than the plug go deeper; the furthest in packs it.
        double plugToBreak = Math.sqrt(breach.plug.distSqr(where));
        double reach = numbers.drumReach();
        List<TermiteWorkerEntity> going = new ArrayList<>(level.getEntitiesOfClass(TermiteWorkerEntity.class, new AABB(where).inflate(reach),
                worker -> worker.isAlive() && !worker.isDowned() && colony.getId().equals(worker.getColonyId())
                        && colony.nest().isInside(level, worker.blockPosition())
                        && worker.position().distanceTo(Vec3.atCenterOf(where)) <= plugToBreak + 0.5));
        going.sort(Comparator.comparingDouble(worker -> worker.blockPosition().distSqr(breach.plug)));
        for (TermiteWorkerEntity worker : going) {
            breach.retreating.add(worker.getUUID());
        }
        if (!going.isEmpty()) {
            breach.plugger = going.get(0).getUUID();
        }
        breaches.add(breach);
        while (breaches.size() > MOST) {
            Breach oldest = breaches.stream().filter(b -> !b.sealed).findFirst().orElse(breaches.get(0));
            breaches.remove(oldest);
        }
    }

    /** The seal for a break at {@code where}: the tunnel it opens, the cell {@code plug_back} back from it; null for none. */
    @Nullable
    private Breach planSeal(ServerLevel level, BlockPos where, long due) {
        NestPlan.ForagingTunnel best = null;
        int bestIndex = -1;
        double bestDistance = INTO_TUNNEL * INTO_TUNNEL;
        for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
            List<BlockPos> cells = tunnel.tunnel();
            for (int i = 0; i < cells.size(); i++) {
                double distance = cells.get(i).distSqr(where);
                if (distance <= bestDistance) {
                    best = tunnel;
                    bestIndex = i;
                    bestDistance = distance;
                }
            }
        }
        if (best == null) {
            return null;
        }
        List<BlockPos> cells = best.tunnel();
        int plug = bestIndex - numbers().plugBack();
        if (plug < 1) {
            return null; // the break is right by the nest: nowhere to pack behind it
        }
        return new Breach(where.immutable(), cells.get(plug), cells.get(plug - 1), cells.get(Math.max(0, plug - 3)),
                cells.get(Math.min(cells.size() - 1, plug + 1)), due);
    }

    // --- every half second ----------------------------------------------------------------------------------------

    void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now - tickedAt < TICKS && now >= tickedAt) {
            return;
        }
        tickedAt = now;
        SavannaNumbers.RaidNumbers numbers = numbers();
        for (Breach breach : new ArrayList<>(breaches)) {
            if (!level.isLoaded(breach.plug)) {
                continue;
            }
            if (foeWithin(level, Vec3.atCenterOf(breach.at), numbers.drumReach())) {
                breach.lastFoe = now;
            }
            if (!breach.sealed) {
                trySeal(level, breach, now, numbers);
            } else if (breach.ours && !level.getBlockState(breach.plug).is(plugState().getBlock())) {
                breaches.remove(breach); // the seal is gone (a player broke it)
            } else if (colony.isForagingTime(level) && now - breach.lastFoe >= numbers.overAfterTicks()) {
                open(level, breach);
            }
        }
    }

    /** Packs the plug once it is due, the cell is free and the worker meant to pack it is behind it (or took too long). */
    private void trySeal(ServerLevel level, Breach breach, long now, SavannaNumbers.RaidNumbers numbers) {
        if (now < breach.due) {
            return;
        }
        if (now - breach.due > numbers.plugGiveUpTicks() * 3L) {
            breaches.remove(breach); // never got shut (something sat in the way all along): forgotten
            return;
        }
        BlockState there = level.getBlockState(breach.plug);
        if (!there.getCollisionShape(level, breach.plug).isEmpty()) {
            breach.sealed = true; // already shut (soil fell in, a player): nothing of ours to open later
            breach.ours = false;
            shutOut(level, breach);
            return;
        }
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(breach.plug), LivingEntity::isAlive).isEmpty()) {
            return; // someone stands in it: wait
        }
        TermiteWorkerEntity plugger = breach.plugger != null && level.getEntity(breach.plugger) instanceof TermiteWorkerEntity worker
                && worker.isAlive() ? worker : null;
        boolean behind = plugger != null && plugger.position().distanceTo(Vec3.atBottomCenterOf(breach.behind)) <= 1.2;
        if (plugger != null && !behind && now - breach.due < numbers.plugGiveUpTicks()) {
            return; // the one furthest in is still on its way behind it
        }
        BlockState plug = plugState();
        level.setBlockAndUpdate(breach.plug, plug);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, plug), breach.plug.getX() + 0.5, breach.plug.getY() + 0.5,
                breach.plug.getZ() + 0.5, 10, 0.25, 0.25, 0.25, 0.0);
        level.playSound(null, breach.plug, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 0.6F, 1.2F);
        breach.sealed = true;
        breach.ours = true;
        breach.retreating.clear();
        shutOut(level, breach);
    }

    /** The soldiers left outside the seal (nearer the break than the plug, within the drumming's reach). */
    private void shutOut(ServerLevel level, Breach breach) {
        double plugToBreak = Math.sqrt(breach.plug.distSqr(breach.at));
        Vec3 at = Vec3.atCenterOf(breach.at);
        for (TermiteSoldierEntity soldier : colony.part(SavannaParts.SOLDIERS).map(part -> part.getSoldiers(level)).orElse(List.of())) {
            double distance = soldier.position().distanceTo(at);
            if (distance <= numbers().drumReach() && distance < plugToBreak + 0.5) {
                breach.shutOut.add(soldier.getUUID());
            }
        }
    }

    /** The raid is over and it is night: the seal is bitten open, the shut-out soldiers may come home. */
    private void open(ServerLevel level, Breach breach) {
        BlockState plug = level.getBlockState(breach.plug);
        if (breach.ours && plug.is(plugState().getBlock())) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, plug), breach.plug.getX() + 0.5, breach.plug.getY() + 0.5,
                    breach.plug.getZ() + 0.5, 10, 0.25, 0.25, 0.25, 0.0);
            level.playSound(null, breach.plug, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 0.6F, 1.2F);
            level.removeBlock(breach.plug, false);
        }
        breaches.remove(breach);
    }

    private BlockState plugState() {
        return colony.profile().nest().plugState();
    }

    private boolean foeWithin(ServerLevel level, Vec3 at, double reach) {
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(BlockPos.containing(at)).inflate(reach),
                living -> living.distanceToSqr(at) <= reach * reach && Fights.isFoe(colony.profile(), colony.getSpecies(), colony.getId(), living)).isEmpty();
    }

    // --- asked by the insects -------------------------------------------------------------------------------------

    /** Where this worker goes to get away from a break (deeper, behind the seal to be), or null: it has nowhere to go. */
    @Nullable
    public BlockPos retreatFor(UUID worker) {
        for (Breach breach : breaches) {
            if (!breach.sealed && breach.retreating.contains(worker)) {
                return worker.equals(breach.plugger) ? breach.behind : breach.deeper;
            }
        }
        return null;
    }

    /** The worker got where it was going (it no longer runs there; the plugger stays counted till the seal is packed). */
    public void retreated(UUID worker) {
        for (Breach breach : breaches) {
            if (!worker.equals(breach.plugger)) {
                breach.retreating.remove(worker);
            }
        }
    }

    /**
     * What a shut-out soldier does once the raiders have gone (no foe within {@code quiet_reach}): it waits by the seal
     * facing it, till it opens at night; null for a soldier not shut out (or with a foe near: then it fights).
     */
    @Nullable
    public Alarm.Task waitTaskFor(ServerLevel level, TermiteSoldierEntity soldier) {
        for (Breach breach : breaches) {
            if (breach.sealed && breach.shutOut.contains(soldier.getUUID())) {
                if (foeWithin(level, soldier.position(), numbers().quietReach())) {
                    return null;
                }
                return new Alarm.Task(null, breach.waitAt, breach.plug, false);
            }
        }
        return null;
    }

    /**
     * Whether an alarm's spot {@code cell} lies past a seal from this soldier (it is inside, the break outside the
     * seal): it does not try to go to it.
     */
    public boolean cutOff(TermiteSoldierEntity soldier, BlockPos cell) {
        for (Breach breach : breaches) {
            if (breach.sealed && !breach.shutOut.contains(soldier.getUUID())
                    && cell.distSqr(breach.at) <= cell.distSqr(breach.plug) && cell.distSqr(breach.at) <= numbers().drumReach() * numbers().drumReach()) {
                return true;
            }
        }
        return false;
    }

    /** Whether this soldier is shut out by a seal now. */
    public boolean isShutOut(UUID soldier) {
        return breaches.stream().anyMatch(breach -> breach.sealed && breach.shutOut.contains(soldier));
    }

    /** The seals standing now (tests and the list). */
    public List<BlockPos> seals() {
        return breaches.stream().filter(breach -> breach.sealed).map(breach -> breach.plug).toList();
    }

    /** The seal planned for a break near {@code where} (sealed or not), or null (tests). */
    @Nullable
    public BlockPos plannedSealNear(BlockPos where) {
        return breaches.stream().filter(breach -> breach.at.distSqr(where) <= INTO_TUNNEL * INTO_TUNNEL).map(breach -> breach.plug).findFirst().orElse(null);
    }

    /** Its words in the colony list (nothing while no break is looked after). */
    public String describe(ServerLevel level) {
        if (breaches.isEmpty()) {
            return "";
        }
        long sealed = breaches.stream().filter(breach -> breach.sealed).count();
        int out = breaches.stream().mapToInt(breach -> breach.shutOut.size()).sum();
        return " | raid breaks " + breaches.size() + ", sealed " + sealed + ", soldiers shut out " + out;
    }

    // --- saving ---------------------------------------------------------------------------------------------------

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Breach breach : breaches) {
            if (!breach.sealed) {
                continue; // a seal not packed yet: after a reload the raid has calmed
            }
            CompoundTag one = new CompoundTag();
            one.put("at", NbtUtils.writeBlockPos(breach.at));
            one.put("plug", NbtUtils.writeBlockPos(breach.plug));
            one.put("behind", NbtUtils.writeBlockPos(breach.behind));
            one.put("deeper", NbtUtils.writeBlockPos(breach.deeper));
            one.put("wait", NbtUtils.writeBlockPos(breach.waitAt));
            one.putLong("due", breach.due);
            one.putLong("lastFoe", breach.lastFoe);
            one.putBoolean("ours", breach.ours);
            list.add(one);
        }
        tag.put("raidSeals", list);
    }

    void load(CompoundTag tag) {
        breaches.clear();
        for (Tag t : tag.getList("raidSeals", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) t;
            BlockPos at = NbtUtils.readBlockPos(one, "at").orElse(null);
            BlockPos plug = NbtUtils.readBlockPos(one, "plug").orElse(null);
            BlockPos behind = NbtUtils.readBlockPos(one, "behind").orElse(plug);
            BlockPos deeper = NbtUtils.readBlockPos(one, "deeper").orElse(plug);
            BlockPos wait = NbtUtils.readBlockPos(one, "wait").orElse(plug);
            if (at == null || plug == null) {
                continue;
            }
            Breach breach = new Breach(at, plug, behind, deeper, wait, one.getLong("due"));
            breach.lastFoe = one.getLong("lastFoe");
            breach.sealed = true;
            breach.ours = one.getBoolean("ours");
            breaches.add(breach);
        }
    }

    /** Tests: a seal of every kind of field, so the save and load check sees them all. */
    void fillForTests(ServerLevel level) {
        BlockPos at = colony.getCenter().offset(12, -2, 0);
        Breach breach = new Breach(at, at.west(4), at.west(5), at.west(7), at.west(3), 100);
        breach.sealed = true;
        breach.ours = true;
        breach.lastFoe = 120;
        breaches.add(breach);
    }
}
