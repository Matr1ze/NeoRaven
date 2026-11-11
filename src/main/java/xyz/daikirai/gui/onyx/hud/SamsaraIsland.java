package xyz.daikirai.gui.onyx.hud;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.BlockPos;
import org.lwjgl.input.Mouse;
import xyz.daikirai.client.Hypixel;
import xyz.daikirai.client.RenderDispatcher;
import xyz.daikirai.gui.onyx.Text;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.player.AutoSwapModule;
import xyz.daikirai.module.player.BedAuraModule;
import xyz.daikirai.module.player.Scaffold2Module;
import xyz.daikirai.module.render.WatermarkModule;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Samsara's Dynamic Island, ported as the second {@code Watermark} island style.
 *
 * <p>Upstream splits this into four classes ({@code DynamicIslandState} for the geometry and the
 * spring, {@code DynamicIslandStatus} for the idle row, {@code DynamicIslandPainter} for the
 * artwork, {@code DynamicIslandManager} for the sources). They are folded into this one class
 * because the client's {@link UI} already plays the role of the NanoVG surface and the sources are
 * a few lines each.</p>
 *
 * <p>Kept 1:1: the shell measurements (23px idle pill, 36px notice row, 50px scaffold panel, 48px
 * Bed Aura panel, 8.5px row radius that turns into a capsule when idle), the analytical damped
 * spring driving width, height and top, the 200ms switch fade, the 800ms notice hold, the dark
 * glass body with its stepped exponential drop shadow, the 8px status row
 * ({@code client · user · ping to server · fps}), the module-toggle notice with its switch, and the
 * scaffold / break-progress panels with the accent bar.</p>
 *
 * <p>Translated to this client: NanoVG paths became Material Icons glyphs (the client mark uses the
 * raven texture the island already ships), the hard-coded lavender palette became the active
 * Material You accent mixed towards white so it stays legible on dark glass, {@code Opai} became
 * the client name, and the sources became this client's own modules.</p>
 */
public final class SamsaraIsland {

    // ------------------------------------------------------------------ //
    // Ported geometry (DynamicIslandState)
    // ------------------------------------------------------------------ //
    private static final float IDLE_HEIGHT = 23.0F;
    private static final float ROW_HEIGHT = 36.0F;
    private static final float SCAFFOLD_HEIGHT = 50.0F;
    private static final float BREAKING_HEIGHT = 48.0F;
    private static final float SCAFFOLD_TEXT_X = 36.0F;
    private static final float SCAFFOLD_RIGHT_PADDING = 8.0F;
    private static final float RADIUS = 8.5F;
    private static final float NOTICE_TEXT_X = 36.0F;
    private static final float NOTICE_RIGHT_PADDING = 5.5F;
    private static final int MAX_NOTICES = 3;
    private static final long FADE_MS = 200L;
    /** How long a module-toggle notice stays on the pill (upstream {@code TOGGLE_MS}). */
    private static final long TOGGLE_MS = 800L;
    private static final long NOTICE_LIVE_MS = 500L;
    private static final double MORPH_DECAY = 8.7D;
    private static final double MORPH_FREQUENCY = 8.0D;
    private static final float SHADOW_EXTENT = 8.0F;
    private static final float SHADOW_STEP = 0.5F;

    // ------------------------------------------------------------------ //
    // Ported status row (DynamicIslandStatus)
    // ------------------------------------------------------------------ //
    private static final float INSET = 9.0F;
    private static final float RIGHT_INSET = 12.5F;
    private static final float ICON_SIZE = 10.5F;
    private static final float ICON_GAP = 3.5F;
    private static final int FOREGROUND = 0xFFE4E1E6;
    private static final int ONLINE = 0xFF80C810;
    private static final int PING_ORANGE = 0xFFC87D28;
    private static final int PING_RED = 0xFFCA3E2E;

    /** Samsara's {@code HudGlassStyle.BODY}: 60% black over the blurred scene. */
    private static final int BODY = 0x98000000;
    /**
     * The frost shader blends {@code blurred -> tint.rgb} by the tint's alpha, so the opacity lives
     * in the colour (opaque black) and the 60% body alpha becomes the coverage. Passing a
     * translucent tint instead lets the sharp scene through and the pill washes out to light grey.
     */
    private static final int BODY_FROST = 0xFF000000;
    private static final float FROST_COVERAGE = 0.596F;

    // Material Icons stand in for the vector paths upstream drew by hand.
    private static final String USER_ICON = "\ue7fd";
    private static final String LINK_ICON = "\ue157";
    private static final String REFRESH_ICON = "\ue5d5";
    private static final String BED_ICON = "\ue53a";
    private static final String CUBE_ICON = "\ue1bd";
    private static final String CHECK_ICON = "\ue86c";
    private static final String WARN_ICON = "\ue002";
    private static final String INFO_ICON = "\ue88e";

