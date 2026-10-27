package edn.lakeopossmc.simulatedhoses.backtank;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import edn.lakeopossmc.simulatedhoses.HoseItems;
import edn.lakeopossmc.simulatedhoses.HoseRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

// --- COPPER BACKTANK + FLUID TANK --- //
public class FluidBacktankRecipe extends CustomRecipe {

    public FluidBacktankRecipe(final CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(final CraftingInput input, final Level level) {
        return this.backtank(input) != null;
    }

    @Override
    public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
        final ItemStack backtank = this.backtank(input);
        if (backtank == null) {
            return ItemStack.EMPTY;
        }
        final ItemStack result = new ItemStack(HoseItems.COPPER_FLUID_BACKTANK.get());
        result.applyComponents(backtank.getComponentsPatch());
        return result;
    }

    private ItemStack backtank(final CraftingInput input) {
        ItemStack backtank = null;
        boolean tank = false;
        for (int i = 0; i < input.size(); i++) {
            final ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(AllItems.COPPER_BACKTANK.get()) && backtank == null) {
                backtank = stack;
            } else if (stack.is(AllBlocks.FLUID_TANK.asItem()) && !tank) {
                tank = true;
            } else {
                return null;
            }
        }
        return tank ? backtank : null;
    }

    @Override
    public boolean canCraftInDimensions(final int width, final int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HoseRecipes.FLUID_BACKTANK.get();
    }
}
