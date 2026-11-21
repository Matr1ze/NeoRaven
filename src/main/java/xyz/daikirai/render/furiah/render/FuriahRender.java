package xyz.daikirai.render.furiah.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.kit.KitTextures;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.setting.ThemeTint;

public final class FuriahRender {
    private static boolean warmed;
    private final GlSave state = new GlSave();
    private final Minecraft mc = Minecraft.getMinecraft();

    public void draw(Camera camera, int color, boolean throughWalls, Runnable draw) {
        state.save();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try (ThemeTint.Scope tint = ThemeTint.use(color)) {
            if (OpenGlHelper.shadersSupported) OpenGlHelper.glUseProgram(0);
            Textures.ensureLoaded();
            if (!warmed) {
                KitTextures.warmUp();
                warmed = true;
            }
            KitTextures.uploadReady();
            RenderState.begin(throughWalls);
            try {
                camera.captureModelView();
                Vfx.setCamera(camera);
                FxFrame.nextFrame();
                draw.run();
            } finally {
                RenderState.end();
            }
        } finally {
            try {
                state.restore();
            } finally {
                GL11.glPopAttrib();
            }
        }
    }

    public void fill(FxFrame frame, Camera camera, float pt, float dt, long now) {
        RenderManager rm = mc.getRenderManager();
        Entity view = mc.getRenderViewEntity();
        frame.cam = camera;
        frame.viewX = rm.viewerPosX;
        frame.viewY = rm.viewerPosY;
        frame.viewZ = rm.viewerPosZ;
        frame.eyeX = frame.viewX + camera.posX;
        frame.eyeY = frame.viewY + camera.posY;
        frame.eyeZ = frame.viewZ + camera.posZ;
        frame.partialTicks = pt;
        frame.dt = dt;
        frame.now = now;
        frame.frameId = FxFrame.currentFrame();
        frame.world = mc.theWorld;
        frame.firstPerson = view == mc.thePlayer && mc.gameSettings.thirdPersonView == 0;
        frame.preview = false;
    }

    public void fillPreview(FxFrame frame, Camera camera, float dt, long now) {
        frame.cam = camera;
        frame.viewX = frame.viewY = frame.viewZ = 0;
        frame.eyeX = camera.posX;
        frame.eyeY = camera.posY;
        frame.eyeZ = camera.posZ;
        frame.partialTicks = 1;
        frame.dt = dt;
        frame.now = now;
        frame.frameId = FxFrame.currentFrame();
        frame.world = null;
        frame.firstPerson = false;
        frame.preview = true;
    }

    public static void pass(FxPass pass) {
        RenderState.decal(false);
        if (pass == FxPass.GLOW) RenderState.additive();
        else RenderState.translucent(pass == FxPass.SOLID);
    }
}
