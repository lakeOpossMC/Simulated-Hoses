package edn.lakeopossmc.simulatedhoses.mixin;

import edn.lakeopossmc.simulatedhoses.fluids.Dyeing;
import edn.lakeopossmc.simulatedhoses.fluids.PaintColors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- DYE FLUIDS COLOUR WHAT THEY TOUCH --- //
@Mixin(value = FlowingFluid.class, remap = false)
public abstract class MixinFlowingFluidDye {

    @Inject(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;)V",
            at = @At("TAIL"))
    private void simulatedhoses$dyeTouching(final Level level, final BlockPos pos, final FluidState state, final CallbackInfo ci) {
        if (level instanceof final ServerLevel serverLevel) {
            final DyeColor color = PaintColors.of(state.getType());
            if (color != null) {
                Dyeing.dyeTouching(serverLevel, pos, color);
            }
        }
    }
}
