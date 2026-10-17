package edn.lakeopossmc.simulatedhoses.hose;

import com.simibubi.create.content.fluids.OpenEndedPipe;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

// --- AN OPEN HOSE END --- //
public final class HoseOpenEnd {
    private static final double SAMPLE_DEPTH = 1.0 / 16.0;
    private static final double SURFACE_SLACK = 1.0 / 16.0;
    // * How far below the port a spill looks for another ship to land on before it gives up and spills into the world
    private static final double SPILL_FALL_REACH = 16.0;
    private static final double SPILL_FALL_STEP = 0.25;

    private final HoseEndpoint owner;
    private final HosePortHandler port;
    private final Handler handler = new Handler();

    private final PipeSlot intake = new PipeSlot(false);
    private final PipeSlot spill = new PipeSlot(true);

    HoseOpenEnd(final HoseEndpoint owner, final HosePortHandler port) {
        this.owner = owner;
        this.port = port;
    }

    @Nullable
    private Level level() {
        return this.owner.asBlockEntity().getLevel();
    }

    private BlockPos pos() {
        return this.owner.asBlockEntity().getBlockPos();
    }

    IFluidHandler handler() {
        return this.handler;
    }

    //#region // --- WHERE THE PORT POINTS --- //
    private record Sample(BlockPos cell, Direction direction, boolean submerged) {
    }

    // * Where the port draws from or spills to
    private Sample sample(final Level level, final boolean toParent) {
        final BlockPos pos = this.pos();
        final Direction port = this.owner.getPortDirection();
        final double depth = 0.5 + SAMPLE_DEPTH;
        final Vector3d local = new Vector3d(pos.getX() + 0.5 + port.getStepX() * depth,
                pos.getY() + 0.5 + port.getStepY() * depth,
                pos.getZ() + 0.5 + port.getStepZ() * depth);

        final SubLevelAccess ownShip = Sable.HELPER.getContaining(level, new ChunkPos(pos));
        final boolean onShip = ownShip != null;
        if (!onShip) {
            if (toParent) {
                final Sample other = this.otherShipSample(level, pos, local, null);
                if (other != null) {
                    return other;
                }
            } else {
                final Sample other = this.otherShipFluid(level, local, null);
                if (other != null) {
                    return other;
                }
            }
            return sampleAt(level, local, port);
        }

        if (toParent) {
            if (!isAlone(level, pos)) {
                return sampleAt(level, local, port);
            }
            final Sample other = this.otherShipSample(level, pos, local, ownShip);
            if (other != null) {
                return other;
            }
            return this.worldSample(level, pos, local);
        }

        if (!level.getFluidState(pos.relative(port)).isEmpty()) {
            return sampleAt(level, local, port);
        }
        final Vector3d point = Sable.HELPER.projectOutOfSubLevel(level, local, new Vector3d());
        final Sample other = this.otherShipFluid(level, point, ownShip);
        if (other != null) {
            return other;
        }
        return this.worldSample(level, pos, local);
    }

