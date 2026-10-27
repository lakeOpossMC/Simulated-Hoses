package edn.lakeopossmc.simulatedhoses;

import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

// --- ADD ITEMS TO SIMULATED CREATIVE SECTION --- //
public final class HoseSimulatedTab {

    private static final ResourceLocation SECTION_ID = SimulatedHosesMod.asResource("simulatedhoses_section");

    public static void register() {
        addItem(HoseItems.HOSE_WINCH.get());
        addItem(HoseItems.HOSE_CONNECTOR.get());
        addItem(HoseItems.MAGNETIC_HOSE_CONNECTOR.get());
        addItem(HoseItems.FLUID_QUICK_CONNECT.get());
        addItem(HoseItems.PRESSURE_NOZZLE.get());
        addItem(HoseItems.SUPER_SOAKER.get());
        addItem(HoseItems.COPPER_FLUID_BACKTANK.get());
        addItem(HoseItems.NETHERITE_FLUID_BACKTANK.get());
        addItem(HoseItems.HOSE_COUPLER.get());
        addItem(HoseItems.PLANT_FOOD_BUCKET.get());
    }

    private static void addItem(final Item item) {
        final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        SimulatedRegistrate.TAB_ITEMS.add(() -> item);
        SimulatedRegistrate.ITEM_TO_SECTION.put(itemId, SECTION_ID);
    }

    private HoseSimulatedTab() {
    }
}
