package xyz.daikirai.render.furiah.module.landing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.daikirai.module.render.JumpCirclesModule;
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
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.render.Vfx;

/** Adapts the supplied Furiah Visuals landing renderers to NeoRaven's JumpCircles module. */
public final class FuriahJumpEffects {
    private static final Logger LOG = LogManager.getLogger("NeoRaven/JumpCircles");
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final int MAX_EFFECTS = 24;
    private static final int MAX_EFFECTS_PER_PLAYER = 4;
    private final EnumMap<LandingStyle, LandingStyleBase> styles = new EnumMap<>(LandingStyle.class);
    private final EnumSet<LandingStyle> failedPasses = EnumSet.noneOf(LandingStyle.class);
    private final EffectEngine<LandingFx> engine = new EffectEngine<>("JumpCircles");
    private final FxParticles particles = new FxParticles(2048);
    private final Camera camera = new Camera();
    private final FxFrame frame = new FxFrame();
    private final FuriahRender renderer = new FuriahRender();
    private final List<LandingFx> ordered = new ArrayList<>();
    private long lastFrame;
    private int spawnIndex;

    public FuriahJumpEffects() {
        engine.setLimits(MAX_EFFECTS, MAX_EFFECTS_PER_PLAYER);
    }

    public void clear() {
        engine.clear();
        particles.clear();
        ordered.clear();
        lastFrame = 0;
    }

    public void spawn(LandingStyle type, EntityPlayer player, Vec3 at, boolean jump, float fallHeight, JumpCirclesModule settings) {
        int id = player.getEntityId();
        if (!engine.hasRoom(id) || MC.thePlayer == null
                || MC.thePlayer.getDistanceSq(at.xCoord, at.yCoord, at.zCoord) > 32 * 32) return;

        LandingStyleBase style = styles.computeIfAbsent(type, LandingStyle::create);
        LandingContext land = new LandingContext();
        land.player = player;
        land.playerId = id;
        land.self = player == MC.thePlayer;
        land.jump = jump;
        land.x = at.xCoord;
        land.y = at.yCoord;
        land.z = at.zCoord;
        land.fallHeight = fallHeight;
        land.fallScale = jump ? (player.isSprinting() ? 1.06F : 0.85F)
                : 0.85F + 0.75F * (float) Math.sqrt(Math.min(1, Math.max(0, (fallHeight - 1.25F) / 8.75F)));
        land.pose.capture(player, 1);
        land.pose.x = land.x;
        land.pose.y = land.y;
        land.pose.z = land.z;
        land.yaw = land.pose.bodyYaw;
        land.blockColor = Terrain.mapColor(MC.theWorld, land.x, land.y, land.z, land.blockColor);

        float lifetime = settings.lifetime() / 1000F;
        float scale = settings.radius() / 1.5F * land.fallScale;
        float opacity = settings.opacity() * Math.min(1, 0.75F + 0.18F * land.fallScale);
        LandingFx effect = new LandingFx(style, land, Anchor.world(land.x, land.y, land.z),
                lifetime, lifetime / style.baseSeconds(), Rng.mix(++spawnIndex ^ (int) GameClock.nanos()),
                particles, scale, opacity, Math.max(1, land.fallScale), false);
        effect.ground = Terrain.HeightField.sample(MC.theWorld, land.x, land.y, land.z, style.reach() * scale + 0.6, 2);
        Entity view = MC.getRenderViewEntity();
        effect.lod = view != null && view.getDistanceSq(land.x, land.y, land.z) > 16 * 16;
        try (ThemeTint.Scope tint = ThemeTint.use(settings.color())) {
            engine.add(effect);
        }
    }

    public void render(JumpCirclesModule settings, float partialTicks) {
        Entity view = MC.getRenderViewEntity();
        if (view == null || engine.isEmpty() && particles.isEmpty()) {
            lastFrame = 0;
            return;
        }

        long now = GameClock.nanos();
        float dt = lastFrame == 0 || MC.isGamePaused() ? 0 : Math.min(0.1F, (now - lastFrame) / 1_000_000_000F);
        lastFrame = now;
        renderFrame(settings, partialTicks, dt, now, false);
    }

    public void previewSpawn(LandingStyle type, JumpCirclesModule settings, boolean jump, float yaw) {
        LandingStyleBase style = styles.computeIfAbsent(type, LandingStyle::create);
        LandingContext land = new LandingContext();
        land.jump = jump;
        land.self = true;
        land.yaw = yaw;
        land.pose.mannequin(yaw, false, 0);
        float lifetime = settings.lifetime() / 1000F;
        LandingFx effect = new LandingFx(style, land, Anchor.world(0, 0, 0), lifetime,
                lifetime / style.baseSeconds(), Rng.mix(++spawnIndex), particles,
                settings.radius() / 1.5F * (jump ? 0.85F : 1), settings.opacity(), 1, true);
        effect.ground = Terrain.HeightField.flat(0);
        try (ThemeTint.Scope tint = ThemeTint.use(settings.color())) {
            engine.add(effect);
        }
    }

    public void renderPreview(JumpCirclesModule settings, float dt) {
        renderFrame(settings, 1, dt, GameClock.nanos(), true);
    }

    private void renderFrame(JumpCirclesModule settings, float partialTicks, float dt, long now, boolean preview) {
        engine.update(dt, partialTicks, preview ? null : MC.theWorld);
        particles.update(dt);
        if (engine.isEmpty() && particles.isEmpty()) return;

        renderer.draw(camera, settings.color(), settings.throughWalls(), () -> {
            if (preview) renderer.fillPreview(frame, camera, dt, now);
            else renderer.fill(frame, camera, partialTicks, dt, now);
            for (LandingFx effect : engine.list()) {
                effect.passFrame = frame;
                float light = preview ? 1 : frame.world.getLightBrightness(new BlockPos(effect.x(), effect.y() + 0.5, effect.z()));
                effect.light = 0.7F + 0.3F * light;
                effect.night = 1 - light;
            }
            drawPasses();
            engine.render(frame);
            particles.render(frame.viewX, frame.viewY, frame.viewZ);
        });
    }

    private void drawPasses() {
        ordered.addAll(engine.list());
        ordered.sort(Comparator.comparingDouble(LandingFx::passDistanceSq).reversed());
        try {
            for (FxPass pass : FxPass.values()) {
                List<LandingFx> effects = pass == FxPass.TRANSLUCENT ? ordered : engine.list();
                for (LandingFx effect : effects) {
                    if (effect.isFinished() || (effect.passes() & pass.bit()) == 0) continue;
                    FuriahRender.pass(pass);
                    try {
                        effect.drawPass(pass);
                    } catch (RuntimeException | LinkageError error) {
                        if (failedPasses.add(effect.style.getType())) {
                            LOG.error("Furiah effect failed in " + pass + ": " + effect.style.getType(), error);
                        }
                        effect.passFailed();
                    }
                    Vfx.flush();
                }
            }
        } finally {
            ordered.clear();
            RenderState.decal(false);
            RenderState.additive();
        }
    }
}
