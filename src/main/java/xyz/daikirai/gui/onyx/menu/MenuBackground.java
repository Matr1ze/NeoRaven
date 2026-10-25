package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.client.ClientConfig;
import xyz.daikirai.gui.onyx.render.UI;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The backdrop the rewritten title screens draw behind their cards.
 *
 * <p>The Onyx cloud shader and the flat Android gradient need no texture at all. A Wallpaper Engine
 * scene cannot be played (it is a 4K H.264 video or a compressed texture pack and 1.8.9 has no
 * decoder), so one frame is decoded offline into a crisp 1920x1080 still: 8.3 MB of video memory,
 * drawn 1:1 on a 1080p window. A still was chosen over a frame loop on purpose - an 8 fps
 * ping-pong read as twitching and the small frames looked soft.</p>
 *
 * <p>Still textures are uploaded lazily and every wallpaper except the selected one is deleted, so
 * picking a shader background costs nothing at all.</p>
 */
public final class MenuBackground {

    /** One wallpaper: a 1920x1080 still shipped inside the jar. */
    private static final class Still {
        final String resource;

        Still(String resource) {
            this.resource = resource;
        }
    }

    public enum Style {
        CLOUDS("Onyx clouds", "Theme coloured cloud shader", null),
        GRADIENT("Android gradient", "Flat wallpaper in the accent colour", null),
        CYRENE("Cyrene", "Wallpaper Engine scene, 1920x1080 still",
                new Still("/assets/clickgui/textures/menu/cyrene.png")),
        RAINY("Rainy Day", "Wallpaper Engine scene, 1920x1080 still",
                new Still("/assets/clickgui/textures/menu/rainy.png"));

        final String label;
        final String hint;
        final Still still;

        Style(String label, String hint, Still still) {
            this.label = label;
            this.hint = hint;
            this.still = still;
        }

        boolean wallpaper() {
            return still != null;
        }
    }

    private static final File FILE = new File(ClientConfig.DIR, "menu_background.txt");

    /** Overscan kept around the artwork so the parallax pan never exposes an edge. */
    private static final float OVERSCAN = 1.12F;
    /** How far the artwork slides at the edge of the screen, as a fraction of its size. */
    private static final float PARALLAX_X = 1.0F;
    private static final float PARALLAX_Y = 0.72F;
    /** Smoothing time constant of the cursor follow, in milliseconds. */
    private static final float FOLLOW_MS = 90.0F;

    private static Style current = read();
    /** Normalised cursor target (-1..1) and the smoothed value actually drawn. */
    private static float targetX;
    private static float targetY;
    private static float followX;
    private static float followY;
    private static long followFrame;
    /** Mica blur program; built once, {@code null} when the driver refuses it. */
    private static xyz.daikirai.gui.onyx.render.Shader mica;
    private static boolean micaTried;
    /** Uploaded atlas textures, keyed by resource path. */
    private static final Map<String, Integer> textures = new HashMap<String, Integer>();
    /** Mean colour of each atlas' first frame, keyed by resource path (used by the dock tint). */
    private static final Map<String, Integer> averages = new HashMap<String, Integer>();

    private MenuBackground() {
    }

    /** Styles whose atlas is actually shipped; a missing resource would draw nothing. */
    public static Style[] styles() {
        java.util.List<Style> list = new java.util.ArrayList<Style>();
        for (Style style : Style.values()) {
            if (!style.wallpaper() || MenuBackground.class.getResource(style.still.resource) != null) {
                list.add(style);
            }
        }
        return list.toArray(new Style[list.size()]);
    }

    public static Style current() {
        return current;
    }

    /** Chooses a backdrop and remembers it for the next launch. */
    public static void select(Style style) {
        if (style == null || style == current) {
            return;
        }
        current = style;
        write(style);
        releaseExcept(style);
    }

    /** Whether the selected wallpaper's atlas is uploaded (the harness uses it to prove the free). */
    public static boolean stillUploaded() {
        return current.wallpaper() && textures.containsKey(current.still.resource);
    }

    /**
     * Mean colour of the current backdrop, so the dock pill can sit on the artwork instead of
     * cutting a white hole in it. Shader backdrops fall back to the theme accent.
     */
    public static int averageColor() {
        if (current.wallpaper()) {
            Integer average = averages.get(current.still.resource);
            if (average != null) {
                return average;
            }
        }
        return xyz.daikirai.gui.onyx.material.Theme.accent();
    }

    /** Drops every atlas except the one the given style needs. */
    private static void releaseExcept(Style keep) {
        String wanted = keep.wallpaper() ? keep.still.resource : null;
        for (Map.Entry<String, Integer> entry : new HashMap<String, Integer>(textures).entrySet()) {
            if (entry.getKey().equals(wanted)) {
                continue;
            }
            TextureUtil.deleteTexture(entry.getValue());
            textures.remove(entry.getKey());
        }
    }

