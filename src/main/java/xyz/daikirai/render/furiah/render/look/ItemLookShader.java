package xyz.daikirai.render.furiah.render.look;

import xyz.daikirai.render.furiah.asset.AssetSource;
import xyz.daikirai.render.furiah.asset.ShaderFiles;
import xyz.daikirai.render.furiah.render.GlSave;
import xyz.daikirai.render.furiah.render.OptiFineCompat;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL20;

public final class ItemLookShader {
   private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("NeoRaven/JumpCircles");
   private static final String VERT = "look/item.vsh";
   private static final String FRAG = "look/item.fsh";
   private static boolean compiled;
   private static boolean broken;
   private static int filesGeneration = -1;
   private static int program;
   private static int uLook;
   private static int uTint;
   private static int uLevels;
   private static int previous;

   private ItemLookShader() {
   }

   public static boolean usable() {
      if (OptiFineCompat.shadersActive()) {
         return false;
      } else {
         if (!compiled || filesGeneration != AssetSource.generation()) {
            compile();
         }

         return program != 0 && !broken;
      }
   }

   public static void begin(BodyLook look, int tint, int levels) {
      previous = GlSave.boundProgram();
      GL20.glUseProgram(program);
      GL20.glUniform1i(uLook, look.ordinal());
      GL20.glUniform4f(uTint, (tint >> 16 & 0xFF) / 255.0F, (tint >> 8 & 0xFF) / 255.0F, (tint & 0xFF) / 255.0F, 1.0F);
      GL20.glUniform1f(uLevels, Math.max(2, levels));
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
         String vertex = ShaderFiles.text("look/item.vsh");
         String fragment = ShaderFiles.text("look/item.fsh");
         if (vertex != null && fragment != null) {
            int before = GlSave.boundProgram();

            try {
               int vs = shader(35633, vertex);
               int fs = shader(35632, fragment);
               if (vs == 0 || fs == 0) {
                  broken = true;
                  return;
               }

               int p = GL20.glCreateProgram();
               GL20.glAttachShader(p, vs);
               GL20.glAttachShader(p, fs);
               GL20.glLinkProgram(p);
               if (GL20.glGetProgrami(p, 35714) != 0) {
                  program = p;
                  uLook = GL20.glGetUniformLocation(p, "uLook");
                  uTint = GL20.glGetUniformLocation(p, "uTint");
                  uLevels = GL20.glGetUniformLocation(p, "uLevels");
                  GL20.glUseProgram(p);
                  GL20.glUniform1i(GL20.glGetUniformLocation(p, "uTex"), 0);
                  GL20.glUniform1i(GL20.glGetUniformLocation(p, "uLightmap"), 1);
                  return;
               }

               LOG.warn("Item look shader failed to link: {}", new Object[]{GL20.glGetProgramInfoLog(p, 2048)});
               broken = true;
            } catch (RuntimeException | LinkageError var9) {
               LOG.warn("Item look shader setup failed; held items keep their colors in the body looks", var9);
               broken = true;
               program = 0;
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
         LOG.warn("Item look shader failed to compile: {}", new Object[]{GL20.glGetShaderInfoLog(s, 2048)});
         return 0;
      } else {
         return s;
      }
   }
}
