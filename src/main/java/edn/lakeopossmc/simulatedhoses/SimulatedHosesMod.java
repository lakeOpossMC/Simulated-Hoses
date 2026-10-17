package edn.lakeopossmc.simulatedhoses;

import com.mojang.logging.LogUtils;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import edn.lakeopossmc.simulatedhoses.blocks.HoseConnectorBlock;
import edn.lakeopossmc.simulatedhoses.blocks.HoseWinchBlock;
import edn.lakeopossmc.simulatedhoses.client.HoseClient;
import edn.lakeopossmc.simulatedhoses.hose.HoseGrabHandler;
import edn.lakeopossmc.simulatedhoses.hose.HoseGrabPacket;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

// --- MOD ENTRY POINT --- //
@Mod(SimulatedHosesMod.MOD_ID)
public class SimulatedHosesMod {
    public static final String MOD_ID = "simulatedhoses";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final double WINCH_STRESS_IMPACT = 4.0;

    //#region // --- CONSTRUCTOR SETUP --- //
    public SimulatedHosesMod(final IEventBus modEventBus, final ModContainer modContainer, final Dist dist) {
        HoseBlocks.register(modEventBus);
        HoseBlockEntities.register(modEventBus);
        HoseItems.register(modEventBus);
        HoseDataComponents.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(HoseCommonEvents::registerCapabilities);
        modEventBus.addListener(HoseGrabPacket::register);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> HoseGrabHandler.serverTick());

        NeoForge.EVENT_BUS.addListener(HoseCommonEvents::onLevelTick);
        SableEventPlatform.INSTANCE.onPhysicsTick(HoseCommonEvents::onPhysicsTick);

        if (dist.isClient()) {
            HoseClient.init(modEventBus);
        }
    }
    //#endregion

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            HoseConnectorBlock.registerAttachedCheck();
            HoseWinchBlock.registerAttachedCheck();
            BlockStressValues.IMPACTS.register(HoseBlocks.HOSE_WINCH.get(), () -> WINCH_STRESS_IMPACT);
            TooltipModifier.REGISTRY.register(HoseItems.HOSE_WINCH.get(), TooltipModifier.mapNull(KineticStats.create(HoseItems.HOSE_WINCH.get())));
            HoseSimulatedTab.register();
        });
    }

    public static ResourceLocation asResource(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
