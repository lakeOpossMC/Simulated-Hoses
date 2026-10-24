package edn.lakeopossmc.simulatedhoses.mixin;

import edn.lakeopossmc.simulatedhoses.fluids.PlantFoodCoating;
import edn.lakeopossmc.simulatedhoses.fluids.PlantFoodSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- PLANT FOOD FOOTSTEPS --- //
@Mixin(value = Entity.class, remap = false)
public abstract class MixinEntityStepSound {

    @Inject(method = "playStepSound", at = @At("TAIL"))
    private void simulatedhoses$plantFoodStep(final BlockPos pos, final BlockState state, final CallbackInfo ci) {
        final Entity self = (Entity) (Object) this;
        if (PlantFoodCoating.isCoated(self)) {
            PlantFoodSounds.footstep(self);
        }
    }
}
