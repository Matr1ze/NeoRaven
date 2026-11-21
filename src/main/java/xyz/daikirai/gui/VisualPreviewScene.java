package xyz.daikirai.gui;

import com.mojang.authlib.GameProfile;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.WorldInfo;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.util.glu.GLU;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.render.BedESPModule;
import xyz.daikirai.module.render.EspModule;
import xyz.daikirai.module.render.JumpCirclesModule;
import xyz.daikirai.module.render.MobESPModule;
import xyz.daikirai.module.render.PlayerESPModule;
import xyz.daikirai.module.render.TrailsModule;
import xyz.daikirai.module.render.WingsModule;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.HitAfterimageRenderer;
import xyz.daikirai.render.HitAfterimageSnapshot;
import xyz.daikirai.render.JumpCircleRenderer;
import xyz.daikirai.render.TargetEspRenderer;
import xyz.daikirai.render.WorldRenderState;
import xyz.daikirai.render.furiah.module.hit.FuriahHitEffects;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.landing.FuriahJumpEffects;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.module.targetaura.FuriahTargetAura;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.render.GlSave;
import xyz.daikirai.util.EspShaders;
import xyz.daikirai.util.Render3D;
import xyz.daikirai.util.RenderUtils;

/** Renders an unregistered display actor and preview-only effects into a private color/depth target. */
final class VisualPreviewScene {
    private static final int WIDTH = 256, HEIGHT = 320;
    static final float ASPECT = WIDTH / (float) HEIGHT;
    private static final Vec3 ORIGIN = new Vec3(0, 0, 0);
    private static final HitStyle[] HIT = HitStyle.values();
    private static final LandingStyle[] LAND = LandingStyle.values();
    private static final EffectType[] AURA = EffectType.values();
    private static final TrailStyle[] TRAIL = TrailStyle.values();
    private static final GameProfile DEFAULT_PROFILE = new GameProfile(new UUID(0, 0), "Steve");
    private final Minecraft mc = Minecraft.getMinecraft();
    private final GlSave gl = new GlSave();
    private final ModelPlayer wide = new ModelPlayer(0, false);
    private final ModelPlayer slim = new ModelPlayer(0, true);
    private final JumpCircleRenderer rings = new JumpCircleRenderer();
    private final TargetEspRenderer mark = new TargetEspRenderer();
    private final HitAfterimageRenderer ghosts = new HitAfterimageRenderer();
    private FuriahJumpEffects landing;
    private FuriahHitEffects hit;
    private FuriahTargetAura aura;
    private FuriahTrails trails;
    private Target target, mask;
    private Shader halo;
    private Module module;
    private int style = -1;
    private double time;
    private int cycle = -1, fired;
    private long circleAt = -1;
    private EntityOtherPlayerMP actor;
    private World displayWorld;
    private boolean failed;

    int render(Module selected, Scheme scheme, float dt) {
        if (!OpenGlHelper.framebufferSupported) return 0;
        int currentStyle = style(selected);
        if (module != selected || style != currentStyle) reset(selected, currentStyle);
        if (failed) return 0;
        dt = Math.max(0, Math.min(dt, 0.075F));
        time += dt;
        GameProfile profile = mc.thePlayer == null ? DEFAULT_PROFILE : mc.thePlayer.getGameProfile();
        RenderManager manager = mc.getRenderManager();
        double oldX = manager.viewerPosX, oldY = manager.viewerPosY, oldZ = manager.viewerPosZ;
        float oldYaw = manager.playerViewY;
        gl.save();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        pushMatrices();
        try {
            if (displayWorld == null) displayWorld = new DisplayWorld();
            if (actor == null || !actor.getGameProfile().equals(profile)) actor = new DisplayPlayer(displayWorld, profile);
            if (target == null) target = new Target();
            target.bind();
            clear(scheme.surfaceContainerLow, 1);
            if (OpenGlHelper.shadersSupported) OpenGlHelper.glUseProgram(0);
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.disableFog();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            manager.viewerPosX = manager.viewerPosY = manager.viewerPosZ = 0;
            manager.playerViewY = 28;
            double zoom = zoom(selected);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GLU.gluPerspective(33, ASPECT, 0.06F, 120);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            boolean trailsMoving = selected instanceof TrailsModule;
            GLU.gluLookAt((float) (zoom * (trailsMoving ? 0.20 : 0.44)), (float) (1.04 + zoom * .26), (float) zoom,
                    0, 0.95F, 0, 0, 1, 0);
            Vec3 position = pose(selected);
            drawStage(scheme);
            drawActor();
            drawEffect(selected, position, dt, scheme);
            return target.texture;
        } catch (RuntimeException | LinkageError error) {
            failed = true;
            LogManager.getLogger("NeoRaven").warn("Visual preview failed for " + selected.getName(), error);
            return 0;
        } finally {
            manager.viewerPosX = oldX;
            manager.viewerPosY = oldY;
            manager.viewerPosZ = oldZ;
            manager.playerViewY = oldYaw;
            popMatrices();
            try {
                gl.restore();
            } finally {
                GL11.glPopAttrib();
            }
        }
    }

