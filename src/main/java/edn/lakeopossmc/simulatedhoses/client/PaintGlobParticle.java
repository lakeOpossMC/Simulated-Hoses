package edn.lakeopossmc.simulatedhoses.client;

import edn.lakeopossmc.simulatedhoses.hose.NozzleJet;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.Nullable;

// --- A GLOB OF PAINT IN A DYE JET --- //
public class PaintGlobParticle extends TextureSheetParticle {
    private static final float JET_SIZE = 0.22F;
    private static final float SPILL_SIZE = 0.18F;
    private static final float FLOW_GROWTH = 0.9F;
    private static final float SIZE_SPREAD = 0.45F;
    private static final float EXTRA_SIZE_SPREAD = 0.3F;
    private static final float SOLID_CHANCE = 0.5F;
    private static final float MIN_ALPHA = 0.35F;
    private static final float MAX_THIN_ALPHA = 0.8F;
    private static final float FADE_FROM = 0.6F;
    private static final float NOZZLE_START = 0.07F;
    private static final float GROW_TICKS = 4.0F;
    private static final int FULL_BRIGHT = 0xF000F0;

    @Nullable
    private final SprayEffect effect;
    private final boolean emissive;
    @Nullable
    private TextureAtlasSprite shine;
    private final float shineRed;
    private final float shineGreen;
    private final float shineBlue;

    private final float strength;
    private final float spin;
    private final float baseAlpha;
    private float startSize;
    private float growTicks;

    public PaintGlobParticle(final ClientLevel level, final double x, final double y, final double z, final double vx, final double vy,
                             final double vz, final int lifetime, final int rgb, final float size, final float strength) {
        this(level, x, y, z, vx, vy, vz, lifetime, rgb, size, strength, SprayEffects.builtin("paint"), false);
    }

    public PaintGlobParticle(final ClientLevel level, final double x, final double y, final double z, final double vx, final double vy,
                             final double vz, final int lifetime, final int rgb, final float size, final float strength,
                             @Nullable final SprayEffect effect, final boolean emissive) {
        super(level, x, y, z, vx, vy, vz);
        this.effect = effect;
        this.emissive = emissive;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = lifetime;
        this.gravity = (float) (NozzleJet.GRAVITY / 0.04);
        this.friction = NozzleJet.DRAG;
        this.strength = strength;
        final float spread = SIZE_SPREAD + EXTRA_SIZE_SPREAD * Math.max(strength, 0.0F);
        this.quadSize = strength < 0.0F ? size : size * (1.0F + (this.random.nextFloat() - 0.5F) * 2.0F * spread);
        this.roll = this.oRoll = this.random.nextFloat() * (float) (Math.PI * 2.0);
        this.baseAlpha = this.random.nextFloat() < SOLID_CHANCE ? 1.0F : MIN_ALPHA + this.random.nextFloat() * (MAX_THIN_ALPHA - MIN_ALPHA);
        this.alpha = this.baseAlpha;
        this.spin = (this.random.nextFloat() - 0.5F) * 2.0F * (effect != null ? effect.spin : 0.0F);
        this.setColor((rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F, (rgb & 255) / 255.0F);
        final int shineRgb = effect != null ? effect.shine(rgb) : DyeFluidsClient.gloss(rgb);
        this.shineRed = (shineRgb >> 16 & 255) / 255.0F;
        this.shineGreen = (shineRgb >> 8 & 255) / 255.0F;
        this.shineBlue = (shineRgb & 255) / 255.0F;
        if (effect != null) {
            final int shape = this.random.nextInt(effect.shapes());
            this.setSprite(effect.body(shape));
            this.shine = effect.gloss(shape);
        }
    }

    @Override
    public void render(final VertexConsumer buffer, final Camera camera, final float partialTicks) {
        super.render(buffer, camera, partialTicks);
        if (this.shine == null) {
            return;
        }
        final TextureAtlasSprite paint = this.sprite;
        final float red = this.rCol;
        final float green = this.gCol;
        final float blue = this.bCol;
        final float alpha = this.alpha;
        this.sprite = this.shine;
        this.setColor(this.shineRed, this.shineGreen, this.shineBlue);
        this.alpha = alpha * (this.effect != null ? this.effect.shine : 1.0F);
        super.render(buffer, camera, partialTicks);
        this.sprite = paint;
        this.setColor(red, green, blue);
        this.alpha = alpha;
    }

    public PaintGlobParticle squeezed() {
        this.startSize = Math.min(NOZZLE_START, this.quadSize);
        this.growTicks = GROW_TICKS;
        return this;
    }

    @Override
    public float getQuadSize(final float partialTicks) {
        final float age = this.age + partialTicks;
        if (this.growTicks <= 0.0F || age >= this.growTicks) {
            return this.quadSize;
        }
        final float t = age / this.growTicks;
        final float eased = 1.0F - (1.0F - t) * (1.0F - t);
        return this.startSize + (this.quadSize - this.startSize) * eased;
    }

    public static float jetSize(final double fraction) {
        return JET_SIZE * (1.0F + FLOW_GROWTH * (float) fraction);
    }

    public static float spillSize(final double fraction) {
        return SPILL_SIZE * (1.0F + FLOW_GROWTH * (float) fraction);
    }

    public static boolean ready() {
        return SprayEffects.builtin("paint") != null;
    }

    @Override
    protected int getLightColor(final float partialTick) {
        return this.emissive ? FULL_BRIGHT : super.getLightColor(partialTick);
    }

    @Override
    public void tick() {
        final double vx = this.xd;
        final double vy = this.yd;
        final double vz = this.zd;
        this.oRoll = this.roll;
        this.roll += this.spin;
        super.tick();
        if (this.strength < 0.0F) {
            final float life = (float) this.age / this.lifetime;
            this.alpha = life <= FADE_FROM ? this.baseAlpha : this.baseAlpha * (1.0F - (life - FADE_FROM) / (1.0F - FADE_FROM));
        }
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
        if (this.removed || !collided) {
            return;
        }
        this.remove();
        if (this.strength < 0.0F || this.effect == null) {
            return;
        }
        final SprayEffect effect = this.effect;
        final int rgb = (int) (this.rCol * 255) << 16 | (int) (this.gCol * 255) << 8 | (int) (this.bCol * 255);
        final int count = effect.minSplash + Math.round(this.strength * effect.extraSplash);
        final double speed = effect.splashSpeed + effect.extraSplashSpeed * this.strength;
        final double lift = effect.splashLift + effect.extraSplashLift * this.strength;
        for (int i = 0; i < count; i++) {
            final double angle = this.random.nextDouble() * Math.PI * 2.0;
            final double out = speed * (0.5 + this.random.nextDouble() * 0.5);
            final float size = this.getQuadSize(0.0F) * (effect.minSplashSize + this.random.nextFloat() * effect.extraSplashSize);
            Minecraft.getInstance().particleEngine.add(new PaintGlobParticle(this.level, this.x, this.y + 0.05, this.z,
                    Math.cos(angle) * out, lift * (0.6 + this.random.nextDouble() * 0.4), Math.sin(angle) * out,
                    effect.minSplashLifetime + this.random.nextInt(effect.extraSplashLifetime + 1), rgb, size, -1.0F, effect, this.emissive));
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // * For /particle; real sprays take their textures from their effect
    public static ParticleProvider<SimpleParticleType> provider(final SpriteSet spriteSet) {
        return (type, level, x, y, z, vx, vy, vz) -> ready()
                ? new PaintGlobParticle(level, x, y, z, vx, vy, vz, 20, 0xFFFFFF, jetSize(0.5), 0.5F) : null;
    }
}
