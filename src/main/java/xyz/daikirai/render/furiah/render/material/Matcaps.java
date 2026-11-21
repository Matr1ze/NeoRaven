package xyz.daikirai.render.furiah.render.material;

import java.nio.Buffer;
import java.nio.IntBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

public final class Matcaps {
   private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("NeoRaven/JumpCircles");
   private static final int PRESETS = Material.values().length - 1;
   private static final int FINISHES = Finish.values().length;
   private static final int SLOTS = PRESETS + FINISHES * 2;
   private static final int[] TEXTURES = new int[SLOTS];
   private static final boolean[] REQUESTED = new boolean[SLOTS];
   private static final int[][] PAINTED = new int[SLOTS][];
   private static int standIn;
   private static float maxAnisotropy = -1.0F;
   private static final ExecutorService PAINTER = Executors.newSingleThreadExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
         Thread t = new Thread(r, "Furiah's Visuals matcaps");
         t.setDaemon(true);
         t.setPriority(1);
         return t;
      }
   });

   private Matcaps() {
   }

   public static void prefetch() {
      request(slot(Material.CUSTOM, Finish.METALLIC, true));
      request(slot(Material.CUSTOM, Finish.METALLIC, false));
   }

   private static int slot(Material m, Finish f, boolean shader) {
      return m != Material.CUSTOM ? m.ordinal() : PRESETS + (shader ? 0 : FINISHES) + f.ordinal();
   }

   public static boolean ready(Material m, Finish f, boolean shader) {
      int s = slot(m, f, shader);
      pollUpload(s);
      return TEXTURES[s] != 0;
   }

   public static int texture(Material m, Finish f, boolean shader) {
      int s = slot(m, f, shader);
      pollUpload(s);
      if (TEXTURES[s] != 0) {
         return TEXTURES[s];
      } else {
         request(s);
         return standIn();
      }
   }

   private static void pollUpload(int s) {
      if (TEXTURES[s] == 0) {
         int[][] var2 = PAINTED;
         synchronized (PAINTED) {
            int[] px = PAINTED[s];
            PAINTED[s] = null;
            if (px != null) {
               TEXTURES[s] = upload(px, 512);
            }
         }
      }
   }

   private static void request(final int s) {
      if (!REQUESTED[s]) {
         REQUESTED[s] = true;
         PAINTER.execute(new Runnable() {
            @Override
            public void run() {
               try {
                  long start = System.nanoTime();
                  int[] px = MatcapPainter.paint(Matcaps.lookOf(s), 512, Matcaps.tintable(s));
                  int[][] nArray = Matcaps.PAINTED;
                  synchronized (nArray) {
                     Matcaps.PAINTED[s] = px;
                  }

                  LOG.debug("Matcap {} painted in {} ms", new Object[]{s, (System.nanoTime() - start) / 1000000L});
               } catch (RuntimeException var8) {
                  LOG.warn("Painting a material failed; it keeps its stand-in", var8);
               }
            }
         });
      }
   }

   private static MatcapPainter.Look lookOf(int s) {
      return s < PRESETS ? MatcapPainter.look(Material.values()[s]) : MatcapPainter.look(Finish.values()[(s - PRESETS) % FINISHES]);
   }

   private static boolean tintable(int s) {
      return s >= PRESETS && s < PRESETS + FINISHES;
   }

   private static int standIn() {
      if (standIn == 0) {
         standIn = upload(MatcapPainter.paint(MatcapPainter.look(Finish.SATIN), 32, false), 32);
      }

      return standIn;
   }

   public static void paintAllForTest() {
      for (int s = 0; s < SLOTS; s++) {
         if (TEXTURES[s] == 0) {
            REQUESTED[s] = true;
            TEXTURES[s] = upload(MatcapPainter.paint(lookOf(s), 512, tintable(s)), 512);
         }
      }
   }

   public static String stateForTest() {
      int n = 0;

      for (int t : TEXTURES) {
         if (t != 0) {
            n++;
         }
      }

      return n + " of " + SLOTS;
   }

   private static int upload(int[] argb, int size) {
      argb = argb.clone();
      for (int i = 0; i < argb.length; i++) {
         int c = argb[i];
         int gray = ((c >> 16 & 255) * 54 + (c >> 8 & 255) * 183 + (c & 255) * 19) >> 8;
         argb[i] = c & 0xFF000000 | gray * 0x010101;
      }
      int id = TextureUtil.glGenTextures();
      GlStateManager.bindTexture(id);
      int levels = Integer.numberOfTrailingZeros(size);
      GL11.glTexParameteri(3553, 33085, levels);
      GL11.glTexParameteri(3553, 10241, 9987);
      GL11.glTexParameteri(3553, 10240, 9729);
      GL11.glTexParameteri(3553, 10242, 33071);
      GL11.glTexParameteri(3553, 10243, 33071);
      if (anisotropy() > 1.0F) {
         GL11.glTexParameterf(3553, 34046, Math.min(4.0F, anisotropy()));
      }

      int[] level = argb;
      int s = size;

      for (int l = 0; l <= levels; l++) {
         IntBuffer buffer = BufferUtils.createIntBuffer(s * s);
         buffer.put(level, 0, s * s);
         ((Buffer)buffer).flip();
         GL11.glTexImage2D(3553, l, 32856, s, s, 0, 32993, 33639, buffer);
         if (l < levels) {
            level = half(level, s);
            s /= 2;
         }
      }

      return id;
   }

   private static int[] half(int[] src, int s) {
      int h = s / 2;
      int[] dst = new int[h * h];

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < h; x++) {
            int a = 0;
            int r = 0;
            int g = 0;
            int b = 0;

            for (int k = 0; k < 4; k++) {
               int c = src[(y * 2 + (k >> 1)) * s + x * 2 + (k & 1)];
               a += c >>> 24;
               r += c >> 16 & 0xFF;
               g += c >> 8 & 0xFF;
               b += c & 0xFF;
            }

            dst[y * h + x] = (a + 2) / 4 << 24 | (r + 2) / 4 << 16 | (g + 2) / 4 << 8 | (b + 2) / 4;
         }
      }

      return dst;
   }

   private static float anisotropy() {
      if (maxAnisotropy < 0.0F) {
         try {
            maxAnisotropy = GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic ? GL11.glGetFloat(34047) : 1.0F;
         } catch (RuntimeException | LinkageError var1) {
            maxAnisotropy = 1.0F;
         }
      }

      return maxAnisotropy;
   }
}
