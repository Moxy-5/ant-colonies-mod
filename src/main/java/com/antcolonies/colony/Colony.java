package com.antcolonies.colony;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.TermiteKingEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.species.SpeciesProfiles;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * One colony's "brain" data. Species-neutral: the species is an id that points at a species profile ({@link #profile()}).
 * Member counts are stored per caste (mostly virtual, not real mobs).
 *
 * <p>The colony itself holds who it is, its headcount and the workers' roles. Everything else lives in its parts, one
 * per subject: {@link #nest()} (the underground plan and digging), {@link #walls()} (sealing the nest), {@link #hill()},
 * {@link #food()} (seeds), {@link #bakery()} (ant bread), {@link #queenCare()} (feeding the queen and her eggs),
 * {@link #brood()} (eggs and larvae), {@link #refuse()} (dead ants), {@link #mound()} (the termite mound),
 * {@link #foragingHoles()} (the termites' ways out to forage) and {@link #garden()} (the termites' fungus garden).
 */
public class Colony {
    /** "No time saved": a hunger clock that has not started yet. */
    public static final long NOT_SET = Long.MIN_VALUE;

    private final UUID id;
    private final ResourceLocation species;
    private final UUID queenId;
    private final BlockPos center;
    private final Map<String, Integer> casteCounts = new LinkedHashMap<>();

    /** The role of each worker: ant -> role. Not saved: after a reload the workers ask again and get a balanced share. */
    private final Map<UUID, ColonyRole> roles = new HashMap<>();
    /** Whether the workers of this colony get hungry. On, except in some tests. Not saved. */
    private boolean hungerOn = true;
    /** Tests only: forces foraging time on or off (null: the real clock and weather). Not saved. */
    @Nullable
    private Boolean foragingTimeForTests;

    private final Nest nest;
    private final NestWalls walls;
    private final Hill hill;
    private final FoodStore food;
    private final Bakery bakery;
    private final QueenCare queenCare;
    private final Brood brood;
    private final Refuse refuse;
    private final Mound mound;
    private final ForagingHoles foragingHoles;
    private final FungusGarden garden;

    public Colony(UUID id, ResourceLocation species, UUID queenId, BlockPos center) {
        this.id = id;
        this.species = species;
        this.queenId = queenId;
        this.center = center;
        this.nest = new Nest(this);
        this.walls = new NestWalls(this);
        this.hill = new Hill(this);
        this.food = new FoodStore(this);
        this.bakery = new Bakery(this);
        this.queenCare = new QueenCare(this);
        this.brood = new Brood(this);
        this.refuse = new Refuse(this);
        this.mound = new Mound(this);
        this.foragingHoles = new ForagingHoles(this);
        this.garden = new FungusGarden(this);
    }

    /** The underground nest: its plan, the next block to dig, and the dug-out cells of each room. */
    public Nest nest() {
        return nest;
    }

    /** The nest walls: openings to seal. */
    public NestWalls walls() {
        return walls;
    }

    /** The hill around the entrance. */
    public Hill hill() {
        return hill;
    }

    /** Seeds: fetching, clipping, storing and taking them. */
    public FoodStore food() {
        return food;
    }

    /** Ant bread: milling, storing and taking it. */
    public Bakery bakery() {
        return bakery;
    }

    /** The queen: feeding her, her eggs and her hunger. */
    public QueenCare queenCare() {
        return queenCare;
    }

    /** Eggs and larvae. */
    public Brood brood() {
        return brood;
    }

    /** Dead ants and the refuse chamber. */
    public Refuse refuse() {
        return refuse;
    }

    /** The termite mound (only species whose profile has a {@code mound} build one; see {@link Mound#numbers}). */
    public Mound mound() {
        return mound;
    }

    /** The fungus garden: comb, nodules (only species whose profile has a {@code garden}). */
    public FungusGarden garden() {
        return garden;
    }

    /**
     * What the nurses feed the queen and the young: fungus nodules for a species with a fungus garden, ant bread
     * otherwise.
     */
    public NurseFood nurseFood() {
        return garden.exists() ? garden : bakery;
    }

    /** The foraging holes and their tunnels (only species whose profile has {@code nest.foraging_holes}). */
    public ForagingHoles foragingHoles() {
        return foragingHoles;
    }

    /**
     * Whether it is time to forage (the profile's {@code food.forage_at}: termites at night and in the rain, harvester
     * ants at any time). Outside this time the foragers stay home, the foraging holes are plugged, and a species that
     * keeps to a schedule does no work outside the nest and the mound at all (it stays in, out of the sun).
     */
    public boolean isForagingTime(net.minecraft.world.level.Level level) {
        if (foragingTimeForTests != null) {
            return foragingTimeForTests;
        }
        return profile().food().foragesNow(level.isDay(), level.isRaining());
    }

    /** Whether this colony's species goes out at some times only (so it stays inside the rest of the time). */
    public boolean keepsASchedule() {
        return !profile().food().forageAt().containsAll(SpeciesProfile.FoodNumbers.ALWAYS);
    }

    /** Tests only: forces foraging time on or off (null for the real clock and weather). */
    public void setForagingTimeForTests(@Nullable Boolean on) {
        foragingTimeForTests = on;
    }

    public UUID getId() {
        return id;
    }

    public ResourceLocation getSpecies() {
        return species;
    }

    /** The numbers this colony runs on, from its species profile (a JSON file, see {@link SpeciesProfiles}). */
    public SpeciesProfile profile() {
        return SpeciesProfiles.get(species);
    }

    public UUID getQueenId() {
        return queenId;
    }

    public BlockPos getCenter() {
        return center;
    }

    /** The queen (of whatever species), if she is loaded and alive. */
    @Nullable
    public AbstractAntEntity getQueen(ServerLevel level) {
        return level.getEntity(queenId) instanceof AbstractAntEntity queen && queen.isAlive() ? queen : null;
    }

    /** The area (around the nest, down to the refuse chamber) where the colony's eggs, larvae and dead ants are looked for. */
    AABB nestArea() {
        int radius = profile().food().radius();
        return new AABB(center).inflate(radius, 0, radius).expandTowards(0, 8, 0).expandTowards(0, -12, 0);
    }

    /** Whether the colony is still being founded: nobody yet but the queen (and her king, for termites). */
    public boolean isFounding() {
        return getMemberCount() - getCount(TermiteKingEntity.CASTE) == 0;
    }

    public boolean isHungerOn() {
        return hungerOn;
    }

    public void setHungerOn(boolean on) {
        hungerOn = on;
    }

    /**
     * The role of this worker. Roles are shared out evenly: the nurses (how many depends on the headcount, see the
     * profile's {@code nurses_from_workers}), the rest half builders and half foragers (builders first). A worker keeps its role until it is one too many for
     * it, so roles only change when workers are born or die.
     */
    public ColonyRole roleOf(ServerLevel level, UUID ant) {
        roles.keySet().removeIf(id -> !id.equals(ant) && !(level.getEntity(id) instanceof AbstractAntEntity worker && worker.isAlive()));
        int[] others = new int[ColonyRole.values().length];
        int total = 1;
        for (Map.Entry<UUID, ColonyRole> entry : roles.entrySet()) {
            if (!entry.getKey().equals(ant)) {
                others[entry.getValue().ordinal()]++;
                total++;
            }
        }
        int nurses = profile().colony().nurses(total);
        int[] target = new int[ColonyRole.values().length];
        target[ColonyRole.NURSE.ordinal()] = nurses;
        target[ColonyRole.BUILDER.ordinal()] = (total - nurses + 1) / 2;
        target[ColonyRole.FORAGER.ordinal()] = (total - nurses) / 2;
        ColonyRole current = roles.get(ant);
        if (current == null || others[current.ordinal()] >= target[current.ordinal()]) {
            current = ColonyRole.BUILDER;
            for (ColonyRole role : new ColonyRole[] {ColonyRole.NURSE, ColonyRole.FORAGER, ColonyRole.BUILDER}) {
                if (others[role.ordinal()] < target[role.ordinal()]) {
                    current = role;
                    break;
                }
            }
            roles.put(ant, current);
        }
        return current;
    }

    public int getCount(String caste) {
        return casteCounts.getOrDefault(caste, 0);
    }

    public void setCount(String caste, int count) {
        casteCounts.put(caste, Math.max(0, count));
    }

    /** Total members across all castes (the queen is not counted). */
    public int getMemberCount() {
        return casteCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<String, Integer> getCasteCounts() {
        return casteCounts;
    }

    /** Writes the colony to the save. The parts write their own numbers into the same tag (the key names are kept, so old saves load). */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("species", species.toString());
        tag.putUUID("queen", queenId);
        tag.putLong("center", center.asLong());
        CompoundTag castes = new CompoundTag();
        casteCounts.forEach(castes::putInt);
        tag.put("castes", castes);
        food.save(tag);
        bakery.save(tag);
        queenCare.save(tag);
        nest.save(tag);
        mound.save(tag);
        return tag;
    }

    public static Colony load(CompoundTag tag) {
        Colony colony = new Colony(
                tag.getUUID("id"),
                ResourceLocation.parse(tag.getString("species")),
                tag.getUUID("queen"),
                BlockPos.of(tag.getLong("center")));
        CompoundTag castes = tag.getCompound("castes");
        for (String key : castes.getAllKeys()) {
            colony.casteCounts.put(key, castes.getInt(key));
        }
        colony.food.load(tag);
        colony.bakery.load(tag);
        colony.queenCare.load(tag);
        colony.nest.load(tag);
        colony.mound.load(tag);
        return colony;
    }
}
