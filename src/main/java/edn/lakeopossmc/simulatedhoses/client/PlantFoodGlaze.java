package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// --- GOO OVER ANYTHING A PLAYER WEARS --- //
public final class PlantFoodGlaze implements MultiBufferSource {
    private static final ResourceLocation GOO = SimulatedHosesMod.asResource("textures/entity/plant_food_film.png");
    private static final int TINT_RED = 0x5A;
    private static final int TINT_GREEN = 0xC8;
    private static final int TINT_BLUE = 0x3C;
    private static final int TINT_ALPHA = 170;
    private static final long MAX_PIXELS = 4096L * 4096L;
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_TEXTURE_BINDING_2D = 0x8069;
    private static final int GL_TEXTURE_WIDTH = 0x1000;
    private static final int GL_TEXTURE_HEIGHT = 0x1001;

    private static final Map<RenderType, Optional<Source>> SOURCES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> OFFSET_TYPES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Optional<ResourceLocation>> GOO_TEXTURES = new ConcurrentHashMap<>();
    @Nullable
    private static NativeImage gooTile;
    @Nullable
    private static Field stateField;
    @Nullable
    private static Field textureField;
    @Nullable
    private static Field layeringField;
    @Nullable
    private static Method cutoutTexture;
    private static boolean reflectionFailed;

    private final MultiBufferSource buffers;
    private final int alpha;

    public PlantFoodGlaze(final MultiBufferSource buffers, final int alpha) {
        this.buffers = buffers;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer getBuffer(final RenderType type) {
        final Optional<Source> source = SOURCES.computeIfAbsent(type, found -> {
            final Optional<Source> read = sourceOf(found);
            SimulatedHosesMod.LOGGER.debug("Plant Food coat: {} -> {}", found, read.map(Source::texture).map(Object::toString).orElse("skipped"));
            return read;
        });
        if (source.isEmpty()) {
            return Discard.INSTANCE;
        }
        final Optional<ResourceLocation> goo = GOO_TEXTURES.computeIfAbsent(source.get().texture(), PlantFoodGlaze::makeGoo);
        if (goo.isEmpty()) {
            return Discard.INSTANCE;
        }
        return new Film(this.buffers.getBuffer(translucent(goo.get(), source.get().offset())), this.alpha);
    }

    public static RenderType translucent(final ResourceLocation texture, final boolean offset) {
        if (!offset) {
            return RenderType.entityTranslucent(texture);
        }
        return OFFSET_TYPES.computeIfAbsent(texture, location -> RenderType.create(
                SimulatedHosesMod.MOD_ID + "_film_offset",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                true,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(location, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .createCompositeState(true)));
    }

    public static void invalidate() {
        final var textures = Minecraft.getInstance().getTextureManager();
        GOO_TEXTURES.values().forEach(goo -> goo.ifPresent(textures::release));
        GOO_TEXTURES.clear();
        OFFSET_TYPES.clear();
        if (gooTile != null) {
            gooTile.close();
            gooTile = null;
        }
    }

    //#region // --- MAKING THE GOO VERSION OF A TEXTURE --- //
    private static Optional<ResourceLocation> makeGoo(final ResourceLocation texture) {
        final NativeImage tile = gooTile();
        if (tile == null) {
            return Optional.empty();
        }
        final AbstractTexture loaded = Minecraft.getInstance().getTextureManager().getTexture(texture);
        final int previous = GlStateManager._getInteger(GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._bindTexture(loaded.getId());
            final int width = GlStateManager._getTexLevelParameter(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
            final int height = GlStateManager._getTexLevelParameter(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
            if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                return Optional.empty();
            }
            final NativeImage goo = new NativeImage(width, height, true);
            try (NativeImage source = new NativeImage(width, height, false)) {
                source.downloadTexture(0, false);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        goo.setPixelRGBA(x, y, gooPixel(source.getPixelRGBA(x, y),
                                tile.getPixelRGBA(x % tile.getWidth(), y % tile.getHeight())));
                    }
                }
            }
            final ResourceLocation location = SimulatedHosesMod.asResource("goo/" + Integer.toHexString(texture.hashCode())
                    + "_" + texture.getPath().replaceAll("[^a-z0-9_./-]", "_"));
            Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(goo));
            SimulatedHosesMod.LOGGER.debug("Plant Food coat made goo for {} ({}x{})", texture, width, height);
            return Optional.of(location);
        } catch (final RuntimeException e) {
            SimulatedHosesMod.LOGGER.warn("Plant Food coat couldn't make goo for {}", texture, e);
            return Optional.empty();
        } finally {
            GlStateManager._bindTexture(previous);
        }
    }

