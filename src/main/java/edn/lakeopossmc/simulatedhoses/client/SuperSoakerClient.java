package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import edn.lakeopossmc.simulatedhoses.HoseFluids;
import edn.lakeopossmc.simulatedhoses.HoseItems;
import edn.lakeopossmc.simulatedhoses.blocks.PressureNozzleBlockEntity;
import edn.lakeopossmc.simulatedhoses.fluids.PlantFoodSounds;
import edn.lakeopossmc.simulatedhoses.hose.NozzleJet;
import edn.lakeopossmc.simulatedhoses.hose.NozzleProcessing;
import edn.lakeopossmc.simulatedhoses.hose.SpraySource;
import edn.lakeopossmc.simulatedhoses.items.SuperSoakerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

// --- SUPER SOAKER ON THE CLIENT --- //
public final class SuperSoakerClient {
    public static final SuperSoakerRenderHandler RENDER_HANDLER = new SuperSoakerRenderHandler();

    private static final float LANDING_SQUISH_CHANCE = 1.0F / 10.0F;

    private static final Map<Player, Soaker> SOAKERS = new WeakHashMap<>();

    public static void registerExtensions(final RegisterClientExtensionsEvent event) {
        event.registerItem(SimpleCustomRenderer.create(HoseItems.SUPER_SOAKER.get(), new SuperSoakerItemRenderer()),
                HoseItems.SUPER_SOAKER.get());
    }