    /** Shown after the logo mark, where upstream printed the client name. */
    private static final String CLIENT_NAME = "NeoRaven";

    /** 8 / 9 / 10px, matching the ported font sizes. */
    private static final Typeface.Style STATUS_FONT = Typeface.TINY;
    private static final Typeface.Style DETAIL_FONT = Typeface.MICRO;
    private static final Typeface.Style TITLE_FONT = Typeface.CAPTION;

    private static final float MARGIN = 10.0F;
    private static final float EDGE = 20.0F;
    /** The Samsara island has no scale option; it is drawn at its design size, as upstream. */
    private static final float ISLAND_SCALE = 1.0F;
    /** How much higher the pill rides while the scaffold panel is up (upstream: 15 vs 21). */
    private static final float SCAFFOLD_LIFT = 6.0F;

    private enum Icon { TOGGLE, SUCCESS, WARNING, INFO, SCAFFOLD, BREAKING }

    private final UI ui = new UI();
    private final Anim visible = new Anim(0.0F);
    private final List<Notice> notices = new ArrayList<Notice>();
    private final List<Row> rows = new ArrayList<Row>();
    private final List<Part> idleParts = new ArrayList<Part>();
    private final Morph width = new Morph();
    private final Morph height = new Morph();
    private final Morph position = new Morph();
    private final Map<Module, Boolean> states = new IdentityHashMap<Module, Boolean>();

    private long lastFrame;
    private long now;
    private boolean wasIdle = true;

    private String username = "Player";
    private String server;
    private int ping = -1;
    private int fps;
    private float idleWidth = 60.0F;
    private boolean idleVisible = true;

    private float designWidth = 60.0F;
    private float designHeight = IDLE_HEIGHT;
    private float designRadius = IDLE_HEIGHT / 2.0F;
    private float renderScale = 1.0F;

    private float x;
    private float y;
    private float w;
    private float h;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;

    // ------------------------------------------------------------------ //
    // Sources
    // ------------------------------------------------------------------ //

