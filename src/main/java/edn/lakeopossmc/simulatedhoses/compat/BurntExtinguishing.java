package edn.lakeopossmc.simulatedhoses.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

// --- PUTTING OUT BURNT'S FIRE --- //
// * With Burnt installed, water sprayed from a nozzle or Super Soaker puts out its burning blocks
public final class BurntExtinguishing {
    public static final String BURNT = "burnt";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String BLOCKS_CLASS = "net.pixelbank.burnt.procedures.ExtinguishBlockProcedure";
    private static final String ENTITY_CLASS = "net.pixelbank.burnt.procedures.ExtinguishEntityProcedure";

    @Nullable
    private static MethodHandle blocks;
    @Nullable
    private static MethodHandle entity;

    static {
        if (ModList.get().isLoaded(BURNT)) {
            final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            try {
                // * (level, x, y, z, splash particles)
                blocks = lookup.findStatic(Class.forName(BLOCKS_CLASS), "execute", MethodType.methodType(void.class,
                        LevelAccessor.class, double.class, double.class, double.class, boolean.class));
                entity = lookup.findStatic(Class.forName(ENTITY_CLASS), "execute", MethodType.methodType(void.class,
                        LevelAccessor.class, Entity.class));
            } catch (final ReflectiveOperationException | LinkageError e) {
                blocks = null;
                entity = null;
                LOGGER.warn("Burnt is installed but its extinguisher couldn't be found, so sprayed water won't put out its fire: {}", e.toString());
            }
        }
    }

    public static boolean isActive() {
        return blocks != null;
    }

    // * Everything within the splash
    public static void douse(final ServerLevel level, final AABB area, @Nullable final BlockHitResult block) {
        if (!isActive()) {
            return;
        }
        try {
            for (final Entity target : level.getEntities((Entity) null, area, Entity::isAlive)) {
                entity.invokeExact((LevelAccessor) level, target);
            }
            if (block != null) {
                final BlockPos pos = block.getBlockPos();
                blocks.invokeExact((LevelAccessor) level, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), false);
            }
        } catch (final Throwable e) {
            blocks = null;
            entity = null;
            LOGGER.warn("Putting out Burnt's fire failed, so sprayed water won't anymore: {}", e.toString());
        }
    }

    private BurntExtinguishing() {
    }
}
