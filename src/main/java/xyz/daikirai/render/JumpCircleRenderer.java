package xyz.daikirai.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.gui.onyx.render.TtfFont;
import xyz.daikirai.module.render.JumpCirclesModule;
import xyz.daikirai.module.render.JumpCirclesModule.Circle;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.util.List;

/** Onyx's textured ground rings, tangent glyphs and ground-plane glass shader. */
public final class JumpCircleRenderer {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final ResourceLocation RING = new ResourceLocation("onyx", "textures/effect/jump_circle.png");
    private final FloatBuffer model = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private TtfFont font;
    private Shader glass;
    private int scene = -1, sceneWidth, sceneHeight;
    private boolean failed;
    private boolean ringLoaded;

    public void reset() { failed = false; }

    public void dispose() {
        if (scene != -1) TextureUtil.deleteTexture(scene);
        scene = -1;
        sceneWidth = sceneHeight = 0;
    }

    public void render(List<Circle> circles, JumpCirclesModule settings, float partialTicks, long now) {
        Entity view = MC.getRenderViewEntity();
        if (view == null) return;
        Vec3 origin = new Vec3(view.prevPosX + (view.posX - view.prevPosX) * partialTicks,
                view.prevPosY + (view.posY - view.prevPosY) * partialTicks,
                view.prevPosZ + (view.posZ - view.prevPosZ) * partialTicks);
        renderAt(circles, settings, origin, now, MC.getFramebuffer().framebufferWidth, MC.getFramebuffer().framebufferHeight);
    }

