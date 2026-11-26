package edn.lakeopossmc.simulatedhoses.client;

import edn.lakeopossmc.simulatedhoses.hose.NozzleJet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

// --- A CLUMP OF RUSHING FLUID --- //
public class FluidSprayParticle extends TextureSheetParticle {
    public enum Kind {
        CORE(1.0F, NozzleJet.DRAG, 0.85F, 0.35F, 1.55F, 0),
        MIST(0.3F, 0.86F, 0.45F, 0.5F, 1.9F, 2),
        SPLASH(0.9F, 0.9F, 0.75F, 0.45F, 1.25F, 3);

        private final float gravity;
        private final float friction;
        private final float alpha;
        private final float foam;
        private final float swell;
        private final int firstFrame;

        Kind(final float gravity, final float friction, final float alpha, final float foam, final float swell, final int firstFrame) {
            this.gravity = gravity;
            this.friction = friction;
            this.alpha = alpha;
            this.foam = foam;
            this.swell = swell;
            this.firstFrame = firstFrame;
        }
    }

    private static final int FRAME_STEPS = 8;
    private static final float NOZZLE_SIZE = 0.06F;
    private static final float GROW_TICKS = 3.0F;
    private static final float FADE_FROM = 0.6F;
    private static final float SHADE_SPREAD = 0.2F;
    private static final int FULL_BRIGHT = 0xF000F0;

    @Nullable
    private final SprayEffect effect;
    private final Kind kind;
    private final boolean emissive;
    private final float spin;
    private final float startSize;
    private final float endSize;

    public FluidSprayParticle(final ClientLevel level, final double x, final double y, final double z, final double vx, final double vy,
                              final double vz, final int lifetime, final int rgb, final float size, final Kind kind, final boolean emissive) {
        this(level, x, y, z, vx, vy, vz, lifetime, rgb, size, kind, emissive, SprayEffects.builtin("default"));
    }

    public FluidSprayParticle(final ClientLevel level, final double x, final double y, final double z, final double vx, final double vy,
                              final double vz, final int lifetime, final int rgb, final float size, final Kind kind, final boolean emissive,
                              @Nullable final SprayEffect effect) {
        super(level, x, y, z, vx, vy, vz);
        this.effect = effect;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = Math.max(1, lifetime);
        this.kind = kind;
        this.emissive = emissive;
        this.gravity = kind == Kind.CORE ? (float) (NozzleJet.GRAVITY / 0.04) * kind.gravity : kind.gravity;
        this.friction = kind.friction;
        this.quadSize = size;
        this.startSize = kind == Kind.CORE ? Math.min(NOZZLE_SIZE, size) : size * 0.6F;
        this.endSize = size * kind.swell;
        this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
        this.spin = (this.random.nextFloat() - 0.5F) * 2.0F * (effect != null ? effect.spin : 0.0F);
        this.alpha = kind.alpha;

        final float shade = 1.0F - this.random.nextFloat() * SHADE_SPREAD;
        final float foam = this.random.nextFloat() * kind.foam;
        this.setColor(Math.min(1.0F, (rgb >> 16 & 255) / 255.0F * shade + foam),
                Math.min(1.0F, (rgb >> 8 & 255) / 255.0F * shade + foam),
                Math.min(1.0F, (rgb & 255) / 255.0F * shade + foam));
        this.pickSprite();
    }

    public static boolean ready() {
        return SprayEffects.builtin("default") != null;
    }

    private void pickSprite() {
        if (this.effect == null) {
            return;
        }
        final int frames = this.effect.shapes();
        final int first = Math.min(frames - 1, this.kind.firstFrame * frames / FRAME_STEPS);
        final float life = Mth.clamp((float) this.age / this.lifetime, 0.0F, 1.0F);
        this.setSprite(this.effect.body(Math.min(frames - 1, first + (int) (life * (frames - first)))));
    }

    @Override
    public float getQuadSize(final float partialTicks) {
        final float age = this.age + partialTicks;
        if (age < GROW_TICKS) {
            final float t = age / GROW_TICKS;
            return Mth.lerp(1.0F - (1.0F - t) * (1.0F - t), this.startSize, this.quadSize);
        }
        return Mth.lerp(Mth.clamp((age - GROW_TICKS) / Math.max(1.0F, this.lifetime - GROW_TICKS), 0.0F, 1.0F), this.quadSize, this.endSize);
    }

    @Override
    public void tick() {
        final double vx = this.xd;
        final double vy = this.yd;
        final double vz = this.zd;
        this.oRoll = this.roll;
        this.roll += this.spin;
        super.tick();
        if (this.removed) {
            return;
        }
        this.pickSprite();
        final float life = (float) this.age / this.lifetime;
        this.alpha = life <= FADE_FROM ? this.kind.alpha : this.kind.alpha * (1.0F - (life - FADE_FROM) / (1.0F - FADE_FROM));

        // * Splashes on the surface of a fluid as it would on the ground
        final Vec3 surface = FluidSurface.entered(this.level, this.xo, this.yo, this.zo, this.x, this.y, this.z);
        if (surface != null) {
            this.setPos(surface.x, surface.y, surface.z);
        }
        final boolean collided = surface != null
                || this.onGround
                || (vx != 0.0 && this.xd == 0.0)
                || (vz != 0.0 && this.zd == 0.0)
                || (vy > 0.0 && this.yd == 0.0);
        if (!collided) {
            return;
        }
        this.remove();
        if (this.kind == Kind.CORE && this.effect != null && (surface != null || this.random.nextFloat() < this.effect.splashChance)) {
            this.splash();
        }
    }

    private void splash() {
        final int rgb = (int) (this.rCol * 255) << 16 | (int) (this.gCol * 255) << 8 | (int) (this.bCol * 255);
        final SprayEffect effect = this.effect;
        final int count = effect.minSplash + this.random.nextInt(effect.extraSplash + 1);
        for (int i = 0; i < count; i++) {
            final double angle = this.random.nextDouble() * Math.PI * 2.0;
            final double out = effect.splashSpeed * (0.5 + this.random.nextDouble());
            final float size = this.getQuadSize(0.0F) * (effect.minSplashSize + this.random.nextFloat() * effect.extraSplashSize);
            Minecraft.getInstance().particleEngine.add(new FluidSprayParticle(this.level, this.x, this.y + 0.05, this.z,
                    Math.cos(angle) * out, effect.splashLift * (0.5 + this.random.nextDouble() * 0.5), Math.sin(angle) * out,
                    effect.minSplashLifetime + this.random.nextInt(effect.extraSplashLifetime + 1), rgb, size, Kind.SPLASH, this.emissive,
                    effect));
        }
    }

    @Override
    protected int getLightColor(final float partialTick) {
        return this.emissive ? FULL_BRIGHT : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // * For /particle; real sprays take their textures from their effect
    public static ParticleProvider<SimpleParticleType> provider(final SpriteSet spriteSet) {
        return (type, level, x, y, z, vx, vy, vz) -> ready()
                ? new FluidSprayParticle(level, x, y, z, vx, vy, vz, 20, 0xFFFFFF, 0.25F, Kind.CORE, false) : null;
    }
}
