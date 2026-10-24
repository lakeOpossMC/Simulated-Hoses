package edn.lakeopossmc.simulatedhoses;

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import edn.lakeopossmc.simulatedhoses.blocks.FluidQuickConnectBlockEntity;
import edn.lakeopossmc.simulatedhoses.blocks.MagneticHoseConnectorBlockEntity;
import edn.lakeopossmc.simulatedhoses.hose.HoseGrabHandler;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;

// --- COMMON EVENT HOOKS --- //
public final class HoseCommonEvents {

    public static void registerCapabilities(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, HoseBlockEntities.HOSE_CONNECTOR.get(),
                (be, side) -> side == null || side == be.getPortDirection() ? be.getPortHandler() : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, HoseBlockEntities.MAGNETIC_HOSE_CONNECTOR.get(),
                (be, side) -> side == null || side == be.getPortDirection() ? be.getPortHandler() : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, HoseBlockEntities.HOSE_WINCH.get(),
                (be, side) -> side == null || side == be.getPortDirection() ? be.getPortHandler() : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, HoseBlockEntities.FLUID_QUICK_CONNECT.get(),
                (be, side) -> side == null || side == be.getPortDirection() ? be.getPortHandler() : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, HoseBlockEntities.PRESSURE_NOZZLE.get(),
                (be, side) -> be.getIntake(side));
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> new FluidBucketWrapper(stack),
                HoseItems.PLANT_FOOD_BUCKET.get());
    }

    // * Drops dead magnet pairs and checks live ones for coupling
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (event.getLevel() instanceof final ServerLevel level) {
            MagneticHoseConnectorBlockEntity.MAGNETS.tick(level);
        }
    }

    // * Magnet forces are applied every physics substep
    public static void onPhysicsTick(final SubLevelPhysicsSystem physicsSystem, final double timeStep) {
        MagneticHoseConnectorBlockEntity.MAGNETS.physicsTick(timeStep, physicsSystem.getLevel());
        HoseGrabHandler.physicsTick(physicsSystem);
        FluidQuickConnectBlockEntity.physicsTick(physicsSystem);
    }

    private HoseCommonEvents() {
    }
}
