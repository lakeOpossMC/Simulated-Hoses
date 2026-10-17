package edn.lakeopossmc.simulatedhoses.items;

import dev.ryanhcode.sable.Sable;
import edn.lakeopossmc.simulatedhoses.HoseDataComponents;
import edn.lakeopossmc.simulatedhoses.blocks.HoseWinchBlockEntity;
import edn.lakeopossmc.simulatedhoses.hose.HoseHolderBehavior;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;


// --- HOSE COUPLER --- //
// * Click one hose block, then another, to run a hose between them
public class HoseCouplerItem extends Item {

    public HoseCouplerItem(final Properties properties) {
        super(properties);
    }

    // * Free hose holder at this spot
    public static boolean isValidHoseAttachment(final Level level, final BlockPos pos) {
        final HoseHolderBehavior holder = HoseHolderBehavior.get(level, pos);
        return holder != null && !holder.isAttached();
    }

    public static boolean canConnect(final Level level, final BlockPos first, final BlockPos second) {
        if (first.equals(second)) {
            return false;
        }
        final HoseHolderBehavior a = HoseHolderBehavior.get(level, first);
        final HoseHolderBehavior b = HoseHolderBehavior.get(level, second);
        if (a == null || b == null) {
            return false;
        }
        return !(a.blockEntity instanceof HoseWinchBlockEntity && b.blockEntity instanceof HoseWinchBlockEntity);
    }

    public static boolean inRange(final Level level, final BlockPos first, final BlockPos second) {
        final double max = HoseHolderBehavior.maxHoseLength();
        return Sable.HELPER.distanceSquaredWithSubLevels(level, first.getCenter(), second.getCenter()) < max * max;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final BlockPos clickedPos = context.getClickedPos();
        final Level level = context.getLevel();
        final ItemStack stack = context.getItemInHand();
        final Player player = context.getPlayer();

        if (player != null && player.isShiftKeyDown()) {
            stack.remove(HoseDataComponents.HOSE_FIRST_CONNECTION);
            return InteractionResult.SUCCESS;
        }

        if (!isValidHoseAttachment(level, clickedPos)) {
            return super.useOn(context);
        }

        final BlockPos first = stack.get(HoseDataComponents.HOSE_FIRST_CONNECTION);
        if (first == null) {
            stack.set(HoseDataComponents.HOSE_FIRST_CONNECTION, clickedPos);
            level.playSound(player, clickedPos, SoundEvents.COPPER_HIT, SoundSource.BLOCKS, 0.6F, 1.4F);
            return InteractionResult.SUCCESS;
        }

        stack.remove(HoseDataComponents.HOSE_FIRST_CONNECTION);

        if (!level.isClientSide) {
            if (!inRange(level, first, clickedPos)) {
                this.fail(player, "too_far");
                return InteractionResult.SUCCESS;
            }
            if (!canConnect(level, first, clickedPos)) {
                this.fail(player, "invalid");
                return InteractionResult.SUCCESS;
            }
            final boolean consume = player == null || !player.hasInfiniteMaterials();
            if (!this.attachHose(level, first, clickedPos, consume)) {
                this.fail(player, "invalid");
                return InteractionResult.SUCCESS;
            }
            if (consume) {
                stack.shrink(1);
            }
        }

        return InteractionResult.SUCCESS;
    }

    private boolean attachHose(final Level level, final BlockPos posA, final BlockPos posB, final boolean dropItem) {
        HoseHolderBehavior holderA = HoseHolderBehavior.get(level, posA);
        HoseHolderBehavior holderB = HoseHolderBehavior.get(level, posB);
        if (holderA == null || holderB == null || holderB.isAttached() || holderA.isAttached()) {
            return false;
        }

        if (holderB.blockEntity instanceof HoseWinchBlockEntity && !(holderA.blockEntity instanceof HoseWinchBlockEntity)) {
            final HoseHolderBehavior swap = holderA;
            holderA = holderB;
            holderB = swap;
        }

        if (holderA.createRope(holderB, dropItem)) {
            level.playSound(null, posA, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 0.8F, 0.9F);
            level.playSound(null, posB, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 0.8F, 0.9F);
            return true;
        }
        return false;
    }

    private void fail(@Nullable final Player player, final String reason) {
        if (player != null) {
            player.displayClientMessage(Component.translatable("item.simulatedhoses.hose_coupler." + reason).withStyle(ChatFormatting.RED), true);
        }
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return stack.has(HoseDataComponents.HOSE_FIRST_CONNECTION) || super.isFoil(stack);
    }
}
