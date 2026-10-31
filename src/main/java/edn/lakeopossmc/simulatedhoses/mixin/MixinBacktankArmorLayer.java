package edn.lakeopossmc.simulatedhoses.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.armor.BacktankArmorLayer;
import edn.lakeopossmc.simulatedhoses.client.FluidBacktankRendering;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- DYED BACKTANKS WHEN WORN --- //
@Mixin(value = BacktankArmorLayer.class, remap = false)
public abstract class MixinBacktankArmorLayer {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void simulatedhoses$dyedBacktank(final PoseStack ms, final MultiBufferSource buffer, final int light, final LivingEntity entity,
                                            final float limbSwing, final float limbSwingAmount, final float partialTick, final float ageInTicks,
                                            final float netHeadYaw, final float headPitch, final CallbackInfo ci) {
        if (entity.getPose() != net.minecraft.world.entity.Pose.SLEEPING
                && FluidBacktankRendering.renderWornDyed(ms, buffer, light, entity, ((RenderLayer<?, ?>) (Object) this).getParentModel())) {
            ci.cancel();
        }
    }
}
