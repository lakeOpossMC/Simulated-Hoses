package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

// --- GOO DRAWN AFTER EVERYTHING IT COVERS --- //
public final class PlantFoodLateBuffers implements MultiBufferSource {
    private static final PlantFoodLateBuffers INSTANCE = new PlantFoodLateBuffers();

    private final Map<RenderType, BufferBuilder> building = new LinkedHashMap<>();
    // * Kept between frames so the memory is reused
    private final Map<RenderType, ByteBufferBuilder> memory = new HashMap<>();
    private boolean inWorld;

    // * Where goo over worn things should go right now
    public static MultiBufferSource forNow(final MultiBufferSource buffers) {
        return INSTANCE.inWorld ? INSTANCE : buffers;
    }

    public static void onRenderStage(final RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            INSTANCE.inWorld = true;
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            INSTANCE.inWorld = false;
            INSTANCE.draw();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            // * Safety net, never carry goo over into the next frame
            INSTANCE.inWorld = false;
            INSTANCE.draw();
        }
    }

    @Override
    public VertexConsumer getBuffer(final RenderType type) {
        return this.building.computeIfAbsent(type, found -> new BufferBuilder(
                this.memory.computeIfAbsent(found, unused -> new ByteBufferBuilder(found.bufferSize())), found.mode(), found.format()));
    }

    private void draw() {
        if (this.building.isEmpty()) {
            return;
        }
        for (final Map.Entry<RenderType, BufferBuilder> entry : this.building.entrySet()) {
            final MeshData mesh = entry.getValue().build();
            if (mesh == null) {
                continue;
            }
            final RenderType type = entry.getKey();
            if (type.sortOnUpload()) {
                mesh.sortQuads(this.memory.get(type), RenderSystem.getVertexSorting());
            }
            type.draw(mesh);
        }
        this.building.clear();
    }

    private PlantFoodLateBuffers() {
    }
}
