package edn.lakeopossmc.simulatedhoses.compat.bitsndyes;

import com.cake.azimuth.registration.BehaviourApplicators;
import com.cake.bnd.foundation.create.BndFlaggedDyeableBehaviour;
import com.cake.bnd.registry.core.BndConfigs;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import edn.lakeopossmc.simulatedhoses.HoseBlockEntities;

import java.util.List;

// --- DYE BEHAVIOUR ON EVERY HOSE BLOCK --- //
public final class BitsnDyesCompat {

    @SuppressWarnings("unchecked")
    public static void registerBehaviours() {
        BehaviourApplicators.registerForTypes(
                be -> List.<BlockEntityBehaviour>of(new BndFlaggedDyeableBehaviour(be, () -> BndConfigs.common().DYED_FLUID_COMPONENTS)),
                HoseBlockEntities.HOSE_CONNECTOR,
                HoseBlockEntities.MAGNETIC_HOSE_CONNECTOR,
                HoseBlockEntities.HOSE_WINCH,
                HoseBlockEntities.FLUID_QUICK_CONNECT,
                HoseBlockEntities.PRESSURE_NOZZLE
        );
    }

    private BitsnDyesCompat() {
    }
}
