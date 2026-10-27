package edn.lakeopossmc.simulatedhoses;

import edn.lakeopossmc.simulatedhoses.backtank.FluidBacktankBlockEntity;
import edn.lakeopossmc.simulatedhoses.blocks.FluidQuickConnectBlockEntity;
import edn.lakeopossmc.simulatedhoses.blocks.HoseConnectorBlockEntity;
import edn.lakeopossmc.simulatedhoses.blocks.HoseWinchBlockEntity;
import edn.lakeopossmc.simulatedhoses.blocks.MagneticHoseConnectorBlockEntity;
import edn.lakeopossmc.simulatedhoses.blocks.PressureNozzleBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// --- REGISTERS ALL BLOCK ENTITY TYPES --- //
public final class HoseBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(
            Registries.BLOCK_ENTITY_TYPE,
            SimulatedHosesMod.MOD_ID
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HoseConnectorBlockEntity>> HOSE_CONNECTOR =
            BLOCK_ENTITY_TYPES.register(
                    "hose_connector",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new HoseConnectorBlockEntity(HoseBlockEntities.HOSE_CONNECTOR.get(), pos, state),
                            HoseBlocks.HOSE_CONNECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagneticHoseConnectorBlockEntity>> MAGNETIC_HOSE_CONNECTOR =
            BLOCK_ENTITY_TYPES.register(
                    "magnetic_hose_connector",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new MagneticHoseConnectorBlockEntity(HoseBlockEntities.MAGNETIC_HOSE_CONNECTOR.get(), pos, state),
                            HoseBlocks.MAGNETIC_HOSE_CONNECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HoseWinchBlockEntity>> HOSE_WINCH =
            BLOCK_ENTITY_TYPES.register(
                    "hose_winch",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new HoseWinchBlockEntity(HoseBlockEntities.HOSE_WINCH.get(), pos, state),
                            HoseBlocks.HOSE_WINCH.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidQuickConnectBlockEntity>> FLUID_QUICK_CONNECT =
            BLOCK_ENTITY_TYPES.register(
                    "fluid_quick_connect",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new FluidQuickConnectBlockEntity(HoseBlockEntities.FLUID_QUICK_CONNECT.get(), pos, state),
                            HoseBlocks.FLUID_QUICK_CONNECT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PressureNozzleBlockEntity>> PRESSURE_NOZZLE =
            BLOCK_ENTITY_TYPES.register(
                    "pressure_nozzle",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new PressureNozzleBlockEntity(HoseBlockEntities.PRESSURE_NOZZLE.get(), pos, state),
                            HoseBlocks.PRESSURE_NOZZLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidBacktankBlockEntity>> FLUID_BACKTANK =
            BLOCK_ENTITY_TYPES.register(
                    "fluid_backtank",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new FluidBacktankBlockEntity(HoseBlockEntities.FLUID_BACKTANK.get(), pos, state),
                            HoseBlocks.COPPER_FLUID_BACKTANK.get()).build(null));

    private HoseBlockEntities() {
    }

    public static void register(final IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
