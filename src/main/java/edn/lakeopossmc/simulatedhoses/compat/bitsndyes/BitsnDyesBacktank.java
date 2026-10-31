package edn.lakeopossmc.simulatedhoses.compat.bitsndyes;

import com.cake.azimuth.registration.BehaviourApplicators;
import com.cake.bnd.foundation.create.BndFlaggedDyeableBehaviour;
import com.cake.bnd.registry.core.BndConfigs;
import com.kipti.bnb.content.decoration.dyeable.simple.SimpleDyeableBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import edn.lakeopossmc.simulatedhoses.HoseBlockEntities;
import edn.lakeopossmc.simulatedhoses.HoseBlocks;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// --- DYEABLE COPPER FLUID BACKTANK --- //
public final class BitsnDyesBacktank {

    public static void registerBehaviours() {
        BehaviourApplicators.registerForTypes(
                be -> be.getBlockState().is(HoseBlocks.COPPER_FLUID_BACKTANK.get())
                        ? List.<BlockEntityBehaviour>of(new BndFlaggedDyeableBehaviour(be, () -> BndConfigs.common().DYED_FLUID_COMPONENTS))
                        : List.of(),
                HoseBlockEntities.FLUID_BACKTANK
        );
    }

    @Nullable
    public static DyeColor getColor(final BlockEntity be) {
        return SimpleDyeableBehaviour.getDyeColor(be);
    }

    public static void setColor(final BlockEntity be, final DyeColor color) {
        final SimpleDyeableBehaviour behaviour = BlockEntityBehaviour.get(be, SimpleDyeableBehaviour.TYPE);
        if (behaviour != null) {
            behaviour.setColor(color);
        }
    }

    private BitsnDyesBacktank() {
    }
}
