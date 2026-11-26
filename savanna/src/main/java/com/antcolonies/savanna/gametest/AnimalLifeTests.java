package com.antcolonies.savanna.gametest;

import java.util.List;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.AnimalLives;
import com.antcolonies.animal.EggClutchBlockEntity;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.LifeNumbers;
import com.antcolonies.animal.Sex;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.CrabEntity;
import com.antcolonies.savanna.animal.SnailEntity;
import com.antcolonies.savanna.animal.TilapiaEntity;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The animal life kit (M9, step 1 of {@code docs/plans/animal-life-cycle.md}): every river animal holds its life
 * (sex, age, own size) through the kit, read from its life file, saved and loaded whole; animals saved before the kit
 * keep their size and sex; a summon's {@code sex} and {@code young} are obeyed. Step 2: the tilapia and the snail breed
 * (a nest pit and mouthbrooding; clutches at the waterline), and room to breed stops them at the local limit.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class AnimalLifeTests {

    @GameTest(template = "arena", timeoutTicks = 40)
    public static void everyRiverAnimalKeepsItsLifeThroughASaveAndLoad(GameTestHelper helper) {
        List<EntityType<? extends Mob>> kinds = List.of(SavannaEntities.TILAPIA.get(), SavannaEntities.CATFISH.get(),
                SavannaEntities.CROCODILE.get(), SavannaEntities.CRAB.get(), SavannaEntities.SNAIL.get(),
                SavannaEntities.MONITOR.get(), SavannaEntities.BULLFROG.get());
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
            boolean breeds = kind == SavannaEntities.TILAPIA.get() || kind == SavannaEntities.SNAIL.get()
                    || kind == SavannaEntities.CRAB.get() || kind == SavannaEntities.CATFISH.get() || kind == SavannaEntities.CROCODILE.get()
                    || kind == SavannaEntities.BULLFROG.get();
            helper.assertTrue(numbers.breeds() == breeds, name + (breeds ? " does not breed (on since step 2 or 3)" : " breeds already (not before its own step)"));

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

    /**
     * A tilapia hatches as a fry a little over a fifth of its grown size (step 2: the fry round their mother's face) and
     * grows; the world's own young spawns are 2 to 3 days old, as big as young spawns were before (half to two thirds).
     */
    @GameTest(template = "arena", timeoutTicks = 40)
    public static void aTilapiaHatchesAsAFryAndTheWorldsYoungAreAsBefore(GameTestHelper helper) {
        TilapiaEntity fish = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(6, 2, 6));
        CompoundTag asked = fish.saveWithoutId(new CompoundTag());
        asked.remove("Life");
        asked.putBoolean("young", true);
        asked.putString("sex", "male");
        fish.load(asked);
        helper.assertTrue(fish.isYoung() && fish.isMale(), "a summon's young:true sex:\"male\" not obeyed");
        helper.assertTrue(fish.look() == TilapiaEntity.YOUNG, "a young tilapia does not wear the young look");
        double hatched = fish.getAttributeBaseValue(Attributes.SCALE) / fish.ownSize();
        helper.assertTrue(Math.abs(hatched - 0.22) < 0.01, "a hatched tilapia is not a fry of 0.22 its grown size: " + hatched);
        for (int i = 0; i < 40; i++) {
            TilapiaEntity spawned = new TilapiaEntity(SavannaEntities.TILAPIA.get(), helper.getLevel());
            spawned.setUp(true, helper.getLevel().random);
            double share = spawned.getAttributeBaseValue(Attributes.SCALE) / spawned.ownSize();
            helper.assertTrue(spawned.isYoung() && share > 0.5 && share < 0.7, "a young spawn of the world at " + share + " of grown (0.5 to 0.7 wanted)");
        }
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

    // --- breeding (step 2: the tilapia and the snail) --------------------------------------------------------------------

    private static final int P0 = 2, P1 = 13;              // the pond's inside, x and z
    private static final int BED = TermiteMoundTests.SURFACE, WATER_TOP = BED + 2;   // a silt bed at the arena's ground, two blocks of water over it

    /**
     * A small pond for breeding: a silt bed with two blocks of water over it, a block-deep shelf of silt along its west
     * bank (the shallows), walled with stone one block higher than the water (a bank face just over the waterline for
     * snail eggs), on a stone floor so no water leaks out.
     */
    private static void breedingPond(GameTestHelper helper) {
        for (int x = P0 - 1; x <= P1 + 1; x++) {
            for (int z = P0 - 1; z <= P1 + 1; z++) {
                boolean wall = x < P0 || x > P1 || z < P0 || z > P1;
                helper.setBlock(new BlockPos(x, BED - 1, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, BED, z), wall ? Blocks.STONE : SavannaBlocks.SILT.get());
                for (int y = BED + 1; y <= WATER_TOP + 2; y++) {
                    BlockPos at = new BlockPos(x, y, z);
                    if (wall) {
                        helper.setBlock(at, y <= WATER_TOP + 1 ? Blocks.STONE : Blocks.AIR);
                    } else if (x == P0 && y == BED + 1) {
                        helper.setBlock(at, SavannaBlocks.SILT.get());
                    } else {
                        helper.setBlock(at, y <= WATER_TOP ? Blocks.WATER : Blocks.AIR);
                    }
                }
            }
        }
    }

    /**
     * Clears animals of this kind left round the arena by tests of earlier batches (room to breed counts every one within
     * {@link com.antcolonies.animal.RoomToBreed#REACH}); only for a test alone in its batch.
     */
    private static void clearLeftovers(GameTestHelper helper, EntityType<?> kind) {
        net.minecraft.world.phys.AABB round = new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(8, BED, 8)))
                .inflate(com.antcolonies.animal.RoomToBreed.REACH + 16);
        helper.getLevel().getEntities(kind, round, e -> true).forEach(net.minecraft.world.entity.Entity::discard);
    }

    /** A grown animal of this sex ("male", "female" or "none"), ready to breed. */
    private static <T extends Mob & HasLife> T grown(GameTestHelper helper, EntityType<T> kind, String sex, BlockPos at) {
        T animal = helper.spawn(kind, at);
        animal.life().setUp(false, animal.getRandom());
        CompoundTag asked = new CompoundTag();
        asked.putString("sex", sex);
        animal.life().load(asked);
        return animal;
    }

    /**
     * The tilapia's breeding: a ready male fans a nest pit out of the silt and wears his breeding colours on it; a ready
     * female comes, they court, she takes the eggs into her mouth (her throat swells) and he leaves the pit in his everyday
     * colours; a day on, 4 to 6 fry come out round her face, fry-sized; a player coming near sends them into her mouth,
     * and when they are gone she lets them out again.
     */
    @GameTest(template = "arena", timeoutTicks = 3000, batch = "breeding_tilapia")
    public static void tilapiaBreedInANestPitAndTheMotherKeepsHerFryInHerMouth(GameTestHelper helper) {
        breedingPond(helper);
        clearLeftovers(helper, SavannaEntities.TILAPIA.get());
        TilapiaEntity male = grown(helper, SavannaEntities.TILAPIA.get(), "male", new BlockPos(5, BED + 1, 5));
        TilapiaEntity female = grown(helper, SavannaEntities.TILAPIA.get(), "female", new BlockPos(10, BED + 2, 10));
        helper.assertTrue(male.look() == TilapiaEntity.EVERYDAY, "a grown male with no nest wears the breeding colours");
        male.wantsNestNow = true;
        int[] stage = {0};
        long[] since = {0};
        int[] fryAt = {0};
        net.minecraft.server.level.ServerPlayer[] danger = {null};
        helper.runAtTickTime(2990, () -> helper.fail("stuck at stage " + stage[0] + " (players " + helper.getLevel().players().size()
                + ", room " + com.antcolonies.animal.RoomToBreed.here(male, male.life().numbers().localLimit()) + "): male nest " + male.nest() + " wants " + male.wantsNestNow
                + " ready " + male.life().readyToBreed() + " in water " + male.isInWater() + " at " + helper.relativePos(male.blockPosition())
                + "; female ready " + female.life().readyToBreed() + " partner " + female.life().partner() + " eggs " + female.eggsInMouth()
                + " at " + helper.relativePos(female.blockPosition()) + " stage " + female.life().stage() + " age " + female.life().age()
                + "; his id " + male.getUUID() + " his partner " + male.life().partner() + " d " + female.distanceTo(male) + " fpos " + female.position()
                + " mpos " + male.position() + " fnav done " + female.getNavigation().isDone() + " fpath " + female.getNavigation().getPath()
                + " fgoals " + female.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning()).map(g -> g.getGoal().getClass().getSimpleName()).toList()
                + " mgoals " + male.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning()).map(g -> g.getGoal().getClass().getSimpleName()).toList()
                + " fry " + fryOf(helper, female).size()));
        helper.onEachTick(() -> {
            ServerLevel level = helper.getLevel();
            long now = level.getGameTime();
            switch (stage[0]) {
                case 0 -> {                         // he digs his pit
                    if (male.holdsNest()) {
                        helper.assertTrue(level.getBlockState(male.nest()).is(SavannaBlocks.TILAPIA_NEST.get()), "he holds a nest with no pit");
                        helper.assertTrue(male.look() == TilapiaEntity.BREEDING, "a male on his pit not in breeding colours");
                        stage[0] = 1;
                    }
                }
                case 1 -> {                         // she comes, they court, she takes the eggs
                    if (female.eggsInMouth() > 0) {
                        int eggs = female.eggsInMouth();
                        helper.assertTrue(eggs >= 4 && eggs <= 6, eggs + " eggs (4 to 6 wanted)");
                        helper.assertTrue(female.mouthFull(), "her mouth full of eggs is not drawn swollen");
                        helper.assertTrue(!male.holdsNest() && male.look() == TilapiaEntity.EVERYDAY, "he still holds his pit after mating");
                        fryAt[0] = eggs;
                        female.ageEggs(female.life().numbers().hatchTicks());
                        stage[0] = 2;
                    }
                }
                case 2 -> {                         // the fry come out round her face
                    List<TilapiaEntity> fry = fryOf(helper, female);
                    if (!fry.isEmpty()) {
                        helper.assertTrue(fry.size() == fryAt[0], fry.size() + " fry from " + fryAt[0] + " eggs");
                        for (TilapiaEntity f : fry) {
                            double share = f.getAttributeBaseValue(Attributes.SCALE) / f.ownSize();
                            helper.assertTrue(f.isYoung() && f.withMother() && Math.abs(share - 0.22) < 0.02, "a fry not fry-sized or not with her: " + share);
                        }
                        helper.assertTrue(female.caring() && !female.mouthFull(), "her mouth still full with her fry out");
                        stage[0] = 3;
                        since[0] = now;
                    }
                }
                case 3 -> {                         // the fry keep round her; then a catfish comes
                    if (now - since[0] == 100) {
                        // (fry gone here were despawned, not taken in: a stand-in player left behind far off, BUGS 303)
                        helper.assertTrue(!fryOf(helper, female).isEmpty(), "her fry are gone before anything came near");
                        for (TilapiaEntity f : fryOf(helper, female)) {
                            helper.assertTrue(f.distanceTo(female) < 3, "a fry strayed " + f.distanceTo(female) + " from its mother");
                        }
                        // a player comes near (a catfish spawned here would be hungry and might grab her instead)
                        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
                        player.moveTo(female.getX() + 2, female.getY() + 1, female.getZ());
                        danger[0] = player;
                        stage[0] = 4;
                        since[0] = now;
                    }
                }
                case 4 -> {                         // she takes them into her mouth
                    if (female.fryInMouth() > 0) {
                        helper.assertTrue(female.mouthFull(), "her mouth full of fry is not drawn swollen");
                        helper.getLevel().getServer().getPlayerList().remove(danger[0]);   // and goes
                        stage[0] = 5;
                        since[0] = now;
                    } else if (now - since[0] > 40) {
                        helper.fail("a player beside her and her fry not taken into her mouth (" + fryOf(helper, female).size() + " fry round her)");
                    }
                }
                case 5 -> {                         // and lets them out when it is gone
                    if (female.fryInMouth() == 0 && fryOf(helper, female).size() == fryAt[0]) {
                        helper.succeed();
                    } else if (now - since[0] > 200) {
                        helper.fail("the player gone and her fry not let out again (" + female.fryInMouth() + " in her mouth)");
                    }
                }
                default -> {
                }
            }
        });
    }

    /** The goals a mob is running now, by name (for a stuck message). */
    private static String goalsRunning(Mob mob) {
        try {
            java.lang.reflect.Field field = Mob.class.getDeclaredField("goalSelector");
            field.setAccessible(true);
            return ((net.minecraft.world.entity.ai.goal.GoalSelector) field.get(mob)).getAvailableGoals().stream()
                    .filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
                    .map(g -> g.getGoal().getClass().getSimpleName()).toList().toString();
        } catch (ReflectiveOperationException e) {
            return "?";
        }
    }

    private static List<TilapiaEntity> fryOf(GameTestHelper helper, TilapiaEntity mother) {
        return helper.getLevel().getEntitiesOfClass(TilapiaEntity.class, mother.getBoundingBox().inflate(16),
                t -> t.isAlive() && mother.getUUID().equals(t.motherId()));
    }

    /** Room to breed: with as many tilapia round as the local limit, a ready male never digs a pit. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "breeding_full")
    public static void noTilapiaNestWhileTheRiverIsFull(GameTestHelper helper) {
        breedingPond(helper);
        TilapiaEntity male = grown(helper, SavannaEntities.TILAPIA.get(), "male", new BlockPos(5, BED + 1, 5));
        int limit = male.life().numbers().localLimit();
        for (int i = 1; i < limit + 3; i++) {                // a few past the limit: a full river, not one fish drifting off the count's edge (2026-11-22, smaller tilapia boxes)
            grown(helper, SavannaEntities.TILAPIA.get(), "female", new BlockPos(P0 + i % 12, BED + 1 + i % 2, P0 + 1 + i / 12 * 3));
        }
        helper.onEachTick(() -> male.wantsNestNow = true);
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(!male.holdsNest(), "a male dug a pit with " + limit + " tilapia round");
            helper.succeed();
        });
    }

    /**
     * The river snail's breeding: two ready snails meet and both lay a pearly clutch, at least one just over the
     * waterline on the bank's face; old enough and watched the eggs hatch into baby snails a quarter of the size; with
     * the local limit of snails round they wait, and long unwatched they are gone.
     */
    @GameTest(template = "arena", timeoutTicks = 3600, batch = "breeding")
    public static void snailsMeetLayAtTheWaterlineAndHatch(GameTestHelper helper) {
        breedingPond(helper);
        clearLeftovers(helper, SavannaEntities.SNAIL.get());
        SnailEntity a = grown(helper, SavannaEntities.SNAIL.get(), "none", new BlockPos(P0 + 1, BED + 1, P0 + 3));
        SnailEntity b = grown(helper, SavannaEntities.SNAIL.get(), "none", new BlockPos(P0 + 3, BED + 1, P0 + 3));
        net.minecraft.world.phys.Vec3 aFrom = a.position(), bFrom = b.position();
        boolean[] laying = {false};
        helper.runAtTickTime(3590, () -> helper.fail("snails stuck: laying " + laying[0] + " a " + a.eggsToLay() + " ready " + a.life().readyToBreed()
                + " partner " + a.life().partner() + " at " + helper.relativePos(a.blockPosition()) + "; b " + b.eggsToLay() + " ready "
                + b.life().readyToBreed() + " at " + helper.relativePos(b.blockPosition()) + " hidden " + a.isHidden() + "/" + b.isHidden()
                // (measured for BUGS 242, 270: the pair sat on their spawn blocks for the whole test, which room alone does not explain)
                + "; alive " + a.isAlive() + "/" + b.isAlive() + " removed " + a.isRemoved() + "/" + b.isRemoved()
                + " moved " + String.format("%.1f/%.1f", a.position().distanceTo(aFrom), b.position().distanceTo(bFrom))
                + " room " + com.antcolonies.animal.RoomToBreed.here(a, a.life().numbers().localLimit())
                + " snails near " + com.antcolonies.animal.RoomToBreed.ownKindNear(helper.getLevel(), a)
                + " players " + helper.getLevel().getServer().getPlayerList().getPlayerCount()
                + " goals a " + goalsRunning(a) + " b " + goalsRunning(b) + " no action " + a.getNoActionTime()
                + " in water " + a.isInWater() + " nav done " + a.getNavigation().isDone()));
        helper.onEachTick(() -> {
            if (a.eggsToLay() > 0 || b.eggsToLay() > 0) {
                laying[0] = true;
            }
            if (!laying[0] || a.eggsToLay() > 0 || b.eggsToLay() > 0) {
                return;
            }
            List<BlockPos> clutches = new java.util.ArrayList<>();
            boolean atTheWaterline = false;
            for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(P0 - 1, BED, P0 - 1), new BlockPos(P1 + 1, WATER_TOP + 2, P1 + 1))) {
                if (helper.getBlockState(rel).is(SavannaBlocks.SNAIL_EGGS.get())) {
                    clutches.add(helper.absolutePos(rel));
                    atTheWaterline |= rel.getY() == WATER_TOP + 1;
                }
            }
            StringBuilder seen = new StringBuilder();
            for (BlockPos c : clutches) {
                EggClutchBlockEntity e = (EggClutchBlockEntity) helper.getLevel().getBlockEntity(c);
                seen.append(helper.relativePos(c).toShortString()).append(" ").append(helper.getLevel().getBlockState(c).getValue(com.antcolonies.savanna.block.SnailEggsBlock.FACING))
                        .append(" x").append(e == null ? -1 : e.count()).append(" at ").append(e == null ? -1 : helper.getLevel().getGameTime() - e.laid()).append("; ");
            }
            helper.assertTrue(clutches.size() == 2, clutches.size() + " clutches laid by the pair (2 wanted): " + seen);
            helper.assertTrue(atTheWaterline, "no clutch just over the waterline: " + clutches);
            hatchChecks(helper, clutches);
            helper.succeed();
        });
    }

    private static void hatchChecks(GameTestHelper helper, List<BlockPos> clutches) {
        ServerLevel level = helper.getLevel();
        EntityType<SnailEntity> kind = SavannaEntities.SNAIL.get();
        long hatch = AnimalLives.get(kind).hatchTicks();
        BlockPos first = clutches.get(0), second = clutches.get(1);
        net.minecraft.world.phys.Vec3 eye = net.minecraft.world.phys.Vec3.atCenterOf(first);
        try {
            // old enough but unwatched: waits
            age(level, first, hatch);
            tick(level, first);
            helper.assertTrue(level.getBlockState(first).is(SavannaBlocks.SNAIL_EGGS.get()), "a clutch hatched with nobody near");
            // watched: hatches into babies
            com.antcolonies.colony.Watchers.setPretend(List.of(eye));
            int eggs = ((EggClutchBlockEntity) level.getBlockEntity(first)).count();
            tick(level, first);
            helper.assertTrue(!level.getBlockState(first).is(SavannaBlocks.SNAIL_EGGS.get()), "a clutch old enough and watched did not hatch");
            List<SnailEntity> babies = level.getEntitiesOfClass(SnailEntity.class, new net.minecraft.world.phys.AABB(first).inflate(1.5),
                    s -> s.life().isYoung());
            helper.assertTrue(babies.size() == eggs, babies.size() + " babies from " + eggs + " eggs");
            for (SnailEntity baby : babies) {
                double share = baby.getAttributeBaseValue(Attributes.SCALE) / baby.life().ownSize();
                helper.assertTrue(Math.abs(share - 0.25) < 0.01, "a baby snail at " + share + " of grown (0.25 wanted)");
            }
            // room to breed: the local limit of snails round, the second clutch waits
            int limit = AnimalLives.get(kind).localLimit();
            int near = level.getEntities(kind, new net.minecraft.world.phys.AABB(second).inflate(com.antcolonies.animal.RoomToBreed.REACH), e -> e.isAlive()).size();
            for (int i = near; i < limit; i++) {
                grown(helper, kind, "none", new BlockPos(P1 - 1, BED + 1, P1 - 1 - i % 4));
            }
            age(level, second, hatch);
            tick(level, second);
            helper.assertTrue(level.getBlockState(second).is(SavannaBlocks.SNAIL_EGGS.get()), "a clutch hatched with " + limit + " snails round");
            // long unwatched: gone
            com.antcolonies.colony.Watchers.setPretend(List.of());
            age(level, second, com.antcolonies.animal.ClutchClock.SHELLS_AFTER);
            tick(level, second);
            helper.assertTrue(!level.getBlockState(second).is(SavannaBlocks.SNAIL_EGGS.get()), "a clutch 10 days old and unwatched is still there");
        } finally {
            com.antcolonies.colony.Watchers.setPretend(List.of());
        }
    }

    /** Makes a clutch this many ticks old. */
    private static void age(ServerLevel level, BlockPos at, long ticks) {
        EggClutchBlockEntity clutch = (EggClutchBlockEntity) level.getBlockEntity(at);
        clutch.lay(clutch.motherKind(), clutch.count(), level.getGameTime() - ticks);
    }

    /** One random tick of the clutch (Minecraft gives one now and then; here at once). */
    private static void tick(ServerLevel level, BlockPos at) {
        level.getBlockState(at).randomTick(level, at, level.random);
    }

    // --- breeding (step 3: the crab and the catfish; eaten eggs) ---------------------------------------------------------

    /**
     * The crab's breeding: a ready male and female walk together and court face to face, he standing his courting
     * distance from her, both in their courting clips; then she carries 4 to 8 eggs under her flap (drawn berried) and
     * he none; when they hatch the babies ride on her back, crab-baby sized, then scatter off her and live on.
     */
    @GameTest(template = "arena", timeoutTicks = 2000, batch = "breeding")
    public static void crabsCourtFaceToFaceAndTheBabiesRideOnTheirMother(GameTestHelper helper) {
        breedingPond(helper);
        clearLeftovers(helper, SavannaEntities.CRAB.get());
        CrabEntity male = grown(helper, SavannaEntities.CRAB.get(), "male", new BlockPos(P0 + 3, BED + 1, P0 + 3));
        CrabEntity female = grown(helper, SavannaEntities.CRAB.get(), "female", new BlockPos(P0 + 8, BED + 1, P0 + 7));
        long started = helper.getLevel().getGameTime();
        int[] stage = {0};
        long[] since = {0};
        double[] nearest = {Double.MAX_VALUE};
        int[] eggs = {0};
        helper.runAtTickTime(1990, () -> helper.fail("crabs stuck at stage " + stage[0] + ": male ready " + male.life().readyToBreed()
                + " doing " + male.doing() + " at " + helper.relativePos(male.blockPosition()) + "; female ready "
                + female.life().readyToBreed() + " doing " + female.doing() + " eggs " + female.eggsUnderFlap() + " at "
                + helper.relativePos(female.blockPosition()) + " riders " + female.getPassengers().size()
                + "; closest to the courting distance " + nearest[0]));
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            switch (stage[0]) {
                case 0 -> {                         // they court face to face
                    if (male.doing() == CrabEntity.COURT && female.doing() == CrabEntity.COURT) {
                        double want = 0.65 * (male.getScale() + female.getScale()) / 2;
                        nearest[0] = Math.min(nearest[0], Math.abs(male.distanceTo(female) - want));
                    }
                    if (female.eggsUnderFlap() > 0) {
                        com.antcolonies.ColonyFrameworkMod.LOGGER.info("[life] crabs mated after {} ticks, closest to the courting distance {}",
                                now - started, nearest[0]);
                        helper.assertTrue(now - started >= female.life().numbers().courtshipTicks(), "they mated after only " + (now - started) + " ticks");
                        helper.assertTrue(nearest[0] < 0.12, "he never stood his courting distance from her (off by " + nearest[0] + ")");
                        eggs[0] = female.eggsUnderFlap();
                        helper.assertTrue(eggs[0] >= 4 && eggs[0] <= 8, eggs[0] + " eggs under her flap (4 to 8 wanted)");
                        helper.assertTrue(female.isBerried() && !male.isBerried() && male.eggsUnderFlap() == 0, "the wrong one carries the eggs");
                        female.ageEggs(female.life().numbers().hatchTicks());
                        stage[0] = 1;
                    }
                }
                case 1 -> {                         // the babies ride on her back
                    if (!female.getPassengers().isEmpty()) {
                        helper.assertTrue(female.getPassengers().size() == eggs[0], female.getPassengers().size() + " babies on her back from " + eggs[0] + " eggs");
                        for (net.minecraft.world.entity.Entity e : female.getPassengers()) {
                            CrabEntity baby = (CrabEntity) e;
                            double share = baby.getAttributeBaseValue(Attributes.SCALE) / baby.life().ownSize();
                            helper.assertTrue(baby.life().isYoung() && Math.abs(share - 0.15) < 0.01, "a baby crab at " + share + " of grown (0.15 wanted)");
                            helper.assertTrue(baby.getY() > female.getY(), "a baby rides below its mother's back");
                        }
                        helper.assertTrue(!female.isBerried() && female.eggsUnderFlap() == 0, "her eggs still under her flap with the babies out");
                        stage[0] = 2;
                        since[0] = now;
                    }
                }
                case 2 -> {                         // and scatter
                    if (female.getPassengers().isEmpty()) {
                        List<CrabEntity> babies = helper.getLevel().getEntitiesOfClass(CrabEntity.class, female.getBoundingBox().inflate(6),
                                c -> c.isAlive() && c.life().isYoung());
                        helper.assertTrue(babies.size() == eggs[0], babies.size() + " babies scattered from " + eggs[0]);
                        helper.assertTrue(now - since[0] >= 55, "the babies left her back after only " + (now - since[0]) + " ticks");
                        helper.succeed();
                    } else if (now - since[0] > 140) {
                        helper.fail("the babies still ride her after 7 s");
                    }
                }
                default -> {
                }
            }
        });
    }

    /** Room to breed: with as many crabs round as the local limit, a ready pair never courts. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "breeding_full")
    public static void noCrabCourtshipWhileTheRiverIsFull(GameTestHelper helper) {
        breedingPond(helper);
        CrabEntity male = grown(helper, SavannaEntities.CRAB.get(), "male", new BlockPos(P0 + 3, BED + 1, P0 + 3));
        CrabEntity female = grown(helper, SavannaEntities.CRAB.get(), "female", new BlockPos(P0 + 4, BED + 1, P0 + 3));
        int limit = male.life().numbers().localLimit();
        for (int i = 2; i < limit; i++) {
            grown(helper, SavannaEntities.CRAB.get(), "male", new BlockPos(P0 + 2 + i % 9, BED + 1, P0 + 8 + i / 9 * 2));
        }
        boolean[] courted = {false};
        helper.onEachTick(() -> courted[0] |= male.doing() == CrabEntity.COURT || female.doing() == CrabEntity.COURT);
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(!courted[0] && female.eggsUnderFlap() == 0, "a pair courted with " + limit + " crabs round");
            helper.succeed();
        });
    }

    /**
     * The catfish's breeding: a ready pair stays apart by day or in the dry and the ripe female is drawn plump; on a
     * rainy night they meet, she leads him to the shallows and he curls round her tail; then she scatters 6 to 10 eggs
     * over the algae mats, a few to a mat; a mat's eggs, old enough and watched, hatch into dark young catfish a fifth of
     * the size that hide near the mat, and the mat is the same algae again.
     */
    @GameTest(template = "arena", timeoutTicks = 3600, batch = "breeding")
    public static void catfishSpawnOnARainyNightAndScatterEggsOnTheAlgae(GameTestHelper helper) {
        breedingPond(helper);
        clearLeftovers(helper, SavannaEntities.CATFISH.get());
        for (int x = P0 + 2; x <= P1 - 1; x += 2) {
            for (int z = P0 + 1; z <= P1 - 1; z += 3) {
                helper.setBlock(new BlockPos(x, BED + 1, z), SavannaBlocks.ALGAE.get().whole(com.antcolonies.savanna.block.AlgaeBlock.LUSH));
            }
        }
        com.antcolonies.savanna.animal.CatfishEntity male = grown(helper, SavannaEntities.CATFISH.get(), "male", new BlockPos(P0 + 3, BED + 2, P0 + 3));
        com.antcolonies.savanna.animal.CatfishEntity female = grown(helper, SavannaEntities.CATFISH.get(), "female", new BlockPos(P1 - 2, BED + 2, P1 - 2));
        // by day, in the rain, and by night, in the dry: not ready; a rainy night: ready
        com.antcolonies.gametest.TestClock.still(helper, 12.0);
        AnimalLife.setRainForTests(true);
        helper.assertTrue(!female.life().readyToBreed(), "a catfish ready to spawn by day");
        com.antcolonies.gametest.TestClock.still(helper, 23.0);
        AnimalLife.setRainForTests(false);
        helper.assertTrue(!female.life().readyToBreed(), "a catfish ready to spawn in the dry");
        AnimalLife.setRainForTests(true);
        helper.assertTrue(female.life().readyToBreed() && male.life().readyToBreed(), "a grown catfish not ready on a rainy night");
        long started = helper.getLevel().getGameTime();
        int[] stage = {0};
        boolean[] curled = {false};
        long[] since = {0};
        helper.runAtTickTime(3590, () -> {
            AnimalLife.setRainForTests(null);
            helper.fail("catfish stuck at stage " + stage[0] + ": female ready " + female.life().readyToBreed() + " partner "
                    + female.life().partner() + " eggs " + female.eggsToScatter() + " at " + helper.relativePos(female.blockPosition())
                    + " shallows " + female.shallows() + "; male partner " + male.life().partner() + " curled " + curled[0]
                    + " at " + helper.relativePos(male.blockPosition()) + " d " + male.distanceTo(female));
        });
        helper.onEachTick(() -> {
            curled[0] |= male.isCourting();
            switch (stage[0]) {
                case 0 -> {
                    helper.assertTrue(female.isPlump() || female.tickCount < 25, "a ripe female not drawn plump");
                    helper.assertTrue(!male.isPlump(), "a male drawn plump");
                    if (female.eggsToScatter() > 0) {
                        AnimalLife.setRainForTests(null);
                        helper.assertTrue(curled[0], "he never curled round her tail");
                        long took = helper.getLevel().getGameTime() - started;
                        helper.assertTrue(took >= female.life().numbers().courtshipTicks(), "they mated after only " + took + " ticks");
                        com.antcolonies.ColonyFrameworkMod.LOGGER.info("[life] catfish mated after {} ticks, she leading to {}", took, female.shallows());
                        int eggs = female.eggsToScatter();
                        helper.assertTrue(eggs >= 6 && eggs <= 10, eggs + " eggs (6 to 10 wanted)");
                        since[0] = eggs;
                        stage[0] = 1;
                    }
                }
                case 1 -> {
                    if (female.eggsToScatter() == 0) {
                        List<BlockPos> mats = new java.util.ArrayList<>();
                        int laid = 0;
                        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(P0, BED + 1, P0), new BlockPos(P1, BED + 1, P1))) {
                            if (helper.getBlockState(rel).is(SavannaBlocks.CATFISH_EGGS.get())) {
                                BlockPos at = helper.absolutePos(rel);
                                mats.add(at);
                                int n = ((EggClutchBlockEntity) helper.getLevel().getBlockEntity(at)).count();
                                helper.assertTrue(n >= 1 && n <= 4, n + " eggs on one mat (1 to 4 wanted)");
                                laid += n;
                            }
                        }
                        helper.assertTrue(mats.size() >= 2 && laid == since[0], laid + " eggs on " + mats.size() + " mats from " + since[0]);
                        catfishHatchChecks(helper, mats.get(0));
                        helper.succeed();
                    }
                }
                default -> {
                }
            }
        });
    }

    private static void catfishHatchChecks(GameTestHelper helper, BlockPos mat) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.level.block.state.BlockState before = level.getBlockState(mat);
        int eggs = ((EggClutchBlockEntity) level.getBlockEntity(mat)).count();
        try {
            age(level, mat, AnimalLives.get(SavannaEntities.CATFISH.get()).hatchTicks());
            com.antcolonies.colony.Watchers.setPretend(List.of(net.minecraft.world.phys.Vec3.atCenterOf(mat)));
            tick(level, mat);
            net.minecraft.world.level.block.state.BlockState after = level.getBlockState(mat);
            helper.assertTrue(after.is(SavannaBlocks.ALGAE.get())
                    && after.getValue(com.antcolonies.savanna.block.AlgaeBlock.GROWTH) == before.getValue(com.antcolonies.savanna.block.AlgaeBlock.GROWTH),
                    "a hatched mat is not the same algae again: " + after);
            List<com.antcolonies.savanna.animal.CatfishEntity> young = level.getEntitiesOfClass(com.antcolonies.savanna.animal.CatfishEntity.class,
                    new net.minecraft.world.phys.AABB(mat).inflate(1.5), c -> c.life().isYoung());
            helper.assertTrue(young.size() == eggs, young.size() + " young from " + eggs + " eggs");
            for (com.antcolonies.savanna.animal.CatfishEntity c : young) {
                double share = c.getAttributeBaseValue(Attributes.SCALE) / c.life().ownSize();
                helper.assertTrue(Math.abs(share - 0.2) < 0.01, "a young catfish at " + share + " of grown (0.2 wanted)");
            }
        } finally {
            com.antcolonies.colony.Watchers.setPretend(List.of());
        }
    }

    /**
     * Eaten eggs: a hungry tilapia goes for catfish eggs on a mat before the plain algae round it and bites some off, and
     * a hungry crab picks at a snail clutch on the bed; a clutch eaten to nothing is gone (the mat left as algae).
     */
    @GameTest(template = "arena", timeoutTicks = 900, batch = "breeding")
    public static void tilapiaAndCrabsEatTheEggs(GameTestHelper helper) {
        breedingPond(helper);
        ServerLevel level = helper.getLevel();
        com.antcolonies.savanna.block.AlgaeBlock algae = SavannaBlocks.ALGAE.get();
        BlockPos mat = helper.absolutePos(new BlockPos(P1 - 2, BED + 1, P1 - 2));
        for (BlockPos rel : List.of(new BlockPos(P1 - 4, BED + 1, P1 - 2), new BlockPos(P1 - 2, BED + 1, P1 - 4), new BlockPos(P1 - 2, BED + 1, P1 - 2))) {
            helper.setBlock(rel, algae.whole(com.antcolonies.savanna.block.AlgaeBlock.LUSH));
        }
        com.antcolonies.savanna.block.CatfishEggsBlock.lay(level, mat, SavannaEntities.CATFISH.get(), 3);
        BlockPos clutch = helper.absolutePos(new BlockPos(P0 + 3, BED + 1, P0 + 3));
        com.antcolonies.savanna.block.SnailEggsBlock.lay(level, clutch, net.minecraft.core.Direction.UP, SavannaEntities.SNAIL.get(), 4);
        helper.assertTrue(level.getBlockState(mat).is(SavannaBlocks.CATFISH_EGGS.get())
                && level.getBlockState(clutch).is(SavannaBlocks.SNAIL_EGGS.get()), "the eggs were not laid for the test");
        TilapiaEntity fish = grown(helper, SavannaEntities.TILAPIA.get(), "female", new BlockPos(P1 - 3, BED + 2, P1 - 5));
        fish.makeHungry();
        CrabEntity crab = grown(helper, SavannaEntities.CRAB.get(), "male", new BlockPos(P0 + 4, BED + 1, P0 + 3));
        crab.makeHungry();
        helper.succeedWhen(() -> {
            int matEggs = level.getBlockEntity(mat) instanceof EggClutchBlockEntity e ? e.count() : 0;
            int snailEggs = level.getBlockEntity(clutch) instanceof EggClutchBlockEntity e ? e.count() : 0;
            helper.assertTrue(matEggs < 3, "the tilapia has not eaten the catfish eggs yet (" + matEggs + " left)");
            helper.assertTrue(snailEggs < 4, "the crab has not eaten the snail eggs yet (" + snailEggs + " left)");
            if (matEggs == 0) {
                helper.assertTrue(level.getBlockState(mat).is(algae), "a mat eaten bare of eggs is not algae again");
            }
        });
    }

    // --- breeding (step 4: the crocodile) --------------------------------------------------------------------------------

    /** The crocodile's river: water 2 deep over silt, x 2 to 24, then a golden sand bank a block over the water, x 25 to 34. */
    private static final int CROC_WATER_TO = 24, CROC_BANK_TO = 34;

    private static void crocRiver(GameTestHelper helper) {
        net.minecraft.world.level.block.Block sand = SavannaBlocks.GOLDEN_SAND.dry().get();
        for (int x = 1; x <= CROC_BANK_TO + 1; x++) {
            for (int z = 1; z <= 15; z++) {
                boolean wall = x == 1 || x == CROC_BANK_TO + 1 || z == 1 || z == 15;
                helper.setBlock(new BlockPos(x, BED - 1, z), Blocks.STONE);
                for (int y = BED; y <= WATER_TOP + 3; y++) {
                    BlockPos at = new BlockPos(x, y, z);
                    if (wall) {
                        helper.setBlock(at, y <= WATER_TOP + 2 ? Blocks.STONE : Blocks.AIR);
                    } else if (x <= CROC_WATER_TO) {
                        helper.setBlock(at, y == BED ? SavannaBlocks.SILT.get() : y <= WATER_TOP ? Blocks.WATER : Blocks.AIR);
                    } else {
                        helper.setBlock(at, y <= WATER_TOP ? sand : Blocks.AIR);
                    }
                }
            }
        }
    }

    /** A grown crocodile of this sex in the crocodile's river, never hungry (it hunts nothing in the test). */
    private static com.antcolonies.savanna.animal.CrocodileEntity croc(GameTestHelper helper, String sex, BlockPos at) {
        com.antcolonies.savanna.animal.CrocodileEntity c = grown(helper, SavannaEntities.CROCODILE.get(), sex, at);
        c.hungerIn = 1_000_000;
        return c;
    }

    /**
     * The crocodile's breeding, whole: a ready male swims to a ready female and courts her (his head slaps and rumbles,
     * her snout raised, then both roll), she comes out onto the sand bank, digs, lays 6 to 10 eggs and covers them (a
     * closed nest a block or two back from the water, on the sand); old enough and watched, the eggs call, she digs them
     * out (as many young as the local limit has room for), carries them to the water in her mouth and keeps them in a
     * crèche, every young one in the water there, half-block banded hatchlings that know her.
     */
    @GameTest(template = "arena", timeoutTicks = 6000, batch = "breeding_croc")
    public static void crocodilesCourtNestAndTheMotherCarriesHerYoungToTheWater(GameTestHelper helper) {
        crocRiver(helper);
        clearLeftovers(helper, SavannaEntities.CROCODILE.get());
        ServerLevel level = helper.getLevel();
        var male = croc(helper, "male", new BlockPos(8, BED + 1, 8));
        var female = croc(helper, "female", new BlockPos(17, BED + 1, 8));
        int[] stage = {0};
        boolean[] seen = new boolean[4];       // slap, rumble, snout up, roll
        int[] eggs = {0};
        int[] carried = {0, 0};             // most at once, now
        BlockPos[] nest = {null};
        int[] room = {0};
        long started = level.getGameTime();
        helper.runAtTickTime(5990, () -> {
            com.antcolonies.colony.Watchers.setPretend(List.of());
            helper.fail("crocodiles stuck at stage " + stage[0] + ": seen " + java.util.Arrays.toString(seen) + "; female gravid "
                    + female.gravid + " nest " + female.nestAt() + " show " + female.show() + " carrying " + female.carrying()
                    + " at " + helper.relativePos(female.blockPosition()) + " in water " + female.isInWater()
                    + "; male ready " + male.life().readyToBreed() + " show " + male.show() + " at " + helper.relativePos(male.blockPosition()));
        });
        helper.onEachTick(() -> {
            switch (stage[0]) {
                case 0 -> {                         // the courtship
                    seen[0] |= male.show() == com.antcolonies.savanna.animal.CrocodileEntity.SHOW_SLAP;
                    seen[1] |= male.show() == com.antcolonies.savanna.animal.CrocodileEntity.SHOW_RUMBLE;
                    seen[2] |= female.show() == com.antcolonies.savanna.animal.CrocodileEntity.SHOW_SNOUT_UP;
                    seen[3] |= female.show() == com.antcolonies.savanna.animal.CrocodileEntity.SHOW_ROLL && male.show() == com.antcolonies.savanna.animal.CrocodileEntity.SHOW_ROLL;
                    if (female.gravid) {
                        com.antcolonies.ColonyFrameworkMod.LOGGER.info("[life] crocodiles mated after {} ticks", level.getGameTime() - started);
                        helper.assertTrue(seen[0] && seen[1] && seen[2] && seen[3], "a courtship part was missing (slap, rumble, snout up, roll): "
                                + java.util.Arrays.toString(seen));
                        helper.assertTrue(!male.gravid && !male.life().readyToBreed(), "the male carries eggs or is ready again at once");
                        male.discard();                 // his part done (he would eat the hatchlings: real, but not this test)
                        stage[0] = 1;
                    }
                }
                case 1 -> {                         // the nest: dug, laid, covered on the sand bank
                    BlockPos at = female.nestAt();
                    if (at != null && level.getBlockState(at).is(SavannaBlocks.CROCODILE_NEST.get())
                            && !level.getBlockState(at).getValue(com.antcolonies.savanna.block.CrocodileNestBlock.OPEN)) {
                        // (absolute places compared: relativePos does not turn back a test placed turned round)
                        BlockPos rel = null;
                        for (int x = CROC_WATER_TO + 2; x <= CROC_WATER_TO + 3 && rel == null; x++) {
                            for (int z = 2; z <= 14 && rel == null; z++) {
                                if (helper.absolutePos(new BlockPos(x, WATER_TOP + 1, z)).equals(at)) {
                                    rel = new BlockPos(x, WATER_TOP + 1, z);
                                }
                            }
                        }
                        helper.assertTrue(rel != null, "the nest is not on the sand bank a block or two back from the water: " + at);
                        helper.assertTrue(level.getBlockState(at.below()).is(SavannaBlocks.GOLDEN_SAND.dry().get()), "the nest is not on sand");
                        EggClutchBlockEntity clutch = (EggClutchBlockEntity) level.getBlockEntity(at);
                        eggs[0] = clutch.count();
                        helper.assertTrue(eggs[0] >= 6 && eggs[0] <= 10, eggs[0] + " eggs (6 to 10 wanted)");
                        helper.assertTrue(!female.gravid, "she still carries her eggs after laying");
                        com.antcolonies.ColonyFrameworkMod.LOGGER.info("[life] the crocodile nested at {} with {} eggs after {} ticks", rel, eggs[0],
                                level.getGameTime() - started);
                        nest[0] = at;
                        age(level, at, female.life().numbers().hatchTicks());
                        com.antcolonies.colony.Watchers.setPretend(List.of(net.minecraft.world.phys.Vec3.atCenterOf(at)));
                        tick(level, at);            // the eggs call
                        helper.assertTrue(com.antcolonies.savanna.block.CrocodileNestBlock.hatching(level.getBlockState(at)),
                                "the eggs did not call with their mother near");
                        stage[0] = 2;
                    }
                }
                case 2 -> {                         // she digs them out and carries them
                    if (female.carrying() != carried[1]) {
                        com.antcolonies.ColonyFrameworkMod.LOGGER.info("[life] the mother carries {} after {} ticks", female.carrying(),
                                level.getGameTime() - started);
                        carried[1] = female.carrying();
                    }
                    carried[0] = Math.max(carried[0], female.carrying());
                    List<com.antcolonies.savanna.animal.CrocodileEntity> young = level.getEntitiesOfClass(com.antcolonies.savanna.animal.CrocodileEntity.class,
                            new net.minecraft.world.phys.AABB(nest[0]).inflate(24),
                            c -> c.isAlive() && c.life().isYoung() && female.getUUID().equals(c.motherId()));
                    if (level.getBlockState(nest[0]).is(SavannaBlocks.CROCODILE_NEST.get())) {
                        // the room the nest really has, read while it stands (a crocodile of a test beside this one, within
                        // room to breed's 48 blocks, takes a place: BUGS 268, 303), the hatchlings let out to fit it
                        room[0] = com.antcolonies.animal.RoomToBreed.leftAt(level, nest[0], SavannaEntities.CROCODILE.get(),
                                female.life().numbers().localLimit());
                    }
                    int expect = Math.min(eggs[0], room[0]);
                    if (!level.getBlockState(nest[0]).is(SavannaBlocks.CROCODILE_NEST.get()) && young.size() + female.carrying() < expect) {
                        helper.fail((young.size() + female.carrying()) + " hatchlings from " + eggs[0] + " eggs (" + expect + " wanted: room for that many)");
                    }
                    if (female.carrying() == 0 && young.size() == expect && young.stream().allMatch(y -> y.isInWater() && y.crecheAt() != null)) {
                        com.antcolonies.colony.Watchers.setPretend(List.of());
                        helper.assertTrue(carried[0] > 0, "she never carried a hatchling in her mouth");
                        helper.assertTrue(female.crecheAt() != null, "she keeps no crèche");
                        for (var y : young) {
                            helper.assertTrue(female.getUUID().equals(y.motherId()), "a hatchling does not know its mother");
                            double size = y.getScale() * 7.5;
                            helper.assertTrue(size > 0.3 && size < 0.7, "a hatchling " + size + " blocks long (about half a block wanted)");
                        }
                        com.antcolonies.ColonyFrameworkMod.LOGGER.info("[life] {} hatchlings carried to the water, at most {} at once, after {} ticks",
                                young.size(), carried[0], level.getGameTime() - started);
                        helper.succeed();
                    }
                }
                default -> {
                }
            }
        });
    }

    /** A mother guarding her nest sees off a monitor coming near it: she hisses and charges, and bites it or it runs. */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "breeding_croc")
    public static void aCrocodileMotherSeesOffAMonitorFromHerNest(GameTestHelper helper) {
        crocRiver(helper);
        ServerLevel level = helper.getLevel();
        BlockPos nest = helper.absolutePos(new BlockPos(CROC_WATER_TO + 2, WATER_TOP + 1, 8));
        helper.assertTrue(com.antcolonies.savanna.block.CrocodileNestBlock.lay(level, nest, SavannaEntities.CROCODILE.get(), 6), "no nest laid");
        com.antcolonies.savanna.block.CrocodileNestBlock.cover(level, nest, false);
        var female = croc(helper, "female", new BlockPos(CROC_WATER_TO + 3, WATER_TOP + 1, 11));
        female.guardNest(nest);
        var monitor = helper.spawn(SavannaEntities.MONITOR.get(), new BlockPos(CROC_WATER_TO + 5, WATER_TOP + 1, 8));
        float full = monitor.getHealth();
        boolean[] hissed = {false};
        helper.onEachTick(() -> hissed[0] |= female.show() == com.antcolonies.savanna.animal.CrocodileEntity.SHOW_HISS);
        helper.succeedWhen(() -> {
            helper.assertTrue(hissed[0], "she has not hissed at the monitor");
            // seen off: bitten, or driven well away from the nest (a monitor hissed at often runs, as a real one would)
            double fled = monitor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(nest));
            helper.assertTrue(female.guardBites > 0 || fled > 9 * 9, "she has not seen the monitor off: she at " + female.position() + " show " + female.show()
                    + " target " + female.getTarget() + "; the monitor at " + monitor.position() + " health " + monitor.getHealth() + " of " + full
                    + "; the nest at " + nest);
            helper.assertTrue(female.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(nest)) < 10 * 10, "she left her nest far behind");
        });
    }

    /**
     * A player breaking a crocodile nest (the user, 2026-11-23): the first hit scrapes the sand cap off and leaves the pit
     * open with every egg still in it; a hit on a nest whose eggs are calling lets the hatchlings out; only a hit on the
     * open pit with quiet eggs breaks it.
     */
    @GameTest(template = "arena", timeoutTicks = 40)
    public static void breakingACrocodileNestsCapShowsTheEggsOrLetsTheHatchlingsOut(GameTestHelper helper) {
        crocRiver(helper);
        ServerLevel level = helper.getLevel();
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var noFluid = net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
        BlockPos eggs = helper.absolutePos(new BlockPos(CROC_WATER_TO + 2, WATER_TOP + 1, 4));
        helper.assertTrue(com.antcolonies.savanna.block.CrocodileNestBlock.lay(level, eggs, SavannaEntities.CROCODILE.get(), 6), "no nest laid");
        com.antcolonies.savanna.block.CrocodileNestBlock.cover(level, eggs, false);
        var covered = level.getBlockState(eggs);
        helper.assertFalse(covered.onDestroyedByPlayer(level, eggs, player, false, noFluid), "the first hit took the nest away");
        var opened = level.getBlockState(eggs);
        helper.assertTrue(opened.is(SavannaBlocks.CROCODILE_NEST.get()) && opened.getValue(com.antcolonies.savanna.block.CrocodileNestBlock.OPEN),
                "the cap is not off: " + opened);
        helper.assertTrue(level.getBlockEntity(eggs) instanceof EggClutchBlockEntity clutch && clutch.count() == 6, "eggs lost with the cap");
        helper.assertTrue(opened.onDestroyedByPlayer(level, eggs, player, false, noFluid) && level.getBlockState(eggs).isAir(),
                "a hit on the open pit did not break it");

        BlockPos calling = helper.absolutePos(new BlockPos(CROC_WATER_TO + 2, WATER_TOP + 1, 12));
        helper.assertTrue(com.antcolonies.savanna.block.CrocodileNestBlock.lay(level, calling, SavannaEntities.CROCODILE.get(), 3), "no second nest laid");
        com.antcolonies.savanna.block.CrocodileNestBlock.cover(level, calling, false);
        level.setBlock(calling, level.getBlockState(calling).setValue(com.antcolonies.savanna.block.CrocodileNestBlock.CHIRPS, 1), 3);
        level.getBlockState(calling).onDestroyedByPlayer(level, calling, player, false, noFluid);
        var young = level.getEntitiesOfClass(com.antcolonies.savanna.animal.CrocodileEntity.class, new net.minecraft.world.phys.AABB(calling).inflate(2),
                c -> c.life().isYoung());
        helper.assertTrue(!young.isEmpty() && young.size() <= 3, "hatchlings out: " + young.size() + " (want 1 to 3)");
        helper.assertTrue(!level.getBlockState(calling).is(SavannaBlocks.CROCODILE_NEST.get()), "the hatched nest is still there");
        helper.succeed();
    }

    /**
     * A crocodile's growing (the life file's stages): a hatchling about half a block, slower than a grown one; a teen
     * from 6 days at a third of grown; grown at 14 days; and a crocodile saved before step 4 (its age 0, when they were
     * born grown) loads grown, not as a hatchling.
     */
    @GameTest(template = "arena", timeoutTicks = 40)
    public static void crocodilesGrowThroughHatchlingAndTeenAndOldOnesLoadGrown(GameTestHelper helper) {
        var croc = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(6, 2, 6));
        LifeNumbers n = croc.life().numbers();
        croc.life().setUp(true, croc.getRandom());
        croc.life().setAge(0);
        CompoundTag tag = croc.saveWithoutId(new CompoundTag());
        croc.load(tag);         // its pace is set from its size as it loads
        helper.assertTrue(croc.life().stage() == com.antcolonies.animal.LifeStage.YOUNG, "a hatched crocodile is not young");
        double length = croc.getScale() * 7.5;
        helper.assertTrue(length > 0.3 && length < 0.7, "a hatchling " + length + " blocks long");
        // its pace goes as its speed setting squared (Minecraft counts the setting twice): about a third of a grown one's
        double pace = Math.pow(croc.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) / 0.3, 2);
        helper.assertTrue(pace > 0.2 && pace < 0.45, "a hatchling's pace " + pace + " of a grown crocodile's (about a third)");
        croc.life().setAge(n.youngTicks());
        helper.assertTrue(croc.life().stage() == com.antcolonies.animal.LifeStage.TEEN, "not a teen after its young days");
        double share = croc.getAttributeBaseValue(Attributes.SCALE) / croc.life().ownSize();
        helper.assertTrue(Math.abs(share - 0.35) < 0.01, "a new teen at " + share + " of grown (0.35 wanted)");
        croc.life().setAge(n.grownAge());
        helper.assertTrue(croc.life().stage() == com.antcolonies.animal.LifeStage.GROWN, "not grown after its teen days");
        helper.assertTrue(Math.abs(n.grownAge() - 14 * LifeNumbers.DAY) < 2, "grown at " + n.grownAge() / (double) LifeNumbers.DAY + " days (14 wanted)");
        // saved before step 4: grown, its age 0
        CompoundTag old = new CompoundTag();
        CompoundTag life = new CompoundTag();
        life.putString("Sex", "female");
        life.putInt("Age", 0);
        life.putFloat("OwnSize", 0.9F);
        old.put("Life", life);
        croc.life().load(old);
        helper.assertTrue(croc.life().stage() == com.antcolonies.animal.LifeStage.GROWN, "a crocodile saved before step 4 loaded as a " + croc.life().stage());
        helper.succeed();
    }

    // --- the bullfrog's breeding (the river tie-up part B, 2026-11-23) ---------------------------------------------------

    /**
     * The bullfrog's breeding: in the rain a ready female comes to a calling male; he climbs onto her back and clasps her
     * while she carries him; she lays a floating mat of 8 to 12 eggs at the top of the shallow water, they part, and he
     * stays to guard the eggs.
     */
    @GameTest(template = "arena", timeoutTicks = 2400, batch = "breeding_frog")
    public static void bullfrogsClaspInTheRainAndSheLaysEggsHeGuards(GameTestHelper helper) {
        breedingPond(helper);
        clearLeftovers(helper, SavannaEntities.BULLFROG.get());
        clearLeftovers(helper, SavannaEntities.TADPOLE.get());
        com.antcolonies.savanna.animal.BullfrogEntity male = grown(helper, SavannaEntities.BULLFROG.get(), "male", new BlockPos(P0 + 2, WATER_TOP, P0 + 2));
        com.antcolonies.savanna.animal.BullfrogEntity female = grown(helper, SavannaEntities.BULLFROG.get(), "female", new BlockPos(P1 - 2, WATER_TOP, P1 - 2));
        male.setPersistenceRequired();
        female.setPersistenceRequired();
        helper.assertTrue(!male.seeksMate() && female.seeksMate(), "the male should wait, calling, and the female come to him");
        AnimalLife.setRainForTests(false);
        helper.assertTrue(!female.life().readyToBreed(), "a female ready to breed in the dry");
        AnimalLife.setRainForTests(true);
        boolean[] clasped = {false};
        helper.onEachTick(() -> {
            male.calledNow();       // he calls in the rain (CallGoal); here every tick, so the test needs no wait for it
            clasped[0] |= male.getVehicle() == female && male.action() == com.antcolonies.savanna.animal.BullfrogEntity.CLASP;
        });
        helper.runAtTickTime(2390, () -> {
            AnimalLife.setRainForTests(null);
            helper.fail("the frogs never bred: clasped " + clasped[0] + ", her partner " + female.life().partner() + ", laid at " + female.lastClutch()
                    + ", she at " + helper.relativePos(female.blockPosition()) + ", he at " + helper.relativePos(male.blockPosition()));
        });
        helper.succeedWhen(() -> {
            BlockPos eggs = female.lastClutch();
            helper.assertTrue(clasped[0], "he never clasped her");
            helper.assertTrue(eggs != null, "no eggs laid yet");
            helper.assertTrue(helper.getLevel().getBlockState(eggs).is(SavannaBlocks.FROG_EGGS.get()), "no eggs at " + helper.relativePos(eggs));
            int count = ((EggClutchBlockEntity) helper.getLevel().getBlockEntity(eggs)).count();
            helper.assertTrue(count >= 8 && count <= 12, count + " eggs (8 to 12 wanted)");
            helper.assertTrue(!male.isPassenger() && !female.isVehicle(), "the pair never parted");
            helper.assertTrue(male.guards(eggs), "he does not guard the eggs");
            AnimalLife.setRainForTests(null);
        });
    }

    /**
     * The eggs hatch into one school (not before their day, and only when watched): each egg a tadpole just hatched,
     * knowing its father; the father, hungry, guards the school and never eats one of his own.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "breeding_frog")
    public static void frogEggsHatchIntoASchoolTheFatherGuardsAndNeverEats(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        breedingPond(helper);
        clearLeftovers(helper, SavannaEntities.BULLFROG.get());
        clearLeftovers(helper, SavannaEntities.TADPOLE.get());
        com.antcolonies.savanna.animal.BullfrogEntity father = grown(helper, SavannaEntities.BULLFROG.get(), "male", new BlockPos(P0 + 4, WATER_TOP, P0 + 5));
        father.setPersistenceRequired();
        BlockPos eggs = helper.absolutePos(new BlockPos(P0 + 6, WATER_TOP, P0 + 5));
        helper.assertTrue(com.antcolonies.savanna.block.FrogEggsBlock.lay(level, eggs, SavannaEntities.BULLFROG.get(), 10), "eggs could not be laid on the pond");
        father.guard(eggs);
        List<com.antcolonies.savanna.animal.TadpoleEntity> school = new java.util.ArrayList<>();
        try {
            com.antcolonies.colony.Watchers.setPretend(List.of(net.minecraft.world.phys.Vec3.atCenterOf(eggs)));
            tick(level, eggs);
            helper.assertTrue(level.getBlockState(eggs).is(SavannaBlocks.FROG_EGGS.get()), "the eggs hatched before their day");
            age(level, eggs, AnimalLives.get(SavannaEntities.BULLFROG.get()).hatchTicks());
            tick(level, eggs);
        } finally {
            com.antcolonies.colony.Watchers.setPretend(List.of());
        }
        helper.assertTrue(level.getBlockState(eggs).is(Blocks.WATER), "the hatched eggs are not plain water again: " + level.getBlockState(eggs));
        school.addAll(level.getEntitiesOfClass(com.antcolonies.savanna.animal.TadpoleEntity.class, new net.minecraft.world.phys.AABB(eggs).inflate(2)));
        helper.assertTrue(school.size() == 10, school.size() + " tadpoles from 10 eggs");
        for (var t : school) {
            t.setPersistenceRequired();
            helper.assertTrue(t.age() < 5 && father.isFatherOf(t), "a tadpole aged " + t.age() + ", its father " + t.father() + " (" + father.getUUID() + " wanted)");
        }
        father.makeHungry();
        boolean[] lunged = {false};
        helper.onEachTick(() -> lunged[0] |= father.action() == com.antcolonies.savanna.animal.BullfrogEntity.LUNGE);
        helper.runAfterDelay(300, () -> {
            long alive = school.stream().filter(t -> t.isAlive() && !t.isRemoved()).count();
            helper.assertTrue(alive == 10 && !lunged[0], "the father ate his own: " + alive + " of 10 left" + (lunged[0] ? ", he lunged" : ""));
            helper.assertTrue(father.guardSpot() != null, "the father does not guard his school");
            helper.succeed();
        });
    }

    /** A father guarding his eggs goes for a monitor that comes near them, puffs up and bites it (he does not flee it). */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "breeding_frog")
    public static void aGuardingFatherGoesForAMonitorNearHisEggs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        breedingPond(helper);
        clearLeftovers(helper, SavannaEntities.BULLFROG.get());
        com.antcolonies.savanna.animal.BullfrogEntity father = grown(helper, SavannaEntities.BULLFROG.get(), "male", new BlockPos(P0 + 8, WATER_TOP, P0 + 8));
        father.setPersistenceRequired();
        BlockPos eggs = helper.absolutePos(new BlockPos(P0 + 3, WATER_TOP, P0 + 8));
        com.antcolonies.savanna.block.FrogEggsBlock.lay(level, eggs, SavannaEntities.BULLFROG.get(), 10);
        father.guard(eggs);
        // the monitor wading on the shallow shelf at the edge, at the frog's level (one lying on the bed right under a
        // floating frog is out of his bite: he floats over it; not a meeting the game makes)
        var monitor = helper.spawn(SavannaEntities.MONITOR.get(), new BlockPos(P0, BED + 2, P0 + 8));
        monitor.setNoAi(true);
        monitor.setPersistenceRequired();
        float full = monitor.getHealth();
        boolean[] puffed = {false};
        int[] lunges = {0};
        double[] nearest = {99};
        byte[] last = {0};
        helper.onEachTick(() -> {
            byte a = father.action();
            puffed[0] |= a == com.antcolonies.savanna.animal.BullfrogEntity.PUFF;
            if (a == com.antcolonies.savanna.animal.BullfrogEntity.LUNGE && last[0] != a) {
                lunges[0]++;
            }
            last[0] = a;
            nearest[0] = Math.min(nearest[0], father.distanceTo(monitor));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(puffed[0], "the father never puffed up at the monitor (" + String.format("%.1f", father.distanceTo(monitor)) + " blocks off)");
            helper.assertTrue(monitor.getHealth() < full, "the father never bit the monitor (" + lunges[0] + " lunges, nearest "
                    + String.format("%.2f", nearest[0]) + ", the monitor " + String.format("%.2f", monitor.getBbWidth()) + " wide, the frog at "
                    + helper.relativePos(father.blockPosition()) + ", the monitor at " + helper.relativePos(monitor.blockPosition()) + ")");
        });
    }

    // --- frog spawn eaten (the river food web's step 2, 2026-11-23) ------------------------------------------------------
    // (a batch of their own: the father here counted against the breeding tests' room to breed, run beside them)

    /** How many eggs a frog egg mat holds now (0 once it is gone). */
    private static int frogEggs(GameTestHelper helper, BlockPos eggs) {
        return helper.getLevel().getBlockState(eggs).is(SavannaBlocks.FROG_EGGS.get())
                && helper.getLevel().getBlockEntity(eggs) instanceof EggClutchBlockEntity clutch ? clutch.count() : 0;
    }

    /** A frog egg mat no father guards is nibbled: one by a hungry tilapia, another by a hungry crab on the bed under it. */
    @GameTest(template = "arena", timeoutTicks = 800, batch = "frog_spawn_eaten")
    public static void tilapiaAndCrabsNibbleAnUnguardedFrogEggMat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        breedingPond(helper);
        clearLeftovers(helper, SavannaEntities.BULLFROG.get());
        BlockPos forFish = helper.absolutePos(new BlockPos(P0 + 3, WATER_TOP, P0 + 3));
        BlockPos forCrab = helper.absolutePos(new BlockPos(P1 - 2, WATER_TOP, P1 - 2));
        helper.assertTrue(com.antcolonies.savanna.block.FrogEggsBlock.lay(level, forFish, SavannaEntities.BULLFROG.get(), 10)
                && com.antcolonies.savanna.block.FrogEggsBlock.lay(level, forCrab, SavannaEntities.BULLFROG.get(), 10), "eggs could not be laid on the pond");
        var tilapia = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(P0 + 3, BED + 1, P0 + 5));
        tilapia.setPersistenceRequired();
        tilapia.makeHungry();
        var crab = helper.spawn(SavannaEntities.CRAB.get(), new BlockPos(P1 - 2, BED + 1, P1 - 4));
        crab.setPersistenceRequired();
        crab.makeHungry();
        helper.succeedWhen(() -> {
            helper.assertTrue(frogEggs(helper, forFish) < 10, "the tilapia never nibbled its egg mat (10 eggs left; it is "
                    + String.format("%.1f", tilapia.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(forFish))) + " blocks off)");
            helper.assertTrue(frogEggs(helper, forCrab) < 10, "the crab never nibbled its egg mat (10 eggs left; it is "
                    + String.format("%.1f", crab.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(forCrab))) + " blocks off)");
        });
    }

    /** A father guarding his eggs puffs up and lunges at a tilapia come within 2 blocks of them to eat them, and bites it. */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "frog_spawn_eaten")
    public static void aGuardingFatherLungesAtATilapiaAtHisEggs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        breedingPond(helper);
        clearLeftovers(helper, SavannaEntities.BULLFROG.get());
        com.antcolonies.savanna.animal.BullfrogEntity father = grown(helper, SavannaEntities.BULLFROG.get(), "male", new BlockPos(P0 + 9, WATER_TOP, P0 + 8));
        father.setPersistenceRequired();
        BlockPos eggs = helper.absolutePos(new BlockPos(P0 + 5, WATER_TOP, P0 + 8));
        com.antcolonies.savanna.block.FrogEggsBlock.lay(level, eggs, SavannaEntities.BULLFROG.get(), 10);
        father.guard(eggs);
        // the tilapia held still at the eggs, at the floating frog's level (the meeting under test, not its escape)
        var tilapia = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(P0 + 5, WATER_TOP, P0 + 7));
        tilapia.setNoAi(true);
        tilapia.setNoGravity(true);
        tilapia.setPersistenceRequired();
        float full = tilapia.getHealth();
        boolean[] puffed = {false};
        helper.onEachTick(() -> puffed[0] |= father.action() == com.antcolonies.savanna.animal.BullfrogEntity.PUFF);
        helper.succeedWhen(() -> {
            helper.assertTrue(puffed[0], "the father never puffed up at the tilapia (" + String.format("%.1f", father.distanceTo(tilapia)) + " blocks off)");
            helper.assertTrue(!tilapia.isAlive() || tilapia.getHealth() < full, "the father never bit the tilapia ("
                    + String.format("%.2f", father.distanceTo(tilapia)) + " blocks off)");
        });
    }

    /**
     * Breeding refills the water (the user, 2026-11-24; review R10-1). A tilapia mother whose eggs are due while the
     * river is full of her kind keeps them in her mouth and asks again (until then one look with no room threw the
     * whole brood away); when room opens she lets her fry out.
     */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "breeding_full_eggs")
    public static void aTilapiaMotherWithNoRoomKeepsHerEggsUntilThereIs(GameTestHelper helper) {
        breedingPond(helper);
        clearLeftovers(helper, SavannaEntities.TILAPIA.get());
        TilapiaEntity mother = grown(helper, SavannaEntities.TILAPIA.get(), "female", new BlockPos(5, BED + 1, 5));
        TilapiaEntity male = grown(helper, SavannaEntities.TILAPIA.get(), "male", new BlockPos(6, BED + 1, 5));
        int limit = mother.life().numbers().localLimit();
        List<TilapiaEntity> crowd = new java.util.ArrayList<>();
        for (int i = 2; i < limit + 3; i++) {
            crowd.add(grown(helper, SavannaEntities.TILAPIA.get(), "female", new BlockPos(P0 + i % 12, BED + 1 + i % 2, P0 + 1 + i / 12 * 3)));
        }
        mother.mated(male);
        int eggs = mother.eggsInMouth();
        helper.assertTrue(eggs > 0, "she took no eggs into her mouth");
        mother.ageEggs(mother.life().numbers().hatchTicks() + 1);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(mother.eggsInMouth() == eggs, "with no room for her kind she has " + mother.eggsInMouth() + " of her " + eggs + " eggs left");
            helper.assertTrue(fryOf(helper, mother).isEmpty(), "fry let out into a full river");
            crowd.forEach(net.minecraft.world.entity.Entity::discard);
        });
        helper.runAfterDelay(160, () -> {
            helper.assertTrue(mother.eggsInMouth() == 0, "room opened and she still holds her eggs");
            helper.assertTrue(fryOf(helper, mother).size() == eggs, fryOf(helper, mother).size() + " fry from " + eggs + " eggs with room for all");
            helper.succeed();
        });
    }

    /**
     * The world's own spawner fills a kind that breeds only to half its count in a zone, so breeding has the other half
     * to fill (review R10-1: both asked for the same room, and the spawner, trying every tick, always won); a kind that
     * does not breed is spawned to its whole count, and no kind is spawned where it does not live.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "breeding_full_eggs")
    public static void theSpawnerLeavesHalfOfABreedingKindsCountToBreeding(GameTestHelper helper) {
        var zones = com.antcolonies.savanna.worldgen.RiverZones.Zone.values();
        for (EntityType<?> kind : List.of(SavannaEntities.TILAPIA.get(), SavannaEntities.CATFISH.get(), SavannaEntities.CRAB.get(),
                SavannaEntities.SNAIL.get(), SavannaEntities.BULLFROG.get(), SavannaEntities.CROCODILE.get())) {
            int cap = kind.getCategory().getMaxInstancesPerChunk(), limit = AnimalLives.get(kind).localLimit();
            for (var zone : zones) {
                int whole = com.antcolonies.savanna.worldgen.RiverZones.allows(kind, zone, 0) ? 1 : 0;
                int bySpawner = com.antcolonies.savanna.worldgen.RiverZones.mostBySpawner(kind, zone);
                if (whole == 0) {
                    helper.assertTrue(bySpawner == 0, kind.getDescriptionId() + " spawned " + zone + ", where it does not live");
                    continue;
                }
                helper.assertTrue(bySpawner >= 1 && bySpawner <= Math.max(1, (cap + 1) / 2),
                        kind.getDescriptionId() + " " + zone + ": the spawner fills " + bySpawner + " of a cap of " + cap);
            }
            helper.assertTrue(limit > 1, kind.getDescriptionId() + " has no room to breed in (local limit " + limit + ")");
        }
        for (EntityType<?> kind : List.of(SavannaEntities.OTTER.get(), SavannaEntities.MONITOR.get())) {
            helper.assertTrue(com.antcolonies.savanna.worldgen.RiverZones.mostBySpawner(kind, com.antcolonies.savanna.worldgen.RiverZones.Zone.MID)
                    == Integer.MAX_VALUE, kind.getDescriptionId() + " does not breed and is held back all the same");
        }
        helper.succeed();
    }
}
