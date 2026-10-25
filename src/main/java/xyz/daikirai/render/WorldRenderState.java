package xyz.daikirai.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.render.Shader;

/** Raw GL changes stay inside this scope to preserve Minecraft's cached state. */
public final class WorldRenderState implements AutoCloseable {
    private final int active = GL11.glGetInteger(0x84E0);
    private final int program = OpenGlHelper.shadersSupported ? GL11.glGetInteger(0x8B8D) : 0;
    private final int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);

    public WorldRenderState(boolean throughWalls) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        Shader.unbind();
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glDepthMask(false);
        if (throughWalls) GL11.glDisable(GL11.GL_DEPTH_TEST);
        else GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
    }

    @Override public void close() {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        if (OpenGlHelper.shadersSupported) OpenGlHelper.glUseProgram(program);
        OpenGlHelper.setActiveTexture(active);
        GL11.glMatrixMode(matrix);
    }

    public static Vec3 position(Entity entity, float partial) {
        return new Vec3(entity.prevPosX + (entity.posX - entity.prevPosX) * partial,
                entity.prevPosY + (entity.posY - entity.prevPosY) * partial,
                entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partial);
    }

    public static Vec3 camera(float partial) { return position(Minecraft.getMinecraft().getRenderViewEntity(), partial); }

    public static void color(int rgb, double opacity) {
        GL11.glColor4d((rgb >> 16 & 255) / 255.0, (rgb >> 8 & 255) / 255.0, (rgb & 255) / 255.0, Math.max(0, Math.min(1, opacity)));
    }

    public static int mix(int a, int b, double t) {
        int r = (int)((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = (int)((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = (int)((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    public static void box(AxisAlignedBB b, boolean outline) {
        double[][] p = {{b.minX,b.minY,b.minZ},{b.maxX,b.minY,b.minZ},{b.maxX,b.maxY,b.minZ},{b.minX,b.maxY,b.minZ},
                {b.minX,b.minY,b.maxZ},{b.maxX,b.minY,b.maxZ},{b.maxX,b.maxY,b.maxZ},{b.minX,b.maxY,b.maxZ}};
        int[] indices = outline ? new int[]{0,1,1,2,2,3,3,0,4,5,5,6,6,7,7,4,0,4,1,5,2,6,3,7}
                : new int[]{0,1,2,3,4,7,6,5,0,4,5,1,3,2,6,7,0,3,7,4,1,5,6,2};
        GL11.glBegin(outline ? GL11.GL_LINES : GL11.GL_QUADS);
        for (int i : indices) GL11.glVertex3d(p[i][0],p[i][1],p[i][2]);
        GL11.glEnd();
    }
}
