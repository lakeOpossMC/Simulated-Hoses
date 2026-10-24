package edn.lakeopossmc.simulatedhoses.client;

import edn.lakeopossmc.simulatedhoses.hose.HoseCarryPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

// --- ARMS REACHING FOR WHAT'S CARRIED --- //
public final class HoseCarryArms {
    // * Arms point this far below the gaze
    private static final float CONNECTOR_DROP = (float) Math.toRadians(8);
    private static final float CONNECTOR_INWARD = (float) Math.toRadians(14);
    private static final float NOZZLE_MAIN_DROP = (float) Math.toRadians(10);
    private static final float NOZZLE_MAIN_OUTWARD = (float) Math.toRadians(6);
    private static final float NOZZLE_OFF_DROP = (float) Math.toRadians(18);
    private static final float NOZZLE_OFF_INWARD = (float) Math.toRadians(38);

    public static void pose(final Player player, final HumanoidModel<?> model) {
        final Boolean nozzle = HoseCarryPose.carryingNozzle(player.getUUID());
        if (nozzle == null) {
            return;
        }
        final float pitch = model.head.xRot - Mth.HALF_PI;
        final float yaw = model.head.yRot;
        if (!nozzle) {
            aim(model.rightArm, pitch + CONNECTOR_DROP, yaw - CONNECTOR_INWARD);
            aim(model.leftArm, pitch + CONNECTOR_DROP, yaw + CONNECTOR_INWARD);
            return;
        }
        final boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        final ModelPart main = right ? model.rightArm : model.leftArm;
        final ModelPart off = right ? model.leftArm : model.rightArm;
        final float side = right ? 1.0F : -1.0F;
        aim(main, pitch + NOZZLE_MAIN_DROP, yaw + side * NOZZLE_MAIN_OUTWARD);
        aim(off, pitch + NOZZLE_OFF_DROP, yaw + side * NOZZLE_OFF_INWARD);
    }

    private static void aim(final ModelPart arm, final float xRot, final float yRot) {
        arm.xRot = xRot;
        arm.yRot = yRot;
        arm.zRot = 0.0F;
    }

    private HoseCarryArms() {
    }
}
