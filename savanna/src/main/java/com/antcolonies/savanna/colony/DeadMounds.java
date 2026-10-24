package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.DeadCombBlock;
import com.antcolonies.savanna.block.FungusCombBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The dead mound book (M8 step 1a, {@code docs/DESIGN.md}, "Dead mounds, details"): every termite mound whose colony
 * has died out, kept for good (the Matabele ants look for them as homes later, and they wear down in later pieces of
 * 1a). Written when a colony with a mound ends ({@code ColonyNews.COLONY_ENDED}, heard by the mound and the garden).
 *
 * <p>The garden: nothing weeds it any more, so Xylaria takes the comb ({@link DeadCombBlock}): furred the first day,
 * black with stalks from day 1 to 4, rotting from day 4, losing its layers, gone after {@link #GONE_DAYS} days (my
 * picks from the real "within days, gone in about a week"). The days count by the world's clock ({@code /time add}
 * moves them on); the comb only ever moves forward, so land away for a while catches up when it loads.
 */
public class DeadMounds extends SavedData {
    private static final String DATA_NAME = SavannaMod.MODID + "_dead_mounds";
    /** How often (in ticks) the book looks at the dead mounds in loaded land. */
    static final int LOOK_TICKS = 100;
    /** Days after the colony's end: furred until 1, black until 4, rotting until gone. */
    public static final double BLACK_DAYS = 1.0;
    public static final double ROTTEN_DAYS = 4.0;
    public static final double GONE_DAYS = 6.5;

    /** One comb of the dead garden: where, and how many layers it had when the colony died. */
    public record Comb(BlockPos pos, int layers) {
    }

    /** One dead mound: its colony (by id), the nest's entrance, when the colony died (world clock ticks), its comb left. */
    public static final class DeadMound {
        public final UUID colony;
        public final BlockPos center;
        public final long diedAt;
        final List<Comb> combs = new ArrayList<>();

        DeadMound(UUID colony, BlockPos center, long diedAt) {
            this.colony = colony;
            this.center = center;
            this.diedAt = diedAt;
        }

        public List<Comb> combs() {
            return List.copyOf(combs);
        }

        /** Whole and part days since the colony died, by the world's clock. */
        public double days(ServerLevel level) {
            return Math.max(0, level.getDayTime() - diedAt) / 24000.0;
        }
    }

    private final Map<UUID, DeadMound> mounds = new LinkedHashMap<>();

