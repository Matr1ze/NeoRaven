package xyz.daikirai.gui.samsara;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import xyz.daikirai.client.ClientConfig;
import xyz.daikirai.gui.BindComponent;
import xyz.daikirai.gui.ColorEditor;
import xyz.daikirai.gui.onyx.Icons;
import xyz.daikirai.gui.onyx.hud.HudPass;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.Category;
import xyz.daikirai.gui.VisualPreview;
import xyz.daikirai.module.ClickGuiModule;
import xyz.daikirai.module.HudModule;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.ProfileManagerModule;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.DescriptionSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Adapted from the GPLv3 Samsara ClickGUI by the Samsara contributors.
 * The Opai column geometry, palettes and interactions are from Samsara's Opai GUI.
 * Modified for NeoRaven's Minecraft 1.8.9 renderer, modules and profiles on 2026-11-18.
 * Upstream: https://github.com/ZiLin466/Samsara
 */
public final class SamsaraClickGui extends GuiScreen implements VisualPreview.Owner {
    private static final float COL_W = 152.0F;
    private static final float COL_GAP = 8.0F;
    private static final float PREVIEW_SPACE = 196;
    private static final float HEADER_H = 21.5F;
    private static final float ROW_H = 20.0F;
    private static final float FOOTER = 7.0F;
    private static final float PAD = 10.0F;
    private static final float COLUMN_RADIUS = 7.0F;
    private static final float COLUMN_TINT = 0.76F;
    private static final float HUD_CONTROL_SIZE = 32.0F;
    private static final Typeface.Style COLUMN_TITLE = Typeface.custom(9, 10.5F, HEADER_H, true);
    private static final Typeface.Style COLUMN_TEXT = Typeface.custom(9, 9.4F, ROW_H, true);
    private static final Pattern ACRONYM_BOUNDARY = Pattern.compile("([A-Z]+)([A-Z][a-z])");
    private static final Pattern WORD_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");
    private static final String[] GROUPS = {"Combat", "Movement", "Player", "Visual", "Misc", "Profiles"};
    private static final Comparator<Module> MODULE_ORDER =
            Comparator.comparing(SamsaraClickGui::moduleLabel, String.CASE_INSENSITIVE_ORDER);

    private final ClickGuiModule launcher;
    private final UI ui = new UI();
    private final SamsaraProfilesPane profilesPane = new SamsaraProfilesPane();
    private final Pane[] columns = new Pane[5];
    private final List<Pane> columnOrder = new ArrayList<>();
    private final Pane primary = new Pane(-1);
    private final Pane secondary = new Pane(-1);
    private final List<Pane> visible = new ArrayList<>();
    private final Map<Module, Anim> expansions = new IdentityHashMap<>();
    private final Map<Module, Anim> toggles = new IdentityHashMap<>();
    private final Properties positions = new Properties();

    private GuiTextField search;
    private int searchWidth;
    private float searchX;
    private float searchY;
    private String query = "";
    private int category;
    private int layout = -1;
    private boolean attached;
    private boolean positionsRead;
    private boolean positionDirty;
    private long openedAt;
    private long lastFrame;
    private float delta;
    private float scale;
    private float animationScale;
    private float logicalW;
    private float logicalH;
    private float mouseX;
    private float mouseY;
    private float horizontal;
    private float horizontalMax;
    private float windowX;
    private float windowY;
    private float windowW;
    private float windowH;
    private boolean draggingWindow;
    private Pane draggingPane;
    private Pane scrollingPane;
    private float dragX;
    private float dragY;
    private float scrollbarGrab;
    private Module binding;
    private Module focusModule;
    private Module previewed;
    private final VisualPreview preview = new VisualPreview();
    private Entry slider;
    private Pane sliderPane;
    private SliderSetting menuSetting;
    private Pane menuPane;
    private float menuScroll;
    private Menu menu;
    private float toolbarY;
    private float toolbarX;
    private float hudX;
    private float hudY;
    private float profilesX;

