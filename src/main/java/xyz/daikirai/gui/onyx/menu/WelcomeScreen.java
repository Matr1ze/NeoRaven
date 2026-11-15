package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import xyz.daikirai.client.FirstRun;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.util.Lang;

import java.util.ArrayList;
import java.util.List;

/**
 * Monochrome Material 3 first-run flow, in the same style as the startup splash.
 *
 * <p>Six pages: welcome, theme, glass, music, modules and done. Pages slide on a shared X axis
 * with Material's emphasized curve; each page has its own entrance animation. Hit areas are
 * recorded while drawing, so clicks always match what is on screen.</p>
 */
public final class WelcomeScreen extends GuiScreen {
    private static final int PAGES = 6;
    private static final ResourceLocation RAVEN = new ResourceLocation("clickgui", "textures/raven.png");
    private static final String[] THEMES = {"Material", "Dark", "Light", "Dark Blue", "Midnight", "Dark Purple", "Sea", "Sakura"};
    /** Accent preset (index into {@link Theme#ACCENTS}) that belongs to each theme card. */
    private static final int[] THEME_ACCENT = {0, 7, 0, 1, 1, 0, 2, 6};
    private static final int[][] PREVIEW = {{0xFF282A30,0xFF41444C,0xFFECEBF1,0xFFB8BBC4},{0xFF131314,0xFF222327,0xFFFFFFFF,0xFFEBEBEB},{0xFFFEFEFE,0xFFEEEEEE,0xFF363636,0xFF6B7581},{0xFF161C29,0xFF1B2434,0xFF9DAFD3,0xFF7483A4},{0xFF2F363D,0xFF24292E,0xFFFFFFFF,0xFFEBEBEB},{0xFF2C0E48,0xFF35185A,0xFFEAE2FC,0xFFC2BAD4},{0xFFCBE0FF,0xFFBED8EE,0xFF24354C,0xFF405570},{0xFFFFBFB2,0xFFFFDFE2,0xFF542F37,0xFF73525C}};
    private static final String[][] CATEGORIES = {{"\ue3e7", "Combat", "战斗"}, {"\ue566", "Movement", "移动"},
            {"\ue7fd", "Player", "玩家"}, {"\ue8f4", "Render", "渲染"}, {"\ue80b", "World", "世界"}};

    private static final int BG = 0xFF0B0B0C, SURFACE = 0xFF1A1A1C, SURFACE_HI = 0xFF26262A, OUTLINE = 0xFF3C3C41;
    private static final int ON = 0xFFF2F2F2, ON_VAR = 0xFF9C9CA3, DIM = 0xFF5E5E64;

    private static final Typeface.Style DISPLAY = Typeface.custom(24, 30, 36, false);
    private static final Typeface.Style TITLE = Typeface.custom(19, 20, 26, false);
    private static final Typeface.Style BODY = Typeface.custom(9, 12, 16, false);
    private static final Typeface.Style SMALL = Typeface.custom(9, 10, 13, false);
    private static final Typeface.Style BUTTON = Typeface.custom(9, 12, 15, true);
    private static final Typeface.Style LYRIC = Typeface.custom(19, 18, 22, true);

    private static final int NEXT = 1, BACK = 2, SKIP = 3, LANGUAGE = 4, OPTIONS = 5;
    private static final int THEME = 100, TOGGLE = 200, SLIDER = 300;

    private final UI ui = new UI();
    private final List<Hit> hits = new ArrayList<>();
    private int step, dir = 1, theme = Theme.surfaceTheme(), dragging = -1;
    /** Accent picked with the theme card; 0 keeps the existing accent untouched. */
    private int accent;
    private float frost = Theme.frostOpacity, liquid = Theme.liquidOpacity;
    private boolean frostOn = Theme.frost(), liquidOn = Theme.liquidGlass();
    private boolean coloured = Theme.colouredFrost, matteOn = Theme.matteLiquid;
    private float px, py;
    private long openedAt, pageAt;

