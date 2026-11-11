package xyz.daikirai.gui.onyx.menu;

import net.minecraftforge.fml.common.ProgressManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/** Render the production OpenGL animation at key times, without booting Minecraft. */
public final class StartupAnimationPreview {
    public static void main(String[] args) throws Exception {
        File output = new File("build/startup-preview");
        if (!output.isDirectory() && !output.mkdirs()) throw new IllegalStateException("Cannot create preview directory");
        Display.setDisplayMode(new DisplayMode(960, 640));
        Display.setTitle("NeoRaven animation render check");
        Display.create();
        try {
            // Forge's push API invokes FML/Minecraft; the preview deliberately
            // uses the production renderer's indeterminate startup progress.
            StartupAnimation.draw();
            Field start = StartupAnimation.class.getDeclaredField("started");
            start.setAccessible(true);
            for (float t : new float[]{0.5F, 2.7F, 4.8F, 8.8F}) {
                start.setLong(null, System.nanoTime() - (long) (t * 1e9));
                StartupAnimation.draw();
                int error = GL11.glGetError();
                if (error != GL11.GL_NO_ERROR) throw new AssertionError("GL error: " + error);
                ByteBuffer pixels = BufferUtils.createByteBuffer(960 * 640 * 4);
                GL11.glReadPixels(0, 0, 960, 640, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                BufferedImage image = new BufferedImage(960, 640, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 640; y++) for (int x = 0; x < 960; x++) {
                    int offset = (y * 960 + x) * 4;
                    int rgb = 0xff000000 | (pixels.get(offset) & 255) << 16
                            | (pixels.get(offset + 1) & 255) << 8 | (pixels.get(offset + 2) & 255);
                    image.setRGB(x, 639 - y, rgb);
                }
                ImageIO.write(image, "png", new File(output, "startup-" + t + ".png"));
            }
            StartupAnimation.finish();
        } finally { Display.destroy(); }
        System.out.println("Startup animation: 4 production-renderer frames exported without GL errors");
    }
}
