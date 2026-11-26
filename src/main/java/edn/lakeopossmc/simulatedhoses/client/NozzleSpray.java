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

    private static final double MIST_SPEED = 0.35;
    private static final double EXTRA_MIST_SPEED = 0.35;
    private static final double MIST_SPREAD = 0.12;
    private static final float MIST_SIZE = 0.12F;
    private static final float EXTRA_MIST_SIZE = 0.1F;
    private static final int MIST_LIFETIME = 8;
    private static final int EXTRA_MIST_LIFETIME = 7;
    // * How many ticks of jet the mist can start along
    private static final double MIST_REACH = 2.0;

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

        // * Each fluid sprays the way its spray effect says, from resource packs
        final SprayEffect effect = SprayEffects.of(fluid);
        if (effect != null) {
            switch (effect.style) {
                case SPRAY -> clumps(clientLevel, aim, fluid, effect, fraction, speed, lifetime);
                case GLOB -> globs(clientLevel, aim, fluid, effect, fraction, speed, lifetime);
                case MOLTEN -> magma(clientLevel, aim, fluid, effect, fraction, speed, lifetime);
                case VISCOUS -> viscous(clientLevel, aim, fluid, effect, fraction, speed, lifetime);
            }
            return;
        }

        // * No effect loaded at all
        final int count = MIN_PARTICLES + (int) Math.round(fraction * EXTRA_PARTICLES);
        for (int i = 0; i < count; i++) {
            final double particleSpeed = speed * (1.0 + (random.nextDouble() - 0.5) * 2.0 * SPEED_JITTER);
            final Vec3 velocity = spread(aim, random, particleSpeed, SPREAD);
            final Vec3 at = from(aim, random).add(velocity.scale(random.nextDouble()));
            Minecraft.getInstance().particleEngine.add(new NozzleJetParticle(clientLevel, fluid, at.x, at.y, at.z,
                    velocity.x, velocity.y, velocity.z, lifetime + random.nextInt(3)));
        }
    }

    private static int count(final SprayEffect effect, final double fraction) {
        return effect.minCount + (int) Math.round(fraction * effect.extraCount);
    }

    private static float size(final SprayEffect effect, final double fraction) {
        return effect.size * (1.0F + effect.flowGrowth * (float) fraction);
    }

    private static void clumps(final ClientLevel level, final NozzleJet.Aim aim, final FluidStack fluid, final SprayEffect effect,
                               final double fraction, final double speed, final int lifetime) {
        final RandomSource random = level.random;
        final int rgb = effect.color(fluid);
        final boolean glows = effect.emissive(fluid);
        final float size = size(effect, fraction);

        final int clumps = count(effect, fraction);
        for (int i = 0; i < clumps; i++) {
            final double clumpSpeed = speed * (1.0 + (random.nextDouble() - 0.5) * 2.0 * SPEED_JITTER);
            final Vec3 velocity = spread(aim, random, clumpSpeed, SPREAD);
            final Vec3 at = from(aim, random).add(velocity.scale(random.nextDouble()));
            Minecraft.getInstance().particleEngine.add(new FluidSprayParticle(level, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z,
                    lifetime + random.nextInt(3), rgb, size * (0.85F + random.nextFloat() * 0.3F), FluidSprayParticle.Kind.CORE, glows,
                    effect));
        }

        // * Only a strong jet throws off mist
        final int mist = (int) (fraction * effect.mist + random.nextDouble() * fraction);
        for (int i = 0; i < mist; i++) {
            final double mistSpeed = speed * (MIST_SPEED + random.nextDouble() * EXTRA_MIST_SPEED);
            final Vec3 velocity = spread(aim, random, mistSpeed, MIST_SPREAD);
            final Vec3 at = from(aim, random).add(aim.direction().scale(random.nextDouble() * speed * MIST_REACH));
            Minecraft.getInstance().particleEngine.add(new FluidSprayParticle(level, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z,
                    MIST_LIFETIME + random.nextInt(EXTRA_MIST_LIFETIME), rgb, MIST_SIZE + random.nextFloat() * EXTRA_MIST_SIZE,
                    FluidSprayParticle.Kind.MIST, glows, effect));
        }
    }

    private static void globs(final ClientLevel level, final NozzleJet.Aim aim, final FluidStack fluid, final SprayEffect effect,
                              final double fraction, final double speed, final int lifetime) {
        final RandomSource random = level.random;
        final int rgb = effect.color(fluid);
        final boolean glows = effect.emissive(fluid);
        final float size = size(effect, fraction);
        final int globs = count(effect, fraction);
        for (int i = 0; i < globs; i++) {
            final double globSpeed = speed * (1.0 + (random.nextDouble() - 0.5) * 2.0 * SPEED_JITTER);
            final Vec3 velocity = spread(aim, random, globSpeed, SPREAD);
            final Vec3 at = from(aim, random).add(velocity.scale(random.nextDouble()));
            Minecraft.getInstance().particleEngine.add(new PaintGlobParticle(level, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z,
                    lifetime + random.nextInt(3), rgb, size, (float) fraction, effect, glows).squeezed());
        }
    }

    private static void magma(final ClientLevel level, final NozzleJet.Aim aim, final FluidStack fluid, final SprayEffect effect,
                              final double fraction, final double speed, final int lifetime) {
        final RandomSource random = level.random;
        final int[] palette = effect.palette(fluid);
        final boolean glows = effect.emissive(fluid);
        final float size = size(effect, fraction);
        final int globs = count(effect, fraction);
        for (int i = 0; i < globs; i++) {
            final double globSpeed = speed * (1.0 + (random.nextDouble() - 0.5) * 2.0 * SPEED_JITTER);
            final Vec3 velocity = spread(aim, random, globSpeed, SPREAD);
            final Vec3 at = from(aim, random).add(velocity.scale(random.nextDouble()));
            Minecraft.getInstance().particleEngine.add(new MagmaGlobParticle(level, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z,
                    lifetime + random.nextInt(3), palette, size, (float) fraction, effect, glows).squeezed());
        }
    }

    private static void viscous(final ClientLevel level, final NozzleJet.Aim aim, final FluidStack fluid, final SprayEffect effect,
                                final double fraction, final double speed, final int lifetime) {
        final RandomSource random = level.random;
        final int rgb = effect.color(fluid);
        final boolean glows = effect.emissive(fluid);
        final float size = size(effect, fraction);
        final int globs = count(effect, fraction);
        for (int i = 0; i < globs; i++) {
            final double globSpeed = speed * (1.0 + (random.nextDouble() - 0.5) * 2.0 * SPEED_JITTER);
            final Vec3 velocity = spread(aim, random, globSpeed, SPREAD);
            final Vec3 at = from(aim, random).add(velocity.scale(random.nextDouble()));
            Minecraft.getInstance().particleEngine.add(new ViscousGlobParticle(level, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z,
                    lifetime + random.nextInt(3), rgb, size, (float) fraction, effect, glows).squeezed());
        }
    }

    private static Vec3 spread(final NozzleJet.Aim aim, final RandomSource random, final double speed, final double spread) {
        return aim.direction().scale(speed)
                .add(aim.side1().scale((random.nextDouble() - 0.5) * 2.0 * spread * speed))
                .add(aim.side2().scale((random.nextDouble() - 0.5) * 2.0 * spread * speed));
    }

    private static Vec3 from(final NozzleJet.Aim aim, final RandomSource random) {
        final double angle = random.nextDouble() * Math.PI * 2;
        final double radius = JET_RADIUS * Math.sqrt(random.nextDouble());
        return aim.start()
                .add(aim.side1().scale(Math.cos(angle) * radius))
                .add(aim.side2().scale(Math.sin(angle) * radius));
    }

    private NozzleSpray() {
    }
}
