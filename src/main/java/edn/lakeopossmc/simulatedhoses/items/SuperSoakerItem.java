package edn.lakeopossmc.simulatedhoses.items;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.foundation.item.CustomArmPoseItem;
import com.simibubi.create.infrastructure.config.AllConfigs;
import edn.lakeopossmc.simulatedhoses.HoseDataComponents;
import edn.lakeopossmc.simulatedhoses.blocks.PressureNozzleBlockEntity;
import edn.lakeopossmc.simulatedhoses.hose.NozzleClouds;
import edn.lakeopossmc.simulatedhoses.hose.NozzleConsumingEffects;
import edn.lakeopossmc.simulatedhoses.hose.NozzleGrowth;
import edn.lakeopossmc.simulatedhoses.hose.NozzleJet;
import edn.lakeopossmc.simulatedhoses.hose.NozzleProcessing;
import edn.lakeopossmc.simulatedhoses.hose.NozzleSprayEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// --- SUPER SOAKER --- //
// * A potato cannon with a pressure nozzle for a barrel
public class SuperSoakerItem extends Item implements CustomArmPoseItem {
    public static final int CAPACITY = 8000;
    public static final int DURABILITY = 100;
    // * Half a full-speed pressure nozzle
    public static final int FLOW = Math.round(PressureNozzleBlockEntity.FULL_FLOW / 2.0F);
    public static final float FRACTION = FLOW / PressureNozzleBlockEntity.FULL_FLOW;
    // * What actually leaves the tank each tick
    public static final int DRAIN = 25;

    private static final double PUSH = 0.75;
    private static final int TICKS_PER_USE = 4;
    private static final Vec3 BARREL = new Vec3(0.75, -0.15, 1.5);
    private static final Vec3 AIM_CORRECTION = new Vec3(-0.05, 0.0, 0.0);
    private static final int USE_DURATION = 72000;

    // * Server side, per shooter and hand
    private static final Map<LivingEntity, Progress[]> PROGRESS = new WeakHashMap<>();

    private static final class Progress {
        private final NozzleProcessing.BlockProgress spout = new NozzleProcessing.BlockProgress();
        private final NozzleClouds.Progress clouds = new NozzleClouds.Progress();
        private final NozzleConsumingEffects.Progress consuming = new NozzleConsumingEffects.Progress();
        private final NozzleGrowth.Progress growth = new NozzleGrowth.Progress();
    }

    public SuperSoakerItem(final Properties properties) {
        super(properties);
    }

    //#region // --- TANK --- //
    public static FluidHandlerItemStack tank(final ItemStack stack) {
        return new FluidHandlerItemStack(HoseDataComponents.SOAKER_FLUID, stack, CAPACITY);
    }

    public static FluidStack getFluid(final ItemStack stack) {
        return stack.getOrDefault(HoseDataComponents.SOAKER_FLUID, SimpleFluidContent.EMPTY).copy();
    }

