package edn.lakeopossmc.simulatedhoses.client;

import dev.ryanhcode.sable.Sable;
import edn.lakeopossmc.simulatedhoses.HoseDataComponents;
import edn.lakeopossmc.simulatedhoses.HoseItems;
import edn.lakeopossmc.simulatedhoses.hose.HoseHolderBehavior;
import edn.lakeopossmc.simulatedhoses.items.HoseCouplerItem;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

// --- PREVIEW WHILE PLACING A HOSE --- //
public final class HoseCouplerClientHandler {
    private static final int VALID = 0x8BE04A;
    private static final int OUT_OF_RANGE = 0xE0943A;
    private static final int INVALID = 0xE0463A;

    public static void tick() {
        final Minecraft mc = Minecraft.getInstance();
        final Player player = mc.player;
        final Level level = mc.level;
        if (player == null || level == null || mc.screen != null) {
            return;
        }

        for (final InteractionHand hand : InteractionHand.values()) {
            final ItemStack held = player.getItemInHand(hand);
            if (!held.is(HoseItems.HOSE_COUPLER.get())) {
                continue;
            }
            final BlockPos first = held.get(HoseDataComponents.HOSE_FIRST_CONNECTION);
            if (first == null) {
                continue;
            }
            if (!(mc.hitResult instanceof final BlockHitResult hit)) {
                return;
            }
            preview(level, first, hit, HoseHolderBehavior.maxHoseLength(),
                    pos -> HoseCouplerItem.isValidHoseAttachment(level, pos) && HoseCouplerItem.canConnect(level, first, pos));
            return;
        }
    }

    // * Outlines both ends and dots a line between them: green when it can connect, orange out of range, red otherwise
    private static void preview(final Level level, final BlockPos first, final BlockHitResult hit, final double maxLength, final Predicate<BlockPos> canConnect) {
        final BlockPos hitPos = hit.getBlockPos();
        final Vec3 firstPoint = first.getCenter();
        final boolean inRange = Sable.HELPER.distanceSquaredWithSubLevels(level, firstPoint, hit.getLocation()) < maxLength * maxLength;
        final boolean valid = inRange && canConnect.test(hitPos);

        final Color color = new Color(valid ? VALID : inRange ? OUT_OF_RANGE : INVALID);
        final Vec3 target = valid ? hitPos.getCenter() : hit.getLocation();

        Outliner.getInstance().chaseAABB("FirstHoseAttachmentPoint", new AABB(firstPoint, firstPoint))
                .colored(color)
                .lineWidth(1 / 3f)
                .disableLineNormals();

        final Vec3 globalFirst = Sable.HELPER.projectOutOfSubLevel(level, firstPoint);
        Vec3 globalTarget = Sable.HELPER.projectOutOfSubLevel(level, target);

        if (valid) {
            Outliner.getInstance().chaseAABB("SecondHoseAttachmentPoint", new AABB(target, target))
                    .colored(color)
                    .lineWidth(1 / 3f)
                    .disableLineNormals();
        } else if (!inRange) {
            globalTarget = globalTarget.subtract(globalFirst).normalize().scale(maxLength - 0.5).add(globalFirst);
            Outliner.getInstance().chaseAABB("SecondHoseAttachmentPoint", new AABB(globalTarget, globalTarget))
                    .colored(color)
                    .lineWidth(1 / 3f)
                    .disableLineNormals();
        }

        final DustParticleOptions dust = new DustParticleOptions(color.asVectorF(), 1);
        for (int i = 0; i < 4; i++) {
            final Vec3 point = globalFirst.lerp(globalTarget, level.random.nextFloat());
            level.addParticle(dust, point.x, point.y, point.z, 0, 0, 0);
        }
    }

    private HoseCouplerClientHandler() {
    }
}
