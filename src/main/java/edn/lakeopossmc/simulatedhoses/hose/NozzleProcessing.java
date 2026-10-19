package edn.lakeopossmc.simulatedhoses.hose;

import com.simibubi.create.api.behaviour.spouting.BlockSpoutingBehaviour;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// --- CREATE PROCESSING UNDER A PRESSURE NOZZLE'S JET --- //
public final class NozzleProcessing {
    private static final double ITEM_REACH = 0.8;
    private static final int MAX_BLOCK_BANK = 2000;
    private static final int BLOCK_FILL_TICKS = 20;

    public static final class BlockProgress {
        @Nullable
        private BlockPos target;
        private int amount;
        private long lastFill = -1;
        private final Map<TransportedItemStack, Integer> filling = new WeakHashMap<>();
    }

    public static void apply(final ServerLevel level, final NozzleJet.Impact impact, final FluidStack fluid, final int flow, final BlockProgress progress) {
        if (fluid.isEmpty() || flow <= 0) {
            return;
        }
        final boolean lava = fluid.is(FluidTags.LAVA);
        final FanProcessingType fan = fanFor(fluid);
        processLooseItems(level, impact, fan, lava);
        if (impact.block() != null) {
            processHeldItems(level, impact.block().getBlockPos(), fluid, flow, fan, progress);
            if (!fillBasin(level, impact.block().getBlockPos(), fluid, flow)) {
                spoutBlock(level, impact.block().getBlockPos(), fluid, flow, progress);
            }
        } else {
            progress.target = null;
        }
    }

    @Nullable
    private static FanProcessingType fanFor(final FluidStack fluid) {
        if (fluid.is(FluidTags.WATER)) {
            return AllFanProcessingTypes.SPLASHING;
        }
        return fluid.is(FluidTags.LAVA) ? AllFanProcessingTypes.BLASTING : null;
    }

    public static void spawnParticles(final Level level, final NozzleJet.Impact impact, final FluidStack fluid) {
        final FanProcessingType fan = fanFor(fluid);
        if (fan == null) {
            return;
        }
        final AABB area = new AABB(impact.point(), impact.point()).inflate(ITEM_REACH);
        for (final ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, ItemEntity::isAlive)) {
            fan.spawnProcessingParticles(level, item.position());
        }
        if (impact.block() == null) {
            return;
        }
        final TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(level, impact.block().getBlockPos(), TransportedItemStackHandlerBehaviour.TYPE);
        if (handler != null) {
            handler.handleProcessingOnAllItems(transported -> {
                fan.spawnProcessingParticles(level, handler.getWorldPositionOf(transported));
                return TransportedResult.doNothing();
            });
        }
    }

    //#region // --- LOOSE ITEMS --- //
    private static void processLooseItems(final ServerLevel level, final NozzleJet.Impact impact, @Nullable final FanProcessingType fan, final boolean lava) {
        final AABB area = new AABB(impact.point(), impact.point()).inflate(ITEM_REACH);
        for (final ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, ItemEntity::isAlive)) {
            if (lava) {
                item.clearFire();
            }
            if (fan != null && FanProcessing.canProcess(item, fan)) {
                FanProcessing.applyProcessing(item, fan);
            }
        }
    }
    //#endregion

    //#region // --- BELTS AND DEPOTS --- //
    private static void processHeldItems(final ServerLevel level, final BlockPos pos, final FluidStack fluid, final int flow,
                                         @Nullable final FanProcessingType fan, final BlockProgress progress) {
        final TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) {
            return;
        }
        handler.handleProcessingOnAllItems(transported -> {
            final TransportedResult filled = fill(level, transported, fluid, flow, progress);
            if (filled != null) {
                return filled;
            }
            return fan != null ? FanProcessing.applyProcessing(transported, level, fan) : TransportedResult.doNothing();
        });
    }

    @Nullable
    private static TransportedResult fill(final ServerLevel level, final TransportedItemStack transported, final FluidStack fluid,
                                          final int amount, final BlockProgress progress) {
        if (!FillingBySpout.canItemBeFilled(level, transported.stack)) {
            return null;
        }
        final int required = FillingBySpout.getRequiredAmountForItem(level, transported.stack, fluid.copyWithAmount(Integer.MAX_VALUE));
        if (required <= 0) {
            return null;
        }
        final int banked = progress.filling.getOrDefault(transported, 0) + amount;
        if (banked < required) {
            progress.filling.put(transported, banked);
            return TransportedResult.doNothing();
        }
        progress.filling.put(transported, Math.min(banked - required, required));

        final ItemStack out = FillingBySpout.fillItem(level, required, transported.stack, fluid.copyWithAmount(required));
        if (out.isEmpty()) {
            return TransportedResult.doNothing();
        }
        transported.clearFanProcessingData();
        final TransportedItemStack result = transported.copy();
        result.stack = out;
        final TransportedItemStack held = transported.stack.isEmpty() ? null : transported.copy();
        final List<TransportedItemStack> outputs = new ArrayList<>();
        outputs.add(result);
        return TransportedResult.convertToAndLeaveHeld(outputs, held);
    }
    //#endregion

    //#region // --- BLOCKS --- //
    private static boolean fillBasin(final ServerLevel level, final BlockPos pos, final FluidStack fluid, final int flow) {
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity)) {
            return false;
        }
        final IFluidHandler basin = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (basin != null) {
            basin.fill(fluid.copyWithAmount(flow), IFluidHandler.FluidAction.EXECUTE);
        }
        return true;
    }

    private static void spoutBlock(final ServerLevel level, final BlockPos pos, final FluidStack fluid, final int flow, final BlockProgress progress) {
        final BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.FARMLAND) || state.is(BlockTags.CONVERTABLE_TO_MUD)) {
            progress.target = null;
            return;
        }
        final BlockSpoutingBehaviour behaviour = BlockSpoutingBehaviour.get(level, pos);
        if (behaviour == null) {
            progress.target = null;
            return;
        }
        if (!pos.equals(progress.target)) {
            progress.target = pos.immutable();
            progress.amount = 0;
        }
        progress.amount = Math.min(progress.amount + flow, MAX_BLOCK_BANK);
        if (progress.lastFill >= 0 && level.getGameTime() - progress.lastFill < BLOCK_FILL_TICKS) {
            return;
        }
        try {
            if (behaviour.fillBlock(level, pos, null, fluid.copyWithAmount(progress.amount), true) <= 0) {
                return;
            }
            progress.amount -= behaviour.fillBlock(level, pos, null, fluid.copyWithAmount(progress.amount), false);
            progress.lastFill = level.getGameTime();
        } catch (final RuntimeException ignored) {
            progress.target = null;
        }
    }
    //#endregion

    private NozzleProcessing() {
    }
}
