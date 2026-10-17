package edn.lakeopossmc.simulatedhoses.blocks;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.index.SimSoundEvents;
import edn.lakeopossmc.simulatedhoses.client.HoseDrips;
import edn.lakeopossmc.simulatedhoses.hose.HoseEndpoint;
import edn.lakeopossmc.simulatedhoses.hose.HoseGoggles;
import edn.lakeopossmc.simulatedhoses.hose.HoseHolderBehavior;
import edn.lakeopossmc.simulatedhoses.hose.HosePortHandler;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3d;

import java.util.List;

// --- HOSE WINCH BLOCK ENTITY --- //
public class HoseWinchBlockEntity extends KineticBlockEntity implements RopeStrandHolderBlockEntity, HoseEndpoint {
    private static final int SYNC_INTERVAL = 10;

    private HoseHolderBehavior hoseHolder;
    private final HosePortHandler portHandler = new HosePortHandler(this);

    private int stretchTimer = 0;
    private boolean stretched;
    private boolean stretchedLastTick;

    private int flow;
    private int lastSyncedFlow;
    private FluidStack fluid = FluidStack.EMPTY;
    private FluidStack lastSyncedFluid = FluidStack.EMPTY;
    private int syncTimer;
    private Direction lastPort;

    // * How far the coil texture has scrolled, client only
    private final LerpedFloat clientAngle = LerpedFloat.linear();

    public HoseWinchBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(this.hoseHolder = new HoseHolderBehavior(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level.isClientSide) {
            this.invalidateRenderBoundingBox();
            this.tickDrips();
            this.clientAngle.setValue(this.clientAngle.getValue() + this.getMovementSpeed());
            return;
        }

        final ServerRopeStrand strand = this.hoseHolder.getOwnedStrand();
        if (strand != null && this.hoseHolder.ownsRope()) {
            this.updateHoseExtension(strand);
        }
        this.portHandler.tick();
        this.flow = this.portHandler.getFlowThisTick();
        this.fluid = this.portHandler.getLastFluid();

        final Direction port = this.getPortDirection();
        if (port != this.lastPort) {
            if (this.lastPort != null) {
                this.level.invalidateCapabilities(this.worldPosition);
            }
            this.lastPort = port;
        }

        this.updateMagneticState();

        if (this.stretchTimer > 0) {
            this.stretchTimer--;
        }

        this.syncIfNeeded();
    }

    private void updateMagneticState() {
        final boolean magnetic;
        if (!this.hoseHolder.isAttached()) {
            magnetic = false;
        } else {
            final BlockEntity partner = this.hoseHolder.getPartner();
            // * Other end not loaded, keep whatever we were
            if (partner == null) {
                return;
            }
            magnetic = partner instanceof MagneticHoseConnectorBlockEntity;
        }
        final BlockState state = this.getBlockState();
        if (state.getValue(HoseWinchBlock.MAGNETIC) != magnetic) {
            this.level.setBlock(this.worldPosition, state.setValue(HoseWinchBlock.MAGNETIC, magnetic), 3);
        }
    }

    public boolean isPowered() {
        return this.getBlockState().getValue(HoseWinchBlock.POWERED);
    }

    public boolean isMagnetic() {
        return this.getBlockState().getValue(HoseWinchBlock.MAGNETIC);
    }

