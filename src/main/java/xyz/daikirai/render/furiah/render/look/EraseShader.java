package xyz.daikirai.render.furiah.render.look;

import xyz.daikirai.render.furiah.asset.AssetSource;
import xyz.daikirai.render.furiah.asset.ShaderFiles;
import xyz.daikirai.render.furiah.render.GlSave;
import xyz.daikirai.render.furiah.render.OptiFineCompat;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL20;

public final class EraseShader {
   private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("NeoRaven/JumpCircles");
   public static final int STROKES = 3;
   private static final String VERT = "look/erase.vsh";
   private static final String FRAG = "look/erase.fsh";
   private static boolean compiled;
   private static boolean broken;
   private static int filesGeneration = -1;
   private static int program;
   private static int uOrigin;
   private static int uRight;
   private static int uUp;
   private static final int[] uSeg = new int[3];
   private static final int[] uRadius = new int[3];
   private static final int[] uDone = new int[3];
   private static int previous;
   private static boolean forceFallback;

   private EraseShader() {
   }

   public static void forceFallbackForTest(boolean on) {
      forceFallback = on;
   }

   public static boolean available() {
      if (!forceFallback && !OptiFineCompat.shadersActive()) {
         if (!compiled || filesGeneration != AssetSource.generation()) {
            compile();
         }

         return program != 0 && !broken;
      } else {
         return false;
      }
   }

   public static void begin(
      double ox, double oy, double oz, double rx, double ry, double rz, double ux, double uy, double uz, float[] seg, float[] radius, float[] done
   ) {
      previous = GlSave.boundProgram();
      GL20.glUseProgram(program);
      GL20.glUniform3f(uOrigin, (float)ox, (float)oy, (float)oz);
      GL20.glUniform3f(uRight, (float)rx, (float)ry, (float)rz);
      GL20.glUniform3f(uUp, (float)ux, (float)uy, (float)uz);

      for (int i = 0; i < 3; i++) {
         GL20.glUniform4f(uSeg[i], seg[i * 4], seg[i * 4 + 1], seg[i * 4 + 2], seg[i * 4 + 3]);
         GL20.glUniform1f(uRadius[i], radius[i]);
         GL20.glUniform1f(uDone[i], done[i]);
      }
   }

   public static void end() {
      GL20.glUseProgram(previous);
   }

   private static void compile() {
      compiled = true;
      filesGeneration = AssetSource.generation();
      if (program != 0) {
         GL20.glDeleteProgram(program);
         program = 0;
      }

      broken = false;
      if (!OpenGlHelper.shadersSupported) {
         broken = true;
      } else {
         String vertex = ShaderFiles.text("look/erase.vsh");
         String fragment = ShaderFiles.text("look/erase.fsh");
         if (vertex != null && fragment != null) {
            int before = GlSave.boundProgram();

            try {
               int vs = shader(35633, vertex);
               int fs = shader(35632, fragment);
               if (vs == 0 || fs == 0) {
                  fail("the shader did not compile");
                  return;
               }

               int p = GL20.glCreateProgram();
               GL20.glAttachShader(p, vs);
               GL20.glAttachShader(p, fs);
               GL20.glLinkProgram(p);
               if (GL20.glGetProgrami(p, 35714) != 0) {
                  program = p;
                  uOrigin = GL20.glGetUniformLocation(p, "uOrigin");
                  uRight = GL20.glGetUniformLocation(p, "uRight");
                  uUp = GL20.glGetUniformLocation(p, "uUp");

                  for (int i = 0; i < 3; i++) {
                     uSeg[i] = GL20.glGetUniformLocation(p, "uSeg[" + i + "]");
                     uRadius[i] = GL20.glGetUniformLocation(p, "uRadius[" + i + "]");
                     uDone[i] = GL20.glGetUniformLocation(p, "uDone[" + i + "]");
                  }

                  GL20.glUseProgram(p);
                  GL20.glUniform1i(GL20.glGetUniformLocation(p, "uTex"), 0);
                  return;
               }

               LOG.warn("Erase shader failed to link: {}", new Object[]{GL20.glGetProgramInfoLog(p, 2048)});
               fail("the shader did not link");
            } catch (RuntimeException | LinkageError var10) {
               LOG.warn("Erase shader setup failed", var10);
               fail("shader setup failed");
               return;
            } finally {
               GL20.glUseProgram(before);
            }
         }
      }
   }

   private static int shader(int type, String source) {
      int s = GL20.glCreateShader(type);
      GL20.glShaderSource(s, source);
      GL20.glCompileShader(s);
      if (GL20.glGetShaderi(s, 35713) == 0) {
         LOG.warn("Erase shader failed to compile: {}", new Object[]{GL20.glGetShaderInfoLog(s, 2048)});
         return 0;
      } else {
         return s;
      }
   }

   private static void fail(String reason) {
      if (!broken) {
         broken = true;
         program = 0;
         LOG.warn("Erase shader switched off ({}); erased bodies fade out instead", new Object[]{reason});
      }
   }
}
