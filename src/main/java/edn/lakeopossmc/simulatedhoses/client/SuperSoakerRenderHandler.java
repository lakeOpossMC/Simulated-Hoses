package edn.lakeopossmc.simulatedhoses.client;

import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonRenderHandler;
import edn.lakeopossmc.simulatedhoses.items.SuperSoakerItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

// --- SUPER SOAKER IN FIRST PERSON --- //
public class SuperSoakerRenderHandler extends PotatoCannonRenderHandler {

    @Override
    protected boolean appliesTo(final ItemStack stack) {
        return stack.getItem() instanceof SuperSoakerItem;
    }

    @Override
    protected void playSound(final InteractionHand hand, final Vec3 position) {
    }
}
