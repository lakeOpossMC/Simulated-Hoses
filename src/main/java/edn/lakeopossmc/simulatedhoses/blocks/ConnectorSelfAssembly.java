package edn.lakeopossmc.simulatedhoses.blocks;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

// --- CONNECTOR SELF ASSEMBLY --- //
public final class ConnectorSelfAssembly {
    private ConnectorSelfAssembly() {
    }

    // * Waits until the placement has finished before moving
    public static void queue(final ServerLevel level, final BlockPos pos) {
        final MinecraftServer server = level.getServer();
        final BlockPos placed = pos.immutable();
        server.tell(new TickTask(server.getTickCount() + 1, () -> assemble(level, placed)));
    }

    private static void assemble(final ServerLevel level, final BlockPos pos) {
        // * Broken, replaced or unloaded since it was placed
        if (!level.isLoaded(pos)) {
            return;
        }
        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HoseConnectorBlock)) {
            return;
        }

        // * Only the connector
        final List<BlockPos> blocks = List.of(pos);
        SubLevelAssemblyHelper.assembleBlocks(level, pos, blocks, BoundingBox3i.from(blocks));
    }
}
