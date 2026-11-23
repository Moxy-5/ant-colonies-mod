package com.antcolonies.savanna.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;

/**
 * One wisp of the red mist round a kill in the water ({@code animal/BloodMist}): it starts small and thick, billows out
 * slowly, turns as it drifts and thins away in 3 to 5 seconds (my picks); out of the water it is gone at once.
 */
public class BloodMistParticle extends TextureSheetParticle {
    private final float startSize, endSize, startAlpha, spin;

    BloodMistParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        pickSprite(sprites);
        lifetime = 60 + random.nextInt(40);
        gravity = 0.0F;
        friction = 0.9F;
        hasPhysics = true;
        float shade = 0.85F + random.nextFloat() * 0.15F;
        rCol = 0.62F * shade;           // the user's shade (2026-11-23)
        gCol = 0.03F;
        bCol = 0.04F;
        startSize = 0.1F + random.nextFloat() * 0.06F;
        endSize = 0.45F + random.nextFloat() * 0.3F;
        startAlpha = 0.7F + random.nextFloat() * 0.15F;
        quadSize = startSize;
        alpha = startAlpha;
        roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        spin = (random.nextFloat() - 0.5F) * 0.03F;
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }
        if (!level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER)) {
            remove();
            return;
        }
        float f = (float) age / lifetime;
        float out = 1.0F - (1.0F - f) * (1.0F - f);        // billows out fast, then slows
        quadSize = Mth.lerp(out, startSize, endSize);
        alpha = startAlpha * (1.0F - f) * (1.0F - f * 0.5F);
        oRoll = roll;
        roll += spin;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Makes the wisps the server sends. */
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new BloodMistParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
