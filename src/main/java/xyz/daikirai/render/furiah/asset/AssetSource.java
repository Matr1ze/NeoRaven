package xyz.daikirai.render.furiah.asset;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class AssetSource {
   private AssetSource() {}

   static boolean ready() { return true; }
   public static int generation() { return 1; }

   static String text(String path) {
      try (InputStream in = AssetSource.class.getResourceAsStream("/assets/onyx/furiah/" + path)) {
         if (in == null) return null;
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         byte[] buffer = new byte[4096];
         int read;
         while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
         return new String(out.toByteArray(), StandardCharsets.UTF_8);
      } catch (IOException e) {
         throw new IllegalStateException("Cannot read Furiah shader " + path, e);
      }
   }
}
