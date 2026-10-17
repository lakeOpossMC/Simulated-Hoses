package edn.lakeopossmc.simulatedhoses.hose;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// --- PIPE PRESSURE AROUND A HOSE PORT --- //
public final class HosePressure {

    @Nullable
    public static PipeConnection neighbourConnection(final Level level, final BlockPos pos, final Direction port) {
        final BlockPos neighbour = pos.relative(port);
        if (!level.isLoaded(neighbour)) {
            return null;
        }
        final FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, neighbour);
        if (pipe == null || !pipe.canHaveFlowToward(level.getBlockState(neighbour), port.getOpposite())) {
            return null;
        }
        return pipe.getConnection(port.getOpposite());
    }

    public static boolean isAgainstPipe(final Level level, final BlockPos pos, final Direction port) {
        final BlockPos neighbour = pos.relative(port);
        if (!level.isLoaded(neighbour)) {
            return false;
        }
        final FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, neighbour);
        return pipe != null && pipe.canHaveFlowToward(level.getBlockState(neighbour), port.getOpposite());
    }

    public static float pressure(@Nullable final PipeConnection connection, final boolean inbound) {
        if (connection == null) {
            return 0;
        }
        final Couple<Float> pressure = connection.getPressure();
        return pressure == null ? 0 : pressure.get(inbound);
    }

    public record Applied(BlockPos pos, Direction face, boolean inbound, float amount) {
    }

    public static List<Applied> distribute(final Level level, final BlockPos origin, final Direction side, final boolean pull, final float pressure, final boolean resetNetworks) {
        final List<Applied> applied = new ArrayList<>();
        if (pressure <= 0) {
            return applied;
        }
        final BlockFace start = new BlockFace(origin, side);
        final Set<BlockFace> targets = new HashSet<>();
        final Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph = new HashMap<>();

        if (!pull && resetNetworks) {
            FluidPropagator.resetAffectedFluidNetworks(level, origin, side.getOpposite());
        }

        if (!hasReachedValidEndpoint(level, start, pull)) {
            graphEntry(pipeGraph, origin, 0).put(side, pull);
            graphEntry(pipeGraph, start.getConnectedPos(), 1).put(side.getOpposite(), !pull);

            final List<Pair<Integer, BlockPos>> frontier = new ArrayList<>();
            final Set<BlockPos> visited = new HashSet<>();
            final int maxDistance = FluidPropagator.getPumpRange();
            frontier.add(Pair.of(1, start.getConnectedPos()));

            while (!frontier.isEmpty()) {
                final Pair<Integer, BlockPos> entry = frontier.remove(0);
                final int distance = entry.getFirst();
                final BlockPos currentPos = entry.getSecond();
                if (!level.isLoaded(currentPos) || !visited.add(currentPos)) {
                    continue;
                }
                final BlockState currentState = level.getBlockState(currentPos);
                final FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, currentPos);
                if (pipe == null) {
                    continue;
                }
                for (final Direction face : FluidPropagator.getPipeConnections(currentState, pipe)) {
                    final BlockFace blockFace = new BlockFace(currentPos, face);
                    final BlockPos connectedPos = blockFace.getConnectedPos();
                    if (!level.isLoaded(connectedPos) || blockFace.isEquivalent(start)) {
                        continue;
                    }
                    if (hasReachedValidEndpoint(level, blockFace, pull)) {
                        graphEntry(pipeGraph, currentPos, distance).put(face, pull);
                        targets.add(blockFace);
                        continue;
                    }
                    final FluidTransportBehaviour next = FluidPropagator.getPipe(level, connectedPos);
                    if (next == null || PumpBlock.isPump(level.getBlockState(connectedPos)) || visited.contains(connectedPos)) {
                        continue;
                    }
                    if (distance + 1 >= maxDistance) {
                        graphEntry(pipeGraph, currentPos, distance).put(face, pull);
                        targets.add(blockFace);
                        continue;
                    }
                    graphEntry(pipeGraph, currentPos, distance).put(face, pull);
                    graphEntry(pipeGraph, connectedPos, distance + 1).put(face.getOpposite(), !pull);
                    frontier.add(Pair.of(distance + 1, connectedPos));
                }
            }
        }

        final Map<Integer, Set<BlockFace>> validFaces = new HashMap<>();
        searchForEndpointRecursively(pipeGraph, targets, validFaces, new BlockFace(start.getPos(), start.getOppositeFace()), pull);

        for (final Set<BlockFace> faces : validFaces.values()) {
            final int parallelBranches = Math.max(1, faces.size() - 1);
            for (final BlockFace face : faces) {
                final BlockPos pipePos = face.getPos();
                if (pipePos.equals(origin)) {
                    continue;
                }
                final Boolean inbound = pipeGraph.get(pipePos).getSecond().get(face.getFace());
                final FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pipePos);
                if (pipe != null && inbound != null) {
                    final float amount = pressure / parallelBranches;
                    pipe.addPressure(face.getFace(), inbound, amount);
                    applied.add(new Applied(pipePos, face.getFace(), inbound, amount));
                }
            }
        }
        return applied;
    }

    public static void remove(final Level level, final List<Applied> applied) {
        for (final Applied entry : applied) {
            if (!level.isLoaded(entry.pos())) {
                continue;
            }
            final FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, entry.pos());
            if (pipe == null) {
                continue;
            }
            final PipeConnection connection = pipe.getConnection(entry.face());
            final float left = pressure(connection, entry.inbound());
            final float take = Math.min(entry.amount(), left);
            if (take > 0) {
                pipe.addPressure(entry.face(), entry.inbound(), -take);
            }
        }
    }

    private static Map<Direction, Boolean> graphEntry(final Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> graph, final BlockPos pos, final int distance) {
        return graph.computeIfAbsent(pos, $ -> Pair.of(distance, new IdentityHashMap<>())).getSecond();
    }

    private static boolean searchForEndpointRecursively(final Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph, final Set<BlockFace> targets,
                                                        final Map<Integer, Set<BlockFace>> validFaces, final BlockFace currentFace, final boolean pull) {
        final BlockPos currentPos = currentFace.getPos();
        final Pair<Integer, Map<Direction, Boolean>> pair = pipeGraph.get(currentPos);
        if (pair == null) {
            return false;
        }
        final int distance = pair.getFirst();
        boolean atLeastOneBranchSuccessful = false;
        for (final Direction nextFacing : Iterate.directions) {
            if (nextFacing == currentFace.getFace() || !pair.getSecond().containsKey(nextFacing)) {
                continue;
            }
            final BlockFace localTarget = new BlockFace(currentPos, nextFacing);
            if (targets.contains(localTarget)) {
                validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(localTarget);
                atLeastOneBranchSuccessful = true;
                continue;
            }
            if (pair.getSecond().get(nextFacing) != pull
                    || !searchForEndpointRecursively(pipeGraph, targets, validFaces, new BlockFace(currentPos.relative(nextFacing), nextFacing.getOpposite()), pull)) {
                continue;
            }
            validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(localTarget);
            atLeastOneBranchSuccessful = true;
        }
        if (atLeastOneBranchSuccessful) {
            validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(currentFace);
        }
        return atLeastOneBranchSuccessful;
    }

    private static boolean hasReachedValidEndpoint(final Level level, final BlockFace blockFace, final boolean pull) {
        final BlockPos connectedPos = blockFace.getConnectedPos();
        final BlockState connectedState = level.getBlockState(connectedPos);
        final BlockEntity blockEntity = level.getBlockEntity(connectedPos);
        final Direction face = blockFace.getFace();

        if (PumpBlock.isPump(connectedState) && connectedState.getValue(PumpBlock.FACING).getAxis() == face.getAxis()
                && blockEntity instanceof final PumpBlockEntity pump) {
            final boolean front = blockFace.getOppositeFace() == connectedState.getValue(PumpBlock.FACING);
            return pump.isPullingOnSide(front) != pull;
        }
        final FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, connectedPos);
        if (pipe != null && pipe.canHaveFlowToward(connectedState, blockFace.getOppositeFace())) {
            return false;
        }
        if (blockEntity != null && level.getCapability(Capabilities.FluidHandler.BLOCK, connectedPos, face.getOpposite()) != null) {
            return true;
        }
        return FluidPropagator.isOpenEnd(level, blockFace.getPos(), face);
    }

    private HosePressure() {
    }
}
