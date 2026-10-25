package xyz.daikirai.render;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Restores both driver state and MC's cached switches after the extra vanilla hand draw. */
final class HandState implements AutoCloseable {
    final int framebuffer = GL11.glGetInteger(0x8CA6);
    final int[] viewport = new int[4];
    private final int active = GL11.glGetInteger(0x84E0);
    private final int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
    private final int program = GL11.glGetInteger(0x8B8D);
    private final int[] textures = new int[2];
    private final boolean[] textured = new boolean[2];
    private final int[] units = {OpenGlHelper.defaultTexUnit, OpenGlHelper.lightmapTexUnit};
    private final int[] flags = {GL11.GL_BLEND, GL11.GL_ALPHA_TEST, GL11.GL_DEPTH_TEST,
            GL11.GL_LIGHTING, GL11.GL_CULL_FACE, GL11.GL_FOG, 32826, GL11.GL_COLOR_MATERIAL};
    private final boolean[] enabled = new boolean[flags.length];
    private final boolean[] lights = new boolean[8];
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final int alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
    private final float alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
    private final int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int shade = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
    private final float[] color = floats(GL11.GL_CURRENT_COLOR);
    private final float[] clear = floats(GL11.GL_COLOR_CLEAR_VALUE);
    private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);

    HandState() {
        IntBuffer buffer = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, buffer);
        for (int i = 0; i < 4; i++) viewport[i] = buffer.get(i);
        for (int i = 0; i < flags.length; i++) enabled[i] = GL11.glIsEnabled(flags[i]);
        for (int i = 0; i < lights.length; i++) lights[i] = GL11.glIsEnabled(GL11.GL_LIGHT0 + i);
        for (int i = 0; i < units.length; i++) {
            GlStateManager.setActiveTexture(units[i]);
            textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textured[i] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        }
        GlStateManager.setActiveTexture(active);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    @Override public void close() {
        for (int i = 0; i < flags.length; i++) restore(i, enabled[i]);
        for (int i = 0; i < lights.length; i++) {
            if (lights[i]) GlStateManager.enableLight(i); else GlStateManager.disableLight(i);
        }
        GlStateManager.depthMask(depthMask);
        GlStateManager.depthFunc(depthFunc);
        GlStateManager.alphaFunc(alphaFunc, alphaRef);
        GlStateManager.tryBlendFuncSeparate(src, dst, srcA, dstA);
        GlStateManager.shadeModel(shade);
        GlStateManager.clearColor(clear[0], clear[1], clear[2], clear[3]);
        GlStateManager.clearDepth(clearDepth);
        GlStateManager.resetColor();
        GlStateManager.color(color[0], color[1], color[2], color[3]);
        for (int i = 0; i < units.length; i++) {
            GlStateManager.setActiveTexture(units[i]);
            GlStateManager.bindTexture(textures[i]);
            if (textured[i]) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
        }
        GlStateManager.setActiveTexture(active);
        // Draw/read buffer attributes belong to the framebuffer active when they were saved.
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        GL11.glPopAttrib();
        OpenGlHelper.glUseProgram(program);
        GlStateManager.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GlStateManager.matrixMode(matrix);
    }

    private void restore(int flag, boolean value) {
        switch (flag) {
            case 0: if (value) GlStateManager.enableBlend(); else GlStateManager.disableBlend(); break;
            case 1: if (value) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha(); break;
            case 2: if (value) GlStateManager.enableDepth(); else GlStateManager.disableDepth(); break;
            case 3: if (value) GlStateManager.enableLighting(); else GlStateManager.disableLighting(); break;
            case 4: if (value) GlStateManager.enableCull(); else GlStateManager.disableCull(); break;
            case 5: if (value) GlStateManager.enableFog(); else GlStateManager.disableFog(); break;
            case 6: if (value) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal(); break;
            case 7: if (value) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial(); break;
            default: break;
        }
    }

    private static float[] floats(int name) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(name, buffer);
        return new float[]{buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3)};
    }
}
