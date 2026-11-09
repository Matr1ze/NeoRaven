package xyz.daikirai.gui.onyx.menu;

import net.minecraft.util.ResourceLocation;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

/** Soar-style two-stage title-screen splash: icon fade, then black-cover fade. */
public final class NeoRavenSplash {
    private static final ResourceLocation LOGO = new ResourceLocation("clickgui", "textures/raven.png");
    private static final long ICON_FADE_MS = 1000L;
    private static final long COVER_FADE_MS = 1000L;
    private static final long TOTAL_MS = ICON_FADE_MS + COVER_FADE_MS;
    private static long startedAt;

    public void reset() {
        // Returning from a child screen calls initGui again; the client splash is one-shot.
        if (startedAt == 0L) {
            startedAt = System.currentTimeMillis();
        }
    }

    public boolean active() {
        return startedAt > 0L && System.currentTimeMillis() - startedAt < TOTAL_MS;
    }

    public void draw(UI ui, int width, int height) {
        if (startedAt <= 0L) return;
        long age = Math.max(0L, System.currentTimeMillis() - startedAt);
        float iconAlpha = 1.0F - Ease.DECELERATE.get(Material.clamp(age / (float) ICON_FADE_MS, 0.0F, 1.0F));
        float coverAlpha = age < ICON_FADE_MS ? 1.0F
                : 1.0F - Ease.DECELERATE.get(Material.clamp((age - ICON_FADE_MS) / (float) COVER_FADE_MS,
                0.0F, 1.0F));
        if (iconAlpha <= 0.001F && coverAlpha <= 0.001F) return;
        ui.push();
        ui.rect(0.0F, 0.0F, width, height, Material.withAlpha(0xFF000000, coverAlpha));
        if (iconAlpha > 0.001F) {
            float size = Math.min(130.0F, Math.min(width, height) * 0.32F);
            float x = width * 0.5F - size * 0.5F;
            float y = height * 0.5F - size * 0.62F;
            int tint = Material.withAlpha(0xFFFFFFFF, iconAlpha);
            ui.image(LOGO, x, y, size, size, 0.0F, 0.0F, 1.0F, 1.0F, tint);
            ui.text(Typeface.HEADLINE, "NeoRaven 1.2", width * 0.5F, y + size + 24.0F,
                    tint, UI.ALIGN_CENTER);
        }
        ui.pop();
    }
}
