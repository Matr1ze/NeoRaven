package xyz.daikirai.render.furiah.render;

import xyz.daikirai.render.furiah.asset.AssetSource;
import xyz.daikirai.render.furiah.asset.ShaderFiles;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL20;

public final class DissolveShader {
   private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("NeoRaven/JumpCircles");
   private static final String VERT = "fx/dissolve.vsh";
   private static final String FRAG = "fx/dissolve.fsh";
   private static boolean compiled;
   private static boolean broken;
   private static int filesGeneration = -1;
   private static int program;
   private static int uOrigin;
   private static int uProgress;
   private static int uNoise;
   private static int uEdge;
   private static int uEdgeColor;
   private static int uAlpha;
   private static int uLit;
   private static int previous;

   private DissolveShader() {
   }

   public static float noiseAt(double x, double y, double z) {
      return valueNoise((float)x * 7.0F, (float)y * 7.0F, (float)z * 7.0F) * 0.62F + valueNoise((float)x * 19.0F, (float)y * 19.0F, (float)z * 19.0F) * 0.38F;
   }

   public static float thresholdAt(float sweep, float noise, float noiseShare, float edge) {
      float v = sweep + (noise - sweep) * noiseShare;
      return (v + edge) / (1.0F + edge);
   }

   private static float fract(float v) {
      return v - (float)Math.floor(v);
   }

   private static float hash(float x, float y, float z) {
      x = fract(x * 0.3183099F + 0.1F) * 17.0F;
      y = fract(y * 0.3183099F + 0.2F) * 17.0F;
      z = fract(z * 0.3183099F + 0.3F) * 17.0F;
      return fract(x * y * z * (x + y + z));
   }

   private static float valueNoise(float x, float y, float z) {
      float ix = (float)Math.floor(x);
      float iy = (float)Math.floor(y);
      float iz = (float)Math.floor(z);
      float fx = x - ix;
      float fy = y - iy;
      float fz = z - iz;
      fx = fx * fx * (3.0F - 2.0F * fx);
      fy = fy * fy * (3.0F - 2.0F * fy);
      fz = fz * fz * (3.0F - 2.0F * fz);
      float a = mix(hash(ix, iy, iz), hash(ix + 1.0F, iy, iz), fx);
      float b = mix(hash(ix, iy + 1.0F, iz), hash(ix + 1.0F, iy + 1.0F, iz), fx);
      float c = mix(hash(ix, iy, iz + 1.0F), hash(ix + 1.0F, iy, iz + 1.0F), fx);
      float d = mix(hash(ix, iy + 1.0F, iz + 1.0F), hash(ix + 1.0F, iy + 1.0F, iz + 1.0F), fx);
      return mix(mix(a, b, fy), mix(c, d, fy), fz);
   }

   private static float mix(float a, float b, float t) {
      return a + (b - a) * t;
   }

   public static boolean available() {
      if (OptiFineCompat.shadersActive()) {
         return false;
      } else {
         if (!compiled || filesGeneration != AssetSource.generation()) {
            compile();
         }

         return program != 0 && !broken;
      }
   }

   static void begin(BodyProxy.Look look, double ox, double oy, double oz) {
      begin(look, ox, oy, oz, look.lit && look.lightmap);
   }

   static void begin(BodyProxy.Look look, double ox, double oy, double oz, boolean lightmap) {
      previous = GlSave.boundProgram();
      GL20.glUseProgram(program);
      GL20.glUniform3f(uOrigin, (float)ox, (float)oy, (float)oz);
      GL20.glUniform1f(uProgress, Math.max(0.0F, Math.min(1.0F, look.dissolve)));
      GL20.glUniform1f(uNoise, Math.max(0.0F, Math.min(1.0F, look.dissolveNoise)));
      GL20.glUniform1f(uEdge, Math.max(0.005F, look.edgeWidth));
      int c = look.edgeColor;
      GL20.glUniform4f(uEdgeColor, (c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F, (c & 0xFF) / 255.0F, 1.0F);
      GL20.glUniform1f(uAlpha, look.alpha);
      GL20.glUniform1f(uLit, lightmap ? 1.0F : 0.0F);
   }

   static void end() {
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
         String vertex = ShaderFiles.text("fx/dissolve.vsh");
         String fragment = ShaderFiles.text("fx/dissolve.fsh");
         if (vertex != null && fragment != null) {
            int before = GlSave.boundProgram();

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
                  uOrigin = GL20.glGetUniformLocation(p, "uOrigin");
                  uProgress = GL20.glGetUniformLocation(p, "uProgress");
                  uNoise = GL20.glGetUniformLocation(p, "uNoise");
                  uEdge = GL20.glGetUniformLocation(p, "uEdge");
                  uEdgeColor = GL20.glGetUniformLocation(p, "uEdgeColor");
                  uAlpha = GL20.glGetUniformLocation(p, "uAlpha");
                  uLit = GL20.glGetUniformLocation(p, "uLit");
                  GL20.glUseProgram(p);
                  GL20.glUniform1i(GL20.glGetUniformLocation(p, "uTex"), 0);
                  GL20.glUniform1i(GL20.glGetUniformLocation(p, "uLightmap"), 1);
                  return;
               }

               LOG.warn("Dissolve shader failed to link: {}", new Object[]{GL20.glGetProgramInfoLog(p, 2048)});
               fail("the shader did not link", null);
            } catch (RuntimeException | LinkageError var9) {
               fail("shader setup failed", var9);
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
         LOG.warn("Dissolve shader failed to compile: {}", new Object[]{GL20.glGetShaderInfoLog(s, 2048)});
         return 0;
      } else {
         return s;
      }
   }

   private static void fail(String reason, Throwable e) {
      if (!broken) {
         broken = true;
         program = 0;
         if (e != null) {
            LOG.warn("Dissolve shader switched off ({}); bodies fade with sparkles instead", new Object[]{reason, e});
         } else {
            LOG.warn("Dissolve shader switched off ({}); bodies fade with sparkles instead", new Object[]{reason});
         }
      }
   }
}