    private void reset(Module selected, int currentStyle) {
        module = selected;
        style = currentStyle;
        cycle = -1;
        fired = 0;
        circleAt = -1;
        time = 0;
        failed = false;
        if (landing != null) landing.clear();
        if (hit != null) hit.clear();
        if (trails != null) trails.clear();
        if (aura != null) aura.clear();
        rings.reset();
        mark.reset();
    }

    void close() {
        reset(null, -1);
        if (target != null) target.close();
        if (mask != null) mask.close();
        target = mask = null;
        rings.dispose();
        actor = null;
        displayWorld = null;
    }

    private double zoom(Module selected) {
        switch (selected.getName()) {
            case "Wings": return Math.max(6.0, 4.6 * value(selected, "Scale", 1));
            case "JumpCircles": return Math.max(5.8, value(selected, "Radius", 1.5) * 3.4);
            case "Hit Effect": return Math.max(5.6, value(selected, "Size", 1) * 4.2);
            case "Target ESP": return Math.max(5.6, value(selected, "Size", 1) * 3.8);
            case "Trails": case "BedESP": case "ChestESP": case "ItemESP": return 7.3;
            default: return 5.3;
        }
    }

    private Vec3 pose(Module selected) {
        Vec3 p = ORIGIN, before = ORIGIN;
        boolean walking = selected instanceof TrailsModule;
        float yaw = (float) (Math.sin(time * 0.72) * 19 + 6);
        if (walking) {
            p = trailPath(time);
            before = trailPath(time - 0.05);
            Vec3 next = trailPath(time + 0.02);
            yaw = (float) Math.toDegrees(Math.atan2(p.xCoord - next.xCoord, next.zCoord - p.zCoord));
        } else if (selected instanceof WingsModule) {
            yaw = (float) (Math.sin(time * 0.75) * 24 - 12);
        } else if (selected instanceof JumpCirclesModule) {
            double local = time % jumpPeriod((JumpCirclesModule) selected);
            double t = Math.max(0, Math.min(1, (local - 0.14) / 0.62));
            p = new Vec3(0, 2.8 * t * (1 - t), 0);
            before = p;
        } else if (isProp(selected)) {
            p = before = new Vec3(-0.57, 0, -0.35);
        }
        actor.setPosition(p.xCoord, p.yCoord, p.zCoord);
        actor.lastTickPosX = actor.prevPosX = before.xCoord;
        actor.lastTickPosY = actor.prevPosY = before.yCoord;
        actor.lastTickPosZ = actor.prevPosZ = before.zCoord;
        actor.prevRotationYaw = actor.rotationYaw = yaw;
        actor.prevRenderYawOffset = actor.renderYawOffset = yaw;
        actor.prevRotationYawHead = actor.rotationYawHead = yaw;
        actor.limbSwing = walking ? (float) time * 8 : 0;
        actor.prevLimbSwingAmount = actor.limbSwingAmount = walking ? 0.75F : 0;
        actor.onGround = p.yCoord < 0.01;
        if (actor.isSprinting() != walking) actor.setSprinting(walking);
        return p;
    }

    private static boolean isProp(Module m) {
        return m.getName().equals("BedESP") || m.getName().equals("ChestESP") || m.getName().equals("ItemESP");
    }

    private static Vec3 trailPath(double time) {
        double t = time * 1.9 - 1.0;
        return new Vec3(Math.sin(t) * 1.27, 0, Math.cos(t) * 0.53);
    }

