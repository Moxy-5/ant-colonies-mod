package com.antcolonies.savanna.gametest;

import java.util.List;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.LifeNumbers;
import com.antcolonies.animal.Sex;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.TilapiaEntity;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The animal life kit (M9, step 1 of {@code docs/plans/animal-life-cycle.md}): every river animal holds its life
 * (sex, age, own size) through the kit, read from its life file, saved and loaded whole; animals saved before the kit
 * keep their size and sex; a summon's {@code sex} and {@code young} are obeyed; nothing changes in how they look.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class AnimalLifeTests {

    @GameTest(template = "arena", timeoutTicks = 40)
    public static void everyRiverAnimalKeepsItsLifeThroughASaveAndLoad(GameTestHelper helper) {
        List<EntityType<? extends Mob>> kinds = List.of(SavannaEntities.TILAPIA.get(), SavannaEntities.CATFISH.get(),
                SavannaEntities.CROCODILE.get(), SavannaEntities.CRAB.get(), SavannaEntities.SNAIL.get(),
                SavannaEntities.MONITOR.get());
        int x = 2;
        for (EntityType<? extends Mob> kind : kinds) {
            Mob mob = helper.spawn(kind, new BlockPos(x, 2, 4));
            x += 4;
            String name = EntityType.getKey(kind).getPath();
            if (!(mob instanceof HasLife animal)) {
                helper.fail(name + " has no life from the kit");
                return;
            }
            AnimalLife life = animal.life();
            LifeNumbers numbers = life.numbers();
            life.setUp(false, mob.getRandom());
            float[] range = numbers.sizeOf(life.sex());
            helper.assertTrue(life.ownSize() >= range[0] - 1e-4 && life.ownSize() <= range[1] + 1e-4,
                    name + "'s own size " + life.ownSize() + " is outside its file's " + range[0] + " to " + range[1]);
            helper.assertTrue(Math.abs(mob.getAttributeBaseValue(Attributes.SCALE) - life.ownSize()) < 0.01,
                    name + " is not drawn at its own size when grown");
            helper.assertTrue(numbers.twoSexes() == (life.sex() != Sex.NONE), name + "'s sex does not fit its kind: " + life.sex());
            helper.assertTrue(!numbers.breeds(), name + " breeds already (breeding is off in step 1)");

            // Saved and loaded whole.
            CompoundTag saved = mob.saveWithoutId(new CompoundTag());
            Mob copy = kind.create(helper.getLevel());
            copy.load(saved);
            AnimalLife back = ((HasLife) copy).life();
            helper.assertTrue(back.sex() == life.sex() && Math.abs(back.ownSize() - life.ownSize()) < 1e-5
                    && back.age() == life.age(), name + " lost its life in a save and load");

            // Saved before the kit: its old size and sex kept, a size too big clamped.
            CompoundTag old = new CompoundTag();
            old.putFloat("OwnSize", 1.05F);
            old.putBoolean("Male", false);
            Mob older = kind.create(helper.getLevel());
            ((HasLife) older).life().load(old);
            AnimalLife o = ((HasLife) older).life();
            helper.assertTrue(Math.abs(o.ownSize() - 1.05F) < 1e-5, name + " lost its size saved before the kit");
            helper.assertTrue(!numbers.twoSexes() || o.sex() == Sex.FEMALE, name + " lost its sex saved before the kit");
            CompoundTag huge = new CompoundTag();
            huge.putFloat("OwnSize", 5.0F);
            o.load(huge);
            helper.assertTrue(o.ownSize() <= numbers.maxSize() + 1e-5, name + " loaded bigger than its legs allow");

            // A summon's sex: obeyed when its kind has it, never giving a snail a sex.
            CompoundTag asked = new CompoundTag();
            asked.putString("sex", "female");
            o.load(asked);
            helper.assertTrue(o.sex() == (numbers.twoSexes() ? Sex.FEMALE : Sex.NONE), name + " got the wrong sex from a summon");
        }
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public static void aYoungTilapiaStillGrowsFromHalfSizeAsBefore(GameTestHelper helper) {
        TilapiaEntity fish = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(6, 2, 6));
        CompoundTag asked = fish.saveWithoutId(new CompoundTag());
        asked.remove("Life");
        asked.putBoolean("young", true);
        asked.putString("sex", "male");
        fish.load(asked);
        helper.assertTrue(fish.isYoung() && fish.isMale(), "a summon's young:true sex:\"male\" not obeyed");
        helper.assertTrue(fish.look() == TilapiaEntity.YOUNG, "a young tilapia does not wear the young look");
        double hatched = fish.getAttributeBaseValue(Attributes.SCALE) / fish.ownSize();
        helper.assertTrue(Math.abs(hatched - 0.5) < 0.01, "a hatched tilapia is not half its grown size: " + hatched);
        fish.setTicksToGrow(0);
        helper.assertTrue(!fish.isYoung() && fish.look() != TilapiaEntity.YOUNG, "a tilapia with no ticks left to grow is still young");
        helper.assertTrue(Math.abs(fish.getAttributeBaseValue(Attributes.SCALE) - fish.ownSize()) < 0.01,
                "a grown tilapia is not drawn at its own size");

        // A tilapia saved before the kit kept its age as "TilapiaAge".
        CompoundTag old = new CompoundTag();
        old.putInt("TilapiaAge", LifeNumbers.DAY);
        old.putBoolean("Male", true);
        old.putFloat("OwnSize", 0.9F);
        TilapiaEntity older = SavannaEntities.TILAPIA.get().create(helper.getLevel());
        older.life().load(old, "TilapiaAge");
        helper.assertTrue(older.isYoung() && older.life().age() == LifeNumbers.DAY && older.isMale(),
                "a tilapia saved before the kit lost its age or sex");
        helper.succeed();
    }
}
