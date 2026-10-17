package edn.lakeopossmc.simulatedhoses.hose;

import com.simibubi.create.content.fluids.PipeConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// --- THE FLUID HANDLER ON A HOSE PORT --- //
// * A hose works like a length of pipe between its two ports
public class HosePortHandler implements IFluidHandler {
    // * The most fluid (mB per tick) a pump can push or pull through one hose
    private static final int HOSE_THROUGHPUT = 250;
    private static final int OUTLET_CACHE_TICKS = 10;
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    private static final int MAX_DEPTH = 4;

    private enum Drive { NONE, PUSH_OUT, PULL_IN }

    private final HoseEndpoint owner;
    private final FluidTank outBuffer = new FluidTank(1000);

    private long budgetTick = -1;
    private int usedThisTick;

    private long flowTick = -1;
    private int flowNow;
    private int flowPrevious;
    private FluidStack lastFluid = FluidStack.EMPTY;

    private long outletsTick = -1;
    private List<IFluidHandler> cachedOutlets = List.of();
    private final HoseOpenEnd openEnd;
    private long sprayTick = -1;

    private Drive drive = Drive.NONE;
    private float drivePressure;
    private float appliedOnNeighbour;
    private List<HosePressure.Applied> appliedPressure = List.of();
    private static final int DRIVE_CHECK_INTERVAL = 5;
    private static final int DRIVE_SETTLE_TICKS = 10;
    private Drive pendingDrive = Drive.NONE;
    private int pendingTicks;
    private int checkTimer;

    private long cacheTick = -1;
    @Nullable
    private HosePortHandler cachedPartner;
    private boolean cachedAgainstPipe;

    public HosePortHandler(final HoseEndpoint owner) {
        this.owner = owner;
        this.openEnd = new HoseOpenEnd(owner, this);
    }

    private Level level() {
        return this.owner.asBlockEntity().getLevel();
    }

    private BlockPos pos() {
        return this.owner.asBlockEntity().getBlockPos();
    }

    //#region // --- ROUTING --- //
    @Nullable
    private HosePortHandler partner() {
        this.refreshCache();
        return this.cachedPartner;
    }

    public boolean isAgainstPipe() {
        this.refreshCache();
        return this.cachedAgainstPipe;
    }

    private void refreshCache() {
        final Level level = this.level();
        if (level == null) {
            this.cachedPartner = null;
            this.cachedAgainstPipe = false;
            return;
        }
        final long now = level.getGameTime();
        if (now == this.cacheTick) {
            return;
        }
        this.cacheTick = now;
        final HoseEndpoint far = HoseNetwork.findFarEnd(this.owner);
        this.cachedPartner = far == null ? null : far.getPortHandler();
        this.cachedAgainstPipe = !this.owner.isPortMated() && HosePressure.isAgainstPipe(level, this.pos(), this.owner.getPortDirection());
    }

    List<IFluidHandler> localOutlets() {
        final Level level = this.level();
        if (level == null) {
            return List.of();
        }
        final long now = level.getGameTime();
        if (this.outletsTick < 0 || now - this.outletsTick >= OUTLET_CACHE_TICKS || now < this.outletsTick) {
            this.cachedOutlets = HoseNetwork.findOutlets(this.owner, this::openEndHandler);
            this.outletsTick = now;
        }
        return this.cachedOutlets;
    }

    private IFluidHandler openEndHandler() {
        return this.openEnd.handler();
    }

    void markSprayed(final FluidStack pushed) {
        final Level level = this.level();
        if (level == null || level.isClientSide) {
            return;
        }
        this.sprayTick = level.getGameTime();
        if (!FluidStack.isSameFluidSameComponents(this.lastFluid, pushed)) {
            this.lastFluid = pushed.copyWithAmount(1);
        }
    }

    public boolean isSpraying() {
        final Level level = this.level();
        return level != null && this.sprayTick >= 0 && level.getGameTime() - this.sprayTick <= 4;
    }

    private int remainingBudget() {
        final Level level = this.level();
        if (level == null || level.isClientSide) {
            return 0;
        }
        final long now = level.getGameTime();
        if (now != this.budgetTick) {
            this.budgetTick = now;
            this.usedThisTick = 0;
        }
        return Math.max(0, HOSE_THROUGHPUT - this.usedThisTick);
    }

    private void recordAlongHose(final FluidStack moved) {
        final int amount = moved.getAmount();
        this.usedThisTick += amount;
        for (final HoseEndpoint endpoint : HoseNetwork.chain(this.owner)) {
            endpoint.getPortHandler().recordFlow(amount, moved);
        }
    }

