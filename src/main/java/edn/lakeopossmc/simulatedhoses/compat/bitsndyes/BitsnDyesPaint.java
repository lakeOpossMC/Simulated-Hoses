package edn.lakeopossmc.simulatedhoses.compat.bitsndyes;

import com.kipti.bnb.content.decoration.dyeable.BaseDyeableBehaviour;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

// --- DYE FLUIDS ON BITS 'N' DYES BLOCKS --- //
public final class BitsnDyesPaint {
    // * Dyeing goes through the same step as right-clicking with a dye
    private static final Method DYE = find();

    public static boolean dye(final BlockEntity be, final DyeColor color) {
        if (!(be instanceof final SmartBlockEntity smart)) {
            return false;
        }
        for (final BlockEntityBehaviour behaviour : smart.getAllBehaviours()) {
            if (behaviour instanceof final BaseDyeableBehaviour dyeable) {
                if (!dyeable.isDyeingEnabled()) {
                    return false;
                }
                if (dyeable.getColor() != color) {
                    apply(dyeable, color);
                }
                return true;
            }
        }
        return false;
    }

    private static void apply(final BaseDyeableBehaviour dyeable, final DyeColor color) {
        if (DYE != null) {
            try {
                DYE.invoke(dyeable, color, false);
                return;
            } catch (final ReflectiveOperationException ignored) {
            }
        }
        if (!(dyeable.blockEntity instanceof IMultiBlockEntityContainer)) {
            dyeable.setColor(color);
        }
    }

    private static Method find() {
        try {
            final Method method = BaseDyeableBehaviour.class.getDeclaredMethod("dye", DyeColor.class, boolean.class);
            method.setAccessible(true);
            return method;
        } catch (final ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private BitsnDyesPaint() {
    }
}
