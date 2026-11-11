package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.renderer.texture.TextureManager;
import org.lwjgl.opengl.Display;

/** First frame before Forge's splash worker begins rendering. */
public final class EarlySplash {
    private EarlySplash() { }
    public static void draw(TextureManager textures) {
        StartupAnimation.draw();
        Display.update();
    }
}
