package edn.lakeopossmc.simulatedhoses.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import edn.lakeopossmc.simulatedhoses.fluids.PaintColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

// --- HOW ONE FLUID LOOKS WHEN IT'S SPRAYED --- //
// * Read from assets/<namespace>/hose_sprays/<name>.json in any resource pack, so packs can give a fluid its own spray
// * Textures live in assets/<namespace>/textures/hose_spray/<folder>/
public final class SprayEffect {
    public enum Style {
        // * Foamy clumps with mist peeling off. Textures are animation frames
        SPRAY,
        // * Glossy globs that splash. Textures: shapes, each with a _gloss shine
        GLOB,
        // * Glowing molten globs that pulse and drift. Textures: shapes, each with _glow and _flow
        MOLTEN,
        // * Heavy globs that stretch, shed droplets and splat. Textures: shapes, each with a _gloss
        VISCOUS
    }

    public enum Tint {
        // * The fluid's own colour
        FLUID,
        // * The colour of the dye the fluid is, or its own colour if it isn't a dye
        DYE,
        // * The texture's own colours
        NONE,
        // * One colour given in the file
        FIXED
    }

    // * Everything a file can leave out, by style
    private static final Map<Style, JsonObject> DEFAULTS = Map.of(
            Style.SPRAY, JsonParser.parseString("""
                    {"tint": "fluid", "emissive": "auto", "size": 0.16, "hose_size": 0.14, "flow_growth": 1.4, "hose_flow_growth": 1.0,
                     "count": [2, 3], "spin": 0.15, "mist": 3.0,
                     "splash": {"chance": 0.6, "count": [1, 2], "speed": 0.07, "lift": 0.09, "size": 0.6, "lifetime": [6, 4]}}
                    """).getAsJsonObject(),
            Style.GLOB, JsonParser.parseString("""
                    {"tint": "dye", "emissive": false, "size": 0.22, "hose_size": 0.18, "flow_growth": 0.9, "hose_flow_growth": 0.9,
                     "count": [1, 1], "spin": 0.12, "shine_color": "auto", "shine": 1.0,
                     "splash": {"count": [2, 5], "speed": [0.08, 0.14], "lift": [0.1, 0.12], "size": [0.5, 0.35], "lifetime": [10, 7]}}
                    """).getAsJsonObject(),
            Style.MOLTEN, JsonParser.parseString("""
                    {"emissive": true, "size": 0.23, "hose_size": 0.18, "flow_growth": 1.2, "hose_flow_growth": 1.0,
                     "count": [1, 2], "spin": 0.08, "stretch": 0.6, "fresh_stretch": 0.8, "smoke_chance": 0.06,
                     "splash": {"count": [2, 4], "speed": [0.07, 0.1], "lift": [0.08, 0.1], "size": [0.45, 0.35], "lifetime": [10, 7]}}
                    """).getAsJsonObject(),
            Style.VISCOUS, JsonParser.parseString("""
                    {"tint": "none", "emissive": false, "size": 0.22, "hose_size": 0.18, "flow_growth": 1.0, "hose_flow_growth": 1.0,
                     "count": [1, 2], "spin": 0.05, "stretch": 0.6, "fresh_stretch": 0.48, "shed_chance": 0.0,
                     "shine_color": "#FFFFFF", "shine": 0.85,
                     "splash": {"count": [1, 3], "speed": [0.025, 0.05], "lift": 0.06, "size": [0.4, 0.3], "lifetime": [12, 9]}}
                    """).getAsJsonObject());

    private static final Set<String> FIELDS = Set.of("fluids", "style", "textures", "priority", "tint", "palette", "emissive", "shine_color",
            "shine", "size", "hose_size", "flow_growth", "hose_flow_growth", "count", "spin", "stretch", "fresh_stretch", "shed_chance",
            "smoke_chance", "mist", "splash");
    private static final Set<String> SPLASH_FIELDS = Set.of("chance", "count", "speed", "lift", "size", "lifetime");
    // * Which settings each style actually uses, so a file is told when it sets one that does nothing
    private static final Map<Style, Set<String>> UNUSED = Map.of(
            Style.SPRAY, Set.of("palette", "shine_color", "shine", "stretch", "fresh_stretch", "shed_chance", "smoke_chance"),
            Style.GLOB, Set.of("palette", "stretch", "fresh_stretch", "shed_chance", "smoke_chance", "mist"),
            Style.MOLTEN, Set.of("tint", "shine_color", "shine", "shed_chance", "mist"),
            Style.VISCOUS, Set.of("palette", "smoke_chance", "mist"));
    private static final String GLOSS = "_gloss";
    private static final String GLOW = "_glow";
    private static final String FLOW = "_flow";
    private static final String PAINT_GROUP = "@paint";

