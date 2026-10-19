package edn.lakeopossmc.simulatedhoses.client;

import dev.simulated_team.simulated.index.SimClickInteractions;
import edn.lakeopossmc.simulatedhoses.HoseBlockEntities;
import edn.lakeopossmc.simulatedhoses.fluids.PlantFoodCoating;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

// --- CLIENT SETUP --- //
// * Only ever touched on the client, keeps render classes off dedicated servers
public final class HoseClient {

    @Nullable
    private static HoseGrabClientHandler grab;

    public static void init(final IEventBus modEventBus) {
        HosePartialModels.init();
        modEventBus.addListener(HoseClient::registerRenderers);
        modEventBus.addListener(PlantFoodClient::registerExtensions);
        modEventBus.addListener(PlantFoodCoatLayer::addLayers);
        modEventBus.addListener(PlantFoodVisionOverlay::register);
        modEventBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(
                (ResourceManagerReloadListener) manager -> {
                    PlantFoodOutlineTexture.invalidate();
                    PlantFoodVisionFilter.invalidate();
                    PlantFoodGlaze.invalidate();
                }));
        // * Hold use with an empty hand to carry a connector
        modEventBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            PlantFoodClient.setRenderLayers();
            grab = new HoseGrabClientHandler();
            SimClickInteractions.register(grab);
        }));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> HoseCouplerClientHandler.tick());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> PlantFoodCoating.clientTick());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> PlantFoodDrips.tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> PlantFoodCoating.clientReset());
        NeoForge.EVENT_BUS.addListener(HoseClient::onClickInput);
        NeoForge.EVENT_BUS.addListener((RenderHandEvent event) -> NozzleArmRenderer.render(event, grab != null && grab.isCarryingNozzle(),
                grab != null ? grab.getNozzleHeldTicks(event.getPartialTick()) : 0.0F));
    }

    // * No mining or punching while attack is working a carried nozzle
    private static void onClickInput(final InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && grab != null && grab.suppressesAttack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(HoseBlockEntities.HOSE_CONNECTOR.get(), HoseConnectorRenderer::new);
        event.registerBlockEntityRenderer(HoseBlockEntities.MAGNETIC_HOSE_CONNECTOR.get(), HoseConnectorRenderer::new);
        event.registerBlockEntityRenderer(HoseBlockEntities.HOSE_WINCH.get(), HoseWinchRenderer::new);
        event.registerBlockEntityRenderer(HoseBlockEntities.FLUID_QUICK_CONNECT.get(), FluidQuickConnectRenderer::new);
        event.registerBlockEntityRenderer(HoseBlockEntities.PRESSURE_NOZZLE.get(), PressureNozzleRenderer::new);
    }

    private HoseClient() {
    }
}
