package edn.lakeopossmc.simulatedhoses.hose;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Optional;

// --- THE PATH A PRESSURE NOZZLE'S JET TAKES --- //
public final class NozzleJet {
    public static final double GRAVITY = 0.04;
    public static final float DRAG = 0.98F;

    private static final double MIN_SPEED = 0.35;
    private static final double MAX_SPEED = 1.25;
    private static final int MIN_TICKS = 12;
    private static final int MAX_TICKS = 45;
    private static final double ENTITY_MARGIN = 0.25;

    public static double speed(final double fraction) {
        return Mth.lerp(fraction, MIN_SPEED, MAX_SPEED);
    }

    public static int lifetime(final double fraction) {
        return Mth.floor(Mth.lerp(fraction, MIN_TICKS, MAX_TICKS));
    }

    //#region // --- AIM --- //
    public record Aim(Vec3 start, Vec3 direction, Vec3 side1, Vec3 side2) {
    }

    public static Aim aim(final Level level, final BlockPos pos, final Direction facing, final double faceOffset) {
        final Vector3d normal = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        final Vector3d start = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5).fma(faceOffset, normal);
        final Vector3d side1 = new Vector3d(Math.abs(normal.y) > 0.5 ? 1 : 0, Math.abs(normal.y) > 0.5 ? 0 : 1, 0);
        final Vector3d side2 = normal.cross(side1, new Vector3d());
        final SubLevel subLevel = Sable.HELPER.getContaining(level, new ChunkPos(pos));
        if (subLevel != null) {
            subLevel.logicalPose().transformPosition(start);
            subLevel.logicalPose().orientation().transform(normal);
            subLevel.logicalPose().orientation().transform(side1);
            subLevel.logicalPose().orientation().transform(side2);
        }
        return new Aim(vec(start), vec(normal), vec(side1), vec(side2));
    }

    // * A jet from a point in any direction
    public static Aim aimAlong(final Vec3 start, final Vec3 direction) {
        final Vec3 normal = direction.normalize();
        final Vec3 up = Math.abs(normal.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        final Vec3 side1 = normal.cross(up).normalize();
        final Vec3 side2 = normal.cross(side1);
        return new Aim(start, normal, side1, side2);
    }

    private static Vec3 vec(final Vector3d v) {
        return new Vec3(v.x, v.y, v.z);
    }
    //#endregion

    //#region // --- WHERE IT LANDS --- //
    public record Impact(Vec3 point, Vec3 velocity, @Nullable BlockHitResult block, @Nullable Entity entity) {
    }

    @Nullable
    public static Impact trace(final Level level, final Aim aim, final double fraction, @Nullable final Entity ignored) {
        Vec3 pos = aim.start();
        Vec3 velocity = aim.direction().scale(speed(fraction));
        final int ticks = lifetime(fraction);
        for (int i = 0; i < ticks; i++) {
            velocity = velocity.subtract(0.0, GRAVITY, 0.0);
            final Vec3 next = pos.add(velocity);
            final BlockHitResult block = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
            // * Ignore starting inside something
            final boolean hitBlock = block.getType() != HitResult.Type.MISS && !block.isInside();
            final Vec3 end = hitBlock ? block.getLocation() : next;

            final Impact entityHit = hitEntity(level, pos, end, velocity, ignored);
            if (entityHit != null) {
                return entityHit;
            }
            if (hitBlock) {
                return new Impact(block.getLocation(), velocity, block, null);
            }
            pos = next;
            velocity = velocity.scale(DRAG);
        }
        return null;
    }

    @Nullable
    private static Impact hitEntity(final Level level, final Vec3 from, final Vec3 to, final Vec3 velocity, @Nullable final Entity ignored) {
        Impact closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (final Entity entity : level.getEntities(ignored, new AABB(from, to).inflate(1.0), e -> !e.isSpectator() && e.isPickable())) {
            final Optional<Vec3> hit = entity.getBoundingBox().inflate(ENTITY_MARGIN).clip(from, to);
            if (hit.isEmpty()) {
                continue;
            }
            final double distance = from.distanceToSqr(hit.get());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = new Impact(hit.get(), velocity, null, entity);
            }
        }
        return closest;
    }
    //#endregion

    private NozzleJet() {
    }
}
