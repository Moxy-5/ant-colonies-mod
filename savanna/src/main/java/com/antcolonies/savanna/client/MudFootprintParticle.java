package com.antcolonies.savanna.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One muddy footprint ({@link MudFootprints}): a square print of Steve's square boot (4 by 4 pixels, a quarter block, the
 * user's ask) lying flat on the ground, turned the way the foot faced, mirrored for the left foot, in the coat's soil colour, fading away over its last {@link #FADE} ticks (my picks: 45 s in all, the last 15 fading).
 */
public class MudFootprintParticle extends TextureSheetParticle {
    static final int LIFE = 900, FADE = 300;
    /** Half the print's width and length (blocks): a Minecraft boot's sole is 4 by 4 pixels. */
    static final float HALF_WIDE = 0.125F, HALF_LONG = 0.125F;

    /** The sprites, kept from the provider so {@link MudFootprints} can make prints itself. */
    static SpriteSet sprites;

    private final Quaternionf turn;
    private final boolean left;
    private final float fullAlpha;

    MudFootprintParticle(ClientLevel level, double x, double y, double z, float yawDegrees, boolean left, int rgb, float alpha) {
        super(level, x, y, z);
        xd = yd = zd = 0.0;
        gravity = 0.0F;
        hasPhysics = false;
        lifetime = LIFE;
        quadSize = HALF_LONG;           // only for the box the camera checks it against
        if (sprites != null) {
            pickSprite(sprites);
        }
        this.left = left;
        rCol = (rgb >> 16 & 0xFF) / 255.0F;
        gCol = (rgb >> 8 & 0xFF) / 255.0F;
        bCol = (rgb & 0xFF) / 255.0F;
        fullAlpha = alpha;
        this.alpha = alpha;
        // the quad's own up (the print's toe) turned to point the way the foot faced, the quad laid flat facing the sky
        turn = new Quaternionf().rotateY(Mth.PI - yawDegrees * Mth.DEG_TO_RAD).rotateX(-Mth.HALF_PI);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        int left = lifetime - age;
        alpha = left < FADE ? fullAlpha * left / FADE : fullAlpha;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 c = camera.getPosition();
        float px = (float) (x - c.x()), py = (float) (y - c.y()), pz = (float) (z - c.z());
        float u0 = left ? getU1() : getU0(), u1 = left ? getU0() : getU1();
        float v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTicks);
        vertex(buffer, px, py, pz, 1, -1, u1, v1, light);
        vertex(buffer, px, py, pz, 1, 1, u1, v0, light);
        vertex(buffer, px, py, pz, -1, 1, u0, v0, light);
        vertex(buffer, px, py, pz, -1, -1, u0, v1, light);
    }

    private void vertex(VertexConsumer buffer, float px, float py, float pz, float sx, float sy, float u, float v, int light) {
        Vector3f p = new Vector3f(sx * HALF_WIDE, sy * HALF_LONG, 0.0F).rotate(turn).add(px, py, pz);
        buffer.addVertex(p.x(), p.y(), p.z()).setUv(u, v).setColor(rCol, gCol, bCol, alpha).setLight(light);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Keeps the sprites; a print made by a command is a plain right foot facing south. */
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        public Provider {
            MudFootprintParticle.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new MudFootprintParticle(level, x, y, z, 0.0F, false, 0x5A4630, 0.8F);
        }
    }
}
