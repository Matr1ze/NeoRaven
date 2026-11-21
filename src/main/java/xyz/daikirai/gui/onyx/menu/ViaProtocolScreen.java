package xyz.daikirai.gui.onyx.menu;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.Icons;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Spring;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.via.ViaCompatibility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Material 3 picker for the protocol the built-in Via translates to.
 *
 * <p>Page one is a cover wall: one poster per headline version (1.8.9 and 1.20.5/1.20.6) under a
 * frosted glass pane with the version number across the middle, plus a tile that opens the full
 * list. Page two is that list, in the same card style as the other Android screens.</p>
 */
public final class ViaProtocolScreen extends GuiScreen {

    private static final float DESIGN_WIDTH = 640.0F;
    private static final float DESIGN_HEIGHT = 360.0F;
    private static final float PAD = 14.0F;

    private static final float CARD_TOP = 84.0F;
    private static final float CARD_WIDTH = 168.0F;
    private static final float CARD_HEIGHT = 224.0F;
    private static final float CARD_GAP = 18.0F;
    private static final float CARD_RADIUS = 20.0F;

    private static final float ROW_HEIGHT = 34.0F;
    private static final float LIST_TOP = 66.0F;
    /** The list card stops short of the page edge; nothing is drawn underneath it. */
    private static final float LIST_HEIGHT = DESIGN_HEIGHT - 26.0F - LIST_TOP;
    /** Rows are clipped this far inside the card, so a half row never touches the rounded edge. */
    private static final float VIEW_INSET = 8.0F;

    private static final float HEADER_BUTTON_Y = 10.0F;
    private static final float HEADER_BUTTON_HEIGHT = 30.0F;

    /** Version number over the glass; the long range drops a size so it never runs past the card. */
    private static final Typeface.Style CARD_TITLE = Typeface.custom(9.0F, 17.0F, 21.0F, true);
    private static final Typeface.Style CARD_TITLE_SMALL = Typeface.custom(9.0F, 14.0F, 17.0F, true);

    private static final String TITLE = "Server protocol";
    private static final String TITLE_ALL = "All versions";
    private static final String APPLY = "Apply";
    private static final String CANCEL = "Cancel";
    private static final String MORE = "All versions";
    private static final String CURRENT = "Current";

    private final GuiScreen parent;
    private final UI ui = new UI();
    private final List<ProtocolVersion> versions = ViaCompatibility.versions();
    private final List<ProtocolVersion> featured = new ArrayList<ProtocolVersion>();

    private final Spring opening = new Spring(0.0F);
    private final Spring pageIn = new Spring(0.0F);
    private final Spring scrollSpring = new Spring(0.0F);
    private final Spring[] cardHover = {new Spring(0.0F), new Spring(0.0F), new Spring(0.0F)};
    private final Spring[] rowHover = new Spring[10];

    /** [card][0] = poster, [card][1] = the matching frosted pane. */
    private ResourceLocation[][] covers;

    private float scale = 1.0F;
    private float ox;
    private float oy;
    private float mx;
    private float my;
    private float dt;
    private float scrollTarget;
    private long lastFrame;
    private long lastClickAt;
    private int lastClickCard = -1;
    private int lastClickRow = -1;

    private ProtocolVersion selected;
    private int page;
    private String error;

    public ViaProtocolScreen(GuiScreen parent) {
        this.parent = parent;
        for (int i = 0; i < rowHover.length; i++) {
            rowHover[i] = new Spring(0.0F);
        }
        // The two headline cards: the PvP legacy protocol and the newest one this client targets.
        addFeatured(ProtocolVersion.v1_8);
        addFeatured(ProtocolVersion.v1_20_5);
    }

    private void addFeatured(ProtocolVersion version) {
        if (versions.contains(version) && !featured.contains(version)) {
            featured.add(version);
        }
    }

    // ------------------------------------------------------------------ //
    // Lifecycle
    // ------------------------------------------------------------------ //

    @Override
    public void initGui() {
        float fit = Math.max(0.05F, Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT));
        scale = Math.min(Theme.screenScale(fit), 2.6F);
        ox = (width / scale - DESIGN_WIDTH) / 2.0F;
        oy = (height / scale - DESIGN_HEIGHT) / 2.0F;
        opening.set(0.0F);
        opening.target(1.0F);
        pageIn.set(1.0F);
        scrollTarget = 0.0F;
        scrollSpring.set(0.0F);
        lastFrame = 0L;
        selected = ViaCompatibility.target();
        // A protocol that is not on the cover wall opens on the full list, the only page that can
        // show it selected.
        page = featured.contains(selected) ? 0 : 1;
        pageIn.set(0.0F);
        pageIn.target(1.0F);
        error = null;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void close() {
        mc.displayGuiScreen(parentOrTitle());
    }

