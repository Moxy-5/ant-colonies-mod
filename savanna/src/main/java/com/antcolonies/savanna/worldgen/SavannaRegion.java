package com.antcolonies.savanna.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.antcolonies.savanna.SavannaMod;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;

import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.Regions;

/**
 * Big savannas (the user, 2026-11-06; {@code docs/DESIGN.md}, "Big savannas in normal worlds with TerraBlender"): our
 * own TerraBlender region, a share of the world's large zones beside vanilla's (and Biomes O' Plenty's or any other
 * mod's). Inside it the biome table is Minecraft's own with the savanna's climate widened: the warm band is wider
 * (eating into the temperate band and the desert's), and inside the warm band the dry part, where savanna grows, takes
 * more of the humidity (eating into forest and jungle). Everything else is vanilla's own table point for point: hills,
 * mountains, oceans, rivers, beaches, caves, the windswept savanna on shattered hills and the savanna plateau all keep
 * their own rules, only their climates move, so the savannas grow as big as the climate patches allow
 * ({@link #SHAPE}) where our zones lie ({@link #WEIGHT}).
 *
 * <p>Real savanna (the ant nerd's half): savanna is the warm, seasonally dry belt between desert and forest; in Africa
 * it is the largest biome, covering about half the continent, in stretches thousands of kilometres across. On the
 * mod's scale of a block a metre that would be far too big for a game; the user's target is about 2,000 blocks across
 * for a typical one, 4,000 the biggest, about as easy to find as a desert.
 */
