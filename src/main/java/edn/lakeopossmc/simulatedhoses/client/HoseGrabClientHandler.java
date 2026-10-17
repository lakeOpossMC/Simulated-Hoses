package edn.lakeopossmc.simulatedhoses.client;

import com.simibubi.create.AllItems;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.index.SimKeys;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction;
import edn.lakeopossmc.simulatedhoses.hose.HoseGrabHandler;
import edn.lakeopossmc.simulatedhoses.hose.HoseGrabPacket;
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

    private float distance = -1;
    private int keepAlive;
    @Nullable
    private Quaterniond orientation;
    private boolean orientationChanged;

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

        // * Keep the arm out while holding
        if (!Minecraft.getInstance().gameRenderer.getMainCamera().isDetached()) {
            player.swingTime = 0;
            player.swinging = true;
            player.swingingArm = InteractionHand.MAIN_HAND;
        }

        if (this.orientationChanged || --this.keepAlive <= 0) {
            this.send(false);
        }
        return false;
    }

    // * While the rotate key is down the mouse turns the connector
    @Override
    public boolean activeOnMouseMove(final double yaw, final double pitch) {
        if (!SimKeys.ROTATE_MODE.isPressed()) {
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
        PacketDistributor.sendToServer(new HoseGrabPacket(pos, this.distance, release, rotated, orientation));
    }

    @Nullable
    private static InteractionHand getHandOrNull(final Player player) {
        return isEmptyOrExtendoGrip(player.getMainHandItem()) ? InteractionHand.MAIN_HAND : null;
    }

    private static boolean isEmptyOrExtendoGrip(final ItemStack stack) {
        return stack.isEmpty() || AllItems.EXTENDO_GRIP.isIn(stack);
    }
}