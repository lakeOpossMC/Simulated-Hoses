package edn.lakeopossmc.simulatedhoses.client;

import com.simibubi.create.content.fluids.particle.FluidStackParticle;
import edn.lakeopossmc.simulatedhoses.hose.NozzleJet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.fluids.FluidStack;

// --- ONE DROP OF A PRESSURE NOZZLE'S JET --- //
public class NozzleJetParticle extends FluidStackParticle {

    public NozzleJetParticle(final ClientLevel level, final FluidStack fluid, final double x, final double y, final double z,
                             final double vx, final double vy, final double vz, final int lifetime) {
        super(level, fluid, x, y, z, vx, vy, vz);
        this.lifetime = lifetime;
        this.gravity = (float) (NozzleJet.GRAVITY / 0.04);
        this.friction = NozzleJet.DRAG;
    }

    @Override
    public void tick() {
        final double vx = this.xd;
        final double vy = this.yd;
        final double vz = this.zd;
        super.tick();
        final boolean collided = this.onGround
                || (vx != 0.0 && this.xd == 0.0)
                || (vz != 0.0 && this.zd == 0.0)
                || (vy > 0.0 && this.yd == 0.0);
        if (!this.removed && collided) {
            this.remove();
        }
    }
}
