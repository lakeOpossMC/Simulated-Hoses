package edn.lakeopossmc.simulatedhoses.hose;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.MagnetPair;
import edn.lakeopossmc.simulatedhoses.blocks.MagneticHoseConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

// --- PAIR OF MAGNETIC HOSE CONNECTORS --- //
public class MagneticHosePair extends MagnetPair<MagneticHoseConnectorBlockEntity> {
    public static final double MAGNET_STRENGTH = 1000.0;
    public static final double MAGNET_RANGE = 10.0;
    public static final double MAGNET_LINEAR_ACCELERATION_LIMIT = 500.0;
    public static final double MAGNET_ANGULAR_ACCELERATION_LIMIT = 50.0;
    public static final double COUPLING_DISTANCE = 0.5;
    public static final double COUPLING_ANGLE = 20.0;
    public static final boolean MAGNETIC_LOCK = true;

    private static final double NEAR_FULL = 1.5;
    private static final double NEAR_START = 3.0;
    private static final double FAR_PULL = 1.0 / 3.0;

    private final Vector3d v = new Vector3d();
    private final Vector3d velocity1 = new Vector3d();
    private final Vector3d velocity2 = new Vector3d();

    public MagneticHosePair(final Level level, final BlockPos pos1, final BlockPos pos2) {
        super(level, pos1, pos2);
    }

    // * From flange 2 to flange 1, in world space
    public static Vector3d getRelativeTipPosition(final MagneticHoseConnectorBlockEntity magnet1, final MagneticHoseConnectorBlockEntity magnet2, final Vector3d dest) {
        final Vec3 plotPos1 = magnet1.getTipPosition();
        final Vec3 plotPos2 = magnet2.getTipPosition();
        return getRelativePosition(magnet1, magnet2, plotPos1, plotPos2, dest);
    }

    @Override
    public void tick() {
        super.tick();
        this.tryCouple();
    }

    //#region // --- COUPLING --- //
    // * Flanges close enough, and square to each other within the angle tolerance
    private void tryCouple() {
        if (!(this.level.getBlockEntity(this.blockPos1) instanceof final MagneticHoseConnectorBlockEntity a)
                || !(this.level.getBlockEntity(this.blockPos2) instanceof final MagneticHoseConnectorBlockEntity b)) {
            return;
        }
        if (!a.isPowered() || !b.isPowered() || a.isCoupled() || b.isCoupled() || !a.hasOppositePolarity(b)) {
            return;
        }

        final Vector3d offset = getRelativeTipPosition(a, b, new Vector3d());

        // * Leave it to the closest pair if this one is not it
        final double closest = Math.min(a.getClosestPairDistance(), b.getClosestPairDistance());
        if (offset.lengthSquared() > closest * closest * 2) {
            return;
        }

        final double minCos = Math.cos(Math.toRadians(COUPLING_ANGLE));
        final double distanceTolerance = COUPLING_DISTANCE;
        if (offset.lengthSquared() < distanceTolerance * distanceTolerance
                && worldDirection(a, a.getBaseDirection()).dot(worldDirection(b, b.getBaseDirection())) < -minCos
                && worldDirection(a, a.getRedSlotDirection()).dot(worldDirection(b, b.getRedSlotDirection())) > minCos) {
            MagneticHoseConnectorBlockEntity.couple(a, b);
        }
    }
    //#endregion

    //#region // --- FORCES --- //
    @Override
    protected double forceDistanceScale(double distance, final MagneticHoseConnectorBlockEntity magnet1, final MagneticHoseConnectorBlockEntity magnet2) {
        final double scale = this.preventPairFightingScale(distance, magnet1, magnet2);

        distance = 1 + Math.max(0, distance - 0.25);

        return 4 * scale / (distance * Math.sqrt(distance));
    }

    @Override
    protected double torqueDistanceScale(double distance, final MagneticHoseConnectorBlockEntity magnet1, final MagneticHoseConnectorBlockEntity magnet2) {
        final double scale = this.preventPairFightingScale(distance, magnet1, magnet2);

        distance = 1 + Math.max(0, distance - 0.5);
        distance *= distance;

        return scale / distance;
    }

    protected double preventPairFightingScale(final double distance, final MagneticHoseConnectorBlockEntity magnet1, final MagneticHoseConnectorBlockEntity magnet2) {
        final double d = Math.min(magnet1.getClosestPairDistance(), magnet2.getClosestPairDistance());
        if (d < distance * 0.9) {
            final double k = d / (distance + 1E-5);
            return k * k;
        }
        return 1;
    }

    // * Any two powered connectors in range attract
    @Override
    protected boolean canConnect(final Vector3d relativePosition, final Vector3d moment1, final Vector3d moment2) {
        final double range = MAGNET_RANGE;
        return relativePosition.lengthSquared() < range * range;
    }