    /**
     * Same "no world, no pause menu" rule the list screens use: a pause menu without a world
     * dereferences {@code theWorld} as soon as a button is pressed.
     */
    private GuiScreen parentOrTitle() {
        GuiScreen target = parent;
        if (target == null || (AndroidScreens.needsWorld(target) && mc.theWorld == null)) {
            target = mc.theWorld != null ? new GuiIngameMenu() : new GuiMainMenu();
        }
        return target;
    }

    // ------------------------------------------------------------------ //
    // Layout helpers
    // ------------------------------------------------------------------ //

    private static float cardX(int index) {
        float row = 3.0F * CARD_WIDTH + 2.0F * CARD_GAP;
        return (DESIGN_WIDTH - row) / 2.0F + index * (CARD_WIDTH + CARD_GAP);
    }

    private static boolean point(float x, float y, float rx, float ry, float rw, float rh) {
        return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
    }

    private boolean isFeatured(ProtocolVersion version) {
        return featured.contains(version);
    }

    private float listScrollMax() {
        return Math.max(0.0F, versions.size() * ROW_HEIGHT + 8.0F - (LIST_HEIGHT - VIEW_INSET * 2.0F));
    }

    private int rowAt(float x, float y) {
        float viewTop = LIST_TOP + VIEW_INSET;
        if (!point(x, y, PAD, viewTop, DESIGN_WIDTH - PAD * 2.0F, LIST_HEIGHT - VIEW_INSET * 2.0F)) {
            return -1;
        }
        float top = LIST_TOP + 4.0F - scrollSpring.value();
        int index = (int) ((y - top) / ROW_HEIGHT);
        if (index < 0 || index >= versions.size()) {
            return -1;
        }
        float rowTop = top + index * ROW_HEIGHT;
        return y >= rowTop && y < rowTop + ROW_HEIGHT ? index : -1;
    }

    private float[] headerButton(int which) {
        // 0 = cancel (text), 1 = apply (filled); laid out from the right edge.
        float applyWidth = Math.max(76.0F, Typeface.LABEL.width(APPLY) + 40.0F);
        float cancelWidth = Math.max(64.0F, Typeface.LABEL.width(CANCEL) + 32.0F);
        float applyX = DESIGN_WIDTH - PAD - applyWidth;
        float cancelX = applyX - 8.0F - cancelWidth;
        return which == 1 ? new float[]{applyX, HEADER_BUTTON_Y, applyWidth, HEADER_BUTTON_HEIGHT}
                : new float[]{cancelX, HEADER_BUTTON_Y, cancelWidth, HEADER_BUTTON_HEIGHT};
    }

    private boolean canApply() {
        return selected != null && !selected.equalTo(ViaCompatibility.target());
    }

    private Typeface.Style cardTitleStyle(String name) {
        return ui.width(CARD_TITLE, name) > CARD_WIDTH - 26.0F ? CARD_TITLE_SMALL : CARD_TITLE;
    }

    // ------------------------------------------------------------------ //
    // Rendering
    // ------------------------------------------------------------------ //

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        dt = lastFrame == 0L ? 16.0F : Math.min(100.0F, (now - lastFrame) / 1.0E6F);
        lastFrame = now;
        opening.update(dt);
        pageIn.update(dt);
        scrollSpring.target(scrollTarget);
        scrollSpring.update(dt);
        mx = mouseX / scale - ox;
        my = mouseY / scale - oy;

        float enter = Material.clamp(opening.value(), 0.0F, 1.0F);
        float pageAlpha = Material.clamp(pageIn.value(), 0.0F, 1.0F);

        ui.begin();
        MenuBackground.mouse(mouseX, mouseY, width, height);
        MenuBackground.draw(ui, width, height, true);
        ui.rect(0.0F, 0.0F, width, height, Material.withAlpha(AndroidWallpaper.page(), 0.94F));
        ui.push();
        ui.scale(scale, 0.0F, 0.0F);
        ui.translate(ox, oy);
        ui.translate(0.0F, (1.0F - enter) * 24.0F);
        ui.alphaPush(Material.clamp(enter * 1.4F, 0.0F, 1.0F));

