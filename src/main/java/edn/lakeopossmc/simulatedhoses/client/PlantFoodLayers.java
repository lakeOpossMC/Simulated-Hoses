package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SpinAttackEffectLayer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// --- GOO OVER EVERYTHING ELSE DRAWN ON A COATED ENTITY --- //
public final class PlantFoodLayers {
    private static final Set<Class<?>> FAILED_LAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> SEEN_LAYERS = ConcurrentHashMap.newKeySet();
    @Nullable
    private static Field layersField;
    private static boolean layersFailed;

    public static <T extends LivingEntity, M extends EntityModel<T>> void cover(final LivingEntityRenderer<T, M> renderer,
            final RenderLayer<T, M> self, final PoseStack poseStack, final MultiBufferSource goo, final int light, final T entity,
            final float limbSwing, final float limbSwingAmount, final float partialTick, final float ageInTicks,
            final float netHeadYaw, final float headPitch) {
        for (final RenderLayer<T, M> layer : layers(renderer)) {
            if (layer == self || layer instanceof PlantFoodCoatLayer || layer instanceof PlantFoodMobCoatLayer
                    || layer instanceof ItemInHandLayer || layer instanceof StuckInBodyLayer
                    || layer instanceof ParrotOnShoulderLayer || layer instanceof SpinAttackEffectLayer) {
                continue;
            }
            if (SEEN_LAYERS.add(layer.getClass())) {
                SimulatedHosesMod.LOGGER.debug("Plant Food coat: covering layer {}", layer.getClass().getName());
            }
            poseStack.pushPose();
            try {
                layer.render(poseStack, goo, light, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            } catch (final RuntimeException e) {
                if (FAILED_LAYERS.add(layer.getClass())) {
                    SimulatedHosesMod.LOGGER.warn("Plant Food coat can't cover {}", layer.getClass().getName(), e);
                }
            }
            poseStack.popPose();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity, M extends EntityModel<T>> List<RenderLayer<T, M>> layers(final LivingEntityRenderer<T, M> renderer) {
        if (layersFailed) {
            return List.of();
        }
        try {
            if (layersField == null) {
                layersField = LivingEntityRenderer.class.getDeclaredField("layers");
                layersField.setAccessible(true);
            }
            return (List<RenderLayer<T, M>>) layersField.get(renderer);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            SimulatedHosesMod.LOGGER.warn("Plant Food coat can't see render layers, worn items won't get goo", e);
            layersFailed = true;
            return List.of();
        }
    }

    private PlantFoodLayers() {
    }
}