    //#region // --- REELING --- //
    private void updateHoseExtension(final ServerRopeStrand strand) {
        float movementSpeed = this.getMovementSpeed();

        final double desiredExtension = strand.getExtension() + (strand.getPoints().size() - 2) * ServerRopeStrand.SEGMENT_LENGTH;
        final double currentExtension = strand.getCurrentExtension();

        // * Too stretched to reel in any further
        this.stretched = currentExtension > desiredExtension * (1.0 + HoseHolderBehavior.winchStretchAllowed() / 100.0);
        if (this.stretched) {
            if (!this.stretchedLastTick) {
                this.effects.triggerOverStressedEffect();
            }
            if (this.stretchTimer == 0) {
                this.stretchTimer = this.level.random.nextIntBetweenInclusive(5 * 20, 15 * 20);
                this.level.playSound(null, this.getBlockPos(), SimSoundEvents.ROPE_WINCH_STRETCH.event(), SoundSource.BLOCKS, 0.1f, 0.8f + this.level.random.nextFloat() * 0.2f);
            }
            movementSpeed = Math.max(0.0f, movementSpeed);
        }
        this.stretchedLastTick = this.stretched;

        // * Out of hose
        if (currentExtension > HoseHolderBehavior.maxHoseLength()) {
            movementSpeed = Math.min(0.0f, movementSpeed);
        }

        double extension = strand.getExtension() + movementSpeed;
        final int minPointCount = 2;

        if (extension < 1.0 && strand.getPoints().size() == minPointCount) {
            extension = 1.0;
        } else {
            while (extension < 0.0) {
                strand.removeFirstPoint();
                extension += ServerRopeStrand.SEGMENT_LENGTH;

                if (extension < 1.0 && strand.getPoints().size() == minPointCount) {
                    extension = 1.0;
                    break;
                }
            }

            while (extension > ServerRopeStrand.SEGMENT_LENGTH) {
                final Vector3d point = JOMLConversion.toJOML(Sable.HELPER.projectOutOfSubLevel(this.level, this.hoseHolder.getAttachmentPoint()));
                strand.addPoint(point);
                extension -= 1.0;
            }

            if (extension < 1.0 && strand.getPoints().size() <= minPointCount) {
                extension = 1.0;
            }
        }

        strand.updateFirstSegmentExtension(extension);
    }

    public float getMovementSpeed() {
        return Mth.clamp(convertToLinear(this.getSpeed()), -.49f, .49f);
    }

    public float getClientAngle(final float partialTicks) {
        return this.clientAngle.getValue(partialTicks);
    }

    public boolean isStretched() {
        return this.stretched;
    }
    //#endregion

    //#region // --- SYNC AND SAVE --- //
    private void syncIfNeeded() {
        if (this.syncTimer > 0) {
            this.syncTimer--;
        }
        final boolean flowStateChanged = (this.flow == 0) != (this.lastSyncedFlow == 0)
                || !FluidStack.isSameFluidSameComponents(this.fluid, this.lastSyncedFluid);
        if (flowStateChanged || (this.flow != this.lastSyncedFlow && this.syncTimer <= 0)) {
            this.lastSyncedFlow = this.flow;
            this.lastSyncedFluid = this.fluid;
            this.syncTimer = SYNC_INTERVAL;
            this.sendData();
        }
    }

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putInt("Flow", this.flow);
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
        if (clientPacket) {
            this.flow = tag.getInt("Flow");
            this.fluid = FluidStack.parseOptional(registries, tag.getCompound("HoseFluid"));
        } else {
            this.portHandler.read(tag, registries);
        }
    }

    // * Runs on break and on chunk unload
    @Override
    public void invalidate() {
        super.invalidate();
        this.portHandler.release();
    }
    //#endregion

    private void tickDrips() {
        if (!this.hoseHolder.ownsRope()) {
            return;
        }
        final ClientRopeStrand strand = this.hoseHolder.getClientStrand();
        if (strand != null) {
            HoseDrips.tick(this.level, strand, this.fluid, this.flow);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        final ClientRopeStrand rope = this.hoseHolder.getClientStrand();
        if (rope != null && this.hoseHolder.ownsRope()) {
            final AABB bounds = rope.getBounds();
            if (bounds != null) {
                return bounds.inflate(HoseConnectorBlockEntity.RENDER_BOUNDING_BOX_INFLATION);
            }
        }
        return super.getRenderBoundingBox();
    }

    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        final boolean kinetic = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        final boolean fluid = HoseGoggles.addFlowTooltip(tooltip, this);
        return kinetic || fluid;
    }

    //#region // --- ROPE HOLDER --- //
    @Override
    public RopeStrandHolderBehavior getBehavior() {
        return this.hoseHolder;
    }

    @Override
    public Vec3 getAttachmentPoint(final BlockPos pos, final BlockState state) {
        return pos.getCenter();
    }
    //#endregion

    //#region // --- HOSE ENDPOINT --- //
    @Override
    public HoseHolderBehavior getHoseHolder() {
        return this.hoseHolder;
    }

    @Override
    public HosePortHandler getPortHandler() {
        return this.portHandler;
    }

    @Override
    public boolean placesSpilledFluid() {
        return true;
    }

    @Override
    public Direction getPortDirection() {
        return HoseWinchBlock.getPipeDirection(this.getBlockState());
    }

    @Override
    public void setHoseFlow(final int flow) {
        this.flow = flow;
    }

    @Override
    public int getHoseFlow() {
        return this.flow;
    }
    //#endregion
}
