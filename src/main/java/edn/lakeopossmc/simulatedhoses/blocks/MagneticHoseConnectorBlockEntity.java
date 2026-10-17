package edn.lakeopossmc.simulatedhoses.blocks;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.MagnetBehaviour;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.MagnetMap;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.MagnetPair;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.SimMagnet;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.index.SimSoundEvents;
import dev.simulated_team.simulated.util.SimMathUtils;
import dev.simulated_team.simulated.util.SimMovementContext;
import net.createmod.catnip.math.VecHelper;
import edn.lakeopossmc.simulatedhoses.hose.HoseEndpoint;
import edn.lakeopossmc.simulatedhoses.hose.MagnetFieldEmitter;
import edn.lakeopossmc.simulatedhoses.hose.MagneticHosePair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionfc;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// --- MAGNETIC HOSE CONNECTOR BLOCK ENTITY --- //
// * While powered it pulls other powered magnetic connectors in until the bases meet
// * Once the bases touch they couple
public class MagneticHoseConnectorBlockEntity extends HoseConnectorBlockEntity implements SimMagnet {
    public static final MagnetMap<MagneticHoseConnectorBlockEntity> MAGNETS = new MagnetMap<>();

    private static final double TIP_OFFSET = 0.5;
    private static final double MAGNET_OFFSET = 0.4;
    private static final int LOCK_TICKS = 6;

    @Nullable
    private BlockPos coupledPos;
    @Nullable
    private UUID coupledSubLevelId;
    @Nullable
    private FixedConstraintHandle lock;
    @Nullable
    private LockSmoother lockSmoother;
    private double closestPairDistance = Double.MAX_VALUE;
    private final List<MagnetFieldEmitter> fieldEmitters = new ArrayList<>();

