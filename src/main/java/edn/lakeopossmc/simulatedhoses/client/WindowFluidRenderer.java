package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.EnumSet;
import java.util.Set;

// --- FLUID BEHIND A WINDOW --- //
public final class WindowFluidRenderer {

    public static void render(final FluidStack fluid, final int capacity, final float x0, final float y0, final float z0,
                              final float x1, final float y1, final float z1, final float minHeight, final float tile,
                              final PoseStack ms, final MultiBufferSource buffer, final int light) {
        render(fluid, capacity, x0, y0, z0, x1, y1, z1, minHeight, tile, EnumSet.allOf(Direction.class), ms, buffer, light);
    }

    public static void render(final FluidStack fluid, final int capacity, final float x0, final float y0, final float z0,
                              final float x1, final float y1, final float z1, final float minHeight, final float tile,
                              final Set<Direction> faces, final PoseStack ms, final MultiBufferSource buffer, final int light) {
        if (fluid.isEmpty() || capacity <= 0) {
            return;
        }
        final IClientFluidTypeExtensions client = IClientFluidTypeExtensions.of(fluid.getFluid());
        final TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(client.getStillTexture(fluid));
        final int color = client.getTintColor(fluid);
        final int luminosity = Math.max((light >> 4) & 0xF, fluid.getFluid().getFluidType().getLightLevel(fluid));
        final int fluidLight = (light & 0xF00000) | luminosity << 4;

        final float fill = Mth.clamp(fluid.getAmount() / (float) capacity, 0.0F, 1.0F);
        final float height = minHeight + fill * (y1 - y0 - minHeight);
        final boolean fromTop = fluid.getFluid().getFluidType().isLighterThanAir();
        final float bottom = fromTop ? y1 - height : y0;
        final float top = fromTop ? y1 : y0 + height;

        final Box box = new Box(ms.last(), buffer.getBuffer(Sheets.translucentCullBlockSheet()), sprite, color, fluidLight, tile);
        for (final Direction face : faces) {
            box.face(face, x0, bottom, z0, x1, top, z1);
        }
    }

    //#region // --- this.tileD FACES --- //
    private static final class Box {
        private final PoseStack.Pose pose;
        private final VertexConsumer consumer;
        private final TextureAtlasSprite sprite;
        private final int color;
        private final int light;
        private final float tile;

        private Box(final PoseStack.Pose pose, final VertexConsumer consumer, final TextureAtlasSprite sprite, final int color, final int light,
                    final float tile) {
            this.tile = tile;
            this.pose = pose;
            this.consumer = consumer;
            this.sprite = sprite;
            this.color = color;
            this.light = light;
        }

        private void face(final Direction face, final float x0, final float y0, final float z0, final float x1, final float y1, final float z1) {
            final boolean horizontal = face.getAxis() == Direction.Axis.Y;
            final float a0 = face.getAxis() == Direction.Axis.X ? z0 : x0;
            final float a1 = face.getAxis() == Direction.Axis.X ? z1 : x1;
            final float b0 = horizontal ? z0 : y0;
            final float b1 = horizontal ? z1 : y1;
            for (float a = a0; a < a1; a = this.cellEnd(a, a1)) {
                final float aEnd = this.cellEnd(a, a1);
                for (float b = b0; b < b1; b = this.cellEnd(b, b1)) {
                    final float bEnd = this.cellEnd(b, b1);
                    this.piece(face, a, aEnd, b, bEnd, x0, y0, z0, x1, y1, z1);
                }
            }
        }

        private float cellEnd(final float from, final float limit) {
            return Math.min((Mth.floor(from / this.tile) + 1) * this.tile, limit);
        }

        private void piece(final Direction face, final float a0, final float a1, final float b0, final float b1,
                           final float x0, final float y0, final float z0, final float x1, final float y1, final float z1) {
            final float cellA = Mth.floor(a0 / this.tile) * this.tile;
            final float cellB = Mth.floor(b0 / this.tile) * this.tile;
            final float u0 = this.sprite.getU((a0 - cellA) / this.tile);
            final float u1 = this.sprite.getU((a1 - cellA) / this.tile);
            final boolean side = face.getAxis() != Direction.Axis.Y;
            final float v0 = this.sprite.getV(side ? 1.0F - (b0 - cellB) / this.tile : (b0 - cellB) / this.tile);
            final float v1 = this.sprite.getV(side ? 1.0F - (b1 - cellB) / this.tile : (b1 - cellB) / this.tile);

            switch (face) {
                case UP -> {
                    this.vertex(face, a0, y1, b0, u0, v0);
                    this.vertex(face, a0, y1, b1, u0, v1);
                    this.vertex(face, a1, y1, b1, u1, v1);
                    this.vertex(face, a1, y1, b0, u1, v0);
                }
                case DOWN -> {
                    this.vertex(face, a0, y0, b1, u0, v1);
                    this.vertex(face, a0, y0, b0, u0, v0);
                    this.vertex(face, a1, y0, b0, u1, v0);
                    this.vertex(face, a1, y0, b1, u1, v1);
                }
                case NORTH -> {
                    this.vertex(face, a1, b1, z0, u1, v1);
                    this.vertex(face, a1, b0, z0, u1, v0);
                    this.vertex(face, a0, b0, z0, u0, v0);
                    this.vertex(face, a0, b1, z0, u0, v1);
                }
                case SOUTH -> {
                    this.vertex(face, a0, b1, z1, u0, v1);
                    this.vertex(face, a0, b0, z1, u0, v0);
                    this.vertex(face, a1, b0, z1, u1, v0);
                    this.vertex(face, a1, b1, z1, u1, v1);
                }
                case WEST -> {
                    this.vertex(face, x0, b1, a0, u0, v1);
                    this.vertex(face, x0, b0, a0, u0, v0);
                    this.vertex(face, x0, b0, a1, u1, v0);
                    this.vertex(face, x0, b1, a1, u1, v1);
                }
                case EAST -> {
                    this.vertex(face, x1, b1, a1, u1, v1);
                    this.vertex(face, x1, b0, a1, u1, v0);
                    this.vertex(face, x1, b0, a0, u0, v0);
                    this.vertex(face, x1, b1, a0, u0, v1);
                }
            }
        }

        private void vertex(final Direction face, final float x, final float y, final float z, final float u, final float v) {
            this.consumer.addVertex(this.pose, x / 16.0F, y / 16.0F, z / 16.0F)
                    .setColor(this.color)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(this.light)
                    .setNormal(this.pose, face.getStepX(), face.getStepY(), face.getStepZ());
        }
    }
    //#endregion

    private WindowFluidRenderer() {
    }
}
