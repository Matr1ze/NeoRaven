package xyz.daikirai.gui.onyx;

import xyz.daikirai.module.ClickGuiModule;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.hud.NotificationHistory;
import xyz.daikirai.gui.onyx.hud.HudReturn;
import xyz.daikirai.gui.onyx.hud.NotificationHistory.Record;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.menu.MenuBackground;
import xyz.daikirai.gui.onyx.menu.MenuClock;
import xyz.daikirai.gui.onyx.menu.MenuTheme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.render.NotificationCenterModule;
import xyz.daikirai.music.CoverCache;
import xyz.daikirai.music.MusicManager;
import xyz.daikirai.music.MusicIsland;
import xyz.daikirai.music.MusicTypeface;
import xyz.daikirai.music.Song;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;

/** A full-screen tablet notification shade, sharing the menu clock and the real music session. */
public final class NotificationCenterScreen extends GuiScreen {
    private static final float CARD_W = 330.0F, MUSIC_Y = 172.0F;
    private static final float MINI_SCALE = CARD_W / MusicIsland.MINI_WIDTH;
    private static final float MUSIC_H = MusicIsland.MINI_HEIGHT * MINI_SCALE;
    private static final float LIST_Y = MUSIC_Y + MUSIC_H + 39, ROW_H = 58.0F, ROW_GAP = 6.0F;
    private static final SimpleDateFormat CLOCK = new SimpleDateFormat("HH:mm");
    private static Shader wallpaper;
    private final GuiScreen parent;
    private final UI ui = new UI();
    private final NotificationBackdrop backdrop = new NotificationBackdrop();
    private final Anim reveal = new Anim(0.0F);
    private final Anim artwork = new Anim(0.0F);
    private final Anim musicPresence = new Anim(0), noticePresence = new Anim(0);
    private final Anim clockPosition = new Anim(4), clockSize = new Anim(0.78F);
    private final Anim musicPosition = new Anim(MUSIC_Y), listPosition = new Anim(LIST_Y);
    private float musicY = MUSIC_Y, listY = LIST_Y;
    private boolean musicVisible, noticesVisible, contentInitialized, hudReturning;
    private final NotificationScroll scrolling = new NotificationScroll();
    private final Map<Long, Card> cards = new HashMap<Long, Card>();
    private final List<Card> drawOrder = new ArrayList<Card>();
    private final String draft;
    private final int cursor, selection;
    private boolean closing, dragging, initialDrag, seeking, moved, closeTap;
    private float scale, designW, designH, left, listHeight, scroll, mx, my;
    private float startY, startReveal, pressX, pressY, lastY, velocity;
    private long lastFrame, lastDrag;
    private String coverUrl;
    private ResourceLocation cover;
    private int seed, paletteSeed, pageInk, pageSoft;
    private boolean dark, paletteDark;
    private Scheme palette;
    private Object backgroundKey;
    private ResourceLocation backgroundCover;
    private int backgroundSeed;
    private boolean backgroundDark;
    private float backgroundArtwork = -1, offset;
    private long backgroundAt;
    private float backgroundBlur = -1;

    public NotificationCenterScreen(GuiScreen parent) {
        HudReturn.cancel();
        this.parent = parent;
        GuiTextField field = chatField(parent);
        draft = field == null ? null : field.getText();
        cursor = field == null ? 0 : field.getCursorPosition();
        selection = field == null ? 0 : field.getSelectionEnd();
        reveal.to(1.0F, 440.0F, Ease.EMPHASIZED);
    }

    public NotificationCenterScreen(GuiScreen parent, float startY, float currentY) {
        this(parent);
        this.startY = startY; lastY = currentY;
        dragging = initialDrag = true;
        reveal.set(Material.clamp((currentY - startY) / Math.max(1.0F, parent.height * 0.65F), 0, 1));
        lastDrag = System.nanoTime();
    }

    private static GuiTextField chatField(GuiScreen screen) {
        return screen instanceof GuiChat ? ReflectionHelper.<GuiTextField, GuiChat>getPrivateValue(
                GuiChat.class, (GuiChat) screen, "inputField", "field_146415_a") : null;
    }

    @Override public void initGui() { layout(); lastFrame = 0; }

