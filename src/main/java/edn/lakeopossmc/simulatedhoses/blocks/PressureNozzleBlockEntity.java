package edn.lakeopossmc.simulatedhoses.blocks;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import edn.lakeopossmc.simulatedhoses.HoseFluids;
import edn.lakeopossmc.simulatedhoses.client.NozzleSpray;
import edn.lakeopossmc.simulatedhoses.client.NozzleSpraySound;
import edn.lakeopossmc.simulatedhoses.fluids.PlantFoodSounds;
import edn.lakeopossmc.simulatedhoses.hose.HoseGoggles;
import edn.lakeopossmc.simulatedhoses.hose.HoseGrabHandler;
import edn.lakeopossmc.simulatedhoses.hose.NozzleClouds;
import edn.lakeopossmc.simulatedhoses.hose.NozzleConsumingEffects;
import edn.lakeopossmc.simulatedhoses.hose.NozzleGrowth;
import edn.lakeopossmc.simulatedhoses.hose.NozzleJet;
import edn.lakeopossmc.simulatedhoses.hose.NozzleProcessing;
import edn.lakeopossmc.simulatedhoses.hose.NozzleSprayEffects;
import edn.lakeopossmc.simulatedhoses.hose.SpraySource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// --- PRESSURE NOZZLE BLOCK ENTITY --- //
// * Closed until whoever is carrying it pulls the trigger
public class PressureNozzleBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, SpraySource {
    public static final float FULL_FLOW = 250.0F;
    private static final int MAX_INTAKE = 250;
    private static final int SYNC_INTERVAL = 10;
    private static final double TIP_FACE = 18.0 / 16.0 - 0.5;
    private static final double JET_START = 0.5 + 1.0 / 32.0;

    // * Shared with the Super Soaker
    public static final float MIN_SPIN = 3.0F;
    public static final float MAX_SPIN = 40.0F;
    public static final float SPIN_EASE = 0.15F;
    public static final float NOZZLE_PRESS = 1.0F;
    public static final float TIP_EXTRA_MIN = 1.0F;
    public static final float TIP_EXTRA_MAX = 2.0F;
    public static final float PRESS_EASE = 0.45F;

    private final IFluidHandler intake = new Intake();
    private final NozzleProcessing.BlockProgress spoutProgress = new NozzleProcessing.BlockProgress();
    private final NozzleClouds.Progress cloudProgress = new NozzleClouds.Progress();
    private final NozzleConsumingEffects.Progress consumingProgress = new NozzleConsumingEffects.Progress();
    private final NozzleGrowth.Progress growthProgress = new NozzleGrowth.Progress();
    private boolean triggered;

    private long flowTick = -1;
    private int flowNow;
    private int flowPrevious;

    private int flow;
    private int lastSyncedFlow;
    private FluidStack fluid = FluidStack.EMPTY;
    private FluidStack lastSyncedFluid = FluidStack.EMPTY;
    private boolean spraying;
    private boolean lastSyncedSpraying;
    private int syncTimer;

    private float cogAngle;
    private float cogAnglePrevious;
    private float cogSpeed;
    private float nozzlePress;
    private float nozzlePressPrevious;
    private float tipPress;
    private float tipPressPrevious;