    private void sampleStatus() {
        Minecraft mc = Minecraft.getMinecraft();
        username = mc.getSession() == null || mc.getSession().getUsername() == null
                ? "Player" : mc.getSession().getUsername();
        fps = Minecraft.getDebugFPS();
        ping = -1;
        if (mc.thePlayer != null && mc.getNetHandler() != null) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            if (info != null) {
                ping = Math.max(0, info.getResponseTime());
            }
        }
        if (mc.isSingleplayer() || mc.getCurrentServerData() == null) {
            // Upstream prints the literal word "singleplayer" for a local world and drops the ping
            // segment there.
            server = null;
        } else {
            server = Hypixel.isHypixelServer() ? "mc.hypixel.net" : mc.getCurrentServerData().serverIP;
            if (server == null || server.isEmpty()) {
                server = "server";
            }
        }
    }

    /** Port of {@code updateScaffold}: blocks left plus the walk speed upstream labels block/s. */
    private void sampleScaffold() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            remove("scaffold");
            return;
        }
        int blocks = -1;
        Module scaffold = ModuleManager.get("Scaffold");
        if (scaffold != null && scaffold.isEnabled()) {
            blocks = AutoSwapModule.countBlocks();
        } else if (Scaffold2Module.instance != null && Scaffold2Module.instance.isEnabled()) {
            blocks = Scaffold2Module.blockCount();
        }
        if (blocks < 0) {
            remove("scaffold");
            return;
        }
        double speed = RenderDispatcher.bps(mc.thePlayer, 2);
        String detail = blocks + " blocks left - " + round1(speed) + " block/s";
        postScaffold(detail, Math.min(1.0F, blocks / 100.0F));
    }

    /** Port of {@code updateBedAura}: the block Bed Aura is digging and its break progress. */
    private void sampleBedAura() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            remove("bed-aura");
            return;
        }
        Module bed = ModuleManager.get("BedAura");
        BlockPos target = BedAuraModule.target;
        if (bed == null || !bed.isEnabled() || target == null) {
            remove("bed-aura");
            return;
        }
        Block block = mc.theWorld.getBlockState(target).getBlock();
        String name = block == null ? "Block" : block.getLocalizedName();
        if (name == null || name.isEmpty()) {
            name = "Block";
        }
        postBreaking(name, Material.clamp(BedAuraModule.progress, 0.0F, 1.0F));
    }

    private static String round1(double value) {
        return String.valueOf(Math.round(value * 10.0D) / 10.0D);
    }

    /** Port of {@code DynamicIslandManager.onModuleToggled}, driven by the modules' own state. */
    private void detectToggles(WatermarkModule module) {
        long duration = TOGGLE_MS;
        for (Module candidate : ModuleManager.modules) {
            if (!Island.trackable(candidate, module)) {
                continue;
            }
            boolean enabled = candidate.isEnabled();
            Boolean before = states.put(candidate, Boolean.valueOf(enabled));
            if (before == null || before.booleanValue() == enabled) {
                continue;
            }
            // Upstream splits camelCase names ("AutoRodNew" -> "Auto Rod New").
            String label = Text.strip(candidate.getName()).replaceAll("(?<=[a-z])(?=[A-Z])", " ");
            post("module:" + candidate.getName(), "Module Toggled", label + " has been ",
                    enabled ? "Enabled" : "Disabled", Icon.TOGGLE, enabled, 0.0F, duration);
        }
    }

    // ------------------------------------------------------------------ //
    // Notice bookkeeping (DynamicIslandState)
    // ------------------------------------------------------------------ //

    private void postScaffold(String detail, float progress) {
        for (Iterator<Notice> it = notices.iterator(); it.hasNext(); ) {
            if (!"scaffold".equals(it.next().key)) {
                it.remove();
            }
        }
        post("scaffold", "Scaffold Toggled", detail, "", Icon.SCAFFOLD, true, progress, NOTICE_LIVE_MS);
    }

    private void postBreaking(String blockName, float progress) {
        String title = "Breaking " + blockName;
        for (Iterator<Notice> it = notices.iterator(); it.hasNext(); ) {
            Notice notice = it.next();
            // A new block retargets the same shell without carrying the old block's width.
            if (!"bed-aura".equals(notice.key) || !title.equals(notice.title)) {
                it.remove();
            }
        }
        post("bed-aura", title, "Break Progress: " + Math.round(progress * 100.0F) + "%", "",
                Icon.BREAKING, true, progress, NOTICE_LIVE_MS);
    }

    private void remove(String key) {
        for (Iterator<Notice> it = notices.iterator(); it.hasNext(); ) {
            if (key.equals(it.next().key)) {
                it.remove();
            }
        }
    }

    private void post(String key, String title, String detail, String status, Icon icon,
                      boolean enabled, float progress, long duration) {
        notices.removeIf(notice -> now >= notice.expiresAt);
        Notice notice = null;
        for (Notice candidate : notices) {
            if (candidate.key.equals(key) && candidate.title.equals(title)) {
                notice = candidate;
                break;
            }
        }
        if (notice == null) {
            notice = new Notice(key, now);
            notices.add(notice);
        }
        notice.title = title;
        notice.detail = detail;
        notice.status = status;
        notice.icon = icon;
        notice.enabled = enabled;
        notice.progress = progress;
        notice.expiresAt = now + duration;
        notice.toggle.target(enabled ? 1.0F : 0.0F, now);
        while (notices.size() > MAX_NOTICES) {
            notices.remove(0);
        }
    }

    // ------------------------------------------------------------------ //
    // Layout (DynamicIslandState.frame)
    // ------------------------------------------------------------------ //

    private void layout(WatermarkModule module) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        // Logical space: the shell is laid out at design size and then scaled, so the room a row
        // may use shrinks as the HUD scale grows (upstream divides the GUI width by the scale).
        float scale = ISLAND_SCALE;
        float available = Math.max(1.0F, resolution.getScaledWidth() / scale - 16.0F);
        layoutIdle(available);

        rows.clear();
        notices.removeIf(notice -> now >= notice.expiresAt);
        float occupied = 0.0F;
        float largestWidth = 0.0F;
        for (Notice notice : notices) {
            boolean progressPanel = notice.icon == Icon.SCAFFOLD || notice.icon == Icon.BREAKING;
            float fullHeight = notice.icon == Icon.BREAKING ? BREAKING_HEIGHT
                    : progressPanel ? SCAFFOLD_HEIGHT : ROW_HEIGHT;
            rows.add(new Row(notice, occupied, fullHeight, notice.toggle.value(now)));
            float textWidth = notice.icon == Icon.BREAKING
                    ? Math.max(DETAIL_FONT.width(notice.title), STATUS_FONT.width("Break Progress: 100%"))
                    : Math.max(TITLE_FONT.width(notice.title), DETAIL_FONT.width(notice.detail)
                    + DETAIL_FONT.width(notice.status)
                    + (notice.status.isEmpty() ? 0.0F : DETAIL_FONT.width("!")));
            // Mining reserves all three percentage digits before the first frame.
            float contentWidth = progressPanel
                    ? SCAFFOLD_TEXT_X + textWidth + SCAFFOLD_RIGHT_PADDING
                    : NOTICE_TEXT_X + textWidth + NOTICE_RIGHT_PADDING;
            notice.contentWidth = Math.max(notice.contentWidth, contentWidth);
            largestWidth = Math.max(largestWidth, Math.min(available, notice.contentWidth));
            occupied += fullHeight;
        }

        idleVisible = rows.isEmpty();
        float targetWidth = Math.min(available, idleVisible ? idleWidth : largestWidth);
        float targetHeight = Material.clamp(occupied, IDLE_HEIGHT, MAX_NOTICES * ROW_HEIGHT);

        // Fixed row coordinates keep the title, status and punctuation together during every frame.
        double impulse = idleVisible != wasIdle ? (idleVisible ? 5.5D : 7.25D) : 0.0D;
        float animatedWidth = Material.clamp(width.value(targetWidth, now, impulse), 1.0F, available);
        float animatedHeight = Math.max(1.0F, height.value(targetHeight, now, impulse));
        boolean scaffold = false;
        for (Row row : rows) {
            if (row.notice.icon == Icon.SCAFFOLD) {
                scaffold = true;
                break;
            }
        }
        designWidth = animatedWidth;
        designHeight = animatedHeight;
        designRadius = idleVisible ? animatedHeight / 2.0F : RADIUS;
        wasIdle = idleVisible;

        // Interpolated width can peak between the endpoints of the morph; never let it leave the
        // screen (the same fit the NeoRaven island applies).
        renderScale = Math.min(scale, Math.min((resolution.getScaledWidth() - EDGE) / animatedWidth,
                (resolution.getScaledHeight() - EDGE) / animatedHeight));
        w = animatedWidth * renderScale;
        h = animatedHeight * renderScale;
        float maxX = Math.max(0.0F, resolution.getScaledWidth() - w - EDGE);
        float maxY = Math.max(0.0F, resolution.getScaledHeight() - h - EDGE);
        float percentX = Material.clamp(module.samsaraPositionX(), 0.0F, 100.0F) / 100.0F;
        float percentY = Material.clamp(module.samsaraPositionY(), 0.0F, 100.0F) / 100.0F;
        float baseY = MARGIN + maxY * percentY;
        x = MARGIN + maxX * percentX;
        y = position.value(baseY + (scaffold ? 0.0F : SCAFFOLD_LIFT), now, impulse);
    }

    private void layoutIdle(float available) {
        idleParts.clear();
        int accent = accent();
        float x = INSET;
        x = mark(x, accent);
        x = label(x, CLIENT_NAME, accent);
        boolean singleplayer = server == null;
        String connection = singleplayer ? "singleplayer" : server;
        float fixed = INSET + RIGHT_INSET + 4.0F * (ICON_SIZE + ICON_GAP)
                + 3.0F * (2.0F + STATUS_FONT.width("  \u00b7  ")) + STATUS_FONT.width(CLIENT_NAME)
                + STATUS_FONT.width(Math.max(0, fps) + " fps")
                + (singleplayer ? 0.0F : STATUS_FONT.width(pingText() + " to "));
        float remaining = Math.max(0.0F, available - fixed);
        float userWidth = STATUS_FONT.width(username);
        float connectionWidth = STATUS_FONT.width(connection);
        float userBudget = userWidth + connectionWidth <= remaining ? userWidth
                : Math.min(userWidth, Math.max(remaining - connectionWidth, remaining * 0.4F));
        String user = fit(STATUS_FONT, username, userBudget);
        connection = fit(STATUS_FONT, connection,
                Math.max(0.0F, remaining - STATUS_FONT.width(user)));
        x = separator(x);
        x = icon(x, USER_ICON, FOREGROUND);
        x = label(x, user, FOREGROUND);
        x = separator(x);
        x = icon(x, LINK_ICON, singleplayer ? FOREGROUND : pingColor());
        if (!singleplayer) {
            x = label(x, pingText(), pingColor());
            x = label(x, " to ", FOREGROUND);
        }
        x = label(x, connection, FOREGROUND);
        x = separator(x);
        x = icon(x, REFRESH_ICON, FOREGROUND);
        x = label(x, Math.max(0, fps) + " fps", FOREGROUND);
        idleWidth = x + RIGHT_INSET;
    }

    private String pingText() {
        return ping < 0 ? "...ms" : ping + "ms";
    }

    private int pingColor() {
        return ping < 0 ? FOREGROUND : ping < 100 ? ONLINE : ping <= 400 ? PING_ORANGE : PING_RED;
    }

    private float mark(float x, int color) {
        idleParts.add(Part.mark(x, color));
        return x + ICON_SIZE + ICON_GAP;
    }

    private float icon(float x, String glyph, int color) {
        idleParts.add(Part.icon(glyph, color, x));
        return x + ICON_SIZE + ICON_GAP;
    }

    private float label(float x, String text, int color) {
        float width = STATUS_FONT.width(text);
        idleParts.add(Part.text(text, color, x, width));
        return x + width;
    }

    private float separator(float x) {
        return label(x + 1.25F, "  \u00b7  ", FOREGROUND) + 0.75F;
    }

    // ------------------------------------------------------------------ //
    // Painting (DynamicIslandPainter)
    // ------------------------------------------------------------------ //

    /** Draws (and updates) the island; {@code preview} keeps it visible for the position editor. */
    public void render(WatermarkModule module, boolean preview) {
        if (module == null) {
            return;
        }
        float delta = tick();
        boolean active = preview || module.isEnabled();
        visible.to(active ? 1.0F : 0.0F, active ? 180.0F : 160.0F, Ease.STANDARD);
        visible.update(delta);
        if (active) {
            now = System.nanoTime() / 1000000L;
            sampleStatus();
            detectToggles(module);
            sampleScaffold();
            sampleBedAura();
            layout(module);
        } else {
            rows.clear();
            notices.clear();
            states.clear();
            wasIdle = true;
            width.clear();
            height.clear();
            position.clear();
        }
        float fade = preview ? 1.0F : visible.value();
        if (fade <= 0.002F) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (!preview) {
            handleDrag(module, mc);
        }
        GlStateManager.pushMatrix();
        mc.entityRenderer.setupOverlayRendering();
        ui.begin();
        ui.push();
        try {
            if (fade > 0.01F) {
                Blur.prepare();
            }
            ui.alphaPush(fade);
            ui.translate(x, y + (1.0F - fade) * 4.0F);
            ui.scale(renderScale, 0.0F, 0.0F);
            drawShadow(designWidth, designHeight, designRadius);
            if (Blur.ready()) {
                // Upstream paints the body over the blurred scene only; the plain fill would stack a
                // second 60% black on top of it.
                ui.frost(0.0F, 0.0F, designWidth, designHeight, designRadius, BODY_FROST, FROST_COVERAGE);
            } else {
                ui.rounded(0.0F, 0.0F, designWidth, designHeight, designRadius, BODY);
            }
            ui.scissorPush(2.0F, 1.0F, Math.max(0.0F, designWidth - 4.0F), designHeight - 2.0F);
            drawContent(ui);
            ui.scissorPop();
            ui.alphaPop();
        } finally {
            ui.pop();
            ui.end();
            GlStateManager.popMatrix();
            restoreHudState();
        }
    }

    /** The shell artwork alone, for the music window's open/close morph endpoint. */
    public void snapshot(UI target) {
        float dw = Math.max(1.0F, designWidth);
        float dh = Math.max(1.0F, designHeight);
        target.rounded(0.0F, 0.0F, dw, dh, designRadius, BODY);
        drawContent(target);
    }

    private void drawContent(UI surface) {
        int accent = accent();
        if (idleVisible) {
            drawIdle(surface, accent);
        }
        for (Row row : rows) {
            surface.scissorPush(3.0F, row.y, Math.max(0.0F, designWidth - 6.0F), row.height);
            drawRow(surface, row, accent);
            surface.scissorPop();
        }
    }

    private void drawIdle(UI surface, int accent) {
        float centerY = IDLE_HEIGHT / 2.0F;
        boolean accentNext = false;
        for (Part part : idleParts) {
            boolean accentPart = part.mark || accentNext;
            accentNext = part.mark;
            int color = accentPart ? accent : part.color;
            if (part.mark) {
                if (Island.prepareLogo()) {
                    surface.image(Island.logoResource(), part.x, centerY - ICON_SIZE / 2.0F,
                            ICON_SIZE, ICON_SIZE * 0.84F, 0.0F, 0.0F, 1.0F, 1.0F, color);
                }
            } else if (part.glyph != null) {
                surface.icon(part.glyph, part.x + part.width / 2.0F, centerY, part.width, color);
            } else {
                surface.text(STATUS_FONT, part.text, part.x, centerY, color, UI.ALIGN_LEFT);
            }
        }
    }

    private void drawRow(UI surface, Row row, int accent) {
        Notice notice = row.notice;
        if (notice.icon == Icon.SCAFFOLD || notice.icon == Icon.BREAKING) {
            drawProgressPanel(surface, row, accent);
            return;
        }
        drawIcon(surface, row, accent);
        float textWidth = Math.max(0.0F, notice.contentWidth - NOTICE_TEXT_X - NOTICE_RIGHT_PADDING);
        surface.text(TITLE_FONT, fit(TITLE_FONT, notice.title, textWidth), NOTICE_TEXT_X,
                row.y + 13.5F, 0xFFFFFFFF, UI.ALIGN_LEFT);
        float punctuation = notice.status.isEmpty() ? 0.0F : DETAIL_FONT.width("!");
        float lineWidth = Math.max(0.0F, textWidth - punctuation);
        String status = fit(DETAIL_FONT, notice.status, lineWidth);
        float remaining = Math.max(0.0F, lineWidth - DETAIL_FONT.width(status));
        boolean truncated = DETAIL_FONT.width(notice.detail) > remaining + 0.01F;
        float gap = truncated && !status.isEmpty() ? DETAIL_FONT.width(" ") : 0.0F;
        String prefix = fit(DETAIL_FONT, notice.detail, Math.max(0.0F, remaining - gap));
        if (!prefix.isEmpty() && gap > 0.0F) {
            prefix += " ";
        }
        surface.text(DETAIL_FONT, prefix, NOTICE_TEXT_X, row.y + 24.75F, 0xFFFFFFFF, UI.ALIGN_LEFT);
        // Drawing returns no width: measure the prefix once, independent of screen X.
        float statusX = NOTICE_TEXT_X + DETAIL_FONT.width(prefix);
        surface.text(DETAIL_FONT, status, statusX, row.y + 24.75F,
                notice.enabled ? ONLINE : PING_RED, UI.ALIGN_LEFT);
        if (!status.isEmpty()) {
            surface.text(DETAIL_FONT, "!", statusX + DETAIL_FONT.width(status), row.y + 24.75F,
                    0xFFFFFFFF, UI.ALIGN_LEFT);
        }
    }

    private void drawProgressPanel(UI surface, Row row, int accent) {
        Notice notice = row.notice;
        boolean breaking = notice.icon == Icon.BREAKING;
        int ink = accent;
        float tileSize = breaking ? 27.0F : 28.0F;
        surface.rounded(4.0F, row.y + 5.0F, tileSize, tileSize, 7.0F, 0xC8141616);
        surface.icon(breaking ? BED_ICON : CUBE_ICON, 4.0F + tileSize / 2.0F,
                row.y + 5.0F + tileSize / 2.0F, breaking ? 17.0F : 18.0F, ink);
        float textWidth = Math.max(0.0F, notice.contentWidth - SCAFFOLD_TEXT_X - SCAFFOLD_RIGHT_PADDING);
        if (breaking) {
            surface.text(DETAIL_FONT, fit(DETAIL_FONT, notice.title, textWidth), SCAFFOLD_TEXT_X,
                    row.y + 12.0F, ink, UI.ALIGN_LEFT);
            surface.text(STATUS_FONT, fit(STATUS_FONT, notice.detail, textWidth), SCAFFOLD_TEXT_X,
                    row.y + 24.0F, 0xFFFFFFFF, UI.ALIGN_LEFT);
        } else {
            surface.text(TITLE_FONT, fit(TITLE_FONT, notice.title, textWidth), SCAFFOLD_TEXT_X,
                    row.y + 13.0F, ink, UI.ALIGN_LEFT);
            surface.text(DETAIL_FONT, fit(DETAIL_FONT, notice.detail, textWidth), SCAFFOLD_TEXT_X,
                    row.y + 25.0F, 0xFFFFFFFF, UI.ALIGN_LEFT);
        }
        float barWidth = Math.max(0.0F, designWidth - 8.0F);
        float barY = row.y + (breaking ? 35.0F : 37.0F);
        float barHeight = breaking ? 7.5F : 8.0F;
        surface.rounded(4.0F, barY, barWidth, barHeight, barHeight / 2.0F,
                breaking ? 0x7034343D : 0xFF343636);
        float filled = barWidth * Material.clamp(notice.progress, 0.0F, 1.0F);
        if (filled > 0.0F) {
            surface.rounded(4.0F, barY, filled, barHeight, Math.min(barHeight / 2.0F, filled / 2.0F),
                    progressInk(accent));
        }
    }

    private void drawIcon(UI surface, Row row, int accent) {
        Notice notice = row.notice;
        float y = row.y;
        if (notice.icon == Icon.TOGGLE) {
            float t = row.toggle;
            surface.rounded(5.0F, y + 10.0F, 26.0F, 16.0F, 8.0F, Material.mix(0xFF938F99, accent, t));
            surface.rounded(6.0F, y + 11.0F, 24.0F, 14.0F, 7.0F, Material.mix(0xFF36343B, accent, t));
            float knob = 8.0F + 3.5F * t;
            // Upstream's hudKnob is a dark ink, so the "on" switch reads as lavender track + dark
            // knob rather than two pale shapes on the light glass.
            surface.rounded(8.5F + 8.5F * t, y + 13.5F - 1.25F * t, knob, knob, knob / 2.0F,
                    Material.mix(0xFF858488, 0xFF5B5F7C, t));
            return;
        }
        int tile = notice.icon == Icon.SUCCESS ? 0xC85F8F50
                : notice.icon == Icon.WARNING ? 0xC88F5050 : 0xC8307593;
        surface.rounded(7.0F, y + 8.0F, 24.0F, 24.0F, 7.0F, tile);
        String glyph = notice.icon == Icon.SUCCESS ? CHECK_ICON
                : notice.icon == Icon.WARNING ? WARN_ICON : INFO_ICON;
        surface.icon(glyph, 19.0F, y + 20.0F, 15.0F, 0xFFFFFFFF);
    }

    private void drawShadow(float w, float h, float radius) {
        for (float spread = SHADOW_EXTENT; spread > 0.0F; spread -= SHADOW_STEP) {
            int layer = shadowLayer(spread);
            if ((layer >>> 24) == 0) {
                continue;
            }
            ui.outline(-spread, -spread, w + spread * 2.0F, h + spread * 2.0F, radius + spread,
                    SHADOW_STEP, layer);
        }
    }

    private static float shadowCoverage(float distance) {
        float normalized = Math.max(0.0F, distance) / 2.8F;
        return 0.5F * (float) Math.exp(-0.78F * normalized - 0.5F * normalized * normalized);
    }

    private static int shadowLayer(float spread) {
        float previous = shadowCoverage(spread + SHADOW_STEP);
        float desired = shadowCoverage(spread);
        float alpha = (desired - previous) / (1.0F - previous);
        return Math.round(255.0F * Material.clamp(alpha, 0.0F, 1.0F)) << 24;
    }

    /** The accent stays legible on the dark glass in both the light and the dark palette. */
    private static int accent() {
        return Material.mix(Theme.scheme().primary, 0xFFFFFFFF, 0.40F);
    }

    private static int progressInk(int accent) {
        return Material.mix(accent, 0xFFFFFFFF, 0.12F);
    }

    // ------------------------------------------------------------------ //
    // Geometry queries (position editor, music morph)
    // ------------------------------------------------------------------ //

    public float[] measure() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        WatermarkModule module = WatermarkModule.instance;
        float scale = ISLAND_SCALE;
        scale = Math.min(scale, Math.min((resolution.getScaledWidth() - EDGE) / Math.max(1.0F, designWidth),
                (resolution.getScaledHeight() - EDGE) / Math.max(1.0F, designHeight)));
        float screenW = designWidth * scale;
        float screenH = designHeight * scale;
        float maxX = Math.max(0.0F, resolution.getScaledWidth() - screenW - EDGE);
        float maxY = Math.max(0.0F, resolution.getScaledHeight() - screenH - EDGE);
        float px = module == null ? 50.0F : Material.clamp(module.samsaraPositionX(), 0.0F, 100.0F) / 100.0F;
        float py = module == null ? 0.0F : Material.clamp(module.samsaraPositionY(), 0.0F, 100.0F) / 100.0F;
        return new float[]{MARGIN + maxX * px, MARGIN + maxY * py + (idleVisible ? SCAFFOLD_LIFT : 0.0F),
                screenW, screenH};
    }

    public float designWidth() {
        return designWidth;
    }

    public float designHeight() {
        return designHeight;
    }

    public boolean inside(float mouseX, float mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public float left() {
        return x;
    }

    public float top() {
        return y;
    }

    public float width() {
        return w;
    }

    public float height() {
        return h;
    }

    public void reset() {
        notices.clear();
        rows.clear();
        idleParts.clear();
        states.clear();
        wasIdle = true;
        visible.set(0.0F);
        width.clear();
        height.clear();
        position.clear();
        designWidth = 60.0F;
        designHeight = IDLE_HEIGHT;
        designRadius = IDLE_HEIGHT / 2.0F;
        dragging = false;
        lastFrame = 0L;
    }

    // ------------------------------------------------------------------ //
    // Dragging (chat open) and the position editor
    // ------------------------------------------------------------------ //

    private void handleDrag(WatermarkModule module, Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiChat) || !Mouse.isButtonDown(0)) {
            dragging = false;
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        float mouseX = Mouse.getX() * resolution.getScaledWidth() / (float) mc.displayWidth;
        float mouseY = resolution.getScaledHeight()
                - Mouse.getY() * resolution.getScaledHeight() / (float) mc.displayHeight - 1.0F;
        if (dragging) {
            dragTo(module, mouseX - dragOffsetX, mouseY - dragOffsetY);
        } else if (inside(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
        }
    }

    /** Moves the island so its top-left corner sits at the requested GUI coordinates. */
    public void dragTo(WatermarkModule module, float desiredX, float desiredY) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        float maxX = Math.max(1.0F, resolution.getScaledWidth() - w - EDGE);
        float maxY = Math.max(1.0F, resolution.getScaledHeight() - h - EDGE);
        float percentX = Material.clamp((desiredX - MARGIN) / maxX * 100.0F, 0.0F, 100.0F);
        float percentY = Material.clamp((desiredY - MARGIN) / maxY * 100.0F, 0.0F, 100.0F);
        module.setPosition(percentX, percentY);
    }

    private float tick() {
        long now = System.nanoTime();
        float delta = lastFrame == 0L ? 16.0F : Math.min((now - lastFrame) / 1000000.0F, 100.0F);
        lastFrame = now;
        return delta;
    }

    private static void restoreHudState() {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ------------------------------------------------------------------ //
    // Value types
    // ------------------------------------------------------------------ //

    private static final class Part {
        final String text;
        final String glyph;
        final boolean mark;
        final int color;
        final float x;
        final float width;

        private Part(String text, String glyph, boolean mark, int color, float x, float width) {
            this.text = text;
            this.glyph = glyph;
            this.mark = mark;
            this.color = color;
            this.x = x;
            this.width = width;
        }

        static Part mark(float x, int color) {
            return new Part(null, null, true, color, x, ICON_SIZE);
        }

        static Part icon(String glyph, int color, float x) {
            return new Part(null, glyph, false, color, x, ICON_SIZE);
        }

        static Part text(String text, int color, float x, float width) {
            return new Part(text, null, false, color, x, width);
        }
    }

    private static final class Row {
        final Notice notice;
        final float y;
        final float height;
        final float toggle;

        Row(Notice notice, float y, float height, float toggle) {
            this.notice = notice;
            this.y = y;
            this.height = height;
            this.toggle = toggle;
        }
    }

    private static final class Notice {
        final String key;
        final Transition toggle = new Transition();
        String title = "";
        String detail = "";
        String status = "";
        Icon icon = Icon.INFO;
        boolean enabled;
        float progress;
        float contentWidth;
        long expiresAt;

        Notice(String key, long now) {
            this.key = key;
            this.toggle.started = now;
        }
    }

    private static final class Transition {
        float from;
        float to;
        long started;

        void target(float destination, long now) {
            if (destination != this.to) {
                this.from = value(now);
                this.to = destination;
                this.started = now;
            }
        }

        float value(long now) {
            float progress = Material.clamp((now - this.started) / (float) FADE_MS, 0.0F, 1.0F);
            float eased = this.to < this.from ? progress
                    : progress >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0D, -10.0D * progress);
            return this.from + (this.to - this.from) * eased;
        }
    }

    /** Analytical damped spring: identical at any frame rate, with velocity retained. */
    private static final class Morph {
        private double current;
        private double target;
        private double velocity;
        private long sampled = -1L;

        float value(float destination, long now, double impulse) {
            if (sampled < 0L) {
                current = target = destination;
                velocity = 0.0D;
            } else {
                double seconds = Math.max(0L, now - sampled) / 1000.0D;
                double displacement = current - target;
                double sineCoefficient = (velocity + MORPH_DECAY * displacement) / MORPH_FREQUENCY;
                double cosine = Math.cos(MORPH_FREQUENCY * seconds);
                double sine = Math.sin(MORPH_FREQUENCY * seconds);
                double decay = Math.exp(-MORPH_DECAY * seconds);
                double offset = displacement * cosine + sineCoefficient * sine;
                current = target + decay * offset;
                velocity = decay * (-MORPH_DECAY * offset
                        + MORPH_FREQUENCY * (-displacement * sine + sineCoefficient * cosine));
                if (destination != target) {
                    target = destination;
                    // Start promptly from rest; a reversal retains the spring's actual velocity.
                    if (Math.abs(velocity) < Math.max(0.1D, Math.abs(destination - current) * 0.1D)) {
                        velocity = (destination - current) * impulse;
                    }
                }
                if (Math.abs(current - target) < 0.01D && Math.abs(velocity) < 0.1D) {
                    current = target;
                    velocity = 0.0D;
                }
            }
            sampled = now;
            return (float) current;
        }

        void clear() {
            sampled = -1L;
            current = target = velocity = 0.0D;
        }
    }

    private static String fit(Typeface.Style style, String text, float width) {
        if (text == null || text.isEmpty() || width <= 0.0F) {
            return "";
        }
        // Adding/subtracting the insets can lose a few float ULPs; never ellipsize a fitting line.
        if (style.width(text) <= width + 0.01F) {
            return text;
        }
        if (style.width("...") > width) {
            return "";
        }
        int end = text.length();
        while (end > 0 && style.width(text.substring(0, end) + "...") > width) {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end) + "...";
    }
}
