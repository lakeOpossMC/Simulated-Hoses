package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopePoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ZiplineClientManager;
import dev.simulated_team.simulated.util.SimMathUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Objects;

// --- DRAWS A HOSE ALONG ITS PHYSICS STRAND --- //
public final class HoseStrandRenderer {
    public record HoseRenderPoint(Quaternionf orientation, Vector3d position) {
    }

    // * Outline size used when hovering a hose to ride or cut it
    private static final float OUTLINE_RADIUS = 2.25f / 16.0f;

    public static void render(final SmartBlockEntity be, final RopeStrandHolderBehavior holder, final float partialTick, final PoseStack ps, final MultiBufferSource buffer) {
        render(be, holder, partialTick, ps, buffer, HosePartialModels.HOSE, HosePartialModels.HOSE_KNOT);
    }

    public static void render(final SmartBlockEntity be, final RopeStrandHolderBehavior holder, final float partialTick, final PoseStack ps, final MultiBufferSource buffer,
                              final PartialModel segment, final PartialModel joint) {
        final Level level = be.getLevel();
        if (level == null) {
            return;
        }

        final BlockPos ownerPos = be.getBlockPos();

        final SuperByteBuffer middle = CachedBuffers.partialFacing(segment, AllBlocks.ROPE.getDefaultState(), Direction.NORTH);
        final SuperByteBuffer knot = CachedBuffers.partialFacing(joint, AllBlocks.ROPE.getDefaultState(), Direction.NORTH);
        final VertexConsumer vb = buffer.getBuffer(RenderType.cutout());

        final SubLevel subLevel = Sable.HELPER.getContaining(be);
        Pose3dc containingPose = null;
        if (subLevel instanceof final ClientSubLevel clientSubLevel) {
            containingPose = clientSubLevel.renderPose();
        }

        final ClientRopeStrand strand = holder.getClientStrand();
        if (!holder.ownsRope() || strand == null) {
            return;
        }

        final List<ClientRopePoint> points = strand.getPoints();
        if (points.size() <= 1) {
            return;
        }

        final ObjectArrayList<HoseRenderPoint> renderPoints = buildRenderPoints(partialTick, points);
        if (renderPoints.isEmpty()) {
            return;
        }

        ps.pushPose();
        for (int i = 1; i < renderPoints.size(); i++) {
            final HoseRenderPoint renderPoint0 = renderPoints.get(i - 1);
            final HoseRenderPoint renderPoint1 = renderPoints.get(i);
            final Vector3d globalRenderPos = new Vector3d(renderPoint0.position());
            final Vector3d renderPos = renderPoint0.position();
            final Quaternionf orientation = renderPoint0.orientation();

            final double length = renderPoint1.position().distance(renderPoint0.position());

            if (containingPose != null) {
                containingPose.transformPositionInverse(renderPos);
                orientation.premul(new Quaternionf(containingPose.orientation()).conjugate());
            }

            ps.pushPose();
            ps.translate(renderPos.x - ownerPos.getX(), renderPos.y - ownerPos.getY(), renderPos.z - ownerPos.getZ());
            ps.mulPose(orientation);
            ps.translate(-0.5, -0.5, -0.5);
            final BlockPos pos = BlockPos.containing(globalRenderPos.x, globalRenderPos.y, globalRenderPos.z);
            final int worldLight = LevelRenderer.getLightColor(level, pos);

            if (i > 1) {
                knot.light(worldLight).renderInto(ps, vb);
            }
            ps.translate(0.0, 0.5, 0.0);
            ps.scale(1.0f, (float) length, 1.0f);

            middle.light(worldLight).renderInto(ps, vb);
            ps.popPose();
        }
        ps.popPose();

        final HoseRenderPoint last = renderPoints.getLast();
        if (containingPose != null) {
            containingPose.transformPositionInverse(last.position());
            last.orientation().premul(new Quaternionf(containingPose.orientation()).conjugate());
        }

        // * Same hover outline as ropes when aiming at it to zipline or cut
        if (Objects.equals(ZiplineClientManager.hoveringRope, strand.getUuid())) {
            renderOutline(ps, buffer, renderPoints, ownerPos);
        }
    }

