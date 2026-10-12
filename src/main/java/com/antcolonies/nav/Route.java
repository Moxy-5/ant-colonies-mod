package com.antcolonies.nav;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * A planned route: the footholds one after the other, each with the kind of move that gets there. Immutable, so one
 * route can be shared: a mind (later: the colony brain for a trail or a marching column) can plan it once and hand it
 * to many insects ({@link AntNavigator#follow}).
 */
public final class Route {
    /** One step: the foothold it ends on and how the insect gets there. */
    public record Step(Foothold to, StepKind kind) {
    }

    private final List<Step> steps;
    private final double cost;

    public Route(List<Step> steps, double cost) {
        if (steps.isEmpty() || steps.get(0).kind() != StepKind.START) {
            throw new IllegalArgumentException("a route starts with its START step");
        }
        this.steps = List.copyOf(steps);
        this.cost = cost;
    }

    public List<Step> steps() {
        return steps;
    }

    public Step step(int index) {
        return steps.get(index);
    }

    /** How many steps, the start included. */
    public int size() {
        return steps.size();
    }

    /** The total cost the planner gave it (about one per block walked; climbing and corners cost a little more). */
    public double cost() {
        return cost;
    }

    public Foothold start() {
        return steps.get(0).to();
    }

    public Foothold end() {
        return steps.get(steps.size() - 1).to();
    }

    /** The cells of the route in order, each once (several footholds in one cell, like a corner turn, count once). */
    public List<BlockPos> cells() {
        List<BlockPos> cells = new ArrayList<>();
        for (Step step : steps) {
            BlockPos cell = step.to().cell();
            if (cells.isEmpty() || !cells.get(cells.size() - 1).equals(cell)) {
                cells.add(cell);
            }
        }
        return cells;
    }

    /** Whether any step lets go and drops (a mind may want to avoid routes like that, for example with brood). */
    public boolean hasFall() {
        return steps.stream().anyMatch(step -> step.kind() == StepKind.FALL);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (Step step : steps) {
            if (text.length() > 0) {
                text.append(" > ");
            }
            text.append(step.kind().name().toLowerCase()).append(' ').append(step.to());
        }
        return text.toString();
    }
}
