package edn.lakeopossmc.simulatedhoses;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// --- REGISTERS ALL FLUIDS --- //
public final class HoseFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, SimulatedHosesMod.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, SimulatedHosesMod.MOD_ID);

    //#region // --- PLANT FOOD --- //
    // * Watered down tree fertilizer
    public static final DeferredHolder<FluidType, FluidType> PLANT_FOOD_TYPE = FLUID_TYPES.register(
            "plant_food",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type.simulatedhoses.plant_food")
                    .canSwim(true)
                    .canDrown(true)
                    .canExtinguish(true)
                    .supportsBoating(true)
                    .canHydrate(true)
                    .pathType(PathType.WATER)
                    .adjacentPathType(null)
                    .density(1000)
                    .viscosity(1000)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY))
    );

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PLANT_FOOD = FLUIDS.register(
            "plant_food",
            () -> new BaseFlowingFluid.Source(plantFoodProperties())
    );

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_PLANT_FOOD = FLUIDS.register(
            "flowing_plant_food",
            () -> new BaseFlowingFluid.Flowing(plantFoodProperties())
    );

    private static BaseFlowingFluid.Properties plantFoodProperties() {
        return new BaseFlowingFluid.Properties(PLANT_FOOD_TYPE, PLANT_FOOD, FLOWING_PLANT_FOOD)
                .block(HoseBlocks.PLANT_FOOD)
                .bucket(HoseItems.PLANT_FOOD_BUCKET)
                .levelDecreasePerBlock(2)
                .slopeFindDistance(3)
                .explosionResistance(100.0F);
    }
    //#endregion

    private HoseFluids() {
    }

    public static void register(final IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
