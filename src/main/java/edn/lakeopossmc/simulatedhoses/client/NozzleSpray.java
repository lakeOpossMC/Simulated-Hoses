package edn.lakeopossmc.simulatedhoses.client;

import edn.lakeopossmc.simulatedhoses.hose.NozzleJet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

// --- JET FROM A PRESSURE NOZZLE --- //
public final class NozzleSpray {
    private static final double JET_RADIUS = 1.0 / 16.0;
    private static final double SPREAD = 0.025;
    private static final double SPEED_JITTER = 0.04;
    private static final int MIN_PARTICLES = 2;
    private static final int EXTRA_PARTICLES = 6;

    public static void tick(final Level level, final BlockPos pos, final Direction direction, final double faceOffset,
                            final FluidStack fluid, final int flow, final float fullFlow) {
        if (fluid.isEmpty() || flow <= 0 || !(level instanceof ClientLevel)) {
            return;
        }
        tick(level, NozzleJet.aim(level, pos, direction, faceOffset), fluid, flow, fullFlow);
    }

    public static void tick(final Level level, final NozzleJet.Aim aim, final FluidStack fluid, final int flow, final float fullFlow) {
        if (fluid.isEmpty() || flow <= 0 || !(level instanceof final ClientLevel clientLevel)) {
            return;
        }
        final RandomSource random = level.random;

        final double fraction = Mth.clamp(flow / fullFlow, 0.0, 1.0);
        final double speed = NozzleJet.speed(fraction);
        final int lifetime = NozzleJet.lifetime(fraction);
        final int count = MIN_PARTICLES + (int) Math.round(fraction * EXTRA_PARTICLES);
        for (int i = 0; i < count; i++) {
            final double angle = random.nextDouble() * Math.PI * 2;
            final double radius = JET_RADIUS * Math.sqrt(random.nextDouble());
            final double particleSpeed = speed * (1.0 + (random.nextDouble() - 0.5) * 2.0 * SPEED_JITTER);
            final Vec3 velocity = aim.direction().scale(particleSpeed)
                    .add(aim.side1().scale((random.nextDouble() - 0.5) * 2.0 * SPREAD * particleSpeed))
                    .add(aim.side2().scale((random.nextDouble() - 0.5) * 2.0 * SPREAD * particleSpeed));
            final Vec3 at = aim.start()
                    .add(aim.side1().scale(Math.cos(angle) * radius))
                    .add(aim.side2().scale(Math.sin(angle) * radius))
                    .add(velocity.scale(random.nextDouble()));
            Minecraft.getInstance().particleEngine.add(new NozzleJetParticle(clientLevel, fluid, at.x, at.y, at.z,
                    velocity.x, velocity.y, velocity.z, lifetime + random.nextInt(3)));
        }
    }

    private NozzleSpray() {
    }
}