    /**
     * Records where the cursor is, normalised to -1..1 across the window. The background then
     * slides a little against it (parallax, the trick SigmaJello's title screen uses) with an
     * exponential follow, so the artwork feels attached to the pointer without snapping.
     */
    public static void mouse(int mouseX, int mouseY, int width, int height) {
        targetX = width <= 0 ? 0.0F : Math.max(-1.0F, Math.min(1.0F, mouseX * 2.0F / width - 1.0F));
        targetY = height <= 0 ? 0.0F : Math.max(-1.0F, Math.min(1.0F, mouseY * 2.0F / height - 1.0F));
    }

    private static void follow() {
        long now = System.currentTimeMillis();
        float dt = followFrame == 0L ? 16.0F : Math.min(100.0F, now - followFrame);
        followFrame = now;
        float k = 1.0F - (float) Math.exp(-dt / FOLLOW_MS);
        followX += (targetX - followX) * k;
        followY += (targetY - followY) * k;
    }

    public static void draw(UI ui, int width, int height) {
        draw(ui, width, height, false);
    }

    /**
     * Draws the chosen backdrop over the given area.
     *
     * @param light only matters for the shader choices: the light title screen keeps the pale
     *              Android wallpaper instead of the dark Onyx clouds
     */
    public static void draw(UI ui, int width, int height, boolean light) {
        follow();
        drawInto(ui, current, 0.0F, 0.0F, width, height, light, System.currentTimeMillis(), true);
        if (current.wallpaper() && !light) {
            // The stills are bright; the dark menus draw near-white text over them.
            ui.rect(0.0F, 0.0F, width, height, 0x40000000);
        }
    }

    /** Draws one backdrop fitted into a rectangle, without the cursor parallax (preview). */
    public static void drawInto(UI ui, Style style, float x, float y, float w, float h,
                                boolean light, long timeMs) {
        drawInto(ui, style, x, y, w, h, light, timeMs, false);
    }

    private static void drawInto(UI ui, Style style, float x, float y, float w, float h,
                                 boolean light, long timeMs, boolean parallax) {
        if (style == null || w <= 0.0F || h <= 0.0F) {
            return;
        }
        ui.push();
        ui.translate(x, y);
        try {
            if (style.wallpaper()) {
                drawStill(style, ui, w, h, parallax);
            } else if (style == Style.GRADIENT || light) {
                shaderBackdrop(ui, w, h, parallax, true);
            } else {
                shaderBackdrop(ui, w, h, parallax, false);
            }
        } finally {
            ui.pop();
        }
    }

