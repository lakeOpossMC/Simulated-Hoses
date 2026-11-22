package edn.lakeopossmc.simulatedhoses.fluids;

import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

// --- DYE FLUIDS --- //
public final class DyeFluids {
    public static final String DRAGONS_PLUS = "create_dragons_plus";
    public static final boolean ENABLED = !ModList.get().isLoaded(DRAGONS_PLUS);

    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, SimulatedHosesMod.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, SimulatedHosesMod.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SimulatedHosesMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SimulatedHosesMod.MOD_ID);

    // * Paint spreads slowly and not far, a little quicker than lava
    private static final int TICK_RATE = 20;
    private static final int SPREAD = 2;

    public record Dye(DyeColor color, DeferredHolder<FluidType, FluidType> type, DeferredHolder<Fluid, BaseFlowingFluid.Source> source,
                      DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing, DeferredBlock<LiquidBlock> block, DeferredItem<BucketItem> bucket) {
    }

    private static final Map<DyeColor, Dye> DYES = new EnumMap<>(DyeColor.class);

    static {
        if (ENABLED) {
            for (final DyeColor color : DyeColor.values()) {
                DYES.put(color, create(color));
            }
        }
    }

    public static Collection<Dye> all() {
        return Collections.unmodifiableCollection(DYES.values());
    }

    @Nullable
    public static Dye of(final DyeColor color) {
        return DYES.get(color);
    }

    // * The colour of one of these fluids, or null for any other fluid
    @Nullable
    public static DyeColor colorOf(final Fluid fluid) {
        for (final Dye dye : DYES.values()) {
            if (fluid == dye.source.get() || fluid == dye.flowing.get()) {
                return dye.color;
            }
        }
        return null;
    }

    public static Component colorName(final DyeColor color) {
        return Component.translatable("color.minecraft." + color.getName());
    }

    private static Dye create(final DyeColor color) {
        final String name = color.getName() + "_dye";
        final ResourceLocation id = SimulatedHosesMod.asResource(name);
        final ResourceLocation flowingId = SimulatedHosesMod.asResource("flowing_" + name);
        final ResourceLocation bucketId = SimulatedHosesMod.asResource(name + "_bucket");
        final DeferredHolder<FluidType, FluidType> type = TYPES.register(name, () -> new DyeFluidType(color));
        final DeferredHolder<Fluid, BaseFlowingFluid.Source> sourceRef = DeferredHolder.create(Registries.FLUID, id);
        final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowingRef = DeferredHolder.create(Registries.FLUID, flowingId);
        final DeferredBlock<LiquidBlock> blockRef = DeferredBlock.createBlock(id);
        final DeferredItem<BucketItem> bucketRef = DeferredItem.createItem(bucketId);
        final Supplier<BaseFlowingFluid.Properties> properties = () -> new BaseFlowingFluid.Properties(type, sourceRef, flowingRef)
                .block(blockRef)
                .bucket(bucketRef)
                .tickRate(TICK_RATE)
                .slopeFindDistance(SPREAD)
                .levelDecreasePerBlock(2)
                .explosionResistance(100.0F);

        final DeferredHolder<Fluid, BaseFlowingFluid.Source> source = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(properties.get()));
        final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing = FLUIDS.register("flowing_" + name,
                () -> new BaseFlowingFluid.Flowing(properties.get()));
        final DeferredBlock<LiquidBlock> block = BLOCKS.register(name, () -> new LiquidBlock(source.get(), BlockBehaviour.Properties.of()
                .mapColor(color.getMapColor())
                .replaceable()
                .noCollission()
                .strength(100.0F)
                .pushReaction(PushReaction.DESTROY)
                .noLootTable()
                .liquid()
                .sound(SoundType.EMPTY)));
        final DeferredItem<BucketItem> bucket = ITEMS.register(name + "_bucket",
                () -> new DyeBucketItem(source.get(), color, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
        return new Dye(color, type, source, flowing, block, bucket);
    }

    //#region // --- PIECES --- //
    private static final class DyeFluidType extends FluidType {
        private final DyeColor color;

        private DyeFluidType(final DyeColor color) {
            super(FluidType.Properties.create()
                    .descriptionId("fluid_type.simulatedhoses.dye")
                    .canSwim(true)
                    .canDrown(true)
                    .canExtinguish(true)
                    .supportsBoating(false)
                    .pathType(PathType.WATER)
                    .adjacentPathType(null)
                    .motionScale(0.007)
                    .density(1500)
                    .viscosity(4000)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
            this.color = color;
        }

        @Override
        public Component getDescription() {
            return Component.translatable("fluid_type.simulatedhoses.dye", colorName(this.color));
        }

        @Override
        public Component getDescription(final FluidStack stack) {
            return this.getDescription();
        }
    }

    private static final class DyeBucketItem extends BucketItem {
        private final DyeColor color;

        private DyeBucketItem(final Fluid fluid, final DyeColor color, final Properties properties) {
            super(fluid, properties);
            this.color = color;
        }

        @Override
        public Component getName(final ItemStack stack) {
            return Component.translatable("item.simulatedhoses.dye_bucket", colorName(this.color));
        }
    }
    //#endregion

    public static void register(final IEventBus modEventBus) {
        if (ENABLED) {
            TYPES.register(modEventBus);
            FLUIDS.register(modEventBus);
            BLOCKS.register(modEventBus);
            ITEMS.register(modEventBus);
            modEventBus.addListener(DyeFluids::registerCapabilities);
        }
    }

    private static void registerCapabilities(final RegisterCapabilitiesEvent event) {
        for (final Dye dye : DYES.values()) {
            event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> new FluidBucketWrapper(stack), dye.bucket.get());
        }
    }

    private DyeFluids() {
    }
}