    public final ResourceLocation id;
    public final Style style;
    final int priority;
    private final List<ResourceLocation> fluidIds;
    private final List<TagKey<Fluid>> fluidTags;
    private final boolean paintGroup;

    // * Sprite names in the particle atlas
    private final ResourceLocation[] bodies;
    private final ResourceLocation[] gloss;
    private final ResourceLocation[] glow;
    private final ResourceLocation[] flow;
    @Nullable
    private TextureAtlasSprite[][] sprites;

    private final Tint tint;
    private final int tintRgb;
    @Nullable
    private final int[] palette;
    @Nullable
    private final Boolean emissive;
    @Nullable
    private final Integer shineRgb;
    final float shine;

    final float size;
    final float hoseSize;
    final float flowGrowth;
    final float hoseFlowGrowth;
    final int minCount;
    final int extraCount;
    // * Radians a tick at most, or rocking either side of upright
    final float spin;
    final boolean rock;
    final float stretch;
    final float freshStretch;
    final float shed;
    final float smoke;
    final float mist;

    final float splashChance;
    final int minSplash;
    final int extraSplash;
    final double splashSpeed;
    final double extraSplashSpeed;
    final double splashLift;
    final double extraSplashLift;
    final float minSplashSize;
    final float extraSplashSize;
    final int minSplashLifetime;
    final int extraSplashLifetime;

    private SprayEffect(final ResourceLocation id, final JsonObject json, final Set<ResourceLocation> textures, final boolean fallback,
                        final Consumer<String> warn) {
        this.id = id;
        for (final String key : json.keySet()) {
            if (!FIELDS.contains(key)) {
                warn.accept("unknown setting \"" + key + "\", it does nothing (a typo?)");
            }
        }
        this.style = style(json);
        final JsonObject defaults = DEFAULTS.get(this.style);
        for (final String key : UNUSED.get(this.style)) {
            if (json.has(key)) {
                warn.accept("\"" + key + "\" does nothing for the " + this.style.name().toLowerCase(Locale.ROOT) + " style");
            }
        }
        this.priority = json.has("priority") ? (int) asNumber(json.get("priority"), "priority") : 0;

        // --- fluids ---
        this.fluidIds = new ArrayList<>();
        this.fluidTags = new ArrayList<>();
        boolean paint = false;
        if (json.has("fluids")) {
            for (final JsonElement entry : json.getAsJsonArray("fluids")) {
                final String name = entry.getAsString();
                if (name.equals(PAINT_GROUP)) {
                    paint = true;
                    continue;
                }
                final boolean tag = name.startsWith("#");
                final ResourceLocation location = ResourceLocation.tryParse(tag ? name.substring(1) : name);
                if (location == null) {
                    warn.accept("\"" + name + "\" in fluids isn't a fluid ID or #tag, skipped");
                } else if (tag) {
                    this.fluidTags.add(TagKey.create(Registries.FLUID, location));
                } else {
                    this.fluidIds.add(location);
                }
            }
        } else if (!fallback) {
            throw new JsonParseException("no \"fluids\" list, so it can't apply to any fluid");
        }
        this.paintGroup = paint;

        // --- textures ---
        if (!json.has("textures")) {
            throw new JsonParseException("no \"textures\" folder");
        }
        final ResourceLocation folder = ResourceLocation.tryParse(json.get("textures").getAsString());
        if (folder == null) {
            throw new JsonParseException("\"textures\" isn't a namespace:folder");
        }
        final String prefix = folder.getPath().endsWith("/") ? folder.getPath() : folder.getPath() + "/";
        final List<String> names = new ArrayList<>();
        for (final ResourceLocation texture : textures) {
            if (texture.getNamespace().equals(folder.getNamespace()) && texture.getPath().startsWith(prefix)
                    && texture.getPath().indexOf('/', prefix.length()) < 0) {
                names.add(texture.getPath().substring(prefix.length()));
            }
        }
        names.sort(NATURAL);
        final List<String> bodyNames = new ArrayList<>();
        for (final String name : names) {
            if (this.style == Style.SPRAY || !(name.endsWith(GLOSS) || name.endsWith(GLOW) || name.endsWith(FLOW))) {
                bodyNames.add(name);
            }
        }
        if (bodyNames.isEmpty()) {
            throw new JsonParseException("no textures in assets/" + folder.getNamespace() + "/textures/hose_spray/" + prefix
                    + (names.isEmpty() ? "" : " other than layers; each shape needs a plain body texture too"));
        }
        final int count = bodyNames.size();
        this.bodies = new ResourceLocation[count];
        this.gloss = new ResourceLocation[count];
        this.glow = new ResourceLocation[count];
        this.flow = new ResourceLocation[count];
        final List<String> noGloss = new ArrayList<>();
        final List<String> noGlow = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final String body = bodyNames.get(i);
            this.bodies[i] = sprite(folder, prefix + body);
            this.gloss[i] = names.contains(body + GLOSS) ? sprite(folder, prefix + body + GLOSS) : null;
            this.glow[i] = names.contains(body + GLOW) ? sprite(folder, prefix + body + GLOW) : null;
            this.flow[i] = names.contains(body + FLOW) ? sprite(folder, prefix + body + FLOW) : null;
            if (this.style == Style.MOLTEN && (this.glow[i] == null || this.flow[i] == null)) {
                noGlow.add(body);
            }
            if ((this.style == Style.GLOB || this.style == Style.VISCOUS) && this.gloss[i] == null) {
                noGloss.add(body);
            }
        }
        if (!noGlow.isEmpty()) {
            warn.accept(String.join(", ", noGlow) + " need both a " + GLOW + " and a " + FLOW + " texture to glow");
        }
        if (!noGloss.isEmpty()) {
            warn.accept(String.join(", ", noGloss) + " have no " + GLOSS + " texture, so they won't shine");
        }

