package edn.lakeopossmc.simulatedhoses.client;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.fluids.potion.PotionFluid;
import edn.lakeopossmc.simulatedhoses.HoseFluids;
import edn.lakeopossmc.simulatedhoses.blocks.PressureNozzleBlockEntity;
import edn.lakeopossmc.simulatedhoses.fluids.PlantFoodSounds;
import edn.lakeopossmc.simulatedhoses.hose.NozzleJet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Map;
import java.util.WeakHashMap;

// --- SPRAY NOISE OF A PRESSURE NOZZLE --- //
public final class NozzleSpraySound {
    private static final Map<PressureNozzleBlockEntity, Playing> PLAYING = new WeakHashMap<>();

    private static final float FLOW_MIN_VOLUME = 0.12F;
    private static final float FLOW_MAX_VOLUME = 0.33F;
    private static final float BUBBLE_MIN_VOLUME = 0.09F;
    private static final float BUBBLE_MAX_VOLUME = 0.27F;
    private static final float MIXING_MIN_VOLUME = 0.12F;
    private static final float MIXING_MAX_VOLUME = 0.34F;
    private static final float MIXING_MIN_PITCH = 0.75F;
    private static final float MIXING_MAX_PITCH = 1.5F;
    private static final float HISS_FROM = 0.35F;
    private static final float HISS_MAX_VOLUME = 0.32F;
    private static final float RAIN_MAX_VOLUME = 0.16F;

    private static final float LAVA_POP_CHANCE = 1.0F / 12.0F;
    private static final float POTION_BREW_CHANCE = 1.0F / 40.0F;
    private static final float PLANT_FOOD_SQUISH_CHANCE = 1.0F / 14.0F;

    private enum Kind {
        WATER(1.0F),
        LAVA(0.75F),
        POTION(1.12F),
        PLANT_FOOD(0.95F);

        // * Multiplies every layer's pitch
        private final float pitch;

        Kind(final float pitch) {
            this.pitch = pitch;
        }

        private static Kind of(final FluidStack fluid) {
            if (fluid.is(FluidTags.LAVA)) {
                return LAVA;
            }
            if (fluid.is(HoseFluids.PLANT_FOOD.get())) {
                return PLANT_FOOD;
            }
            return fluid.getFluid() instanceof PotionFluid ? POTION : WATER;
        }
    }

    private record Playing(Kind kind, Loop[] loops) {
        private boolean anyStopped() {
            for (final Loop loop : this.loops) {
                if (loop.isStopped()) {
                    return true;
                }
            }
            return false;
        }

        private void stop() {
            for (final Loop loop : this.loops) {
                Minecraft.getInstance().getSoundManager().stop(loop);
            }
        }
    }

    // * Called every client tick while the nozzle sprays
    public static void tick(final PressureNozzleBlockEntity nozzle, final double tipOffset) {
        final Kind kind = Kind.of(nozzle.getSprayFluid());
        final Playing playing = PLAYING.get(nozzle);
        if (playing == null || playing.kind() != kind || playing.anyStopped()) {
            if (playing != null) {
                playing.stop();
            }
            start(nozzle, tipOffset, kind);
        }
        oneOffs(nozzle, tipOffset, kind);
    }

    private static void start(final PressureNozzleBlockEntity nozzle, final double tipOffset, final Kind kind) {
        final SoundEvent flow = kind == Kind.LAVA ? SoundEvents.LAVA_AMBIENT : SoundEvents.WATER_AMBIENT;
        final Loop[] loops = {
                new Loop(nozzle, tipOffset, flow, 0.0F, FLOW_MIN_VOLUME, FLOW_MAX_VOLUME, kind.pitch * 1.0F, kind.pitch * 1.7F),
                new Loop(nozzle, tipOffset, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.0F, BUBBLE_MIN_VOLUME, BUBBLE_MAX_VOLUME, kind.pitch * 1.2F, kind.pitch * 1.6F),
                new Loop(nozzle, tipOffset, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 0.0F, MIXING_MIN_VOLUME, MIXING_MAX_VOLUME, kind.pitch * MIXING_MIN_PITCH, kind.pitch * MIXING_MAX_PITCH),
                new Loop(nozzle, tipOffset, AllSoundEvents.STEAM.getMainEvent(), HISS_FROM, 0.0F, HISS_MAX_VOLUME, kind.pitch * 1.3F, kind.pitch * 1.8F),
                new Loop(nozzle, tipOffset, SoundEvents.WEATHER_RAIN, HISS_FROM, 0.0F, RAIN_MAX_VOLUME, kind.pitch * 1.2F, kind.pitch * 1.7F)
        };
        for (final Loop loop : loops) {
            Minecraft.getInstance().getSoundManager().play(loop);
        }
        PLAYING.put(nozzle, new Playing(kind, loops));
    }

