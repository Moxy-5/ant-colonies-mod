package com.antcolonies.savanna.gametest;

import static com.antcolonies.savanna.gametest.TermiteMoundTests.SURFACE;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.CatfishEntity;
import com.antcolonies.savanna.animal.TilapiaEntity;
import com.antcolonies.savanna.block.AlgaeBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.worldgen.RiverAlgaeFeature;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * River life (M9, 2026-11-14): the algae on the river bed, the first link of the food chain. One pond, shallow at one
 * end (2 blocks of water) and deep at the other (7), checks laying, growing by depth, spreading, grazing and breaking.
 * The same pond holds the Nile tilapia, the grazing fish: they graze the shelf's algae down, grow up from young and wear
 * their three looks.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class RiverLifeTests {
    private static final int X0 = 2, X1 = 11, Z0 = 2, Z1 = 9;   // the pond's inside
    private static final int SHALLOW_TO = 6;                   // x up to here: a shelf with 2 blocks of water over it
    private static final int TOP = SURFACE + 6;                // the water's surface

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void algaeGrowsByDepthSpreadsAndIsGrazed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        algaeChecks(helper, level);
    }

    /**
     * The pond: a stone floor and walls, a shelf under 2 blocks of water up to {@link #SHALLOW_TO}, 7 blocks deep past it.
     * The arena is empty air down to the test world's ground about 12 blocks lower, so without the floor the water poured
     * out under the pond and its current dragged the fish down (the catfish test's misses, 2026-11-14).
     */
    private static void buildPond(GameTestHelper helper) {
        for (int x = X0 - 1; x <= X1 + 1; x++) {
            for (int z = Z0 - 1; z <= Z1 + 1; z++) {
                boolean wall = x < X0 || x > X1 || z < Z0 || z > Z1;
                helper.setBlock(new BlockPos(x, SURFACE - 1, z), Blocks.STONE);
                for (int y = SURFACE; y <= TOP + 1; y++) {
                    BlockPos at = new BlockPos(x, y, z);
                    if (wall) {
                        helper.setBlock(at, y <= TOP ? Blocks.STONE : Blocks.AIR);
                    } else if (x <= SHALLOW_TO && y <= TOP - 2) {
                        helper.setBlock(at, Blocks.STONE);
                    } else {
                        helper.setBlock(at, y <= TOP ? Blocks.WATER : Blocks.AIR);
                    }
                }
            }
        }
    }

    private static void algaeChecks(GameTestHelper helper, ServerLevel level) {
        // 1. new land's laying: thick or lush on the shelf, never more than thin in the deep
        BlockPos corner = helper.absolutePos(new BlockPos(X0, TOP, Z0));
        int laid = RiverAlgaeFeature.lay(level, corner.getX(), corner.getZ(), X1 - X0 + 1, Z1 - Z0 + 1, corner.getY(), at -> true);
        int shallowThick = 0;
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                boolean shallow = x <= SHALLOW_TO;
                BlockState algae = helper.getBlockState(new BlockPos(x, shallow ? TOP - 1 : SURFACE, z));
                if (!(algae.getBlock() instanceof AlgaeBlock)) {
                    if (shallow) {
                        helper.fail("no algae laid on the shallow shelf at " + x + ", " + z);
                    }
                    continue;
                }
                int g = algae.getValue(AlgaeBlock.GROWTH);
                if (!shallow && g > AlgaeBlock.THIN) {
                    helper.fail("algae " + g + " in 7 blocks of water at " + x + ", " + z + " (thin at most)");
                }
                if (shallow && g >= AlgaeBlock.THICK) {
                    shallowThick++;
                }
            }
        }
        if (laid == 0 || shallowThick < 10) {
            helper.fail("laid " + laid + ", thick or lush on the shelf " + shallowThick);
        }
        // 2. growing: a bare mat in the shallows grows lush, one in the deep only thin
        BlockPos shallow = new BlockPos(X0 + 1, TOP - 1, Z0 + 1), deep = new BlockPos(X1 - 1, SURFACE, Z1 - 1);
        BlockState bare = SavannaBlocks.ALGAE.get().defaultBlockState().setValue(AlgaeBlock.GROWTH, AlgaeBlock.BARE);
        helper.setBlock(shallow, bare);
        helper.setBlock(deep, bare);
        for (int i = 0; i < 400; i++) {
            for (BlockPos p : new BlockPos[] {shallow, deep}) {
                BlockPos abs = helper.absolutePos(p);
                level.getBlockState(abs).randomTick(level, abs, level.random);
            }
        }
        helper.assertBlockProperty(shallow, AlgaeBlock.GROWTH, AlgaeBlock.LUSH);
        helper.assertBlockProperty(deep, AlgaeBlock.GROWTH, AlgaeBlock.THIN);
        // 3. grazing bites a stage off and never takes the last of it
        BlockPos absShallow = helper.absolutePos(shallow);
        for (int i = 0; i < 3; i++) {
            if (!AlgaeBlock.graze(level, absShallow)) {
                helper.fail("nothing to graze after " + i + " bites");
            }
        }
        if (AlgaeBlock.graze(level, absShallow)) {
            helper.fail("a bare mat was grazed");
        }
        helper.assertBlockProperty(shallow, AlgaeBlock.GROWTH, AlgaeBlock.BARE);
        // 4. spreading: the shelf cleared but one lush mat, which seeds the bare bed round it
        for (int x = X0; x <= SHALLOW_TO; x++) {
            for (int z = Z0; z <= Z1; z++) {
                helper.setBlock(new BlockPos(x, TOP - 1, z), Blocks.WATER);
            }
        }
        BlockPos seed = new BlockPos(X0 + 2, TOP - 1, Z0 + 3);
        helper.setBlock(seed, bare.setValue(AlgaeBlock.GROWTH, AlgaeBlock.LUSH));
        BlockPos absSeed = helper.absolutePos(seed);
        for (int i = 0; i < 1000; i++) {
            level.getBlockState(absSeed).randomTick(level, absSeed, level.random);
        }
        int spread = 0;
        for (int x = X0; x <= SHALLOW_TO; x++) {
            for (int z = Z0; z <= Z1; z++) {
                if (helper.getBlockState(new BlockPos(x, TOP - 1, z)).getBlock() instanceof AlgaeBlock) {
                    spread++;
                }
            }
        }
        if (spread < 3) {
            helper.fail("the lush mat seeded only " + (spread - 1) + " cells in 1000 ticks");
        }
        // 5. broken, it leaves water
        helper.destroyBlock(seed);
        if (!helper.getBlockState(seed).is(Blocks.WATER)) {
            helper.fail("broken algae left " + helper.getBlockState(seed) + ", not water");
        }
        helper.succeed();
    }

    /**
     * The tilapia (2026-11-14): hungry fish over a shelf of lush algae graze it down; a young fish is small and barred
     * and grows into a grown look; about 1 grown male in 3 wears the breeding colours, never a female; a bucket keeps a
     * fish's look and size; it spawns only in water.
     */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void tilapiaGrazeGrowUpAndWearTheirLooks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        BlockState lush = SavannaBlocks.ALGAE.get().defaultBlockState().setValue(AlgaeBlock.GROWTH, AlgaeBlock.LUSH);
        int start = 0;
        for (int x = X0; x <= SHALLOW_TO; x++) {
            for (int z = Z0; z <= Z1; z++) {
                helper.setBlock(new BlockPos(x, TOP - 1, z), lush);
                start += AlgaeBlock.LUSH;
            }
        }
        // the looks, from many fish never put in the world
        int males = 0, breeding = 0;
        for (int i = 0; i < 400; i++) {
            TilapiaEntity fish = new TilapiaEntity(SavannaEntities.TILAPIA.get(), level);
            fish.setUp(false, level.random);
            if (fish.look() == TilapiaEntity.BREEDING && !fish.isMale()) {
                helper.fail("a female wears the breeding colours");
            }
            if (fish.look() == TilapiaEntity.YOUNG) {
                helper.fail("a grown fish wears the young look");
            }
            float size = (float) fish.getAttributeValue(Attributes.SCALE);
            if (size < 0.84F || size > 1.16F) {
                helper.fail("a grown fish of size " + size);
            }
            if (fish.isMale()) {
                males++;
                if (fish.look() == TilapiaEntity.BREEDING) {
                    breeding++;
                }
            }
        }
        if (males < 150 || breeding < males * 0.2 || breeding > males * 0.47) {
            helper.fail(breeding + " of " + males + " males in breeding colours (about 1 in 3 wanted)");
        }
        // a bucket keeps a fish's look and size
        TilapiaEntity caught = new TilapiaEntity(SavannaEntities.TILAPIA.get(), level);
        do {
            caught.setUp(false, level.random);
        } while (caught.look() != TilapiaEntity.BREEDING);
        ItemStack bucket = caught.getBucketItemStack();
        caught.saveToBucketTag(bucket);
        TilapiaEntity poured = new TilapiaEntity(SavannaEntities.TILAPIA.get(), level);
        poured.setUp(true, level.random);
        poured.loadFromBucketTag(bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA).copyTag());
        if (poured.look() != TilapiaEntity.BREEDING || Math.abs(poured.ownSize() - caught.ownSize()) > 0.001F) {
            helper.fail("out of the bucket: look " + poured.look() + ", size " + poured.ownSize() + " (was " + caught.ownSize() + ")");
        }
        // spawning: in the pond's water (when its height is in the spawn band), never in the air
        BlockPos water = helper.absolutePos(new BlockPos(X1 - 1, SURFACE + 3, Z1 - 1));
        BlockPos air = helper.absolutePos(new BlockPos(X1 - 1, TOP + 1, Z1 - 1));
        if (Math.abs(water.getY() - level.getSeaLevel()) <= 6
                && !TilapiaEntity.canSpawn(SavannaEntities.TILAPIA.get(), level, MobSpawnType.NATURAL, water, level.random)) {
            helper.fail("may not spawn in the pond's water at " + water.toShortString());
        }
        if (TilapiaEntity.canSpawn(SavannaEntities.TILAPIA.get(), level, MobSpawnType.NATURAL, air, level.random)) {
            helper.fail("may spawn in the air over the pond");
        }
        // natural spawning is wired: the placement rule signed up and the savanna waters listing the fish
        if (net.minecraft.world.entity.SpawnPlacements.getPlacementType(SavannaEntities.TILAPIA.get())
                != net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER) {
            helper.fail("the tilapia's spawn placement is not signed up");
        }
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        for (var key : java.util.List.of(net.minecraft.world.level.biome.Biomes.SAVANNA,
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_river")))) {
            boolean listed = biomes.getOrThrow(key).getMobSettings().getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverFish())
                    .unwrap().stream().anyMatch(d -> d.type == SavannaEntities.TILAPIA.get());
            if (!listed) {
                helper.fail("no tilapia in the spawn list of " + key.location());
            }
        }
        // river fish have their own spawning group and cap, apart from Minecraft's fish; a wild fish stays near a
        // player, goes when left far behind, and counts toward its group's cap
        var group = SavannaEntities.TILAPIA.get().getCategory();
        if (group != com.antcolonies.savanna.registry.SavannaMobCategories.riverFish()
                || group.getMaxInstancesPerChunk() <= net.minecraft.world.entity.MobCategory.WATER_AMBIENT.getMaxInstancesPerChunk()
                || group.isPersistent()) {
            helper.fail("tilapia are not in the river fish group with its own bigger cap: " + group.getName());
        }
        TilapiaEntity wild = new TilapiaEntity(SavannaEntities.TILAPIA.get(), level);
        wild.setUp(false, level.random);
        if (wild.removeWhenFarAway(64.0 * 64.0) || !wild.removeWhenFarAway(200.0 * 200.0)
                || wild.isPersistenceRequired() || wild.requiresCustomPersistence()) {
            helper.fail("a wild tilapia is removed near a player, kept far away, or no longer counts toward the cap");
        }
        // hungry grown fish in the deep end; a young one about to grow up
        TilapiaEntity[] school = new TilapiaEntity[4];
        for (int i = 0; i < school.length; i++) {
            school[i] = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(X1 - 1 - i, SURFACE + 3, Z0 + 2 + i));
            school[i].setUp(false, level.random);
            school[i].setPersistenceRequired();
            school[i].makeHungry();
        }
        TilapiaEntity young = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(X1 - 1, SURFACE + 4, Z1 - 1));
        young.setUp(true, level.random);
        young.setPersistenceRequired();
        if (young.look() != TilapiaEntity.YOUNG || young.getAttributeValue(Attributes.SCALE) > young.ownSize() * 0.7) {
            helper.fail("a young fish: look " + young.look() + ", size " + young.getAttributeValue(Attributes.SCALE));
        }
        young.setTicksToGrow(40);
        final int full = start;
        boolean[] sawGrazing = {false};
        helper.onEachTick(() -> {
            for (TilapiaEntity fish : school) {
                sawGrazing[0] |= fish.isGrazing();
            }
        });
        helper.runAtTickTime(80, () -> {
            if (young.look() == TilapiaEntity.YOUNG || Math.abs(young.getAttributeValue(Attributes.SCALE) - young.ownSize()) > 0.01) {
                helper.fail("not grown up: look " + young.look() + ", size " + young.getAttributeValue(Attributes.SCALE)
                        + " (own " + young.ownSize() + ")");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 80, "the young fish's check first");
            int left = 0;
            for (int x = X0; x <= SHALLOW_TO; x++) {
                for (int z = Z0; z <= Z1; z++) {
                    BlockState state = helper.getBlockState(new BlockPos(x, TOP - 1, z));
                    if (state.getBlock() instanceof AlgaeBlock) {
                        left += state.getValue(AlgaeBlock.GROWTH);
                    }
                }
            }
            helper.assertTrue(sawGrazing[0] && full - left >= 3, "grazed " + (full - left) + " stages of " + full
                    + (sawGrazing[0] ? "" : ", no fish seen grazing"));
        });
    }

    /**
     * The catfish (2026-11-14): spawns in the river fish group in the savanna's waters, only in water at least 3 deep; a
     * hungry one hunts a wild tilapia in the pond's deep end, strikes with its mouth open and swallows it, and is fed;
     * a tilapia from a bucket (the player's own) is left alone; it is 2 blocks long at the usual size.
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aHungryCatfishHuntsAndSwallowsATilapia(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        // wiring: the placement rule, the savanna waters' spawn list, the river fish group
        if (net.minecraft.world.entity.SpawnPlacements.getPlacementType(SavannaEntities.CATFISH.get())
                != net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER) {
            helper.fail("the catfish's spawn placement is not signed up");
        }
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        boolean listed = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA).getMobSettings()
                .getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverFish())
                .unwrap().stream().anyMatch(d -> d.type == SavannaEntities.CATFISH.get());
        if (!listed || SavannaEntities.CATFISH.get().getCategory() != com.antcolonies.savanna.registry.SavannaMobCategories.riverFish()) {
            helper.fail("the catfish is not in the savanna's river fish spawn list");
        }
        // never in the shelf's 2 blocks of water (when the pond is in the spawn band), always in the deep end
        BlockPos shelf = helper.absolutePos(new BlockPos(X0 + 1, TOP, Z0 + 1));
        BlockPos deep = helper.absolutePos(new BlockPos(X1 - 1, SURFACE + 3, Z1 - 1));
        if (Math.abs(deep.getY() - level.getSeaLevel()) <= 6) {
            if (CatfishEntity.canSpawn(SavannaEntities.CATFISH.get(), level, MobSpawnType.NATURAL, shelf, level.random)) {
                helper.fail("a catfish may spawn in 2 blocks of water");
            }
            if (!CatfishEntity.canSpawn(SavannaEntities.CATFISH.get(), level, MobSpawnType.NATURAL, deep, level.random)) {
                helper.fail("a catfish may not spawn in the deep end");
            }
        }
        CatfishEntity catfish = helper.spawn(SavannaEntities.CATFISH.get(), new BlockPos(X1 - 1, SURFACE + 3, Z0 + 1));
        catfish.setPersistenceRequired();
        catfish.makeHungry();
        if (Math.abs(catfish.getAttributeValue(Attributes.SCALE) - 1.0) > 0.001) {
            helper.fail("a spawned catfish's size is " + catfish.getAttributeValue(Attributes.SCALE) + " (1 before its own size is rolled)");
        }
        TilapiaEntity pet = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(X1 - 2, SURFACE + 3, Z0 + 2));
        pet.setUp(false, level.random);
        pet.setFromBucket(true);
        TilapiaEntity wild = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(X1 - 1, SURFACE + 4, Z1 - 1));
        wild.setUp(false, level.random);
        wild.setPersistenceRequired();
        boolean[] sawStrike = {false};
        // the catch (2026-11-15): from the moment the tilapia is gone it is held in the catfish's mouth, in its own
        // look, for the catch clip's length, then the mouth is empty again
        int wildLook = wild.look();
        int[] held = {0};
        boolean[] wrongLook = {false};
        // the side it lies on: the side of the tilapia the catfish was on at the catch (0 not caught yet, 1 right, 2 wrong)
        int[] side = {0};
        double[] was = new double[5];
        helper.onEachTick(() -> {
            sawStrike[0] |= catfish.isStriking();
            if (!wild.isRemoved()) {
                was[0] = wild.getX();
                was[1] = wild.getZ();
                was[2] = wild.getYRot();
                was[3] = catfish.getX();
                was[4] = catfish.getZ();
            }
            if (catfish.isHolding()) {
                if (side[0] == 0) {
                    double yaw = Math.toRadians(was[2]);
                    double toRight = (was[3] - was[0]) * -Math.cos(yaw) + (was[4] - was[1]) * -Math.sin(yaw);
                    // a catfish nearly dead astern may cross the line in the catch's own tick: only a clear side counts
                    side[0] = Math.abs(toRight) < 0.15 || catfish.heldOnRight() == toRight > 0 ? 1 : 2;
                }
                held[0]++;
                wrongLook[0] |= catfish.heldLook() != wildLook || !wild.isRemoved();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(catfish.isAlive(), "the catfish died");
            helper.assertTrue(side[0] != 2, "the caught tilapia lies on the side away from where the catfish came from");
            helper.assertTrue(!wrongLook[0], "the tilapia in the catfish's mouth was not the caught one's look, or the caught one still swam");
            helper.assertTrue(wild.isRemoved() && !catfish.isHolding() && held[0] >= 45 && held[0] <= 60,
                    "the caught tilapia was held in the mouth for " + held[0] + " ticks"
                            + (catfish.isHolding() ? ", still held" : "") + " (about 52, then gone)");
            helper.assertTrue(wild.isRemoved() && sawStrike[0] && !catfish.isHungry(),
                    "the wild tilapia " + (wild.isRemoved() ? "is gone" : "still swims") + (sawStrike[0] ? "" : ", no strike seen")
                            + (catfish.isHungry() ? ", the catfish still hungry" : ""));
            helper.assertTrue(pet.isAlive(), "the bucket tilapia was eaten");
        });
    }

    /**
     * The school after a hunt (the user's report 2026-11-14: after the catfish struck at them a few times the tilapia
     * huddled in a corner and stopped moving). A school of 8 in the deep end, a hungry catfish; once it has eaten, every
     * tilapia left must still be swimming about: none stands still (moves less than half a block across) for 20 s. The fish are
     * wild (not kept for ever) and no player is counted near, as with the user watching in spectator mode: Minecraft
     * counts only non-spectators near a mob, and a mob with nobody counted near stops wandering after 5 s.
     */
    @GameTest(template = "arena", timeoutTicks = 3200)
    public static void theSchoolSwimsOnAfterAHunt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        java.util.List<TilapiaEntity> school = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            // made by hand: helper.spawn marks a mob kept for ever, and Minecraft zeroes a kept mob's idle clock
            TilapiaEntity t = SavannaEntities.TILAPIA.get().create(level);
            net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(X1 - 0.5 - i % 3, SURFACE + 2 + i % 3, Z1 - 0.5 - i / 3));
            t.moveTo(at.x, at.y, at.z);
            t.setUp(false, level.random);
            level.addFreshEntity(t);
            school.add(t);   // not kept for ever: as wild fish with only a spectator near (no player counted)
        }
        for (int i = 1; i < school.size(); i++) {
            school.get(i).startFollowing(school.get(0));
        }
        CatfishEntity catfish = helper.spawn(SavannaEntities.CATFISH.get(), new BlockPos(X1 - 1, SURFACE + 3, Z0 + 1));
        catfish.setPersistenceRequired();
        catfish.makeHungry();
        int[] fedAt = {-1};
        java.util.Map<TilapiaEntity, net.minecraft.world.phys.Vec3> lastAt = new java.util.HashMap<>();
        java.util.Map<TilapiaEntity, Double> walked = new java.util.HashMap<>();
        int[] tick = {0};
        StringBuilder still = new StringBuilder();
        helper.onEachTick(() -> {
            tick[0]++;
            if (fedAt[0] < 0 && !catfish.isHungry()) {
                fedAt[0] = tick[0];
            }
            if (fedAt[0] < 0 || tick[0] < fedAt[0] + 100) {
                return;
            }
            int since = tick[0] - fedAt[0] - 100;
            for (TilapiaEntity t : school) {
                if (t.isRemoved()) {
                    continue;
                }
                net.minecraft.world.phys.Vec3 was = lastAt.put(t, t.position());
                if (was != null) {
                    walked.merge(t, Math.hypot(was.x - t.getX(), was.z - t.getZ()), Double::sum);   // across: an idle fish sinks
                }
            }
            if (since > 0 && since % 400 == 0) {
                for (TilapiaEntity t : school) {
                    if (t.isRemoved()) {
                        continue;
                    }
                    double w = walked.getOrDefault(t, 0.0);
                    if (w < 0.5 && still.length() < 2000) {
                        String goals = t.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning())
                                .map(g -> g.getGoal().getClass().getSimpleName()).collect(java.util.stream.Collectors.joining(","));
                        still.append(String.format(java.util.Locale.ROOT, " | tick %d: fish %d moved %.2f in 20 s at %s, follower %b, leads %b, nav done %b, idle %d, goals [%s], leader %s %.1f away, path %s",
                                since, school.indexOf(t), w, helper.relativeVec(t.position()), t.isFollower(), t.hasFollowers(),
                                t.getNavigation().isDone(), t.getNoActionTime(), goals,
                                t.followedForTests() == null ? "-" : String.valueOf(school.indexOf(t.followedForTests())),
                                t.followedForTests() == null ? 0.0 : t.distanceTo(t.followedForTests()),
                                t.followedForTests() == null ? "-" : String.valueOf(t.getNavigation().createPath(t.followedForTests(), 0))));
                    }
                    walked.put(t, 0.0);
                }
            }
            if (since >= 2000) {
                if (still.length() > 0) {
                    helper.fail("tilapia stood still after the hunt:" + still);
                } else {
                    helper.succeed();
                }
            }
        });
    }

    /**
     * The Nile crocodile's first step (2026-11-15): put in the pond's deep end by day and wanting the sun, it swims to
     * the bank, climbs out and basks (the mouth-open pose is the synced basking flag); told it has had enough, it walks
     * back and slides into the water. Its swimming and walking speeds go to the log. Then its life step 3 (2026-11-15):
     * basking again and hit, it rushes back and lunges in within a few seconds, and sinks out of sight while startled;
     * then wanting the sun to rest half out, it lies in the water nose to a bank, basking with its front over the bank.
     */
    @GameTest(template = "arena", timeoutTicks = 4800)
    public static void aCrocodileClimbsOutToBaskAndGoesBackIn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        for (int x = X1 + 1; x <= X1 + 6; x++) {            // a bank beside the deep end, level with the pond's rim
            for (int z = Z0 - 1; z <= Z1 + 1; z++) {
                helper.setBlock(new BlockPos(x, TOP, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, TOP + 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, TOP + 2, z), Blocks.AIR);
            }
        }
        helper.setDayTime(6000);
        if (net.minecraft.world.entity.SpawnPlacements.getPlacementType(SavannaEntities.CROCODILE.get())
                != net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER) {
            helper.fail("the crocodile's spawn placement is not signed up");
        }
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        if (biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA).getMobSettings()
                .getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverFish())
                .unwrap().stream().noneMatch(d -> d.type == SavannaEntities.CROCODILE.get())) {
            helper.fail("the crocodile is not in the savanna's river fish spawn list");
        }
        com.antcolonies.savanna.animal.CrocodileEntity croc = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(X1 - 2, SURFACE + 4, Z0 + 3));
        croc.setPersistenceRequired();
        croc.wantSun();
        // 0 not basked yet; 1 sent back; 2 back in, wanting the sun again; 3 basking again and hit; 4 in, startled; 5 resting half out
        int[] stage = {0};
        long[] hitAt = {0};
        double[] fastest = new double[2];
        helper.onEachTick(() -> {
            double dx = croc.getX() - croc.xo, dy = croc.getY() - croc.yo, dz = croc.getZ() - croc.zo;
            double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int where = croc.isInWater() ? 0 : 1;
            fastest[where] = Math.max(fastest[where], speed);
            long now = helper.getTick();
            switch (stage[0]) {
                case 0 -> {
                    if (croc.isBasking() && !croc.isInWater() && croc.onGround()) {
                        stage[0] = 1;
                        croc.sunIn = 100_000;        // enough sun
                    }
                }
                case 1 -> {
                    if (!croc.isBasking() && croc.isInWater()) {
                        SavannaMod.LOGGER.info("[crocodile] fastest in water {} blocks a tick, on land {}",
                                String.format(java.util.Locale.ROOT, "%.3f", fastest[0]), String.format(java.util.Locale.ROOT, "%.3f", fastest[1]));
                        stage[0] = 2;
                        croc.wantSun();
                    }
                }
                case 2 -> {
                    if (croc.isBasking() && !croc.isInWater() && croc.onGround()) {
                        stage[0] = 3;
                        hitAt[0] = now;
                        croc.hurt(level.damageSources().generic(), 1.0F);
                        helper.assertTrue(croc.startled > 0 && !croc.isBasking(), "hit while basking, the crocodile was not startled off the bank");
                    }
                }
                case 3 -> {
                    if (croc.isInWater()) {
                        SavannaMod.LOGGER.info("[crocodile] in the water {} ticks after it was hit", now - hitAt[0]);
                        stage[0] = 4;
                    } else if (now - hitAt[0] > 120) {
                        helper.fail("hit on the bank, the crocodile was not back in the water within 6 s: at "
                                + croc.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
                    }
                }
                case 4 -> {
                    if (croc.startled == 0) {
                        stage[0] = 5;
                        croc.wantSunHalfOut();
                    } else if (croc.startled < 30 && croc.getNavigation().isDone() && croc.getEyeY() > croc.getY() + croc.getFluidHeight(net.minecraft.tags.FluidTags.WATER)) {
                        helper.fail("startled in the water, the crocodile did not sink out of sight: its eyes are above the surface");
                    }
                }
                default -> { }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(croc.isAlive(), "the crocodile died");
            helper.assertTrue(stage[0] == 5, "the crocodile is at stage " + stage[0] + " of 5 (0: not basked yet, 2: not out to bask again,"
                    + " 3: not back in after being hit) at " + croc.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
            float yaw = croc.yBodyRot * net.minecraft.util.Mth.DEG_TO_RAD;
            BlockPos ahead = BlockPos.containing(croc.getX() - net.minecraft.util.Mth.sin(yaw) * 1.0, helper.absolutePos(BlockPos.ZERO).getY() + TOP,
                    croc.getZ() + net.minecraft.util.Mth.cos(yaw) * 1.0);
            helper.assertTrue(croc.isBasking() && croc.isInWater() && !level.getBlockState(ahead).isAir() && level.getFluidState(ahead).isEmpty(),
                    "the crocodile is not resting half out, in the water nose to a bank: basking " + croc.isBasking() + ", in water "
                            + croc.isInWater() + ", ahead " + level.getBlockState(ahead));
        });
    }

    /**
     * The hunt (2026-11-15): a hungry crocodile in the pond and a cow standing at the water's edge on the bank. The
     * first time the cow is made to slip free of the haul: it lives, and the crocodile is back in the water, not hunting. Then the
     * crocodile creeps up, strikes and grabs the cow, hauls it backward into the water (the cow is in the water before
     * the first roll, and the crocodile was moved toward the water while it held it) and death-rolls it until it dies, then is
     * fed and hunts no more. A chicken put at the edge after that is left alone (fed), and once hungry again it is
     * swallowed whole, with no roll.
     */
    /**
     * The crocodile hunts fish (its life step 4, 2026-11-15): a hungry crocodile in the pond with a school of wild
     * tilapia and one from a bucket strikes at the school (a snap to the side or a lunge), catches a wild one, holds it
     * and swallows it whole; its strike calls a second hungry crocodile to the school. Then, hungry again, it swallows a
     * catfish whole too, with no death roll. The bucket fish is never taken.
     */
    @GameTest(template = "arena", timeoutTicks = 3000)
    public static void aCrocodileSnapsUpFishAndCallsOthersToTheSchool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        helper.setDayTime(18000);                            // night: no wish to bask
        com.antcolonies.savanna.animal.CrocodileEntity croc = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(X0 + 2, SURFACE + 5, Z0 + 3));
        croc.setPersistenceRequired();
        croc.makeHungry();
        croc.strikeLuck = 1;        // no fish darts away: the test is about the catch, not the dice
        com.antcolonies.savanna.animal.CrocodileEntity other = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(X0 + 2, SURFACE + 5, Z1 - 1));
        other.setPersistenceRequired();
        other.holdOffHunting(100000);
        java.util.List<TilapiaEntity> school = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TilapiaEntity fish = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(X1 - 1 - (i % 2), SURFACE + 3 + (i % 3), Z0 + 2 + i));
            fish.setUp(false, level.random);
            fish.setPersistenceRequired();
            school.add(fish);
        }
        TilapiaEntity pet = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(X1 - 2, SURFACE + 4, Z0 + 4));
        pet.setUp(false, level.random);
        pet.setFromBucket(true);
        // 0 hunting the school; 1 the catfish put in, hungry again; 2 done
        int[] stage = {0};
        boolean[] struck = {false}, heldFish = {false}, called = {false}, rolled = {false}, heldCatfish = {false};
        CatfishEntity[] catfish = {null};
        helper.onEachTick(() -> {
            byte jaws = croc.jaws();
            struck[0] |= jaws == com.antcolonies.savanna.animal.CrocodileEntity.JAWS_SNAP_LEFT
                    || jaws == com.antcolonies.savanna.animal.CrocodileEntity.JAWS_SNAP_RIGHT
                    || jaws == com.antcolonies.savanna.animal.CrocodileEntity.JAWS_GAPE;
            called[0] |= other.calledToSchool();
            rolled[0] |= croc.rollAngle(0.0F) != 0.0F;
            helper.assertTrue(pet.isAlive() && !croc.holds(pet), "the crocodile took the bucket tilapia");
            switch (stage[0]) {
                case 0 -> {
                    heldFish[0] |= croc.held() instanceof TilapiaEntity;
                    if (school.stream().anyMatch(TilapiaEntity::isRemoved) && croc.held() == null && !croc.isHungry()) {
                        helper.assertTrue(struck[0], "a tilapia was eaten with no strike seen");
                        helper.assertTrue(heldFish[0], "the tilapia was never held in the jaws before it was swallowed");
                        helper.assertTrue(called[0], "the strike at the school did not call the other crocodile");
                        helper.assertTrue(!rolled[0], "the tilapia was rolled, not swallowed");
                        SavannaMod.LOGGER.info("[crocodile] swallowed a tilapia at tick {}", helper.getTick());
                        school.forEach(f -> { if (!f.isRemoved()) f.discard(); });
                        catfish[0] = helper.spawn(SavannaEntities.CATFISH.get(), new BlockPos(X1 - 2, SURFACE + 3, Z0 + 4));
                        catfish[0].setPersistenceRequired();
                        catfish[0].setNoAi(true);
                        croc.makeHungry();
                        stage[0] = 1;
                    }
                }
                case 1 -> {
                    heldCatfish[0] |= croc.holds(catfish[0]);
                    if (catfish[0].isRemoved() && croc.held() == null && !croc.isHungry()) {
                        helper.assertTrue(heldCatfish[0], "the catfish was gone but never held in the jaws");
                        helper.assertTrue(!rolled[0], "the catfish was rolled, not swallowed whole");
                        SavannaMod.LOGGER.info("[crocodile] swallowed the catfish at tick {}", helper.getTick());
                        stage[0] = 2;
                        helper.succeed();
                    }
                }
                default -> { }
            }
        });
    }

    @GameTest(template = "arena", timeoutTicks = 4800)
    public static void aHungryCrocodileGrabsDrownsAndRollsItsPrey(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        for (int x = X1 + 1; x <= X1 + 6; x++) {            // a bank beside the deep end, level with the pond's rim
            for (int z = Z0 - 1; z <= Z1 + 1; z++) {
                helper.setBlock(new BlockPos(x, TOP, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, TOP + 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, TOP + 2, z), Blocks.AIR);
            }
        }
        helper.setDayTime(18000);                            // night: no wish to bask
        com.antcolonies.savanna.animal.CrocodileEntity croc = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(X0 + 3, SURFACE + 4, Z0 + 4));
        croc.setPersistenceRequired();
        croc.makeHungry();
        croc.strikeLuck = -1;
        net.minecraft.world.entity.animal.Cow cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(X1 + 1, TOP + 1, Z0 + 4));
        cow.setNoAi(true);
        boolean[] missed = {false}, rolledYet = {false};
        // -1 (missed[0] false): the strike that misses; 0 stalking; 1 holding; 2 the cow dead, fed; 3 a chicken left alone while fed; 4 hungry again, the chicken swallowed
        int[] stage = {0};
        boolean[] rolled = {false}, rolledChicken = {false};
        long[] at = {0};
        net.minecraft.world.entity.animal.Chicken[] chicken = {null};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            switch (stage[0]) {
                case 0 -> {
                    if (!missed[0]) {
                        helper.assertTrue(cow.isAlive(), "the cow that was to slip free died");
                        if (croc.misses > 0 && croc.isInWater() && now - at[0] > 100) {
                            helper.assertTrue(croc.held() == null && cow.getVehicle() == null, "the cow slipped free but is still in the crocodile's jaws");
                            SavannaMod.LOGGER.info("[crocodile] the cow slipped free and the crocodile was back in the water at tick {}", now);
                            missed[0] = true;
                            croc.strikeLuck = 1;
                            croc.makeHungry();
                        } else if (croc.misses == 0) {
                            at[0] = now;
                        }
                        break;
                    }
                    if (croc.holds(cow)) {
                        SavannaMod.LOGGER.info("[crocodile] grabbed the cow at tick {}, the crocodile in the water: {}, the cow: {}", now,
                                croc.isInWater(), cow.isInWater());
                        stage[0] = 1;
                    } else if (!cow.isAlive()) {
                        stage[0] = 2;       // killed by the bite itself
                    }
                }
                case 1 -> {
                    if (croc.rollAngle(0.0F) != 0.0F) {
                        rolled[0] = true;
                        helper.assertTrue(cow.getVehicle() == croc, "the cow is not in the crocodile's jaws while it rolls");
                        if (!rolledYet[0]) {
                            rolledYet[0] = true;
                            SavannaMod.LOGGER.info("[crocodile] the first roll at tick {}", now);
                            helper.assertTrue(cow.isInWater() && croc.isInWater(), "the cow was rolled before it was hauled into the water: the cow in water "
                                    + cow.isInWater() + ", the crocodile " + croc.isInWater());
                        }
                    }
                    if (!cow.isAlive()) {
                        SavannaMod.LOGGER.info("[crocodile] the cow died at tick {}", now);
                        stage[0] = 2;
                    }
                }
                case 2 -> {
                    if (croc.held() == null && !croc.isHungry()) {
                        helper.assertTrue(rolled[0], "the cow died without a death roll");
                        chicken[0] = helper.spawn(net.minecraft.world.entity.EntityType.CHICKEN, new BlockPos(X1 + 1, TOP + 1, Z0 + 6));
                        chicken[0].setNoAi(true);
                        at[0] = now;
                        stage[0] = 3;
                    }
                }
                case 3 -> {
                    helper.assertTrue(chicken[0].isAlive() && !croc.holds(chicken[0]), "a fed crocodile took the chicken");
                    if (now - at[0] > 200) {
                        croc.makeHungry();
                        stage[0] = 4;
                    }
                }
                case 4 -> {
                    if (croc.rollAngle(0.0F) != 0.0F) {
                        rolledChicken[0] = true;
                    }
                    if (chicken[0].isRemoved() && croc.held() == null && !croc.isHungry()) {
                        helper.assertTrue(!rolledChicken[0], "the chicken was rolled, not swallowed");
                        SavannaMod.LOGGER.info("[crocodile] swallowed the chicken at tick {}", now);
                        helper.succeed();
                    }
                }
                default -> { }
            }
        });
    }
}
