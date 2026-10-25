package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.LegacyText;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.render.CustomGuiModule;

import java.util.ArrayList;
import java.util.List;

/**
 * Material 3 Expressive sidebar scoreboard, ported from OpenOnyx's
 * {@code client.onyx.render.hud.Cls2}.
 *
 * <p>Onyx drew the sidebar objective as a rounded card with the title centred, a divider and one
 * text row per entry (7px padding, 11px rows) and let the player drag it. The geometry here keeps
 * that layout with the port's type scale, and adds the shared frosted-glass backdrop: the world
 * behind the card is blurred exactly like the keybind list and the watermark island.</p>
 *
 * <p>Legacy {@code §} colour codes are preserved (Hypixel sidebars rely on them), names starting
 * with {@code #} are skipped and at most 15 lines are shown, matching the vanilla renderer. The
 * vanilla sidebar is cancelled by {@code MixinGuiIngame} while this module is enabled.</p>
 */
public final class Scoreboard {

    private static final float PAD = 7.0F;
    private static final float TITLE_Y = 13.0F;
    private static final float DIVIDER_Y = 22.0F;
    private static final float ROWS_TOP = 26.0F;
    private static final float ROWS_TOP_NO_TITLE = 8.0F;
    private static final float ROW_HEIGHT = 12.0F;
    private static final float BOTTOM_PAD = 6.0F;
    private static final float RADIUS = 14.0F;
    private static final float MIN_WIDTH = 64.0F;
    private static final float MAX_WIDTH = 260.0F;
    private static final float SCORE_GAP = 6.0F;
    private static final float MARGIN = 10.0F;
    private static final float EDGE = 20.0F;
    private static final float BLUR_COVERAGE = 0.55F;
    private static final int MAX_ROWS = 15;
    private static final long POLL_MS = 100L;

    private final UI ui = new UI();
    private final Anim visible = new Anim(0.0F);
    private final Anim cardWidth = new Anim(MIN_WIDTH);
    private final Anim cardHeight = new Anim(40.0F);
    private final List<Row> rows = new ArrayList<Row>();

    private String title = "";
    private long lastPoll;
    private long lastFrame;
    private float x;
    private float y;
    private float w;
    private float h;

    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;

    /** Draws (and updates) the card; {@code preview} keeps it visible for the position editor. */
    public void render(CustomGuiModule module, boolean preview) {
        if (module == null) {
            return;
        }
        float delta = tick();
        poll();

        boolean active = (preview || module.scoreboardEnabled()) && !rows.isEmpty();
        visible.to(active ? 1.0F : 0.0F, active ? 260.0F : 180.0F,
                active ? Ease.EMPHASIZED : Ease.DECELERATE);
        visible.update(delta);
        float fade = visible.value();
        if (fade <= 0.002F) {
            if (!preview) {
                return;
            }
            fade = 1.0F;
        }

        Scheme scheme = Theme.scheme();
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);

        boolean hasTitle = !title.isEmpty();
        float rowsTop = hasTitle ? ROWS_TOP : ROWS_TOP_NO_TITLE;
        boolean scores = module.scoreboardShowScores();

        float rowTextWidth = 0.0F;
        float scoreWidth = 0.0F;
        for (Row row : rows) {
            rowTextWidth = Math.max(rowTextWidth, LegacyText.width(Typeface.CAPTION, row.text));
            scoreWidth = Math.max(scoreWidth, Typeface.CAPTION.width(row.scoreText()));
        }
        float titleWidth = hasTitle ? LegacyText.width(Typeface.HEADLINE, title) : 0.0F;
        float scoreColumn = scores ? SCORE_GAP + scoreWidth : 0.0F;
        float contentWidth = Math.max(titleWidth, rowTextWidth + scoreColumn);
        float targetWidth = Material.clamp(contentWidth + PAD * 2.0F, MIN_WIDTH, MAX_WIDTH);
        float targetHeight = rowsTop + rows.size() * ROW_HEIGHT + BOTTOM_PAD;

        cardWidth.to(targetWidth, 240.0F, Ease.EMPHASIZED_DECELERATE);
        cardHeight.to(targetHeight, 240.0F, Ease.EMPHASIZED_DECELERATE);
        cardWidth.update(delta);
        cardHeight.update(delta);

        float base = module.scoreboardScale();
        float entrance = 0.94F + 0.06F * fade;
        float scale = base * entrance;
        float layoutWidth = cardWidth.value();
        float layoutHeight = cardHeight.value();
        this.w = layoutWidth * base;
        this.h = layoutHeight * base;

        float maxX = Math.max(0.0F, resolution.getScaledWidth() - w - EDGE);
        float maxY = Math.max(0.0F, resolution.getScaledHeight() - h - EDGE);
        this.x = MARGIN + maxX * (Material.clamp(module.scoreboardPositionX(), 0.0F, 100.0F) / 100.0F);
        this.y = MARGIN + maxY * (Material.clamp(module.scoreboardPositionY(), 0.0F, 100.0F) / 100.0F);

        if (!preview) {
            handleDrag(module, mc, resolution);
        }

        // Lay the rows out against the wider of the animated and target widths, so a sidebar that
        // gains or loses a line never flashes an ellipsis mid-animation.
        float rowMax = Math.max(layoutWidth, targetWidth) - PAD * 2.0F - scoreColumn + 1.0F;

