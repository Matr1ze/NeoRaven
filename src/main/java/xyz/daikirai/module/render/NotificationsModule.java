package xyz.daikirai.module.render;

import net.minecraft.block.BlockBed;
import net.minecraft.util.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.gui.NotificationsPositionEditor;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.gui.onyx.Text;
import xyz.daikirai.gui.onyx.hud.NotificationLevel;
import xyz.daikirai.gui.onyx.hud.KeybindsHidden;
import xyz.daikirai.gui.onyx.hud.Notifications;
import xyz.daikirai.gui.onyx.hud.NowPlaying;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.player.AutoSwapModule;
import xyz.daikirai.module.player.BedAuraModule;
import xyz.daikirai.module.player.Scaffold2Module;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.music.MusicManager;
import xyz.daikirai.music.Song;

import java.util.IdentityHashMap;
import java.util.Map;

/** Onyx Notifications, adapted to the shared HUD renderer and local client event sources. */
public final class NotificationsModule extends Module {
    public static NotificationsModule instance;
    private static final Notifications HUD = new Notifications();
    private static Object listener;
    public static SliderSetting scale, positionX, positionY, hold, maxStack, lowBlocks;
    public static ButtonSetting frostedGlass, editPosition, moduleToggles, scaffoldBlocks, bedBreaker,
            nowPlaying, progressBar, subtitle;

    private final Map<Module, Boolean> states = new IdentityHashMap<Module, Boolean>();
    private boolean primed, scaffoldLow, scaffoldEmpty;
    private BlockPos lastBed;
    private String lastSong = "";
    private Object world;

    public NotificationsModule(int key) {
        super("Notifications", Category.render, key);
        instance = this;
    }

    @Override public void link() {
        scale = slider("Scale"); positionX = slider("Position X"); positionY = slider("Position Y");
        hold = slider("Hold"); maxStack = slider("Max stack"); lowBlocks = slider("Low blocks");
        frostedGlass = button("Frosted glass"); editPosition = button("Edit position");
        moduleToggles = button("Module toggles"); scaffoldBlocks = button("Scaffold blocks");
        bedBreaker = button("Bed breaker"); nowPlaying = button("Now playing");
        progressBar = button("Progress bar"); subtitle = button("Subtitle");
        if (listener != null) return;
        listener = new Object() {
            @SubscribeEvent public void onTick(TickEvent.ClientTickEvent event) {
                if (event.phase != TickEvent.Phase.END || instance == null) return;
                WatermarkModule watermark = WatermarkModule.instance;
                NowPlaying.setEnabled(needsMedia() || watermark != null && watermark.isEnabled() && watermark.nowPlaying());
                if (instance.isEnabled()) instance.sampleEvents();
            }
            @SubscribeEvent public void onRender(TickEvent.RenderTickEvent event) {
                if (event.phase != TickEvent.Phase.END || instance == null) return;
                if (!Overlay.canRender()) { HUD.suspend(); return; }
                OnyxClickGuiModule.sync();
                HUD.render(instance, false);
            }
        };
        MinecraftForge.EVENT_BUS.register(listener);
    }

    private static SliderSetting slider(String name) { return (SliderSetting) ModuleManager.setting(instance, name); }
    private static ButtonSetting button(String name) { return (ButtonSetting) ModuleManager.setting(instance, name); }
    private static boolean on(ButtonSetting setting) { return setting != null && setting.isToggled(); }

    public static Notifications hud() { return HUD; }
    public static boolean needsMedia() {
        return instance != null && instance.isEnabled() && on(nowPlaying) && mc.theWorld != null;
    }
    public boolean frosted() { return on(frostedGlass); }
    public boolean subtitles() { return on(subtitle); }
    public boolean progress() { return on(progressBar); }
    public float scale() { return scale == null ? 0.85F : (float) scale.getInput(); }
    public float positionX() { return positionX == null ? 82.0F : (float) positionX.getInput(); }
    public float positionY() { return positionY == null ? 8.0F : (float) positionY.getInput(); }
    public float holdMs() { return hold == null ? 3000.0F : (float) hold.getInput() * 1000.0F; }
    public int maxStack() { return maxStack == null ? 5 : (int) maxStack.getInput(); }
    public void setPosition(float x, float y) {
        if (positionX != null) positionX.setValue(x);
        if (positionY != null) positionY.setValue(y);
    }

    /** Safe for client services that complete work off the render thread. */
    public static void post(String title, String message, NotificationLevel level) { post(title, message, level, null); }
    public static void post(final String title, final String message, final NotificationLevel level, final Integer accent) {
        mc.addScheduledTask(() -> {
            if (instance != null && instance.isEnabled() && mc.theWorld != null && mc.thePlayer != null) {
                HUD.post(title, message, level, accent, instance.holdMs(), instance.maxStack());
            }
        });
    }

