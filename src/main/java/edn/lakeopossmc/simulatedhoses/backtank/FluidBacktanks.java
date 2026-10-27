package edn.lakeopossmc.simulatedhoses.backtank;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.content.equipment.armor.BacktankBlock;
import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import edn.lakeopossmc.simulatedhoses.HoseBlocks;
import edn.lakeopossmc.simulatedhoses.HoseItems;
import edn.lakeopossmc.simulatedhoses.items.SuperSoakerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

// --- FITTING AND FEEDING FLUID BACKTANKS --- //
public final class FluidBacktanks {
    private static final int FEED_PER_TICK = 100;
    private static final String[] KEPT_TAGS = {"Air", "Timer", "CapacityEnchantment", "CustomName", "Components"};

    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        final Player player = event.getEntity();
        final Level level = event.getLevel();
        final BlockPos pos = event.getPos();
        final ItemStack held = event.getItemStack();
        final BlockState state = level.getBlockState(pos);
        if (player.isShiftKeyDown() || !held.is(AllBlocks.FLUID_TANK.asItem()) || !state.is(AllBlocks.COPPER_BACKTANK.get())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof final BacktankBlockEntity old)) {
            return;
        }

        final CompoundTag saved = old.saveWithoutMetadata(level.registryAccess());
        final CompoundTag kept = new CompoundTag();
        for (final String key : KEPT_TAGS) {
            if (saved.contains(key)) {
                kept.put(key, saved.get(key));
            }
        }

        final BlockState fitted = HoseBlocks.COPPER_FLUID_BACKTANK.get().defaultBlockState()
                .setValue(BacktankBlock.HORIZONTAL_FACING, state.getValue(BacktankBlock.HORIZONTAL_FACING))
                .setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED));
        level.setBlockAndUpdate(pos, fitted);
        if (level.getBlockEntity(pos) instanceof final FluidBacktankBlockEntity be) {
            be.loadWithComponents(kept, level.registryAccess());
            be.setChanged();
            be.sendData();
        }
        level.playSound(null, pos, fitted.getSoundType(level, pos, player).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
    }

    public static void fillCreativeBacktanks(final BuildCreativeModeTabContentsEvent event) {
        final ItemStack empty = new ItemStack(HoseItems.COPPER_FLUID_BACKTANK.get());
        final boolean inTab = event.getParentEntries().contains(empty);
        final boolean inSearch = event.getSearchEntries().contains(empty);
        if (!inTab && !inSearch) {
            return;
        }
        final CreativeModeTab.TabVisibility visibility = inTab && inSearch ? CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
                : inTab ? CreativeModeTab.TabVisibility.PARENT_TAB_ONLY : CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY;
        final ItemStack full = empty.copy();
        full.set(AllDataComponents.BACKTANK_AIR, BacktankUtil.maxAirWithoutEnchants());
        event.insertAfter(empty, full, visibility);
        event.remove(empty, visibility);
    }

    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        final Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        final ItemStack backtank = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(backtank.getItem() instanceof FluidBacktankItem)) {
            return;
        }
        for (final InteractionHand hand : InteractionHand.values()) {
            final ItemStack soaker = player.getItemInHand(hand);
            if (!(soaker.getItem() instanceof SuperSoakerItem)) {
                continue;
            }
            final FluidStack stored = FluidBacktankItem.getFluid(backtank);
            if (stored.isEmpty()) {
                return;
            }
            final IFluidHandlerItem soakerTank = SuperSoakerItem.tank(soaker);
            final int room = soakerTank.fill(stored.copyWithAmount(Math.min(FEED_PER_TICK, stored.getAmount())), IFluidHandler.FluidAction.SIMULATE);
            if (room > 0) {
                soakerTank.fill(FluidBacktankItem.tank(backtank).drain(room, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    private FluidBacktanks() {
    }
}
