package com.antcolonies.fight;

import com.antcolonies.entity.AbstractAntEntity;

/**
 * Whoever gave an insect its fight order and keeps a diary of the fight (BUGS 189: a species' raid writes every grab,
 * dodge, sting and break-in of its raiders into its {@code [raid]} lines, so an attack that stings nothing can be read
 * afterwards). Species-neutral: the shared fight only tells, never asks.
 */
public interface FightDiary {
    /** One thing that happened in the fight of {@code ant}, in plain words. */
    void fightNote(AbstractAntEntity ant, String what);

    /** Tells the giver of {@code ant}'s fight order, if it keeps a diary. */
    static void note(AbstractAntEntity ant, String what) {
        if (ant.fightOrderBy() instanceof FightDiary diary) {
            diary.fightNote(ant, what);
        }
    }
}
