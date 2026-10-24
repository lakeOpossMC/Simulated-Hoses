package edn.lakeopossmc.simulatedhoses.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;

// --- PLANT FOOD IN THE WORLD --- //
public class PlantFoodBlock extends LiquidBlock {
    public PlantFoodBlock(final FlowingFluid fluid, final Properties properties) {
        super(fluid, properties);
    }

    @Override
    public ItemStack pickupBlock(@Nullable final Player player, final LevelAccessor level, final BlockPos pos, final BlockState state) {
        final ItemStack picked = super.pickupBlock(player, level, pos, state);
        if (!picked.isEmpty()) {
            PlantFoodSounds.squish(level, player, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.0F);
        }
        return picked;
    }
}
