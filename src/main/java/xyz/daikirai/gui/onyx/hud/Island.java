package xyz.daikirai.gui.onyx.hud;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.Text;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.gui.onyx.render.WavyProgressIndicator;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.combat.AutoGappleModule;
import xyz.daikirai.module.player.AutoSwapModule;
import xyz.daikirai.module.player.BedAuraModule;
import xyz.daikirai.module.render.WatermarkModule;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The Dynamic Island watermark, ported from OpenOnyx's {@code WatermarkModule}.
 *
 * <p>Onyx drew a frosted pill that morphs between four states: idle (client name), module toggle
 * announcements, a live gauge and now-playing. The geometry is kept 1:1: 22px tall, 36.7px of fixed
 * padding (7px left, 16.7px logo, 6px gap, 7px right), 22px minimum content, 30px for the gauge,
 * 200ms width/height morphs and a 300ms entrance. The idle label reads "Raven" for this port.</p>
 *
 * <p>Gauge values update in place (Onyx only re-morphed when the state kind changed), so the
 * Scaffold block counter no longer re-animates the whole island on every block change.</p>
 */
public final class Island {

    /**
     * Raven B4's own raven mark, a white alpha mask tinted with the scheme's primary colour, so it
     * follows the theme exactly like the rest of the island. Replaces Onyx's planet logo.
     *
     * <p>The bitmap is read from the classpath and uploaded as a dynamic texture, the same way the
     * ported onyx assets are loaded: in the dev run the texture manager cannot resolve this mod's
     * resource pack, and the jar ships the bitmap at this exact path.</p>
     */
    private static final ResourceLocation LOGO = new ResourceLocation("clickgui", "island_raven");
    private static boolean logoLoaded;

    private static final float LOGO_WIDTH = 16.695652F;
    private static final float LOGO_HEIGHT = 14.0F;
    private static final float TEXT_X = 29.695652F;
    private static final float FIXED_WIDTH = 36.695652F;
    private static final float MIN_CONTENT = 22.0F;
    private static final float MAX_TEXT = 90.0F;
    private static final float HEIGHT = 22.0F;
    private static final float GAUGE_HEIGHT = 30.0F;
    private static final float MARGIN = 10.0F;
    private static final float EDGE = 20.0F;
    /** Same frosted recipe as the keybind card: the surface tint covers 58% of the blurred scene. */
    private static final float FROST_COVERAGE = 0.58F;

    private static final int IDLE = 0;
    private static final int TOGGLE = 1;
    private static final int GAUGE = 2;
    private static final int MUSIC = 3;

    private static final String CLIENT_NAME = "NeoRaven";
    private static final String MUSIC_ICON = "\ue405";

    private final UI ui = new UI();
    private final Anim visible = new Anim(0.0F);
    private final Anim contentWidth = new Anim(MIN_CONTENT);
    private final Anim contentHeight = new Anim(HEIGHT);
    private final Anim morph = new Anim(1.0F);
    private final Anim flash = new Anim(0.0F);
    private final Anim gaugeFillAnim = new Anim(0.0F);
    private final Anim musicProgressAnim = new Anim(0.0F);
    private final Anim tabOpen = new Anim(0.0F);
    private final TabList tabList = new TabList();
    private final Map<Module, Boolean> states = new IdentityHashMap<Module, Boolean>();
    private long lastFrame;
    private float phase;

    private int kind = IDLE;
    private int previousKind = IDLE;
    private String text = CLIENT_NAME;
    private String previousText = "";
    private String badge = "";
    private String previousBadge = "";
    private boolean positive = true;
    private boolean previousPositive = true;
    private float previousGaugeFill;

    private String notifyText = "";
    private String notifyBadge = "";
    private boolean notifyPositive = true;
    private float hold;

    private boolean gaugeActive;
    private int gaugeCount;
    private int gaugeMax = 64;
    private boolean gaugeAlert;
    private int flashColor = 0xFFFFFFFF;

    private boolean bedActive;
    private String bedLabel = "Bed";
    private float bedPercent;

    private boolean gappleActive;
    private int gappleCount;
    private float gapplePercent;

    private boolean musicActive;
    private String musicTitle = "";
    private boolean musicPlaying;

    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;

    private float x;
    private float y;
    private float w;
    private float h;