public final class SavannaRegion extends Region {
    public static final ResourceLocation NAME = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "big_savannas");

    /**
     * Our region's own weight; Minecraft's zones' weight (10, TerraBlender's default) is added at sign-up
     * ({@link #signUp()}), so with no other biome mod every zone is ours and the climate alone sets where a savanna ends
     * (one recipe everywhere, 2026-11-16: a zone edge used to cut big savannas into small scraps). Biomes O' Plenty adds
     * its own regions of about 10 each, where savannas are still cut. TerraBlender's zone size is one setting for every
     * mod ({@code config/terrablender.toml}, {@code overworld_region_size}, default 3, about 512 blocks a zone); it is
     * left to the player.
     */
    public static final int WEIGHT = 30;

    /**
     * How the climate table is stretched. Minecraft's warm band is temperature 0.2 to 0.55 and its savanna the driest two
     * fifths of it (humidity under -0.1). {@code warmFrom, warmTo}: where the warm band now runs; {@code dryTo}: where its
     * dry part (savanna) now ends.
     */
    public record Shape(float warmFrom, float warmTo, float dryTo) {
        public static final Shape VANILLA = new Shape(0.2F, 0.55F, -0.1F);
    }

    /**
     * The shape in the game, my pick from the biome maps (2026-11-06, {@code SavannaWorldTests.stressSavannaShapes}, 4
     * seeds, 16,384 blocks square each): the warm band from 0.05 to 0.6 (was 0.2 to 0.55) and savanna its driest 65
     * percent (was 45). With {@link #WEIGHT} 30 a typical savanna comes out about 2,000 blocks across and the biggest
     * about 4,000, the user's target; with Biomes O' Plenty's regions beside ours (our share about half) about 1,200 to
     * 1,500 across, still about twice Minecraft's own (about 700).
     */
    public static final Shape SHAPE = new Shape(0.05F, 0.6F, 0.3F);

    /**
     * The savanna rivers (2026-11-07, {@code docs/plans/savanna-rivers.md}): in the savanna's climate Minecraft's river
     * becomes our murky {@code antcolonies:savanna_river}, and the savanna's band along it our green
     * {@code antcolonies:savanna_floodplain}. The floodplain follows the same "peaks and valleys" band the land is
     * flattened by ({@code tools/flat_savannas/flat_offset.py}, flat from -0.80 to -0.55): weirdness within 0.15 of 0
     * (the river biome itself is within 0.05).
     */
    public static final ResourceKey<Biome> SAVANNA_RIVER = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_river"));
    public static final ResourceKey<Biome> SAVANNA_FLOODPLAIN = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_floodplain"));
    public static final float FLOODPLAIN_WEIRDNESS = 0.15F;

    /**
     * Rivers widen toward the sea (2026-11-16, {@code docs/plans/big-savannas-big-rivers.md}, step 4): the land's river
     * gets wider the nearer the sea ({@code flat_offset.py ... wide}, blended by continentalness), and the river and
     * floodplain biomes follow it in slices of continentalness: {@link #INLAND_EDGES} cut the land from the sea to far
     * inland, and in each slice the savanna within {@link #RIVER_HALF} of the valley's line (in weirdness) is the savanna
     * river, out to {@link #FLOOD_HALF} the floodplain. Far inland and in the sea they are Minecraft's own river (0.05) and
     * the old floodplain (0.15). The land changes smoothly and the biome in steps, so near a step a little water lies in
     * floodplain biome or a little bank in river biome (my picks, from the land's shapes: the water's edge is at about
     * weirdness (1 + pv) / 3).
     */
    static final float[] INLAND_EDGES = {-0.19F, -0.11F, 0.03F, 0.12F, 0.21F, 0.30F};
    static final float[] RIVER_HALF = {0.05F, 0.15F, 0.17F, 0.125F, 0.10F, 0.07F, 0.05F};
    static final float[] FLOOD_HALF = {0.15F, 0.24F, 0.26F, 0.22F, 0.19F, 0.165F, 0.15F};

    /** Minecraft's own band edges (OverworldBiomeBuilder's temperatures and humidities). */
    private static final float[] TEMPERATURE_EDGES = {-1F, -0.45F, -0.15F, 0.2F, 0.55F, 1F};
    private static final float[] HUMIDITY_EDGES = {-1F, -0.35F, -0.1F, 0.1F, 0.3F, 1F};

    public SavannaRegion(int weight) {
        super(NAME, RegionType.OVERWORLD, weight);
    }

    /**
     * Signs our region up with TerraBlender (at common set-up): Minecraft's own zones are emptied ({@link MinecraftZones})
     * and their weight added to ours, so with no other biome mod every zone is ours and no zone edge cuts a savanna into
     * small scraps (one recipe everywhere, the user's OK 2026-11-16). Beside another biome mod our share is 40 against its
     * regions' weights.
     */
    public static void signUp() {
        Regions.register(new SavannaRegion(WEIGHT + MinecraftZones.replace()));
        List<String> problems = MinecraftZones.indexProblems();
        if (problems.isEmpty()) {
            SavannaMod.LOGGER.info("[biome regions] indices right: {}", MinecraftZones.describe());
        } else {
            SavannaMod.LOGGER.error("[biome regions] indices wrong: {} ({})", problems, MinecraftZones.describe());
        }
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        table(SHAPE).forEach(mapper);
    }

    /** Minecraft's overworld table with the savanna's climate stretched by a shape. */
    public static List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> table(Shape shape) {
        List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> vanilla = new ArrayList<>();
        new OverworldBiomeBuilder().addBiomes(vanilla::add); // (opened by accesstransformer.cfg)
        float[] newTemperature = TEMPERATURE_EDGES.clone();
        newTemperature[3] = shape.warmFrom();
        newTemperature[4] = shape.warmTo();
        float[] newHumidity = HUMIDITY_EDGES.clone();
        newHumidity[2] = shape.dryTo();
        // the levels below and above the dry edge keep their shares of what is left on their side
        newHumidity[1] = -1F + (HUMIDITY_EDGES[1] + 1F) / (HUMIDITY_EDGES[2] + 1F) * (shape.dryTo() + 1F);
        for (int i = 3; i <= 4; i++) {
            newHumidity[i] = shape.dryTo() + (HUMIDITY_EDGES[i] - HUMIDITY_EDGES[2]) / (1F - HUMIDITY_EDGES[2]) * (1F - shape.dryTo());
        }
        long warmLow = Climate.quantizeCoord(TEMPERATURE_EDGES[3]);
        long warmHigh = Climate.quantizeCoord(TEMPERATURE_EDGES[4]);
        List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out = new ArrayList<>(vanilla.size() + vanilla.size() / 4);
        for (Pair<Climate.ParameterPoint, ResourceKey<Biome>> pair : vanilla) {
            Climate.ParameterPoint p = pair.getFirst();
            long lo = p.temperature().min();
            long hi = p.temperature().max();
            // cut the point's temperature span at the warm band's edges, so the humidity is stretched only inside it
            long[] cuts = {lo, Math.max(lo, Math.min(hi, warmLow)), Math.max(lo, Math.min(hi, warmHigh)), hi};
            for (int piece = 0; piece < 3; piece++) {
                long a = cuts[piece], b = cuts[piece + 1];
                if (b <= a && !(lo == hi && piece == pieceOf(lo, warmLow, warmHigh))) {
                    continue;
                }
                Climate.Parameter temperature = new Climate.Parameter(stretch(a, TEMPERATURE_EDGES, newTemperature),
                        stretch(b, TEMPERATURE_EDGES, newTemperature));
                Climate.Parameter humidity = piece == 1
                        ? new Climate.Parameter(stretch(p.humidity().min(), HUMIDITY_EDGES, newHumidity), stretch(p.humidity().max(), HUMIDITY_EDGES, newHumidity))
                        : p.humidity();
                if (piece == 1) {
                    addRiversAndFloodplains(out, temperature, humidity, p, pair.getSecond(), shape.dryTo());
                } else {
                    out.add(Pair.of(new Climate.ParameterPoint(temperature, humidity, p.continentalness(), p.erosion(), p.depth(), p.weirdness(),
                            p.offset()), pair.getSecond()));
                }
            }
        }
        return out;
    }

    /**
     * One point of the warm band: a river in the savanna's dry part becomes the savanna river, and a savanna's slice within
     * {@link #FLOODPLAIN_WEIRDNESS} of the valley the floodplain; everything else as it was.
     */
    private static void addRiversAndFloodplains(List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out, Climate.Parameter temperature,
            Climate.Parameter humidity, Climate.ParameterPoint p, ResourceKey<Biome> biome, float dryTo) {
        long dry = Climate.quantizeCoord(dryTo);
        if (biome == Biomes.RIVER && humidity.min() < dry) {
            out.add(point(temperature, new Climate.Parameter(humidity.min(), Math.min(dry, humidity.max())), p, p.weirdness(), SAVANNA_RIVER));
            if (humidity.max() > dry) {
                out.add(point(temperature, new Climate.Parameter(dry, humidity.max()), p, p.weirdness(), Biomes.RIVER));
            }
            return;
        }
        if (biome == Biomes.SAVANNA) {
            // by how far inland: the savanna nearest the valley's line is river, then floodplain, then savanna
            long ca = p.continentalness().min(), cb = p.continentalness().max();
            for (int slice = 0; slice <= INLAND_EDGES.length; slice++) {
                long from = slice == 0 ? ca : Math.max(ca, Math.min(cb, Climate.quantizeCoord(INLAND_EDGES[slice - 1])));
                long to = slice == INLAND_EDGES.length ? cb : Math.max(ca, Math.min(cb, Climate.quantizeCoord(INLAND_EDGES[slice])));
                if (to <= from && !(ca == cb && slice == sliceOf(ca))) {
                    continue;
                }
                Climate.Parameter inland = new Climate.Parameter(from, to);
                long river = Climate.quantizeCoord(RIVER_HALF[slice]), flood = Climate.quantizeCoord(FLOOD_HALF[slice]);
                long a = p.weirdness().min(), b = p.weirdness().max();
                long[] edges = {-flood, -river, river, flood};
                long[] cuts = new long[6];
                cuts[0] = a;
                cuts[5] = b;
                for (int k = 0; k < 4; k++) {
                    cuts[k + 1] = Math.max(a, Math.min(b, edges[k]));
                }
                for (int k = 0; k < 5; k++) {
                    if (cuts[k + 1] > cuts[k] || (a == b && k == pieceOfWeirdness(a, edges))) {
                        ResourceKey<Biome> here = k == 2 ? SAVANNA_RIVER : k == 1 || k == 3 ? SAVANNA_FLOODPLAIN : Biomes.SAVANNA;
                        out.add(Pair.of(new Climate.ParameterPoint(temperature, humidity, inland, p.erosion(), p.depth(),
                                new Climate.Parameter(cuts[k], cuts[k + 1]), p.offset()), here));
                    }
                }
            }
            return;
        }
        out.add(point(temperature, humidity, p, p.weirdness(), biome));
    }

    private static Pair<Climate.ParameterPoint, ResourceKey<Biome>> point(Climate.Parameter temperature, Climate.Parameter humidity,
            Climate.ParameterPoint p, Climate.Parameter weirdness, ResourceKey<Biome> biome) {
        return Pair.of(new Climate.ParameterPoint(temperature, humidity, p.continentalness(), p.erosion(), p.depth(), weirdness, p.offset()), biome);
    }

    private static int sliceOf(long continentalness) {
        int slice = 0;
        while (slice < INLAND_EDGES.length && continentalness >= Climate.quantizeCoord(INLAND_EDGES[slice])) {
            slice++;
        }
        return slice;
    }

    private static int pieceOfWeirdness(long value, long[] edges) {
        int piece = 0;
        while (piece < edges.length && value >= edges[piece]) {
            piece++;
        }
        return piece;
    }

    private static int pieceOf(long value, long warmLow, long warmHigh) {
        return value < warmLow ? 0 : value < warmHigh ? 1 : 2;
    }

    /** A climate value moved by the piecewise straight map from the old edges to the new ones. */
    static long stretch(long quantized, float[] from, float[] to) {
        float v = Climate.unquantizeCoord(quantized);
        if (v <= from[0]) {
            return quantized; // the ends (and anything past them, like Minecraft's -2 to 2 "full range") stay
        }
        for (int i = 1; i < from.length; i++) {
            if (v <= from[i]) {
                float t = (v - from[i - 1]) / (from[i] - from[i - 1]);
                return Climate.quantizeCoord(to[i - 1] + t * (to[i] - to[i - 1]));
            }
        }
        return quantized;
    }
}
