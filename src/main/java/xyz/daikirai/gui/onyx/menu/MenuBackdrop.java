package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.gui.onyx.render.UI;

public final class MenuBackdrop {
    private static Shader clouds;
    private static final long START = System.nanoTime();
    private static int seed;
    private static int deep, mid, accent, glow;
    private MenuBackdrop() { }

    public static void draw(UI ui, int width, int height) {
        if (clouds == null) clouds = Shader.load("onyx_clouds");
        if (seed != Theme.accent()) {
            seed = Theme.accent();
            Material.Hct hct = Material.Hct.fromInt(seed);
            double chroma = hct.chroma * 0.6;
            deep = Material.TonalPalette.of(hct.hue - 25,chroma).tone(15);
            mid = Material.TonalPalette.of(hct.hue + 20,chroma).tone(27);
            accent = Material.TonalPalette.of(hct.hue + 60,chroma).tone(41);
            glow = Material.TonalPalette.of(hct.hue - 45,chroma).tone(57);
        }
        if (!clouds.valid()) { ui.gradient4(0,0,width,height,0,mid,deep,deep,accent); return; }
        GlStateManager.disableTexture2D(); clouds.use();
        clouds.uniform1f("uAspect",width / (float)Math.max(height,1));
        clouds.uniform1f("uTime",(System.nanoTime()-START)/1.0E9F);
        clouds.uniformColor("uDeep",deep); clouds.uniformColor("uMid",mid);
        clouds.uniformColor("uAccent",accent); clouds.uniformColor("uGlow",glow);
        GlStateManager.color(1,1,1,1);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0,0); GL11.glVertex2f(0,0);
        GL11.glTexCoord2f(0,1); GL11.glVertex2f(0,height);
        GL11.glTexCoord2f(1,1); GL11.glVertex2f(width,height);
        GL11.glTexCoord2f(1,0); GL11.glVertex2f(width,0);
        GL11.glEnd(); Shader.unbind();
    }

    public static boolean ready() { return clouds != null && clouds.valid(); }
}
