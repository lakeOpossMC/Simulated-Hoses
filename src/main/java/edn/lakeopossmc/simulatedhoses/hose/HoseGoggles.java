package edn.lakeopossmc.simulatedhoses.hose;

import com.simibubi.create.foundation.utility.CreateLang;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

// --- GOGGLE TOOLTIP FOR HOSE ENDS --- //
public final class HoseGoggles {

    public static boolean addFlowTooltip(final List<Component> tooltip, final HoseEndpoint endpoint) {
        final boolean hosed = endpoint.getHoseHolder() != null && endpoint.getHoseHolder().isAttached();
        if (!hosed && endpoint.getCoupledEndpoint() == null) {
            return false;
        }
        lang().translate("goggles.fluid_stats").forGoggles(tooltip);
        lang().translate("goggles.fluid_pressure").style(ChatFormatting.GRAY).forGoggles(tooltip);
        lang().translate("goggles.flow", Math.abs(endpoint.getHoseFlow()))
                .style(ChatFormatting.AQUA)
                .space()
                .add(CreateLang.translate("gui.goggles.at_current_speed").style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);
        return true;
    }

    private static LangBuilder lang() {
        return new LangBuilder(SimulatedHosesMod.MOD_ID);
    }

    private HoseGoggles() {
    }
}
