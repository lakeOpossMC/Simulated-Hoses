package edn.lakeopossmc.simulatedhoses.blocks;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import edn.lakeopossmc.simulatedhoses.HoseBlockEntities;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

// --- FLUID QUICK CONNECT --- //
// * A port any hose connector flange locks onto
public class FluidQuickConnectBlock extends DirectionalBlock implements IBE<FluidQuickConnectBlockEntity>, IWrenchable {
    public static final MapCodec<FluidQuickConnectBlock> CODEC = simpleCodec(FluidQuickConnectBlock::new);

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    // * Set by a wrench
    public static final BooleanProperty INVERTED = BooleanProperty.create("inverted");

    private static final VoxelShaper SHAPE = VoxelShaper.forDirectional(Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(2, 3, 2, 14, 7, 14),
            Block.box(3, 7, 3, 13, 9, 13)
    ), Direction.UP);
    private static final VoxelShaper RETRACTED_SHAPE = VoxelShaper.forDirectional(Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(2, 3, 2, 14, 5, 14),
            Block.box(3, 5, 3, 13, 5.1, 13)
    ), Direction.UP);

    public FluidQuickConnectBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.UP).setValue(POWERED, false).setValue(INVERTED, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, INVERTED);
    }

    // * Retracted = no connector can lock on, and any currently locked gets popped off
    public static boolean isRetracted(final BlockState state) {
        return state.getValue(POWERED) != state.getValue(INVERTED);
    }

    //#region // --- PLACEMENT AND POWER --- //
    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getClickedFace())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos, final Block neighborBlock, final BlockPos neighborPos, final boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        final boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered), 3);
            playToggleSound(level, pos, isRetracted(state.setValue(POWERED, powered)));
        }
    }

    // * A wrench swaps the resting state
    @Override
    public InteractionResult onWrenched(final BlockState state, final UseOnContext context) {
        final Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        final BlockPos pos = context.getClickedPos();
        final BlockState flipped = state.cycle(INVERTED);
        level.setBlock(pos, flipped, 3);
        playToggleSound(level, pos, isRetracted(flipped));
        final Player player = context.getPlayer();
        if (player != null) {
            final boolean restsRetracted = flipped.getValue(INVERTED);
            player.displayClientMessage(Component.translatable("block.simulatedhoses.fluid_quick_connect.resting_state")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable("block.simulatedhoses.fluid_quick_connect." + (restsRetracted ? "retracted" : "extended"))
                            .withStyle(restsRetracted ? ChatFormatting.RED : ChatFormatting.GREEN)), true);
        }
        return InteractionResult.SUCCESS;
    }

    private static void playToggleSound(final Level level, final BlockPos pos, final boolean retracted) {
        level.playSound(null, pos, retracted ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.25F, retracted ? 1.6F : 1.8F);
    }
    //#endregion

    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return (isRetracted(state) ? RETRACTED_SHAPE : SHAPE).get(state.getValue(FACING));
    }

    @Override
    protected boolean triggerEvent(final BlockState state, final Level level, final BlockPos pos, final int id, final int param) {
        final BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState, final boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<FluidQuickConnectBlockEntity> getBlockEntityClass() {
        return FluidQuickConnectBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FluidQuickConnectBlockEntity> getBlockEntityType() {
        return HoseBlockEntities.FLUID_QUICK_CONNECT.get();
    }
}