    private void layout() {
        float fit = Math.min(width / 780.0F, height / 600.0F);
        scale = Math.max(0.1F, Math.min(fit * NotificationCenterModule.scale(), height / 500.0F));
        scale = Math.min(scale, Math.max(0.1F, (width - 24.0F) / CARD_W));
        designW = width / scale; designH = height / scale;
        left = (designW - CARD_W) * 0.5F;
        listHeight = Math.max(62.0F, designH - listY - 40.0F);
        clampScroll(NotificationHistory.snapshot().size());
    }

    public void close() {
        if (closing) return;
        closing = true; dragging = seeking = false;
        reveal.to(0.0F, 310.0F, Ease.EMPHASIZED);
    }

    @Override public void onGuiClosed() { backdrop.dispose(); }

    private void returnToParent() {
        if (mc.theWorld != null && !hudReturning) HudReturn.arm();
        mc.displayGuiScreen(mc.theWorld == null ? new GuiMainMenu() : parent);
        GuiTextField field = chatField(mc.currentScreen);
        if (field != null && draft != null) {
            field.setText(draft); field.setCursorPosition(cursor); field.setSelectionPos(selection);
        }
    }

    @Override public boolean doesGuiPauseGame() { return parent != null && parent.doesGuiPauseGame(); }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (mc.theWorld == null || mc.thePlayer == null) { mc.displayGuiScreen(new GuiMainMenu()); return; }
        ClickGuiModule.sync(); layout();
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 16.0F : Math.min(100.0F, (now - lastFrame) / 1.0E6F);
        lastFrame = now;
        // GuiScreen mouse events run at 20 Hz; sample the pointer every rendered frame while dragging.
        if (dragging || seeking) {
            float x = Mouse.getX() * width / (float) mc.displayWidth;
            float y = height - Mouse.getY() * height / (float) mc.displayHeight - 1;
            if (Mouse.isButtonDown(0)) move(x, y); else release(x, y);
        }
        reveal.update(dt);
        if (closing && reveal.isDone()) { returnToParent(); return; }
        float progress = reveal.value(); offset = (progress - 1.0F) * height;
        mx = mouseX / scale; my = (mouseY - offset) / scale;
        if (progress < 0.999F && parent != null) parent.drawScreen(mouseX, mouseY, partialTicks);

        MusicManager music = MusicManager.get();
        Song song = music.current();
        String url = song == null ? null : song.cover(600);
        if (url == null ? coverUrl != null : !url.equals(coverUrl)) { coverUrl = url; cover = null; }
        cover = coverUrl == null ? null : CoverCache.get(coverUrl);
        if (cover == null && song != null) cover = CoverCache.get(song.cover(96));
        if (cover == null && song != null) cover = CoverCache.get(song.cover(300));
        int sampled = CoverCache.artworkColor(coverUrl);
        if (sampled == 0) sampled = CoverCache.average(coverUrl);
        if (sampled == 0 && song != null) sampled = CoverCache.artworkColor(song.cover(300));
        if (sampled == 0 && song != null) sampled = CoverCache.average(song.cover(300));
        if (sampled == 0 && song != null) sampled = CoverCache.artworkColor(song.cover(96));
        if (sampled == 0 && song != null) sampled = CoverCache.average(song.cover(96));
        music.observePlayback();
        boolean keepMusic = song != null && !music.pausedFor(10000L);
        dark = MenuTheme.dark();
        seed = keepMusic && sampled != 0 ? sampled : MenuBackground.averageColor();
        if (palette == null || seed != paletteSeed || dark != paletteDark) {
            palette = Scheme.artwork(seed, dark); paletteSeed = seed; paletteDark = dark;
        }
        pageInk = palette.onSurface; pageSoft = palette.onSurfaceVariant;
        if (!contentInitialized) {
            // A loaded session starts on its artwork, without a menu-wallpaper crossfade.
            artwork.set(keepMusic ? 1 : 0); contentInitialized = true;
        }
        artwork.to(keepMusic ? 1.0F : 0.0F, 500.0F, Ease.STANDARD);
        artwork.update(dt);
        MusicIsland.tick(dt, true);
        updateContentLayout(dt, keepMusic, !NotificationHistory.snapshot().isEmpty());

