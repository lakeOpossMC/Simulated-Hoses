package edn.lakeopossmc.simulatedhoses.compat.bitsndyes;

import com.kipti.bnb.content.decoration.dyeable.simple.SimpleDyeableModelHelper;
import com.kipti.bnb.content.decoration.dyeable.simple.SimpleDyeableModelWrapper;
import com.kipti.bnb.content.decoration.dyeable.simple.SimpleDyeablePartialHelper;
import com.simibubi.create.Create;
import edn.lakeopossmc.simulatedhoses.HoseBlocks;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SpriteShifter;
import net.createmod.catnip.render.SuperBufferFactory;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.render.SuperByteBufferCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// --- DYED COPPER FLUID BACKTANK --- //
public final class BitsnDyesBacktankClient {
    private static final ResourceLocation BACKTANK = Create.asResource("block/copper_backtank");
    private static final ResourceLocation MICRO_TANK = SimulatedHosesMod.asResource("block/copper_fluid_backtank/micro_tank");
    private static final Set<String> COLOURS = Set.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black", "amber", "aqua", "beige", "coral", "forest", "ginger", "indigo", "maroon", "mint",
            "navy", "olive", "rose", "slate", "tan", "teal", "verdant");
    private static final Map<DyeColor, SpriteShiftEntry> BACKTANK_SHIFTS = shifts("copper_backtank", BACKTANK);
    private static final Map<DyeColor, SpriteShiftEntry> MICRO_TANK_SHIFTS = shifts("micro_tank", MICRO_TANK);
    private static final Map<BakedModel, Map<DyeColor, BakedModel>> DYED = new WeakHashMap<>();
    private static final SuperByteBufferCache.Compartment<DyeColor> WORN_BODY = new SuperByteBufferCache.Compartment<>();

    public static void init(final IEventBus modEventBus) {
        SuperByteBufferCache.getInstance().registerCompartment(WORN_BODY);
        modEventBus.addListener(BitsnDyesBacktankClient::wrapModels);
    }

    private static Map<DyeColor, SpriteShiftEntry> shifts(final String name, final ResourceLocation original) {
        final Map<DyeColor, SpriteShiftEntry> map = new EnumMap<>(DyeColor.class);
        for (final DyeColor color : DyeColor.values()) {
            if (COLOURS.contains(color.getName())) {
                map.put(color, SpriteShifter.get(original,
                        SimulatedHosesMod.asResource("block/copper_fluid_backtank/dyed/" + name + "_" + color.getName())));
            }
        }
        return Collections.unmodifiableMap(map);
    }

    @SuppressWarnings("unchecked")
    private static void wrapModels(final ModelEvent.ModifyBakingResult event) {
        final Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (final BlockState state : HoseBlocks.COPPER_FLUID_BACKTANK.get().getStateDefinition().getPossibleStates()) {
            final ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
            final BakedModel model = models.get(location);
            if (model != null && !(model instanceof SimpleDyeableModelWrapper)) {
                models.put(location, new SimpleDyeableModelWrapper(model, BACKTANK_SHIFTS, MICRO_TANK_SHIFTS));
            }
        }
    }

    public static BakedModel dye(final BakedModel model, final DyeColor color) {
        final SpriteShiftEntry backtank = BACKTANK_SHIFTS.get(color);
        if (backtank == null) {
            return model;
        }
        return DYED.computeIfAbsent(model, m -> new EnumMap<>(DyeColor.class))
                .computeIfAbsent(color, c -> new Dyed(model, c, backtank, MICRO_TANK_SHIFTS.get(c)));
    }

    public static SuperByteBuffer dye(final SuperByteBuffer buffer, final DyeColor color) {
        return SimpleDyeablePartialHelper.apply(buffer, color, BACKTANK_SHIFTS);
    }

    public static SuperByteBuffer wornBody(final BlockState state, final DyeColor color) {
        return SuperByteBufferCache.getInstance().get(WORN_BODY, color, () -> SuperBufferFactory.getInstance()
                .createForBlock(dye(Minecraft.getInstance().getBlockRenderer().getBlockModel(state), color), state));
    }

    private static final class Dyed extends BakedModelWrapper<BakedModel> {
        private final DyeColor color;
        private final SpriteShiftEntry[] shifts;
        private final Map<List<Object>, List<BakedQuad>> quads = new HashMap<>();

        private Dyed(final BakedModel original, final DyeColor color, final SpriteShiftEntry... shifts) {
            super(original);
            this.color = color;
            this.shifts = shifts;
        }

        @Override
        public List<BakedModel> getRenderPasses(final ItemStack stack, final boolean fabulous) {
            return List.of(this);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable final BlockState state, @Nullable final Direction side, final RandomSource rand) {
            return this.quads.computeIfAbsent(Arrays.asList(state, side, null),
                    key -> SimpleDyeableModelHelper.shiftSprites(super.getQuads(state, side, rand), this.color, this.shifts));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable final BlockState state, @Nullable final Direction side, final RandomSource rand,
                                        final ModelData data, @Nullable final RenderType renderType) {
            return this.quads.computeIfAbsent(Arrays.asList(state, side, renderType),
                    key -> SimpleDyeableModelHelper.shiftSprites(super.getQuads(state, side, rand, data, renderType), this.color, this.shifts));
        }
    }

    private BitsnDyesBacktankClient() {
    }
}
