package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.systems.RenderSystem;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import edn.lakeopossmc.simulatedhoses.fluids.PlantFoodCoating;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// --- GREEN VISION WHILE COATED --- //
public final class PlantFoodVisionOverlay {
    private static final ResourceLocation OUTLINE = SimulatedHosesMod.asResource("textures/misc/plant_food_outline.png");

    public static void register(final RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, SimulatedHosesMod.asResource("plant_food_vision"), PlantFoodVisionOverlay::render);
    }

    private static void render(final GuiGraphics graphics, final DeltaTracker delta) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        final float strength = PlantFoodCoating.strength(minecraft.player);
        if (strength <= 0.0F) {
            return;
        }
        // * Finish anything already queued, green the world under it, then draw the goo on top
        graphics.flush();
        PlantFoodVisionFilter.apply(strength, delta.getGameTimeDeltaPartialTick(false));

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, strength);
        graphics.blit(OUTLINE, 0, 0, -90, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(), graphics.guiHeight());
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private PlantFoodVisionOverlay() {
    }
}
