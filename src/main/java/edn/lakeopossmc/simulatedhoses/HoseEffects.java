package edn.lakeopossmc.simulatedhoses;

import edn.lakeopossmc.simulatedhoses.fluids.GoopedEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// --- REGISTERS ALL EFFECTS --- //
public final class HoseEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SimulatedHosesMod.MOD_ID);

    public static final DeferredHolder<MobEffect, GoopedEffect> GOOPED = EFFECTS.register("gooped", GoopedEffect::new);

    private HoseEffects() {
    }

    public static void register(final IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