    /** Draws (and updates) the island; {@code preview} keeps it visible for the position editor. */
    public void render(WatermarkModule module, boolean preview) {
        if (module == null) {
            return;
        }
        float delta = tick();
        boolean held = !preview && WatermarkModule.tablistHeld();
        if (preview || !WatermarkModule.replacesTablist()) {
            tabOpen.set(0.0F);
        }
        tabOpen.to(held ? 1.0F : 0.0F, held ? 420.0F : 280.0F,
                held ? Ease.EMPHASIZED : Ease.STANDARD);
        tabOpen.update(delta);
        update(module, delta, preview);

        float fade = preview ? 1.0F : visible.value();
        if (fade <= 0.002F) {
            if (!preview) {
                return;
            }
            fade = 1.0F;
        }
        Scheme scheme = Theme.scheme();
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);

        float expansion = tabOpen.value();
        float scale = module.islandScale() * (0.94F + 0.06F * fade);
        float content = contentWidth.value();
        float pillWidth = FIXED_WIDTH + content;
        float pillHeight = contentHeight.value();
        float tabScale = scale * WatermarkModule.tablistScaleFactor();
        if (held) {
            tabList.layout((resolution.getScaledHeight() - EDGE) / scale);
        }
        float fit = Math.min(1.0F, Math.min((resolution.getScaledWidth() - EDGE) / (tabList.width() * tabScale),
                (resolution.getScaledHeight() - EDGE) / (tabList.height() * tabScale)));
        scale += (tabScale * fit - scale) * expansion;
        float islandWidth = pillWidth + (tabList.width() - pillWidth) * expansion;
        float islandHeight = pillHeight + (tabList.height() - pillHeight) * expansion;
        // Interpolated width and scale can peak between the endpoints of the morph.
        scale = Math.min(scale, Math.min((resolution.getScaledWidth() - EDGE) / islandWidth,
                (resolution.getScaledHeight() - EDGE) / islandHeight));
        this.w = islandWidth * scale;
        this.h = islandHeight * scale;

        float maxX = Math.max(0.0F, resolution.getScaledWidth() - w - EDGE);
        float maxY = Math.max(0.0F, resolution.getScaledHeight() - h - EDGE);
        this.x = MARGIN + maxX * (Material.clamp(module.positionX(), 0.0F, 100.0F) / 100.0F);
        this.y = MARGIN + maxY * (Material.clamp(module.positionY(), 0.0F, 100.0F) / 100.0F);

        if (!preview) {
            handleDrag(module, mc, resolution);
        }
        boolean drawLogo = module.logo() && kind != MUSIC && ensureLogo();
        boolean frosted = expansion > 0.001F ? WatermarkModule.tablistFrosted() : module.frosted();

