package edn.lakeopossmc.simulatedhoses.backtank;

import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import edn.lakeopossmc.simulatedhoses.items.SuperSoakerItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// --- SUPER SOAKER STOWED ON A FLUID BACKTANK --- //
// * Soakers carried in the inventory but not in either hand hang on the backtank
public final class StowedSoakers {
    // * Server: what was last sent for each player. Client: what each player has stowed, by entity id
    private static final Map<UUID, List<ItemStack>> SENT = new HashMap<>();
    private static final Map<Integer, List<ItemStack>> STOWED = new HashMap<>();

    //#region // --- SERVER --- //
    // * Every soaker in the inventory except the one in the main hand
    private static List<ItemStack> stowedBy(final Player player) {
        if (!(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof FluidBacktankItem)) {
            return List.of();
        }
        final List<ItemStack> stowed = new ArrayList<>();
        final List<ItemStack> items = player.getInventory().items;
        for (int slot = 0; slot < items.size(); slot++) {
            if (slot != player.getInventory().selected && items.get(slot).getItem() instanceof SuperSoakerItem) {
                stowed.add(items.get(slot).copy());
            }
        }
        return stowed;
    }

    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof final ServerPlayer player)) {
            return;
        }
        final List<ItemStack> stowed = stowedBy(player);
        final List<ItemStack> sent = SENT.getOrDefault(player.getUUID(), List.of());
        if (!ItemStack.listMatches(stowed, sent)) {
            SENT.put(player.getUUID(), stowed);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new Packet(player.getId(), stowed));
        }
    }

    public static void onStartTracking(final PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof final Player target && event.getEntity() instanceof final ServerPlayer tracker) {
            PacketDistributor.sendToPlayer(tracker, new Packet(target.getId(), SENT.getOrDefault(target.getUUID(), List.of())));
        }
    }

    // * A new entity id after respawning or changing dimension
    public static void forget(final PlayerEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
    //#endregion

    //#region // --- CLIENT --- //
    public static List<ItemStack> stowedOn(final Player player) {
        return STOWED.getOrDefault(player.getId(), List.of());
    }

    public static void clientReset() {
        STOWED.clear();
    }
    //#endregion

    //#region // --- PACKET --- //
    public record Packet(int player, List<ItemStack> soakers) implements CustomPacketPayload {
        public static final Type<Packet> TYPE = new Type<>(SimulatedHosesMod.asResource("stowed_soaker"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Packet> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Packet::player,
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), Packet::soakers,
                Packet::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerPacket(final RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Packet.TYPE, Packet.CODEC, StowedSoakers::handle);
    }

    private static void handle(final Packet packet, final IPayloadContext context) {
        if (packet.soakers().isEmpty()) {
            STOWED.remove(packet.player());
        } else {
            STOWED.put(packet.player(), List.copyOf(packet.soakers()));
        }
    }
    //#endregion

    private StowedSoakers() {
    }
}
