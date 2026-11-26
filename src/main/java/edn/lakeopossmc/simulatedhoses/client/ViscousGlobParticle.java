package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import edn.lakeopossmc.simulatedhoses.hose.NozzleJet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

// --- A GLOB OF THICK FLUID --- //
public class ViscousGlobParticle extends TextureSheetParticle {
    private static final float NOZZLE_SIZE = 0.08F;
    private static final float GROW_TICKS = 4.0F;
    private static final float SIZE_SPREAD = 0.3F;
    private static final float SHADE_SPREAD = 0.1F;
    private static final float FADE_FROM = 0.6F;
    private static final float STRETCH_TICKS = 10.0F;
    private static final float FULL_STRETCH_SPEED = 0.8F;
    private static final float THINNING = 0.3F;
    private static final float MIN_ROCK = 0.15F;
    private static final float EXTRA_ROCK = 0.15F;
    private static final float MIN_ROCK_SPEED = 0.2F;
    private static final float EXTRA_ROCK_SPEED = 0.15F;
    private static final int SHED_AFTER = 2;
    private static final float MIN_SHED_SIZE = 0.25F;
    private static final float EXTRA_SHED_SIZE = 0.15F;
    private static final double MIN_SHED_KEEP = 0.85;
    private static final double EXTRA_SHED_KEEP = 0.12;
    private static final double SHED_DRIFT = 0.025;
    private static final int FULL_BRIGHT = 0xF000F0;

    private final SprayEffect effect;
    private final int rgb;
    private final boolean emissive;
    private final float shineRed;
    private final float shineGreen;
    private final float shineBlue;
    @Nullable
    private TextureAtlasSprite gloss;
    private final float strength;
    private final float spin;
    private final float rock;
    private final float rockSpeed;
    private final float rockPhase;
    private float startSize;
    private float growTicks;

