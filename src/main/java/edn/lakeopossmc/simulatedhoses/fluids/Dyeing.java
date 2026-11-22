package edn.lakeopossmc.simulatedhoses.fluids;

import edn.lakeopossmc.simulatedhoses.HoseDataComponents;
import edn.lakeopossmc.simulatedhoses.HoseItems;
import edn.lakeopossmc.simulatedhoses.compat.HoseDyeing;
import edn.lakeopossmc.simulatedhoses.mixin.CatCollarAccessor;
import edn.lakeopossmc.simulatedhoses.mixin.WolfCollarAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// --- WHAT DYE FLUIDS DYE --- //
public final class Dyeing {
    // * Families of coloured blocks
    private static final Set<String> FAMILIES = Set.of(
            "wool", "carpet", "terracotta", "glazed_terracotta", "concrete", "concrete_powder", "stained_glass", "stained_glass_pane",
            "shulker_box", "candle", "candle_cake", "banner", "wall_banner", "bed");
    // * Uncoloured blocks that join a family once dyed
    private static final Map<String, String> PLAIN = Map.of(
            "minecraft:terracotta", "terracotta",
            "minecraft:glass", "stained_glass",
            "minecraft:glass_pane", "stained_glass_pane",
            "minecraft:shulker_box", "shulker_box",
            "minecraft:candle", "candle",
            "minecraft:candle_cake", "candle_cake");
    private static final int CONTACT_INTERVAL = 5;

    @Nullable
    private static Map<Block, String> familyOf;
    @Nullable
    private static Map<String, Map<DyeColor, Block>> family;

    //#region // --- BLOCKS --- //
    // * Dyes the block at this spot, if it can be dyed and isn't already this colour
    public static void dyeBlock(final ServerLevel level, final BlockPos pos, final DyeColor color) {
        final BlockState state = level.getBlockState(pos);
        final BlockEntity be = level.getBlockEntity(pos);
        if (be != null && HoseDyeing.dyeBlockEntity(be, color)) {
            return;
        }
        final Block target = recolored(state.getBlock(), color);
        if (target == null || target == state.getBlock()) {
            return;
        }
        final List<BlockPos> parts = new ArrayList<>(2);
        parts.add(pos);
        final BlockPos other = otherPart(state, pos);
        if (other != null && level.getBlockState(other).is(state.getBlock())) {
            parts.add(other);
        }
        final List<BlockState> placed = new ArrayList<>(parts.size());
        for (final BlockPos part : parts) {
            placed.add(swap(level, part, level.getBlockState(part), target));
        }
        for (int i = 0; i < parts.size(); i++) {
            final BlockPos part = parts.get(i);
            placed.get(i).updateNeighbourShapes(level, part, Block.UPDATE_ALL);
            level.blockUpdated(part, target);
        }
    }

    private static BlockState swap(final ServerLevel level, final BlockPos pos, final BlockState state, final Block target) {
        BlockState next = target.defaultBlockState();
        for (final Property<?> property : state.getProperties()) {
            if (next.hasProperty(property)) {
                next = copy(state, next, property);
            }
        }
        // * Shulker boxes keep their items and banners their patterns
        final BlockEntity be = level.getBlockEntity(pos);
        final CompoundTag saved = be != null ? be.saveWithoutMetadata(level.registryAccess()) : null;
        level.setBlock(pos, next, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (saved != null && level.getBlockEntity(pos) instanceof final BlockEntity replaced) {
            replaced.loadWithComponents(saved, level.registryAccess());
            replaced.setChanged();
        }
        return next;
    }

    // * The other half of a two-part block: the head or foot of a bed, or the top or bottom of a tall block
    @Nullable
    private static BlockPos otherPart(final BlockState state, final BlockPos pos) {
        if (state.hasProperty(BlockStateProperties.BED_PART) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            final Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return pos.relative(state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT ? facing : facing.getOpposite());
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        }
        return null;
    }

    private static <T extends Comparable<T>> BlockState copy(final BlockState from, final BlockState to, final Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    @Nullable
    private static Block recolored(final Block block, final DyeColor color) {
        buildFamilies();
        final String name = familyOf.get(block);
        if (name == null) {
            return null;
        }
        return family.getOrDefault(name, Map.of()).get(color);
    }

    private static synchronized void buildFamilies() {
        if (familyOf != null) {
            return;
        }
        final Map<Block, String> blocks = new HashMap<>();
        final Map<String, Map<DyeColor, Block>> families = new HashMap<>();
        // * Longest colour names first, so light_blue isn't taken for blue
        final List<DyeColor> colors = java.util.Arrays.stream(DyeColor.values())
                .sorted(Comparator.comparingInt((DyeColor c) -> c.getName().length()).reversed()).toList();
        for (final Block block : BuiltInRegistries.BLOCK) {
            final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            final String plain = PLAIN.get(id.toString());
            if (plain != null) {
                blocks.put(block, plain);
                continue;
            }
            for (final DyeColor color : colors) {
                final String prefix = color.getName() + "_";
                if (id.getPath().startsWith(prefix)) {
                    final String rest = id.getPath().substring(prefix.length());
                    if (FAMILIES.contains(rest)) {
                        blocks.put(block, rest);
                        families.computeIfAbsent(rest, r -> new EnumMap<>(DyeColor.class)).putIfAbsent(color, block);
                    }
                    break;
                }
            }
        }
        family = families;
        familyOf = blocks;
    }

    public static void dyePatch(final ServerLevel level, final BlockPos hit, final Direction face, final int radius, final DyeColor color) {
        final Direction.Axis axis = face.getAxis();
        final int limit = radius * radius + radius;
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                if (a * a + b * b > limit) {
                    continue;
                }
                final BlockPos pos = switch (axis) {
                    case X -> hit.offset(0, a, b);
                    case Y -> hit.offset(a, 0, b);
                    case Z -> hit.offset(a, b, 0);
                };
                dyeBlock(level, pos, color);
                dyeBlock(level, pos.relative(face), color);
            }
        }
        final AABB area = new AABB(hit).inflate(axis == Direction.Axis.X ? 1 : radius + 0.5,
                axis == Direction.Axis.Y ? 1 : radius + 0.5, axis == Direction.Axis.Z ? 1 : radius + 0.5).move(face.getStepX(), face.getStepY(), face.getStepZ());
        for (final Entity entity : level.getEntities((Entity) null, area, e -> !e.isSpectator())) {
            dyeEntity(entity, color);
        }
    }

