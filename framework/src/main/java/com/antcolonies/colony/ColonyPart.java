package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * One part of a colony (its nest, its food store, its alarm, ...) as the colony runs it: a name, what the part does once
 * a second while the nest is loaded and while it is away, and how it is saved and loaded. {@link Colony} holds its parts
 * in one list and ticks, saves and loads them each in one loop, so a part is written in once instead of in four or five
 * places that could drift apart (Project Apex, colony parts step 1, {@code docs/plans/apex.md}).
 *
 * <p>Each part says whether it is saved ({@link #saves}) or why not ({@link #notSaved}): a part that says neither stops
 * the colony being made, so a forgotten save line cannot slip in. A part's own keys go straight into the colony's tag, as
 * they always have, so old saves load unchanged.
 *
 * <p>The tick places: lower ticks first; gaps of 10, so a new part fits between two without renumbering. The places
 * while loaded and while away are separate orders (the hidden trips and the galleries take turns differently in the two).
 * A part with no tick here may still be ticked by its insects' goals or by another part.
 */
public final class ColonyPart<T> {
    private final String name;
    private final T part;
    private int tickPlace;
    @Nullable
    private BiConsumer<T, ServerLevel> tick;
    private int awayPlace;
    @Nullable
    private BiConsumer<T, ServerLevel> tickAway;
    @Nullable
    private BiConsumer<T, CompoundTag> save;
    @Nullable
    private BiConsumer<T, CompoundTag> load;
    @Nullable
    private String notSavedBecause;
    @Nullable
    private BiConsumer<T, ServerLevel> sample;
    /** The news this part hears (step 4), signed up when the colony is made ({@link #signUp}). */
    private final List<Hears<T, ?>> hears = new ArrayList<>();
    /** The questions this part answers (steps 6 and 7), signed up when the colony is made ({@link #signUp}). */
    private final List<Answers<T, ?>> answers = new ArrayList<>();
    /** What this part plugs into the shared code's sockets (step 8b), plugged in when the colony is made ({@link #signUp}). */
    private final List<Plugs<T, ?>> plugs = new ArrayList<>();

    /** What a part does with a piece of news (colony parts step 4, {@link ColonyNews}). */
    @FunctionalInterface
    public interface Hearer<T, N> {
        void hear(T part, ServerLevel level, N news);
    }

    private record Hears<T, N>(ColonyNews.Kind<N> kind, int place, Hearer<T, N> hearer) {
    }

    /** How a part answers a question (colony parts steps 6 and 7, {@link ColonyQuestions}): yes or no, only looking. */
    @FunctionalInterface
    public interface Answerer<T, Q> {
        boolean yes(T part, Level level, Q asked);
    }

    private record Answers<T, Q>(ColonyQuestions.Kind<Q> kind, int place, Answerer<T, Q> answerer) {
    }

    private record Plugs<T, H>(ColonyHooks.Kind<H> kind, int place, java.util.function.Function<T, H> hook) {
    }

    private ColonyPart(String name, T part) {
        this.name = name;
        this.part = part;
    }

    /** A part called {@code name} (as in a species file and the logs). */
    static <T> ColonyPart<T> of(String name, T part) {
        return new ColonyPart<>(name, part);
    }

    /** Ticked once a second while the nest's middle is loaded (from {@link Colony#tick}), at this place. */
    public ColonyPart<T> ticks(int place, BiConsumer<T, ServerLevel> tick) {
        this.tickPlace = place;
        this.tick = tick;
        return this;
    }

    /** Ticked once a second while the colony is away (from {@link AwayLife#tickAway}), at this place. */
    public ColonyPart<T> ticksAway(int place, BiConsumer<T, ServerLevel> tick) {
        this.awayPlace = place;
        this.tickAway = tick;
        return this;
    }

    /** Saved into the colony's tag, and loaded from it. */
    public ColonyPart<T> saves(BiConsumer<T, CompoundTag> save, BiConsumer<T, CompoundTag> load) {
        this.save = save;
        this.load = load;
        return this;
    }

    /** Never saved, on purpose (claims and short-lived things that start again after a load): {@code why}. */
    public ColonyPart<T> notSaved(String why) {
        this.notSavedBecause = why;
        return this;
    }

    /**
     * Hears every {@code kind} of news told in its colony ({@link ColonyNews}), at {@code place} among that kind's
     * listeners (lower first, gaps of 10, as with the tick places). Called only when that news comes.
     */
    public <N> ColonyPart<T> hears(ColonyNews.Kind<N> kind, int place, Hearer<T, N> hearer) {
        hears.add(new Hears<>(kind, place, hearer));
        return this;
    }

    /**
     * Answers every {@code kind} of question asked in its colony ({@link ColonyQuestions}), at {@code place} among that
     * kind's answerers (lower first, gaps of 10, as with the news).
     */
    public <Q> ColonyPart<T> answers(ColonyQuestions.Kind<Q> kind, int place, Answerer<T, Q> answerer) {
        answers.add(new Answers<>(kind, place, answerer));
        return this;
    }

    /**
     * Plugs {@code hook} (made from the part) into every {@code kind} of socket in the shared code ({@link ColonyHooks}),
     * at {@code place} among that kind's hooks (lower first, gaps of 10, as with the news).
     */
    public <H> ColonyPart<T> plugs(ColonyHooks.Kind<H> kind, int place, java.util.function.Function<T, H> hook) {
        plugs.add(new Plugs<>(kind, place, hook));
        return this;
    }

    /**
     * Signs this part up to the news it hears and the questions it answers, and plugs in its hooks (from {@link Colony}'s
     * constructor, once the colony's parts are made).
     */
    void signUp(ColonyNews news, ColonyQuestions questions, ColonyHooks hooks) {
        for (Hears<T, ?> one : hears) {
            signUp(news, one);
        }
        for (Answers<T, ?> one : answers) {
            signUp(questions, one);
        }
        for (Plugs<T, ?> one : plugs) {
            signUp(hooks, one);
        }
    }

    private <H> void signUp(ColonyHooks hooks, Plugs<T, H> one) {
        hooks.plug(one.kind(), one.place(), name, one.hook().apply(part));
    }

    private <Q> void signUp(ColonyQuestions questions, Answers<T, Q> one) {
        questions.answer(one.kind(), one.place(), name, (level, asked) -> one.answerer().yes(part, level, asked));
    }

    private <N> void signUp(ColonyNews news, Hears<T, N> one) {
        news.listen(one.kind(), one.place(), name, (level, told) -> one.hearer().hear(part, level, told));
    }

    /**
     * Tests only: how to put a sample into every number the part saves, for the save and load check
     * ({@code AwayKit.everyPartSavesAndLoads}), when the test scenes leave the part empty.
     */
    public ColonyPart<T> sample(BiConsumer<T, ServerLevel> sample) {
        this.sample = sample;
        return this;
    }

    /** Tests only: puts the part's sample in (nothing for a part without one, see {@link #sample}). */
    public void fillForTests(ServerLevel level) {
        if (sample != null) {
            sample.accept(part, level);
        }
    }

    /** Its name (as in a species file and the logs). */
    public String name() {
        return name;
    }

    /** The part itself. */
    public T part() {
        return part;
    }

    /** Whether it is saved with the colony. */
    public boolean isSaved() {
        return save != null;
    }

    /** Writes the part's own keys into {@code tag} (nothing for a part that is not saved). */
    public void save(CompoundTag tag) {
        if (save != null) {
            save.accept(part, tag);
        }
    }

    void load(CompoundTag tag) {
        if (load != null) {
            load.accept(part, tag);
        }
    }

    void tick(ServerLevel level) {
        tick.accept(part, level);
    }

    void tickAway(ServerLevel level) {
        tickAway.accept(part, level);
    }

    /** The parts that tick while loaded, in their tick order; checked: every part saved or not on purpose, no name or place twice. */
    static List<ColonyPart<?>> ticking(List<ColonyPart<?>> parts) {
        check(parts);
        return inOrder(parts, part -> part.tick != null, part -> part.tickPlace);
    }

    /** The parts that tick while away, in their tick order. */
    static List<ColonyPart<?>> tickingAway(List<ColonyPart<?>> parts) {
        return inOrder(parts, part -> part.tickAway != null, part -> part.awayPlace);
    }

    private static List<ColonyPart<?>> inOrder(List<ColonyPart<?>> parts, java.util.function.Predicate<ColonyPart<?>> ticks,
            java.util.function.ToIntFunction<ColonyPart<?>> place) {
        List<ColonyPart<?>> ticking = new ArrayList<>();
        Set<Integer> places = new HashSet<>();
        for (ColonyPart<?> part : parts) {
            if (ticks.test(part) && !places.add(place.applyAsInt(part))) {
                throw new IllegalStateException("two colony parts tick at place " + place.applyAsInt(part) + " (one is " + part.name + ")");
            }
            if (ticks.test(part)) {
                ticking.add(part);
            }
        }
        ticking.sort(Comparator.comparingInt(place));
        return List.copyOf(ticking);
    }

    private static void check(List<ColonyPart<?>> parts) {
        Set<String> names = new HashSet<>();
        for (ColonyPart<?> part : parts) {
            if (!names.add(part.name)) {
                throw new IllegalStateException("two colony parts are called " + part.name);
            }
            if ((part.save == null) == (part.notSavedBecause == null)) {
                throw new IllegalStateException("colony part " + part.name + " must say either how it is saved or why it is not");
            }
        }
    }
}
