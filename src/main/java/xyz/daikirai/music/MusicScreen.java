package xyz.daikirai.music;

import net.minecraft.client.gui.GuiScreen;
import xyz.daikirai.module.render.WatermarkModule;
import xyz.daikirai.gui.onyx.hud.Island;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.hud.HudPass;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Spring;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.gui.onyx.render.WavyProgressIndicator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The music player window: a Material 3 shell with a nav rail, a content area, a mini player bar
 * and the lyric panel.
 *
 * <p>Ported from Melodify's {@code MusicPlayerGui}, keeping its proportions (840x520 design
 * canvas, 28px corner radius, 42px nav rail, 84px player bar, cover-tinted palette) but drawn
 * with the Onyx material primitives that already exist in this client instead of a bespoke
 * renderer. Melodify derives its whole palette from the album art; that behaviour is kept through
 * {@link Scheme#of(int, float)} seeded with {@link CoverCache#dominant(String)}.</p>
 */
public final class MusicScreen extends GuiScreen {

    private static final float W = 840.0F;
    private static final float H = 520.0F;
    private static final float RADIUS = 28.0F;
    private static final float RAIL_X = 8.0F;
    private static final float RAIL_Y = 8.0F;
    private static final float RAIL_W = 42.0F;
    private static final float RAIL_H = H - 16.0F;
    private static final float CT_X = 70.0F;
    private static final float CT_Y = 56.0F;
    private static final float CT_W = W - 90.0F;
    private static final float CT_H = H - 140.0F;
    private static final float MINI_X = 58.0F;
    private static final float MINI_Y = H - 84.0F;
    private static final float MINI_W = W - 66.0F;
    private static final float MINI_H = 84.0F;
    private static final float ROW_PITCH = 64.0F;

    private static final String I_HOME = "\ue88a";
    private static final String I_SEARCH = "\ue8b6";
    private static final String I_PERSON = "\ue7fd";
    private static final String I_SETTINGS = "\ue8b8";
    private static final String I_PLAY = "\ue037";
    private static final String I_PAUSE = "\ue034";
    private static final String I_NEXT = "\ue044";
    private static final String I_PREV = "\ue045";
    private static final String I_REPEAT = "\ue040";
    private static final String I_REPEAT_ONE = "\ue041";
    private static final String I_SHUFFLE = "\ue043";
    private static final String I_VOLUME = "\ue050";
    private static final String I_VOLUME_OFF = "\ue04f";
    private static final String I_CLOSE = "\ue5cd";
    private static final String I_BACK = "\ue5c4";
    private static final String I_REFRESH = "\ue5d5";
    private static final String I_LYRICS = "\ue8d2";
    private static final String I_MUSIC = "\ue405";
    private static final String I_CHECK = "\ue5ca";

    private static final Typeface.Style DISPLAY = MusicTypeface.DISPLAY;
    private static final Typeface.Style SEARCH = MusicTypeface.SEARCH;
    private static final Typeface.Style ROW = MusicTypeface.ROW;
    private static final Typeface.Style ROW_SUB = MusicTypeface.ROW_SUB;
    private static final Typeface.Style CARD_TITLE = MusicTypeface.CARD_TITLE;

    private enum Page {
        HOME, SEARCH, MINE, SETTINGS
    }

    private static class Hit {
        final float x;
        final float y;
        final float w;
        final float h;
        final Runnable action;

        Hit(float x, float y, float w, float h, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        boolean contains(float mx, float my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final MusicManager manager = MusicManager.get();
    private final UI ui = new UI();
    private final LyricPanel lyricPanel = new LyricPanel(this);
    private final List<Hit> hits = new ArrayList<Hit>();
    private final Anim open = new Anim(0.0F);
    private final Anim scrim = new Anim(0.0F);
    private final Spring scroll = new Spring(0.0F);
    private final Spring hover = new Spring(0.0F);

    private Page page = Page.HOME;
    private Scheme scheme;
    private int coverSeed;
    private float scale = 1.0F;
    private float gx;
    private float gy;
    private float mouseX;
    private float mouseY;
    private float delta = 16.0F;
    private long lastFrame;
    private boolean initialized;
    private boolean closing;
    private boolean closeCommitted;
    private boolean restored;
    private boolean returnMini;
    private boolean endpointMini;
    private float[] islandBounds;
    private float[] closingBounds;
    private float drawX, drawY, drawScale;
    private float shellX, shellY, shellW, shellH;
    private float contentHeight;
    private float contentTop;
    private boolean contentScrollable;
    private float lastMouseX;
    private float lastMouseY;

    private String searchText = "";
    private boolean searchFocused;
    private long searchTypedAt;
    private long caretAt;
    private int searchCaret;

    private boolean draggingProgress;
    private float progressPreview = -1.0F;
    private boolean draggingVolume;
    private boolean volumeOpen;
    private SliderHit draggingSlider;
    private float searchRectX;
    private float searchRectY;
    private float searchRectW;
    private float searchRectH;

    private boolean playlistOpen;
    private boolean lyricOpen;
    private boolean qrOpen;
    private boolean settingsSavesVolume;
    private boolean coverDark;

    public MusicScreen() {
        MusicManager.get().boot();
    }

    public UI ui() {
        return ui;
    }

    public Scheme scheme() {
        return scheme == null ? Theme.scheme() : scheme;
    }

    public float delta() {
        return delta;
    }

    public boolean lyricTranslation() {
        return true;
    }

    /** Whether the pointer is inside the given local rectangle (used by the lyric panel). */
    boolean mouseIn(float x, float y, float w, float h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    void setLyricOpen(boolean value) {
        lyricOpen = value;
        if (value) {
            lyricPanel.reset();
        }
    }

    // ------------------------------------------------------------------ //
    // Lifecycle
    // ------------------------------------------------------------------ //

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.scale = fitScale();
        this.lastFrame = System.nanoTime();
        if (!initialized) {
            Island island = WatermarkModule.island();
            islandBounds = island.width() > 0.0F
                    ? new float[] {island.left(), island.top(), island.width(), island.height()}
                    : island.measure();
            endpointMini = islandBounds[3] / islandBounds[2] > 0.3F && MusicIsland.hasTrack();
            open.set(1.0F);
            scrim.set(1.0F);
            initialized = true;
        } else if (closing) {
            islandBounds = WatermarkModule.island().measure();
        }
        Blur.request();
        layout();
    }

    /**
     * The window is fitted to 92% of the screen and limited by the shared Material scale, then
     * multiplied by the module's own "GUI Scale". The multiplier shrinks below the default fit and
     * grows back up to it (never past it: the window must stay on screen).
     */
    private float fitScale() {
        float fit = Math.min(this.width * 0.92F / W, this.height * 0.92F / H);
        float base = Math.min(Theme.scale(), fit);
        return Math.max(0.1F, Math.min(base * MusicModule.guiScale(), fit));
    }

    private void layout() {
        float virtualWidth = this.width / scale;
        float virtualHeight = this.height / scale;
        this.gx = Math.round((virtualWidth - W) / 2.0F);
        this.gy = Math.round((virtualHeight - H) / 2.0F);
    }

    private void tick() {
        long now = System.nanoTime();
        this.delta = Math.min((now - lastFrame) / 1000000.0F, 100.0F);
        this.lastFrame = now;
        open.update(delta);
        scrim.update(delta);
        hover.update(delta);
        MusicIsland.tick(delta);
        lyricPanel.tick(delta);
        tickSearch();
        tickScheme();
        tickScale();
        scroll.update(delta);
        if (contentScrollable) {
            float max = Math.max(0.0F, contentHeight - CT_H);
            if (scroll.target() > max) {
                scroll.target(max);
            }
            if (scroll.value() > max) {
                scroll.set(max);
            }
        } else if (scroll.value() != 0.0F) {
            scroll.set(0.0F);
        }
    }

    /** Re-fits the window when "GUI Scale" (or the shared Material scale) changes while open. */
    private void tickScale() {
        float wanted = fitScale();
        if (Math.abs(wanted - scale) > 0.001F) {
            scale = wanted;
            layout();
        }
    }


    private void insertSearch(String value) {
        String merged = searchText.substring(0, searchCaret) + value + searchText.substring(searchCaret);
        if (merged.length() > 48) {
            merged = merged.substring(0, 48);
        }
        searchText = merged;
        searchCaret = Math.min(merged.length(), searchCaret + value.length());
    }

    private int searchStart(float maxText) {
        float limit = Math.max(2.0F, maxText - 2.0F);
        if (searchCaret <= 0) {
            return 0;
        }
        int start = 0;
        while (start < searchCaret && SEARCH.width(searchText.substring(start, searchCaret)) > limit) {
            start++;
        }
        return start;
    }

    private void tickSearch() {
        if (!searchFocused || searchText.isEmpty()) {
            return;
        }
        if (System.currentTimeMillis() - searchTypedAt > 320L) {
            if (!searchText.equals(manager.searchQuery())) {
                manager.search(searchText);
                page = Page.SEARCH;
            }
        }
    }

    /** Rebuilds the palette from the current artwork; falls back to the client accent. */
    private void tickScheme() {
        if (!manager.coverTint()) {
            if (scheme != Theme.scheme()) {
                scheme = Theme.scheme();
            }
            return;
        }
        Song song = manager.current();
        int seed = 0;
        if (song != null) {
            seed = CoverCache.dominant(song.cover(300));
        }
        if (seed == 0) {
            seed = Theme.accent();
        }
        boolean dark = Theme.dark();
        if (scheme == null || seed != coverSeed || coverDark != dark) {
            coverSeed = seed;
            coverDark = dark;
            scheme = Scheme.of(seed, 0.35F, dark);
        }
    }

    // ------------------------------------------------------------------ //
    // Rendering
    // ------------------------------------------------------------------ //

    @Override
    public void drawScreen(int rawMouseX, int rawMouseY, float partialTicks) {
        tick();
        updateMouse(rawMouseX, rawMouseY);
        hits.clear();
        contentHeight = 0.0F;

        ui.begin();
        try {
            drawBackdrop();
            // Same rule as the ClickGUI: the HUD keeps rendering for the whole time the screen is
            // open; the window simply draws over it.
            HudPass.render(partialTicks);
            drawMorph();
        } finally {
            ui.end();
        }
    }

    // ------------------------------------------------------------------ //
    // Island morph (same recipe as the island's Tablist expansion)
    // ------------------------------------------------------------------ //

    /**
     * The Tablist expands the island surface with {@code Ease.EMPHASIZED} over 420ms and fades its
     * content in with a small upward slide; the player window opens the same way so both gestures
     * feel like one interaction model. The matching collapse is Tablist's 280ms {@code STANDARD}.
     */
    private static final Ease OPEN_CURVE = Ease.EMPHASIZED;
    private static final Ease CLOSE_CURVE = Ease.STANDARD;
    private static final float OPEN_MS = 420.0F;
    private static final float CLOSE_MS = 280.0F;
    /** Above this morph the window covers the screen, so the HUD pass stops being drawn. */

    /** 0 = the island endpoint, 1 = the full window; eased in both directions. */
    private float morph() {
        float progress = open.progress();
        float eased = (closing ? CLOSE_CURVE : OPEN_CURVE).get(Material.clamp(progress, 0.0F, 1.0F));
        return closing ? 1.0F - eased : eased;
    }

    private void drawMorph() {
        float k = Material.clamp(morph(), 0.0F, 1.0F);
        // Close from wherever the window actually is, so closing mid-open never jumps.
        float targetX = closing ? closingBounds[0] : gx * scale;
        float targetY = closing ? closingBounds[1] : gy * scale;
        float targetW = closing ? closingBounds[2] : W * scale;
        float targetH = closing ? closingBounds[3] : H * scale;
        shellX = islandBounds[0] + (targetX - islandBounds[0]) * k;
        shellY = islandBounds[1] + (targetY - islandBounds[1]) * k;
        shellW = islandBounds[2] + (targetW - islandBounds[2]) * k;
        shellH = islandBounds[3] + (targetH - islandBounds[3]) * k;
        float smallRadius = endpointMini
                ? 24.0F * islandBounds[2] / MusicIsland.miniWidth()
                : islandBounds[3] / 2.0F;
        float radius = smallRadius + (RADIUS * scale - smallRadius) * k;
        Scheme current = scheme();
        int color = Material.mix(Theme.scheme().surfaceContainerHigh, current.surfaceContainer, k);
        ui.shadow(shellX, shellY, shellW, shellH, radius, Math.round(3.0F + k));
        ui.rounded(shellX, shellY, shellW, shellH, radius, color);
        if (Theme.frost() && Blur.ready()) ui.frost(shellX, shellY, shellW, shellH, radius, color,
                0.58F + 0.35F * k);

        drawMorphArtwork(k);
        drawMorphWindow(k, radius);
    }

    /**
     * The island artwork the morph starts from. It rides inside the growing surface, is clipped by
     * it and never magnified past its own size, so the same continuous shape is shown in both
     * directions instead of a layer that pops in at a fixed size halfway through.
     */
    private void drawMorphArtwork(float k) {
        // Tablist's pill fade: the old island content is gone once the surface is a third open.
        float alpha = 1.0F - Material.clamp(k * 3.0F, 0.0F, 1.0F);
        if (alpha <= 0.003F) return;
        // A 1.5px bleed keeps the island's own outline ring identical to the HUD on the first and
        // last frame of the morph; the artwork is bounded to its own box anyway.
        ui.scissorPush(shellX - 1.5F, shellY - 1.5F, Math.max(0.0F, shellW + 3.0F),
                Math.max(0.0F, shellH + 3.0F));
        if (endpointMini && MusicIsland.hasTrack()) {
            drawMorphArtworkBox(MusicIsland.miniWidth(), MusicIsland.miniHeight(), alpha, false);
        } else {
            Island island = WatermarkModule.island();
            drawMorphArtworkBox(island.designWidth(), island.designHeight(), alpha, true);
        }
        ui.scissorPop();
    }

    private void drawMorphArtworkBox(float designWidth, float designHeight, float alpha, boolean islandRow) {
        if (designWidth < 1.0F || designHeight < 1.0F) return;
        // Endpoint scale: never magnify the source artwork, so text stays crisp and the pill does
        // not blow up while the surface grows around it. Shrinking is allowed, so a closing window
        // carries the card into the island instead of cropping it.
        float endpoint = Math.min(islandBounds[2] / designWidth, islandBounds[3] / designHeight);
        float fit = Math.min(shellW / designWidth, shellH / designHeight);
        float factor = Math.min(endpoint, fit);
        ui.push();
        ui.translate(shellX + (shellW - designWidth * factor) / 2.0F,
                shellY + (shellH - designHeight * factor) / 2.0F);
        ui.scale(factor, 0.0F, 0.0F);
        ui.alphaPush(alpha);
        if (islandRow) {
            WatermarkModule.island().snapshot(ui, Theme.scheme(),
                    WatermarkModule.instance == null || WatermarkModule.instance.logo());
        } else {
            MusicIsland.draw(ui, Theme.scheme(), designWidth, designHeight, 1.0F);
        }
        ui.alphaPop();
        ui.pop();
    }

    /**
     * The window content fades in on Tablist's own curve — a plain {@code (k - 0.15) / 0.65} ramp
     * plus an 8px upward slide — and is read at its final layout size, so nothing is squashed while
     * the surface grows around it. Like the tab list, the content rides the surface: it is anchored
     * to the card's origin and clipped by it, then lands exactly on the final rect when k reaches 1.
     */
    private void drawMorphWindow(float k, float radius) {
        float alpha = Material.clamp((k - 0.15F) / 0.65F, 0.0F, 1.0F);
        if (alpha <= 0.003F) return;
        float inset = radius * (1.0F - k) * 0.3F + 1.0F;
        drawScale = scale;
        drawX = gx * scale;
        drawY = gy * scale;
        ui.scissorPush(shellX + inset, shellY + inset, Math.max(0.0F, shellW - inset * 2.0F),
                Math.max(0.0F, shellH - inset * 2.0F));
        ui.push();
        ui.translate(shellX, shellY + (1.0F - k) * -8.0F);
        ui.scale(drawScale, 0.0F, 0.0F);
        ui.alphaPush(alpha);
        drawWindow();
        ui.alphaPop();
        ui.pop();
        ui.scissorPop();
    }

    /** Called by the existing Forge GUI hook; the screen closes on the same frame. */
    public boolean deferClose() {
        if (closeCommitted) return false;
        closeCommitted = true;
        restoreIsland();
        if (draggingVolume) {
            manager.commitVolume();
        }
        draggingProgress = draggingVolume = false;
        volumeOpen = false;
        draggingSlider = null;
        return false;
    }

    private void restoreIsland() {
        if (restored) return;
        restored = true;
        returnMini = MusicIsland.hasTrack();
        MusicIsland.returned();
        WatermarkModule.island().returnFromMusic(returnMini);
    }

    private boolean interactive() {
        return !closing && open.isDone();
    }

    private void drawBackdrop() {
        Scheme current = scheme();
        if (mc.theWorld == null || mc.thePlayer == null) {
            ui.rect(0.0F, 0.0F, width, height, current.surface);
            ui.gradient(0.0F, 0.0F, width, height, current.surfaceContainerLow, current.surface);
            return;
        }
        if (Theme.frost()) {
            Blur.prepare();
        }
        ui.rect(0.0F, 0.0F, width, height, Theme.scrim(scrim.value()));
    }

    private void drawWindow() {
        Scheme current = scheme();

        ui.scissorPush(1.0F, 1.0F, W - 2.0F, H - 2.0F);
        drawRail(current);
        drawHeader(current);
        if (lyricOpen) {
            lyricPanel.draw(ui, current, CT_X, CT_Y, CT_W, CT_H);
        } else if (playlistOpen) {
            drawPlaylist(current);
        } else {
            drawContent(current);
        }
        drawMiniBar(current);
        if (qrOpen) {
            drawQrOverlay(current);
        }
        ui.scissorPop();
    }

    private void drawRail(Scheme current) {
        Page[] pages = {Page.HOME, Page.SEARCH, Page.MINE, Page.SETTINGS};
        String[] icons = {I_HOME, I_SEARCH, I_PERSON, I_SETTINGS};
        String[] labels = {"推荐", "搜索", "我的", "设置"};
        float cx = RAIL_X + RAIL_W / 2.0F;
        for (int i = 0; i < pages.length; i++) {
            final Page target = pages[i];
            boolean selected = page == target && !playlistOpen && !lyricOpen;
            float top = RAIL_Y + 10.0F + i * 56.0F;
            float iconY = top + 16.0F;
            boolean hovered = mouseX >= RAIL_X && mouseX < RAIL_X + RAIL_W
                    && mouseY >= top && mouseY < top + 56.0F;
            if (selected) {
                // The indicator is centred on the icon, not on the icon+label pair: a pill that
                // spanned both read as a checkbox once the label sat under it.
                ui.rounded(cx - 19.0F, iconY - 14.0F, 38.0F, 28.0F, 14.0F,
                        Material.withAlpha(current.secondaryContainer, 0.95F));
            } else if (hovered) {
                ui.rounded(cx - 19.0F, iconY - 14.0F, 38.0F, 28.0F, 14.0F,
                        Material.withAlpha(current.onSurface, 0.08F));
            }
            int iconColor = selected ? current.onSecondaryContainer : current.onSurfaceVariant;
            ui.icon(icons[i], cx, iconY, 19.0F, iconColor);
            ui.text(MusicTypeface.MICRO, labels[i], cx, top + 40.0F,
                    selected ? current.onSurface : Material.withAlpha(current.onSurfaceVariant, 0.85F),
                    UI.ALIGN_CENTER);
            hits.add(new Hit(RAIL_X, top, RAIL_W, 56.0F, new Runnable() {
                @Override
                public void run() {
                    page = target;
                    playlistOpen = false;
                    lyricOpen = false;
                    qrOpen = false;
                    scroll.set(0.0F);
                }
            }));
        }
        ui.icon("\ue5cf", cx, H - 38.0F, 22.0F, current.onSurfaceVariant);
        ui.text(MusicTypeface.MICRO, "收起", cx, H - 17.0F, current.onSurfaceVariant, UI.ALIGN_CENTER);
        hits.add(new Hit(RAIL_X, H - 58.0F, RAIL_W, 48.0F, () -> mc.displayGuiScreen(null)));
    }

    private void drawHeader(Scheme current) {
        String title = playlistOpen ? manager.playlistTitle()
                : lyricOpen ? "歌词"
                : page == Page.HOME ? "推荐"
                : page == Page.SEARCH ? "搜索"
                : page == Page.MINE ? "我的" : "设置";
        boolean showSearch = !playlistOpen && !lyricOpen;
        float titleWidth = showSearch ? 96.0F : 300.0F;
        ui.text(DISPLAY, ui.truncate(DISPLAY, title, titleWidth), CT_X + 16.0F, 28.0F,
                current.onSurface, UI.ALIGN_LEFT);

        float avatarSize = 40.0F;
        float avatarX = W - 24.0F - avatarSize;
        float searchX = CT_X + 128.0F;
        float searchW = Math.min(400.0F, avatarX - 24.0F - searchX);
        float searchY = 8.0F;
        this.searchRectX = searchX;
        this.searchRectY = searchY;
        this.searchRectW = showSearch ? searchW : 0.0F;
        this.searchRectH = 40.0F;
        boolean focused = searchFocused;
        if (showSearch) {
            ui.rounded(searchX, searchY, searchW, 40.0F, 20.0F,
                    focused ? current.surfaceContainerHighest : current.surfaceContainerHigh);
            if (focused) {
                ui.outline(searchX, searchY, searchW, 40.0F, 20.0F, 2.0F,
                        Material.withAlpha(current.primary, 0.9F));
            }
            ui.icon(I_SEARCH, searchX + 20.0F, searchY + 20.0F, 17.0F, current.onSurfaceVariant);
            String displayed = searchText.isEmpty() ? "搜索歌曲、歌手" : searchText;
            int textColor = searchText.isEmpty()
                    ? Material.withAlpha(current.onSurfaceVariant, 0.75F) : current.onSurface;
            float textStart = searchX + 38.0F;
            float maxText = searchW - 52.0F;
            int start = searchStart(maxText);
            String visible = searchText.isEmpty() ? displayed : ui.truncate(SEARCH, searchText.substring(start), maxText);
            ui.text(SEARCH, visible, textStart, searchY + 20.0F, textColor, UI.ALIGN_LEFT);
            if (focused && (System.currentTimeMillis() / 530L) % 2L == 0L) {
                float caretX = textStart + SEARCH.width(searchText.substring(start, searchCaret)) + 1.0F;
                ui.rect(caretX, searchY + 12.0F, 1.6F, 16.0F, current.primary);
            }
            hits.add(new Hit(searchX, searchY, searchW, 40.0F, new Runnable() {
                @Override
                public void run() {
                    searchFocused = true;
                    caretAt = System.currentTimeMillis();
                }
            }));
        }

        Profile profile = manager.profile();
        ui.rounded(avatarX, searchY, avatarSize, avatarSize, avatarSize / 2.0F,
                Material.withAlpha(current.surfaceContainerHighest, 1.0F));
        if (profile != null) {
            net.minecraft.util.ResourceLocation avatar = CoverCache.get(profile.avatar(80));
            if (avatar != null) {
                ui.imageRounded(avatar, avatarX, searchY, avatarSize, avatarSize, avatarSize / 2.0F, -1);
            } else {
                ui.icon(I_PERSON, avatarX + avatarSize / 2.0F, searchY + avatarSize / 2.0F, 20.0F,
                        current.onSurfaceVariant);
            }
        } else {
            ui.icon(I_PERSON, avatarX + avatarSize / 2.0F, searchY + avatarSize / 2.0F, 20.0F,
                    current.onSurfaceVariant);
        }
        hits.add(new Hit(avatarX, searchY, avatarSize, avatarSize, new Runnable() {
            @Override
            public void run() {
                page = Page.MINE;
                playlistOpen = false;
                lyricOpen = false;
                qrOpen = manager.profile() == null;
                scroll.set(0.0F);
            }
        }));
    }

    // ------------------------------------------------------------------ //
    // Pages
    // ------------------------------------------------------------------ //

    private void drawContent(Scheme current) {
        ui.scissorPush(CT_X, CT_Y, CT_W, CT_H);
        float y = CT_Y - scroll.value();
        switch (page) {
            case HOME:
                y = drawHome(current, y);
                break;
            case SEARCH:
                y = drawSearchPage(current, y);
                break;
            case MINE:
                y = drawMine(current, y);
                break;
            default:
                y = drawSettings(current, y);
                break;
        }
        contentHeight = y - (CT_Y - scroll.value()) + 12.0F;
        contentScrollable = contentHeight > CT_H;
        ui.scissorPop();
    }

    private float drawHome(Scheme current, float y) {
        ui.text(DISPLAY, greeting(), CT_X + 8.0F, y + 18.0F, current.onSurface, UI.ALIGN_LEFT);
        y += 44.0F;
        y = section(current, "推荐歌单", y);
        List<Playlist> home = manager.home();
        if (home.isEmpty()) {
            ui.text(MusicTypeface.BODY, manager.homeLoading() ? "正在加载…" : "暂无推荐（登录后可看到每日推荐）",
                    CT_X + 8.0F, y + 12.0F, current.onSurfaceVariant, UI.ALIGN_LEFT);
            y += 40.0F;
        } else {
            y = grid(current, home, y, 5, true);
        }
        y += 8.0F;
        List<Playlist> mine = manager.myPlaylists();
        if (!mine.isEmpty()) {
            y = section(current, "我的歌单", y);
            y = grid(current, mine, y, 5, true);
        }
        return y;
    }

    private String greeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 5) {
            return "夜深了";
        }
        if (hour < 11) {
            return "早上好";
        }
        if (hour < 14) {
            return "中午好";
        }
        if (hour < 18) {
            return "下午好";
        }
        return "晚上好";
    }

    private float section(Scheme current, String title, float y) {
        ui.text(MusicTypeface.HEADLINE, title, CT_X + 8.0F, y + 14.0F, current.onSurface, UI.ALIGN_LEFT);
        return y + 32.0F;
    }

    private float grid(Scheme current, List<Playlist> playlists, float y, int columns, boolean withTitle) {
        float gap = 10.0F;
        float available = CT_W - 16.0F;
        float cardW = (available - gap * (columns - 1)) / columns;
        float cardH = cardW + (withTitle ? 38.0F : 0.0F);
        int rows = (playlists.size() + columns - 1) / columns;
        for (int i = 0; i < playlists.size(); i++) {
            Playlist playlist = playlists.get(i);
            int column = i % columns;
            int row = i / columns;
            float x = CT_X + 8.0F + column * (cardW + gap);
            float cy = y + row * (cardH + gap);
            boolean hovered = inside(x, cy, cardW, cardH);
            if (hovered) {
                ui.rounded(x - 2.0F, cy - 2.0F, cardW + 4.0F, cardH + 4.0F, 18.0F,
                        Material.withAlpha(current.onSurface, 0.06F));
            }
            ui.rounded(x, cy, cardW, cardH, 16.0F, current.surfaceContainerHigh);
            net.minecraft.util.ResourceLocation cover = CoverCache.get(playlist.cover(220));
            if (cover != null) {
                ui.imageRounded(cover, x + 5.0F, cy + 5.0F, cardW - 10.0F, cardW - 10.0F, 12.0F, -1);
            } else {
                ui.rounded(x + 5.0F, cy + 5.0F, cardW - 10.0F, cardW - 10.0F, 12.0F,
                        Material.withAlpha(current.primaryContainer, 0.55F));
                ui.icon(I_MUSIC, x + cardW / 2.0F, cy + cardW / 2.0F, 22.0F,
                        Material.withAlpha(current.onPrimaryContainer, 0.8F));
            }
            ui.text(CARD_TITLE, ui.truncate(CARD_TITLE, playlist.name, cardW - 16.0F),
                    x + 8.0F, cy + cardW + 2.0F, current.onSurface, UI.ALIGN_LEFT);
            ui.text(MusicTypeface.CAPTION, playlist.trackCount + " 首", x + 8.0F, cy + cardW + 20.0F,
                    Material.withAlpha(current.onSurfaceVariant, 0.9F), UI.ALIGN_LEFT);
            final long id = playlist.id;
            final String name = playlist.name;
            hits.add(new Hit(x, cy, cardW, cardH, new Runnable() {
                @Override
                public void run() {
                    manager.openPlaylist(id, name);
                    playlistOpen = true;
                    lyricOpen = false;
                    scroll.set(0.0F);
                }
            }));
        }
        return y + rows * (cardH + gap);
    }

    private float drawSearchPage(Scheme current, float y) {
        String query = manager.searchQuery();
        if (query.isEmpty()) {
            ui.text(MusicTypeface.BODY, "输入关键字搜索网易云音乐", CT_X + 8.0F, y + 16.0F,
                    current.onSurfaceVariant, UI.ALIGN_LEFT);
            return y + 40.0F;
        }
        List<Song> results = manager.searchResults();
        if (manager.searching() && results.isEmpty()) {
            ui.text(MusicTypeface.BODY, "正在搜索…", CT_X + 8.0F, y + 16.0F, current.onSurfaceVariant, UI.ALIGN_LEFT);
            return y + 40.0F;
        }
        if (results.isEmpty()) {
            String message = manager.searchError() != null ? manager.searchError() : "没有找到结果";
            ui.text(MusicTypeface.BODY, message, CT_X + 8.0F, y + 16.0F, current.onSurfaceVariant, UI.ALIGN_LEFT);
            return y + 40.0F;
        }
        ui.text(MusicTypeface.LABEL, "找到 " + results.size() + " 首  ·  " + query,
                CT_X + 8.0F, y + 12.0F, Material.withAlpha(current.onSurfaceVariant, 0.9F), UI.ALIGN_LEFT);
        y += 28.0F;
        return songList(current, results, y, null);
    }

    private float drawMine(Scheme current, float y) {
        Profile profile = manager.profile();
        if (profile == null) {
            float cardH = 132.0F;
            ui.rounded(CT_X + 8.0F, y, CT_W - 16.0F, cardH, 20.0F, current.surfaceContainerHigh);
            ui.text(MusicTypeface.HEADLINE_LARGE, "登录网易云音乐", CT_X + 28.0F, y + 34.0F,
                    current.onSurface, UI.ALIGN_LEFT);
            ui.text(MusicTypeface.BODY, "扫码登录后可同步歌单、查看每日推荐", CT_X + 28.0F, y + 62.0F,
                    current.onSurfaceVariant, UI.ALIGN_LEFT);
            button(current, "扫码登录", CT_X + 28.0F, y + 86.0F, 132.0F, 36.0F, true, new Runnable() {
                @Override
                public void run() {
                    qrOpen = true;
                    manager.startQrLogin();
                }
            });
            y += cardH + 12.0F;
            if (!manager.myPlaylists().isEmpty()) {
                y = section(current, "我的歌单", y);
                y = grid(current, manager.myPlaylists(), y, 5, true);
            }
            return y;
        }
        ui.rounded(CT_X + 8.0F, y, CT_W - 16.0F, 96.0F, 20.0F, current.surfaceContainerHigh);
        net.minecraft.util.ResourceLocation avatar = CoverCache.get(profile.avatar(160));
        if (avatar != null) {
            ui.imageRounded(avatar, CT_X + 24.0F, y + 20.0F, 56.0F, 56.0F, 28.0F, -1);
        } else {
            ui.circle(CT_X + 52.0F, y + 48.0F, 28.0F, Material.withAlpha(current.primaryContainer, 0.8F));
            ui.icon(I_PERSON, CT_X + 52.0F, y + 48.0F, 22.0F, current.onPrimaryContainer);
        }
        ui.text(MusicTypeface.HEADLINE_LARGE, ui.truncate(MusicTypeface.HEADLINE_LARGE, profile.nickname, 320.0F),
                CT_X + 96.0F, y + 38.0F, current.onSurface, UI.ALIGN_LEFT);
        ui.text(MusicTypeface.BODY, profile.vipType > 0 ? "VIP 会员" : "普通用户", CT_X + 96.0F, y + 62.0F,
                profile.vipType > 0 ? Material.withAlpha(0xFFF5C542, 1.0F) : current.onSurfaceVariant,
                UI.ALIGN_LEFT);
        button(current, "退出登录", CT_X + CT_W - 130.0F, y + 30.0F, 106.0F, 36.0F, false,
                new Runnable() {
                    @Override
                    public void run() {
                        manager.logout();
                    }
                });
        y += 108.0F;
        y = section(current, "我的歌单", y);
        List<Playlist> mine = manager.myPlaylists();
        if (mine.isEmpty()) {
            ui.text(MusicTypeface.BODY, manager.myPlaylistsLoading() ? "正在加载…" : "暂无歌单",
                    CT_X + 8.0F, y + 12.0F, current.onSurfaceVariant, UI.ALIGN_LEFT);
            return y + 40.0F;
        }
        return grid(current, mine, y, 5, true);
    }

    private float drawSettings(Scheme current, float y) {
        MusicManager m = manager;
        y = settingRowHeader(current, "播放", y);
        y = chipRow(current, "音质", new String[]{"标准", "较高", "极高", "无损"},
                new String[]{"standard", "higher", "exhigh", "lossless"},
                new String[]{m.streamLevel()}, 1, y, new StringSetter() {
                    @Override
                    public void set(String value) {
                        manager.setStreamLevel(value);
                    }
                });
        final int outputMode = m.outputMode();
        y = chipRow(current, "音频输出", new String[]{"自动", "Java Sound", "游戏 OpenAL"},
                new String[]{"auto", "java", "game"},
                new String[]{outputMode == AudioEngine.OUTPUT_JAVA_SOUND ? "java"
                        : outputMode == AudioEngine.OUTPUT_GAME ? "game" : "auto"},
                1, y, new StringSetter() {
                    @Override
                    public void set(String value) {
                        manager.setOutputMode("java".equals(value) ? AudioEngine.OUTPUT_JAVA_SOUND
                                : "game".equals(value) ? AudioEngine.OUTPUT_GAME : AudioEngine.OUTPUT_AUTO);
                    }
                });
        y = settingRowHeader(current, "外观", y);
        y = switchRow(current, "封面取色", "整个界面按专辑封面重新配色", y, m.coverTint(),
                new BooleanSetter() {
                    @Override
                    public void set(boolean value) {
                        manager.setCoverTint(value);
                    }
                });
        y = sliderRow(current, "歌词字号", String.format("%.0f%%", m.lyricScale() * 100.0F), y,
                (m.lyricScale() - 0.8F) / 0.6F, 0.0F, 1.0F, new FloatSetter() {
                    @Override
                    public void set(float value) {
                        manager.setLyricScale(0.8F + value * 0.6F);
                    }
                });
        y = switchRow(current, "歌词动效", "逐字扫光、行滚动与呼吸放大", y, m.lyricAnimation(),
                new BooleanSetter() {
                    @Override
                    public void set(boolean value) {
                        manager.setLyricAnimation(value);
                    }
                });
        y = settingRowHeader(current, "3D 歌词", y);
        final MusicManager.Lyrics3DSettings l3 = m.lyrics3d;
        y = switchRow(current, "3D 歌词", "把当前歌词漂在世界里（歌词来自内置播放器）", y, l3.enabled,
                new BooleanSetter() {
                    @Override
                    public void set(boolean value) {
                        l3.enabled = value;
                        manager.saveSettings();
                    }
                });
        if (l3.enabled) {
            y = sliderRow(current, "字号", Math.round(l3.scale * 100.0F) + "%", y,
                    (l3.scale - 0.5F) / 1.5F, 0.0F, 1.0F, new FloatSetter() {
                        @Override
                        public void set(float value) {
                            l3.scale = 0.5F + value * 1.5F;
                            manager.saveSettings();
                        }
                    });
            y = sliderRow(current, "距离", Math.round(l3.distance) + " 格", y,
                    (l3.distance - 3.0F) / 37.0F, 0.0F, 1.0F, new FloatSetter() {
                        @Override
                        public void set(float value) {
                            l3.distance = 3.0F + value * 37.0F;
                            manager.saveSettings();
                        }
                    });
            y = sliderRow(current, "左右位置", String.format("%.1f", l3.offsetX), y,
                    (l3.offsetX + 8.0F) / 16.0F, 0.0F, 1.0F, new FloatSetter() {
                        @Override
                        public void set(float value) {
                            l3.offsetX = -8.0F + value * 16.0F;
                            manager.saveSettings();
                        }
                    });
            y = sliderRow(current, "上下位置", String.format("%.1f", l3.offsetY), y,
                    (l3.offsetY + 6.0F) / 12.0F, 0.0F, 1.0F, new FloatSetter() {
                        @Override
                        public void set(float value) {
                            l3.offsetY = -6.0F + value * 12.0F;
                            manager.saveSettings();
                        }
                    });
            y = sliderRow(current, "散布角度", Math.round(l3.spread) + "°", y,
                    l3.spread / 60.0F, 0.0F, 1.0F, new FloatSetter() {
                        @Override
                        public void set(float value) {
                            l3.spread = value * 60.0F;
                            manager.saveSettings();
                        }
                    });
            y = switchRow(current, "发光", "暗处更醒目", y, l3.glow,
                    new BooleanSetter() {
                        @Override
                        public void set(boolean value) {
                            l3.glow = value;
                            manager.saveSettings();
                        }
                    });
            y = switchRow(current, "手绘抖动", "逐字低频抖动", y, l3.wiggle,
                    new BooleanSetter() {
                        @Override
                        public void set(boolean value) {
                            l3.wiggle = value;
                            manager.saveSettings();
                        }
                    });
            y = switchRow(current, "逐字弹出", "唱到哪个字就弹出哪个字", y, l3.wordPop,
                    new BooleanSetter() {
                        @Override
                        public void set(boolean value) {
                            l3.wordPop = value;
                            manager.saveSettings();
                        }
                    });
            y = switchRow(current, "彩虹色", "唱过的字按彩虹渐变", y, l3.rainbow,
                    new BooleanSetter() {
                        @Override
                        public void set(boolean value) {
                            l3.rainbow = value;
                            manager.saveSettings();
                        }
                    });
            y = switchRow(current, "中文翻译", "关闭后只显示原语言歌词", y, l3.translation,
                    new BooleanSetter() {
                        @Override
                        public void set(boolean value) {
                            l3.translation = value;
                            manager.saveSettings();
                        }
                    });
            y = switchRow(current, "跟随视角", "关闭后固定在原处，出视野时自动拉回", y, l3.followView,
                    new BooleanSetter() {
                        @Override
                        public void set(boolean value) {
                            l3.followView = value;
                            manager.saveSettings();
                        }
                    });
        }
        y = settingRowHeader(current, "账号", y);
        boolean logged = m.profile() != null;
        button(current, logged ? "退出登录" : "扫码登录", CT_X + 8.0F, y + 6.0F, 132.0F, 38.0F, !logged,
                new Runnable() {
                    @Override
                    public void run() {
                        if (manager.profile() != null) {
                            manager.logout();
                        } else {
                            qrOpen = true;
                            manager.startQrLogin();
                        }
                    }
                });
        ui.text(MusicTypeface.BODY, logged ? "已登录：" + m.profile().nickname : "未登录，仅能播放试听/免费曲目",
                CT_X + 156.0F, y + 25.0F, current.onSurfaceVariant, UI.ALIGN_LEFT);
        y += 56.0F;
        String active = m.activeOutput();
        String outputLabel = active.isEmpty()
                ? ("自动（" + AudioRuntime.autoReason() + "）")
                : ("当前：" + active);
        ui.text(MusicTypeface.CAPTION, "音频输出：" + outputLabel,
                CT_X + 8.0F, y + 10.0F, Material.withAlpha(current.onSurfaceVariant, 0.7F), UI.ALIGN_LEFT);
        return y + 28.0F;
    }

    private float settingRowHeader(Scheme current, String title, float y) {
        ui.text(MusicTypeface.LABEL, title, CT_X + 8.0F, y + 12.0F,
                Material.withAlpha(current.primary, 0.95F), UI.ALIGN_LEFT);
        return y + 30.0F;
    }

    private float drawPlaylist(Scheme current) {
        ui.scissorPush(CT_X, CT_Y, CT_W, CT_H);
        float y = CT_Y + 6.0F;
        ui.circle(CT_X + 24.0F, y + 14.0F, 16.0F, Material.withAlpha(current.onSurface, 0.08F));
        ui.icon(I_BACK, CT_X + 24.0F, y + 14.0F, 16.0F, current.onSurface);
        hits.add(new Hit(CT_X + 4.0F, y - 2.0F, 40.0F, 32.0F, new Runnable() {
            @Override
            public void run() {
                playlistOpen = false;
                scroll.set(0.0F);
            }
        }));
        ui.text(MusicTypeface.LABEL, manager.playlistLoading() ? "加载中…" : manager.playlistTracks().size() + " 首",
                CT_X + 48.0F, y + 14.0F, current.onSurfaceVariant, UI.ALIGN_LEFT);
        y += 38.0F;
        // The track list scrolls under a fixed header, so it gets its own clip: without it the
        // first row slid up through the back chip and the count.
        ui.scissorPush(CT_X, y, CT_W, CT_Y + CT_H - y);
        float start = y - scroll.value();
        float end = songList(current, manager.playlistTracks(), start, manager.playlistTitle());
        ui.scissorPop();
        contentHeight = end - start + 12.0F;
        contentScrollable = contentHeight > (CT_Y + CT_H - y);
        ui.scissorPop();
        return 0.0F;
    }

    private float songList(Scheme current, List<Song> songs, float y, String header) {
        if (songs.isEmpty()) {
            ui.text(MusicTypeface.BODY, manager.playlistLoading() ? "正在加载曲目…" : "这里还没有歌曲",
                    CT_X + 8.0F, y + 16.0F, current.onSurfaceVariant, UI.ALIGN_LEFT);
            return y + 44.0F;
        }
        Song playing = manager.current();
        for (int i = 0; i < songs.size(); i++) {
            final Song song = songs.get(i);
            float rowY = y + i * ROW_PITCH;
            // Cull rows outside the list viewport. They were still drawn (and, worse, still asked
            // CoverCache for artwork) for the whole playlist, so opening a 70-track playlist
            // requested 70+ covers in one frame and thrashed the texture cache.
            if (rowY + ROW_PITCH < CT_Y - 8.0F || rowY > CT_Y + CT_H + 8.0F) {
                continue;
            }
            boolean isCurrent = playing != null && playing.id == song.id && manager.isPlaying();
            boolean hovered = inside(CT_X + 8.0F, rowY, CT_W - 16.0F, ROW_PITCH - 6.0F);
            if (hovered || isCurrent) {
                ui.rounded(CT_X + 8.0F, rowY, CT_W - 16.0F, ROW_PITCH - 6.0F, 14.0F,
                        Material.withAlpha(isCurrent ? current.primary : current.onSurface,
                                isCurrent ? 0.16F : 0.06F));
            }
            net.minecraft.util.ResourceLocation cover = CoverCache.get(song.cover(120));
            float coverX = CT_X + 20.0F;
            float coverY = rowY + 6.0F;
            if (cover != null) {
                ui.rounded(coverX, coverY, 44.0F, 44.0F, 10.0F, current.surfaceContainerHighest);
                ui.imageRounded(cover, coverX, coverY, 44.0F, 44.0F, 10.0F, -1);
            } else {
                ui.rounded(coverX, coverY, 44.0F, 44.0F, 10.0F,
                        Material.withAlpha(current.primaryContainer, 0.5F));
                ui.icon(I_MUSIC, coverX + 22.0F, coverY + 22.0F, 18.0F,
                        Material.withAlpha(current.onPrimaryContainer, 0.85F));
            }
            int titleColor = isCurrent ? current.primary : current.onSurface;
            ui.text(ROW, ui.truncate(ROW, song.name, CT_W - 220.0F), coverX + 60.0F, rowY + 20.0F,
                    titleColor, UI.ALIGN_LEFT);
            ui.text(ROW_SUB, ui.truncate(ROW_SUB, song.artist + "  ·  " + song.album, CT_W - 220.0F),
                    coverX + 60.0F, rowY + 38.0F, current.onSurfaceVariant, UI.ALIGN_LEFT);
            ui.text(MusicTypeface.CAPTION, duration(song.durationMs), CT_X + CT_W - 24.0F, rowY + 29.0F,
                    Material.withAlpha(current.onSurfaceVariant, 0.85F), UI.ALIGN_RIGHT);
            hits.add(new Hit(CT_X + 8.0F, rowY, CT_W - 16.0F, ROW_PITCH - 6.0F, new Runnable() {
                @Override
                public void run() {
                    manager.play(song, songs);
                    lyricPanel.reset();
                }
            }));
        }
        return y + songs.size() * ROW_PITCH;
    }

    private static String duration(long ms) {
        if (ms <= 0L) {
            return "--:--";
        }
        long seconds = ms / 1000L;
        return String.format("%d:%02d", seconds / 60L, seconds % 60L);
    }

    /** {@code m:ss} for the transport clock, with a zero state instead of dashes. */
    private static String clock(long ms) {
        long seconds = Math.max(0L, ms) / 1000L;
        return String.format("%d:%02d", seconds / 60L, seconds % 60L);
    }

    // ------------------------------------------------------------------ //
    // Mini player
    // ------------------------------------------------------------------ //

    private void drawMiniBar(Scheme current) {
        float px = MINI_X + 4.0F;
        float py = MINI_Y + 4.0F;
        float pw = MINI_W - 8.0F;
        float ph = MINI_H - 8.0F;
        ui.shadow(px, py, pw, ph, 18.0F, 2);
        ui.rounded(px, py, pw, ph, 18.0F, Material.withAlpha(current.surfaceContainerHigh, 0.97F));
        if (Theme.frost() && Blur.ready()) {
            ui.frost(px, py, pw, ph, 18.0F, current.surfaceContainerHigh, 0.82F);
        }

        Song song = manager.current();
        long duration = manager.durationMs();
        float progress = duration > 0L ? Math.min(1.0F, manager.positionMs() / (float) duration) : 0.0F;
        if (progressPreview >= 0.0F) {
            progress = progressPreview;
        }

        float trackX = px + 14.0F;
        float trackW = pw - 28.0F - 104.0F;
        float trackY = py + 9.0F;
        if (song != null) {
            WavyProgressIndicator.Style style = new WavyProgressIndicator.Style(40.0F, 0.42F, 28.0F,
                    current.primary, Material.withAlpha(current.secondaryContainer, 0.9F));
            WavyProgressIndicator.drawLinear(ui, trackX, trackY - 4.0F, trackW, 9.0F, progress, style);
            hits.add(new ProgressHit(trackX, trackY - 12.0F, trackW, 26.0F, trackX, trackW));

            long shownPosition = progressPreview >= 0.0F && duration > 0L
                    ? (long) (duration * progressPreview) : manager.positionMs();
            String time = clock(shownPosition) + " / " + clock(duration);
            ui.text(MusicTypeface.CAPTION, time, px + pw - 14.0F, trackY,
                    Material.withAlpha(current.onSurfaceVariant, 0.95F), UI.ALIGN_RIGHT);
        }

        float coverSize = 52.0F;
        float coverX = px + 14.0F;
        float coverY = py + 18.0F;
        ui.rounded(coverX, coverY, coverSize, coverSize, 12.0F, current.surfaceContainerHighest);
        if (song != null) {
            net.minecraft.util.ResourceLocation cover = CoverCache.get(song.cover(160));
            if (cover != null) {
                ui.imageRounded(cover, coverX, coverY, coverSize, coverSize, 12.0F, -1);
            } else {
                ui.icon(I_MUSIC, coverX + coverSize / 2.0F, coverY + coverSize / 2.0F, 20.0F,
                        Material.withAlpha(current.onSurfaceVariant, 0.8F));
            }
        }
        hits.add(new Hit(coverX, coverY, coverSize, coverSize, new Runnable() {
            @Override
            public void run() {
                if (manager.current() != null) {
                    lyricOpen = !lyricOpen;
                    lyricPanel.reset();
                }
            }
        }));

        float textX = coverX + coverSize + 14.0F;
        float textW = 240.0F;
        String title = song == null ? "未在播放" : song.name;
        String artist = song == null ? "网易云音乐" : song.artist;
        ui.text(ROW, ui.truncate(ROW, title, textW), textX, py + 30.0F,
                song == null ? current.onSurfaceVariant : current.onSurface, UI.ALIGN_LEFT);
        ui.text(ROW_SUB, ui.truncate(ROW_SUB, artist, textW), textX, py + 50.0F,
                Material.withAlpha(current.onSurfaceVariant, 0.9F), UI.ALIGN_LEFT);

        float centerX = px + pw / 2.0F;
        float buttonY = py + ph / 2.0F;
        transportButton(current, I_PREV, centerX - 84.0F, buttonY, 34.0F, new Runnable() {
            @Override
            public void run() {
                manager.previous();
                lyricPanel.reset();
            }
        });
        boolean playing = manager.isPlaying();
        ui.shadow(centerX - 28.0F, buttonY - 17.0F, 56.0F, 34.0F, 17.0F, 1);
        ui.rounded(centerX - 28.0F, buttonY - 17.0F, 56.0F, 34.0F, 17.0F, current.primary);
        ui.icon(playing ? I_PAUSE : I_PLAY, centerX, buttonY, 20.0F, current.onPrimary);
        hits.add(new Hit(centerX - 28.0F, buttonY - 17.0F, 56.0F, 34.0F, new Runnable() {
            @Override
            public void run() {
                manager.togglePlay();
            }
        }));
        transportButton(current, I_NEXT, centerX + 84.0F, buttonY, 34.0F, new Runnable() {
            @Override
            public void run() {
                manager.next();
                lyricPanel.reset();
            }
        });

        float rightX = px + pw - 34.0F;
        transportButton(current, I_LYRICS, rightX, buttonY, 30.0F, new Runnable() {
            @Override
            public void run() {
                if (manager.current() != null) {
                    lyricOpen = !lyricOpen;
                    lyricPanel.reset();
                }
            }
        });
        rightX -= 42.0F;
        String modeIcon = manager.repeat() == MusicManager.Repeat.ONE ? I_REPEAT_ONE : I_REPEAT;
        int modeColor = manager.repeat() == MusicManager.Repeat.OFF
                ? Material.withAlpha(current.onSurfaceVariant, 0.75F) : current.primary;
        ui.icon(modeIcon, rightX, buttonY, 19.0F, manager.shuffle() ? current.primary : modeColor);
        hits.add(new Hit(rightX - 16.0F, buttonY - 16.0F, 32.0F, 32.0F, new Runnable() {
            @Override
            public void run() {
                manager.cycleRepeat();
            }
        }));
        rightX -= 42.0F;
        ui.icon(manager.shuffle() ? I_SHUFFLE : I_SHUFFLE, rightX, buttonY, 19.0F,
                manager.shuffle() ? current.primary : Material.withAlpha(current.onSurfaceVariant, 0.75F));
        hits.add(new Hit(rightX - 16.0F, buttonY - 16.0F, 32.0F, 32.0F, new Runnable() {
            @Override
            public void run() {
                manager.toggleShuffle();
            }
        }));
        float speakerX = rightX - 42.0F;
        if (volumeOpen) {
            float volW = 92.0F;
            float volStartX = speakerX - 20.0F - volW;
            ui.rounded(volStartX, buttonY - 2.0F, volW, 4.0F, 2.0F, current.surfaceContainerHighest);
            ui.rounded(volStartX, buttonY - 2.0F, Math.max(4.0F, volW * manager.volume()), 4.0F, 2.0F,
                    current.primary);
            ui.circle(volStartX + volW * manager.volume(), buttonY, 6.0F, current.primary);
            ui.text(MusicTypeface.CAPTION, Math.round(manager.volume() * 100.0F) + "%",
                    volStartX + volW / 2.0F, buttonY + 15.0F,
                    Material.withAlpha(current.onSurfaceVariant, 0.9F), UI.ALIGN_CENTER);
            hits.add(new VolumeHit(volStartX, buttonY - 14.0F, volW, 28.0F, volStartX, volW));
        }
        ui.icon(manager.volume() <= 0.001F ? I_VOLUME_OFF : I_VOLUME, speakerX, buttonY, 19.0F,
                volumeOpen ? current.primary : Material.withAlpha(current.onSurfaceVariant, 0.9F));
        hits.add(new Hit(speakerX - 16.0F, buttonY - 16.0F, 32.0F, 32.0F, new Runnable() {
            @Override
            public void run() {
                volumeOpen = !volumeOpen;
            }
        }));

        if (manager.isLoadingTrack() || manager.engine().isBuffering()) {
            ui.text(MusicTypeface.CAPTION, manager.isLoadingTrack() ? "缓冲中…" : "解码中…",
                    textX, py + 68.0F, Material.withAlpha(current.primary, 0.9F), UI.ALIGN_LEFT);
        } else if (manager.playbackError() != null) {
            ui.text(MusicTypeface.CAPTION, ui.truncate(MusicTypeface.CAPTION, manager.playbackError(), textW + 40.0F),
                    textX, py + 68.0F, current.error, UI.ALIGN_LEFT);
        }
    }

    private void transportButton(Scheme current, String icon, float x, float y, float size, Runnable action) {
        boolean hovered = inside(x - size / 2.0F, y - size / 2.0F, size, size);
        if (hovered) {
            ui.circle(x, y, size / 2.0F + 2.0F, Material.withAlpha(current.onSurface, 0.08F));
        }
        ui.icon(icon, x, y, size * 0.62F, current.onSurface);
        hits.add(new Hit(x - size / 2.0F, y - size / 2.0F, size, size, action));
    }

    // ------------------------------------------------------------------ //
    // QR overlay
    // ------------------------------------------------------------------ //

    private void drawQrOverlay(Scheme current) {
        ui.rect(0.0F, 0.0F, W, H, Material.withAlpha(0x000000, 0.55F));
        float cardW = 340.0F;
        float cardH = 420.0F;
        float x = (W - cardW) / 2.0F;
        float y = (H - cardH) / 2.0F;
        ui.shadow(x, y, cardW, cardH, 24.0F, 3);
        ui.rounded(x, y, cardW, cardH, 24.0F, current.surfaceContainerHigh);
        ui.text(MusicTypeface.HEADLINE_LARGE, "扫码登录网易云", W / 2.0F, y + 30.0F,
                current.onSurface, UI.ALIGN_CENTER);
        ui.circle(x + cardW - 28.0F, y + 26.0F, 14.0F, Material.withAlpha(current.onSurface, 0.08F));
        ui.icon(I_CLOSE, x + cardW - 28.0F, y + 26.0F, 15.0F, current.onSurface);
        hits.add(new Hit(x + cardW - 42.0F, y + 12.0F, 28.0F, 28.0F, new Runnable() {
            @Override
            public void run() {
                qrOpen = false;
                manager.cancelQrLogin();
            }
        }));

        float qrSize = 220.0F;
        float qrX = W / 2.0F - qrSize / 2.0F;
        float qrY = y + 70.0F;
        ui.rounded(qrX - 10.0F, qrY - 10.0F, qrSize + 20.0F, qrSize + 20.0F, 16.0F, 0xFFFFFFFF);
        boolean[][] matrix = manager.qrMatrix();
        if (matrix != null && matrix.length > 0) {
            int modules = matrix.length;
            float cell = qrSize / modules;
            ui.push();
            ui.scissorPush(qrX, qrY, qrSize, qrSize);
            for (int row = 0; row < modules; row++) {
                for (int col = 0; col < modules; col++) {
                    if (matrix[row][col]) {
                        ui.rect(qrX + col * cell, qrY + row * cell, cell + 0.4F, cell + 0.4F, 0xFF111111);
                    }
                }
            }
            ui.scissorPop();
            ui.pop();
        } else {
            ui.text(MusicTypeface.BODY, "二维码生成中…", W / 2.0F, qrY + qrSize / 2.0F,
                    Material.withAlpha(current.onSurfaceVariant, 0.9F), UI.ALIGN_CENTER);
        }

        MusicManager.QrState state = manager.qrState();
        String status;
        int color = current.onSurfaceVariant;
        switch (state) {
            case WAITING:
                status = "打开网易云音乐 App 扫描二维码";
                break;
            case SCANNED:
                status = "已扫码，请在手机上确认登录";
                color = current.primary;
                break;
            case SUCCESS:
                status = "登录成功";
                color = current.primary;
                break;
            case EXPIRED:
                status = "二维码已过期，请刷新";
                color = current.error;
                break;
            case ERROR:
                status = manager.qrError() == null ? "登录失败" : manager.qrError();
                color = current.error;
                break;
            default:
                status = "正在获取二维码…";
                break;
        }
        ui.text(MusicTypeface.BODY, status, W / 2.0F, qrY + qrSize + 30.0F, color, UI.ALIGN_CENTER);
        ui.text(MusicTypeface.CAPTION, "登录信息仅保存在 .minecraft/keystrokes/music/",
                W / 2.0F, qrY + qrSize + 50.0F,
                Material.withAlpha(current.onSurfaceVariant, 0.7F), UI.ALIGN_CENTER);

        if (state == MusicManager.QrState.EXPIRED || state == MusicManager.QrState.ERROR) {
            button(current, "刷新二维码", W / 2.0F - 60.0F, y + cardH - 52.0F, 120.0F, 36.0F, true,
                    new Runnable() {
                        @Override
                        public void run() {
                            manager.startQrLogin();
                        }
                    });
        }
    }

    // ------------------------------------------------------------------ //
    // Small controls
    // ------------------------------------------------------------------ //

    private void button(Scheme current, String label, float x, float y, float w, float h, boolean filled,
                        Runnable action) {
        boolean hovered = inside(x, y, w, h);
        int background = filled
                ? (hovered ? Material.mix(current.primary, 0xFFFFFFFF, 0.10F) : current.primary)
                : Material.withAlpha(current.onSurface, hovered ? 0.10F : 0.04F);
        ui.rounded(x, y, w, h, h / 2.0F, background);
        ui.text(MusicTypeface.LABEL_TIGHT, label, x + w / 2.0F, y + h / 2.0F,
                filled ? current.onPrimary : current.onSurface, UI.ALIGN_CENTER);
        hits.add(new Hit(x, y, w, h, action));
    }

    private float switchRow(Scheme current, String title, String description, float y, boolean value,
                            final BooleanSetter setter) {
        float height = 56.0F;
        boolean hovered = inside(CT_X + 8.0F, y, CT_W - 16.0F, height);
        if (hovered) {
            ui.rounded(CT_X + 8.0F, y, CT_W - 16.0F, height, 12.0F,
                    Material.withAlpha(current.onSurface, 0.05F));
        }
        ui.text(MusicTypeface.HEADLINE, title, CT_X + 20.0F, y + 20.0F, current.onSurface, UI.ALIGN_LEFT);
        ui.text(MusicTypeface.BODY, description, CT_X + 20.0F, y + 40.0F, current.onSurfaceVariant, UI.ALIGN_LEFT);
        float trackW = 44.0F;
        float trackH = 24.0F;
        float trackX = CT_X + CT_W - 28.0F - trackW;
        float trackY = y + height / 2.0F - trackH / 2.0F;
        ui.rounded(trackX, trackY, trackW, trackH, trackH / 2.0F,
                value ? current.primary : current.surfaceContainerHighest);
        if (!value) {
            ui.outline(trackX, trackY, trackW, trackH, trackH / 2.0F, 1.5F, current.outline);
        }
        float knob = value ? trackW - trackH + 4.0F : 4.0F;
        ui.circle(trackX + knob + trackH / 2.0F - 7.0F, trackY + trackH / 2.0F, 8.0F,
                value ? current.onPrimary : current.outline);
        hits.add(new Hit(CT_X + 8.0F, y, CT_W - 16.0F, height, new Runnable() {
            @Override
            public void run() {
                setter.set(!value);
            }
        }));
        return y + height;
    }

    private float sliderRow(Scheme current, String title, String valueLabel, float y, final float value,
                            float min, float max, final FloatSetter setter) {
        float height = 52.0F;
        ui.text(MusicTypeface.HEADLINE, title, CT_X + 20.0F, y + 18.0F, current.onSurface, UI.ALIGN_LEFT);
        ui.text(MusicTypeface.CAPTION, valueLabel, CT_X + CT_W - 28.0F, y + 18.0F,
                current.onSurfaceVariant, UI.ALIGN_RIGHT);
        float trackX = CT_X + 20.0F;
        float trackW = CT_W - 56.0F;
        float trackY = y + 36.0F;
        ui.rounded(trackX, trackY, trackW, 4.0F, 2.0F, current.surfaceContainerHighest);
        ui.rounded(trackX, trackY, Math.max(4.0F, trackW * value), 4.0F, 2.0F, current.primary);
        ui.circle(trackX + trackW * value, trackY + 2.0F, 7.0F, current.primary);
        hits.add(new SliderHit(trackX, y + 24.0F, trackW, 26.0F, min, max, setter));
        return y + height;
    }

    private float chipRow(Scheme current, String title, String[] labels, String[] values, String[] selected,
                          int unused, float y, final StringSetter setter) {
        float height = 52.0F;
        ui.text(MusicTypeface.HEADLINE, title, CT_X + 20.0F, y + 18.0F, current.onSurface, UI.ALIGN_LEFT);
        float x = CT_X + 20.0F;
        float chipY = y + 30.0F;
        for (int i = 0; i < labels.length; i++) {
            final String value = values[i];
            float chipW = MusicTypeface.LABEL.width(labels[i]) + 26.0F;
            boolean active = selected.length > 0 && value.equals(selected[0]);
            boolean hovered = inside(x, chipY, chipW, 26.0F);
            ui.rounded(x, chipY, chipW, 26.0F, 13.0F,
                    active ? current.secondaryContainer
                            : Material.withAlpha(current.onSurface, hovered ? 0.10F : 0.04F));
            if (!active) {
                ui.outline(x, chipY, chipW, 26.0F, 13.0F, 1.0F, current.outlineVariant);
            }
            ui.text(MusicTypeface.LABEL_TIGHT, labels[i], x + chipW / 2.0F, chipY + 13.0F,
                    active ? current.onSecondaryContainer : current.onSurfaceVariant, UI.ALIGN_CENTER);
            hits.add(new Hit(x, chipY, chipW, 26.0F, new Runnable() {
                @Override
                public void run() {
                    setter.set(value);
                }
            }));
            x += chipW + 8.0F;
        }
        return y + height + 8.0F;
    }

    // ------------------------------------------------------------------ //
    // Input
    // ------------------------------------------------------------------ //

    private boolean inside(float x, float y, float w, float h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void updateMouse(int guiX, int guiY) {
        this.mouseX = (guiX - drawX) / Math.max(0.01F, drawScale);
        this.mouseY = (guiY - drawY) / Math.max(0.01F, drawScale);
        this.lastMouseX = this.mouseX;
        this.lastMouseY = this.mouseY;
    }

    private float toLocalX(float guiX) {
        return guiX / scale - gx;
    }

    private float toLocalY(float guiY) {
        return guiY / scale - gy;
    }

    @Override
    protected void mouseClicked(int guiX, int guiY, int button) throws IOException {
        if (!interactive()) return;
        super.mouseClicked(guiX, guiY, button);
        updateMouse(guiX, guiY);
        if (button != 0) {
            return;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mouseX, mouseY)) {
                continue;
            }
            if (hit instanceof ProgressHit) {
                draggingProgress = true;
                progressPreview = ((ProgressHit) hit).fraction(mouseX);
            } else if (hit instanceof VolumeHit) {
                draggingVolume = true;
                manager.setVolume(((VolumeHit) hit).fraction(mouseX));
            } else if (hit instanceof SliderHit) {
                draggingSlider = (SliderHit) hit;
                draggingSlider.apply(mouseX);
            } else {
                boolean wasSearch = mouseX >= searchRectX && mouseX < searchRectX + searchRectW
                        && mouseY >= searchRectY && mouseY < searchRectY + searchRectH;
                searchFocused = wasSearch;
                if (wasSearch) {
                    float maxText = searchRectW - 52.0F;
                    float target = mouseX - (searchRectX + 38.0F);
                    int start = searchStart(maxText);
                    int best = start;
                    float bestDelta = Float.MAX_VALUE;
                    for (int c = start; c <= searchText.length(); c++) {
                        float w = SEARCH.width(searchText.substring(start, c));
                        float delta = Math.abs(w - target);
                        if (delta < bestDelta) {
                            bestDelta = delta;
                            best = c;
                        }
                    }
                    searchCaret = best;
                    caretAt = System.currentTimeMillis();
                }
                if (hit.action != null) {
                    hit.action.run();
                }
            }
            return;
        }
        if (lyricOpen && lyricPanel.mouseClicked(mouseX, mouseY)) {
            return;
        }
        volumeOpen = false;
        searchFocused = false;
    }

    @Override
    protected void mouseReleased(int guiX, int guiY, int button) {
        updateMouse(guiX, guiY);
        if (draggingProgress && progressPreview >= 0.0F) {
            long duration = manager.durationMs();
            if (duration > 0L) {
                manager.seek((long) (duration * progressPreview));
            }
        }
        draggingProgress = false;
        if (draggingVolume) {
            manager.commitVolume();
        }
        draggingVolume = false;
        draggingSlider = null;
        progressPreview = -1.0F;
    }

    @Override
    protected void mouseClickMove(int guiX, int guiY, int button, long timeSinceLastClick) {
        updateMouse(guiX, guiY);
        if (draggingProgress) {
            for (Hit hit : hits) {
                if (hit instanceof ProgressHit) {
                    progressPreview = ((ProgressHit) hit).fraction(mouseX);
                    break;
                }
            }
        } else if (draggingVolume) {
            for (Hit hit : hits) {
                if (hit instanceof VolumeHit) {
                    manager.setVolume(((VolumeHit) hit).fraction(mouseX));
                    break;
                }
            }
        } else if (draggingSlider != null) {
            draggingSlider.apply(mouseX);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        if (!interactive()) return;
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        float guiX = Mouse.getEventX() * this.width / (float) this.mc.displayWidth;
        float guiY = this.height - Mouse.getEventY() * this.height / (float) this.mc.displayHeight - 1.0F;
        updateMouse((int) guiX, (int) guiY);
        if (lyricOpen) {
            float amount = wheel / 120.0F;
            if (amount > 0.0F && mouseX > CT_X && mouseX < CT_X + CT_W) {
                // Wheel up scrolls the lyric view back towards the current line.
                lyricPanel.nudge(-1.0F);
            } else if (amount < 0.0F) {
                lyricPanel.nudge(1.0F);
            }
            return;
        }
        if (mouseX < CT_X || mouseX > CT_X + CT_W || mouseY < CT_Y || mouseY > CT_Y + CT_H) {
            return;
        }
        if (!contentScrollable) {
            return;
        }
        float max = Math.max(0.0F, contentHeight - CT_H);
        float target = scroll.target() - wheel / 120.0F * 48.0F;
        scroll.target(Material.clamp(target, 0.0F, max));
    }

    @Override
    protected void keyTyped(char typed, int key) throws IOException {
        if (closing) return;
        if (key == 1) {
            if (searchFocused) {
                searchFocused = false;
                return;
            }
            if (qrOpen) {
                qrOpen = false;
                manager.cancelQrLogin();
                return;
            }
            if (lyricOpen) {
                lyricOpen = false;
                return;
            }
            if (playlistOpen) {
                playlistOpen = false;
                return;
            }
            mc.displayGuiScreen(null);
            return;
        }
        if (!interactive()) return;
        if (searchFocused) {
            if (key == 14) {
                if (searchCaret > 0) {
                    searchText = searchText.substring(0, searchCaret - 1) + searchText.substring(searchCaret);
                    searchCaret--;
                    searchTypedAt = System.currentTimeMillis();
                }
                return;
            }
            if (key == 211) {
                if (searchCaret < searchText.length()) {
                    searchText = searchText.substring(0, searchCaret) + searchText.substring(searchCaret + 1);
                    searchTypedAt = System.currentTimeMillis();
                }
                return;
            }
            if (key == 203) {
                if (searchCaret > 0) {
                    searchCaret--;
                }
                return;
            }
            if (key == 205) {
                if (searchCaret < searchText.length()) {
                    searchCaret++;
                }
                return;
            }
            if (key == 199) {
                searchCaret = 0;
                return;
            }
            if (key == 207) {
                searchCaret = searchText.length();
                return;
            }
            if (key == 28) {
                manager.search(searchText);
                page = Page.SEARCH;
                return;
            }
            if (key == 47 && GuiScreen.isCtrlKeyDown()) {
                String clipboard = GuiScreen.getClipboardString();
                if (clipboard != null) {
                    insertSearch(clipboard.replace('\n', ' '));
                    searchTypedAt = System.currentTimeMillis();
                }
                return;
            }
            if (typed >= 32 && typed != 127) {
                if (searchText.length() < 48) {
                    insertSearch(String.valueOf(typed));
                    searchTypedAt = System.currentTimeMillis();
                    page = Page.SEARCH;
                }
                return;
            }
        }
        if (key == 57) {
            manager.togglePlay();
            return;
        }
        if (key == 200) {
            manager.setVolume(Math.min(1.0F, manager.volume() + 0.05F));
            manager.commitVolume();
            return;
        }
        if (key == 208) {
            manager.setVolume(Math.max(0.0F, manager.volume() - 0.05F));
            manager.commitVolume();
            return;
        }
        if (key == 203) {
            manager.previous();
            return;
        }
        if (key == 205) {
            manager.next();
            return;
        }
        if (key == 211) {
            manager.togglePlay();
            return;
        }
        if (key == 209) {
            lyricOpen = !lyricOpen && manager.current() != null;
            lyricPanel.reset();
        }
    }

    @Override
    public void onGuiClosed() {
        restoreIsland();
        Blur.request();
        manager.saveSettings();
    }

    // ------------------------------------------------------------------ //
    // Hit helpers
    // ------------------------------------------------------------------ //

    interface FloatSetter {
        void set(float value);
    }

    interface BooleanSetter {
        void set(boolean value);
    }

    interface StringSetter {
        void set(String value);
    }

    private static final class ProgressHit extends Hit {
        private final float trackX;
        private final float trackW;

        ProgressHit(float x, float y, float w, float h, float trackX, float trackW) {
            super(x, y, w, h, null);
            this.trackX = trackX;
            this.trackW = trackW;
        }

        float fraction(float mouseX) {
            return Material.clamp((mouseX - trackX) / trackW, 0.0F, 1.0F);
        }
    }

    private static final class VolumeHit extends Hit {
        private final float startX;
        private final float span;

        VolumeHit(float x, float y, float w, float h, float startX, float span) {
            super(x, y, w, h, null);
            this.startX = startX;
            this.span = span <= 0.0F ? 1.0F : span;
        }

        float fraction(float mouseX) {
            return Material.clamp((mouseX - startX) / span, 0.0F, 1.0F);
        }
    }

    private static final class SliderHit extends Hit {
        private final float min;
        private final float max;
        private final FloatSetter setter;

        SliderHit(float x, float y, float w, float h, float min, float max, FloatSetter setter) {
            super(x, y, w, h, null);
            this.min = min;
            this.max = max;
            this.setter = setter;
        }

        void apply(float mouseX) {
            float t = Material.clamp((mouseX - x) / w, 0.0F, 1.0F);
            setter.set(min + (max - min) * t);
        }
    }
}