    public static void tick() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        RENDER_HANDLER.tick();
        SOAKERS.keySet().removeIf(player -> player.isRemoved() || player.level() != mc.level);
        for (final Player player : mc.level.players()) {
            SOAKERS.computeIfAbsent(player, Soaker::new).tick();
        }
    }

    private static void kick(final InteractionHand hand, final Vec3 tip) {
        RENDER_HANDLER.dontAnimateItem(hand);
        RENDER_HANDLER.shoot(hand, tip);
    }

    private static boolean inLevel;

    public static void onRenderStage(final RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            inLevel = true;
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            inLevel = false;
        }
    }

    public static boolean isInLevel() {
        return inLevel;
    }

    public static void clientReset() {
        SOAKERS.clear();
    }

    @Nullable
    public static Animation animationFor(final ItemStack stack, final ItemDisplayContext context) {
        for (final Soaker soaker : SOAKERS.values()) {
            if (soaker.player.getMainHandItem() == stack) {
                return soaker.mainHand;
            }
            if (soaker.player.getOffhandItem() == stack) {
                return soaker.offHand;
            }
        }
        final Player player = Minecraft.getInstance().player;
        final Soaker local = player != null ? SOAKERS.get(player) : null;
        if (local == null || !context.firstPerson()) {
            return null;
        }
        final boolean rightHand = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        return rightHand == (player.getMainArm() == HumanoidArm.RIGHT) ? local.mainHand : local.offHand;
    }

    // * Which player is holding the soaker being drawn
    @Nullable
    public static Player holderOf(final ItemStack stack, final ItemDisplayContext context) {
        for (final Soaker soaker : SOAKERS.values()) {
            if (soaker.player.getMainHandItem() == stack || soaker.player.getOffhandItem() == stack) {
                return soaker.player;
            }
        }
        return context.firstPerson() ? Minecraft.getInstance().player : null;
    }

    private static final Vector3f TIP = new Vector3f(8.0F, 8.5F, 0.0F).div(16.0F).sub(0.5F, 0.5F, 0.5F);
    private static final long TIP_FRESH_TICKS = 2;

    public static void tipDrawn(final ItemStack stack, final ItemDisplayContext context, final PoseStack ms) {
        if ((context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                || !isInLevel()) {
            return;
        }
        for (final Soaker soaker : SOAKERS.values()) {
            final int hand = soaker.player.getMainHandItem() == stack ? 0 : soaker.player.getOffhandItem() == stack ? 1 : -1;
            if (hand < 0) {
                continue;
            }
            final Minecraft mc = Minecraft.getInstance();
            final Vector3f drawn = ms.last().pose().transformPosition(new Vector3f(TIP));
            final Vec3 world = mc.gameRenderer.getMainCamera().getPosition().add(drawn.x, drawn.y, drawn.z);
            final float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
            soaker.drawnTip[hand] = world.subtract(soaker.player.getPosition(partialTick));
            soaker.drawnAt[hand] = soaker.player.level().getGameTime();
            return;
        }
    }

    //#region // --- ONE PLAYER'S SOAKERS --- //
    private static final class Soaker {
        private final Player player;
        private final Animation mainHand = new Animation();
        private final Animation offHand = new Animation();
        private final Hand[] hands = {new Hand(this, InteractionHand.MAIN_HAND), new Hand(this, InteractionHand.OFF_HAND)};
        private final Vec3[] drawnTip = new Vec3[2];
        private final long[] drawnAt = new long[2];

        private Soaker(final Player player) {
            this.player = player;
        }

        private void tick() {
            final boolean using = this.player.isUsingItem() && this.player.getUseItem().getItem() instanceof SuperSoakerItem;
            for (final Hand hand : this.hands) {
                final ItemStack held = this.player.getItemInHand(hand.hand);
                hand.fluid = using && held.getItem() instanceof SuperSoakerItem ? SuperSoakerItem.getFluid(held) : FluidStack.EMPTY;
                final boolean started = !hand.spraying && !hand.fluid.isEmpty();
                hand.spraying = !hand.fluid.isEmpty();
                if (started && this.player == Minecraft.getInstance().player) {
                    kick(hand.hand, SuperSoakerItem.aim(this.player, hand.hand).start());
                }
            }
            this.mainHand.tick(this.hands[0].spraying);
            this.offHand.tick(this.hands[1].spraying);
            for (final Hand hand : this.hands) {
                if (hand.spraying) {
                    hand.spray();
                }
            }
        }
    }

    private static final class Hand implements SpraySource {
        private final Soaker soaker;
        private final InteractionHand hand;
        private FluidStack fluid = FluidStack.EMPTY;
        private boolean spraying;

        private Hand(final Soaker soaker, final InteractionHand hand) {
            this.soaker = soaker;
            this.hand = hand;
        }

        private void spray() {
            final Level level = this.getLevel();
            final NozzleJet.Aim aim = this.aim();
            NozzleSpray.tick(level, aim, this.fluid, SuperSoakerItem.FLOW, PressureNozzleBlockEntity.FULL_FLOW);
            NozzleSpraySound.tick(this);
            final NozzleJet.Impact impact = NozzleJet.trace(level, aim, SuperSoakerItem.FRACTION, this.soaker.player);
            if (impact != null) {
                NozzleProcessing.spawnParticles(level, impact, this.fluid);
                if (this.fluid.is(HoseFluids.PLANT_FOOD.get()) && level.random.nextFloat() < LANDING_SQUISH_CHANCE) {
                    PlantFoodSounds.squishLocal(level, impact.point(), 0.5F + SuperSoakerItem.FRACTION * 0.3F);
                }
            }
        }

        @Override
        public Level getLevel() {
            return this.soaker.player.level();
        }

        @Override
        public boolean isRemoved() {
            return this.soaker.player.isRemoved();
        }

        @Override
        public boolean isSpraying() {
            return this.spraying;
        }

        @Override
        public FluidStack getSprayFluid() {
            return this.fluid;
        }

        @Override
        public float getSprayFraction() {
            return SuperSoakerItem.FRACTION;
        }

        @Override
        public Vec3 getSprayTip() {
            return this.aim().start();
        }

        private NozzleJet.Aim aim() {
            final Player player = this.soaker.player;
            final NozzleJet.Aim aim = SuperSoakerItem.aim(player, this.hand);
            final Minecraft mc = Minecraft.getInstance();
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
                return aim;
            }
            final int hand = this.hand.ordinal();
            final Vec3 tip = this.soaker.drawnTip[hand];
            if (tip == null || player.level().getGameTime() - this.soaker.drawnAt[hand] > TIP_FRESH_TICKS) {
                return aim;
            }
            return NozzleJet.aimAlong(player.position().add(tip), aim.direction());
        }

        @Override
        public SoundSource getSpraySoundSource() {
            return SoundSource.PLAYERS;
        }
    }
    //#endregion

    //#region // --- MOVING PARTS --- //
    public static final class Animation {
        private static final float PRESS_SCALE = 0.5F;

        private float spin;
        private float spinPrevious;
        private float spinSpeed;
        private float rubber;
        private float rubberPrevious;
        private float tip;
        private float tipPrevious;

        private void tick(final boolean spraying) {
            final float fraction = SuperSoakerItem.FRACTION;
            final float spinTarget = spraying
                    ? Mth.lerp(fraction, PressureNozzleBlockEntity.MIN_SPIN, PressureNozzleBlockEntity.MAX_SPIN) : 0.0F;
            this.spinSpeed += (spinTarget - this.spinSpeed) * PressureNozzleBlockEntity.SPIN_EASE;
            if (spinTarget == 0.0F && Math.abs(this.spinSpeed) < 0.01F) {
                this.spinSpeed = 0.0F;
            }
            this.spinPrevious = this.spin;
            this.spin += this.spinSpeed;
            if (this.spin >= 360.0F) {
                this.spin -= 360.0F;
                this.spinPrevious -= 360.0F;
            }

            this.rubberPrevious = this.rubber;
            this.tipPrevious = this.tip;
            final float rubberTarget = spraying ? PressureNozzleBlockEntity.NOZZLE_PRESS * PRESS_SCALE : 0.0F;
            final float tipTarget = spraying ? (PressureNozzleBlockEntity.NOZZLE_PRESS
                    + Mth.lerp(fraction, PressureNozzleBlockEntity.TIP_EXTRA_MIN, PressureNozzleBlockEntity.TIP_EXTRA_MAX)) * PRESS_SCALE : 0.0F;
            this.rubber += (rubberTarget - this.rubber) * PressureNozzleBlockEntity.PRESS_EASE;
            this.tip += (tipTarget - this.tip) * PressureNozzleBlockEntity.PRESS_EASE;
        }

        public float getSpin(final float partialTicks) {
            return Mth.lerp(partialTicks, this.spinPrevious, this.spin);
        }

        public float getRubber(final float partialTicks) {
            return Mth.lerp(partialTicks, this.rubberPrevious, this.rubber);
        }

        public float getTip(final float partialTicks) {
            return Mth.lerp(partialTicks, this.tipPrevious, this.tip);
        }
    }
    //#endregion

    private SuperSoakerClient() {
    }
}
