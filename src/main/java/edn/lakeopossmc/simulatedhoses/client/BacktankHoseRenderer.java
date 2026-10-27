package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidFX;
import edn.lakeopossmc.simulatedhoses.backtank.FluidBacktankItem;
import edn.lakeopossmc.simulatedhoses.backtank.StowedSoakers;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// --- HOSE FROM A FLUID BACKTANK --- //
// * Fun physics hose visual
public final class BacktankHoseRenderer {
    private static final float PIXEL = 1.0F / 16.0F;
    private static final Vector3f SOAKER_END = new Vector3f(8.0F, 6.0F, 12.75F).mul(PIXEL).sub(0.5F, 0.5F, 0.5F);
    private static final Vector3f FIRST_PERSON_BEND = new Vector3f(0.0F, -0.9F, 0.7F);
    private static final Vector3f FIRST_PERSON_END = new Vector3f(0.0F, -2.0F, 1.6F);
    private static final Vector3f TANK_END = new Vector3f(16.0F - 3.0F, 1.0F, 16.0F - 8.0F).mul(PIXEL);
    private static final Vector3f MIDDLE_END = new Vector3f(8.0F, 0.0F, 8.0F).mul(PIXEL);
    private static final Vector3f[][] TANK_BOXES = {
            {new Vector3f(11, 1, 6).mul(PIXEL), new Vector3f(15, 11, 10).mul(PIXEL)},
            {new Vector3f(1, 1, 6).mul(PIXEL), new Vector3f(5, 11, 10).mul(PIXEL)}};

    //#region // --- ROPE SETTINGS --- //
    private static final double SEGMENT = 0.25;
    private static final int POINTS = 9;
    private static final double GRAVITY = 14.0;
    private static final double DAMPING = 0.9;
    private static final int ITERATIONS = 20;
    private static final double MAX_STEP = 1.0 / 20.0;
    private static final long STALE_NANOS = 500_000_000L;
    private static final double CLEARANCE = 1.5 / 16.0;
    private static final Vector3f STOWED_AT = new Vector3f(1.5F, 6.0F, 8.0F).mul(1.0F / 16.0F);
    private static final Vector3f STOWED_ON_BACK_AT = new Vector3f(8.0F, 6.0F, 1.5F).mul(1.0F / 16.0F);
    private static final float STOWED_SCALE = 1.0F;
    private static final int STOWED_SHOWN = 1;
    private static final float STOWED_STEP = 5.0F / 16.0F;
    private static final float SOAKER_MIDDLE = 0.75F / 16.0F;
    private static final float TUCK = 1.5F / 16.0F;
    private static final Vector3f HIP = new Vector3f(7.0F, 14.0F, 0.0F).mul(1.0F / 16.0F);
    private static final double SNAG_STRETCH = 4.0;
    private static final double[] SEGMENT_CHECKS = {0.25, 0.5, 0.75};
    //#endregion

    private record Captured(Vector3f point, long frame) {
    }

    // * A hose for each hand, and one for the stowed soaker
    private static final int STOWED_HOSE = 2;

    private static final Map<Player, Captured[]> SOAKER_ENDS = new WeakHashMap<>();
    private static final Map<Player, Rope[]> ROPES = new WeakHashMap<>();
    private static long frame;

    public static void onFrame(final RenderFrameEvent.Pre event) {
        frame++;
    }


    public static boolean hasHose(final Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof FluidBacktankItem;
    }

    //#region // --- SOAKER END --- //
    public static void soakerDrawn(final ItemStack stack, final ItemDisplayContext context, final PoseStack ms,
                                   final MultiBufferSource buffer, final int light) {
        if (context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                && !context.firstPerson()) {
            return;
        }
        final Player holder = SuperSoakerClient.holderOf(stack, context);
        if (holder == null || !hasHose(holder)) {
            return;
        }
        // * Every soaker in a hand gets its own hose
        if (context.firstPerson()) {
            final List<Vector3f> points = new ArrayList<>();
            for (int i = 0; i < POINTS; i++) {
                final float t = i / (float) (POINTS - 1);
                final float u = 1.0F - t;
                points.add(new Vector3f(SOAKER_END).mul(u * u).add(new Vector3f(FIRST_PERSON_BEND).mul(2.0F * u * t))
                        .add(new Vector3f(FIRST_PERSON_END).mul(t * t)));
            }
            drawHose(ms, buffer, light, points);
            return;
        }
        final InteractionHand hand = holder.getMainHandItem() == stack ? InteractionHand.MAIN_HAND
                : holder.getOffhandItem() == stack ? InteractionHand.OFF_HAND : null;
        if (hand != null) {
            SOAKER_ENDS.computeIfAbsent(holder, p -> new Captured[2])[hand.ordinal()] =
                    new Captured(ms.last().pose().transformPosition(new Vector3f(SOAKER_END)), frame);
        }
    }
    //#endregion

