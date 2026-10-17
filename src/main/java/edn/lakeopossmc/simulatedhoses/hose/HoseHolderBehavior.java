package edn.lakeopossmc.simulatedhoses.hose;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import edn.lakeopossmc.simulatedhoses.HoseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

// --- SIMULATED ROPE HOLDER THAT HOLDS HOSES --- //
public class HoseHolderBehavior extends RopeStrandHolderBehavior {

    public HoseHolderBehavior(final SmartBlockEntity be) {
        super(be);
    }

    @Nullable
    public static HoseHolderBehavior get(final Level level, final BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof final SmartBlockEntity smart
                && smart.getBehaviour(TYPE) instanceof final HoseHolderBehavior holder) {
            return holder;
        }
        return null;
    }

    @Override
    public void destroyRope(@Nullable final ServerPlayer player, @Nullable final Vec3 ropeDropPos, final boolean returnItem) {
        final ServerRopeStrand strand = this.getOwnedStrand();
        final Level level = this.blockEntity.getLevel();

        if (!this.ownsRope() || strand == null || level == null) {
            super.destroyRope(player, ropeDropPos, false);
            return;
        }

        final List<Vector3d> points = strand.getPoints();
        final Vector3d middle = new Vector3d(points.get(points.size() / 2));

        super.destroyRope(player, ropeDropPos, false);

        if (level instanceof final ServerLevel serverLevel) {
            final ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(HoseItems.HOSE_COUPLER.get()));
            for (final Vector3d point : points) {
                serverLevel.sendParticles(particle, point.x, point.y, point.z, 4, 0.1, 0.1, 0.1, 0.05);
            }
            serverLevel.playSound(null, middle.x, middle.y, middle.z, SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
        }

        if (!returnItem) {
            return;
        }

        final ItemStack stack = new ItemStack(HoseItems.HOSE_COUPLER.get());
        if (player != null) {
            if (!player.hasInfiniteMaterials() || !player.getInventory().contains(stack)) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        } else {
            if (ropeDropPos != null) {
                middle.set(ropeDropPos.x, ropeDropPos.y, ropeDropPos.z);
            }
            level.addFreshEntity(new ItemEntity(level, middle.x, middle.y, middle.z, stack));
        }
    }

    @Nullable
    public BlockEntity getPartner() {
        final Level level = this.blockEntity.getLevel();
        if (level == null || level.isClientSide) {
            return null;
        }

        final ServerRopeStrand strand = this.getAttachedStrand();
        if (strand == null) {
            return null;
        }

        final RopeAttachment attachment = strand.getAttachment(this.ownsRope() ? RopeAttachmentPoint.END : RopeAttachmentPoint.START);
        if (attachment == null || !level.isLoaded(attachment.blockAttachment())) {
            return null;
        }

        return level.getBlockEntity(attachment.blockAttachment());
    }

    public static double maxHoseLength() {
        try {
            return dev.simulated_team.simulated.service.SimConfigService.INSTANCE.server().blocks.maxRopeRange.get();
        } catch (final Throwable ignored) {
            // * Server settings not loaded yet (main menu), use Simulated's default
            return 40.0;
        }
    }

    public static double winchStretchAllowed() {
        try {
            return dev.simulated_team.simulated.service.SimConfigService.INSTANCE.server().blocks.maxRopeStretchAllowed.get();
        } catch (final Throwable ignored) {
            return 25.0;
        }
    }
}
