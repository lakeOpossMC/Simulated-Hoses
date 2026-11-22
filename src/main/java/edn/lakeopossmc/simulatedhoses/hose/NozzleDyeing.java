package edn.lakeopossmc.simulatedhoses.hose;

import edn.lakeopossmc.simulatedhoses.blocks.PressureNozzleBlockEntity;
import edn.lakeopossmc.simulatedhoses.fluids.Dyeing;
import edn.lakeopossmc.simulatedhoses.fluids.PaintColors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;

// --- A DYE JET COLOURS WHERE IT LANDS --- //
public final class NozzleDyeing {
    private static final double NO_SPREAD_BELOW = 0.15;
    private static final int MAX_RADIUS = 3;

    public static void apply(final ServerLevel level, final NozzleJet.Impact impact, final FluidStack fluid, final int flow) {
        final DyeColor color = PaintColors.of(fluid);
        if (color == null) {
            return;
        }
        if (impact.entity() != null) {
            Dyeing.dyeEntity(impact.entity(), color);
            return;
        }
        final BlockHitResult block = impact.block();
        if (block != null) {
            Dyeing.dyePatch(level, block.getBlockPos(), block.getDirection(), radius(flow), color);
        }
    }

    public static int radius(final int flow) {
        final double fraction = Mth.clamp(flow / PressureNozzleBlockEntity.FULL_FLOW, 0.0, 1.0);
        return fraction < NO_SPREAD_BELOW ? 0 : Math.min(MAX_RADIUS, Mth.ceil(fraction * MAX_RADIUS));
    }

    private NozzleDyeing() {
    }
}