    //#region // --- BACKTANK END --- //
    public static void addLayers(final EntityRenderersEvent.AddLayers event) {
        for (final PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof final LivingEntityRenderer<?, ?> renderer) {
                addTo(renderer);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addTo(final LivingEntityRenderer renderer) {
        renderer.addLayer(new Layer<>(renderer));
    }

    private static final class Layer<T extends Player, M extends EntityModel<T>> extends RenderLayer<T, M> {
        private Layer(final RenderLayerParent<T, M> parent) {
            super(parent);
        }

        @Override
        public void render(final PoseStack ms, final MultiBufferSource buffer, final int light, final T player, final float limbSwing,
                           final float limbSwingAmount, final float partialTick, final float ageInTicks, final float netHeadYaw, final float headPitch) {
            if (player.getPose() == Pose.SLEEPING || !hasHose(player) || !(this.getParentModel() instanceof final HumanoidModel<?> model)) {
                SOAKER_ENDS.remove(player);
                return;
            }
            final ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
            final int tankCount = FluidBacktankItem.tankCount(worn);
            final boolean fromMiddle = worn.getItem() instanceof FluidBacktankItem.Netherite;
            renderWornFluid(ms, buffer, light, player, model);
            // * A hose to each soaker in a hand, and one to the stowed soaker
            final Captured[] held = SOAKER_ENDS.remove(player);
            final Vector3f stowedEnd = renderStowed(ms, buffer, light, player, model, tankCount > 1);
            final Vector3f[] ends = new Vector3f[3];
            boolean holding = false;
            for (int hand = 0; hand < 2; hand++) {
                if (held != null && held[hand] != null && held[hand].frame() == frame) {
                    ends[hand] = held[hand].point();
                    holding = true;
                }
            }
            ends[STOWED_HOSE] = stowedEnd;
            if (!SuperSoakerClient.isInLevel() || (!holding && stowedEnd == null)) {
                return;
            }
            final Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            final Matrix4f layer = new Matrix4f(ms.last().pose());
            final Matrix4f toLocal = new Matrix4f(layer).invert();

            // * The pieces of the player the hose bumps into, each a box in its own space
            final List<Obstacle> obstacles = new ArrayList<>();
            obstacles.add(Obstacle.of(ms, model.body, -4, 0, -2, 4, 12, 2));
            obstacles.add(Obstacle.of(ms, model.rightLeg, -2, 0, -2, 2, 12, 2));
            obstacles.add(Obstacle.of(ms, model.leftLeg, -2, 0, -2, 2, 12, 2));
            obstacles.add(Obstacle.of(ms, model.rightArm, -3, -2, -2, 1, 10, 2));
            obstacles.add(Obstacle.of(ms, model.leftArm, -1, -2, -2, 3, 10, 2));

            // * The backtank and its side tank
            ms.pushPose();
            model.body.translateAndRotate(ms);
            ms.translate(-0.5F, 10.0F / 16.0F, 1.0F);
            ms.scale(1.0F, -1.0F, -1.0F);
            final Matrix4f backtank = new Matrix4f(ms.last().pose());
            ms.popPose();
            ms.pushPose();
            model.body.translateAndRotate(ms);
            final Matrix4f body = new Matrix4f(ms.last().pose());
            ms.popPose();
            final Matrix4f toBody = new Matrix4f(body).invert();
            obstacles.add(new Obstacle(backtank, new Vector3f(4, 0, 4).mul(PIXEL), new Vector3f(12, 12, 12).mul(PIXEL)));
            for (int tank = 0; tank < tankCount; tank++) {
                obstacles.add(new Obstacle(backtank, TANK_BOXES[tank][0], TANK_BOXES[tank][1]));
            }

            final Vec3 start = world(backtank.transformPosition(new Vector3f(fromMiddle ? MIDDLE_END : TANK_END)), camera);
            final Rope[] ropes = ROPES.computeIfAbsent(player, p -> new Rope[3]);
            for (int i = 0; i < ends.length; i++) {
                if (ends[i] == null) {
                    ropes[i] = null;
                    continue;
                }
                if (ropes[i] == null) {
                    ropes[i] = new Rope();
                }
                final float side = !fromMiddle || toBody.transformPosition(new Vector3f(ends[i])).x >= 0.0F ? 1.0F : -1.0F;
                final Vec3 hip = world(body.transformPosition(new Vector3f(HIP).mul(side, 1.0F, 1.0F)), camera);
                ropes[i].step(start, hip, world(ends[i], camera), player.getPosition(partialTick), obstacles, camera, player.getY());
                final List<Vector3f> points = new ArrayList<>();
                for (final Vec3 point : ropes[i].points) {
                    points.add(toLocal.transformPosition(new Vector3f(
                            (float) (point.x - camera.x), (float) (point.y - camera.y), (float) (point.z - camera.z))));
                }
                drawHose(ms, buffer, light, points);
            }
        }
    }

    private static void renderWornFluid(final PoseStack ms, final MultiBufferSource buffer, final int light, final Player player,
                                        final HumanoidModel<?> model) {
        final ItemStack backtank = player.getItemBySlot(EquipmentSlot.CHEST);
        ms.pushPose();
        model.body.translateAndRotate(ms);
        ms.translate(-0.5F, 10.0F / 16.0F, 1.0F);
        ms.scale(1.0F, -1.0F, -1.0F);
        FluidBacktankRendering.renderTanks(tank -> FluidBacktankItem.getFluid(backtank, tank), FluidBacktankItem.tankCount(backtank),
                FluidBacktankItem.capacity(backtank), Direction.SOUTH, ms, buffer, light);
        ms.popPose();
    }

    @Nullable
    private static Vector3f renderStowed(final PoseStack ms, final MultiBufferSource buffer, final int light, final Player player,
                                         final HumanoidModel<?> model, final boolean onBack) {
        final List<ItemStack> stowed = StowedSoakers.stowedOn(player);
        Vector3f hoseEnd = null;
        for (int i = 0; i < Math.min(stowed.size(), STOWED_SHOWN); i++) {
            ms.pushPose();
            model.body.translateAndRotate(ms);
            ms.translate(-0.5F, 10.0F / 16.0F, 1.0F);
            ms.scale(1.0F, -1.0F, -1.0F);
            if (onBack) {
                ms.translate(STOWED_ON_BACK_AT.x, STOWED_ON_BACK_AT.y, STOWED_ON_BACK_AT.z - i * STOWED_STEP);
            } else {
                ms.translate(STOWED_AT.x - i * STOWED_STEP, STOWED_AT.y, STOWED_AT.z);
            }
            ms.mulPose(Axis.YP.rotationDegrees(onBack ? 270.0F : 180.0F));
            ms.mulPose(Axis.XP.rotationDegrees(-90.0F));
            ms.scale(STOWED_SCALE, STOWED_SCALE, STOWED_SCALE);
            ms.translate(0.0F, 0.0F, -SOAKER_MIDDLE);
            if (i == 0) {
                hoseEnd = ms.last().pose().transformPosition(new Vector3f(SOAKER_END));
            }
            Minecraft.getInstance().getItemRenderer().renderStatic(stowed.get(i), ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                    ms, buffer, player.level(), player.getId() + i);
            ms.popPose();
        }
        return hoseEnd;
    }

    private static Vec3 world(final Vector3f drawn, final Vec3 camera) {
        return new Vec3(drawn.x + camera.x, drawn.y + camera.y, drawn.z + camera.z);
    }
    //#endregion

    //#region // --- ROPE --- //
    private record Obstacle(Matrix4f toDrawn, Matrix4f fromDrawn, Vector3f min, Vector3f max) {
        private Obstacle(final Matrix4f toDrawn, final Vector3f min, final Vector3f max) {
            this(toDrawn, new Matrix4f(toDrawn).invert(), min, max);
        }

        private static Obstacle of(final PoseStack ms, final ModelPart part, final float x0, final float y0, final float z0,
                                   final float x1, final float y1, final float z1) {
            ms.pushPose();
            part.translateAndRotate(ms);
            final Matrix4f pose = new Matrix4f(ms.last().pose());
            ms.popPose();
            return new Obstacle(pose, new Vector3f(x0, y0, z0).mul(PIXEL), new Vector3f(x1, y1, z1).mul(PIXEL));
        }

        private Vec3 pushOut(final Vec3 point, final Vec3 camera) {
            final Vector3f local = this.fromDrawn.transformPosition(new Vector3f(
                    (float) (point.x - camera.x), (float) (point.y - camera.y), (float) (point.z - camera.z)));
            final float scale = new Vector3f(1.0F, 0.0F, 0.0F).length() / this.fromDrawn.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F)).length();
            final float pad = (float) CLEARANCE / scale;
            final float[] low = {this.min.x - pad, this.min.y - pad, this.min.z - pad};
            final float[] high = {this.max.x + pad, this.max.y + pad, this.max.z + pad};
            final float[] at = {local.x, local.y, local.z};
            int axis = -1;
            float best = Float.MAX_VALUE;
            float target = 0.0F;
            for (int i = 0; i < 3; i++) {
                if (at[i] <= low[i] || at[i] >= high[i]) {
                    return point;
                }
                final float toLow = at[i] - low[i];
                final float toHigh = high[i] - at[i];
                if (toLow < best) {
                    best = toLow;
                    axis = i;
                    target = low[i];
                }
                if (toHigh < best) {
                    best = toHigh;
                    axis = i;
                    target = high[i];
                }
            }
            at[axis] = target;
            final Vector3f drawn = this.toDrawn.transformPosition(new Vector3f(at[0], at[1], at[2]));
            return world(drawn, camera);
        }
    }

