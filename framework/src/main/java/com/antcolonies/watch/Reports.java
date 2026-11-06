package com.antcolonies.watch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.command.ColonyCommands;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * The raid test bench, B2 (the black box, {@code docs/plans/raid-test-bench.md}): a report folder written when a rule
 * breaks ({@link Watch}) or a player types {@code /antcolonies report}. In {@code reports/} next to the world's game
 * folder ({@code run/reports/} for {@code runClient}), one folder each, the newest {@link #KEEP} kept:
 * <ul>
 * <li>{@code report.txt}: what broke (or the player's words), when, the colonies near, every insect within 24 blocks
 * and the rules broken lately;</li>
 * <li>{@code diary.txt}: the last 3 minutes of the black boxes of the colonies near, merged in time order;</li>
 * <li>{@code colonies.nbt}: those colonies' own saves;</li>
 * <li>{@code place.nbt}: the blocks and insects round the spot as a structure (up to 96 x 48 x 96; smaller, or none,
 * where the land is not loaded), for B3's replay;</li>
 * <li>{@code screenshots/screenshot.png}: what the player saw (only for a player's report in single player).</li>
 * </ul>
 * The world is read on the server thread; the files are written in the background, so the game does not stop for them.
 */
public final class Reports {
    private static final Logger LOG = LoggerFactory.getLogger("antcolonies/rules");
    /** Report folders kept (the oldest deleted by themselves). */
    static final int KEEP = 50;
    /** Colonies this near the spot go in the report (blocks): a raid's own colony and the termites it raids. */
    static final double COLONIES_NEAR = 112;
    /** Insects this near the spot are told one by one in {@code report.txt}. */
    static final double INSECTS_NEAR = 24;
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private Reports() {
    }

    /** The reports folder of this game. */
    public static Path folder(ServerLevel level) {
        return level.getServer().getServerDirectory().resolve("reports");
    }

    /**
     * Writes a report about the place round {@code where}; returns its folder's name ({@code reports/<name>}), or null
     * if it could not be made. {@code title} names the folder (a rule's name, or "player"); {@code why} heads the text.
     */
    @Nullable
    public static String save(ServerLevel level, BlockPos where, String title, String why, @Nullable ServerPlayer player) {
        try {
            return write(level, where, title, why, player);
        } catch (RuntimeException e) {
            LOG.error("[rule] could not save a report at {}", where.toShortString(), e);
            return null;
        }
    }

    private static String write(ServerLevel level, BlockPos where, String title, String why, @Nullable ServerPlayer player) {
        String name = LocalDateTime.now().format(NAME) + "-" + title.replaceAll("[^A-Za-z0-9_-]", "-");
        Path root = folder(level);
        Path dir = root.resolve(name);
        for (int n = 2; Files.exists(dir); n++) {
            dir = root.resolve(name + "-" + n);
        }
        String folderName = dir.getFileName().toString();
        long now = level.getGameTime();
        // The picture first, before the place is copied (that takes a moment, and the scene moves on): the user's report.
        Runnable shot = null;
        if (player != null && FMLEnvironment.dist.isClient() && level.getServer().isSingleplayer()) {
            shot = com.antcolonies.client.ReportShot.take(dir);
        }
        Runnable finalShot = shot;

        List<Colony> near = new ArrayList<>();
        for (Colony colony : ColonyManager.get(level).getColonies()) {
            if (colony.getCenter().distSqr(where) <= COLONIES_NEAR * COLONIES_NEAR) {
                near.add(colony);
            }
        }
        near.sort(Comparator.comparingDouble(colony -> colony.getCenter().distSqr(where)));

        // report.txt
        StringBuilder text = new StringBuilder();
        text.append("Report ").append(folderName).append('\n');
        text.append(why).append('\n');
        text.append("Where: ").append(where.toShortString()).append(" in ").append(level.dimension().location()).append('\n');
        text.append("When: game tick ").append(now).append(", day ").append(level.getDayTime() / 24000 + 1).append(", ")
                .append(SpeciesProfile.DayClock.clock(level.getDayTime())).append(level.isRaining() ? ", raining" : "").append('\n');
        if (player != null) {
            text.append("Typed by ").append(player.getGameProfile().getName()).append(" standing at ")
                    .append(player.blockPosition().toShortString()).append('\n');
        }
        text.append("Rules on: ").append(Watch.mode()).append('\n');
        text.append("\nColonies within ").append((int) COLONIES_NEAR).append(" blocks (nearest first):\n");
        for (Colony colony : near) {
            try {
                text.append(ColonyCommands.describe(colony, level)).append('\n');
            } catch (RuntimeException e) {
                text.append("- ").append(colony.getId()).append(" (could not describe: ").append(e).append(")\n");
            }
        }
        text.append("\nRules broken lately by these colonies:\n");
        int told = 0;
        for (Colony colony : near) {
            for (Watch.Breach breach : Watch.breaches(colony)) {
                if (now - breach.tick() <= BlackBox.KEEP * 2) {
                    text.append("- tick ").append(breach.tick()).append(" (").append((now - breach.tick()) / 20).append(" s ago) ")
                            .append(breach.rule()).append(" at ").append(breach.where().toShortString()).append(": ").append(breach.text())
                            .append('\n');
                    told++;
                }
            }
        }
        if (told == 0) {
            text.append("- none\n");
        }
        text.append("\nInsects within ").append((int) INSECTS_NEAR).append(" blocks (nearest first):\n");
        List<AbstractAntEntity> insects = level.getEntitiesOfClass(AbstractAntEntity.class, new AABB(where).inflate(INSECTS_NEAR));
        insects.sort(Comparator.comparingDouble(insect -> insect.distanceToSqr(where.getCenter())));
        for (AbstractAntEntity insect : insects) {
            Colony colony = insect.getColony();
            text.append("- ").append(InsectStories.line(level, insect, Watch.stillFor(insect)))
                    .append(colony == null ? " [no colony]" : " [colony " + colony.getId().toString().substring(0, 8) + "]").append('\n');
        }
        if (insects.isEmpty()) {
            text.append("- none\n");
        }

        // diary.txt: the black boxes merged.
        record Dated(long tick, String colony, String text) {
        }
        List<Dated> diary = new ArrayList<>();
        for (Colony colony : near) {
            String id = colony.getId().toString().substring(0, 8);
            for (BlackBox.Line line : Watch.boxOf(colony).lines()) {
                diary.add(new Dated(line.tick(), id, line.text()));
            }
        }
        diary.sort(Comparator.comparingLong(Dated::tick));
        StringBuilder diaryText = new StringBuilder();
        diaryText.append("The last ").append(BlackBox.KEEP / 20).append(" s of the colonies near ").append(where.toShortString())
                .append(" (tick, seconds before the report, colony, line)\n");
        for (Dated line : diary) {
            diaryText.append(line.tick()).append(String.format(" (-%3d s) ", (now - line.tick()) / 20)).append(line.colony()).append(' ')
                    .append(line.text()).append('\n');
        }

        // colonies.nbt
        CompoundTag colonies = new CompoundTag();
        for (Colony colony : near) {
            try {
                colonies.put(colony.getId().toString(), colony.save());
            } catch (RuntimeException e) {
                LOG.warn("[rule] report: could not save the colony {}", colony.getId(), e);
            }
        }

        // place.nbt
        CompoundTag place = place(level, where, near);
        if (place == null) {
            text.append("\n(The place was not saved: the land round it is not loaded.)\n");
        } else {
            // Round 2 (B3, the replay): what the replay needs to put the moment back (Replay).
            place.putLong(Replay.GAME_TIME, now);
            place.putLong(Replay.DAY_TIME, level.getDayTime());
            place.putBoolean(Replay.RAINING, level.isRaining());
            place.putIntArray(Replay.SPOT, new int[] {where.getX(), where.getY(), where.getZ()});
            level.getBiome(where).unwrapKey().ifPresent(biome -> place.putString(Replay.BIOME, biome.location().toString()));
            place.putString(Replay.DIMENSION, level.dimension().location().toString());
            text.append("\nThe place: ").append(place.getString("ReportCorner")).append(" (corner), size ")
                    .append(place.getString("ReportSize")).append(", in place.nbt.\n");
        }

        Path finalDir = dir;
        String reportText = text.toString();
        String diaryOut = diaryText.toString();
        Util.ioPool().execute(() -> {
            try {
                Files.createDirectories(finalDir);
                Files.writeString(finalDir.resolve("report.txt"), reportText, StandardCharsets.UTF_8);
                Files.writeString(finalDir.resolve("diary.txt"), diaryOut, StandardCharsets.UTF_8);
                NbtIo.writeCompressed(colonies, finalDir.resolve("colonies.nbt"));
                if (place != null) {
                    NbtIo.writeCompressed(place, finalDir.resolve("place.nbt"));
                }
                if (finalShot != null) {
                    finalShot.run();
                }
                prune(root);
            } catch (IOException e) {
                LOG.error("[rule] could not write the report {}", finalDir, e);
            }
        });
        LOG.info("[rule] report saved: reports/{} ({})", folderName, why);
        return "reports/" + folderName;
    }

    /** The blocks and insects round the spot as a structure, the biggest box whose land is all loaded (or null). */
    @Nullable
    private static CompoundTag place(ServerLevel level, BlockPos where, List<Colony> near) {
        int[][] sizes = {{96, 48}, {48, 32}};
        for (int[] size : sizes) {
            int wide = size[0];
            int high = size[1];
            // Round 2 (B3, found by the first replay): the box takes in the nests of the nearest colonies too, as far as
            // they fit with 12 blocks to spare, so a replay has the nests their insects live from (a box round the spot
            // alone cut a termite colony's galleries off from its nest, and its workers came out walled in).
            int minX = where.getX();
            int maxX = minX;
            int minZ = where.getZ();
            int maxZ = minZ;
            for (Colony colony : near) {
                BlockPos c = colony.getCenter();
                int nMinX = Math.min(minX, c.getX());
                int nMaxX = Math.max(maxX, c.getX());
                int nMinZ = Math.min(minZ, c.getZ());
                int nMaxZ = Math.max(maxZ, c.getZ());
                if (nMaxX - nMinX <= wide - 24 && nMaxZ - nMinZ <= wide - 24) {
                    minX = nMinX;
                    maxX = nMaxX;
                    minZ = nMinZ;
                    maxZ = nMaxZ;
                }
            }
            int bottom = Math.max(level.getMinBuildHeight(), where.getY() - high / 2);
            int top = Math.min(level.getMaxBuildHeight(), bottom + high);
            BlockPos corner = new BlockPos((minX + maxX) / 2 - wide / 2, bottom, (minZ + maxZ) / 2 - wide / 2);
            if (!loaded(level, corner, wide)) {
                continue;
            }
            StructureTemplate template = new StructureTemplate();
            Vec3i box = new Vec3i(wide, top - bottom, wide);
            template.fillFromWorld(level, corner, box, true, null);
            CompoundTag tag = template.save(new CompoundTag());
            tag.putString("ReportCorner", corner.toShortString());
            tag.putString("ReportSize", box.getX() + " x " + box.getY() + " x " + box.getZ());
            tag.putIntArray("ReportCornerXYZ", new int[] {corner.getX(), corner.getY(), corner.getZ()});
            return tag;
        }
        return null;
    }

    private static boolean loaded(ServerLevel level, BlockPos corner, int wide) {
        for (int cx = corner.getX() >> 4; cx <= (corner.getX() + wide - 1) >> 4; cx++) {
            for (int cz = corner.getZ() >> 4; cz <= (corner.getZ() + wide - 1) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Only the newest {@link #KEEP} report folders are kept. */
    private static void prune(Path root) throws IOException {
        List<Path> folders;
        try (Stream<Path> all = Files.list(root)) {
            folders = all.filter(Files::isDirectory).sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        for (int i = 0; i < folders.size() - KEEP; i++) {
            try (Stream<Path> inside = Files.walk(folders.get(i))) {
                for (Path path : inside.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