    private void drawActor() {
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(mc.thePlayer == null
                ? DefaultPlayerSkin.getDefaultSkin(DEFAULT_PROFILE.getId()) : mc.thePlayer.getLocationSkin());
        ModelPlayer model = mc.thePlayer != null && "slim".equals(mc.thePlayer.getSkinType()) ? slim : wide;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(actor.posX, actor.posY, actor.posZ);
            GlStateManager.rotate(180 - actor.renderYawOffset, 0, 1, 0);
            GlStateManager.scale(-1, -1, 1);
            GlStateManager.translate(0, -1.5078125F, 0);
            model.render(actor, actor.limbSwing, actor.limbSwingAmount, (float) time * 20, 0, 0, 0.0625F);
        } finally {
            GlStateManager.popMatrix();
            RenderHelper.disableStandardItemLighting();
        }
    }

    private static void drawStage(Scheme s) {
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        WorldRenderState.color(s.surfaceContainer, 1);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(-9, -.028, -9);
        GL11.glVertex3d(9, -.028, -9);
        GL11.glVertex3d(9, -.028, 9);
        GL11.glVertex3d(-9, -.028, 9);
        GL11.glEnd();
        WorldRenderState.color(Material.mix(s.surfaceContainer, s.outlineVariant, .48F), 1);
        GL11.glLineWidth(1);
        GL11.glBegin(GL11.GL_LINES);
        for (int i = -8; i <= 8; i++) {
            GL11.glVertex3d(i, -.025, -9);
            GL11.glVertex3d(i, -.025, 9);
            GL11.glVertex3d(-9, -.025, i);
            GL11.glVertex3d(9, -.025, i);
        }
        GL11.glEnd();
        GlStateManager.resetColor();
    }

    private void drawEffect(Module m, Vec3 position, float dt, Scheme scheme) {
        int rgb = color(m, scheme.primary);
        switch (m.getName()) {
            case "ESP": ((EspModule) m).preview(actor); break;
            case "PlayerESP":
                int type = PlayerESPModule.Type.values()[style].mode;
                int playerColor = toggle(m, "Team color", false) ? 0xFF55FF55 : rgbSetting(m);
                if (toggle(m, "Red on damage", false) && time % 2.4 < .35) playerColor = 0xFFEF5566;
                if (type < 0) glow(playerColor, type == -1 ? 0 : type == -2 ? 4.0F : 9.0F, true);
                else if (type != 0) Render3D.entityBox(actor, type, playerColor, 1);
                if (toggle(m, "Health bar", false)) Render3D.entityBox(actor, 4, playerColor, 1);
                break;
            case "MobESP":
                int mobColor = mobColor();
                if (MobESPModule.shaded.isToggled()) Render3D.shadedBox(actor, mobColor & 0xFFFFFF | 0x5A000000, 1);
                if (MobESPModule.outline.isToggled()) Render3D.entityBox(actor, 1, mobColor, 1);
                if (MobESPModule.healthBar.isToggled()) Render3D.entityBox(actor, 4, mobColor, 1);
                break;
            case "Glow ESP": glow(rgb, (float) value(m, "Glow radius", 7), false); break;
            case "Target ESP": target(m, rgb, dt); break;
            case "Trails":
                if (style < 2) ((TrailsModule) m).previewLegacy(VisualPreviewScene::trailPath, time);
                else {
                    if (trails == null) trails = new FuriahTrails();
                    trails.renderPreview(TRAIL[style - 2], actor, value(m, "Length", 1), value(m, "Opacity", 60),
                            rgb, toggle(m, "Through walls", false), time, dt);
                }
                break;
            case "JumpCircles": jump((JumpCirclesModule) m, dt); break;
            case "Hit Effect": strike(m, rgb, dt); break;
            case "Hit Afterimage": afterimage(m, rgb); break;
            case "Wings": ((WingsModule) m).preview(position, actor.renderYawOffset, time); break;
            case "BedESP": bed(m); break;
            case "ChestESP":
                prop(new ItemStack(Blocks.chest), 0.64, .40, .32, .85);
                box(new AxisAlignedBB(.24, .01, -.08, 1.04, .81, .72), rgbSetting(m),
                        toggle(m, "Outline", true), toggle(m, "Shade", false), .36F);
                break;
            case "ItemESP": items(m); break;
            default: break;
        }
    }

    private void target(Module m, int rgb, float dt) {
        float size = (float) value(m, "Size", 1);
        float speed = (float) value(m, "Speed", 4) / 4;
        float strength = (float) value(m, "Glow", 75) / 100;
        boolean middle = value(m, "Start", 0) == 1;
        if (style == 0) {
            mark.tick(dt * 1000);
            if (pulse(2.3)) mark.strike(3, 1);
            mark.render(actor, 1, 1, time * speed, actor.width * 1.35F * size, size, strength, rgb,
                    middle ? actor.height * .025 : 0);
        } else if (style == 1) {
            mark.renderRing(actor, 1, 1, (float) time * 120 * speed - (middle ? 0 : 90), size, strength, rgb);
        } else if (style == 2) {
            box(actor.getEntityBoundingBox().expand(.05 * size, .05 * size, .05 * size), rgb, false, true, .16F);
        } else {
            if (aura == null) aura = new FuriahTargetAura();
            aura.renderPreview(AURA[style - 3], actor, dt, size, speed, strength, rgb);
        }
    }

    private static double jumpPeriod(JumpCirclesModule m) { return Math.max(1.65, m.lifetime() / 1000.0 + .72); }

    private void jump(JumpCirclesModule m, float dt) {
        double period = jumpPeriod(m), local = time % period;
        int up = style >= 4 ? (int) value(m, "Furiah trigger", 0) : style >= 2 ? 1 : 0;
        pulse(period);
        if (local >= .14 && (fired & 1) == 0 && up != 1) jumpPulse(m, true, 1);
        if (local >= .76 && (fired & 2) == 0 && up != 0) jumpPulse(m, false, 2);
        if (style >= 4) {
            if (landing != null) landing.renderPreview(m, dt);
        } else if (circleAt >= 0) {
            long now = Math.round(time * 1000);
            if (now - circleAt < m.lifetime()) rings.renderAt(Collections.singletonList(new JumpCirclesModule.Circle(ORIGIN, circleAt)),
                    m, ORIGIN, now, WIDTH, HEIGHT);
        }
    }

    private void jumpPulse(JumpCirclesModule m, boolean jumping, int bit) {
        fired |= bit;
        if (style < 4) circleAt = Math.round(time * 1000);
        else {
            if (landing == null) landing = new FuriahJumpEffects();
            landing.previewSpawn(LAND[style - 4], m, jumping, actor.renderYawOffset);
        }
    }

    private void strike(Module m, int rgb, float dt) {
        if (hit == null) hit = new FuriahHitEffects();
        float speed = (float) value(m, "Speed", 1);
        if (pulse(Math.max(1.6, 2.4 / speed)))
            hit.previewSpawn(HIT[style], rgb, (float) value(m, "Size", 1), speed, (float) value(m, "Opacity", 85) / 100,
                    actor.renderYawOffset);
        hit.renderPreview(rgb, toggle(m, "Through walls", false), dt);
    }

    private void afterimage(Module m, int rgb) {
        double duration = value(m, "Duration", 1.38);
        long now = Math.round(time * 1e9), local = Math.round((time % (duration + .35)) * 1e9);
        Vec3 previous = new Vec3(actor.posX, actor.posY, actor.posZ);
        actor.setPosition(previous.xCoord - .36, previous.yCoord, previous.zCoord - .19);
        try {
            ghosts.renderAt(Collections.singletonList(new HitAfterimageSnapshot(actor, now - local)), now,
                    Math.round(duration * 1e9), rgb, (float) value(m, "Opacity", 100) / 100, ORIGIN);
        } finally {
            actor.setPosition(previous.xCoord, previous.yCoord, previous.zCoord);
        }
    }

    private boolean pulse(double period) {
        int next = (int) Math.floor(time / period);
        if (next == cycle) return false;
        cycle = next;
        fired = 0;
        return true;
    }

    private void glow(int rgb, float radius, boolean outline) {
        if (!OpenGlHelper.shadersSupported) {
            box(actor.getEntityBoundingBox().expand(.09, .09, .09), rgb, true, false, .5F);
            return;
        }
        boolean outlinePass = outline && EspShaders.valid(EspShaders.fill()) && EspShaders.valid(EspShaders.outline());
        if (radius > 0 && halo == null) halo = Shader.load("onyx_entity_glow", "onyx_jump_glass");
        boolean glowPass = radius > 0 && halo.valid();
        if (!outlinePass && !glowPass) return;
        if (mask == null) mask = new Target();
        mask.bind();
        clear(0, 0);
        if (outlinePass) EspShaders.useFill(rgb);
        try {
            drawActor();
        } finally {
            Shader.unbind();
        }
        target.bind();
        pushMatrices();
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
            GlStateManager.bindTexture(mask.texture);
            if (glowPass) {
                halo.use();
                halo.uniform1i("mask", 0);
                halo.uniform2f("texel", 1F / WIDTH, 1F / HEIGHT);
                halo.uniform1f("radius", radius);
                halo.uniform3f("tint", (rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F);
                quad(-1, -1, 2, 2, false);
            }
            if (outlinePass) {
                xyz.daikirai.util.Shader program = EspShaders.outline();
                program.use();
                program.uniformi("u_texture", 0);
                program.uniformf("u_texel_size", 1F / WIDTH, 1F / HEIGHT);
                program.uniformf("u_radius", 2);
                program.uniformf("u_alpha_threshold", .1F);
                quad(-1, -1, 2, 2, false);
            }
        } finally {
            Shader.unbind();
            popMatrices();
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
        }
    }

    private void bed(Module m) {
        box(new AxisAlignedBB(.16, 0, -.15, 1.24, .21, .59), 0xFF705343, false, true, 1);
        box(new AxisAlignedBB(.16, .20, -.15, 1.24, .42, .59), 0xFFB64C58, false, true, 1);
        box(new AxisAlignedBB(.94, .40, -.12, 1.22, .46, .56), 0xFFF3EAE1, false, true, 1);
        if (value(m, "Covering blocks", 0) > 0)
            box(new AxisAlignedBB(.16, .47, -.15, 1.24, .88, .59), 0xFF9C927C, true, true, .36F);
        int rgb = BedESPModule.theme.getRGB(0);
        box(new AxisAlignedBB(.13, -.005, -.18, 1.27, .49, .62), rgb, false, true, .42F);
        if (toggle(m, "Draw beam", false))
            box(new AxisAlignedBB(.62, .5, .14, .78, 3.8, .30), 0xFF83D5FF, false, true, .29F);
    }

    private void items(Module m) {
        Item[] items = {Items.iron_ingot, Items.gold_ingot, Items.diamond, Items.emerald};
        String[] labels = {"Disable iron", "Disable gold", "Disable diamond", "Disable emerald"};
        int[] colors = {0xFFE2E2E2, 0xFFF0BC45, 0xFF5DF0E7, 0xFF2DE67D};
        for (int i = 0; i < items.length; i++) {
            double x = .52 + (i % 2) * .5, z = -.18 + (i / 2) * .53;
            double height = .22 + Math.sin(time * 3 + i) * .06;
            prop(new ItemStack(items[i]), x, height, z, .34);
            if (!toggle(m, labels[i], false))
                box(new AxisAlignedBB(x - .18, .01, z - .18, x + .18, height + .2, z + .18), colors[i], true, false, .75F);
        }
    }

    private void prop(ItemStack stack, double x, double y, double z, double scale) {
        GlStateManager.pushMatrix();
        RenderHelper.enableStandardItemLighting();
        try {
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate((float) (time * 35), 0, 1, 0);
            GlStateManager.scale(scale, scale, scale);
            mc.getRenderItem().renderItem(stack, TransformType.FIXED);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
    }

    private static void box(AxisAlignedBB bounds, int rgb, boolean outline, boolean fill, float alpha) {
        try (WorldRenderState state = new WorldRenderState(true)) {
            if (fill) {
                WorldRenderState.color(rgb, alpha);
                WorldRenderState.box(bounds, false);
            }
            if (outline) {
                WorldRenderState.color(rgb, 1);
                GL11.glLineWidth(1.65F);
                RenderGlobal.drawSelectionBoundingBox(bounds);
            }
        }
    }

    private static int style(Module m) {
        if (m instanceof PlayerESPModule) return (int) value(m, "Type", 1);
        return (int) value(m, m instanceof TrailsModule || m instanceof JumpCirclesModule ? "Mode" : "Style", 0);
    }

    private static int mobColor() {
        if (MobESPModule.zombie.isToggled()) return 0xFF5076FF;
        if (MobESPModule.creeper.isToggled() || MobESPModule.slime.isToggled()) return 0xFF71DC63;
        if (MobESPModule.blaze.isToggled()) return 0xFFFFB749;
        if (MobESPModule.witch.isToggled()) return 0xFFDD61DF;
        return 0xFFFFFFFF;
    }

    private static int rgbSetting(Module m) {
        if (toggle(m, "Rainbow", false)) return RenderUtils.rainbow(2L);
        return 0xFF000000 | (int) value(m, "Red", 255) << 16 | (int) value(m, "Green", 255) << 8 | (int) value(m, "Blue", 255);
    }

    private static int color(Module m, int fallback) {
        Setting setting = setting(m, "Color");
        return setting instanceof ColorSetting ? ((ColorSetting) setting).color() : fallback;
    }

    private static boolean toggle(Module m, String name, boolean fallback) {
        Setting setting = setting(m, name);
        return setting instanceof ButtonSetting ? ((ButtonSetting) setting).isToggled() : fallback;
    }

    private static double value(Module m, String name, double fallback) {
        Setting setting = setting(m, name);
        return setting instanceof SliderSetting ? ((SliderSetting) setting).getInput() : fallback;
    }

    private static Setting setting(Module m, String name) {
        for (Setting setting : m.persistedSettings()) if (name.equals(setting.getName())) return setting;
        return null;
    }

    private static void pushMatrices() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
    }

    private static void popMatrices() {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private static void clear(int color, float alpha) {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.depthMask(true);
        GlStateManager.clearColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, alpha);
        GL11.glClearDepth(1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    static void blit(int texture, float x, float y, float w, float h, float alpha) {
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glColor4f(1, 1, 1, alpha);
            quad(x, y, w, h, true);
        } finally {
            GL11.glPopAttrib();
            OpenGlHelper.setActiveTexture(activeTexture);
        }
    }

    private static void quad(float x, float y, float w, float h, boolean flip) {
        float top = flip ? 1 : 0, bottom = 1 - top;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, top); GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(1, top); GL11.glVertex2f(x + w, y);
        GL11.glTexCoord2f(1, bottom); GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(0, bottom); GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    private static final class Target {
        private int fbo = -1, texture = -1, depth = -1;

        private Target() {
            int renderbuffer = GL11.glGetInteger(0x8CA7);
            try {
                texture = GlStateManager.generateTexture();
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
                GlStateManager.bindTexture(texture);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, WIDTH, HEIGHT, 0, GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);
                fbo = OpenGlHelper.glGenFramebuffers();
                bind();
                OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
                depth = OpenGlHelper.glGenRenderbuffers();
                OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, depth);
                OpenGlHelper.glRenderbufferStorage(OpenGlHelper.GL_RENDERBUFFER, 33190, WIDTH, HEIGHT);
                OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT, OpenGlHelper.GL_RENDERBUFFER, depth);
                if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE)
                    throw new IllegalStateException("Preview framebuffer incomplete");
            } catch (RuntimeException | LinkageError error) {
                close();
                throw error;
            } finally {
                OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, renderbuffer);
            }
        }

        private void bind() {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
            GL11.glViewport(0, 0, WIDTH, HEIGHT);
        }

        private void close() {
            if (depth >= 0) OpenGlHelper.glDeleteRenderbuffers(depth);
            if (fbo >= 0) OpenGlHelper.glDeleteFramebuffers(fbo);
            if (texture >= 0) GlStateManager.deleteTexture(texture);
        }
    }

    private static final class DisplayWorld extends World {
        private DisplayWorld() {
            super(null, new WorldInfo(new WorldSettings(0, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT),
                    "Visual preview"), new WorldProviderSurface(), Minecraft.getMinecraft().mcProfiler, true);
            chunkProvider = createChunkProvider();
        }

        @Override public BlockPos getSpawnPoint() { return BlockPos.ORIGIN; }
        @Override protected IChunkProvider createChunkProvider() { return new ChunkProviderClient(this); }
        @Override protected int getRenderDistanceChunks() { return 1; }
    }

    private static final class DisplayPlayer extends EntityOtherPlayerMP {
        private DisplayPlayer(World world, GameProfile profile) { super(world, profile); }
        @Override public boolean isSpectator() { return false; }
        @Override public ResourceLocation getLocationSkin() {
            Minecraft mc = Minecraft.getMinecraft();
            return mc.thePlayer == null ? DefaultPlayerSkin.getDefaultSkin(getUniqueID()) : mc.thePlayer.getLocationSkin();
        }
        @Override public String getSkinType() {
            Minecraft mc = Minecraft.getMinecraft();
            return mc.thePlayer == null ? "default" : mc.thePlayer.getSkinType();
        }
    }
}