    private static final class Rope {
        private final Vec3[] points = new Vec3[POINTS];
        private final Vec3[] previous = new Vec3[POINTS];
        private long lastNanos;
        private Vec3 playerAt;

        private void step(final Vec3 start, final Vec3 hip, final Vec3 end, final Vec3 playerAt, final List<Obstacle> obstacles,
                          final Vec3 camera, final double groundY) {
            final long now = System.nanoTime();
            if (this.points[0] == null || now - this.lastNanos > STALE_NANOS || this.snagged()) {
                this.reset(start, hip, end);
            } else if (this.playerAt != null) {
                // * Carried along with the player
                final Vec3 moved = playerAt.subtract(this.playerAt);
                for (int i = 0; i < POINTS; i++) {
                    this.points[i] = this.points[i].add(moved);
                    this.previous[i] = this.previous[i].add(moved);
                }
            }
            this.playerAt = playerAt;
            final double dt = Math.min((now - this.lastNanos) / 1.0E9, MAX_STEP);
            this.lastNanos = now;

            final double keep = Math.pow(DAMPING, dt * 20.0);
            for (int i = 1; i < POINTS - 1; i++) {
                final Vec3 point = this.points[i];
                final Vec3 velocity = point.subtract(this.previous[i]).scale(keep);
                this.previous[i] = point;
                this.points[i] = point.add(velocity).add(0.0, -GRAVITY * dt * dt, 0.0);
            }
            this.points[0] = start;
            this.points[POINTS - 1] = end;

            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                this.collide(obstacles, camera, groundY);
                // * A hose can go slack but can't stretch
                for (int i = 0; i < POINTS - 1; i++) {
                    final Vec3 a = this.points[i];
                    final Vec3 b = this.points[i + 1];
                    final Vec3 gap = b.subtract(a);
                    final double length = gap.length();
                    if (length <= SEGMENT || length < 1.0E-6) {
                        continue;
                    }
                    final Vec3 fix = gap.scale((length - SEGMENT) / length);
                    if (i == 0) {
                        this.points[i + 1] = b.subtract(fix);
                    } else if (i + 1 == POINTS - 1) {
                        this.points[i] = a.add(fix);
                    } else {
                        this.points[i] = a.add(fix.scale(0.5));
                        this.points[i + 1] = b.subtract(fix.scale(0.5));
                    }
                }
            }
        }

