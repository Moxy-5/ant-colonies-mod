package com.antcolonies.entity.body;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.loading.FileLoader;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

/**
 * Bones of its own for a model drawn on another animal's geo file (BUGS 383, 2026-11-25). GeckoLib keeps one set of
 * bones for each geo file, shared by every renderer that names it, and poses an animal again only when its time has
 * moved since it was last drawn: drawn again at the same moment, it trusts the bones to still hold its pose. A buffalo's
 * carcass drawn on the living buffalo's geo left those shared bones in the carcass pose, so in a still frame (a frozen
 * film, a paused game) a living buffalo drawn after it was drawn lying as the carcass. The borrower draws on a copy of
 * its own, baked from the same file and baked again after each resource reload; the animal keeps GeckoLib's.
 * <p>
 * One per borrowing model (it remembers which bones the model's clip player is set to), used from its
 * {@code getBakedModel}. Drawing only (the render thread); species-neutral.
 */
public final class OwnBones {
    private final Set<ResourceLocation> borrowed;
    /** Each borrowed geo: GeckoLib's shared model it was copied from, and the copy. */
    private final Map<ResourceLocation, BakedGeoModel[]> copies = new HashMap<>();
    /** The model the clip player was last set to. */
    @Nullable
    private BakedGeoModel drawn;

    /** For a model that draws on these other animals' geo files (and on its own for the rest). */
    public OwnBones(ResourceLocation... borrowed) {
        this.borrowed = Set.of(borrowed);
    }

    /**
     * The model to draw at {@code location} (a borrowed geo's own copy, GeckoLib's shared model for the rest), with the
     * clip player set to it; null when GeckoLib has no such model (GeckoLib's own lookup then says what is wrong).
     */
    @Nullable
    public BakedGeoModel model(ResourceLocation location, AnimationProcessor<?> player) {
        BakedGeoModel shared = GeckoLibCache.getBakedModels().get(location);
        if (shared == null) {
            return null;
        }
        BakedGeoModel model = shared;
        if (borrowed.contains(location)) {
            BakedGeoModel[] copy = copies.get(location);
            if (copy == null || copy[0] != shared) {        // first drawn, or the resources were reloaded
                var raw = FileLoader.loadModelFile(location, Minecraft.getInstance().getResourceManager());
                copy = new BakedGeoModel[] {shared,
                        BakedModelFactory.getForNamespace(location.getNamespace()).constructGeoModel(GeometryTree.fromModel(raw))};
                copies.put(location, copy);
            }
            model = copy[1];
        }
        if (model != drawn) {
            player.setActiveModel(model);
            drawn = model;
        }
        return model;
    }
}
