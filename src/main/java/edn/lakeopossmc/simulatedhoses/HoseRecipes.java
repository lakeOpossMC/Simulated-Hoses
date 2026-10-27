package edn.lakeopossmc.simulatedhoses;

import edn.lakeopossmc.simulatedhoses.backtank.FluidBacktankRecipe;
import edn.lakeopossmc.simulatedhoses.items.SuperSoakerDyeingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// --- REGISTERS RECIPE TYPES --- //
public final class HoseRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, SimulatedHosesMod.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<FluidBacktankRecipe>> FLUID_BACKTANK =
            SERIALIZERS.register("fluid_backtank", () -> new SimpleCraftingRecipeSerializer<>(FluidBacktankRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<SuperSoakerDyeingRecipe>> SUPER_SOAKER_DYEING =
            SERIALIZERS.register("super_soaker_dyeing", () -> new SimpleCraftingRecipeSerializer<>(SuperSoakerDyeingRecipe::new));

    private HoseRecipes() {
    }

    public static void register(final IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
