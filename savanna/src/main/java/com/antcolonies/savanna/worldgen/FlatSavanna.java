package com.antcolonies.savanna.worldgen;

import com.antcolonies.savanna.SavannaMod;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Flat savannas (the user, 2026-11-07; {@code docs/DESIGN.md}, "Flat savannas"): real savanna is flat to gently
 * rolling plain, Minecraft's is hill country. The land is shaped in data, two of Minecraft's own terrain numbers
 * overridden where the climate is the savanna's ({@code data/minecraft/worldgen/density_function/overworld/erosion.json}
 * and {@code ridges_folded.json}, the mask in {@code data/antcolonies/worldgen/density_function/.../savanna_flatness.json}).
 *
 * <p>This class is the one piece data cannot do: in a single-biome savanna world (the user's test worlds) the climate
 * says "savanna" only in patches, but every block of land is savanna, so the whole world must be flat. The density
 * function {@code antcolonies:whole_savanna} wraps the climate mask and answers 1 everywhere while such a world runs.
 */
@EventBusSubscriber(modid = SavannaMod.MODID)
public final class FlatSavanna {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, SavannaMod.MODID);

    static {
        DENSITY_FUNCTION_TYPES.register("whole_savanna", () -> WholeSavanna.CODEC.codec());
        DENSITY_FUNCTION_TYPES.register("shared", () -> Shared.CODEC.codec());
    }

    /** True while the running world is one biome, a savanna: the mask is 1 everywhere. */
    private static volatile boolean wholeWorld;

    private FlatSavanna() {
    }

    /** For the tests' made-up single-biome land (set back in a finally). */
    public static void setWholeWorld(boolean on) {
        wholeWorld = on;
    }

    public static boolean wholeWorld() {
        return wholeWorld;
    }

    /** Before any land is made: is the overworld one biome, a savanna? */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        wholeWorld = isSingleBiomeSavanna(event.getServer());
        if (wholeWorld) {
            SavannaMod.LOGGER.info("[flat savannas] single-biome savanna world: the whole world is flattened");
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        wholeWorld = false;
    }

    private static boolean isSingleBiomeSavanna(MinecraftServer server) {
        return server.registryAccess().registry(Registries.LEVEL_STEM)
                .map(stems -> stems.get(LevelStem.OVERWORLD))
                .map(stem -> stem.generator().getBiomeSource() instanceof FixedBiomeSource fixed
                        && fixed.possibleBiomes().stream().allMatch(b -> b.is(BiomeTags.IS_SAVANNA)))
                .orElse(false);
    }

    /**
     * {@code antcolonies:shared} (savanna loading, round 2, {@code docs/plans/savanna-loading-2.md}): its argument,
     * unchanged, worked out exactly as before. Only the wiring is quicker.
     *
     * <p>Why: before Minecraft works out a ground height for land not made yet (or a whole chunk), it wires up its land
     * recipe afresh ({@code NoiseChunk}'s {@code mapAll}). That walks the recipe as a tree: a part used in ten places is
     * rebuilt ten times, and every rebuilt part is looked up in a table (hashed) to find it was already there. The flat
     * savanna's parts (the savanna mask, its erosion, peaks and floodplain) sit inside Minecraft's land-height curves at
     * dozens of places, so since the flat savannas a ground height took about 7.5 ms (was 2.5), 4 parts in 5 of it wiring.
     * Wrapped in this, a part is wired once per wiring and the same wired part handed to every place that uses it: the
     * very object Minecraft's own table would have handed back, so the wired recipe, and the land, are the same.
     *
     * <p>Remembered per thread (land is made on several at once): the last wiring ({@code Visitor}) seen and, for it,
     * each part's answer. One memory per thread for every part ({@link #WIRING}), cleared when a new wiring starts, so
     * at most one wiring per thread is held. (Until 2026-11-16 each part kept its own per-thread memory; the wired copies
     * made their own too, and as a thread's memory of a copy held the copy's own wiring, none was ever let go: every
     * ground height worked out kept its whole wiring, about 5 KB, for as long as the thread lived, BUGS 222.)
     */
    public static final class Shared implements DensityFunction {
        static final KeyDispatchDataCodec<Shared> CODEC = KeyDispatchDataCodec.of(
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").xmap(Shared::new, Shared::argument));
        /**
         * For the speed tests only: true wires every use afresh, as Minecraft does (the same land, only slower); the flight
         * run's "before" ({@code tools/flight.sh ... old}, system property {@code antcolonies.landWiringOld}).
         */
        public static volatile boolean off = Boolean.getBoolean("antcolonies.landWiringOld");

        /** A thread's memory: the wiring it is in and each part's wired answer in it (by the part itself, not equality). */
        private static final class Wiring {
            Visitor visitor;
            final java.util.IdentityHashMap<Shared, DensityFunction> wired = new java.util.IdentityHashMap<>();
        }

        private static final ThreadLocal<Wiring> WIRING = ThreadLocal.withInitial(Wiring::new);

        private final DensityFunction argument;
        private final int hash;

        public Shared(DensityFunction argument) {
            this.argument = argument;
            this.hash = argument.hashCode() * 31 + 11;
        }

        public DensityFunction argument() {
            return argument;
        }

        @Override
        public double compute(FunctionContext context) {
            return argument.compute(context);
        }

        @Override
        public void fillArray(double[] array, ContextProvider provider) {
            argument.fillArray(array, provider);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            if (off) {
                return visitor.apply(new Shared(argument.mapAll(visitor)));
            }
            Wiring memory = WIRING.get();
            if (memory.visitor != visitor) {
                memory.visitor = visitor;
                memory.wired.clear();
            }
            DensityFunction known = memory.wired.get(this);
            if (known != null) {
                return known;
            }
            DensityFunction result = visitor.apply(new Shared(argument.mapAll(visitor)));
            if (memory.visitor != visitor) {
                // another wiring ran inside this one (and cleared the memory): start this one's memory again
                memory.visitor = visitor;
                memory.wired.clear();
            }
            memory.wired.put(this, result);
            return result;
        }

        @Override
        public double minValue() {
            return argument.minValue();
        }

        @Override
        public double maxValue() {
            return argument.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        /** Equal when they wrap equal parts, as Minecraft's own parts are (its wiring shares equal parts). */
        @Override
        public boolean equals(Object other) {
            return other == this || other instanceof Shared s && s.hash == hash && s.argument.equals(argument);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /** {@code antcolonies:whole_savanna}: its argument (the climate mask), or 1 in a single-biome savanna world. */
    public record WholeSavanna(DensityFunction argument) implements DensityFunction {
        static final KeyDispatchDataCodec<WholeSavanna> CODEC = KeyDispatchDataCodec.of(
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").xmap(WholeSavanna::new, WholeSavanna::argument));

        @Override
        public double compute(FunctionContext context) {
            return wholeWorld ? 1.0 : argument.compute(context);
        }

        @Override
        public void fillArray(double[] array, ContextProvider provider) {
            if (wholeWorld) {
                java.util.Arrays.fill(array, 1.0);
            } else {
                argument.fillArray(array, provider);
            }
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new WholeSavanna(argument.mapAll(visitor)));
        }

        @Override
        public double minValue() {
            return Math.min(argument.minValue(), 1.0);
        }

        @Override
        public double maxValue() {
            return Math.max(argument.maxValue(), 1.0);
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }
}
