package xyz.daikirai.render.furiah.module.trails;

import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderManager;
import xyz.daikirai.render.furiah.core.GameClock;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.trail.PathRecorder;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.fx.trail.TrailShape;
import xyz.daikirai.render.furiah.render.FuriahRender;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;

/** Drives the Furiah trail styles from the local player's interpolated pose. */
public final class FuriahTrails {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final EnumMap<TrailStyle, TrailStyleBase> styles = new EnumMap<>(TrailStyle.class);
    private final TrailFrame frame = new TrailFrame();
    private final TrailShape shape = new TrailShape();
    private final FuriahRender render = new FuriahRender();
    private final double[] anchorPoint = new double[3];
    private PlayerTrail player;
    private TrailStyle current;
    private double lastTime;

    public void clear() {
        current = null;
        player = null;
        frame.clearPieces(null);
        lastTime = 0;
    }

    public boolean piecesFull(TrailFrame frame) { return frame.allPieces() >= frame.pieceCapacity; }

    public void render(TrailStyle type, float pt, double length, double opacity, int color, boolean throughWalls, boolean visible) {
        if (mc.thePlayer == null || mc.theWorld == null || !mc.thePlayer.isEntityAlive() || mc.thePlayer.isSpectator()) {
            clear();
            return;
        }
        double now = GameClock.seconds();
        float dt = lastTime == 0 || mc.isGamePaused() ? 0 : (float) Math.min(0.1, now - lastTime);
        renderFrame(type, mc.thePlayer, pt, length, opacity, color, throughWalls, visible, now, dt, false);
    }

    public void renderPreview(TrailStyle type, AbstractClientPlayer actor, double length, double opacity,
                              int color, boolean throughWalls, double now, float dt) {
        renderFrame(type, actor, 1, length, opacity, color, throughWalls, true, now, dt, true);
    }

    private void renderFrame(TrailStyle type, AbstractClientPlayer entity, float pt, double length, double opacity,
                             int color, boolean throughWalls, boolean visible, double now, float dt, boolean preview) {
        TrailStyleBase style = styles.computeIfAbsent(type, key -> key.create(this));
        if (current != type || player == null || player.entity != entity) {
            clear();
            current = type;
            player = new PlayerTrail(entity.getEntityId(), true);
            player.entity = entity;
        }

        style.length.set(length);
        style.opacity.set(opacity);
        lastTime = now;
        update(entity, style, pt, now, dt, visible);
        frame.dt = dt;
        frame.now = now;
        frame.colorTime = now;
        frame.partialTicks = pt;
        frame.world = preview ? null : mc.theWorld;
        frame.preview = preview;
        style.update(frame);
        if (!visible) return;

        render.draw(frame.cam, color, throughWalls, () -> {
            RenderManager rm = mc.getRenderManager();
            frame.viewX = rm.viewerPosX;
            frame.viewY = rm.viewerPosY;
            frame.viewZ = rm.viewerPosZ;
            frame.eyeX = frame.viewX + frame.cam.posX;
            frame.eyeY = frame.viewY + frame.cam.posY;
            frame.eyeZ = frame.viewZ + frame.cam.posZ;
            frame.pixelsPerBlock = (float) ((preview ? 360 : mc.displayHeight) * frame.cam.projectionScaleY * 0.5);
            frame.nearInner = 0.2F;
            frame.nearOuter = 0.55F;
            Textures.ensureTrailsLoaded();
            Vfx.resetTextureBinding();
            if (style.passes() == 0) {
                draw(style, null);
                style.renderPieces(frame);
            } else {
                for (FxPass pass : FxPass.values()) {
                    if ((style.passes() & pass.bit()) == 0) continue;
                    FuriahRender.pass(pass);
                    draw(style, pass);
                    FuriahRender.pass(pass);
                    style.piecesState();
                    style.renderPiecesPass(frame, pass);
                    Vfx.flush();
                }
            }
        });
    }

