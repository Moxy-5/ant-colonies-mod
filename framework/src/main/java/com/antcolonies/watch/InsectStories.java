package com.antcolonies.watch;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

/**
 * The raid test bench, B2 and B6: one insect told in a line, for the black box, the report and the labels over the ants.
 * The framework tells what every insect has (its kind, place, goals, route, how long it stood still); a species mod adds
 * its own story ({@link #signUp}: a Matabele's raid stage and what it waits for, a termite soldier shut out by a seal).
 */
public final class InsectStories {
    /** A species' own words about one insect (null when it has nothing to say about it). */
    @FunctionalInterface
    public interface Story {
        @Nullable
        String tell(ServerLevel level, AbstractAntEntity insect);
    }

    private static final List<Story> STORIES = new CopyOnWriteArrayList<>();

    private InsectStories() {
    }

    /** Signs up a species' story (while the mod loads). */
    public static void signUp(Story story) {
        STORIES.add(story);
    }

    /** Its kind's short name ({@code matabele_major}). */
    public static String kind(AbstractAntEntity insect) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(insect.getType()).getPath();
    }

    /** The first 8 letters of its id, enough to tell insects apart in a report. */
    public static String shortId(AbstractAntEntity insect) {
        return insect.getUUID().toString().substring(0, 8);
    }

    /** What the species mods say about it, joined ("" when nothing). */
    public static String story(ServerLevel level, AbstractAntEntity insect) {
        StringBuilder said = new StringBuilder();
        for (Story story : STORIES) {
            String words;
            try {
                words = story.tell(level, insect);
            } catch (RuntimeException e) {
                words = "(story failed: " + e + ")";
            }
            if (words != null && !words.isEmpty()) {
                if (!said.isEmpty()) {
                    said.append("; ");
                }
                said.append(words);
            }
        }
        return said.toString();
    }

    /** The black box's and the report's line: everything known about it now. */
    public static String line(ServerLevel level, AbstractAntEntity insect, long stillTicks) {
        BlockPos at = insect.blockPosition();
        StringBuilder line = new StringBuilder();
        line.append(kind(insect)).append(' ').append(shortId(insect)).append(" at ").append(at.toShortString());
        line.append(String.format(" (%.1f, %.1f, %.1f)", insect.getX(), insect.getY(), insect.getZ()));
        line.append(String.format(" hp %.1f/%.0f", insect.getHealth(), insect.getMaxHealth()));
        if (insect.isParalysed()) {
            line.append(" paralysed ").append(insect.paralysedForTicks() / 20).append(" s");
        } else if (insect.isDowned()) {
            line.append(" staggering");
        }
        if (insect.isPinned()) {
            line.append(" held");
        }
        if (insect.isAsleep()) {
            line.append(" asleep");
        }
        if (insect.fightsNoMore()) {
            line.append(" out of the fight");
        }
        if (insect.isInWall()) {
            line.append(" INSIDE A BLOCK");
        }
        BlockPos goal = insect.getNavigator().getGoal();
        if (insect.getNavigator().isDone()) {
            line.append(insect.getNavigator().hasFailed() ? " no route (last search failed)" : " standing");
        } else {
            line.append(" walking to ").append(goal == null ? "?" : goal.toShortString());
        }
        if (stillTicks >= 40) {
            line.append(", still ").append(stillTicks / 20).append(" s");
        }
        BlockPos fight = insect.getFightSpot();
        if (fight != null) {
            line.append(", fight spot ").append(fight.toShortString());
        }
        line.append(", goals ").append(insect.runningGoals());
        String story = story(level, insect);
        if (!story.isEmpty()) {
            line.append(" | ").append(story);
        }
        return line.toString();
    }

    /** The label's short line over it: the species' story first (what it is doing), else its goal; with how long still. */
    public static String label(ServerLevel level, AbstractAntEntity insect, long stillTicks) {
        StringBuilder label = new StringBuilder();
        String story = story(level, insect);
        if (!story.isEmpty()) {
            label.append(story);
        } else {
            String goals = insect.runningGoals();
            label.append(goals.length() > 2 ? goals.substring(1, goals.length() - 1).replace("Goal", "") : "idle");
        }
        if (insect.isParalysed()) {
            label.insert(0, "paralysed; ");
        }
        if (insect.getNavigator().hasFailed() && insect.getNavigator().isDone()) {
            label.append("; no way");
        }
        if (stillTicks >= 100) {
            label.append("; still ").append(stillTicks / 20).append(" s");
        }
        String text = label.toString();
        return text.length() > 70 ? text.substring(0, 67) + "..." : text;
    }
}
