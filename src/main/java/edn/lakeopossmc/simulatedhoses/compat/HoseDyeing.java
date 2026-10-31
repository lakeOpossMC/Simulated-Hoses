package edn.lakeopossmc.simulatedhoses.compat;

import edn.lakeopossmc.simulatedhoses.compat.bitsndyes.BitsnDyesBacktank;
import edn.lakeopossmc.simulatedhoses.compat.bitsndyes.BitsnDyesBacktankClient;
import edn.lakeopossmc.simulatedhoses.compat.bitsndyes.BitsnDyesClient;
import edn.lakeopossmc.simulatedhoses.compat.bitsndyes.BitsnDyesCompat;
import edn.lakeopossmc.simulatedhoses.compat.bitsndyes.BitsnDyesSoaker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

// --- DYEABLE COPPER --- //
// * With Bits 'n' Dyes installed, the copper on every hose block can be dyed. Dye Depot's extra colours work too
public final class HoseDyeing {
    public static final String BITS_N_DYES = "bits_n_dyes";
    private static final boolean LOADED = ModList.get().isLoaded(BITS_N_DYES);
    public static final Set<String> TEXTURED_COLOURS = Set.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black", "amber", "aqua", "beige", "coral", "forest", "ginger", "indigo", "maroon", "mint",
            "navy", "olive", "rose", "slate", "tan", "teal", "verdant");

    public static boolean isLoaded() {
        return LOADED;
    }

    public static void init(final IEventBus modEventBus, final Dist dist) {
        if (!LOADED) {
            return;
        }
        BitsnDyesCompat.registerBehaviours();
        BitsnDyesBacktank.registerBehaviours();
        if (dist.isClient()) {
            BitsnDyesClient.init(modEventBus);
            BitsnDyesSoaker.init();
            BitsnDyesBacktankClient.init(modEventBus);
        }
    }

    @Nullable
    public static DyeColor backtankColor(final BlockEntity be) {
        return LOADED ? BitsnDyesBacktank.getColor(be) : null;
    }

    public static void setBacktankColor(final BlockEntity be, final DyeColor color) {
        if (LOADED) {
            BitsnDyesBacktank.setColor(be, color);
        }
    }

    private HoseDyeing() {
    }
}
