package edn.lakeopossmc.simulatedhoses.client;

import com.simibubi.create.content.fluids.FluidFX;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3d;

// --- SPRAY FROM AN OPEN HOSE END --- //
public final class HoseSpray {
    private static final double RIM_RADIUS = 0.265625;

    public static void tick(final Level level, final BlockPos pos, final Direction direction, final double faceOffset, final FluidStack fluid, final int flow) {
        if (fluid.isEmpty() || flow <= 0) {
            return;
        }
        final RandomSource random = level.random;
        final ParticleOptions particle = FluidFX.getFluidParticle(fluid);
        final SubLevel subLevel = Sable.HELPER.getContaining(level, new ChunkPos(pos));

        // * Face center and direction, turned into world space
        final Vector3d normal = new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        final Vector3d center = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5).fma(faceOffset, normal);
        final Vector3d side1 = new Vector3d(Math.abs(normal.y) > 0.5 ? 1 : 0, Math.abs(normal.y) > 0.5 ? 0 : 1, 0);
        final Vector3d side2 = normal.cross(side1, new Vector3d());
        if (subLevel != null) {
            subLevel.logicalPose().transformPosition(center);
            subLevel.logicalPose().orientation().transform(normal);
            subLevel.logicalPose().orientation().transform(side1);
            subLevel.logicalPose().orientation().transform(side2);
        }

        // * A busier hose throws out a thicker spray
        final int count = flow >= 100 ? 2 : 1;
        for (int i = 0; i < count; i++) {
            final double angle = random.nextDouble() * Math.PI * 2;
            final double radius = RIM_RADIUS * Math.sqrt(random.nextDouble());
            final Vector3d at = new Vector3d(center)
                    .fma(Math.cos(angle) * radius, side1)
                    .fma(Math.sin(angle) * radius, side2);
            final double speed = 0.1 + random.nextDouble() * 0.08;
            final Vector3d motion = new Vector3d(normal).mul(speed)
                    .fma((random.nextDouble() - 0.5) * 0.04, side1)
                    .fma((random.nextDouble() - 0.5) * 0.04, side2);
            level.addParticle(particle, at.x, at.y, at.z, motion.x, motion.y, motion.z);
        }
    }

    private HoseSpray() {
    }
}
