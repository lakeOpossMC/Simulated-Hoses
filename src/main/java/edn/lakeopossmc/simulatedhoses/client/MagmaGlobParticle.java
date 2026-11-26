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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

// --- A GLOB OF MAGMA --- //
public class MagmaGlobParticle extends TextureSheetParticle {
    private static final float NOZZLE_SIZE = 0.08F;
    private static final float GROW_TICKS = 4.0F;
    private static final float SIZE_SPREAD = 0.45F;
    private static final float EXTRA_SIZE_SPREAD = 0.3F;
    private static final int HISS_STEAM = 3;
    private static final int HISS_SPLASH = 4;
    private static final float FADE_FROM = 0.6F;

    private static final float MIN_GLOW = 0.85F;
    private static final float MIN_PULSE = 0.1F;
    private static final float EXTRA_PULSE = 0.12F;
    private static final float MIN_FLOW = 0.05F;
    private static final float EXTRA_FLOW = 0.07F;
    private static final float FLOW_BLEND = 0.7F;
    private static final float MIN_BODY_WARMTH = 0.2F;
    private static final float EXTRA_BODY_WARMTH = 0.4F;
    private static final float BODY_BRIGHTNESS = 1.06F;
    private static final float MIN_GLOW_GOLD = 0.6F;
    private static final float GLOW_BRIGHTNESS = 1.06F;
    private static final float PEAK_BRIGHTEN = 0.1F;
    // * Globs leave the nozzle at their hottest and settle over their first few ticks
    private static final float FRESH_TICKS = 6.0F;
    private static final float FRESH_BODY_WARMTH = 0.35F;
    private static final float FRESH_WHITE = 0.1F;
    private static final float FRESH_BRIGHTEN = 0.1F;
    private static final float STRETCH_TICKS = 10.0F;
    private static final float FULL_STRETCH_SPEED = 0.8F;
    private static final float THINNING = 0.3F;

    private static final int MAX_POPS = 2;
    private static final int FULL_BRIGHT = 0xF000F0;

    @Nullable
    private final SprayEffect effect;
    private final boolean emissive;
    @Nullable
    private TextureAtlasSprite glow;
    @Nullable
    private TextureAtlasSprite flow;
    private final int[] palette;
    private final float strength;
    private final float spin;
    private final float pulse;
    private final float phase;
    private final float flowSpeed;
    private final float flowPhase;
    private float startSize;
    private float growTicks;

    public MagmaGlobParticle(final ClientLevel level, final double x, final double y, final double z, final double vx, final double vy,
                             final double vz, final int lifetime, final int[] palette, final float size, final float strength) {
        this(level, x, y, z, vx, vy, vz, lifetime, palette, size, strength, SprayEffects.builtin("lava"), true);
    }

    public MagmaGlobParticle(final ClientLevel level, final double x, final double y, final double z, final double vx, final double vy,
                             final double vz, final int lifetime, final int[] palette, final float size, final float strength,
                             @Nullable final SprayEffect effect, final boolean emissive) {
        super(level, x, y, z, vx, vy, vz);
        this.effect = effect;
        this.emissive = emissive;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = Math.max(1, lifetime);
        this.gravity = (float) (NozzleJet.GRAVITY / 0.04);
        this.friction = NozzleJet.DRAG;
        this.palette = palette;
        this.strength = strength;
        final float spread = SIZE_SPREAD + EXTRA_SIZE_SPREAD * Math.max(strength, 0.0F);
        this.quadSize = strength < 0.0F ? size : size * (1.0F + (this.random.nextFloat() - 0.5F) * 2.0F * spread);
        this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
        this.spin = (this.random.nextFloat() - 0.5F) * 2.0F * (effect != null ? effect.spin : 0.0F);
        this.pulse = MIN_PULSE + this.random.nextFloat() * EXTRA_PULSE;
        this.phase = this.random.nextFloat() * Mth.TWO_PI;
        this.flowSpeed = MIN_FLOW + this.random.nextFloat() * EXTRA_FLOW;
        this.flowPhase = this.random.nextFloat() * Mth.TWO_PI;
        if (effect != null) {
            final int shape = this.random.nextInt(effect.shapes());
            this.setSprite(effect.body(shape));
            this.glow = effect.glow(shape);
            this.flow = effect.flow(shape);
        }
    }

    public static boolean ready() {
        return SprayEffects.builtin("lava") != null;
    }

    // * Starts as thin as the nozzle and swells into a glob over its first few ticks
    public MagmaGlobParticle squeezed() {
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
        final float heat = 0.5F + 0.5F * Mth.sin(time * this.pulse + this.phase);
        final float drift = 0.5F + 0.5F * Mth.sin(time * this.flowSpeed + this.flowPhase);
        final boolean jet = this.strength >= 0.0F;
        final float fresh = jet ? Math.max(0.0F, 1.0F - time / FRESH_TICKS) : 0.0F;

        final Vector3f[] corners = this.corners(camera, partialTicks, jet ? Math.max(0.0F, 1.0F - time / STRETCH_TICKS) : 0.0F);
        final Vec3 cam = camera.getPosition();
        final float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        final float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        final float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());
        final int light = this.getLightColor(partialTicks);

