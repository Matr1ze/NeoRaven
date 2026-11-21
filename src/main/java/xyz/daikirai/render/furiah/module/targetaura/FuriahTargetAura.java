package xyz.daikirai.render.furiah.module.targetaura;

import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import xyz.daikirai.render.furiah.core.GameClock;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.FuriahRender;
import xyz.daikirai.render.furiah.render.ParticleSystem;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Vfx;

public final class FuriahTargetAura {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final EnumMap<EffectType, AuraEffect> styles = new EnumMap<>(EffectType.class);
    private final ParticleSystem particles = new ParticleSystem(768);
    private final EffectContext context = new EffectContext();
    private final Camera camera = new Camera();
    private final FuriahRender renderer = new FuriahRender();
    private AuraState state;
    private EffectType current;
    private double elapsed;

    public void clear() {
        state = null;
        current = null;
        elapsed = 0;
        particles.clear();
    }

    public void render(EffectType type, EntityLivingBase target, float pt, float dt, float fade, boolean fading,
                       float scale, float speed, float glow, int color) {
        render(type, target, pt, dt, fade, fading, scale, speed, glow, color, false);
    }

    public void renderPreview(EffectType type, EntityLivingBase target, float dt, float scale, float speed, float glow, int color) {
        render(type, target, 1, dt, 1, false, scale, speed, glow, color, true);
    }

    private void render(EffectType type, EntityLivingBase target, float pt, float dt, float fade, boolean fading,
                        float scale, float speed, float glow, int color, boolean preview) {
        long now = GameClock.nanos();
        if (state == null || state.entity != target || current != type) {
            clear();
            current = type;
            state = new AuraState(target, now);
        }
        AuraEffect style = styles.computeIfAbsent(type, EffectType::create);
        elapsed += dt * speed;
        particles.update(dt);
        if (preview) state.groundTarget = 0;
        else state.updateGround(mc.theWorld);
        renderer.draw(camera, color, false, () -> {
            fillContext(target, pt, dt, fade, fading, scale, glow, now, preview);
            for (FxPass pass : FxPass.values()) {
                FuriahRender.pass(pass);
                style.render(context, pass);
                Vfx.flush();
            }
            RenderState.decal(false);
            RenderState.additive();
            RenderManager rm = mc.getRenderManager();
            particles.render(rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ);
        });
    }

    private void fillContext(EntityLivingBase target, float pt, float dt, float fade, boolean fading,
                             float scale, float glow, long now, boolean preview) {
        EffectContext c = context;
        RenderManager rm = mc.getRenderManager();
        c.worldX = target.lastTickPosX + (target.posX - target.lastTickPosX) * pt;
        c.worldY = target.lastTickPosY + (target.posY - target.lastTickPosY) * pt;
        c.worldZ = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * pt;
        if (Double.isNaN(state.groundSmooth)) state.groundSmooth = state.groundTarget;
        else state.groundSmooth += (state.groundTarget - state.groundSmooth) * (1 - Math.exp(-14 * dt));
        c.worldGroundY = Math.min(c.worldY, state.groundSmooth);
        c.x = c.worldX - rm.viewerPosX;
        c.y = c.worldY - rm.viewerPosY;
        c.z = c.worldZ - rm.viewerPosZ;
        c.groundY = c.worldGroundY - rm.viewerPosY;
        c.groundAlpha = 1 - Easing.smoothstep(0.6F, 3, (float) (c.worldY - c.worldGroundY));
        c.width = target.width;
        c.height = target.height;
        c.headTop = target.height + 0.075F;
        c.nameBottom = c.headTop + 0.25F;
        c.nameTop = c.nameBottom + 0.2F;
        c.bodyYaw = target.prevRenderYawOffset + MathHelper.wrapAngleTo180_float(target.renderYawOffset - target.prevRenderYawOffset) * pt;
        c.lightLevel = preview ? 1 : mc.theWorld.getLightBrightness(new BlockPos(c.worldX, c.worldY + c.height * 0.5, c.worldZ));
        c.light = 0.7F + 0.3F * c.lightLevel;
        c.night = 1 - c.lightLevel;
        c.alpha = fade * (0.45F + 0.55F * glow);
        c.scale = scale;
        c.intro = fade;
        c.time = elapsed;
        c.globalTime = now / 1_000_000_000.0;
        c.dt = dt;
        c.seed = state.seed;
        c.state = state;
        c.cam = camera;
        c.particles = particles;
        c.fadingOut = fading;
        c.outro = fading ? 1 - fade : 0;
        c.spawnParticles = !fading;
        c.preview = preview;
        c.distanceSq = camera.distanceSq(c.x, c.y + c.height * 0.5, c.z);
        c.keep.set(camera, c.x, c.y, c.z, c.width, c.height, true, 0.35F, 0.35F);
    }
}
