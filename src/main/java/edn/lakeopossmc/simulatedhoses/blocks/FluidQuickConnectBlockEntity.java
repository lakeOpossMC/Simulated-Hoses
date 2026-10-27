package edn.lakeopossmc.simulatedhoses.blocks;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.index.SimSoundEvents;
import dev.simulated_team.simulated.util.SimMathUtils;
import edn.lakeopossmc.simulatedhoses.client.HoseSpray;
import edn.lakeopossmc.simulatedhoses.hose.HoseEndpoint;
import edn.lakeopossmc.simulatedhoses.hose.HoseGoggles;
import edn.lakeopossmc.simulatedhoses.hose.HoseGrabHandler;
import edn.lakeopossmc.simulatedhoses.hose.HoseHolderBehavior;
import edn.lakeopossmc.simulatedhoses.hose.HosePortHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

// --- FLUID QUICK CONNECT BLOCK ENTITY --- //
// * While extended, the nearest hose connector whose flange comes close locks on flush
// * Retracting it, or pulling the connector off by hand, pops the connector off
public class FluidQuickConnectBlockEntity extends SmartBlockEntity implements HoseEndpoint, IHaveGoggleInformation {

    private static final double PORT_SURFACE = 9.0 / 16.0 - 0.5;
    private static final double CAPTURE_RANGE = 0.5;
    private static final double CAPTURE_COS = Math.cos(Math.toRadians(40.0));
    private static final double BREAK_DISTANCE = 1.0;
    private static final double RELOCK_DISTANCE = 3.0;
    private static final int LOCK_TICKS = 6;
    private static final int POP_COOLDOWN = 10;
    private static final int SYNC_INTERVAL = 10;

    public static final int EVENT_POP = 1;
    // * Sent while a connector is being pulled off
    public static final int EVENT_STRAIN = 2;
    private static final float MAX_PULL_STRETCH = 2.5F;
    private static final float CREAK_VOLUME = 0.8F;
    private static final float CREAK_PITCH = 1.4F;

    static final List<Quaterniondc> GRID_ROTATIONS = buildGridRotations();