        updateBackdrop(now);
        if (closing && progress <= 0.5F && !mc.gameSettings.hideGUI && !mc.gameSettings.showDebugInfo) {
            if (!hudReturning) { HudReturn.arm(); hudReturning = true; }
            HudReturn.shadePass(true);
            try { xyz.daikirai.gui.onyx.hud.HudPass.renderNotificationReturn(partialTicks); }
            finally { HudReturn.shadePass(false); }
        }
        ui.begin(); ui.push(); ui.translate(0, offset); ui.scale(scale, 0, 0);
        ui.push(); ui.scale(1 / scale, 0, 0);
        if (backdrop.ready()) backdrop.draw(ui, width, height, palette.surface, offset * mc.displayHeight / height); else drawWallpaper();
        ui.pop();
        ui.push(); ui.translate(designW * 0.5F - 320.0F * clockSize.value(), clockPosition.value()); ui.scale(clockSize.value(), 0, 0);
        MenuClock.draw(ui, 1.0F, 1.0F, pageInk, pageSoft);
        ui.pop();
        if (musicPresence.value() > 0.001F) drawMusic(music, song);
        drawNotifications(dt);
        ui.rounded(designW * 0.5F - 38, designH - 14, 76, 3.5F, 2, Material.withAlpha(pageInk, 0.7F));
        ui.icon("\ue5cd", designW - 27, 25, 18, pageInk);
        ui.pop(); ui.end();
    }

    /** Retarget from the current geometry so incoming messages and playback changes never snap. */
    private void updateContentLayout(float dt, boolean playing, boolean notices) {
        musicVisible = playing; noticesVisible = notices;
        musicPresence.to(playing ? 1 : 0, 280, Ease.STANDARD); musicPresence.update(dt);
        noticePresence.to(notices ? 1 : 0, 280, Ease.STANDARD); noticePresence.update(dt);
        float bottomMusic = designH - MUSIC_H - 42;
        float large = Math.min(1.35F, (designW - 40) / 440.0F);
        float size = notices ? 0.78F : large;
        float center = playing ? (40 + bottomMusic - 24) * 0.5F : designH * 0.5F;
        float anchor = MenuClock.style() == MenuTheme.CLOCK_ANALOG ? 128 : 144;
        clockSize.to(size, 620, Ease.EMPHASIZED); clockSize.update(dt);
        clockPosition.to(notices ? 4 : center - anchor * size, 620, Ease.EMPHASIZED); clockPosition.update(dt);
        musicPosition.to(notices ? MUSIC_Y : bottomMusic, 620, Ease.EMPHASIZED); musicPosition.update(dt);
        // Reclaim the player area smoothly when playback stops.
        listPosition.to(playing ? LIST_Y : 194, 620, Ease.EMPHASIZED); listPosition.update(dt);
        musicY = musicPosition.value(); listY = listPosition.value();
        listHeight = Math.max(62, designH - listY - 40);
        if (!playing) seeking = false;
    }

    private void updateBackdrop(long now) {
        boolean resized = backdrop.resize();
        float blurRadius = Blur.samplingRadius();
        boolean changed = backgroundKey != MenuBackground.current() || backgroundCover != cover
                || backgroundSeed != seed || backgroundDark != dark || backgroundBlur != blurRadius;
        boolean animated = !artwork.isDone() || artwork.value() < 1 &&
                (MenuBackground.current() == MenuBackground.Style.CLOUDS || MenuBackground.current() == MenuBackground.Style.GRADIENT);
        if (resized || !backdrop.ready() || changed || animated && now - backgroundAt >= 33333333L
                || backgroundArtwork != artwork.value() && artwork.isDone()) {
            backdrop.capture(() -> { ui.begin(); drawWallpaper(); ui.end(); });
            backgroundKey = MenuBackground.current(); backgroundCover = cover;
            backgroundSeed = seed; backgroundDark = dark; backgroundArtwork = artwork.value(); backgroundAt = now; backgroundBlur = blurRadius;
        }
    }

    private void drawWallpaper() {
        MenuBackground.drawRawInto(ui, MenuBackground.current(), width, height);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        if (artwork.value() > 0.001F) drawArtwork(artwork.value());
        ui.gradient(0, 0, width, height, dark ? 0x50081320 : 0x94FFFFFF, dark ? 0x78081320 : 0xB0FFFFFF);
    }

    /** Album art stays centred; its edges dissolve into a gradient sampled from that same cover. */
    private void drawArtwork(float alpha) {
        if (wallpaper == null) wallpaper = Shader.load("onyx_notification_cover", "onyx_clouds");
        ui.alphaPush(alpha);
        int edge = Material.mix(seed, dark ? 0xFF081323 : 0xFFF3F7FB, dark ? 0.38F : 0.45F);
        if (cover == null || !wallpaper.valid()) {
            ui.gradient(0, 0, width, height, seed, edge);
            if (cover != null) {
                float side = Math.min(width, height) * 0.95F;
                ui.imageRounded(cover, (width - side) / 2, (height - side) / 2, side, side, 22, 0xBFFFFFFF);
            }
        } else {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            mc.getTextureManager().bindTexture(cover); GlStateManager.enableTexture2D();
            wallpaper.use(); wallpaper.uniform1i("Sampler0", 0);
            wallpaper.uniform1f("uAspect", width / (float) Math.max(1, height));
            wallpaper.uniform1f("uAlpha", alpha);
            wallpaper.uniformColor("uSeed", seed); wallpaper.uniformColor("uDark", edge);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(0, 0);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(0, height);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(width, height);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(width, 0);
            GL11.glEnd(); Shader.unbind(); GlStateManager.disableTexture2D();
        }
        ui.alphaPop();
    }

    private void glass(float x, float y, float w, float h, float radius) {
        glass(x, y, w, h, radius, 1);
    }

    private void glass(float x, float y, float w, float h, float radius, float alpha) {
        int tint = Material.withAlpha(palette.surfaceContainerHigh, alpha);
        if (!backdrop.glass(ui, x, y, w, h, radius, tint, offset * mc.displayHeight / height))
            ui.rounded(x, y, w, h, radius, Material.withAlpha(palette.surfaceContainerHigh, 0.96F * alpha));
        ui.outline(x, y, w, h, radius, 0.6F, Material.withAlpha(palette.outlineVariant, 0.35F * alpha));
    }

    private void drawMusic(MusicManager music, Song song) {
        glass(left, musicY, CARD_W, MUSIC_H, 23, musicPresence.value());
        ui.alphaPush(musicPresence.value());
        ui.push(); ui.translate(left, musicY);
        if (song != null) {
            MusicIsland.draw(ui, palette, CARD_W, MUSIC_H, 1, MINI_SCALE, cover, true);
        }
        ui.pop(); ui.alphaPop();
    }

    private void drawNotifications(float dt) {
        List<Record> records = NotificationHistory.snapshot();
        clampScroll(records.size());
        scrolling.update(dt); scroll = scrolling.position();
        updateCards(records, dt);
        if (noticePresence.value() < 0.001F) return;
        ui.alphaPush(noticePresence.value());
        ui.text(MusicTypeface.HEADLINE, "通知中心", left + 3, listY - 19, pageInk, UI.ALIGN_LEFT);
        if (!records.isEmpty()) {
            ui.text(Typeface.MICRO, Integer.toString(records.size()), left + 75, listY - 19, pageSoft, UI.ALIGN_LEFT);
            ui.circle(left + CARD_W - 12, listY - 20, 11, Material.withAlpha(pageInk, 0.12F));
            ui.icon("\ue5cd", left + CARD_W - 12, listY - 20, 14, pageInk);
        }
        ui.scissorPush(left - 6, listY, CARD_W + 14, listHeight);
        for (Card card : drawOrder) {
            if (card.alpha < 0.01F) continue;
            float y = card.y;
            Record record = card.record;
            ui.push(); ui.translate(left + card.inset, y); ui.scale(card.scale, 0, 0);
            ui.translate(-left, -y);
            glass(left, y, CARD_W, ROW_H, 18, card.alpha);
            // The stacked plates keep their silhouette; labels fade before they overlap the front card.
            ui.alphaPush(card.alpha * Material.clamp(1 - card.depth * 3, 0, 1));
            int accent = record.accent == null ? record.level.color(palette) : record.accent;
            ui.rounded(left + 12, y + 14, 32, 32, 10, Material.withAlpha(accent, 0.17F));
            ui.icon(record.level.icon, left + 28, y + 30, 19, accent);
            ui.text(MusicTypeface.CARD_TITLE, ui.truncate(MusicTypeface.CARD_TITLE, record.title, CARD_W - 122),
                    left + 54, y + 21, palette.onSurface, UI.ALIGN_LEFT);
            String message = record.message == null ? "" : record.message.replace('\n', ' ');
            ui.text(MusicTypeface.CAPTION, ui.truncate(MusicTypeface.CAPTION, message, CARD_W - 86),
                    left + 54, y + 40, palette.onSurfaceVariant, UI.ALIGN_LEFT);
            ui.text(Typeface.TINY, age(record.time), left + CARD_W - 16, y + 17, palette.onSurfaceVariant, UI.ALIGN_RIGHT);
            ui.icon("\ue5cd", left + CARD_W - 17, y + 41, 12, Material.withAlpha(palette.onSurfaceVariant, 0.7F));
            ui.alphaPop(); ui.pop();
        }
        ui.scissorPop();
        float contentHeight = contentHeight(records.size());
        if (contentHeight > listHeight) {
            float thumb = Math.max(18, listHeight * listHeight / contentHeight);
            ui.rounded(left + CARD_W + 7, listY + (listHeight - thumb) * scroll / (contentHeight - listHeight),
                    2, thumb, 1, Material.withAlpha(pageInk, 0.45F));
        }
        ui.alphaPop();
    }

    private void updateCards(List<Record> records, float dt) {
        drawOrder.clear();
        for (Card card : cards.values()) card.present = false;
        float top = listY + 12, bottom = Math.max(top, listY + listHeight - ROW_H - 12);
        for (int i = 0; i < records.size(); i++) {
            Record record = records.get(i);
            Card card = cards.get(record.id);
            if (card == null) { card = new Card(record); cards.put(record.id, card); }
            card.present = true;
            card.row.to(i * (ROW_H + ROW_GAP), 420, Ease.EMPHASIZED);
            card.appear.to(1, 380, Ease.STANDARD); card.appear.update(dt); card.row.update(dt);
            float raw = top + card.row.value() - scroll;
            float overflow = raw < top ? top - raw : Math.max(0, raw - bottom);
            card.depth = overflow / (ROW_H + ROW_GAP);
            float compression = 1 - (float) Math.exp(-overflow / (ROW_H + ROW_GAP));
            card.y = raw < top ? top - 12 * compression : raw > bottom ? bottom + 12 * compression : raw;
            card.inset = 12 * compression;
            card.scale = 1 - 2 * card.inset / CARD_W;
            card.alpha = card.appear.value() * Material.clamp(3 - card.depth, 0, 1);
            drawOrder.add(card);
        }
        cards.values().removeIf(card -> !card.present);
        // Back plates first; the full-size card always owns both the text and pointer hit area.
        drawOrder.sort(Comparator.comparingDouble((Card card) -> card.depth).reversed());
    }

    private static final class Card {
        final Record record;
        final Anim row = new Anim(0), appear = new Anim(0);
        float y, inset, scale, alpha, depth;
        boolean present;
        Card(Record record) { this.record = record; }
    }

    private float contentHeight(int count) {
        return count == 0 ? 0 : count * (ROW_H + ROW_GAP) - ROW_GAP + 24;
    }

    private static String age(long time) {
        long seconds = Math.max(0, System.currentTimeMillis() - time) / 1000;
        return seconds < 60 ? "刚刚" : seconds < 3600 ? seconds / 60 + "分钟前" : CLOCK.format(new Date(time));
    }
    private void clampScroll(int count) {
        scrolling.clampTo(Math.max(0, contentHeight(count) - listHeight));
        scroll = scrolling.position();
    }

    private void scrollWheel(float steps) {
        scrolling.wheel(steps, Math.max(0, contentHeight(NotificationHistory.snapshot().size()) - listHeight));
    }

    @Override protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE || NotificationCenterModule.boundKey(key)) {
            NotificationCenterModule.consumeBind(); close();
        }
    }

    @Override public void handleMouseInput() throws IOException {
        float x = Mouse.getEventX() * width / (float) mc.displayWidth;
        float y = height - Mouse.getEventY() * height / (float) mc.displayHeight - 1.0F;
        int button = Mouse.getEventButton();
        if (closing) return;
        if (Mouse.getEventButtonState() && NotificationCenterModule.boundMouse(button)) {
            NotificationCenterModule.consumeBind(); close(); return;
        }
        int wheel = Mouse.getEventDWheel();
        if (noticesVisible && wheel != 0 && !dragging && !seeking && x / scale >= left && x / scale <= left + CARD_W + 14
                && (y - offset) / scale >= listY && (y - offset) / scale < listY + listHeight) {
            scrollWheel(wheel / 120.0F);
        }
        if (button == 0) {
            if (Mouse.getEventButtonState()) press(x, y); else release(x, y);
        } else if (Mouse.isButtonDown(0)) move(x, y);
    }

    private void press(float x, float y) {
        if (reveal.value() < 0.98F) return;
        mx = x / scale; my = y / scale;
        pressX = mx; pressY = my; moved = false;
        closeTap = mx > designW - 48 && my < 48 || my > designH - 28;
        if (closeTap && my < 48) return;
        if (musicVisible && mx >= left + MusicIsland.PROGRESS_X * MINI_SCALE && mx <= left + CARD_W - MusicIsland.PROGRESS_X * MINI_SCALE
                && Math.abs(my - (musicY + MusicIsland.PROGRESS_Y * MINI_SCALE)) < 10 * MINI_SCALE
                && MusicManager.get().current() != null && MusicManager.get().durationMs() > 0) {
            seeking = true; seek(mx); return;
        }
        // Blank space and the bottom handle are the upward-dismiss gesture surface.
        boolean onMusic = musicVisible && mx >= left && mx <= left + CARD_W && my >= musicY && my <= musicY + MUSIC_H;
        boolean onNotices = noticesVisible && mx >= left && mx <= left + CARD_W && my >= listY - 33 && my <= listY + listHeight;
        if (!onMusic && !onNotices) {
            dragging = true; initialDrag = false; startY = lastY = y;
            startReveal = reveal.value(); lastDrag = System.nanoTime(); velocity = 0;
        }
    }

    private void move(float x, float y) {
        if (seeking) { seek(x / scale); return; }
        if (Math.abs(x / scale - pressX) + Math.abs(y / scale - pressY) > 8) moved = true;
        if (!dragging) return;
        long now = System.nanoTime();
        if (Math.abs(y - lastY) > 0.01F) {
            velocity = (y - lastY) / Math.max(1, (now - lastDrag) / 1.0E6F);
            lastDrag = now; lastY = y;
        }
        reveal.set(Material.clamp((initialDrag ? 0 : startReveal)
                + (y - startY) / Math.max(1, height * 0.65F), 0, 1));
    }

    private void release(float x, float y) {
        if (seeking) { seek(x / scale); seeking = false; return; }
        if (dragging) {
            move(x, y);
            if (!initialDrag && !moved && closeTap) { close(); return; }
            boolean open = initialDrag ? reveal.value() >= 0.18F : reveal.value() > 0.78F;
            if (System.nanoTime() - lastDrag < 120000000L && Math.abs(velocity) > 0.4F) open = velocity > 0;
            dragging = initialDrag = false;
            if (open) reveal.to(1, 340, Ease.EMPHASIZED); else close();
            return;
        }
        if (closing || moved || reveal.value() < 0.98F) return;
        mx = x / scale; my = y / scale;
        if (Math.abs(mx - pressX) + Math.abs(my - pressY) > 8) return;
        if (mx > designW - 48 && my < 48 || my > designH - 28) { close(); return; }
        if (mx < left || mx > left + CARD_W) return;
        MusicManager music = MusicManager.get();
        if (musicVisible && my >= musicY && my <= musicY + MUSIC_H) {
            float xMini = (mx - left) / MINI_SCALE, yMini = (my - musicY) / MINI_SCALE;
            if (music.current() != null) MusicIsland.click(xMini, yMini);
            else if (Math.abs(xMini - (MusicIsland.MINI_WIDTH - 30)) < 20
                    && Math.abs(yMini - MusicIsland.CONTROLS_Y) < 20) {
                mc.displayGuiScreen(new xyz.daikirai.music.MusicScreen());
            }
        } else if (noticesVisible && my > listY - 33 && my < listY - 6 && mx > left + CARD_W - 32) {
            NotificationHistory.clear(); scrolling.reset(); scroll = 0;
        } else if (noticesVisible && my >= listY && my < listY + listHeight) {
            for (int i = drawOrder.size() - 1; i >= 0; i--) {
                Card card = drawOrder.get(i);
                if (card.alpha < 0.1F) continue;
                float localX = (mx - left - card.inset) / card.scale;
                float localY = (my - card.y) / card.scale;
                if (localX < 0 || localX > CARD_W || localY < 0 || localY > ROW_H) continue;
                if (card.depth < 0.2F && localX > CARD_W - 34 && localY >= 30 && localY <= 54)
                    NotificationHistory.dismiss(card.record.id);
                break;
            }
        }
    }

    private void seek(float x) {
        MusicManager music = MusicManager.get();
        float fraction = Material.clamp(((x - left) / MINI_SCALE - MusicIsland.PROGRESS_X) / (MusicIsland.MINI_WIDTH - 2 * MusicIsland.PROGRESS_X), 0, 1);
        music.seek((long) (music.durationMs() * fraction));
    }
}
