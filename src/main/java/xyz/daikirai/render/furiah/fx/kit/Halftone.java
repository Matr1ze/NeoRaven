package xyz.daikirai.render.furiah.fx.kit;

import xyz.daikirai.render.furiah.asset.AssetSource;
import xyz.daikirai.render.furiah.asset.ShaderFiles;
import xyz.daikirai.render.furiah.render.GlSave;
import xyz.daikirai.render.furiah.render.OptiFineCompat;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL20;

public final class Halftone {
   private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("NeoRaven/JumpCircles");
   private static final String VERT = "kit/halftone.vsh";
   private static final String FRAG = "kit/halftone.fsh";
   private static boolean compiled;
   private static boolean broken;
   private static boolean forceFallback;
   private static int filesGeneration = -1;
   private static int program;
   private static int uPeriod;
   private static int uDot;
   private static int uAmount;
   private static int previous;

   private Halftone() {
   }

   public static boolean usable() {
      if (!forceFallback && !OptiFineCompat.shadersActive()) {
         if (!compiled || filesGeneration != AssetSource.generation()) {
            compile();
         }

         return program != 0 && !broken;
      } else {
         return false;
      }
   }

   public static void begin(float periodPx, int dotColor, float amount) {
      previous = GlSave.boundProgram();
      GL20.glUseProgram(program);
      GL20.glUniform1f(uPeriod, Math.max(2.0F, periodPx));
      GL20.glUniform4f(uDot, (dotColor >> 16 & 0xFF) / 255.0F, (dotColor >> 8 & 0xFF) / 255.0F, (dotColor & 0xFF) / 255.0F, (dotColor >>> 24) / 255.0F);
      GL20.glUniform1f(uAmount, Math.max(0.0F, Math.min(1.0F, amount)));
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
         String vertex = ShaderFiles.text("kit/halftone.vsh");
         String fragment = ShaderFiles.text("kit/halftone.fsh");
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
                  uPeriod = GL20.glGetUniformLocation(p, "uPeriod");
                  uDot = GL20.glGetUniformLocation(p, "uDot");
                  uAmount = GL20.glGetUniformLocation(p, "uAmount");
                  GL20.glUseProgram(p);
                  GL20.glUniform1i(GL20.glGetUniformLocation(p, "uTex"), 0);
                  return;
               }

               LOG.warn("Halftone shader failed to link: {}", new Object[]{GL20.glGetProgramInfoLog(p, 2048)});
               fail("the shader did not link");
            } catch (RuntimeException | LinkageError var9) {
               LOG.warn("Halftone shader setup failed", var9);
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
         LOG.warn("Halftone shader failed to compile: {}", new Object[]{GL20.glGetShaderInfoLog(s, 2048)});
         return 0;
      } else {
         return s;
      }
   }

   private static void fail(String reason) {
      if (!broken) {
         broken = true;
         program = 0;
         LOG.warn("Halftone shader switched off ({}); comic fills use their printed dots instead", new Object[]{reason});
      }
   }

   public static String stateForTest() {
      if (forceFallback) {
         return "fallback (asked)";
      } else if (OptiFineCompat.shadersActive()) {
         return "fallback (shader pack)";
      } else {
         if (!compiled || filesGeneration != AssetSource.generation()) {
            compile();
         }

         return program != 0 && !broken ? "shader" : "fallback (no shader)";
      }
   }

   public static void forceFallbackForTest(boolean on) {
      forceFallback = on;
   }
}
