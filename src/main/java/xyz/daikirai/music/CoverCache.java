package xyz.daikirai.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Album art / avatar cache.
 *
 * <p>Images are fetched on a small daemon pool and uploaded to GL on the render thread: VBO and
 * texture creation must not happen on a network thread, and doing it in the draw path would stall
 * the frame for the length of a decode. {@link #get(String)} therefore returns {@code null} for
 * the first few frames and the caller simply draws the placeholder card until it resolves.</p>
 *
 * <p>The cache is bounded (the textures are 300x300 RGBA, so a hundred of them would be a
 * noticeable amount of VRAM); the oldest entry is deleted when the limit is reached.</p>
 */
public final class CoverCache {

    /**
     * Resident texture budget. A long playlist plus the recommendation grid easily passes a
     * hundred distinct covers, and every eviction used to delete a texture that was still on
     * screen; scrolling then showed the placeholder until the download ran again.
     */
    private static final int MAX_TEXTURES = 256;
    /** How many cover payloads stay around so an evicted texture can be re-uploaded instantly. */
    private static final int MAX_BYTES = 512;
    private static final int TIMEOUT_CONNECT = 8000;
    private static final int TIMEOUT_READ = 12000;

    private static final Map<String, ResourceLocation> TEXTURES =
            new LinkedHashMap<String, ResourceLocation>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
                    if (size() > MAX_TEXTURES) {
                        Minecraft.getMinecraft().getTextureManager().deleteTexture(eldest.getValue());
                        return true;
                    }
                    return false;
                }
            };
    private static final Set<String> IN_FLIGHT = new HashSet<String>();
    private static final Set<String> FAILED = new HashSet<String>();
    private static final Map<String, byte[]> READY = new ConcurrentHashMap<String, byte[]>();
    /** Raw payload of every uploaded cover, so an evicted texture can come back instantly. */
    private static final Map<String, byte[]> BYTES =
            new LinkedHashMap<String, byte[]>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                    return size() > MAX_BYTES;
                }
            };
    private static final Map<String, Integer> DOMINANT = new ConcurrentHashMap<String, Integer>();
    private static final Map<String, BufferedImage> PENDING = new HashMap<String, BufferedImage>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "neoraven-covers");
        thread.setDaemon(true);
        return thread;
    });
    private static int counter;

    private CoverCache() {
    }

    /** Texture for {@code url}, or {@code null} while it is still being fetched/uploaded. */
    public static ResourceLocation get(String url) {
        if (url == null || url.isEmpty() || FAILED.contains(url)) {
            return null;
        }
        ResourceLocation texture = TEXTURES.get(url);
        if (texture != null) {
            return texture;
        }
        byte[] bytes = READY.remove(url);
        if (bytes == null) {
            // Evicted but already fetched: re-upload in the same frame. Without this the row shows
            // a placeholder for the frames the re-download takes, which is what made covers flicker
            // while scrolling a long playlist.
            bytes = BYTES.get(url);
        }
        if (bytes != null) {
            texture = upload(url, bytes);
            if (texture != null) {
                return texture;
            }
        } else if (!IN_FLIGHT.contains(url)) {
            download(url);
        }
        return null;
    }

    /** Uploads decoded images; call once per frame from the render thread before drawing. */
    public static void tick() {
        if (PENDING.isEmpty()) {
            return;
        }
        List<String> done = new ArrayList<String>();
        for (Map.Entry<String, BufferedImage> entry : PENDING.entrySet()) {
            Minecraft.getMinecraft().getTextureManager().loadTexture(location(entry.getKey()),
                    new DynamicTexture(entry.getValue()));
            done.add(entry.getKey());
        }
        for (String url : done) {
            PENDING.remove(url);
        }
    }

    private static ResourceLocation upload(String url, byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                FAILED.add(url);
                return null;
            }
            Minecraft.getMinecraft().getTextureManager().loadTexture(location(url),
                    new DynamicTexture(image));
            ResourceLocation texture = location(url);
            TEXTURES.put(url, texture);
            BYTES.put(url, bytes);
            int dominant = computeDominant(image);
            if (dominant != 0) {
                DOMINANT.put(url, Integer.valueOf(dominant));
            }
            return texture;
        } catch (Throwable t) {
            FAILED.add(url);
            return null;
        }
    }

    private static ResourceLocation location(String url) {
        return new ResourceLocation("neoraven/cover/" + Integer.toHexString(url.hashCode()));
    }

    private static void download(final String url) {
        IN_FLIGHT.add(url);
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setConnectTimeout(TIMEOUT_CONNECT);
                    connection.setReadTimeout(TIMEOUT_READ);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                    connection.setRequestProperty("Referer", "https://music.163.com");
                    InputStream in = connection.getInputStream();
                    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                    byte[] chunk = new byte[8192];
                    int read;
                    while ((read = in.read(chunk)) > 0) {
                        buffer.write(chunk, 0, read);
                    }
                    in.close();
                    READY.put(url, buffer.toByteArray());
                } catch (Throwable t) {
                    FAILED.add(url);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                    IN_FLIGHT.remove(url);
                }
            }
        });
    }

    /** Stats used by the debug HUD: how many textures are resident. */
    public static int resident() {
        return TEXTURES.size();
    }

    /** Accent colour sampled from the artwork, or {@code 0} while the image is not resident. */
    public static int dominant(String url) {
        if (url == null || url.isEmpty()) {
            return 0;
        }
        Integer value = DOMINANT.get(url);
        return value == null ? 0 : value.intValue();
    }

    /**
     * Picks a usable accent from the artwork.
     *
     * <p>A plain average turns every cover grey, so this takes the circular mean of the hue
     * weighted by saturation, ignores near-neutral pixels (shadows, text, frames) and finally
     * asks for a mid-tone of that hue: the Material palette needs a chromatic seed, not the
     * average brightness of the image.</p>
     */
    private static int computeDominant(BufferedImage image) {
        try {
            int stepX = Math.max(1, image.getWidth() / 32);
            int stepY = Math.max(1, image.getHeight() / 32);
            double sin = 0.0D;
            double cos = 0.0D;
            double weight = 0.0D;
            for (int y = 0; y < image.getHeight(); y += stepY) {
                for (int x = 0; x < image.getWidth(); x += stepX) {
                    int rgb = image.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
                    if (hsb[1] < 0.14F || hsb[2] < 0.10F) {
                        continue;
                    }
                    double w = hsb[1] * (0.35D + hsb[2]);
                    double angle = hsb[0] * Math.PI * 2.0D;
                    sin += Math.sin(angle) * w;
                    cos += Math.cos(angle) * w;
                    weight += w;
                }
            }
            if (weight < 0.75D) {
                return 0;
            }
            double hue = Math.atan2(sin, cos) / (Math.PI * 2.0D);
            if (hue < 0.0D) {
                hue += 1.0D;
            }
            int argb = java.awt.Color.HSBtoRGB((float) hue, 0.62F, 0.78F);
            return 0xFF000000 | (argb & 0xFFFFFF);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static synchronized int nextId() {
        return ++counter;
    }
}