    private void recordFlow(final int amount, final FluidStack moved) {
        if (!FluidStack.isSameFluidSameComponents(this.lastFluid, moved)) {
            this.lastFluid = moved.copyWithAmount(1);
        }
        final Level level = this.level();
        if (level == null) {
            return;
        }
        final long now = level.getGameTime();
        if (now != this.flowTick) {
            this.flowPrevious = now - this.flowTick == 1 ? this.flowNow : 0;
            this.flowTick = now;
            this.flowNow = 0;
        }
        this.flowNow += amount;
    }

    public FluidStack getLastFluid() {
        return this.lastFluid;
    }

    public int getFlowThisTick() {
        final Level level = this.level();
        if (level == null) {
            return 0;
        }
        final long now = level.getGameTime();
        if (now == this.flowTick) {
            return Math.max(this.flowNow, this.flowPrevious);
        }
        return now - this.flowTick == 1 ? this.flowNow : 0;
    }
    //#endregion

    //#region // --- ACTING LIKE A PUMP --- //
    public void tick() {
        final Level level = this.level();
        if (level == null || level.isClientSide) {
            return;
        }
        if (++this.checkTimer < DRIVE_CHECK_INTERVAL) {
            return;
        }
        this.checkTimer = 0;

        Drive wanted = Drive.NONE;
        float wantedPressure = 0;
        final HosePortHandler partner = this.partner();
        if (partner != null && this.isAgainstPipe() && !this.hasOutsideDrive()) {
            final float push = partner.outsidePush();
            final float pull = partner.outsidePull();
            if (push > 0 && pull <= 0) {
                wanted = Drive.PUSH_OUT;
                wantedPressure = push;
            } else if (pull > 0 && push <= 0) {
                wanted = Drive.PULL_IN;
                wantedPressure = pull;
            }
        }

        if (wanted != this.drive) {
            if (wanted != this.pendingDrive) {
                this.pendingDrive = wanted;
                this.pendingTicks = 0;
            }
            this.pendingTicks += DRIVE_CHECK_INTERVAL;
            if (this.pendingTicks >= DRIVE_SETTLE_TICKS) {
                this.release();
                if (wanted != Drive.NONE) {
                    this.apply(wanted, wantedPressure, true);
                }
            }
            return;
        }
        this.pendingDrive = wanted;
        this.pendingTicks = 0;

        if (this.drive == Drive.NONE) {
            return;
        }
        final boolean pressureChanged = Math.abs(wantedPressure - this.drivePressure) > Math.max(1.0f, this.drivePressure * 0.1f);
        final boolean wiped = this.ownPressureOnNeighbour() < this.appliedOnNeighbour * 0.5f;
        if (pressureChanged || wiped) {
            HosePressure.remove(level, this.appliedPressure);
            this.apply(this.drive, wantedPressure, false);
        }
    }

    private void apply(final Drive wanted, final float pressure, final boolean resetNetworks) {
        final Level level = this.level();
        final boolean pull = wanted == Drive.PULL_IN;
        final float before = this.rawPressureOnNeighbour(pull);
        this.appliedPressure = HosePressure.distribute(level, this.pos(), this.owner.getPortDirection(), pull, pressure, resetNetworks);
        this.drive = wanted;
        this.drivePressure = pressure;
        this.appliedOnNeighbour = Math.max(0, this.rawPressureOnNeighbour(pull) - before);
    }

    public void release() {
        if (this.drive == Drive.NONE) {
            return;
        }
        final Level level = this.level();
        if (level != null && !level.isClientSide) {
            HosePressure.remove(level, this.appliedPressure);
        }
        this.appliedPressure = List.of();
        this.drive = Drive.NONE;
        this.drivePressure = 0;
        this.appliedOnNeighbour = 0;
    }

    private float rawPressureOnNeighbour(final boolean pull) {
        final PipeConnection connection = HosePressure.neighbourConnection(this.level(), this.pos(), this.owner.getPortDirection());
        return HosePressure.pressure(connection, !pull);
    }

    private float ownPressureOnNeighbour() {
        return this.rawPressureOnNeighbour(this.drive == Drive.PULL_IN);
    }

    float outsidePush() {
        final Level level = this.level();
        if (level == null) {
            return 0;
        }
        final PipeConnection connection = HosePressure.neighbourConnection(level, this.pos(), this.owner.getPortDirection());
        float push = HosePressure.pressure(connection, false);
        if (this.drive == Drive.PULL_IN) {
            push -= this.appliedOnNeighbour;
        }
        return push > 0.01f ? push : 0;
    }