        // * Keeps the middle of the hose out of the player
        private void collide(final List<Obstacle> obstacles, final Vec3 camera, final double groundY) {
            for (int i = 2; i < POINTS - 2; i++) {
                this.points[i] = this.points[i].add(pushOut(this.points[i], obstacles, camera, groundY));
            }
            for (int i = 1; i < POINTS - 2; i++) {
                for (final double t : SEGMENT_CHECKS) {
                    final Vec3 spot = this.points[i].lerp(this.points[i + 1], t);
                    final Vec3 push = pushOut(spot, obstacles, camera, groundY);
                    if (push.lengthSqr() == 0.0) {
                        continue;
                    }
                    final double total = (1.0 - t) * (1.0 - t) + t * t;
                    this.points[i] = this.points[i].add(push.scale((1.0 - t) / total));
                    this.points[i + 1] = this.points[i + 1].add(push.scale(t / total));
                }
            }
        }

        // * How far a spot has to move to be clear of the player and above the ground they stand on
        private static Vec3 pushOut(final Vec3 spot, final List<Obstacle> obstacles, final Vec3 camera, final double groundY) {
            Vec3 point = spot;
            for (final Obstacle obstacle : obstacles) {
                point = obstacle.pushOut(point, camera);
            }
            if (point.y < groundY + CLEARANCE) {
                point = new Vec3(point.x, groundY + CLEARANCE, point.z);
            }
            return point.subtract(spot);
        }

