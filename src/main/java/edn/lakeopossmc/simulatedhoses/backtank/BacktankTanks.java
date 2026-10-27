package edn.lakeopossmc.simulatedhoses.backtank;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

// --- A FLUID BACKTANK'S SIDE TANKS --- //
public class BacktankTanks implements IFluidHandlerItem {
    private final ItemStack container;
    private final IFluidHandler[] tanks;
    private final int first;
    private final int[] drainable;

    // * Each handler is a single tank
    public BacktankTanks(final ItemStack container, final int pullMode, final IFluidHandler... tanks) {
        this.container = container;
        this.tanks = tanks;
        if (tanks.length < 2) {
            this.first = 0;
            this.drainable = new int[]{0};
        } else if (pullMode == FluidBacktankItem.PULL_BOTH) {
            this.first = 0;
            this.drainable = new int[]{0, 1};
        } else {
            this.first = Math.floorMod(pullMode, tanks.length);
            this.drainable = new int[]{this.first};
        }
    }

    public int[] drainable() {
        return this.drainable.clone();
    }

    public int[] order() {
        final int[] order = new int[this.tanks.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = (this.first + i) % this.tanks.length;
        }
        return order;
    }

    public IFluidHandler tank(final int tank) {
        return this.tanks[tank];
    }

    @Override
    public int getTanks() {
        return this.tanks.length;
    }

    @Override
    public FluidStack getFluidInTank(final int tank) {
        return this.tanks[tank].getFluidInTank(0);
    }

    @Override
    public int getTankCapacity(final int tank) {
        return this.tanks[tank].getTankCapacity(0);
    }

    @Override
    public boolean isFluidValid(final int tank, final FluidStack stack) {
        return this.tanks[tank].isFluidValid(0, stack);
    }

    @Override
    public int fill(final FluidStack resource, final FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        int filled = 0;
        for (final boolean holding : new boolean[]{true, false}) {
            for (final int tank : this.order()) {
                if (filled >= resource.getAmount()) {
                    return filled;
                }
                final FluidStack held = this.getFluidInTank(tank);
                final boolean fits = holding ? !held.isEmpty() && FluidStack.isSameFluidSameComponents(held, resource) : held.isEmpty();
                if (fits) {
                    filled += this.tanks[tank].fill(resource.copyWithAmount(resource.getAmount() - filled), action);
                }
            }
        }
        return filled;
    }

    @Override
    public FluidStack drain(final FluidStack resource, final FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        for (final int tank : this.drainable) {
            if (FluidStack.isSameFluidSameComponents(this.getFluidInTank(tank), resource)) {
                return this.tanks[tank].drain(resource, action);
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(final int maxDrain, final FluidAction action) {
        for (final int tank : this.drainable) {
            if (!this.getFluidInTank(tank).isEmpty()) {
                return this.tanks[tank].drain(maxDrain, action);
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public ItemStack getContainer() {
        return this.container;
    }
}
