package edn.lakeopossmc.simulatedhoses;

import com.simibubi.create.content.equipment.armor.BacktankItem;
import edn.lakeopossmc.simulatedhoses.backtank.FluidBacktankItem;
import edn.lakeopossmc.simulatedhoses.fluids.PlantFoodBucketItem;
import edn.lakeopossmc.simulatedhoses.items.HoseCouplerItem;
import edn.lakeopossmc.simulatedhoses.items.SuperSoakerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// --- REGISTERS ALL ITEMS --- //
public final class HoseItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SimulatedHosesMod.MOD_ID);

    public static final DeferredItem<HoseCouplerItem> HOSE_COUPLER = ITEMS.register(
            "hose_coupler",
            () -> new HoseCouplerItem(new Item.Properties())
    );

    public static final DeferredItem<BlockItem> HOSE_CONNECTOR = ITEMS.registerSimpleBlockItem("hose_connector", HoseBlocks.HOSE_CONNECTOR);
    public static final DeferredItem<BlockItem> MAGNETIC_HOSE_CONNECTOR = ITEMS.registerSimpleBlockItem("magnetic_hose_connector", HoseBlocks.MAGNETIC_HOSE_CONNECTOR);
    public static final DeferredItem<BlockItem> HOSE_WINCH = ITEMS.registerSimpleBlockItem("hose_winch", HoseBlocks.HOSE_WINCH);
    public static final DeferredItem<BlockItem> FLUID_QUICK_CONNECT = ITEMS.registerSimpleBlockItem("fluid_quick_connect", HoseBlocks.FLUID_QUICK_CONNECT);
    public static final DeferredItem<BlockItem> PRESSURE_NOZZLE = ITEMS.registerSimpleBlockItem("pressure_nozzle", HoseBlocks.PRESSURE_NOZZLE);

    public static final DeferredItem<SuperSoakerItem> SUPER_SOAKER = ITEMS.register(
            "super_soaker",
            () -> new SuperSoakerItem(new Item.Properties().durability(SuperSoakerItem.DURABILITY))
    );

    public static final DeferredItem<BacktankItem.BacktankBlockItem> COPPER_FLUID_BACKTANK_PLACEABLE = ITEMS.register(
            "copper_fluid_backtank_placeable",
            () -> new BacktankItem.BacktankBlockItem(HoseBlocks.COPPER_FLUID_BACKTANK.get(), HoseItems.COPPER_FLUID_BACKTANK::get, new Item.Properties())
    );

    public static final DeferredItem<FluidBacktankItem> COPPER_FLUID_BACKTANK = ITEMS.register(
            "copper_fluid_backtank",
            () -> FluidBacktankItem.copper(new Item.Properties(), COPPER_FLUID_BACKTANK_PLACEABLE)
    );

    public static final DeferredItem<BacktankItem.BacktankBlockItem> NETHERITE_FLUID_BACKTANK_PLACEABLE = ITEMS.register(
            "netherite_fluid_backtank_placeable",
            () -> new BacktankItem.BacktankBlockItem(HoseBlocks.NETHERITE_FLUID_BACKTANK.get(), HoseItems.NETHERITE_FLUID_BACKTANK::get, new Item.Properties())
    );

    public static final DeferredItem<FluidBacktankItem> NETHERITE_FLUID_BACKTANK = ITEMS.register(
            "netherite_fluid_backtank",
            () -> FluidBacktankItem.netherite(new Item.Properties(), NETHERITE_FLUID_BACKTANK_PLACEABLE)
    );

    public static final DeferredItem<PlantFoodBucketItem> PLANT_FOOD_BUCKET = ITEMS.register(
            "plant_food_bucket",
            () -> new PlantFoodBucketItem(HoseFluids.PLANT_FOOD.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1))
    );

    private HoseItems() {
    }

    public static void register(final IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