    public static void dyeTouching(final ServerLevel level, final BlockPos pos, final DyeColor color) {
        for (final Direction direction : Direction.values()) {
            dyeBlock(level, pos.relative(direction), color);
        }
    }
    //#endregion

    //#region // --- ENTITIES AND ITEMS --- //
    public static void dyeEntity(final Entity entity, final DyeColor color) {
        if (entity instanceof final Sheep sheep && !sheep.isSheared() && sheep.getColor() != color) {
            sheep.setColor(color);
        } else if (entity instanceof final Wolf wolf && wolf.isTame() && wolf.getCollarColor() != color) {
            ((WolfCollarAccessor) wolf).simulatedhoses$setCollarColor(color);
        } else if (entity instanceof final Cat cat && cat.isTame() && cat.getCollarColor() != color) {
            ((CatCollarAccessor) cat).simulatedhoses$setCollarColor(color);
        } else if (entity instanceof final Shulker shulker && shulker.getVariant().orElse(null) != color) {
            shulker.setVariant(Optional.of(color));
        } else if (entity instanceof final ItemEntity item) {
            final ItemStack dyed = dyeStack(item.getItem(), color);
            if (dyed != item.getItem()) {
                item.setItem(dyed);
            }
        }
        if (entity instanceof final LivingEntity living) {
            for (final EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.isArmor()) {
                    final ItemStack worn = living.getItemBySlot(slot);
                    final ItemStack dyed = dyeStack(worn, color);
                    if (dyed != worn) {
                        living.setItemSlot(slot, dyed);
                    }
                }
            }
        }
    }

    public static ItemStack dyeStack(final ItemStack stack, final DyeColor color) {
        if (stack.isEmpty()) {
            return stack;
        }
        if (stack.is(ItemTags.DYEABLE)) {
            final int rgb = color.getTextureDiffuseColor() & 0xFFFFFF;
            final DyedItemColor current = stack.get(DataComponents.DYED_COLOR);
            if (current != null && current.rgb() == rgb) {
                return stack;
            }
            final ItemStack dyed = stack.copy();
            dyed.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, current == null || current.showInTooltip()));
            return dyed;
        }
        if (HoseDyeing.isLoaded() && stack.is(HoseItems.SUPER_SOAKER.get())) {
            return recolor(stack, HoseDataComponents.SOAKER_COLOR.get(), color);
        }
        if (HoseDyeing.isLoaded() && stack.is(HoseItems.COPPER_FLUID_BACKTANK.get())) {
            return recolor(stack, HoseDataComponents.BACKTANK_COLOR.get(), color);
        }
        if (stack.getItem() instanceof final BlockItem blockItem) {
            final Block target = recolored(blockItem.getBlock(), color);
            if (target != null && target != blockItem.getBlock() && target.asItem() != blockItem) {
                return stack.transmuteCopy(target.asItem(), stack.getCount());
            }
        }
        return stack;
    }

    private static ItemStack recolor(final ItemStack stack, final net.minecraft.core.component.DataComponentType<DyeColor> type, final DyeColor color) {
        if (stack.get(type) == color) {
            return stack;
        }
        final ItemStack dyed = stack.copy();
        dyed.set(type, color);
        return dyed;
    }

    public static void onEntityTick(final EntityTickEvent.Post event) {
        final Entity entity = event.getEntity();
        final Level level = entity.level();
        if (level.isClientSide || (entity.tickCount + entity.getId()) % CONTACT_INTERVAL != 0) {
            return;
        }
        DyeColor color = colorAt(level, entity.blockPosition());
        if (color == null) {
            color = colorAt(level, BlockPos.containing(entity.getEyePosition()));
        }
        if (color != null) {
            dyeEntity(entity, color);
        }
    }

    @Nullable
    private static DyeColor colorAt(final Level level, final BlockPos pos) {
        final FluidState fluid = level.getFluidState(pos);
        return fluid.isEmpty() ? null : PaintColors.of(fluid.getType());
    }
    //#endregion

    private Dyeing() {
    }
}