        ui.begin();
        try {
            if (!preview) {
                GlStateManager.pushMatrix();
                mc.entityRenderer.setupOverlayRendering();
            }
            if (module.scoreboardFrosted() && fade > 0.01F) {
                Blur.prepare();
            }
            ui.alphaPush(fade);
            ui.push();
            ui.translate(x + w / 2.0F, y + h / 2.0F);
            ui.scale(scale, 0.0F, 0.0F);
            ui.translate(-layoutWidth / 2.0F, -layoutHeight / 2.0F);

            drawCard(module, scheme, layoutWidth, layoutHeight, hasTitle, rowsTop, scores, rowMax);

            ui.pop();
            ui.alphaPop();
        } finally {
            if (!preview) {
                GlStateManager.popMatrix();
            }
            ui.end();
            restoreHudState();
        }
    }

    private void drawCard(CustomGuiModule module, Scheme scheme, float width, float height,
                          boolean hasTitle, float rowsTop, boolean scores, float rowMax) {
        // Opaque backing + a fully opaque frost layer on top, the same recipe as the keybind card.
        int surface = Material.withAlpha(scheme.surfaceContainerHigh, 1.0F);
        ui.shadow(0.0F, 0.0F, width, height, RADIUS, 3);
        ui.rounded(0.0F, 0.0F, width, height, RADIUS, surface);
        if (module.scoreboardFrosted() && Blur.ready()) {
            ui.frost(0.0F, 0.0F, width, height, RADIUS, scheme.surfaceContainerHigh, BLUR_COVERAGE);
        }
        // Borderless card, matching the rest of the ported HUD.

        ui.scissorPush(0.0F, 0.0F, width, height);
        if (hasTitle) {
            LegacyText.draw(ui, Typeface.HEADLINE, title, width / 2.0F, TITLE_Y, UI.ALIGN_CENTER,
                    scheme.onSurface);
            ui.rect(PAD, DIVIDER_Y, Math.max(0.0F, width - PAD * 2.0F), 1.0F,
                    Material.withAlpha(scheme.outlineVariant, 0.6F));
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float centerY = rowsTop + i * ROW_HEIGHT + ROW_HEIGHT / 2.0F;
            String text = LegacyText.truncate(Typeface.CAPTION, row.text, rowMax);
            LegacyText.draw(ui, Typeface.CAPTION, text, PAD, centerY, UI.ALIGN_LEFT,
                    scheme.onSurfaceVariant);
            if (scores) {
                ui.text(Typeface.CAPTION, row.scoreText(), width - PAD, centerY,
                        scheme.onSurfaceVariant, UI.ALIGN_RIGHT);
            }
        }
        ui.scissorPop();
    }

    // ------------------------------------------------------------------ //
    // Data
    // ------------------------------------------------------------------ //

    private void poll() {
        long now = System.currentTimeMillis();
        if (now - lastPoll < POLL_MS) {
            return;
        }
        lastPoll = now;
        rows.clear();
        title = "";
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        net.minecraft.scoreboard.Scoreboard board = mc.theWorld.getScoreboard();
        ScoreObjective objective = sidebar(board, mc.thePlayer);
        if (objective == null) {
            return;
        }
        title = objective.getDisplayName() == null ? "" : objective.getDisplayName();

        List<Score> scores = new ArrayList<Score>();
        for (Score score : board.getSortedScores(objective)) {
            if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
                scores.add(score);
            }
        }
        if (scores.size() > MAX_ROWS) {
            scores = scores.subList(scores.size() - MAX_ROWS, scores.size());
        }
        // getSortedScores hands back the lowest score first; the sidebar draws the highest first,
        // which is also the order the vanilla renderer walks the list in.
        for (int i = scores.size() - 1; i >= 0; i--) {
            Score score = scores.get(i);
            Team team = board.getPlayersTeam(score.getPlayerName());
            rows.add(new Row(ScorePlayerTeam.formatPlayerName(team, score.getPlayerName()),
                    score.getScorePoints()));
        }
    }

    /**
     * The objective the vanilla sidebar would show: Hypixel switches between the per-team colour
     * display slots (3+) and the plain sidebar slot, and Onyx followed the same rule.
     */
    private static ScoreObjective sidebar(net.minecraft.scoreboard.Scoreboard board, EntityPlayer player) {
        ScorePlayerTeam team = board.getPlayersTeam(player.getName());
        if (team != null) {
            int index = team.getChatFormat().getColorIndex();
            if (index >= 0) {
                ScoreObjective objective = board.getObjectiveInDisplaySlot(3 + index);
                if (objective != null) {
                    return objective;
                }
            }
        }
        return board.getObjectiveInDisplaySlot(1);
    }

    // ------------------------------------------------------------------ //
    // Dragging and the position editor
    // ------------------------------------------------------------------ //

    private void handleDrag(CustomGuiModule module, Minecraft mc, ScaledResolution resolution) {
        boolean chat = mc.currentScreen instanceof GuiChat;
        if (!chat || !Mouse.isButtonDown(0)) {
            dragging = false;
            return;
        }
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

    public void dragTo(CustomGuiModule module, float desiredX, float desiredY) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        float maxX = Math.max(1.0F, resolution.getScaledWidth() - w - EDGE);
        float maxY = Math.max(1.0F, resolution.getScaledHeight() - h - EDGE);
        float percentX = Material.clamp((desiredX - MARGIN) / maxX * 100.0F, 0.0F, 100.0F);
        float percentY = Material.clamp((desiredY - MARGIN) / maxY * 100.0F, 0.0F, 100.0F);
        module.setScoreboardPosition(percentX, percentY);
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
        rows.clear();
        title = "";
        visible.set(0.0F);
        cardWidth.set(MIN_WIDTH);
        cardHeight.set(40.0F);
        dragging = false;
        lastPoll = 0L;
        lastFrame = 0L;
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

    /** One sidebar line: the already team-formatted text and its score. */
    private static final class Row {
        private final String text;
        private final int score;

        private Row(String text, int score) {
            this.text = text;
            this.score = score;
        }

        private String scoreText() {
            return String.valueOf(score);
        }
    }
}
