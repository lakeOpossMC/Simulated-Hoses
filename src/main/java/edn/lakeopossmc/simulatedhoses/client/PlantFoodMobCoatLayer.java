package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import edn.lakeopossmc.simulatedhoses.fluids.PlantFoodCoating;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

// --- PLANT FOOD COAT ON MOBS --- //
public class PlantFoodMobCoatLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final LivingEntityRenderer<T, M> renderer;

    public PlantFoodMobCoatLayer(final LivingEntityRenderer<T, M> renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void render(final PoseStack poseStack, final MultiBufferSource buffers, final int light, final T entity,
                       final float limbSwing, final float limbSwingAmount, final float partialTick, final float ageInTicks,
                       final float netHeadYaw, final float headPitch) {
        final float strength = PlantFoodCoating.strength(entity);
        if (strength <= 0.0F || entity.isInvisible()) {
            return;
        }
        final ResourceLocation texture = this.renderer.getTextureLocation(entity);
        if (texture == null) {
            return;
        }
        final PlantFoodGlaze goo = new PlantFoodGlaze(PlantFoodLateBuffers.forNow(buffers), (int) (255 * strength));
        this.getParentModel().renderToBuffer(poseStack, goo.getBuffer(RenderType.entityCutoutNoCull(texture)), light,
                LivingEntityRenderer.getOverlayCoords(entity, 0.0F), -1);
        PlantFoodLayers.cover(this.renderer, this, poseStack, goo, light, entity, limbSwing, limbSwingAmount, partialTick,
                ageInTicks, netHeadYaw, headPitch);
    }
}
