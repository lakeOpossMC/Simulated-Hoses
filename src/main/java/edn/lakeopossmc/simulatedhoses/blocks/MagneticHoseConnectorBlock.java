package edn.lakeopossmc.simulatedhoses.blocks;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.block.BlockSubLevelCustomCenterOfMass;
import edn.lakeopossmc.simulatedhoses.HoseBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

// --- MAGNETIC HOSE CONNECTOR --- //
public class MagneticHoseConnectorBlock extends HoseConnectorBlock implements BlockSubLevelCustomCenterOfMass {
    private static final double BODY_CENTER = 0.35;

    public static final MapCodec<MagneticHoseConnectorBlock> CODEC = simpleCodec(MagneticHoseConnectorBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty INVERTED = BooleanProperty.create("inverted");

    public MagneticHoseConnectorBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false).setValue(INVERTED, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED, INVERTED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        final BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    //#region // --- POLARITY --- //
    // * A wrench flips the poles instead of rotating
    @Override
    public InteractionResult onWrenched(final BlockState state, final UseOnContext context) {
        flipPolarity(context.getLevel(), context.getClickedPos(), state, context.getPlayer());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    // * Sneak clicking with an empty hand flips the poles too
    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            flipPolarity(level, pos, state, player);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    private static final int NORTH_COLOR = 0xBE5143;
    private static final int SOUTH_COLOR = 0x5384C3;

    private static void flipPolarity(final Level level, final BlockPos pos, final BlockState state, @Nullable final Player player) {
        if (level.isClientSide()) {
            return;
        }
        final boolean inverted = !state.getValue(INVERTED);
        level.setBlock(pos, state.setValue(INVERTED, inverted), 3);
        level.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 0.6F, inverted ? 1.3F : 1.0F);
        if (player != null) {
            player.displayClientMessage(Component.translatable("block.simulatedhoses.magnetic_hose_connector.polarity")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable("block.simulatedhoses.magnetic_hose_connector.polarity." + (inverted ? "south" : "north"))
                            .withStyle(style -> style.withColor(inverted ? SOUTH_COLOR : NORTH_COLOR))), true);
        }
    }
    //#endregion

    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return HoseShapes.MAGNETIC_CONNECTOR.get(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(final BlockGetter level, final BlockState state) {
        return HoseShapes.MAGNETIC_COLLIDER.get(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
    }

    @Override
    public Vector3dc getCenterOfMass(final BlockGetter level, final BlockState state) {
        final Direction base = state.getValue(FACING).getOpposite();
        return new Vector3d(0.5, 0.5, 0.5).add(base.getStepX() * BODY_CENTER, base.getStepY() * BODY_CENTER, base.getStepZ() * BODY_CENTER);
    }

    @Override
    public BlockEntityType<? extends HoseConnectorBlockEntity> getBlockEntityType() {
        return HoseBlockEntities.MAGNETIC_HOSE_CONNECTOR.get();
    }
}