    private static double nearWeight(final double distance) {
        final double t = Mth.clamp((NEAR_START - distance) / (NEAR_START - NEAR_FULL), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    @Override
    protected Vector3d getForce(final PairData data, final Vector3d f) {
        final double strength = data.moment1().length() * data.moment2().length();
        final double facing = strength > 0 ? Mth.clamp(-data.moment1().dot(data.moment2()) / strength, 0.0, 1.0) : 0.0;
        final double near = nearWeight(data.distance()) * facing;

        f.set(data.moment2()).mul(-0.5 * data.moment1().dot(data.relativePosition()));
        f.fma(-0.5 * data.moment2().dot(data.relativePosition()), data.moment1());
        f.fma(data.moment1().dot(data.moment2()), data.relativePosition());
        f.fma(3 * (data.moment1().dot(data.relativePosition()) * data.moment2().dot(data.relativePosition())) / (data.relativePosition().lengthSquared() + 1E-12), data.relativePosition());
        f.mul(data.forceScale() / 3 * near);

        final double far = 1.0 - near;
        if (far > 0) {
            final double stiffness = strength * data.forceScale() * FAR_PULL;
            f.fma(-stiffness * far, data.relativePosition());
            this.addFarDamping(data, stiffness, far, f);
        }
        return f;
    }

    private void addFarDamping(final PairData data, final double stiffness, final double weight, final Vector3d f) {
        Sable.HELPER.getVelocity(this.level, data.magnet1Pos(), this.velocity1);
        Sable.HELPER.getVelocity(this.level, data.magnet2Pos(), this.velocity2);
        final Vector3d relativeVelocity = this.velocity1.sub(this.velocity2);
        if (relativeVelocity.lengthSquared() < 1.0E-10) {
            return;
        }

        final Vector3d direction = new Vector3d(relativeVelocity).normalize();
        final double inverseMass = inverseNormalMass(data.body1(), data.magnet1Pos(), direction) + inverseNormalMass(data.body2(), data.magnet2Pos(), direction);
        if (inverseMass <= 0) {
            return;
        }
        final double damping = 2.0 * this.getDampingRatio() * Math.sqrt(stiffness / inverseMass);
        f.fma(-damping * weight, relativeVelocity);
    }

    private static double inverseNormalMass(@Nullable final ServerSubLevel body, final Vector3dc localPoint, final Vector3dc worldDirection) {
        if (body == null) {
            return 0.0;
        }
        final Vector3d local = body.logicalPose().orientation().transformInverse(new Vector3d(worldDirection));
        return body.getMassTracker().getInverseNormalMass(localPoint, local);
    }

    // * Orientation is only fixed once they are close
    @Override
    protected Vector3d getTorque(final PairData data, final Vector3d t) {
        final double near = nearWeight(data.distance());
        if (near <= 0) {
            return t.zero();
        }

        // * Turn flange 1 towards flange 2
        final Vector3d towardsPartner = new Vector3d(data.relativePosition()).negate().normalize(data.moment2().length());
        data.moment1().cross(towardsPartner, t);

        t.add(data.moment2().cross(data.moment1(), this.v));
        return t.mul(data.torqueScale() * near);
    }

    @Override
    protected Vector3d getSymmetricTorque(final PairData data, final Vector3d t) {
        final double near = nearWeight(data.distance());
        if (near <= 0) {
            return t.zero();
        }

        final MagneticHoseConnectorBlockEntity magnet1 = (MagneticHoseConnectorBlockEntity) data.magnet1();
        final MagneticHoseConnectorBlockEntity magnet2 = (MagneticHoseConnectorBlockEntity) data.magnet2();
        final Vector3d red1 = worldDirection(magnet1, magnet1.getRedSlotDirection());
        final Vector3d red2 = worldDirection(magnet2, magnet2.getRedSlotDirection());

        this.v.set(data.moment1()).sub(data.moment2()).div(2);
        red1.cross(red2, t);
        if (red1.dot(red2) < -0.999) {
            t.set(this.v).normalize();
        }

        this.v.mul(this.v.dot(t), t);
        return t.mul(data.torqueScale() * near);
    }

    // * A direction on a connector's block grid, turned into world space
    private static Vector3d worldDirection(final MagneticHoseConnectorBlockEntity magnet, final Direction direction) {
        final Vector3d v = new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        final SubLevel subLevel = magnet.getLatestSubLevel();
        if (subLevel != null) {
            subLevel.logicalPose().orientation().transform(v);
        }
        return v;
    }

    @Override
    public double getDampingRatio() {
        return 0.8;
    }

    @Override
    public double getAccelerationLimit() {
        return MAGNET_LINEAR_ACCELERATION_LIMIT;
    }

    @Override
    public double getAngularAccelerationLimit() {
        return MAGNET_ANGULAR_ACCELERATION_LIMIT;
    }
    //#endregion
}
