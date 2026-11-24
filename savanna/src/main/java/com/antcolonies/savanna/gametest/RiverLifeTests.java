package com.antcolonies.savanna.gametest;

import static com.antcolonies.savanna.gametest.TermiteMoundTests.SURFACE;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.BarbEntity;
import com.antcolonies.savanna.animal.BullfrogEntity;
import com.antcolonies.savanna.animal.CatfishEntity;
import com.antcolonies.savanna.animal.FlyCloudEntity;
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
        sandBankBlockChecks(helper);
        algaeChecks(helper, level);
    }

    /**
     * The sand banks' blocks (2026-11-16, {@code docs/plans/sand-banks.md} step 2), checked beside the pond: every river
     * sand stands on stone, the crocodile slide keeps the way it was turned, pebbles take a look by position (a row is
     * not all one look), hold water, and wash away when the ground under them goes.
     */
    private static void sandBankBlockChecks(GameTestHelper helper) {
        int y = SURFACE;
        for (int x = 14; x <= 30; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, y - 1, z), Blocks.STONE);
            }
        }
        int x = 14;
        for (var tone : java.util.List.of(SavannaBlocks.RED_SAND, SavannaBlocks.GOLDEN_SAND, SavannaBlocks.PALE_SAND)) {
            for (var block : tone.all()) {
                BlockPos at = new BlockPos(x++, y, 2);
                helper.setBlock(at, block.get());
                if (!helper.getBlockState(at).is(block.get())) {
                    helper.fail(block.getId() + " did not stay where it was put");
                }
            }
        }
        BlockPos slide = new BlockPos(14, y, 3);
        helper.setBlock(slide, SavannaBlocks.GOLDEN_SAND.slide().get().defaultBlockState()
                .setValue(com.antcolonies.savanna.block.RiverSand.Slide.AXIS, net.minecraft.core.Direction.Axis.X)
                .setValue(com.antcolonies.savanna.block.RiverSand.Slide.WET, true));
        BlockState turned = helper.getBlockState(slide);
        if (turned.getValue(com.antcolonies.savanna.block.RiverSand.Slide.AXIS) != net.minecraft.core.Direction.Axis.X
                || !turned.getValue(com.antcolonies.savanna.block.RiverSand.Slide.WET)) {
            helper.fail("the crocodile slide lost its turn or its wetness: " + turned);
        }
        var pebbles = SavannaBlocks.RIVER_PEBBLES.get();
        java.util.Set<Integer> looks = new java.util.HashSet<>();
        for (int px = 14; px <= 30; px++) {
            BlockPos at = new BlockPos(px, y, 4);
            BlockState state = pebbles.at(helper.absolutePos(at), false);
            looks.add(state.getValue(com.antcolonies.savanna.block.RiverPebblesBlock.LOOK));
            helper.setBlock(at, state);
            if (!helper.getBlockState(at).is(pebbles)) {
                helper.fail("pebbles did not stay on stone at " + at);
            }
        }
        if (looks.size() < 4) {
            helper.fail("a row of 17 pebble blocks showed only " + looks.size() + " of the 7 looks");
        }
        BlockPos wet = new BlockPos(14, y, 5);
        helper.setBlock(wet, pebbles.at(helper.absolutePos(wet), true));
        if (!helper.getLevel().getFluidState(helper.absolutePos(wet)).is(net.minecraft.tags.FluidTags.WATER)) {
            helper.fail("wet pebbles hold no water");
        }
        helper.setBlock(new BlockPos(14, y - 1, 4), Blocks.AIR);
        if (helper.getBlockState(new BlockPos(14, y, 4)).is(pebbles)) {
            helper.fail("pebbles stayed hanging in the air after the ground under them went");
        }
        for (int cx = 14; cx <= 30; cx++) {                     // cleared, so nothing falls or flows into the pond's checks
            for (int cz = 2; cz <= 6; cz++) {
                helper.setBlock(new BlockPos(cx, y, cz), Blocks.AIR);
            }
        }
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
        helper.assertBlockProperty(shallow, AlgaeBlock.STRANDS, AlgaeBlock.fullStrands(AlgaeBlock.LUSH));
        helper.assertBlockProperty(deep, AlgaeBlock.GROWTH, AlgaeBlock.THIN);
        helper.assertBlockProperty(deep, AlgaeBlock.STRANDS, 0);
        // 3. each bite takes 2 strands (a pair, one off each plane), the mat staying lush; with the strands gone the next
        // bite eats the mat down to the bare bed; the bare specks are never taken, and the patch grows back, strands too
        BlockPos absShallow = helper.absolutePos(shallow);
        for (int left = AlgaeBlock.fullStrands(AlgaeBlock.LUSH) - 1; left >= 0; left--) {
            if (!AlgaeBlock.graze(level, absShallow)) {
                helper.fail("nothing to graze with " + (left + 1) + " strand pairs standing");
            }
            helper.assertBlockProperty(shallow, AlgaeBlock.STRANDS, left);
            helper.assertBlockProperty(shallow, AlgaeBlock.GROWTH, AlgaeBlock.LUSH);
        }
        if (!AlgaeBlock.graze(level, absShallow)) {
            helper.fail("nothing to graze on a lush mat with its strands gone");
        }
        helper.assertBlockProperty(shallow, AlgaeBlock.GROWTH, AlgaeBlock.BARE);
        if (AlgaeBlock.graze(level, absShallow)) {
            helper.fail("a bare mat was grazed");
        }
        helper.assertTrue(helper.getBlockState(shallow).getBlock() instanceof AlgaeBlock, "a grazed mat is gone, it cannot grow back");
        for (int i = 0; i < 400; i++) {
            level.getBlockState(absShallow).randomTick(level, absShallow, level.random);
        }
        helper.assertBlockProperty(shallow, AlgaeBlock.GROWTH, AlgaeBlock.LUSH);
        helper.assertBlockProperty(shallow, AlgaeBlock.STRANDS, AlgaeBlock.fullStrands(AlgaeBlock.LUSH));
        AlgaeBlock.graze(level, absShallow);
        helper.assertBlockProperty(shallow, AlgaeBlock.STRANDS, AlgaeBlock.fullStrands(AlgaeBlock.LUSH) - 1);
        // 4. spreading: the shelf cleared but one lush mat, which seeds the bare bed round it
        for (int x = X0; x <= SHALLOW_TO; x++) {
            for (int z = Z0; z <= Z1; z++) {
                helper.setBlock(new BlockPos(x, TOP - 1, z), Blocks.WATER);
            }
        }
        BlockPos seed = new BlockPos(X0 + 2, TOP - 1, Z0 + 3);
        helper.setBlock(seed, SavannaBlocks.ALGAE.get().whole(AlgaeBlock.LUSH));
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
        // 6. new land's old remains (2026-11-15): laid on the bed (the shelf or the deep floor), never afloat, part eaten
        // or bare, each set to its stage's bites
        int remainsLaid = com.antcolonies.savanna.worldgen.RiverRemainsFeature.scatter(level, level.random,
                corner.getX(), corner.getZ(), X1 - X0 + 1, Z1 - Z0 + 1, corner.getY(), 6, at -> true);
        java.util.List<com.antcolonies.savanna.animal.RemainsEntity> lying = level.getEntitiesOfClass(
                com.antcolonies.savanna.animal.RemainsEntity.class, net.minecraft.world.phys.AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(X0, SURFACE, Z0)),
                        helper.absolutePos(new BlockPos(X1, TOP + 1, Z1))));
        if (remainsLaid != 6 || lying.size() != 6) {
            helper.fail("old remains: laid " + remainsLaid + ", found " + lying.size() + " (6 asked)");
        }
        for (com.antcolonies.savanna.animal.RemainsEntity r : lying) {
            BlockPos cell = r.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));    // relativePos turns x and z round
            int bed = cell.getX() <= SHALLOW_TO ? TOP - 1 : SURFACE;
            if (cell.getY() != bed) {
                helper.fail("old remains at " + cell + ", the bed is at " + bed);
            }
            if (com.antcolonies.savanna.animal.RemainsEntity.stageOf(r.kind(), r.bitesLeft()) != r.stage() || r.stage() < 1 || r.stage() > 4) {
                helper.fail("old remains with a bad stage " + r.stage());
            }
            r.discard();
        }
        helper.succeed();
    }

    /**
     * The tilapia (2026-11-14): hungry fish over a shelf of lush algae graze it down; a young fish is small and barred
     * and grows into a grown look; no fish wears the breeding colours away from a nest pit (since step 2 of the animal life
     * cycle only a male holding a pit does: AnimalLifeTests); a bucket keeps a fish's look, youth and size; it spawns
     * only in water.
     */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void tilapiaGrazeGrowUpAndWearTheirLooks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        BlockState lush = SavannaBlocks.ALGAE.get().whole(AlgaeBlock.LUSH);
        int start = 0;
        for (int x = X0; x <= SHALLOW_TO; x++) {
            for (int z = Z0; z <= Z1; z++) {
                helper.setBlock(new BlockPos(x, TOP - 1, z), lush);
                start += AlgaeBlock.bitesLeft(lush);
            }
        }
        // the looks, from many fish never put in the world
        int males = 0;
        for (int i = 0; i < 400; i++) {
            TilapiaEntity fish = new TilapiaEntity(SavannaEntities.TILAPIA.get(), level);
            fish.setUp(false, level.random);
            if (fish.look() == TilapiaEntity.BREEDING) {
                helper.fail("a fish with no nest pit wears the breeding colours");
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
            }
        }
        if (males < 150 || males > 250) {
            helper.fail(males + " males of 400 (about half wanted)");
        }
        // a bucket keeps a fish's look, youth and size
        TilapiaEntity caught = new TilapiaEntity(SavannaEntities.TILAPIA.get(), level);
        caught.setUp(true, level.random);
        ItemStack bucket = caught.getBucketItemStack();
        caught.saveToBucketTag(bucket);
        TilapiaEntity poured = new TilapiaEntity(SavannaEntities.TILAPIA.get(), level);
        poured.setUp(false, level.random);
        poured.loadFromBucketTag(bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA).copyTag());
        if (poured.look() != TilapiaEntity.YOUNG || !poured.isYoung() || Math.abs(poured.ownSize() - caught.ownSize()) > 0.001F
                || Math.abs(poured.getAttributeBaseValue(Attributes.SCALE) - caught.getAttributeBaseValue(Attributes.SCALE)) > 0.001) {
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
                    left += AlgaeBlock.bitesLeft(state);
                }
            }
            helper.assertTrue(sawGrazing[0] && full - left >= 3, "grazed " + (full - left) + " bites of " + full
                    + (sawGrazing[0] ? "" : ", no fish seen grazing"));
        });
    }

    /**
     * The catfish (2026-11-14): spawns in the river fish group in the savanna's waters, only in water at least 3 deep; a
     * hungry one hunts a wild tilapia in the pond's deep end, strikes with its mouth open and swallows it, and is fed;
     * a tilapia from a bucket (the player's own) is left alone; it is 2 blocks long at the usual size.
     */
    // (2600 ticks: a chase that fails gives up at 600 and rests 1200 before the next; with 900 a first miss failed the
    // test, BUGS 279/298)
    @GameTest(template = "arena", timeoutTicks = 2600)
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
        // the bite-off (2026-11-15): half the catches the jaws shut early, the front half falling away as remains
        boolean[] cut = {false};
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
                cut[0] = catfish.heldCut();
                wrongLook[0] |= catfish.heldLook() != wildLook || !wild.isRemoved();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(catfish.isAlive(), "the catfish died");
            helper.assertTrue(side[0] != 2, "the caught tilapia lies on the side away from where the catfish came from");
            helper.assertTrue(!wrongLook[0], "the tilapia in the catfish's mouth was not the caught one's look, or the caught one still swam");
            int want = cut[0] ? 42 : 52;
            helper.assertTrue(wild.isRemoved() && !catfish.isHolding() && held[0] >= want - 7 && held[0] <= want + 8,
                    "the caught tilapia was held in the mouth for " + held[0] + " ticks"
                            + (catfish.isHolding() ? ", still held" : "") + " (about " + want + (cut[0] ? ", bitten off" : "") + ", then gone)");
            var halves = level.getEntitiesOfClass(com.antcolonies.savanna.animal.RemainsEntity.class, catfish.getBoundingBox().inflate(4.0));
            helper.assertTrue(halves.size() == (cut[0] ? 1 : 0) && (!cut[0] || halves.get(0).kind() == com.antcolonies.savanna.animal.RemainsEntity.Kind.TILAPIA_HALF
                            && halves.get(0).stage() == 1),
                    (cut[0] ? "bitten off, " : "swallowed whole, ") + halves.size() + " remains by the catfish"
                            + (halves.isEmpty() ? "" : " (" + halves.get(0).kind() + ", stage " + halves.get(0).stage() + ")")
                            + " (a bite-off leaves 1 fresh tilapia half, a swallow none)");
            // scraps (2026-11-15): every fish eaten in the water leaves a fresh film of flecks on the bed under it
            int scraps = 0;
            for (BlockPos at : BlockPos.betweenClosed(catfish.blockPosition().offset(-6, -8, -6), catfish.blockPosition().offset(6, 4, 6))) {
                if (level.getBlockState(at).is(com.antcolonies.savanna.registry.SavannaBlocks.SCRAPS.get())) {
                    scraps++;
                }
            }
            int inPond = 0;
            for (BlockPos at : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(X0, SURFACE - 1, Z0)), helper.absolutePos(new BlockPos(X1, TOP, Z1)))) {
                if (level.getBlockState(at).is(com.antcolonies.savanna.registry.SavannaBlocks.SCRAPS.get())) {
                    inPond++;
                }
            }
            helper.assertTrue(scraps >= 1, "no scraps on the bed after the meal (a fish eaten leaves flecks); " + inPond
                    + " anywhere in the pond, the catfish at " + catfish.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
            helper.assertTrue(wild.isRemoved() && sawStrike[0] && !catfish.isHungry(),
                    "the wild tilapia " + (wild.isRemoved() ? "is gone" : "still swims") + (sawStrike[0] ? "" : ", no strike seen")
                            + (catfish.isHungry() ? ", the catfish still hungry" : ""));
            helper.assertTrue(pet.isAlive(), "the bucket tilapia was eaten");
            SavannaMod.LOGGER.info("[catfish] the tilapia was {}", cut[0] ? "bitten off, its front half left" : "swallowed whole");
        });
    }

    /**
     * The catfish comes up for a gulp of air (2026-11-18, the user's ask): a catfish on the bottom of the pond's deep
     * end that wants air swims up, gulps with its body at the surface (its snout out), and goes back down; then it waits
     * 2 to 5 minutes before the next.
     */
    @GameTest(template = "arena", timeoutTicks = 500)
    public static void aCatfishComesUpToGulpAir(GameTestHelper helper) {
        buildPond(helper);
        CatfishEntity catfish = helper.spawn(SavannaEntities.CATFISH.get(), new BlockPos(X1 - 1, SURFACE, Z0 + 3));
        catfish.setPersistenceRequired();
        catfish.makeWantAir();
        double top = helper.absolutePos(new BlockPos(0, TOP, 0)).getY() + 8 / 9.0;
        double[] gulpY = {Double.NaN};
        boolean[] down = {false};
        StringBuilder path = new StringBuilder();
        int[] tick = {0};
        helper.onEachTick(() -> {
            if (tick[0]++ % 20 == 0) {
                path.append(String.format(java.util.Locale.ROOT, " %.1f", top - catfish.getY()));
            }
            if (catfish.isGulping() && Double.isNaN(gulpY[0])) {
                gulpY[0] = catfish.getY();
            }
            if (!Double.isNaN(gulpY[0]) && !catfish.isGulping() && catfish.getY() < gulpY[0] - 1.0) {
                down[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!Double.isNaN(gulpY[0]), "the catfish never gulped (depth under the surface every second:" + path + ")");
            helper.assertTrue(top - gulpY[0] < 0.8, "the catfish gulped " + (top - gulpY[0]) + " under the surface");
            helper.assertTrue(down[0], "the catfish did not go back down after its gulp (depths:" + path + ")");
            helper.assertTrue(catfish.isAlive() && catfish.isInWater(), "the catfish is out of the water");
            SavannaMod.LOGGER.info("[catfish] gulped air after {} ticks, {} under the surface; depths every second:{}",
                    tick[0], String.format(java.util.Locale.ROOT, "%.2f", top - gulpY[0]), path);
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
     * Sand to bask on (sand banks, 2026-11-16): a crocodile in a long channel beside grass banks, wanting the sun with no
     * company near, swims past the grass to a sand bank 40 blocks off and basks there, never on the grass.
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void aCrocodileSwimsToASandBankToBask(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int floor = TOP - 4;
        for (int x = 1; x <= 59; x++) {
            for (int z = 4; z <= 26; z++) {
                boolean channel = x >= 2 && x <= 58 && z >= 12 && z <= 17;
                boolean sand = x >= 42 && x <= 54 && z >= 18 && z <= 22;
                for (int y = floor; y <= TOP + 2; y++) {
                    BlockState b;
                    if (y == floor) {
                        b = Blocks.STONE.defaultBlockState();
                    } else if (channel) {
                        b = (y <= TOP ? Blocks.WATER : Blocks.AIR).defaultBlockState();
                    } else if (y < TOP) {
                        b = (sand ? (net.minecraft.world.level.block.Block) SavannaBlocks.GOLDEN_SAND.dry().get() : Blocks.DIRT).defaultBlockState();
                    } else if (y == TOP) {
                        b = sand ? (z == 18 ? SavannaBlocks.GOLDEN_SAND.wet() : SavannaBlocks.GOLDEN_SAND.dry()).get().defaultBlockState()
                                : Blocks.GRASS_BLOCK.defaultBlockState();
                    } else {
                        b = Blocks.AIR.defaultBlockState();
                    }
                    helper.setBlock(new BlockPos(x, y, z), b);
                }
            }
        }
        helper.setDayTime(6000);
        com.antcolonies.savanna.animal.CrocodileEntity croc = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(6, TOP - 1, 14));
        croc.setPersistenceRequired();
        croc.wantSun();
        long[] at = {-1};
        helper.onEachTick(() -> {
            if (at[0] >= 0 || !croc.isBasking() || croc.isInWater() || !croc.onGround()) {
                return;
            }
            BlockState under = level.getBlockState(croc.blockPosition().below());
            if (!under.is(SavannaBlocks.GOLDEN_SAND.dry().get()) && !under.is(SavannaBlocks.GOLDEN_SAND.wet().get())) {
                helper.fail("the crocodile basks on " + under.getBlock().getName().getString() + " at "
                        + croc.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + ", not on the sand bank");
            }
            at[0] = helper.getTick();
            SavannaMod.LOGGER.info("[crocodile] basking on the sand bank {} ticks after wanting the sun", at[0]);
        });
        helper.succeedWhen(() -> helper.assertTrue(at[0] >= 0, "the crocodile has not basked on the sand bank: at "
                + croc.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + ", sand spot "
                + (croc.sandAt == null ? "none" : croc.sandAt.subtract(helper.absolutePos(BlockPos.ZERO)))));
    }

    /**
     * Mostly solitary, basking together (2026-11-15, the user's ask): the world spawns no crocodile within
     * {@code SPAWN_APART} blocks of another (an egg or command still may); a crocodile basking on the bank, and a second
     * one in the pond wanting the sun climbs out and lies beside it, side by side and facing the same way.
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void crocodilesSpawnApartAndBaskTogether(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        for (int x = X1 + 1; x <= X1 + 7; x++) {            // a bank beside the deep end, level with the pond's rim
            for (int z = Z0 - 1; z <= Z1 + 1; z++) {
                helper.setBlock(new BlockPos(x, TOP, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, TOP + 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, TOP + 2, z), Blocks.AIR);
            }
        }
        helper.setDayTime(6000);
        com.antcolonies.savanna.animal.CrocodileEntity first = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(X1 + 4, TOP + 1, Z0 + 4));
        first.setPersistenceRequired();
        first.hungerIn = 100_000;
        first.wantSun();
        BlockPos inPond = helper.absolutePos(new BlockPos(X1 - 2, SURFACE + 3, Z0 + 3));
        if (com.antcolonies.savanna.animal.CrocodileEntity.farFromOthers(level, inPond)) {
            helper.fail("the world may spawn a crocodile " + Math.round(Math.sqrt(first.distanceToSqr(inPond.getCenter()))) + " blocks from another");
        }
        if (!com.antcolonies.savanna.animal.CrocodileEntity.farFromOthers(level, inPond.offset(40, 0, 0))) {
            helper.fail("the world may not spawn a crocodile 40 blocks from another");
        }
        com.antcolonies.savanna.animal.CrocodileEntity second = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(X1 - 2, SURFACE + 3, Z0 + 3));
        second.setPersistenceRequired();
        second.hungerIn = 100_000;
        second.sunIn = 100_000;
        boolean[] called = {false};
        helper.onEachTick(() -> {
            if (!called[0] && first.isBasking() && !first.isInWater()) {
                called[0] = true;
                second.wantSun();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(first.isBasking() && !first.isInWater(), "the first crocodile is not basking on the bank");
            helper.assertTrue(second.isBasking() && !second.isInWater() && second.onGround(), "the second crocodile is not basking on the bank: at "
                    + second.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
            double apart = Math.sqrt(Math.pow(first.getX() - second.getX(), 2) + Math.pow(first.getZ() - second.getZ(), 2));
            helper.assertTrue(apart > 1.2 && apart < 2.6, "the second crocodile lies " + String.format(java.util.Locale.ROOT, "%.1f", apart)
                    + " blocks from the first (side by side is about 1.7)");
            float turn = Math.abs(net.minecraft.util.Mth.degreesDifference(first.getYRot(), second.getYRot()));
            helper.assertTrue(turn < 20.0F, "the second crocodile faces " + Math.round(turn) + " degrees off the first's way");
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
                .getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverHunter())
                .unwrap().stream().noneMatch(d -> d.type == SavannaEntities.CROCODILE.get())) {
            helper.fail("the crocodile is not in the savanna's river hunter spawn list");
        }
        // the world's own last checks on a natural spawn, in the pond's deep water (BUGS 218: a land mob's check refused water)
        com.antcolonies.savanna.animal.CrocodileEntity trial = new com.antcolonies.savanna.animal.CrocodileEntity(SavannaEntities.CROCODILE.get(), level);
        BlockPos trialAt = helper.absolutePos(new BlockPos(X1 - 2, SURFACE + 1, Z0 + 3));
        trial.moveTo(trialAt.getX() + 0.5, trialAt.getY(), trialAt.getZ() + 0.5, 0, 0);
        helper.assertTrue(level.getFluidState(trialAt).is(net.minecraft.tags.FluidTags.WATER), "the trial spot is not water");
        if (!trial.checkSpawnRules(level, net.minecraft.world.entity.MobSpawnType.NATURAL) || !trial.checkSpawnObstruction(level)) {
            helper.fail("the world would refuse a crocodile spawning in the water");
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
     * and swallows it whole; its strike calls a second hungry crocodile to the school. Then, hungry again, it takes a
     * catfish, with no death roll: whips it from side to side, tears it in two and gulps the tail half, the front half
     * left as remains (2026-11-15) that sink to the bed, lose a look's stage a third of their bites at a time, and break
     * at a hit. The bucket fish is never taken.
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
        boolean[] struck = {false}, heldFish = {false}, called = {false}, rolled = {false}, heldCatfish = {false}, shook = {false};
        com.antcolonies.savanna.animal.RemainsEntity[] left = {null};
        int[] tornAt = {0};
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
                    shook[0] |= jaws == com.antcolonies.savanna.animal.CrocodileEntity.JAWS_SHAKE;
                    if (catfish[0].isRemoved() && croc.held() == null && !croc.isHungry()) {
                        helper.assertTrue(heldCatfish[0], "the catfish was gone but never held in the jaws");
                        helper.assertTrue(shook[0], "the catfish was never shaken before it tore");
                        helper.assertTrue(!rolled[0], "the catfish was rolled, not torn");
                        var found = level.getEntitiesOfClass(com.antcolonies.savanna.animal.RemainsEntity.class,
                                croc.getBoundingBox().inflate(4.0));
                        helper.assertTrue(found.size() == 1, "the torn catfish left " + found.size() + " remains, not 1");
                        left[0] = found.get(0);
                        helper.assertTrue(left[0].kind() == com.antcolonies.savanna.animal.RemainsEntity.Kind.CATFISH_TORN
                                && left[0].stage() == 1, "the remains are " + left[0].kind() + " at stage " + left[0].stage()
                                + ", not the torn catfish's front half, fresh");
                        SavannaMod.LOGGER.info("[crocodile] tore the catfish at tick {}", helper.getTick());
                        tornAt[0] = (int) helper.getTick();
                        stage[0] = 2;
                    }
                }
                case 2 -> {
                    com.antcolonies.savanna.animal.RemainsEntity r = left[0];
                    if (r.onGround()) {
                        helper.assertTrue(r.isInWater(), "the remains came to rest at " + r.getY() + ", not on the bed under the water");
                        int all = com.antcolonies.savanna.animal.RemainsEntity.Kind.CATFISH_TORN.bites;
                        int[] stages = new int[all + 1];
                        for (int i = 0; i < all; i++) {
                            stages[i] = r.stage();
                            helper.assertTrue(r.bite(croc), "bite " + (i + 1) + " of " + all + " found no meat");
                        }
                        stages[all] = r.stage();
                        helper.assertTrue(!r.bite(croc), "a bite took meat from bare bones");
                        String seen = java.util.Arrays.toString(stages);
                        helper.assertTrue(seen.equals("[1, 1, 1, 2, 2, 2, 3, 3, 3, 4]"), "the looks went " + seen + " bite by bite");
                        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
                        r.hurt(level.damageSources().playerAttack(player), 1.0F);
                        level.getServer().getPlayerList().remove(player);   // a player left in the world changes spawning caps for later tests
                        helper.assertTrue(r.isRemoved(), "a player's hit did not break the bones");
                        stage[0] = 3;
                        helper.succeed();
                    } else {
                        helper.assertTrue(helper.getTick() - tornAt[0] < 200, "the remains never came to rest");
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

    /**
     * The river crab (M9 river life, 2026-11-15): spawns on the bed of shallow savanna water only, in the river fish
     * group; hungry, it walks over the bed to the remains before the algae beside it and picks two bits off them into its
     * mouth (two bites), then, hungry again with the remains gone, picks two pairs of strands off the lush algae.
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void aHungryCrabPicksRemainsBeforeAlgae(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        if (net.minecraft.world.entity.SpawnPlacements.getPlacementType(SavannaEntities.CRAB.get())
                != net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER) {
            helper.fail("the crab's spawn placement is not signed up");
        }
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        boolean listed = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA).getMobSettings()
                .getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverBed())
                .unwrap().stream().anyMatch(d -> d.type == SavannaEntities.CRAB.get());
        if (!listed) {
            helper.fail("the crab is not in the savanna's river bed spawn list");
        }
        BlockPos shelf = helper.absolutePos(new BlockPos(X0 + 1, TOP - 1, Z0 + 1));
        BlockPos deep = helper.absolutePos(new BlockPos(X1 - 1, SURFACE, Z1 - 1));
        if (Math.abs(deep.getY() - level.getSeaLevel()) <= 6) {
            if (!com.antcolonies.savanna.animal.CrabEntity.canSpawn(SavannaEntities.CRAB.get(), level, MobSpawnType.NATURAL, shelf, level.random)) {
                helper.fail("a crab may not spawn on the shallow shelf");
            }
            if (com.antcolonies.savanna.animal.CrabEntity.canSpawn(SavannaEntities.CRAB.get(), level, MobSpawnType.NATURAL, deep, level.random)) {
                helper.fail("a crab may spawn under 7 blocks of water");
            }
        }
        BlockPos algae = new BlockPos(X0 + 3, TOP - 1, Z0 + 2);
        helper.setBlock(algae, SavannaBlocks.ALGAE.get().defaultBlockState().setValue(AlgaeBlock.GROWTH, AlgaeBlock.LUSH)
                .setValue(AlgaeBlock.STRANDS, AlgaeBlock.fullStrands(AlgaeBlock.LUSH)));
        com.antcolonies.savanna.animal.RemainsEntity remains = com.antcolonies.savanna.animal.RemainsEntity.leave(level,
                com.antcolonies.savanna.animal.RemainsEntity.Kind.TILAPIA, helper.absolutePos(new BlockPos(X0 + 3, TOP - 1, Z1 - 1)).getBottomCenter(), 0, 1.0F);
        if (remains == null) {
            helper.fail("no remains made");
            return;
        }
        int meat = remains.bitesLeft();
        com.antcolonies.savanna.animal.CrabEntity crab = helper.spawn(SavannaEntities.CRAB.get(), new BlockPos(X0 + 1, TOP - 1, Z0 + 3));
        crab.setPersistenceRequired();
        crab.makeHungry();
        int strands = AlgaeBlock.fullStrands(AlgaeBlock.LUSH);
        boolean[] remainsDone = {false};
        boolean[] sawPick = {false, false};
        helper.onEachTick(() -> {
            byte doing = crab.doing();
            sawPick[0] |= doing == com.antcolonies.savanna.animal.CrabEntity.PICK_MEAT;
            sawPick[1] |= doing == com.antcolonies.savanna.animal.CrabEntity.PICK_ALGAE;
            if (!remainsDone[0] && crab.isFed()) {
                remainsDone[0] = true;
                if (meat - remains.bitesLeft() != 2) {
                    helper.fail("the crab took " + (meat - remains.bitesLeft()) + " bites off the remains (2 in one pick)");
                }
                if (helper.getBlockState(algae).getValue(AlgaeBlock.STRANDS) != strands) {
                    helper.fail("the crab picked at the algae before the remains");
                }
                remains.discard();
                crab.makeHungry();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(remainsDone[0] && sawPick[0], "the crab has not picked at the remains: at "
                    + crab.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
            helper.assertTrue(sawPick[1] && crab.isFed(), "the crab has not picked at the algae: at "
                    + crab.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
            helper.assertBlockProperty(algae, AlgaeBlock.STRANDS, strands - 2);
        });
    }

    /**
     * The river snail (M9 river life, 2026-11-16): spawns on the bed of shallow savanna water only, in the river fish
     * group; hungry, it creeps to the lush algae and rasps one bite (a strand) off it, then is fed; a crab coming
     * up beside it makes it pull into its shell, and once the crab is gone it peeks out again.
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void aSnailGrazesAlgaeAndHidesFromACrab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        if (net.minecraft.world.entity.SpawnPlacements.getPlacementType(SavannaEntities.SNAIL.get())
                != net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER) {
            helper.fail("the snail's spawn placement is not signed up");
        }
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        boolean listed = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA).getMobSettings()
                .getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverBed())
                .unwrap().stream().anyMatch(d -> d.type == SavannaEntities.SNAIL.get());
        if (!listed) {
            helper.fail("the snail is not in the savanna's river bed spawn list");
        }
        BlockPos shelf = helper.absolutePos(new BlockPos(X0 + 1, TOP - 1, Z0 + 1));
        BlockPos deep = helper.absolutePos(new BlockPos(X1 - 1, SURFACE, Z1 - 1));
        if (Math.abs(deep.getY() - level.getSeaLevel()) <= 6) {
            if (!com.antcolonies.savanna.animal.SnailEntity.canSpawn(SavannaEntities.SNAIL.get(), level, MobSpawnType.NATURAL, shelf, level.random)) {
                helper.fail("a snail may not spawn on the shallow shelf");
            }
            if (com.antcolonies.savanna.animal.SnailEntity.canSpawn(SavannaEntities.SNAIL.get(), level, MobSpawnType.NATURAL, deep, level.random)) {
                helper.fail("a snail may spawn under 7 blocks of water");
            }
        }
        BlockPos algae = new BlockPos(X0 + 3, TOP - 1, Z0 + 2);
        helper.setBlock(algae, SavannaBlocks.ALGAE.get().defaultBlockState().setValue(AlgaeBlock.GROWTH, AlgaeBlock.LUSH)
                .setValue(AlgaeBlock.STRANDS, AlgaeBlock.fullStrands(AlgaeBlock.LUSH)));
        int strands = AlgaeBlock.fullStrands(AlgaeBlock.LUSH);
        com.antcolonies.savanna.animal.SnailEntity snail = helper.spawn(SavannaEntities.SNAIL.get(), new BlockPos(X0 + 1, TOP - 1, Z0 + 2));
        snail.setPersistenceRequired();
        snail.makeHungry();
        // 0 grazing, 1 fed: crab sent, 2 hidden: crab gone, 3 peeked out
        int[] stage = {0};
        boolean[] sawGraze = {false};
        com.antcolonies.savanna.animal.CrabEntity[] crab = {null};
        long[] at = {0};
        helper.onEachTick(() -> {
            byte doing = snail.doing();
            sawGraze[0] |= doing == com.antcolonies.savanna.animal.SnailEntity.GRAZE;
            switch (stage[0]) {
                case 0 -> {
                    if (snail.isFed() && sawGraze[0]) {
                        if (helper.getBlockState(algae).getValue(AlgaeBlock.STRANDS) != strands - 1) {
                            helper.fail("the snail took " + (strands - helper.getBlockState(algae).getValue(AlgaeBlock.STRANDS))
                                    + " strands off the algae (1 in one bite)");
                        }
                        BlockPos near = snail.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).east();
                        crab[0] = helper.spawn(SavannaEntities.CRAB.get(), near);
                        crab[0].setPersistenceRequired();
                        crab[0].setNoAi(true);
                        stage[0] = 1;
                    }
                }
                case 1 -> {
                    if (snail.isHidden()) {
                        crab[0].discard();
                        stage[0] = 2;
                        at[0] = helper.getTick();
                    }
                }
                case 2 -> {
                    if (doing == com.antcolonies.savanna.animal.SnailEntity.PEEK) {
                        if (helper.getTick() - at[0] < 90) {
                            helper.fail("the snail peeked out " + (helper.getTick() - at[0]) + " ticks after the crab left (5 to 10 s)");
                        }
                        stage[0] = 3;
                    }
                }
                default -> { }
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(stage[0] == 3, "the snail got to stage " + stage[0]
                + " (0 grazing, 1 hiding from the crab, 2 peeking out) at "
                + snail.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + ", doing " + snail.doing() + ", fed " + snail.isFed()
                + ", grazed " + sawGraze[0] + ", strands " + helper.getBlockState(algae).getValue(AlgaeBlock.STRANDS) + " of " + strands
                + ", path " + (snail.getNavigation().getPath() == null ? "none" : snail.getNavigation().getPath().getEndNode())));
    }

    /**
     * Crabs eat snails (M9 river life, 2026-11-16): a hungry crab with a snail near it and nothing else to eat walks to
     * it, grabs it (the snail is gone, the crack clip plays), and puts the broken shell down on the bed, holding water,
     * then is fed.
     */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void aCrabCracksASnailAndLeavesItsShell(GameTestHelper helper) {
        buildPond(helper);
        com.antcolonies.savanna.animal.SnailEntity snail = helper.spawn(SavannaEntities.SNAIL.get(), new BlockPos(X0 + 1, TOP - 1, Z0 + 2));
        snail.setPersistenceRequired();
        com.antcolonies.savanna.animal.CrabEntity crab = helper.spawn(SavannaEntities.CRAB.get(), new BlockPos(X0 + 4, TOP - 1, Z0 + 2));
        crab.setPersistenceRequired();
        crab.makeHungry();
        boolean[] cracked = {false};
        helper.onEachTick(() -> {
            if (crab.doing() == com.antcolonies.savanna.animal.CrabEntity.CRACK) {
                cracked[0] = true;
                if (snail.isAlive()) {
                    helper.fail("the crab cracks a shell while the snail is still there");
                }
            }
        });
        helper.succeedWhen(() -> {
            BlockPos shell = null;
            for (BlockPos at : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(X0, TOP - 3, Z0)), helper.absolutePos(new BlockPos(X1, TOP + 1, Z1)))) {
                if (helper.getLevel().getBlockState(at).is(SavannaBlocks.CRAB_SHELL.get())) {
                    shell = at.immutable();
                    break;
                }
            }
            helper.assertTrue(cracked[0] && shell != null && crab.isFed(), "cracked " + cracked[0] + ", shell " + shell
                    + ", crab fed " + crab.isFed() + ", snail alive " + snail.isAlive() + " at "
                    + snail.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + ", crab at "
                    + crab.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + " doing " + crab.doing());
            BlockState there = helper.getLevel().getBlockState(shell);
            helper.assertTrue(there.getValue(com.antcolonies.savanna.block.CrabShellBlock.WATERLOGGED)
                    && there.getValue(com.antcolonies.savanna.block.CrabShellBlock.AGE) == 0, "the shell is " + there + " (fresh, holding water)");
        });
    }

    // --- the bed dwellers spawning by themselves (2026-11-16) -------------------------------------------------------------
    private static final int[] CHANNEL_DEPTHS = {1, 2, 3, 4, 5, 6, 7, 7, 6, 5, 4, 3, 2, 1}; // a river's cross-section, bank to bank
    private static final int CX0 = 20, CZ0 = 20, CZ1 = 27;

    /**
     * A stretch of river over the arena, its water's top at the world's sea level (where the river animals' height rules
     * let them spawn; the arena itself lies far below): sloping beds from 1 block of water at the banks to 7 in the
     * channel. Built with absolute positions over the arena's own ground, and taken away again by {@link #clearChannel}.
     * Returns the water's top.
     */
    private static int buildChannel(GameTestHelper helper, boolean clear) {
        ServerLevel level = helper.getLevel();
        int top = level.getSeaLevel() - 1;
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        for (int i = -1; i <= CHANNEL_DEPTHS.length; i++) {
            for (int dz = -1; dz <= CZ1 - CZ0 + 1; dz++) {
                boolean wall = i < 0 || i >= CHANNEL_DEPTHS.length || dz < 0 || dz > CZ1 - CZ0;
                int depth = wall ? 0 : CHANNEL_DEPTHS[i];
                for (int y = top - 8; y <= top + 2; y++) {
                    BlockState state = clear ? Blocks.AIR.defaultBlockState()
                            : (y <= top - depth ? Blocks.STONE : y <= top ? Blocks.WATER : Blocks.AIR).defaultBlockState();
                    level.setBlock(new BlockPos(corner.getX() + i, y, corner.getZ() + dz), state, 2);
                }
            }
        }
        return top;
    }

    /**
     * Pans and oxbows (oxbows and pans step 5, 2026-11-22; the spawning check list): a pond 2 deep painted open savanna (a
     * pan) up on the plain, 9 blocks over the river's level (higher than the river animals' old limit of 6), then painted
     * floodplain (an oxbow) 2 over the river's level, as the oxbows lie. Every check Minecraft's spawner makes, counted over the pond and its bank: in the pan snails,
     * bullfrogs and fly clouds and none of the river's fish, crocodiles, crabs or monitors; in the oxbow the river's
     * animals too.
     */
    @GameTest(template = "arena", timeoutTicks = 40)
    public static void pansKeepSnailsFrogsAndFliesOxbowsTheRiversAnimals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(new BlockPos(20, 0, 20));
        int w = 14, d = 12;
        // the pond with its surface at this height (its land's grass level with the water)
        java.util.function.IntConsumer build = surface -> {
            for (int i = 0; i < w; i++) {
                for (int k = 0; k < d; k++) {
                    boolean pond = i >= 2 && i < w - 2 && k >= 2 && k < d - 2;
                    for (int y = level.getSeaLevel() - 6; y <= level.getSeaLevel() + 16; y++) {
                        BlockState state = y > surface ? Blocks.AIR.defaultBlockState()
                                : pond && y >= surface - 1 ? Blocks.WATER.defaultBlockState()
                                : y == surface ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                        level.setBlock(new BlockPos(corner.getX() + i, y, corner.getZ() + k), state, 2);
                    }
                }
            }
        };
        var kinds = java.util.List.<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>>of(SavannaEntities.SNAIL.get(),
                SavannaEntities.BULLFROG.get(), SavannaEntities.FLY_CLOUD.get(), SavannaEntities.TILAPIA.get(), SavannaEntities.BARB.get(),
                SavannaEntities.CATFISH.get(), SavannaEntities.CROCODILE.get(), SavannaEntities.CRAB.get(), SavannaEntities.MONITOR.get());
        java.util.function.BiFunction<String, Integer, int[]> count = (biome, surface) -> {
            build.accept(surface);
            BlockPos far = corner.offset(w, 0, d);
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput().withLevel(level),
                    "fillbiome " + (corner.getX() - 4) + " " + (surface - 8) + " " + (corner.getZ() - 4) + " " + (far.getX() + 4) + " " + (surface + 8) + " "
                            + (far.getZ() + 4) + " " + biome);
            int[] spots = new int[kinds.size()];
            for (int i = 0; i < w; i++) {
                for (int k = 0; k < d; k++) {
                    for (int y = surface - 2; y <= surface + 2; y++) {
                        BlockPos at = new BlockPos(corner.getX() + i, y, corner.getZ() + k);
                        for (int n = 0; n < kinds.size(); n++) {
                            spots[n] += worldWouldSpawn(level, kinds.get(n), at) ? 1 : 0;
                        }
                    }
                }
            }
            return spots;
        };
        int[] pan = count.apply("minecraft:savanna", level.getSeaLevel() - 1 + 9);                       // a pan up on the plain
        int[] oxbow = count.apply(SavannaMod.MODID + ":savanna_floodplain", level.getSeaLevel() - 1 + 2);  // an oxbow 2 over the river
        String report = "spots in the pan / the oxbow: " + java.util.stream.IntStream.range(0, kinds.size())
                .mapToObj(n -> kinds.get(n).getDescriptionId().replace("entity.antcolonies.", "") + " " + pan[n] + "/" + oxbow[n])
                .collect(java.util.stream.Collectors.joining(", "));
        SavannaMod.LOGGER.info("[pans] {}", report);
        for (int n = 0; n < 3; n++) {
            helper.assertTrue(pan[n] > 0, "no " + kinds.get(n).getDescriptionId() + " in a pan up on the plain: " + report);
        }
        for (int n = 3; n < kinds.size(); n++) {
            helper.assertTrue(pan[n] == 0, kinds.get(n).getDescriptionId() + " in a pan: " + report);
        }
        helper.assertTrue(oxbow[3] > 0 && oxbow[4] > 0 && oxbow[6] > 0, "the oxbow lost the river's fish or crocodiles: " + report);
        helper.succeed();
    }

    /**
     * Whether the world itself would spawn this kind here (every check Minecraft's natural spawner makes at a spot but the
     * biome's list and the distance to a player): the placement, the kind's own rule (with the quiet-savanna gate), room
     * for its box, then the mob's own last checks.
     */
    static boolean worldWouldSpawn(ServerLevel level, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type, BlockPos at) {
        if (!net.minecraft.world.entity.SpawnPlacements.isSpawnPositionOk(type, level, at)
                || !net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, at, level.random)
                || !level.noCollision(type.getSpawnAABB(at.getX() + 0.5, at.getY(), at.getZ() + 0.5))) {
            return false;
        }
        net.minecraft.world.entity.Mob trial = type.create(level);
        if (trial == null) {
            return false;
        }
        trial.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        boolean ok = net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(trial, level, MobSpawnType.NATURAL);
        trial.discard();
        return ok;
    }

    /**
     * Crabs and snails spawn by themselves on shallow river beds (the user's report 2026-11-16: never seen without an egg).
     * Minecraft's natural spawner draws one random height per try in a column and keeps it; a bed dweller on the old rule
     * passed only at the one height just over the bed. The test counts, over a river's cross-section, the spots each kind
     * passes every world check at (the share of the spawner's draws it can use), and spawns a crab and snails the world's
     * way to see them settle on the bed.
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void crabsAndSnailsSpawnOnShallowBedsByThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int top = buildChannel(helper, false);
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        var kinds = java.util.List.<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>>of(
                SavannaEntities.TILAPIA.get(), SavannaEntities.CATFISH.get(), SavannaEntities.CRAB.get(), SavannaEntities.SNAIL.get());
        // 1. every kind's spots over the channel (the share of the spawner's draws it can use)
        int[] total = new int[kinds.size()];
        int[] crabs = new int[CHANNEL_DEPTHS.length], snails = new int[CHANNEL_DEPTHS.length];
        StringBuilder byDepth = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            int[] column = new int[kinds.size()];
            for (int dz = 0; dz <= CZ1 - CZ0; dz++) {
                for (int y = top - 8; y <= top + 1; y++) {
                    BlockPos at = new BlockPos(corner.getX() + i, y, corner.getZ() + dz);
                    for (int k = 0; k < kinds.size(); k++) {
                        if (worldWouldSpawn(level, kinds.get(k), at)) {
                            column[k]++;
                            total[k]++;
                        }
                    }
                }
            }
            crabs[i] = column[2];
            snails[i] = column[3];
            if (i < CHANNEL_DEPTHS.length / 2) {
                byDepth.append(" depth ").append(CHANNEL_DEPTHS[i]).append(": crab ").append(column[2]).append(" snail ").append(column[3]);
            }
        }
        SavannaMod.LOGGER.info("[bed spawns] spots the world would spawn at over the channel: tilapia {}, catfish {}, crab {}, snail {};{}",
                total[0], total[1], total[2], total[3], byDepth);
        // 2. the world's own spawn, drawn at the water's top over a shallow bed: set down on the bed
        int[][] trials = {{3, 4}, {1, 2}};             // {column, its depth}: the crab over 4 blocks of water, the snail over 2
        for (int t = 0; t < 2; t++) {
            var type = t == 0 ? SavannaEntities.CRAB.get() : SavannaEntities.SNAIL.get();
            BlockPos drawn = new BlockPos(corner.getX() + trials[t][0], top, corner.getZ() + 3);
            net.minecraft.world.entity.Mob mob = type.create(level);
            mob.moveTo(drawn.getX() + 0.5, drawn.getY(), drawn.getZ() + 0.5, 0, 0);
            if (!net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(mob, level, MobSpawnType.NATURAL)) {
                helper.fail("the world refuses a " + type.toShortString() + " at the water's top over a shallow bed");
            }
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(drawn), MobSpawnType.NATURAL, null);
            int bed = top - trials[t][1] + 1;
            if (mob.getBlockY() != bed) {
                helper.fail("a " + type.toShortString() + " spawned by the world stays at y " + mob.getBlockY() + ", not on the bed at " + bed);
            }
            mob.discard();
        }
        buildChannel(helper, true);
        // 3. on the channel's spots: everywhere in the water of a shallow column, never over deeper water
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            int want = CHANNEL_DEPTHS[i] <= com.antcolonies.savanna.animal.RiverBed.SHALLOW ? CHANNEL_DEPTHS[i] * (CZ1 - CZ0 + 1) : 0;
            if (crabs[i] != want || snails[i] != want) {
                wrong.append(" depth ").append(CHANNEL_DEPTHS[i]).append(": crab ").append(crabs[i]).append(", snail ")
                        .append(snails[i]).append(", want ").append(want);
            }
        }
        if (!wrong.isEmpty()) {
            helper.fail("bed dwellers spawn at the wrong spots:" + wrong);
        }
        // 4. their own spawning group, listed in the savanna's waters (not the fish group, which the tilapia fill)
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var river = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_river"));
        for (var type : java.util.List.of(SavannaEntities.CRAB.get(), SavannaEntities.SNAIL.get())) {
            if (type.getCategory() != com.antcolonies.savanna.registry.SavannaMobCategories.riverBed()) {
                helper.fail(type.toShortString() + " is in the " + type.getCategory().getName() + " group, not the river bed group");
            }
            for (var key : java.util.List.of(net.minecraft.world.level.biome.Biomes.SAVANNA, river)) {
                var settings = biomes.getOrThrow(key).getMobSettings();
                if (settings.getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverBed()).unwrap().stream().noneMatch(d -> d.type == type)) {
                    helper.fail(type.toShortString() + " is not in " + key.location() + "'s river bed spawn list");
                }
                if (settings.getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverFish()).unwrap().stream().anyMatch(d -> d.type == type)) {
                    helper.fail(type.toShortString() + " is still in " + key.location() + "'s river fish spawn list");
                }
            }
        }
        helper.succeed();
    }

    /**
     * Bull sharks spawn by themselves (2026-11-22, the spawning check list): over a river's cross-section at sea level the
     * world's own checks pass only in water at least {@code SPAWN_DEPTH} (4) deep, at any height of its column, and never
     * over the shallower edges; a spawn is set into the middle of the water, 5 to 7 blocks long; none comes within
     * {@code SPAWN_APART} blocks of another; the kind is in its own group (cap 2), listed in the savanna's waters; and the
     * river zones give it the coast's whole cap, half of it near inland, none anywhere else. (No other test of this batch
     * has a shark: a shark of another test within 80 blocks would refuse the spawns.)
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void bullSharksSpawnInDeepRiverWaterByThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int top = buildChannel(helper, false);
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        var sharkType = SavannaEntities.BULL_SHARK.get();
        // 1. its spots over the channel by the column's depth (the crocodile's for comparison)
        int[] byColumn = new int[CHANNEL_DEPTHS.length];
        int spots = 0, crocs = 0;
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            for (int dz = 0; dz <= CZ1 - CZ0; dz++) {
                for (int y = top - 8; y <= top + 1; y++) {
                    BlockPos at = new BlockPos(corner.getX() + i, y, corner.getZ() + dz);
                    if (worldWouldSpawn(level, sharkType, at)) {
                        byColumn[i]++;
                        spots++;
                    }
                    crocs += worldWouldSpawn(level, SavannaEntities.CROCODILE.get(), at) ? 1 : 0;
                }
            }
        }
        SavannaMod.LOGGER.info("[shark spawns] spots the world would spawn at over the channel: bull shark {}, crocodile {}; by column {} (depths {})",
                spots, crocs, java.util.Arrays.toString(byColumn), java.util.Arrays.toString(CHANNEL_DEPTHS));
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            if (CHANNEL_DEPTHS[i] < com.antcolonies.savanna.animal.BullSharkEntity.SPAWN_DEPTH && byColumn[i] > 0) {
                wrong.append(" column ").append(i).append(" (").append(CHANNEL_DEPTHS[i]).append(" deep): ").append(byColumn[i]);
            }
        }
        helper.assertTrue(spots > 0, "the world would spawn a bull shark nowhere over the river");
        helper.assertTrue(wrong.isEmpty(), "bull sharks would spawn over shallow water:" + wrong);
        // 2. the world's own spawn drawn at the top of the deepest column: set into the middle of the water
        BlockPos drawn = new BlockPos(corner.getX() + 6, top, corner.getZ() + 4);
        var shark = sharkType.create(level);
        shark.moveTo(drawn.getX() + 0.5, drawn.getY(), drawn.getZ() + 0.5, 0, 0);
        helper.assertTrue(net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(shark, level, MobSpawnType.NATURAL),
                "the world refuses a bull shark at the top of deep water");
        shark.finalizeSpawn(level, level.getCurrentDifficultyAt(drawn), MobSpawnType.NATURAL, null);
        int bed = top - CHANNEL_DEPTHS[6] + 1;
        double back = shark.getY() + 1.5 * shark.getScale();
        helper.assertTrue(shark.getY() >= bed && back <= top + 0.9 - 0.4, "a spawned bull shark lies at y " + String.format("%.2f", shark.getY())
                + " (its back at " + String.format("%.2f", back) + "), not in the middle of water from " + bed + " to " + (top + 1));
        helper.assertTrue(shark.length() >= 5.0F && shark.length() <= 7.5F && shark.length() * 2 == Math.round(shark.length() * 2),
                "a spawned bull shark is " + shark.length() + " blocks long, not one of 5.0 to 7.5 in half blocks");
        // 3. none within SPAWN_APART of another
        level.addFreshEntity(shark);
        BlockPos other = new BlockPos(corner.getX() + 8, top - 2, corner.getZ() + 1);
        helper.assertFalse(worldWouldSpawn(level, sharkType, other), "a bull shark would spawn " + (int) Math.sqrt(other.distSqr(drawn)) + " blocks from another");
        shark.discard();
        buildChannel(helper, true);
        // 4. its own group (cap 2), listed in the savanna's waters
        helper.assertTrue(sharkType.getCategory() == com.antcolonies.savanna.registry.SavannaMobCategories.bullShark()
                && sharkType.getCategory().getMaxInstancesPerChunk() == 2, "the bull shark is in the " + sharkType.getCategory().getName() + " group");
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        for (var key : java.util.List.of(net.minecraft.world.level.biome.Biomes.SAVANNA, com.antcolonies.savanna.worldgen.RiverZones.RIVER)) {
            var listed = biomes.getOrThrow(key).getMobSettings().getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.bullShark()).unwrap();
            helper.assertTrue(listed.size() == 1 && listed.get(0).type == sharkType, "the bull shark group's list in " + key.location() + ": " + listed);
        }
        // 5. its river zones: the coast's whole cap, one near inland (half of 2), none anywhere else
        helper.assertTrue(com.antcolonies.savanna.worldgen.RiverZones.allows(sharkType, com.antcolonies.savanna.worldgen.RiverZones.Zone.COAST, 1),
                "no second bull shark at the coast");
        helper.assertTrue(com.antcolonies.savanna.worldgen.RiverZones.allows(sharkType, com.antcolonies.savanna.worldgen.RiverZones.Zone.NEAR, 0)
                && !com.antcolonies.savanna.worldgen.RiverZones.allows(sharkType, com.antcolonies.savanna.worldgen.RiverZones.Zone.NEAR, 1),
                "near inland is not one bull shark at most");
        for (var z : java.util.List.of(com.antcolonies.savanna.worldgen.RiverZones.Zone.MID, com.antcolonies.savanna.worldgen.RiverZones.Zone.FAR,
                com.antcolonies.savanna.worldgen.RiverZones.Zone.OXBOW, com.antcolonies.savanna.worldgen.RiverZones.Zone.PAN)) {
            helper.assertFalse(com.antcolonies.savanna.worldgen.RiverZones.allows(sharkType, z, 0), "a bull shark allowed " + z);
        }
        helper.succeed();
    }

    // --- the Nile monitor (2026-11-17) ---------------------------------------------------------------------------------
    private static final int MX0 = 16, MX1 = 40, MZ0 = 34, MZ1 = 44;    // a stone bank to hunt on, clear of the pond and channel

    /**
     * A hungry Nile monitor goes to a snail, crushes it (the broken shell left where it was) and is fed; hungry again, it
     * takes bites off remains lying on the bank.
     */
    @GameTest(template = "arena", timeoutTicks = 1600)
    public static void aHungryMonitorCrushesASnailAndBitesRemains(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = MX0; x <= MX1; x++) {
            for (int z = MZ0; z <= MZ1; z++) {
                helper.setBlock(new BlockPos(x, TOP, z), Blocks.STONE);
                for (int y = TOP + 1; y <= TOP + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        com.antcolonies.savanna.animal.MonitorEntity monitor = helper.spawn(SavannaEntities.MONITOR.get(), new BlockPos(MX0 + 3, TOP + 1, MZ0 + 5));
        monitor.setPersistenceRequired();
        monitor.makeHungry();
        com.antcolonies.savanna.animal.SnailEntity snail = helper.spawn(SavannaEntities.SNAIL.get(), new BlockPos(MX0 + 12, TOP + 1, MZ0 + 5));
        snail.setPersistenceRequired();
        BlockPos snailAt = snail.blockPosition();
        com.antcolonies.savanna.animal.RemainsEntity[] remains = {null};
        int[] meat = {0};
        boolean[] sawBite = {false};
        helper.onEachTick(() -> {
            sawBite[0] |= monitor.doing() == com.antcolonies.savanna.animal.MonitorEntity.BITE;
            if (remains[0] == null && !snail.isAlive() && monitor.isFed()) {
                remains[0] = com.antcolonies.savanna.animal.RemainsEntity.leave(level, com.antcolonies.savanna.animal.RemainsEntity.Kind.CATFISH,
                        helper.absolutePos(new BlockPos(MX1 - 4, TOP + 1, MZ0 + 5)).getBottomCenter(), 0, 1.0F);
                if (remains[0] == null) {
                    helper.fail("no remains made");
                    return;
                }
                meat[0] = remains[0].bitesLeft();
                monitor.makeHungry();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!snail.isAlive() && sawBite[0], "the monitor has not eaten the snail: monitor at "
                    + monitor.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + " doing " + monitor.doing() + ", snail at "
                    + snail.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
            BlockPos shell = null;
            for (BlockPos at : BlockPos.betweenClosed(snailAt.offset(-6, -1, -6), snailAt.offset(6, 2, 6))) {
                if (level.getBlockState(at).is(SavannaBlocks.CRAB_SHELL.get())) {
                    shell = at.immutable();
                    break;
                }
            }
            helper.assertTrue(shell != null, "the crushed snail left no shell");
            helper.assertTrue(remains[0] != null && meat[0] - remains[0].bitesLeft() >= 2 && monitor.isFed(), "the monitor has not fed on the remains: bites "
                    + (remains[0] == null ? "-" : meat[0] - remains[0].bitesLeft()) + ", monitor at "
                    + monitor.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + " doing " + monitor.doing());
        });
    }

    /**
     * A hungry monitor afloat dives for a snail on the bed under it (2026-11-19, the user's report of a monitor stuck in the
     * river: it hung at the surface over a snail 3 blocks down, its jaws never reaching, and picked the same snail again
     * every time it gave up). The report's scene: water 4 deep, the monitor afloat, the snail on the bed 2 blocks across.
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aHungryMonitorDivesForASnailOnTheBed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int bed = TOP - 4;
        for (int x = MX0; x <= MX0 + 12; x++) {
            for (int z = MZ0; z <= MZ1; z++) {
                boolean wall = x == MX0 || x == MX0 + 12 || z == MZ0 || z == MZ1;
                helper.setBlock(new BlockPos(x, bed, z), Blocks.STONE);
                for (int y = bed + 1; y <= TOP + 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), wall && y <= TOP + 1 ? Blocks.STONE : y <= TOP ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
        com.antcolonies.savanna.animal.SnailEntity snail = helper.spawn(SavannaEntities.SNAIL.get(), new BlockPos(MX0 + 5, bed + 1, MZ0 + 5));
        snail.setPersistenceRequired();
        com.antcolonies.savanna.animal.MonitorEntity monitor = helper.spawn(SavannaEntities.MONITOR.get(),
                new net.minecraft.world.phys.Vec3(MX0 + 7.5, TOP + 0.4, MZ0 + 5.5));
        monitor.setPersistenceRequired();
        monitor.makeHungry();
        helper.succeedWhen(() -> helper.assertTrue(!snail.isAlive() && monitor.isFed(), "the monitor has not dived for the snail: monitor at "
                + String.format("%.2f", monitor.getY() - helper.absolutePos(BlockPos.ZERO).getY()) + " up, in water " + monitor.isInWater()
                + ", doing " + monitor.doing() + ", snail at " + snail.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO))));
    }

    // --- moves of their own, cut short and at an angle (the Animal Maker Engine step B, 2026-11-20) --------------------
    /** The monitor's climbing ground: stone at TOP, a one-block step from x = STEP_X on; a monitor sent up it from x = MX0 + 3. */
    private static final int STEP_X = MX0 + 10;

    private static com.antcolonies.savanna.animal.MonitorEntity monitorBeforeStep(GameTestHelper helper, double x0, int z, float yaw) {
        for (int x = MX0; x <= MX1; x++) {
            for (int zz = MZ0; zz <= MZ1; zz++) {
                helper.setBlock(new BlockPos(x, TOP, zz), Blocks.STONE);
                helper.setBlock(new BlockPos(x, TOP + 1, zz), x >= STEP_X ? Blocks.STONE : Blocks.AIR);
                for (int y = TOP + 2; y <= TOP + 5; y++) {
                    helper.setBlock(new BlockPos(x, y, zz), Blocks.AIR);
                }
            }
        }
        com.antcolonies.savanna.animal.MonitorEntity monitor = helper.spawn(SavannaEntities.MONITOR.get(),
                new net.minecraft.world.phys.Vec3(x0, TOP + 1, z + 0.5));
        monitor.setPersistenceRequired();
        monitor.setYRot(yaw);
        monitor.yBodyRot = yaw;
        monitor.yHeadRot = yaw;
        return monitor;
    }

    /** Sends it on up the step (again whenever its route is done or another goal took it, until it is on top). */
    private static void upTheStep(GameTestHelper helper, com.antcolonies.savanna.animal.MonitorEntity monitor, int z) {
        if (monitor.climb() == null && monitor.getNavigation().isDone() && monitor.hurtTime == 0) {
            BlockPos goal = helper.absolutePos(new BlockPos(STEP_X + 6, TOP + 2, z));
            monitor.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
        }
    }

    /**
     * A move cut short from any tick ends cleanly: a monitor hurt half way up its climb is set down on the ground at
     * once (not held in the air on the climb's path, not inside the step), its move over, and walks on afterwards.
     */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void aMonitorHurtMidClimbIsSetDownAndWalksOn(GameTestHelper helper) {
        com.antcolonies.savanna.animal.MonitorEntity monitor = monitorBeforeStep(helper, MX0 + 3.5, MZ0 + 5, -90.0F);
        ServerLevel level = helper.getLevel();
        long[] hurtAt = {-1};
        double[] landed = {Double.NaN, 0, 0};
        helper.onEachTick(() -> {
            if (hurtAt[0] < 0) {
                upTheStep(helper, monitor, MZ0 + 5);
                com.antcolonies.entity.body.BlockMoves.Play play = monitor.climb();
                if (play != null && com.antcolonies.savanna.animal.MonitorEntity.MOVES.ticks(play, level.getGameTime()) > 40) {
                    monitor.hurt(monitor.damageSources().generic(), 1.0F);
                    hurtAt[0] = level.getGameTime();
                    helper.assertTrue(monitor.climb() == null, "the hurt did not end the climb");
                }
                return;
            }
            long since = level.getGameTime() - hurtAt[0];
            helper.assertTrue(monitor.climb() == null || since > 20, "a move started again at once after the hurt");
            helper.assertTrue(level.noCollision(monitor, monitor.getBoundingBox().deflate(1.0E-3)), "the monitor is inside a block "
                    + since + " ticks after the hurt, at " + monitor.position());
            if (Double.isNaN(landed[0]) && monitor.onGround()) {
                landed[0] = monitor.getX();
                landed[2] = monitor.getZ();
                landed[1] = since;
            }
            helper.assertTrue(!Double.isNaN(landed[0]) || since < 30, "30 ticks after the hurt the monitor is still not on the ground, at "
                    + monitor.position());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!Double.isNaN(landed[0]), "not hurt and landed yet (hurt at " + hurtAt[0] + ", on a move "
                    + (monitor.climb() != null) + ", at " + monitor.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(
                    helper.absolutePos(BlockPos.ZERO))) + ", doing " + monitor.doing() + ", on ground " + monitor.onGround() + ")");
            double walked = Math.hypot(monitor.getX() - landed[0], monitor.getZ() - landed[2]);
            helper.assertTrue(walked > 1.0, "the monitor has not walked on since it was set down (" + String.format("%.2f", walked) + " blocks)");
        });
    }

    /**
     * The block a climbing monitor is on taken away under it mid-move (broken by a player, say): the move ends and the
     * monitor falls to the ground below, never left floating on the climb's path.
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aMonitorWhoseStepIsBrokenMidClimbFallsBack(GameTestHelper helper) {
        com.antcolonies.savanna.animal.MonitorEntity monitor = monitorBeforeStep(helper, MX0 + 3.5, MZ0 + 5, -90.0F);
        ServerLevel level = helper.getLevel();
        int floor = helper.absolutePos(new BlockPos(0, TOP + 1, 0)).getY();
        long[] brokenAt = {-1};
        double[] seen = {0, -1, 0};     // ticks on a move, the highest it got on one (over the floor), its size
        helper.onEachTick(() -> {
            if (brokenAt[0] < 0) {
                upTheStep(helper, monitor, MZ0 + 5);
                seen[2] = monitor.ownSize();
                if (monitor.climb() != null) {
                    seen[0]++;
                    seen[1] = Math.max(seen[1], monitor.getY() - floor);
                }
                if (monitor.climb() != null && monitor.getY() > floor + 0.5) {     // its box on top of the step
                    for (int x = STEP_X; x <= STEP_X + 4; x++) {
                        for (int z = MZ0; z <= MZ1; z++) {
                            helper.setBlock(new BlockPos(x, TOP + 1, z), Blocks.AIR);
                        }
                    }
                    brokenAt[0] = level.getGameTime();
                }
                return;
            }
            long since = level.getGameTime() - brokenAt[0];
            helper.assertTrue(since < 2 || monitor.climb() == null || monitor.getY() < floor + 0.5,
                    "the monitor is still on its climb over the broken step (" + since + " ticks), at " + monitor.position());
            helper.assertTrue(level.noCollision(monitor, monitor.getBoundingBox().deflate(1.0E-3)), "the monitor is inside a block at "
                    + monitor.position());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(brokenAt[0] >= 0, "the monitor never got its box on top of the step (on a move "
                    + (int) seen[0] + " ticks, highest on one " + String.format("%.2f", seen[1]) + ", size "
                    + String.format("%.3f", seen[2]) + ", now at " + monitor.position() + ")");
            helper.assertTrue(monitor.onGround() && Math.abs(monitor.getY() - floor) < 0.05,
                    "the monitor is not back on the ground under the broken step: y " + String.format("%.2f", monitor.getY() - floor)
                            + " over it, on ground " + monitor.onGround());
        });
    }

    /**
     * Coming at a step far off square (60 degrees), the monitor turns square on the spot and climbs it with its move (it
     * stepped up the old way before, 2026-11-20), and ends on top. (Its turn is only told, not required: Minecraft's own
     * steering may turn it most of the way before it reaches the climb's start.)
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aMonitorAtAnAngleTurnsSquareAndClimbs(GameTestHelper helper) {
        // already where the climb starts (its move's 50 px short of the face at size 1: 2.34 blocks), facing 60 degrees
        // off the step's face, which lies straight ahead along x
        com.antcolonies.savanna.animal.MonitorEntity monitor = monitorBeforeStep(helper, STEP_X - 2.3, MZ0 + 5, -30.0F);
        ServerLevel level = helper.getLevel();
        int[] turn = {-1};
        helper.onEachTick(() -> {
            com.antcolonies.entity.body.BlockMoves.Play play = monitor.climb();
            if (play != null && turn[0] < 0) {
                turn[0] = play.turn();
            }
            if (turn[0] < 0 && monitor.getNavigation().isDone()) {
                // straight at the face along x from where it stands, so the route's last step up is square to it
                BlockPos goal = helper.absolutePos(new BlockPos(STEP_X + 6, TOP + 2, MZ0 + 5));
                monitor.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
            }
        });
        int top = helper.absolutePos(new BlockPos(0, TOP + 2, 0)).getY();
        helper.succeedWhen(() -> {
            helper.assertTrue(turn[0] >= 0, "the monitor never started its climb (at " + monitor.position() + ")");
            helper.assertTrue(monitor.climb() == null && monitor.onGround() && Math.abs(monitor.getY() - top) < 0.05,
                    "the monitor is not on top yet: y " + String.format("%.2f", monitor.getY() - top) + ", turned first " + turn[0] + " ticks");
        });
    }

    /**
     * A Nile monitor watches a survival player (2026-11-17, the user's ask): 11 blocks off it stops and faces them; when
     * they step back to 13.5 it trails them back toward 6; when they walk right up to its jaws it hisses, then lunges and
     * bites ({@code bite_player}).
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aMonitorWatchesTrailsAndBitesAPlayer(GameTestHelper helper) {
        for (int x = MX0; x <= MX1; x++) {
            for (int z = MZ0; z <= MZ1; z++) {
                helper.setBlock(new BlockPos(x, TOP, z), Blocks.STONE);
                for (int y = TOP + 1; y <= TOP + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        com.antcolonies.savanna.animal.MonitorEntity monitor = helper.spawn(SavannaEntities.MONITOR.get(), new BlockPos(MX0 + 2, TOP + 1, MZ0 + 5));
        monitor.setPersistenceRequired();
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        monitor.saveWithoutId(tag);
        tag.putInt("SunIn", 99999);                      // no basking, no hunting: only the player
        tag.putInt("HungryIn", 99999);
        monitor.load(tag);
        net.minecraft.server.level.ServerPlayer player = MatabeleDefenceTests.survivalPlayer(helper);
        double z = monitor.getZ(), y = monitor.getY();
        player.moveTo(monitor.getX() + 11.0, y, z, 90.0F, 0.0F);
        int[] stage = {0}, at = {0};
        double[] startX = {0};
        boolean[] bit = {false};
        helper.onEachTick(() -> {
            long t = helper.getTick();
            byte doing = monitor.doing();
            bit[0] |= doing == com.antcolonies.savanna.animal.MonitorEntity.BITE_PLAYER;
            if (stage[0] == 0 && doing == com.antcolonies.savanna.animal.MonitorEntity.WATCH) {
                stage[0] = 1;
                at[0] = (int) t;
                startX[0] = monitor.getX();
                player.moveTo(monitor.getX() + 13.5, y, z, 90.0F, 0.0F);
            } else if (stage[0] == 0 && t > 300) {
                helper.fail("the monitor never watched the player: doing " + doing + ", player " + String.format("%.1f", player.distanceTo(monitor)) + " away, survival " + net.minecraft.world.entity.EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player) + ", on ground " + monitor.onGround() + ", in water " + monitor.isInWater() + ", players " + helper.getLevel().players().size());
            } else if (stage[0] == 1 && monitor.getX() - startX[0] > 3.0) {
                stage[0] = 2;
                at[0] = (int) t;
                player.moveTo(monitor.getX() + 2.8, y, monitor.getZ(), 90.0F, 0.0F);
            } else if (stage[0] == 1 && t - at[0] > 200) {
                helper.fail("the monitor did not trail the player: moved " + String.format("%.1f", monitor.getX() - startX[0])
                        + " blocks, doing " + doing + ", player " + String.format("%.1f", player.distanceTo(monitor)) + " away");
            } else if (stage[0] == 2 && bit[0]) {
                helper.succeed();
            } else if (stage[0] == 2 && t - at[0] > 200) {
                helper.fail("the monitor did not bite the player at its jaws: doing " + doing);
            }
        });
    }

    /**
     * Monitors spawn by themselves (the spawning check list): the world's own checks over a river at sea level pass in
     * the water within {@code BANK_REACH} blocks of a bank and nowhere farther out, a spawn is set down on the bank, none
     * comes within {@code SPAWN_APART} blocks of another, and the kind is in its own group's list in the savanna's waters.
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void monitorsSpawnInTheWaterByBanksByThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int top = buildChannel(helper, false);
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        int reach = com.antcolonies.savanna.animal.MonitorEntity.BANK_REACH;
        var monitorType = SavannaEntities.MONITOR.get();
        // 1. its spots over the channel: only in the water near a bank (and the others' for comparison)
        int spots = 0, crocs = 0, crabs = 0;
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            for (int dz = 0; dz <= CZ1 - CZ0; dz++) {
                int fromBank = Math.min(Math.min(i + 1, CHANNEL_DEPTHS.length - i), Math.min(dz + 1, CZ1 - CZ0 + 1 - dz));
                for (int y = top - 8; y <= top + 1; y++) {
                    BlockPos at = new BlockPos(corner.getX() + i, y, corner.getZ() + dz);
                    if (worldWouldSpawn(level, monitorType, at)) {
                        spots++;
                        if (fromBank > reach) {
                            wrong.append(" ").append(i).append(",").append(dz).append(" (").append(fromBank).append(" from the bank)");
                        }
                    }
                    crocs += worldWouldSpawn(level, SavannaEntities.CROCODILE.get(), at) ? 1 : 0;
                    crabs += worldWouldSpawn(level, SavannaEntities.CRAB.get(), at) ? 1 : 0;
                }
            }
        }
        SavannaMod.LOGGER.info("[bank spawns] spots the world would spawn at over the channel: monitor {}, crocodile {}, crab {}", spots, crocs, crabs);
        if (spots == 0) {
            helper.fail("the world would spawn a monitor nowhere over the river");
        }
        if (!wrong.isEmpty()) {
            helper.fail("monitors would spawn too far from a bank:" + wrong);
        }
        // 2. the world's own spawn in the water by the bank: set down on the bank
        BlockPos drawn = new BlockPos(corner.getX() + 1, top - 1, corner.getZ() + 3);
        net.minecraft.world.entity.Mob mob = monitorType.create(level);
        mob.moveTo(drawn.getX() + 0.5, drawn.getY(), drawn.getZ() + 0.5, 0, 0);
        if (!net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(mob, level, MobSpawnType.NATURAL)) {
            helper.fail("the world refuses a monitor in the water by a bank");
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(drawn), MobSpawnType.NATURAL, null);
        BlockPos stood = mob.blockPosition();
        if (stood.getY() != top + 1 || !level.getFluidState(stood.below()).isEmpty() || !level.getBlockState(stood.below()).isSolid()) {
            helper.fail("a monitor spawned by the world stands at " + stood.subtract(corner) + ", not on the bank (y " + (top + 1) + ")");
        }
        // 3. none within SPAWN_APART of another
        level.addFreshEntity(mob);
        BlockPos other = new BlockPos(corner.getX() + CHANNEL_DEPTHS.length - 2, top - 1, corner.getZ() + 3);
        if (worldWouldSpawn(level, monitorType, other)) {
            helper.fail("a monitor would spawn " + (int) Math.sqrt(other.distSqr(stood)) + " blocks from another");
        }
        mob.discard();
        buildChannel(helper, true);
        // 4. its own spawning group, listed in the savanna's waters
        if (monitorType.getCategory() != com.antcolonies.savanna.registry.SavannaMobCategories.riverBank()) {
            helper.fail("the monitor is in the " + monitorType.getCategory().getName() + " group, not the river bank group");
        }
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var river = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_river"));
        for (var key : java.util.List.of(net.minecraft.world.level.biome.Biomes.SAVANNA, river)) {
            if (biomes.getOrThrow(key).getMobSettings().getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverBank())
                    .unwrap().stream().noneMatch(d -> d.type == monitorType)) {
                helper.fail("the monitor is not in " + key.location() + "'s river bank spawn list");
            }
        }
        helper.succeed();
    }

    // --- the otter (2026-11-22) ------------------------------------------------------------------------------------------

    /**
     * Otters spawn by themselves (the spawning check list): the world's own checks over a river at sea level pass in the
     * water within {@code BANK_REACH} blocks of a bank and nowhere farther out, a spawn is set down on the bank, and the
     * kind is in its own group's list (cap 2, ones and twos; was cap 6 in families of 2 to 4) in the savanna's waters.
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void ottersSpawnInTheWaterByBanksByThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int top = buildChannel(helper, false);
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        int reach = com.antcolonies.savanna.animal.OtterEntity.BANK_REACH;
        var otterType = SavannaEntities.OTTER.get();
        int spots = 0, monitors = 0, crocs = 0;
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            for (int dz = 0; dz <= CZ1 - CZ0; dz++) {
                int fromBank = Math.min(Math.min(i + 1, CHANNEL_DEPTHS.length - i), Math.min(dz + 1, CZ1 - CZ0 + 1 - dz));
                for (int y = top - 8; y <= top + 1; y++) {
                    BlockPos at = new BlockPos(corner.getX() + i, y, corner.getZ() + dz);
                    if (worldWouldSpawn(level, otterType, at)) {
                        spots++;
                        if (fromBank > reach) {
                            wrong.append(" ").append(i).append(",").append(dz).append(" (").append(fromBank).append(" from the bank)");
                        }
                    }
                    monitors += worldWouldSpawn(level, SavannaEntities.MONITOR.get(), at) ? 1 : 0;
                    crocs += worldWouldSpawn(level, SavannaEntities.CROCODILE.get(), at) ? 1 : 0;
                }
            }
        }
        SavannaMod.LOGGER.info("[otter spawns] spots the world would spawn at over the channel: otter {}, monitor {}, crocodile {}", spots, monitors, crocs);
        if (spots == 0) {
            helper.fail("the world would spawn an otter nowhere over the river");
        }
        if (!wrong.isEmpty()) {
            helper.fail("otters would spawn too far from a bank:" + wrong);
        }
        BlockPos drawn = new BlockPos(corner.getX() + 1, top - 1, corner.getZ() + 3);
        net.minecraft.world.entity.Mob mob = otterType.create(level);
        mob.moveTo(drawn.getX() + 0.5, drawn.getY(), drawn.getZ() + 0.5, 0, 0);
        if (!net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(mob, level, MobSpawnType.NATURAL)) {
            helper.fail("the world refuses an otter in the water by a bank");
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(drawn), MobSpawnType.NATURAL, null);
        BlockPos stood = mob.blockPosition();
        if (stood.getY() != top + 1 || !level.getFluidState(stood.below()).isEmpty() || !level.getBlockState(stood.below()).isSolid()) {
            helper.fail("an otter spawned by the world stands at " + stood.subtract(corner) + ", not on the bank (y " + (top + 1) + ")");
        }
        mob.discard();
        buildChannel(helper, true);
        if (otterType.getCategory() != com.antcolonies.savanna.registry.SavannaMobCategories.riverOtter()) {
            helper.fail("the otter is in the " + otterType.getCategory().getName() + " group, not the river otter group");
        }
        if (otterType.getCategory().getMaxInstancesPerChunk() != 2) {
            helper.fail("the river otter group's cap is " + otterType.getCategory().getMaxInstancesPerChunk() + ", not 2");
        }
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var river = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_river"));
        for (var key : java.util.List.of(net.minecraft.world.level.biome.Biomes.SAVANNA, river)) {
            var list = biomes.getOrThrow(key).getMobSettings().getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverOtter()).unwrap();
            if (list.stream().noneMatch(d -> d.type == otterType && d.minCount == 1 && d.maxCount == 2)) {
                helper.fail("the otter is not in " + key.location() + "'s river otter spawn list in ones and twos");
            }
        }
        helper.succeed();
    }

    /** A grown otter on the stone bank by the step (the monitor's climbing ground), facing the step. */
    private static com.antcolonies.savanna.animal.OtterEntity otterBeforeStep(GameTestHelper helper, double x0, int z) {
        for (int x = MX0; x <= MX1; x++) {
            for (int zz = MZ0; zz <= MZ1; zz++) {
                helper.setBlock(new BlockPos(x, TOP, zz), Blocks.STONE);
                helper.setBlock(new BlockPos(x, TOP + 1, zz), x >= STEP_X ? Blocks.STONE : Blocks.AIR);
                for (int y = TOP + 2; y <= TOP + 5; y++) {
                    helper.setBlock(new BlockPos(x, y, zz), Blocks.AIR);
                }
            }
        }
        com.antcolonies.savanna.animal.OtterEntity otter = helper.spawn(SavannaEntities.OTTER.get(),
                new net.minecraft.world.phys.Vec3(x0, TOP + 1, z + 0.5));
        otter.setPersistenceRequired();
        otter.life().setUp(false, otter.getRandom());
        otter.goalSelector.removeAllGoals(g -> true);       // only the test's own routes
        otter.setYRot(-90.0F);
        otter.yBodyRot = -90.0F;
        otter.yHeadRot = -90.0F;
        return otter;
    }

    /**
     * The otter's own moves on a step (2026-11-22, the user: a quick cute scramble up a block, a quick hop down): sent up
     * a one-block step it scrambles up with its move (under a second) and stands on top; sent back down it hops down
     * with its move and stands on the low ground.
     */
    @GameTest(template = "arena", timeoutTicks = 700)
    public static void anOtterScramblesUpAStepAndHopsBackDown(GameTestHelper helper) {
        int z = MZ0 + 5;
        com.antcolonies.savanna.animal.OtterEntity otter = otterBeforeStep(helper, MX0 + 3.5, z);
        int low = helper.absolutePos(new BlockPos(0, TOP + 1, 0)).getY();
        java.util.Set<com.antcolonies.entity.body.MoveRider.Edge> played = java.util.EnumSet.noneOf(com.antcolonies.entity.body.MoveRider.Edge.class);
        int[] upTicks = {0};
        StringBuilder fell = new StringBuilder();
        double[] lastY = {Double.NaN};
        helper.onEachTick(() -> {
            var play = otter.rider().play();
            if (play == null && !Double.isNaN(lastY[0]) && otter.getY() < lastY[0] - 0.3 && fell.length() < 400) {
                var path = otter.getNavigation().getPath();
                fell.append(String.format(" [dropped at x %.2f y %.2f yaw %.0f doing %d onGround %s path %s]",
                        otter.getX() - helper.absolutePos(BlockPos.ZERO).getX(), otter.getY() - low, otter.yBodyRot, otter.doing(),
                        otter.onGround(), path == null ? "none" : path.getNextNodeIndex() + "/" + path.getNodeCount()));
            }
            lastY[0] = otter.getY();
            var edge = otter.rider().edge(play);
            if (edge != null) {
                played.add(edge);
                if (edge == com.antcolonies.entity.body.MoveRider.Edge.UP && otter.rider().clipOn()) {
                    upTicks[0]++;               // its clip playing (not turning square first), with the stand after
                }
            }
            if (play == null && otter.getNavigation().isDone()) {
                boolean up = !played.contains(com.antcolonies.entity.body.MoveRider.Edge.UP);
                BlockPos goal = helper.absolutePos(up ? new BlockPos(STEP_X + 6, TOP + 2, z) : new BlockPos(MX0 + 3, TOP + 1, z));
                otter.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, com.antcolonies.savanna.animal.OtterEntity.BOUND);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(played.contains(com.antcolonies.entity.body.MoveRider.Edge.UP), "the otter never scrambled up the step with its move (at "
                    + otter.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))) + ")");
            helper.assertTrue(played.contains(com.antcolonies.entity.body.MoveRider.Edge.DOWN), "the otter never hopped down the step with its move;"
                    + fell + " step at x " + STEP_X);
            helper.assertTrue(otter.rider().play() == null && otter.onGround() && Math.abs(otter.getY() - low) < 0.05,
                    "the otter is not back on the low ground");
            helper.assertTrue(upTicks[0] < 40, "its scramble up took " + upTicks[0] + " ticks (it should be quick)");
        });
    }

    /**
     * Into the water and out again (2026-11-22): from a stone bank level with a pool's water it slides in with its move
     * (or now and then takes the leaping dive) and swims; sent back to the bank it climbs out quickly with its move and shakes itself dry.
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void anOtterSlidesIntoAPoolAndClimbsOutAndShakes(GameTestHelper helper) {
        int poolX = MX0 + 10, z = MZ0 + 5;
        for (int x = MX0; x <= MX1; x++) {
            for (int zz = MZ0; zz <= MZ1; zz++) {
                boolean pool = x >= poolX && x < MX1 && zz > MZ0 && zz < MZ1;
                for (int y = TOP - 3; y <= TOP; y++) {
                    helper.setBlock(new BlockPos(x, y, zz), pool && y > TOP - 3 ? Blocks.WATER : Blocks.STONE);
                }
                for (int y = TOP + 1; y <= TOP + 5; y++) {
                    helper.setBlock(new BlockPos(x, y, zz), Blocks.AIR);
                }
            }
        }
        com.antcolonies.savanna.animal.OtterEntity otter = helper.spawn(SavannaEntities.OTTER.get(),
                new net.minecraft.world.phys.Vec3(MX0 + 4.5, TOP + 1, z + 0.5));
        otter.setPersistenceRequired();
        otter.life().setUp(false, otter.getRandom());
        otter.goalSelector.removeAllGoals(g -> true);       // only the test's own routes (its swims and strolls took them over)
        java.util.Set<com.antcolonies.entity.body.MoveRider.Edge> played = java.util.EnumSet.noneOf(com.antcolonies.entity.body.MoveRider.Edge.class);
        boolean[] shook = {false};
        int[] swum = {0};
        helper.onEachTick(() -> {
            var edge = otter.rider().edge(otter.rider().play());
            if (edge != null) {
                played.add(edge);
            }
            if (otter.isInWater()) {
                swum[0]++;
            }
            shook[0] |= otter.doing() == com.antcolonies.savanna.animal.OtterEntity.SHAKE;
            if (otter.rider().play() == null && otter.getNavigation().isDone() && otter.doing() == com.antcolonies.savanna.animal.OtterEntity.CALM) {
                boolean wentIn = played.contains(com.antcolonies.entity.body.MoveRider.Edge.IN)
                        || played.contains(com.antcolonies.entity.body.MoveRider.Edge.RUN_IN);
                boolean in = !wentIn || swum[0] < 60;
                BlockPos goal = helper.absolutePos(in ? new BlockPos(poolX + 6, TOP, z) : new BlockPos(MX0 + 3, TOP + 1, z));
                otter.getNavigation().moveTo(goal.getX() + 0.5, goal.getY() + 0.2, goal.getZ() + 0.5, com.antcolonies.savanna.animal.OtterEntity.STROLL);
            }
        });
        helper.succeedWhen(() -> {
            // in calmly, or (one time in three, its swim about's fun) with the leaping dive
            helper.assertTrue(played.contains(com.antcolonies.entity.body.MoveRider.Edge.IN) || played.contains(com.antcolonies.entity.body.MoveRider.Edge.RUN_IN),
                    "the otter never went into the pool with its move (at "
                    + otter.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))) + ", in water " + otter.isInWater() + ")");
            helper.assertTrue(played.contains(com.antcolonies.entity.body.MoveRider.Edge.OUT), "the otter never climbed out with its move (swum "
                    + swum[0] + " ticks, at " + otter.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))) + ")");
            helper.assertTrue(shook[0], "the otter did not shake itself dry once out");
        });
    }

    /** The otter's pool: stone ground at TOP, a pool 3 deep from x = poolX on, its water's top level with the bank. */
    private static void otterPool(GameTestHelper helper, int poolX) {
        for (int x = MX0; x <= MX1; x++) {
            for (int zz = MZ0; zz <= MZ1; zz++) {
                boolean pool = x >= poolX && x < MX1 && zz > MZ0 && zz < MZ1;
                for (int y = TOP - 3; y <= TOP; y++) {
                    helper.setBlock(new BlockPos(x, y, zz), pool && y > TOP - 3 ? Blocks.WATER : Blocks.STONE);
                }
                for (int y = TOP + 1; y <= TOP + 5; y++) {
                    helper.setBlock(new BlockPos(x, y, zz), Blocks.AIR);
                }
            }
        }
    }

    /**
     * Play part 1 (2026-11-22, plan otter-play-and-fish.md section 1): two fed otters on a bank start a bout by
     * themselves, rear up face to face with their noses nearly touching (their middles the measured gap apart) and finish
     * it; a hungry pair beside them never plays.
     */
    // (6000 ticks: a try every 200 needs both calm, not resting or skittering; one pair first played at t3141, BUGS 299)
    @GameTest(template = "arena", timeoutTicks = 6000)
    public static void twoFedOttersPlayABoutAHungryPairDoesNot(GameTestHelper helper) {
        otterPool(helper, MX0 + 20);
        // each pair kept within 3 blocks of one spot, so they stay in playmate reach (6): kept 5 from their own starting
        // spots they could wander 13 apart and once never met in 3 minutes beside other tests
        com.antcolonies.savanna.animal.OtterEntity a = playOtter(helper, MX0 + 4.5, MZ0 + 4.5, true, MX0 + 6, MZ0 + 5);
        com.antcolonies.savanna.animal.OtterEntity b = playOtter(helper, MX0 + 7.5, MZ0 + 6.5, true, MX0 + 6, MZ0 + 5);
        com.antcolonies.savanna.animal.OtterEntity c = playOtter(helper, MX0 + 15.5, MZ0 + 4.5, false, MX0 + 16, MZ0 + 6);
        com.antcolonies.savanna.animal.OtterEntity d = playOtter(helper, MX0 + 16.5, MZ0 + 7.5, false, MX0 + 16, MZ0 + 6);
        double gap = com.antcolonies.savanna.animal.OtterEntity.REAR_REACH * 2.0;
        double[] reared = {-1, 0};          // their gap and how far off facing each other, the first time both rear on land
        long[] startedAt = {-1}, endedAt = {-1};
        java.util.Set<String> hungry = new java.util.TreeSet<>();
        helper.onEachTick(() -> {
            if (startedAt[0] < 0 && a.inBout() && b.inBout()) {
                startedAt[0] = helper.getTick();
                SavannaMod.LOGGER.info("[otter play] a bout starts at t{}", helper.getTick());
            }
            if (startedAt[0] >= 0 && endedAt[0] < 0 && !a.inBout() && !b.inBout()) {
                endedAt[0] = helper.getTick();
                SavannaMod.LOGGER.info("[otter play] the bout ends at t{}", helper.getTick());
            }
            if (reared[0] < 0 && a.doing() == com.antcolonies.savanna.animal.OtterEntity.PLAY
                    && b.doing() == com.antcolonies.savanna.animal.OtterEntity.PLAY && a.onGround() && b.onGround()) {
                reared[0] = a.position().subtract(b.position()).horizontalDistance();
                reared[1] = Math.abs(net.minecraft.util.Mth.wrapDegrees(a.getYRot() - b.getYRot() - 180.0F));
                SavannaMod.LOGGER.info("[otter play] rearing {} apart (the gap {}), {} degrees off facing", String.format("%.2f", reared[0]),
                        String.format("%.2f", gap), (int) reared[1]);
            }
            for (com.antcolonies.savanna.animal.OtterEntity o : new com.antcolonies.savanna.animal.OtterEntity[] {c, d}) {
                if (o.inBout() || o.doing() == com.antcolonies.savanna.animal.OtterEntity.PLAY) {
                    hungry.add("t" + helper.getTick());
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hungry.isEmpty(), "a hungry otter played: " + hungry);
            helper.assertTrue(startedAt[0] >= 0, "the fed otters never started a bout: " + String.format("%.1f", a.distanceTo(b))
                    + " apart; a " + a.playState() + "; b " + b.playState());
            helper.assertTrue(reared[0] >= 0, "they never reared up face to face on land");
            helper.assertTrue(Math.abs(reared[0] - gap) < 0.4, "they reared " + reared[0] + " apart, not about " + gap);
            helper.assertTrue(reared[1] < 35.0, "rearing they faced " + reared[1] + " degrees off each other");
            helper.assertTrue(endedAt[0] >= 0, "the bout never ended");
        });
    }

    /**
     * Chase and tag (2026-11-23, plan otter-play-and-fish.md 6e step 4 and 6g): two fed otters on an open field, made to
     * pick tag (the tag play.tag). The runner zigzags in leaps, left and right in turn, each flung to the side its clip
     * says, at about 6 blocks a second along its path; the chaser runs straight at it, speeding up from 5.5 to 6.5 blocks
     * a second (the user's numbers); both get caught in turn (the roles swap at each tag); at the nip the pouncer stands
     * about the clip's measured reach (1.31 blocks) behind the runner's middle.
     */
    @GameTest(template = "arena", timeoutTicks = 3600)
    public static void playingOttersChaseAndTagEachOtherOnTheBank(GameTestHelper helper) {
        for (int x = 2; x <= 50; x++) {                        // an open stone field, 49 blocks a side
            for (int zz = 2; zz <= 50; zz++) {
                helper.setBlock(new BlockPos(x, TOP, zz), Blocks.STONE);
                for (int y = TOP + 1; y <= TOP + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, zz), Blocks.AIR);
                }
            }
        }
        com.antcolonies.savanna.animal.OtterEntity a = playOtter(helper, 24.5, 26.5, true, 26, 26);
        com.antcolonies.savanna.animal.OtterEntity b = playOtter(helper, 27.5, 26.5, true, 26, 26);
        for (com.antcolonies.savanna.animal.OtterEntity o : new com.antcolonies.savanna.animal.OtterEntity[] {a, b}) {
            o.addTag("play.now");
            o.addTag("play.tag");
        }
        byte zl = com.antcolonies.savanna.animal.OtterEntity.ZIG_L, zr = com.antcolonies.savanna.animal.OtterEntity.ZIG_R;
        java.util.List<String> pounces = new java.util.ArrayList<>();
        java.util.List<Double> reach = new java.util.ArrayList<>();
        double[] zig = {0, 0}, chaseTop = {0};                 // blocks and ticks leaping; the chaser's best 10-tick pace
        int[] leaps = {0}, alternated = {0}, wrongSide = {0};
        int[] since = {-1, -1};
        byte[] lastDoing = {0, 0};
        net.minecraft.world.phys.Vec3[] last = {a.position(), b.position()}, leapFrom = {null, null};
        float[] leapYaw = {0, 0};
        java.util.List<java.util.ArrayDeque<net.minecraft.world.phys.Vec3>> trail = java.util.List.of(new java.util.ArrayDeque<>(), new java.util.ArrayDeque<>());
        boolean[] ended = {false};
        helper.onEachTick(() -> {
            com.antcolonies.savanna.animal.OtterEntity[] both = {a, b};
            for (int i = 0; i < 2; i++) {
                com.antcolonies.savanna.animal.OtterEntity o = both[i], other = both[1 - i];
                byte d = o.doing();
                boolean pouncing = d == com.antcolonies.savanna.animal.OtterEntity.POUNCE;
                since[i] = pouncing ? since[i] + 1 : -1;
                if (since[i] == 0) {
                    pounces.add((i == 0 ? "a" : "b") + "@t" + helper.getTick());
                }
                if (since[i] == 10) {                          // the nip: paws on the rump
                    reach.add(o.position().subtract(other.position()).horizontalDistance());
                }
                if (d == zl || d == zr) {
                    zig[0] += o.position().subtract(last[i]).horizontalDistance() / o.getScale();   // (speeds go with size)
                    zig[1]++;
                }
                if (d != lastDoing[i] && (lastDoing[i] == zl || lastDoing[i] == zr) && leapFrom[i] != null) {
                    // a leap done: how far it went to its left (+) or right, against its heading
                    float yaw = leapYaw[i] * net.minecraft.util.Mth.DEG_TO_RAD;
                    net.minecraft.world.phys.Vec3 m = o.position().subtract(leapFrom[i]);
                    double aside = m.x * net.minecraft.util.Mth.cos(yaw) + m.z * net.minecraft.util.Mth.sin(yaw);
                    if (aside * (lastDoing[i] == zl ? 1 : -1) < -0.2) {
                        wrongSide[0]++;
                    }
                }
                if (d != lastDoing[i] && (d == zl || d == zr)) {
                    leaps[0]++;
                    if (lastDoing[i] == (d == zl ? zr : zl)) {
                        alternated[0]++;
                    }
                    leapFrom[i] = o.position();
                    leapYaw[i] = o.getYRot();
                }
                java.util.ArrayDeque<net.minecraft.world.phys.Vec3> tr = trail.get(i);
                tr.addLast(o.position());
                if (tr.size() > 11) {
                    tr.removeFirst();
                }
                byte od = other.doing();
                if (tr.size() == 11 && o.inBout() && d == com.antcolonies.savanna.animal.OtterEntity.CALM && (od == zl || od == zr)) {
                    chaseTop[0] = Math.max(chaseTop[0], tr.getLast().subtract(tr.getFirst()).horizontalDistance() * 2.0 / o.getScale());
                }
                lastDoing[i] = d;
                last[i] = o.position();
            }
            if (!pounces.isEmpty() && !a.inBout() && !b.inBout()) {
                ended[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ended[0], "the game of tag never ended; pounces so far " + pounces);
            double zigBps = zig[1] > 0 ? zig[0] / zig[1] * 20.0 : 0.0;
            SavannaMod.LOGGER.info("[otter tag] pounces {}, pouncer behind the runner at the nip {} (the clip's 1.31); {} leaps, {} "
                    + "after one to the other side, {} to the wrong side; leaping {} b/s; the chaser's best {} b/s", pounces,
                    reach.stream().map(r -> String.format("%.2f", r)).toList(), leaps[0], alternated[0], wrongSide[0],
                    String.format("%.2f", zigBps), String.format("%.2f", chaseTop[0]));
            helper.assertTrue(pounces.stream().anyMatch(p -> p.startsWith("a")) && pounces.stream().anyMatch(p -> p.startsWith("b")),
                    "they did not both pounce (the roles never swapped): " + pounces);
            helper.assertTrue(pounces.size() >= 3, "only " + pounces.size() + " tags: " + pounces);
            for (double r : reach) {
                helper.assertTrue(Math.abs(r - com.antcolonies.savanna.animal.OtterEntity.POUNCE_BEHIND) < 0.4,
                        "a pouncer stood " + r + " behind the runner at the nip, not about 1.31: " + reach);
            }
            helper.assertTrue(leaps[0] >= 10, "only " + leaps[0] + " zigzag leaps");
            helper.assertTrue(alternated[0] >= leaps[0] * 0.6, "the leaps did not go left and right in turn: " + alternated[0] + " of " + leaps[0]);
            helper.assertTrue(wrongSide[0] <= leaps[0] / 10, wrongSide[0] + " of " + leaps[0] + " leaps went to the other side from their clip");
            helper.assertTrue(zigBps > 5.2 && zigBps < 6.6, "the runner leapt at " + zigBps + " blocks a second, not about 6");
            // (the chaser cuts the zigzag's bends, so a run seldom lasts the 8 s its pace takes to reach 6.5: first try 5.9)
            helper.assertTrue(chaseTop[0] > 5.4 && chaseTop[0] < 7.0, "the chaser's best pace was " + chaseTop[0] + " blocks a second, not 5.5 to 6.5");
        });
    }

    /**
     * No body inside another where they meet (2026-11-23; the user saw their heads pass through each other at the nose
     * poke and lying down together): at the poke their middles stand the measured nose reach apart (the poker's nose at
     * the bite's reach, the other's standing: 2.83 blocks), so the noses just touch; resting after the bout (the tag
     * play.rest: always) they lie flank to flank the same way round, their middles about two half widths apart.
     */
    @GameTest(template = "arena", timeoutTicks = 4800)
    public static void playingOttersTouchNosesAndRestSideBySideWithoutOverlapping(GameTestHelper helper) {
        for (int x = 2; x <= 50; x++) {                        // an open stone field
            for (int zz = 2; zz <= 50; zz++) {
                helper.setBlock(new BlockPos(x, TOP, zz), Blocks.STONE);
                for (int y = TOP + 1; y <= TOP + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, zz), Blocks.AIR);
                }
            }
        }
        // 5 blocks apart, inside the 6 of playmate reach (BUGS 299: 7 apart they could stand all test out of reach)
        com.antcolonies.savanna.animal.OtterEntity a = playOtter(helper, 23.5, 26.5, true, 26, 26);
        com.antcolonies.savanna.animal.OtterEntity b = playOtter(helper, 28.5, 27.5, true, 26, 26);
        for (com.antcolonies.savanna.animal.OtterEntity o : new com.antcolonies.savanna.animal.OtterEntity[] {a, b}) {
            o.addTag("play.now");
            o.addTag("play.rest");
        }
        double nose = com.antcolonies.savanna.animal.OtterEntity.NOSE_BITE + com.antcolonies.savanna.animal.OtterEntity.NOSE_STAND;
        double beside = com.antcolonies.savanna.animal.OtterEntity.REST_BESIDE;
        double[] poke = {-1}, rest = {-1, 0, 0};             // the poke's gap; resting: the gap, their headings apart, along
        StringBuilder trace = new StringBuilder();           // each change of either's doing or bout stage, for the failure
        String[] lastState = {""};
        helper.onEachTick(() -> {
            String state = a.doing() + "/" + b.doing() + " " + a.boutStage();
            if (!state.equals(lastState[0]) && trace.length() < 2500 && (poke[0] < 0 || !a.inBout())) {
                trace.append(" t").append(helper.getTick()).append(' ').append(state).append(String.format(" %.2f yaw %d/%d", a.distanceTo(b),
                        (int) a.getYRot(), (int) b.getYRot()));
                lastState[0] = state;
            }
            byte bite = com.antcolonies.savanna.animal.OtterEntity.BITE, lie = com.antcolonies.savanna.animal.OtterEntity.REST;
            if (poke[0] < 0 && a.inBout() && b.inBout() && (a.doing() == bite || b.doing() == bite)) {
                poke[0] = a.position().subtract(b.position()).horizontalDistance();
            }
            if (rest[0] < 0 && a.doing() == lie && b.doing() == lie) {
                net.minecraft.world.phys.Vec3 m = b.position().subtract(a.position());
                float yaw = a.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
                rest[0] = m.horizontalDistance();
                rest[1] = Math.abs(net.minecraft.util.Mth.wrapDegrees(a.getYRot() - b.getYRot()));
                rest[2] = Math.abs(-m.x * net.minecraft.util.Mth.sin(yaw) + m.z * net.minecraft.util.Mth.cos(yaw));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(poke[0] >= 0, "they never poked noses:" + trace);
            helper.assertTrue(rest[0] >= 0, "they never lay down together after the bout:" + trace);
            SavannaMod.LOGGER.info("[otter spacing] the poke {} apart (noses touch at {}); resting {} apart (flank to flank {}), "
                    + "{} degrees apart, {} along", String.format("%.2f", poke[0]), String.format("%.2f", nose),
                    String.format("%.2f", rest[0]), String.format("%.2f", beside), (int) rest[1], String.format("%.2f", rest[2]));
            helper.assertTrue(Math.abs(poke[0] - nose) < 0.3, "at the poke their middles stood " + poke[0] + " apart, not " + nose);
            helper.assertTrue(rest[0] > beside - 0.15 && rest[0] < beside + 0.4, "resting their middles lay " + rest[0] + " apart, not about " + beside
                    + ":" + trace);
            helper.assertTrue(rest[1] < 25.0, "resting they faced " + rest[1] + " degrees apart, not the same way");
            helper.assertTrue(rest[2] < 0.5, "resting one lay " + rest[2] + " ahead of the other, not flank to flank");
        });
    }

    /**
     * Otters keep out of each other's bodies (2026-11-23; the user saw one walk its head through the other's flank:
     * Minecraft kept them apart by their 0.75-block walking boxes only, on bodies 2.6 long). One otter stands; another is
     * sent walking straight through the spot it stands on and on beyond: it gives way round the other's body, never more
     * than a little way into it (without the rule it went 0.6 deep and through).
     */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void anOtterWalkingThroughAnotherGoesRoundItsBody(GameTestHelper helper) {
        for (int x = 2; x <= 40; x++) {
            for (int zz = 2; zz <= 30; zz++) {
                helper.setBlock(new BlockPos(x, TOP, zz), Blocks.STONE);
                for (int y = TOP + 1; y <= TOP + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, zz), Blocks.AIR);
                }
            }
        }
        com.antcolonies.savanna.animal.OtterEntity stander = playOtter(helper, 20.5, 16.5, false, 20, 16);
        com.antcolonies.savanna.animal.OtterEntity walker = playOtter(helper, 12.5, 16.5, false, 20, 16);
        for (com.antcolonies.savanna.animal.OtterEntity o : new com.antcolonies.savanna.animal.OtterEntity[] {stander, walker}) {
            o.goalSelector.removeAllGoals(g -> true);
            o.setYRot(o == stander ? 0.0F : -90.0F);
            o.yBodyRot = o.getYRot();
        }
        net.minecraft.core.BlockPos to = helper.absolutePos(new BlockPos(30, TOP + 1, 16));
        double[] deepest = {0};
        long[] passedAt = {-1};
        helper.onEachTick(() -> {
            if (helper.getTick() % 10 == 1) {
                walker.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0.8);
            }
            if (helper.getTick() > 2) {                       // (the beads are set on the body from its first tick)
                deepest[0] = Math.max(deepest[0], walker.bodyOverlap(stander));
            }
            if (passedAt[0] < 0 && walker.getX() > stander.getX() + 1.5) {
                passedAt[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(walker.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(to)) < 1.5,
                    "the walker never got past the stander (at " + walker.position() + ")");
            SavannaMod.LOGGER.info("[otter apart] the deepest one body went into the other: {} blocks; past it at t{}", String.format("%.2f", deepest[0]), passedAt[0]);
            helper.assertTrue(deepest[0] < 0.3, "the walking otter went " + deepest[0] + " blocks into the standing one's body");
        });
    }

    /**
     * The swimming chase porpoises (2026-11-23): two fed otters in a pool 3 deep play; going after each other at the
     * surface, now and then one leaps out in a porpoise.
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void playingOttersPorpoiseInADeepPool(GameTestHelper helper) {
        int poolX = MX0 + 4;
        otterPool(helper, poolX);
        com.antcolonies.savanna.animal.OtterEntity a = poolOtter(helper, poolX + 5.5, MZ0 + 3.5);
        com.antcolonies.savanna.animal.OtterEntity b = poolOtter(helper, poolX + 8.5, MZ0 + 6.5);
        java.util.Set<String> leapt = new java.util.TreeSet<>();
        boolean[] played = {false};
        helper.onEachTick(() -> {
            played[0] |= a.inBout();
            for (com.antcolonies.savanna.animal.OtterEntity o : new com.antcolonies.savanna.animal.OtterEntity[] {a, b}) {
                if (o.doing() == com.antcolonies.savanna.animal.OtterEntity.PORPOISE) {
                    leapt.add(o == a ? "a" : "b");
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(played[0], "they never started a bout in the pool: a " + a.playState());
            helper.assertTrue(!leapt.isEmpty(), "no otter porpoised in the swimming chase");
            SavannaMod.LOGGER.info("[otter porpoise] leapt: {}", leapt);
        });
    }

    /** A grown fed otter afloat in the pool, ready to play at once, kept in the water. */
    private static com.antcolonies.savanna.animal.OtterEntity poolOtter(GameTestHelper helper, double x, double z) {
        com.antcolonies.savanna.animal.OtterEntity otter = helper.spawn(SavannaEntities.OTTER.get(),
                new net.minecraft.world.phys.Vec3(x, TOP, z));
        otter.setPersistenceRequired();
        otter.life().setUp(false, otter.getRandom());
        otter.makeFed();
        otter.addTag("play.now");
        otter.restrictTo(helper.absolutePos(new BlockPos((int) x, TOP, (int) z)), 4);
        return otter;
    }

    /** A grown otter on the bank, fed or hungry, kept within 3 blocks of a spot; no swims (so the bout stays on the bank). */
    private static com.antcolonies.savanna.animal.OtterEntity playOtter(GameTestHelper helper, double x, double z, boolean fed,
            int keepX, int keepZ) {
        com.antcolonies.savanna.animal.OtterEntity otter = helper.spawn(SavannaEntities.OTTER.get(),
                new net.minecraft.world.phys.Vec3(x, TOP + 1, z));
        otter.setPersistenceRequired();
        otter.life().setUp(false, otter.getRandom());
        if (fed) {
            otter.makeFed();
        } else {
            otter.makeHungry();
        }
        otter.restrictTo(helper.absolutePos(new BlockPos(keepX, TOP + 1, keepZ)), 3);
        otter.goalSelector.removeAllGoals(g -> g.getClass().getSimpleName().equals("SwimAboutGoal"));
        return otter;
    }

    /**
     * The skitter (2026-11-22, plan otter-play-and-fish.md 6b): a curious otter on the bank goes in bursts: scurries, skid
     * stops, looks or sniffs, hops round to a new heading; its scurry runs at about the scurry clip's stride (1.57 blocks a
     * second) and its sniff at about the sniff clip's (0.59), so the feet do not slide. A calm one beside it only strolls.
     */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void aCuriousOtterSkittersInBurstsACalmOneStrolls(GameTestHelper helper) {
        int poolX = MX0 + 16;
        otterPool(helper, poolX);
        com.antcolonies.savanna.animal.OtterEntity curious = skitterOtter(helper, MZ0 + 3.5, "mood.curious");
        com.antcolonies.savanna.animal.OtterEntity calm = skitterOtter(helper, MZ0 + 8.5, "mood.calm");
        java.util.Set<Byte> doings = new java.util.TreeSet<>(), gaits = new java.util.TreeSet<>(), calmGaits = new java.util.TreeSet<>();
        double[] scurry = {0, 0}, sniff = {0, 0};      // blocks gone, ticks
        float[] hopTurned = {0};
        float[] lastYaw = {curious.getYRot()};
        net.minecraft.world.phys.Vec3[] last = {curious.position()};
        helper.onEachTick(() -> {
            byte g = curious.gait();
            doings.add(curious.doing());
            gaits.add(g);
            calmGaits.add(calm.gait());
            if (calm.doing() != com.antcolonies.savanna.animal.OtterEntity.CALM) {
                calmGaits.add((byte) (10 + calm.doing()));
            }
            double moved = curious.position().subtract(last[0]).horizontalDistance();
            if (moved > 0.01 && curious.onGround()) {          // going, its route's start and end left in (a fair measure)
                double[] into = g == com.antcolonies.savanna.animal.OtterEntity.SCURRY ? scurry
                        : g == com.antcolonies.savanna.animal.OtterEntity.SNIFF ? sniff : null;
                if (into != null) {
                    into[0] += moved;
                    into[1]++;
                }
            }
            if (curious.doing() == com.antcolonies.savanna.animal.OtterEntity.HOP) {
                hopTurned[0] += Math.abs(net.minecraft.util.Mth.wrapDegrees(curious.getYRot() - lastYaw[0]));
            }
            last[0] = curious.position();
            lastYaw[0] = curious.getYRot();
        });
        helper.runAfterDelay(1100, () -> {
            double scurryBps = scurry[1] > 0 ? scurry[0] / scurry[1] * 20.0 : 0.0, sniffBps = sniff[1] > 0 ? sniff[0] / sniff[1] * 20.0 : 0.0;
            SavannaMod.LOGGER.info("[otter skitter] doings {} gaits {} scurry {} b/s over {} ticks, sniff {} b/s over {} ticks, "
                    + "turned in hops {} deg; the calm one: {}", doings, gaits, String.format("%.2f", scurryBps), (int) scurry[1],
                    String.format("%.2f", sniffBps), (int) sniff[1], (int) hopTurned[0], calmGaits);
            helper.assertTrue(doings.contains(com.antcolonies.savanna.animal.OtterEntity.SKID)
                    && doings.contains(com.antcolonies.savanna.animal.OtterEntity.HOP)
                    && gaits.contains(com.antcolonies.savanna.animal.OtterEntity.SCURRY),
                    "the curious otter did not skitter (doings " + doings + ", gaits " + gaits + ")");
            helper.assertTrue(hopTurned[0] > 60.0F, "its hops turned it only " + hopTurned[0] + " degrees in all");
            helper.assertTrue(scurryBps > 1.3 && scurryBps < 1.9, "it scurried at " + scurryBps + " blocks a second, the clip's stride is 1.57");
            helper.assertTrue(sniff[1] == 0 || sniffBps > 0.4 && sniffBps < 0.8, "it sniffed along at " + sniffBps + " blocks a second, the clip's 0.59");
            helper.assertTrue(calmGaits.equals(java.util.Set.of(com.antcolonies.savanna.animal.OtterEntity.EVEN)),
                    "the calm otter skittered: " + calmGaits);
            helper.succeed();
        });
    }

    /** A grown otter on the bank, held in a mood by its tag, with only its land wandering left (no hunting, swims or rests). */
    private static com.antcolonies.savanna.animal.OtterEntity skitterOtter(GameTestHelper helper, double z, String moodTag) {
        com.antcolonies.savanna.animal.OtterEntity otter = helper.spawn(SavannaEntities.OTTER.get(),
                new net.minecraft.world.phys.Vec3(MX0 + 7.5, TOP + 1, z));
        otter.setPersistenceRequired();
        otter.life().setUp(false, otter.getRandom());
        otter.addTag(moodTag);
        otter.restrictTo(BlockPos.containing(otter.position()), 6);
        otter.goalSelector.removeAllGoals(g -> {
            String n = g.getClass().getSimpleName();
            return n.equals("SwimAboutGoal") || n.equals("HuntGoal") || n.equals("RestGoal");
        });
        return otter;
    }

    /**
     * The otter's hunt (step 2, 2026-11-22): a hungry otter on the bank by a pool with a crab on the bed in the shallow
     * corner and a barb in the deep: it catches one (feeling under the stones for the crab, or diving after the fish),
     * carries it out in its jaws, eats it on the bank and is full; a full otter leaves the other alone.
     */
    @GameTest(template = "arena", timeoutTicks = 1600)
    public static void aHungryOtterCatchesItsPreyAndEatsItOnTheBank(GameTestHelper helper) {
        int poolX = MX0 + 10, z = MZ0 + 5;
        otterPool(helper, poolX);
        helper.setBlock(new BlockPos(poolX, TOP - 2, z), Blocks.STONE);       // a shallow shelf by the bank for the crab
        helper.setBlock(new BlockPos(poolX, TOP - 1, z), Blocks.STONE);
        com.antcolonies.savanna.animal.CrabEntity crab = helper.spawn(SavannaEntities.CRAB.get(), new BlockPos(poolX, TOP, z));
        crab.setPersistenceRequired();
        TilapiaEntity fish = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(poolX + 6, TOP - 1, z + 1));
        fish.setPersistenceRequired();
        com.antcolonies.savanna.animal.OtterEntity otter = helper.spawn(SavannaEntities.OTTER.get(),
                new net.minecraft.world.phys.Vec3(MX0 + 6.5, TOP + 1, z + 0.5));
        otter.setPersistenceRequired();
        otter.life().setUp(false, otter.getRandom());
        otter.makeHungry();
        boolean[] carried = {false};
        boolean[] ateOnLand = {false};
        java.util.Set<Byte> doings = new java.util.TreeSet<>();
        String[] last = {""};
        helper.onEachTick(() -> {
            doings.add(otter.doing());
            carried[0] |= otter.held() != null;
            var rel = otter.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)));
            var play = otter.rider().play();
            String now = "doing " + otter.doing() + " held " + (otter.held() != null) + " water " + otter.isInWater()
                    + " move " + (play == null ? "-" : otter.rider().edge(play)) + " nav " + otter.getNavigation().isDone();
            if (!now.equals(last[0])) {
                last[0] = now;
                SavannaMod.LOGGER.info("[otter hunt] t{} {} at {} yaw {}", helper.getTick(), now,
                        String.format("%.2f %.2f %.2f", rel.x, rel.y, rel.z), (int) otter.yBodyRot);
            }
            if (otter.doing() == com.antcolonies.savanna.animal.OtterEntity.EAT && !otter.isInWater()) {
                ateOnLand[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            boolean oneGone = !crab.isAlive() || !fish.isAlive();
            helper.assertTrue(oneGone && otter.isFed(), "the otter has not caught and eaten anything: doings " + doings + " vehicle "
                    + otter.getVehicle() + " on ground " + otter.onGround() + " under " + otter.level().getBlockState(otter.blockPosition().below())
                    + " crab at " + crab.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)))
                    + " fish alive " + fish.isAlive() + ", doing " + otter.doing()
                    + ", holding " + otter.held() + ", in water " + otter.isInWater() + ", at "
                    + otter.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))));
            helper.assertTrue(carried[0] && ateOnLand[0], "it did not carry its catch out and eat it on the bank (carried "
                    + carried[0] + ", ate on land " + ateOnLand[0] + ")");
            helper.assertTrue(crab.isAlive() != fish.isAlive(), "a full otter caught both");
        });
    }

    /** An open stone field for the otter's trigger tests (x 2 to 40, z 2 to 30, at TOP). */
    private static void otterField(GameTestHelper helper) {
        for (int x = 2; x <= 40; x++) {
            for (int zz = 2; zz <= 30; zz++) {
                helper.setBlock(new BlockPos(x, TOP, zz), Blocks.STONE);
                for (int y = TOP + 1; y <= TOP + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, zz), Blocks.AIR);
                }
            }
        }
    }

    /**
     * What sets its clips off (2026-11-23, the gaps filled): a hungry otter pounces on a bullfrog on land and pins it
     * (the pounce_pin clip), and eats it; a player walking up while it eats makes it stop and stand over its catch, cross
     * (stand_cross); once they are gone it eats on; after the meal it stands content a while, going nowhere
     * (stand_content).
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void anOtterPinsAFrogGuardsItsMealThenStandsContent(GameTestHelper helper) {
        otterField(helper);
        com.antcolonies.savanna.animal.OtterEntity otter = helper.spawn(SavannaEntities.OTTER.get(),
                new net.minecraft.world.phys.Vec3(20.5, TOP + 1, 16.5));
        otter.setPersistenceRequired();
        otter.life().setUp(false, otter.getRandom());
        otter.makeHungry();
        BullfrogEntity frog = helper.spawn(SavannaEntities.BULLFROG.get(), new net.minecraft.world.phys.Vec3(26.5, TOP + 1, 16.5));
        frog.setPersistenceRequired();
        byte calm = com.antcolonies.savanna.animal.OtterEntity.CALM, eat = com.antcolonies.savanna.animal.OtterEntity.EAT;
        long[] pinnedAt = {-1}, grabbedAt = {-1}, eatAt = {-1}, guardAt = {-1}, ateOnAt = {-1}, doneAt = {-1};
        double[] stillFrom = {0, 0, 0}, contentMoved = {-1};
        boolean[] contentSeen = {false};
        net.minecraft.server.level.ServerPlayer[] player = {null};
        helper.onEachTick(() -> {
            long t = helper.getTick();
            byte d = otter.doing();
            if (pinnedAt[0] < 0 && d == com.antcolonies.savanna.animal.OtterEntity.PIN) {
                pinnedAt[0] = t;
            }
            if (grabbedAt[0] < 0 && otter.held() != null) {
                grabbedAt[0] = t;
            }
            if (eatAt[0] < 0 && d == eat && !otter.isInWater()) {
                eatAt[0] = t;
                player[0] = helper.makeMockServerPlayerInLevel();   // someone walks up to it eating
                player[0].moveTo(otter.getX() + 2.0, otter.getY(), otter.getZ(), 0.0F, 0.0F);
            }
            if (player[0] != null && guardAt[0] < 0 && d == calm && otter.mood() == com.antcolonies.savanna.animal.OtterEntity.CROSS
                    && otter.held() != null) {
                guardAt[0] = t;
            }
            if (player[0] != null && (guardAt[0] >= 0 && t > guardAt[0] + 40 || t > eatAt[0] + 100)) {
                helper.getLevel().getServer().getPlayerList().remove(player[0]);   // gone (a player left behind changes later tests' spawning)
                player[0] = null;
            }
            if (guardAt[0] >= 0 && player[0] == null && ateOnAt[0] < 0 && d == eat) {
                ateOnAt[0] = t;
            }
            if (ateOnAt[0] >= 0 && doneAt[0] < 0 && otter.held() == null && d == calm) {
                doneAt[0] = t;
                stillFrom[0] = otter.getX();
                stillFrom[2] = otter.getZ();
            }
            if (doneAt[0] >= 0 && t == doneAt[0] + 50) {
                contentMoved[0] = Math.hypot(otter.getX() - stillFrom[0], otter.getZ() - stillFrom[2]);
            }
            if (doneAt[0] >= 0 && t > doneAt[0] && t < doneAt[0] + 50 && otter.mood() == com.antcolonies.savanna.animal.OtterEntity.CONTENT) {
                contentSeen[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(pinnedAt[0] >= 0, "the otter never pounced on the frog: " + otter.playState() + ", frog " + frog.isAlive());
            helper.assertTrue(grabbedAt[0] >= 0 && grabbedAt[0] - pinnedAt[0] < 20, "the pounce did not catch the frog (pounced t"
                    + pinnedAt[0] + ", caught t" + grabbedAt[0] + ")");
            helper.assertTrue(eatAt[0] >= 0, "it never ate its frog on land");
            helper.assertTrue(guardAt[0] >= 0, "a player walking up did not make it stand over its meal, cross");
            helper.assertTrue(ateOnAt[0] >= 0, "it did not eat on once the player was gone");
            helper.assertTrue(contentMoved[0] >= 0, "the meal never ended");
            SavannaMod.LOGGER.info("[otter triggers] pounced t{}, caught t{}, ate t{}, guarded t{}, ate on t{}, done t{}, moved {} in the "
                    + "next 2.5 s, content {}", pinnedAt[0], grabbedAt[0], eatAt[0], guardAt[0], ateOnAt[0], doneAt[0],
                    String.format("%.2f", contentMoved[0]), contentSeen[0]);
            helper.assertTrue(contentSeen[0], "after the meal it was not content");
            helper.assertTrue(contentMoved[0] < 0.3, "after the meal it went " + contentMoved[0] + " blocks, not standing content");
        });
    }

    /**
     * The startle (2026-11-23): an otter on land seeing a crocodile for the first time freezes facing it for half a
     * second (stand_scared) before it bolts.
     */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void anOtterSeeingACrocodileFreezesThenBolts(GameTestHelper helper) {
        otterField(helper);
        com.antcolonies.savanna.animal.OtterEntity otter = playOtter(helper, 20.5, 16.5, true, 20, 16);
        long[] scaredAt = {-1};
        double[] from = {0, 0}, frozen = {-1}, ran = {-1};
        helper.runAfterDelay(20, () -> {
            com.antcolonies.savanna.animal.CrocodileEntity croc = helper.spawn(SavannaEntities.CROCODILE.get(),
                    new net.minecraft.world.phys.Vec3(27.5, TOP + 1, 16.5));
            croc.setPersistenceRequired();
            croc.setNoAi(true);
        });
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (scaredAt[0] < 0 && otter.mood() == com.antcolonies.savanna.animal.OtterEntity.SCARED) {
                scaredAt[0] = t;                                // (the mood is worked out every 5 ticks: up to 4 late)
                from[0] = otter.getX();
                from[1] = otter.getZ();
            }
            if (scaredAt[0] >= 0 && t == scaredAt[0] + 4) {
                frozen[0] = Math.hypot(otter.getX() - from[0], otter.getZ() - from[1]);
            }
            if (scaredAt[0] >= 0 && t == scaredAt[0] + 60) {
                ran[0] = Math.hypot(otter.getX() - from[0], otter.getZ() - from[1]);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ran[0] >= 0, "it never took fright: " + otter.playState());
            SavannaMod.LOGGER.info("[otter startle] moved {} in its first 4 frightened ticks, {} in 3 s", String.format("%.2f", frozen[0]),
                    String.format("%.2f", ran[0]));
            helper.assertTrue(frozen[0] < 0.1, "it did not freeze when it first saw the crocodile (went " + frozen[0] + ")");
            helper.assertTrue(ran[0] > 2.0, "it did not bolt after its startle (went " + ran[0] + " in 3 s)");
        });
    }

    /**
     * Porpoising is not only play (2026-11-23): a lone otter swimming about a deep pool at its travelling pace leaps out
     * in a porpoise now and then.
     */
    @GameTest(template = "arena", timeoutTicks = 3000)
    public static void aLoneOtterSwimmingFastPorpoises(GameTestHelper helper) {
        int poolX = MX0 + 4;
        otterPool(helper, poolX);
        com.antcolonies.savanna.animal.OtterEntity otter = helper.spawn(SavannaEntities.OTTER.get(),
                new net.minecraft.world.phys.Vec3(poolX + 8.5, TOP, MZ0 + 5.5));
        otter.setPersistenceRequired();
        otter.life().setUp(false, otter.getRandom());
        otter.makeFed();
        double[] fastest = {0};
        boolean[] leapt = {false};
        helper.onEachTick(() -> {
            if (otter.isInWater() && !otter.diving()) {
                fastest[0] = Math.max(fastest[0], otter.getDeltaMovement().horizontalDistance() / otter.getScale());
            }
            leapt[0] |= otter.doing() == com.antcolonies.savanna.animal.OtterEntity.PORPOISE;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(leapt[0], "the otter never porpoised swimming about (fastest at the surface " + fastest[0] + " a tick)");
            SavannaMod.LOGGER.info("[otter porpoise alone] leapt; fastest at the surface {} blocks a tick", String.format("%.3f", fastest[0]));
        });
    }

    /**
     * A small fish gulped down (2026-11-22, the user: "super small fish are going to mostly just be swallowed"): by the
     * body plans' numbers (animal/HeldCatch) a barb is swallowed at once most times and a tilapia never; and a hungry
     * otter by a pool with a barb in it catches it and eats it: when it gulps it down (most runs), it never takes it to
     * the bank, and it is full for a shorter while than after a catch eaten on the bank.
     */
    @GameTest(template = "arena", timeoutTicks = 1600)
    public static void aHungryOtterGulpsDownASmallFishAtOnce(GameTestHelper helper) {
        int poolX = MX0 + 10, z = MZ0 + 5;
        otterPool(helper, poolX);
        BarbEntity barb = helper.spawn(SavannaEntities.BARB.get(), new BlockPos(poolX + 4, TOP - 1, z + 1));
        barb.setPersistenceRequired();
        TilapiaEntity sample = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(poolX + 12, TOP - 1, z + 6));
        net.minecraft.util.RandomSource dice = net.minecraft.util.RandomSource.create(7L);
        int barbs = 0, tilapia = 0;
        for (int i = 0; i < 200; i++) {
            barbs += com.antcolonies.savanna.animal.HeldCatch.swallowed(barb, dice) ? 1 : 0;
            tilapia += com.antcolonies.savanna.animal.HeldCatch.swallowed(sample, dice) ? 1 : 0;
        }
        helper.assertTrue(barbs >= 150 && barbs < 200, "a barb was swallowed " + barbs + " times in 200 (most, not all)");
        helper.assertTrue(tilapia == 0, "a tilapia was swallowed " + tilapia + " times");
        sample.discard();
        int swallowedBarbs = barbs;
        com.antcolonies.savanna.animal.OtterEntity otter = helper.spawn(SavannaEntities.OTTER.get(),
                new net.minecraft.world.phys.Vec3(MX0 + 6.5, TOP + 1, z + 0.5));
        otter.setPersistenceRequired();
        otter.life().setUp(false, otter.getRandom());
        otter.makeHungry();
        boolean[] gulped = {false}, ateOnLand = {false};
        helper.onEachTick(() -> {
            gulped[0] |= otter.doing() == com.antcolonies.savanna.animal.OtterEntity.GULP;
            ateOnLand[0] |= otter.doing() == com.antcolonies.savanna.animal.OtterEntity.EAT;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!barb.isAlive() && otter.isFed(), "the otter has not caught and eaten the barb (doing "
                    + otter.doing() + ", holding " + otter.held() + ")");
            helper.assertTrue(gulped[0] != ateOnLand[0], "gulped " + gulped[0] + " and ate on the bank " + ateOnLand[0]);
            if (gulped[0]) {
                helper.assertTrue(otter.fullFor() < com.antcolonies.savanna.animal.OtterEntity.FULL_TICKS,
                        "a gulped barb filled it for " + otter.fullFor() + " ticks, as long as a meal on the bank");
            }
            SavannaMod.LOGGER.info("[otter gulp] {} ({} of 200 barbs swallowed by the numbers)",
                    gulped[0] ? "gulped it down" : "carried it to the bank", swallowedBarbs);
        });
    }

    /**
     * An otter afloat in a pool for 75 s (longer than Minecraft's air would have lasted it) is never hurt: afloat, its
     * eye point sits just under the surface, so it drowned in the river (BUGS 288); its own dives decide its breath.
     */
    @GameTest(template = "arena", timeoutTicks = 1700)
    public static void anOtterAfloatInAPoolDoesNotDrown(GameTestHelper helper) {
        int poolX = MX0 + 10, z = MZ0 + 5;
        otterPool(helper, poolX);
        com.antcolonies.savanna.animal.OtterEntity otter = helper.spawn(SavannaEntities.OTTER.get(),
                new net.minecraft.world.phys.Vec3(poolX + 5.5, TOP, z + 0.5));
        otter.setPersistenceRequired();
        otter.life().setUp(false, otter.getRandom());
        otter.goalSelector.removeAllGoals(g -> true);       // it stays afloat where it is
        int[] wet = {0};
        helper.onEachTick(() -> {
            if (otter.isInWater()) {
                wet[0]++;
            }
            helper.assertTrue(otter.isAlive() && otter.getHealth() >= otter.getMaxHealth(), "the otter was hurt afloat after "
                    + wet[0] + " ticks in the water (health " + otter.getHealth() + ", air " + otter.getAirSupply()
                    + ", eye in water " + otter.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) + ")");
        });
        helper.runAtTickTime(1500, () -> {
            helper.assertTrue(wet[0] > 1400, "the otter was not in the water the whole time (" + wet[0] + " ticks)");
            helper.succeed();
        });
    }

    // --- the hippo (2026-11-21) ------------------------------------------------------------------------------------------

    /** The hippo's ground: what lies at each x of the strip MX0..MX1 (stone walls round it hold any water in). */
    private enum HippoGround { STEP, SHALLOWS, POOL }

    /**
     * Builds the hippo's ground over MX0..MX1, MZ0..MZ1: STEP a stone floor at TOP with a one-block step from STEP_X on;
     * SHALLOWS the same with the water a block deep before the step (the step is a bank one block over the bed); POOL
     * water two deep before STEP_X - 3, a shelf a block deep up to STEP_X, then a grass bank level with the water's top.
     */
    private static void hippoGround(GameTestHelper helper, HippoGround kind) {
        for (int x = MX0 - 1; x <= MX1 + 1; x++) {
            for (int z = MZ0 - 1; z <= MZ1 + 1; z++) {
                boolean wall = x < MX0 || x > MX1 || z < MZ0 || z > MZ1;
                for (int y = TOP - 3; y <= TOP + 7; y++) {
                    BlockState state;
                    if (wall) {
                        state = (y <= TOP + 2 ? Blocks.STONE : Blocks.AIR).defaultBlockState();
                    } else if (kind == HippoGround.POOL) {
                        int bed = x < STEP_X - 3 ? TOP - 2 : TOP - 1;
                        state = y <= bed ? Blocks.STONE.defaultBlockState()
                                : x < STEP_X && y <= TOP ? Blocks.WATER.defaultBlockState()
                                : x >= STEP_X && y == TOP ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    } else {
                        state = y <= TOP - 1 || y == TOP ? Blocks.STONE.defaultBlockState()
                                : y == TOP + 1 && x >= STEP_X ? Blocks.STONE.defaultBlockState()
                                : y == TOP + 1 && kind == HippoGround.SHALLOWS ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    }
                    helper.setBlock(new BlockPos(x, y, z), state);
                }
            }
        }
    }

    private static com.antcolonies.savanna.animal.HippoEntity hippoAt(GameTestHelper helper, double x, double y, float yaw) {
        com.antcolonies.savanna.animal.HippoEntity hippo = helper.spawn(SavannaEntities.HIPPO.get(),
                new net.minecraft.world.phys.Vec3(x, y, MZ0 + 5.5));
        hippo.setPersistenceRequired();
        hippo.led = true;
        hippo.setYRot(yaw);
        hippo.yBodyRot = yaw;
        hippo.yHeadRot = yaw;
        return hippo;
    }

    /**
     * A hippo sent along its ground plays its block move ({@code up}, {@code down} or {@code out}) and ends on the far
     * level, never inside a block on the way: its route is resent whenever it is done, as a player's walking would.
     */
    private static void hippoMoves(GameTestHelper helper, com.antcolonies.savanna.animal.HippoEntity hippo, int move, double goalX,
            int endY, String what) {
        ServerLevel level = helper.getLevel();
        boolean[] saw = {false};
        int[] other = {-1};
        helper.onEachTick(() -> {
            com.antcolonies.entity.body.BlockMoves.Play play = hippo.climb();
            if (play != null) {
                saw[0] |= play.move() == move;
                if (play.move() != move) {
                    other[0] = play.move();
                }
            }
            helper.assertTrue(level.noCollision(hippo, hippo.getBoundingBox().deflate(0.01)), "the hippo is inside a block at "
                    + hippo.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))));
            if (play == null && hippo.getNavigation().isDone()) {
                BlockPos goal = helper.absolutePos(new BlockPos((int) goalX, endY, MZ0 + 5));
                hippo.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
            }
        });
        int top = helper.absolutePos(new BlockPos(0, endY, 0)).getY();
        helper.succeedWhen(() -> {
            helper.assertTrue(saw[0], "the hippo never " + what + " on its move (other move " + other[0] + ", at "
                    + hippo.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))) + ")");
            helper.assertTrue(hippo.climb() == null && hippo.onGround() && Math.abs(hippo.getY() - top) < 0.05,
                    "the hippo is not on the far level yet: y " + String.format("%.2f", hippo.getY() - top));
        });
    }

    /** A hippo walking up to a one-block step climbs it on its own move (forefeet up first, then the hind legs heave). */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void aHippoClimbsAStepOnItsMove(GameTestHelper helper) {
        hippoGround(helper, HippoGround.STEP);
        var hippo = hippoAt(helper, MX0 + 3.5, TOP + 1, -90.0F);
        hippoMoves(helper, hippo, com.antcolonies.savanna.animal.HippoEntity.UP, STEP_X + 6, TOP + 2, "climbed the step");
    }

    /** A hippo wading in water a block deep walks out onto the bank a block up on its out move. */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void aHippoWalksOutOfTheShallowsOnItsMove(GameTestHelper helper) {
        hippoGround(helper, HippoGround.SHALLOWS);
        var hippo = hippoAt(helper, MX0 + 3.5, TOP + 1, -90.0F);
        hippoMoves(helper, hippo, com.antcolonies.savanna.animal.HippoEntity.OUT, STEP_X + 6, TOP + 2, "walked out of the shallows");
    }

    /** A hippo on a bank walks down into the shallows (water a block deep) on its step-down move, wading. */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void aHippoStepsDownIntoTheShallowsOnItsMove(GameTestHelper helper) {
        hippoGround(helper, HippoGround.SHALLOWS);
        var hippo = hippoAt(helper, STEP_X + 6.5, TOP + 2, 90.0F);
        hippoMoves(helper, hippo, com.antcolonies.savanna.animal.HippoEntity.DOWN, MX0 + 2, TOP + 1, "stepped down into the shallows");
    }

    /**
     * A survival player walking up to a hippo gets the warning yawn; staying near, they are charged and bitten (5 hearts).
     */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void aHippoYawnsAtAPlayerThenChargesAndBites(GameTestHelper helper) {
        hippoGround(helper, HippoGround.STEP);
        var hippo = hippoAt(helper, MX0 + 3.5, TOP + 1, -90.0F);
        net.minecraft.server.level.ServerPlayer player = MatabeleDefenceTests.survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(MX0 + 8, TOP + 1, MZ0 + 5));
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 90.0F, 0.0F);
        boolean[] yawned = {false};
        float health = player.getHealth();
        helper.onEachTick(() -> {
            yawned[0] |= hippo.doing() == com.antcolonies.savanna.animal.HippoEntity.YAWN;
            helper.assertTrue(hippo.doing() != com.antcolonies.savanna.animal.HippoEntity.BITE || yawned[0], "it bit before its warning yawn");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(yawned[0], "the hippo never yawned at the player " + String.format("%.1f", player.distanceTo(hippo)) + " away");
            helper.assertTrue(player.getHealth() <= health - 8.0F, "the hippo has not bitten the player: health " + player.getHealth()
                    + ", doing " + hippo.doing() + ", charging " + hippo.charging() + ", " + String.format("%.1f", player.distanceTo(hippo)) + " away");
        });
    }

    /**
     * At night a hippo in its pool walks out onto the land and grazes ({@code graze}); by day it goes back into the water.
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void aHippoGrazesOnLandAtNightAndGoesBackByDay(GameTestHelper helper) {
        hippoGround(helper, HippoGround.POOL);
        var hippo = hippoAt(helper, MX0 + 2.5, TOP - 1, -90.0F);
        hippo.led = false;
        hippo.heldNight = true;
        boolean[] grazed = {false};
        helper.onEachTick(() -> {
            if (!grazed[0] && hippo.doing() == com.antcolonies.savanna.animal.HippoEntity.GRAZE && !hippo.isInWater()) {
                grazed[0] = true;
                hippo.heldNight = false;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(grazed[0], "the hippo never grazed on land at night: in water " + hippo.isInWater() + ", doing " + hippo.doing()
                    + ", at " + hippo.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)))
                    + ", " + hippo.about() + ", origin " + helper.absolutePos(BlockPos.ZERO));
            helper.assertTrue(hippo.isInWater(), "the hippo has not gone back into the water by day, at "
                    + hippo.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))));
        });
    }

    /** A hippo resting in the deep end of the POOL ground, its pool there, by day (night held off). */
    private static com.antcolonies.savanna.animal.HippoEntity hippoInPool(GameTestHelper helper) {
        var hippo = hippoAt(helper, MX0 + 2.5, TOP - 1, -90.0F);
        hippo.led = false;
        hippo.heldNight = false;
        hippo.heldCool = false;
        hippo.joinPod(helper.absolutePos(new BlockPos(MX0 + 2, TOP - 1, MZ0 + 5)));
        return hippo;
    }

    /**
     * In the cool hours a hippo in its pool hauls out onto the bank and lies down there ({@code rest}, out of the water,
     * on the grass within 3 blocks of the water); when the cool hours end it gets up ({@code rest_up}) and goes back into
     * the water (2026-11-23).
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void aHippoRestsOnTheBankInTheCoolHoursThenGoesBackIn(GameTestHelper helper) {
        hippoGround(helper, HippoGround.POOL);
        var hippo = hippoInPool(helper);
        hippo.heldCool = true;
        int[] rested = {0};
        boolean[] rose = {false};
        helper.onEachTick(() -> {
            if (hippo.doing() == com.antcolonies.savanna.animal.HippoEntity.REST) {
                helper.assertFalse(hippo.isInWater(), "the hippo lies down in the water");
                BlockPos at = hippo.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
                helper.assertTrue(at.getX() >= STEP_X && at.getX() <= STEP_X + 3, "the hippo rests away from the water's edge at " + at);
                if (++rested[0] == 200) {
                    hippo.heldCool = false;           // the day heats up
                }
            }
            rose[0] |= rested[0] > 0 && hippo.doing() == com.antcolonies.savanna.animal.HippoEntity.RISE;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(rested[0] >= 200, "the hippo has not rested on the bank: doing " + hippo.doing() + ", in water "
                    + hippo.isInWater() + ", " + hippo.about());
            helper.assertTrue(rose[0], "the hippo never got up off the bank");
            helper.assertTrue(hippo.isInWater(), "the hippo has not gone back into the water after its rest, at "
                    + hippo.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
        });
    }

    /** A hippo walking back and forth over grass wears a path under its feet: path blocks along its way, none off it. */
    @GameTest(template = "arena", timeoutTicks = 1600)
    public static void aHippoWearsAPathWalkingTheSameWay(GameTestHelper helper) {
        hippoGround(helper, HippoGround.POOL);
        var hippo = hippoAt(helper, STEP_X + 3.5, TOP + 1, -90.0F);
        int[] trips = {0};
        helper.onEachTick(() -> {
            if (hippo.climb() == null && hippo.getNavigation().isDone()) {
                trips[0]++;
                BlockPos goal = helper.absolutePos(new BlockPos(trips[0] % 2 == 1 ? MX1 - 3 : STEP_X + 3, TOP + 1, MZ0 + 5));
                hippo.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
            }
        });
        helper.succeedWhen(() -> {
            int on = 0;
            for (int x = STEP_X; x <= MX1; x++) {
                for (int z = MZ0; z <= MZ1; z++) {
                    if (helper.getBlockState(new BlockPos(x, TOP, z)).is(Blocks.DIRT_PATH)) {
                        helper.assertTrue(Math.abs(z - (MZ0 + 5)) <= 2, "a path block off the hippo's way at " + x + "," + z);
                        on++;
                    }
                }
            }
            helper.assertTrue(on >= 6 && trips[0] >= 6, on + " path blocks after " + trips[0] + " trips");
        });
    }

    /**
     * At night a hippo grazes its lawn short: with tall grass all over the bank it finds its lawns, leaves the water at its
     * landing, and crops tall grass down to short grass (or to the ground), not pulling it up all at once (2026-11-23).
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void aHippoGrazesItsLawnShort(GameTestHelper helper) {
        hippoGround(helper, HippoGround.POOL);
        for (int x = STEP_X; x <= MX1; x++) {
            for (int z = MZ0; z <= MZ1; z++) {
                helper.setBlock(new BlockPos(x, TOP + 1, z), Blocks.TALL_GRASS.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
                helper.setBlock(new BlockPos(x, TOP + 2, z), Blocks.TALL_GRASS.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            }
        }
        var hippo = hippoInPool(helper);
        hippo.heldNight = true;
        boolean[] landed = {false};
        helper.onEachTick(() -> landed[0] |= !hippo.isInWater() && hippo.landing() != null);
        helper.succeedWhen(() -> {
            // grazed: its tall grass cropped to short grass or on down to the bare grass block (a lawn), not worn to path
            int shortGrass = 0;
            for (int x = STEP_X; x <= MX1; x++) {
                for (int z = MZ0; z <= MZ1; z++) {
                    BlockState plant = helper.getBlockState(new BlockPos(x, TOP + 1, z));
                    if (helper.getBlockState(new BlockPos(x, TOP, z)).is(Blocks.GRASS_BLOCK) && (plant.is(Blocks.SHORT_GRASS) || plant.isAir())) {
                        shortGrass++;
                    }
                }
            }
            helper.assertFalse(hippo.lawnsKnown().isEmpty(), "the hippo found no lawn: " + hippo.about());
            helper.assertTrue(landed[0], "the hippo did not leave the water at a landing: " + hippo.about());
            helper.assertTrue(shortGrass >= 2, "the hippo has grazed " + shortGrass + " tall grass short (doing " + hippo.doing()
                    + ", lawns " + hippo.lawnsKnown() + ", " + hippo.about() + ")");
        });
    }

    /** A hippo in its pool by day wades to a hippo grass mat near the bank and eats it, grazing in the water. */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void aHippoEatsHippoGrassNearItsPool(GameTestHelper helper) {
        hippoGround(helper, HippoGround.POOL);
        BlockPos mat = new BlockPos(STEP_X - 1, TOP + 1, MZ0 + 5);
        helper.setBlock(mat, com.antcolonies.savanna.registry.SavannaBlocks.HIPPO_GRASS.get().defaultBlockState());
        var hippo = hippoInPool(helper);
        boolean[] grazedInWater = {false};
        helper.onEachTick(() -> grazedInWater[0] |= hippo.isInWater() && hippo.doing() == com.antcolonies.savanna.animal.HippoEntity.GRAZE);
        helper.succeedWhen(() -> {
            helper.assertTrue(grazedInWater[0], "the hippo never grazed in the water: " + hippo.about());
            helper.assertTrue(helper.getBlockState(mat).isAir(), "the hippo grass mat is not eaten: " + helper.getBlockState(mat)
                    + ", hippo at " + hippo.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
        });
    }

    /**
     * Hippo pods are laid with new land and kept for good (the user, 2026-11-21; BUGS 271): in a flat-bedded pool three
     * deep a pod of one to three is laid, the bull (male) first, his cows (female) within reach of him, all sharing his
     * pool, never despawning; every one rests with its whole body clear of the ground and is unhurt after a while there
     * (the old spawner set them on the bed under their middle only, half in a sloping bank). No spot fits a hippo on a bed
     * sloping a block a column (the river channel), beside a wall, or in water under a roof (an underground pocket); the
     * hippo is in no biome's spawn list.
     */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void hippoPodsAreLaidWithTheLandAndKeptForGood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // a pool 15 by 15, three deep over a flat stone bed, stone walls round it up to the water's top
        for (int x = 2; x <= 18; x++) {
            for (int z = 2; z <= 18; z++) {
                boolean wall = x == 2 || x == 18 || z == 2 || z == 18;
                for (int y = TOP - 4; y <= TOP + 4; y++) {
                    BlockState state = y <= TOP - 3 || wall && y <= TOP ? Blocks.STONE.defaultBlockState()
                            : y <= TOP ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    helper.setBlock(new BlockPos(x, y, z), state);
                }
            }
        }
        // an underground pocket of water: a roof of stone three thick over it
        for (int x = 44; x <= 50; x++) {
            for (int z = 4; z <= 10; z++) {
                boolean wall = x == 44 || x == 50 || z == 4 || z == 10;
                for (int y = TOP - 4; y <= TOP + 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), (y <= TOP - 3 || wall || y > TOP ? Blocks.STONE : Blocks.WATER).defaultBlockState());
                }
            }
        }
        int top = buildChannel(helper, false);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int absTop = helper.absolutePos(new BlockPos(0, TOP, 0)).getY();
        StringBuilder wrong = new StringBuilder();
        for (int x = 3; x <= 17; x++) {
            for (int z = 3; z <= 17; z++) {
                boolean fits = com.antcolonies.savanna.worldgen.HippoPodFeature.standSpot(level, origin.getX() + x, origin.getZ() + z, absTop) != null;
                boolean shouldFit = x > 3 && x < 17 && z > 3 && z < 17;
                if (fits != shouldFit) {
                    wrong.append(" ").append(x).append(",").append(z).append(fits ? " fits" : " does not fit");
                }
            }
        }
        helper.assertTrue(wrong.isEmpty(), "where a hippo fits in the flat pool (all but beside the walls):" + wrong);
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            for (int dz = 0; dz <= CZ1 - CZ0; dz++) {
                helper.assertTrue(com.antcolonies.savanna.worldgen.HippoPodFeature.standSpot(level, corner.getX() + i, corner.getZ() + dz, top) == null,
                        "a hippo fits on the sloping river bed at " + i + "," + dz + " (" + CHANNEL_DEPTHS[i] + " deep): it would lie half in the bank");
            }
        }
        for (int x = 45; x <= 49; x++) {
            for (int z = 5; z <= 9; z++) {
                helper.assertTrue(com.antcolonies.savanna.worldgen.HippoPodFeature.standSpot(level, origin.getX() + x, origin.getZ() + z, absTop) == null,
                        "a hippo fits in the water under the roof at " + x + "," + z);
                helper.assertFalse(com.antcolonies.savanna.worldgen.RiverZones.openToSky(level, helper.absolutePos(new BlockPos(x, TOP, z))),
                        "the water under the roof reads as open to the sky");
            }
        }
        // the pod
        int laid = com.antcolonies.savanna.worldgen.HippoPodFeature.lay(level, net.minecraft.util.RandomSource.create(11),
                origin.getX() + 3, origin.getZ() + 3, absTop);
        java.util.List<com.antcolonies.savanna.animal.HippoEntity> pod = level.getEntitiesOfClass(com.antcolonies.savanna.animal.HippoEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(2, TOP - 4, 2))).expandTowards(17, 9, 17));
        helper.assertTrue(laid >= 1 && laid <= 3 && pod.size() == laid, "a pod of " + laid + " laid (" + pod.size() + " found), not 1 to 3");
        pod.sort(java.util.Comparator.comparing(h -> !h.life().isMale()));
        com.antcolonies.savanna.animal.HippoEntity bull = pod.get(0);
        helper.assertTrue(bull.life().isMale() && pod.stream().skip(1).noneMatch(h -> h.life().isMale()), "a pod is one bull and his cows");
        for (com.antcolonies.savanna.animal.HippoEntity h : pod) {
            helper.assertTrue(bull.pool() != null && bull.pool().equals(h.pool()), "a pod member's pool " + h.pool() + " is not the bull's " + bull.pool());
            helper.assertTrue(h.distanceTo(bull) <= 7.5, "a cow rests " + String.format("%.1f", h.distanceTo(bull)) + " from her bull");
            helper.assertFalse(h.removeWhenFarAway(1.0E9), "a pod hippo would despawn far from a player");
        }
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        for (var key : java.util.List.of(net.minecraft.world.level.biome.Biomes.SAVANNA, com.antcolonies.savanna.worldgen.RiverZones.RIVER)) {
            for (net.minecraft.world.entity.MobCategory group : net.minecraft.world.entity.MobCategory.values()) {
                helper.assertTrue(biomes.getOrThrow(key).getMobSettings().getMobs(group).unwrap().stream()
                        .noneMatch(d -> d.type == SavannaEntities.HIPPO.get()), "the hippo is in " + key.location() + "'s " + group.getName() + " spawn list");
            }
        }
        helper.runAtTickTime(200, () -> {
            for (com.antcolonies.savanna.animal.HippoEntity h : pod) {
                helper.assertTrue(h.isAlive() && h.getHealth() >= h.getMaxHealth(), "a pod hippo was hurt resting in its pool: health " + h.getHealth());
                helper.assertTrue(level.noCollision(h, h.getBoundingBox().deflate(0.01)), "a pod hippo is inside a block at " + h.position().subtract(
                        net.minecraft.world.phys.Vec3.atLowerCornerOf(origin)));
            }
            helper.succeed();
        });
    }

    /**
     * River zones (2026-11-21, {@code worldgen/RiverZones}): the savanna river reads as coast, near, mid or far inland by
     * how far inland (the sand's bands), the floodplain's and savanna's other waters as still water, anywhere else as no
     * zone (the whole cap, as the quiet gate); the still waters split into oxbows (the floodplain's) and pans (the open
     * savanna's, 2026-11-22); each kind keeps its share of its group's cap there: hippos spawned none at the coast, far
     * inland or the still waters (the oxbows' pods are placed with the land); the pans only snails, bullfrogs and flies; crocodiles a quarter of theirs far inland;
     * crabs most far inland; bullfrogs only the still waters, a little mid inland; kinds not listed everywhere.
     */
    @GameTest(template = "arena", timeoutTicks = 20)
    public static void riverZonesShareEachKindsGroupByStretch(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var river = biomes.getHolderOrThrow(com.antcolonies.savanna.worldgen.RiverZones.RIVER);
        var floodplain = biomes.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_floodplain")));
        var plains = biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        {
            double[][] reads = {{-0.3, 0}, {-0.11, 1}, {0.0, 1}, {0.03, 2}, {0.29, 2}, {0.3, 3}, {0.8, 3}};
            for (double[] r : reads) {
                var zone = com.antcolonies.savanna.worldgen.RiverZones.zoneOf(river, () -> r[0]);
                helper.assertTrue(zone.ordinal() == (int) r[1], "the river " + r[0] + " inland reads " + zone);
            }
        }
        helper.assertTrue(com.antcolonies.savanna.worldgen.RiverZones.zoneOf(floodplain, () -> 0) == com.antcolonies.savanna.worldgen.RiverZones.Zone.OXBOW,
                "the floodplain's water is not an oxbow's");
        var savanna = biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA);
        helper.assertTrue(com.antcolonies.savanna.worldgen.RiverZones.zoneOf(savanna, () -> 0) == com.antcolonies.savanna.worldgen.RiverZones.Zone.PAN,
                "the open savanna's water is not a pan's");
        helper.assertTrue(com.antcolonies.savanna.worldgen.RiverZones.zoneOf(plains, () -> 0) == com.antcolonies.savanna.worldgen.RiverZones.Zone.UNKNOWN,
                "a plains spot has a river zone");
        var Z = com.antcolonies.savanna.worldgen.RiverZones.Zone.values();
        var hippo = SavannaEntities.HIPPO.get();
        var croc = SavannaEntities.CROCODILE.get();
        var crab = SavannaEntities.CRAB.get();
        var frog = SavannaEntities.BULLFROG.get();
        var flies = SavannaEntities.FLY_CLOUD.get();
        for (var z : Z) {
            boolean hippoRiver = z == com.antcolonies.savanna.worldgen.RiverZones.Zone.NEAR || z == com.antcolonies.savanna.worldgen.RiverZones.Zone.MID
                    || z == com.antcolonies.savanna.worldgen.RiverZones.Zone.UNKNOWN;
            helper.assertTrue(com.antcolonies.savanna.worldgen.RiverZones.allows(hippo, z, 0) == hippoRiver, "hippos " + (hippoRiver ? "not " : "") + "allowed " + z);
            helper.assertTrue(com.antcolonies.savanna.worldgen.RiverZones.allows(flies, z, 1000), "fly clouds held back " + z);
        }
        var far = com.antcolonies.savanna.worldgen.RiverZones.Zone.FAR;
        int crocsFar = com.antcolonies.savanna.worldgen.RiverZones.most(croc, far);
        helper.assertTrue(crocsFar == Math.max(1, Math.round(croc.getCategory().getMaxInstancesPerChunk() * 0.25F)), "crocodiles far inland: " + crocsFar);
        helper.assertTrue(com.antcolonies.savanna.worldgen.RiverZones.allows(croc, far, crocsFar - 1)
                && !com.antcolonies.savanna.worldgen.RiverZones.allows(croc, far, crocsFar), "the far-inland crocodiles' limit is not " + crocsFar);
        helper.assertTrue(com.antcolonies.savanna.worldgen.RiverZones.allows(crab, far, 1000), "crabs held back far inland");
        helper.assertTrue(com.antcolonies.savanna.worldgen.RiverZones.allows(frog, com.antcolonies.savanna.worldgen.RiverZones.Zone.OXBOW, 1000)
                && com.antcolonies.savanna.worldgen.RiverZones.allows(frog, com.antcolonies.savanna.worldgen.RiverZones.Zone.PAN, 1000)
                && !com.antcolonies.savanna.worldgen.RiverZones.allows(frog, com.antcolonies.savanna.worldgen.RiverZones.Zone.COAST, 0),
                "bullfrogs are not the still waters' alone");
        // the pans (2026-11-22): rain pools with snails, bullfrogs and flies, none of the river's fish, crocodiles, crabs or monitors
        var pan = com.antcolonies.savanna.worldgen.RiverZones.Zone.PAN;
        for (var kind : java.util.List.of(SavannaEntities.TILAPIA.get(), SavannaEntities.CATFISH.get(), SavannaEntities.BARB.get(), croc, crab,
                SavannaEntities.MONITOR.get(), hippo)) {
            helper.assertFalse(com.antcolonies.savanna.worldgen.RiverZones.allows(kind, pan, 0), kind.getDescriptionId() + " allowed in a pan");
        }
        for (var kind : java.util.List.of(SavannaEntities.SNAIL.get(), frog, flies)) {
            helper.assertTrue(com.antcolonies.savanna.worldgen.RiverZones.allows(kind, pan, 1000), kind.getDescriptionId() + " held back in a pan");
        }
        helper.succeed();
    }

    /** A grown crocodile of this sex for the hippo's tests, never hungry. */
    private static com.antcolonies.savanna.animal.CrocodileEntity grownCroc(GameTestHelper helper, double x, double y, double z) {
        com.antcolonies.savanna.animal.CrocodileEntity croc = helper.spawn(SavannaEntities.CROCODILE.get(), new net.minecraft.world.phys.Vec3(x, y, z));
        croc.life().setUp(false, com.antcolonies.animal.Sex.MALE, helper.getLevel().random);
        croc.life().apply();
        croc.hungerIn = 1_000_000;
        return croc;
    }

    /**
     * A bull sees off a grown crocodile that comes right up to him with a bluff, not a fight (BUGS 272): he yawns first,
     * bites at most once, gives up once it is off, and the crocodile swims clear and lives.
     */
    @GameTest(template = "arena", timeoutTicks = 800)
    public static void aBullSeesOffACrocodileWithABluffNotAFight(GameTestHelper helper) {
        hippoGround(helper, HippoGround.POOL);
        var bull = hippoAt(helper, MX0 + 2.5, TOP - 1, -90.0F);
        bull.life().setUp(false, com.antcolonies.animal.Sex.MALE, helper.getLevel().random);
        bull.life().apply();
        var croc = grownCroc(helper, MX0 + 6.5, TOP, MZ0 + 5.5);
        float health = croc.getMaxHealth();
        boolean[] yawned = {false};
        helper.onEachTick(() -> {
            yawned[0] |= bull.doing() == com.antcolonies.savanna.animal.HippoEntity.YAWN;
            helper.assertTrue(bull.doing() != com.antcolonies.savanna.animal.HippoEntity.BITE || yawned[0], "the bull bit before his warning yawn");
            helper.assertTrue(croc.getHealth() >= health - 10.5F, "the bull bit the crocodile more than once: health " + croc.getHealth());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(yawned[0], "the bull never yawned at the crocodile " + String.format("%.1f", croc.distanceTo(bull)) + " away");
            helper.assertTrue(croc.isAlive() && croc.distanceTo(bull) >= 8.0 && !bull.charging(),
                    "the crocodile is not seen off: " + String.format("%.1f", croc.distanceTo(bull)) + " away, charging " + bull.charging());
        });
    }

    /**
     * A crocodile afloat in water two deep, sent to the bank past a shelf a block under the surface, rises over the shelf
     * and walks out (BUGS 273: its climb-out lift asked Minecraft's isFree, which refuses water too, so against the
     * shelf it never rose and drifted along the lip).
     */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void aCrocodileAfloatClimbsOverAnUnderwaterLedge(GameTestHelper helper) {
        hippoGround(helper, HippoGround.POOL);
        var croc = grownCroc(helper, MX0 + 4.5, TOP, MZ0 + 5.5);
        BlockPos bank = helper.absolutePos(new BlockPos(STEP_X + 5, TOP + 1, MZ0 + 5));
        boolean[] out = {false};
        helper.onEachTick(() -> {
            if (croc.getNavigation().isDone() && croc.tickCount % 20 == 0) {
                croc.getNavigation().moveTo(bank.getX() + 0.5, bank.getY(), bank.getZ() + 0.5, 1.0);
            }
            out[0] |= croc.getX() - helper.absolutePos(BlockPos.ZERO).getX() >= STEP_X + 0.5 && !croc.isInWater();
        });
        helper.succeedWhen(() -> helper.assertTrue(out[0], "the crocodile never got over the ledge onto the bank: at "
                + croc.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))) + ", in water " + croc.isInWater()));
    }

    /** A cow lets a grown crocodile be (until calves come, only bulls mind them). */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void aCowLetsACrocodileBe(GameTestHelper helper) {
        hippoGround(helper, HippoGround.POOL);
        var cow = hippoAt(helper, MX0 + 2.5, TOP - 1, -90.0F);
        cow.life().setUp(false, com.antcolonies.animal.Sex.FEMALE, helper.getLevel().random);
        cow.life().apply();
        var croc = grownCroc(helper, MX0 + 6.5, TOP, MZ0 + 5.5);
        helper.onEachTick(() -> helper.assertTrue(!cow.charging() && cow.doing() != com.antcolonies.savanna.animal.HippoEntity.YAWN,
                "the cow went for the crocodile " + String.format("%.1f", croc.distanceTo(cow)) + " away"));
        helper.runAtTickTime(240, helper::succeed);
    }

    // --- the river barb (2026-11-20) -----------------------------------------------------------------------------------

    /**
     * Barbs spawn by themselves in the shallows (the spawning check list): over a river's cross-section the world would
     * spawn a barb at every water spot of a column 1 to 3 blocks deep and nowhere deeper; barbs are in their own group,
     * river schools (cap 40), listed in the savanna's waters in schools of 8 to 14; the river fish group is down to 45
     * and the tilapia come in groups of 2 to 5 (the user, 2026-11-20).
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void barbsSpawnInTheShallowsByThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int top = buildChannel(helper, false);
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        int[] barbs = new int[CHANNEL_DEPTHS.length];
        int tilapia = 0;
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            for (int dz = 0; dz <= CZ1 - CZ0; dz++) {
                for (int y = top - 8; y <= top + 1; y++) {
                    BlockPos at = new BlockPos(corner.getX() + i, y, corner.getZ() + dz);
                    barbs[i] += worldWouldSpawn(level, SavannaEntities.BARB.get(), at) ? 1 : 0;
                    tilapia += worldWouldSpawn(level, SavannaEntities.TILAPIA.get(), at) ? 1 : 0;
                }
            }
        }
        buildChannel(helper, true);
        int total = java.util.Arrays.stream(barbs).sum();
        SavannaMod.LOGGER.info("[barb spawns] spots over the channel: barb {} (by column {}), tilapia {}", total, java.util.Arrays.toString(barbs), tilapia);
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            int want = CHANNEL_DEPTHS[i] <= 3 ? CHANNEL_DEPTHS[i] * (CZ1 - CZ0 + 1) : 0;
            if (barbs[i] != want) {
                wrong.append(" depth ").append(CHANNEL_DEPTHS[i]).append(": ").append(barbs[i]).append(", want ").append(want);
            }
        }
        if (!wrong.isEmpty()) {
            helper.fail("barbs spawn at the wrong spots:" + wrong);
        }
        var school = com.antcolonies.savanna.registry.SavannaMobCategories.riverSchool();
        var fish = com.antcolonies.savanna.registry.SavannaMobCategories.riverFish();
        helper.assertTrue(SavannaEntities.BARB.get().getCategory() == school, "the barb is in the " + SavannaEntities.BARB.get().getCategory().getName() + " group");
        helper.assertTrue(school.getMaxInstancesPerChunk() == 40, "the river school group's cap is " + school.getMaxInstancesPerChunk() + " (40 wanted)");
        helper.assertTrue(fish.getMaxInstancesPerChunk() == 45, "the river fish group's cap is " + fish.getMaxInstancesPerChunk() + " (45 wanted)");
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var river = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_river"));
        for (var key : java.util.List.of(net.minecraft.world.level.biome.Biomes.SAVANNA, river)) {
            var settings = biomes.getOrThrow(key).getMobSettings();
            var barb = settings.getMobs(school).unwrap().stream().filter(d -> d.type == SavannaEntities.BARB.get()).findFirst();
            helper.assertTrue(barb.isPresent() && barb.get().minCount == 8 && barb.get().maxCount == 14,
                    "barbs in " + key.location() + "'s river school list: " + barb.map(d -> d.minCount + " to " + d.maxCount).orElse("none") + " (8 to 14 wanted)");
            var t = settings.getMobs(fish).unwrap().stream().filter(d -> d.type == SavannaEntities.TILAPIA.get()).findFirst();
            helper.assertTrue(t.isPresent() && t.get().minCount == 2 && t.get().maxCount == 5,
                    "tilapia in " + key.location() + "'s river fish list: " + t.map(d -> d.minCount + " to " + d.maxCount).orElse("none") + " (2 to 5 wanted)");
        }
        helper.succeed();
    }

    /**
     * A hungry hatchling crocodile in the shallows snaps up a barb passing it (young crocodiles' first meal, 2026-11-20)
     * and is fed; a barb is not frightened of a hatchling.
     */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void aHatchlingCrocodileSnapsUpABarb(GameTestHelper helper) {
        buildPond(helper);
        var croc = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(3, TOP - 1, 5));
        croc.life().setUp(true, croc.getRandom());
        croc.life().setAge(0);
        croc.load(croc.saveWithoutId(new net.minecraft.nbt.CompoundTag()));       // its size and pace as it loads
        croc.setPersistenceRequired();
        croc.makeHungry();
        croc.strikeLuck = 1;        // the barb never darts off at the snap (the hunt is under test, not the dodge)
        BarbEntity barb = helper.spawn(SavannaEntities.BARB.get(), new BlockPos(5, TOP - 1, 5));
        barb.setNoAi(true);
        barb.setPersistenceRequired();
        helper.succeedWhen(() -> {
            helper.assertTrue(croc.isAlive(), "the hatchling died");
            helper.assertTrue(croc.life().isYoung(), "the crocodile is not a hatchling");
            helper.assertTrue(barb.isRemoved() && !croc.isHungry(),
                    "the barb " + (barb.isRemoved() ? "is gone" : "still swims " + String.format("%.1f", barb.distanceTo(croc)) + " blocks off")
                            + (croc.isHungry() ? ", the hatchling still hungry" : ""));
        });
    }

    /**
     * The river tie-up (2026-11-23): a hungry teen crocodile takes a bullfrog in the water (frogs are a big share of young
     * crocodiles' food; teens hunted fish only before). The frog is held still: the hunt is under test, not its escape.
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aTeenCrocodileTakesABullfrogInTheWater(GameTestHelper helper) {
        buildPond(helper);
        var croc = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(X1 - 2, TOP - 1, Z0 + 2));
        croc.life().setUp(true, croc.getRandom());
        croc.life().setAge(croc.life().numbers().youngTicks());
        croc.load(croc.saveWithoutId(new net.minecraft.nbt.CompoundTag()));       // its size and pace as it loads
        croc.setPersistenceRequired();
        croc.makeHungry();
        croc.strikeLuck = 1;
        com.antcolonies.savanna.animal.BullfrogEntity frog = helper.spawn(SavannaEntities.BULLFROG.get(), new BlockPos(X1 - 2, TOP, Z1 - 2));
        frog.setNoAi(true);
        frog.setPersistenceRequired();
        helper.succeedWhen(() -> {
            helper.assertTrue(croc.life().stage() == com.antcolonies.animal.LifeStage.TEEN, "the crocodile is a " + croc.life().stage() + ", not a teen");
            helper.assertTrue(!frog.isAlive() && !croc.isHungry(), "the frog " + (frog.isAlive() ? "still lives " + String.format("%.1f", frog.distanceTo(croc))
                    + " blocks off, in water " + frog.isInWater() : "is gone") + (croc.isHungry() ? ", the teen still hungry" : ""));
        });
    }

    /**
     * A hungry catfish takes a barb out of a school (a snack, 2026-11-20): one barb is gone, the catfish is fed, and at
     * the strike the others burst outward with their fast start.
     */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void aCatfishTakesABarbAndTheSchoolBursts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        CatfishEntity catfish = helper.spawn(SavannaEntities.CATFISH.get(), new BlockPos(X1 - 1, SURFACE + 2, Z0 + 1));
        catfish.setPersistenceRequired();
        catfish.makeHungry();
        java.util.List<BarbEntity> school = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            BarbEntity barb = helper.spawn(SavannaEntities.BARB.get(), new BlockPos(X1 - 2 - i % 2, SURFACE + 3 + i / 3, Z1 - 1 - i % 3));
            barb.setPersistenceRequired();
            school.add(barb);
        }
        double[] fastest = {0};
        helper.onEachTick(() -> {
            for (BarbEntity b : school) {
                if (!b.isRemoved()) {
                    fastest[0] = Math.max(fastest[0], b.getDeltaMovement().length());
                }
            }
        });
        helper.succeedWhen(() -> {
            long gone = school.stream().filter(BarbEntity::isRemoved).count();
            helper.assertTrue(gone == 1 && !catfish.isHungry() && catfish.isAlive(),
                    gone + " barbs gone" + (catfish.isHungry() ? ", the catfish still hungry" : "") + " (one eaten, the catfish fed)");
            helper.assertTrue(fastest[0] > 0.35, "the school never burst at the strike (fastest barb " + String.format("%.2f", fastest[0]) + " blocks a tick)");
            helper.assertTrue(level.getEntitiesOfClass(com.antcolonies.savanna.animal.RemainsEntity.class, catfish.getBoundingBox().inflate(6)).isEmpty(),
                    "a barb left remains (it is gulped whole)");
        });
    }

    // --- the fly clouds (2026-11-20) -----------------------------------------------------------------------------------

    /**
     * Fly clouds spawn by themselves over the shallows and the banks beside them (the spawning check list): over a
     * river's cross-section the world would spawn one at every spot of a column within 2 blocks of water 1 to 3 deep
     * (the water there or the free block over the top), nowhere over the deep middle; a cloud spawned the world's way is
     * set up to hang 1.7 to 3 blocks over the water with 4 to 12 flies; its own group (cap 18) in the savanna's waters.
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void flyCloudsSpawnOverTheShallowsByThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int top = buildChannel(helper, false);
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        var type = SavannaEntities.FLY_CLOUD.get();
        int[] spots = new int[CHANNEL_DEPTHS.length];
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            for (int dz = 0; dz <= CZ1 - CZ0; dz++) {
                for (int y = top - 8; y <= top + 1; y++) {
                    spots[i] += worldWouldSpawn(level, type, new BlockPos(corner.getX() + i, y, corner.getZ() + dz)) ? 1 : 0;
                }
            }
        }
        // the world's own spawn, drawn in the water over a shallow bed: set up over the water, full of flies
        BlockPos drawn = new BlockPos(corner.getX() + 1, top - 1, corner.getZ() + 3);
        FlyCloudEntity cloud = type.create(level);
        cloud.moveTo(drawn.getX() + 0.5, drawn.getY(), drawn.getZ() + 0.5, 0, 0);
        helper.assertTrue(net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(cloud, level, MobSpawnType.NATURAL),
                "the world refuses a fly cloud in the water over a shallow bed");
        cloud.finalizeSpawn(level, level.getCurrentDifficultyAt(drawn), MobSpawnType.NATURAL, null);
        double over = cloud.getY() - (top + 1);
        int flies = cloud.flies();
        cloud.discard();
        buildChannel(helper, true);
        SavannaMod.LOGGER.info("[fly clouds] spots over the channel by column {}; a new cloud {} blocks over the water with {} flies",
                java.util.Arrays.toString(spots), String.format("%.2f", over), flies);
        helper.assertTrue(over >= 1.65 && over <= 3.05, "a new cloud hangs " + over + " blocks over the water (1.7 to 3 wanted)");
        helper.assertTrue(flies >= 4 && flies <= 12, "a new cloud holds " + flies + " flies (4 to 12 wanted)");
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            boolean nearShallow = false;
            for (int j = Math.max(0, i - 2); j <= Math.min(CHANNEL_DEPTHS.length - 1, i + 2); j++) {
                nearShallow |= CHANNEL_DEPTHS[j] <= 3;
            }
            int want = nearShallow ? (CHANNEL_DEPTHS[i] + 1) * (CZ1 - CZ0 + 1) : 0;
            if (spots[i] != want) {
                wrong.append(" column ").append(i).append(" (depth ").append(CHANNEL_DEPTHS[i]).append("): ").append(spots[i]).append(", want ").append(want);
            }
        }
        if (!wrong.isEmpty()) {
            helper.fail("fly clouds spawn at the wrong spots:" + wrong);
        }
        var group = com.antcolonies.savanna.registry.SavannaMobCategories.flyCloud();
        helper.assertTrue(type.getCategory() == group && group.getMaxInstancesPerChunk() == 18,
                "the fly cloud is in the " + type.getCategory().getName() + " group, cap " + type.getCategory().getMaxInstancesPerChunk() + " (its own, 18 wanted)");
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        helper.assertTrue(biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA).getMobSettings().getMobs(group).unwrap().stream()
                .anyMatch(d -> d.type == type), "the fly cloud is not in the savanna's fly cloud spawn list");
        helper.succeed();
    }

    /**
     * A fly cloud hangs over the water between about 1.7 and 3 blocks up, wanders slowly round its spot, and cannot be
     * hurt or pushed (only /kill removes it).
     */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void aFlyCloudHangsOverTheWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        FlyCloudEntity cloud = helper.spawn(SavannaEntities.FLY_CLOUD.get(), new BlockPos(4, TOP + 1, 5));
        double water = helper.absolutePos(new BlockPos(0, TOP + 1, 0)).getY();     // the pond's top (the free block over it)
        double[] low = {99}, high = {-99}, farthest = {0};
        net.minecraft.world.phys.Vec3[] home = {null};
        helper.onEachTick(() -> {
            if (cloud.tickCount < 120) {
                return;     // rising to its height first
            }
            double over = cloud.getY() - water;
            low[0] = Math.min(low[0], over);
            high[0] = Math.max(high[0], over);
            if (home[0] == null) {
                home[0] = cloud.home();
            }
            farthest[0] = Math.max(farthest[0], Math.hypot(cloud.getX() - home[0].x, cloud.getZ() - home[0].z));
        });
        helper.runAtTickTime(380, () -> {
            boolean hurt = cloud.hurt(level.damageSources().generic(), 5.0F);
            helper.assertTrue(!hurt && cloud.isAlive() && !cloud.isPickable() && !cloud.isPushable(), "the fly cloud can be hurt, hit or pushed");
            helper.assertTrue(low[0] >= 1.6 && high[0] <= 3.1, String.format("the cloud hung %.2f to %.2f blocks over the water (1.7 to 3 wanted)", low[0], high[0]));
            helper.assertTrue(farthest[0] <= 2.8, String.format("the cloud wandered %.2f blocks from its spot (2.5 at most)", farthest[0]));
            SavannaMod.LOGGER.info("[fly clouds] hung {} to {} over the water, wandered {} from its spot",
                    String.format("%.2f", low[0]), String.format("%.2f", high[0]), String.format("%.2f", farthest[0]));
            helper.succeed();
        });
    }

    // --- the African bullfrog (2026-11-20) -----------------------------------------------------------------------------

    /** A stone bank (the monitor tests' ground) with a pool one block deep in it; returns nothing, the pool at x 6..9, z 3..7. */
    private static void bankWithPool(GameTestHelper helper) {
        for (int x = MX0; x <= MX1; x++) {
            for (int z = MZ0; z <= MZ1; z++) {
                boolean pool = x >= MX0 + 6 && x <= MX0 + 9 && z >= MZ0 + 3 && z <= MZ0 + 7;
                helper.setBlock(new BlockPos(x, TOP - 1, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, TOP, z), pool ? Blocks.WATER : Blocks.STONE);
                for (int y = TOP + 1; y <= TOP + 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static BullfrogEntity frogOnBank(GameTestHelper helper, int x, int z, float yaw) {
        BullfrogEntity frog = helper.spawn(SavannaEntities.BULLFROG.get(), new BlockPos(x, TOP + 1, z));
        frog.setPersistenceRequired();
        frog.setYRot(yaw);
        frog.yBodyRot = yaw;
        frog.yHeadRot = yaw;
        return frog;
    }

    /**
     * Bullfrogs spawn by themselves at the shallows' edges (the spawning check list): over a river's cross-section the
     * world would spawn one at every spot of a column within 2 blocks of water 1 to 3 deep (the water there or the free
     * block over the top), never over the deep middle; one spawned the world's way is set down afloat on the water, a
     * male 0.8 blocks long or a female 0.65 (each 0.9 to 1.1 of that); its own group (cap 4) in the savanna's waters, in ones and twos.
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void bullfrogsSpawnAtTheShallowsByThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int top = buildChannel(helper, false);
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        var type = SavannaEntities.BULLFROG.get();
        int[] spots = new int[CHANNEL_DEPTHS.length];
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            for (int dz = 0; dz <= CZ1 - CZ0; dz++) {
                for (int y = top - 8; y <= top + 1; y++) {
                    spots[i] += worldWouldSpawn(level, type, new BlockPos(corner.getX() + i, y, corner.getZ() + dz)) ? 1 : 0;
                }
            }
        }
        BlockPos drawn = new BlockPos(corner.getX() + 1, top - 1, corner.getZ() + 3);
        StringBuilder sizes = new StringBuilder();
        for (int n = 0; n < 6; n++) {
            BullfrogEntity frog = type.create(level);
            frog.moveTo(drawn.getX() + 0.5, drawn.getY(), drawn.getZ() + 0.5, 0, 0);
            helper.assertTrue(net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(frog, level, MobSpawnType.NATURAL),
                    "the world refuses a bullfrog in the water over a shallow bed");
            frog.finalizeSpawn(level, level.getCurrentDifficultyAt(drawn), MobSpawnType.NATURAL, null);
            double length = 0.8 * frog.getScale(), usual = frog.isMale() ? 0.8 : 0.65;
            sizes.append(frog.isMale() ? " male " : " female ").append(String.format("%.2f", length));
            helper.assertTrue(length > usual * 0.89 && length < usual * 1.11, "a " + (frog.isMale() ? "male" : "female") + " " + length + " blocks long");
            helper.assertTrue(Math.abs(frog.getY() - (top + 1 - 0.4)) < 0.01, "a new bullfrog set at y " + frog.getY() + ", not afloat on the water");
            frog.discard();
        }
        buildChannel(helper, true);
        SavannaMod.LOGGER.info("[bullfrog] spots over the channel by column {}; new frogs:{}", java.util.Arrays.toString(spots), sizes);
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            boolean nearShallow = false;
            for (int j = Math.max(0, i - 2); j <= Math.min(CHANNEL_DEPTHS.length - 1, i + 2); j++) {
                nearShallow |= CHANNEL_DEPTHS[j] <= 3;
            }
            int want = nearShallow ? (CHANNEL_DEPTHS[i] + 1) * (CZ1 - CZ0 + 1) : 0;
            if (spots[i] != want) {
                wrong.append(" column ").append(i).append(" (depth ").append(CHANNEL_DEPTHS[i]).append("): ").append(spots[i]).append(", want ").append(want);
            }
        }
        if (!wrong.isEmpty()) {
            helper.fail("bullfrogs spawn at the wrong spots:" + wrong);
        }
        var group = com.antcolonies.savanna.registry.SavannaMobCategories.bullfrog();
        helper.assertTrue(type.getCategory() == group && group.getMaxInstancesPerChunk() == 4,
                "the bullfrog is in the " + type.getCategory().getName() + " group, cap " + type.getCategory().getMaxInstancesPerChunk() + " (its own, 4 wanted)");
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var listed = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA).getMobSettings().getMobs(group).unwrap().stream()
                .filter(d -> d.type == type).findFirst();
        helper.assertTrue(listed.isPresent() && listed.get().minCount == 1 && listed.get().maxCount == 2,
                "bullfrogs in the savanna's list: " + listed.map(d -> d.minCount + " to " + d.maxCount).orElse("none") + " (1 to 2 wanted)");
        helper.succeed();
    }

    /** A hungry bullfrog at the water's edge lunges at a barb in the water in front of it and swallows it. */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void aHungryBullfrogLungesAtABarb(GameTestHelper helper) {
        bankWithPool(helper);
        BullfrogEntity frog = frogOnBank(helper, MX0 + 5, MZ0 + 5, -90);
        frog.makeHungry();
        BarbEntity barb = helper.spawn(SavannaEntities.BARB.get(), new BlockPos(MX0 + 6, TOP, MZ0 + 5));
        barb.setNoAi(true);
        barb.setPersistenceRequired();
        boolean[] lunged = {false};
        helper.onEachTick(() -> lunged[0] |= frog.action() == BullfrogEntity.LUNGE);
        helper.succeedWhen(() -> {
            helper.assertTrue(lunged[0], "the bullfrog never lunged (barb " + String.format("%.2f", barb.distanceTo(frog)) + " blocks off)");
            helper.assertTrue(barb.isRemoved() && !frog.isHungry(), "the barb " + (barb.isRemoved() ? "is gone" : "still swims")
                    + (frog.isHungry() ? ", the frog still hungry" : ""));
        });
    }

    // --- the tadpole (the river tie-up, 2026-11-23) --------------------------------------------------------------------

    /** A hungry bullfrog at the water's edge lunges at a tadpole in the water in front of it (real bullfrogs eat their own kind's). */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void aHungryBullfrogLungesAtATadpole(GameTestHelper helper) {
        bankWithPool(helper);
        BullfrogEntity frog = frogOnBank(helper, MX0 + 5, MZ0 + 5, -90);
        frog.makeHungry();
        com.antcolonies.savanna.animal.TadpoleEntity tadpole = helper.spawn(SavannaEntities.TADPOLE.get(), new BlockPos(MX0 + 6, TOP, MZ0 + 5));
        tadpole.setNoAi(true);
        tadpole.setPersistenceRequired();
        helper.succeedWhen(() -> helper.assertTrue(tadpole.isRemoved() && !frog.isHungry(), "the tadpole " + (tadpole.isRemoved() ? "is gone"
                : "still swims " + String.format("%.2f", tadpole.distanceTo(frog)) + " blocks off") + (frog.isHungry() ? ", the frog still hungry" : "")));
    }

    /**
     * Tadpoles spawn by themselves in the shallows (the spawning check list): over a river's cross-section the world would
     * spawn one at every water spot of a column 1 or 2 blocks deep and nowhere deeper; their own group (cap 16), listed in
     * the savanna's waters in schools of 6 to 10.
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void tadpolesSpawnInTheShallowsByThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int top = buildChannel(helper, false);
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        int[] spots = new int[CHANNEL_DEPTHS.length];
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            for (int dz = 0; dz <= CZ1 - CZ0; dz++) {
                for (int y = top - 8; y <= top + 1; y++) {
                    spots[i] += worldWouldSpawn(level, SavannaEntities.TADPOLE.get(), new BlockPos(corner.getX() + i, y, corner.getZ() + dz)) ? 1 : 0;
                }
            }
        }
        buildChannel(helper, true);
        SavannaMod.LOGGER.info("[tadpole spawns] spots over the channel by column {}", java.util.Arrays.toString(spots));
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            int want = CHANNEL_DEPTHS[i] <= 2 ? CHANNEL_DEPTHS[i] * (CZ1 - CZ0 + 1) : 0;
            if (spots[i] != want) {
                wrong.append(" depth ").append(CHANNEL_DEPTHS[i]).append(": ").append(spots[i]).append(", want ").append(want);
            }
        }
        helper.assertTrue(wrong.isEmpty(), "tadpoles spawn at the wrong spots:" + wrong);
        var group = com.antcolonies.savanna.registry.SavannaMobCategories.tadpole();
        helper.assertTrue(SavannaEntities.TADPOLE.get().getCategory() == group, "the tadpole is in the " + SavannaEntities.TADPOLE.get().getCategory().getName() + " group");
        helper.assertTrue(group.getMaxInstancesPerChunk() == 16, "the tadpole group's cap is " + group.getMaxInstancesPerChunk() + " (16 wanted)");
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var listed = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA).getMobSettings().getMobs(group).unwrap().stream()
                .filter(d -> d.type == SavannaEntities.TADPOLE.get()).findFirst();
        helper.assertTrue(listed.isPresent() && listed.get().minCount == 6 && listed.get().maxCount == 10,
                "tadpoles in the savanna's list: " + listed.map(d -> d.minCount + " to " + d.maxCount).orElse("none") + " (6 to 10 wanted)");
        helper.succeed();
    }

    /**
     * A tadpole's life: its leg buds show from day 3; at the end of day 3 it becomes a froglet (a young bullfrog, about a
     * third of a grown one's size) when there is room; beside 4 bullfrogs another does not make it (the frogs' cap).
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void aTadpoleGrowsLegsThenBecomesAFrogletOnlyWithRoom(GameTestHelper helper) {
        buildPond(helper);
        com.antcolonies.savanna.animal.TadpoleEntity legs = helper.spawn(SavannaEntities.TADPOLE.get(), new BlockPos(X1 - 1, SURFACE + 2, Z0 + 1));
        legs.setPersistenceRequired();
        legs.setAge(com.antcolonies.savanna.animal.TadpoleEntity.LEGS_AT - 20);
        com.antcolonies.savanna.animal.TadpoleEntity grows = helper.spawn(SavannaEntities.TADPOLE.get(), new BlockPos(X1 - 1, SURFACE + 2, Z1 - 1));
        grows.setPersistenceRequired();
        grows.setAge(com.antcolonies.savanna.animal.TadpoleEntity.FROGLET_AT - 20);
        java.util.Set<java.util.UUID> before = new java.util.HashSet<>();
        long[] grewAt = {-1};
        helper.onEachTick(() -> {
            if (helper.getTick() == 1) {
                helper.getLevel().getEntitiesOfClass(BullfrogEntity.class, helper.getBounds().inflate(40)).forEach(f -> before.add(f.getUUID()));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(legs.hasLegs(), "no legs at day 3 (age " + legs.age() + ")");
            helper.assertTrue(grows.isRemoved(), "the day-3 tadpole is still a tadpole (age " + grows.age() + ")");
            var froglets = helper.getLevel().getEntitiesOfClass(BullfrogEntity.class, helper.getBounds().inflate(40), f -> !before.contains(f.getUUID()));
            helper.assertTrue(froglets.size() == 1, froglets.size() + " froglets came of it (1 wanted)");
            BullfrogEntity froglet = froglets.get(0);
            helper.assertTrue(froglet.isFroglet() && froglet.getScale() < 0.4F, "the froglet: froglet " + froglet.isFroglet() + ", size " + froglet.getScale());
            if (grewAt[0] < 0) {
                grewAt[0] = helper.getTick();
            }
        });
    }

    /** Beside 4 bullfrogs a day-3 tadpole does not become a fifth: it is gone and no froglet comes of it. */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void aTadpoleBesideFourFrogsDoesNotBecomeAFifth(GameTestHelper helper) {
        buildPond(helper);
        java.util.List<BullfrogEntity> frogs = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            BullfrogEntity frog = helper.spawn(SavannaEntities.BULLFROG.get(), new BlockPos(X0 + i, TOP + 1, Z0 - 1));
            frog.setNoAi(true);
            frog.setPersistenceRequired();
            frogs.add(frog);
        }
        com.antcolonies.savanna.animal.TadpoleEntity late = helper.spawn(SavannaEntities.TADPOLE.get(), new BlockPos(X1 - 1, SURFACE + 2, Z1 - 1));
        late.setPersistenceRequired();
        late.setAge(com.antcolonies.savanna.animal.TadpoleEntity.FROGLET_AT - 20);
        helper.succeedWhen(() -> {
            helper.assertTrue(late.isRemoved(), "the day-3 tadpole is still a tadpole");
            int count = helper.getLevel().getEntitiesOfClass(BullfrogEntity.class, helper.getBounds().inflate(40)).size();
            helper.assertTrue(count == 4, count + " bullfrogs (4 wanted: no froglet beside the 4)");
        });
    }

    /** Tadpoles over an algae mat graze it: one noses down onto it (the graze), now and then taking a bite. */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void tadpolesGrazeTheAlgae(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        BlockPos corner = helper.absolutePos(new BlockPos(X0, TOP, Z0));
        RiverAlgaeFeature.lay(level, corner.getX(), corner.getZ(), X1 - X0 + 1, Z1 - Z0 + 1, corner.getY(), at -> true);
        java.util.List<com.antcolonies.savanna.animal.TadpoleEntity> school = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            com.antcolonies.savanna.animal.TadpoleEntity t = helper.spawn(SavannaEntities.TADPOLE.get(), new BlockPos(X0 + 1 + i % 3, TOP, Z0 + 2 + i / 3));
            t.setPersistenceRequired();
            school.add(t);
        }
        boolean[] grazed = {false};
        helper.onEachTick(() -> {
            for (var t : school) {
                grazed[0] |= t.action() == com.antcolonies.savanna.animal.TadpoleEntity.GRAZE;
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(grazed[0], "no tadpole grazed the algae"));
    }

    /** A hungry hatchling crocodile in the shallows snaps up a tadpole; a hungry catfish takes one (a snack, as a barb). */
    // (2600 ticks: a catfish's missed chase gives up at 600 and rests 1200, as in its tilapia test)
    @GameTest(template = "arena", timeoutTicks = 2600)
    public static void aHatchlingCrocodileAndACatfishEatTadpoles(GameTestHelper helper) {
        buildPond(helper);
        var croc = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(3, TOP - 1, 5));
        croc.life().setUp(true, croc.getRandom());
        croc.life().setAge(0);
        croc.load(croc.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        croc.setPersistenceRequired();
        croc.makeHungry();
        croc.strikeLuck = 1;
        com.antcolonies.savanna.animal.TadpoleEntity forCroc = helper.spawn(SavannaEntities.TADPOLE.get(), new BlockPos(5, TOP - 1, 5));
        forCroc.setNoAi(true);
        forCroc.setPersistenceRequired();
        CatfishEntity catfish = helper.spawn(SavannaEntities.CATFISH.get(), new BlockPos(X1 - 1, SURFACE + 2, Z0 + 1));
        catfish.setPersistenceRequired();
        catfish.makeHungry();
        com.antcolonies.savanna.animal.TadpoleEntity forCatfish = helper.spawn(SavannaEntities.TADPOLE.get(), new BlockPos(X1 - 2, SURFACE + 3, Z1 - 2));
        forCatfish.setPersistenceRequired();
        helper.succeedWhen(() -> {
            helper.assertTrue(forCroc.isRemoved() && !croc.isHungry(), "the hatchling's tadpole " + (forCroc.isRemoved() ? "is gone" : "still swims")
                    + (croc.isHungry() ? ", the hatchling still hungry" : ""));
            helper.assertTrue(forCatfish.isRemoved() && !catfish.isHungry(), "the catfish's tadpole " + (forCatfish.isRemoved() ? "is gone" : "still swims")
                    + (catfish.isHungry() ? ", the catfish still hungry" : ""));
        });
    }

    /**
     * The long tongue (the user's 2 blocks): a hungry bullfrog under a fly cloud hanging within reach flicks at it and
     * eats flies; another under a cloud hanging 3 blocks up (out of reach) never flicks.
     */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void aBullfrogTonguesFliesOnlyFromACloudInReach(GameTestHelper helper) {
        bankWithPool(helper);
        BullfrogEntity near = frogOnBank(helper, MX0 + 2, MZ0 + 2, -90);
        BullfrogEntity far = frogOnBank(helper, MX0 + 14, MZ0 + 8, -90);
        near.makeHungry();
        far.makeHungry();
        FlyCloudEntity low = cloudAt(helper, near.position().add(1.0, 1.9, 0));
        FlyCloudEntity high = cloudAt(helper, far.position().add(0.6, 3.5, 0));
        boolean[] flicked = {false, false, false};
        int[] flicks = {0, -1};         // flicks by the near frog (each a new action), and the action id last counted
        helper.onEachTick(() -> {
            flicked[0] |= near.action() == BullfrogEntity.TONGUE;
            flicked[1] |= far.action() == BullfrogEntity.TONGUE;
            flicked[2] |= near.action() == BullfrogEntity.AIM;
            if (near.action() == BullfrogEntity.TONGUE && near.actionId() != flicks[1]) {
                flicks[0]++;
                flicks[1] = near.actionId();
            }
        });
        helper.runAtTickTime(380, () -> {
            helper.assertTrue(flicked[0] && low.flies() < 4, "the frog under the low cloud " + (flicked[0] ? "flicked" : "never flicked")
                    + ", its cloud holds " + low.flies() + " of 4 flies");
            helper.assertTrue(!flicked[1] && high.flies() == 4, "the frog under the cloud 3 blocks up flicked at it (out of reach)");
            // the polish's step 3: it fixes its eyes on a fly before flicking, and flicks again while flies stay in reach
            helper.assertTrue(flicked[2], "the frog flicked without aiming at a fly first");
            helper.assertTrue(flicks[0] >= 2, "the frog under the low cloud flicked only " + flicks[0] + " time(s), not flick after flick");
            SavannaMod.LOGGER.info("[bullfrog] the frog under the low cloud ate {} flies", 4 - low.flies());
            helper.succeed();
        });
    }

    private static FlyCloudEntity cloudAt(GameTestHelper helper, net.minecraft.world.phys.Vec3 at) {
        FlyCloudEntity cloud = SavannaEntities.FLY_CLOUD.get().create(helper.getLevel());
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        cloud.saveWithoutId(tag);
        tag.putBoolean("NoAI", true);
        tag.putInt("Flies", 4);
        tag.putInt("Most", 4);
        tag.putInt("Refill", 99999);
        cloud.load(tag);
        cloud.moveTo(at.x, at.y, at.z, 0, 0);
        cloud.setPersistenceRequired();
        helper.getLevel().addFreshEntity(cloud);
        cloud.holdLandings(true);       // no fly lands by itself (a test lands one where it wants it)
        return cloud;
    }

    // --- landed flies (the river food web's step 1, 2026-11-23) ---------------------------------------------------------

    /** Keeps a fly landed at this spot (relative): a new one lands each time the last is eaten or flies off. */
    private static void keepAFlyLanded(GameTestHelper helper, FlyCloudEntity cloud, net.minecraft.world.phys.Vec3 spot) {
        net.minecraft.world.phys.Vec3 at = helper.absoluteVec(spot);
        helper.onEachTick(() -> {
            if (cloud.landState() == FlyCloudEntity.LAND_NONE && cloud.flies() > 0) {
                cloud.landAt(at);
            }
        });
    }

    /** A barb sips a fly landed on the water near it (any time of day: no dawn or dusk set). */
    @GameTest(template = "arena", timeoutTicks = 500)
    public static void aBarbSipsALandedFlyOffTheWater(GameTestHelper helper) {
        buildPond(helper);
        BarbEntity barb = helper.spawn(SavannaEntities.BARB.get(), new BlockPos(5, TOP - 1, 5));
        barb.setPersistenceRequired();
        FlyCloudEntity cloud = cloudAt(helper, helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, TOP + 3.0, 5.5)));
        keepAFlyLanded(helper, cloud, new net.minecraft.world.phys.Vec3(3.5, TOP + 0.89, 5.5));
        boolean[] sipped = {false};
        helper.onEachTick(() -> sipped[0] |= barb.action() == BarbEntity.SIP);
        helper.succeedWhen(() -> helper.assertTrue(sipped[0] && cloud.flies() < 4, "the barb " + (sipped[0] ? "sipped" : "never sipped")
                + ", the cloud holds " + cloud.flies() + " of 4 flies"));
    }

    /** A hatchling crocodile, not hungry, snaps up a fly landed on the water near it. */
    @GameTest(template = "arena", timeoutTicks = 500)
    public static void aHatchlingCrocodileSnapsALandedFly(GameTestHelper helper) {
        buildPond(helper);
        var croc = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(3, TOP - 1, 5));
        croc.life().setUp(true, croc.getRandom());
        croc.life().setAge(0);
        croc.load(croc.saveWithoutId(new net.minecraft.nbt.CompoundTag()));       // its size and pace as it loads
        croc.setPersistenceRequired();
        FlyCloudEntity cloud = cloudAt(helper, helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, TOP + 3.0, 5.5)));
        keepAFlyLanded(helper, cloud, new net.minecraft.world.phys.Vec3(5.5, TOP + 0.89, 5.5));
        boolean[] snapped = {false};
        helper.onEachTick(() -> snapped[0] |= croc.jaws() == com.antcolonies.savanna.animal.CrocodileEntity.JAWS_SNAP_LEFT
                || croc.jaws() == com.antcolonies.savanna.animal.CrocodileEntity.JAWS_SNAP_RIGHT);
        helper.succeedWhen(() -> {
            helper.assertTrue(croc.life().isYoung(), "the crocodile is not a hatchling");
            helper.assertTrue(snapped[0] && cloud.flies() < 4, "the hatchling " + (snapped[0] ? "snapped" : "never snapped")
                    + " (" + String.format("%.1f", croc.position().distanceTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, TOP + 0.89, 5.5))))
                    + " blocks from the fly), the cloud holds " + cloud.flies() + " of 4 flies");
        });
    }

    /** A hungry bullfrog tongues a fly landed on the ground in front of it, though its cloud hangs out of reach. */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void aBullfrogTonguesALandedFly(GameTestHelper helper) {
        bankWithPool(helper);
        BullfrogEntity frog = frogOnBank(helper, MX0 + 2, MZ0 + 2, -90);
        frog.makeHungry();
        FlyCloudEntity cloud = cloudAt(helper, frog.position().add(0.8, 3.5, 0));
        keepAFlyLanded(helper, cloud, new net.minecraft.world.phys.Vec3(MX0 + 2 + 0.5 + 1.2, TOP + 1.0, MZ0 + 2 + 0.5));
        boolean[] flicked = {false};
        helper.onEachTick(() -> flicked[0] |= frog.action() == BullfrogEntity.TONGUE);
        helper.succeedWhen(() -> helper.assertTrue(flicked[0] && cloud.flies() < 4, "the frog " + (flicked[0] ? "flicked" : "never flicked")
                + ", the cloud holds " + cloud.flies() + " of 4 flies"));
    }

    /** With no eater near, a cloud's flies land by themselves now and then and every one flies back up: none is lost. */
    @GameTest(template = "arena", timeoutTicks = 1100)
    public static void aCloudWithNobodyNearLosesNoLandedFlies(GameTestHelper helper) {
        bankWithPool(helper);
        FlyCloudEntity cloud = cloudAt(helper, helper.absoluteVec(new net.minecraft.world.phys.Vec3(MX0 + 8, TOP + 2.0, MZ0 + 5)));
        cloud.holdLandings(false);
        int[] landings = {0};
        byte[] last = {FlyCloudEntity.LAND_NONE};
        helper.onEachTick(() -> {
            byte now = cloud.landState();
            if (now == FlyCloudEntity.LAND_DOWN && last[0] != now) {
                landings[0]++;
            }
            last[0] = now;
        });
        helper.runAtTickTime(1050, () -> {
            helper.assertTrue(landings[0] >= 1, "no fly landed by itself in 50 s");
            helper.assertTrue(cloud.flies() == 4, "the cloud holds " + cloud.flies() + " of 4 flies with nobody to eat them");
            SavannaMod.LOGGER.info("[flies] {} landings in 50 s", landings[0]);
            helper.succeed();
        });
    }

    /** Bold, not shy: a player standing close makes a bullfrog puff up, then lunge and bite (1 heart). */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void aBullfrogPuffsUpAndBitesAPlayerWhoStaysClose(GameTestHelper helper) {
        bankWithPool(helper);
        BullfrogEntity frog = frogOnBank(helper, MX0 + 3, MZ0 + 8, -90);
        frog.makeHungry();
        net.minecraft.server.level.ServerPlayer player = MatabeleDefenceTests.survivalPlayer(helper);
        boolean[] puffed = {false};
        helper.onEachTick(() -> {
            player.moveTo(frog.getX() + 1.2, frog.getY(), frog.getZ(), 90.0F, 0.0F);
            puffed[0] |= frog.action() == BullfrogEntity.PUFF;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(puffed[0], "the bullfrog never puffed up at the player");
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "the bullfrog never bit the player");
            helper.assertTrue(player.getMaxHealth() - player.getHealth() <= 2.01F, "the bite took " + (player.getMaxHealth() - player.getHealth()) + " health (1 heart wanted)");
        });
    }

    /**
     * A bullfrog sent somewhere gets there (the polish's step 1, 2026-11-20): over 5 blocks of bank and up two one-block
     * steps two blocks apart, hopping (its legs: BullfrogMoveControl), never giving up half way and never sliding up a
     * step without a hop. The course film found it stopping for good after 2.2 blocks, then (the user) stuck hopping
     * straight up and down against the stairs' second step.
     */
    @GameTest(template = "arena", timeoutTicks = 700)
    public static void aBullfrogHopsWhereItIsSentAndUpAStep(GameTestHelper helper) {
        bankWithPool(helper);
        int z = MZ0 + 9;
        for (int x = MX0 + 19; x <= MX1; x++) {
            for (int zz = MZ0; zz <= MZ1; zz++) {
                helper.setBlock(new BlockPos(x, TOP + 1, zz), Blocks.STONE);
                if (x >= MX0 + 21) {
                    helper.setBlock(new BlockPos(x, TOP + 2, zz), Blocks.STONE);
                }
            }
        }
        BullfrogEntity frog = frogOnBank(helper, MX0 + 14, z, -90);
        frog.setUp(true, 1.0F);
        BlockPos goal = helper.absolutePos(new BlockPos(MX0 + 23, TOP + 3, z));
        boolean[] hopped = {false};
        double[] slid = {0};
        net.minecraft.world.phys.Vec3[] stopped = {null};       // where its route ended (it may wander on after)
        helper.runAtTickTime(5, () -> frog.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0));
        helper.onEachTick(() -> {
            byte a = frog.action();
            hopped[0] |= a == BullfrogEntity.HOP;
            // up the step: every rise happens in a hop (a rise of over 0.3 in one tick outside a hop is a slide up)
            double rise = frog.getY() - frog.yo;
            if (rise > 0.3 && a != BullfrogEntity.HOP) {
                slid[0] = Math.max(slid[0], rise);
            }
            if (stopped[0] == null && helper.getTick() > 10 && frog.getNavigation().isDone() && a == BullfrogEntity.NONE) {
                stopped[0] = frog.position();
            }
            if (helper.getTick() % 40 == 0) {
                SavannaMod.LOGGER.info("[bullfrog] sent: tick {} at {} action {} route {}", helper.getTick(),
                        frog.position(), a, frog.getNavigation().isDone() ? "done" : "on");
            }
        });
        helper.runAtTickTime(680, () -> {
            net.minecraft.world.phys.Vec3 at = stopped[0] == null ? frog.position() : stopped[0];
            double off = Math.hypot(at.x - (goal.getX() + 0.5), at.z - (goal.getZ() + 0.5));
            // a route ends within a block of where it was sent (Minecraft's own moveTo), so within 1.6 of the goal's middle
            helper.assertTrue(off < 1.6 && at.y > goal.getY() - 0.5, "the bullfrog sent 9 blocks and up two steps stopped at "
                    + at + ", " + String.format("%.1f", off) + " blocks short of " + goal);
            helper.assertTrue(hopped[0], "the bullfrog got there without a single hop");
            helper.assertTrue(slid[0] == 0, "the bullfrog slid up " + slid[0] + " blocks in a tick without hopping");
            helper.succeed();
        });
    }

    /**
     * A bullfrog in the water sent onto a bank about level with the water hauls itself out (the polish's step 5, a move
     * of its own: arms onto the bank, the pull, the kick), never gliding up it; one sent onto a bank a block over the
     * water stays in (the user, 2026-11-20: African bullfrogs are too heavy to climb).
     */
    @GameTest(template = "arena", timeoutTicks = 500)
    public static void aBullfrogClimbsOutOfTheWaterOntoTheBank(GameTestHelper helper) {
        bankWithPool(helper);
        // the second frog's bank a block above the water: too high (the user, 2026-11-20: African bullfrogs are too
        // heavy to climb), so it gets out where the pool's edge is low (it may then hop up the step on land)
        for (int x = MX0 + 10; x <= MX1; x++) {
            for (int zz = MZ0 + 6; zz <= MZ1; zz++) {
                helper.setBlock(new BlockPos(x, TOP + 1, zz), Blocks.STONE);
            }
        }
        int[][] scenes = {{MZ0 + 4, TOP + 1}, {MZ0 + 6, TOP + 2}};     // (row, the bank top) level, then high
        for (int[] scene : scenes) {
            int z = scene[0];
            BullfrogEntity frog = helper.spawn(SavannaEntities.BULLFROG.get(), new BlockPos(MX0 + 7, TOP, z));
            frog.setPersistenceRequired();
            frog.setUp(true, 1.0F);
            BlockPos goal = helper.absolutePos(new BlockPos(MX0 + 12, scene[1], z));
            String bank = scene[1] == TOP + 1 ? "level bank" : "bank a block up";
            boolean[] climbed = {false};
            boolean[] out = {false};        // out of the water, on the bank, when its route ended (it may wander back in after)
            double[] highestClimb = {-999};  // the highest a climb out ever ended (never onto a bank a block over the water)
            byte[] last = {BullfrogEntity.NONE};
            helper.runAtTickTime(20, () -> frog.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0));
            helper.onEachTick(() -> {
                climbed[0] |= frog.action() == BullfrogEntity.CLIMB_OUT;
                if (last[0] == BullfrogEntity.CLIMB_OUT && frog.action() != BullfrogEntity.CLIMB_OUT) {
                    highestClimb[0] = Math.max(highestClimb[0], frog.getY());
                }
                last[0] = frog.action();
                if (climbed[0] && frog.action() == BullfrogEntity.NONE && frog.getNavigation().isDone()) {
                    out[0] |= !frog.isInWater() && frog.getY() >= goal.getY() - 0.05;
                }
                if (helper.getTick() % 10 == 0) {
                    SavannaMod.LOGGER.info("[bullfrog] out ({}): tick {} at {} action {} water {} route {}", bank, helper.getTick(),
                            frog.position(), frog.action(), frog.isInWater(), frog.getNavigation().isDone() ? "done" : "on");
                }
            });
            double levelTop = helper.absolutePos(new BlockPos(0, TOP + 1, 0)).getY();
            helper.runAtTickTime(479, () -> {
                helper.assertTrue(climbed[0], "the bullfrog sent onto the " + bank + " never climbed out (it is at " + frog.position()
                        + ", in the water: " + frog.isInWater() + ")");
                helper.assertTrue(highestClimb[0] < levelTop + 0.1, "the bullfrog climbed out onto a bank a block over the water (to y "
                        + highestClimb[0] + "; too heavy to climb, it should get out where the edge is low)");
                helper.assertTrue(out[0], "the bullfrog sent onto the " + bank + " never ended up on it (now at " + frog.position()
                        + ", in the water: " + frog.isInWater() + ")");
            });
        }
        helper.runAtTickTime(480, helper::succeed);
    }

    /**
     * A stone bank with a pond in it (the hunt, 2026-11-20): x {@code MX0+6..MX0+14}, z {@code MZ0+1..MZ0+9}, the water's
     * top at {@code TOP}; a shelf 2 blocks deep up to {@code MX0+9}, 3 deep past it; the bank level with the water. Past
     * {@code MX0+17} a second pool (x {@code MX0+19..MX0+22}, z {@code MZ0+3..MZ0+7}, 2 deep) whose banks stand a block
     * over its water all round: a frog in it cannot get out (too heavy to climb, the user).
     */
    private static void bankWithPond(GameTestHelper helper) {
        for (int x = MX0; x <= MX1; x++) {
            for (int z = MZ0; z <= MZ1; z++) {
                boolean pond = x >= MX0 + 6 && x <= MX0 + 14 && z >= MZ0 + 1 && z <= MZ0 + 9;
                boolean pool = x >= MX0 + 19 && x <= MX0 + 22 && z >= MZ0 + 3 && z <= MZ0 + 7;
                boolean poolBank = !pool && x >= MX0 + 17 && x <= MX0 + 24 && z >= MZ0 + 1 && z <= MZ0 + 9;
                int bed = pond ? (x <= MX0 + 9 ? TOP - 2 : TOP - 3) : pool ? TOP - 2 : TOP;
                for (int y = TOP - 4; y <= TOP + 5; y++) {
                    BlockState state = y <= bed || poolBank && y == TOP + 1 ? Blocks.STONE.defaultBlockState()
                            : y <= TOP && (pond || pool) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    helper.setBlock(new BlockPos(x, y, z), state);
                }
            }
        }
    }

    /**
     * Hungry bullfrogs hunt (the user, 2026-11-20: "hunting the snails underwater", and frogs too slow to act when
     * hunting): one on the bank 6 blocks from a snail on the bed of a pond 2 blocks deep goes to it, dives and eats it;
     * one afloat over the deep end dives 2 blocks to a snail on the bed there. Logs every second what each frog does, and
     * how long a frog that is hungry sits doing nothing, and how long one in a pool it cannot leave (banks a block high)
     * swims against a bank.
     */
    @GameTest(template = "arena", timeoutTicks = 1000)
    public static void hungryBullfrogsHuntSnailsOnThePondBed(GameTestHelper helper) {
        bankWithPond(helper);
        BullfrogEntity onBank = frogOnBank(helper, MX0 + 3, MZ0 + 5, -90);
        onBank.setUp(true, 1.0F);
        BullfrogEntity afloat = helper.spawn(SavannaEntities.BULLFROG.get(), new BlockPos(MX0 + 13, TOP, MZ0 + 2));
        afloat.setPersistenceRequired();
        afloat.setUp(false, 1.0F);
        BullfrogEntity trapped = helper.spawn(SavannaEntities.BULLFROG.get(), new BlockPos(MX0 + 20, TOP, MZ0 + 5));
        trapped.setPersistenceRequired();
        trapped.setUp(true, 1.0F);
        trapped.ateMeal();
        onBank.makeHungry();
        afloat.makeHungry();
        com.antcolonies.savanna.animal.SnailEntity near = helper.spawn(SavannaEntities.SNAIL.get(), new BlockPos(MX0 + 9, TOP - 1, MZ0 + 5));
        com.antcolonies.savanna.animal.SnailEntity deep = helper.spawn(SavannaEntities.SNAIL.get(), new BlockPos(MX0 + 12, TOP - 2, MZ0 + 7));
        near.setPersistenceRequired();
        deep.setPersistenceRequired();
        BullfrogEntity[] frogs = {onBank, afloat, trapped};
        String[] names = {"bank", "afloat", "trapped"};
        int[] idleHungry = new int[2], pushing = {0};
        int[] ateAt = {-1, -1};
        helper.runAtTickTime(20, () -> {
            BlockPos out = helper.absolutePos(new BlockPos(MX0 + 25, TOP + 1, MZ0 + 5));       // over the high bank
            trapped.getNavigation().moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 1.0);
        });
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            for (int i = 0; i < 2; i++) {
                BullfrogEntity f = frogs[i];
                if (f.isHungry() && f.action() == BullfrogEntity.NONE && f.getNavigation().isDone()) {     // sitting or afloat, going nowhere
                    idleHungry[i]++;
                }
                if (ateAt[i] < 0 && !f.isHungry()) {
                    ateAt[i] = (int) tick;
                }
            }
            if (!trapped.getNavigation().isDone() && trapped.horizontalCollision) {
                pushing[0]++;
            }
            if (tick % 20 == 0) {
                for (int i = 0; i < 3; i++) {
                    BullfrogEntity f = frogs[i];
                    SavannaMod.LOGGER.info("[bullfrog hunt] tick {} {}: at {} action {} hungry {} water {} route {}{}", tick, names[i],
                            String.format("%.2f %.2f %.2f", f.getX(), f.getY(), f.getZ()), f.action(), f.isHungry(), f.isInWater(),
                            f.getNavigation().isDone() ? "done" : "on", f.horizontalCollision ? " bumping" : "");
                }
            }
        });
        helper.runAtTickTime(980, () -> {
            SavannaMod.LOGGER.info("[bullfrog hunt] ate at ticks {} and {}; hungry and idle {} and {} ticks; the trapped frog pushed at a bank {} ticks; snails {} {}",
                    ateAt[0], ateAt[1], idleHungry[0], idleHungry[1], pushing[0], near.isRemoved() ? "eaten" : "alive", deep.isRemoved() ? "eaten" : "alive");
            helper.assertTrue(near.isRemoved() && deep.isRemoved(), "the snails on the bed: " + (near.isRemoved() ? "eaten" : "alive")
                    + " (2 deep), " + (deep.isRemoved() ? "eaten" : "alive") + " (3 deep); frogs ate at ticks " + ateAt[0] + ", " + ateAt[1]);
            helper.assertTrue(idleHungry[0] < 40 && idleHungry[1] < 40, "hungry frogs sat going nowhere " + idleHungry[0] + " and "
                    + idleHungry[1] + " ticks with a snail in sight (2 s at most wanted)");
            helper.assertTrue(pushing[0] < 60, "the frog in the pool it cannot leave swam against its bank " + pushing[0] + " ticks");
            helper.succeed();
        });
    }

    /**
     * World eggs step 1 (2026-11-20, {@code docs/plans/world-eggs.md}): on a nest site's chunk new land lays covered
     * crocodile nests on the dry river sand 2 to 6 blocks back from the water, at least 6 apart, each a clutch of the
     * crocodile's 6 to 10 eggs laid under 3 days ago (so none hatches at once); a mother by a nest is a grown female in the
     * water close by, guarding that nest. Built over one whole chunk: a strip of water 2 deep and a golden sand bank level
     * with its top.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void newLandLaysCrocodileNestsOnTheSandBank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(4, TOP, 4));
        int x0 = (base.getX() + 15) & ~15, z0 = (base.getZ() + 15) & ~15, y = base.getY();
        BlockState sand = SavannaBlocks.GOLDEN_SAND.dry().get().defaultBlockState();
        for (int x = x0 - 3; x <= x0 + 18; x++) {
            for (int z = z0 - 3; z <= z0 + 18; z++) {
                boolean water = z < z0 + 4;
                for (int dy = -3; dy <= 4; dy++) {
                    BlockState state = dy <= -2 ? Blocks.STONE.defaultBlockState() : dy <= 0 ? (water ? Blocks.WATER.defaultBlockState() : dy == 0 ? sand
                            : Blocks.STONE.defaultBlockState()) : Blocks.AIR.defaultBlockState();
                    level.setBlock(new BlockPos(x, y + dy, z), state, 2);
                }
            }
        }
        long now = level.getGameTime();
        int laid = com.antcolonies.savanna.worldgen.CrocodileNestFeature.lay(level, net.minecraft.util.RandomSource.create(20261120L), x0, z0, y, now);
        java.util.List<BlockPos> nests = new java.util.ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(x0, y + 1, z0, x0 + 15, y + 1, z0 + 15)) {
            if (level.getBlockState(p).getBlock() instanceof com.antcolonies.savanna.block.CrocodileNestBlock) {
                nests.add(p.immutable());
            }
        }
        var mothers = level.getEntitiesOfClass(com.antcolonies.savanna.animal.CrocodileEntity.class,
                new net.minecraft.world.phys.AABB(x0 - 10, y - 4, z0 - 10, x0 + 26, y + 6, z0 + 26));
        SavannaMod.LOGGER.info("[world nests] laid {}: {}; mothers {}", laid, nests, mothers.size());
        helper.assertTrue(laid >= 1 && laid <= 4 && nests.size() == laid, "laid " + laid + " nests, found " + nests.size() + " (1 to 4 wanted)");
        for (BlockPos n : nests) {
            int toWater = n.getZ() - (z0 + 3);
            helper.assertTrue(toWater >= 2 && toWater <= 6, "a nest " + toWater + " blocks from the water (2 to 6 wanted) at " + n);
            helper.assertTrue(!level.getBlockState(n).getValue(com.antcolonies.savanna.block.CrocodileNestBlock.OPEN), "a nest laid open at " + n);
            var clutch = (com.antcolonies.animal.EggClutchBlockEntity) level.getBlockEntity(n);
            helper.assertTrue(clutch.count() >= 6 && clutch.count() <= 10, "a nest of " + clutch.count() + " eggs (6 to 10 wanted)");
            long age = now - clutch.laid();
            helper.assertTrue(age >= 0 && age <= 3 * 24000L && clutch.motherKind() == SavannaEntities.CROCODILE.get(),
                    "a nest laid " + age + " ticks ago (3 days at most) by " + clutch.motherKind());
            for (BlockPos m : nests) {
                helper.assertTrue(m == n || m.distSqr(n) >= 36, "two nests " + Math.sqrt(m.distSqr(n)) + " blocks apart (6 at least)");
            }
        }
        for (var mother : mothers) {
            helper.assertTrue(!mother.life().isMale() && !mother.life().isYoung() && nests.contains(mother.nestAt())
                    && level.getFluidState(mother.blockPosition()).is(net.minecraft.tags.FluidTags.WATER),      // (isInWater waits for her first tick)
                    "a world-laid mother: male " + mother.life().isMale() + ", young " + mother.life().isYoung() + ", guards " + mother.nestAt()
                    + ", at " + mother.blockPosition());
            mother.discard();
        }
        helper.succeed();
    }

    /** A Nile monitor coming at a bullfrog on the bank makes it leap into the water (Minecraft's long-jump aim). */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void aBullfrogLeapsIntoTheWaterFromAMonitor(GameTestHelper helper) {
        bankWithPool(helper);
        BullfrogEntity frog = frogOnBank(helper, MX0 + 4, MZ0 + 5, -90);
        frog.ateMeal();
        var monitor = helper.spawn(SavannaEntities.MONITOR.get(), new BlockPos(MX0 + 1, TOP + 1, MZ0 + 5));
        monitor.setPersistenceRequired();
        boolean[] leapt = {false}, wet = {false};
        helper.onEachTick(() -> {
            leapt[0] |= frog.action() == BullfrogEntity.LEAP;
            wet[0] |= leapt[0] && frog.isInWater();     // it may flee on out of so small a pool afterwards
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(leapt[0], "the bullfrog never leapt (monitor " + String.format("%.1f", monitor.distanceTo(frog)) + " blocks off)");
            helper.assertTrue(wet[0], "the bullfrog leapt but never came down in the water (at " + frog.blockPosition() + ")");
        });
    }

    /**
     * At midday a bullfrog on soft ground by the water digs in backward (only its back and eyes out) and stays dug; a
     * player coming within 3 blocks brings it out, and once its digging-out clip is over it is a free frog again (a bug
     * found while building: the digging out never ended, 2026-11-20).
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aBullfrogDigsInAtMiddayAndComesOutForAPlayer(GameTestHelper helper) {
        bankWithPool(helper);
        com.antcolonies.gametest.TestClock.still(helper, 12.0);
        for (int x = MX0; x <= MX0 + 5; x++) {
            for (int z = MZ0 + 2; z <= MZ0 + 8; z++) {
                helper.setBlock(new BlockPos(x, TOP, z), Blocks.DIRT);
            }
        }
        BullfrogEntity frog = frogOnBank(helper, MX0 + 4, MZ0 + 5, 90);
        frog.ateMeal();
        net.minecraft.server.level.ServerPlayer player = MatabeleDefenceTests.survivalPlayer(helper);
        player.moveTo(frog.getX() + 9, frog.getY(), frog.getZ() + 6);
        int[] dugAt = {-1}, outAt = {-1};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (dugAt[0] < 0 && frog.action() == BullfrogEntity.DUG) {
                dugAt[0] = (int) tick;
            }
            if (dugAt[0] >= 0 && tick < dugAt[0] + 60) {
                player.moveTo(frog.getX() + 9, frog.getY(), frog.getZ() + 6);       // far off a while: it stays dug
                helper.assertTrue(frog.isDug(), "the bullfrog came out of the ground with nobody near");
            } else if (dugAt[0] >= 0) {
                player.moveTo(frog.getX() + 1.5, frog.getY(), frog.getZ(), 90.0F, 0.0F);
                if (outAt[0] < 0 && !frog.isDug()) {
                    outAt[0] = (int) tick;
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(dugAt[0] >= 0, "the bullfrog never dug in at midday on soft ground (action " + frog.action() + ")");
            helper.assertTrue(outAt[0] >= 0, "the bullfrog never finished coming out of the ground for a close player (action " + frog.action() + ")");
            SavannaMod.LOGGER.info("[bullfrog] dug in at tick {}, out at {}", dugAt[0], outAt[0]);
        });
    }
}
