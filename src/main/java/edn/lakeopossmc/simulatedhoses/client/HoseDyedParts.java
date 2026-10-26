package edn.lakeopossmc.simulatedhoses.client;

import edn.lakeopossmc.simulatedhoses.HoseDataComponents;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import edn.lakeopossmc.simulatedhoses.compat.HoseDyeing;
import edn.lakeopossmc.simulatedhoses.compat.bitsndyes.BitsnDyesClient;
import edn.lakeopossmc.simulatedhoses.compat.bitsndyes.BitsnDyesSoaker;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

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

    private HoseDyedParts() {
    }
}
