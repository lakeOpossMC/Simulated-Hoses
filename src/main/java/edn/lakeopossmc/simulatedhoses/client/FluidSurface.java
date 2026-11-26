package edn.lakeopossmc.simulatedhoses.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// --- WHERE A PARTICLE MEETS THE SURFACE OF A FLUID --- //
final class FluidSurface {
    private static final double STEP = 0.25;

    // * The point on the surface where it went in, or null if it didn't
    @Nullable
    static Vec3 entered(final Level level, final double fromX, final double fromY, final double fromZ,
                        final double toX, final double toY, final double toZ) {
        if (inside(level, fromX, fromY, fromZ)) {
            return null;
        }
        final double dx = toX - fromX;
        final double dy = toY - fromY;
        final double dz = toZ - fromZ;
        final int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / STEP));
        double lastY = fromY;
        for (int i = 1; i <= steps; i++) {
            final double t = (double) i / steps;
            final double x = fromX + dx * t;
            final double y = fromY + dy * t;
            final double z = fromZ + dz * t;
            final BlockPos pos = BlockPos.containing(x, y, z);
            final FluidState fluid = level.getFluidState(pos);
            if (!fluid.isEmpty()) {
                final double surface = pos.getY() + fluid.getHeight(level, pos);
                if (y <= surface) {
                    return new Vec3(x, lastY > surface ? surface : y, z);
                }
            }
            lastY = y;
        }
        return null;
    }

    private static boolean inside(final Level level, final double x, final double y, final double z) {
        final BlockPos pos = BlockPos.containing(x, y, z);
        final FluidState fluid = level.getFluidState(pos);
        return !fluid.isEmpty() && y <= pos.getY() + fluid.getHeight(level, pos);
    }

    private FluidSurface() {
    }
}