    // * NativeImage pixels are ABGR
    private static int gooPixel(final int source, final int tile) {
        final int sourceAlpha = source >>> 24;
        if (sourceAlpha == 0) {
            return 0;
        }
        final int tileAlpha = tile >>> 24;
        if (tileAlpha > 0) {
            return (tileAlpha * sourceAlpha / 255) << 24 | (tile & 0x00FFFFFF);
        }
        final int red = (source & 0xFF) * TINT_RED / 255;
        final int green = (source >> 8 & 0xFF) * TINT_GREEN / 255;
        final int blue = (source >> 16 & 0xFF) * TINT_BLUE / 255;
        return (TINT_ALPHA * sourceAlpha / 255) << 24 | blue << 16 | green << 8 | red;
    }

    @Nullable
    private static NativeImage gooTile() {
        if (gooTile != null) {
            return gooTile;
        }
        final Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(GOO);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream stream = resource.get().open()) {
            gooTile = NativeImage.read(stream);
        } catch (final Exception e) {
            SimulatedHosesMod.LOGGER.warn("Couldn't read the Plant Food goo texture", e);
        }
        return gooTile;
    }
    //#endregion

    //#region // --- WHICH TEXTURE A RENDER TYPE USES --- //
    private record Source(ResourceLocation texture, boolean offset) {
    }

    private static Optional<Source> sourceOf(final RenderType type) {
        if (reflectionFailed || type.toString().contains("glint")) {
            return Optional.empty();
        }
        try {
            if (stateField == null) {
                final Class<?> composite = Class.forName("net.minecraft.client.renderer.RenderType$CompositeRenderType");
                stateField = composite.getDeclaredField("state");
                stateField.setAccessible(true);
                final Class<?> state = Class.forName("net.minecraft.client.renderer.RenderType$CompositeState");
                textureField = state.getDeclaredField("textureState");
                textureField.setAccessible(true);
                layeringField = state.getDeclaredField("layeringState");
                layeringField.setAccessible(true);
                cutoutTexture = RenderStateShard.EmptyTextureStateShard.class.getDeclaredMethod("cutoutTexture");
                cutoutTexture.setAccessible(true);
            }
            if (!stateField.getDeclaringClass().isInstance(type)) {
                return Optional.empty();
            }
            final Object state = stateField.get(type);
            final Object shard = textureField.get(state);
            @SuppressWarnings("unchecked")
            final Optional<ResourceLocation> texture = (Optional<ResourceLocation>) cutoutTexture.invoke(shard);
            final boolean offset = layeringField.get(state) == RenderStateShard.VIEW_OFFSET_Z_LAYERING;
            return texture.map(location -> new Source(location, offset));
        } catch (final ReflectiveOperationException | RuntimeException e) {
            SimulatedHosesMod.LOGGER.warn("Plant Food coat can't read render types, worn items won't get goo", e);
            reflectionFailed = true;
            return Optional.empty();
        }
    }
    //#endregion

    private static final class Film implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;

        private Film(final VertexConsumer delegate, final int alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(final float x, final float y, final float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(final int red, final int green, final int blue, final int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(final float u, final float v) {
            this.delegate.setUv(u, v);
            this.delegate.setColor(255, 255, 255, this.alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv1(final int u, final int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(final int u, final int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(final float x, final float y, final float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }
    }

    private static final class Discard implements VertexConsumer {
        private static final Discard INSTANCE = new Discard();

        @Override
        public VertexConsumer addVertex(final float x, final float y, final float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(final int red, final int green, final int blue, final int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(final float u, final float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(final int u, final int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(final int u, final int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(final float x, final float y, final float z) {
            return this;
        }
    }
}