        private boolean snagged() {
            for (int i = 0; i < POINTS - 1; i++) {
                if (this.points[i].distanceTo(this.points[i + 1]) > SEGMENT * SNAG_STRETCH) {
                    return true;
                }
            }
            return false;
        }

        private void reset(final Vec3 start, final Vec3 hip, final Vec3 end) {
            final double first = start.distanceTo(hip);
            final double total = first + hip.distanceTo(end);
            for (int i = 0; i < POINTS; i++) {
                final double along = total * i / (POINTS - 1);
                final Vec3 point = along <= first
                        ? start.lerp(hip, first == 0.0 ? 0.0 : along / first)
                        : hip.lerp(end, (along - first) / (total - first));
                this.points[i] = point;
                this.previous[i] = point;
            }
            this.lastNanos = System.nanoTime();
        }
    }

    //#region // --- DRIPS --- //
    private static final float DRIP_CHANCE = 0.06F;
    private static final double DRIP_BELOW = 1.0 / 16.0;

    public static void drip(final Level level, final Player player, final InteractionHand hand, final FluidStack fluid) {
        final Rope[] ropes = ROPES.get(player);
        final Rope rope = ropes != null ? ropes[hand.ordinal()] : null;
        if (rope == null || rope.points[0] == null || fluid.isEmpty() || System.nanoTime() - rope.lastNanos > STALE_NANOS
                || level.random.nextFloat() >= DRIP_CHANCE) {
            return;
        }
        final int segment = level.random.nextInt(POINTS - 1);
        final Vec3 spot = rope.points[segment].lerp(rope.points[segment + 1], level.random.nextDouble());
        level.addParticle(FluidFX.getDrippingParticle(fluid), spot.x, spot.y - DRIP_BELOW, spot.z, 0.0, 0.0, 0.0);
    }
    //#endregion

    public static void clientReset() {
        SOAKER_ENDS.clear();
        ROPES.clear();
    }
    //#endregion

    //#region // --- DRAWING --- //
    private static void drawHose(final PoseStack ms, final MultiBufferSource buffer, final int light, final List<Vector3f> rope) {
        final List<Vector3f> points = tucked(rope);
        final VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());
        final SuperByteBuffer segment = CachedBuffers.partialFacing(HosePartialModels.BACKTANK_HOSE, AllBlocks.ROPE.getDefaultState(), Direction.NORTH);
        final SuperByteBuffer knot = CachedBuffers.partialFacing(HosePartialModels.BACKTANK_HOSE_KNOT, AllBlocks.ROPE.getDefaultState(), Direction.NORTH);
        final SuperByteBuffer band = CachedBuffers.partialFacing(HosePartialModels.BACKTANK_HOSE_BAND, AllBlocks.ROPE.getDefaultState(), Direction.NORTH);

        Quaternionf turn = null;
        for (int i = 1; i < points.size(); i++) {
            final Vector3f from = points.get(i - 1);
            final Vector3f along = new Vector3f(points.get(i)).sub(from);
            final float length = along.length();
            if (length < 1.0E-5F) {
                continue;
            }
            along.div(length);
            turn = facing(along, turn == null ? 0.0F : -0.15F);

            ms.pushPose();
            ms.translate(from.x, from.y, from.z);
            ms.mulPose(turn);
            ms.translate(-0.5F, -0.5F, -0.5F);
            if (i > 1) {
                (i % 2 == 0 ? band : knot).light(light).renderInto(ms, consumer);
            }
            ms.translate(0.0F, 0.5F, 0.0F);
            ms.scale(1.0F, length, 1.0F);
            segment.light(light).renderInto(ms, consumer);
            ms.popPose();
        }
    }

    private static List<Vector3f> tucked(final List<Vector3f> rope) {
        final List<Vector3f> points = new ArrayList<>(rope);
        if (points.size() < 2) {
            return points;
        }
        final Vector3f first = new Vector3f(points.get(0)).sub(points.get(1));
        if (first.lengthSquared() > 1.0E-10F) {
            points.set(0, first.normalize().mul(TUCK).add(points.get(0)));
        }
        return points;
    }

    private static Quaternionf facing(final Vector3f along, final float flipBelow) {
        if (along.y < flipBelow) {
            return new Quaternionf().rotationTo(0.0F, -1.0F, 0.0F, along.x, along.y, along.z).rotateZ((float) Math.PI);
        }
        return new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, along.x, along.y, along.z);
    }
    //#endregion

    private BacktankHoseRenderer() {
    }
}
