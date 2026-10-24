package edn.lakeopossmc.simulatedhoses.hose;

import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// --- WHO IS CARRYING WHAT --- //
public final class HoseCarryPose {
    // * Server: what was last sent. Client: who is carrying
    private static Map<UUID, Boolean> sent = Map.of();
    private static final Map<UUID, Boolean> CARRYING = new HashMap<>();

    //#region // --- SERVER --- //
    public static void update(final Map<UUID, Boolean> carrying) {
        if (!carrying.equals(sent)) {
            sent = Map.copyOf(carrying);
            PacketDistributor.sendToAllPlayers(new Packet(sent));
        }
    }

    public static void onLogin(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new Packet(sent));
        }
    }
    //#endregion

    //#region // --- CLIENT --- //
    // * Who is carrying, and whether it's a nozzle
    public static Boolean carryingNozzle(final UUID player) {
        return CARRYING.get(player);
    }

    public static void clientReset() {
        CARRYING.clear();
    }
    //#endregion

    //#region // --- PACKET --- //
    public record Packet(Map<UUID, Boolean> carrying) implements CustomPacketPayload {
        public static final Type<Packet> TYPE = new Type<>(SimulatedHosesMod.asResource("carry_pose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Packet> CODEC = StreamCodec.composite(
                ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC, ByteBufCodecs.BOOL), Packet::carrying,
                Packet::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerPacket(final RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Packet.TYPE, Packet.CODEC, HoseCarryPose::handle);
    }

    private static void handle(final Packet packet, final IPayloadContext context) {
        CARRYING.clear();
        CARRYING.putAll(packet.carrying());
    }
    //#endregion

    private HoseCarryPose() {
    }
}
