package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.watch.Soak;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The raid test bench, round 2, B4: the savanna's part of the soak run ({@link Soak}). It watches Matabele colonies with
 * a living termite colony in reach (and those termite colonies), counts the raids and how far each got, and pokes at
 * them as a player might: a raid sent at an odd hour, a block put on a raid's scent trail or the ground under it dug
 * away, a hole dug into a nest, a raider killed.
 */
public final class RaidSoak implements Soak.Part {
    /** A living termite colony within this many blocks of a Matabele nest makes it worth watching (the new-land rule). */
    static final double TERMITES_NEAR = 70;

    private final Map<RaidColumn, RaidColumn.Stage> open = new IdentityHashMap<>();
    private CompoundTag counts = new CompoundTag();

    @Override
    public TagKey<Biome> land() {
        return BiomeTags.IS_SAVANNA;
    }

    @Override
    public List<Colony> subjects(ServerLevel level, BlockPos site, int radius) {
        List<Colony> ants = new ArrayList<>();
        for (Colony colony : ColonyManager.get(level).getColonies()) {
            if (SavannaSpecies.MATABELE.equals(colony.getSpecies()) && sideways(colony.getCenter(), site) <= radius - 48
                    && !Raids.termiteColonies(level, colony.getCenter(), TERMITES_NEAR).isEmpty()) {
                ants.add(colony);
            }
        }
        ants.sort((a, b) -> Double.compare(sideways(a.getCenter(), site), sideways(b.getCenter(), site)));
        List<Colony> picked = new ArrayList<>();
        for (Colony ant : ants.subList(0, Math.min(Soak.MAX_SUBJECTS, ants.size()))) {
            picked.add(ant);
            for (Colony termites : Raids.termiteColonies(level, ant.getCenter(), TERMITES_NEAR)) {
                if (!picked.contains(termites)) {
                    picked.add(termites);
                }
            }
        }
        return picked;
    }

    @Override
    public Map<String, Soak.Poke> pokes() {
        Map<String, Soak.Poke> pokes = new LinkedHashMap<>();
        pokes.put("raid", (level, watched, random) -> {
            for (Colony colony : shuffled(ants(watched), random)) {
                Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
                if (raids == null || raids.column() != null) {
                    continue;
                }
                MatabeleEntity scout = raids.sendNow(level, true);
                if (scout != null) {
                    return "a scout sent out at once (any hour) from " + name(colony);
                }
            }
            return null;
        });
        pokes.put("trail-block", (level, watched, random) -> trailPoke(level, watched, random, true));
        pokes.put("trail-dig", (level, watched, random) -> trailPoke(level, watched, random, false));
        pokes.put("dig-nest", (level, watched, random) -> {
            if (watched.isEmpty()) {
                return null;
            }
            Colony colony = watched.get(random.nextInt(watched.size()));
            BlockPos center = colony.getCenter();
            for (int tries = 0; tries < 20; tries++) {
                BlockPos at = center.offset(random.nextInt(7) - 3, random.nextInt(5) - 3, random.nextInt(7) - 3);
                BlockState state = level.getBlockState(at);
                if (!state.isAir() && state.getDestroySpeed(level, at) >= 0 && state.getFluidState().isEmpty()) {
                    level.destroyBlock(at, false);
                    Raids.blockChangedNear(level, at);
                    return "dug out " + state.getBlock().getName().getString() + " at " + at.toShortString() + " in " + name(colony);
                }
            }
            return null;
        });
        pokes.put("kill-raider", (level, watched, random) -> {
            for (Colony colony : shuffled(ants(watched), random)) {
                RaidColumn column = colony.part(SavannaParts.RAIDS).map(Raids::column).orElse(null);
                if (column == null || column.members().isEmpty()) {
                    continue;
                }
                List<java.util.UUID> members = column.members();
                Entity ant = level.getEntity(members.get(random.nextInt(members.size())));
                if (ant instanceof LivingEntity living && living.isAlive()) {
                    living.kill();
                    return "killed a raider of " + name(colony) + " at " + ant.blockPosition().toShortString() + " (" + column.stage() + ")";
                }
            }
            return null;
        });
        return pokes;
    }

