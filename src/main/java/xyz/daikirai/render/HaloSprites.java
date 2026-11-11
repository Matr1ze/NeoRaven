package xyz.daikirai.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Halo sprites for the {@code Halo} module, ported from blue-archive-halo's {@code HaloRenderer}.
 *
 * <p>The upstream extension shipped eight PNG layers (two or three per character: a plain glowing
 * ring plus spiked rings stacked above it) and drew them as quads on a horizontal plane over the
 * player's head. The sprite loading is kept here: the PNGs are read from the jar's classpath and
 * uploaded as {@link DynamicTexture}s with linear filtering, so the thin glowing rings stay smooth at
 * any distance.</p>
 */
public final class HaloSprites {

    private static final String ROOT = "/assets/clickgui/halo/";
    private static final Map<String, List<Integer>> CACHE = new HashMap<String, List<Integer>>();

    /** Stacking distance per layer, relative to {@code Size / 1.5}, exactly like upstream. */
    public static final double LAYER_SPACING = 0.08D;
    /** Hoshino's three layers sit closer together. */
    public static final double LAYER_SPACING_NARROW = 0.06D;

    private HaloSprites() {
    }

    /**
     * Returns the GL texture ids of one style's layers, loading them on first use.
     *
     * @param style folder under {@code assets/clickgui/halo}
     */
    public static List<Integer> layers(String style) {
        List<Integer> cached = CACHE.get(style);
        if (cached != null) {
            return cached;
        }
        List<Integer> textures = new ArrayList<Integer>();
        for (int layer = 0; layer < 3; layer++) {
            String resource = ROOT + style + "/layer" + layer + ".png";
            // Two-layer styles simply have no layer2 - stop quietly instead of warning.
            if (HaloSprites.class.getResource(resource) == null) {
                break;
            }
            int texture = load(resource);
            if (texture <= 0) {
                break;
            }
            textures.add(texture);
        }
        CACHE.put(style, textures);
        return textures;
    }

    /** Uploads one PNG as its own texture; 0 when the resource is missing or unreadable. */
    private static int load(String resource) {
        try (InputStream in = HaloSprites.class.getResourceAsStream(resource)) {
            if (in == null) {
                System.err.println("[halo] missing sprite " + resource);
                return 0;
            }
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                return 0;
            }
            DynamicTexture texture = new DynamicTexture(image);
            Minecraft.getMinecraft().getTextureManager().loadTexture(
                    new ResourceLocation("clickgui", "halo/" + resource.substring(ROOT.length())), texture);
            int id = texture.getGlTextureId();
            GlStateManager.bindTexture(id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GlStateManager.bindTexture(0);
            return id;
        } catch (Throwable t) {
            System.err.println("[halo] sprite failed: " + resource + " - " + t);
            return 0;
        }
    }
}
