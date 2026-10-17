package edn.lakeopossmc.simulatedhoses.mixin;

import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import edn.lakeopossmc.simulatedhoses.hose.HoseHolderBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// --- KEEPS THE ROPE COUPLING OFF HOSE BLOCKS --- //
@Mixin(value = RopeItem.class, remap = false)
public abstract class MixinRopeItem {

    @Inject(method = "isValidRopeAttachment", at = @At("RETURN"), cancellable = true)
    private static void simulatedhoses$rejectHoseHolders(final Level level, final BlockPos blockPos, final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && HoseHolderBehavior.get(level, blockPos) != null) {
            cir.setReturnValue(false);
        }
    }
}