    // * Right-clicking a fluid item onto it in the inventory pours it in
    @Override
    public boolean overrideOtherStackedOnMe(final ItemStack stack, final ItemStack other, final Slot slot, final ClickAction action,
                                            final Player player, final SlotAccess access) {
        return FluidDeposits.pour(stack, tank(stack), other, action, player, access);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context, final List<Component> tooltip, final TooltipFlag flag) {
        final FluidStack fluid = getFluid(stack);
        if (fluid.isEmpty()) {
            tooltip.add(Component.translatable("item.simulatedhoses.super_soaker.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.simulatedhoses.super_soaker.contents",
                fluid.getHoverName(), fluid.getAmount(), CAPACITY).withStyle(ChatFormatting.GRAY));
    }
    //#endregion

    //#region // --- SPRAYING --- //
    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (getFluid(stack).isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(final Level level, final LivingEntity shooter, final ItemStack stack, final int remainingUseDuration) {
        boolean anyFluid = false;
        for (final InteractionHand hand : InteractionHand.values()) {
            final ItemStack held = shooter.getItemInHand(hand);
            anyFluid |= held.getItem() instanceof SuperSoakerItem && !getFluid(held).isEmpty();
        }
        if (!anyFluid) {
            shooter.stopUsingItem();
            return;
        }
        if (!(level instanceof final ServerLevel serverLevel)) {
            return;
        }
        final int used = this.getUseDuration(stack, shooter) - remainingUseDuration;
        // * Creative players never run dry or use anything up
        final boolean creative = shooter instanceof final Player player && player.getAbilities().instabuild;
        for (final InteractionHand hand : InteractionHand.values()) {
            final ItemStack held = shooter.getItemInHand(hand);
            if (!(held.getItem() instanceof SuperSoakerItem)) {
                continue;
            }
            final FluidStack drained = tank(held).drain(DRAIN, creative ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty()) {
                continue;
            }
            spray(serverLevel, shooter, hand, drained);
            if (!creative && used % TICKS_PER_USE == 0 && !BacktankUtil.canAbsorbDamage(shooter, maxUses())) {
                held.hurtAndBreak(1, shooter, LivingEntity.getSlotForHand(hand));
            }
        }
    }

    // * Everything a pressure nozzle's jet does where it lands
    private static void spray(final ServerLevel level, final LivingEntity shooter, final InteractionHand hand, final FluidStack drained) {
        final NozzleJet.Impact impact = NozzleJet.trace(level, aim(shooter, hand), FRACTION, shooter);
        if (impact == null) {
            return;
        }
        final FluidStack fluid = drained.copyWithAmount(1);
        final int flow = drained.getAmount();
        final Player player = shooter instanceof final Player p ? p : null;
        final Progress progress = PROGRESS.computeIfAbsent(shooter, s -> new Progress[]{new Progress(), new Progress()})[hand.ordinal()];
        NozzleSprayEffects.apply(level, impact, fluid);
        NozzleSprayEffects.push(impact, PUSH);
        NozzleProcessing.apply(level, impact, fluid, flow, progress.spout);
        NozzleClouds.apply(level, impact, fluid, flow, player, progress.clouds);
        NozzleConsumingEffects.apply(level, impact, fluid, flow, progress.consuming);
        NozzleGrowth.apply(level, impact, fluid, flow, player, progress.growth);
    }

    // * From the end of the barrel, converging on the crosshair
    public static NozzleJet.Aim aim(final LivingEntity shooter, final InteractionHand hand) {
        final boolean mainHand = hand == InteractionHand.MAIN_HAND;
        final Vec3 eye = shooter.getEyePosition();
        final Vec3 barrel = barrelOffset(shooter, mainHand, BARREL);
        final Vec3 correction = barrelOffset(shooter, mainHand, AIM_CORRECTION);
        return NozzleJet.aimAlong(eye.add(barrel), shooter.getLookAngle().add(correction));
    }

    private static Vec3 barrelOffset(final LivingEntity shooter, final boolean mainHand, final Vec3 rightHandForward) {
        final float yaw = (float) (shooter.getYRot() / -180.0 * Math.PI);
        final float pitch = (float) (shooter.getXRot() / -180.0 * Math.PI);
        final int flip = mainHand == (shooter.getMainArm() == HumanoidArm.RIGHT) ? -1 : 1;
        return new Vec3(flip * rightHandForward.x, rightHandForward.y, rightHandForward.z).xRot(pitch).yRot(yaw);
    }

    @Override
    public int getUseDuration(final ItemStack stack, final LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(final ItemStack stack) {
        return UseAnim.NONE;
    }
    //#endregion

    //#region // --- LIKE THE POTATO CANNON --- //
    private static int maxUses() {
        return AllConfigs.server().equipment.maxPotatoCannonShots.get();
    }

    @Override
    public boolean isBarVisible(final ItemStack stack) {
        return BacktankUtil.isBarVisible(stack, maxUses());
    }

    @Override
    public int getBarWidth(final ItemStack stack) {
        return BacktankUtil.getBarWidth(stack, maxUses());
    }

    @Override
    public int getBarColor(final ItemStack stack) {
        return BacktankUtil.getBarColor(stack, maxUses());
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    @Override
    public boolean canAttackBlock(final BlockState state, final Level level, final BlockPos pos, final Player player) {
        return false;
    }

    @Override
    public boolean onEntitySwing(final ItemStack stack, final LivingEntity entity, final InteractionHand hand) {
        return true;
    }

    @Override
    public boolean shouldCauseReequipAnimation(final ItemStack oldStack, final ItemStack newStack, final boolean slotChanged) {
        return slotChanged || newStack.getItem() != oldStack.getItem();
    }

    @Override
    @Nullable
    public ArmPose getArmPose(final ItemStack stack, final AbstractClientPlayer player, final InteractionHand hand) {
        return player.swinging ? null : ArmPose.CROSSBOW_HOLD;
    }
    //#endregion
}
