package xyz.daikirai.gui.onyx.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Compile the shipped shader on the real driver and inspect its fade and glass cutout. */
public final class ShadowPreview {
    private static final int W = 960, H = 640;

    public static void main(String[] args) throws Exception {
        Display.setDisplayMode(new DisplayMode(W, H));
        Display.setTitle("NeoRaven shadow render check");
        Display.create();
        try {
            int vertex = compile(GL20.GL_VERTEX_SHADER, "onyx_shape.vsh");
            int fragment = compile(GL20.GL_FRAGMENT_SHADER, "onyx_shadow.fsh");
            int program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
                throw new AssertionError(GL20.glGetProgramInfoLog(program, 4096));
            GL11.glViewport(0, 0, W, H);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0, W, H, 0, -1, 1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glClearColor(1, 1, 1, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            draw(program, 80, 70, 300, 180, 28, 6);
            draw(program, 550, 70, 300, 180, 28, 12);
            draw(program, 160, 400, 140, 140, 70, 6);
            draw(program, 550, 400, 300, 40, 20, 1);
            ByteBuffer pixels = BufferUtils.createByteBuffer(W * H * 4);
            GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            if (red(pixels, 230, 160) != 255) throw new AssertionError("Shadow darkens glass interior");
            int previous = red(pixels, 230, 253);
            if (previous < 225 || previous > 250) throw new AssertionError("Shadow too dark or invisible: " + previous);
            for (int y = 254; y < 290; y++) {
                int current = red(pixels, 230, y);
                if (current < previous) throw new AssertionError("Shadow fade reverses at " + y);
                if (current - previous > 4) throw new AssertionError("Hard edge at " + y);
                previous = current;
            }
            if (previous != 255) throw new AssertionError("Shadow does not fade to background");
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("OpenGL error");
            BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int c = red(pixels, x, y);
                image.setRGB(x, y, (c << 16) | (c << 8) | c);
            }
            File output = new File("build/shadow-preview.png");
            ImageIO.write(image, "png", output);
            GL20.glDeleteProgram(program);
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            System.out.println("Shadow shader: compile/link, transparent interior, smooth outer fade and GL checks passed.");
            System.out.println("Preview: " + output.getAbsolutePath());
        } finally {
            Display.destroy();
        }
    }

    private static int red(ByteBuffer pixels, int x, int y) {
        return pixels.get(((H - 1 - y) * W + x) * 4) & 255;
    }

    private static int compile(int type, String file) throws Exception {
        int shader = GL20.glCreateShader(type);
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/resources/assets/onyx/shaders/core/" + file)), StandardCharsets.UTF_8);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
            throw new AssertionError(GL20.glGetShaderInfoLog(shader, 4096));
        return shader;
    }

    private static void draw(int program, float x, float y, float w, float h, float radius, float spread) {
        float sigma = 2 + spread * .75F, offset = spread * .35F;
        float pad = sigma * 4 + offset + 2;
        GL20.glUseProgram(program);
        GL20.glUniform2f(GL20.glGetUniformLocation(program, "uHalfExtents"), w / 2, h / 2);
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "uRadius"), radius);
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "uSigma"), sigma);
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "uOffset"), offset);
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "uPixel"), 1);
        GL11.glColor4f(0, 0, 0, .16F);
        GL11.glBegin(GL11.GL_QUADS);
        point(x - pad, y - pad, x + w / 2, y + h / 2);
        point(x - pad, y + h + pad, x + w / 2, y + h / 2);
        point(x + w + pad, y + h + pad, x + w / 2, y + h / 2);
        point(x + w + pad, y - pad, x + w / 2, y + h / 2);
        GL11.glEnd();
        GL20.glUseProgram(0);
    }

    private static void point(float x, float y, float cx, float cy) {
        GL11.glTexCoord2f(x - cx, y - cy);
        GL11.glVertex2f(x, y);
    }
}
