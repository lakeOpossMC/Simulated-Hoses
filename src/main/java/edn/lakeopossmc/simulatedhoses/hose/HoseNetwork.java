package edn.lakeopossmc.simulatedhoses.hose;

import com.simibubi.create.content.fluids.FluidPropagator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

// --- FINDS WHERE FLUID GOES WHEN IT ENTERS A HOSE PORT --- //
public final class HoseNetwork {
    private static final int MAX_CHAIN = 16;

    @Nullable
    public static HoseEndpoint findFarEnd(final HoseEndpoint start) {
        if (hosePartner(start) != null && start.getCoupledEndpoint() != null) {
            return null;
        }

        final Set<HoseEndpoint> visited = new HashSet<>();
        visited.add(start);
        HoseEndpoint previous = null;
        HoseEndpoint current = start;

        for (int i = 0; i < MAX_CHAIN; i++) {
            final HoseEndpoint next = step(current, previous);
            if (next == null) {
                break;
            }
            if (!visited.add(next)) {
                return null;
            }
            previous = current;
            current = next;
        }
        return current == start ? null : current;
    }

    @Nullable
    private static HoseEndpoint step(final HoseEndpoint current, @Nullable final HoseEndpoint previous) {
        final HoseEndpoint hose = hosePartner(current);
        if (hose != null && hose != previous) {
            return hose;
        }
        final HoseEndpoint coupled = current.getCoupledEndpoint();
        if (coupled != null && coupled != previous) {
            return coupled;
        }
        return null;
    }

    @Nullable
    private static HoseEndpoint hosePartner(final HoseEndpoint endpoint) {
        final HoseHolderBehavior holder = endpoint.getHoseHolder();
        return holder != null && holder.getPartner() instanceof final HoseEndpoint partner ? partner : null;
    }

    public static List<HoseEndpoint> chain(final HoseEndpoint start) {
        final List<HoseEndpoint> chain = new ArrayList<>();
        chain.add(start);
        if (hosePartner(start) != null && start.getCoupledEndpoint() != null) {
            return chain;
        }
        HoseEndpoint previous = null;
        HoseEndpoint current = start;
        for (int i = 0; i < MAX_CHAIN; i++) {
            final HoseEndpoint next = step(current, previous);
            if (next == null || chain.contains(next)) {
                break;
            }
            chain.add(next);
            previous = current;
            current = next;
        }
        return chain;
    }

    public static List<IFluidHandler> findOutlets(final HoseEndpoint endpoint, final Supplier<IFluidHandler> openEnd) {
        final BlockEntity be = endpoint.asBlockEntity();
        final Level level = be.getLevel();
        if (level == null) {
            return List.of();
        }

        if (endpoint.isPortMated()) {
            return List.of();
        }

        final Direction port = endpoint.getPortDirection();
        final BlockPos pos = be.getBlockPos();
        final BlockPos neighbour = pos.relative(port);
        if (!level.isLoaded(neighbour) || HosePressure.isAgainstPipe(level, pos, port)) {
            return List.of();
        }

        final IFluidHandler direct = level.getCapability(Capabilities.FluidHandler.BLOCK, neighbour, port.getOpposite());
        if (direct != null) {
            return List.of(direct);
        }
        if (FluidPropagator.isOpenEnd(level, pos, port)) {
            final IFluidHandler open = openEnd.get();
            return open != null ? List.of(open) : List.of();
        }
        return List.of();
    }

    private HoseNetwork() {
    }
}
