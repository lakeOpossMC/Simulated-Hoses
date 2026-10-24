package edn.lakeopossmc.simulatedhoses.client;

import edn.lakeopossmc.simulatedhoses.HoseFluids;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

// --- HOW PLANT FOOD LOOKS --- //
public final class PlantFoodClient {
    private static final ResourceLocation STILL = SimulatedHosesMod.asResource("fluid/plant_food_still");
    private static final ResourceLocation FLOW = SimulatedHosesMod.asResource("fluid/plant_food_flow");
    private static final ResourceLocation OVERLAY = ResourceLocation.withDefaultNamespace("block/water_overlay");
    private static final Vector3f FOG_COLOR = new Vector3f(0x3a / 255F, 0x6e / 255F, 0x2a / 255F);
    private static final float FOG_END = 0.5F;

    public static void registerExtensions(final RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public @NotNull ResourceLocation getStillTexture() {
                return STILL;
            }

            @Override
            public @NotNull ResourceLocation getFlowingTexture() {
                return FLOW;
            }

            @Override
            public ResourceLocation getOverlayTexture() {
                return OVERLAY;
            }

            @Override
            public int getTintColor() {
                return 0xFFFFFFFF;
            }

            @Override
            public @NotNull Vector3f modifyFogColor(final Camera camera, final float partialTick, final ClientLevel level,
                                                    final int renderDistance, final float darkenWorldAmount, final Vector3f fluidFogColor) {
                return new Vector3f(FOG_COLOR);
            }

            @Override
            public void modifyFogRender(final Camera camera, final FogRenderer.FogMode mode, final float renderDistance,
                                        final float partialTick, final float nearDistance, final float farDistance, final FogShape shape) {
                RenderSystem.setShaderFogStart(0.0F);
                RenderSystem.setShaderFogEnd(FOG_END);
            }
        }, HoseFluids.PLANT_FOOD_TYPE.get());
    }

    public static void setRenderLayers() {
        ItemBlockRenderTypes.setRenderLayer(HoseFluids.PLANT_FOOD.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(HoseFluids.FLOWING_PLANT_FOOD.get(), RenderType.translucent());
    }

    private PlantFoodClient() {
    }
}
