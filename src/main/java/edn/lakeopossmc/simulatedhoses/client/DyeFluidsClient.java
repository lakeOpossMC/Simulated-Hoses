package edn.lakeopossmc.simulatedhoses.client;

import edn.lakeopossmc.simulatedhoses.HoseParticles;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import edn.lakeopossmc.simulatedhoses.fluids.DyeFluids;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.Map;

// --- HOW DYE FLUIDS LOOK --- //
public final class DyeFluidsClient {
    private static final ResourceLocation STILL = SimulatedHosesMod.asResource("fluid/dye_still");
    private static final ResourceLocation FLOW = SimulatedHosesMod.asResource("fluid/dye_flow");
    private static final ModelResourceLocation BUCKET_MODEL = ModelResourceLocation.standalone(SimulatedHosesMod.asResource("item/dye_bucket"));
    private static final ModelResourceLocation BLOCK_MODEL = ModelResourceLocation.standalone(SimulatedHosesMod.asResource("block/dye"));
    private static final int PAINT_LAYER = 1;
    private static final int GLOSS_LAYER = 2;

    private static final float GLOSS_SATURATION = 0.9F;
    private static final float GLOSS_LIFT = 0.6F;
    private static final float DARK_LIFT = 0.2F;
    private static final float DARK_BELOW = 0.7F;

    public static void init(final IEventBus modEventBus) {
        modEventBus.addListener(HoseParticlesClient::register);
        if (!DyeFluids.ENABLED) {
            return;
        }
        modEventBus.addListener(DyeFluidsClient::registerExtensions);
        modEventBus.addListener(DyeFluidsClient::registerItemColors);
        modEventBus.addListener(DyeFluidsClient::registerModels);
        modEventBus.addListener(DyeFluidsClient::swapModels);
    }

    public static int paint(final DyeColor color) {
        return 0xFF000000 | color.getTextureDiffuseColor();
    }

    private static void registerExtensions(final RegisterClientExtensionsEvent event) {
        for (final DyeFluids.Dye dye : DyeFluids.all()) {
            final int tint = paint(dye.color());
            final Vector3f fog = new Vector3f((tint >> 16 & 255) / 255.0F, (tint >> 8 & 255) / 255.0F, (tint & 255) / 255.0F);
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public @NotNull ResourceLocation getStillTexture() {
                    return STILL;
                }

                @Override
                public @NotNull ResourceLocation getFlowingTexture() {
                    return FLOW;
                }

                @Override
                public int getTintColor() {
                    return tint;
                }

                @Override
                public @NotNull Vector3f modifyFogColor(final Camera camera, final float partialTick, final ClientLevel level,
                                                        final int renderDistance, final float darkenWorldAmount, final Vector3f fluidFogColor) {
                    return new Vector3f(fog);
                }
            }, dye.type().get());
        }
    }

    public static void setRenderLayers() {
        for (final DyeFluids.Dye dye : DyeFluids.all()) {
            ItemBlockRenderTypes.setRenderLayer(dye.source().get(), RenderType.solid());
            ItemBlockRenderTypes.setRenderLayer(dye.flowing().get(), RenderType.solid());
        }
    }

    private static void registerItemColors(final RegisterColorHandlersEvent.Item event) {
        for (final DyeFluids.Dye dye : DyeFluids.all()) {
            final int paint = paint(dye.color());
            final int gloss = gloss(paint);
            event.register((stack, layer) -> layer == PAINT_LAYER ? paint : layer == GLOSS_LAYER ? gloss : -1, dye.bucket().get());
        }
    }

    public static int gloss(final int rgb) {
        final float r = (rgb >> 16 & 255) / 255.0F;
        final float g = (rgb >> 8 & 255) / 255.0F;
        final float b = (rgb & 255) / 255.0F;
        final float max = Math.max(r, Math.max(g, b));
        final float min = Math.min(r, Math.min(g, b));
        final float value = max;
        final float saturation = max <= 0.0F ? 0.0F : (max - min) / max;
        final float lift = GLOSS_LIFT * (DARK_LIFT + (1.0F - DARK_LIFT) * Math.min(1.0F, value / DARK_BELOW));
        final float newValue = value + (1.0F - value) * lift;
        final float newSaturation = saturation * GLOSS_SATURATION;
        final float[] out = new float[3];
        final float[] in = {r, g, b};
        for (int i = 0; i < 3; i++) {
            final float fromTop = max <= 0.0F || saturation <= 0.0F ? 0.0F : (max - in[i]) / (max * saturation);
            out[i] = newValue * (1.0F - newSaturation * fromTop);
        }
        return 0xFF000000 | Math.round(out[0] * 255) << 16 | Math.round(out[1] * 255) << 8 | Math.round(out[2] * 255);
    }

    private static void registerModels(final ModelEvent.RegisterAdditional event) {
        event.register(BUCKET_MODEL);
        event.register(BLOCK_MODEL);
    }

    // * Every bucket and every fluid block uses the one shared model
    private static void swapModels(final ModelEvent.ModifyBakingResult event) {
        final Map<ModelResourceLocation, BakedModel> models = event.getModels();
        final BakedModel bucket = models.get(BUCKET_MODEL);
        final BakedModel block = models.get(BLOCK_MODEL);
        for (final DyeFluids.Dye dye : DyeFluids.all()) {
            if (bucket != null) {
                models.put(ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(dye.bucket().get())), bucket);
            }
            if (block != null) {
                for (final BlockState state : dye.block().get().getStateDefinition().getPossibleStates()) {
                    models.put(BlockModelShaper.stateToModelLocation(state), block);
                }
            }
        }
    }

    private DyeFluidsClient() {
    }

    //#region // --- PARTICLES --- //
    private static final class HoseParticlesClient {
        private static void register(final RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(HoseParticles.PAINT_GLOB.get(), PaintGlobParticle::provider);
        }
    }
    //#endregion
}
