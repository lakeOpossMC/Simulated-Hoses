package edn.lakeopossmc.simulatedhoses.client;

import edn.lakeopossmc.simulatedhoses.backtank.BacktankFeed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// --- CTRL + SCROLL TO SWAP THE FEEDING TANK --- //
public final class BacktankFeedClient {

    public static void onScroll(final InputEvent.MouseScrollingEvent event) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || event.getScrollDeltaY() == 0.0 || !Screen.hasControlDown()
                || !BacktankFeed.canSwap(mc.player.getItemBySlot(EquipmentSlot.CHEST))) {
            return;
        }
        event.setCanceled(true);
        PacketDistributor.sendToServer(new BacktankFeed.SwapPacket());
    }

    private BacktankFeedClient() {
    }
}
