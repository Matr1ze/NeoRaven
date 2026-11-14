package xyz.daikirai.gui.onyx.menu;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;
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
            // Use production drawing without booting Forge's mod loader.
            StartupAnimation.prepare();
            StartupAnimation.draw();
            Field start = StartupAnimation.class.getDeclaredField("started");
            start.setAccessible(true);
            float[] times = {0.25F, 1.4F, 2.7F, 3.6F, 4.4F, 5.8F, 9.0F, 10.5F, 14.0F};
            BufferedImage sheet = new BufferedImage(1440, 1008, BufferedImage.TYPE_INT_RGB);
            Graphics2D sheetGraphics = sheet.createGraphics();
            int frame = 0;
            for (float t : times) {
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
                int sx = (frame % 3) * 480, sy = (frame / 3) * 336;
                sheetGraphics.drawImage(image, sx, sy, 480, 320, null);
                sheetGraphics.setColor(Color.LIGHT_GRAY);
                sheetGraphics.drawString(t + " s", sx + 12, sy + 332);
                frame++;
            }
            sheetGraphics.dispose();
            ImageIO.write(sheet, "png", new File(output, "storyboard.png"));
            // Exercise every transition plus a long wait; finish GPU work before timing.
            long[] durations = new long[900];
            for (int i = 0; i < durations.length; i++) {
                start.setLong(null, System.nanoTime() - (long) (i / 60.0 * 1e9));
                long before = System.nanoTime();
                StartupAnimation.draw();
                GL11.glFinish();
                durations[i] = System.nanoTime() - before;
                int error = GL11.glGetError();
                if (error != GL11.GL_NO_ERROR) throw new AssertionError("Frame " + i + ": GL error " + error);
            }
            java.util.Arrays.sort(durations);
            System.out.println("900 frames (CPU + GPU): median=" + durations[450] / 1e6
                    + " ms, p95=" + durations[855] / 1e6 + " ms, max=" + durations[899] / 1e6 + " ms");
            StartupAnimation.finish();
        } finally { Display.destroy(); }
        System.out.println("Startup animation: 9 keyframes and storyboard exported without GL errors");
    }
}
