package xyz.daikirai.render.furiah.render;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/** Saves state touched by the Furiah passes and restores Minecraft's cached values. */
public final class GlSave {
    private static final int TEXTURE_UNITS = 4;
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer color = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer clearColor = BufferUtils.createFloatBuffer(16);
    private final ByteBuffer writeMask = BufferUtils.createByteBuffer(16);
    private final int[] textures = new int[TEXTURE_UNITS];
    private final boolean[] textureEnabled = new boolean[TEXTURE_UNITS];
    private int activeTexture, program, drawFramebuffer, readFramebuffer, matrixMode;
    private int depthFunc, alphaFunc, shadeModel, srcRgb, dstRgb, srcAlpha, dstAlpha;
    private float alphaRef, polygonFactor, polygonUnits;
    private boolean blend, depth, cull, alpha, lighting, fog, depthMask, polygonOffset, rescale, colorMaterial;

    public static int boundProgram() {
        return GLContext.getCapabilities().OpenGL20 ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
    }

    public void save() {
        program = boundProgram();
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        if (OpenGlHelper.framebufferSupported) {
            drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            readFramebuffer = GLContext.getCapabilities().OpenGL30
                    ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) : drawFramebuffer;
        }
        matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        viewport.clear();
        color.clear();
        clearColor.clear();
        writeMask.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, writeMask);
        for (int unit = 0; unit < TEXTURE_UNITS; unit++) {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + unit);
            textures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textureEnabled[unit] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        }
        GlStateManager.setActiveTexture(activeTexture);
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        fog = GL11.glIsEnabled(GL11.GL_FOG);
        rescale = GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL);
        colorMaterial = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
        polygonOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        polygonFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
        polygonUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
        shadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    }

    public void restore() {
        if (OpenGlHelper.framebufferSupported && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != drawFramebuffer) {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, drawFramebuffer);
        }
        if (OpenGlHelper.framebufferSupported && GLContext.getCapabilities().OpenGL30
                && GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) != readFramebuffer) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        }
        if (GLContext.getCapabilities().OpenGL20) GL20.glUseProgram(program);
        for (int unit = 0; unit < TEXTURE_UNITS; unit++) {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + unit);
            GlStateManager.bindTexture(textures[unit]);
            if (textureEnabled[unit]) GlStateManager.enableTexture2D();
            else GlStateManager.disableTexture2D();
        }
        GlStateManager.setActiveTexture(activeTexture);
        if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
        if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
        if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
        if (fog) GlStateManager.enableFog(); else GlStateManager.disableFog();
        if (rescale) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
        if (colorMaterial) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial();
        if (polygonOffset) GlStateManager.enablePolygonOffset(); else GlStateManager.disablePolygonOffset();
        GlStateManager.alphaFunc(alphaFunc, alphaRef);
        GlStateManager.doPolygonOffset(polygonFactor, polygonUnits);
        GlStateManager.depthMask(depthMask);
        GlStateManager.depthFunc(depthFunc);
        GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        GlStateManager.shadeModel(shadeModel);
        GlStateManager.colorMask(writeMask.get(0) != 0, writeMask.get(1) != 0, writeMask.get(2) != 0, writeMask.get(3) != 0);
        GlStateManager.clearColor(clearColor.get(0), clearColor.get(1), clearColor.get(2), clearColor.get(3));
        GlStateManager.resetColor();
        GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
        GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        GlStateManager.matrixMode(matrixMode);
    }
}