    @Override public void initGui() {
        long now = System.currentTimeMillis();
        if (openedAt == 0) openedAt = now;
        pageAt = now;
    }
    @Override public boolean doesGuiPauseGame() { return false; }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        px = mouseX; py = mouseY;
        if (dragging >= 0) drag(mouseX);
        hits.clear();
        long now = System.currentTimeMillis();
        float t = (now - openedAt) / 1000F, local = (now - pageAt) / 1000F;
        float pe = Ease.EMPHASIZED.get(clamp(local / 0.45F));
        float cx = width / 2F, cy = height / 2F - 16;
        float slideX = dir * 40 * (1 - pe);
        if (step == 2) applyGlass();
        // Backdrop first, then capture it so the glass card refracts what is really behind it.
        ui.begin();
        try {
            background(t);
            if (step == 2) {
                ui.push(); ui.translate(slideX, 0); ui.alphaPush(pe);
                glassBackdrop(cx, cy, t);
                ui.alphaPop(); ui.pop();
            }
        } finally { ui.end(); }
        if (step == 2) xyz.daikirai.gui.onyx.render.Glass.prepare();
        ui.begin();
        try {
            ui.push();
            ui.translate(slideX, 0);
            ui.alphaPush(pe);
            switch (step) {
                case 0: welcome(cx, cy, t, local); break;
                case 1: themes(cx, cy, local); break;
                case 2: glass(cx, cy, t, local); break;
                case 3: music(cx, cy, t, local); break;
                case 4: modules(cx, cy, t, local); break;
                default: done(cx, cy, t, local); break;
            }
            ui.alphaPop();
            ui.pop();
            chrome(t);
        } finally { ui.end(); }
    }

    // ------------------------------------------------------------------ pages

    private void welcome(float cx, float cy, float t, float local) {
        float k = Ease.EMPHASIZED.get(clamp(local / 0.9F));
        float ry = cy - 44;
        ring(cx, ry, mix(90, 48, k), 56, t * 24, k, 1.0F, 0x66FFFFFF);
        ring(cx, ry, mix(110, 62, k), 72, -t * 14, k, 0.8F, 0x33FFFFFF);
        float size = mix(20, 58, k);
        ui.image(RAVEN, cx - size / 2, ry - size / 2, size, size, 0, 0, 1, 1, Material.withAlpha(0xFFFFFF, k));
        float a = Ease.EMPHASIZED.get(clamp((local - 0.35F) / 0.7F));
        ui.text(DISPLAY, tr("Welcome to NeoRaven", "欢迎使用 NeoRaven"), cx, cy + 40 + 16 * (1 - a), Material.withAlpha(ON, a), UI.ALIGN_CENTER);
        float b = Ease.EMPHASIZED.get(clamp((local - 0.55F) / 0.7F));
        ui.text(BODY, tr("A few quick steps to make it yours.", "几步就能设置好你的客户端。"), cx, cy + 68 + 12 * (1 - b), Material.withAlpha(ON_VAR, b), UI.ALIGN_CENTER);
        float c = Ease.EMPHASIZED.get(clamp((local - 0.75F) / 0.7F));
        ui.alphaPush(c);
        tonal(cx - 116, cy + 90, 108, 30, "\ue894", tr("Language", "语言"), LANGUAGE);
        tonal(cx + 8, cy + 90, 108, 30, "\ue8b8", tr("Options", "设置"), OPTIONS);
        ui.alphaPop();
    }

    private void themes(float cx, float cy, float local) {
        header(cx, cy - 92, tr("Choose a theme", "选一个主题"), tr("Used by the menu, HUD and music player.", "菜单、HUD 和音乐播放器都会用它。"), local);
        float cw = 66, ch = 54, gap = 12, left = cx - (4 * cw + 3 * gap) / 2, top = cy - 32;
        for (int i = 0; i < 8; i++) {
            float k = Ease.EMPHASIZED.get(clamp((local - 0.12F - i * 0.05F) / 0.5F));
            if (k <= 0.01F) continue;
            float bx = left + (i % 4) * (cw + gap), by = top + (i / 4) * (ch + 28);
            float s = mix(0.6F, 1, k), w = cw * s, h = ch * s, x = bx + (cw - w) / 2, y = by + (ch - h) / 2 + 10 * (1 - k);
            ui.alphaPush(k);
            int[] p = PREVIEW[i];
            boolean selected = theme == i, hover = hover(bx, by, cw, ch + 18);
            if (selected) ui.outline(x - 3, y - 3, w + 6, h + 6, 12, 2, ON);
            else if (hover) ui.outline(x - 3, y - 3, w + 6, h + 6, 12, 1, DIM);
            ui.rounded(x, y, w, h, 9, p[0]);
            ui.rounded(x + 6 * s, y + 8 * s, w - 12 * s, h - 16 * s, 5, p[1]);
            ui.rounded(x + 10 * s, y + 14 * s, 26 * s, 5 * s, 2, p[2]);
            ui.rounded(x + 10 * s, y + 24 * s, 36 * s, 5 * s, 2, p[3]);
            ui.text(SMALL, THEMES[i], bx + cw / 2, by + ch + 11, selected ? ON : ON_VAR, UI.ALIGN_CENTER);
            ui.alphaPop();
            hit(THEME + i, bx, by, cw, ch + 18);
        }
    }

    private void glass(float cx, float cy, float t, float local) {
        header(cx, cy - 92, tr("Glass materials", "玻璃材质"), tr("Liquid glass wins when both are on.", "两个都开时，用液态玻璃。"), local);
        float x = cx - 196, y = cy - 40, w = 160, h = 120;
        float k = Ease.EMPHASIZED.get(clamp((local - 0.1F) / 0.6F));
        ui.alphaPush(k);
        ui.scissorPush(x, y, w, h);
        float slide = (float) Math.sin(t * 1.1) * 0.5F + 0.5F;
        float cw = 96, chh = 76, gx = x + 10 + slide * (w - 20 - cw), gy = y + (h - chh) / 2;
        // The same renderer every menu and HUD uses: liquid lens, frost, or solid.
        // The opacity sliders already control the material's tint. An additional translucent
        // tint here would attenuate both the slider range and the refracted backdrop again.
        ui.glassSurface(gx, gy, cw, chh, 18, 0xFF202024);
        ui.text(SMALL, liquidOn ? tr("Liquid", "液态") : frostOn ? tr("Frost", "毛玻璃") : tr("Solid", "实心"), gx + cw / 2, gy + chh - 14, ON, UI.ALIGN_CENTER);
        ui.scissorPop();
        ui.outline(x, y, w, h, 16, 1, OUTLINE);
        ui.alphaPop();

        float rx = cx - 14, ry = cy - 40;
        String[] names = {tr("Frosted glass", "毛玻璃"), tr("Liquid glass", "液态玻璃"), tr("Coloured frost", "彩色毛玻璃"), tr("Matte liquid", "磨砂液态玻璃")};
        boolean[] values = {frostOn, liquidOn, coloured, matteOn};
        for (int i = 0; i < 4; i++) {
            float e = Ease.EMPHASIZED.get(clamp((local - 0.15F - i * 0.05F) / 0.5F));
            ui.alphaPush(e);
            float tx = rx + (i % 2) * 108, ty = ry + (i / 2) * 28 + 8 * (1 - e);
            toggle(tx, ty, names[i], values[i]);
            hit(TOGGLE + i, tx, ty - 2, 104, 22);
            ui.alphaPop();
        }
        float e = Ease.EMPHASIZED.get(clamp((local - 0.4F) / 0.5F));
        ui.alphaPush(e);
        slider(rx, ry + 70, 210, tr("Frost opacity", "毛玻璃不透明度"), frost, 0);
        slider(rx, ry + 106, 210, tr("Liquid opacity", "液态玻璃不透明度"), liquid, 1);
        ui.alphaPop();
    }

    private void music(float cx, float cy, float t, float local) {
        header(cx, cy - 92, tr("Music and lyrics", "音乐与歌词"), tr("Built-in NetEase player with 3D lyrics in the world.", "内置网易云播放器，歌词能显示在游戏世界里。"), local);
        float k = Ease.EMPHASIZED.get(clamp((local - 0.1F) / 0.7F));
        float dx = cx - 120 - 30 * (1 - k), dy = cy + 6;
        ui.alphaPush(k);
        ui.circle(dx, dy, 50, SURFACE);
        float spin = t * 45 * k;
        for (int r = 44; r > 18; r -= 5) ring(dx, dy, r, r * 2, spin, 1, 0.45F, 0x22FFFFFF);
        ring(dx, dy, 40, 24, spin, 0.25F, 1.1F, 0x77FFFFFF);
        ui.circle(dx, dy, 15, 0xFFEDEDED);
        double a = Math.toRadians(spin);
        ui.circle(dx + (float) Math.cos(a) * 9, dy + (float) Math.sin(a) * 9, 2, BG);
        ui.circle(dx, dy, 2.5F, BG);
        ui.alphaPop();

        // A sample line streamed word by word, with the translation in small type below.
        String line = tr("Starlight on your shoulder", "星光落在你的肩上");
        String sub = tr("星光落在你的肩上", "Starlight on your shoulder");
        float cycle = 4.6F, p = (local - 0.4F) % cycle;
        if (local < 0.4F) p = -1;
        float lx = cx - 40, ly = cy - 4, x = lx;
        for (int i = 0; i < line.length(); i++) {
            String ch = line.substring(i, i + 1);
            float at = i * 0.12F, ce = clamp((p - at) / 0.25F), fade = 1 - clamp((p - cycle + 0.5F) / 0.5F);
            float e = Ease.EMPHASIZED.get(ce) * fade;
            if (e > 0.01F) ui.text(LYRIC, ch, x, ly + 6 * (1 - e), Material.withAlpha(ON, e * (0.45F + 0.55F * clamp((p - at) / 0.6F))), UI.ALIGN_LEFT);
            x += ui.width(LYRIC, ch);
        }
        float se = clamp((p - line.length() * 0.12F) / 0.4F) * (1 - clamp((p - cycle + 0.5F) / 0.5F));
        ui.text(SMALL, sub, lx, ly + 24, Material.withAlpha(ON_VAR, se), UI.ALIGN_LEFT);
        float h = Ease.EMPHASIZED.get(clamp((local - 0.6F) / 0.6F));
        ui.alphaPush(h);
        chip(lx, cy + 46, tr("NetEase  >  Settings  >  3D Lyrics", "网易云  >  设置  >  3D 歌词"));
        ui.alphaPop();
    }

    private void modules(float cx, float cy, float t, float local) {
        header(cx, cy - 92, tr("Modules", "模块"), tr("Everything lives in one menu, sorted by category.", "所有功能都在一个菜单里，按分类排好。"), local);
        float gap = 72, left = cx - gap * 2;
        for (int i = 0; i < CATEGORIES.length; i++) {
            float e = Ease.EMPHASIZED.get(clamp((local - 0.1F - i * 0.07F) / 0.6F));
            float bob = (float) Math.sin(t * 2 + i * 1.2) * 3 * e;
            float x = left + i * gap, y = cy - 22 + 24 * (1 - e) + bob;
            ui.alphaPush(e);
            ui.circle(x, y, 22, SURFACE_HI);
            ui.icon(CATEGORIES[i][0], x, y, 20, ON);
            ui.text(SMALL, tr(CATEGORIES[i][1], CATEGORIES[i][2]), x, y + 36, ON_VAR, UI.ALIGN_CENTER);
            ui.alphaPop();
        }
        float e = Ease.EMPHASIZED.get(clamp((local - 0.55F) / 0.6F));
        float pulse = 0.5F + 0.5F * (float) Math.sin(t * 3.2);
        float kw = 96, kh = 30, kx = cx - kw / 2, ky = cy + 50 + 10 * (1 - e);
        ui.alphaPush(e);
        ui.rounded(kx - 4 * pulse, ky - 4 * pulse, kw + 8 * pulse, kh + 8 * pulse, 12, Material.withAlpha(0xFFFFFF, 0.08F * (1 - pulse)));
        ui.rounded(kx, ky + 3, kw, kh, 9, 0xFF050505);
        ui.rounded(kx, ky, kw, kh, 9, Material.mix(SURFACE_HI, 0xFFFFFFFF, 0.85F * pulse));
        ui.text(BUTTON, "Right Shift", cx, ky + kh / 2, Material.mix(ON, 0xFF0B0B0C, pulse), UI.ALIGN_CENTER);
        ui.text(SMALL, tr("Press it in game to open the menu.", "在游戏里按它，打开功能菜单。"), cx, ky + kh + 16, ON_VAR, UI.ALIGN_CENTER);
        ui.alphaPop();
    }

    private void done(float cx, float cy, float t, float local) {
        float k = Ease.EMPHASIZED.get(clamp(local / 1.1F));
        float ry = cy - 34;
        ring(cx, ry, mix(150, 44, k), 64, t * 30, 1, 1.0F, Material.withAlpha(0xFFFFFF, 0.25F + 0.35F * k));
        ring(cx, ry, mix(190, 58, k), 80, -t * 18, 1, 0.8F, Material.withAlpha(0xFFFFFF, 0.15F + 0.2F * k));
        float r = Ease.EMPHASIZED.get(clamp((local - 0.6F) / 0.6F));
        ui.circle(cx, ry, 38 * r, SURFACE_HI);
        float size = 50 * mix(0.8F, 1, r);
        ui.image(RAVEN, cx - size / 2, ry - size / 2, size, size, 0, 0, 1, 1, Material.withAlpha(0xFFFFFF, r));
        float a = Ease.EMPHASIZED.get(clamp((local - 0.8F) / 0.6F));
        ui.text(DISPLAY, tr("You're all set", "一切就绪"), cx, cy + 46 + 14 * (1 - a), Material.withAlpha(ON, a), UI.ALIGN_CENTER);
        ui.text(BODY, tr("You can change all of this later on the Theme page.", "这些以后都能在主题页里改。"), cx, cy + 74 + 10 * (1 - a), Material.withAlpha(ON_VAR, a), UI.ALIGN_CENTER);
    }

    // ------------------------------------------------------------------ shared pieces

    private void background(float t) {
        ui.rect(0, 0, width, height, BG);
        float fade = clamp(t / 1.0F), drift = (t * 5) % 24;
        float cx = width / 2F, cy = height / 2F;
        for (float y = 12 - drift; y < height; y += 24) {
            for (float x = 12; x < width; x += 24) {
                float dx = (x - cx) / cx, dy = (y - cy) / cy;
                float a = 0.10F * (1 - Math.min(1, (float) Math.sqrt(dx * dx + dy * dy))) * fade;
                if (a > 0.004F) ui.rect(x, y, 1, 1, Material.withAlpha(0xFFFFFF, a));
            }
        }
    }

    private void chrome(float t) {
        ui.text(SMALL, "N E O R A V E N", 18, 16, ON_VAR, UI.ALIGN_LEFT);
        if (step < PAGES - 1) textButton(width - 18, 16, tr("Skip", "跳过"), SKIP);
        float y = height - 40, total = 0;
        for (int i = 0; i < PAGES; i++) total += (i == step ? 18 : 6) + (i < PAGES - 1 ? 6 : 0);
        float x = width / 2F - total / 2;
        for (int i = 0; i < PAGES; i++) {
            float w = i == step ? 18 : 6;
            ui.rounded(x, y + 9, w, 6, 3, i == step ? ON : OUTLINE);
            x += w + 6;
        }
        if (step > 0) outlined(24, y, 84, 24, tr("Back", "上一步"), BACK);
        filled(width - 24 - 96, y, 96, 24, step == PAGES - 1 ? tr("Start", "开始") : tr("Next", "下一步"), NEXT);
    }

    private void header(float cx, float y, String title, String sub, float local) {
        float a = Ease.EMPHASIZED.get(clamp(local / 0.5F));
        ui.text(TITLE, title, cx, y + 10 * (1 - a), Material.withAlpha(ON, a), UI.ALIGN_CENTER);
        ui.text(BODY, sub, cx, y + 22 + 10 * (1 - a), Material.withAlpha(ON_VAR, a), UI.ALIGN_CENTER);
    }

    /** Dotted ring; {@code sweep} 0..1 reveals it clockwise. */
    private void ring(float cx, float cy, float r, int dots, float rotation, float sweep, float dot, int color) {
        int n = (int) (dots * clamp(sweep));
        double start = Math.toRadians(rotation);
        for (int i = 0; i < n; i++) {
            double a = start + i * Math.PI * 2 / dots;
            ui.circle(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, dot, color);
        }
    }

    /** Scene behind the glass preview: drifting stripes, a ring of text and two bright discs. */
    private void glassBackdrop(float cx, float cy, float t) {
        float x = cx - 196, y = cy - 40, w = 160, h = 120;
        float k = Ease.EMPHASIZED.get(clamp(((System.currentTimeMillis() - pageAt) / 1000F - 0.1F) / 0.6F));
        ui.alphaPush(k);
        ui.rounded(x, y, w, h, 16, SURFACE);
        ui.scissorPush(x, y, w, h);
        float shift = (t * 14) % 24;
        for (float sx = x - 24 + shift; sx < x + w; sx += 24) ui.rect(sx, y, 10, h, 0xFF3A3A40);
        ui.circle(x + w * 0.28F + (float) Math.sin(t * 0.7) * 10, y + h * 0.36F, 20, 0xFFEDEDED);
        ui.circle(x + w * 0.74F, y + h * 0.68F + (float) Math.cos(t * 0.9) * 8, 13, 0xFF8E8E95);
        ui.text(LYRIC, "NeoRaven", x + w / 2, y + h * 0.55F, 0xFFD0D0D4, UI.ALIGN_CENTER);
        ui.scissorPop();
        ui.alphaPop();
    }

    /** The glass page previews for real, so its switches drive the theme while it is open. */
    private void applyGlass() {
        Theme.frost(frostOn); Theme.liquidGlass(liquidOn);
        Theme.frostOpacity = frost; Theme.liquidOpacity = liquid;
        Theme.colouredFrost = coloured; Theme.matteLiquid = matteOn;
    }

    private void toggle(float x, float y, String label, boolean on) {
        if (on) {
            ui.rounded(x, y, 32, 18, 9, ON);
            ui.circle(x + 23, y + 9, 6.5F, BG);
        } else {
            ui.outline(x, y, 32, 18, 9, 1.5F, DIM);
            ui.circle(x + 9, y + 9, 4.5F, DIM);
        }
        ui.text(SMALL, label, x + 38, y + 9, on ? ON : ON_VAR, UI.ALIGN_LEFT);
    }

    private void slider(float x, float y, float w, String label, float value, int id) {
        ui.text(SMALL, label, x, y, ON_VAR, UI.ALIGN_LEFT);
        ui.text(SMALL, Math.round(value * 100) + "%", x + w, y, ON, UI.ALIGN_RIGHT);
        float ly = y + 14, hx = x + w * value;
        if (hx - 4 > x) ui.rounded(x, ly, hx - 4 - x, 6, 3, ON);
        if (hx + 4 < x + w) ui.rounded(hx + 4, ly, x + w - hx - 4, 6, 3, OUTLINE);
        ui.rounded(hx - 1.5F, ly - 5, 3, 16, 1.5F, ON);
        hit(SLIDER + id, x, ly - 8, w, 22);
    }

    private void chip(float x, float y, String text) {
        float w = ui.width(SMALL, text) + 20;
        ui.outline(x, y, w, 22, 8, 1, OUTLINE);
        ui.text(SMALL, text, x + 10, y + 11, ON, UI.ALIGN_LEFT);
    }

    private void tonal(float x, float y, float w, float h, String icon, String text, int id) {
        boolean hover = hover(x, y, w, h);
        ui.rounded(x, y, w, h, h / 2, hover ? 0xFF33333A : SURFACE_HI);
        ui.icon(icon, x + 18, y + h / 2, 14, ON);
        ui.text(BUTTON, text, x + 30, y + h / 2, ON, UI.ALIGN_LEFT);
        hit(id, x, y, w, h);
    }

    private void filled(float x, float y, float w, float h, String text, int id) {
        boolean hover = hover(x, y, w, h);
        ui.rounded(x, y, w, h, h / 2, hover ? 0xFFDADADA : ON);
        ui.text(BUTTON, text, x + w / 2, y + h / 2, BG, UI.ALIGN_CENTER);
        hit(id, x, y, w, h);
    }

    private void outlined(float x, float y, float w, float h, String text, int id) {
        if (hover(x, y, w, h)) ui.rounded(x, y, w, h, h / 2, 0x14FFFFFF);
        ui.outline(x, y, w, h, h / 2, 1, DIM);
        ui.text(BUTTON, text, x + w / 2, y + h / 2, ON, UI.ALIGN_CENTER);
        hit(id, x, y, w, h);
    }

    private void textButton(float right, float y, String text, int id) {
        float w = ui.width(SMALL, text) + 16, x = right - w;
        if (hover(x, y - 9, w, 18)) ui.rounded(x, y - 9, w, 18, 9, 0x14FFFFFF);
        ui.text(SMALL, text, x + 8, y, ON_VAR, UI.ALIGN_LEFT);
        hit(id, x, y - 9, w, 18);
    }

    // ------------------------------------------------------------------ input

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.contains(mouseX, mouseY)) continue;
            if (h.id == NEXT) { if (step == PAGES - 1) finish(); else go(step + 1); }
            else if (h.id == BACK) go(step - 1);
            else if (h.id == SKIP) go(PAGES - 1);
            else if (h.id == LANGUAGE) mc.displayGuiScreen(new GuiLanguage(this, mc.gameSettings, mc.getLanguageManager()));
            else if (h.id == OPTIONS) mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
            else if (h.id >= SLIDER) { dragging = h.id - SLIDER; drag(mouseX); return; }
            else if (h.id >= TOGGLE) {
                int k = h.id - TOGGLE;
                if (k == 0) frostOn = !frostOn; else if (k == 1) liquidOn = !liquidOn;
                else if (k == 2) coloured = !coloured; else matteOn = !matteOn;
            } else if (h.id >= THEME) {
                theme = h.id - THEME;
                // The cards are full colour themes, not only surface palettes: give the card its
                // matching accent seed so the menu, HUD and ClickGUI are not left with the default
                // purple once the guide is done.
                accent = Theme.ACCENTS[THEME_ACCENT[theme]];
            }
            sound();
            return;
        }
    }

    private void drag(int mouseX) {
        for (Hit h : hits) {
            if (h.id != SLIDER + dragging) continue;
            float v = clamp((mouseX - h.x) / h.w);
            if (dragging == 0) frost = v; else liquid = v;
        }
    }

    @Override protected void mouseClickMove(int x, int y, int button, long elapsed) { if (dragging >= 0) drag(x); }
    @Override protected void mouseReleased(int x, int y, int state) { dragging = -1; }
    @Override protected void keyTyped(char c, int key) {
        // Setup navigation is explicit: only the visible buttons may advance or finish it.
        // In particular, do not delegate Escape to GuiScreen, which would close the guide.
    }

    private void go(int to) {
        if (to < 0 || to >= PAGES || to == step) return;
        dir = to > step ? 1 : -1;
        step = to;
        pageAt = System.currentTimeMillis();
    }

    private void finish() {
        Theme.surfaceTheme(theme);
        if (accent != 0) {
            Theme.accent(accent);
            if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.accent != null) {
                xyz.daikirai.gui.onyx.OnyxClickGuiModule.accent.setAppearance(accent, false);
            }
        }
        Theme.frost(frostOn); Theme.liquidGlass(liquidOn);
        Theme.frostOpacity = frost; Theme.liquidOpacity = liquid; Theme.colouredFrost = coloured; Theme.matteLiquid = matteOn;
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.surfaceTheme != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.surfaceTheme.setValue(theme);
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.frostOpacity != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.frostOpacity.setValue(frost * 100.0);
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.liquidOpacity != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.liquidOpacity.setValue(liquid * 100.0);
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.colouredFrost != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.colouredFrost.set(coloured);
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.matteLiquid != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.matteLiquid.set(matteOn);
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.frostedGlass != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.frostedGlass.set(frostOn);
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.liquidGlass != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.liquidGlass.set(liquidOn);
        Theme.savePreferences(); FirstRun.dismissed(); mc.displayGuiScreen(MenuStyle.current().screen());
    }

    // ------------------------------------------------------------------ helpers

    private void hit(int id, float x, float y, float w, float h) { hits.add(new Hit(id, x, y, w, h)); }
    private boolean hover(float x, float y, float w, float h) { return px >= x && px < x + w && py >= y && py < y + h; }
    private void sound() { mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1)); }
    private static String tr(String en, String zh) { return Lang.chinese() ? zh : en; }
    private static float clamp(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    private static final class Hit {
        final int id; final float x, y, w, h;
        Hit(int id, float x, float y, float w, float h) { this.id = id; this.x = x; this.y = y; this.w = w; this.h = h; }
        boolean contains(float mx, float my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
}
