package edn.lakeopossmc.simulatedhoses.hose;

import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;

// --- GRAB PACKET --- //
// * Client to server: Sent again every few ticks while holding
public record HoseGrabPacket(BlockPos pos, float distance, boolean release, boolean rotated, Quaternionf orientation, boolean trigger) implements CustomPacketPayload {

    public static final Type<HoseGrabPacket> TYPE = new Type<>(SimulatedHosesMod.asResource("grab_connector"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HoseGrabPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HoseGrabPacket::pos,
            ByteBufCodecs.FLOAT, HoseGrabPacket::distance,
            ByteBufCodecs.BOOL, HoseGrabPacket::release,
            ByteBufCodecs.BOOL, HoseGrabPacket::rotated,
            ByteBufCodecs.QUATERNIONF, HoseGrabPacket::orientation,
            ByteBufCodecs.BOOL, HoseGrabPacket::trigger,
            HoseGrabPacket::new);

    public static void register(final RegisterPayloadHandlersEvent event) {
        event.registrar("2")
                .playToServer(TYPE, CODEC, HoseGrabPacket::handle);
    }

    private static void handle(final HoseGrabPacket packet, final IPayloadContext context) {
        if (!(context.player() instanceof final ServerPlayer player)) {
            return;
        }
        if (packet.release()) {
            HoseGrabHandler.release(player);
        } else {
            HoseGrabHandler.hold(player, packet.pos(), packet.distance(), packet.rotated() ? packet.orientation() : null, packet.trigger());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}