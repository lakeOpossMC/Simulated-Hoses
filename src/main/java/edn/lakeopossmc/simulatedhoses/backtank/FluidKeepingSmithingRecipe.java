package edn.lakeopossmc.simulatedhoses.backtank;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import edn.lakeopossmc.simulatedhoses.HoseDataComponents;
import edn.lakeopossmc.simulatedhoses.HoseRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

import java.util.List;
import java.util.function.Supplier;

// --- NETHERITE CHESTPLATE + FLUID BACKTANK --- //
public class FluidKeepingSmithingRecipe extends SmithingTransformRecipe {
    private static final List<Supplier<? extends DataComponentType<?>>> KEPT = List.of(
            HoseDataComponents.BACKTANK_FLUID, HoseDataComponents.BACKTANK_SECOND_FLUID, HoseDataComponents.BACKTANK_FEED);

    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final ItemStack result;

    public FluidKeepingSmithingRecipe(final Ingredient template, final Ingredient base, final Ingredient addition, final ItemStack result) {
        super(template, base, addition, result);
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.result = result;
    }

    @Override
    public ItemStack assemble(final SmithingRecipeInput input, final HolderLookup.Provider registries) {
        final ItemStack assembled = super.assemble(input, registries);
        for (final Supplier<? extends DataComponentType<?>> type : KEPT) {
            copy(type.get(), input.addition(), assembled);
        }
        return assembled;
    }

    private static <T> void copy(final DataComponentType<T> type, final ItemStack from, final ItemStack to) {
        final T value = from.get(type);
        if (value != null) {
            to.set(type, value);
        }
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HoseRecipes.FLUID_KEEPING_SMITHING.get();
    }

    // * The same format as vanilla's smithing_transform
    public static class Serializer implements RecipeSerializer<FluidKeepingSmithingRecipe> {
        private static final MapCodec<FluidKeepingSmithingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC.fieldOf("template").forGetter(recipe -> recipe.template),
                Ingredient.CODEC.fieldOf("base").forGetter(recipe -> recipe.base),
                Ingredient.CODEC.fieldOf("addition").forGetter(recipe -> recipe.addition),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
        ).apply(instance, FluidKeepingSmithingRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, FluidKeepingSmithingRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.template,
                Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.base,
                Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.addition,
                ItemStack.STREAM_CODEC, recipe -> recipe.result,
                FluidKeepingSmithingRecipe::new);

        @Override
        public MapCodec<FluidKeepingSmithingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FluidKeepingSmithingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
