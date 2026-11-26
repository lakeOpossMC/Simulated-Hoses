package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// --- WHAT COLOUR A SPRAYED FLUID IS --- //
// * The fluid's still texture averaged and tinted
// * Worked out once per texture and tint, and again after resource packs reload
public final class FluidSprayColors {
    private static final int FALLBACK = 0xFFFFFF;
    private static final float AERATED = 0.25F;
    private static final int MIN_ALPHA = 20;

    private static final Map<String, Integer> CACHE = new HashMap<>();
    private static final Map<String, int[]> PALETTES = new HashMap<>();

    public static final int DARK = 0;
    public static final int BODY = 1;
    public static final int HOT = 2;
    public static final int PEAK = 3;
    private static final float[] PALETTE_PICKS = {0.2F, 0.55F, 0.95F, 0.995F};
    private static final int[] FALLBACK_PALETTE = {0xCD4509, 0xD5570F, 0xE2892C, 0xEBAF46};

    public static int of(final FluidStack fluid) {
        final IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        final ResourceLocation still = extensions.getStillTexture(fluid);
        final int tint = extensions.getTintColor(fluid);
        final boolean glows = glows(fluid);
        final String key = still + "|" + Integer.toHexString(tint) + "|" + glows;
        return CACHE.computeIfAbsent(key, k -> {
            final int base = still != null ? average(still) : FALLBACK;
            final int tinted = multiply(base, tint);
            return glows ? tinted : lighten(tinted, AERATED);
        });
    }

    public static int[] palette(final FluidStack fluid) {
        final IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        final ResourceLocation still = extensions.getStillTexture(fluid);
        final int tint = extensions.getTintColor(fluid);
        return PALETTES.computeIfAbsent(still + "|" + Integer.toHexString(tint), k -> {
            final int[] sampled = still != null ? sample(still) : null;
            if (sampled == null) {
                return FALLBACK_PALETTE;
            }
            final int[] palette = new int[PALETTE_PICKS.length];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = multiply(sampled[Math.min(sampled.length - 1, (int) (PALETTE_PICKS[i] * sampled.length))], tint);
            }
            return palette;
        });
    }

    public static boolean glows(final FluidStack fluid) {
        return fluid.getFluid().getFluidType().getLightLevel(fluid) > 0;
    }

    public static void invalidate() {
        CACHE.clear();
        PALETTES.clear();
    }

    private static int average(final ResourceLocation sprite) {
        final ResourceLocation png = sprite.withPath("textures/" + sprite.getPath() + ".png");
        final Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(png);
        if (resource.isEmpty()) {
            return FALLBACK;
        }
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            // * Only the first frame of an animated texture
            final int width = image.getWidth();
            final int height = Math.min(width, image.getHeight());
            long red = 0;
            long green = 0;
            long blue = 0;
            long count = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // * NativeImage pixels are ABGR
                    final int pixel = image.getPixelRGBA(x, y);
                    if ((pixel >>> 24) < MIN_ALPHA) {
                        continue;
                    }
                    red += pixel & 255;
                    green += pixel >> 8 & 255;
                    blue += pixel >> 16 & 255;
                    count++;
                }
            }
            if (count == 0) {
                return FALLBACK;
            }
            return (int) (red / count) << 16 | (int) (green / count) << 8 | (int) (blue / count);
        } catch (final Exception e) {
            return FALLBACK;
        }
    }

    // * Every visible pixel of every frame, dimmest first
    @Nullable
    private static int[] sample(final ResourceLocation sprite) {
        final ResourceLocation png = sprite.withPath("textures/" + sprite.getPath() + ".png");
        final Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(png);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            final int[] pixels = new int[image.getWidth() * image.getHeight()];
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    // * NativeImage pixels are ABGR
                    final int pixel = image.getPixelRGBA(x, y);
                    if ((pixel >>> 24) >= MIN_ALPHA) {
                        pixels[count++] = (pixel & 255) << 16 | (pixel >> 8 & 255) << 8 | (pixel >> 16 & 255);
                    }
                }
            }
            if (count == 0) {
                return null;
            }
            final Integer[] sorted = new Integer[count];
            for (int i = 0; i < count; i++) {
                sorted[i] = pixels[i];
            }
            Arrays.sort(sorted, (a, b) -> Integer.compare(luminance(a), luminance(b)));
            return Arrays.stream(sorted).mapToInt(Integer::intValue).toArray();
        } catch (final Exception e) {
            return null;
        }
    }

    private static int luminance(final int rgb) {
        return (rgb >> 16 & 255) * 299 + (rgb >> 8 & 255) * 587 + (rgb & 255) * 114;
    }

    private static int multiply(final int rgb, final int tint) {
        final int red = (rgb >> 16 & 255) * (tint >> 16 & 255) / 255;
        final int green = (rgb >> 8 & 255) * (tint >> 8 & 255) / 255;
        final int blue = (rgb & 255) * (tint & 255) / 255;
        return red << 16 | green << 8 | blue;
    }

    private static int lighten(final int rgb, final float amount) {
        final int red = (rgb >> 16 & 255);
        final int green = (rgb >> 8 & 255);
        final int blue = (rgb & 255);
        return (int) (red + (255 - red) * amount) << 16 | (int) (green + (255 - green) * amount) << 8 | (int) (blue + (255 - blue) * amount);
    }

    private FluidSprayColors() {
    }
}
