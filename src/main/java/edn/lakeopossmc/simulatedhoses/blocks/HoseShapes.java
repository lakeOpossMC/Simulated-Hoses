package edn.lakeopossmc.simulatedhoses.blocks;

import dev.simulated_team.simulated.util.DirectionalAxisShaper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// --- BLOCK SHAPES --- //
public final class HoseShapes {

    public static final DirectionalAxisShaper CONNECTOR = DirectionalAxisShaper.make(Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(4, 2, 4, 12, 5, 12),
            Block.box(5, 5, 5, 11, 8, 11)
    ));

    public static final DirectionalAxisShaper MAGNETIC_CONNECTOR = DirectionalAxisShaper.make(Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(4, 2, 4, 12, 8, 12)
    ));

    public static final DirectionalAxisShaper WINCH = DirectionalAxisShaper.make(Shapes.or(
            Block.box(4, 0, 0, 12, 3, 16),
            Block.box(2, 2, 0, 14, 14, 16)
    ));

    public static final DirectionalAxisShaper THIN_COLLIDER = DirectionalAxisShaper.make(
            Block.box(1, 0, 1, 15, 0.25, 15)
    );

    public static final DirectionalAxisShaper MAGNETIC_COLLIDER = DirectionalAxisShaper.make(
            Block.box(3, 0, 3, 13, 2, 13)
    );

    private HoseShapes() {
    }
}
