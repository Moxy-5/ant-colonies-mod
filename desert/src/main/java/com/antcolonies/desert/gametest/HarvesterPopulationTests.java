package com.antcolonies.desert.gametest;

import com.antcolonies.gametest.TestScenes;
import static com.antcolonies.gametest.TestScenes.*;
import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.desert.DesertMod;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.colony.Population;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.desert.registry.DesertEntities;
import com.antcolonies.desert.DesertSpecies;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;


/**
 * The brain, part 1 ({@link Population}): most of the colony is numbers inside the nest, a few are real mobs. Game tests
 * have no players, so each test says where pretend players' eyes are ({@link Population#setViewersForTests}).
 * The red harvester ant's scenes (the split, step S4; the termites' are in {@link TermitePopulationTests}).
 */
@GameTestHolder(DesertMod.MODID)
@PrefixGameTestTemplate(false)
public class HarvesterPopulationTests {
    /** An ant colony in this test area with its whole nest dug out at once and the brain on; null if there is none yet. */
    static Colony antColony(GameTestHelper helper) {
        return colony(helper, DesertSpecies.RED_HARVESTER_ANT);
    }
}
