package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldSettings;
import xyz.daikirai.gui.onyx.LegacyText;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.render.WatermarkModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Content only: Island owns the single frosted surface and its expanding geometry. */
public final class TabList {
    private static final float ROW = 24.0F;
    private static final float SERVER_SCALE = 0.85F;
    private static final Comparator<NetworkPlayerInfo> ORDER = new Comparator<NetworkPlayerInfo>() {
        @Override
        public int compare(NetworkPlayerInfo a, NetworkPlayerInfo b) {
            int spectator = Boolean.compare(isSpectator(a), isSpectator(b));
            if (spectator != 0) return spectator;
            ScorePlayerTeam at = a.getPlayerTeam();
            ScorePlayerTeam bt = b.getPlayerTeam();
            int team = (at == null ? "" : at.getRegisteredName())
                    .compareTo(bt == null ? "" : bt.getRegisteredName());
            return team != 0 ? team : a.getGameProfile().getName().compareTo(b.getGameProfile().getName());
        }
    };

    private final List<NetworkPlayerInfo> players = new ArrayList<NetworkPlayerInfo>();
    private List<String> header = Collections.emptyList();
    private List<String> footer = Collections.emptyList();
    private ScoreObjective objective;
    private float width = 200.0F;
    private float height = 60.0F;
    private float columnWidth;
    private float rowsY;
    private int rows;
    private long lastLayout;
    private float lastAvailableHeight;
    private boolean lastServerText;

    public void layout(float availableHeight) {
        long now = System.nanoTime();
        boolean serverText = WatermarkModule.tablistShowsServerText();
        if (now - lastLayout < 100000000L && availableHeight == lastAvailableHeight
                && serverText == lastServerText) return;
        lastLayout = now;
        lastAvailableHeight = availableHeight;
        lastServerText = serverText;
        Minecraft mc = Minecraft.getMinecraft();
        players.clear();
        if (mc.getNetHandler() != null) {
            players.addAll(mc.getNetHandler().getPlayerInfoMap());
        }
        Collections.sort(players, ORDER);
        // Match vanilla's 80-entry limit and spectator/team/name ordering.
        if (players.size() > 80) players.subList(80, players.size()).clear();
        objective = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(0);
        float nameWidth = 70.0F;
        for (NetworkPlayerInfo player : players) {
            nameWidth = Math.max(nameWidth, LegacyText.width(Typeface.LABEL_TIGHT, name(player)));
        }
        columnWidth = Math.min(250.0F, Math.max(186.0F, nameWidth + 84.0F
                + (objective == null ? 0.0F : 42.0F)));
        int preferredRows = Math.max(8, Math.min(20, (int) ((availableHeight - 100.0F) / ROW)));
        int columns = Math.max(1, Math.min(4, (players.size() + preferredRows - 1) / preferredRows));
        rows = Math.max(1, (players.size() + columns - 1) / columns);
        width = 20.0F + columns * columnWidth + (columns - 1) * 6.0F;
        GuiPlayerTabOverlay overlay = mc.ingameGUI.getTabList();
        TabListData data = overlay instanceof TabListData ? (TabListData) overlay : null;
        header = wrap(data == null ? null : data.onyx$header());
        footer = wrap(data == null ? null : data.onyx$footer());
        rowsY = 36.0F + (header.isEmpty() ? 0.0F : header.size() * 10.0F + 9.0F);
        height = rowsY + rows * ROW + 10.0F + (footer.isEmpty() ? 0.0F : footer.size() * 10.0F + 12.0F);
    }