        final int dark = this.palette[FluidSprayColors.DARK];
        final int body = this.palette[FluidSprayColors.BODY];
        final int hot = this.palette[FluidSprayColors.HOT];
        final int peak = this.palette.length > FluidSprayColors.PEAK ? this.palette[FluidSprayColors.PEAK] : hot;

        setColor(this, lerp(lerp(dark, body, MIN_BODY_WARMTH + EXTRA_BODY_WARMTH * heat), hot, FRESH_BODY_WARMTH * fresh), BODY_BRIGHTNESS);
        this.quad(buffer, this.sprite, corners, x, y, z, light);
        if (this.glow == null || this.flow == null) {
            return;
        }
        final float alpha = this.alpha;
        final float glowAlpha = alpha * Mth.lerp(heat, MIN_GLOW, 1.0F);
        setColor(this, lerp(lerp(hot, peak, Math.min(1.0F, MIN_GLOW_GOLD + (1.0F - MIN_GLOW_GOLD) * heat + fresh)), 0xFFFFFF, FRESH_WHITE * fresh),
                GLOW_BRIGHTNESS + PEAK_BRIGHTEN * heat + FRESH_BRIGHTEN * fresh);
        this.alpha = glowAlpha * (1.0F - FLOW_BLEND * drift);
        this.quad(buffer, this.glow, corners, x, y, z, light);
        this.alpha = glowAlpha * (1.0F - FLOW_BLEND + FLOW_BLEND * drift);
        this.quad(buffer, this.flow, corners, x, y, z, light);
        this.alpha = alpha;
    }

    private Vector3f[] corners(final Camera camera, final float partialTicks, final float freshStretch) {
        final Vector3f up = camera.getUpVector();
        final Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        final float screenX = (float) (this.xd * right.x() + this.yd * right.y() + this.zd * right.z());
        final float screenY = (float) (this.xd * up.x() + this.yd * up.y() + this.zd * up.z());
        final float screenSpeed = Mth.sqrt(screenX * screenX + screenY * screenY);
        final boolean moving = screenSpeed > 1.0E-4F;
        final float dirX = moving ? screenX / screenSpeed : 1.0F;
        final float dirY = moving ? screenY / screenSpeed : 0.0F;
        final float stretch = moving && this.effect != null
                ? (this.effect.stretch + this.effect.freshStretch * freshStretch) * Math.min(1.0F, screenSpeed / FULL_STRETCH_SPEED) : 0.0F;
        final float size = this.getQuadSize(partialTicks);
        final float length = size * (1.0F + stretch);
        final float width = size / (1.0F + THINNING * stretch);
        final float roll = Mth.lerp(partialTicks, this.oRoll, this.roll);
        final float cos = Mth.cos(roll);
        final float sin = Mth.sin(roll);
        final float[][] offsets = {{1.0F, -1.0F}, {1.0F, 1.0F}, {-1.0F, 1.0F}, {-1.0F, -1.0F}};
        final Vector3f[] corners = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            final float spunX = offsets[i][0] * cos - offsets[i][1] * sin;
            final float spunY = offsets[i][0] * sin + offsets[i][1] * cos;
            final float along = (spunX * dirX + spunY * dirY) * length;
            final float across = (spunY * dirX - spunX * dirY) * width;
            final float screenOffsetX = dirX * along - dirY * across;
            final float screenOffsetY = dirY * along + dirX * across;
            corners[i] = new Vector3f(right).mul(screenOffsetX).add(up.x() * screenOffsetY, up.y() * screenOffsetY, up.z() * screenOffsetY);
        }
        return corners;
    }

    private void quad(final VertexConsumer buffer, final TextureAtlasSprite sprite, final Vector3f[] corners,
                      final float x, final float y, final float z, final int light) {
        final float[] us = {sprite.getU1(), sprite.getU1(), sprite.getU0(), sprite.getU0()};
        final float[] vs = {sprite.getV1(), sprite.getV0(), sprite.getV0(), sprite.getV1()};
        for (int i = 0; i < 4; i++) {
            buffer.addVertex(x + corners[i].x(), y + corners[i].y(), z + corners[i].z())
                    .setUv(us[i], vs[i])
                    .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                    .setLight(light);
        }
    }

    @Override
    public AABB getRenderBoundingBox(final float partialTicks) {
        final float reach = this.getQuadSize(partialTicks) * (1.0F + (this.effect != null ? this.effect.maxStretch() : 0.0F));
        return new AABB(this.x - reach, this.y - reach, this.z - reach, this.x + reach, this.y + reach, this.z + reach);
    }

    private static void setColor(final MagmaGlobParticle particle, final int rgb, final float brightness) {
        particle.setColor(Math.min(1.0F, (rgb >> 16 & 255) / 255.0F * brightness),
                Math.min(1.0F, (rgb >> 8 & 255) / 255.0F * brightness),
                Math.min(1.0F, (rgb & 255) / 255.0F * brightness));
    }

    private static int lerp(final int from, final int to, final float t) {
        final int red = (int) Mth.lerp(t, from >> 16 & 255, to >> 16 & 255);
        final int green = (int) Mth.lerp(t, from >> 8 & 255, to >> 8 & 255);
        final int blue = (int) Mth.lerp(t, from & 255, to & 255);
        return red << 16 | green << 8 | blue;
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
        final Vec3 surface = FluidSurface.entered(this.level, this.xo, this.yo, this.zo, this.x, this.y, this.z);
        if (surface != null && this.level.getFluidState(BlockPos.containing(surface.x, surface.y - 0.01, surface.z)).is(FluidTags.WATER)) {
            this.hiss(surface);
            this.remove();
            return;
        }
        if (surface != null) {
            this.setPos(surface.x, surface.y, surface.z);
        }
        if (this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).is(FluidTags.WATER)) {
            this.level.addParticle(ParticleTypes.LARGE_SMOKE, this.x, this.y + 0.1, this.z, 0.0, 0.05, 0.0);
            this.remove();
            return;
        }
        if (this.strength < 0.0F) {
            final float life = (float) this.age / this.lifetime;
            this.alpha = life <= FADE_FROM ? 1.0F : 1.0F - (life - FADE_FROM) / (1.0F - FADE_FROM);
        } else if (this.effect != null && this.random.nextFloat() < this.effect.smoke) {
            this.level.addParticle(ParticleTypes.SMOKE, this.x, this.y, this.z, 0.0, 0.02, 0.0);
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
            this.splash();
        }
    }

    private void hiss(final Vec3 at) {
        final boolean big = this.strength >= 0.0F;
        for (int i = 0; i < (big ? HISS_STEAM : 1); i++) {
            this.level.addParticle(i == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD,
                    at.x + (this.random.nextDouble() - 0.5) * 0.3, at.y + 0.05, at.z + (this.random.nextDouble() - 0.5) * 0.3,
                    (this.random.nextDouble() - 0.5) * 0.04, 0.05 + this.random.nextDouble() * 0.05, (this.random.nextDouble() - 0.5) * 0.04);
        }
        for (int i = 0; i < (big ? HISS_SPLASH : 0); i++) {
            this.level.addParticle(ParticleTypes.SPLASH, at.x + (this.random.nextDouble() - 0.5) * 0.3, at.y + 0.05,
                    at.z + (this.random.nextDouble() - 0.5) * 0.3, 0.0, 0.0, 0.0);
        }
    }

    private void splash() {
        if (this.effect == null) {
            return;
        }
        final SprayEffect effect = this.effect;
        final int pops = this.random.nextInt(MAX_POPS + 1);
        for (int i = 0; i < pops; i++) {
            this.level.addParticle(ParticleTypes.LAVA, this.x, this.y + 0.05, this.z, 0.0, 0.0, 0.0);
        }
        final int count = effect.minSplash + Math.round(this.strength * effect.extraSplash);
        final double speed = effect.splashSpeed + effect.extraSplashSpeed * this.strength;
        final double lift = effect.splashLift + effect.extraSplashLift * this.strength;
        for (int i = 0; i < count; i++) {
            final double angle = this.random.nextDouble() * Math.PI * 2.0;
            final double out = speed * (0.5 + this.random.nextDouble() * 0.5);
            final float size = this.getQuadSize(0.0F) * (effect.minSplashSize + this.random.nextFloat() * effect.extraSplashSize);
            Minecraft.getInstance().particleEngine.add(new MagmaGlobParticle(this.level, this.x, this.y + 0.05, this.z,
                    Math.cos(angle) * out, lift * (0.6 + this.random.nextDouble() * 0.4), Math.sin(angle) * out,
                    effect.minSplashLifetime + this.random.nextInt(effect.extraSplashLifetime + 1), this.palette, size, -1.0F, effect,
                    this.emissive));
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

    public static ParticleProvider<SimpleParticleType> provider(final SpriteSet spriteSet) {
        return (type, level, x, y, z, vx, vy, vz) -> ready() ? new MagmaGlobParticle(level, x, y, z, vx, vy, vz, 20,
                new int[]{0xCD4509, 0xD5570F, 0xE2892C, 0xEBAF46}, 0.25F, 0.5F) : null;
    }
}
