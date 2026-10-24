package edn.lakeopossmc.simulatedhoses.fluids;

import edn.lakeopossmc.simulatedhoses.HoseEffects;
import edn.lakeopossmc.simulatedhoses.HoseFluids;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// --- PLAYERS AND MOBS COVERED IN PLANT FOOD --- //
public final class PlantFoodCoating {
    public static final int DURATION = 600;
    public static final int FADE_TICKS = 100;
    private static final int REFRESH_AFTER = 20;
    private static final int FOREVER = Integer.MAX_VALUE / 2;

    private static final Map<LivingEntity, long[]> SYNCED = new WeakHashMap<>();
    private static final Map<Integer, Integer> CLIENT = new HashMap<>();

    //#region // --- SERVER --- //
    // * Sprayed or standing in it: Gooped!
    public static void coat(final LivingEntity entity) {
        final MobEffectInstance current = entity.getEffect(HoseEffects.GOOPED);
        if (current != null && (current.isInfiniteDuration() || current.getAmplifier() > 0 || current.getDuration() > DURATION - REFRESH_AFTER)) {
            return;
        }
        entity.addEffect(new MobEffectInstance(HoseEffects.GOOPED, DURATION));
    }

    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof final ServerPlayer player) {
            tick(player);
        }
    }

    public static void onEntityTick(final EntityTickEvent.Post event) {
        if (event.getEntity() instanceof final LivingEntity living && !(living instanceof Player) && !living.level().isClientSide()) {
            tick(living);
        }
    }

    private static void tick(final LivingEntity entity) {
        if (entity.isAlive() && entity.isInFluidType(HoseFluids.PLANT_FOOD_TYPE.get())) {
            coat(entity);
        }
        MobEffectInstance effect = entity.getEffect(HoseEffects.GOOPED);
        if (effect != null && (!entity.isAlive() || entity.isInWaterRainOrBubble())) {
            entity.removeEffect(HoseEffects.GOOPED);
            effect = null;
        }
        final long now = entity.level().getGameTime();
        final long[] synced = SYNCED.get(entity);
        if (effect == null) {
            if (synced != null) {
                SYNCED.remove(entity);
                send(entity, 0);
            }
            return;
        }
        final int duration = effect.isInfiniteDuration() ? FOREVER : effect.getDuration();
        final long expected = synced == null ? -1 : synced[0] - (now - synced[1]);
        if (synced == null || Math.abs(expected - duration) > REFRESH_AFTER) {
            SYNCED.put(entity, new long[]{duration, now});
            send(entity, duration);
        }
    }

    public static void onStartTracking(final PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof final LivingEntity target && event.getEntity() instanceof final ServerPlayer viewer) {
            final MobEffectInstance effect = target.getEffect(HoseEffects.GOOPED);
            if (effect != null) {
                PacketDistributor.sendToPlayer(viewer, new Packet(target.getId(), effect.isInfiniteDuration() ? FOREVER : effect.getDuration()));
            }
        }
    }

    private static void send(final LivingEntity entity, final int ticks) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new Packet(entity.getId(), ticks));
    }
    //#endregion

    //#region // --- BOTH SIDES --- //
    public static boolean isCoated(final Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        return entity.level().isClientSide() ? CLIENT.containsKey(entity.getId())
                : ((LivingEntity) entity).hasEffect(HoseEffects.GOOPED);
    }

    public static float strength(final Entity entity) {
        final Integer left = CLIENT.get(entity.getId());
        return left == null ? 0.0F : Math.min(1.0F, left / (float) FADE_TICKS);
    }
    //#endregion

    //#region // --- CLIENT --- //
    public static void clientTick() {
        CLIENT.replaceAll((id, left) -> left - 1);
        CLIENT.values().removeIf(left -> left <= 0);
    }

    public static Set<Integer> clientCoated() {
        return Set.copyOf(CLIENT.keySet());
    }

    public static void clientReset() {
        CLIENT.clear();
    }
    //#endregion

    //#region // --- PACKET --- //
    public record Packet(int entityId, int ticks) implements CustomPacketPayload {
        public static final Type<Packet> TYPE = new Type<>(SimulatedHosesMod.asResource("plant_food_coating"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Packet> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Packet::entityId,
                ByteBufCodecs.VAR_INT, Packet::ticks,
                Packet::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerPacket(final RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Packet.TYPE, Packet.CODEC, PlantFoodCoating::handle);
    }

    private static void handle(final Packet packet, final IPayloadContext context) {
        if (packet.ticks() <= 0) {
            CLIENT.remove(packet.entityId());
        } else {
            CLIENT.put(packet.entityId(), packet.ticks());
        }
    }
    //#endregion

    private PlantFoodCoating() {
    }
}
