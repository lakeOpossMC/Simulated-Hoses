package edn.lakeopossmc.simulatedhoses;

import edn.lakeopossmc.simulatedhoses.blocks.FluidQuickConnectBlock;
import edn.lakeopossmc.simulatedhoses.blocks.HoseConnectorBlock;
import edn.lakeopossmc.simulatedhoses.blocks.HoseWinchBlock;
import edn.lakeopossmc.simulatedhoses.blocks.MagneticHoseConnectorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

// --- REGISTERS ALL BLOCKS --- //
public final class HoseBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SimulatedHosesMod.MOD_ID);

    public static final DeferredBlock<HoseConnectorBlock> HOSE_CONNECTOR = BLOCKS.register(
            "hose_connector",
            () -> new HoseConnectorBlock(connectorProperties())
    );

    public static final DeferredBlock<MagneticHoseConnectorBlock> MAGNETIC_HOSE_CONNECTOR = BLOCKS.register(
            "magnetic_hose_connector",
            () -> new MagneticHoseConnectorBlock(connectorProperties())
    );

    public static final DeferredBlock<HoseWinchBlock> HOSE_WINCH = BLOCKS.register(
            "hose_winch",
            () -> new HoseWinchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.COPPER)
                    .strength(2.5F, 6.0F)
                    .noOcclusion())
    );

    public static final DeferredBlock<FluidQuickConnectBlock> FLUID_QUICK_CONNECT = BLOCKS.register(
            "fluid_quick_connect",
            () -> new FluidQuickConnectBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.COPPER)
                    .strength(2.0F, 6.0F)
                    .noOcclusion()
                    .forceSolidOn())
    );

    private static BlockBehaviour.Properties connectorProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_ORANGE)
                .sound(SoundType.COPPER)
                .strength(2.0F, 6.0F)
                .noOcclusion()
                .forceSolidOn();
    }

    private HoseBlocks() {
    }

    public static void register(final IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
