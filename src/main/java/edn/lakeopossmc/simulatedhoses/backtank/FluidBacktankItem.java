package edn.lakeopossmc.simulatedhoses.backtank;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllEnchantments;
import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.armor.AllArmorMaterials;
import com.simibubi.create.content.equipment.armor.BacktankItem;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.foundation.item.LayeredArmorItem;
import edn.lakeopossmc.simulatedhoses.HoseDataComponents;
import edn.lakeopossmc.simulatedhoses.items.FluidDeposits;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

// --- BACKTANK WITH FLUID TANKS --- //
public class FluidBacktankItem extends BacktankItem {
    public static final int CAPACITY = 4000;
    // * Each level of Create's Capacity enchantment adds this much fluid room to each tank
    public static final int CAPACITY_PER_LEVEL = 500;

    private final int tankCount;
    private final Supplier<? extends ItemLike> named;

    public FluidBacktankItem(final Holder<ArmorMaterial> material, final Properties properties, final ResourceLocation textureLoc,
                             final Supplier<BacktankBlockItem> placeable, final int tankCount, final Supplier<? extends ItemLike> named) {
        super(material, properties, textureLoc, placeable);
        this.tankCount = tankCount;
        this.named = named;
    }

    public static FluidBacktankItem copper(final Properties properties, final Supplier<BacktankBlockItem> placeable) {
        return new FluidBacktankItem(AllArmorMaterials.COPPER, properties, Create.asResource("copper_diving"), placeable, 1,
                AllItems.COPPER_BACKTANK);
    }

    public static FluidBacktankItem netherite(final Properties properties, final Supplier<BacktankBlockItem> placeable, final int tankCount) {
        return new Netherite(properties.fireResistant(), placeable, tankCount);
    }

    public int tankCount() {
        return this.tankCount;
    }

    public static int tankCount(final ItemStack stack) {
        return stack.getItem() instanceof final FluidBacktankItem item ? item.tankCount : 0;
    }

    public static Supplier<DataComponentType<SimpleFluidContent>> component(final int tank) {
        return tank == 0 ? HoseDataComponents.BACKTANK_FLUID : HoseDataComponents.BACKTANK_SECOND_FLUID;
    }

    public static FluidHandlerItemStack tank(final ItemStack stack, final int tank) {
        return new FluidHandlerItemStack(component(tank), stack, capacity(stack));
    }

    public static BacktankTanks tanks(final ItemStack stack) {
        final FluidHandlerItemStack[] tanks = new FluidHandlerItemStack[Math.max(1, tankCount(stack))];
        for (int i = 0; i < tanks.length; i++) {
            tanks[i] = tank(stack, i);
        }
        return new BacktankTanks(stack, pullMode(stack), tanks);
    }

    //#region // --- WHICH TANK SOAKERS PULL FROM --- //
    public static final int PULL_LEFT = 0;
    public static final int PULL_RIGHT = 1;
    public static final int PULL_BOTH = 2;
    public static final int PULL_MODES = 3;
    private static final int[] PULL_COLORS = {0x5384C3, 0xBE5143, 0x5FA55A};
    private static final String[] PULL_NAMES = {"left", "right", "both"};

    public static int pullMode(final ItemStack stack) {
        return tankCount(stack) > 1 ? Math.floorMod(stack.getOrDefault(HoseDataComponents.BACKTANK_FEED, PULL_BOTH), PULL_MODES) : PULL_LEFT;
    }

    public static int feedTank(final ItemStack stack) {
        final int mode = pullMode(stack);
        return mode == PULL_BOTH ? 0 : mode;
    }

    public static Component pullingFrom(final int mode) {
        return Component.translatable("simulatedhoses.tank.pulling_from").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("simulatedhoses.tank.side." + PULL_NAMES[mode])
                        .withStyle(style -> style.withColor(PULL_COLORS[mode])));
    }

    public static int tankBeside(final Player player, final InteractionHand hand) {
        final boolean leftHand = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.LEFT);
        return leftHand ? 0 : 1;
    }
    //#endregion

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

    public static FluidStack getFluid(final ItemStack stack, final int tank) {
        return stack.getOrDefault(component(tank), SimpleFluidContent.EMPTY).copy();
    }

    @Override
    public boolean overrideOtherStackedOnMe(final ItemStack stack, final ItemStack other, final Slot slot, final ClickAction action,
                                            final Player player, final SlotAccess access) {
        final BacktankTanks tanks = tanks(stack);
        return FluidDeposits.pour(stack, tanks, other, action, player, access)
                || FluidDeposits.draw(stack, tanks, tanks.drainable(), other, action, player, access);
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
        return this.named.get().asItem().getDescriptionId();
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context, final List<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (this.tankCount == 1) {
            final FluidStack fluid = getFluid(stack, 0);
            tooltip.add(fluid.isEmpty()
                    ? Component.translatable("simulatedhoses.tank.empty", capacity(stack)).withStyle(ChatFormatting.GRAY)
                    : Component.translatable("simulatedhoses.tank.contents", fluid.getHoverName(), fluid.getAmount(), capacity(stack))
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        for (int tank = 0; tank < this.tankCount; tank++) {
            final FluidStack fluid = getFluid(stack, tank);
            tooltip.add(fluid.isEmpty()
                    ? Component.translatable("simulatedhoses.tank.named_empty", tankName(tank), capacity(stack)).withStyle(ChatFormatting.GRAY)
                    : Component.translatable("simulatedhoses.tank.named_contents", tankName(tank), fluid.getHoverName(), fluid.getAmount(),
                    capacity(stack)).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(pullingFrom(pullMode(stack)));
        tooltip.add(Component.translatable("simulatedhoses.tank.feed_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static Component tankName(final int tank) {
        return Component.translatable(tank == 0 ? "simulatedhoses.tank.left" : "simulatedhoses.tank.right");
    }

    //#region // --- NETHERITE --- //
    public static class Netherite extends FluidBacktankItem implements LayeredArmorItem {
        private Netherite(final Properties properties, final Supplier<BacktankBlockItem> placeable, final int tankCount) {
            super(ArmorMaterials.NETHERITE, properties, Create.asResource("netherite_diving"), placeable, tankCount, AllItems.NETHERITE_BACKTANK);
        }

        @Override
        public String getArmorTextureLocation(final LivingEntity entity, final EquipmentSlot slot, final ItemStack stack, final int layer) {
            return String.format(Locale.ROOT, "%s:textures/models/armor/%s_layer_%d.png", this.textureLoc.getNamespace(),
                    this.textureLoc.getPath(), layer);
        }
    }
    //#endregion
}
