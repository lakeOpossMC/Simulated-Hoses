package edn.lakeopossmc.simulatedhoses.client;

import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import edn.lakeopossmc.simulatedhoses.compat.HoseDyeing;
import edn.lakeopossmc.simulatedhoses.compat.bitsndyes.BitsnDyesClient;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.resources.ResourceLocation;
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

    private HoseDyedParts() {
    }
}
