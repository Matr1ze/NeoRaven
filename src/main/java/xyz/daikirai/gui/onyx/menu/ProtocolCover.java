package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Uploads a cover PNG from the jar's classpath as an ordinary texture.
 *
 * <p>The picker draws its covers through {@code UI.imageRounded}, which needs a texture the texture
 * manager knows. Going through {@link net.minecraft.client.renderer.texture.SimpleTexture} would
 * ask the resource pack for the file; the classpath upload used for the menu wallpapers works in
 * every environment (and is what {@link MenuBackground} already relies on), so the covers use the
 * same route and stay rounded.</p>
 */
final class ProtocolCover extends AbstractTexture {

    private static final String ROOT = "/assets/clickgui/textures/menu/";
    private static final String[] NAMES = {"cover-189", "cover-1206"};

    private final String resource;

    private ProtocolCover(String resource) {
        this.resource = resource;
    }

    /**
     * Registers every cover once: {@code [card][0]} is the poster, {@code [card][1]} the matching
     * frosted pane (the same artwork, blurred by the generator).
     */
    static ResourceLocation[][] register() {
        ResourceLocation[][] locations = new ResourceLocation[NAMES.length][2];
        for (int i = 0; i < NAMES.length; i++) {
            locations[i][0] = load("protocol/" + NAMES[i], ROOT + NAMES[i] + ".png");
            locations[i][1] = load("protocol/" + NAMES[i] + "-frost", ROOT + NAMES[i] + "-frost.png");
        }
        return locations;
    }

    private static ResourceLocation load(String path, String resource) {
        ResourceLocation location = new ResourceLocation("clickgui", path);
        if (Minecraft.getMinecraft().getTextureManager().getTexture(location) == null) {
            Minecraft.getMinecraft().getTextureManager().loadTexture(location, new ProtocolCover(resource));
        }
        return location;
    }

    @Override
    public void loadTexture(IResourceManager manager) throws IOException {
        InputStream in = ProtocolCover.class.getResourceAsStream(resource);
        if (in == null) {
            throw new FileNotFoundException(resource);
        }
        try {
            TextureUtil.uploadTextureImageAllocate(getGlTextureId(),
                    TextureUtil.readBufferedImage(in), false, false);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(resource, e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }
}
