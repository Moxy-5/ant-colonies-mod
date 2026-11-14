package com.antcolonies.savanna.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.DoumBlockEntity;
import com.antcolonies.savanna.block.DoumLook;
import com.antcolonies.savanna.block.DoumPiece;
import com.antcolonies.savanna.block.DoumPieceBlock;
import com.antcolonies.savanna.tree.PalmPieces;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

/**
 * How a doum palm's sculpted piece is drawn (floodplain trees step 5): its block's recipe ({@link DoumLook}) replayed into
 * the very boxes the model viewer draws, each box's six faces made into the block's quads with the viewer's colours. The
 * quads go into the chunk's picture like any block's, so a crown costs nothing per frame once drawn; a chunk is drawn
 * again only when a block in it changes. The same few recipes are kept so neighbouring changes do not work them out
 * again. Flat light (no ambient occlusion), as the viewer has; the game's own side shading is the viewer's exactly.
 */
public final class DoumModel implements IDynamicBakedModel {
    /** The model file's loader name: {@code "loader": "antcolonies:doum_sculpted"}. */
    public static final ResourceLocation LOADER = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "doum_sculpted");
    /** The viewer's face order, as three.js builds a box: +x, -x, +y, -y, +z, -z. */
    private static final Direction[] FACES = {Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.NORTH};
    /** Each face's outward normal, the way across it and the way up it, as seen from outside (box corners at plus or minus 1). */
    private static final int[][] NORMAL = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] RIGHT = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {1, 0, 0}, {1, 0, 0}, {-1, 0, 0}};
    private static final int[][] UP = {{0, 1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {0, 1, 0}, {0, 1, 0}};
    /** The corners in Minecraft's order (counter-clockwise from outside): top left, bottom left, bottom right, top right. */
    private static final int[][] CORNER = {{-1, 1}, {-1, -1}, {1, -1}, {1, 1}};
    private static final int KEEP = 24;

    private record Key(DoumLook look, DoumPiece.Piece piece, boolean green) {
    }

    private final TextureAtlasSprite plain, cap, capDead, particle;
    private final Map<Key, List<BakedQuad>> made = new LinkedHashMap<>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, List<BakedQuad>> eldest) {
            return size() > KEEP;
        }
    };

    private DoumModel(TextureAtlasSprite plain, TextureAtlasSprite cap, TextureAtlasSprite capDead, TextureAtlasSprite particle) {
        this.plain = plain;
        this.cap = cap;
        this.capDead = capDead;
        this.particle = particle;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                    @Nullable RenderType renderType) {
        if (side != null || state == null || !(state.getBlock() instanceof DoumPiece piece)) {
            return List.of();
        }
        DoumLook look = data.get(DoumBlockEntity.LOOK);
        if (look == null) {
            look = DoumLook.byPlace(BlockPos.ZERO);
        }
        boolean green = piece.piece() == DoumPiece.Piece.BUNCH && state.hasProperty(DoumPieceBlock.RIPE) && !state.getValue(DoumPieceBlock.RIPE);
        Key key = new Key(look, piece.piece(), green);
        List<BakedQuad> quads;
        synchronized (made) {
            quads = made.get(key);
        }
        if (quads == null) {
            // Worked out outside the lock: the chunk builders draw on several threads at once.
            quads = quads(look.bits(piece.piece(), green), look.dead());
            synchronized (made) {
                made.put(key, quads);
            }
        }
        return quads;
    }

    /** Every box's six faces as quads. */
    private List<BakedQuad> quads(List<PalmPieces.Bit> bits, boolean dead) {
        List<BakedQuad> out = new ArrayList<>(bits.size() * 6);
        for (PalmPieces.Bit b : bits) {
            boolean capped = b.skin() == PalmPieces.Skin.CAP;
            TextureAtlasSprite sprite = capped ? (dead ? capDead : cap) : plain;
            // The cap's tint is baked into its texture (tools/floodplain_tree_art.py); the rest are plain white, coloured here.
            int r = capped ? 255 : channel(b.r()), g = capped ? 255 : channel(b.g()), bl = capped ? 255 : channel(b.b());
            double c = Math.cos(b.rot()), s = Math.sin(b.rot());
            double[] half = {b.w() / 32.0, b.h() / 32.0, b.d() / 32.0};
            int[] size = {b.w(), b.h(), b.d()};
            for (int f = 0; f < 6; f++) {
                // The face's width and height in pixels, as the viewer counts them for its texture: [d,h] [d,h] [w,d] [w,d] [w,h] [w,h].
                int across = f < 2 ? size[2] : size[0], upward = f < 2 ? size[1] : f < 4 ? size[2] : size[1];
                float u0 = sprite.getU0(), u1 = u0 + (sprite.getU1() - u0) * Math.min(1F, across / 16F);
                float vBottom = sprite.getV1(), vTop = vBottom - (sprite.getV1() - sprite.getV0()) * Math.min(1F, upward / 16F);
                QuadBakingVertexConsumer quad = new QuadBakingVertexConsumer();
                quad.setSprite(sprite);
                quad.setDirection(FACES[f]);
                quad.setTintIndex(-1);
                quad.setShade(true);
                quad.setHasAmbientOcclusion(false);
                double nx = c * NORMAL[f][0] + s * NORMAL[f][2], nz = -s * NORMAL[f][0] + c * NORMAL[f][2];
                for (int[] corner : CORNER) {
                    double lx = (NORMAL[f][0] + corner[0] * RIGHT[f][0] + corner[1] * UP[f][0]) * half[0];
                    double ly = (NORMAL[f][1] + corner[0] * RIGHT[f][1] + corner[1] * UP[f][1]) * half[1];
                    double lz = (NORMAL[f][2] + corner[0] * RIGHT[f][2] + corner[1] * UP[f][2]) * half[2];
                    quad.addVertex((float) (b.x() + c * lx + s * lz), (float) (b.y() + ly), (float) (b.z() - s * lx + c * lz));
                    quad.setColor(r, g, bl, 255);
                    quad.setUv(corner[0] < 0 ? u0 : u1, corner[1] < 0 ? vBottom : vTop);
                    quad.setNormal((float) nx, NORMAL[f][1], (float) nz);
                }
                out.add(quad.bakeQuad());
            }
        }
        return List.copyOf(out);
    }

    /** A viewer colour (linear light) as the block's vertex colour (sRGB), clamped. */
    private static int channel(double linear) {
        return (int) Math.round(Math.max(0, Math.min(1, PalmPieces.srgb(linear))) * 255);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.solid());
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particle;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    public ItemTransforms getTransforms() {
        return ItemTransforms.NO_TRANSFORMS;
    }

    /** The model file's side: names its textures (plain, cap, cap_dead, particle). */
    public static final class Geometry implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                               ModelState state, ItemOverrides overrides) {
            return new DoumModel(sprites.apply(context.getMaterial("plain")), sprites.apply(context.getMaterial("cap")),
                    sprites.apply(context.getMaterial("cap_dead")), sprites.apply(context.getMaterial("particle")));
        }
    }

    /** Reads {@code "loader": "antcolonies:doum_sculpted"} model files. */
    public static final class Loader implements IGeometryLoader<Geometry> {
        public static final Loader INSTANCE = new Loader();

        @Override
        public Geometry read(JsonObject json, JsonDeserializationContext context) {
            return new Geometry();
        }
    }
}
