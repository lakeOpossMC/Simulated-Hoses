package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.platform.Window;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

// --- GREEN FILTER OVER THE WORLD --- //
public final class PlantFoodVisionFilter {
    private static final ResourceLocation CHAIN = SimulatedHosesMod.asResource("shaders/post/plant_food.json");
    // * How far toward full green
    private static final float MAX_STRENGTH = 0.45F;

    @Nullable
    private static PostChain chain;
    private static int chainWidth = -1;
    private static int chainHeight = -1;
    // * Stops retrying every frame if the shader couldn't load
    private static boolean failed;

    public static void apply(final float coat, final float partialTick) {
        final PostChain post = chain();
        if (post == null) {
            return;
        }
        post.setUniform("Strength", MAX_STRENGTH * coat);
        post.process(partialTick);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }

    @Nullable
    private static PostChain chain() {
        if (failed) {
            return null;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (chain == null) {
            try {
                chain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), minecraft.getMainRenderTarget(), CHAIN);
            } catch (final Exception e) {
                SimulatedHosesMod.LOGGER.warn("Couldn't load the Plant Food vision shader", e);
                failed = true;
                return null;
            }
            chainWidth = -1;
        }
        final Window window = minecraft.getWindow();
        if (window.getWidth() != chainWidth || window.getHeight() != chainHeight) {
            chainWidth = window.getWidth();
            chainHeight = window.getHeight();
            chain.resize(chainWidth, chainHeight);
        }
        return chain;
    }

    // * Resource reload: drop the shader so it loads fresh from the new resources
    public static void invalidate() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        failed = false;
    }

    private PlantFoodVisionFilter() {
    }
}