    public MagneticHoseConnectorBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(new MagnetBehaviour(this, MAGNETS));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level.isClientSide) {
            return;
        }

        final boolean powered = this.level.hasNeighborSignal(this.worldPosition) || this.isPoweredByWinch();
        final BlockState state = this.getBlockState();
        if (state.getValue(MagneticHoseConnectorBlock.POWERED) != powered) {
            this.level.setBlock(this.worldPosition, state.setValue(MagneticHoseConnectorBlock.POWERED, powered), 3);
        }

        if (this.coupledPos != null) {
            this.validateCoupling(powered);
        }
        if (this.lockSmoother != null && ++this.lockSmoother.ticks >= LOCK_TICKS) {
            this.lockSmoother.step(this, 1.0);
            this.lockSmoother = null;
        }

        if (powered && this.getQuickConnectPos() == null) {
            this.searchForPairs();
        }

        if (powered) {
            this.spawnFieldParticles();
        }
        for (final MagnetFieldEmitter emitter : this.fieldEmitters) {
            emitter.update();
        }
        this.fieldEmitters.removeIf(MagnetFieldEmitter::isDone);
    }

    //#region // --- FIELD PARTICLES --- //
    private void spawnFieldParticles() {
        if (!(this.level instanceof final ServerLevel serverLevel) || this.level.random.nextFloat() >= 0.1F) {
            return;
        }
        final boolean negative = this.level.random.nextBoolean();
        final Direction pole = negative ? this.getFieldDirection().getOpposite() : this.getFieldDirection();
        final BlockPos ahead = this.worldPosition.relative(pole);
        if (this.level.getBlockState(ahead).isSolidRender(this.level, ahead)) {
            return;
        }

        final Vector3d normal = new Vector3d(pole.getStepX(), pole.getStepY(), pole.getStepZ());
        final Vec3 scatter = VecHelper.offsetRandomly(Vec3.ZERO, this.level.random, 0.35F);
        final Vector3d start = new Vector3d(scatter.x, scatter.y, scatter.z);
        start.fma(-normal.dot(start), normal).fma(0.55, normal);
        final SimMovementContext context = SimMovementContext.getMovementContext(this.level, Vec3.atCenterOf(this.worldPosition));
        context.orientation().transform(start);
        start.add(context.globalPosition().x, context.globalPosition().y, context.globalPosition().z);

        final Map<Vector3d, Vector3d> magnets = new HashMap<>();
        final List<SimMovementContext> nearby = new ArrayList<>(MAGNETS.findNearby(context));
        nearby.add(context);
        for (final SimMovementContext other : nearby) {
            if (this.level.getBlockEntity(other.localBlockPos()) instanceof final MagneticHoseConnectorBlockEntity magnet && magnet.isPowered()) {
                final Direction direction = magnet.getFieldDirection();
                final Vector3d moment = new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
                other.orientation().transform(moment);
                final Vec3 at = other.globalPosition();
                magnets.put(new Vector3d(at.x, at.y, at.z), moment);
            }
        }
        final int lifetime = 4 + (int) (20.0F * this.level.random.nextFloat());
        this.fieldEmitters.add(new MagnetFieldEmitter(start, magnets, lifetime, serverLevel, negative));
    }

    private Direction getFieldDirection() {
        return this.isInverted() ? this.getBaseDirection().getOpposite() : this.getBaseDirection();
    }
    //#endregion

    //#region // --- POWER --- //
    // * Normal and inverted connectors attract, two of the same kind ignore each other
    public boolean isInverted() {
        return this.getBlockState().getValue(MagneticHoseConnectorBlock.INVERTED);
    }

    public boolean hasOppositePolarity(final MagneticHoseConnectorBlockEntity other) {
        return this.isInverted() != other.isInverted();
    }

    public boolean isPowered() {
        return this.getBlockState().getValue(MagneticHoseConnectorBlock.POWERED);
    }

    // * A powered hose winch powers the magnet hanging off its hose
    private boolean isPoweredByWinch() {
        if (this.hoseHolder.ownsRope()) {
            return false;
        }
        final ServerRopeStrand strand = this.hoseHolder.getAttachedStrand();
        if (strand == null) {
            return false;
        }
        final RopeAttachment start = strand.getAttachment(RopeAttachmentPoint.START);
        if (start == null || !this.level.isLoaded(start.blockAttachment())) {
            return false;
        }
        return this.level.getBlockEntity(start.blockAttachment()) instanceof final HoseWinchBlockEntity winch && winch.isPowered();
    }
    //#endregion

    //#region // --- PAIRING AND COUPLING --- //
    private void searchForPairs() {
        if (this.coupledPos != null) {
            if (!this.isLocked()) {
                MAGNETS.tryAddPair(this.level, this.worldPosition, this.coupledPos, MagneticHosePair::new);
            }
            return;
        }

        // * Reach for the single closest powered connector in range
        final Map<SectionPos, HashSet<BlockPos>> sections = MAGNETS.magnetMap.get(this.level);
        if (sections == null) {
            this.closestPairDistance = Double.MAX_VALUE;
            return;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        final double range = MagneticHosePair.MAGNET_RANGE;
        final Vector3d relative = new Vector3d();
        MagneticHoseConnectorBlockEntity best = null;
        double closest = range;

        for (final HashSet<BlockPos> positions : sections.values()) {
            for (final BlockPos pos : positions) {
                if (pos.equals(this.worldPosition) || !this.level.isLoaded(pos)
                        || !(this.level.getBlockEntity(pos) instanceof final MagneticHoseConnectorBlockEntity magnet)) {
                    continue;
                }
                // * Two magnets on the same grid can never move towards each other
                if (magnet.coupledPos != null || magnet.getQuickConnectPos() != null || !magnet.isPowered() || !this.hasOppositePolarity(magnet)
                        || Sable.HELPER.getContaining(magnet) == subLevel) {
                    continue;
                }
                final double distance = MagneticHosePair.getRelativeTipPosition(this, magnet, relative).length();
                if (distance < closest) {
                    closest = distance;
                    best = magnet;
                }
            }
        }

        if (best != null) {
            MAGNETS.tryAddPair(this.level, this.worldPosition, best.worldPosition, MagneticHosePair::new);
        }
        this.closestPairDistance = best != null ? closest : Double.MAX_VALUE;
    }

    private void validateCoupling(final boolean powered) {
        final MagneticHoseConnectorBlockEntity partner = this.getCoupledPartner();
        if (!powered || partner == null || !partner.isPowered() || !this.hasOppositePolarity(partner)) {
            this.decouple();
            return;
        }

        if (!this.isLocked()) {
            final double limit = MagneticHosePair.COUPLING_DISTANCE * 2.0;
            if (MagneticHosePair.getRelativeTipPosition(this, partner, new Vector3d()).lengthSquared() > limit * limit) {
                this.decouple();
                return;
            }

            if (MagneticHosePair.MAGNETIC_LOCK && this.worldPosition.compareTo(partner.worldPosition) < 0) {
                createLock(this, partner, true);
            }
        }
    }

    public static void couple(final MagneticHoseConnectorBlockEntity a, final MagneticHoseConnectorBlockEntity b) {
        a.setCoupled(b);
        b.setCoupled(a);

        if (MagneticHosePair.MAGNETIC_LOCK) {
            createLock(a, b, false);
        }

        final Vec3 at = a.getWorldBasePosition();
        a.level.playSound(null, at.x, at.y, at.z, SimSoundEvents.DOCKING_CONNECTOR_DOCKS.event(), SoundSource.BLOCKS, 1.0F, 1.1F + a.level.random.nextFloat() * 0.2F);
    }

    private void setCoupled(final MagneticHoseConnectorBlockEntity other) {
        final SubLevel otherSubLevel = Sable.HELPER.getContaining(other);
        this.coupledPos = other.worldPosition;
        this.coupledSubLevelId = otherSubLevel != null ? otherSubLevel.getUniqueId() : null;
        this.closestPairDistance = Double.MAX_VALUE;
        this.sendData();
    }

    // * Pins the two sub-levels together
    private static void createLock(MagneticHoseConnectorBlockEntity a, MagneticHoseConnectorBlockEntity b, final boolean instant) {
        if (!(a.level instanceof ServerLevel)) {
            return;
        }

        ServerSubLevel subLevelA = (ServerSubLevel) Sable.HELPER.getContaining(a);
        ServerSubLevel subLevelB = (ServerSubLevel) Sable.HELPER.getContaining(b);
        if (subLevelA == subLevelB) {
            return;
        }
        if (subLevelA == null) {
            final MagneticHoseConnectorBlockEntity swap = a;
            a = b;
            b = swap;
            subLevelA = subLevelB;
            subLevelB = null;
        }

        final Quaterniond orientationA = new Quaterniond(subLevelA.logicalPose().orientation());
        final Quaterniond orientationB = subLevelB == null ? new Quaterniond() : new Quaterniond(subLevelB.logicalPose().orientation());

        final Vector3d offset = MagneticHosePair.getRelativeTipPosition(a, b, new Vector3d());
        orientationB.transformInverse(offset);

        final Quaterniond relativeOrientation = new Quaterniond(orientationB).premul(orientationA.conjugate(new Quaterniond()));

        final Quaterniond targetOrientation = SimMathUtils.clampQuaternionToGrid(relativeOrientation, SimMathUtils.GridQuats.ALL, new Quaterniond());

        b.removeLock();
        b.lockSmoother = null;
        a.lockSmoother = new LockSmoother(b.worldPosition, offset, relativeOrientation, targetOrientation);
        a.lockSmoother.step(a, instant ? 1.0 : 0.0);
        if (instant) {
            a.lockSmoother = null;
        }
    }

    private static final class LockSmoother {
        private final BlockPos partnerPos;
        private final Vector3d initialOffset;
        private final Quaterniond initialOrientation;
        private final Quaterniond targetOrientation;
        private int ticks;

        private LockSmoother(final BlockPos partnerPos, final Vector3d initialOffset, final Quaterniond initialOrientation, final Quaterniond targetOrientation) {
            this.partnerPos = partnerPos;
            this.initialOffset = initialOffset;
            this.initialOrientation = initialOrientation;
            this.targetOrientation = targetOrientation;
        }

        private void step(final MagneticHoseConnectorBlockEntity owner, final double progress) {
            if (!(owner.level instanceof final ServerLevel level)
                    || !(level.getBlockEntity(this.partnerPos) instanceof final MagneticHoseConnectorBlockEntity partner)) {
                return;
            }
            final ServerSubLevel subLevelA = (ServerSubLevel) Sable.HELPER.getContaining(owner);
            final ServerSubLevel subLevelB = (ServerSubLevel) Sable.HELPER.getContaining(partner);
            final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (subLevelA == null || subLevelA == subLevelB || container == null) {
                return;
            }

            final double lerp = Mth.clamp(progress * progress, 0.0, 1.0);
            final double rotationLerp = Mth.clamp(lerp * 2.0, 0.0, 1.0);

            final Vector3d anchorA = JOMLConversion.toJOML(owner.getTipPosition());
            final Vector3d anchorB = JOMLConversion.toJOML(partner.getTipPosition()).fma(1.0 - lerp, this.initialOffset);
            final Quaterniond orientation = this.initialOrientation.slerp(this.targetOrientation, rotationLerp, new Quaterniond());

            owner.removeConstraint();
            owner.lock = container.physicsSystem().getPipeline().addConstraint(subLevelA, subLevelB,
                    new FixedConstraintConfiguration(anchorA, anchorB, orientation));
        }
    }

    @Override
    public void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {
        final LockSmoother smoother = this.lockSmoother;
        if (smoother == null || !(this.level instanceof final ServerLevel level)) {
            return;
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        final double partial = container != null ? container.physicsSystem().getPartialPhysicsTick() : 0.0;
        smoother.step(this, (smoother.ticks + partial) / LOCK_TICKS);
    }

    public void decouple() {
        final MagneticHoseConnectorBlockEntity partner = this.getCoupledPartner();
        this.clearCoupling();
        if (partner != null) {
            partner.clearCoupling();
        }

        if (this.level != null && !this.level.isClientSide) {
            final Vec3 at = this.getWorldBasePosition();
            this.level.playSound(null, at.x, at.y, at.z, SoundEvents.NETHERITE_BLOCK_FALL, SoundSource.BLOCKS, 0.4F, 0.75F);
        }
    }

    private void clearCoupling() {
        this.removeLock();
        this.coupledPos = null;
        this.coupledSubLevelId = null;
        this.closestPairDistance = Double.MAX_VALUE;
        if (this.level != null && !this.level.isClientSide && !this.isRemoved()) {
            this.sendData();
        }
    }

    private void removeLock() {
        this.lockSmoother = null;
        this.removeConstraint();
    }

    private void removeConstraint() {
        if (this.lock != null) {
            this.lock.remove();
            this.lock = null;
        }
    }

    public boolean isCoupled() {
        return this.coupledPos != null;
    }

    public boolean isLocked() {
        if (this.lock != null) {
            return true;
        }
        final MagneticHoseConnectorBlockEntity partner = this.getCoupledPartner();
        return partner != null && partner.lock != null;
    }

    @Nullable
    public MagneticHoseConnectorBlockEntity getCoupledPartner() {
        if (this.coupledPos == null || this.level == null || !this.level.isLoaded(this.coupledPos)) {
            return null;
        }
        if (this.level.getBlockEntity(this.coupledPos) instanceof final MagneticHoseConnectorBlockEntity partner
                && Objects.equals(partner.coupledPos, this.worldPosition)) {
            return partner;
        }
        return null;
    }

    // * Coupled connectors pass fluid straight across like a pipe joint
    @Override
    public @Nullable HoseEndpoint getCoupledEndpoint() {
        final MagneticHoseConnectorBlockEntity partner = this.getCoupledPartner();
        return partner != null ? partner : super.getCoupledEndpoint();
    }

    @Override
    public boolean isCoupledElsewhere() {
        return this.isCoupled();
    }

    public double getClosestPairDistance() {
        return this.closestPairDistance;
    }

    public Direction getBaseDirection() {
        return this.getBlockState().getValue(MagneticHoseConnectorBlock.FACING).getOpposite();
    }

    public Direction getRedSlotDirection() {
        final BlockState state = this.getBlockState();
        final boolean alongFirst = state.getValue(MagneticHoseConnectorBlock.AXIS_ALONG_FIRST_COORDINATE);
        return switch (state.getValue(MagneticHoseConnectorBlock.FACING)) {
            case DOWN, UP -> alongFirst ? Direction.WEST : Direction.NORTH;
            case NORTH -> alongFirst ? Direction.EAST : Direction.UP;
            case SOUTH -> alongFirst ? Direction.WEST : Direction.UP;
            case EAST -> alongFirst ? Direction.UP : Direction.SOUTH;
            case WEST -> alongFirst ? Direction.UP : Direction.NORTH;
        };
    }

    public Vec3 getWorldBasePosition() {
        final Vec3 local = this.getTipPosition();
        final SubLevel subLevel = this.getLatestSubLevel();
        return subLevel != null ? subLevel.logicalPose().transformPosition(local) : local;
    }
    //#endregion

    //#region // --- SIMULATED MAGNET --- //
    @Override
    public Quaternionfc getOrientation() {
        return this.getBaseDirection().getRotation();
    }

    @Override
    public SubLevel getLatestSubLevel() {
        return Sable.HELPER.getContaining(this);
    }

    @Override
    public Vec3 getMagnetPosition() {
        return this.alongBase(MAGNET_OFFSET);
    }

    public Vec3 getTipPosition() {
        return this.alongBase(TIP_OFFSET);
    }

    private Vec3 alongBase(final double distance) {
        final Direction base = this.getBaseDirection();
        return Vec3.atCenterOf(this.worldPosition).add(base.getStepX() * distance, base.getStepY() * distance, base.getStepZ() * distance);
    }

    @Override
    public Vector3d setMagneticMoment(final Vector3d v) {
        final Direction base = this.getBaseDirection();
        return v.set(base.getStepX(), base.getStepY(), base.getStepZ()).mul(Math.sqrt(MagneticHosePair.MAGNET_STRENGTH));
    }

    @Override
    public boolean magnetActive() {
        return !this.isRemoved() && this.isPowered() && !this.isLocked();
    }
    //#endregion

    @Override
    public @Nullable Iterable<@NotNull SubLevel> sable$getConnectionDependencies() {
        final Iterable<SubLevel> hoseDependencies = super.sable$getConnectionDependencies();
        if (this.coupledSubLevelId == null || this.level == null) {
            return hoseDependencies;
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        final SubLevel coupled = container != null ? container.getSubLevel(this.coupledSubLevelId) : null;
        if (coupled == null) {
            return hoseDependencies;
        }

        final List<SubLevel> dependencies = new ObjectArrayList<>();
        if (hoseDependencies != null) {
            hoseDependencies.forEach(dependencies::add);
        }
        dependencies.add(coupled);
        return dependencies;
    }

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (this.coupledPos != null) {
            tag.put("CoupledTo", NbtUtils.writeBlockPos(this.coupledPos));
        }
        if (this.coupledSubLevelId != null) {
            tag.putUUID("CoupledSubLevel", this.coupledSubLevelId);
        }
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.coupledPos = tag.contains("CoupledTo") ? NbtUtils.readBlockPos(tag, "CoupledTo").orElse(null) : null;
        this.coupledSubLevelId = tag.hasUUID("CoupledSubLevel") ? tag.getUUID("CoupledSubLevel") : null;
    }

    // * Runs on break and on chunk unload
    @Override
    public void invalidate() {
        super.invalidate();
        this.removeLock();
    }
}
