package edn.lakeopossmc.simulatedhoses.backtank;

import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import edn.lakeopossmc.simulatedhoses.HoseDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

// --- PLACED BACKTANK WITH FLUID TANKS --- //
public class FluidBacktankBlockEntity extends BacktankBlockEntity {
    public static final int TANKS = 2;
    private static final String[] TANK_TAGS = {"Tank", "SecondTank"};

    private final FluidTank[] tanks = {this.newTank(), this.newTank()};
    private int feedTank = FluidBacktankItem.PULL_BOTH;

    public FluidBacktankBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    private FluidTank newTank() {
        return new FluidTank(FluidBacktankItem.CAPACITY) {
            @Override
            protected void onContentsChanged() {
                FluidBacktankBlockEntity.this.setChanged();
                if (FluidBacktankBlockEntity.this.level != null && !FluidBacktankBlockEntity.this.level.isClientSide) {
                    FluidBacktankBlockEntity.this.sendData();
                }
            }
        };
    }

    public int tankCount() {
        return this.getBlockState().getBlock() instanceof final FluidBacktankBlock block ? block.tankCount() : 1;
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable final Direction side) {
        if (side != null && side != Direction.DOWN) {
            return null;
        }
        return new BacktankTanks(ItemStack.EMPTY, this.feedTank, Arrays.copyOf(this.tanks, this.tankCount()));
    }

    // * Capacity enchantments make room for more fluid too
    @Override
    public void setCapacityEnchantLevel(final int capacityEnchantLevel) {
        super.setCapacityEnchantLevel(capacityEnchantLevel);
        for (final FluidTank tank : this.tanks) {
            tank.setCapacity(FluidBacktankItem.capacityFor(capacityEnchantLevel));
        }
    }

    public int getCapacity() {
        return this.tanks[0].getCapacity();
    }

    public FluidStack getFluid(final int tank) {
        return this.tanks[tank].getFluid();
    }

    public void setFluid(final int tank, final FluidStack fluid) {
        this.tanks[tank].setFluid(fluid.copy());
        this.setChanged();
    }

    public int getFeedTank() {
        return this.feedTank;
    }

    public void setFeedTank(final int feedTank) {
        this.feedTank = Math.floorMod(feedTank, FluidBacktankItem.PULL_MODES);
        this.setChanged();
    }

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        for (int i = 0; i < TANKS; i++) {
            tag.put(TANK_TAGS[i], this.tanks[i].writeToNBT(registries, new CompoundTag()));
        }
        tag.putInt("Feed", this.feedTank);
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        for (int i = 0; i < TANKS; i++) {
            this.tanks[i].setCapacity(FluidBacktankItem.capacityFor(tag.getInt("CapacityEnchantment")));
            this.tanks[i].readFromNBT(registries, tag.getCompound(TANK_TAGS[i]));
        }
        this.feedTank = tag.contains("Feed") ? tag.getInt("Feed") : FluidBacktankItem.PULL_BOTH;
    }

    @Override
    protected void applyImplicitComponents(final DataComponentInput input) {
        super.applyImplicitComponents(input);
        for (int i = 0; i < TANKS; i++) {
            this.tanks[i].setFluid(input.getOrDefault(FluidBacktankItem.component(i).get(), SimpleFluidContent.EMPTY).copy());
        }
        this.feedTank = input.getOrDefault(HoseDataComponents.BACKTANK_FEED.get(), FluidBacktankItem.PULL_BOTH);
    }

    @Override
    protected void collectImplicitComponents(final DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        for (int i = 0; i < TANKS; i++) {
            if (!this.tanks[i].isEmpty()) {
                components.set(FluidBacktankItem.component(i).get(), SimpleFluidContent.copyOf(this.tanks[i].getFluid()));
            }
        }
        if (this.feedTank != FluidBacktankItem.PULL_BOTH) {
            components.set(HoseDataComponents.BACKTANK_FEED.get(), this.feedTank);
        }
    }
}
