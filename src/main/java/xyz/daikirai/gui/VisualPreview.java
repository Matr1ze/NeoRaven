package xyz.daikirai.gui;

import net.minecraft.client.gui.GuiScreen;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.Module;

/** Side card shared by the ClickGUI layouts and the color editor. */
public final class VisualPreview {
    public interface Owner {
        Module previewModule();
    }

    private final VisualPreviewScene scene = new VisualPreviewScene();
    private Module module;
    private float entrance;

    public static boolean supports(Module module) {
        if (module == null) return false;
        switch (module.getName()) {
            case "ESP": case "PlayerESP": case "MobESP": case "Glow ESP": case "Target ESP":
            case "ItemESP": case "BedESP": case "ChestESP": case "Trails": case "JumpCircles":
            case "Hit Effect": case "Hit Afterimage": case "Wings":
                return true;
            default:
                return false;
        }
    }

    public static Module editing(GuiScreen parent) {
        return parent instanceof Owner ? ((Owner) parent).previewModule() : null;
    }

    public void draw(UI ui, Scheme scheme, Module selected, float x, float y, float width, float height,
                     float screenW, float screenH, float delta) {
        if (!supports(selected)) {
            if (module != null) close();
            return;
        }

        float gap = 8;
        float right = screenW - (x + width) - gap * 2;
        float left = x - gap * 2;
        boolean useRight = right >= 158 || right >= left;
        float cardWidth = Math.min(180, useRight ? right : left);
        if (cardWidth < 70) return;
        float cardHeight = Math.min(screenH - 2 * gap, cardWidth * 1.24F + 31);
        float cardX = useRight ? x + width + gap : x - gap - cardWidth;
        float cardY = Math.max(gap, Math.min(y + (height - cardHeight) * 0.5F, screenH - gap - cardHeight));
        if (module != selected) {
            module = selected;
            entrance = 0;
        }
        entrance = Math.min(1, entrance + delta / 180);
        float fade = 1 - (1 - entrance) * (1 - entrance);
        cardX += (useRight ? -1 : 1) * 7 * (1 - fade);
        int texture = scene.render(selected, scheme, delta / 1000);

        ui.alphaPush(fade);
        try {
            ui.shadow(cardX, cardY, cardWidth, cardHeight, 16, 3);
            ui.rounded(cardX, cardY, cardWidth, cardHeight, 16, scheme.surfaceContainer);
            ui.outline(cardX, cardY, cardWidth, cardHeight, 16, 1, scheme.outlineVariant);
            ui.text(Typeface.LABEL, label(), cardX + 11, cardY + 14, scheme.onSurface, UI.ALIGN_LEFT);
            float sceneX = cardX + 7;
            float sceneY = cardY + 26;
            float sceneW = cardWidth - 14;
            float sceneH = cardHeight - 33;
            if (texture != 0) {
                ui.scissorPush(sceneX, sceneY, sceneW, sceneH);
                try {
                    float drawW = Math.min(sceneW, sceneH * VisualPreviewScene.ASPECT);
                    float drawH = drawW / VisualPreviewScene.ASPECT;
                    float drawX = sceneX + (sceneW - drawW) * 0.5F;
                    float drawY = sceneY + (sceneH - drawH) * 0.5F;
                    ui.rounded(sceneX, sceneY, sceneW, sceneH, 8, scheme.surfaceContainerLow);
                    VisualPreviewScene.blit(texture, drawX, drawY, drawW, drawH, ui.alpha());
                } finally {
                    ui.scissorPop();
                }
            } else {
                ui.text(Typeface.LABEL, unavailable(), sceneX + sceneW / 2, sceneY + sceneH / 2,
                        scheme.onSurfaceVariant, UI.ALIGN_CENTER);
            }
        } finally {
            ui.alphaPop();
        }
    }

    public void close() {
        if (module == null) return;
        module = null;
        entrance = 0;
        scene.close();
    }

    private static String label() { return "Preview"; }
    private static String unavailable() { return "Preview unavailable"; }
}