    public static DeadMounds get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(DeadMounds::new, DeadMounds::load, null), DATA_NAME);
    }

    public List<DeadMound> all() {
        return List.copyOf(mounds.values());
    }

    /** The colony with this id has died out at {@code center}: its dead mound (a new one, or the one already written). */
    public DeadMound died(ServerLevel level, UUID colony, BlockPos center) {
        DeadMound mound = mounds.computeIfAbsent(colony, id -> new DeadMound(id, center, level.getDayTime()));
        setDirty();
        return mound;
    }

    /** The dead colony's comb, as it was when it died: each turns furred at once (its nodules gone). */
    public void addCombs(ServerLevel level, UUID colony, BlockPos center, List<BlockPos> combs) {
        DeadMound mound = died(level, colony, center);
        for (BlockPos pos : combs) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FungusCombBlock) {
                mound.combs.add(new Comb(pos.immutable(), FungusCombBlock.layers(state)));
            }
        }
        update(level, mound);
    }

    /** Every few seconds: the dead gardens in loaded land move on. */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % LOOK_TICKS != 0) {
            return;
        }
        DeadMounds book = get(level);
        for (DeadMound mound : book.mounds.values()) {
            if (!mound.combs.isEmpty() && level.isLoaded(mound.center)) {
                book.update(level, mound);
            }
        }
    }

    /** The mound's comb brought up to its day: furred, black, rotting, gone; a comb broken or replaced is forgotten. */
    void update(ServerLevel level, DeadMound mound) {
        update(level, mound, mound.days(level));
    }

    /** The game tests: the mound's comb as it would be {@code days} after the colony died (the world's clock is shared). */
    public void updateForTests(ServerLevel level, DeadMound mound, double days) {
        update(level, mound, days);
    }

    /** The game tests: the dead mound is forgotten (the test's land is cleared after it). */
    public void forgetForTests(UUID colony) {
        if (mounds.remove(colony) != null) {
            setDirty();
        }
    }

    public DeadMound get(UUID colony) {
        return mounds.get(colony);
    }

    private void update(ServerLevel level, DeadMound mound, double days) {
        for (Iterator<Comb> it = mound.combs.iterator(); it.hasNext(); ) {
            Comb comb = it.next();
            if (!level.isLoaded(comb.pos())) {
                continue;
            }
            BlockState now = level.getBlockState(comb.pos());
            boolean living = now.getBlock() instanceof FungusCombBlock;
            if (!living && !now.is(SavannaBlocks.DEAD_COMB.get())) {
                it.remove(); // broken or built over: nothing left to rot
                setDirty();
                continue;
            }
            BlockState target = stateAt(days, comb.layers());
            if (target == null) {
                level.setBlock(comb.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                it.remove();
                setDirty();
            } else if (living || isLater(target, now)) {
                level.setBlock(comb.pos(), target, Block.UPDATE_ALL);
            }
        }
    }

    /** Whether {@code target} lies further along the comb's death than {@code now} (it never goes back). */
    static boolean isLater(BlockState target, BlockState now) {
        int stage = target.getValue(DeadCombBlock.STAGE), was = now.getValue(DeadCombBlock.STAGE);
        return stage > was || stage == was && target.getValue(DeadCombBlock.LAYERS) < now.getValue(DeadCombBlock.LAYERS);
    }

    /** The dead comb {@code days} after the colony died, from a comb of {@code layers}; null once it has rotted away. */
    public static BlockState stateAt(double days, int layers) {
        int[] stage = stage(days, layers);
        return stage == null ? null : SavannaBlocks.DEAD_COMB.get().defaultBlockState()
                .setValue(DeadCombBlock.STAGE, stage[0]).setValue(DeadCombBlock.LAYERS, stage[1]);
    }

    /** {stage, layers} {@code days} after the colony died, from a comb of {@code layers}; null once gone. */
    static int[] stage(double days, int layers) {
        layers = Math.max(1, Math.min(DeadCombBlock.MAX_LAYERS, layers));
        if (days < BLACK_DAYS) {
            return new int[] {DeadCombBlock.FURRED, layers};
        } else if (days < ROTTEN_DAYS) {
            return new int[] {DeadCombBlock.BLACK, layers};
        } else if (days < GONE_DAYS) {
            // Slumping as it rots: the layers go one by one through the rotting days.
            int left = (int) Math.ceil(layers * (GONE_DAYS - days) / (GONE_DAYS - ROTTEN_DAYS));
            return new int[] {DeadCombBlock.ROTTEN, Math.max(1, Math.min(layers, left))};
        }
        return null;
    }

    /** For the command: one line per dead mound. */
    public String describe(ServerLevel level) {
        if (mounds.isEmpty()) {
            return "No dead termite mounds.";
        }
        StringBuilder out = new StringBuilder("Dead termite mounds: " + mounds.size());
        for (DeadMound mound : mounds.values()) {
            out.append("\n  ").append(mound.center.toShortString()).append(": dead ")
                    .append(String.format(java.util.Locale.ROOT, "%.1f", mound.days(level))).append(" days, comb left ")
                    .append(mound.combs.size());
        }
        return out.toString();
    }

    // --- saving ---------------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (DeadMound mound : mounds.values()) {
            CompoundTag m = new CompoundTag();
            m.putUUID("colony", mound.colony);
            m.put("center", NbtUtils.writeBlockPos(mound.center));
            m.putLong("diedAt", mound.diedAt);
            ListTag combs = new ListTag();
            for (Comb comb : mound.combs) {
                CompoundTag c = new CompoundTag();
                c.putLong("pos", comb.pos().asLong());
                c.putInt("layers", comb.layers());
                combs.add(c);
            }
            m.put("combs", combs);
            list.add(m);
        }
        tag.put("mounds", list);
        return tag;
    }

    private static DeadMounds load(CompoundTag tag, HolderLookup.Provider registries) {
        DeadMounds book = new DeadMounds();
        for (Tag t : tag.getList("mounds", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) t;
            BlockPos center = NbtUtils.readBlockPos(m, "center").orElse(BlockPos.ZERO);
            DeadMound mound = new DeadMound(m.getUUID("colony"), center, m.getLong("diedAt"));
            for (Tag ct : m.getList("combs", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) ct;
                mound.combs.add(new Comb(BlockPos.of(c.getLong("pos")), c.getInt("layers")));
            }
            book.mounds.put(mound.colony, mound);
        }
        return book;
    }
}
