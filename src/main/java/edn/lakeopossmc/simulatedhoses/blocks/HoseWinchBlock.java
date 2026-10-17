package edn.lakeopossmc.simulatedhoses.blocks;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.simulated_team.simulated.content.blocks.rope.RopeHolderBlock;
import dev.simulated_team.simulated.index.SimTags;
import edn.lakeopossmc.simulatedhoses.HoseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// --- HOSE WINCH --- //
public class HoseWinchBlock extends DirectionalAxisKineticBlock implements IBE<HoseWinchBlockEntity>, RopeHolderBlock<HoseWinchBlockEntity>, BlockSubLevelCollisionShape {
    public static final BooleanProperty MAGNETIC = BooleanProperty.create("magnetic");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty FLIPPED = BooleanProperty.create("flipped");

    public static void registerAttachedCheck() {
        BlockMovementChecks.registerAttachedCheck((state, world, pos, direction) -> {
            if (state.getBlock() instanceof HoseWinchBlock && state.getValue(FACING) == direction.getOpposite()) {
                return BlockMovementChecks.CheckResult.SUCCESS;
            }
            final BlockState relativeState = world.getBlockState(pos.relative(direction));
            if (relativeState.getBlock() instanceof HoseWinchBlock && relativeState.getValue(FACING) == direction) {
                return BlockMovementChecks.CheckResult.SUCCESS;
            }
            return BlockMovementChecks.CheckResult.PASS;
        });
    }

    public HoseWinchBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(MAGNETIC, false).setValue(POWERED, false).setValue(FLIPPED, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MAGNETIC, POWERED, FLIPPED);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        final Direction facing = state.getValue(FACING);
        for (final Direction looking : context.getNearestLookingDirections()) {
            if (looking.getAxis() != facing.getAxis()) {
                final boolean sneaking = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
                state = withPipe(state, facing, sneaking ? looking : looking.getOpposite());
                break;
            }
        }
        return state.setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos, final Block block, final BlockPos fromPos, final boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) {
            return;
        }
        final boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), 2);
        }
    }

    //#region // --- SHAFT AND PIPE ENDS --- //
    public static Direction getPipeDirection(final BlockState state) {
        final Direction pipe = unflippedPipe(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
        return state.getValue(FLIPPED) ? pipe.getOpposite() : pipe;
    }

    private static Direction unflippedPipe(final Direction facing, final boolean alongFirst) {
        return switch (facing) {
            case DOWN, UP -> alongFirst ? Direction.WEST : Direction.NORTH;
            case NORTH -> alongFirst ? Direction.EAST : Direction.UP;
            case SOUTH -> alongFirst ? Direction.WEST : Direction.UP;
            case EAST -> alongFirst ? Direction.UP : Direction.SOUTH;
            case WEST -> alongFirst ? Direction.UP : Direction.NORTH;
        };
    }

    public static BlockState withPipe(final BlockState state, final Direction facing, final Direction pipe) {
        for (final boolean alongFirst : new boolean[]{false, true}) {
            final Direction unflipped = unflippedPipe(facing, alongFirst);
            if (unflipped == pipe || unflipped == pipe.getOpposite()) {
                return state.setValue(FACING, facing)
                        .setValue(AXIS_ALONG_FIRST_COORDINATE, alongFirst)
                        .setValue(FLIPPED, unflipped != pipe);
            }
        }
        return state.setValue(FACING, facing);
    }

    @Override
    public BlockState getRotatedBlockState(final BlockState originalState, final Direction targetedFace) {
        final Direction facing = originalState.getValue(FACING);
        final Direction pipe = getPipeDirection(originalState);
        final Direction.Axis axis = targetedFace.getAxis();
        final Direction newFacing = facing.getAxis() == axis ? facing : facing.getClockWise(axis);
        return withPipe(originalState, newFacing, pipe.getAxis() == axis ? pipe : pipe.getClockWise(axis));
    }

    @Override
    public BlockState rotate(final BlockState state, final Rotation rotation) {
        return withPipe(state, rotation.rotate(state.getValue(FACING)), rotation.rotate(getPipeDirection(state)));
    }

    @Override
    public BlockState mirror(final BlockState state, final Mirror mirror) {
        return withPipe(state, mirror.mirror(state.getValue(FACING)), mirror.mirror(getPipeDirection(state)));
    }

    public static Direction getShaftDirection(final BlockState state) {
        return getPipeDirection(state).getOpposite();
    }

    @Override
    public boolean hasShaftTowards(final LevelReader level, final BlockPos pos, final BlockState state, final Direction face) {
        return face == getShaftDirection(state);
    }
    //#endregion

    @Override
    protected Direction getFacingForPlacement(final BlockPlaceContext context) {
        return context.getClickedFace();
    }

    @Override
    protected boolean getAxisAlignmentForPlacement(final BlockPlaceContext context) {
        return context.getHorizontalDirection().getAxis() != Direction.Axis.X;
    }

    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return HoseShapes.WINCH.get(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(final BlockGetter level, final BlockState state) {
        return HoseShapes.THIN_COLLIDER.get(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level, final BlockPos pos, final Player player, final InteractionHand hand, final BlockHitResult hitResult) {
        if (!level.isClientSide() && stack.is(SimTags.Items.DESTROYS_ROPE)) {
            return RopeHolderBlock.shearRope(this, level, pos, (ServerPlayer) player);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    public Class<HoseWinchBlockEntity> getBlockEntityClass() {
        return HoseWinchBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HoseWinchBlockEntity> getBlockEntityType() {
        return HoseBlockEntities.HOSE_WINCH.get();
    }
}
