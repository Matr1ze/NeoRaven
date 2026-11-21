package xyz.daikirai.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

public final class Sounds {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static String name = "note.pling";
    private static final float[] args = new float[2];
    private static boolean queued;
    /** Set while a profile is being read, so loading a config does not click once per module. */
    private static boolean suppressed;

    private Sounds() {
    }

    public static void queue(String s, float volume, float pitch) {
        if (!Chat.inWorld()) {
            return;
        }
        name = s;
        args[0] = volume;
        args[1] = pitch;
        queued = true;
    }

    public static void flush() {
        if (queued) {
            queued = false;
            play(name, args[0], args[1]);
        }
    }

    public static void play(String s, float volume, float pitch) {
        mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(s), volume, pitch,
                (float) mc.thePlayer.posX, (float) mc.thePlayer.posY, (float) mc.thePlayer.posZ));
    }

    /** Plays a UI sound ({@code assets/<domain>/sounds/<name>.ogg}) at the vanilla GUI sound level. */
    public static void playUi(String id, float pitch) {
        try {
            if (mc.getSoundHandler() == null) {
                return;
            }
            mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation(id), pitch));
        } catch (Throwable ignored) {
            // A missing sound file must never break a module toggle.
        }
    }

    /**
     * The module-toggle click. One sample plays when a module comes on, another when it goes off;
     * all of them live in {@code assets/clickgui/sounds} and are registered by
     * {@code assets/clickgui/sounds.json}.
     *
     * <p>The style is picked in the ClickGUI's Theme page ({@code Click GUI > Sound style}): the
     * synthesized tick, the vanilla lever click, or one of the three sample sets.</p>
     *
     * <p>When the samples are not visible to the sound manager (the dev workspace does not expose a
     * mod's assets to it) the missing set falls back to the tick, and the tick falls back to the
     * vanilla pling, so a toggle never goes silent.</p>
     */
    public static void toggle(boolean enabled) {
        if (suppressed || !xyz.daikirai.module.ClickGuiModule.toggleSound()) {
            return;
        }
        if (playStyle(xyz.daikirai.module.ClickGuiModule.soundStyle(), enabled)) {
            return;
        }
        playUi("note.pling", enabled ? 1.85F : 1.45F);
    }

    /**
     * Plays the "on" sample of the current style; used as a preview when the row changes. The
     * master switch still owns it: with toggle sounds off, picking a style stays silent.
     */
    public static void preview() {
        if (suppressed || !xyz.daikirai.module.ClickGuiModule.toggleSound()) {
            return;
        }
        if (!playStyle(xyz.daikirai.module.ClickGuiModule.soundStyle(), true)) {
            playUi("note.pling", 1.85F);
        }
    }

    /**
     * One toggle sample: 0 = tick, 1 = lever, 2 = smooth, 3 = celestial, 4 = nursultan.
     *
     * <p>Returns false when the chosen set is missing, so the caller can fall back.</p>
     */
    private static boolean playStyle(int style, boolean enabled) {
        if (style == 1) {
            // Exactly what BlockLever does: random.click, pitch 0.6 when the lever goes down and 0.5
            // when it flips back (vanilla plays it at 0.3 volume at the block position;
            // PositionedSoundRecord.create() uses the GUI level, 0.25).
            playUi("random.click", enabled ? 0.6F : 0.5F);
            return true;
        }
        String suffix = enabled ? "_on" : "_off";
        String set = style == 2 ? "smooth" : style == 3 ? "celestial" : style == 4 ? "nursultan" : null;
        if (set != null && playSample("clickgui:toggle_" + set + suffix)) {
            return true;
        }
        return playSample("clickgui:toggle" + suffix);
    }

    private static boolean playSample(String id) {
        try {
            if (mc.getSoundHandler() == null) {
                return false;
            }
            ResourceLocation location = new ResourceLocation(id);
            if (mc.getSoundHandler().getSound(location) == null) {
                return false;
            }
            mc.getSoundHandler().playSound(PositionedSoundRecord.create(location, 1.0F));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void suppress(boolean value) {
        suppressed = value;
    }

    public static float random(float min, float max) {
        return (float) ((double) min + (double) (max - min) * Math.random());
    }
}
