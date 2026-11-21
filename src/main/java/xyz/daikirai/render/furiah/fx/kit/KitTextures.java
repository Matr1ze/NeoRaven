package xyz.daikirai.render.furiah.fx.kit;

import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.stroke.StrokeTextures;

public final class KitTextures {
   private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("NeoRaven/JumpCircles");
   private static final ConcurrentLinkedQueue<KitTextures.Painted> READY = new ConcurrentLinkedQueue<>();
   private static final AtomicInteger PENDING = new AtomicInteger();
   private static ExecutorService painter;
   private static long warmedAt;
   private static volatile String summary = "not warmed";

   private KitTextures() {
   }

   private static synchronized ExecutorService painter() {
      if (painter == null) {
         painter = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
               Thread t = new Thread(r, "Furiah's Visuals kit textures");
               t.setDaemon(true);
               t.setPriority(1);
               return t;
            }
         });
      }

      return painter;
   }

   public static void warmUp() {
      warmedAt = System.nanoTime();
      Ink.warmUp();
      Smoke.warmUp();
      Paper.warmUp();
      Glass.warmUp();
      Clockwork.warmUp();
      Cards.warmUp();
      Comic.warmUpTextures();
      Glitch.warmUp();
      StrokeTextures.warmUp();
   }

   public static void uploadReady() {
      KitTextures.Painted p;
      while ((p = READY.poll()) != null) {
         KitTextures.Image[] im = p.images;
         if (p.textures == null && im != null) {
            p.upload(im);
            done();
            return;
         }

         p.images = null;
         done();
      }
   }

   private static void done() {
      if (PENDING.decrementAndGet() == 0) {
         summary = String.format(Locale.ROOT, "all painted ahead and uploaded %d ms after start-up", (System.nanoTime() - warmedAt) / 1000000L);
         LOG.info("Kit textures: {}", new Object[]{summary});
      }
   }

   public static String stateForTest() {
      int left = PENDING.get();
      return left > 0 ? left + " still being painted or waiting for upload" : summary;
   }

   public static final class Image {
      final int[][] argb;
      final byte[][] la;
      final int width;
      final int height;
      final boolean repeat;
      final Object extra;

      private Image(int[][] argb, byte[][] la, int width, int height, boolean repeat, Object extra) {
         this.argb = argb;
         this.la = la;
         this.width = width;
         this.height = height;
         this.repeat = repeat;
         this.extra = extra;
      }

      public static KitTextures.Image argb(int[] px, int width, int height, boolean mipmap, boolean repeat) {
         Object object;
         if (mipmap) {
            object = Textures.mipChain(px, width, height);
         } else {
            int[][] nArrayArray = new int[1][];
            object = nArrayArray;
            nArrayArray[0] = px;
         }

         return new KitTextures.Image((int[][])object, (byte[][])null, width, height, repeat, null);
      }

      public static KitTextures.Image la(byte[] la, int width, int height, boolean repeat) {
         return new KitTextures.Image((int[][])null, Textures.laMipChain(la, width, height), width, height, repeat, null);
      }

      public KitTextures.Image with(Object extra) {
         return new KitTextures.Image(this.argb, this.la, this.width, this.height, this.repeat, extra);
      }

      int upload() {
         return this.la != null
            ? Textures.uploadLAChain(this.la, this.width, this.height, this.repeat)
            : Textures.uploadChain(this.argb, this.width, this.height, this.repeat);
      }
   }

   public static final class Painted {
      final String name;
      final KitTextures.Painter painter;
      volatile KitTextures.Image[] images;
      private int[] textures;
      private Object[] extras;

      public Painted(String name, KitTextures.Painter painter) {
         this.name = name;
         this.painter = painter;
      }

      public int texture(int i) {
         if (this.textures == null) {
            KitTextures.Image[] im = this.images;
            this.upload(im != null ? im : this.painter.paint());
         }

         return this.textures[i];
      }

      public int texture() {
         return this.texture(0);
      }

      public Object extra(int i) {
         this.texture(i);
         return this.extras[i];
      }

      public boolean uploaded() {
         return this.textures != null;
      }

      private void upload(KitTextures.Image[] im) {
         int[] t = new int[im.length];
         Object[] x = new Object[im.length];

         for (int k = 0; k < im.length; k++) {
            t[k] = im[k].upload();
            x[k] = im[k].extra;
         }

         this.extras = x;
         this.textures = t;
         this.images = null;
      }

      public void warm() {
         KitTextures.PENDING.incrementAndGet();
         KitTextures.painter().execute(new Runnable() {
            @Override
            public void run() {
               if (Painted.this.textures != null) {
                  KitTextures.done();
               } else {
                  try {
                     long start = System.nanoTime();
                     Painted.this.images = Painted.this.painter.paint();
                     KitTextures.READY.add(Painted.this);
                     LOG.debug("Kit texture {} painted in {} ms", new Object[]{Painted.this.name, (System.nanoTime() - start) / 1000000L});
                  } catch (RuntimeException | LinkageError var3) {
                     LOG.warn("Painting the kit texture {} ahead failed", new Object[]{Painted.this.name, var3});
                     KitTextures.done();
                  }
               }
            }
         });
      }
   }

   public interface Painter {
      KitTextures.Image[] paint();
   }
}
