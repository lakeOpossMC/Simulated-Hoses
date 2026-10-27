package edn.lakeopossmc.simulatedhoses.backtank;

import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import edn.lakeopossmc.simulatedhoses.HoseDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

// --- PLACED BACKTANK WITH A FLUID TANK --- //
public class FluidBacktankBlockEntity extends BacktankBlockEntity {
    private final FluidTank tank = new FluidTank(FluidBacktankItem.CAPACITY) {
        @Override
        protected void onContentsChanged() {
            FluidBacktankBlockEntity.this.setChanged();
            if (FluidBacktankBlockEntity.this.level != null && !FluidBacktankBlockEntity.this.level.isClientSide) {
                FluidBacktankBlockEntity.this.sendData();
            }
        }
    };

    public FluidBacktankBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable final Direction side) {
        return side == null || side == Direction.DOWN ? this.tank : null;
    }

    // * Capacity enchantments make room for more fluid too
    @Override
    public void setCapacityEnchantLevel(final int capacityEnchantLevel) {
        super.setCapacityEnchantLevel(capacityEnchantLevel);
        this.tank.setCapacity(FluidBacktankItem.capacityFor(capacityEnchantLevel));
    }

    public int getCapacity() {
        return this.tank.getCapacity();
    }

    public FluidStack getFluid() {
        return this.tank.getFluid();
    }

    public void setFluid(final FluidStack fluid) {
        this.tank.setFluid(fluid.copy());
        this.setChanged();
    }

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Tank", this.tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.tank.setCapacity(FluidBacktankItem.capacityFor(tag.getInt("CapacityEnchantment")));
        this.tank.readFromNBT(registries, tag.getCompound("Tank"));
    }

    @Override
    protected void applyImplicitComponents(final DataComponentInput input) {
        super.applyImplicitComponents(input);
        this.tank.setFluid(input.getOrDefault(HoseDataComponents.BACKTANK_FLUID, SimpleFluidContent.EMPTY).copy());
    }

    @Override
    protected void collectImplicitComponents(final DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!this.tank.isEmpty()) {
            components.set(HoseDataComponents.BACKTANK_FLUID, SimpleFluidContent.copyOf(this.tank.getFluid()));
        }
    }
}
