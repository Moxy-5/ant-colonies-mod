package com.antcolonies.entity.load;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * One kind of load an insect can hold (Project Apex part 3, step T3, {@code docs/plans/apex-termites.md}): a row in the
 * list of loads the framework and the species mods sign up. A row says its name (also what the synced slot sends), how
 * it is written to the save and read back (each row keeps its own save words, so a load carried in an old world is
 * still held after the update), and what happens to it when its carrier dies. How it is drawn is the client's half of
 * the row ({@code client.LoadLooks}, signed up under the same row). The insect holds one load at a time in one synced
 * slot ({@link CarriedLoad}, {@code AbstractAntEntity.getLoad}).
 *
 * <p>The framework's own rows are in {@link FrameworkLoads}. A species with a new load (the Matabele ants' dead
 * termites, a hurt nestmate) signs up its own row in its own mod: {@code LoadKind.signUp(LoadKind.flag(...))}, plus its
 * look; nothing in the framework changes.
 */
public final class LoadKind {
    /** Writes the load into the insect's save (only called while it is held). */
    @FunctionalInterface
    public interface Write {
        void write(CompoundTag tag, CarriedLoad load, HolderLookup.Provider registries);
    }

    /** Reads this row's load back from a save; null if the save holds none of it. */
    @FunctionalInterface
    public interface Read {
        @Nullable
        CarriedLoad read(CompoundTag tag, HolderLookup.Provider registries);
    }

    /** What happens to the load when its carrier dies holding it (it lets go, so another can pick it up). */
    @FunctionalInterface
    public interface Drop {
        void drop(ServerLevel level, Colony colony, AbstractAntEntity carrier, CarriedLoad load);
    }

    /** Empty jaws: holds nothing, saves nothing, draws nothing. Not in the list. */
    public static final LoadKind NONE = new LoadKind(ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "none"),
            (tag, load, registries) -> { }, (tag, registries) -> null, (level, colony, carrier, load) -> { });

    private static final List<LoadKind> ALL = new ArrayList<>();
    private static final Map<ResourceLocation, LoadKind> BY_NAME = new HashMap<>();

    private final ResourceLocation name;
    private final Write write;
    private final Read read;
    private final Drop drop;

    public LoadKind(ResourceLocation name, Write write, Read read, Drop drop) {
        this.name = name;
        this.write = write;
        this.read = read;
        this.drop = drop;
    }

    /**
     * A load with nothing more to it than being held (an egg, a dead nestmate): saved as {@code saveWord: true}, as the
     * old flags were.
     */
    public static LoadKind flag(ResourceLocation name, String saveWord, Drop drop) {
        LoadKind[] self = {null};
        self[0] = new LoadKind(name, (tag, load, registries) -> tag.putBoolean(saveWord, true),
                (tag, registries) -> tag.getBoolean(saveWord) ? CarriedLoad.of(self[0]) : null, drop);
        return self[0];
    }

    /**
     * Adds a row to the list (while the mods load; species mods load side by side, so the list takes turns,
     * {@code docs/BUGS.md} 64). A name used twice stops the game loading.
     */
    public static synchronized LoadKind signUp(LoadKind kind) {
        if (BY_NAME.putIfAbsent(kind.name, kind) != null) {
            throw new IllegalStateException("Two carried loads named " + kind.name);
        }
        ALL.add(kind);
        return kind;
    }

    /** The row with this name (what the synced slot sends); {@link #NONE} if there is none. */
    public static synchronized LoadKind byName(ResourceLocation name) {
        return BY_NAME.getOrDefault(name, NONE);
    }

    /** Every row in the list, in the order signed up. */
    public static synchronized List<LoadKind> all() {
        return List.copyOf(ALL);
    }

    /** The load a save holds: the first row that finds its own save words; {@link CarriedLoad#NONE} if none does. */
    public static CarriedLoad readFrom(CompoundTag tag, HolderLookup.Provider registries) {
        for (LoadKind kind : all()) {
            CarriedLoad load = kind.read.read(tag, registries);
            if (load != null && !load.isEmpty()) {
                return load;
            }
        }
        return CarriedLoad.NONE;
    }

    public ResourceLocation name() {
        return name;
    }

    public void write(CompoundTag tag, CarriedLoad load, HolderLookup.Provider registries) {
        write.write(tag, load, registries);
    }

    public void drop(ServerLevel level, Colony colony, AbstractAntEntity carrier, CarriedLoad load) {
        drop.drop(level, colony, carrier, load);
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