    private static void renderOutline(final PoseStack ps, final MultiBufferSource buffer, final ObjectArrayList<HoseRenderPoint> renderPoints, final BlockPos ownerPos) {
        final float rad = OUTLINE_RADIUS;
        final Vector3d previousCorner = new Vector3d();
        final Vector3d currentCorner = new Vector3d();
        final Vector3d cornerDiff = new Vector3d();

        final Vector3d[] corners = {
                new Vector3d(-rad, 0, -rad),
                new Vector3d(-rad, 0, rad),
                new Vector3d(rad, 0, rad),
                new Vector3d(rad, 0, -rad),
        };

        final VertexConsumer linesVB = buffer.getBuffer(RenderType.lines());
        final Matrix4f pose = ps.last().pose();

        for (int i = 0; i < renderPoints.size() + 1; i++) {
            final HoseRenderPoint renderPoint0 = renderPoints.get(Math.max(0, i - 1));
            final HoseRenderPoint renderPoint1 = renderPoints.get(Math.min(renderPoints.size() - 1, i));
            final boolean start = i == 0;
            final boolean end = i == renderPoints.size();

            for (final Vector3d corner : corners) {
                renderPoint0.orientation().transform(start ? corner.rotateY(Math.PI / 2.0, previousCorner) : corner, previousCorner)
                        .add(renderPoint0.position())
                        .sub(ownerPos.getX(), ownerPos.getY(), ownerPos.getZ());

                renderPoint1.orientation().transform(end ? corner.rotateY(Math.PI / 2.0, corner) : corner, currentCorner)
                        .add(renderPoint1.position())
                        .sub(ownerPos.getX(), ownerPos.getY(), ownerPos.getZ());

                currentCorner.sub(previousCorner, cornerDiff).normalize();

                linesVB.addVertex(pose, (float) previousCorner.x, (float) previousCorner.y, (float) previousCorner.z)
                        .setColor(0f, 0f, 0f, .4f)
                        .setNormal(ps.last(), (float) cornerDiff.x, (float) cornerDiff.y, (float) cornerDiff.z);

                linesVB.addVertex(pose, (float) currentCorner.x, (float) currentCorner.y, (float) currentCorner.z)
                        .setColor(0f, 0f, 0f, .4f)
                        .setNormal(ps.last(), (float) cornerDiff.x, (float) cornerDiff.y, (float) cornerDiff.z);
            }
        }
    }

    private static @NotNull ObjectArrayList<HoseRenderPoint> buildRenderPoints(final float partialTick, final List<ClientRopePoint> inputPoints) {
        final ObjectArrayList<HoseRenderPoint> renderPoints = new ObjectArrayList<>();
        final ObjectArrayList<ClientRopePoint> points = new ObjectArrayList<>(inputPoints);

        while (points.size() >= 2 && points.getFirst().position().distanceSquared(points.get(1).position()) < 1e-3) {
            points.removeFirst();
        }

        if (points.size() <= 1) {
            return new ObjectArrayList<>();
        }

        final Vector3dc pointZeroPosition = points.get(0).renderPos(partialTick, new Vector3d());
        final Vector3dc pointOnePosition = points.get(1).renderPos(partialTick, new Vector3d());

        final Vector3d normal = pointOnePosition.sub(pointZeroPosition, new Vector3d()).normalize();

        final Quaternionf runningRotation;
        if (normal.dot(OrientedBoundingBox3d.UP) < 0) {
            runningRotation = SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, -1, 0), normal);
            runningRotation.rotateZ((float) Math.PI);
        } else {
            runningRotation = SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, 1, 0), normal);
        }

        renderPoints.add(new HoseRenderPoint(new Quaternionf(runningRotation), new Vector3d(pointZeroPosition)));

        final Vector3d runningNormal = new Vector3d();
        final Vector3d bPos = new Vector3d();
        final Vector3d aPos = new Vector3d();

        for (int i = 2; i < points.size(); i++) {
            final ClientRopePoint pointA = points.get(i - 1);
            final ClientRopePoint pointB = points.get(i);

            runningNormal.set(pointB.renderPos(partialTick, bPos))
                    .sub(pointA.renderPos(partialTick, aPos))
                    .normalize();

            if (runningNormal.dot(OrientedBoundingBox3d.UP) < -0.15) {
                runningRotation.set(SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, -1, 0), runningNormal));
                runningRotation.rotateZ((float) Math.PI);
            } else {
                runningRotation.set(SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, 1, 0), runningNormal));
            }

            renderPoints.add(new HoseRenderPoint(new Quaternionf(runningRotation), pointA.renderPos(partialTick, new Vector3d())));
            normal.set(runningNormal);
        }

        renderPoints.add(new HoseRenderPoint(new Quaternionf(runningRotation), points.getLast().renderPos(partialTick, new Vector3d())));
        return renderPoints;
    }

    private HoseStrandRenderer() {
    }
}
