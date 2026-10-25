package edn.lakeopossmc.simulatedhoses.compat.bitsndyes;

import com.kipti.bnb.content.decoration.dyeable.simple.SimpleDyeableBehaviour;
import com.kipti.bnb.content.decoration.dyeable.simple.SimpleDyeableModelWrapper;
import com.kipti.bnb.content.decoration.dyeable.simple.SimpleDyeablePartialHelper;
import edn.lakeopossmc.simulatedhoses.HoseBlocks;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SpriteShifter;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// --- DYED COPPER TEXTURES --- //
// * Every copper texture has a dyed copy per colour in a "dyed" folder beside it, named <texture>_<colour>
public final class BitsnDyesClient {
    // * Copper textures by block, as folder/name under textures/block
    private static final Map<Supplier<? extends Block>, List<String>> COPPER = new LinkedHashMap<>();

    static {
        COPPER.put(HoseBlocks.HOSE_CONNECTOR, List.of("hose_connector/hose_connector"));
        COPPER.put(HoseBlocks.MAGNETIC_HOSE_CONNECTOR, List.of(
                "magnetic_hose_connector/magnetic_hose_connector",
                "magnetic_hose_connector/magnetic_hose_connector_inverted",
                "magnetic_hose_connector/magnetic_hose_connector_powered",
                "magnetic_hose_connector/magnetic_hose_connector_powered_inverted"));
        COPPER.put(HoseBlocks.HOSE_WINCH, List.of(
                "hose_winch/hose_winch",
                "hose_winch/redstone_hose_winch",
                "hose_winch/redstone_hose_winch_powered"));
        COPPER.put(HoseBlocks.FLUID_QUICK_CONNECT, List.of(
                "fluid_quick_connect/fluid_quick_connect",
                "fluid_quick_connect/fluid_quick_connect_powered"));
        COPPER.put(HoseBlocks.PRESSURE_NOZZLE, List.of("pressure_nozzle/pressure_nozzle"));
    }

    // * Original texture to its dyed copy
    private static final Map<ResourceLocation, Map<DyeColor, SpriteShiftEntry>> SHIFTS = new HashMap<>();

    public static void init(final IEventBus modEventBus) {
        for (final List<String> textures : COPPER.values()) {
            for (final String texture : textures) {
                SHIFTS.put(SimulatedHosesMod.asResource("block/" + texture), shifts(texture));
            }
        }
        modEventBus.addListener(BitsnDyesClient::wrapModels);
    }

    private static Map<DyeColor, SpriteShiftEntry> shifts(final String texture) {
        final int slash = texture.indexOf('/');
        final String folder = texture.substring(0, slash);
        final String name = texture.substring(slash + 1);
        final ResourceLocation original = SimulatedHosesMod.asResource("block/" + texture);
        final Map<DyeColor, SpriteShiftEntry> map = new EnumMap<>(DyeColor.class);
        for (final DyeColor color : DyeColor.values()) {
            map.put(color, SpriteShifter.get(original,
                    SimulatedHosesMod.asResource("block/" + folder + "/dyed/" + name + "_" + color.getName())));
        }
        return Collections.unmodifiableMap(map);
    }

    @SuppressWarnings("unchecked")
    private static void wrapModels(final ModelEvent.ModifyBakingResult event) {
        final Map<ModelResourceLocation, BakedModel> models = event.getModels();
        COPPER.forEach((block, textures) -> {
            final Map<DyeColor, SpriteShiftEntry>[] shifts = textures.stream()
                    .map(texture -> SHIFTS.get(SimulatedHosesMod.asResource("block/" + texture)))
                    .toArray(Map[]::new);
            for (final BlockState state : block.get().getStateDefinition().getPossibleStates()) {
                final ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
                final BakedModel model = models.get(location);
                if (model != null && !(model instanceof SimpleDyeableModelWrapper)) {
                    models.put(location, new SimpleDyeableModelWrapper(model, shifts));
                }
            }
        });
    }

    public static SuperByteBuffer dye(final SuperByteBuffer buffer, final BlockEntity be, final ResourceLocation texture) {
        final Map<DyeColor, SpriteShiftEntry> shifts = SHIFTS.get(texture);
        return shifts == null ? buffer : SimpleDyeablePartialHelper.apply(buffer, SimpleDyeableBehaviour.getDyeColor(be), shifts);
    }

    private BitsnDyesClient() {
    }
}
