package edn.lakeopossmc.simulatedhoses.backtank;

import com.simibubi.create.content.equipment.armor.BacktankBlock;
import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import com.simibubi.create.content.equipment.armor.BacktankItem;
import edn.lakeopossmc.simulatedhoses.HoseBlockEntities;
import edn.lakeopossmc.simulatedhoses.HoseDataComponents;
import com.simibubi.create.AllShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// --- PLACED BACKTANK WITH FLUID TANKS --- //
public class FluidBacktankBlock extends BacktankBlock {
    private static final float[][] TANK_BOXES = {{1.0F, 6.0F, 5.0F, 10.0F}, {11.0F, 6.0F, 15.0F, 10.0F}};

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    private final int tankCount;
    private final Supplier<? extends Block> plain;

    public FluidBacktankBlock(final Properties properties, final int tankCount, final Supplier<? extends Block> plain) {
        super(properties);
        this.tankCount = tankCount;
        this.plain = plain;
        for (final Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            VoxelShape shape = AllShapes.BACKTANK;
            for (int tank = 0; tank < tankCount; tank++) {
                shape = Shapes.or(shape, turned(TANK_BOXES[tank], facing));
            }
            this.shapes.put(facing, shape);
        }
    }

    private static VoxelShape turned(final float[] box, final Direction facing) {
        float x0 = box[0];
        float z0 = box[1];
        float x1 = box[2];
        float z1 = box[3];
        for (int turn = 0; turn < (facing.get2DDataValue() + 2) % 4; turn++) {
            final float nx0 = 16.0F - z0;
            final float nz0 = x0;
            final float nx1 = 16.0F - z1;
            final float nz1 = x1;
            x0 = nx0;
            z0 = nz0;
            x1 = nx1;
            z1 = nz1;
        }
        return Block.box(Math.min(x0, x1), 1.0, Math.min(z0, z1), Math.max(x0, x1), 11.0, Math.max(z0, z1));
    }

    public int tankCount() {
        return this.tankCount;
    }

    public Block plain() {
        return this.plain.get();
    }

    @Override
    public String getDescriptionId() {
        return this.plain.get().getDescriptionId();
    }

    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return this.shapes.get(state.getValue(HORIZONTAL_FACING));
    }

    @Override
    public BlockEntityType<? extends BacktankBlockEntity> getBlockEntityType() {
        return HoseBlockEntities.FLUID_BACKTANK.get();
    }

    @Override
    public void setPlacedBy(final Level level, final BlockPos pos, final BlockState state, final LivingEntity placer, final ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || stack == null || !(level.getBlockEntity(pos) instanceof final FluidBacktankBlockEntity be)) {
            return;
        }
        for (int tank = 0; tank < FluidBacktankBlockEntity.TANKS; tank++) {
            be.setFluid(tank, FluidBacktankItem.getFluid(stack, tank));
        }
        be.setFeedTank(stack.getOrDefault(HoseDataComponents.BACKTANK_FEED, FluidBacktankItem.PULL_BOTH));
        be.setComponentPatch(be.getComponentPatch().forget(type -> type == HoseDataComponents.BACKTANK_FLUID.get()
                || type == HoseDataComponents.BACKTANK_SECOND_FLUID.get() || type == HoseDataComponents.BACKTANK_FEED.get()));
    }

    @Override
    public ItemStack getCloneItemStack(final LevelReader level, final BlockPos pos, final BlockState state) {
        final ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof final FluidBacktankBlockEntity be) {
            withFluids(stack, be);
        }
        return stack;
    }

    @Override
    public List<ItemStack> getDrops(final BlockState state, final LootParams.Builder params) {
        final List<ItemStack> drops = super.getDrops(state, params);
        final BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof final FluidBacktankBlockEntity be) {
            for (final ItemStack drop : drops) {
                if (drop.getItem() instanceof BacktankItem) {
                    withFluids(drop, be);
                }
            }
        }
        return drops;
    }

    private static void withFluids(final ItemStack stack, final FluidBacktankBlockEntity be) {
        for (int tank = 0; tank < FluidBacktankBlockEntity.TANKS; tank++) {
            final FluidStack fluid = be.getFluid(tank);
            if (fluid.isEmpty()) {
                stack.remove(FluidBacktankItem.component(tank));
            } else {
                stack.set(FluidBacktankItem.component(tank), SimpleFluidContent.copyOf(fluid));
            }
        }
        if (be.getFeedTank() == FluidBacktankItem.PULL_BOTH) {
            stack.remove(HoseDataComponents.BACKTANK_FEED);
        } else {
            stack.set(HoseDataComponents.BACKTANK_FEED, be.getFeedTank());
        }
    }
}
