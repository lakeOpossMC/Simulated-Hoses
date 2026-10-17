package edn.lakeopossmc.simulatedhoses.client;

import dev.simulated_team.simulated.index.SimClickInteractions;
import edn.lakeopossmc.simulatedhoses.HoseBlockEntities;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

// --- CLIENT SETUP --- //
// * Only ever touched on the client, keeps render classes off dedicated servers
public final class HoseClient {

    public static void init(final IEventBus modEventBus) {
        HosePartialModels.init();
        modEventBus.addListener(HoseClient::registerRenderers);
        // * Hold use with an empty hand to carry a connector
        modEventBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> SimClickInteractions.register(new HoseGrabClientHandler())));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> HoseCouplerClientHandler.tick());
    }

    private static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(HoseBlockEntities.HOSE_CONNECTOR.get(), HoseConnectorRenderer::new);
        event.registerBlockEntityRenderer(HoseBlockEntities.MAGNETIC_HOSE_CONNECTOR.get(), HoseConnectorRenderer::new);
        event.registerBlockEntityRenderer(HoseBlockEntities.HOSE_WINCH.get(), HoseWinchRenderer::new);
        event.registerBlockEntityRenderer(HoseBlockEntities.FLUID_QUICK_CONNECT.get(), FluidQuickConnectRenderer::new);
    }

    private HoseClient() {
    }
}