    /** A block put on a raid's scent trail ({@code block}), or the ground under it dug away. */
    private static String trailPoke(ServerLevel level, List<Colony> watched, RandomSource random, boolean block) {
        for (Colony colony : shuffled(ants(watched), random)) {
            Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
            if (raids == null) {
                continue;
            }
            RaidColumn column = raids.column();
            ScentTrails.Trail trail = column != null ? column.trail() : null;
            if (trail == null) {
                List<ScentTrails.Trail> all = raids.trails().all();
                trail = all.isEmpty() ? null : all.get(random.nextInt(all.size()));
            }
            if (trail == null || trail.points().size() < 6) {
                continue;
            }
            List<BlockPos> points = trail.points();
            // Not at either end: the nest and the termites' own ground are other pokes.
            BlockPos at = points.get(2 + random.nextInt(points.size() - 4));
            if (block) {
                if (!level.getBlockState(at).canBeReplaced()) {
                    continue;
                }
                level.setBlockAndUpdate(at, Blocks.DIRT.defaultBlockState());
            } else {
                BlockPos below = at.below();
                BlockState state = level.getBlockState(below);
                if (state.isAir() || state.getDestroySpeed(level, below) < 0) {
                    continue;
                }
                level.destroyBlock(below, false);
                at = below;
            }
            Raids.blockChangedNear(level, at);
            return (block ? "dirt put on " : "dug the ground under ") + "a scent trail of " + name(colony) + " at " + at.toShortString()
                    + (column != null ? " (a raid " + column.stage() + ")" : " (no raid out)");
        }
        return null;
    }

    /** Raids sent at odd hours come three times as often as each other poke (so a soak day sees raids; my pick). */
    @Override
    public int weight(String poke) {
        return "raid".equals(poke) ? 3 : 1;
    }

    @Override
    public boolean busy(ServerLevel level, List<Colony> watched) {
        for (Colony colony : ants(watched)) {
            RaidColumn column = colony.part(SavannaParts.RAIDS).map(Raids::column).orElse(null);
            if (column != null && column.stage() != RaidColumn.Stage.DONE && column.stage() != RaidColumn.Stage.FORMING) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick(ServerLevel level, List<Colony> watched) {
        List<RaidColumn> now = new ArrayList<>();
        for (Colony colony : ants(watched)) {
            RaidColumn column = colony.part(SavannaParts.RAIDS).map(Raids::column).orElse(null);
            if (column == null) {
                continue;
            }
            now.add(column);
            RaidColumn.Stage before = open.get(column);
            if (before == null) {
                add("raids", 1);
                open.put(column, column.stage());
            } else if (column.stage().ordinal() > before.ordinal()) {
                open.put(column, column.stage());
            }
        }
        // A raid gone: how far it got.
        open.entrySet().removeIf(entry -> {
            if (now.contains(entry.getKey())) {
                return false;
            }
            add("reached " + entry.getValue().name(), 1);
            return true;
        });
    }

    private void add(String key, int n) {
        counts.putInt(key, counts.getInt(key) + n);
    }

    @Override
    public List<String> tally() {
        List<String> rows = new ArrayList<>();
        rows.add("Raids started: " + counts.getInt("raids") + (open.isEmpty() ? "" : " (" + open.size() + " out now)"));
        StringBuilder reached = new StringBuilder("Raids ended, by the farthest stage reached:");
        for (RaidColumn.Stage stage : RaidColumn.Stage.values()) {
            int n = counts.getInt("reached " + stage.name());
            if (n > 0) {
                reached.append(' ').append(stage.name()).append(' ').append(n).append(',');
            }
        }
        rows.add(reached.toString().replaceAll(",$", ""));
        return rows;
    }

    @Override
    public CompoundTag save() {
        return counts.copy();
    }

    @Override
    public void load(CompoundTag tag) {
        counts = tag.copy();
        open.clear();
    }

    private static List<Colony> ants(List<Colony> watched) {
        return watched.stream().filter(colony -> SavannaSpecies.MATABELE.equals(colony.getSpecies())).toList();
    }

    private static List<Colony> shuffled(List<Colony> colonies, RandomSource random) {
        List<Colony> out = new ArrayList<>(colonies);
        for (int i = out.size() - 1; i > 0; i--) {
            java.util.Collections.swap(out, i, random.nextInt(i + 1));
        }
        return out;
    }

    private static String name(Colony colony) {
        return "the colony " + colony.getId().toString().substring(0, 8);
    }

    private static double sideways(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
