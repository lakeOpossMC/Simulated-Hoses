package edn.lakeopossmc.simulatedhoses.backtank;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllEnchantments;
import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.armor.AllArmorMaterials;
import com.simibubi.create.content.equipment.armor.BacktankItem;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import edn.lakeopossmc.simulatedhoses.HoseDataComponents;
import edn.lakeopossmc.simulatedhoses.items.FluidDeposits;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;

import java.util.List;
import java.util.function.Supplier;

// --- COPPER BACKTANK WITH A FLUID TANK --- //
public class FluidBacktankItem extends BacktankItem {
    public static final int CAPACITY = 4000;
    // * Each level of Create's Capacity enchantment adds this much fluid room
    public static final int CAPACITY_PER_LEVEL = 500;

    public FluidBacktankItem(final Properties properties, final Supplier<BacktankBlockItem> placeable) {
        super(AllArmorMaterials.COPPER, properties, Create.asResource("copper_diving"), placeable);
    }

    public static FluidHandlerItemStack tank(final ItemStack stack) {
        return new FluidHandlerItemStack(HoseDataComponents.BACKTANK_FLUID, stack, capacity(stack));
    }

    public static int capacityFor(final int capacityLevel) {
        return CAPACITY + CAPACITY_PER_LEVEL * capacityLevel;
    }

    public static int capacity(final ItemStack stack) {
        final ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (final Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            if (entry.getKey().is(AllEnchantments.CAPACITY)) {
                return capacityFor(entry.getIntValue());
            }
        }
        return CAPACITY;
    }

    public static FluidStack getFluid(final ItemStack stack) {
        return stack.getOrDefault(HoseDataComponents.BACKTANK_FLUID, SimpleFluidContent.EMPTY).copy();
    }

    @Override
    public boolean overrideOtherStackedOnMe(final ItemStack stack, final ItemStack other, final Slot slot, final ClickAction action,
                                            final Player player, final SlotAccess access) {
        final FluidHandlerItemStack tank = tank(stack);
        return FluidDeposits.pour(stack, tank, other, action, player, access)
                || FluidDeposits.draw(stack, tank, other, action, player, access);
    }

    @Override
    public ItemStack getDefaultInstance() {
        final ItemStack stack = super.getDefaultInstance();
        try {
            stack.set(AllDataComponents.BACKTANK_AIR, BacktankUtil.maxAirWithoutEnchants());
        } catch (final RuntimeException notLoaded) {
            return stack;
        }
        return stack;
    }

    // * Keeps the plain backtank's name
    @Override
    public String getDescriptionId() {
        return AllItems.COPPER_BACKTANK.get().getDescriptionId();
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context, final List<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        final FluidStack fluid = getFluid(stack);
        tooltip.add(fluid.isEmpty()
                ? Component.translatable("simulatedhoses.tank.empty", capacity(stack)).withStyle(ChatFormatting.GRAY)
                : Component.translatable("simulatedhoses.tank.contents", fluid.getHoverName(), fluid.getAmount(), capacity(stack))
                .withStyle(ChatFormatting.GRAY));
    }
}
