package xyz.daikirai.render.furiah.render;

import java.nio.Buffer;
import java.nio.IntBuffer;
import xyz.daikirai.render.furiah.asset.AssetSource;
import xyz.daikirai.render.furiah.asset.ShaderFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public final class Distortion {
   private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("NeoRaven/JumpCircles");
   private static final String VERT = "fx/distortion.vsh";
   private static final String FRAG = "fx/distortion.fsh";
   private static final int MAX_VERTICES = 4096;
   private static final double[] PX = new double[4096];
   private static final double[] PY = new double[4096];
   private static final double[] PZ = new double[4096];
   private static final float[] NX = new float[4096];
   private static final float[] NY = new float[4096];
   private static final float[] NZ = new float[4096];
   private static final float[] A = new float[4096];
   private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
   private static boolean compiled;
   private static boolean broken;
   private static boolean missing;
   private static int filesGeneration = -1;
   private static int program;
   private static int uScreen;
   private static int uView;
   private static int texture = -1;
   private static int texW;
   private static int texH;
   private static long copiedFrame = -1L;
   private static boolean firstCopyChecked;
   private static int count;
   private static float strength;
   private static float alpha;
   private static float rimStrength;
   private static int tint;
   private static boolean refract;

   private Distortion() {
   }

   public static boolean refracts() {
      return !broken && (!missing || filesGeneration != AssetSource.generation()) && OpenGlHelper.shadersSupported && !OptiFineCompat.shadersActive();
   }

   public static void begin(long frameId, float strength, int tint, float rimStrength, float opacity, boolean allowRefract) {
      count = 0;
      Distortion.strength = strength;
      Distortion.tint = tint;
      Distortion.rimStrength = rimStrength;
      alpha = opacity;
      refract = allowRefract && refracts() && prepare(frameId);
   }

   public static void vertex(double x, double y, double z, double nx, double ny, double nz) {
      vertex(x, y, z, nx, ny, nz, 1.0F);
   }

   public static void vertex(double x, double y, double z, double nx, double ny, double nz, float strength) {
      if (count < 4096) {
         PX[count] = x;
         PY[count] = y;
         PZ[count] = z;
         NX[count] = (float)nx;
         NY[count] = (float)ny;
         NZ[count] = (float)nz;
         A[count] = strength;
         count++;
      }
   }

   public static int room() {
      return 4096 - count;
   }

   public static void end() {
      int quads = count / 4;
      if (quads != 0 && !(alpha <= 0.003F)) {
         Vfx.flush();
         if (refract) {
            try {
               drawRefracting(quads);
               return;
            } catch (RuntimeException | LinkageError var2) {
               fail("drawing failed", var2);
            }
         }

         drawFallback(quads);
      }
   }

   private static boolean facesCamera(int q, Camera cam) {
      int i = q * 4;
      double nx = NX[i] + NX[i + 2];
      double cx = (PX[i] + PX[i + 2]) * 0.5 - cam.posX;
      double ny = NY[i] + NY[i + 2];
      double cy = (PY[i] + PY[i + 2]) * 0.5 - cam.posY;
      double nz = NZ[i] + NZ[i + 2];
      double cz = (PZ[i] + PZ[i + 2]) * 0.5 - cam.posZ;
      return nx * cx + ny * cy + nz * cz < 0.0;
   }

   private static void drawRefracting(int quads) {
      Camera cam = Vfx.camera();
      int previous = GL11.glGetInteger(35725);
      ((Buffer)VIEWPORT).clear();
      GL11.glGetInteger(2978, VIEWPORT);
      int viewW = VIEWPORT.get(2);
      int viewH = VIEWPORT.get(3);
      RenderState.translucent(false);
      GlStateManager.bindTexture(texture);
      GL20.glUseProgram(program);
      GL20.glUniform2f(uScreen, texW, texH);
      GL20.glUniform2f(uView, viewW, viewH);

      try {
         int r = ColorUtil.red(tint);
         int g = ColorUtil.green(tint);
         int b = ColorUtil.blue(tint);
         float shell = Math.max(0.0F, Math.min(1.0F, alpha));
         Tessellator tess = Tessellator.getInstance();
         WorldRenderer wr = tess.getWorldRenderer();
         wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);

         for (int q = 0; q < quads; q++) {
            if (facesCamera(q, cam)) {
               for (int k = 0; k < 4; k++) {
                  int i = q * 4 + k;
                  int a = Math.round(shell * Math.max(0.0F, Math.min(1.0F, A[i])) * 255.0F);
                  wr.pos(PX[i], PY[i], PZ[i])
                     .tex(strength * A[i], rimStrength)
                     .color(r, g, b, a)
                     .normal(NX[i], NY[i], NZ[i])
                     .endVertex();
               }
            }
         }

         tess.draw();
      } finally {
         GL20.glUseProgram(previous);
         Vfx.resetTextureBinding();
      }
   }

   private static void drawFallback(int quads) {
      Camera cam = Vfx.camera();
      RenderState.additive();

      for (int q = 0; q < quads; q++) {
         if (facesCamera(q, cam)) {
            int i = q * 4;
            Vfx.quad(
               PX[i],
               PY[i],
               PZ[i],
               PX[i + 1],
               PY[i + 1],
               PZ[i + 1],
               PX[i + 2],
               PY[i + 2],
               PZ[i + 2],
               PX[i + 3],
               PY[i + 3],
               PZ[i + 3],
               fresnelColor(i, cam),
               fresnelColor(i + 1, cam),
               fresnelColor(i + 2, cam),
               fresnelColor(i + 3, cam)
            );
         }
      }

      Vfx.flush();
   }

   private static int fresnelColor(int i, Camera cam) {
      double vx = cam.posX - PX[i];
      double vy = cam.posY - PY[i];
      double vz = cam.posZ - PZ[i];
      double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
      float f = len < 1.0E-9 ? 0.0F : 1.0F - (float)Math.abs((vx * NX[i] + vy * NY[i] + vz * NZ[i]) / len);
      f = (float)Math.pow(f, 2.5) * rimStrength;
      int c = ColorUtil.lerp(tint | 0xFF000000, -1, 0.35F * f);
      return ColorUtil.withAlpha(c, Math.min(1.0F, (0.06F + 0.9F * f) * alpha * A[i]));
   }

   private static boolean prepare(long frameId) {
      if (!compiled || filesGeneration != AssetSource.generation()) {
         compile();
      }

      if (broken || missing) {
         return false;
      } else if (copiedFrame == frameId) {
         return true;
      } else {
         try {
            copyScreen();
            copiedFrame = frameId;
            return !broken;
         } catch (RuntimeException | LinkageError var3) {
            fail("screen copy failed", var3);
            return false;
         }
      }
   }

   private static void copyScreen() {
      Minecraft mc = Minecraft.getMinecraft();
      Framebuffer fb = mc.getFramebuffer();
      int h;
      int w;
      if (OpenGlHelper.isFramebufferEnabled() && fb != null) {
         w = fb.framebufferWidth;
         h = fb.framebufferHeight;
      } else {
         w = mc.displayWidth;
         h = mc.displayHeight;
      }

      if (w > 0 && h > 0) {
         Vfx.flush();
         if (texture < 0) {
            texture = TextureUtil.glGenTextures();
         }

         if (w != texW || h != texH) {
            TextureUtil.allocateTexture(texture, w, h);
            GlStateManager.bindTexture(texture);
            GL11.glTexParameteri(3553, 10241, 9729);
            GL11.glTexParameteri(3553, 10240, 9729);
            GL11.glTexParameteri(3553, 10242, 33071);
            GL11.glTexParameteri(3553, 10243, 33071);
            texW = w;
            texH = h;
            firstCopyChecked = false;
         }

         if (!firstCopyChecked) {
            int i = 0;

            while (i < 16 && GL11.glGetError() != 0) {
               i++;
            }
         }

         GlStateManager.bindTexture(texture);
         GL11.glCopyTexSubImage2D(3553, 0, 0, 0, 0, 0, w, h);
         Vfx.resetTextureBinding();
         if (!firstCopyChecked) {
            firstCopyChecked = true;
            int error = GL11.glGetError();
            if (error != 0) {
               fail("OpenGL error " + error + " while copying the screen", null);
            }
         }
      }
   }

   private static void compile() {
      compiled = true;
      filesGeneration = AssetSource.generation();
      if (program != 0) {
         GL20.glDeleteProgram(program);
         program = 0;
      }

      broken = false;
      String vertex = ShaderFiles.text("fx/distortion.vsh");
      String fragment = ShaderFiles.text("fx/distortion.fsh");
      missing = vertex == null || fragment == null;
      if (!missing) {
         int previous = GL11.glGetInteger(35725);

         try {
            int vs = shader(35633, vertex);
            int fs = shader(35632, fragment);
            if (vs == 0 || fs == 0) {
               fail("the shader did not compile", null);
               return;
            }

            int p = GL20.glCreateProgram();
            GL20.glAttachShader(p, vs);
            GL20.glAttachShader(p, fs);
            GL20.glLinkProgram(p);
            if (GL20.glGetProgrami(p, 35714) != 0) {
               program = p;
               uScreen = GL20.glGetUniformLocation(p, "uScreen");
               uView = GL20.glGetUniformLocation(p, "uView");
               GL20.glUseProgram(p);
               GL20.glUniform1i(GL20.glGetUniformLocation(p, "uScene"), 0);
               return;
            }

            LOG.warn("Distortion shader failed to link: {}", new Object[]{GL20.glGetProgramInfoLog(p, 2048)});
            fail("the shader did not link", null);
         } catch (RuntimeException | LinkageError var10) {
            fail("shader setup failed", var10);
            return;
         } finally {
            GL20.glUseProgram(previous);
         }
      }
   }

   private static int shader(int type, String source) {
      int s = GL20.glCreateShader(type);
      GL20.glShaderSource(s, source);
      GL20.glCompileShader(s);
      if (GL20.glGetShaderi(s, 35713) == 0) {
         LOG.warn("Distortion shader failed to compile: {}", new Object[]{GL20.glGetShaderInfoLog(s, 2048)});
         return 0;
      } else {
         return s;
      }
   }

   private static void fail(String reason, Throwable e) {
      if (!broken) {
         broken = true;
         if (e != null) {
            LOG.warn("Distortion switched off ({}); shells are drawn without refraction", new Object[]{reason, e});
         } else {
            LOG.warn("Distortion switched off ({}); shells are drawn without refraction", new Object[]{reason});
         }
      }
   }
}
