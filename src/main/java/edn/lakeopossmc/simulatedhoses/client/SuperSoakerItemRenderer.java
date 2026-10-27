package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// --- SUPER SOAKER MODEL --- //
public class SuperSoakerItemRenderer extends CustomRenderedItemModelRenderer {
    private static final float IDLE_SPIN = -2.5F;
    private static final float COG_CENTER = 0.5F / 16.0F;

    @Override
    protected void render(final ItemStack stack, final CustomRenderedItemModel model, final PartialItemModelRenderer renderer,
                          final ItemDisplayContext transformType, final PoseStack ms, final MultiBufferSource buffer, final int light, final int overlay) {
        renderer.render(HoseDyedParts.dye(model.getOriginalModel(), stack), light);
        SuperSoakerFluidRenderer.render(stack, ms, buffer, light);
        BacktankHoseRenderer.soakerDrawn(stack, transformType, ms, buffer, light);
        SuperSoakerClient.tipDrawn(stack, transformType, ms);

        final float partialTicks = AnimationTickHolder.getPartialTicks();
        final SuperSoakerClient.Animation animation = SuperSoakerClient.animationFor(stack, transformType);
        float angle = AnimationTickHolder.getRenderTime() * IDLE_SPIN;
        float rubber = 0.0F;
        float tip = 0.0F;
        if (animation != null) {
            angle -= animation.getSpin(partialTicks);
            rubber = animation.getRubber(partialTicks);
            tip = animation.getTip(partialTicks);
        }
        angle %= 360.0F;

        ms.pushPose();
        ms.translate(0.0F, COG_CENTER, 0.0F);
        ms.mulPose(Axis.ZP.rotationDegrees(angle));
        ms.translate(0.0F, -COG_CENTER, 0.0F);
        renderer.render(HoseDyedParts.dye(HosePartialModels.SUPER_SOAKER_COG.get(), stack), light);
        ms.popPose();

        ms.pushPose();
        ms.translate(0.0F, 0.0F, rubber / 16.0F);
        renderer.render(HoseDyedParts.dye(HosePartialModels.SUPER_SOAKER_RUBBER.get(), stack), light);
        ms.popPose();

        ms.pushPose();
        ms.translate(0.0F, 0.0F, tip / 16.0F);
        renderer.render(HoseDyedParts.dye(HosePartialModels.SUPER_SOAKER_TIP.get(), stack), light);
        ms.popPose();
    }
}
