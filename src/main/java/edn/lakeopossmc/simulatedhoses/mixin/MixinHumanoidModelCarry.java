package edn.lakeopossmc.simulatedhoses.mixin;

import edn.lakeopossmc.simulatedhoses.client.HoseCarryArms;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- ARMS OUT WHILE CARRYING --- //
@Mixin(value = HumanoidModel.class, remap = false)
public abstract class MixinHumanoidModelCarry {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void simulatedhoses$carryPose(final LivingEntity entity, final float limbSwing, final float limbSwingAmount,
                                          final float ageInTicks, final float netHeadYaw, final float headPitch, final CallbackInfo ci) {
        if (entity instanceof final Player player) {
            HoseCarryArms.pose(player, (HumanoidModel<?>) (Object) this);
        }
    }
}
