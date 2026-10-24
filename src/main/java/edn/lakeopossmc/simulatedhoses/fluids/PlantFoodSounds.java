package edn.lakeopossmc.simulatedhoses.fluids;

import edn.lakeopossmc.simulatedhoses.HoseFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

// --- PLANT FOOD'S SLIMY SQUELCH --- //
public final class PlantFoodSounds {
    private static final float VOLUME = 0.25F;
    private static final float MIN_PITCH = 0.6F;
    private static final float PITCH_RANGE = 0.2F;
    private static final float STEP_VOLUME = 0.3F;

    // * Moving through it squishes now and then
    private static final double MOVING_SPEED = 0.03;
    private static final float WADE_CHANCE_PER_SPEED = 0.6F;
    private static final float MAX_WADE_CHANCE = 1.0F / 6.0F;

    private static final Map<Entity, Boolean> WAS_IN = new WeakHashMap<>();

    public static void squish(final LevelAccessor level, @Nullable final Player player, final double x, final double y, final double z, final float volume) {
        final RandomSource random = level.getRandom();
        level.playSound(player, BlockPos.containing(x, y, z), SoundEvents.HONEY_BLOCK_STEP, SoundSource.BLOCKS,
                VOLUME * volume, MIN_PITCH + random.nextFloat() * PITCH_RANGE);
    }

    // * For the client only
    public static void squishLocal(final Level level, final Vec3 at, final float volume) {
        final RandomSource random = level.getRandom();
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_STEP, SoundSource.BLOCKS,
                VOLUME * volume, MIN_PITCH + random.nextFloat() * PITCH_RANGE, false);
    }

    // * Played through the entity
    public static void footstep(final Entity entity) {
        final RandomSource random = entity.level().getRandom();
        entity.playSound(SoundEvents.HONEY_BLOCK_STEP, STEP_VOLUME, MIN_PITCH + random.nextFloat() * PITCH_RANGE);
    }

    public static void onEntityTick(final EntityTickEvent.Post event) {
        final Entity entity = event.getEntity();
        final Level level = entity.level();
        if (level.isClientSide() || entity.isSpectator()) {
            return;
        }
        final boolean in = entity.isInFluidType(HoseFluids.PLANT_FOOD_TYPE.get());
        final boolean was = in ? Boolean.TRUE.equals(WAS_IN.put(entity, Boolean.TRUE)) : WAS_IN.remove(entity) != null;
        if (in != was) {
            // * Stepping in or climbing out
            squish(level, null, entity.getX(), entity.getY(), entity.getZ(), 1.0F);
            return;
        }
        if (!in) {
            return;
        }
        final double speed = entity.getDeltaMovement().horizontalDistance();
        if (speed > MOVING_SPEED && level.random.nextFloat() < Math.min(MAX_WADE_CHANCE, (float) speed * WADE_CHANCE_PER_SPEED)) {
            squish(level, null, entity.getX(), entity.getY(), entity.getZ(), 0.7F);
        }
    }

    private PlantFoodSounds() {
    }
}
