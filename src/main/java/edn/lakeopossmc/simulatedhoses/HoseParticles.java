package edn.lakeopossmc.simulatedhoses;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// --- REGISTERS PARTICLES --- //
public final class HoseParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, SimulatedHosesMod.MOD_ID);

    // * Globs of paint thrown by a dye jet, tinted by the client as it spawns them
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PAINT_GLOB = PARTICLES.register(
            "paint_glob", () -> new SimpleParticleType(false));

    // * Foamy clumps of any other sprayed fluid, tinted by the client as it spawns them
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLUID_SPRAY = PARTICLES.register(
            "fluid_spray", () -> new SimpleParticleType(false));

    // * Molten globs thrown by a lava jet
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MAGMA_GLOB = PARTICLES.register(
            "magma_glob", () -> new SimpleParticleType(false));

    // * Heavy globs of honey, chocolate or plant food
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VISCOUS_GLOB = PARTICLES.register(
            "viscous_glob", () -> new SimpleParticleType(false));

    private HoseParticles() {
    }

    public static void register(final IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
