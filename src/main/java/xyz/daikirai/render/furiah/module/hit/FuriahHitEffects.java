package xyz.daikirai.render.furiah.module.hit;

import java.util.EnumMap;
import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.daikirai.render.furiah.core.GameClock;
import xyz.daikirai.render.furiah.fx.Anchor;
import xyz.daikirai.render.furiah.fx.EffectEngine;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.FxParticles;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.Terrain;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.FuriahRender;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;

public final class FuriahHitEffects {
    private static final Logger LOG = LogManager.getLogger("NeoRaven/Hit Effect");
    private final EnumMap<HitStyle, HitStyleBase> styles = new EnumMap<>(HitStyle.class);
    private final EnumSet<HitStyle> reported = EnumSet.noneOf(HitStyle.class);
    private final EffectEngine<HitFx> engine = new EffectEngine<>("Hit Effect");
    private final FxParticles particles = new FxParticles(2048);
    private final Camera camera = new Camera();
    private final FxFrame frame = new FxFrame();
    private final FuriahRender renderer = new FuriahRender();
    private final Minecraft mc = Minecraft.getMinecraft();
    private long lastFrame;
    private int seed;

    public FuriahHitEffects() { engine.setLimits(24, 4); }

    public void clear() {
        engine.clear();
        particles.clear();
        lastFrame = 0;
    }

    public HitContext measure(EntityLivingBase target) {
        EntityPlayerSP me = mc.thePlayer;
        HitContext hit = new HitContext();
        hit.target = target;
        hit.targetId = target.getEntityId();
        hit.feetX = target.posX;
        hit.feetY = target.posY;
        hit.feetZ = target.posZ;
        hit.width = target.width;
        hit.height = target.height;
        hit.time = GameClock.nanos();
        Vec3 eye = me.getPositionEyes(1);
        Vec3 look = me.getLook(1);
        AxisAlignedBB box = target.getEntityBoundingBox();
        MovingObjectPosition over = mc.objectMouseOver;
        MovingObjectPosition intersection = box.calculateIntercept(eye, eye.addVector(look.xCoord * 8, look.yCoord * 8, look.zCoord * 8));
        Vec3 impact = over != null && over.entityHit == target && over.hitVec != null ? over.hitVec
                : intersection != null ? intersection.hitVec : new Vec3(target.posX, target.posY + target.height * 0.55, target.posZ);
        hit.offX = impact.xCoord - hit.feetX;
        hit.offY = impact.yCoord - hit.feetY;
        hit.offZ = impact.zCoord - hit.feetZ;
        Vec3 direction = impact.subtract(eye).normalize();
        hit.dirX = direction.xCoord;
        hit.dirY = direction.yCoord;
        hit.dirZ = direction.zCoord;
        hit.swipeX = look.zCoord;
        hit.swipeZ = -look.xCoord;
        float yawDelta = MathHelper.wrapAngleTo180_float(me.rotationYaw - me.prevRotationYaw);
        hit.side = yawDelta < 0 ? -1 : 1;
        hit.swipeX *= hit.side;
        hit.swipeZ *= hit.side;
        hit.screenSwipeX = hit.side;
        hit.crit = me.fallDistance > 0 && !me.onGround && !me.isOnLadder() && !me.isInWater()
                && !me.isPotionActive(net.minecraft.potion.Potion.blindness) && !me.isRiding();
        double ground = Terrain.surfaceAt(mc.theWorld, target.posX, target.posZ, target.posY, 0.3, 6);
        hit.groundY = Double.isNaN(ground) ? hit.feetY - 6 : ground;
        hit.pose.capture(target, 1);
        return hit;
    }

    public void spawn(HitStyle type, HitContext hit, int color, float size, float speed, float opacity, boolean follow) {
        spawn(type, hit, color, size, speed, opacity, follow, false);
    }

    private void spawn(HitStyle type, HitContext hit, int color, float size, float speed, float opacity, boolean follow, boolean preview) {
        if (!engine.hasRoom(hit.targetId)) return;
        HitStyleBase style = styles.computeIfAbsent(type, HitStyle::create);
        Anchor anchor = follow ? Anchor.entity(hit.target, hit.offX, hit.offY, hit.offZ, true, 1)
                : Anchor.fixed(hit.feetX, hit.feetY, hit.feetZ, hit.offX, hit.offY, hit.offZ);
        float scale = 1 / speed;
        HitFx effect = new HitFx(style, hit, anchor, style.baseSeconds() * scale, scale, Rng.mix(++seed),
                particles, size, opacity, 1, hit.crit, preview);
        try (ThemeTint.Scope tint = ThemeTint.use(color)) {
            engine.add(effect);
        }
    }

    public void render(float pt, int color, boolean throughWalls) {
        if (engine.isEmpty() && particles.isEmpty()) {
            lastFrame = 0;
            return;
        }
        long now = GameClock.nanos();
        float dt = lastFrame == 0 || mc.isGamePaused() ? 0 : Math.min(0.1F, (now - lastFrame) / 1_000_000_000F);
        lastFrame = now;
        renderFrame(pt, color, throughWalls, dt, now, false);
    }

    public void previewSpawn(HitStyle type, int color, float size, float speed, float opacity, float yaw) {
        HitContext hit = new HitContext();
        hit.targetId = -2;
        hit.offY = 1.18;
        hit.offZ = 0.28;
        hit.dirX = -0.35;
        hit.dirY = -0.12;
        hit.dirZ = -0.92;
        hit.swipeX = 0.85;
        hit.swipeY = 0.3;
        hit.swipeZ = -0.35;
        hit.screenSwipeY = 0.3F;
        hit.pose.mannequin(yaw, false, 0);
        hit.crit = true;
        spawn(type, hit, color, size, speed, opacity, false, true);
    }

    public void renderPreview(int color, boolean throughWalls, float dt) {
        renderFrame(1, color, throughWalls, dt, GameClock.nanos(), true);
    }

    private void renderFrame(float pt, int color, boolean throughWalls, float dt, long now, boolean preview) {
        if (engine.isEmpty() && particles.isEmpty()) return;
        engine.update(dt, pt, preview ? null : mc.theWorld);
        particles.update(dt);
        renderer.draw(camera, color, throughWalls, () -> {
            if (preview) renderer.fillPreview(frame, camera, dt, now);
            else renderer.fill(frame, camera, pt, dt, now);
            for (HitFx effect : engine.list()) {
                effect.passFrame = frame;
                float light = preview ? 1 : frame.world.getLightBrightness(new BlockPos(effect.cx(), effect.cy(), effect.cz()));
                effect.light = 0.7F + 0.3F * light;
                effect.night = 1 - light;
                effect.keep.set(camera, frame.rx(effect.cx()), frame.ry(effect.cy() - effect.hit.height * 0.5),
                        frame.rz(effect.cz()), effect.hit.width, effect.hit.height, true, 0.35F, 0.35F);
            }
            for (FxPass pass : FxPass.values()) {
                for (HitFx effect : engine.list()) {
                    if (effect.isFinished() || (effect.passes() & pass.bit()) == 0) continue;
                    FuriahRender.pass(pass);
                    try {
                        effect.drawPass(pass);
                    } catch (RuntimeException | LinkageError error) {
                        if (reported.add(effect.style.getType())) LOG.error("Hit effect failed: " + effect.style.getType(), error);
                        effect.passFailed();
                    }
                    Vfx.flush();
                }
            }
            RenderState.decal(false);
            RenderState.additive();
            engine.render(frame);
            particles.render(frame.viewX, frame.viewY, frame.viewZ);
        });
    }
}
