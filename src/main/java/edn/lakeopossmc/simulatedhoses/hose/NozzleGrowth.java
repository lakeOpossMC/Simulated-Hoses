package edn.lakeopossmc.simulatedhoses.hose;

import edn.lakeopossmc.simulatedhoses.HoseFluids;
import edn.lakeopossmc.simulatedhoses.fluids.PlantFoodCoating;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;

// --- PLANT FOOD FROM A PRESSURE NOZZLE --- //
// * Where Plant Food lands it's used like bone meal
// * Anything living it hits gets coated
public final class NozzleGrowth {
    private static final int BONE_MEAL_COST = 100;
    private static final int TREE_FERTILIZER_COST = 250;
    private static final int USE_COOLDOWN = 4;
    private static final double REACH = 0.6;
    private static final ResourceLocation TREE_FERTILIZER = ResourceLocation.fromNamespaceAndPath("create", "tree_fertilizer");

    public static final class Progress {
        private int banked;
        private long lastUse = -1;
    }

    public static void apply(final ServerLevel level, final NozzleJet.Impact impact, final FluidStack fluid, final int flow,
                             @Nullable final Player carrier, final Progress progress) {
        if (fluid.is(HoseFluids.PLANT_FOOD.get()) && impact.entity() instanceof final LivingEntity living) {
            PlantFoodCoating.coat(living);
        }
        if (!fluid.is(HoseFluids.PLANT_FOOD.get()) || impact.block() == null) {
            progress.banked = 0;
            return;
        }
        progress.banked = Math.min(progress.banked + flow, TREE_FERTILIZER_COST);
        if (progress.lastUse >= 0 && level.getGameTime() - progress.lastUse < USE_COOLDOWN) {
            return;
        }
        final BlockPos target = findTarget(level, impact.point(), impact.block());
        if (target == null) {
            return;
        }
        final BlockState state = level.getBlockState(target);
        final boolean sapling = state.is(BlockTags.SAPLINGS);
        final int cost = sapling ? TREE_FERTILIZER_COST : BONE_MEAL_COST;
        if (progress.banked < cost) {
            return;
        }
        final Item item = sapling ? BuiltInRegistries.ITEM.getOptional(TREE_FERTILIZER).orElse(Items.BONE_MEAL) : Items.BONE_MEAL;
        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), impact.block().getDirection(), target, false);
        if (use(level, item, target, hit, carrier)) {
            progress.banked -= cost;
            progress.lastUse = level.getGameTime();
        }
    }

    private static boolean use(final ServerLevel level, final Item item, final BlockPos target, final BlockHitResult hit, @Nullable final Player carrier) {
        if (carrier == null && item == Items.BONE_MEAL) {
            if (!BoneMealItem.growCrop(new ItemStack(item), level, target)) {
                return false;
            }
            level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, target, 15);
            return true;
        }
        return item.useOn(new UseOnContext(level, carrier, InteractionHand.MAIN_HAND, new ItemStack(item), hit)).consumesAction();
    }

    @Nullable
    private static BlockPos findTarget(final ServerLevel level, final Vec3 point, final BlockHitResult block) {
        final AABB area = new AABB(point, point).inflate(REACH);
        final Optional<BlockPos> found = BlockPos.betweenClosedStream(area)
                .map(BlockPos::immutable)
                .filter(pos -> canGrow(level, pos))
                .min(Comparator.<BlockPos>comparingInt(pos -> level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() ? 0 : 1)
                        .thenComparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(point)));
        return found.orElse(null);
    }

    private static boolean canGrow(final ServerLevel level, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof final BonemealableBlock plant && plant.isValidBonemealTarget(level, pos, state);
    }

    private NozzleGrowth() {
    }
}
