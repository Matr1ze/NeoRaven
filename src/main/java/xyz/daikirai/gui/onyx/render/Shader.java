package xyz.daikirai.gui.onyx.render;

import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal GLSL program wrapper, ported from Onyx's {@code render.extra.Cls}.
 *
 * <p>Onyx shipped its GUI shaders as plain glsl 120 sources next to the fonts; they are now
 * part of this resource pack ({@code assets/onyx/shaders/core}) and compiled through the same
 * {@link OpenGlHelper} entry points.</p>
 */
public final class Shader {

    private static final String ROOT = "/assets/onyx/shaders/core/";
    private static final int LOG_SIZE = 1024;

    private final String name;
    private String vertexName;
    private final Map<String, Integer> uniforms = new HashMap<String, Integer>();
    private final FloatBuffer buffer = BufferUtils.createFloatBuffer(4);
    private int program;
    private boolean valid;

    private Shader(String name) {
        this.name = name;
    }

    /** Loads and links {@code name.vsh}/{@code name.fsh}, disabling itself on failure. */
    public static Shader load(String name) {
        return load(name, name);
    }

    public static Shader load(String name, String vertexName) {
        Shader shader = new Shader(name);
        shader.vertexName = vertexName;
        try {
            shader.compile();
        } catch (Throwable t) {
            System.err.println("[onyx] failed to build shader '" + name + "': " + t);
            shader.valid = false;
        }
        return shader;
    }

    private void compile() throws IOException {
        if (!OpenGlHelper.shadersSupported) {
            System.err.println("[onyx] shaders unsupported, '" + name + "' disabled");
            return;
        }
        int vertex = compileShader(OpenGlHelper.GL_VERTEX_SHADER, vertexName + ".vsh");
        int fragment = compileShader(OpenGlHelper.GL_FRAGMENT_SHADER, name + ".fsh");
        if (vertex == 0 || fragment == 0) {
            if (vertex != 0) {
                OpenGlHelper.glDeleteShader(vertex);
            }
            if (fragment != 0) {
                OpenGlHelper.glDeleteShader(fragment);
            }
            return;
        }

        this.program = OpenGlHelper.glCreateProgram();
        OpenGlHelper.glAttachShader(program, vertex);
        OpenGlHelper.glAttachShader(program, fragment);
        OpenGlHelper.glLinkProgram(program);
        OpenGlHelper.glDeleteShader(vertex);
        OpenGlHelper.glDeleteShader(fragment);

        if (OpenGlHelper.glGetProgrami(program, OpenGlHelper.GL_LINK_STATUS) == 0) {
            System.err.println("[onyx] failed to link shader '" + name + "': "
                    + OpenGlHelper.glGetProgramInfoLog(program, LOG_SIZE));
            OpenGlHelper.glDeleteProgram(program);
            this.program = 0;
            return;
        }
        this.valid = true;
    }

    private int compileShader(int type, String file) throws IOException {
        int shader = OpenGlHelper.glCreateShader(type);
        OpenGlHelper.glShaderSource(shader, read(ROOT + file));
        OpenGlHelper.glCompileShader(shader);
        if (OpenGlHelper.glGetShaderi(shader, OpenGlHelper.GL_COMPILE_STATUS) == 0) {
            System.err.println("[onyx] failed to compile '" + file + "': "
                    + OpenGlHelper.glGetShaderInfoLog(shader, LOG_SIZE));
            OpenGlHelper.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static ByteBuffer read(String path) throws IOException {
        InputStream in = Shader.class.getResourceAsStream(path);
        if (in == null) {
            throw new IOException("Missing shader resource " + path);
        }
        try {
            byte[] bytes = new byte[8192];
            int length = 0;
            int read;
            while ((read = in.read(bytes, length, bytes.length - length)) != -1) {
                length += read;
                if (length == bytes.length) {
                    byte[] bigger = new byte[bytes.length * 2];
                    System.arraycopy(bytes, 0, bigger, 0, length);
                    bytes = bigger;
                }
            }
            ByteBuffer buffer = BufferUtils.createByteBuffer(length);
            buffer.put(bytes, 0, length);
            buffer.flip();
            return buffer;
        } finally {
            in.close();
        }
    }

    public boolean valid() {
        return valid;
    }

    public void use() {
        if (valid) {
            OpenGlHelper.glUseProgram(program);
        }
    }

    public static void unbind() {
        if (OpenGlHelper.shadersSupported) {
            OpenGlHelper.glUseProgram(0);
        }
    }

    private int location(String uniform) {
        if (!valid) {
            return -1;
        }
        Integer cached = uniforms.get(uniform);
        if (cached == null) {
            cached = OpenGlHelper.glGetUniformLocation(program, uniform);
            uniforms.put(uniform, cached);
        }
        return cached;
    }

    public void uniform1f(String uniform, float value) {
        int location = location(uniform);
        if (location >= 0) {
            buffer.clear();
            buffer.put(value).flip();
            OpenGlHelper.glUniform1(location, buffer);
        }
    }

    public void uniform2f(String uniform, float x, float y) {
        int location = location(uniform);
        if (location >= 0) {
            buffer.clear();
            buffer.put(x).put(y).flip();
            OpenGlHelper.glUniform2(location, buffer);
        }
    }

    public void uniform3f(String uniform, float x, float y, float z) {
        int location = location(uniform);
        if (location >= 0) {
            buffer.clear();
            buffer.put(x).put(y).put(z).flip();
            OpenGlHelper.glUniform3(location, buffer);
        }
    }

    public void uniform4f(String uniform, float x, float y, float z, float w) {
        int location = location(uniform);
        if (location >= 0) {
            buffer.clear();
            buffer.put(x).put(y).put(z).put(w).flip();
            OpenGlHelper.glUniform4(location, buffer);
        }
    }

    public void uniform1i(String uniform, int value) {
        int location = location(uniform);
        if (location >= 0) {
            OpenGlHelper.glUniform1i(location, value);
        }
    }

    /** Sets a colour uniform from an ARGB int (alpha dropped). */
    public void uniformColor(String uniform, int argb) {
        uniform3f(uniform,
                (argb >> 16 & 0xFF) / 255.0F,
                (argb >> 8 & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F);
    }
}
