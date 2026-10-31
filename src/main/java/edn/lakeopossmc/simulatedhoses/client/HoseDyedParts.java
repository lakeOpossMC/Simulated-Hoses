package edn.lakeopossmc.simulatedhoses.client;

import edn.lakeopossmc.simulatedhoses.HoseDataComponents;
import edn.lakeopossmc.simulatedhoses.HoseItems;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import edn.lakeopossmc.simulatedhoses.compat.HoseDyeing;
import edn.lakeopossmc.simulatedhoses.compat.bitsndyes.BitsnDyesBacktankClient;
import edn.lakeopossmc.simulatedhoses.compat.bitsndyes.BitsnDyesClient;
import edn.lakeopossmc.simulatedhoses.compat.bitsndyes.BitsnDyesSoaker;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

// --- DYED MOVING PARTS --- //
// * Renderers pass their parts through here. Does nothing without Bits 'n' Dyes
public final class HoseDyedParts {
    public static final ResourceLocation QUICK_CONNECT = SimulatedHosesMod.asResource("block/fluid_quick_connect/fluid_quick_connect");
    public static final ResourceLocation QUICK_CONNECT_POWERED = SimulatedHosesMod.asResource("block/fluid_quick_connect/fluid_quick_connect_powered");
    public static final ResourceLocation PRESSURE_NOZZLE = SimulatedHosesMod.asResource("block/pressure_nozzle/pressure_nozzle");

    public static SuperByteBuffer dye(final SuperByteBuffer buffer, final BlockEntity be, final ResourceLocation texture) {
        return HoseDyeing.isLoaded() ? BitsnDyesClient.dye(buffer, be, texture) : buffer;
    }

    // * A Super Soaker model in the soaker's colour
    public static BakedModel dye(final BakedModel model, final ItemStack soaker) {
        final DyeColor color = soaker.get(HoseDataComponents.SOAKER_COLOR);
        return color != null && HoseDyeing.isLoaded() ? BitsnDyesSoaker.dye(model, color) : model;
    }

    //#region // --- COPPER FLUID BACKTANK --- //
    @Nullable
    public static DyeColor backtankColor(final ItemStack stack) {
        return HoseDyeing.isLoaded() && stack.is(HoseItems.COPPER_FLUID_BACKTANK.get()) ? stack.get(HoseDataComponents.BACKTANK_COLOR) : null;
    }

    @Nullable
    public static DyeColor backtankColor(final BlockEntity be) {
        return HoseDyeing.backtankColor(be);
    }

    public static BakedModel dyeBacktank(final BakedModel model, @Nullable final DyeColor color) {
        return color != null && HoseDyeing.isLoaded() ? BitsnDyesBacktankClient.dye(model, color) : model;
    }

    public static SuperByteBuffer dyeBacktank(final SuperByteBuffer buffer, @Nullable final DyeColor color) {
        return color != null && HoseDyeing.isLoaded() ? BitsnDyesBacktankClient.dye(buffer, color) : buffer;
    }

    public static SuperByteBuffer dyedBacktankBody(final BlockState state, final DyeColor color) {
        return BitsnDyesBacktankClient.wornBody(state, color);
    }
    //#endregion

    private HoseDyedParts() {
    }
}