    private void update(AbstractClientPlayer e, TrailStyleBase style, float pt, double now, float dt, boolean visible) {
        player.pose.from(e, pt, false);
        player.skin = e.getLocationSkin();
        player.slim = "slim".equals(e.getSkinType());
        double dx = player.pose.x - player.lastX, dy = player.pose.y - player.lastY, dz = player.pose.z - player.lastZ;
        double distance = !player.hasLast ? 0 : Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean teleported = distance > 4;
        player.hasLast = true;
        player.lastX = player.pose.x;
        player.lastY = player.pose.y;
        player.lastZ = player.pose.z;
        float motion = (float) Math.sqrt(e.getDistanceSq(e.lastTickPosX, e.lastTickPosY, e.lastTickPosZ)) * 20;
        player.speed += (motion - player.speed) * (1 - (float) Math.exp(-dt / 0.25F));
        player.moveSpeed = motion;
        player.state = (e.onGround ? PathRecorder.ON_GROUND : 0) | (e.isSprinting() ? PathRecorder.SPRINTING : 0)
                | (e.isSneaking() ? PathRecorder.SNEAKING : 0) | (e.isInWater() ? PathRecorder.IN_WATER : 0)
                | (e.capabilities.isFlying ? PathRecorder.FLYING : 0) | (e.isRiding() ? PathRecorder.RIDING : 0);
        player.growing = visible && !teleported && !e.isInvisible() && (motion >= 0.25F || distance > 0.008)
                && (!style.sprintOnly() || e.isSprinting());

        PlayerTrail.StyleState state = player.style(style.getType());
        if (teleported) {
            player.reset();
            frame.clearPieces(style.getType());
        } else if (player.growing) {
            state.maxAge = style.lengthNow(player.speed);
            state.alpha = 1;
            state.head = Math.min(1, state.head + dt / 0.15F);
        } else {
            state.head = Math.max(0, state.head - dt / 0.4F);
            if (style.getType().isPath()) state.alpha = Math.max(0, state.alpha - dt / 0.4F);
        }

        TrailAnchor anchor = style.anchor();
        for (int side = 0; side < anchor.sides(); side++) {
            anchor.place(player.pose, side, anchorPoint);
            PathRecorder path = player.path(anchor, side);
            if (player.growing) {
                path.feed(anchorPoint[0], anchorPoint[1], anchorPoint[2], now, player.speed, player.pose.bodyYaw, player.state, 0.10, true);
            } else if (path.attached()) {
                path.finish(anchorPoint[0], anchorPoint[1], anchorPoint[2], now, player.speed, player.pose.bodyYaw, player.state);
            }
            path.expire(now, style.length.getFloat() * 3, 64, 0);
        }
    }

    private void draw(TrailStyleBase style, FxPass pass) {
        TrailAnchor anchor = style.anchor();
        PlayerTrail.StyleState state = player.style(style.getType());
        if (state.alpha <= 0.002F) return;
        float cap = style.maxLength == null ? 64 : style.maxLength.getFloat();
        float smoothing = style.smoothing == null ? 0 : style.smoothing.getFloat() / 100;
        for (int side = 0; side < anchor.sides(); side++) {
            PathRecorder path = player.path(anchor, side);
            if (path.count() == 0) continue;
            anchor.place(player.pose, side, anchorPoint);
            boolean attached = player.growing && path.attached();
            if (style.getType().isPath()) {
                shape.build(path, anchorPoint[0], anchorPoint[1], anchorPoint[2], attached, player.speed, frame.now,
                        Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, state.maxAge, cap, smoothing, 1, frame.eyeX, frame.eyeY, frame.eyeZ);
            } else {
                shape.runs = 0;
                shape.length = 0;
            }
            player.shape = shape;
            player.path = path;
            player.side = side;
            player.headX = anchorPoint[0];
            player.headY = anchorPoint[1];
            player.headZ = anchorPoint[2];
            player.attached = attached;
            player.alpha = state.alpha;
            player.widthScale = 1;
            player.maxAge = state.maxAge;
            player.style = state;
            style.piecesState();
            if (pass == null) style.render(player, frame);
            else style.renderPass(player, frame, pass);
        }
    }
}