        // --- look ---
        final String tintName = string(json, defaults, "tint", "none");
        if (tintName.startsWith("#")) {
            this.tint = Tint.FIXED;
            this.tintRgb = color(tintName, "tint");
        } else {
            this.tint = switch (tintName) {
                case "fluid" -> Tint.FLUID;
                case "dye" -> Tint.DYE;
                case "none" -> Tint.NONE;
                default -> throw new JsonParseException("\"tint\" must be \"fluid\", \"dye\", \"none\" or a #RRGGBB colour");
            };
            this.tintRgb = 0xFFFFFF;
        }
        if (json.has("palette")) {
            final JsonArray colors = json.getAsJsonArray("palette");
            if (colors.size() != 4) {
                throw new JsonParseException("\"palette\" needs exactly 4 colours, darkest to brightest");
            }
            this.palette = new int[4];
            for (int i = 0; i < 4; i++) {
                this.palette[i] = color(colors.get(i).getAsString(), "palette");
            }
        } else {
            this.palette = null;
        }
        final JsonElement glows = value(json, defaults, "emissive");
        this.emissive = glows.isJsonPrimitive() && glows.getAsJsonPrimitive().isBoolean() ? glows.getAsBoolean() : null;
        final String shineName = string(json, defaults, "shine_color", "#FFFFFF");
        this.shineRgb = shineName.equals("auto") ? null : color(shineName, "shine_color");
        this.shine = number(json, defaults, "shine", 1.0F);

        // --- motion ---
        this.size = number(json, defaults, "size", 0.2F);
        this.hoseSize = number(json, defaults, "hose_size", this.size);
        this.flowGrowth = number(json, defaults, "flow_growth", 1.0F);
        this.hoseFlowGrowth = number(json, defaults, "hose_flow_growth", 1.0F);
        final float[] counts = range(json, defaults, "count");
        this.minCount = Math.max(1, (int) counts[0]);
        this.extraCount = Math.max(0, (int) counts[1]);
        final JsonElement spin = value(json, defaults, "spin");
        this.rock = spin.isJsonPrimitive() && spin.getAsJsonPrimitive().isString() && spin.getAsString().equals("rock");
        if (this.rock && this.style != Style.VISCOUS) {
            throw new JsonParseException("\"spin\": \"rock\" only works with the viscous style; use a number");
        }
        this.spin = this.rock ? 0.0F : asNumber(spin, "spin");
        this.stretch = number(json, defaults, "stretch", 0.0F);
        this.freshStretch = number(json, defaults, "fresh_stretch", 0.0F);
        this.shed = number(json, defaults, "shed_chance", 0.0F);
        this.smoke = number(json, defaults, "smoke_chance", 0.0F);
        this.mist = number(json, defaults, "mist", 0.0F);