    /** Uploads an atlas once; returns -1 when the resource is missing. */
    private static int texture(Still still) {
        Integer id = textures.get(still.resource);
        if (id != null) {
            return id;
        }
        InputStream in = MenuBackground.class.getResourceAsStream(still.resource);
        if (in == null) {
            return -1;
        }
        try {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                return -1;
            }
            averages.put(still.resource, average(image));
            int uploaded = TextureUtil.uploadTextureImageAllocate(TextureUtil.glGenTextures(),
                    image, false, false);
            if (uploaded > 0) {
                textures.put(still.resource, uploaded);
            }
            return uploaded;
        } catch (Exception e) {
            return -1;
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Mean colour of an image, sampled on a coarse grid (cheap, called once per upload). */
    private static int average(BufferedImage image) {
        long r = 0L;
        long g = 0L;
        long b = 0L;
        int count = 0;
        int stepX = Math.max(1, image.getWidth() / 32);
        int stepY = Math.max(1, image.getHeight() / 18);
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                int rgb = image.getRGB(x, y);
                r += (rgb >> 16) & 0xFF;
                g += (rgb >> 8) & 0xFF;
                b += rgb & 0xFF;
                count++;
            }
        }
        if (count == 0) {
            return 0xFF808080;
        }
        return 0xFF000000 | ((int) (r / count) << 16) | ((int) (g / count) << 8) | (int) (b / count);
    }

    /** Shader backdrops: translated by the parallax and drawn with the same overscan. */
    private static void shaderBackdrop(UI ui, float w, float h, boolean parallax, boolean gradient) {
        float dx = 0.0F;
        float dy = 0.0F;
        if (parallax) {
            float over = (OVERSCAN - 1.0F) * 0.5F;
            dx = followX * w * over * PARALLAX_X;
            dy = followY * h * over * PARALLAX_Y;
            ui.translate(-(OVERSCAN - 1.0F) * 0.5F * w + dx, -(OVERSCAN - 1.0F) * 0.5F * h + dy);
        }
        float dw = w * (parallax ? OVERSCAN : 1.0F);
        float dh = h * (parallax ? OVERSCAN : 1.0F);
        if (gradient) {
            AndroidWallpaper.draw(ui, Math.round(dw), Math.round(dh));
        } else {
            MenuBackdrop.draw(ui, Math.round(dw), Math.round(dh));
        }
    }

    private static void drawStill(Style style, UI ui, float w, float h, boolean parallax) {
        Still still = style.still;
        int id = texture(still);
        if (id <= 0) {
            return;
        }
        // Cover fit: keep the 16:9 source, crop whichever side has spare room.
        float u0 = 0.0F;
        float u1 = 1.0F;
        float v0 = 0.0F;
        float v1 = 1.0F;
        float imageAspect = 16.0F / 9.0F;
        float rectAspect = w / h;
        if (rectAspect > imageAspect) {
            float visible = imageAspect / rectAspect;
            v0 = (1.0F - visible) * 0.5F;
            v1 = v0 + visible;
        } else {
            float visible = rectAspect / imageAspect;
            u0 = (1.0F - visible) * 0.5F;
            u1 = u0 + visible;
        }
        if (parallax) {
            // Zoom in by the overscan and slide the visible window towards the cursor, using the
            // margin the zoom created so the artwork never runs out of pixels at the edges.
            float cx = (u0 + u1) * 0.5F;
            float cy = (v0 + v1) * 0.5F;
            float halfW = (u1 - u0) * 0.5F / OVERSCAN;
            float halfH = (v1 - v0) * 0.5F / OVERSCAN;
            float marginU = (u1 - u0) * 0.5F - halfW;
            float marginV = (v1 - v0) * 0.5F - halfH;
            cx += followX * marginU * PARALLAX_X;
            cy += followY * marginV * PARALLAX_Y;
            u0 = cx - halfW;
            u1 = cx + halfW;
            v0 = cy - halfH;
            v1 = cy + halfH;
        }

        int mode = MenuTheme.blurMode();
        if (mode == 2 && parallax) {
            // Frosted glass: the client's own material - blur the frame we just painted the
            // wallpaper into, then composite the tint over it exactly like the panels do.
            drawQuad(id, u0, v0, u1, v1, w, h);
            xyz.daikirai.gui.onyx.render.Blur.prepare();
            xyz.daikirai.gui.onyx.material.Scheme scheme = xyz.daikirai.gui.onyx.material.Theme.scheme();
            ui.frost(0.0F, 0.0F, w, h, 0.0F, scheme.surface, 0.55F);
            return;
        }
        if (mode != 0 && mica()) {
            // Mica, and the frosted choice in the picker preview (capturing mid-frame there would
            // bake the wrong pixels into the glass).
            drawMica(id, u0, v0, u1, v1, w, h);
            return;
        }
        drawQuad(id, u0, v0, u1, v1, w, h);
    }

    /** Plain textured quad: the wallpaper blitted into the current rect. */
    private static void drawQuad(int id, float u0, float v0, float u1, float v1, float w, float h) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(0.0F, 0.0F);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(0.0F, h);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(w, h);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(w, 0.0F);
        GL11.glEnd();
        GlStateManager.disableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Builds the Mica program once; {@code null} when shaders are unavailable. */
    private static boolean mica() {
        if (!micaTried) {
            micaTried = true;
            mica = xyz.daikirai.gui.onyx.render.Shader.load("menu_mica");
        }
        return mica != null && mica.valid();
    }

    /**
     * One quad through the Mica program: {@code Sampler0} is the wallpaper and the vertex texcoords
     * carry the visible window, so the shader blurs exactly the pixels that end up on screen.
     */
    private static void drawMica(int id, float u0, float v0, float u1, float v1,
                                 float w, float h) {
        xyz.daikirai.gui.onyx.material.Scheme scheme = xyz.daikirai.gui.onyx.material.Theme.scheme();
        int tint = MenuTheme.dark() ? scheme.surface : scheme.surfaceBright;
        mica.use();
        mica.uniform1i("Sampler0", 0);
        mica.uniform2f("uTexel", 1.0F / 1920.0F, 1.0F / 1080.0F);
        // A very wide kernel is what makes it read as Mica instead of "slightly out of focus".
        mica.uniform1f("uRadius", 38.0F);
        mica.uniform3f("uTint", (tint >> 16 & 0xFF) / 255.0F, (tint >> 8 & 0xFF) / 255.0F,
                (tint & 0xFF) / 255.0F);
        mica.uniform1f("uTintAmount", MenuTheme.dark() ? 0.40F : 0.30F);
        mica.uniform1f("uDesaturate", 0.22F);

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(0.0F, 0.0F);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(0.0F, h);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(w, h);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(w, 0.0F);
        GL11.glEnd();
        xyz.daikirai.gui.onyx.render.Shader.unbind();
        GlStateManager.disableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static String label(Style style) {
        return style.label;
    }

    public static String hint(Style style) {
        return style.hint;
    }

    private static Style read() {
        try {
            if (FILE.isFile()) {
                BufferedReader reader = new BufferedReader(new FileReader(FILE));
                try {
                    String line = reader.readLine();
                    if (line != null) {
                        String value = line.trim().toLowerCase(Locale.ROOT);
                        for (Style style : Style.values()) {
                            if (style.name().toLowerCase(Locale.ROOT).equals(value)) {
                                return style;
                            }
                        }
                    }
                } finally {
                    reader.close();
                }
            }
        } catch (Exception ignored) {
        }
        // First launch (no menu_background.txt): start on the Rainy Day still.
        return Style.RAINY;
    }

    private static void write(Style style) {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return;
            }
            FileWriter writer = new FileWriter(FILE, false);
            try {
                writer.write(style.name() + "\n");
            } finally {
                writer.close();
            }
        } catch (Exception ignored) {
        }
    }
}
