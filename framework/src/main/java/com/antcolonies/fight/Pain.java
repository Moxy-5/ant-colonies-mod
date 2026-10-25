package com.antcolonies.fight;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.antcolonies.registry.ModEffects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * A sting on a player (M8 step 2e, the user's call, 2026-10-22: 2 hearts and a short pain effect, armour as normal):
 * the sting's damage, then the pain ({@link ModEffects#STING_PAIN}: the player slowed, the screen's edge pulsing red),
 * longer with every sting in a short while. Real: a Matabele sting is one of the most painful of any ant; the pain is
 * said to last hours, here only seconds.
 */
public final class Pain {
    /** The stings each player had lately (game time), to make the pain longer; server only, not saved. */
    private static final Map<UUID, Deque<Long>> STINGS = new HashMap<>();

    private Pain() {
    }

    /**
     * What a sting does to a player: {@code damage} (4, two hearts, before armour), at most once every
     * {@code every_ticks} from one ant; the pain lasts {@code pain_ticks}, {@code more_ticks} longer for each sting in the
     * last {@code window_ticks}, up to {@code most_ticks}.
     */
    public record Numbers(float damage, int everyTicks, int painTicks, int moreTicks, int mostTicks, int windowTicks) {
        public static final Numbers DEFAULT = new Numbers(4.0F, 30, 60, 40, 300, 600);
        public static final Codec<Numbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("damage", 4.0F).forGetter(Numbers::damage),
                Codec.INT.optionalFieldOf("every_ticks", 30).forGetter(Numbers::everyTicks),
                Codec.INT.optionalFieldOf("pain_ticks", 60).forGetter(Numbers::painTicks),
                Codec.INT.optionalFieldOf("more_ticks", 40).forGetter(Numbers::moreTicks),
                Codec.INT.optionalFieldOf("most_ticks", 300).forGetter(Numbers::mostTicks),
                Codec.INT.optionalFieldOf("window_ticks", 600).forGetter(Numbers::windowTicks)
        ).apply(i, Numbers::new));
    }

    /** The pain of a sting that landed on this player (its damage already done): longer for each sting lately. */
    public static void stung(Player player, Numbers numbers) {
        long now = player.level().getGameTime();
        Deque<Long> times = STINGS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        while (!times.isEmpty() && (now - times.peekFirst() > numbers.windowTicks() || times.peekFirst() > now)) {
            times.pollFirst();
        }
        int lately = times.size();
        times.addLast(now);
        int lasts = Math.min(numbers.mostTicks(), numbers.painTicks() + numbers.moreTicks() * lately);
        MobEffectInstance had = player.getEffect(ModEffects.STING_PAIN);
        if (had == null || had.getDuration() < lasts) {
            player.addEffect(new MobEffectInstance(ModEffects.STING_PAIN, lasts, 0, false, true, true));
        }
        if (STINGS.size() > 64) {
            STINGS.values().removeIf(Deque::isEmpty);
        }
    }

    /** How many stings this player had in the window up to now (for the game tests). */
    public static int stingsLately(Player player, Numbers numbers) {
        long now = player.level().getGameTime();
        Deque<Long> times = STINGS.get(player.getUUID());
        return times == null ? 0 : (int) times.stream().filter(time -> now - time <= numbers.windowTicks() && time <= now).count();
    }
}
