package edn.lakeopossmc.simulatedhoses.mixin;

import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// --- LETS DYE FLUIDS RECOLOUR A WOLF'S COLLAR --- //
@Mixin(value = Wolf.class, remap = false)
public interface WolfCollarAccessor {
    @Invoker("setCollarColor")
    void simulatedhoses$setCollarColor(DyeColor color);
}
