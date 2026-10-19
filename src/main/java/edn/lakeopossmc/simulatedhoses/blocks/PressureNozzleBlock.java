package edn.lakeopossmc.simulatedhoses.blocks;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.block.BlockSubLevelCustomCenterOfMass;
import edn.lakeopossmc.simulatedhoses.HoseBlockEntities;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

// --- PRESSURE NOZZLE --- //
// * Sits on the flange of a hose connector
public class PressureNozzleBlock extends DirectionalBlock implements IBE<PressureNozzleBlockEntity>, BlockSubLevelCollisionShape, BlockSubLevelCustomCenterOfMass {
    public static final MapCodec<PressureNozzleBlock> CODEC = simpleCodec(PressureNozzleBlock::new);

    private static final VoxelShaper SHAPE = VoxelShaper.forDirectional(Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(4, 2, 4, 12, 13, 12),
            Block.box(5, 13, 5, 11, 15, 11),
            Block.box(6, 15, 6, 10, 16, 10)
    ), Direction.UP);
    private static final VoxelShaper COLLIDER = VoxelShaper.forDirectional(Shapes.or(
            Block.box(4, 0, 4, 12, 13, 12),
            Block.box(5, 13, 5, 11, 15, 11)
    ), Direction.UP);
    private static final double CENTER_OF_MASS_OFFSET = 0.375;

    public static void registerAttachedCheck() {
        BlockMovementChecks.registerAttachedCheck((state, world, pos, direction) -> {
            if (state.getBlock() instanceof PressureNozzleBlock && state.getValue(FACING).getOpposite() == direction) {
                return BlockMovementChecks.CheckResult.SUCCESS;
            }
            return BlockMovementChecks.CheckResult.PASS;
        });
    }

    public PressureNozzleBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    //#region // --- ONLY ON A CONNECTOR FLANGE --- //
    public static boolean isFlange(final BlockState connectorState, final Direction facing) {
        return connectorState.getBlock() instanceof final HoseConnectorBlock connector
                && connector.getMountDirection(connectorState) == facing;
    }

    @Override
    protected boolean canSurvive(final BlockState state, final LevelReader level, final BlockPos pos) {
        final Direction facing = state.getValue(FACING);
        return isFlange(level.getBlockState(pos.relative(facing.getOpposite())), facing);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        final BlockState state = this.defaultBlockState().setValue(FACING, context.getClickedFace());
        if (!this.canSurvive(state, level, pos)) {
            return null;
        }
        final BlockPos behind = pos.relative(context.getClickedFace().getOpposite());
        if (level.getBlockEntity(behind) instanceof final HoseConnectorBlockEntity connector
                && (connector.getCoupledEndpoint() != null || connector.isCoupledElsewhere() || connector.getQuickConnectPos() != null)) {
            return null;
        }
        return state;
    }

    // * Pops off when the connector is gone
    @Override
    protected BlockState updateShape(final BlockState state, final Direction direction, final BlockState neighborState, final LevelAccessor level, final BlockPos pos, final BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !this.canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
    //#endregion

    @Override
    protected BlockState rotate(final BlockState state, final Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(final BlockState state, final Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(final BlockGetter level, final BlockState state) {
        return COLLIDER.get(state.getValue(FACING));
    }

    @Override
    public Vector3dc getCenterOfMass(final BlockGetter level, final BlockState state) {
        final Direction facing = state.getValue(FACING);
        return new Vector3d(0.5, 0.5, 0.5).add(facing.getStepX() * CENTER_OF_MASS_OFFSET,
                facing.getStepY() * CENTER_OF_MASS_OFFSET, facing.getStepZ() * CENTER_OF_MASS_OFFSET);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState, final boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<PressureNozzleBlockEntity> getBlockEntityClass() {
        return PressureNozzleBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PressureNozzleBlockEntity> getBlockEntityType() {
        return HoseBlockEntities.PRESSURE_NOZZLE.get();
    }
}
