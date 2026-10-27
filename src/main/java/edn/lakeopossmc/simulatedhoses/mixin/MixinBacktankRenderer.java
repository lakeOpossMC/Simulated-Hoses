package edn.lakeopossmc.simulatedhoses.mixin;

import com.simibubi.create.content.equipment.armor.BacktankBlock;
import com.simibubi.create.content.equipment.armor.BacktankRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import edn.lakeopossmc.simulatedhoses.backtank.FluidBacktankBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// --- FLUID BACKTANKS SPIN THEIR OWN KIND OF COGS --- //
// * Create picks copper or netherite cogs and shaft by its own blocks, so a fluid backtank asks as the backtank it's
// * built on. Covers the placed block, its shaft visual and the worn backtank
@Mixin(value = BacktankRenderer.class, remap = false)
public abstract class MixinBacktankRenderer {

    @Inject(method = "getCogsModel", at = @At("HEAD"), cancellable = true)
    private static void simulatedhoses$fluidBacktankCogs(final BlockState state, final CallbackInfoReturnable<PartialModel> cir) {
        if (state.getBlock() instanceof final FluidBacktankBlock block) {
            cir.setReturnValue(BacktankRenderer.getCogsModel(plain(block, state)));
        }
    }

    @Inject(method = "getShaftModel", at = @At("HEAD"), cancellable = true)
    private static void simulatedhoses$fluidBacktankShaft(final BlockState state, final CallbackInfoReturnable<PartialModel> cir) {
        if (state.getBlock() instanceof final FluidBacktankBlock block) {
            cir.setReturnValue(BacktankRenderer.getShaftModel(plain(block, state)));
        }
    }

    private static BlockState plain(final FluidBacktankBlock block, final BlockState state) {
        return block.plain().defaultBlockState().setValue(BacktankBlock.HORIZONTAL_FACING, state.getValue(BacktankBlock.HORIZONTAL_FACING));
    }
}