    public PressureNozzleBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
    }

    public Direction getFacing() {
        return this.getBlockState().getValue(PressureNozzleBlock.FACING);
    }

    @Nullable
    public IFluidHandler getIntake(@Nullable final Direction side) {
        return side == null || side == this.getFacing().getOpposite() ? this.intake : null;
    }

    public BlockPos getConnectorPos() {
        return this.worldPosition.relative(this.getFacing().getOpposite());
    }

    @Nullable
    public HoseConnectorBlockEntity getConnector() {
        if (this.level == null) {
            return null;
        }
        return this.level.getBlockEntity(this.getConnectorPos()) instanceof final HoseConnectorBlockEntity connector
                && connector.getPortDirection() == this.getFacing() ? connector : null;
    }

    @Nullable
    public static PressureNozzleBlockEntity forCarried(final Level level, final BlockPos pos) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof final PressureNozzleBlockEntity nozzle) {
            return nozzle;
        }
        if (be instanceof final HoseConnectorBlockEntity connector) {
            return connector.getNozzle();
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level.isClientSide) {
            this.tickAnimation();
            if (this.spraying) {
                NozzleSpray.tick(this.level, this.worldPosition, this.getFacing(), TIP_FACE - this.tipPress / 16.0,
                        this.fluid, this.flow, FULL_FLOW);
                this.processingParticles();
                NozzleSpraySound.tick(this);
            }
            return;
        }

        this.triggered = HoseGrabHandler.isTriggerHeld(this.level, this.worldPosition, this.getConnectorPos());
        this.flow = this.getFlowThisTick();
        this.spraying = this.triggered && this.flow > 0;
        this.syncIfNeeded();
        if (this.spraying && this.level instanceof final ServerLevel serverLevel) {
            this.sprayAt(serverLevel);
        }
    }

    private static final float LANDING_SQUISH_CHANCE = 1.0F / 10.0F;

    private void processingParticles() {
        final double fraction = Mth.clamp(this.flow / FULL_FLOW, 0.0F, 1.0F);
        final NozzleJet.Aim aim = NozzleJet.aim(this.level, this.worldPosition, this.getFacing(), JET_START);
        final NozzleJet.Impact impact = NozzleJet.trace(this.level, aim, fraction, null);
        if (impact != null) {
            NozzleProcessing.spawnParticles(this.level, impact, this.fluid);
            // * Plant Food squishes where it lands too, now and then
            if (this.fluid.is(HoseFluids.PLANT_FOOD.get()) && this.level.random.nextFloat() < LANDING_SQUISH_CHANCE) {
                PlantFoodSounds.squishLocal(this.level, impact.point(), 0.5F + (float) fraction * 0.3F);
            }
        }
    }

    private void sprayAt(final ServerLevel level) {
        final double fraction = Mth.clamp(this.flow / FULL_FLOW, 0.0F, 1.0F);
        final NozzleJet.Aim aim = NozzleJet.aim(level, this.worldPosition, this.getFacing(), JET_START);
        Player carrier = HoseGrabHandler.getCarrier(level, this.worldPosition);
        if (carrier == null) {
            carrier = HoseGrabHandler.getCarrier(level, this.getConnectorPos());
        }
        final NozzleJet.Impact impact = NozzleJet.trace(level, aim, fraction, carrier);
        if (impact != null) {
            NozzleSprayEffects.apply(level, impact, this.fluid);
            NozzleSprayEffects.push(impact, fraction);
            NozzleProcessing.apply(level, impact, this.fluid, this.flow, this.spoutProgress);
            NozzleClouds.apply(level, impact, this.fluid, this.flow, carrier, this.cloudProgress);
            NozzleConsumingEffects.apply(level, impact, this.fluid, this.flow, this.consumingProgress);
            NozzleGrowth.apply(level, impact, this.fluid, this.flow, carrier, this.growthProgress);
        }
    }

    //#region // --- FLOW --- //
    private void rollFlow() {
        final long now = this.level.getGameTime();
        if (now != this.flowTick) {
            this.flowPrevious = now - this.flowTick == 1 ? this.flowNow : 0;
            this.flowTick = now;
            this.flowNow = 0;
        }
    }

    private int getFlowThisTick() {
        final long now = this.level.getGameTime();
        if (now == this.flowTick) {
            return Math.max(this.flowNow, this.flowPrevious);
        }
        return now - this.flowTick == 1 ? this.flowNow : 0;
    }

    public int getFlow() {
        return this.flow;
    }

    @Override
    public FluidStack getSprayFluid() {
        return this.fluid;
    }

    @Override
    public boolean isSpraying() {
        return this.spraying;
    }

    @Override
    public float getSprayFraction() {
        return Mth.clamp(this.flow / FULL_FLOW, 0.0F, 1.0F);
    }

    @Override
    public Vec3 getSprayTip() {
        return NozzleJet.aim(this.level, this.worldPosition, this.getFacing(), TIP_FACE).start();
    }

    private final class Intake implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(final int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(final int tank) {
            return MAX_INTAKE;
        }

        @Override
        public boolean isFluidValid(final int tank, final FluidStack stack) {
            return true;
        }

        @Override
        public int fill(final FluidStack resource, final FluidAction action) {
            final PressureNozzleBlockEntity nozzle = PressureNozzleBlockEntity.this;
            if (resource.isEmpty() || nozzle.level == null || nozzle.level.isClientSide || !nozzle.triggered) {
                return 0;
            }
            nozzle.rollFlow();
            final int amount = Math.min(resource.getAmount(), MAX_INTAKE - nozzle.flowNow);
            if (amount <= 0) {
                return 0;
            }
            if (action.execute()) {
                nozzle.flowNow += amount;
                if (!FluidStack.isSameFluidSameComponents(nozzle.fluid, resource)) {
                    nozzle.fluid = resource.copyWithAmount(1);
                }
            }
            return amount;
        }

        @Override
        public FluidStack drain(final FluidStack resource, final FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(final int maxDrain, final FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
    //#endregion

    //#region // --- CLIENT ANIMATION --- //
    private void tickAnimation() {
        final float fraction = this.spraying ? Mth.clamp(this.flow / FULL_FLOW, 0.0F, 1.0F) : 0.0F;

        final float spinTarget = this.spraying ? Mth.lerp(fraction, MIN_SPIN, MAX_SPIN) : 0.0F;
        this.cogSpeed += (spinTarget - this.cogSpeed) * SPIN_EASE;
        if (spinTarget == 0.0F && Math.abs(this.cogSpeed) < 0.01F) {
            this.cogSpeed = 0.0F;
        }
        this.cogAnglePrevious = this.cogAngle;
        this.cogAngle += this.cogSpeed;
        if (this.cogAngle >= 360.0F) {
            this.cogAngle -= 360.0F;
            this.cogAnglePrevious -= 360.0F;
        }

        this.nozzlePressPrevious = this.nozzlePress;
        this.tipPressPrevious = this.tipPress;
        final float nozzleTarget = this.spraying ? NOZZLE_PRESS : 0.0F;
        final float tipTarget = this.spraying ? NOZZLE_PRESS + Mth.lerp(fraction, TIP_EXTRA_MIN, TIP_EXTRA_MAX) : 0.0F;
        this.nozzlePress += (nozzleTarget - this.nozzlePress) * PRESS_EASE;
        this.tipPress += (tipTarget - this.tipPress) * PRESS_EASE;
    }

    public float getCogAngle(final float partialTicks) {
        return Mth.lerp(partialTicks, this.cogAnglePrevious, this.cogAngle);
    }

    public float getNozzleOffset(final float partialTicks) {
        return Mth.lerp(partialTicks, this.nozzlePressPrevious, this.nozzlePress);
    }

    public float getTipOffset(final float partialTicks) {
        return Mth.lerp(partialTicks, this.tipPressPrevious, this.tipPress);
    }
    //#endregion

    //#region // --- SYNCING --- //
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
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putInt("Flow", this.flow);
            tag.putBoolean("Spraying", this.spraying);
            if (!this.fluid.isEmpty()) {
                tag.put("HoseFluid", this.fluid.save(registries));
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
        }
    }
    //#endregion

    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        if (this.getConnector() == null) {
            return false;
        }
        HoseGoggles.addFlowLines(tooltip, this.flow);
        return true;
    }
}
