package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import edn.lakeopossmc.simulatedhoses.blocks.HoseConnectorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;

// --- ONLY DRAWS THE HOSE --- //
public class HoseConnectorRenderer extends SafeBlockEntityRenderer<HoseConnectorBlockEntity> {

    public HoseConnectorRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(final HoseConnectorBlockEntity be) {
        return true;
    }

    @Override
    public boolean shouldRender(final HoseConnectorBlockEntity be, final Vec3 cameraPos) {
        return true;
    }

    @Override
    protected void renderSafe(final HoseConnectorBlockEntity be, final float partialTicks, final PoseStack ms, final MultiBufferSource buffer, final int light, final int overlay) {
        HoseStrandRenderer.render(be, be.getHoseHolder(), partialTicks, ms, buffer);
    }
}
