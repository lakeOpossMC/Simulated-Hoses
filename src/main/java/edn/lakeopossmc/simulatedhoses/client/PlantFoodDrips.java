package edn.lakeopossmc.simulatedhoses.client;

import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.content.fluids.particle.FluidParticleData;
import edn.lakeopossmc.simulatedhoses.HoseFluids;
import edn.lakeopossmc.simulatedhoses.fluids.PlantFoodCoating;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;

// --- PLANT FOOD DRIPPING OFF COATED PLAYERS --- //
public final class PlantFoodDrips {
    private static final float DRIP_CHANCE = 0.35F;
    private static final double OWN_DRIP_TOP = 0.6;

    public static void tick() {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            return;
        }
        final RandomSource random = level.random;
        FluidParticleData drip = null;
        for (final int id : PlantFoodCoating.clientCoated()) {
            final Entity entity = level.getEntity(id);
            if (entity == null) {
                continue;
            }
            final float strength = PlantFoodCoating.strength(entity);
            if (strength <= 0.0F || entity.isInvisible() || random.nextFloat() >= DRIP_CHANCE * strength) {
                continue;
            }
            if (drip == null) {
                drip = new FluidParticleData(AllParticleTypes.FLUID_DRIP.get(), new FluidStack(HoseFluids.PLANT_FOOD.get(), 1));
            }
            final AABB box = entity.getBoundingBox();
            final double top = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson()
                    ? box.minY + box.getYsize() * OWN_DRIP_TOP : box.maxY;
            final double x = box.minX + random.nextDouble() * box.getXsize();
            final double y = box.minY + random.nextDouble() * (top - box.minY);
            final double z = box.minZ + random.nextDouble() * box.getZsize();
            level.addParticle(drip, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    private PlantFoodDrips() {
    }
}
