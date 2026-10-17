package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import edn.lakeopossmc.simulatedhoses.blocks.FluidQuickConnectBlock;
import edn.lakeopossmc.simulatedhoses.blocks.FluidQuickConnectBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

// --- FLUID QUICK CONNECT RENDERER --- //
public class FluidQuickConnectRenderer extends SafeBlockEntityRenderer<FluidQuickConnectBlockEntity> {

    public FluidQuickConnectRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(final FluidQuickConnectBlockEntity be, final float partialTicks, final PoseStack ms, final MultiBufferSource buffer, final int light, final int overlay) {
        final BlockState state = be.getBlockState();
        final boolean powered = state.getValue(FluidQuickConnectBlock.POWERED);
        final Direction facing = state.getValue(FluidQuickConnectBlock.FACING);

        render(powered ? HosePartialModels.QUICK_CONNECT_EXTENDER_POWERED : HosePartialModels.QUICK_CONNECT_EXTENDER,
                state, facing, be.getExtenderOffset(partialTicks), light, ms, buffer);
        render(powered ? HosePartialModels.QUICK_CONNECT_PORT_POWERED : HosePartialModels.QUICK_CONNECT_PORT,
                state, facing, be.getPortOffset(partialTicks), light, ms, buffer);
    }

    private static void render(final PartialModel partial, final BlockState state, final Direction facing, final float pixels,
                               final int light, final PoseStack ms, final MultiBufferSource buffer) {
        final float offset = pixels / 16.0F;
        final SuperByteBuffer model = CachedBuffers.partial(partial, state);
        model.translate(facing.getStepX() * offset, facing.getStepY() * offset, facing.getStepZ() * offset);
        model.rotateCentered(facing.getRotation());
        model.light(light).renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
    }
}
