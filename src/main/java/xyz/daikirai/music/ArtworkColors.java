package xyz.daikirai.music;

/** Most populated RGB cluster, retaining its actual average instead of a preset hue/palette. */
public final class ArtworkColors {
    private ArtworkColors() { }
    public static int dominant(int[] pixels) {
        int[] counts = new int[32768];
        long[] red = new long[32768], green = new long[32768], blue = new long[32768];
        int winner = -1;
        for (int pixel : pixels) {
            if ((pixel >>> 24) < 128) continue;
            int r = pixel >> 16 & 255, g = pixel >> 8 & 255, b = pixel & 255;
            int bin = (r >> 3) << 10 | (g >> 3) << 5 | b >> 3;
            counts[bin]++; red[bin] += r; green[bin] += g; blue[bin] += b;
            if (winner < 0 || counts[bin] > counts[winner]) winner = bin;
        }
        if (winner < 0) return 0;
        return 0xFF000000 | (int)(red[winner] / counts[winner]) << 16
                | (int)(green[winner] / counts[winner]) << 8 | (int)(blue[winner] / counts[winner]);
    }
}
