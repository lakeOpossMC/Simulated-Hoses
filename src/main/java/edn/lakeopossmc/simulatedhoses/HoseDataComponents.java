package edn.lakeopossmc.simulatedhoses;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.DyeColor;
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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> BACKTANK_FLUID =
            COMPONENTS.registerComponentType(
                    "backtank_fluid",
                    builder -> builder.persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC)
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> BACKTANK_SECOND_FLUID =
            COMPONENTS.registerComponentType(
                    "backtank_second_fluid",
                    builder -> builder.persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC)
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BACKTANK_FEED =
            COMPONENTS.registerComponentType(
                    "backtank_feed",
                    builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT)
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> SOAKER_FLUID =
            COMPONENTS.registerComponentType(
                    "soaker_fluid",
                    builder -> builder.persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC)
            );

    // * The colour a Super Soaker is dyed, with Bits 'n' Dyes
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DyeColor>> SOAKER_COLOR =
            COMPONENTS.registerComponentType(
                    "soaker_color",
                    builder -> builder.persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC)
            );

    private HoseDataComponents() {
    }

    public static void register(final IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
