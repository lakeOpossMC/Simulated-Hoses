package edn.lakeopossmc.simulatedhoses.hose;

import dev.simulated_team.simulated.content.particle.MagnetFieldParticleData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.Map;

// --- MAGNET FIELD LINE PARTICLES --- //
public class MagnetFieldEmitter {
    private final Map<Vector3d, Vector3d> magnets;
    private final Vector3d pos;
    private final Vector3d nudge = new Vector3d();
    private final ServerLevel level;
    private final boolean negative;
    private int time;

    private final Vector3d k1 = new Vector3d();
    private final Vector3d k2 = new Vector3d();
    private final Vector3d k3 = new Vector3d();
    private final Vector3d k4 = new Vector3d();
    private final Vector3d temp = new Vector3d();
    private final Vector3d relative = new Vector3d();
    private final Vector3d moment = new Vector3d();
    private final Vector3d field = new Vector3d();

    public MagnetFieldEmitter(final Vector3d start, final Map<Vector3d, Vector3d> magnets, final int lifetime, final ServerLevel level, final boolean negative) {
        this.pos = new Vector3d(start);
        this.magnets = magnets;
        this.time = lifetime;
        this.level = level;
        this.negative = negative;
        this.rk4(this.pos, this.nudge);
    }

    public boolean isDone() {
        return this.time < 0;
    }

    public void update() {
        if (--this.time < 0) {
            return;
        }
        if (!this.level.getBlockState(BlockPos.containing(this.pos.x, this.pos.y, this.pos.z)).isAir()) {
            this.time = -1;
            return;
        }
        this.pos.add(this.nudge);
        this.rk4(this.pos, this.nudge);
        this.level.sendParticles(new MagnetFieldParticleData(this.negative), this.pos.x, this.pos.y, this.pos.z, 1, 0.01, 0.01, 0.01, 0.0);
    }

    private void rk4(final Vector3d at, final Vector3d out) {
        this.field(at, this.k1);
        this.field(at.fma(0.1, this.k1, this.temp), this.k2);
        this.field(at.fma(0.1, this.k2, this.temp), this.k3);
        this.field(at.fma(0.2, this.k3, this.temp), this.k4);
        out.set(this.k1).fma(2.0, this.k2).fma(2.0, this.k3).add(this.k4).mul(1.0 / 30.0);
    }

    private void field(final Vector3d at, final Vector3d out) {
        out.zero();
        for (final Map.Entry<Vector3d, Vector3d> entry : this.magnets.entrySet()) {
            this.relative.set(at).sub(entry.getKey());
            this.moment.set(entry.getValue()).mul(this.negative ? -1.0 : 1.0);
            if (this.moment.lengthSquared() == 0.0) {
                continue;
            }
            final double distanceSq = this.relative.lengthSquared();
            if (distanceSq < 0.2) {
                this.time = -1;
                return;
            }
            final double d = this.moment.dot(this.relative) / distanceSq;
            this.field.set(this.relative).mul(3.0 * d).sub(this.moment).div(distanceSq);
            out.add(this.field);
        }
        out.normalize();
    }
}