    public SamsaraClickGui(ClickGuiModule launcher) {
        this.launcher = launcher;
        for (int i = 0; i < columns.length; i++) {
            columns[i] = new Pane(i);
            columnOrder.add(columns[i]);
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        if (!attached) {
            openedAt = System.nanoTime();
            attached = true;
        }
        lastFrame = 0;
        loadPositions();
        search = new GuiTextField(0, fontRendererObj, 0, 0, 160, 19);
        search.setMaxStringLength(64);
        search.setEnableBackgroundDrawing(false);
        search.setText(query);
        searchWidth = 160;
    }

    @Override
    public void updateScreen() {
        launcher.updateScreenStyle(this);
        if (mc.currentScreen == this && search != null) search.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        preview.close();
        savePositions();
        attached = false;
        profilesPane.onClose();
        binding = null;
        clearDrag();
        closeMenu();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int x, int y, float partialTicks) {
        long now = System.nanoTime();
        delta = lastFrame == 0 ? 16.0F : Math.min(50.0F, (now - lastFrame) / 1_000_000.0F);
        lastFrame = now;
        ClickGuiModule.sync();
        Scheme scheme = Theme.scheme();
        boolean columnsLayout = launcher.samsaraLayout() == 0;
        boolean glass = launcher.samsaraGlass();
        if (columnsLayout || glass) Blur.prepare();
        if (columnsLayout) drawScrim(scheme, 0.085F);
        HudPass.render(partialTicks);
        if (!columnsLayout) drawScrim(scheme, glass ? 0.16F : 0.33F);
        float elapsed = (now - openedAt) / 1_000_000.0F;
        updateLayout();
        unproject(x, y);
        Palette palette = palette(scheme);
        ui.begin();
        ui.push();
        ui.alphaPush(Math.min(1.0F, elapsed / 140.0F));
        ui.scale(animationScale, width / 2.0F, height / 2.0F);
        ui.scale(scale, 0, 0);
        try {
            if (layout != 0) drawWindow(palette, glass);
            for (Pane pane : visible) drawPane(pane, palette);
            if (columnsLayout) profilesPane.draw(ui, mouseX, mouseY, palette);
            drawToolbar(palette);
            drawPreview(scheme);
            if (menu != null) drawMenu(palette);
        } finally {
            ui.alphaPop();
            ui.pop();
            ui.end();
        }
    }

    @Override public Module previewModule() {
        Anim expansion = expansions.get(previewed);
        if (expansion == null || expansion.target() <= 0) return null;
        for (Pane pane : visible) {
            for (Entry row : pane.entries) if (row.module == previewed) return previewed;
        }
        return null;
    }

    private void drawPreview(Scheme scheme) {
        Module selected = previewModule();
        if (!VisualPreview.supports(selected)) {
            preview.close();
            return;
        }
        if (layout != 0) {
            preview.draw(ui, scheme, selected, windowX, windowY, windowW, windowH, logicalW, logicalH, delta);
        } else {
            Pane pane = columns[group(selected)];
            preview.draw(ui, scheme, selected, 0, pane.y, logicalW - PREVIEW_SPACE, pane.h + pane.header,
                    logicalW, logicalH, delta);
        }
    }

    private void updateLayout() {
        int selected = launcher.samsaraLayout();
        if (layout != selected) {
            clearDrag();
            closeMenu();
            profilesPane.onClose();
            binding = null;
            layout = selected;
            primary.scroll = secondary.scroll = 0;
            if (layout == 0 && category == 5) category = 0;
        }
        Anim expanded = expansions.get(previewed);
        boolean side = VisualPreview.supports(previewed) && expanded != null && expanded.target() > 0
                && shown(previewed) && (layout == 0 ? columns[group(previewed)].open
                    : matchesSearch(previewed, query.toLowerCase(Locale.ROOT).trim()));
        float baseWidth = side ? 795.0F : 500.0F;
        float fit;
        if (layout == 0) {
            float dockWidth = PREVIEW_SPACE + (columns.length + 1) * 116 + columns.length * COL_GAP + 60;
            fit = side ? Math.min(1.25F, width / dockWidth) : 1.25F;
        } else {
            fit = Math.min((width - 14.0F) / baseWidth, (height - 14.0F) / 343.0F);
        }
        scale = Math.max(0.2F, Math.min(launcher.samsaraScale(), fit));
        logicalW = width / scale;
        logicalH = height / scale;
        double seconds = Math.max(0, (System.nanoTime() - openedAt) / 1_000_000_000.0);
        animationScale = seconds >= 0.32 ? 1 : 1 + (float) (0.18 * Math.exp(-17 * seconds) * Math.cos(23 * seconds));
        visible.clear();
        List<Module> modules = new ArrayList<>(ModuleManager.modules);
        modules.sort(MODULE_ORDER);
        for (Module module : new ArrayList<>(expansions.keySet())) {
            if (!modules.contains(module)) expansions.remove(module);
        }
        for (Module module : new ArrayList<>(toggles.keySet())) {
            if (!modules.contains(module)) toggles.remove(module);
        }

        if (layout == 0) layoutColumns(modules, side);
        else layoutWindow(modules);
        focusEntry();
        rebuildMenu();
    }

    private void layoutColumns(List<Module> modules, boolean side) {
        int dockSlots = columns.length + 1;
        float columnsW = side ? logicalW - PREVIEW_SPACE : logicalW;
        float margin = Math.min(30.0F, columnsW * 0.032F);
        float room = columnsW - margin * 2.0F;
        float fitted = (room - (dockSlots - 1) * COL_GAP) / dockSlots;
        float colW = Math.min(room, clamp(fitted, 116, COL_W));
        float top = Math.max(16.0F, logicalH * 0.095F);
        float bottomRoom = Math.max(28, logicalH * 0.09F);
        horizontalMax = Math.max(0, margin * 2 + dockSlots * colW
                + (dockSlots - 1) * COL_GAP - columnsW);
        horizontal = clamp(horizontal, 0, horizontalMax);
        for (Pane pane : columnOrder) {
            pane.w = colW;
            pane.x = clamp(position(pane.id(), "x", margin + pane.group * (colW + COL_GAP))
                    - horizontal, 4 - horizontalMax, Math.max(4, columnsW - colW - 4 + horizontalMax));
            pane.y = clamp(position(pane.id(), "y", top), 8, Math.max(8, logicalH - HEADER_H - ROW_H - bottomRoom));
            pane.header = HEADER_H;
            pane.entries.clear();
            float content = 0;
            if (pane.open) {
                for (Module module : modules) {
                    if (shown(module) && group(module) == pane.group) {
                        content = append(pane, module, content);
                    }
                }
            }
            float cap = Math.max(ROW_H, logicalH - pane.y - HEADER_H - bottomRoom);
            pane.h = pane.open ? Math.min(cap, content + FOOTER) : 0;
            pane.finish(content);
            visible.add(pane);
        }
        profilesPane.bounds(margin + columns.length * (colW + COL_GAP) - horizontal,
                top, colW, logicalH - top - bottomRoom);
        hudX = logicalW - 42;
        hudY = logicalH - 42;
        toolbarX = hudX - 76;
        toolbarY = hudY + 7.5F;
    }

    private void layoutWindow(List<Module> modules) {
        windowW = Math.min(layout == 1 ? 500 : 530, logicalW - 8);
        windowH = Math.min(layout == 1 ? 343 : 365, logicalH - 8);
        windowX = clamp(position(windowId(), "x", (logicalW - windowW) / 2), 4, logicalW - windowW - 4);
        windowY = clamp(position(windowId(), "y", (logicalH - windowH) / 2), 4, logicalH - windowH - 4);
        toolbarX = windowX + 9;
        toolbarY = windowY + 5;
        float left = windowX + 114;
        float room = windowX + windowW - left - 9;
        float columnW = layout == 1 ? (room - 7) / 2 : room;
        initWindowPane(primary, left, columnW);
        if (layout == 1) initWindowPane(secondary, left + columnW + 7, columnW);
        float first = 0, second = 0;
        int index = 0;
        String needle = query.toLowerCase(Locale.ROOT).trim();
        for (Module module : modules) {
            if (!shown(module)) continue;
            if (!matchesSearch(module, needle)) continue;
            if (layout == 1 && index++ % 2 == 1) second = append(secondary, module, second);
            else first = append(primary, module, first);
        }
        float content = Math.max(first, second);
        primary.finish(content);
        visible.add(primary);
        if (layout == 1) {
            secondary.finish(content);
            secondary.scroll = primary.scroll;
            visible.add(secondary);
        }
        int wantedWidth = Math.max(1, Math.round(room - 12));
        if (searchWidth != wantedWidth) {
            boolean focused = search.isFocused();
            search = new GuiTextField(0, fontRendererObj, 0, 0, wantedWidth, 19);
            search.setMaxStringLength(64);
            search.setEnableBackgroundDrawing(false);
            search.setText(query);
            search.setFocused(focused);
            searchWidth = wantedWidth;
        }
        searchX = left;
        searchY = windowY + 29;
        search.xPosition = Math.round(searchX + 6);
        search.yPosition = Math.round(searchY + 5);
    }

    private boolean matchesSearch(Module module, String needle) {
        if (needle.isEmpty()) return group(module) == category;
        return moduleLabel(module).toLowerCase(Locale.ROOT).contains(needle)
                || module.getName().toLowerCase(Locale.ROOT).contains(needle);
    }

    private void initWindowPane(Pane pane, float x, float w) {
        pane.entries.clear();
        pane.x = x;
        pane.y = windowY + 54;
        pane.w = w;
        pane.h = windowH - 63;
        pane.header = 0;
    }

    private float append(Pane pane, Module module, float y) {
        float rowHeight = layout == 1 ? 28 : ROW_H;
        pane.entries.add(new Entry(module, null, y, rowHeight, y + rowHeight));
        y += rowHeight;
        Anim anim = expansions.get(module);
        if (anim != null) {
            anim.update(delta);
            float clip = y;
            List<Setting> settings = module.getSettings();
            for (Setting setting : settings) clip += height(setting);
            clip = y + (clip - y + (module.canBeBound() ? ROW_H : 0)) * anim.value();
            if (clip > y) {
                for (Setting setting : settings) {
                    float h = height(setting);
                    if (y >= clip) break;
                    pane.entries.add(new Entry(module, setting, y, h, Math.min(y + h, clip)));
                    y += h;
                }
                if (y < clip && module.canBeBound()) {
                    pane.entries.add(new Entry(module, BIND, y, ROW_H, Math.min(y + ROW_H, clip)));
                }
                y = clip;
            } else if (anim.isDone() && anim.target() == 0) {
                expansions.remove(module);
            }
        }
        return y + (layout == 0 ? 0 : 5);
    }

    private static final Setting BIND = new Setting("Bind");

    private static float height(Setting s) {
        if (s instanceof SliderSetting) return ((SliderSetting) s).isMode() ? 39.0F : 23.0F;
        if (s instanceof DescriptionSetting) return 22.0F;
        return ROW_H;
    }

    private static boolean shown(Module module) {
        return !module.isHidden() || module == ModuleManager.gui;
    }

    private static int group(Module module) {
        switch (module.moduleCategory()) {
            case combat: return 0;
            case movement: return 1;
            case player: return 2;
            case render: return 3;
            case profiles: return 5;
            default: return 4;
        }
    }

    private void drawScrim(Scheme scheme, float opacity) {
        if (!Theme.translucent()) drawRect(0, 0, width, height, Material.withAlpha(scheme.scrim, opacity));
    }

    private Palette palette(Scheme scheme) {
        boolean translucent = Theme.translucent();
        boolean dark = Theme.dark() && !translucent;
        int text = translucent ? scheme.inverseOnSurface : scheme.onSurface;
        int muted = translucent ? Material.mix(text, 0xFFFFFFFF, 0.3F) : scheme.onSurfaceVariant;
        int accent = translucent ? scheme.inversePrimary : scheme.primary;
        int onAccent = translucent ? 0xFFFFFFFF : scheme.onPrimary;
        int enabled = dark ? scheme.primary : scheme.primaryContainer;
        int onEnabled = dark ? scheme.onPrimary : translucent ? text : scheme.onPrimaryContainer;
        return new Palette(scheme.surfaceContainerHigh | 0xFF000000,
                Material.withAlpha(scheme.surfaceContainer, layout == 0 ? 0.85F : 0.97F),
                enabled, accent, onEnabled, onAccent, text, muted,
                scheme.surfaceContainerHighest | 0xFF000000,
                translucent ? Material.withAlpha(text, 0.26F) : scheme.outlineVariant,
                translucent ? Material.withAlpha(text, 0.55F) : scheme.outline);
    }

    private void drawWindow(Palette p, boolean glass) {
        if (!Theme.translucent()) ui.shadow(windowX, windowY, windowW, windowH, 12, 3);
        if (glass && Blur.ready()) ui.frost(windowX, windowY, windowW, windowH, 12, p.body | 0xFF000000, 0.62F);
        else ui.rounded(windowX, windowY, windowW, windowH, 12, p.body);
        ui.rounded(windowX + 7, windowY + 28, 101, windowH - 35, 8, p.header);
        for (int i = 0; i < GROUPS.length; i++) {
            float y = navY(i);
            if (i == category && query.isEmpty()) ui.rounded(windowX + 12, y, 91, 23, 6, p.enabled);
            String label = groupLabel(i);
            ui.text(Typeface.LABEL, label, windowX + 20, y + 11.5F,
                    i == category && query.isEmpty() ? p.onEnabled : p.text, UI.ALIGN_LEFT);
        }
        ui.rounded(searchX, searchY, searchWidth + 12, 19, 6, p.field);
        if (!search.isFocused() && query.isEmpty()) {
            ui.text(Typeface.LABEL, label("Search..."), searchX + 6,
                    searchY + 9.5F, p.text, UI.ALIGN_LEFT);
        } else {
            search.setTextColor(p.text & 0xFFFFFF);
            search.drawTextBox();
        }
        if (primary.entries.isEmpty() && (layout != 1 || secondary.entries.isEmpty())) {
            ui.text(Typeface.LABEL, label("No modules match"), primary.x + 6, primary.y + 11, p.text, UI.ALIGN_LEFT);
        }
    }

    private float navY(int group) {
        return windowY + 37 + 27 * group;
    }

    private void drawToolbar(Palette p) {
        if (layout == 0) {
            toolbarButton(label(ClickGuiModule.NAME), toolbarX, 68, p);
            if (mc.theWorld != null) {
                boolean hover = inside(mouseX, mouseY, hudX, hudY, HUD_CONTROL_SIZE, HUD_CONTROL_SIZE);
                ui.rounded(hudX, hudY, HUD_CONTROL_SIZE, HUD_CONTROL_SIZE, 10, hover ? p.accent : p.header);
                ui.icon("\ue3c9", hudX + HUD_CONTROL_SIZE / 2, hudY + HUD_CONTROL_SIZE / 2, 16, hover ? p.onAccent : p.accent);
            }
            if (horizontalMax > 1) {
                ui.rounded(10, hudY, 56, HUD_CONTROL_SIZE, 10, p.header);
                int leftInk = horizontal > 0 ? p.accent : p.scrollbar;
                int rightInk = horizontal < horizontalMax ? p.accent : p.scrollbar;
                ui.icon("\ue5cb", 24, hudY + HUD_CONTROL_SIZE / 2, 14, leftInk);
                ui.icon("\ue5cc", 52, hudY + HUD_CONTROL_SIZE / 2, 14, rightInk);
            }
            return;
        }
        hudX = toolbarX + 72;
        profilesX = hudX + 38;
        toolbarButton(label(ClickGuiModule.NAME), toolbarX, 68, p);
        toolbarButton("HUD", hudX, 34, p);
        toolbarButton(label("Profiles"), profilesX, 57, p);
    }

    private void toolbarButton(String text, float x, float w, Palette p) {
        ui.rounded(x, toolbarY, w, 17, 5, inside(mouseX, mouseY, x, toolbarY, w, 17) ? p.field : p.header);
        ui.text(Typeface.CAPTION, ui.truncate(Typeface.CAPTION, text, w - 8),
                x + w / 2, toolbarY + 8.5F, p.text, UI.ALIGN_CENTER);
    }

    private void drawPane(Pane pane, Palette p) {
        float bodyY = pane.body();
        if (layout == 0) {
            float surfaceHeight = pane.header + pane.h;
            if (Blur.ready()) {
                ui.frost(pane.x, pane.y, pane.w, surfaceHeight, COLUMN_RADIUS, p.body | 0xFF000000, COLUMN_TINT);
            } else {
                ui.rounded(pane.x, pane.y, pane.w, surfaceHeight, COLUMN_RADIUS, p.body);
            }
            float bottomRadius = pane.h > 0 ? 0 : COLUMN_RADIUS;
            ui.roundedCorners(pane.x, pane.y, pane.w, pane.header, COLUMN_RADIUS, COLUMN_RADIUS,
                    bottomRadius, bottomRadius, Material.withAlpha(p.header, 0.58F));
            ui.text(COLUMN_TITLE, groupLabel(pane.group), pane.x + PAD, pane.y + HEADER_H / 2,
                    p.text, UI.ALIGN_LEFT);
            if (pane.h <= 0) return;
            ui.rect(pane.x + PAD, bodyY - 1, pane.w - PAD * 2, 1, Material.withAlpha(p.accent, 0.23F));
        }
        ui.scissorPush(pane.x + 1, bodyY, pane.w - 2, pane.viewHeight());
        for (Entry e : pane.entries) {
            float y = bodyY + e.top - pane.scroll;
            float bottom = bodyY + e.clipBottom - pane.scroll;
            if (bottom <= bodyY || y >= bodyY + pane.viewHeight()) continue;
            if (e.clipBottom < e.top + e.height) ui.scissorPush(pane.x, y, pane.w, bottom - y);
            drawEntry(pane, e, y, p);
            if (e.clipBottom < e.top + e.height) ui.scissorPop();
        }
        ui.scissorPop();
        if (pane.maxScroll > 0 && (layout != 1 || pane == secondary)) {
            ui.rounded(pane.x + pane.w - 3.5F, bodyY + pane.thumbTop(),
                    2.5F, pane.thumbHeight(), 1.25F, p.scrollbar);
        }
    }

    private void drawEntry(Pane pane, Entry e, float y, Palette p) {
        float x = pane.x;
        float center = y + e.height / 2;
        float room = pane.w - PAD * 2;
        boolean hover = menu == null && topPane() == pane
                && inside(mouseX, mouseY, x, Math.max(y, pane.body()), pane.w,
                    Math.min(pane.body() + pane.viewHeight(), pane.body() + e.clipBottom - pane.scroll)
                            - Math.max(y, pane.body()));
        if (e.setting == null) {
            Anim toggle = toggles.computeIfAbsent(e.module, ignored -> new Anim(e.module.isEnabled() ? 1 : 0));
            toggle.to(e.module.isEnabled() ? 1 : 0, 125, Ease.DECELERATE);
            toggle.update(delta);
            float active = toggle.value();
            int fill = active > 0 ? Material.withAlpha(p.enabled, active)
                    : Material.withAlpha(p.text, hover ? 0.12F : 0.06F);
            if (layout == 0) {
                if (active > 0 || hover) {
                    ui.roundedCorners(x, y, pane.w, e.height, 0, 0, 0, 0, fill, true, true);
                }
            } else {
                ui.rounded(x + 1, y, pane.w - 2, e.height, 5, fill);
            }
            Anim expansion = expansions.get(e.module);
            if (layout != 0 && (!e.module.getSettings().isEmpty() || e.module.canBeBound())) {
                chevron(x + pane.w - 11, center, expansion != null && expansion.target() > 0, p.text);
                room -= 9;
            }
            String name = binding == e.module ? label("Press a key...") : moduleLabel(e.module);
            if (layout == 0) name = WORD_BOUNDARY.matcher(ACRONYM_BOUNDARY.matcher(name).replaceAll("$1 $2"))
                    .replaceAll("$1 $2");
            text(name, x + PAD, center, room, Material.mix(p.text, p.onEnabled, active));
            return;
        }
        if (e.setting == BIND) {
            text(label("Bind"), x + PAD, center, room * 0.45F, p.text);
            String key = BindComponent.keyName(e.module.getKeycode());
            value(binding == e.module ? label("Press a key...") : key == null ? label("None") : key,
                    x + pane.w - PAD, center, room * 0.55F, p.accent);
            return;
        }
        if (e.setting instanceof DescriptionSetting) {
            text(settingLabel(e.setting.getName()), x + PAD, center, room, p.text);
            return;
        }
        if (e.setting instanceof SliderSetting) {
            SliderSetting setting = (SliderSetting) e.setting;
            if (setting.isMode()) {
                text(settingLabel(setting.getName()), x + PAD, y + 8, room, p.text);
                ui.rounded(x + PAD - 2, y + 16, room + 4, 20, 3, p.field);
                ui.rect(x + PAD - 1, y + 35, room + 2, 1, p.accent);
                text(settingLabel(setting.getDisplay(false)), x + PAD + 4, y + 26, room - 15, p.text);
                chevron(x + pane.w - PAD - 3, y + 26, menuSetting == setting, p.text);
            } else {
                text(settingLabel(setting.getName()), x + PAD, y + 7, room * 0.66F, p.text);
                value(setting.getDisplay(false), x + pane.w - PAD, y + 7, room * 0.34F, p.text);
                double range = setting.getMax() - setting.getMin();
                float fraction = range <= 0 ? 0 : clamp((float) ((setting.getInput() - setting.getMin()) / range), 0, 1);
                ui.rounded(x + PAD, y + 15, room, 2.5F, 1.25F, p.track);
                if (fraction > 0) ui.rounded(x + PAD, y + 15, room * fraction, 2.5F, 1.25F, p.accent);
                ui.circle(x + PAD + room * fraction, y + 16.25F, 4, p.accent);
            }
            return;
        }
        if (e.setting instanceof ButtonSetting) {
            ButtonSetting button = (ButtonSetting) e.setting;
            text(settingLabel(button.getName()), x + PAD, center, room - 31, p.text);
            float right = x + pane.w - PAD;
            if (button instanceof ColorSetting) {
                ui.rounded(right - 21, center - 5, 21, 10, 3, ((ColorSetting) button).color());
            } else if (button.isMethodButton()) {
                chevron(right - 3, center, true, p.accent);
            } else {
                boolean on = button.isToggled();
                ui.rounded(right - 26, center - 7, 26, 14, 7, on ? p.accent : p.track);
                ui.circle(right - (on ? 7 : 19), center, 4, on ? p.onAccent : p.text);
            }
        }
    }

    private void chevron(float x, float y, boolean expanded, int color) {
        ui.text(Typeface.CAPTION, expanded ? "−" : "+", x, y, color, UI.ALIGN_CENTER);
    }

    private void text(String text, float x, float y, float w, int color) {
        Typeface.Style style = layout == 0 ? COLUMN_TEXT : Typeface.LABEL;
        ui.text(style, ui.truncate(style, text, w), x, y, color, UI.ALIGN_LEFT);
    }

    private void value(String text, float x, float y, float w, int color) {
        ui.text(Typeface.CAPTION, ui.truncate(Typeface.CAPTION, text, w), x, y, color, UI.ALIGN_RIGHT);
    }

    private void rebuildMenu() {
        if (menuSetting == null || !visible.contains(menuPane)) {
            closeMenu();
            return;
        }
        for (Entry item : menuPane.entries) {
            if (item.setting != menuSetting) continue;
            float y = menuPane.body() + item.top - menuPane.scroll + 16;
            if (y < menuPane.body() || y + 20 > menuPane.body() + menuPane.viewHeight()) break;
            float total = menuSetting.options.length * ROW_H;
            float below = logicalH - y - 24;
            float above = y - 4;
            boolean upward = below < Math.min(total, 105) && above > below;
            float h = Math.min(total, Math.min(150, upward ? above : below));
            if (h < ROW_H) break;
            menu = new Menu(menuPane.x + PAD - 2, upward ? y - h : y + 20,
                    menuPane.w - PAD * 2 + 4, h);
            menuScroll = clamp(menuScroll, 0, total - h);
            return;
        }
        closeMenu();
    }

    private void drawMenu(Palette p) {
        ui.shadow(menu.x, menu.y, menu.w, menu.h, 3, 2);
        ui.rounded(menu.x, menu.y, menu.w, menu.h, 3, p.field | 0xFF000000);
        ui.scissorPush(menu.x, menu.y, menu.w, menu.h);
        for (int i = 0; i < menuSetting.options.length; i++) {
            float y = menu.y + i * ROW_H - menuScroll;
            if (y + ROW_H <= menu.y || y >= menu.y + menu.h) continue;
            boolean selected = i == (int) menuSetting.getInput();
            if (selected || inside(mouseX, mouseY, menu.x, y, menu.w, ROW_H)) {
                ui.rect(menu.x + 1, y, menu.w - 2, ROW_H, selected ? p.enabled : Material.withAlpha(p.text, 0.14F));
            }
            text(settingLabel(menuSetting.options[i]), menu.x + 6, y + ROW_H / 2, menu.w - 12,
                    selected ? p.onEnabled : p.text);
        }
        ui.scissorPop();
    }


    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {
        unproject(x, y);
        if (binding != null) {
            if (button > 2) finishBind(button - 100);
            else binding = null;
            return;
        }
        if (menu != null && menu.contains(mouseX, mouseY)) {
            int index = (int) ((mouseY - menu.y + menuScroll) / ROW_H);
            if (button == 0 && index < menuSetting.options.length) {
                menuSetting.setValue(index);
                closeMenu();
            }
            return;
        }
        closeMenu();
        if (button == 0 && clickToolbar()) return;
        if (layout == 0 && profilesPane.click(mouseX, mouseY, button)) return;
        if (layout != 0 && clickNavigation(button)) return;

        Pane pane = topPane();
        if (pane == null) return;
        if (layout == 0 && mouseY < pane.body()) {
            if (button == 1) {
                pane.open = !pane.open;
                binding = null;
            } else if (button == 0) {
                columnOrder.remove(pane);
                columnOrder.add(pane);
                draggingPane = pane;
                dragX = mouseX - pane.x;
                dragY = mouseY - pane.y;
            }
            return;
        }
        if (button == 0 && pane.maxScroll > 0 && mouseX >= pane.x + pane.w - 5
                && (layout != 1 || pane == secondary)) {
            scrollingPane = pane;
            float local = mouseY - pane.body() - pane.thumbTop();
            scrollbarGrab = local >= 0 && local < pane.thumbHeight() ? local : pane.thumbHeight() / 2;
            moveScrollbar(pane);
            return;
        }
        double localY = mouseY - pane.body();
        if (localY >= pane.viewHeight()) return;
        float rowY = (float) localY + pane.scroll;
        for (Entry entry : pane.entries) {
            if (rowY >= entry.top && rowY < entry.clipBottom) {
                clickEntry(pane, entry, button, rowY);
                return;
            }
        }
    }

    private boolean clickToolbar() {
        if (layout == 0 && horizontalMax > 1 && inside(mouseX, mouseY, 10, hudY, 56, HUD_CONTROL_SIZE)) {
            float direction = mouseX < 38 ? -1 : 1;
            horizontal = clamp(horizontal + direction * logicalW * 0.65F, 0, horizontalMax);
            return true;
        }
        if (inside(mouseX, mouseY, toolbarX, toolbarY, 68, 17)) {
            openGroup(4, launcher);
            return true;
        }
        boolean hudHit = layout == 0
                ? inside(mouseX, mouseY, hudX, hudY, HUD_CONTROL_SIZE, HUD_CONTROL_SIZE)
                : inside(mouseX, mouseY, hudX, toolbarY, 34, 17);
        if (hudHit) {
            openHudEditor();
            return true;
        }
        if (layout == 0) return false;
        if (inside(mouseX, mouseY, profilesX, toolbarY, 57, 17)) {
            Module manager = null;
            for (Module module : ModuleManager.modules) {
                if (module instanceof ProfileManagerModule) {
                    manager = module;
                    break;
                }
            }
            openGroup(5, manager);
            return true;
        }
        return false;
    }

    private void openHudEditor() {
        if (mc.theWorld != null && HudModule.editPosition != null) {
            mc.displayGuiScreen(new xyz.daikirai.gui.HudPositionEditor() {
                @Override
                protected void keyTyped(char c, int key) {
                    if (key == Keyboard.KEY_ESCAPE || key == launcher.getKeycode()) {
                        mc.displayGuiScreen(SamsaraClickGui.this);
                    }
                }
            });
        }
    }

    private void openGroup(int group, Module focus) {
        closeMenu();
        if (layout == 0) {
            Pane pane = columns[4];
            pane.open = true;
            horizontal = horizontalMax;
            columnOrder.remove(pane);
            columnOrder.add(pane);
        } else {
            category = group;
            search.setText("");
            search.setFocused(false);
            query = "";
            primary.scroll = secondary.scroll = 0;
        }
        focusModule = focus;
        previewed = focus;
        if (focus != null) expansions.computeIfAbsent(focus, ignored -> new Anim(0))
                .to(1, 180, Ease.DECELERATE);
    }

    private boolean clickNavigation(int button) {
        search.mouseClicked((int) mouseX, (int) mouseY, button);
        if (search.isFocused()) return true;
        if (button != 0) return false;
        for (int i = 0; i < GROUPS.length; i++) {
            if (inside(mouseX, mouseY, windowX + 12, navY(i), 91, 23)) {
                openGroup(i, null);
                return true;
            }
        }
        if (inside(mouseX, mouseY, profilesX + 59, toolbarY, windowX + windowW - profilesX - 59, 19)) {
            draggingWindow = true;
            dragX = mouseX - windowX;
            dragY = mouseY - windowY;
            return true;
        }
        return false;
    }

    private void clickEntry(Pane pane, Entry e, int button, float rowY) {
        Module module = e.module;
        if (e.setting == null) {
            if (button == 0) {
                if (module == launcher) openGroup(4, launcher);
                else module.toggle();
            } else if (button == 1) {
                Anim anim = expansions.computeIfAbsent(module, ignored -> new Anim(0));
                anim.to(anim.target() > 0 ? 0 : 1, 180, Ease.DECELERATE);
                previewed = anim.target() > 0 ? module : null;
            } else if (button == 2 && module.canBeBound()) {
                binding = module;
            }
            return;
        }
        if (e.setting == BIND) {
            if (button == 0) binding = module;
            else if (button == 1) module.setHoldBind(!module.isHoldBind());
            return;
        }
        if (button != 0) return;
        previewed = module;
        if (e.setting instanceof ColorSetting) {
            mc.displayGuiScreen(new ColorEditor(this, (ColorSetting) e.setting));
        } else if (e.setting instanceof ButtonSetting) {
            ButtonSetting toggle = (ButtonSetting) e.setting;
            toggle.toggle();
            module.guiButtonToggled(toggle);
            module.guiUpdate();
        } else if (e.setting instanceof SliderSetting) {
            SliderSetting setting = (SliderSetting) e.setting;
            if (setting.isMode()) {
                if (rowY - e.top >= 15) {
                    menuSetting = setting;
                    menuPane = pane;
                    rebuildMenu();
                }
            } else {
                slider = e;
                sliderPane = pane;
                moveSlider();
            }
        }
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (button != 0) return;
        unproject(x, y);
        if (draggingPane != null) {
            putPosition(draggingPane.id(), clamp(mouseX - dragX, 4, logicalW - draggingPane.w - 4)
                    + horizontal, clamp(mouseY - dragY, 8, logicalH - HEADER_H - ROW_H - Math.max(28, logicalH * 0.09F)));
        } else if (draggingWindow) {
            putPosition(windowId(), clamp(mouseX - dragX, 4, logicalW - windowW - 4),
                    clamp(mouseY - dragY, 4, logicalH - windowH - 4));
        } else if (scrollingPane != null) {
            moveScrollbar(scrollingPane);
        } else if (slider != null) {
            if (slider.module.getSettings().contains(slider.setting)) moveSlider();
            else clearDrag();
        }
    }

    @Override
    protected void mouseReleased(int x, int y, int button) {
        if (button == 0) {
            clearDrag();
            savePositions();
        }
    }

    private void moveSlider() {
        SliderSetting setting = (SliderSetting) slider.setting;
        float fraction = clamp((mouseX - sliderPane.x - PAD) / Math.max(1, sliderPane.w - 2 * PAD), 0, 1);
        setting.setValue(setting.getMin() + (setting.getMax() - setting.getMin()) * fraction);
        slider.module.guiUpdate();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        unproject(x, y);
        float move = -Integer.signum(wheel) * ROW_H * 2;
        if (menu == null && layout == 0 && !isShiftKeyDown() && profilesPane.wheel(mouseX, mouseY, wheel)) return;
        if (menu != null && menu.contains(mouseX, mouseY)) {
            menuScroll = clamp(menuScroll + move, 0, menuSetting.options.length * ROW_H - menu.h);
            return;
        }
        closeMenu();
        Pane pane = topPane();
        if (layout == 0 && (pane == null || isShiftKeyDown())) {
            horizontal = clamp(horizontal + move * 1.8F, 0, horizontalMax);
        } else if (pane != null) {
            scroll(pane, pane.scroll + move);
        }
    }

    private void moveScrollbar(Pane pane) {
        float travel = pane.viewHeight() - 4 - pane.thumbHeight();
        if (travel > 0) scroll(pane, pane.maxScroll * (mouseY - pane.body() - scrollbarGrab - 2) / travel);
    }

    private void scroll(Pane pane, float target) {
        pane.scroll = clamp(target, 0, pane.maxScroll);
        if (layout == 1) primary.scroll = secondary.scroll = pane.scroll;
    }

    @Override
    protected void keyTyped(char ch, int key) throws IOException {
        if (layout == 0 && profilesPane.keyTyped(ch, key)) return;
        if (binding != null) {
            finishBind(key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_BACK ? 0 : key);
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            if (menu != null) closeMenu();
            else if (search.isFocused()) search.setFocused(false);
            else mc.displayGuiScreen(null);
            return;
        }
        if (layout != 0 && search.isFocused() && search.textboxKeyTyped(ch, key)) {
            query = search.getText();
            primary.scroll = secondary.scroll = 0;
            closeMenu();
            return;
        }
        if (key != 0 && key == launcher.getKeycode()) mc.displayGuiScreen(null);
    }

    private void finishBind(int key) {
        if (binding != launcher || key != 0) binding.setBind(key);
        binding = null;
    }

    private void clearDrag() {
        draggingPane = scrollingPane = sliderPane = null;
        draggingWindow = false;
        slider = null;
    }

    private void closeMenu() {
        menuSetting = null;
        menuPane = null;
        menu = null;
        menuScroll = 0;
    }

    private Pane topPane() {
        for (int i = visible.size() - 1; i >= 0; i--) {
            Pane pane = visible.get(i);
            if (inside(mouseX, mouseY, pane.x, pane.y, pane.w, pane.h + pane.header)) return pane;
        }
        return null;
    }

    private void unproject(int x, int y) {
        float transform = Math.max(0.1F, animationScale);
        float zoom = Math.max(0.1F, scale);
        mouseX = (width / 2.0F + (x - width / 2.0F) / transform) / zoom;
        mouseY = (height / 2.0F + (y - height / 2.0F) / transform) / zoom;
    }

    private void focusEntry() {
        if (focusModule == null) return;
        for (Pane pane : visible) {
            for (Entry entry : pane.entries) {
                if (entry.module == focusModule) {
                    scroll(pane, entry.top);
                    focusModule = null;
                    return;
                }
            }
        }
        focusModule = null;
    }

    private String windowId() { return layout == 1 ? "modern" : "sidebar"; }

    private float position(String id, String axis, float fallback) {
        String saved = positions.getProperty(id + "." + axis);
        if (saved == null) return fallback;
        try {
            float parsed = Float.parseFloat(saved);
            return Float.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void putPosition(String id, float x, float y) {
        positions.setProperty(id + ".x", Float.toString(x));
        positions.setProperty(id + ".y", Float.toString(y));
        positionDirty = true;
    }

    private Path positionFile() {
        return Paths.get(ClientConfig.DIR, "samsara_clickgui.properties");
    }

    private void loadPositions() {
        if (positionsRead) return;
        positionsRead = true;
        if (!Files.isRegularFile(positionFile())) return;
        try (InputStream input = Files.newInputStream(positionFile())) {
            positions.load(input);
        } catch (IOException | IllegalArgumentException error) {
            System.err.println("[Samsara GUI] Could not read layout: " + error.getMessage());
        }
    }

    private void savePositions() {
        if (!positionDirty) return;
        try {
            Files.createDirectories(positionFile().getParent());
            try (OutputStream output = Files.newOutputStream(positionFile())) {
                positions.store(output, "NeoRaven Samsara Click GUI");
            }
            positionDirty = false;
        } catch (IOException error) {
            System.err.println("[Samsara GUI] Could not save layout: " + error.getMessage());
        }
    }

    public void resetLayouts() {
        loadPositions();
        positions.clear();
        horizontal = 0;
        for (Pane pane : columns) {
            pane.open = true;
            pane.scroll = 0;
        }
        primary.scroll = secondary.scroll = 0;
        positionDirty = true;
        savePositions();
    }

    private String groupLabel(int group) {
        if (group == 3 || group == 4) return label(GROUPS[group]);
        return Icons.label(Category.valueOf(GROUPS[group].toLowerCase(Locale.ROOT)));
    }

    private static String moduleLabel(Module module) { return module.getName(); }
    private static String settingLabel(String value) { return value; }
    private static String label(String value) { return value; }

    private static boolean inside(float x, float y, float left, float top, float w, float h) {
        return x >= left && y >= top && x < left + w && y < top + h;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Pane {
        final int group;
        final List<Entry> entries = new ArrayList<>();
        boolean open = true;
        float x, y, w, h, header, scroll, maxScroll;

        Pane(int group) { this.group = group; }
        String id() { return "opai." + group; }
        float body() { return y + header; }
        float viewHeight() { return Math.max(0, h - (group >= 0 ? FOOTER : 0)); }
        void finish(float content) {
            maxScroll = Math.max(0, content - viewHeight());
            scroll = clamp(scroll, 0, maxScroll);
        }
        float thumbHeight() {
            float track = Math.max(0, viewHeight() - 4);
            return Math.min(track, Math.max(12, track * viewHeight() / (viewHeight() + maxScroll)));
        }
        float thumbTop() {
            return 2 + (maxScroll == 0 ? 0 : (viewHeight() - 4 - thumbHeight()) * scroll / maxScroll);
        }
    }

    private static final class Entry {
        final Module module;
        final Setting setting;
        final float top, height, clipBottom;

        Entry(Module module, Setting setting, float top, float height, float clipBottom) {
            this.module = module;
            this.setting = setting;
            this.top = top;
            this.height = height;
            this.clipBottom = clipBottom;
        }
    }

    private static final class Menu {
        final float x, y, w, h;

        Menu(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(float mouseX, float mouseY) { return inside(mouseX, mouseY, x, y, w, h); }
    }

    static final class Palette {
        final int header, body, enabled, accent, onEnabled, onAccent, text, muted, field, track, scrollbar;

        Palette(int header, int body, int enabled, int accent, int onEnabled, int onAccent, int text, int muted,
                int field, int track, int scrollbar) {
            this.header = header;
            this.body = body;
            this.enabled = enabled;
            this.accent = accent;
            this.onEnabled = onEnabled;
            this.onAccent = onAccent;
            this.text = text;
            this.muted = muted;
            this.field = field;
            this.track = track;
            this.scrollbar = scrollbar;
        }
    }
}

