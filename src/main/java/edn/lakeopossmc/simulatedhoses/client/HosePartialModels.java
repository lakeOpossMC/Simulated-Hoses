package edn.lakeopossmc.simulatedhoses.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;

// --- PARTIAL MODELS --- //
// * Hose pieces
public final class HosePartialModels {
    public static final PartialModel HOSE = block("hose/segment");
    public static final PartialModel HOSE_KNOT = block("hose/knot");
    public static final PartialModel HOSE_WINCH_COIL = block("hose_winch/coil");
    public static final PartialModel QUICK_CONNECT_EXTENDER = block("fluid_quick_connect/extender");
    public static final PartialModel QUICK_CONNECT_EXTENDER_POWERED = block("fluid_quick_connect/extender_powered");
    public static final PartialModel QUICK_CONNECT_PORT = block("fluid_quick_connect/port");
    public static final PartialModel QUICK_CONNECT_PORT_POWERED = block("fluid_quick_connect/port_powered");

    private static PartialModel block(final String path) {
        return PartialModel.of(SimulatedHosesMod.asResource("block/" + path));
    }

    // * Loads the class so the partials register before models bake
    public static void init() {
    }

    private HosePartialModels() {
    }
}
