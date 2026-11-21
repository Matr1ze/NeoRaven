package xyz.daikirai.module.render;

import java.io.InputStream;
import java.util.Scanner;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.*;
import xyz.daikirai.module.*;
import xyz.daikirai.module.setting.SliderSetting;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

public final class CinematicModule extends Module {
    private int program, color, depth, width, height;
    private int scene, geometry, pixel, controls;
    private boolean highQuality;
    private SliderSetting quality, ao, bloom, lens, adaptation;
    private long previous;
    private float exposure;
    private Object world;

    public CinematicModule(int key) { super("Cinematic", Category.render, key); }

    @Override public void link() {
        quality = setting("Quality");
        ao = setting("Ambient occlusion");
        bloom = setting("Bloom");
        lens = setting("Lens distortion");
        adaptation = setting("Eye adaptation");
    }

    private SliderSetting setting(String name) {
        return (SliderSetting) ModuleManager.setting(this, name);
    }

    private int shader(int type, String source) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == 0) {
            String error = glGetShaderInfoLog(id, 4096);
            glDeleteShader(id);
            throw new IllegalStateException(error);
        }
        return id;
    }

    private void init(boolean high) {
        highQuality = high;
        String file = high ? "cinematic-high.frag" : "cinematic.frag";
        try (InputStream input = getClass().getResourceAsStream("/assets/clickgui/shaders/" + file);
             Scanner reader = new Scanner(input, "UTF-8")) {
            program = glCreateProgram();
            int vertex = shader(GL_VERTEX_SHADER, "#version 120\nvarying vec2 uv; varying vec4 projection; void main(){projection=vec4(gl_ProjectionMatrix[0][0],gl_ProjectionMatrix[1][1],gl_ProjectionMatrix[2][2],gl_ProjectionMatrix[3][2]);gl_Position=gl_Vertex;uv=gl_Vertex.xy*.5+.5;}");
            glAttachShader(program, vertex);
            glDeleteShader(vertex);
            int fragment = shader(GL_FRAGMENT_SHADER, reader.useDelimiter("\\A").next());
            glAttachShader(program, fragment);
            glDeleteShader(fragment);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == 0)
                throw new IllegalStateException(glGetProgramInfoLog(program,4096));
            scene = glGetUniformLocation(program,"scene");
            geometry = glGetUniformLocation(program,"geometry");
            pixel = glGetUniformLocation(program,"pixel");
            controls = glGetUniformLocation(program,"controls");
            color = glGenTextures();
            depth = glGenTextures();
        } catch (Exception ex) { throw new IllegalStateException("Cinematic initialization failed", ex); }
    }

    private void capture(int unit, int texture, int format, int filter, boolean resize) {
        GL13.glActiveTexture(unit);
        glBindTexture(GL_TEXTURE_2D, texture);
        if (resize) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            glCopyTexImage2D(GL_TEXTURE_2D,0,format,0,0,width,height,0);
        } else glCopyTexSubImage2D(GL_TEXTURE_2D,0,0,0,0,0,width,height);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void render(RenderWorldLastEvent event) {
        if (mc.thePlayer == null || !OpenGlHelper.isFramebufferEnabled()
                || !GLContext.getCapabilities().OpenGL20 || mc.gameSettings.anaglyph
                || glGetInteger(0x8CA6) != mc.getFramebuffer().framebufferObject) return;
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int oldUnit = glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        glPushAttrib(GL_ENABLE_BIT | GL_TEXTURE_BIT | GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT);
        try {
            boolean high = quality.getInput() >= 1;
            if (program != 0 && high != highQuality) onDisable();
            if (program == 0) init(high);
            boolean resize = width != mc.displayWidth || height != mc.displayHeight;
            width = mc.displayWidth;
            height = mc.displayHeight;
            capture(GL13.GL_TEXTURE0,color,GL_RGB8,GL_LINEAR,resize);
            if (highQuality && bloom.getInput() > 0) {
                glTexParameteri(GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 5);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
                if (GLContext.getCapabilities().OpenGL30) GL30.glGenerateMipmap(GL_TEXTURE_2D);
                else if (GLContext.getCapabilities().GL_ARB_framebuffer_object)
                    ARBFramebufferObject.glGenerateMipmap(GL_TEXTURE_2D);
                else EXTFramebufferObject.glGenerateMipmapEXT(GL_TEXTURE_2D);
            }
            capture(GL13.GL_TEXTURE1,depth,GL14.GL_DEPTH_COMPONENT24,GL_NEAREST,resize);
            long now = System.nanoTime();
            float light = mc.theWorld.getLightBrightness(new BlockPos(mc.thePlayer.posX,
                    mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ));
            float target = 1.30f - 0.42f * light;
            if (world != mc.theWorld) exposure = target;
            else exposure += (target-exposure)*(1-Math.exp(-Math.min(.2,(now-previous)*1e-9)
                    * (target < exposure ? 3.0 : 1.1)));
            world = mc.theWorld;
            previous = now;
            glUseProgram(program);
            glUniform1i(scene,0);
            glUniform1i(geometry,1);
            glUniform2f(pixel,1f/width,1f/height);
            glUniform4f(controls,(float)ao.getInput(),(float)bloom.getInput(),(float)lens.getInput(),
                    1f+(exposure-1f)*(float)adaptation.getInput());
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_ALPHA_TEST);
            glDisable(GL_BLEND);
            glDisable(GL_CULL_FACE);
            glDepthMask(false);
            glBegin(GL_TRIANGLES);
            glVertex2f(-1,-1); glVertex2f(3,-1); glVertex2f(-1,3);
            glEnd();
        } catch (RuntimeException ex) {
            org.apache.logging.log4j.LogManager.getLogger("Cinematic").error("Post processing failed", ex);
            disable();
        } finally {
            glUseProgram(oldProgram);
            glPopAttrib();
            GL13.glActiveTexture(oldUnit);
        }
    }

    @Override public void onDisable() {
        if (program != 0) glDeleteProgram(program);
        if (color != 0) glDeleteTextures(color);
        if (depth != 0) glDeleteTextures(depth);
        program = color = depth = width = height = 0;
        world = null;
    }
}