    private static List<Quaterniondc> buildGridRotations() {
        final List<Quaterniondc> rotations = new java.util.ArrayList<>();
        final Vector3d x = new Vector3d();
        final Vector3d y = new Vector3d();
        for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 4; b++) {
                for (int c = 0; c < 4; c++) {
                    final Quaterniond q = new Quaterniond().rotateX(a * Math.PI / 2).rotateY(b * Math.PI / 2).rotateZ(c * Math.PI / 2);
                    q.transform(1, 0, 0, x);
                    q.transform(0, 1, 0, y);
                    boolean known = false;
                    for (final Quaterniondc other : rotations) {
                        if (other.transform(1, 0, 0, new Vector3d()).distanceSquared(x) < 1.0E-6
                                && other.transform(0, 1, 0, new Vector3d()).distanceSquared(y) < 1.0E-6) {
                            known = true;
                            break;
                        }
                    }
                    if (!known) {
                        rotations.add(q);
                    }
                }
            }
        }
        return List.copyOf(rotations);
    }

    private static final Set<FluidQuickConnectBlockEntity> SMOOTHING = Collections.newSetFromMap(new WeakHashMap<>());

    private final HosePortHandler portHandler = new HosePortHandler(this);
    private int flow;
    private int lastSyncedFlow;
    private FluidStack fluid = FluidStack.EMPTY;
    private FluidStack lastSyncedFluid = FluidStack.EMPTY;
    private boolean spraying;
    private boolean lastSyncedSpraying;
    private int syncTimer;

    @Nullable
    private BlockPos coupledPos;
    @Nullable
    private FixedConstraintHandle lock;
    @Nullable
    private LockSmoother lockSmoother;
    private int cooldown;
    // * The connector just popped off, ignored until it has been moved away
    @Nullable
    private BlockPos poppedPos;

    private float extender;
    private float extenderPrevious;
    private float port;
    private float portPrevious;
    private float wobble;
    private float wobblePrevious;
    private float wobbleVelocity;
    private float stretch;
    private float stretchPrevious;
    private float stretchTarget;
    private boolean animationStarted;

    private int pullStrain;
    private long pullStrainTick;
    private boolean pullCreaked;

    public FluidQuickConnectBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
    }

    public Direction getFacing() {
        return this.getBlockState().getValue(FluidQuickConnectBlock.FACING);
    }

    public boolean isRetracted() {
        return FluidQuickConnectBlock.isRetracted(this.getBlockState());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level.isClientSide) {
            this.tickAnimation();
            if (this.spraying) {
                // * Out of the front of the port
                final double portFace = PORT_SURFACE + (this.getPortOffset(1.0F)) / 16.0;
                HoseSpray.tick(this.level, this.worldPosition, this.getFacing(), portFace, this.fluid, Math.max(this.flow, 1));
            }
            return;
        }

        if (this.pullStrain > 0 && this.level.getGameTime() - this.pullStrainTick > 2) {
            this.setPullStrain(0, 1);
        }

        this.portHandler.tick();
        this.flow = this.portHandler.getFlowThisTick();
        this.fluid = this.portHandler.getLastFluid();
        this.spraying = this.portHandler.isSpraying();
        this.syncIfNeeded();

        if (this.coupledPos != null) {
            this.validateCoupling();
        } else if (this.cooldown > 0) {
            this.cooldown--;
        } else if (!this.isRetracted() && (this.level.getGameTime() + this.worldPosition.hashCode()) % 2 == 0) {
            this.searchForConnector();
        }

        if (this.lockSmoother != null && ++this.lockSmoother.ticks >= LOCK_TICKS) {
            this.lockSmoother.step(this, 1.0);
            this.lockSmoother = null;
            SMOOTHING.remove(this);
        }
    }

    //#region // --- FINDING AND LOCKING ON --- //
    private void searchForConnector() {
        final SubLevel ownSubLevel = Sable.HELPER.getContaining(this);
        final Vector3d port = this.worldPortPosition();
        final Vector3d facing = worldDirection(this.level, this.worldPosition, this.getFacing());

        HoseConnectorBlockEntity best = null;
        double closest = CAPTURE_RANGE;
        for (final HoseConnectorBlockEntity connector : HoseConnectorBlockEntity.loaded(this.level)) {
            if (connector.isRemoved() || connector.isCoupledElsewhere() || connector.getQuickConnectPos() != null || connector.hasNozzle()) {
                continue;
            }
            final SubLevel connectorSubLevel = Sable.HELPER.getContaining(connector);
            if (connectorSubLevel == ownSubLevel) {
                continue;
            }
            final double distance = connector.worldFlangePosition().distance(port);
            if (connector.getBlockPos().equals(this.poppedPos)) {
                if (distance > CAPTURE_RANGE * 2) {
                    this.poppedPos = null;
                }
                continue;
            }
            if (distance >= closest) {
                continue;
            }
            // * Flange square onto the port
            final Vector3d flange = worldDirection(this.level, connector.getBlockPos(), connector.getFlangeDirection());
            if (flange.dot(facing) > -CAPTURE_COS) {
                continue;
            }
            closest = distance;
            best = connector;
        }
        if (best != null) {
            this.couple(best);
        }
    }

    private void couple(final HoseConnectorBlockEntity connector) {
        this.coupledPos = connector.getBlockPos();
        connector.setQuickConnectPos(this.worldPosition);
        this.createLock(connector, false);
        this.setChanged();
        this.sendData();

        final Vector3d at = this.worldPortPosition();
        this.level.playSound(null, at.x, at.y, at.z, SimSoundEvents.DOCKING_CONNECTOR_DOCKS.event(), SoundSource.BLOCKS, 0.8F, 1.5F + this.level.random.nextFloat() * 0.15F);
        this.level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5F, 1.7F);
    }

    private void validateCoupling() {
        final HoseConnectorBlockEntity connector = this.getCoupledConnector();
        if (connector == null) {
            // * Not loaded in yet, or unloaded first. Wait for it rather than letting go, unless retracted
            if (!this.level.isLoaded(this.coupledPos) && !this.isRetracted()) {
                this.removeLock();
                return;
            }
            // * Connector broken or gone
            this.clearCoupling(null);
            return;
        }
        if (this.isRetracted()) {
            this.pop(false);
            return;
        }
        // * Locks are not saved, so put it back after a reload, easing it in if it drifted
        if (this.lock == null && this.lockSmoother == null) {
            final double drift = connector.worldFlangePosition().distance(this.worldPortPosition());
            if (drift > RELOCK_DISTANCE) {
                this.pop(true);
                return;
            }
            this.createLock(connector, drift < 0.05);
            return;
        }
        if (this.lockSmoother == null && connector.worldFlangePosition().distance(this.worldPortPosition()) > BREAK_DISTANCE) {
            this.pop(true);
        }
    }

    //#region // --- PULLING OFF --- //
    public void setPullStrain(final int strain, final int max) {
        this.pullStrainTick = this.level.getGameTime();
        if (strain == this.pullStrain) {
            return;
        }
        final float fraction = max > 0 ? Mth.clamp((float) strain / max, 0.0F, 1.0F) : 0.0F;
        if (strain > 0 && this.pullStrain == 0 && !this.pullCreaked) {
            final Vector3d at = this.worldPortPosition();
            this.level.playSound(null, at.x, at.y, at.z, SimSoundEvents.ROPE_WINCH_STRETCH.event(), SoundSource.BLOCKS, CREAK_VOLUME, CREAK_PITCH + this.level.random.nextFloat() * 0.1F);
            this.pullCreaked = true;
        } else if (strain == 0) {
            this.stopCreak();
        }
        this.pullStrain = strain;
        this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), EVENT_STRAIN, Math.round(fraction * 100.0F));

        final HoseConnectorBlockEntity connector = this.getCoupledConnector();
        if (connector != null && this.lock != null && this.lockSmoother == null) {
            this.createLock(connector, true);
        }
    }

    private void stopCreak() {
        if (!this.pullCreaked || !(this.level instanceof final ServerLevel serverLevel)) {
            this.pullCreaked = false;
            return;
        }
        this.pullCreaked = false;
        final Vector3d at = this.worldPortPosition();
        final ClientboundStopSoundPacket packet = new ClientboundStopSoundPacket(SimSoundEvents.ROPE_WINCH_STRETCH.event().getLocation(), SoundSource.BLOCKS);
        for (final ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(at.x, at.y, at.z) < 32.0 * 32.0) {
                player.connection.send(packet);
            }
        }
    }

    private double pullStretch() {
        return this.pullStrain <= 0 ? 0.0 : Math.min(1.0, this.pullStrain / (double) HoseGrabHandler.QUICK_CONNECT_PULL_OFF_TICKS) * MAX_PULL_STRETCH / 16.0;
    }
    //#endregion

    public void pop(final boolean pulled) {
        this.pullStrain = 0;
        this.stopCreak();
        final HoseConnectorBlockEntity connector = this.getCoupledConnector();
        this.clearCoupling(connector);
        this.cooldown = POP_COOLDOWN;
        this.poppedPos = connector != null ? connector.getBlockPos() : null;

        final Vector3d at = this.worldPortPosition();
        this.level.playSound(null, at.x, at.y, at.z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 1.0F, 0.6F);
        this.level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7F, 0.5F);
        this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), EVENT_POP, pulled ? 1 : 0);
    }

    private void clearCoupling(@Nullable final HoseConnectorBlockEntity connector) {
        this.removeLock();
        this.coupledPos = null;
        if (connector != null && Objects.equals(connector.getQuickConnectPos(), this.worldPosition)) {
            connector.setQuickConnectPos(null);
        }
        this.setChanged();
        this.sendData();
    }

    @Nullable
    public HoseConnectorBlockEntity getCoupledConnector() {
        if (this.coupledPos == null || this.level == null || !this.level.isLoaded(this.coupledPos)) {
            return null;
        }
        if (this.level.getBlockEntity(this.coupledPos) instanceof final HoseConnectorBlockEntity connector
                && Objects.equals(connector.getQuickConnectPos(), this.worldPosition)) {
            return connector;
        }
        return null;
    }

    public boolean isCoupled() {
        return this.coupledPos != null;
    }

    @Nullable
    public BlockPos getCoupledConnectorPos() {
        return this.coupledPos;
    }

    // * Center of the port face the flange mates with
    public Vec3 getPortPosition() {
        final Direction facing = this.getFacing();
        final double surface = PORT_SURFACE + this.pullStretch();
        return Vec3.atCenterOf(this.worldPosition).add(facing.getStepX() * surface, facing.getStepY() * surface, facing.getStepZ() * surface);
    }

    public Vector3d worldPortPosition() {
        final Vec3 local = this.getPortPosition();
        return Sable.HELPER.projectOutOfSubLevel(this.level, new Vector3d(local.x, local.y, local.z));
    }

    static Vector3d worldDirection(final Level level, final BlockPos pos, final Direction direction) {
        final Vector3d v = new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        final SubLevel subLevel = Sable.HELPER.getContaining(level, new ChunkPos(pos));
        if (subLevel != null) {
            subLevel.logicalPose().orientation().transform(v);
        }
        return v;
    }
    //#endregion

    //#region // --- THE LOCK --- //
    // * Pins the connector's flange to the port with a fixed joint
    // * Same approach as the magnetic connectors
    private void createLock(final HoseConnectorBlockEntity connector, final boolean instant) {
        if (!(this.level instanceof ServerLevel)) {
            return;
        }
        final ServerSubLevel connectorSubLevel = (ServerSubLevel) Sable.HELPER.getContaining(connector);
        final ServerSubLevel ownSubLevel = (ServerSubLevel) Sable.HELPER.getContaining(this);
        if (connectorSubLevel == ownSubLevel) {
            return;
        }

        final boolean connectorFirst = connectorSubLevel != null;
        final ServerSubLevel subLevelA = connectorFirst ? connectorSubLevel : ownSubLevel;
        final ServerSubLevel subLevelB = connectorFirst ? ownSubLevel : null;
        final Vec3 localA = connectorFirst ? connector.getFlangePosition() : this.getPortPosition();
        final Vec3 localB = connectorFirst ? this.getPortPosition() : connector.getFlangePosition();
        final Direction matingA = connectorFirst ? connector.getFlangeDirection() : this.getFacing();
        final Direction matingB = connectorFirst ? this.getFacing() : connector.getFlangeDirection();

        final Quaterniond orientationA = new Quaterniond(subLevelA.logicalPose().orientation());
        final Quaterniond orientationB = subLevelB == null ? new Quaterniond() : new Quaterniond(subLevelB.logicalPose().orientation());

        final Vector3d worldA = Sable.HELPER.projectOutOfSubLevel(this.level, new Vector3d(localA.x, localA.y, localA.z));
        final Vector3d worldB = Sable.HELPER.projectOutOfSubLevel(this.level, new Vector3d(localB.x, localB.y, localB.z));
        final Vector3d offset = worldA.sub(worldB, new Vector3d());
        orientationB.transformInverse(offset);

        final Quaterniond relativeOrientation = new Quaterniond(orientationB).premul(orientationA.conjugate(new Quaterniond()));
        final Quaterniond targetOrientation = matingOrientation(relativeOrientation, matingA, matingB);

        this.removeLock();
        this.lockSmoother = new LockSmoother(connector.getBlockPos(), connectorFirst, offset, relativeOrientation, targetOrientation);
        this.lockSmoother.step(this, instant ? 1.0 : 0.0);
        if (instant) {
            this.lockSmoother = null;
        } else {
            SMOOTHING.add(this);
        }
    }

    static Quaterniond matingOrientation(final Quaterniondc relative, final Direction matingA, final Direction matingB) {
        return matingOrientation(relative, matingA, matingB, null, null);
    }

    static Quaterniond matingOrientation(final Quaterniondc relative, final Direction matingA, final Direction matingB, @Nullable final Direction.Axis axisA, @Nullable final Direction.Axis axisB) {
        final Vector3d faceB = new Vector3d(matingB.getStepX(), matingB.getStepY(), matingB.getStepZ());
        final Vector3d wanted = new Vector3d(-matingA.getStepX(), -matingA.getStepY(), -matingA.getStepZ());
        final boolean aligned = axisA != null && axisB != null;
        final Vector3d lineB = aligned ? axisVector(axisB) : null;
        final Vector3d lineA = aligned ? axisVector(axisA) : null;
        Quaterniondc best = null;
        double bestDot = -1;
        final Vector3d turned = new Vector3d();
        for (final Quaterniondc grid : GRID_ROTATIONS) {
            grid.transform(faceB, turned);
            if (turned.distanceSquared(wanted) > 1.0E-4) {
                continue;
            }
            if (aligned && Math.abs(grid.transform(lineB, turned).dot(lineA)) < 0.5) {
                continue;
            }
            final double dot = Math.abs(grid.dot(relative));
            if (dot > bestDot) {
                bestDot = dot;
                best = grid;
            }
        }
        if (best == null) {
            return SimMathUtils.clampQuaternionToGrid(relative, SimMathUtils.GridQuats.ALL, new Quaterniond());
        }
        final Quaterniond result = new Quaterniond(best);
        if (result.dot(relative) < 0) {
            result.set(-result.x, -result.y, -result.z, -result.w);
        }
        return result;
    }

    private static Vector3d axisVector(final Direction.Axis axis) {
        return new Vector3d(axis == Direction.Axis.X ? 1 : 0, axis == Direction.Axis.Y ? 1 : 0, axis == Direction.Axis.Z ? 1 : 0);
    }

    private static final class LockSmoother {
        private final BlockPos connectorPos;
        private final boolean connectorFirst;
        private final Vector3d initialOffset;
        private final Quaterniond initialOrientation;
        private final Quaterniond targetOrientation;
        private int ticks;

        private LockSmoother(final BlockPos connectorPos, final boolean connectorFirst, final Vector3d initialOffset, final Quaterniond initialOrientation, final Quaterniond targetOrientation) {
            this.connectorPos = connectorPos;
            this.connectorFirst = connectorFirst;
            this.initialOffset = initialOffset;
            this.initialOrientation = initialOrientation;
            this.targetOrientation = targetOrientation;
        }

        private void step(final FluidQuickConnectBlockEntity owner, final double progress) {
            if (!(owner.level instanceof final ServerLevel level)
                    || !(level.getBlockEntity(this.connectorPos) instanceof final HoseConnectorBlockEntity connector)) {
                return;
            }
            final ServerSubLevel connectorSubLevel = (ServerSubLevel) Sable.HELPER.getContaining(connector);
            final ServerSubLevel ownSubLevel = (ServerSubLevel) Sable.HELPER.getContaining(owner);
            final ServerSubLevel subLevelA = this.connectorFirst ? connectorSubLevel : ownSubLevel;
            final ServerSubLevel subLevelB = this.connectorFirst ? ownSubLevel : connectorSubLevel;
            final var container = SubLevelContainer.getContainer(level);
            if (subLevelA == null || subLevelA == subLevelB || container == null) {
                return;
            }

            final double lerp = Mth.clamp(progress * progress, 0.0, 1.0);
            final double rotationLerp = Mth.clamp(lerp * 2.0, 0.0, 1.0);

            final Vec3 localA = this.connectorFirst ? connector.getFlangePosition() : owner.getPortPosition();
            final Vec3 localB = this.connectorFirst ? owner.getPortPosition() : connector.getFlangePosition();
            final Vector3d anchorA = new Vector3d(localA.x, localA.y, localA.z);
            final Vector3d anchorB = new Vector3d(localB.x, localB.y, localB.z).fma(1.0 - lerp, this.initialOffset);
            final Quaterniond orientation = this.initialOrientation.slerp(this.targetOrientation, rotationLerp, new Quaterniond());

            owner.removeConstraint();
            owner.lock = container.physicsSystem().getPipeline().addConstraint(subLevelA, subLevelB,
                    new FixedConstraintConfiguration(anchorA, anchorB, orientation));
        }
    }

    public static void physicsTick(final SubLevelPhysicsSystem physicsSystem) {
        if (SMOOTHING.isEmpty()) {
            return;
        }
        final double partial = physicsSystem.getPartialPhysicsTick();
        for (final FluidQuickConnectBlockEntity quickConnect : List.copyOf(SMOOTHING)) {
            final LockSmoother smoother = quickConnect.lockSmoother;
            if (smoother == null || quickConnect.isRemoved()) {
                SMOOTHING.remove(quickConnect);
                continue;
            }
            if (quickConnect.level == physicsSystem.getLevel()) {
                smoother.step(quickConnect, (smoother.ticks + partial) / LOCK_TICKS);
            }
        }
    }

    private void removeLock() {
        this.lockSmoother = null;
        SMOOTHING.remove(this);
        this.removeConstraint();
    }

    private void removeConstraint() {
        if (this.lock != null) {
            this.lock.remove();
            this.lock = null;
        }
    }
    //#endregion

    //#region // --- CLIENT ANIMATION --- //
    private void tickAnimation() {
        final boolean retracted = this.isRetracted();
        final float extenderTarget = retracted ? -2.0F : 0.0F;
        final float portTarget = retracted ? -1.9F : 0.0F;
        if (!this.animationStarted) {
            this.extender = this.extenderPrevious = extenderTarget;
            this.port = this.portPrevious = portTarget;
            this.animationStarted = true;
        }
        this.extenderPrevious = this.extender;
        this.portPrevious = this.port;
        this.wobblePrevious = this.wobble;
        this.stretchPrevious = this.stretch;

        this.extender += (extenderTarget - this.extender) * 0.3F;
        this.stretch += (this.stretchTarget - this.stretch) * 0.4F;
        this.port += (portTarget - this.port) * 0.22F;

        this.wobbleVelocity += -0.35F * this.wobble - 0.25F * this.wobbleVelocity;
        this.wobble += this.wobbleVelocity;
        if (Math.abs(this.wobble) < 0.001F && Math.abs(this.wobbleVelocity) < 0.001F) {
            this.wobble = 0;
            this.wobbleVelocity = 0;
        }
    }

    @Override
    public boolean triggerEvent(final int id, final int param) {
        if (id == EVENT_POP) {
            if (this.level != null && this.level.isClientSide) {
                // * Springs back from wherever the pull had stretched it
                final float from = Math.max(param == 1 ? 1.6F : 0.0F, this.stretch);
                this.stretch = this.stretchPrevious = this.stretchTarget = 0.0F;
                if (from > 0.0F) {
                    this.wobble = from;
                    this.wobblePrevious = from;
                    this.wobbleVelocity = 0.0F;
                }
            }
            return true;
        }
        if (id == EVENT_STRAIN) {
            if (this.level != null && this.level.isClientSide) {
                this.stretchTarget = param / 100.0F * MAX_PULL_STRETCH;
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    public float getExtenderOffset(final float partialTicks) {
        return Mth.lerp(partialTicks, this.extenderPrevious, this.extender) + Mth.lerp(partialTicks, this.wobblePrevious, this.wobble)
                + Mth.lerp(partialTicks, this.stretchPrevious, this.stretch);
    }

    public float getPortOffset(final float partialTicks) {
        return this.getExtenderOffset(partialTicks) + Mth.lerp(partialTicks, this.portPrevious, this.port)
                + 0.15F * Mth.lerp(partialTicks, this.wobblePrevious, this.wobble);
    }
    //#endregion

    //#region // --- HOSE ENDPOINT --- //
    @Override
    public @Nullable HoseHolderBehavior getHoseHolder() {
        return null;
    }

    @Override
    public HosePortHandler getPortHandler() {
        return this.portHandler;
    }

    @Override
    public Direction getPortDirection() {
        return this.getFacing().getOpposite();
    }

    @Override
    public @Nullable HoseEndpoint getCoupledEndpoint() {
        return this.getCoupledConnector();
    }

    @Override
    public boolean isPortMated() {
        return false;
    }

    @Override
    public boolean isOpenOnItsOwn() {
        return !this.isRetracted() && this.coupledPos == null;
    }

    @Override
    public void setHoseFlow(final int flow) {
        this.flow = flow;
    }

    @Override
    public int getHoseFlow() {
        return this.flow;
    }

    private void syncIfNeeded() {
        if (this.syncTimer > 0) {
            this.syncTimer--;
        }
        final boolean changed = (this.flow == 0) != (this.lastSyncedFlow == 0)
                || !FluidStack.isSameFluidSameComponents(this.fluid, this.lastSyncedFluid)
                || this.spraying != this.lastSyncedSpraying;
        if (changed || (this.flow != this.lastSyncedFlow && this.syncTimer <= 0)) {
            this.lastSyncedFlow = this.flow;
            this.lastSyncedFluid = this.fluid;
            this.lastSyncedSpraying = this.spraying;
            this.syncTimer = SYNC_INTERVAL;
            this.sendData();
        }
    }

    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        return HoseGoggles.addFlowTooltip(tooltip, this);
    }
    //#endregion

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (this.coupledPos != null) {
            tag.put("CoupledTo", NbtUtils.writeBlockPos(this.coupledPos));
        }
        if (clientPacket) {
            tag.putInt("Flow", this.flow);
            tag.putBoolean("Spraying", this.spraying);
            if (!this.fluid.isEmpty()) {
                tag.put("HoseFluid", this.fluid.save(registries));
            }
        } else {
            this.portHandler.write(tag, registries);
        }
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.coupledPos = tag.contains("CoupledTo") ? NbtUtils.readBlockPos(tag, "CoupledTo").orElse(null) : null;
        if (clientPacket) {
            this.flow = tag.getInt("Flow");
            this.spraying = tag.getBoolean("Spraying");
            this.fluid = FluidStack.parseOptional(registries, tag.getCompound("HoseFluid"));
        } else {
            this.portHandler.read(tag, registries);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        this.removeLock();
        this.portHandler.release();
    }

    @Override
    public void destroy() {
        final HoseConnectorBlockEntity connector = this.getCoupledConnector();
        if (connector != null) {
            connector.setQuickConnectPos(null);
        }
        super.destroy();
    }
}
