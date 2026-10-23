package com.antcolonies.tree;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One kind of giant savanna tree (M7.5), read from {@code data/antcolonies/tree_kind/<name>.json}, like the species
 * profiles: its sizes, crown and blocks, so a new tree kind later is a new file plus its textures. The numbers are the
 * mature tree's (an ancient one is a little bigger, see {@link TreeShapes}); my picks from the tree viewer, to confirm.
 *
 * @param form   which shape maker draws it: {@code acacia} (a trunk forking into limbs under a crown) or {@code baobab}
 * @param salt   mixed into the seed so two kinds with the same seed differ
 * @param shapes the kind's two shapes, mixed by each tree's seed (the user's pick)
 * @param lifespan how many days a tree of this kind lives after it came up, from and to, each tree its own by its seed
 *                 (step 5, the user's decision: real ratios, one game day about one tree-year)
 */
public record TreeKind(String form, int salt, Trunk trunk, Crown crown, List<Shape> shapes, Blocks blocks, List<Integer> lifespan) {
    public static final Codec<TreeKind> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("form").forGetter(TreeKind::form),
            Codec.INT.fieldOf("salt").forGetter(TreeKind::salt),
            Trunk.CODEC.fieldOf("trunk").forGetter(TreeKind::trunk),
            Crown.CODEC.fieldOf("crown").forGetter(TreeKind::crown),
            Shape.CODEC.listOf().fieldOf("shapes").forGetter(TreeKind::shapes),
            Blocks.CODEC.fieldOf("blocks").forGetter(TreeKind::blocks),
            Codec.INT.listOf().optionalFieldOf("lifespan", List.of(150, 250)).forGetter(TreeKind::lifespan)).apply(i, TreeKind::new));

    /**
     * The trunk. For an acacia: the tree's height, its spread either way, the trunk's radius, and where it forks (a share
     * of the height, from to). For a baobab: the trunk's own height and radius (its branches and crown come on top).
     */
    public record Trunk(double height, double heightSpread, double radius, double radiusSpread, List<Double> fork) {
        public static final Codec<Trunk> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("height").forGetter(Trunk::height),
                Codec.DOUBLE.fieldOf("height_spread").forGetter(Trunk::heightSpread),
                Codec.DOUBLE.fieldOf("radius").forGetter(Trunk::radius),
                Codec.DOUBLE.fieldOf("radius_spread").forGetter(Trunk::radiusSpread),
                Codec.DOUBLE.listOf().optionalFieldOf("fork", List.of(0.2, 0.3)).forGetter(Trunk::fork)).apply(i, Trunk::new));
    }

    /**
     * The crown: its radius (acacias), how many limbs (from, to; a baobab's stubby branches), how many bends each limb makes, how much it domes (0 flat), how
     * thick the leaf layer is, the radius of each leaf pad, the share of leaf blocks filled (the leaves look C), and
     * whether pods hang under it.
     */
    public record Crown(double radius, List<Integer> limbs, int bends, double dome, double thickness, double padRadius,
                        double leafDensity, boolean pods) {
        public static final Codec<Crown> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("radius", 0.0).forGetter(Crown::radius),
                Codec.INT.listOf().fieldOf("limbs").forGetter(Crown::limbs),
                Codec.INT.optionalFieldOf("bends", 2).forGetter(Crown::bends),
                Codec.DOUBLE.optionalFieldOf("dome", 0.0).forGetter(Crown::dome),
                Codec.DOUBLE.optionalFieldOf("thickness", 4.5).forGetter(Crown::thickness),
                Codec.DOUBLE.optionalFieldOf("pad_radius", 6.5).forGetter(Crown::padRadius),
                Codec.DOUBLE.fieldOf("leaf_density").forGetter(Crown::leafDensity),
                Codec.BOOL.optionalFieldOf("pods", false).forGetter(Crown::pods)).apply(i, Crown::new));
    }

    /**
     * One of the kind's two shapes. Acacias: {@code tiers} (2 or 3 flat pads at different heights, the layered umbrella
     * thorn), and scales for the height, the crown's width and its dome (the spreading camel thorn). Baobabs: the trunk's
     * {@code profile}, {@code bottle} or {@code column}.
     */
    public record Shape(String name, boolean tiers, double heightScale, double crownScale, double domeScale, String profile) {
        public static final Codec<Shape> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Shape::name),
                Codec.BOOL.optionalFieldOf("tiers", false).forGetter(Shape::tiers),
                Codec.DOUBLE.optionalFieldOf("height_scale", 1.0).forGetter(Shape::heightScale),
                Codec.DOUBLE.optionalFieldOf("crown_scale", 1.0).forGetter(Shape::crownScale),
                Codec.DOUBLE.optionalFieldOf("dome_scale", 1.0).forGetter(Shape::domeScale),
                Codec.STRING.optionalFieldOf("profile", "").forGetter(Shape::profile)).apply(i, Shape::new));
    }

    /** The kind's own blocks (ids), for placing the tree (step 3). */
    public record Blocks(String log, String wood, String branch, String leaves, String seedling) {
        public static final Codec<Blocks> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("log").forGetter(Blocks::log),
                Codec.STRING.fieldOf("wood").forGetter(Blocks::wood),
                Codec.STRING.fieldOf("branch").forGetter(Blocks::branch),
                Codec.STRING.fieldOf("leaves").forGetter(Blocks::leaves),
                Codec.STRING.fieldOf("seedling").forGetter(Blocks::seedling)).apply(i, Blocks::new));
    }

    public boolean isBaobab() {
        return form.equals("baobab");
    }

    /** The umbrella thorn's own shape (the user's redo from photos, 2026-10-18, {@link UmbrellaShape}). */
    public boolean isUmbrella() {
        return form.equals("umbrella");
    }

    /** Which of the two shapes a tree with this seed has (mixed by seed, about half each). */
    public Shape shapeFor(long seed) {
        return shapes.get(shapes.size() > 1 && new TreeRandom(seed * 31 + 17 + salt).next() >= 0.5 ? 1 : 0);
    }

    /** Reads a kind from the mod's own files (the game tests and the shape checks; the game reads them as data later). */
    public static TreeKind load(String name) {
        String path = "/data/antcolonies/tree_kind/" + name + ".json";
        try (InputStream in = TreeKind.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("no tree kind file " + path);
            }
            return CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .getOrThrow(message -> new IllegalArgumentException(path + ": " + message));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
