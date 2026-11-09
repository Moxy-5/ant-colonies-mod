package com.antcolonies.entity;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.fight.FightNumbers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * An insect's open wound (M8 step 5, {@code docs/DESIGN.md} "Step 5, rescue and wound care"): a leg bitten off leaves
 * one. Real (Frank et al. 2018, "Wound treatment and selective help in a termite-hunting ant"; Frank et al. 2023,
 * "Targeted treatment of injured nestmates with antimicrobial compounds in an ant society"): nestmates lick a fresh
 * wound, which keeps most from becoming infected (untended about 80 in 100 die within a day or two, tended about 10);
 * an infected wound changes the smell of the hurt ant's body within about 11 hours, and nestmates then treat it with
 * the secretion of their metapleural gland, which saves most.
 *
 * <p>The wound's course, by its species' {@link FightNumbers.Rescue} numbers: licked up to {@code licks} times; at
 * {@code infect_ticks} after the wound it becomes infected or not by the dice ({@code infect_untended}, or
 * {@code infect_licked} once licked); an infected one can be smelt by nestmates from {@code smell_ticks} on and waits for
 * a gland dab; at {@code course_ticks} the infected insect dies ({@code death_untreated}, or {@code death_treated} once
 * dabbed) or heals; a clean wound heals at {@code heal_ticks}. One wound at a time (a second leg lost renews it: the
 * course starts over, the licks and the dab gone with the new wound; my pick). The server's side, saved with the insect.
 */
public final class Wounds {
    private static final long NONE = Long.MIN_VALUE;

    private final AbstractAntEntity insect;
    /** When the wound opened (game time), or {@link #NONE}. */
    private long openedAt = NONE;
    private int licks;
    private boolean dabbed;
    /** Whether the infection has been decided, and how. */
    private boolean decided;
    private boolean infected;

    Wounds(AbstractAntEntity insect) {
        this.insect = insect;
    }

    /** The insect's species' rescue numbers, or null if it does not care for its hurt. */
    @Nullable
    public static FightNumbers.Rescue numbers(AbstractAntEntity insect) {
        return FightNumbers.of(insect.profile()).flatMap(FightNumbers::rescue).orElse(null);
    }

    /** Whether it has an open wound. */
    public boolean isOpen() {
        return openedAt != NONE;
    }

    /** How long (ticks) the wound has been open, or -1 with none. */
    public long age() {
        return openedAt == NONE ? -1 : insect.level().getGameTime() - openedAt;
    }

    public int licks() {
        return licks;
    }

    public boolean isDabbed() {
        return dabbed;
    }

    /** Whether the wound is infected (decided at {@code infect_ticks}). */
    public boolean isInfected() {
        return isOpen() && decided && infected;
    }

    /** Whether nestmates still lick it: an open wound not yet decided, licked fewer than {@code licks} times. */
    public boolean wantsLicking() {
        FightNumbers.Rescue n = numbers(insect);
        return n != null && isOpen() && !decided && licks < n.licks();
    }

    /** Whether nestmates smell an infection on it and it waits for a gland dab. */
    public boolean wantsDab() {
        FightNumbers.Rescue n = numbers(insect);
        return n != null && isInfected() && !dabbed && age() >= n.smellTicks();
    }

    /** Whether it needs a nestmate's care now (an infected wound first). */
    public boolean wantsCare() {
        return wantsDab() || wantsLicking();
    }

    /** A leg was lost: a new wound opens (only for a species that cares for its hurt). */
    void open() {
        if (numbers(insect) == null) {
            return;
        }
        openedAt = insect.level().getGameTime();
        licks = 0;
        dabbed = false;
        decided = false;
        infected = false;
    }

    /** A nestmate licked it once. */
    public void licked() {
        licks++;
    }

    /** A nestmate dabbed its gland's secretion on the infected wound. */
    public void dab() {
        dabbed = true;
    }

    /** Tests: the wound opened this long ago (ticks), and its infection decided now if asked ({@code infected} null: not yet). */
    public void setForTests(long age, @Nullable Boolean infect) {
        openedAt = insect.level().getGameTime() - age;
        licks = 0;
        dabbed = false;
        decided = infect != null;
        infected = infect != null && infect;
    }

    /** Once a second: the infection decided, and the course run out (death or healing). */
    void tick(ServerLevel level) {
        if (!isOpen() || insect.tickCount % 20 != 0) {
            return;
        }
        FightNumbers.Rescue n = numbers(insect);
        if (n == null) {
            openedAt = NONE;
            return;
        }
        long age = level.getGameTime() - openedAt;
        RandomSource random = insect.getRandom();
        if (!decided && age >= n.infectTicks()) {
            decided = true;
            infected = random.nextDouble() < infectChance(licks, n);
        }
        if (decided && infected && age >= n.courseTicks()) {
            boolean dies = random.nextDouble() < deathChance(dabbed, n);
            openedAt = NONE;
            if (dies) {
                insect.kill(); // the infection killed it (its nestmates carry it to the refuse like any dead)
            }
        } else if (decided && !infected && age >= n.healTicks()) {
            openedAt = NONE; // healed
        }
    }

    /** The odds a wound turns infected (test revamp R5): lower once a nestmate licked it at least once. */
    public static double infectChance(int licks, FightNumbers.Rescue n) {
        return licks > 0 ? n.infectLicked() : n.infectUntended();
    }

    /** The odds an infected wound kills (test revamp R5): lower once a nestmate dabbed its gland's secretion on it. */
    public static double deathChance(boolean dabbed, FightNumbers.Rescue n) {
        return dabbed ? n.deathTreated() : n.deathUntreated();
    }

    /** A line for the report and the labels. */
    public String describe() {
        if (!isOpen()) {
            return "no wound";
        }
        return "wound " + age() + " ticks, licked " + licks + (decided ? infected ? ", infected" : ", clean" : "")
                + (dabbed ? ", dabbed" : "");
    }

    void save(CompoundTag tag) {
        if (!isOpen()) {
            return;
        }
        tag.putLong("WoundAt", openedAt);
        tag.putByte("WoundLicks", (byte) licks);
        tag.putBoolean("WoundDabbed", dabbed);
        tag.putByte("WoundInfection", (byte) (decided ? infected ? 2 : 1 : 0));
    }

    void load(CompoundTag tag) {
        if (!tag.contains("WoundAt")) {
            return;
        }
        openedAt = tag.getLong("WoundAt");
        licks = tag.getByte("WoundLicks");
        dabbed = tag.getBoolean("WoundDabbed");
        int infection = tag.getByte("WoundInfection");
        decided = infection > 0;
        infected = infection == 2;
    }
}
