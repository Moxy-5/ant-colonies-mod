package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Review R1-44: the plants a colony knows are saved as rows of numbers, and come back the same. */
class FieldSaveTest {
    private static Map<BlockPos, FieldLife.Plant> plants(int count) {
        Random dice = new Random(7);
        Map<BlockPos, FieldLife.Plant> plants = new HashMap<>();
        while (plants.size() < count) {
            plants.put(new BlockPos(dice.nextInt(4000) - 2000, 60 + dice.nextInt(20), dice.nextInt(4000) - 2000),
                    new FieldLife.Plant(dice.nextInt(4), dice.nextFloat() * 40, dice.nextBoolean()));
        }
        return plants;
    }

    @Test
    void packedPlantsComeBackTheSame() {
        Map<BlockPos, FieldLife.Plant> plants = plants(500);
        CompoundTag field = new CompoundTag();
        FieldLife.pack(field, "plant", plants);
        Map<BlockPos, FieldLife.Plant> back = new HashMap<>();
        FieldLife.unpack(field, "plant", back);
        assertEquals(plants, back);
    }
}
