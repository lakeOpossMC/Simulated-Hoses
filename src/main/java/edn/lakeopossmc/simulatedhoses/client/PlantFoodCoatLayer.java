package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import edn.lakeopossmc.simulatedhoses.fluids.PlantFoodCoating;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// --- PLANT FOOD COAT ON PLAYERS --- //
public class PlantFoodCoatLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final int TINT_RED = 0x5A;
    private static final int TINT_GREEN = 0xC8;
    private static final int TINT_BLUE = 0x3C;
    // * How strongly the green covers the skin when freshly sprayed
    private static final int MAX_ALPHA = 170;
    private static final ResourceLocation COAT = SimulatedHosesMod.asResource("textures/entity/plant_food_coat.png");

    private final LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer;

    public PlantFoodCoatLayer(final LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
        this.renderer = parent;
    }

    @Override
    public void render(final PoseStack poseStack, final MultiBufferSource buffers, final int light, final AbstractClientPlayer player,
                       final float limbSwing, final float limbSwingAmount, final float partialTick, final float ageInTicks,
                       final float netHeadYaw, final float headPitch) {
        final float strength = PlantFoodCoating.strength(player);
        if (strength <= 0.0F || player.isInvisible()) {
            return;
        }
        final int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        final int fade = FastColor.ARGB32.color((int) (255 * strength), 255, 255, 255);

        final VertexConsumer tint = buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
        this.getParentModel().renderToBuffer(poseStack, tint, light, overlay,
                FastColor.ARGB32.color((int) (MAX_ALPHA * strength), TINT_RED, TINT_GREEN, TINT_BLUE));
        final VertexConsumer coat = buffers.getBuffer(RenderType.entityTranslucent(COAT));
        this.getParentModel().renderToBuffer(poseStack, coat, light, overlay, fade);

        this.renderFilm(poseStack, buffers, light, player, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch, strength);
    }

    private void renderFilm(final PoseStack poseStack, final MultiBufferSource buffers, final int light, final AbstractClientPlayer player,
                            final float limbSwing, final float limbSwingAmount, final float partialTick, final float ageInTicks,
                            final float netHeadYaw, final float headPitch, final float strength) {
        final PlantFoodGlaze film = new PlantFoodGlaze(PlantFoodLateBuffers.forNow(buffers), (int) (255 * strength));
        PlantFoodLayers.cover(this.renderer, this, poseStack, film, light, player, limbSwing, limbSwingAmount, partialTick,
                ageInTicks, netHeadYaw, headPitch);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayers(final EntityRenderersEvent.AddLayers event) {
        for (final PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof final PlayerRenderer renderer) {
                renderer.addLayer(new PlantFoodCoatLayer(renderer));
            }
        }
        // * Every mob, and anything else living, gets the general coat
        for (final EntityType<?> type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof final LivingEntityRenderer living) {
                living.addLayer(new PlantFoodMobCoatLayer(living));
            }
        }
    }
}
