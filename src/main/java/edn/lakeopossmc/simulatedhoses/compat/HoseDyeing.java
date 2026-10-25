package edn.lakeopossmc.simulatedhoses.compat;

import edn.lakeopossmc.simulatedhoses.compat.bitsndyes.BitsnDyesClient;
import edn.lakeopossmc.simulatedhoses.compat.bitsndyes.BitsnDyesCompat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

// --- DYEABLE COPPER --- //
// * With Bits 'n' Dyes installed, the copper on every hose block can be dyed. Dye Depot's extra colours work too
public final class HoseDyeing {
    public static final String BITS_N_DYES = "bits_n_dyes";
    private static final boolean LOADED = ModList.get().isLoaded(BITS_N_DYES);

    public static boolean isLoaded() {
        return LOADED;
    }

    public static void init(final IEventBus modEventBus, final Dist dist) {
        if (!LOADED) {
            return;
        }
        BitsnDyesCompat.registerBehaviours();
        if (dist.isClient()) {
            BitsnDyesClient.init(modEventBus);
        }
    }

    private HoseDyeing() {
    }
}
