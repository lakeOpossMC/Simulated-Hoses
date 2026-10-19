package edn.lakeopossmc.simulatedhoses.hose;

import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

// --- WHAT A PRESSURE NOZZLE'S JET DOES WHERE IT LANDS --- //
public final class NozzleSprayEffects {
    private static final double SPLASH = 0.6;
    private static final int SLOW_EFFECT_INTERVAL = 5;

    private static final int OXIDIZE_CHANCE = 200;
    private static final int MUD_CHANCE = 60;
    private static final int SPONGE_CHANCE = 50;
    private static final int CONCRETE_CHANCE = 30;
    private static final int LAVA_COOL_CHANCE = 20;
    private static final int IGNITE_CHANCE = 8;

    public static void apply(final ServerLevel level, final NozzleJet.Impact impact, final FluidStack fluid) {
        if (fluid.isEmpty()) {
            return;
        }
        final AABB area = impact.entity() != null
                ? impact.entity().getBoundingBox().inflate(SPLASH * 0.5)
                : new AABB(impact.point(), impact.point()).inflate(SPLASH);

        final OpenPipeEffectHandler handler = OpenPipeEffectHandler.REGISTRY.get(fluid.getFluid());
        if (handler != null) {
            handler.apply(level, area, fluid.copy());
        }

        if (fluid.is(FluidTags.WATER)) {
            water(level, area, impact.block());
        } else if (fluid.is(FluidTags.LAVA)) {
            lava(level, impact.block());
        }
    }

    //#region // --- PRESSURE --- //
    private static final float PUSH_FROM = 0.4F;
    private static final double MAX_PUSH = 0.35;
    private static final double MAX_PUSHED_SPEED = 1.1;

    public static void push(final NozzleJet.Impact impact, final double fraction) {
        final Entity entity = impact.entity();
        if (entity == null || fraction <= PUSH_FROM || !entity.isPushable() && !(entity instanceof Player)) {
            return;
        }
        double strength = MAX_PUSH * (fraction - PUSH_FROM) / (1.0 - PUSH_FROM);
        if (entity instanceof final LivingEntity living) {
            strength *= 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        }
        if (strength <= 0.0) {
            return;
        }
        final Vec3 push = impact.velocity().normalize().scale(strength);
        final Vec3 motion = entity.getDeltaMovement().add(push);
        final Vec3 capped = motion.lengthSqr() > MAX_PUSHED_SPEED * MAX_PUSHED_SPEED
                && motion.lengthSqr() > entity.getDeltaMovement().lengthSqr()
                ? entity.getDeltaMovement() : motion;
        entity.setDeltaMovement(capped);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }
    //#endregion

    //#region // --- WATER --- //
    private static void water(final ServerLevel level, final AABB area, final BlockHitResult block) {
        if (level.getGameTime() % SLOW_EFFECT_INTERVAL == 0) {
            for (final LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (entity.isSensitiveToWater()) {
                    entity.hurt(level.damageSources().drown(), 1.0F);
                }
                if (entity instanceof final Axolotl axolotl) {
                    axolotl.rehydrate();
                }
            }
        }
        if (block == null) {
            return;
        }
        final RandomSource random = level.random;
        BlockPos.betweenClosedStream(area).map(BlockPos::immutable).toList()
                .forEach(pos -> waterOnBlock(level, pos, random));
    }

    private static void waterOnBlock(final ServerLevel level, final BlockPos pos, final RandomSource random) {
        final BlockState state = level.getBlockState(pos);
        final FluidState fluid = state.getFluidState();

        if (fluid.is(FluidTags.LAVA)) {
            if (random.nextInt(LAVA_COOL_CHANCE) == 0) {
                level.setBlockAndUpdate(pos, (fluid.isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState());
                level.levelEvent(LevelEvent.LAVA_FIZZ, pos, 0);
            }
            return;
        }

        if (state.getBlock() instanceof ConcretePowderBlock) {
            final Block concrete = concreteFor(state.getBlock());
            if (concrete != null && random.nextInt(CONCRETE_CHANCE) == 0) {
                level.setBlockAndUpdate(pos, concrete.defaultBlockState());
            }
            return;
        }

        if (state.is(BlockTags.CONVERTABLE_TO_MUD)) {
            if (random.nextInt(MUD_CHANCE) == 0) {
                level.setBlockAndUpdate(pos, Blocks.MUD.defaultBlockState());
                level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.5F, 1.0F);
                level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 5, 0.3, 0.0, 0.3, 0.0);
            }
            return;
        }

        if (state.is(Blocks.SPONGE)) {
            if (random.nextInt(SPONGE_CHANCE) == 0) {
                level.setBlockAndUpdate(pos, Blocks.WET_SPONGE.defaultBlockState());
            }
            return;
        }

        if (state.getBlock() instanceof final WeatheringCopper copper && random.nextInt(OXIDIZE_CHANCE) == 0) {
            copper.getNext(state).ifPresent(next -> level.setBlockAndUpdate(pos, next));
        }
    }

    private static Block concreteFor(final Block powder) {
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(powder);
        if (!id.getPath().endsWith("_powder")) {
            return null;
        }
        final ResourceLocation concreteId = id.withPath(id.getPath().substring(0, id.getPath().length() - "_powder".length()));
        return BuiltInRegistries.BLOCK.getOptional(concreteId).orElse(null);
    }
    //#endregion

    //#region // --- LAVA --- //
    private static void lava(final ServerLevel level, final BlockHitResult block) {
        if (block == null || level.random.nextInt(IGNITE_CHANCE) != 0
                || !level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            return;
        }
        final BlockPos hit = block.getBlockPos();
        final BlockState state = level.getBlockState(hit);
        if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) {
            level.setBlockAndUpdate(hit, state.setValue(BlockStateProperties.LIT, true));
            level.playSound(null, hit, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
            return;
        }
        final Direction face = block.getDirection();
        final BlockPos firePos = hit.relative(face);
        if (level.getBlockState(firePos).isAir() && BaseFireBlock.canBePlacedAt(level, firePos, face)) {
            level.setBlockAndUpdate(firePos, BaseFireBlock.getState(level, firePos));
        }
    }
    //#endregion

    private NozzleSprayEffects() {
    }
}
