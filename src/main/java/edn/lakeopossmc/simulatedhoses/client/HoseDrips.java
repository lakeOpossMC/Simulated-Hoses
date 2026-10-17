package edn.lakeopossmc.simulatedhoses.client;

import com.simibubi.create.content.fluids.FluidFX;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopePoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3d;

import java.util.List;

// --- DRIPS ALONG A HOSE THAT IS CARRYING FLUID --- //
public final class HoseDrips {
    // * Chance per block of hose per tick at full flow
    private static final float DRIPS_PER_BLOCK = 0.02f;
    private static final float FULL_FLOW = 250.0f;
    private static final double DROP_BELOW = 0.2;

    public static void tick(final Level level, final ClientRopeStrand strand, final FluidStack fluid, final int flow) {
        if (flow <= 0 || fluid.isEmpty()) {
            return;
        }
        final List<ClientRopePoint> points = strand.getPoints();
        if (points.size() < 2) {
            return;
        }

        final RandomSource random = level.random;
        final float speed = Math.min(1.0f, 0.25f + flow / FULL_FLOW);
        final float chance = DRIPS_PER_BLOCK * speed * (points.size() - 1);
        if (random.nextFloat() >= chance) {
            return;
        }

        // * A random spot on a random segment
        final int segment = random.nextInt(points.size() - 1);
        final Vector3d a = points.get(segment).position();
        final Vector3d b = points.get(segment + 1).position();
        final double t = random.nextDouble();
        final double x = a.x + (b.x - a.x) * t;
        final double y = a.y + (b.y - a.y) * t - DROP_BELOW;
        final double z = a.z + (b.z - a.z) * t;

        final ParticleOptions drip = FluidFX.getDrippingParticle(fluid);
        level.addParticle(drip, x, y, z, 0, 0, 0);
    }

    private HoseDrips() {
    }
}
