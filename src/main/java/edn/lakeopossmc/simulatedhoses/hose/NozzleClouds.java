package edn.lakeopossmc.simulatedhoses.hose;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.fluids.potion.PotionFluid;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

// --- LINGERING CLOUDS FROM A PRESSURE NOZZLE --- //
public final class NozzleClouds {
    private static final int FLUID_PER_CLOUD = 250;
    private static final int CLOUD_COOLDOWN = 20;
    private static final double NEARBY_CLOUD_RANGE = 2.5;
    private static final float FRESH_CLOUD_RADIUS = 2.0F;
    private static final TagKey<Fluid> DRAGON_BREATH = FluidTags.create(ResourceLocation.fromNamespaceAndPath("c", "dragon_breath"));

    public static final class Progress {
        private int banked;
        private long lastCloud = -1;
    }

    public static void apply(final ServerLevel level, final NozzleJet.Impact impact, final FluidStack fluid, final int flow,
                             @Nullable final Entity owner, final Progress progress) {
        final CloudKind kind = kindOf(fluid);
        if (kind == null) {
            progress.banked = 0;
            return;
        }
        progress.banked = Math.min(progress.banked + flow, FLUID_PER_CLOUD);
        if (progress.banked < FLUID_PER_CLOUD
                || (progress.lastCloud >= 0 && level.getGameTime() - progress.lastCloud < CLOUD_COOLDOWN)) {
            return;
        }
        final Vec3 at = impact.entity() != null ? impact.entity().position() : impact.point();
        if (hasFreshCloud(level, at)) {
            return;
        }

        final AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
        if (owner instanceof final LivingEntity living) {
            cloud.setOwner(living);
        }
        if (kind == CloudKind.LINGERING) {
            cloud.setRadius(3.0F);
            cloud.setRadiusOnUse(-0.5F);
            cloud.setWaitTime(10);
            cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
            cloud.setPotionContents(fluid.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY));
        } else {
            cloud.setParticle(ParticleTypes.DRAGON_BREATH);
            cloud.setRadius(3.0F);
            cloud.setDuration(600);
            cloud.setRadiusPerTick((7.0F - cloud.getRadius()) / cloud.getDuration());
            cloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1));
        }
        level.addFreshEntity(cloud);
        progress.banked -= FLUID_PER_CLOUD;
        progress.lastCloud = level.getGameTime();
    }

    private enum CloudKind {
        LINGERING,
        DRAGON_BREATH
    }

    @Nullable
    private static CloudKind kindOf(final FluidStack fluid) {
        if (fluid.getFluid() instanceof PotionFluid
                && fluid.getOrDefault(AllDataComponents.POTION_FLUID_BOTTLE_TYPE, PotionFluid.BottleType.REGULAR) == PotionFluid.BottleType.LINGERING
                && fluid.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).hasEffects()) {
            return CloudKind.LINGERING;
        }
        final ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        final String path = id.getPath();
        if (fluid.is(DRAGON_BREATH) || path.contains("dragon_breath") || path.contains("dragons_breath")) {
            return CloudKind.DRAGON_BREATH;
        }
        return null;
    }

    private static boolean hasFreshCloud(final ServerLevel level, final Vec3 at) {
        final AABB area = new AABB(at, at).inflate(NEARBY_CLOUD_RANGE);
        return !level.getEntitiesOfClass(AreaEffectCloud.class, area, cloud -> cloud.getRadius() >= FRESH_CLOUD_RADIUS).isEmpty();
    }

    private NozzleClouds() {
    }
}
