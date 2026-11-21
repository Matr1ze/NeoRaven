package xyz.daikirai.render.furiah.asset;

public final class ShaderFiles {
   private static final int MAX_DEPTH = 4;

   private ShaderFiles() {
   }

   public static String text(String path) {
      return text(path, 0);
   }

   public static String require(String path) {
      String s = text(path);
      if (s == null) {
         throw new IllegalStateException("shader file " + path + (AssetSource.ready() ? " is missing" : " isn't here yet (no asset pack)"));
      } else {
         return s;
      }
   }

   private static String text(String path, int depth) {
      String s = AssetSource.text("shaders/" + path);
      if (s != null && depth < 4 && s.contains("#include")) {
         StringBuilder out = new StringBuilder(s.length() + 256);

         for (String line : s.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("#include \"") && t.endsWith("\"")) {
               String piece = text(t.substring(10, t.length() - 1), depth + 1);
               if (piece == null) {
                  return null;
               }

               out.append(piece);
               if (!piece.endsWith("\n")) {
                  out.append('\n');
               }
            } else {
               out.append(line).append('\n');
            }
         }

         out.setLength(out.length() - 1);
         return out.toString();
      } else {
         return s;
      }
   }
}
