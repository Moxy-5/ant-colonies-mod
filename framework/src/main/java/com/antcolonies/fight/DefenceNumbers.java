package com.antcolonies.fight;

import java.util.Optional;

import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.species.SpeciesSection;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How a species' colony defends its nest against a player (M8 step 2e, {@code docs/DESIGN.md}, "Steps 2e and 2f"), the
 * {@code defence} object of its species file, read by {@link NestDefence}:
 * <ul>
 * <li>{@code reach}: how far (blocks) from where it was provoked a defender chases a player before it gives up and goes
 * home.</li>
 * <li>{@code scent_reach}: the members this near (blocks) the trouble join in at once (the alarm scent of a hurt
 * nestmate, the stir at a broken nest).</li>
 * <li>{@code first_wave}, {@code wave}, {@code wave_every}, {@code most}: trouble near the nest brings out the
 * {@code first_wave} real members nearest the entrance, then {@code wave} more every {@code wave_every} ticks while it
 * goes on, at most {@code most} defenders at once.</li>
 * <li>{@code keep_going}: how long (ticks) one provocation keeps the defence going.</li>
 * <li>{@code nest_radius}: a block this near (blocks) the entrance, or touching a cell of the nest, is the nest.</li>
 * <li>{@code look}: a player walking over the entrance ({@link Look}).</li>
 * <li>{@code column_touch_ticks}: a player touching a member out on a fight order this long provokes it.</li>
 * <li>{@code queen_reach}: a provoking player this near (blocks) the queen is stung by her too; 0 for a queen that never
 * fights.</li>
 * <li>{@code sting}: what a sting does to a player ({@link Pain.Numbers}).</li>
 * </ul>
 */
public record DefenceNumbers(double reach, double scentReach, int firstWave, int wave, int waveEvery, int most, int keepGoing,
        double nestRadius, Look look, int columnTouchTicks, double queenReach, Pain.Numbers sting) {

    public static final Codec<DefenceNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("reach", 16.0).forGetter(DefenceNumbers::reach),
            Codec.DOUBLE.optionalFieldOf("scent_reach", 8.0).forGetter(DefenceNumbers::scentReach),
            Codec.INT.optionalFieldOf("first_wave", 5).forGetter(DefenceNumbers::firstWave),
            Codec.INT.optionalFieldOf("wave", 5).forGetter(DefenceNumbers::wave),
            Codec.INT.optionalFieldOf("wave_every", 200).forGetter(DefenceNumbers::waveEvery),
            Codec.INT.optionalFieldOf("most", 20).forGetter(DefenceNumbers::most),
            Codec.INT.optionalFieldOf("keep_going", 600).forGetter(DefenceNumbers::keepGoing),
            Codec.DOUBLE.optionalFieldOf("nest_radius", 3.0).forGetter(DefenceNumbers::nestRadius),
            Look.CODEC.optionalFieldOf("look", Look.DEFAULT).forGetter(DefenceNumbers::look),
            Codec.INT.optionalFieldOf("column_touch_ticks", 20).forGetter(DefenceNumbers::columnTouchTicks),
            Codec.DOUBLE.optionalFieldOf("queen_reach", 0.0).forGetter(DefenceNumbers::queenReach),
            Pain.Numbers.CODEC.optionalFieldOf("sting", Pain.Numbers.DEFAULT).forGetter(DefenceNumbers::sting)
    ).apply(i, DefenceNumbers::new));

    /**
     * A player walking over the entrance (not sneaking): this near it ({@code radius} blocks across, {@code height}
     * up or down), {@code ants} members come out to look; still there after {@code sting_after} ticks, they sting; they
     * stay on it {@code lasts} ticks after the player steps off.
     */
    public record Look(double radius, double height, int ants, int stingAfter, int lasts) {
        public static final Look DEFAULT = new Look(2.0, 3.0, 3, 60, 200);
        public static final Codec<Look> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("radius", 2.0).forGetter(Look::radius),
                Codec.DOUBLE.optionalFieldOf("height", 3.0).forGetter(Look::height),
                Codec.INT.optionalFieldOf("ants", 3).forGetter(Look::ants),
                Codec.INT.optionalFieldOf("sting_after", 60).forGetter(Look::stingAfter),
                Codec.INT.optionalFieldOf("lasts", 200).forGetter(Look::lasts)
        ).apply(i, Look::new));
    }

    public static final SpeciesSection<DefenceNumbers> SECTION = SpeciesSection.signUp("defence", CODEC);

    /** Signs the section up (at start, before the species files load). */
    public static void signUp() {
    }

    /** A species' defence numbers, if its file has them. */
    public static Optional<DefenceNumbers> of(SpeciesProfile profile) {
        return profile.section(SECTION);
    }
}