    private Sample worldSample(final Level level, final BlockPos pos, final Vector3d local) {
        final Vector3d center = Sable.HELPER.projectOutOfSubLevel(level, new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), new Vector3d());
        final Vector3d point = Sable.HELPER.projectOutOfSubLevel(level, local, new Vector3d());
        return sampleAt(level, point, Direction.getNearest(point.x - center.x, point.y - center.y, point.z - center.z));
    }

    private static Sample sampleAt(final Level level, final Vector3d point, final Direction direction) {
        final BlockPos cell = BlockPos.containing(point.x, point.y, point.z);
        final FluidState fluid = level.isLoaded(cell) ? level.getFluidState(cell) : null;
        // * Touching or below the surface
        final boolean submerged = fluid != null && !fluid.isEmpty()
                && point.y <= cell.getY() + fluid.getHeight(level, cell) + SURFACE_SLACK;
        return new Sample(cell, direction, submerged);
    }

    // * Nothing else on this block's ship
    private static boolean isAlone(final Level level, final BlockPos pos) {
        for (final BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (!near.equals(pos) && !level.getBlockState(near).isAir()) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private Sample otherShipFluid(final Level level, final Vector3d point, @Nullable final SubLevelAccess ownShip) {
        final BoundingBox3d probe = new BoundingBox3d(point.x - 0.01, point.y - 0.01, point.z - 0.01, point.x + 0.01, point.y + 0.01, point.z + 0.01);
        for (final SubLevelAccess ship : Sable.HELPER.getAllIntersecting(level, probe)) {
            if (ship == ownShip) {
                continue;
            }
            final Vector3d shipPoint = ship.logicalPose().transformPositionInverse(point, new Vector3d());
            final BlockPos cell = BlockPos.containing(shipPoint.x, shipPoint.y, shipPoint.z);
            if (!level.isLoaded(cell) || Sable.HELPER.getContaining(level, cell) != ship || level.getFluidState(cell).isEmpty()) {
                continue;
            }
            final Sample sample = shipSample(level, ship, cell, shipPoint, new Vector3d(0.0, -1.0, 0.0));
            if (sample.submerged()) {
                return sample;
            }
        }
        return null;
    }

    // * Another ship to spill onto
    @Nullable
    private Sample otherShipSample(final Level level, final BlockPos pos, final Vector3d local, @Nullable final SubLevelAccess ownShip) {
        final Vector3d start = Sable.HELPER.projectOutOfSubLevel(level, local, new Vector3d());
        final Vector3d center = Sable.HELPER.projectOutOfSubLevel(level, new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), new Vector3d());
        final Vector3d outward = start.sub(center, new Vector3d());

        final Vector3d point = new Vector3d(start);
        final Vector3d previous = new Vector3d(start);
        for (double fallen = 0.0; fallen <= SPILL_FALL_REACH; fallen += SPILL_FALL_STEP) {
            point.set(start.x, start.y - fallen, start.z);
            final BoundingBox3d probe = new BoundingBox3d(point.x - 0.01, point.y - 0.01, point.z - 0.01, point.x + 0.01, point.y + 0.01, point.z + 0.01);
            for (final SubLevelAccess ship : Sable.HELPER.getAllIntersecting(level, probe)) {
                if (ship == ownShip) {
                    continue;
                }
                final Vector3d shipPoint = ship.logicalPose().transformPositionInverse(point, new Vector3d());
                final BlockPos cell = BlockPos.containing(shipPoint.x, shipPoint.y, shipPoint.z);
                if (!level.isLoaded(cell) || Sable.HELPER.getContaining(level, cell) != ship) {
                    continue;
                }
                final boolean open = holdsFluid(level.getBlockState(cell));
                if (fallen == 0.0 && open) {
                    return shipSample(level, ship, cell, shipPoint, outward);
                }
                if (!open && fallen > 0.0) {
                    final Vector3d abovePoint = ship.logicalPose().transformPositionInverse(previous, new Vector3d());
                    final BlockPos above = BlockPos.containing(abovePoint.x, abovePoint.y, abovePoint.z);
                    if (!above.equals(cell) && level.isLoaded(above) && Sable.HELPER.getContaining(level, above) == ship
                            && holdsFluid(level.getBlockState(above))) {
                        return shipSample(level, ship, above, abovePoint, new Vector3d(0.0, -1.0, 0.0));
                    }
                }
            }
            final BlockPos worldCell = BlockPos.containing(point.x, point.y, point.z);
            if (fallen > 0.0 && (!level.isLoaded(worldCell) || !holdsFluid(level.getBlockState(worldCell)))) {
                return null;
            }
            previous.set(point);
        }
        return null;
    }

    private static Sample shipSample(final Level level, final SubLevelAccess ship, final BlockPos cell, final Vector3d shipPoint, final Vector3d worldDirection) {
        final Vector3d shipDirection = ship.logicalPose().transformNormalInverse(worldDirection, new Vector3d());
        final Direction direction = Direction.getNearest(shipDirection.x, shipDirection.y, shipDirection.z);
        final FluidState fluid = level.getFluidState(cell);
        final boolean submerged = !fluid.isEmpty() && shipPoint.y <= cell.getY() + fluid.getHeight(level, cell) + SURFACE_SLACK;
        return new Sample(cell, direction, submerged);
    }

    private static boolean holdsFluid(final BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty() || state.getBlock() instanceof LiquidBlockContainer;
    }

    private static FluidStack leftover(@Nullable final IFluidHandler pipe) {
        return pipe instanceof final FluidTank tank ? tank.getFluid().copy() : FluidStack.EMPTY;
    }

    private final class PipeSlot {
        private final boolean spilling;
        @Nullable
        private OpenEndedPipe pipe;
        @Nullable
        private BlockPos target;
        @Nullable
        private Direction direction;
        private FluidStack savedLeftover = FluidStack.EMPTY;
        private long sampleTick = -1;
        @Nullable
        private Sample sample;

        private PipeSlot(final boolean spilling) {
            this.spilling = spilling;
        }

        private Sample sample(final Level level) {
            final long now = level.getGameTime();
            if (this.sample == null || now != this.sampleTick) {
                this.sample = HoseOpenEnd.this.sample(level, this.spilling && HoseOpenEnd.this.owner.spillsToParentLevel());
                this.sampleTick = now;
            }
            return this.sample;
        }

        private IFluidHandler pipe(final Level level) {
            final Sample sample = this.sample(level);
            if (this.pipe == null || !sample.cell.equals(this.target) || sample.direction != this.direction) {
                final FluidStack carried = this.held();
                this.savedLeftover = FluidStack.EMPTY;
                this.pipe = new OpenEndedPipe(new BlockFace(sample.cell.relative(sample.direction.getOpposite()), sample.direction));
                this.target = sample.cell;
                this.direction = sample.direction;
                if (!carried.isEmpty() && this.pipe.provideHandler().getCapability() instanceof final FluidTank tank) {
                    tank.setFluid(carried);
                }
            }
            this.pipe.manageSource(level, HoseOpenEnd.this.owner.asBlockEntity());
            return this.pipe.provideHandler().getCapability();
        }

        private FluidStack held() {
            return this.pipe != null ? leftover(this.pipe.provideHandler().getCapability()) : this.savedLeftover;
        }
    }
    //#endregion

    private final class Handler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(final int tank) {
            return HoseOpenEnd.this.intake.held();
        }

        @Override
        public int getTankCapacity(final int tank) {
            return 1000;
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
            if (!HoseOpenEnd.this.owner.placesSpilledFluid()) {
                HoseOpenEnd.this.port.markSprayed(resource);
                return 0;
            }
            final Level level = HoseOpenEnd.this.level();
            if (level == null || level.isClientSide) {
                return 0;
            }
            HoseOpenEnd.this.port.markSprayed(resource);
            return HoseOpenEnd.this.spill.pipe(level).fill(resource, action);
        }

        @Override
        public FluidStack drain(final FluidStack resource, final FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            return this.drain(resource, resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(final int maxDrain, final FluidAction action) {
            return this.drain(FluidStack.EMPTY, maxDrain, action);
        }

        private FluidStack drain(final FluidStack wanted, final int maxDrain, final FluidAction action) {
            final Level level = HoseOpenEnd.this.level();
            if (level == null || level.isClientSide || maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            final IFluidHandler pipe = HoseOpenEnd.this.intake.pipe(level);
            int amount = maxDrain;
            if (!HoseOpenEnd.this.intake.sample(level).submerged) {
                amount = Math.min(amount, leftover(pipe).getAmount());
                if (amount <= 0) {
                    return FluidStack.EMPTY;
                }
            }
            return wanted.isEmpty() ? pipe.drain(amount, action) : pipe.drain(wanted.copyWithAmount(amount), action);
        }
    }

    //#region // --- SAVING --- //
    void write(final CompoundTag tag, final HolderLookup.Provider registries) {
        final FluidStack held = this.intake.held();
        if (!held.isEmpty()) {
            tag.put("OpenEndFluid", held.save(registries));
        }
        final FluidStack spilling = this.spill.held();
        if (!spilling.isEmpty()) {
            tag.put("SpillFluid", spilling.save(registries));
        }
    }

    void read(final CompoundTag tag, final HolderLookup.Provider registries) {
        this.intake.savedLeftover = FluidStack.parseOptional(registries, tag.getCompound("OpenEndFluid"));
        this.intake.pipe = null;
        this.spill.savedLeftover = FluidStack.parseOptional(registries, tag.getCompound("SpillFluid"));
        this.spill.pipe = null;
    }
    //#endregion
}
