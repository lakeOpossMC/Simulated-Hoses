package edn.lakeopossmc.simulatedhoses.items;

import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import edn.lakeopossmc.simulatedhoses.backtank.BacktankTanks;
import net.createmod.catnip.data.Pair;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

// --- POURING INTO A TANK ITEM IN THE INVENTORY --- //
public final class FluidDeposits {

    // * True when the click was taken, even if there was no room
    public static boolean pour(final ItemStack target, final IFluidHandlerItem tank, final ItemStack carried, final ClickAction action,
                               final Player player, final SlotAccess carriedAccess) {
        if (action != ClickAction.SECONDARY || carried.isEmpty() || target.getCount() != 1) {
            return false;
        }
        final Level level = player.level();
        if (!GenericItemEmptying.canItemBeEmptied(level, carried)) {
            return false;
        }
        final FluidStack fluid = GenericItemEmptying.emptyItem(level, carried, true).getFirst();
        if (fluid.isEmpty()) {
            return false;
        }
        if (tank.fill(fluid, IFluidHandler.FluidAction.SIMULATE) < fluid.getAmount()) {
            return true;
        }

        final Pair<FluidStack, ItemStack> poured = GenericItemEmptying.emptyItem(level, carried, false);
        tank.fill(poured.getFirst(), IFluidHandler.FluidAction.EXECUTE);
        final ItemStack emptied = poured.getSecond();
        if (carried.isEmpty()) {
            carriedAccess.set(emptied);
        } else if (!emptied.isEmpty() && !player.getInventory().add(emptied)) {
            player.drop(emptied, false);
        }
        final SoundEvent sound = poured.getFirst().getFluid().getFluidType().getSound(poured.getFirst(), SoundActions.BUCKET_EMPTY);
        player.playSound(sound != null ? sound : SoundEvents.BUCKET_EMPTY, 0.8F, 1.0F);
        return true;
    }

    // * The other way round
    public static boolean draw(final ItemStack target, final BacktankTanks tanks, final int[] from, final ItemStack carried,
                               final ClickAction action, final Player player, final SlotAccess carriedAccess) {
        if (action != ClickAction.SECONDARY || carried.isEmpty() || target.getCount() != 1) {
            return false;
        }
        final Level level = player.level();
        if (!GenericItemFilling.canItemBeFilled(level, carried)) {
            return false;
        }
        for (final int tank : from) {
            final FluidStack available = tanks.getFluidInTank(tank);
            if (available.isEmpty()) {
                continue;
            }
            final int required = GenericItemFilling.getRequiredAmountForItem(level, carried, available.copy());
            if (required <= 0 || required > available.getAmount()) {
                continue;
            }
            final FluidStack drained = tanks.tank(tank).drain(required, IFluidHandler.FluidAction.EXECUTE);
            if (drained.getAmount() < required) {
                continue;
            }
            final ItemStack filled = GenericItemFilling.fillItem(level, required, carried, drained.copy());
            if (carried.isEmpty()) {
                carriedAccess.set(filled);
            } else if (!filled.isEmpty() && !player.getInventory().add(filled)) {
                player.drop(filled, false);
            }
            final SoundEvent sound = drained.getFluid().getFluidType().getSound(drained, SoundActions.BUCKET_FILL);
            player.playSound(sound != null ? sound : SoundEvents.BUCKET_FILL, 0.8F, 1.0F);
            return true;
        }
        return true;
    }

    private FluidDeposits() {
    }
}