        drawHeader();
        // The pages cross over with a short rise so switching to the list does not feel like a jump.
        ui.push();
        ui.translate(0.0F, (1.0F - pageAlpha) * 10.0F);
        ui.alphaPush(pageAlpha);
        if (page == 0) {
            drawCovers();
        } else {
            drawVersions();
        }
        ui.alphaPop();
        ui.pop();

        ui.alphaPop();
        ui.pop();
        ui.end();
    }

    private void drawHeader() {
        float titleX = PAD;
        if (page == 1) {
            // Round back button, then the page title.
            boolean hovered = point(mx, my, PAD, HEADER_BUTTON_Y, HEADER_BUTTON_HEIGHT, HEADER_BUTTON_HEIGHT);
            ui.rounded(PAD, HEADER_BUTTON_Y, HEADER_BUTTON_HEIGHT, HEADER_BUTTON_HEIGHT, 15.0F,
                    hovered ? AndroidWallpaper.primaryContainer() : AndroidWallpaper.surfaceHigh());
            ui.icon(Icons.ARROW_BACK, PAD + HEADER_BUTTON_HEIGHT / 2.0F,
                    HEADER_BUTTON_Y + HEADER_BUTTON_HEIGHT / 2.0F, 18.0F, AndroidWallpaper.onSurfaceVariant());
            titleX = PAD + HEADER_BUTTON_HEIGHT + 10.0F;
        }
        ui.textBaseline(Typeface.HEADLINE_LARGE, page == 0 ? TITLE : TITLE_ALL, titleX, 30.0F,
                AndroidWallpaper.ink(), UI.ALIGN_LEFT);

        String caption = error;
        if (caption == null) {
            caption = page == 0
                    ? CURRENT + ": " + ViaCompatibility.name(ViaCompatibility.target())
                    : versions.size() + " versions - pick one, then " + APPLY;
        }
        ui.textBaseline(Typeface.CAPTION, caption, titleX, 48.0F,
                error == null ? AndroidWallpaper.faintInk() : AndroidWallpaper.errorInk(), UI.ALIGN_LEFT);

        float[] apply = headerButton(1);
        float[] cancel = headerButton(0);
        boolean applyHovered = canApply() && point(mx, my, apply[0], apply[1], apply[2], apply[3]);
        if (!canApply()) {
            ui.alphaPush(0.38F);
        }
        ui.shadow(apply[0], apply[1], apply[2], apply[3], 15.0F, applyHovered ? 2 : 1);
        ui.rounded(apply[0], apply[1], apply[2], apply[3], 15.0F,
                applyHovered ? Material.mix(AndroidWallpaper.primary(), AndroidWallpaper.card(), 0.12F)
                        : AndroidWallpaper.primary());
        ui.text(Typeface.LABEL, APPLY, apply[0] + apply[2] / 2.0F, apply[1] + apply[3] / 2.0F,
                AndroidWallpaper.onPrimary(), UI.ALIGN_CENTER);
        if (!canApply()) {
            ui.alphaPop();
        }

        boolean cancelHovered = point(mx, my, cancel[0], cancel[1], cancel[2], cancel[3]);
        if (cancelHovered) {
            ui.rounded(cancel[0], cancel[1], cancel[2], cancel[3], 15.0F,
                    Material.withAlpha(AndroidWallpaper.primary(), 0.08F));
        }
        ui.text(Typeface.LABEL, CANCEL, cancel[0] + cancel[2] / 2.0F, cancel[1] + cancel[3] / 2.0F,
                AndroidWallpaper.primary(), UI.ALIGN_CENTER);
    }

    // ------------------------------------------------------------------ //
    // Page 1: cover wall
    // ------------------------------------------------------------------ //

    private void drawCovers() {
        if (covers == null) {
            covers = ProtocolCover.register();
        }
        // Pass 1: the artwork. The frost pass below blurs exactly this content.
        float[] tops = new float[3];
        for (int i = 0; i < 3; i++) {
            tops[i] = cardTop(i);
        }
        for (int i = 0; i < featured.size() && i < 2; i++) {
            drawCardArt(i, tops[i], covers[Math.min(i, covers.length - 1)]);
        }
        drawMoreArt(2, tops[2]);

        // Pass 2: the frosted pane and everything that has to stay sharp.
        for (int i = 0; i < featured.size() && i < 2; i++) {
            drawCardGlass(i, tops[i], featured.get(i));
        }
        drawMoreContent(2, tops[2]);
    }

    private float cardTop(int index) {
        Spring hover = cardHover[index];
        boolean hovered = point(mx, my, cardX(index), CARD_TOP, CARD_WIDTH, CARD_HEIGHT);
        hover.target(hovered ? 1.0F : 0.0F);
        hover.update(dt);
        return CARD_TOP - hover.value() * 2.0F;
    }

    private void drawCardArt(int index, float top, ResourceLocation[] cover) {
        float x = cardX(index);
        boolean chosen = selected != null && selected.equalTo(featured.get(index));
        ui.shadow(x, top, CARD_WIDTH, CARD_HEIGHT, CARD_RADIUS, chosen ? 4 : cardHover[index].value() > 0.4F ? 3 : 2);
        ui.imageRounded(cover[0], x, top, CARD_WIDTH, CARD_HEIGHT, CARD_RADIUS, 0xFFFFFFFF);
        // The frosted pane: the same artwork blurred up front, clipped to the card shape.
        ui.imageRounded(cover[1], x, top, CARD_WIDTH, CARD_HEIGHT, CARD_RADIUS, 0xEFFFFFFF);
    }

    private void drawCardGlass(int index, float top, ProtocolVersion version) {
        float x = cardX(index);
        boolean chosen = selected != null && selected.equalTo(version);
        float hover = cardHover[index].value();

        // Exactly the HUD recipe: the scheme's container colour, then the blurred backdrop through
        // the shared frost shader. Rounded gradient (not a square one) so no corner pokes out.
        // A trace of the theme's surface colour, so the pane still belongs to the scheme; then the
        // glass highlight. Both are rounded - a square gradient is what painted corners outside the
        // card before.
        Scheme scheme = Theme.scheme();
        ui.rounded(x, top, CARD_WIDTH, CARD_HEIGHT, CARD_RADIUS,
                Material.withAlpha(scheme.surfaceContainerHigh, 0.16F));
        ui.gradient4(x, top, CARD_WIDTH, CARD_HEIGHT * 0.45F, CARD_RADIUS,
                0x18FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x18FFFFFF);
        ui.outline(x + 0.5F, top + 0.5F, CARD_WIDTH - 1.0F, CARD_HEIGHT - 1.0F, CARD_RADIUS - 0.5F,
                1.0F, 0x40FFFFFF);
        if (chosen) {
            ui.outline(x - 1.0F, top - 1.0F, CARD_WIDTH + 2.0F, CARD_HEIGHT + 2.0F, CARD_RADIUS + 1.0F,
                    2.4F, AndroidWallpaper.primary());
        } else if (hover > 0.02F) {
            ui.outline(x, top, CARD_WIDTH, CARD_HEIGHT, CARD_RADIUS, 1.0F,
                    Material.withAlpha(0xFFFFFFFF, 0.30F + 0.30F * hover));
        }

        String name = ViaCompatibility.name(version);
        Typeface.Style style = cardTitleStyle(name);
        float cy = top + CARD_HEIGHT * 0.46F;
        ui.text(style, name, x + CARD_WIDTH / 2.0F + 0.5F, cy + 0.5F, 0x66000000, UI.ALIGN_CENTER);
        ui.text(style, name, x + CARD_WIDTH / 2.0F, cy, 0xFFFFFFFF, UI.ALIGN_CENTER);

        // Apply affordance inside the glass: chevron when it is only a choice, check when it is on.
        float bx = x + CARD_WIDTH / 2.0F;
        float by = top + CARD_HEIGHT - 34.0F;
        boolean buttonHovered = point(mx, my, bx - 14.0F, by - 14.0F, 28.0F, 28.0F);
        ui.circle(bx, by, buttonHovered ? 14.0F : 13.0F,
                chosen ? AndroidWallpaper.primary() : Material.withAlpha(0xFFFFFFFF, 0.22F + 0.12F * hover));
        ui.icon(chosen ? Icons.CHECK : Icons.CHEVRON_RIGHT, bx, by, 16.0F,
                chosen ? AndroidWallpaper.onPrimary() : 0xFFFFFFFF);

        if (chosen) {
            float badgeX = x + CARD_WIDTH - 20.0F;
            float badgeY = top + 20.0F;
            ui.circle(badgeX, badgeY, 10.0F, AndroidWallpaper.primary());
            ui.icon(Icons.CHECK, badgeX, badgeY, 14.0F, AndroidWallpaper.onPrimary());
        }
    }

    private void drawMoreArt(int index, float top) {
        float x = cardX(index);
        int base = Material.mix(AndroidWallpaper.card(), AndroidWallpaper.secondaryContainer(), 0.55F);
        ui.shadow(x, top, CARD_WIDTH, CARD_HEIGHT, CARD_RADIUS, cardHover[index].value() > 0.4F ? 3 : 2);
        ui.rounded(x, top, CARD_WIDTH, CARD_HEIGHT, CARD_RADIUS,
                Material.mix(base, AndroidWallpaper.primaryContainer(), 0.16F * cardHover[index].value()));
        ui.outline(x, top, CARD_WIDTH, CARD_HEIGHT, CARD_RADIUS, 1.0F, AndroidWallpaper.outlineVariant());
    }

    private void drawMoreContent(int index, float top) {
        float x = cardX(index);
        float cx = x + CARD_WIDTH / 2.0F;
        ui.circle(cx, top + CARD_HEIGHT * 0.36F, 24.0F, AndroidWallpaper.primaryContainer());
        ui.icon(Icons.CHEVRON_RIGHT, cx, top + CARD_HEIGHT * 0.36F, 24.0F,
                AndroidWallpaper.onPrimaryContainer());
        ui.text(CARD_TITLE, MORE, cx, top + CARD_HEIGHT * 0.62F, AndroidWallpaper.ink(), UI.ALIGN_CENTER);
        ui.text(Typeface.CAPTION, versions.size() + " protocols", cx, top + CARD_HEIGHT * 0.62F + 22.0F,
                AndroidWallpaper.faintInk(), UI.ALIGN_CENTER);
        if (selected != null && !isFeatured(selected)) {
            String current = ViaCompatibility.name(selected);
            float w = Typeface.CAPTION.width(current) + 18.0F;
            ui.rounded(cx - w / 2.0F, top + CARD_HEIGHT - 30.0F, w, 17.0F, 8.5F,
                    AndroidWallpaper.primaryContainer());
            ui.text(Typeface.CAPTION, current, cx, top + CARD_HEIGHT - 21.5F,
                    AndroidWallpaper.onPrimaryContainer(), UI.ALIGN_CENTER);
        }
    }

    // ------------------------------------------------------------------ //
    // Page 2: every protocol
    // ------------------------------------------------------------------ //

    private void drawVersions() {
        float cardX = PAD;
        float cardY = LIST_TOP;
        float cardWidth = DESIGN_WIDTH - PAD * 2.0F;
        ui.shadow(cardX, cardY, cardWidth, LIST_HEIGHT, 24.0F, 2);
        ui.rounded(cardX, cardY, cardWidth, LIST_HEIGHT, 24.0F, AndroidWallpaper.card());
        // The rows scroll inside an inset window: a half row is clipped 8 units below the card's
        // top edge, so it can never cover the rounded corner or read as a bar floating above the
        // card. The strip that is left over is card coloured and dissolves under the fade.
        float viewTop = cardY + VIEW_INSET;
        float viewHeight = LIST_HEIGHT - VIEW_INSET * 2.0F;
        ui.scissorPush(cardX, viewTop, cardWidth, viewHeight);
        float top = cardY + 4.0F - scrollSpring.value();
        for (int i = 0; i < versions.size(); i++) {
            float y = top + i * ROW_HEIGHT;
            // Rows that are mostly scrolled out are skipped entirely: the fade hides the moment they
            // appear, and it keeps a sliver of the selected row from showing on the card's edge.
            float visibleTop = Math.max(y, viewTop);
            float visibleBottom = Math.min(y + ROW_HEIGHT, viewTop + viewHeight);
            if (visibleBottom - visibleTop < ROW_HEIGHT * 0.6F) {
                continue;
            }
            ProtocolVersion version = versions.get(i);
            boolean hovered = rowAt(mx, my) == i;
            Spring hover = rowHover[i % rowHover.length];
            hover.target(hovered ? 1.0F : 0.0F);
            hover.update(dt);
            boolean chosen = selected != null && selected.equalTo(version);
            if (chosen) {
                ui.rounded(cardX + 4.0F, y + 2.0F, cardWidth - 8.0F, ROW_HEIGHT - 4.0F, 16.0F,
                        AndroidWallpaper.primaryContainer());
            } else if (hover.value() > 0.01F) {
                ui.rounded(cardX + 4.0F, y + 2.0F, cardWidth - 8.0F, ROW_HEIGHT - 4.0F, 16.0F,
                        Material.withAlpha(AndroidWallpaper.ink(), 0.045F * hover.value()));
            }
            int ink = chosen ? AndroidWallpaper.onPrimaryContainer() : AndroidWallpaper.ink();
            float labelY = y + ROW_HEIGHT / 2.0F;
            ui.text(Typeface.BODY_MEDIUM, ViaCompatibility.name(version), cardX + 24.0F, labelY, ink,
                    UI.ALIGN_LEFT);
            if (version.equalTo(ViaCompatibility.target())) {
                ui.text(Typeface.CAPTION, CURRENT, cardX + cardWidth - 48.0F, labelY,
                        chosen ? AndroidWallpaper.onPrimaryContainer() : AndroidWallpaper.faintInk(),
                        UI.ALIGN_RIGHT);
            }
            if (chosen) {
                ui.icon(Icons.CHECK, cardX + cardWidth - 26.0F, labelY, 16.0F,
                        AndroidWallpaper.onPrimaryContainer());
            }
        }
        ui.scissorPop();

        // Fade the two ends of the scrolled window into the card instead of cutting a row in half.
        // Both bands sit over the inset window, so no row can paint a hard edge on the card.
        int card = AndroidWallpaper.card();
        int clear = card & 0x00FFFFFF;
        // Anchored to the card itself (not to the inset window): with the card's own radius the band
        // cannot paint outside the rounded corners, and it covers the row sliver all the way to the
        // left and right edges of the window.
        float fadeInset = VIEW_INSET + 12.0F;
        ui.gradient4(cardX, cardY, cardWidth, fadeInset, CARD_RADIUS, card, clear, clear, card);
        ui.gradient4(cardX, cardY + LIST_HEIGHT - fadeInset, cardWidth, fadeInset, CARD_RADIUS,
                clear, card, card, clear);

        float content = versions.size() * ROW_HEIGHT + 8.0F;
        if (content > viewHeight) {
            float track = viewHeight - 8.0F;
            float thumb = Math.max(24.0F, track * viewHeight / content);
            float progress = Material.clamp(scrollSpring.value() / (content - viewHeight), 0.0F, 1.0F);
            ui.rounded(cardX + cardWidth - 7.0F, viewTop + 4.0F + (track - thumb) * progress, 3.0F, thumb,
                    1.5F, Material.withAlpha(AndroidWallpaper.ink(), 0.18F));
        }
    }

    // ------------------------------------------------------------------ //
    // Input
    // ------------------------------------------------------------------ //

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        float x = mouseX / scale - ox;
        float y = mouseY / scale - oy;

        float[] apply = headerButton(1);
        if (point(x, y, apply[0], apply[1], apply[2], apply[3])) {
            apply();
            return;
        }
        float[] cancel = headerButton(0);
        if (point(x, y, cancel[0], cancel[1], cancel[2], cancel[3])) {
            click();
            close();
            return;
        }
        if (page == 1 && point(x, y, PAD, HEADER_BUTTON_Y, HEADER_BUTTON_HEIGHT, HEADER_BUTTON_HEIGHT)) {
            click();
            showPage(0);
            return;
        }

        if (page == 0) {
            for (int i = 0; i < featured.size() && i < 2; i++) {
                float cx = cardX(i);
                // The round control inside the glass applies straight away.
                float bx = cx + CARD_WIDTH / 2.0F;
                float by = CARD_TOP + CARD_HEIGHT - 34.0F;
                if (point(x, y, bx - 14.0F, by - 14.0F, 28.0F, 28.0F)) {
                    click();
                    selected = featured.get(i);
                    apply();
                    return;
                }
                if (point(x, y, cx, CARD_TOP, CARD_WIDTH, CARD_HEIGHT)) {
                    long now = System.currentTimeMillis();
                    boolean again = i == lastClickCard && now - lastClickAt < 420L;
                    lastClickCard = i;
                    lastClickAt = now;
                    selected = featured.get(i);
                    click();
                    if (again) {
                        apply();
                    }
                    return;
                }
            }
            if (point(x, y, cardX(2), CARD_TOP, CARD_WIDTH, CARD_HEIGHT)) {
                click();
                showPage(1);
                return;
            }
            return;
        }

        int row = rowAt(x, y);
        if (row >= 0) {
            long now = System.currentTimeMillis();
            boolean again = row == lastClickRow && now - lastClickAt < 420L;
            lastClickRow = row;
            lastClickAt = now;
            selected = versions.get(row);
            click();
            if (again) {
                apply();
            }
        }
    }

    private void showPage(int next) {
        page = next;
        pageIn.set(0.0F);
        pageIn.target(1.0F);
        if (next == 1) {
            scrollTarget = 0.0F;
            int index = selected == null ? -1 : versions.indexOf(selected);
            if (index >= 0) {
                ensureVisible(index);
            }
        }
    }

    /** Keeps a row on screen; jumping to the selection centres it instead of pinning it to an edge. */
    private void ensureVisible(int index) {
        float top = index * ROW_HEIGHT;
        float bottom = top + ROW_HEIGHT;
        if (top < scrollTarget || bottom > scrollTarget + LIST_HEIGHT - 8.0F) {
            scrollTarget = top - (LIST_HEIGHT - ROW_HEIGHT) / 2.0F;
        }
        scrollTarget = Math.max(0.0F, Math.min(listScrollMax(), scrollTarget));
    }

    @Override
    public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        super.handleMouseInput();
        if (wheel != 0 && page == 1) {
            scrollTarget = Math.max(0.0F, Math.min(listScrollMax(), scrollTarget - wheel * 0.6F));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (page == 1) {
                showPage(0);
            } else {
                close();
            }
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            apply();
            return;
        }
        if (page != 1) {
            return;
        }
        int index = selected == null ? -1 : versions.indexOf(selected);
        if (keyCode == Keyboard.KEY_DOWN) {
            index = Math.min(versions.size() - 1, index + 1);
            selected = versions.get(index);
            ensureVisible(index);
        } else if (keyCode == Keyboard.KEY_UP) {
            index = Math.max(0, index - 1);
            selected = versions.get(index);
            ensureVisible(index);
        }
    }

    private void apply() {
        if (!canApply()) {
            return;
        }
        try {
            ViaCompatibility.select(selected);
            close();
        } catch (IOException e) {
            error = "Couldn't save the protocol selection";
        }
    }

    private void click() {
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }

    // ------------------------------------------------------------------ //
    // Automation hooks (design space)
    // ------------------------------------------------------------------ //

    public int page() {
        return page;
    }

    public String selectedName() {
        return selected == null ? null : ViaCompatibility.name(selected);
    }

    public String targetName() {
        return ViaCompatibility.name(ViaCompatibility.target());
    }

    public int featuredCount() {
        return featured.size();
    }

    public String featuredName(int index) {
        return index >= 0 && index < featured.size() ? ViaCompatibility.name(featured.get(index)) : null;
    }

    public boolean canApplyPublic() {
        return canApply();
    }

    public int rowCount() {
        return versions.size();
    }

    public String rowName(int index) {
        return index >= 0 && index < versions.size() ? ViaCompatibility.name(versions.get(index)) : null;
    }

    public float[] tileCenter(int index) {
        return new float[]{cardX(index) + CARD_WIDTH / 2.0F, CARD_TOP + CARD_HEIGHT / 2.0F};
    }

    public float[] playCenter(int index) {
        return new float[]{cardX(index) + CARD_WIDTH / 2.0F, CARD_TOP + CARD_HEIGHT - 34.0F};
    }

    public float[] rowCenter(int index) {
        return new float[]{PAD + 60.0F,
                LIST_TOP + 4.0F - scrollSpring.value() + index * ROW_HEIGHT + ROW_HEIGHT / 2.0F};
    }

    public float[] applyCenter() {
        float[] rect = headerButton(1);
        return new float[]{rect[0] + rect[2] / 2.0F, rect[1] + rect[3] / 2.0F};
    }

    public float scale() {
        return scale;
    }

    public float originX() {
        return ox;
    }

    public float originY() {
        return oy;
    }

    /** Design-space click, converted exactly like {@link #mouseClicked(int, int, int)} does. */
    public void clickDesign(int screenX, int screenY) {
        try {
            mouseClicked(screenX, screenY, 0);
        } catch (Exception ignored) {
        }
    }

    public void pressKey(int keyCode) {
        keyTyped('\0', keyCode);
    }

    public static Minecraft minecraft() {
        return Minecraft.getMinecraft();
    }
}
