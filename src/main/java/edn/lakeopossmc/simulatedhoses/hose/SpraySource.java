package edn.lakeopossmc.simulatedhoses.hose;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

// --- ANYTHING THAT SPRAYS A JET --- //
// * A pressure nozzle or a Super Soaker. The spray noise follows whichever one it belongs to
public interface SpraySource {
    @Nullable
    Level getLevel();

    boolean isRemoved();

    boolean isSpraying();

    FluidStack getSprayFluid();

    // * Flow as a share of a full-speed nozzle, 0 to 1
    float getSprayFraction();

    Vec3 getSprayTip();

    default SoundSource getSpraySoundSource() {
        return SoundSource.BLOCKS;
    }
}
