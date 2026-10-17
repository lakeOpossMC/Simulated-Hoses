package edn.lakeopossmc.simulatedhoses.blocks;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import edn.lakeopossmc.simulatedhoses.client.HoseDrips;
import edn.lakeopossmc.simulatedhoses.client.HoseSpray;
import edn.lakeopossmc.simulatedhoses.hose.HoseEndpoint;
import edn.lakeopossmc.simulatedhoses.hose.HoseGoggles;
import edn.lakeopossmc.simulatedhoses.hose.HoseHolderBehavior;
import edn.lakeopossmc.simulatedhoses.hose.HosePortHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// --- HOSE CONNECTOR BLOCK ENTITY --- //
// * Works like a pipe
public class HoseConnectorBlockEntity extends SmartBlockEntity implements RopeStrandHolderBlockEntity, HoseEndpoint, IHaveGoggleInformation {
    public static final double RENDER_BOUNDING_BOX_INFLATION = 3.0;

    private static final double ATTACHMENT_OFFSET = -3.0 / 16.0;
    private static final int SYNC_INTERVAL = 10;

    protected HoseHolderBehavior hoseHolder;
    protected final HosePortHandler portHandler = new HosePortHandler(this);
    private int flow;
    private int lastSyncedFlow;
    private FluidStack fluid = FluidStack.EMPTY;
    private FluidStack lastSyncedFluid = FluidStack.EMPTY;
    private boolean spraying;
    private boolean lastSyncedSpraying;
    private int syncTimer;
    private Direction lastPort;
    @Nullable
    private BlockPos quickConnectPos;

    private static final Map<Level, Set<HoseConnectorBlockEntity>> LOADED = new WeakHashMap<>();

    public static Iterable<HoseConnectorBlockEntity> loaded(final Level level) {
        final Set<HoseConnectorBlockEntity> set = LOADED.get(level);
        return set == null ? List.of() : List.copyOf(set);
    }

