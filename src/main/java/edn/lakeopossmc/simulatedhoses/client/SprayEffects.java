package edn.lakeopossmc.simulatedhoses.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// --- EVERY FLUID'S SPRAY, FROM RESOURCE PACKS --- //
// * Loads assets/<namespace>/hose_sprays/*.json and the textures under assets/<namespace>/textures/hose_spray/
// * A fluid no file covers uses simulatedhoses:default
public final class SprayEffects extends SimplePreparableReloadListener<SprayEffects.Found> {
    public static final SprayEffects INSTANCE = new SprayEffects();
    public static final String DIRECTORY = "hose_sprays";
    private static final FileToIdConverter TEXTURES = new FileToIdConverter("textures/hose_spray", ".png");
    private static final ResourceLocation FALLBACK = ResourceLocation.fromNamespaceAndPath(SimulatedHosesMod.MOD_ID, "default");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final Comparator<SprayEffect> ORDER = Comparator.<SprayEffect>comparingInt(effect -> -effect.priority)
            .thenComparing(effect -> effect.id.toString());

    private static volatile List<SprayEffect> effects = List.of();
    private static volatile Map<ResourceLocation, SprayEffect> byId = Map.of();
    @Nullable
    private static volatile SprayEffect fallback;
    private static final Map<Fluid, Optional<SprayEffect>> CACHE = new ConcurrentHashMap<>();

    record Found(Map<ResourceLocation, JsonElement> files, Set<ResourceLocation> textures) {
    }

    @Nullable
    public static SprayEffect of(final FluidStack fluid) {
        if (fluid.isEmpty()) {
            return null;
        }
        return CACHE.computeIfAbsent(fluid.getFluid(), SprayEffects::find).orElse(fallback);
    }

    @Nullable
    public static SprayEffect builtin(final String name) {
        return byId.get(ResourceLocation.fromNamespaceAndPath(SimulatedHosesMod.MOD_ID, name));
    }

    private static Optional<SprayEffect> find(final Fluid fluid) {
        SprayEffect best = null;
        int bestMatch = 0;
        for (final SprayEffect effect : effects) {
            if (best != null && effect.priority < best.priority) {
                break;
            }
            final int match = effect.match(fluid);
            if (match > bestMatch) {
                best = effect;
                bestMatch = match;
            }
        }
        return Optional.ofNullable(best);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    @Override
    protected Found prepare(final ResourceManager resources, final ProfilerFiller profiler) {
        final Map<ResourceLocation, JsonElement> files = new HashMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(resources, DIRECTORY, GSON, files);
        final Set<ResourceLocation> textures = new HashSet<>();
        for (final ResourceLocation file : TEXTURES.listMatchingResources(resources).keySet()) {
            textures.add(TEXTURES.fileToId(file));
        }
        return new Found(files, textures);
    }

    @Override
    protected void apply(final Found found, final ResourceManager resources, final ProfilerFiller profiler) {
        final List<SprayEffect> loaded = new ArrayList<>();
        final Map<ResourceLocation, SprayEffect> named = new HashMap<>();
        SprayEffect loadedFallback = null;
        for (final Map.Entry<ResourceLocation, JsonElement> file : found.files().entrySet()) {
            final ResourceLocation id = file.getKey();
            final String where = "assets/" + id.getNamespace() + "/" + DIRECTORY + "/" + id.getPath() + ".json";
            final boolean isFallback = id.equals(FALLBACK);
            try {
                if (!file.getValue().isJsonObject()) {
                    throw new JsonParseException("it should be a { } object");
                }
                final SprayEffect effect = SprayEffect.parse(id, file.getValue().getAsJsonObject(), found.textures(), isFallback,
                        problem -> LOGGER.warn("[Simulated Hoses] Spray effect {}: {}", where, problem));
                named.put(id, effect);
                if (isFallback) {
                    loadedFallback = effect;
                } else {
                    loaded.add(effect);
                }
            } catch (final JsonParseException | IllegalStateException | UnsupportedOperationException exception) {
                LOGGER.error("[Simulated Hoses] Spray effect {} wasn't loaded: {}. Its fluids use the default spray instead.",
                        where, exception.getMessage());
            }
        }
        loaded.sort(ORDER);
        effects = List.copyOf(loaded);
        byId = Map.copyOf(named);
        fallback = loadedFallback;
        clearCache();
        if (loadedFallback == null) {
            LOGGER.error("[Simulated Hoses] The default spray ({}) didn't load, so fluids without their own spray use plain fluid particles.",
                    "assets/simulatedhoses/" + DIRECTORY + "/default.json");
        }
        LOGGER.info("[Simulated Hoses] Loaded {} spray effects", named.size());
    }

    private SprayEffects() {
    }
}
