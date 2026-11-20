package com.antcolonies.savanna.client;

import com.antcolonies.savanna.animal.FlyCloudEntity;
import com.antcolonies.savanna.registry.SavannaSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * A fly cloud's buzz (fly clouds step 2, 2026-11-20): the looping buzz ({@code tools/fly_buzz.py}) following the cloud,
 * heard within a few blocks (its sounds.json reach), louder the more flies it holds; it stops when the cloud is gone.
 */
final class FlyBuzzSound extends AbstractTickableSoundInstance {
    private final FlyCloudEntity cloud;

    private FlyBuzzSound(FlyCloudEntity cloud) {
        super(SavannaSounds.FLY_BUZZ.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.cloud = cloud;
        looping = true;
        delay = 0;
        follow();
    }

    /** Starts the buzz for a cloud the player's computer has just got. */
    static void start(FlyCloudEntity cloud) {
        Minecraft.getInstance().getSoundManager().queueTickingSound(new FlyBuzzSound(cloud));
    }

    private void follow() {
        x = cloud.getX();
        y = cloud.getY();
        z = cloud.getZ();
        volume = cloud.flies() <= 0 ? 0.0F : 0.25F + 0.05F * cloud.flies();
        pitch = 1.0F;
    }

    @Override
    public void tick() {
        if (cloud.isRemoved()) {
            stop();
            return;
        }
        follow();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
