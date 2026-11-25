package com.antcolonies.savanna.animal;

import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;

/**
 * The Cape buffalo's mind ({@link BuffaloEntity}; {@code docs/plans/buffalo.md}). Step 1 of the plan gives it only
 * Minecraft's float goal, so a buffalo in deep water swims, floating low; its day (grazing, drinking at its pan at midday,
 * lying down) and its temper (watch, warn, charge, calm) are step 2's, added here.
 */
final class BuffaloGoals {
    private BuffaloGoals() {
    }

    /** Its goals, by weight. */
    static void register(BuffaloEntity buffalo, GoalSelector goals) {
        goals.addGoal(0, new FloatGoal(buffalo));
    }
}
