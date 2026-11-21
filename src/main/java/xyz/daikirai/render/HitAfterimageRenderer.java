package xyz.daikirai.render;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import xyz.daikirai.gui.onyx.render.Shader;

/** Direct model rendering from HitAfterimage 1.0; no extra mod, FBO or shader is required. */
public final class HitAfterimageRenderer {
    private static final float[] SCALES = {1.03F, 1.08F, 1.14F, 1.21F, 1.29F, 1.39F, 1.51F, 1.64F, 1.78F, 1.95F};
    private static final float[] ALPHAS = {.3F, .255F, .215F, .18F, .15F, .125F, .103F, .084F, .066F, .05F};
    private static final Method PRE_RENDER = ReflectionHelper.findMethod(RendererLivingEntity.class, null,
            new String[]{"preRenderCallback", "func_77041_b"}, EntityLivingBase.class, float.class);
    private final Set<Class<?>> failed = new HashSet<Class<?>>();

    public void render(List<HitAfterimageSnapshot> images, float partial, long now, long lifetime, int rgb, float opacity) {
        if (images.isEmpty() || opacity <= 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        Vec3 camera = WorldRenderState.camera(partial);
        renderAt(images, now, lifetime, rgb, opacity, camera);
    }

    public void renderAt(List<HitAfterimageSnapshot> images, long now, long lifetime, int rgb, float opacity, Vec3 camera) {
        if (images.isEmpty() || opacity <= 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        try (State state = new State()) {
            for (HitAfterimageSnapshot image : images) {
                if (image.localPlayer && mc.gameSettings.thirdPersonView == 0) continue;
                float alpha = image.alpha(now, lifetime) * opacity;
                if (alpha <= .001F) continue;
                Render<?> render = mc.getRenderManager().getEntityRenderObject(image.entity);
                if (!(render instanceof RendererLivingEntity) || failed.contains(render.getClass())) continue;
                ModelBase model = ((RendererLivingEntity<?>) render).getMainModel();
                if (model == null) continue;
                try { draw(image, camera, render, model, rgb, alpha); }
                catch (Exception error) {
                    failed.add(render.getClass());
                    LogManager.getLogger("NeoRaven").warn("Hit Afterimage: unsupported renderer " + render.getClass().getName(), error);
                }
            }
        }
    }

    private void draw(HitAfterimageSnapshot image, Vec3 camera, Render<?> render, ModelBase model, int rgb, float alpha) throws Exception {
        float swing = model.swingProgress;
        boolean riding = model.isRiding, child = model.isChild;
        ModelBiped biped = model instanceof ModelBiped ? (ModelBiped) model : null;
        boolean sneak = biped != null && biped.isSneak, bow = biped != null && biped.aimedBow;
        model.swingProgress = image.swing; model.isRiding = image.riding; model.isChild = image.child;
        if (biped != null) { biped.isSneak = image.sneaking; biped.aimedBow = false; }
        try {
            // The invisible depth silhouette and front-culled expanded shells are upstream's
            // outline recipe. Keep it depth-tested so the ghost cannot show through terrain.
            GL11.glColorMask(false, false, false, false);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_CULL_FACE);
            pass(image, camera, render, model, 1);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
            WorldRenderState.color(rgb, alpha * .22F);
            pass(image, camera, render, model, 1);
            GL11.glCullFace(GL11.GL_FRONT);
            for (int i = 0; i < SCALES.length; i++) {
                float jitter = MathHelper.sin((image.entity.getEntityId() + i) * 12.9898F) * .5F;
                WorldRenderState.color(rgb, alpha * ALPHAS[i] * (.85F + .15F * (jitter + 1)));
                pass(image, camera, render, model, SCALES[i]);
            }
        } finally {
            GL11.glColorMask(true, true, true, true);
            model.swingProgress = swing; model.isRiding = riding; model.isChild = child;
            if (biped != null) { biped.isSneak = sneak; biped.aimedBow = bow; }
        }
    }

    private void pass(HitAfterimageSnapshot image, Vec3 camera, Render<?> render, ModelBase model, float scale) throws Exception {
        GlStateManager.pushMatrix();
        try {
            // Subtract in double precision, including at large world coordinates.
            GlStateManager.translate(image.x - camera.xCoord, image.y - camera.yCoord, image.z - camera.zCoord);
            GlStateManager.rotate(180 - image.bodyYaw, 0, 1, 0);
            GlStateManager.scale(-1, -1, 1);
            // Invoke the base virtual method: works for both MCP and remapped SRG names and
            // preserves mob-specific scale callbacks (giants, slimes, etc.).
            PRE_RENDER.invoke(render, image.entity, 0F);
            GlStateManager.translate(0, -1.5078125F, 0);
            GlStateManager.scale(scale, scale, scale);
            model.render(image.entity, image.limbSwing, image.limbAmount, image.age,
                    image.headYaw - image.bodyYaw, image.pitch, .0625F);
        } finally { GlStateManager.popMatrix(); }
    }

    /** Isolate fixed-function effects from fog/lightmap/shader leftovers, restoring caller state. */
    private static final class State implements AutoCloseable {
        private final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        private final int program = OpenGlHelper.shadersSupported ? GL11.glGetInteger(0x8B8D) : 0;
        private final int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int[] textures = new int[Math.max(1, Math.min(32, GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS)))];
        private final boolean[] enabled = new boolean[textures.length];
        private final FloatBuffer color = BufferUtils.createFloatBuffer(16);

        State() {
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            for (int unit = 0; unit < textures.length; unit++) {
                OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + unit);
                textures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                enabled[unit] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            activeTexture(OpenGlHelper.defaultTexUnit);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            Shader.unbind();
            for (int cap : new int[]{GL11.GL_LIGHTING, GL11.GL_FOG, GL11.GL_ALPHA_TEST,
                    GL11.GL_COLOR_MATERIAL, GL11.GL_COLOR_LOGIC_OP, 0x803A}) GL11.glDisable(cap);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
        }

        @Override public void close() {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.tryBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            GlStateManager.resetColor();
            GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
            for (int unit = 0; unit < textures.length; unit++) {
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + unit);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[unit]);
                GlStateManager.bindTexture(textures[unit]);
                GlStateManager.disableTexture2D();
                if (enabled[unit]) GlStateManager.enableTexture2D();
            }
            GL11.glPopAttrib();
            if (OpenGlHelper.shadersSupported) OpenGlHelper.glUseProgram(program);
            activeTexture(active);
            GL11.glMatrixMode(matrix);
        }

        private static void activeTexture(int unit) {
            GlStateManager.setActiveTexture(unit == OpenGlHelper.defaultTexUnit ? OpenGlHelper.lightmapTexUnit : OpenGlHelper.defaultTexUnit);
            GlStateManager.setActiveTexture(unit);
        }
    }
}