    private List<String> wrap(IChatComponent component) {
        if (!WatermarkModule.tablistShowsServerText() || component == null || component.getFormattedText().isEmpty()) {
            return Collections.emptyList();
        }
        return Minecraft.getMinecraft().fontRendererObj.listFormattedStringToWidth(
                component.getFormattedText(), Math.max(1, (int) ((width - 28.0F) / SERVER_SCALE)));
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public void draw(UI ui, Scheme scheme, float progress) {
        ui.icon("\ue7ef", 18.0F, 18.0F, 15.0F, scheme.primary);
        ui.text(Typeface.LABEL, "Players", 32.0F, 18.0F, scheme.onSurface, UI.ALIGN_LEFT);
        String count = String.valueOf(players.size());
        float countWidth = Math.max(24.0F, Typeface.CAPTION.width(count) + 14.0F);
        ui.rounded(width - countWidth - 12.0F, 9.0F, countWidth, 18.0F, 9.0F, scheme.primaryContainer);
        ui.text(Typeface.CAPTION, count, width - 12.0F - countWidth / 2.0F, 18.0F,
                scheme.onPrimaryContainer, UI.ALIGN_CENTER);

        drawServerText(ui, header, 39.0F, scheme.onSurfaceVariant);
        if (!header.isEmpty()) {
            ui.rect(14.0F, rowsY - 5.0F, width - 28.0F, 0.5F,
                    Material.withAlpha(scheme.outlineVariant, 0.45F));
        }
        if (players.isEmpty()) {
            ui.text(Typeface.CAPTION, "No players", width / 2.0F, rowsY + ROW / 2.0F,
                    scheme.onSurfaceVariant, UI.ALIGN_CENTER);
        }
        for (int i = 0; i < players.size(); i++) {
            int row = i % rows;
            int column = i / rows;
            float entry = Material.clamp((progress - row * 0.012F) / 0.65F, 0.0F, 1.0F);
            ui.alphaPush(entry);
            drawPlayer(ui, scheme, players.get(i), 10.0F + column * (columnWidth + 6.0F),
                    rowsY + row * ROW + (1.0F - entry) * 5.0F);
            ui.alphaPop();
        }
        if (!footer.isEmpty()) {
            float separator = rowsY + rows * ROW + 5.0F;
            ui.rect(14.0F, separator, width - 28.0F, 0.5F,
                    Material.withAlpha(scheme.outlineVariant, 0.45F));
            drawServerText(ui, footer, separator + 11.0F, scheme.onSurfaceVariant);
        }
    }

    private void drawPlayer(UI ui, Scheme scheme, NetworkPlayerInfo player, float x, float y) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean self = mc.thePlayer.getUniqueID().equals(player.getGameProfile().getId());
        int surface = self ? Material.mix(scheme.surfaceContainerHigh, scheme.primaryContainer, 0.65F)
                : Material.mix(scheme.surfaceContainerHigh, scheme.onSurface, 0.045F);
        ui.rounded(x, y, columnWidth, ROW - 2.0F, 6.0F, Material.withAlpha(surface, self ? 0.88F : 0.38F));
        ui.image(player.getLocationSkin(), x + 4.0F, y + 3.0F, 16.0F, 16.0F,
                0.125F, 0.125F, 0.25F, 0.25F, 0xFFFFFFFF);
        ui.image(player.getLocationSkin(), x + 4.0F, y + 3.0F, 16.0F, 16.0F,
                0.625F, 0.125F, 0.75F, 0.25F, 0xFFFFFFFF);
        if (self) ui.circle(x + 19.0F, y + 18.0F, 2.0F, scheme.primary);
        float right = x + columnWidth - 7.0F;
        if (WatermarkModule.tablistShowsPing()) {
            int ping = player.getResponseTime();
            String latency = ping < 0 ? "--" : Math.min(ping, 9999) + "ms";
            int color = ping < 0 ? scheme.onSurfaceVariant
                    : ping < 100 ? scheme.primary : ping < 200 ? scheme.tertiary : scheme.error;
            ui.text(Typeface.MICRO, latency, right, y + 11.0F, color, UI.ALIGN_RIGHT);
            right -= 43.0F;
        }
        if (objective != null && !isSpectator(player)) {
            Scoreboard board = mc.theWorld.getScoreboard();
            String key = player.getGameProfile().getName();
            if (board.entityHasObjective(key, objective)) {
                int points = board.getValueFromObjective(key, objective).getScorePoints();
                boolean hearts = objective.getRenderType() == IScoreObjectiveCriteria.EnumRenderType.HEARTS;
                String value = hearts ? String.format(java.util.Locale.ROOT, "%.1f", points / 2.0F)
                        : String.valueOf(points);
                value = ui.truncate(Typeface.MICRO, value, 26.0F);
                ui.text(Typeface.MICRO, value, right, y + 11.0F, scheme.onSurfaceVariant, UI.ALIGN_RIGHT);
                if (hearts) ui.icon("\ue87d", right - Typeface.MICRO.width(value) - 6.0F,
                        y + 11.0F, 8.0F, scheme.error);
            }
            right -= 40.0F;
        }
        String label = LegacyText.truncate(Typeface.LABEL_TIGHT, name(player), Math.max(8.0F, right - x - 29.0F));
        LegacyText.draw(ui, Typeface.LABEL_TIGHT, label, x + 27.0F, y + 11.0F, UI.ALIGN_LEFT,
                isSpectator(player) ? scheme.onSurfaceVariant : scheme.onSurface);
    }

    private void drawServerText(UI ui, List<String> lines, float y, int color) {
        Minecraft mc = Minecraft.getMinecraft();
        for (String line : lines) {
            ui.push();
            ui.translate((width - mc.fontRendererObj.getStringWidth(line) * SERVER_SCALE) / 2.0F, y - 4.0F);
            ui.scale(SERVER_SCALE, 0.0F, 0.0F);
            // Shape/divider draws disable texturing; FontRenderer expects it to be enabled.
            GlStateManager.enableTexture2D();
            // Keep the server's font formatting, obfuscated text and resource-pack glyphs.
            int alpha = Math.round(ui.alpha() * 255.0F);
            if (alpha > 3) mc.fontRendererObj.drawString(line, 0.0F, 0.0F,
                    (color & 0xFFFFFF) | alpha << 24, false);
            ui.pop();
            y += 10.0F;
        }
        GlStateManager.disableAlpha();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static String name(NetworkPlayerInfo player) {
        return Minecraft.getMinecraft().ingameGUI.getTabList().getPlayerName(player);
    }

    private static boolean isSpectator(NetworkPlayerInfo player) {
        return player.getGameType() == WorldSettings.GameType.SPECTATOR;
    }
}
