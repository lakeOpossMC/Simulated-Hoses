package edn.lakeopossmc.simulatedhoses.mixin;

import com.simibubi.create.content.equipment.armor.NetheriteBacktankFirstPersonRenderer;
import edn.lakeopossmc.simulatedhoses.backtank.FluidBacktankItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- NETHERITE SLEEVES IN FIRST PERSON --- //
// * Create gives the arm a netherite sleeve only for its own netherite backtank, so a netherite fluid backtank gets it too
@Mixin(value = NetheriteBacktankFirstPersonRenderer.class, remap = false)
public abstract class MixinNetheriteBacktankFirstPerson {
    @Shadow
    private static boolean rendererActive;

    @Inject(method = "clientTick", at = @At("TAIL"))
    private static void simulatedhoses$netheriteFluidBacktank(final CallbackInfo ci) {
        final Minecraft mc = Minecraft.getInstance();
        if (!rendererActive && mc.player != null
                && mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof FluidBacktankItem.Netherite) {
            rendererActive = true;
        }
    }
}