    float outsidePull() {
        final Level level = this.level();
        if (level == null) {
            return 0;
        }
        final PipeConnection connection = HosePressure.neighbourConnection(level, this.pos(), this.owner.getPortDirection());
        float pull = HosePressure.pressure(connection, true);
        if (this.drive == Drive.PUSH_OUT) {
            pull -= this.appliedOnNeighbour;
        }
        return pull > 0.01f ? pull : 0;
    }

    private boolean hasOutsideDrive() {
        return this.outsidePush() > 0 || this.outsidePull() > 0;
    }
    //#endregion

    //#region // --- FLUID HANDLER --- //
    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(final int tank) {
        return this.drain(HOSE_THROUGHPUT, FluidAction.SIMULATE);
    }

    @Override
    public int getTankCapacity(final int tank) {
        return HOSE_THROUGHPUT;
    }

    @Override
    public boolean isFluidValid(final int tank, final FluidStack stack) {
        return true;
    }

    @Override
    public int fill(final FluidStack resource, final FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        final int[] depth = DEPTH.get();
        if (depth[0] >= MAX_DEPTH) {
            return 0;
        }
        depth[0]++;
        try {
            final HosePortHandler partner = this.partner();
            final int budget = Math.min(resource.getAmount(), this.remainingBudget());
            if (budget <= 0) {
                return 0;
            }
            if (partner == null) {
                if (this.owner.isOpenOnItsOwn()) {
                    this.markSprayed(resource);
                }
                return 0;
            }

            final int filled;
            if (partner.isAgainstPipe()) {
                filled = partner.outBuffer.fill(resource.copyWithAmount(budget), action);
            } else {
                int remaining = budget;
                int total = 0;
                for (final IFluidHandler outlet : partner.localOutlets()) {
                    if (remaining <= 0) {
                        break;
                    }
                    final int accepted = outlet.fill(resource.copyWithAmount(remaining), action);
                    total += accepted;
                    remaining -= accepted;
                }
                filled = total;
            }

            if (action.execute() && filled > 0) {
                this.recordAlongHose(resource.copyWithAmount(filled));
            }
            return filled;
        } finally {
            depth[0]--;
        }
    }

    @Override
    public FluidStack drain(final FluidStack resource, final FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return this.drainInternal(resource, resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(final int maxDrain, final FluidAction action) {
        return this.drainInternal(FluidStack.EMPTY, maxDrain, action);
    }

    private FluidStack drainInternal(final FluidStack wanted, final int maxDrain, final FluidAction action) {
        if (maxDrain <= 0) {
            return FluidStack.EMPTY;
        }
        final int[] depth = DEPTH.get();
        if (depth[0] >= MAX_DEPTH) {
            return FluidStack.EMPTY;
        }
        depth[0]++;
        try {
            if (!this.outBuffer.isEmpty()) {
                return wanted.isEmpty()
                        ? this.outBuffer.drain(maxDrain, action)
                        : this.outBuffer.drain(wanted.copyWithAmount(maxDrain), action);
            }

            final HosePortHandler partner = this.partner();
            int remaining = Math.min(maxDrain, this.remainingBudget());
            if (partner == null || remaining <= 0 || partner.isAgainstPipe()) {
                return FluidStack.EMPTY;
            }

            FluidStack result = FluidStack.EMPTY;
            for (final IFluidHandler outlet : partner.localOutlets()) {
                if (remaining <= 0) {
                    break;
                }
                final FluidStack drained;
                if (!result.isEmpty()) {
                    drained = outlet.drain(result.copyWithAmount(remaining), action);
                } else if (!wanted.isEmpty()) {
                    drained = outlet.drain(wanted.copyWithAmount(remaining), action);
                } else {
                    drained = outlet.drain(remaining, action);
                }
                if (drained.isEmpty()) {
                    continue;
                }
                if (result.isEmpty()) {
                    result = drained.copy();
                } else {
                    result.grow(drained.getAmount());
                }
                remaining -= drained.getAmount();
            }
            if (action.execute() && !result.isEmpty()) {
                this.recordAlongHose(result);
            }
            return result;
        } finally {
            depth[0]--;
        }
    }
    //#endregion

    //#region // --- SAVING --- //
    public void write(final CompoundTag tag, final HolderLookup.Provider registries) {
        if (!this.outBuffer.isEmpty()) {
            tag.put("HoseBuffer", this.outBuffer.writeToNBT(registries, new CompoundTag()));
        }
        this.openEnd.write(tag, registries);
    }

    public void read(final CompoundTag tag, final HolderLookup.Provider registries) {
        this.outBuffer.setFluid(FluidStack.EMPTY);
        if (tag.contains("HoseBuffer")) {
            this.outBuffer.readFromNBT(registries, tag.getCompound("HoseBuffer"));
        }
        this.openEnd.read(tag, registries);
    }
    //#endregion
}
