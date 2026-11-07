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
import xyz.daikirai.music.MusicIsland;
import xyz.daikirai.music.MusicManager;
import xyz.daikirai.music.MusicTypeface;
import xyz.daikirai.music.Song;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.combat.AutoGappleModule;
import xyz.daikirai.module.combat.Velocity2Module;
import xyz.daikirai.module.player.AutoSwapModule;
import xyz.daikirai.module.player.AutoBlockinModule;
import xyz.daikirai.module.player.BedAuraModule;
import xyz.daikirai.module.player.ChestStealerModule;
import xyz.daikirai.module.player.Scaffold2Module;
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
    /** How long a paused track keeps the island before the watermark comes back. */
    private static final long MUSIC_PAUSE_TIMEOUT_MS = 10000L;
    private static final float MARGIN = 10.0F;
    private static final float EDGE = 20.0F;
    /** Same frosted recipe as the keybind card: the surface tint covers 58% of the blurred scene. */
    private static final float FROST_COVERAGE = 0.58F;

    private static final int IDLE = 0;
    private static final int TOGGLE = 1;
    private static final int GAUGE = 2;
    private static final int MUSIC = 3;
    private static final int VELOCITY = 4;

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
    private final Anim miniOpen = new Anim(0.0F);
    private final Anim tabOpen = new Anim(0.0F);
    private final TabList tabList = new TabList();
    private final Anim chestOpen = new Anim(0.0F);
    private final ChestLoot chestLoot = new ChestLoot();
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
    private String bedLabel = xyz.daikirai.util.Lang.text("Bed");
    private float bedPercent;

    private boolean gappleActive;
    private int gappleCount;
    private float gapplePercent;

    private boolean blockinActive;
    private float blockinPercent;
    private String blockinLabel = xyz.daikirai.util.Lang.text("Blockin");

    private boolean musicActive;
    private boolean musicInternal;
    private String musicTitle = "";
    private boolean musicPlaying;

    private boolean velocityActive;
    private int velocityTicks;
    private int velocityPackets;

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
        boolean chestScreen = !preview && WatermarkModule.replacesChestScreen();
        boolean looting = chestLoot.update(chestScreen ? ChestStealerModule.instance.visualSession() : null, delta);
        if (preview || !WatermarkModule.chestStealerEnabled()) {
            looting = false;
            chestLoot.clear();
        }
        chestOpen.to(looting ? 1.0F : 0.0F, looting ? 420.0F : 280.0F,
                looting ? Ease.EMPHASIZED : Ease.STANDARD);
        chestOpen.update(delta);
        boolean held = !preview && chestOpen.value() < 0.001F && WatermarkModule.tablistHeld();
        if (looting) tabOpen.set(0.0F);
        if (preview || !WatermarkModule.replacesTablist()) {
            tabOpen.set(0.0F);
        }
        tabOpen.to(held ? 1.0F : 0.0F, held ? 420.0F : 280.0F,
                held ? Ease.EMPHASIZED : Ease.STANDARD);
        tabOpen.update(delta);
        update(module, delta, preview);
        MusicIsland.tick(delta);
        boolean mini = !preview && !looting && !held && MusicIsland.miniVisible();
        miniOpen.to(mini ? 1.0F : 0.0F, mini ? 460.0F : 420.0F, Ease.EMPHASIZED_DECELERATE);
        miniOpen.update(delta);

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

        float chestExpansion = chestOpen.value();
        float tabExpansion = tabOpen.value() * (1.0F - chestExpansion);
        float musicExpansion = miniOpen.value() * (1.0F - tabExpansion - chestExpansion);
        float expansion = tabExpansion + chestExpansion + musicExpansion;
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
        float lootScale = scale * WatermarkModule.chestStealerScaleFactor();
        float lootFit = Math.min(1.0F, Math.min((resolution.getScaledWidth() - EDGE) / (ChestLoot.WIDTH * lootScale),
                (resolution.getScaledHeight() - EDGE) / (ChestLoot.HEIGHT * lootScale)));
        scale += (tabScale * fit - scale) * tabExpansion + (lootScale * lootFit - scale) * chestExpansion;
        float islandWidth = pillWidth + (tabList.width() - pillWidth) * tabExpansion
                + (ChestLoot.WIDTH - pillWidth) * chestExpansion
                + (MusicIsland.miniWidth() - pillWidth) * musicExpansion;
        float islandHeight = pillHeight + (tabList.height() - pillHeight) * tabExpansion
                + (ChestLoot.HEIGHT - pillHeight) * chestExpansion
                + (MusicIsland.miniHeight() - pillHeight) * musicExpansion;
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
        boolean frosted = tabExpansion > 0.001F ? WatermarkModule.tablistFrosted() : module.frosted();

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
                drawContent(ui, scheme, previousKind, previousText, previousBadge, previousPositive,
                        islandWidth, islandHeight, -6.0F * t, previousGaugeFill);
                ui.alphaPop();
            }
            if (t > 0.001F) {
                ui.alphaPush(t);
                drawContent(ui, scheme, kind, text, badge, positive, islandWidth, islandHeight,
                        6.0F * (1.0F - t), gaugeFillAnim.value());
                ui.alphaPop();
            }
            if (kind == MUSIC) {
                drawMusicProgress(ui, scheme, islandWidth, islandHeight, radius);
            }
            ui.scissorPop();
            ui.alphaPop();
            if (musicExpansion > 0.001F) {
                ui.scissorPush(3.0F, 3.0F, islandWidth - 6.0F, islandHeight - 6.0F);
                MusicIsland.draw(ui, scheme, islandWidth, islandHeight, musicExpansion);
                ui.scissorPop();
            }
            if (tabExpansion > 0.01F) {
                ui.scissorPush(6.0F, 6.0F, islandWidth - 12.0F, islandHeight - 12.0F);
                ui.alphaPush(Material.clamp((tabExpansion - 0.15F) / 0.65F, 0.0F, 1.0F));
                ui.push();
                ui.translate((islandWidth - tabList.width()) / 2.0F, (1.0F - tabExpansion) * -8.0F);
                tabList.draw(ui, scheme, tabExpansion);
                ui.pop();
                ui.alphaPop();
                ui.scissorPop();
            }
            if (chestExpansion > 0.01F) {
                ui.scissorPush(6.0F, 6.0F, islandWidth - 12.0F, islandHeight - 12.0F);
                ui.alphaPush(Material.clamp((chestExpansion - 0.15F) / 0.65F, 0.0F, 1.0F));
                ui.push();
                ui.translate((islandWidth - ChestLoot.WIDTH) / 2.0F, (1.0F - chestExpansion) * -8.0F);
                chestLoot.draw(ui, scheme, chestExpansion);
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

    private void drawMusicProgress(UI ui, Scheme scheme, float islandWidth, float islandHeight, float radius) {
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

    private void drawContent(UI ui, Scheme scheme, int contentKind, String contentText, String contentBadge,
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
                Typeface.Style style = musicStyle();
                float maxText = musicTextMax();
                ui.icon(MUSIC_ICON, 15.35F + offsetX, islandHeight / 2.0F, 14.0F, scheme.primary);
                String label = ui.truncate(style, contentText, maxText);
                ui.text(style, label, TEXT_X + offsetX, islandHeight / 2.0F,
                        scheme.onSurface, UI.ALIGN_LEFT);
                float barsX = TEXT_X + offsetX + style.width(label) + 6.0F;
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
            case VELOCITY: {
                String label = ui.truncate(Typeface.LABEL_TIGHT, contentText, MAX_TEXT);
                ui.text(Typeface.LABEL_TIGHT, label, TEXT_X + offsetX, islandHeight / 2.0F,
                        scheme.primary, UI.ALIGN_LEFT);
                if (contentBadge != null && !contentBadge.isEmpty()) {
                    float badgeWidth = 10.0F + Typeface.CAPTION.width(contentBadge);
                    float badgeX = TEXT_X + offsetX + Typeface.LABEL_TIGHT.width(label) + 6.0F;
                    ui.rounded(badgeX, islandHeight / 2.0F - 6.0F, badgeWidth, 12.0F, 6.0F,
                            scheme.primaryContainer);
                    ui.text(Typeface.CAPTION, contentBadge, badgeX + badgeWidth / 2.0F, islandHeight / 2.0F,
                            scheme.onPrimaryContainer, UI.ALIGN_CENTER);
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
        boolean active = preview || module.isEnabled() || MusicIsland.hasTrack() || tabOpen.value() > 0.001F;
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
            blockinActive = false;
            musicActive = false;
            velocityActive = false;
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
        musicProgressAnim.to(musicActive ? (musicInternal ? MusicIsland.progress() : NowPlaying.progress()) : 0.0F,
                250.0F, Ease.DECELERATE);
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
            notifyText = Text.strip(candidate.localizedName());
            notifyBadge = enabled ? xyz.daikirai.util.Lang.text("ON") : xyz.daikirai.util.Lang.text("OFF");
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
                String label = block == null ? xyz.daikirai.util.Lang.text("Block") : block.getLocalizedName();
                if (label == null || label.isEmpty()) {
                    label = xyz.daikirai.util.Lang.text("Block");
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
            } else if (mc.thePlayer != null && Scaffold2Module.instance != null
                    && Scaffold2Module.instance.isEnabled()) {
                // Scaffold2 feeds the same gauge and stops drawing its own counter in that case.
                int count = Scaffold2Module.blockCount();
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

        // AutoBlockin's enclosure progress: this is the island gauge that replaced the old
        // "42%" text the module used to draw under the crosshair.
        blockinActive = false;
        Module blockin = ModuleManager.get("AutoBlockin");
        if (blockin != null && blockin.isEnabled() && AutoBlockinModule.active) {
            blockinPercent = Material.clamp(AutoBlockinModule.progress(), 0.0F, 1.0F);
            blockinActive = true;
        }

        // Same row as the old system now-playing state: the client's own player feeds it first,
        // the Windows media session is the fallback when nothing is loaded in the built-in player.
        refreshMusicSource();

        // Velocity2's whole point is holding S12 knockback packets for a few ticks; surface that
        // delay (and how many packets are queued) on the island while it is happening.
        velocityActive = false;
        if (module.velocityDelay()) {
            Module velocity = ModuleManager.get("Velocity2");
            Velocity2Module impl = Velocity2Module.instance;
            if (velocity != null && velocity.isEnabled() && impl != null && impl.delaying()) {
                velocityTicks = impl.delayTicks();
                velocityPackets = impl.delayPackets();
                velocityActive = true;
            }
        }
    }

    private String velocityTickLabel() {
        String label = velocityTicks + "t";
        return velocityPackets > 1 ? label + " ×" + velocityPackets : label;
    }

    /** Picks the source of the compact now-playing row: built-in player first, then SMTC. */
    private void refreshMusicSource() {
        musicActive = false;
        musicInternal = false;
        WatermarkModule module = WatermarkModule.instance;
        if (module != null && !module.nowPlaying()) {
            return;
        }
        MusicManager manager = MusicManager.get();
        manager.observePlayback();
        Song song = manager.current();
        if (song != null) {
            // A song left paused for a while gives the island back to the watermark: an advert for a
            // track nobody is listening to is worse than the client's own name.
            if (manager.pausedFor(MUSIC_PAUSE_TIMEOUT_MS)) {
                return;
            }
            musicInternal = true;
            musicPlaying = manager.isPlaying();
            // "Music: Display" chooses between the synced lyric line and the plain song title.
            String lyric = WatermarkModule.musicShowsLyric() ? MusicIsland.currentLyric() : "";
            musicTitle = lyric == null || lyric.trim().isEmpty()
                    ? musicLabel(Text.strip(song.name), Text.strip(song.artist))
                    : musicLabel(lyric.trim(), Text.strip(song.artist));
            musicActive = true;
            return;
        }
        if (NowPlaying.hasSession()) {
            musicTitle = musicLabel(Text.strip(NowPlaying.title()), Text.strip(NowPlaying.artist()));
            musicPlaying = NowPlaying.playing();
            musicActive = true;
        }
    }

    /**
     * The music row is part of the player, so it uses the player's own face; {@code ISLAND_ROW} is
     * the same MiSans Medium one step larger, because the face runs small at pill size.
     */
    private static Typeface.Style musicStyle() {
        return MusicTypeface.ISLAND_ROW;
    }

    /** A lyric line needs more room than a short song title before it gets truncated. */
    private static float musicTextMax() {
        return WatermarkModule.musicShowsLyric() ? 175.0F : 130.0F;
    }

    /**
     * Falls back to the artist, then to a generic label, when the face cannot render a title.
     * OPPO Sans covers both scripts, so this only fires for rare glyphs.
     */
    private static String musicLabel(String title, String artist) {
        if (!title.isEmpty() && musicStyle().supports(title)) {
            return title;
        }
        if (!artist.isEmpty() && musicStyle().supports(artist)) {
            return artist;
        }
        return xyz.daikirai.util.Lang.text("Now playing");
    }

    private void chooseContent() {
        int nextKind;
        String nextText;
        String nextBadge = "";
        boolean nextPositive = true;
        // The expanded mini player is the one music surface that keeps the island for itself. While
        // it is collapsed its lyric row must yield to the Scaffold block gauge and the BedAura
        // progress, or a playing song hides both of them for the whole match.
        boolean musicCard = musicActive && miniOpen.value() > 0.5F;

        if (hold > 0.0F) {
            nextKind = TOGGLE;
            nextText = notifyText;
            nextBadge = notifyBadge;
            nextPositive = notifyPositive;
        } else if (musicCard) {
            nextKind = MUSIC;
            nextText = musicTitle;
        } else if (velocityActive) {
            nextKind = VELOCITY;
            nextText = xyz.daikirai.util.Lang.module("Velocity");
            nextBadge = velocityTickLabel();
        } else if (gappleActive) {
            nextKind = GAUGE;
            nextText = xyz.daikirai.util.Lang.text("Gapple");
            nextBadge = Math.min(gappleCount, AutoGappleModule.targetPackets())
                    + "/" + AutoGappleModule.targetPackets();
        } else if (bedActive) {
            nextKind = GAUGE;
            nextText = bedLabel;
            nextBadge = Math.round(bedPercent * 100.0F) + "%";
        } else if (blockinActive) {
            nextKind = GAUGE;
            nextText = blockinLabel;
            nextBadge = Math.round(blockinPercent * 100.0F) + "%";
        } else if (gaugeActive) {
            nextKind = GAUGE;
            nextText = xyz.daikirai.util.Lang.text("Blocks");
            nextBadge = String.valueOf(gaugeCount);
            nextPositive = !gaugeAlert;
        } else if (musicActive) {
            nextKind = MUSIC;
            nextText = musicTitle;
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
                return Math.min(musicStyle().width(text), musicTextMax()) + 6.0F + 10.0F;
            case VELOCITY: {
                float textWidth = Math.min(Typeface.LABEL_TIGHT.width(text), MAX_TEXT);
                return textWidth + 6.0F + 10.0F + Typeface.CAPTION.width(badge);
            }
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
        if (blockinActive) {
            return blockinPercent;
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
        if (MusicIsland.hasTrack() && miniOpen.value() > 0.5F) {
            // The top artwork/title area remains draggable; transport controls own the lower half.
            float pointerY = resolution.getScaledHeight()
                    - Mouse.getY() * resolution.getScaledHeight() / (float) mc.displayHeight - 1.0F;
            if (pointerY > y + h * 0.44F) { dragging = false; return; }
        }
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
        blockinActive = false;
        musicActive = false;
        musicInternal = false;
        velocityActive = false;
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
        chestOpen.set(0.0F);
        miniOpen.set(0.0F);
        chestLoot.clear();
        dragging = false;
        lastFrame = 0L;
    }

    /** Primes the exact return endpoint before the GUI gives rendering back to the HUD. */
    public void returnFromMusic(boolean mini) {
        hold = 0.0F;
        refreshMusicSource();
        kind = musicActive ? MUSIC : IDLE;
        previousKind = kind;
        text = musicActive ? musicTitle : CLIENT_NAME;
        previousText = text;
        badge = "";
        previousBadge = "";
        positive = true;
        previousPositive = true;
        visible.set(1.0F);
        morph.set(1.0F);
        contentWidth.set(targetContentWidth());
        contentHeight.set(HEIGHT);
        miniOpen.set(mini && musicActive ? 1.0F : 0.0F);
        tabOpen.set(0.0F);
        chestOpen.set(0.0F);
        dragging = false;
        lastFrame = 0L;
    }

    /** Design-space size of the surface (before the HUD scale is applied). */
    public float designWidth() {
        float pill = FIXED_WIDTH + contentWidth.value();
        float expansion = Material.clamp(miniOpen.value(), 0.0F, 1.0F);
        return pill + (MusicIsland.miniWidth() - pill) * expansion;
    }

    public float designHeight() {
        float pill = contentHeight.value();
        float expansion = Material.clamp(miniOpen.value(), 0.0F, 1.0F);
        return pill + (MusicIsland.miniHeight() - pill) * expansion;
    }

    /**
     * Physical-pixel rectangle the island occupies for its current state, using the same math as
     * {@link #render}. The player window starts and ends its morph on exactly this rect.
     */
    public float[] measure() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        WatermarkModule module = WatermarkModule.instance;
        float scale = module == null ? 1.0F : module.islandScale();
        float designWidth = designWidth();
        float designHeight = designHeight();
        scale = Math.min(scale, Math.min((resolution.getScaledWidth() - EDGE) / designWidth,
                (resolution.getScaledHeight() - EDGE) / designHeight));
        float width = designWidth * scale;
        float height = designHeight * scale;
        float maxX = Math.max(0.0F, resolution.getScaledWidth() - width - EDGE);
        float maxY = Math.max(0.0F, resolution.getScaledHeight() - height - EDGE);
        float px = module == null ? 50.0F : Material.clamp(module.positionX(), 0.0F, 100.0F);
        float py = module == null ? 0.0F : Material.clamp(module.positionY(), 0.0F, 100.0F);
        return new float[] {MARGIN + maxX * px / 100.0F, MARGIN + maxY * py / 100.0F, width, height};
    }

    /**
     * Draws the island's current content into another UI so the player window can start its morph
     * from the exact HUD artwork that was on screen a frame earlier.
     */
    public void snapshot(UI target, Scheme scheme, boolean logo) {
        float designWidth = designWidth();
        float designHeight = designHeight();
        if (logo && ensureLogo()) {
            target.image(LOGO, 7.0F + (LOGO_WIDTH - LOGO_HEIGHT) / 2.0F, (designHeight - LOGO_HEIGHT) / 2.0F,
                    LOGO_HEIGHT, LOGO_HEIGHT, 0.0F, 0.0F, 1.0F, 1.0F, scheme.primary);
        }
        drawContent(target, scheme, kind, text, badge, positive, designWidth, designHeight, 0.0F,
                gaugeFillAnim.value());
        if (kind == MUSIC) {
            drawMusicProgress(target, scheme, designWidth, designHeight, designHeight / 2.0F);
        }
    }

    /** Handles transport clicks before chat receives them. Header drags still use handleDrag(). */
    public boolean musicClick(float mouseX, float mouseY) {
        if (!MusicIsland.hasTrack() || miniOpen.value() < 0.98F || !inside(mouseX, mouseY)) return false;
        // The card is drawn in its own 296x130 design space, so the click map stays in that space.
        float localX = (mouseX - x) * MusicIsland.MINI_WIDTH / w;
        float localY = (mouseY - y) * MusicIsland.MINI_HEIGHT / h;
        return MusicIsland.click(localX, localY);
    }

    public void hideTabList() {
        tabOpen.set(0.0F);
        chestOpen.set(0.0F);
        chestLoot.clear();
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
