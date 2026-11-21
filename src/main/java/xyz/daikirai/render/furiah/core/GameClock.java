package xyz.daikirai.render.furiah.core;

public final class GameClock {
   private static final long START = System.nanoTime();

   private GameClock() {}

   public static long nanos() { return System.nanoTime() - START; }
   public static long millis() { return nanos() / 1_000_000L; }
   public static double seconds() { return nanos() / 1_000_000_000.0; }
}
