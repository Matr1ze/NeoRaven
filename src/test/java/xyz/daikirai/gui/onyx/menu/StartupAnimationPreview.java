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
            WaitingParticlesCheck.main(new String[0]);
            float[] times = {9.0F, 10.1F, 10.45F, 11.2F, 12.8F, 14.0F, 16.0F, 19.0F, 23.0F};
            BufferedImage sheet = new BufferedImage(1440, 1008, BufferedImage.TYPE_INT_RGB);
            Graphics2D sheetGraphics = sheet.createGraphics();
            int frame = 0;
            float simulated = 0;
            for (float t : times) {
                while (simulated + 1 / 60F < t) {
                    simulated += 1 / 60F;
                    StartupAnimation.drawPreview(simulated, cursorX(simulated), cursorY(simulated));
                }
                simulated = t;
                StartupAnimation.drawPreview(t, cursorX(t), cursorY(t));
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
                // Annotate only the exported test image with the simulated pointer position.
                Graphics2D marker = image.createGraphics();
                marker.setColor(new Color(255, 255, 255, 125));
                int mx = Math.round(cursorX(t) * 960 / 900F), my = Math.round(cursorY(t) * 640 / 600F);
                marker.drawOval(mx - 5, my - 5, 10, 10);
                marker.drawLine(mx - 9, my, mx + 9, my);
                marker.drawLine(mx, my - 9, mx, my + 9);
                marker.dispose();
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
            long[] durations = new long[1800];
            for (int i = 0; i < durations.length; i++) {
                float time = i / 60F;
                long before = System.nanoTime();
                StartupAnimation.drawPreview(time, cursorX(time), cursorY(time));
                GL11.glFinish();
                durations[i] = System.nanoTime() - before;
                int error = GL11.glGetError();
                if (error != GL11.GL_NO_ERROR) throw new AssertionError("Frame " + i + ": GL error " + error);
            }
            java.util.Arrays.sort(durations);
            System.out.println("1800 frames (CPU + GPU): median=" + durations[900] / 1e6
                    + " ms, p95=" + durations[1710] / 1e6 + " ms, max=" + durations[1799] / 1e6 + " ms");
            StartupAnimation.finish();
        } finally { Display.destroy(); }
        System.out.println("Startup animation: 9 keyframes and storyboard exported without GL errors");
    }

    private static float cursorX(float t) { return t < 13 ? 450 : 450 + 210 * Math.min(1, (t - 13) / 1.5F); }
    private static float cursorY(float t) { return t < 13 ? 300 : 300 - 130 * Math.min(1, (t - 13) / 1.5F); }
}
