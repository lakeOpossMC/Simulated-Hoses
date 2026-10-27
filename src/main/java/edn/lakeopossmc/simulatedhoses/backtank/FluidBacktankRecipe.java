package edn.lakeopossmc.simulatedhoses.backtank;

import com.simibubi.create.AllBlocks;
import edn.lakeopossmc.simulatedhoses.HoseRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

// --- BACKTANK + FLUID TANKS --- //
// * Shapeless, and keeps the backtank's air, enchantments, name and fluid
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
        final ItemStack result = new ItemStack(FluidBacktanks.withMoreTanks(backtank.getItem(), tanks(input)));
        result.applyComponents(backtank.getComponentsPatch());
        return result;
    }

    private ItemStack backtank(final CraftingInput input) {
        ItemStack backtank = null;
        for (int i = 0; i < input.size(); i++) {
            final ItemStack stack = input.getItem(i);
            if (stack.isEmpty() || stack.is(AllBlocks.FLUID_TANK.asItem())) {
                continue;
            }
            if (backtank != null) {
                return null;
            }
            backtank = stack;
        }
        final int tanks = tanks(input);
        return backtank != null && tanks > 0 && FluidBacktanks.withMoreTanks(backtank.getItem(), tanks) != null ? backtank : null;
    }

    private static int tanks(final CraftingInput input) {
        int tanks = 0;
        for (int i = 0; i < input.size(); i++) {
            if (input.getItem(i).is(AllBlocks.FLUID_TANK.asItem())) {
                tanks++;
            }
        }
        return tanks;
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