        GlStateManager.pushMatrix();
        mc.entityRenderer.setupOverlayRendering();
        ui.begin();
        ui.push();
        try {
            if (frosted && fade > 0.01F) {
                Blur.prepare();
            }
            ui.alphaPush(fade);
            ui.translate(this.x, this.y + (1.0F - fade) * 5.0F);
            ui.scale(scale, 0.0F, 0.0F);

            float radius = pillHeight / 2.0F + (24.0F - pillHeight / 2.0F) * expansion;
            ui.shadow(0.0F, 0.0F, islandWidth, islandHeight, radius, 3);
            ui.rounded(0.0F, 0.0F, islandWidth, islandHeight, radius, scheme.surfaceContainerHigh);
            if (frosted && Blur.ready()) {
                ui.frost(0.0F, 0.0F, islandWidth, islandHeight, radius, scheme.surfaceContainerHigh,
                        FROST_COVERAGE);
            }
            // No resting border: the outline is only the state-change flash ring.
            float border = Material.clamp(flash.value(), 0.0F, 1.0F);
            if (border > 0.01F && expansion < 0.01F) {
                ui.outline(0.0F, 0.0F, islandWidth, islandHeight, radius, 1.0F,
                        Material.withAlpha(borderColor(scheme), border));
            }

            ui.alphaPush(1.0F - Material.clamp(expansion * 3.0F, 0.0F, 1.0F));
            if (drawLogo) {
                ui.image(LOGO, 7.0F + (LOGO_WIDTH - LOGO_HEIGHT) / 2.0F,
                        (islandHeight - LOGO_HEIGHT) / 2.0F, LOGO_HEIGHT, LOGO_HEIGHT,
                        0.0F, 0.0F, 1.0F, 1.0F, scheme.primary);
            }

            ui.scissorPush(0.0F, 0.0F, islandWidth, islandHeight);
            float t = morph.value();
            if (t < 0.999F) {
                ui.alphaPush(1.0F - t);
                drawContent(scheme, previousKind, previousText, previousBadge, previousPositive,
                        islandWidth, islandHeight, -6.0F * t, previousGaugeFill);
                ui.alphaPop();
            }
            if (t > 0.001F) {
                ui.alphaPush(t);
                drawContent(scheme, kind, text, badge, positive, islandWidth, islandHeight,
                        6.0F * (1.0F - t), gaugeFillAnim.value());
                ui.alphaPop();
            }
            if (kind == MUSIC) {
                drawMusicProgress(scheme, islandWidth, islandHeight, radius);
            }
            ui.scissorPop();
            ui.alphaPop();
            if (expansion > 0.01F) {
                ui.scissorPush(6.0F, 6.0F, islandWidth - 12.0F, islandHeight - 12.0F);
                ui.alphaPush(Material.clamp((expansion - 0.15F) / 0.65F, 0.0F, 1.0F));
                ui.push();
                ui.translate((islandWidth - tabList.width()) / 2.0F, (1.0F - expansion) * -8.0F);
                tabList.draw(ui, scheme, expansion);
                ui.pop();
                ui.alphaPop();
                ui.scissorPop();
            }
            ui.alphaPop();
        } finally {
            ui.pop();
            ui.end();
            GlStateManager.popMatrix();
            restoreHudState();
        }
    }

    private void drawMusicProgress(Scheme scheme, float islandWidth, float islandHeight, float radius) {
        float progress = Material.clamp(musicProgressAnim.value(), 0.0F, 1.0F);
        float stripY = islandHeight - 6.0F;
        ui.scissorPush(0.0F, stripY, islandWidth, 6.0F);
        ui.outline(0.0F, 0.0F, islandWidth, islandHeight, radius, 2.5F, scheme.surfaceContainerHighest);
        if (progress > 0.01F) {
            ui.scissorPush(0.0F, stripY, islandWidth * progress, 6.0F);
            ui.outline(0.0F, 0.0F, islandWidth, islandHeight, radius, 2.5F, scheme.primary);
            ui.scissorPop();
        }
        ui.scissorPop();
    }

    private int borderColor(Scheme scheme) {
        return Material.mix(scheme.outlineVariant, flashColor, Material.clamp(flash.value(), 0.0F, 1.0F));
    }

    private void drawContent(Scheme scheme, int contentKind, String contentText, String contentBadge,
                             boolean contentPositive, float islandWidth, float islandHeight,
                             float offsetX, float fill) {
        switch (contentKind) {
            case TOGGLE: {
                String label = ui.truncate(Typeface.LABEL_TIGHT, contentText, MAX_TEXT);
                ui.text(Typeface.LABEL_TIGHT, label, TEXT_X + offsetX, islandHeight / 2.0F,
                        scheme.onSurface, UI.ALIGN_LEFT);
                if (contentBadge != null && !contentBadge.isEmpty()) {
                    float badgeWidth = 10.0F + Typeface.CAPTION.width(contentBadge);
                    float badgeX = TEXT_X + offsetX + Typeface.LABEL_TIGHT.width(label) + 6.0F;
                    ui.rounded(badgeX, islandHeight / 2.0F - 6.0F, badgeWidth, 12.0F, 6.0F,
                            contentPositive ? scheme.primaryContainer : scheme.surfaceContainerHighest);
                    ui.text(Typeface.CAPTION, contentBadge, badgeX + badgeWidth / 2.0F, islandHeight / 2.0F,
                            contentPositive ? scheme.onPrimaryContainer : scheme.onSurfaceVariant,
                            UI.ALIGN_CENTER);
                }
                break;
            }
            case GAUGE: {
                float left = TEXT_X + 2.0F + offsetX;
                float right = islandWidth - 9.0F + offsetX;
                ui.text(Typeface.CAPTION, contentText, left, 11.0F, scheme.onSurfaceVariant, UI.ALIGN_LEFT);
                ui.text(Typeface.LABEL_TIGHT, contentBadge, right, 11.0F,
                        contentPositive ? scheme.onSurface : scheme.error, UI.ALIGN_RIGHT);
                float barY = islandHeight - 11.0F;
                float barWidth = Math.max(0.0F, right - left);
                WavyProgressIndicator.Style style = new WavyProgressIndicator.Style(
                        16.0F, 0.72F, 22.0F,
                        contentPositive ? scheme.primary : scheme.error,
                        scheme.surfaceContainerHighest);
                WavyProgressIndicator.drawLinear(ui, left, barY - 1.0F, barWidth, 6.0F,
                        Material.clamp(fill, 0.0F, 1.0F), style);
                break;
            }
            case MUSIC: {
                ui.icon(MUSIC_ICON, 15.35F + offsetX, islandHeight / 2.0F, 14.0F, scheme.primary);
                String label = ui.truncate(Typeface.LABEL_TIGHT, contentText, MAX_TEXT);
                ui.text(Typeface.LABEL_TIGHT, label, TEXT_X + offsetX, islandHeight / 2.0F,
                        scheme.onSurface, UI.ALIGN_LEFT);
                float barsX = TEXT_X + offsetX + Typeface.LABEL_TIGHT.width(label) + 6.0F;
                for (int i = 0; i < 3; i++) {
                    float level = musicPlaying
                            ? 0.5F + 0.5F * (float) Math.sin(phase * 0.007F + i * 2.1F)
                            : 0.1F;
                    float barHeight = 3.0F + 6.0F * level;
                    ui.rounded(barsX + i * 4.0F, islandHeight / 2.0F - barHeight / 2.0F, 2.0F, barHeight,
                            1.0F, scheme.primary);
                }
                break;
            }
            default:
                ui.text(Typeface.LABEL_TIGHT, contentText, TEXT_X + offsetX, islandHeight / 2.0F,
                        scheme.onSurface, UI.ALIGN_LEFT);
                break;
        }
    }

    private void update(WatermarkModule module, float delta, boolean preview) {
        phase = (phase + delta) % 100000.0F;
        boolean active = preview || module.isEnabled() || tabOpen.value() > 0.001F;
        visible.to(active ? 1.0F : 0.0F, active ? 300.0F : 200.0F, active ? Ease.EMPHASIZED : Ease.DECELERATE);
        visible.update(delta);
        flash.update(delta);

        if (active) {
            detectToggles(module, delta);
            updateSources(module);
            chooseContent();
        } else {
            hold = 0.0F;
            gaugeActive = false;
            bedActive = false;
            gappleActive = false;
            musicActive = false;
            states.clear();
        }

        contentWidth.to(Math.max(MIN_CONTENT, targetContentWidth()), 200.0F, Ease.EMPHASIZED_DECELERATE);
        contentWidth.update(delta);
        contentHeight.to(kind == GAUGE ? GAUGE_HEIGHT : HEIGHT, 200.0F, Ease.EMPHASIZED_DECELERATE);
        contentHeight.update(delta);
        morph.to(1.0F, 250.0F, Ease.EMPHASIZED_DECELERATE);
        morph.update(delta);
        gaugeFillAnim.to(gaugeFillTarget(), 250.0F, Ease.DECELERATE);
        gaugeFillAnim.update(delta);
        musicProgressAnim.to(musicActive ? NowPlaying.progress() : 0.0F, 250.0F, Ease.DECELERATE);
        musicProgressAnim.update(delta);
    }

    private void detectToggles(WatermarkModule module, float delta) {
        if (hold > 0.0F) {
            hold -= delta;
            if (hold < 0.0F) {
                hold = 0.0F;
            }
        }
        for (Module candidate : ModuleManager.modules) {
            if (!trackable(candidate, module)) {
                continue;
            }
            boolean enabled = candidate.isEnabled();
            Boolean before = states.put(candidate, Boolean.valueOf(enabled));
            if (before == null || before.booleanValue() == enabled) {
                continue;
            }
            if (!module.announceToggles()) {
                continue;
            }
            notifyText = Text.strip(candidate.getName());
            notifyBadge = enabled ? "ON" : "OFF";
            notifyPositive = enabled;
            hold = Math.max(0.1F, module.holdSeconds()) * 1000.0F;
            flashColor = enabled ? Theme.scheme().primary : Theme.scheme().error;
            flash.set(1.0F);
            flash.to(0.0F, 450.0F, Ease.DECELERATE);
        }
    }

    private static boolean trackable(Module candidate, WatermarkModule self) {
        if (candidate == null || candidate == self || candidate.noToggle || candidate.isProfile()) {
            return false;
        }
        // Hidden modules stay quiet: `.hide` removes them from the HUD element list and `.hidekey`
        // removes them from the Keybinds HUD, so neither should be announced on the island.
        if (candidate.hidden || KeybindsHidden.isHidden(candidate)) {
            return false;
        }
        String name = candidate.getName();
        return !"Gui".equals(name) && !"Onyx ClickGUI".equals(name) && !"Command Line".equals(name);
    }

    /** Collects the gauge sources and the now-playing session. */
    private void updateSources(WatermarkModule module) {
        Minecraft mc = Minecraft.getMinecraft();
        bedActive = false;
        if (module.bedProgress()) {
            Module bedAura = ModuleManager.get("BedAura");
            BlockPos target = BedAuraModule.target;
            if (bedAura != null && bedAura.isEnabled() && target != null && mc.theWorld != null) {
                Block block = mc.theWorld.getBlockState(target).getBlock();
                String label = block == null ? "Block" : block.getLocalizedName();
                if (label == null || label.isEmpty()) {
                    label = "Block";
                }
                bedLabel = label;
                bedPercent = Material.clamp(BedAuraModule.progress, 0.0F, 1.0F);
                bedActive = bedPercent > 0.01F;
            }
        }

        gaugeActive = false;
        if (module.scaffoldGauge()) {
            Module scaffold = ModuleManager.get("Scaffold");
            if (mc.thePlayer != null && scaffold != null && scaffold.isEnabled()) {
                int count = AutoSwapModule.countBlocks();
                gaugeCount = count;
                gaugeMax = Math.max(gaugeMax, count);
                gaugeActive = true;
                gaugeAlert = count <= module.lowBlockCount();
            }
        }

        // AutoGapple's own progress: the module no longer draws a screen bar, the island does.
        gappleActive = false;
        if (module.gappleProgress()) {
            Module gapple = ModuleManager.get("AutoGapple");
            if (gapple != null && gapple.isEnabled() && AutoGappleModule.isEating()) {
                gappleCount = AutoGappleModule.moving();
                gapplePercent = Material.clamp(AutoGappleModule.progress(), 0.0F, 1.0F);
                gappleActive = true;
            }
        }

        musicActive = module.nowPlaying() && NowPlaying.hasSession();
        if (musicActive) {
            musicTitle = musicLabel();
            musicPlaying = NowPlaying.playing();
        }
    }

    /**
     * The bundled Google Sans Flex has no CJK coverage, so non-Latin titles fall back to the
     * artist (usually Latin) and finally to a generic label instead of rendering blank glyphs.
     */
    private static String musicLabel() {
        String title = Text.strip(NowPlaying.title());
        if (Typeface.LABEL_TIGHT.supports(title)) {
            return title;
        }
        String artist = Text.strip(NowPlaying.artist());
        if (!artist.isEmpty() && Typeface.LABEL_TIGHT.supports(artist)) {
            return artist;
        }
        return "Now playing";
    }

    private void chooseContent() {
        int nextKind;
        String nextText;
        String nextBadge = "";
        boolean nextPositive = true;

        if (hold > 0.0F) {
            nextKind = TOGGLE;
            nextText = notifyText;
            nextBadge = notifyBadge;
            nextPositive = notifyPositive;
        } else if (musicActive) {
            nextKind = MUSIC;
            nextText = musicTitle;
        } else if (gappleActive) {
            nextKind = GAUGE;
            nextText = "Gapple";
            nextBadge = Math.min(gappleCount, AutoGappleModule.targetPackets())
                    + "/" + AutoGappleModule.targetPackets();
        } else if (bedActive) {
            nextKind = GAUGE;
            nextText = bedLabel;
            nextBadge = Math.round(bedPercent * 100.0F) + "%";
        } else if (gaugeActive) {
            nextKind = GAUGE;
            nextText = "Blocks";
            nextBadge = String.valueOf(gaugeCount);
            nextPositive = !gaugeAlert;
        } else {
            nextKind = IDLE;
            nextText = CLIENT_NAME;
        }

        boolean changed;
        if (nextKind != kind) {
            changed = true;
        } else if (nextKind == TOGGLE) {
            changed = !nextText.equals(text) || !nextBadge.equals(badge) || nextPositive != positive;
        } else if (nextKind == MUSIC) {
            changed = !nextText.equals(text);
        } else {
            // Gauge values are refreshed in place: Onyx only re-morphed on a state change, so the
            // block counter must not restart the island animation on every block.
            changed = false;
        }

        if (changed) {
            previousKind = kind;
            previousText = text;
            previousBadge = badge;
            previousPositive = positive;
            previousGaugeFill = gaugeFillAnim.value();
            kind = nextKind;
            text = nextText;
            badge = nextBadge;
            positive = nextPositive;
            morph.set(0.0F);
            morph.to(1.0F, 250.0F, Ease.EMPHASIZED_DECELERATE);
        } else {
            kind = nextKind;
            text = nextText;
            badge = nextBadge;
            positive = nextPositive;
        }
    }

    private float targetContentWidth() {
        switch (kind) {
            case TOGGLE: {
                float textWidth = Math.min(Typeface.LABEL_TIGHT.width(text), MAX_TEXT);
                if (badge.isEmpty()) {
                    return textWidth;
                }
                return textWidth + 6.0F + 10.0F + Typeface.CAPTION.width(badge);
            }
            case GAUGE:
                return Math.max(54.0F, Typeface.CAPTION.width(text) + 8.0F
                        + Typeface.LABEL_TIGHT.width(badge)) + 4.0F;
            case MUSIC:
                return Math.min(Typeface.LABEL_TIGHT.width(text), MAX_TEXT) + 6.0F + 10.0F;
            default:
                return Typeface.LABEL_TIGHT.width(text);
        }
    }

    private float gaugeFillTarget() {
        if (gappleActive) {
            return gapplePercent;
        }
        if (bedActive) {
            return bedPercent;
        }
        return gaugeActive ? Material.clamp(gaugeCount / (float) Math.max(1, gaugeMax), 0.0F, 1.0F) : 0.0F;
    }

    private float tick() {
        long now = System.nanoTime();
        float delta = lastFrame == 0L ? 16.0F : Math.min((now - lastFrame) / 1000000.0F, 100.0F);
        lastFrame = now;
        return delta;
    }

    // ------------------------------------------------------------------ //
    // Dragging (chat open) and the position editor
    // ------------------------------------------------------------------ //

    private void handleDrag(WatermarkModule module, Minecraft mc, ScaledResolution resolution) {
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

    /** Clears per-session state so re-enabling starts from a clean idle island. */
    public void reset() {
        states.clear();
        hold = 0.0F;
        gaugeActive = false;
        gaugeAlert = false;
        bedActive = false;
        gappleActive = false;
        musicActive = false;
        kind = IDLE;
        previousKind = IDLE;
        text = CLIENT_NAME;
        previousText = "";
        badge = "";
        previousBadge = "";
        positive = true;
        previousPositive = true;
        morph.set(1.0F);
        flash.set(0.0F);
        contentWidth.set(MIN_CONTENT);
        contentHeight.set(HEIGHT);
        gaugeFillAnim.set(0.0F);
        musicProgressAnim.set(0.0F);
        tabOpen.set(0.0F);
        dragging = false;
        lastFrame = 0L;
    }

    public void hideTabList() {
        tabOpen.set(0.0F);
    }

    /** Texture the island logo is uploaded under (used by the dev harness). */
    public static ResourceLocation logoResource() {
        return LOGO;
    }

    private static boolean ensureLogo() {
        if (logoLoaded) {
            return true;
        }
        logoLoaded = true;
        try (InputStream in = Island.class.getResourceAsStream("/assets/clickgui/textures/raven.png")) {
            if (in == null) {
                throw new IllegalStateException("missing raven icon");
            }
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IllegalStateException("unreadable raven icon");
            }
            Minecraft.getMinecraft().getTextureManager().loadTexture(LOGO, new DynamicTexture(image));
            return true;
        } catch (Throwable t) {
            System.err.println("[island] raven icon unavailable: " + t);
            return false;
        }
    }


    private static void restoreHudState() {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
