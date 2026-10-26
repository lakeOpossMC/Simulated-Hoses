package edn.lakeopossmc.simulatedhoses;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// --- REGISTERS ITEM DATA COMPONENTS --- //
public final class HoseDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, SimulatedHosesMod.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> HOSE_FIRST_CONNECTION =
            COMPONENTS.registerComponentType(
                    "hose_first_connection",
                    builder -> builder.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC)
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> SOAKER_FLUID =
            COMPONENTS.registerComponentType(
                    "soaker_fluid",
                    builder -> builder.persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC)
            );

    private HoseDataComponents() {
    }

    public static void register(final IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
