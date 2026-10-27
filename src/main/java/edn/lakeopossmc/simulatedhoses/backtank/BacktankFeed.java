package edn.lakeopossmc.simulatedhoses.backtank;

import edn.lakeopossmc.simulatedhoses.HoseDataComponents;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// --- WHICH TANK FEEDS THE SOAKERS --- //
// * Holding Ctrl and scrolling while wearing a backtank with two tanks switches which one Super Soakers pull from
public final class BacktankFeed {

    public record SwapPacket() implements CustomPacketPayload {
        public static final Type<SwapPacket> TYPE = new Type<>(SimulatedHosesMod.asResource("swap_backtank_feed"));
        public static final StreamCodec<ByteBuf, SwapPacket> CODEC = StreamCodec.unit(new SwapPacket());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerPacket(final RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(SwapPacket.TYPE, SwapPacket.CODEC, BacktankFeed::handle);
    }

    // * Whether this backtank has more than one tank to choose between
    public static boolean canSwap(final ItemStack backtank) {
        return FluidBacktankItem.tankCount(backtank) > 1;
    }

    private static void handle(final SwapPacket packet, final IPayloadContext context) {
        if (!(context.player() instanceof final ServerPlayer player)) {
            return;
        }
        final ItemStack backtank = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!canSwap(backtank)) {
            return;
        }
        final int mode = FluidBacktankItem.pullMode(backtank);
        final int next = (mode + 1) % FluidBacktankItem.PULL_MODES;
        if (next == FluidBacktankItem.PULL_BOTH) {
            backtank.remove(HoseDataComponents.BACKTANK_FEED);
        } else {
            backtank.set(HoseDataComponents.BACKTANK_FEED, next);
        }
        player.displayClientMessage(FluidBacktankItem.pullingFrom(next), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COPPER_TRAPDOOR_CLOSE, SoundSource.PLAYERS,
                0.6F, 1.1F + 0.1F * next);
    }

    private BacktankFeed() {
    }
}
