package xyz.daikirai.gui.onyx;

import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.Module;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compact Chinese translation card shown while hovering a module in the ClickGUI. */
final class ModuleTooltip {

    private static final float MAX_WIDTH = 252.0F;
    private static final float MIN_WIDTH = 154.0F;
    private static final float PADDING = 10.0F;
    private static final float TITLE_LINE = 20.0F;
    private static final float ZH_LINE = 16.0F;
    private static final float DESC_LINE = 15.0F;

    private ModuleTooltip() {
    }

    static void draw(UI ui, Scheme scheme, Module module, float mouseX, float mouseY,
                     float panelX, float panelY, float panelWidth, float panelHeight) {
        ModuleTranslations.Entry entry = ModuleTranslations.get(module);
        if (entry == null) {
            return;
        }

        float textWidth = MAX_WIDTH - PADDING * 2.0F;
        String title = ui.truncate(Typeface.HEADLINE, Text.strip(module.getName()), textWidth);
        String chinese = entry.zh;
        if (chinese.trim().equalsIgnoreCase(title.trim())) {
            chinese = "";
        } else {
            chinese = ui.truncate(Typeface.BODY_MEDIUM, chinese, textWidth);
        }
        List<String> description = wrap(entry.desc, textWidth, 4);

        float contentWidth = Typeface.HEADLINE.width(title);
        if (!chinese.isEmpty()) {
            contentWidth = Math.max(contentWidth, Typeface.BODY_MEDIUM.width(chinese));
        }
        for (String line : description) {
            contentWidth = Math.max(contentWidth, Typeface.BODY.width(line));
        }
        float width = Material.clamp(contentWidth + PADDING * 2.0F, MIN_WIDTH, MAX_WIDTH);
        float height = PADDING * 2.0F + TITLE_LINE;
        if (!chinese.isEmpty()) {
            height += ZH_LINE;
        }
        height += description.size() * DESC_LINE;

        float x = mouseX + 16.0F;
        float y = mouseY + 18.0F;
        if (x + width > panelX + panelWidth - 8.0F) {
            x = mouseX - width - 16.0F;
        }
        if (y + height > panelY + panelHeight - 8.0F) {
            y = mouseY - height - 18.0F;
        }
        x = Material.clamp(x, panelX + 8.0F, panelX + panelWidth - width - 8.0F);
        y = Material.clamp(y, panelY + 8.0F, panelY + panelHeight - height - 8.0F);

        ui.shadow(x, y, width, height, 10.0F, 2);
        ui.rounded(x, y, width, height, 10.0F, scheme.inverseSurface);
        ui.outline(x, y, width, height, 10.0F, 1.0F,
                Material.withAlpha(scheme.outlineVariant, 0.35F));

        float cursor = y + PADDING;
        ui.textTop(Typeface.HEADLINE, title, x + PADDING, cursor, scheme.inverseOnSurface, UI.ALIGN_LEFT);
        cursor += TITLE_LINE;
        if (!chinese.isEmpty()) {
            ui.textTop(Typeface.BODY_MEDIUM, chinese, x + PADDING, cursor,
                    scheme.inversePrimary, UI.ALIGN_LEFT);
            cursor += ZH_LINE;
        }
        for (String line : description) {
            ui.textTop(Typeface.BODY, line, x + PADDING, cursor,
                    Material.withAlpha(scheme.inverseOnSurface, 0.78F), UI.ALIGN_LEFT);
            cursor += DESC_LINE;
        }
    }

    /** BreakIterator handles both CJK character breaks and Latin word breaks. */
    private static List<String> wrap(String text, float maxWidth, int maxLines) {
        List<String> lines = new ArrayList<String>();
        if (text == null || text.trim().isEmpty()) {
            return lines;
        }
        BreakIterator iterator = BreakIterator.getLineInstance(Locale.SIMPLIFIED_CHINESE);
        iterator.setText(text);
        String current = "";
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String token = text.substring(start, end);
            String candidate = current + token;
            if (!current.isEmpty() && Typeface.BODY.width(candidate.trim()) > maxWidth) {
                lines.add(current.trim());
                if (lines.size() == maxLines) {
                    return lines;
                }
                current = token.trim();
            } else {
                current = candidate;
            }
        }
        if (!current.trim().isEmpty() && lines.size() < maxLines) {
            lines.add(current.trim());
        }
        return lines;
    }
}
