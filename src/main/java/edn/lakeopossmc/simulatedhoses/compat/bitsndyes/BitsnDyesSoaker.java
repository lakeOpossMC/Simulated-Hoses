package edn.lakeopossmc.simulatedhoses.compat.bitsndyes;

import com.kipti.bnb.content.decoration.dyeable.simple.SimpleDyeableModelHelper;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SpriteShifter;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// --- DYED SUPER SOAKER --- //
public final class BitsnDyesSoaker {
    private static final ResourceLocation TEXTURE = SimulatedHosesMod.asResource("item/super_soaker");
    private static final Set<String> COLOURS = Set.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black", "amber", "aqua", "beige", "coral", "forest", "ginger", "indigo", "maroon", "mint",
            "navy", "olive", "rose", "slate", "tan", "teal", "verdant");
    private static final Map<DyeColor, SpriteShiftEntry> SHIFTS = new EnumMap<>(DyeColor.class);
    private static final Map<BakedModel, Map<DyeColor, BakedModel>> DYED = new WeakHashMap<>();

    public static void init() {
        for (final DyeColor color : DyeColor.values()) {
            if (COLOURS.contains(color.getName())) {
                SHIFTS.put(color, SpriteShifter.get(TEXTURE, SimulatedHosesMod.asResource("item/dyed/super_soaker_" + color.getName())));
            }
        }
    }

    public static BakedModel dye(final BakedModel model, final DyeColor color) {
        final SpriteShiftEntry shift = SHIFTS.get(color);
        if (shift == null) {
            return model;
        }
        return DYED.computeIfAbsent(model, m -> new EnumMap<>(DyeColor.class)).computeIfAbsent(color, c -> new Dyed(model, c, shift));
    }

    private static final class Dyed extends BakedModelWrapper<BakedModel> {
        private final DyeColor color;
        private final SpriteShiftEntry shift;
        private final Map<List<Object>, List<BakedQuad>> quads = new HashMap<>();

        private Dyed(final BakedModel original, final DyeColor color, final SpriteShiftEntry shift) {
            super(original);
            this.color = color;
            this.shift = shift;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable final BlockState state, @Nullable final Direction side, final RandomSource rand) {
            return this.quads.computeIfAbsent(Arrays.asList(side, null),
                    key -> SimpleDyeableModelHelper.shiftSprites(super.getQuads(state, side, rand), this.color, this.shift));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable final BlockState state, @Nullable final Direction side, final RandomSource rand,
                                        final ModelData data, @Nullable final RenderType renderType) {
            return this.quads.computeIfAbsent(Arrays.asList(side, renderType),
                    key -> SimpleDyeableModelHelper.shiftSprites(super.getQuads(state, side, rand, data, renderType), this.color, this.shift));
        }
    }

    private BitsnDyesSoaker() {
    }
}