    private static void oneOffs(final PressureNozzleBlockEntity nozzle, final double tipOffset, final Kind kind) {
        final Level level = nozzle.getLevel();
        if (level == null) {
            return;
        }
        final RandomSource random = level.random;
        final float fraction = fraction(nozzle);
        if (kind == Kind.LAVA && random.nextFloat() < LAVA_POP_CHANCE) {
            final Vec3 tip = tip(nozzle, tipOffset);
            level.playLocalSound(tip.x, tip.y, tip.z, SoundEvents.LAVA_POP, SoundSource.BLOCKS,
                    0.1F + fraction * 0.12F, 0.8F + random.nextFloat() * 0.4F, false);
        } else if (kind == Kind.POTION && random.nextFloat() < POTION_BREW_CHANCE) {
            final Vec3 tip = tip(nozzle, tipOffset);
            level.playLocalSound(tip.x, tip.y, tip.z, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS,
                    0.06F + fraction * 0.06F, 1.3F + random.nextFloat() * 0.3F, false);
        } else if (kind == Kind.PLANT_FOOD && random.nextFloat() < PLANT_FOOD_SQUISH_CHANCE) {
            PlantFoodSounds.squishLocal(level, tip(nozzle, tipOffset), 0.4F + fraction * 0.3F);
        }
    }

    private static float fraction(final PressureNozzleBlockEntity nozzle) {
        return Mth.clamp(nozzle.getFlow() / PressureNozzleBlockEntity.FULL_FLOW, 0.0F, 1.0F);
    }

    private static Vec3 tip(final PressureNozzleBlockEntity nozzle, final double tipOffset) {
        return NozzleJet.aim(nozzle.getLevel(), nozzle.getBlockPos(), nozzle.getFacing(), tipOffset).start();
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final PressureNozzleBlockEntity nozzle;
        private final double tipOffset;
        private final float fadeFrom;
        private final float minVolume;
        private final float maxVolume;
        private final float minPitch;
        private final float maxPitch;

        private Loop(final PressureNozzleBlockEntity nozzle, final double tipOffset, final SoundEvent sound, final float fadeFrom,
                     final float minVolume, final float maxVolume, final float minPitch, final float maxPitch) {
            super(sound, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.nozzle = nozzle;
            this.tipOffset = tipOffset;
            this.fadeFrom = fadeFrom;
            this.minVolume = minVolume;
            this.maxVolume = maxVolume;
            this.minPitch = minPitch;
            this.maxPitch = maxPitch;
            this.looping = true;
            this.delay = 0;
            this.update();
        }

        @Override
        public void tick() {
            if (this.nozzle.getLevel() == null || this.nozzle.isRemoved() || !this.nozzle.isSpraying()) {
                this.stop();
                return;
            }
            this.update();
        }

        private void update() {
            if (this.nozzle.getLevel() == null) {
                return;
            }
            final float fraction = fraction(this.nozzle);
            final float strength = this.fadeFrom <= 0.0F ? fraction
                    : Mth.clamp((fraction - this.fadeFrom) / (1.0F - this.fadeFrom), 0.0F, 1.0F);
            this.volume = Mth.lerp(strength, this.minVolume, this.maxVolume);
            this.pitch = Mth.lerp(fraction, this.minPitch, this.maxPitch);
            final Vec3 tip = tip(this.nozzle, this.tipOffset);
            this.x = tip.x;
            this.y = tip.y;
            this.z = tip.z;
        }
    }

    private NozzleSpraySound() {
    }
}