    public void renderAt(List<Circle> circles, JumpCirclesModule settings, Vec3 origin, long now, int width, int height) {
        if (failed || circles.isEmpty()) return;
        int activeTexture = GL11.glGetInteger(0x84E0);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int program = OpenGlHelper.shadersSupported ? GL11.glGetInteger(0x8B8D) : 0;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            Shader.unbind();
            // Use raw state inside the attribute scope so Minecraft's cached state remains intact.
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);
            GL11.glColor4f(1, 1, 1, 1);
            if (settings.distortion() && (settings.blur() > 0 || settings.warp() > 0))
                distort(circles, settings, origin, now, width, height);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            if (settings.throughWalls()) GL11.glDisable(GL11.GL_DEPTH_TEST);
            else GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (settings.landingMode()) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                if (settings.tileWave()) GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                int color = settings.color();
                for (Circle circle : circles) {
                    float age = circle.progress(now, settings.lifetime());
                    Vec3 center = circle.position.subtract(origin);
                    if (settings.tileWave()) {
                        tiles(center, age, settings, color);
                    } else {
                        wave(center, age, settings, color, false);
                        if (age > 0.16F) wave(center, (age - 0.16F) / 0.84F, settings, color, true);
                    }
                }
                return;
            }
            bindRing();
            GL11.glBegin(GL11.GL_QUADS);
            for (Circle circle : circles) {
                float size = circle.radius(settings.radius(), now, settings.lifetime()) / 0.49F;
                tint(settings, circle, now);
                Vec3 p = circle.position.subtract(origin);
                vertex(p, -size, -size, 0, 0);
                vertex(p, -size, size, 0, 1);
                vertex(p, size, size, 1, 1);
                vertex(p, size, -size, 1, 0);
            }
            GL11.glEnd();
            if (settings.textMode()) text(circles, settings, origin, now);
        } catch (Exception error) {
            failed = true;
            System.err.println("[onyx] JumpCircles renderer disabled after failure: " + error);
            error.printStackTrace();
        } finally {
            GlStateManager.bindTexture(texture);
            if (OpenGlHelper.shadersSupported) OpenGlHelper.glUseProgram(program);
            GL11.glPopAttrib();
            GlStateManager.setActiveTexture(activeTexture);
        }
    }

    private static float expansion(float age) {
        return 1 - (float) Math.pow(1 - age, 4);
    }

    private static final int TILE_RADIUS = 6;
    private static final float TILE_TRAVEL = 0.64F;

    private static void tiles(Vec3 center, float age, JumpCirclesModule settings, int color) {
        double pitch = settings.radius() / (TILE_RADIUS + 0.5);
        double floor = center.yCoord + 0.022;
        float opacity = settings.opacity();
        GL11.glBegin(GL11.GL_QUADS);
        for (int row = -TILE_RADIUS; row <= TILE_RADIUS; row++) {
            for (int column = -TILE_RADIUS; column <= TILE_RADIUS; column++) {
                float distance = (float) Math.sqrt(row * row + column * column) / TILE_RADIUS;
                float phase = (age - distance * TILE_TRAVEL) / (1 - TILE_TRAVEL);
                if (distance > 1 || phase <= 0 || phase >= 1) continue;
                float flash = Math.min(1, phase * 8) * (1 - phase);
                double half = pitch * (0.38 + 0.09 * flash);
                double tileX = center.xCoord + column * pitch;
                double tileZ = center.zCoord + row * pitch;
                GL11.glColor4ub((byte) (color >> 16), (byte) (color >> 8), (byte) color,
                        (byte) Math.round(255 * opacity * flash * 0.32F));
                square(tileX, floor, tileZ, pitch * 0.49);
                GL11.glColor4ub((byte) (color >> 16), (byte) (color >> 8), (byte) color,
                        (byte) Math.round(255 * opacity * flash));
                square(tileX, floor, tileZ, half);
                float glint = Math.max(0, 1 - Math.abs(phase - 0.125F) * 12);
                if (glint > 0) {
                    GL11.glColor4f(1, 1, 1, opacity * glint * 0.8F);
                    square(tileX, floor, tileZ, half * 0.25);
                }
            }
        }
        GL11.glEnd();
    }

    private static void square(double centerX, double floor, double centerZ, double half) {
        GL11.glVertex3d(centerX - half, floor, centerZ - half);
        GL11.glVertex3d(centerX - half, floor, centerZ + half);
        GL11.glVertex3d(centerX + half, floor, centerZ + half);
        GL11.glVertex3d(centerX + half, floor, centerZ - half);
    }

    private static void wave(Vec3 center, float age, JumpCirclesModule settings, int color, boolean outer) {
        double radius = settings.radius() * expansion(age) * (outer ? 1 : 0.52);
        double height = outer ? radius * 0.24 * Math.sin(Math.PI * Math.sqrt(age)) : 0;
        float light = settings.opacity() * Math.min(1, age * 22) * (1 - age * age);
        double[] widths = {-0.055, -0.009, 0, 0.009, 0.055};
        float[] glow = {0, 0.48F, 1, 0.48F, 0};
        for (int band = outer ? -1 : 0; band < widths.length - 1; band++) {
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int segment = 0; segment <= 96; segment++) {
                double angle = segment * Math.PI / 48;
                double ripple = 1 + 0.035 * Math.sin(3 * angle + age * 4)
                        + 0.018 * Math.cos(5 * angle - age * 3);
                double crest = height * (0.84 + 0.16 * Math.sin(angle * 4 + age * 3));
                for (int side = 0; side < 2; side++) {
                    double reach = radius * (ripple + (band < 0 ? -0.04 * (1 - side) : widths[band + side]));
                    float alpha = light * (band < 0 ? side * 0.34F : glow[band + side]);
                    GL11.glColor4f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F,
                            (color & 255) / 255F, alpha);
                    GL11.glVertex3d(center.xCoord + reach * Math.cos(angle),
                            center.yCoord + 0.022 + crest * (band < 0 ? side : 1),
                            center.zCoord + reach * Math.sin(angle));
                }
            }
            GL11.glEnd();
        }
    }

    private void bindRing() throws IOException {
        if (!ringLoaded) {
            // Forge's development resource pack only exposes the mod's own namespace.
            try (InputStream in = JumpCircleRenderer.class.getResourceAsStream("/assets/onyx/textures/effect/jump_circle.png")) {
                if (in == null) throw new IOException("Missing Onyx jump circle texture");
                MC.getTextureManager().loadTexture(RING, new DynamicTexture(ImageIO.read(in)));
            }
            ringLoaded = true;
        }
        MC.getTextureManager().bindTexture(RING);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);
    }

    private static void tint(JumpCirclesModule settings, Circle circle, long now) {
        int color = settings.color();
        GL11.glColor4f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                (color & 255) / 255.0F, settings.opacity() * (1 - circle.progress(now, settings.lifetime())));
    }

    private static void vertex(Vec3 p, double x, double z, float u, float v) {
        GL11.glTexCoord2f(u, v);
        GL11.glVertex3d(p.xCoord + x, p.yCoord + 0.015, p.zCoord + z);
    }

    private void text(List<Circle> circles, JumpCirclesModule settings, Vec3 origin, long now) {
        String text = settings.text().trim();
        float textRadius = settings.radius() - settings.textSize() * 1.35F;
        if (text.isEmpty() || textRadius <= 0) return;
        if (font == null) font = TtfFont.load("/assets/onyx/font/ttf/googlesansflex_36pt-bold.ttf", 36);
        text += "  ";
        TtfFont.Glyph[] glyphs = new TtfFont.Glyph[text.length()];
        for (int i = 0; i < text.length(); i++) glyphs[i] = font.glyph(text.charAt(i));
        float baseScale = settings.textSize() / font.ascent();
        float width = font.width(text) * baseScale;
        if (width <= 0) return;
        float circumference = (float) (2 * Math.PI * textRadius);
        int repeats = Math.max(1, Math.round(circumference / width));
        float spacing = circumference / (repeats * width);
        font.bind();
        for (Circle circle : circles) {
            float growth = circle.radius(settings.radius(), now, settings.lifetime()) / settings.radius();
            if (growth <= 0) continue;
            float scale = baseScale * growth;
            float radius = textRadius * growth;
            double spin = Math.toRadians(settings.spin() * (now - circle.time) / 1000.0);
            Vec3 position = circle.position.subtract(origin);
            tint(settings, circle, now);
            float caret = 0;
            GL11.glBegin(GL11.GL_QUADS);
            for (int repeat = 0; repeat < repeats; repeat++) {
                for (TtfFont.Glyph glyph : glyphs) {
                    if (!glyph.isEmpty()) {
                        float halfWidth = glyph.width * scale / 2;
                        double angle = spin + (caret + (glyph.bearingX * scale + halfWidth) * spacing) / radius;
                        double cosine = Math.cos(angle), sine = Math.sin(angle);
                        float top = radius - glyph.bearingY * scale;
                        float bottom = top - glyph.height * scale;
                        glyphVertex(position, cosine, sine, top, -halfWidth, glyph.u0, glyph.v0);
                        glyphVertex(position, cosine, sine, bottom, -halfWidth, glyph.u0, glyph.v1);
                        glyphVertex(position, cosine, sine, bottom, halfWidth, glyph.u1, glyph.v1);
                        glyphVertex(position, cosine, sine, top, halfWidth, glyph.u1, glyph.v0);
                    }
                    caret += glyph.advance * scale * spacing;
                }
            }
            GL11.glEnd();
        }
    }

    private static void glyphVertex(Vec3 p, double cosine, double sine, double radial, double tangent, float u, float v) {
        vertex(p, radial * cosine - tangent * sine, radial * sine + tangent * cosine, u, v);
    }

    private Vec3 inverseDirection(float x, float y, float z) {
        return new Vec3(model.get(0) * x + model.get(1) * y + model.get(2) * z,
                model.get(4) * x + model.get(5) * y + model.get(6) * z,
                model.get(8) * x + model.get(9) * y + model.get(10) * z);
    }

    private void distort(List<Circle> circles, JumpCirclesModule settings, Vec3 origin, long now, int width, int height) {
        if (!OpenGlHelper.shadersSupported || !OpenGlHelper.isFramebufferEnabled()) return;
        if (glass == null) glass = Shader.load("onyx_jump_glass");
        if (!glass.valid()) return;
        model.clear(); projection.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, model);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        float px = Math.abs(projection.get(0)), py = Math.abs(projection.get(5));
        if (px <= 0 || py <= 0) return;
        Vec3 camera = origin.add(inverseDirection(-model.get(12), -model.get(13), -model.get(14)));
        Vec3 forward = inverseDirection(0, 0, -1);
        Vec3 right = inverseDirection(1, 0, 0);
        Vec3 up = right.crossProduct(forward);
        if (width <= 0 || height <= 0) return;
        if (scene == -1) scene = TextureUtil.glGenTextures();
        GlStateManager.bindTexture(scene);
        if (sceneWidth != width || sceneHeight != height) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);
            sceneWidth = width; sceneHeight = height;
        }
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        glass.use();
        try {
            glass.uniform1i("Sampler0", 0);
            glass.uniform2f("uResolution", width, height);
            glass.uniform1f("uBlur", settings.blur() * 0.12F);
            glass.uniform1f("uWarp", settings.warp() * 0.16F);
            basis("uForward", forward); basis("uRight", right); basis("uUp", up);
            glass.uniform2f("uTanHalf", 1 / px, 1 / py);
            int count = 0;
            for (int i = circles.size() - 1; i >= 0 && count < 8; i--) {
                Circle circle = circles.get(i);
                float radius = circle.radius(settings.radius(), now, settings.lifetime());
                float fade = 1 - circle.progress(now, settings.lifetime());
                if (settings.shockwave()) {
                    float age = 1 - fade;
                    float outer = expansion(Math.max(0, (age - 0.16F) / 0.84F));
                    radius = settings.radius() * Math.max(expansion(age) * 0.52F, outer) * 1.12F;
                    fade *= settings.opacity();
                } else if (settings.tileWave()) {
                    float age = 1 - fade;
                    radius = settings.radius() * Math.min(1, age / TILE_TRAVEL + 0.08F);
                    fade = settings.opacity() * Math.min(1, age * 20) * Math.min(1, fade / (1 - TILE_TRAVEL));
                }
                Vec3 center = circle.position.subtract(camera);
                if (radius < 0.05F || fade <= 0 || center.dotProduct(forward) + radius < 0.06) continue;
                float apparentSize = Math.min((float) (radius * py * 0.5 / Math.max(center.lengthVector(), 0.1)), 0.3F);
                glass.uniform4f("uLensA[" + count + "]", (float) center.xCoord, (float) center.yCoord, (float) center.zCoord, radius);
                glass.uniform4f("uLensB[" + count + "]", fade, apparentSize, 0, 0);
                count++;
            }
            glass.uniform1i("uCount", count);
            if (count > 0) postprocess();
        } finally {
            Shader.unbind();
        }
    }

    private void basis(String uniform, Vec3 vector) {
        glass.uniform3f(uniform, (float) vector.xCoord, (float) vector.yCoord, (float) vector.zCoord);
    }

    private static void postprocess() {
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(-1, -1);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, -1);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(-1, 1);
            GL11.glEnd();
        } finally {
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (blend) GL11.glEnable(GL11.GL_BLEND);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
        }
    }
}
