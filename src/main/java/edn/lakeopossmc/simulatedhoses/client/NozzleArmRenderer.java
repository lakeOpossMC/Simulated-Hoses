package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.neoforge.client.event.RenderHandEvent;

// --- FIRST PERSON ARM WHILE CARRYING A NOZZLE --- //
public final class NozzleArmRenderer {
    private static final float REACH_IN = -0.06F;
    private static final float REACH_UP = 0.005F;
    private static final float REACH_OUT = -0.05F;
    private static final float REACH_TILT = 5.0F;
    private static final float REACH_YAW = 10.0F;

    private static final float GRAB_REACH = 1.0F / 6.0F;
    private static final float GRIP = 0.0F;
    private static final float GRAB_TICKS = 3.0F;
    private static final float SETTLE_TICKS = 5.0F;

    public static void render(final RenderHandEvent event, final boolean carryingNozzle, final float heldTicks) {
        if (!carryingNozzle || event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || player.isInvisible()
                || !(minecraft.getEntityRenderDispatcher().getRenderer(player) instanceof final PlayerRenderer renderer)) {
            return;
        }
        event.setCanceled(true);

        final boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        final float side = right ? 1.0F : -1.0F;
        final float equip = event.getEquipProgress();
        final float swing = grabSwing(heldTicks);
        final PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        poseStack.translate(side * -REACH_IN, REACH_UP, -REACH_OUT);
        poseStack.mulPose(Axis.XP.rotationDegrees(REACH_TILT));

        final float swingRoot = Mth.sqrt(swing);
        final float swingX = -0.3F * Mth.sin(swingRoot * Mth.PI);
        final float swingY = 0.4F * Mth.sin(swingRoot * Mth.TWO_PI);
        final float swingZ = -0.4F * Mth.sin(swing * Mth.PI);
        poseStack.translate(side * (swingX + 0.64000005F), swingY - 0.6F + equip * -0.6F, swingZ - 0.71999997F);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0F + REACH_YAW)));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * Mth.sin(swingRoot * Mth.PI) * 70.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * Mth.sin(swing * swing * Mth.PI) * -20.0F));
        poseStack.translate(side * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -135.0F));
        poseStack.translate(side * 5.6F, 0.0F, 0.0F);
        if (right) {
            renderer.renderRightHand(poseStack, event.getMultiBufferSource(), event.getPackedLight(), player);
        } else {
            renderer.renderLeftHand(poseStack, event.getMultiBufferSource(), event.getPackedLight(), player);
        }
        poseStack.popPose();
    }

    private static float grabSwing(final float heldTicks) {
        if (heldTicks < GRAB_TICKS) {
            final float t = heldTicks / GRAB_TICKS;
            return GRAB_REACH * (1.0F - (1.0F - t) * (1.0F - t));
        }
        final float t = Mth.clamp((heldTicks - GRAB_TICKS) / SETTLE_TICKS, 0.0F, 1.0F);
        final float eased = t * t * (3.0F - 2.0F * t);
        return Mth.lerp(eased, GRAB_REACH, GRIP);
    }

    private NozzleArmRenderer() {
    }
}
