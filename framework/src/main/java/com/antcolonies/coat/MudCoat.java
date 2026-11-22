package com.antcolonies.coat;

import com.antcolonies.ColonyFrameworkMod;
import com.mojang.serialization.Codec;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Mud on a body (savanna mud step 4, 2026-11-21, {@code docs/plans/savanna-mud.md}; Neon's one-byte coat): any creature
 * can wear a coat of mud, kept in one byte on it, saved with it and sent to the players who see it:
 * <ul>
 * <li><b>coverage</b> 0 to 7: 0 clean, 1 and 2 feet and belly, 3 to 5 the flanks, 6 and 7 the whole body;</li>
 * <li><b>dryness</b> 0 to 7: dark and glossy wet, then matte, then pale and cracked, flaking off;</li>
 * <li><b>tone</b> 0 to 3: which soil it came from (the species mod says what colour that is).</li>
 * </ul>
 * Species-neutral: a species' mud puts the coat on ({@link #pickUp}); the coat dries, flakes and washes off here, step by
 * step, never all at once: in water a step every {@link #WADE_WASH} ticks (every {@link #SWIM_WASH} under it), in rain
 * the mud softens and a step every {@link #RAIN_WASH}; in the dry it gets a step drier every {@link #DRY_STEP} and, once
 * dry, flakes off a step every {@link #FLAKE_STEP} (my picks: a full coat lasts about 7 minutes in the dry, a few seconds
 * in water). A clean creature costs nothing: the rules run only on a creature wearing mud.
 */
public final class MudCoat {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
            ColonyFrameworkMod.MODID);
    public static final Supplier<AttachmentType<Byte>> COAT = ATTACHMENTS.register("mud_coat",
            () -> AttachmentType.builder(() -> (byte) 0).serialize(Codec.BYTE).build());

    static final int WADE_WASH = 28, SWIM_WASH = 15, RAIN_WASH = 100, RAIN_SOFTEN = 20, DRY_STEP = 400, FLAKE_STEP = 800;
    /** From this dryness on the coat flakes off. */
    static final int FLAKES_FROM = 6;
    /** How many mud bits fly off at each step it loses (wash or flake). */
    static final int SHED_BITS = 6;

    /**
     * A species shows a lost step of its mud (bits of mud flying off, a brown cloud in the water): the creature and the
     * coat's tone. Nothing by default.
     */
    public static BiConsumer<Entity, Integer> onShed = (entity, tone) -> {
    };

    private MudCoat() {
    }

    public static int pack(int coverage, int dryness, int tone) {
        return (coverage & 7) << 5 | (dryness & 7) << 2 | tone & 3;
    }

    public static int packed(Entity entity) {
        return entity.hasData(COAT) ? entity.getData(COAT) & 0xFF : 0;
    }

    public static int coverage(Entity entity) {
        return packed(entity) >> 5 & 7;
    }

    public static int dryness(Entity entity) {
        return packed(entity) >> 2 & 7;
    }

    public static int tone(Entity entity) {
        return packed(entity) & 3;
    }

    /** Sets a coat (0 coverage takes it off), and tells the players who see the creature if it changed. */
    public static void set(Entity entity, int coverage, int dryness, int tone) {
        int now = coverage <= 0 ? 0 : pack(Math.min(7, coverage), Math.max(0, Math.min(7, dryness)), tone);
        if (now == packed(entity)) {
            return;
        }
        if (now == 0) {
            entity.removeData(COAT);
        } else {
            entity.setData(COAT, (byte) now);
        }
        if (!entity.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new Sync(entity.getId(), (byte) now));
        }
    }

    /**
     * A creature in mud of this tone gets muddier, a step at a time, up to {@code most} (how much of it the mud reaches),
     * and its mud is made wet again. Called by a species' mud every so often while the creature stands in it.
     */
    public static void pickUp(Entity entity, int most, int tone) {
        int coverage = coverage(entity);
        int next = coverage < most ? coverage + 1 : coverage;
        // fresh mud over old mud of another soil takes the new soil's colour once it covers more than the old
        int keepTone = coverage > 0 && next == coverage ? tone(entity) : tone;
        set(entity, next, 0, keepTone);
    }

    /** The coat's rules, every tick for a creature wearing mud (server only). */
    public static void tick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide || !entity.hasData(COAT)) {
            return;
        }
        int coverage = coverage(entity), dryness = dryness(entity), tone = tone(entity);
        long t = entity.tickCount + (long) entity.getId();
        if (entity.isInWater()) {
            int every = entity.isUnderWater() ? SWIM_WASH : WADE_WASH;
            if (t % every == 0) {
                shed(entity, coverage - 1, 0, tone);
            } else if (dryness > 0) {
                set(entity, coverage, 0, tone);
            }
        } else if (entity.isInWaterOrRain()) {
            if (t % RAIN_WASH == 0) {
                shed(entity, coverage - 1, Math.max(0, dryness - 1), tone);
            } else if (t % RAIN_SOFTEN == 0 && dryness > 0) {
                set(entity, coverage, dryness - 1, tone);
            }
        } else if (dryness >= FLAKES_FROM && t % FLAKE_STEP == 0) {
            shed(entity, coverage - 1, Math.min(7, dryness + 1), tone);
        } else if (dryness < 7 && t % DRY_STEP == 0) {
            set(entity, coverage, dryness + 1, tone);
        }
    }

    private static void shed(Entity entity, int coverage, int dryness, int tone) {
        set(entity, coverage, dryness, tone);
        onShed.accept(entity, tone);
    }

    /** A player starting to see a muddy creature is told its coat. */
    public static void startTracking(PlayerEvent.StartTracking event) {
        int packed = packed(event.getTarget());
        if (packed != 0 && event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new Sync(event.getTarget().getId(), (byte) packed));
        }
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Sync.TYPE, Sync.CODEC, Sync::handle);
    }

    /** A creature's coat, sent to the players who see it. */
    public record Sync(int entity, byte coat) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "mud_coat"));
        public static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sync::entity, ByteBufCodecs.BYTE, Sync::coat, Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        void handle(IPayloadContext context) {
            context.enqueueWork(() -> Client.apply(entity, coat));
        }
    }

    /** The player's own computer's half (kept apart so a server never loads it). */
    static final class Client {
        static void apply(int id, byte coat) {
            var level = Minecraft.getInstance().level;
            Entity entity = level == null ? null : level.getEntity(id);
            if (entity == null) {
                return;
            }
            if (coat == 0) {
                entity.removeData(COAT);
            } else {
                entity.setData(COAT, coat);
            }
        }
    }
}
