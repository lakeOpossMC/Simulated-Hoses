package edn.lakeopossmc.simulatedhoses.hose;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

// --- ANYTHING A HOSE CAN PLUG INTO --- //
public interface HoseEndpoint {

    // * Null for ends that never hold a hose themselves
    @Nullable
    HoseHolderBehavior getHoseHolder();

    HosePortHandler getPortHandler();

    // * Side of the block that pipes connect to
    Direction getPortDirection();

    @Nullable
    default HoseEndpoint getCoupledEndpoint() {
        return null;
    }

    default boolean isPortMated() {
        return this.getCoupledEndpoint() != null;
    }

    default boolean placesSpilledFluid() {
        return false;
    }

    default boolean spillsToParentLevel() {
        return false;
    }

    default boolean isOpenOnItsOwn() {
        return false;
    }

    default BlockEntity asBlockEntity() {
        return (BlockEntity) this;
    }

    void setHoseFlow(int flow);

    int getHoseFlow();
}
