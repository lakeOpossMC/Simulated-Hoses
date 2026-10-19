package edn.lakeopossmc.simulatedhoses.hose;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.config.server.physics.SimPhysics;
import dev.simulated_team.simulated.service.SimConfigService;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import edn.lakeopossmc.simulatedhoses.blocks.FluidQuickConnectBlockEntity;
import edn.lakeopossmc.simulatedhoses.blocks.HoseConnectorBlockEntity;
import edn.lakeopossmc.simulatedhoses.blocks.PressureNozzleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// --- GRABBING CONNECTORS BY HAND --- //
// * Holding use with an empty hand on a hose connector that is a physics object carries it in front of the player
public final class HoseGrabHandler {

    public static final float MIN_DISTANCE = 1.5F;
    public static final float MAX_DISTANCE = 4.0F;

    private static final double STIFFNESS = 240.0;
    private static final double DAMPING = 30.0;
    private static final double ANGULAR_DAMPING = 4.5;
    private static final int TIMEOUT_TICKS = 20;
    private static final double REACH_BUFFER = 3.0;
    public static final double PULL_OFF_DISTANCE = 1.0;
    public static final int PULL_OFF_TICKS = 6;
    public static final int QUICK_CONNECT_PULL_OFF_TICKS = 14;

    private static final ResourceLocation NOZZLE_SLOWNESS_ID = SimulatedHosesMod.asResource("pressure_nozzle_carry");
    private static final double NOZZLE_SLOWNESS = -0.25;
    private static final double NOZZLE_SIDE = 0.3;
    private static final double NOZZLE_DROP = 0.22;
    private static final double NOZZLE_DISTANCE = 2.0;
    private static final double NOZZLE_AIM_RANGE = 24.0;

    private static final Map<UUID, Grab> GRABS = new HashMap<>();

    private static final class Grab {
        private final BlockPos pos;
        private final ServerLevel level;
        private float distance;
        @Nullable
        private Quaterniond orientation;
        private int timeout = TIMEOUT_TICKS;
        private int strain;
        private boolean trigger;
        @Nullable
        private Direction nozzleFacing;
        @Nullable
        private Vec3 nozzleAim;
        @Nullable
        private PhysicsConstraintHandle constraint;

        private Grab(final ServerLevel level, final BlockPos pos, final float distance) {
            this.level = level;
            this.pos = pos;
            this.distance = distance;
        }

        private void removeJoint() {
            if (this.constraint != null) {
                this.constraint.remove();
                this.constraint = null;
            }
        }
    }