    /** Share .hidekey with Keybinds and the island, including events queued off-thread. */
    private static void postModule(final Module source, final String message, final NotificationLevel level,
                                   final Integer accent) {
        mc.addScheduledTask(() -> {
            if (source != null && !KeybindsHidden.isHidden(source) && instance != null && instance.isEnabled()
                    && mc.theWorld != null && mc.thePlayer != null) {
                HUD.post(Text.strip(source.getName()), message, level, accent, instance.holdMs(), instance.maxStack());
            }
        });
    }

    @Override public void guiButtonToggled(ButtonSetting setting) {
        if (setting == editPosition) mc.displayGuiScreen(new NotificationsPositionEditor());
    }

    /** Draws the stack once from inside a screen (see {@code HudPass}). */
    public static void renderHud() {
        if (instance != null) {
            HUD.render(instance, false);
        }
    }

    @Override public void onEnable() { reset(); }
    @Override public void onDisable() { reset(); }
    private void reset() {
        HUD.reset(); states.clear(); primed = false; scaffoldLow = scaffoldEmpty = false;
        lastBed = null; lastSong = ""; world = null;
    }

    private void sampleEvents() {
        if (mc.theWorld == null || mc.thePlayer == null) { if (world != null) reset(); return; }
        if (world != mc.theWorld) { reset(); world = mc.theWorld; }
        if (on(moduleToggles)) {
            for (Module module : ModuleManager.modules) {
                if (module == this || module == ModuleManager.gui || module == ModuleManager.commandLine
                        || module instanceof OnyxClickGuiModule || module.noToggle || module.isProfile()) continue;
                boolean enabled = module.isEnabled();
                Boolean previous = states.put(module, enabled);
                if (primed && previous != null && previous != enabled) {
                    postModule(module, enabled ? "Enabled" : "Disabled",
                            enabled ? NotificationLevel.SUCCESS : NotificationLevel.INFO, null);
                }
            }
            states.keySet().retainAll(ModuleManager.modules);
            primed = true;
        } else { states.clear(); primed = false; }
        sampleScaffold();
        sampleBed();
        sampleMusic();
    }

    private void sampleScaffold() {
        Module scaffold = ModuleManager.get("Scaffold");
        boolean second = Scaffold2Module.instance != null && Scaffold2Module.instance.isEnabled();
        if (!on(scaffoldBlocks) || !second && (scaffold == null || !scaffold.isEnabled())) {
            scaffoldLow = scaffoldEmpty = false; return;
        }
        int count = second ? Scaffold2Module.blockCount() : AutoSwapModule.countBlocks();
        boolean low = count <= (lowBlocks == null ? 32 : lowBlocks.getInput());
        // Escalate a low-block warning once when the remaining stack reaches zero.
        if (low && (!scaffoldLow || count == 0 && !scaffoldEmpty)) {
            postModule(second ? Scaffold2Module.instance : scaffold, count <= 0 ? "Out of blocks" : count + " blocks left",
                    count <= 0 ? NotificationLevel.ERROR : NotificationLevel.WARNING, null);
        }
        scaffoldLow = low; scaffoldEmpty = count <= 0;
    }

    private void sampleBed() {
        Module bed = ModuleManager.get("BedAura");
        if (!on(bedBreaker) || bed == null || !bed.isEnabled()) { lastBed = null; return; }
        BlockPos target = BedAuraModule.target;
        if (lastBed != null && !lastBed.equals(target) && mc.theWorld.isBlockLoaded(lastBed)
                && !(mc.theWorld.getBlockState(lastBed).getBlock() instanceof BlockBed)) {
            postModule(bed, "Bed broken", NotificationLevel.SUCCESS, 0xFF6BE40B);
            lastBed = null;
        }
        if (target != null && mc.theWorld.getBlockState(target).getBlock() instanceof BlockBed) lastBed = target;
        else if (target == null) lastBed = null;
    }

    private void sampleMusic() {
        if (!on(nowPlaying)) { lastSong = ""; return; }
        Song song = MusicManager.get().current();
        String title = song != null ? song.name : NowPlaying.title();
        String artist = song != null ? song.artist : NowPlaying.artist();
        String key = song != null ? "internal:" + song.id : title + "\n" + artist;
        if (title != null && !title.trim().isEmpty()) {
            if (!key.equals(lastSong)) post(title, artist == null || artist.isEmpty() ? "Now playing" : artist, NotificationLevel.INFO);
            lastSong = key;
        } else lastSong = "";
    }
}