    public HoseConnectorBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        behaviours.add(this.hoseHolder = new HoseHolderBehavior(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level.isClientSide) {
            this.invalidateRenderBoundingBox();
            this.tickDrips();
            if (this.spraying) {
                HoseSpray.tick(this.level, this.worldPosition, this.getPortDirection(), 0.5, this.fluid, Math.max(this.flow, 1));
            }
            return;
        }

        LOADED.computeIfAbsent(this.level, $ -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
        this.validateQuickConnect();

        this.portHandler.tick();
        this.flow = this.portHandler.getFlowThisTick();
        this.fluid = this.portHandler.getLastFluid();
        this.spraying = this.portHandler.isSpraying();

        final Direction port = this.getPortDirection();
        if (port != this.lastPort) {
            if (this.lastPort != null) {
                this.level.invalidateCapabilities(this.worldPosition);
            }
            this.lastPort = port;
        }
        this.syncIfNeeded();
    }

    protected void syncIfNeeded() {
        if (this.syncTimer > 0) {
            this.syncTimer--;
        }
        final boolean flowStateChanged = (this.flow == 0) != (this.lastSyncedFlow == 0)
                || !FluidStack.isSameFluidSameComponents(this.fluid, this.lastSyncedFluid)
                || this.spraying != this.lastSyncedSpraying;
        if (flowStateChanged || (this.flow != this.lastSyncedFlow && this.syncTimer <= 0)) {
            this.lastSyncedFlow = this.flow;
            this.lastSyncedFluid = this.fluid;
            this.lastSyncedSpraying = this.spraying;
            this.syncTimer = SYNC_INTERVAL;
            this.sendData();
        }
    }

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putInt("Flow", this.flow);
            tag.putBoolean("Spraying", this.spraying);
            if (!this.fluid.isEmpty()) {
                tag.put("HoseFluid", this.fluid.save(registries));
            }
        } else {
            this.portHandler.write(tag, registries);
            if (this.quickConnectPos != null) {
                tag.put("QuickConnect", NbtUtils.writeBlockPos(this.quickConnectPos));
            }
        }
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            this.flow = tag.getInt("Flow");
            this.spraying = tag.getBoolean("Spraying");
            this.fluid = FluidStack.parseOptional(registries, tag.getCompound("HoseFluid"));
        } else {
            this.portHandler.read(tag, registries);
            this.quickConnectPos = tag.contains("QuickConnect") ? NbtUtils.readBlockPos(tag, "QuickConnect").orElse(null) : null;
        }
    }

    // * Runs on break and on chunk unload
    @Override
    public void invalidate() {
        super.invalidate();
        this.portHandler.release();
        final Set<HoseConnectorBlockEntity> set = this.level != null ? LOADED.get(this.level) : null;
        if (set != null) {
            set.remove(this);
        }
    }

    //#region // --- QUICK CONNECT --- //
    public Direction getFlangeDirection() {
        return this.getBlockState().getValue(HoseConnectorBlock.FACING).getOpposite();
    }

    // * Center of the flange face
    public Vec3 getFlangePosition() {
        final Direction flange = this.getFlangeDirection();
        return Vec3.atCenterOf(this.worldPosition).add(flange.getStepX() * 0.5, flange.getStepY() * 0.5, flange.getStepZ() * 0.5);
    }

    public Vector3d worldFlangePosition() {
        final Vec3 local = this.getFlangePosition();
        return Sable.HELPER.projectOutOfSubLevel(this.level, new Vector3d(local.x, local.y, local.z));
    }

    @Nullable
    public BlockPos getQuickConnectPos() {
        return this.quickConnectPos;
    }

    public void setQuickConnectPos(@Nullable final BlockPos pos) {
        this.quickConnectPos = pos;
        this.setChanged();
    }

    // * Locked onto something other than a quick connect
    public boolean isCoupledElsewhere() {
        return false;
    }

    @Nullable
    public FluidQuickConnectBlockEntity getQuickConnect() {
        if (this.quickConnectPos == null || this.level == null || !this.level.isLoaded(this.quickConnectPos)) {
            return null;
        }
        if (this.level.getBlockEntity(this.quickConnectPos) instanceof final FluidQuickConnectBlockEntity quickConnect
                && this.worldPosition.equals(quickConnect.getCoupledConnectorPos())) {
            return quickConnect;
        }
        return null;
    }

    // * Forget a quick connect that was broken or let go while this was unloaded
    private void validateQuickConnect() {
        if (this.quickConnectPos != null && this.level.isLoaded(this.quickConnectPos) && this.getQuickConnect() == null) {
            this.setQuickConnectPos(null);
        }
    }

    // * Locked onto a quick connect
    @Override
    public @Nullable HoseEndpoint getCoupledEndpoint() {
        return this.getQuickConnect();
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
                return bounds.inflate(RENDER_BOUNDING_BOX_INFLATION);
            }
        }
        return super.getRenderBoundingBox();
    }

    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        return HoseGoggles.addFlowTooltip(tooltip, this);
    }

    //#region // --- ROPE HOLDER --- //
    @Override
    public RopeStrandHolderBehavior getBehavior() {
        return this.hoseHolder;
    }

    @Override
    public Vec3 getAttachmentPoint(final BlockPos pos, final BlockState state) {
        final Direction facing = state.getValue(HoseConnectorBlock.FACING);
        final double offset = this.getAttachmentOffset();
        return pos.getCenter().add(facing.getStepX() * offset, facing.getStepY() * offset, facing.getStepZ() * offset);
    }

    protected double getAttachmentOffset() {
        return ATTACHMENT_OFFSET;
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
    public boolean spillsToParentLevel() {
        return true;
    }

    @Override
    public Direction getPortDirection() {
        return this.getBlockState().getValue(HoseConnectorBlock.FACING).getOpposite();
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
