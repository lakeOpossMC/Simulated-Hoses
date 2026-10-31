package edn.lakeopossmc.simulatedhoses.backtank;

import edn.lakeopossmc.simulatedhoses.HoseDataComponents;
import edn.lakeopossmc.simulatedhoses.HoseItems;
import edn.lakeopossmc.simulatedhoses.HoseRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

// --- COPPER FLUID BACKTANK + DYE --- //
// * Shapeless. Recolours the backtank's copper, keeping its air, fluid and the rest. Only loaded with Bits 'n' Dyes
public class FluidBacktankDyeingRecipe extends CustomRecipe {

    public FluidBacktankDyeingRecipe(final CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(final CraftingInput input, final Level level) {
        return this.parts(input) != null;
    }

    @Override
    public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
        final ItemStack[] parts = this.parts(input);
        if (parts == null) {
            return ItemStack.EMPTY;
        }
        final ItemStack result = parts[0].copyWithCount(1);
        result.set(HoseDataComponents.BACKTANK_COLOR, ((DyeItem) parts[1].getItem()).getDyeColor());
        return result;
    }

    private ItemStack[] parts(final CraftingInput input) {
        ItemStack backtank = null;
        ItemStack dye = null;
        for (int i = 0; i < input.size(); i++) {
            final ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(HoseItems.COPPER_FLUID_BACKTANK.get()) && backtank == null) {
                backtank = stack;
            } else if (stack.getItem() instanceof DyeItem && dye == null) {
                dye = stack;
            } else {
                return null;
            }
        }
        return backtank != null && dye != null ? new ItemStack[]{backtank, dye} : null;
    }

    @Override
    public boolean canCraftInDimensions(final int width, final int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HoseRecipes.FLUID_BACKTANK_DYEING.get();
    }
}
