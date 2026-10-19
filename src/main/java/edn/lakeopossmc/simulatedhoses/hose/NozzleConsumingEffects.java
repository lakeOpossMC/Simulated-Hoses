package edn.lakeopossmc.simulatedhoses.hose;

import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// --- FLUIDS THAT TURN INTO SOMETHING WHERE THEY LAND --- //
public final class NozzleConsumingEffects {
    private static final String CONSUMING_HANDLER = "ConsumingOpenPipeEffectHandler";
    private static final TagKey<Fluid> EXPERIENCE = FluidTags.create(ResourceLocation.fromNamespaceAndPath("c", "experience"));
    private static final int MB_PER_EXPERIENCE = 20;
    private static final int MAX_BANK = 1000;
    private static final double SPLASH = 0.75;

    private static final Map<Class<?>, Optional<Method>> CONSUME_METHODS = new ConcurrentHashMap<>();

    public static final class Progress {
        private FluidStack fluid = FluidStack.EMPTY;
        private int amount;
    }

    public static void apply(final ServerLevel level, final NozzleJet.Impact impact, final FluidStack fluid, final int flow, final Progress progress) {
        if (fluid.isEmpty() || flow <= 0) {
            return;
        }
        final OpenPipeEffectHandler handler = OpenPipeEffectHandler.REGISTRY.get(fluid.getFluid());
        final Method consume = handler != null ? consumeMethod(handler) : null;
        final boolean looseExperience = handler == null && fluid.is(EXPERIENCE);
        if (consume == null && !looseExperience) {
            progress.amount = 0;
            return;
        }

        if (!FluidStack.isSameFluidSameComponents(progress.fluid, fluid)) {
            progress.fluid = fluid.copyWithAmount(1);
            progress.amount = 0;
        }
        progress.amount = Math.min(progress.amount + flow, MAX_BANK);

        final AABB area = impact.entity() != null
                ? impact.entity().getBoundingBox().inflate(SPLASH * 0.5)
                : new AABB(impact.point(), impact.point()).inflate(SPLASH);
        if (consume != null) {
            progress.amount -= consume(consume, handler, level, area, fluid.copyWithAmount(progress.amount), progress.amount);
            return;
        }
        final int points = progress.amount / MB_PER_EXPERIENCE;
        if (points > 0) {
            ExperienceOrb.award(level, area.getCenter(), points);
            progress.amount -= points * MB_PER_EXPERIENCE;
        }
    }

    private static int consume(final Method consume, final OpenPipeEffectHandler handler, final Level level, final AABB area,
                               final FluidStack fluid, final int available) {
        try {
            final Object used = consume.invoke(handler, level, area, fluid);
            return used instanceof final Integer amount ? Math.clamp(amount, 0, available) : 0;
        } catch (final ReflectiveOperationException | RuntimeException e) {
            SimulatedHosesMod.LOGGER.debug("Pressure nozzle could not run consuming pipe effect {}", handler.getClass().getName(), e);
            return 0;
        }
    }

    @Nullable
    private static Method consumeMethod(final OpenPipeEffectHandler handler) {
        return CONSUME_METHODS.computeIfAbsent(handler.getClass(), type -> {
            if (!implementsByName(type, CONSUMING_HANDLER)) {
                return Optional.empty();
            }
            try {
                final Method method = type.getMethod("consume", Level.class, AABB.class, FluidStack.class);
                method.setAccessible(true);
                return Optional.of(method);
            } catch (final ReflectiveOperationException | RuntimeException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static boolean implementsByName(@Nullable final Class<?> type, final String simpleName) {
        if (type == null) {
            return false;
        }
        for (final Class<?> face : type.getInterfaces()) {
            if (face.getSimpleName().equals(simpleName) || implementsByName(face, simpleName)) {
                return true;
            }
        }
        return implementsByName(type.getSuperclass(), simpleName);
    }

    private NozzleConsumingEffects() {
    }
}
