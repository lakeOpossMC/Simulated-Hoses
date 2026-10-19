package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import edn.lakeopossmc.simulatedhoses.blocks.PressureNozzleBlock;
import edn.lakeopossmc.simulatedhoses.blocks.PressureNozzleBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

// --- PRESSURE NOZZLE RENDERER --- //
public class PressureNozzleRenderer extends SafeBlockEntityRenderer<PressureNozzleBlockEntity> {

    public PressureNozzleRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(final PressureNozzleBlockEntity be, final float partialTicks, final PoseStack ms, final MultiBufferSource buffer, final int light, final int overlay) {
        final BlockState state = be.getBlockState();
        final Quaternionf facing = modelRotation(state.getValue(PressureNozzleBlock.FACING));
        final VertexConsumer consumer = buffer.getBuffer(RenderType.cutoutMipped());

        CachedBuffers.partial(HosePartialModels.PRESSURE_NOZZLE_COG, state)
                .rotateCentered(facing)
                .rotateCentered((float) Math.toRadians(be.getCogAngle(partialTicks)), Direction.SOUTH)
                .light(light)
                .renderInto(ms, consumer);
        CachedBuffers.partial(HosePartialModels.PRESSURE_NOZZLE_NOZZLE, state)
                .rotateCentered(facing)
                .translate(0.0F, 0.0F, -be.getNozzleOffset(partialTicks) / 16.0F)
                .light(light)
                .renderInto(ms, consumer);
        CachedBuffers.partial(HosePartialModels.PRESSURE_NOZZLE_TIP, state)
                .rotateCentered(facing)
                .translate(0.0F, 0.0F, -be.getTipOffset(partialTicks) / 16.0F)
                .light(light)
                .renderInto(ms, consumer);
    }

    private static Quaternionf modelRotation(final Direction facing) {
        final float x = switch (facing) {
            case UP -> 90.0F;
            case DOWN -> 270.0F;
            default -> 0.0F;
        };
        final float y = switch (facing) {
            case NORTH -> 180.0F;
            case EAST -> 270.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
        return new Quaternionf().rotationYXZ((float) Math.toRadians(-y), (float) Math.toRadians(-x), 0.0F);
    }
}
