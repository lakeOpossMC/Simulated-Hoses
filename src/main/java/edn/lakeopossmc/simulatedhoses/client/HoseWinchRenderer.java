package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.content.contraptions.pulley.AbstractPulleyRenderer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import edn.lakeopossmc.simulatedhoses.blocks.HoseWinchBlock;
import edn.lakeopossmc.simulatedhoses.blocks.HoseWinchBlockEntity;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import static com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE;
import static com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING;

// --- HOSE WINCH RENDERER --- //
public class HoseWinchRenderer extends SafeBlockEntityRenderer<HoseWinchBlockEntity> {

    public HoseWinchRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(final HoseWinchBlockEntity be) {
        return true;
    }

    @Override
    public boolean shouldRender(final HoseWinchBlockEntity be, final Vec3 cameraPos) {
        return true;
    }

    @Override
    protected void renderSafe(final HoseWinchBlockEntity be, final float partialTicks, final PoseStack ms, final MultiBufferSource buffer, final int light, final int overlay) {
        final BlockState state = be.getBlockState();
        final Direction shaftEnd = HoseWinchBlock.getShaftDirection(state);
        final Direction.Axis axis = shaftEnd.getAxis();

        final SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, shaftEnd);
        final float angle = KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), axis);
        KineticBlockEntityRenderer.kineticRotationTransform(shaft, be, axis, angle, light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));

        // * While a hose is on the winch
        if (be.getHoseHolder().isAttached()) {
            final SuperByteBuffer coil = CachedBuffers.partial(HosePartialModels.HOSE_WINCH_COIL, state);
            coil.light(light);
            AbstractPulleyRenderer.scrollCoil(coil, AllSpriteShifts.HOSE_PULLEY_COIL, be.getClientAngle(partialTicks), coilScrollDirection(state));
            transform(coil, state).renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }

        HoseStrandRenderer.render(be, be.getHoseHolder(), partialTicks, ms, buffer);
    }

    private static float coilScrollDirection(final BlockState state) {
        final Direction facing = state.getValue(FACING);
        if (facing == Direction.DOWN) {
            return facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? -1.0f : 1.0f;
        }
        return facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE == state.getValue(AXIS_ALONG_FIRST_COORDINATE) ? -1.0f : 1.0f;
    }

    private static SuperByteBuffer transform(final SuperByteBuffer buffer, final BlockState state) {
        final Direction facing = state.getValue(FACING);
        final boolean alongFirst = state.getValue(AXIS_ALONG_FIRST_COORDINATE);

        final float zRotLast = alongFirst ^ facing.getAxis() == Direction.Axis.Z ? 90 : 0;
        final float yRot = AngleHelper.horizontalAngle(facing) + (alongFirst || facing.getAxis() != Direction.Axis.Y ? 0.0f : 90.0f);
        final float zRot = facing == Direction.UP ? 270 : facing == Direction.DOWN ? 90 : 0;

        if (state.getValue(HoseWinchBlock.FLIPPED)) {
            buffer.rotateCentered((float) Math.PI, facing);
        }

        buffer.rotateCentered((float) Math.toRadians(zRot), Direction.SOUTH);
        buffer.rotateCentered((float) Math.toRadians(yRot), Direction.UP);
        buffer.rotateCentered((float) Math.toRadians(zRotLast), Direction.SOUTH);
        return buffer;
    }
}