    public ViscousGlobParticle(final ClientLevel level, final double x, final double y, final double z, final double vx, final double vy,
                               final double vz, final int lifetime, final int rgb, final float size, final float strength,
                               final SprayEffect effect, final boolean emissive) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = Math.max(1, lifetime);
        this.gravity = (float) (NozzleJet.GRAVITY / 0.04);
        this.friction = NozzleJet.DRAG;
        this.effect = effect;
        this.rgb = rgb;
        this.emissive = emissive;
        this.strength = strength;
        this.quadSize = strength < 0.0F ? size : size * (1.0F + (this.random.nextFloat() - 0.5F) * 2.0F * SIZE_SPREAD);
        this.spin = (this.random.nextFloat() - 0.5F) * 2.0F * effect.spin;
        this.rock = (MIN_ROCK + this.random.nextFloat() * EXTRA_ROCK) * (this.random.nextBoolean() ? 1.0F : -1.0F);
        this.rockSpeed = MIN_ROCK_SPEED + this.random.nextFloat() * EXTRA_ROCK_SPEED;
        this.rockPhase = this.random.nextFloat() * Mth.TWO_PI;
        this.roll = this.oRoll = effect.rock ? this.rocking(0) : this.random.nextFloat() * Mth.TWO_PI;
        final float shade = 1.0F - this.random.nextFloat() * SHADE_SPREAD;
        this.setColor((rgb >> 16 & 255) / 255.0F * shade, (rgb >> 8 & 255) / 255.0F * shade, (rgb & 255) / 255.0F * shade);
        final int shine = effect.shine(rgb);
        this.shineRed = (shine >> 16 & 255) / 255.0F;
        this.shineGreen = (shine >> 8 & 255) / 255.0F;
        this.shineBlue = (shine & 255) / 255.0F;
        final int shape = this.random.nextInt(effect.shapes());
        this.setSprite(effect.body(shape));
        this.gloss = effect.gloss(shape);
    }

    private float rocking(final int age) {
        return this.rock * Mth.sin(age * this.rockSpeed + this.rockPhase);
    }

    // * Starts as thin as the nozzle and swells into a glob over its first few ticks
    public ViscousGlobParticle squeezed() {
        this.startSize = Math.min(NOZZLE_SIZE, this.quadSize);
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
        return Mth.lerp(1.0F - (1.0F - t) * (1.0F - t), this.startSize, this.quadSize);
    }

    @Override
    public void render(final VertexConsumer buffer, final Camera camera, final float partialTicks) {
        final float time = this.age + partialTicks;
        final float fresh = this.strength >= 0.0F ? Math.max(0.0F, 1.0F - time / STRETCH_TICKS) : 0.0F;
        final float stretch = this.effect.stretch + this.effect.freshStretch * fresh;
        final float size = this.getQuadSize(partialTicks);
        final float roll = Mth.lerp(partialTicks, this.oRoll, this.roll);

        final Vec3 cam = camera.getPosition();
        final float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        final float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        final float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());
        final int light = this.getLightColor(partialTicks);

        final Vector3f[] body = GlobQuad.corners(camera, this.xd, this.yd, this.zd, size, roll, stretch, FULL_STRETCH_SPEED, THINNING);
        GlobQuad.draw(buffer, this.sprite, body, x, y, z, this.rCol, this.gCol, this.bCol, this.alpha, light);
        if (this.gloss == null) {
            return;
        }
        final Vector3f[] shine = this.effect.rock ? body
                : GlobQuad.corners(camera, this.xd, this.yd, this.zd, size, 0.0F, stretch, FULL_STRETCH_SPEED, THINNING);
        GlobQuad.draw(buffer, this.gloss, shine, x, y, z, this.shineRed, this.shineGreen, this.shineBlue,
                this.alpha * this.effect.shine, light);
    }

    // * Long enough to hold the glob at its most stretched, so it isn't culled at the edge of the screen
    @Override
    public AABB getRenderBoundingBox(final float partialTicks) {
        final float reach = this.getQuadSize(partialTicks) * (1.0F + this.effect.maxStretch());
        return new AABB(this.x - reach, this.y - reach, this.z - reach, this.x + reach, this.y + reach, this.z + reach);
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
        this.roll = this.effect.rock ? this.rocking(this.age + 1) : this.roll + this.spin;
        super.tick();
        if (this.removed) {
            return;
        }
        if (this.strength < 0.0F) {
            final float life = (float) this.age / this.lifetime;
            this.alpha = life <= FADE_FROM ? 1.0F : 1.0F - (life - FADE_FROM) / (1.0F - FADE_FROM);
        } else if (this.age > SHED_AFTER && this.random.nextFloat() < this.effect.shed) {
            this.shedDroplet();
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
        if (!collided) {
            return;
        }
        this.remove();
        if (this.strength >= 0.0F) {
            this.splat();
        }
    }

    private void shedDroplet() {
        final double keep = MIN_SHED_KEEP + this.random.nextDouble() * EXTRA_SHED_KEEP;
        final float size = this.getQuadSize(0.0F) * (MIN_SHED_SIZE + this.random.nextFloat() * EXTRA_SHED_SIZE);
        Minecraft.getInstance().particleEngine.add(new ViscousGlobParticle(this.level, this.x, this.y, this.z,
                this.xd * keep + (this.random.nextDouble() - 0.5) * 2.0 * SHED_DRIFT,
                this.yd * keep + (this.random.nextDouble() - 0.5) * 2.0 * SHED_DRIFT,
                this.zd * keep + (this.random.nextDouble() - 0.5) * 2.0 * SHED_DRIFT,
                Math.max(4, this.lifetime - this.age), this.rgb, size, -1.0F, this.effect, this.emissive));
    }

    private void splat() {
        final SprayEffect effect = this.effect;
        final int count = effect.minSplash + Math.round(this.strength * effect.extraSplash);
        final double speed = effect.splashSpeed + effect.extraSplashSpeed * this.strength;
        final double lift = effect.splashLift + effect.extraSplashLift * this.strength;
        for (int i = 0; i < count; i++) {
            final double angle = this.random.nextDouble() * Math.PI * 2.0;
            final double out = speed * (0.5 + this.random.nextDouble() * 0.5);
            final float size = this.getQuadSize(0.0F) * (effect.minSplashSize + this.random.nextFloat() * effect.extraSplashSize);
            Minecraft.getInstance().particleEngine.add(new ViscousGlobParticle(this.level, this.x, this.y + 0.05, this.z,
                    Math.cos(angle) * out, lift * (0.6 + this.random.nextDouble() * 0.4), Math.sin(angle) * out,
                    effect.minSplashLifetime + this.random.nextInt(effect.extraSplashLifetime + 1), this.rgb, size, -1.0F, effect,
                    this.emissive));
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // * For /particle; real sprays take their textures from their effect
    public static ParticleProvider<SimpleParticleType> provider(final SpriteSet spriteSet) {
        return (type, level, x, y, z, vx, vy, vz) -> {
            final SprayEffect honey = SprayEffects.builtin("honey");
            return honey == null ? null : new ViscousGlobParticle(level, x, y, z, vx, vy, vz, 20, 0xFFFFFF, 0.22F, 0.5F, honey, false);
        };
    }
}
