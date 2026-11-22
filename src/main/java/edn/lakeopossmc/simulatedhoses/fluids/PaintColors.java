package edn.lakeopossmc.simulatedhoses.fluids;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// --- WHICH FLUIDS ARE DYE --- //
public final class PaintColors {
    private static final Map<Fluid, Optional<DyeColor>> CACHE = new ConcurrentHashMap<>();

    @Nullable
    public static DyeColor of(final FluidStack fluid) {
        return fluid.isEmpty() ? null : of(fluid.getFluid());
    }

    @Nullable
    public static DyeColor of(final Fluid fluid) {
        return CACHE.computeIfAbsent(fluid, PaintColors::find).orElse(null);
    }

    private static Optional<DyeColor> find(final Fluid fluid) {
        final DyeColor ours = DyeFluids.colorOf(fluid);
        if (ours != null) {
            return Optional.of(ours);
        }
        for (final DyeColor color : DyeColor.values()) {
            if (fluid.is(tag(color))) {
                return Optional.of(color);
            }
        }
        return Optional.empty();
    }

    public static TagKey<Fluid> tag(final DyeColor color) {
        return TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "dyes/" + color.getName()));
    }

    // * Tags can change on reload
    public static void onTagsUpdated(final TagsUpdatedEvent event) {
        CACHE.clear();
    }

    private PaintColors() {
    }
}
