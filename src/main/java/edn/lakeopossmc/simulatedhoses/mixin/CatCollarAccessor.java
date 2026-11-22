package edn.lakeopossmc.simulatedhoses.mixin;

import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// --- LETS DYE FLUIDS RECOLOUR A CAT'S COLLAR --- //
@Mixin(value = Cat.class, remap = false)
public interface CatCollarAccessor {
    @Invoker("setCollarColor")
    void simulatedhoses$setCollarColor(DyeColor color);
}