    //#region // --- FROM THE CLIENT --- //
    public static void hold(final ServerPlayer player, final BlockPos pos, final float distance, @Nullable final Quaternionf orientation, final boolean trigger) {
        final ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !isGrabbable(level.getBlockEntity(pos)) || containing(level, pos) == null) {
            release(player);
            return;
        }
        final float clamped = Mth.clamp(distance, MIN_DISTANCE, MAX_DISTANCE);
        final Grab grab = GRABS.get(player.getUUID());
        if (grab != null && grab.level == level && grab.pos.equals(pos)) {
            grab.distance = clamped;
            grab.timeout = TIMEOUT_TICKS;
            grab.orientation = toDouble(orientation);
            grab.trigger = trigger;
            return;
        }
        release(player);
        final Grab created = new Grab(level, pos.immutable(), clamped);
        created.orientation = toDouble(orientation);
        created.trigger = trigger;
        GRABS.put(player.getUUID(), created);
    }

    public static void release(final ServerPlayer player) {
        final Grab grab = GRABS.remove(player.getUUID());
        if (grab != null) {
            grab.removeJoint();
        }
        setNozzleSlowness(player, false);
    }
    //#endregion

    //#region // --- TICKING --- //
    // * Server tick: forget holds the client stopped confirming, or whose player left
    public static void serverTick() {
        final Iterator<Map.Entry<UUID, Grab>> it = GRABS.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<UUID, Grab> entry = it.next();
            final Grab grab = entry.getValue();
            final ServerPlayer player = grab.level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (--grab.timeout <= 0 || player == null || player.isDeadOrDying() || player.isSpectator() || player.level() != grab.level) {
                grab.removeJoint();
                it.remove();
                if (player != null) {
                    setNozzleSlowness(player, false);
                }
                continue;
            }
            final PressureNozzleBlockEntity nozzle = PressureNozzleBlockEntity.forCarried(grab.level, grab.pos);
            setNozzleSlowness(player, nozzle != null);
            grab.nozzleFacing = nozzle != null ? nozzle.getFacing() : null;
            grab.nozzleAim = nozzle != null ? crosshairPoint(player, grab) : null;
            tickPullOff(player, grab);
        }
    }

    public static boolean isTriggerHeld(final Level level, final BlockPos nozzlePos, @Nullable final BlockPos connectorPos) {
        for (final Grab grab : GRABS.values()) {
            if (grab.trigger && grab.level == level && (grab.pos.equals(nozzlePos) || grab.pos.equals(connectorPos))) {
                return true;
            }
        }
        return false;
    }

    private static void setNozzleSlowness(final ServerPlayer player, final boolean slowed) {
        final AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        final boolean has = speed.hasModifier(NOZZLE_SLOWNESS_ID);
        if (slowed && !has) {
            speed.addTransientModifier(new AttributeModifier(NOZZLE_SLOWNESS_ID, NOZZLE_SLOWNESS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!slowed && has) {
            speed.removeModifier(NOZZLE_SLOWNESS_ID);
        }
    }

    // * The player carrying the block at this spot, or null when nobody is
    @Nullable
    public static Player getCarrier(final Level level, final BlockPos pos) {
        for (final Map.Entry<UUID, Grab> entry : GRABS.entrySet()) {
            final Grab grab = entry.getValue();
            if (grab.level == level && grab.pos.equals(pos)) {
                return level.getPlayerByUUID(entry.getKey());
            }
        }
        return null;
    }

    // * Where the player carrying the block at this spot is holding it, or null when nobody is
    @Nullable
    public static Vec3 getCarryTarget(final Level level, final BlockPos pos) {
        for (final Map.Entry<UUID, Grab> entry : GRABS.entrySet()) {
            final Grab grab = entry.getValue();
            if (grab.level != level || !grab.pos.equals(pos)) {
                continue;
            }
            final Player player = level.getPlayerByUUID(entry.getKey());
            return player == null ? null : carryGoal(player, grab);
        }
        return null;
    }

    // * Hose connectors and pressure nozzles can be carried by hand
    public static boolean isGrabbable(@Nullable final BlockEntity be) {
        return be instanceof HoseConnectorBlockEntity || be instanceof PressureNozzleBlockEntity;
    }

    private static void tickPullOff(final ServerPlayer player, final Grab grab) {
        if (!(grab.level.getBlockEntity(grab.pos) instanceof final HoseConnectorBlockEntity connector)) {
            return;
        }
        final FluidQuickConnectBlockEntity quickConnect = connector.getQuickConnect();
        if (quickConnect == null) {
            grab.strain = 0;
            return;
        }
        final Vec3 goal = carryGoal(player, grab);
        final Vector3d flange = connector.worldFlangePosition();
        if (flange.distance(goal.x, goal.y, goal.z) > PULL_OFF_DISTANCE) {
            if (++grab.strain >= QUICK_CONNECT_PULL_OFF_TICKS) {
                grab.strain = 0;
                quickConnect.pop(true);
                return;
            }
        } else {
            grab.strain = Math.max(0, grab.strain - 1);
        }
        quickConnect.setPullStrain(grab.strain, QUICK_CONNECT_PULL_OFF_TICKS);
    }

    public static void physicsTick(final SubLevelPhysicsSystem physicsSystem) {
        if (GRABS.isEmpty()) {
            return;
        }
        final Level physicsLevel = physicsSystem.getLevel();
        for (final Map.Entry<UUID, Grab> entry : GRABS.entrySet()) {
            final Grab grab = entry.getValue();
            if (grab.level != physicsLevel) {
                continue;
            }
            grab.removeJoint();
            final ServerPlayer player = (ServerPlayer) grab.level.getPlayerByUUID(entry.getKey());
            if (player == null) {
                continue;
            }
            final ServerSubLevel subLevel = containing(grab.level, grab.pos);
            if (subLevel == null || !isGrabbable(grab.level.getBlockEntity(grab.pos))) {
                continue;
            }
            if (Sable.HELPER.getTrackingSubLevel(player) == subLevel) {
                continue;
            }

            final Vec3 eye = player.getEyePosition();
            final Vec3 goal = carryGoal(player, grab);
            final Vector3d target = new Vector3d(goal.x, goal.y, goal.z);
            final Vector3d grabPoint = new Vector3d(grab.pos.getX() + 0.5, grab.pos.getY() + 0.5, grab.pos.getZ() + 0.5);

            // * Let go once it is pulled too far away
            final double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + REACH_BUFFER;
            final Vector3d worldGrabPoint = Sable.HELPER.projectOutOfSubLevel(grab.level, grabPoint, new Vector3d());
            if (worldGrabPoint.distanceSquared(eye.x, eye.y, eye.z) > reach * reach) {
                continue;
            }

            final double maxForce = SimConfigService.INSTANCE.server().physics.handleMaxForce.getF();
            // * A carried pressure nozzle turns to point at whatever the crosshair is on
            Quaterniond orientation = grab.orientation;
            if (grab.nozzleFacing != null && grab.nozzleAim != null) {
                orientation = aimAlong(subLevel, grab.nozzleFacing, grab.nozzleAim.subtract(goal));
            }
            final boolean rotated = orientation != null;
            grab.constraint = physicsSystem.getPipeline().addConstraint(null, (PhysicsPipelineBody) subLevel,
                    new FreeConstraintConfiguration(target, grabPoint, rotated ? orientation : new Quaterniond()));
            for (final ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
                grab.constraint.setMotor(axis, 0.0, STIFFNESS, DAMPING, true, maxForce);
            }
            if (rotated) {
                final SimPhysics physics = SimConfigService.INSTANCE.server().physics;
                final double angularStiffness = physics.physicsStaffAngularStiffness.getF();
                final double angularDamping = physics.physicsStaffAngularDamping.getF();
                for (final ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
                    grab.constraint.setMotor(axis, 0.0, angularStiffness, angularDamping, true, maxForce);
                }
            } else {
                for (final ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
                    grab.constraint.setMotor(axis, 0.0, 0.0, ANGULAR_DAMPING, true, maxForce);
                }
            }
        }
    }
    //#endregion

    //#region // --- CARRYING A NOZZLE --- //
    private static Vec3 carryGoal(final Player player, final Grab grab) {
        final Vec3 eye = player.getEyePosition();
        final Vec3 look = player.getLookAngle();
        if (grab.nozzleFacing == null) {
            return eye.add(look.scale(grab.distance));
        }
        final double distance = NOZZLE_DISTANCE;
        final double side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0;
        final Vec3 right = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F);
        final Vec3 up = right.cross(look).normalize();
        return eye.add(look.scale(distance))
                .add(right.scale(side * distance * NOZZLE_SIDE))
                .subtract(up.scale(distance * NOZZLE_DROP));
    }

    private static Vec3 crosshairPoint(final Player player, final Grab grab) {
        final Vec3 eye = player.getEyePosition();
        final Vec3 far = eye.add(player.getLookAngle().scale(NOZZLE_AIM_RANGE));
        final HitResult hit = player.pick(NOZZLE_AIM_RANGE, 1.0F, false);
        if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(eye) < NOZZLE_DISTANCE + 1.0) {
            return far;
        }
        return hit.getLocation();
    }
    //#endregion

    private static Quaterniond aimAlong(final ServerSubLevel subLevel, final Direction facing, final Vec3 look) {
        final Quaterniond current = new Quaterniond(subLevel.logicalPose().orientation());
        final Vector3d pointing = current.transform(new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ()));
        final Vector3d wanted = new Vector3d(look.x, look.y, look.z).normalize();
        return new Quaterniond().rotationTo(pointing, wanted).mul(current).normalize();
    }

    @Nullable
    private static Quaterniond toDouble(@Nullable final Quaternionf q) {
        if (q == null || !Float.isFinite(q.x) || !Float.isFinite(q.y) || !Float.isFinite(q.z) || !Float.isFinite(q.w) || q.lengthSquared() < 1.0E-6F) {
            return null;
        }
        return new Quaterniond(q.x, q.y, q.z, q.w).normalize();
    }

    @Nullable
    private static ServerSubLevel containing(final Level level, final BlockPos pos) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, new ChunkPos(pos));
        return subLevel instanceof final ServerSubLevel server ? server : null;
    }

    private HoseGrabHandler() {
    }
}