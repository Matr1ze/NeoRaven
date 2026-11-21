package xyz.daikirai.render.furiah.render.look;

import xyz.daikirai.render.furiah.asset.AssetSource;
import xyz.daikirai.render.furiah.asset.ShaderFiles;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.GlSave;
import xyz.daikirai.render.furiah.render.OptiFineCompat;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL20;

public final class BodyLookShader {
   private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("NeoRaven/JumpCircles");
   public static final int FEET_UP = 0;
   public static final int HEAD_DOWN = 1;
   public static final int FROM_POINT = 2;
   private static final String VERT = "look/body.vsh";
   private static final String FRAG = "look/body.fsh";
   private static boolean compiled;
   private static boolean broken;
   private static int filesGeneration = -1;
   private static int program;
   private static int uRight;
   private static int uUp;
   private static int uBack;
   private static int uEye;
   private static int uLit;
   private static int uMode;
   private static int uOrigin;
   private static int uSpan;
   private static int uProgress;
   private static int uNoise;
   private static int uEdge;
   private static int uEdgeColor;
   private static int uSide;
   private static int uAlphaCut;
   private static int previous;
   private static boolean forceFallback;

   private BodyLookShader() {
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

   public static void begin(
      Camera cam,
      int mode,
      double ox,
      double oy,
      double oz,
      float span,
      float progress,
      float noise,
      float edge,
      int edgeColor,
      int side,
      boolean lit,
      float alphaCut
   ) {
      previous = GlSave.boundProgram();
      GL20.glUseProgram(program);
      GL20.glUniform3f(uRight, (float)cam.rightX, (float)cam.rightY, (float)cam.rightZ);
      GL20.glUniform3f(uUp, (float)cam.upX, (float)cam.upY, (float)cam.upZ);
      GL20.glUniform3f(uBack, (float)(-cam.forwardX), (float)(-cam.forwardY), (float)(-cam.forwardZ));
      GL20.glUniform3f(uEye, (float)cam.posX, (float)cam.posY, (float)cam.posZ);
      GL20.glUniform1i(uMode, mode);
      GL20.glUniform3f(uOrigin, (float)ox, (float)oy, (float)oz);
      GL20.glUniform1f(uSpan, Math.max(0.05F, span));
      GL20.glUniform1f(uProgress, Math.max(0.0F, Math.min(1.0F, progress)));
      GL20.glUniform1f(uNoise, Math.max(0.0F, Math.min(1.0F, noise)));
      GL20.glUniform1f(uEdge, Math.max(0.005F, edge));
      GL20.glUniform4f(
         uEdgeColor, (edgeColor >> 16 & 0xFF) / 255.0F, (edgeColor >> 8 & 0xFF) / 255.0F, (edgeColor & 0xFF) / 255.0F, (edgeColor >>> 24) / 255.0F
      );
      GL20.glUniform1f(uSide, side);
      GL20.glUniform1f(uLit, lit ? 1.0F : 0.0F);
      GL20.glUniform1f(uAlphaCut, alphaCut);
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
         String vertex = ShaderFiles.text("look/body.vsh");
         String fragment = ShaderFiles.text("look/body.fsh");
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
                  uRight = GL20.glGetUniformLocation(p, "uRight");
                  uUp = GL20.glGetUniformLocation(p, "uUp");
                  uBack = GL20.glGetUniformLocation(p, "uBack");
                  uEye = GL20.glGetUniformLocation(p, "uEye");
                  uLit = GL20.glGetUniformLocation(p, "uLit");
                  uMode = GL20.glGetUniformLocation(p, "uMode");
                  uOrigin = GL20.glGetUniformLocation(p, "uOrigin");
                  uSpan = GL20.glGetUniformLocation(p, "uSpan");
                  uProgress = GL20.glGetUniformLocation(p, "uProgress");
                  uNoise = GL20.glGetUniformLocation(p, "uNoise");
                  uEdge = GL20.glGetUniformLocation(p, "uEdge");
                  uEdgeColor = GL20.glGetUniformLocation(p, "uEdgeColor");
                  uSide = GL20.glGetUniformLocation(p, "uSide");
                  uAlphaCut = GL20.glGetUniformLocation(p, "uAlphaCut");
                  GL20.glUseProgram(p);
                  GL20.glUniform1i(GL20.glGetUniformLocation(p, "uTex"), 0);
                  GL20.glUniform1i(GL20.glGetUniformLocation(p, "uLightmap"), 1);
                  return;
               }

               LOG.warn("Body look shader failed to link: {}", new Object[]{GL20.glGetProgramInfoLog(p, 2048)});
               fail("the shader did not link");
            } catch (RuntimeException | LinkageError var9) {
               LOG.warn("Body look shader setup failed", var9);
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
         LOG.warn("Body look shader failed to compile: {}", new Object[]{GL20.glGetShaderInfoLog(s, 2048)});
         return 0;
      } else {
         return s;
      }
   }

   private static void fail(String reason) {
      if (!broken) {
         broken = true;
         program = 0;
         LOG.warn("Body look shader switched off ({}); looks change with a crossfade instead", new Object[]{reason});
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
