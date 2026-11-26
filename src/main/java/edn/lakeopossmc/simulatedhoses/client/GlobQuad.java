package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

// --- A CAMERA-FACING QUAD THAT STRETCHES --- //
final class GlobQuad {
    private static final float[][] OFFSETS = {{1.0F, -1.0F}, {1.0F, 1.0F}, {-1.0F, 1.0F}, {-1.0F, -1.0F}};

    // * Corners as offsets from the particle. Full stretch once it crosses the screen at fullStretchSpeed blocks a tick
    static Vector3f[] corners(final Camera camera, final double vx, final double vy, final double vz, final float size, final float roll,
                              final float stretch, final float fullStretchSpeed, final float thinning) {
        final Vector3f up = camera.getUpVector();
        final Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        final float screenX = (float) (vx * right.x() + vy * right.y() + vz * right.z());
        final float screenY = (float) (vx * up.x() + vy * up.y() + vz * up.z());
        final float screenSpeed = Mth.sqrt(screenX * screenX + screenY * screenY);
        final boolean moving = screenSpeed > 1.0E-4F;
        final float dirX = moving ? screenX / screenSpeed : 1.0F;
        final float dirY = moving ? screenY / screenSpeed : 0.0F;
        final float amount = moving ? stretch * Math.min(1.0F, screenSpeed / fullStretchSpeed) : 0.0F;
        final float length = size * (1.0F + amount);
        final float width = size / (1.0F + thinning * amount);
        final float cos = Mth.cos(roll);
        final float sin = Mth.sin(roll);
        final Vector3f[] corners = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            final float spunX = OFFSETS[i][0] * cos - OFFSETS[i][1] * sin;
            final float spunY = OFFSETS[i][0] * sin + OFFSETS[i][1] * cos;
            final float along = (spunX * dirX + spunY * dirY) * length;
            final float across = (spunY * dirX - spunX * dirY) * width;
            final float offsetX = dirX * along - dirY * across;
            final float offsetY = dirY * along + dirX * across;
            corners[i] = new Vector3f(right).mul(offsetX).add(up.x() * offsetY, up.y() * offsetY, up.z() * offsetY);
        }
        return corners;
    }

    static void draw(final VertexConsumer buffer, final TextureAtlasSprite sprite, final Vector3f[] corners, final float x, final float y,
                     final float z, final float red, final float green, final float blue, final float alpha, final int light) {
        final float[] us = {sprite.getU1(), sprite.getU1(), sprite.getU0(), sprite.getU0()};
        final float[] vs = {sprite.getV1(), sprite.getV0(), sprite.getV0(), sprite.getV1()};
        for (int i = 0; i < 4; i++) {
            buffer.addVertex(x + corners[i].x(), y + corners[i].y(), z + corners[i].z())
                    .setUv(us[i], vs[i])
                    .setColor(red, green, blue, alpha)
                    .setLight(light);
        }
    }

    private GlobQuad() {
    }
}
