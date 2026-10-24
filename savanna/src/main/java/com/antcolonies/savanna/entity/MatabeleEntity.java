package com.antcolonies.savanna.entity;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.species.SpeciesProfiles;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.Level;

/**
 * A Matabele ant (Megaponera analis, M8 step 1), a major or a minor: the model, clips and climbing made in step 1, put
 * into the game by the user 2026-10-23 with a spawn egg each, before the rest of M8. For now it only walks about,
 * climbs and looks round, with no colony: its nest, the raids, the fights and its own species file come with M8's
 * steps ({@code docs/plans/m8-matabele.md}). Real Matabele workers come in a continuous spread of sizes, the majors
 * about twice the minors' length; here the two ends of the spread, each its own model.
 */
public class MatabeleEntity extends AbstractAntEntity {
    /** Its species id; it has no species file yet (see {@link #profile}). */
    public static final ResourceLocation SPECIES = SavannaSpecies.MATABELE;

    public MatabeleEntity(EntityType<? extends MatabeleEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public ResourceLocation species() {
        return SPECIES;
    }

    /**
     * Until M8 gives the Matabele its own species file, a loose ant reads the savanna's termite numbers for the little it
     * asks of a profile without a colony (its personality ranges, all of them open; no hot ground).
     */
    @Override
    public SpeciesProfile profile() {
        return SpeciesProfiles.get(SavannaSpecies.MACROTERMES);
    }

    /** No body of its own kind yet (M8 step 5 has the dead carried home): it goes as any mob does. */
    @Override
    protected boolean leavesBody() {
        return false;
    }

    /**
     * A major: big and strong (health and bite my picks, to be set with the fights, M8 step 2). Majors and minors march
     * at one speed, about 3 blocks a second (the user's pick, 2026-10-23; attribute 0.273), the speed their walks are fitted to ({@code tools/anim_lab.py WALK_SPEED}).
     */
    public static AttributeSupplier.Builder createMajorAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.273)
                .add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    /** A minor: half the length, lighter. */
    public static AttributeSupplier.Builder createMinorAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.273)
                .add(Attributes.ATTACK_DAMAGE, 1.5);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new AntWanderGoal(this, 1.0, 120, 8, () -> true));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }
}
