package edn.lakeopossmc.simulatedhoses.blocks;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.simulated_team.simulated.content.blocks.rope.RopeHolderBlock;
import dev.simulated_team.simulated.content.blocks.util.AbstractDirectionalAxisBlock;
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
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// --- HOSE CONNECTOR --- //
public class HoseConnectorBlock extends AbstractDirectionalAxisBlock implements IBE<HoseConnectorBlockEntity>, RopeHolderBlock<HoseConnectorBlockEntity>, BlockSubLevelCollisionShape {
    public static final MapCodec<HoseConnectorBlock> CODEC = simpleCodec(HoseConnectorBlock::new);

    public static void registerAttachedCheck() {
        BlockMovementChecks.registerAttachedCheck((state, world, pos, direction) -> {
            if (state.getBlock() instanceof final HoseConnectorBlock connector && connector.getMountDirection(state) == direction) {
                return BlockMovementChecks.CheckResult.SUCCESS;
            }
            final BlockState relativeState = world.getBlockState(pos.relative(direction));
            if (relativeState.getBlock() instanceof final HoseConnectorBlock connector && connector.getMountDirection(relativeState) == direction.getOpposite()) {
                return BlockMovementChecks.CheckResult.SUCCESS;
            }
            return BlockMovementChecks.CheckResult.PASS;
        });
    }

    public HoseConnectorBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    // * Side the block is stuck to
    public Direction getMountDirection(final BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

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
        return HoseShapes.CONNECTOR.get(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(final BlockGetter level, final BlockState state) {
        return HoseShapes.THIN_COLLIDER.get(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
    }

    @Override
    public void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState, final boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    // * Shears and the like cut the hose
    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level, final BlockPos pos, final Player player, final InteractionHand hand, final BlockHitResult hitResult) {
        if (!level.isClientSide() && stack.is(SimTags.Items.DESTROYS_ROPE)) {
            return RopeHolderBlock.shearRope(this, level, pos, (ServerPlayer) player);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    public Class<HoseConnectorBlockEntity> getBlockEntityClass() {
        return HoseConnectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HoseConnectorBlockEntity> getBlockEntityType() {
        return HoseBlockEntities.HOSE_CONNECTOR.get();
    }
}
