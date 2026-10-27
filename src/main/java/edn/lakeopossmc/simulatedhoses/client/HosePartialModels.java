package edn.lakeopossmc.simulatedhoses.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;

// --- PARTIAL MODELS --- //
// * Hose pieces
public final class HosePartialModels {
    public static final PartialModel HOSE = block("hose/segment");
    public static final PartialModel HOSE_KNOT = block("hose/knot");
    // * Smaller hose for the fluid backtank
    public static final PartialModel BACKTANK_HOSE = block("backtank_hose/segment");
    public static final PartialModel BACKTANK_HOSE_KNOT = block("backtank_hose/knot");
    public static final PartialModel BACKTANK_HOSE_BAND = block("backtank_hose/band");
    public static final PartialModel HOSE_WINCH_COIL = block("hose_winch/coil");
    public static final PartialModel QUICK_CONNECT_EXTENDER = block("fluid_quick_connect/extender");
    public static final PartialModel QUICK_CONNECT_EXTENDER_POWERED = block("fluid_quick_connect/extender_powered");
    public static final PartialModel QUICK_CONNECT_PORT = block("fluid_quick_connect/port");
    public static final PartialModel QUICK_CONNECT_PORT_POWERED = block("fluid_quick_connect/port_powered");
    public static final PartialModel PRESSURE_NOZZLE_COG = block("pressure_nozzle/cog");
    public static final PartialModel PRESSURE_NOZZLE_NOZZLE = block("pressure_nozzle/nozzle");
    public static final PartialModel PRESSURE_NOZZLE_TIP = block("pressure_nozzle/tip");
    public static final PartialModel SUPER_SOAKER_COG = item("super_soaker/cog");
    public static final PartialModel SUPER_SOAKER_RUBBER = item("super_soaker/rubber");
    public static final PartialModel SUPER_SOAKER_TIP = item("super_soaker/tip");

    private static PartialModel block(final String path) {
        return PartialModel.of(SimulatedHosesMod.asResource("block/" + path));
    }

    private static PartialModel item(final String path) {
        return PartialModel.of(SimulatedHosesMod.asResource("item/" + path));
    }

    // * Loads the class so the partials register before models bake
    public static void init() {
    }

    private HosePartialModels() {
    }
}
