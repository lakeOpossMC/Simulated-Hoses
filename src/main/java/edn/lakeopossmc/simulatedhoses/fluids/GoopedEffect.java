package edn.lakeopossmc.simulatedhoses.fluids;

import edn.lakeopossmc.simulatedhoses.HoseEffects;
import edn.lakeopossmc.simulatedhoses.SimulatedHosesMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;

// --- GOOPED! --- //
// * Being coated in Plant Food
public class GoopedEffect extends MobEffect {
    public static final int COLOR = 0x5AC83C;
    private static final int FEED_INTERVAL = 80;
    private static final int FEED_NUTRITION = 1;
    private static final float FEED_SATURATION = 0.1F;

    private static final double SLOWDOWN = -0.1;

    public GoopedEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, SimulatedHosesMod.asResource("effect.gooped"), SLOWDOWN,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(final int duration, final int amplifier) {
        final int interval = FEED_INTERVAL >> amplifier;
        return interval <= 0 || duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(final LivingEntity entity, final int amplifier) {
        if (entity instanceof final Player player && !player.level().isClientSide()) {
            player.getFoodData().eat(FEED_NUTRITION, FEED_SATURATION);
        }
        return true;
    }

    public static void noFloatingParticles(final EffectParticleModificationEvent event) {
        if (event.getEffect().is(HoseEffects.GOOPED)) {
            event.setVisible(false);
        }
    }
}
