package edn.lakeopossmc.simulatedhoses.client;

import com.simibubi.create.AllItems;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.index.SimKeys;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction;
import edn.lakeopossmc.simulatedhoses.blocks.PressureNozzleBlockEntity;
import edn.lakeopossmc.simulatedhoses.hose.HoseGrabHandler;
import edn.lakeopossmc.simulatedhoses.hose.HoseGrabPacket;
import edn.lakeopossmc.simulatedhoses.hose.NozzleJet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

// --- GRABBING CONNECTORS, CLIENT SIDE --- //
public class HoseGrabClientHandler extends BlockHoldInteraction {

    private static final int KEEP_ALIVE_TICKS = 5;
    private static final double REACH_BUFFER = 3.0;
    private static final float RECOIL_FROM = 0.4F;
    private static final double MAX_RECOIL = 0.02;

    private float distance = -1;
    private int keepAlive;
    @Nullable
    private Quaterniond orientation;
    private boolean orientationChanged;
    private boolean carryingNozzle;
    private boolean attackHeld;
    private int nozzleHeldTicks;
    private boolean trigger;

    @Override
    public void clientTick(final Level level, final LocalPlayer player) {
        if (this.isActive()) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyUse.isDown() || player.isShiftKeyDown() || player.isUsingItem() || minecraft.screen != null) {
            return;
        }
        if (getHandOrNull(player) == null) {
            return;
        }
        final HitResult hit = minecraft.hitResult;
        if (!(hit instanceof final BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        final BlockPos pos = blockHit.getBlockPos();
        if (!HoseGrabHandler.isGrabbable(level.getBlockEntity(pos)) || Sable.HELPER.getContaining(level, new ChunkPos(pos)) == null) {
            return;
        }
        this.startHold(level, player, pos);
    }

    @Override
    public void startHold(final Level level, final Player player, final BlockPos pos) {
        final InteractionHand hand = getHandOrNull(player);
        if (hand == null) {
            return;
        }
        final Vector3d center = Sable.HELPER.projectOutOfSubLevel(level, new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        final Vec3 eye = player.getEyePosition();
        this.distance = Mth.clamp((float) center.distance(eye.x, eye.y, eye.z), HoseGrabHandler.MIN_DISTANCE, HoseGrabHandler.MAX_DISTANCE);
        player.swing(hand);
        super.startHold(level, player, pos);
        this.carryingNozzle = PressureNozzleBlockEntity.forCarried(level, pos) != null;
        this.nozzleHeldTicks = 0;
        this.send(false);
    }

    // * Returns true to let go
    @Override
    public boolean activeTick(final Level level, final LocalPlayer player) {
        final BlockPos pos = this.getInteractionPos();
        if (pos == null || getHandOrNull(player) == null || player.isDeadOrDying() || player.isSpectator()) {
            return true;
        }
        if (!HoseGrabHandler.isGrabbable(level.getBlockEntity(pos)) || Sable.HELPER.getContaining(level, new ChunkPos(pos)) == null) {
            return true;
        }
        final Vector3d center = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (!inInteractionRange(player, center, REACH_BUFFER)) {
            return true;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final PressureNozzleBlockEntity nozzle = PressureNozzleBlockEntity.forCarried(level, pos);
        this.carryingNozzle = nozzle != null;
        this.nozzleHeldTicks = this.carryingNozzle ? this.nozzleHeldTicks + 1 : 0;
        if (nozzle != null) {
            recoil(level, player, nozzle);
        }

        // * Keep the arm out while holding
        if (!this.carryingNozzle && !minecraft.gameRenderer.getMainCamera().isDetached()) {
            player.swingTime = 0;
            player.swinging = true;
            player.swingingArm = InteractionHand.MAIN_HAND;
        }
        final boolean trigger = this.carryingNozzle && minecraft.screen == null
                && (this.attackHeld || minecraft.options.keyAttack.isDown());
        if (trigger != this.trigger) {
            this.trigger = trigger;
            this.send(false);
        } else if (this.orientationChanged || --this.keepAlive <= 0) {
            this.send(false);
        }
        return false;
    }

    // * A hard jet nudges whoever holds it backwards
    private static void recoil(final Level level, final LocalPlayer player, final PressureNozzleBlockEntity nozzle) {
        if (!nozzle.isSpraying()) {
            return;
        }
        final float fraction = Mth.clamp(nozzle.getFlow() / PressureNozzleBlockEntity.FULL_FLOW, 0.0F, 1.0F);
        if (fraction <= RECOIL_FROM) {
            return;
        }
        final double strength = MAX_RECOIL * (fraction - RECOIL_FROM) / (1.0F - RECOIL_FROM);
        final Vec3 pointing = NozzleJet.aim(level, nozzle.getBlockPos(), nozzle.getFacing(), 0.5).direction();
        final Vec3 push = pointing.scale(-strength);
        // * Never lifts the player
        player.setDeltaMovement(player.getDeltaMovement().add(push.x, Math.min(push.y, 0.0), push.z));
    }

    @Override
    public InteractCallback.Result onAttack(final int modifiers, final int action, final KeyMapping key) {
        if (this.isActive()) {
            this.attackHeld = action != 0;
        }
        return super.onAttack(modifiers, action, key);
    }

    public boolean suppressesAttack() {
        return this.isCarryingNozzle();
    }

    public boolean isCarryingNozzle() {
        return this.carryingNozzle && this.getInteractionPos() != null;
    }

    public float getNozzleHeldTicks(final float partialTicks) {
        return this.nozzleHeldTicks + partialTicks;
    }

    // * While the rotate key is down the mouse turns the connector
    @Override
    public boolean activeOnMouseMove(final double yaw, final double pitch) {
        // * A nozzle already follows the camera
        if (!SimKeys.ROTATE_MODE.isPressed() || this.carryingNozzle) {
            return false;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        final BlockPos pos = this.getInteractionPos();
        if (player == null || pos == null || minecraft.level == null) {
            return false;
        }
        if (this.orientation == null) {
            final SubLevel subLevel = Sable.HELPER.getContaining(minecraft.level, new ChunkPos(pos));
            if (subLevel == null) {
                return false;
            }
            this.orientation = new Quaterniond(subLevel.logicalPose().orientation());
        }
        final double sensitivity = SimConfigService.INSTANCE.client().itemConfig.physicsStaffRotateSensitivity.get();
        final Vec3 right = Vec3.directionFromRotation(0.0F, player.getYRot() - 90.0F);
        this.orientation.rotateLocalY(Math.toRadians(yaw) * sensitivity);
        this.orientation.premul(new Quaterniond(new AxisAngle4d(Math.toRadians(-pitch) * sensitivity, right.x, right.y, right.z)));
        this.orientation.normalize();
        this.orientationChanged = true;
        return true;
    }

    @Override
    public InteractCallback.Result onScroll(final double deltaX, final double deltaY) {
        if (!this.isActive()) {
            return InteractCallback.Result.empty();
        }
        if (this.carryingNozzle) {
            return new InteractCallback.Result(true);
        }
        this.distance = Mth.clamp(this.distance + (float) deltaY * 0.5F, HoseGrabHandler.MIN_DISTANCE, HoseGrabHandler.MAX_DISTANCE);
        this.send(false);
        return new InteractCallback.Result(true);
    }

    @Override
    public void stop() {
        if (this.getInteractionPos() != null) {
            this.send(true);
        }
        this.distance = -1;
        this.orientation = null;
        this.orientationChanged = false;
        this.carryingNozzle = false;
        this.attackHeld = false;
        this.nozzleHeldTicks = 0;
        this.trigger = false;
        super.stop();
    }

    private void send(final boolean release) {
        final BlockPos pos = this.getInteractionPos();
        if (pos == null) {
            return;
        }
        this.keepAlive = KEEP_ALIVE_TICKS;
        this.orientationChanged = false;
        final boolean rotated = this.orientation != null;
        final Quaternionf orientation = rotated ? new Quaternionf((float) this.orientation.x, (float) this.orientation.y, (float) this.orientation.z, (float) this.orientation.w) : new Quaternionf();
        PacketDistributor.sendToServer(new HoseGrabPacket(pos, this.distance, release, rotated, orientation, !release && this.trigger));
    }

    @Nullable
    private static InteractionHand getHandOrNull(final Player player) {
        return isEmptyOrExtendoGrip(player.getMainHandItem()) ? InteractionHand.MAIN_HAND : null;
    }

    private static boolean isEmptyOrExtendoGrip(final ItemStack stack) {
        return stack.isEmpty() || AllItems.EXTENDO_GRIP.isIn(stack);
    }
}