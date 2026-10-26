package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import edn.lakeopossmc.simulatedhoses.items.SuperSoakerItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

// --- FLUID IN THE SUPER SOAKER'S BODY --- //
public final class SuperSoakerFluidRenderer {
    private static final float INSET = 0.02F;
    private static final float X0 = 6.0F + INSET;
    private static final float X1 = 10.0F - INSET;
    private static final float Y0 = 6.5F + INSET;
    private static final float Y1 = 10.5F - INSET;
    private static final float Z0 = 9.5F + INSET;
    private static final float Z1 = 15.5F - INSET;
    private static final float MIN_HEIGHT = 0.5F;
    private static final float TILE = 8.0F;

    // * With the soaker model centred on the pose
    public static void render(final ItemStack stack, final PoseStack ms, final MultiBufferSource buffer, final int light) {
        ms.pushPose();
        ms.translate(-0.5F, -0.5F, -0.5F);
        WindowFluidRenderer.render(SuperSoakerItem.getFluid(stack), SuperSoakerItem.CAPACITY, X0, Y0, Z0, X1, Y1, Z1, MIN_HEIGHT, TILE,
                ms, buffer, light);
        ms.popPose();
    }

    private SuperSoakerFluidRenderer() {
    }
}