        // --- splash ---
        final JsonObject splash = json.has("splash") ? json.getAsJsonObject("splash") : new JsonObject();
        final JsonObject splashDefaults = defaults.getAsJsonObject("splash");
        for (final String key : splash.keySet()) {
            if (!SPLASH_FIELDS.contains(key)) {
                warn.accept("unknown splash setting \"" + key + "\", it does nothing (a typo?)");
            }
        }
        this.splashChance = number(splash, splashDefaults, "chance", 1.0F);
        final float[] splashCount = range(splash, splashDefaults, "count");
        this.minSplash = (int) splashCount[0];
        this.extraSplash = (int) splashCount[1];
        final float[] speed = range(splash, splashDefaults, "speed");
        this.splashSpeed = speed[0];
        this.extraSplashSpeed = speed[1];
        final float[] lift = range(splash, splashDefaults, "lift");
        this.splashLift = lift[0];
        this.extraSplashLift = lift[1];
        final float[] sizes = range(splash, splashDefaults, "size");
        this.minSplashSize = sizes[0];
        this.extraSplashSize = sizes[1];
        final float[] lifetime = range(splash, splashDefaults, "lifetime");
        this.minSplashLifetime = Math.max(1, (int) lifetime[0]);
        this.extraSplashLifetime = Math.max(0, (int) lifetime[1]);
    }

    // * Reads one file
    static SprayEffect parse(final ResourceLocation id, final JsonObject json, final Set<ResourceLocation> textures, final boolean fallback,
                             final Consumer<String> warn) {
        try {
            return new SprayEffect(id, json, textures, fallback, warn);
        } catch (final IllegalStateException | UnsupportedOperationException | ClassCastException | NumberFormatException exception) {
            throw new JsonParseException("a setting has the wrong kind of value (" + exception.getMessage() + ")");
        }
    }

    // --- matching --- //

    // * 2 for a fluid named outright, 1 for one in a listed tag or group, 0 if it doesn't apply
    int match(final Fluid fluid) {
        final ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid);
        final Fluid source = fluid instanceof final FlowingFluid flowing ? flowing.getSource() : fluid;
        if (this.fluidIds.contains(key) || this.fluidIds.contains(BuiltInRegistries.FLUID.getKey(source))) {
            return 2;
        }
        for (final TagKey<Fluid> tag : this.fluidTags) {
            if (fluid.is(tag)) {
                return 1;
            }
        }
        return this.paintGroup && PaintColors.of(fluid) != null ? 1 : 0;
    }

    // --- textures --- //

    public int shapes() {
        return this.bodies.length;
    }

    TextureAtlasSprite body(final int shape) {
        return this.sprites()[0][shape];
    }

    @Nullable
    TextureAtlasSprite gloss(final int shape) {
        return this.sprites()[1][shape];
    }

    @Nullable
    TextureAtlasSprite glow(final int shape) {
        return this.sprites()[2][shape];
    }

    @Nullable
    TextureAtlasSprite flow(final int shape) {
        return this.sprites()[3][shape];
    }

    // * Looked up the first time they're needed, once the particle atlas has been stitched
    private TextureAtlasSprite[][] sprites() {
        if (this.sprites == null) {
            final AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
            final TextureAtlas atlas = (TextureAtlas) texture;
            final ResourceLocation[][] names = {this.bodies, this.gloss, this.glow, this.flow};
            final TextureAtlasSprite[][] found = new TextureAtlasSprite[4][this.bodies.length];
            for (int layer = 0; layer < 4; layer++) {
                for (int i = 0; i < this.bodies.length; i++) {
                    found[layer][i] = names[layer][i] == null ? null : atlas.getSprite(names[layer][i]);
                }
            }
            this.sprites = found;
        }
        return this.sprites;
    }

    // --- colour --- //

    public int color(final FluidStack fluid) {
        return switch (this.tint) {
            case FLUID -> FluidSprayColors.of(fluid);
            case DYE -> {
                final DyeColor dye = PaintColors.of(fluid);
                yield dye != null ? dye.getTextureDiffuseColor() : FluidSprayColors.of(fluid);
            }
            case NONE -> 0xFFFFFF;
            case FIXED -> this.tintRgb;
        };
    }

    public int[] palette(final FluidStack fluid) {
        return this.palette != null ? this.palette : FluidSprayColors.palette(fluid);
    }

    // * Lit up at full brightness, or only when the fluid gives off light if left to "auto"
    public boolean emissive(final FluidStack fluid) {
        return this.emissive != null ? this.emissive : FluidSprayColors.glows(fluid);
    }

    int shine(final int rgb) {
        return this.shineRgb != null ? this.shineRgb : DyeFluidsClient.gloss(rgb);
    }

    float maxStretch() {
        return this.stretch + this.freshStretch;
    }

    // --- reading --- //

    private static Style style(final JsonObject json) {
        if (!json.has("style")) {
            throw new JsonParseException("no \"style\"; use \"spray\", \"glob\", \"molten\" or \"viscous\"");
        }
        try {
            return Style.valueOf(json.get("style").getAsString().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            throw new JsonParseException("unknown style \"" + json.get("style").getAsString()
                    + "\"; use \"spray\", \"glob\", \"molten\" or \"viscous\"");
        }
    }

    private static ResourceLocation sprite(final ResourceLocation folder, final String path) {
        return ResourceLocation.fromNamespaceAndPath(folder.getNamespace(), "hose_spray/" + path);
    }

    private static JsonElement value(final JsonObject json, final JsonObject defaults, final String key) {
        return json.has(key) ? json.get(key) : defaults.get(key);
    }

    private static float number(final JsonObject json, final JsonObject defaults, final String key, final float fallback) {
        final JsonElement value = value(json, defaults, key);
        return value == null ? fallback : asNumber(value, key);
    }

    private static float asNumber(final JsonElement value, final String key) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("\"" + key + "\" should be a number, not " + value);
        }
        return value.getAsFloat();
    }

    private static String string(final JsonObject json, final JsonObject defaults, final String key, final String fallback) {
        final JsonElement value = value(json, defaults, key);
        return value == null ? fallback : value.getAsString();
    }

    // * A single number, or [least, extra]
    private static float[] range(final JsonObject json, final JsonObject defaults, final String key) {
        final JsonElement value = value(json, defaults, key);
        if (value == null) {
            return new float[]{0.0F, 0.0F};
        }
        if (value.isJsonArray()) {
            final JsonArray array = value.getAsJsonArray();
            if (array.size() != 2) {
                throw new JsonParseException("\"" + key + "\" needs one number or two, [least, extra]");
            }
            return new float[]{asNumber(array.get(0), key), asNumber(array.get(1), key)};
        }
        return new float[]{asNumber(value, key), 0.0F};
    }

    private static int color(final String hex, final String key) {
        if (!hex.matches("#[0-9a-fA-F]{6}")) {
            throw new JsonParseException("\"" + key + "\" colour \"" + hex + "\" must look like #RRGGBB");
        }
        return Integer.parseInt(hex.substring(1), 16);
    }

    // * glob_2 before glob_10
    private static final Comparator<String> NATURAL = (a, b) -> {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            final char ca = a.charAt(i);
            final char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int ei = i;
                int ej = j;
                while (ei < a.length() && Character.isDigit(a.charAt(ei))) {
                    ei++;
                }
                while (ej < b.length() && Character.isDigit(b.charAt(ej))) {
                    ej++;
                }
                final int compared = Long.compare(Long.parseLong(a.substring(i, Math.min(ei, i + 18))),
                        Long.parseLong(b.substring(j, Math.min(ej, j + 18))));
                if (compared != 0) {
                    return compared;
                }
                i = ei;
                j = ej;
            } else {
                if (ca != cb) {
                    return Character.compare(ca, cb);
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    };
}
