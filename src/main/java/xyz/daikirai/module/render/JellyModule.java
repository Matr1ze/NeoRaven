package xyz.daikirai.module.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.client.ClientConfig;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.music.AudioEngine;
import xyz.daikirai.render.JellyAnimation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * "Jelly" — the friends wine doll effect, client side only.
 *
 * <p>While it is on, living entities around the player get the doll's squash-and-rotate treatment
 * and the doll's song loops locally. Ported from the mod's {@code JellyAnimation} /
 * {@code EntityAnimation} / {@code DollRenderer.applyAnimation} (1.21.1 NeoForge), with the doll
 * block replaced by "everything within {@code Range}": there is nothing to place or right-click in
 * a 1.8.9 client module, and the animation only ever needed a clock and a target list.</p>
 *
 * <p>Two differences from upstream, both deliberate:</p>
 * <ul>
 *   <li>The clock is the song's own playback position while the song plays, so the bounce lands on
 *       the beat; with {@code Music} off it falls back to the wall clock and keeps bouncing.</li>
 *   <li>The audio path is the client's existing Java Sound engine (same one the music player uses)
 *       instead of a {@code SoundInstance}, because 1.8.9's sound engine cannot seek and cannot
 *       report a position.</li>
 * </ul>
 *
 * <p>Everything here is local: no packets, no collision box, no server-side change.</p>
 */
public final class JellyModule extends Module {

    public static JellyModule instance;

    private static final String BUNDLED_SONG = "/assets/clickgui/sounds/jelly_music.mp3";
    private static final String SONG_NAME = "jelly_music.mp3";
    /** Distance from {@code RendererLivingEntity}'s model origin down to the model's foot line. */
    private static final double MODEL_FOOT_LINE = 1.5078125;

    private SliderSetting compression;
    private SliderSetting width;
    private SliderSetting rotationSpeed;
    private SliderSetting range;
    private SliderSetting volume;
    private ButtonSetting players;
    private ButtonSetting mobs;
    private ButtonSetting self;
    private ButtonSetting music;

    private AudioEngine audio;
    private File song;
    private boolean songLookupFailed;
    /** Wall-clock nanosecond origin for the no-music bounce clock. */
    private long clock;
    /** Wall-clock nanosecond origin that {@code positionMs() == 0} corresponds to (0 = unanchored). */
    private long musicAnchor;

    public JellyModule(int key) {
        super("Friendswine", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        compression = slider("Compression");
        width = slider("Width");
        rotationSpeed = slider("Rotate speed");
        range = slider("Range");
        volume = slider("Volume");
        players = button("Players");
        mobs = button("Mobs");
        self = button("Self");
        music = button("Music");
    }

    private SliderSetting slider(String name) {
        return (SliderSetting) ModuleManager.setting(this, name);
    }

    private ButtonSetting button(String name) {
        return (ButtonSetting) ModuleManager.setting(this, name);
    }

    @Override
    public void onEnable() {
        clock = System.nanoTime();
        musicAnchor = 0L;
        song = null;
        songLookupFailed = false;
        if (music.isToggled()) {
            startMusic();
        }
    }

    @Override
    public void onDisable() {
        stopMusic();
        if (audio != null) {
            // Release the Java Sound line instead of leaving a suspended daemon thread behind.
            audio.dispose();
            audio = null;
        }
    }

    @Override
    public String getInfo() {
        return compression == null ? null : (int) Math.round(compression.getInput()) + "%";
    }

    /** Keeps the audio device in step with the two settings that can change while it plays. */
    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!music.isToggled()) {
            stopMusic();
            return;
        }
        startMusic();
        AudioEngine engine = audio;
        if (engine != null) {
            engine.setVolume((float) (volume.getInput() / 100.0));
        }
    }

    private void startMusic() {
        if (song == null && !songLookupFailed) {
            song = resolveSong();
            songLookupFailed = song == null;
        }
        if (song == null) {
            return;
        }
        if (audio == null) {
            final AudioEngine engine = new AudioEngine();
            engine.setVolume((float) (volume.getInput() / 100.0));
            engine.setListener(new AudioEngine.Listener() {
                @Override
                public void onTrackEnded() {
                    // Same song again: upstream loops the doll's music, and the animation clock is
                    // read from playback position, so a fresh play() is all the loop needs.
                    File target = song;
                    if (target != null && isEnabled() && music.isToggled()) {
                        engine.play(target, 0L);
                    }
                }
            });
            audio = engine;
        }
        if (!audio.hasTrack()) {
            audio.play(song, 0L);
        }
    }

    private void stopMusic() {
        AudioEngine engine = audio;
        if (engine != null) {
            engine.stop();
        }
    }

    /**
     * The song ships inside the jar and is copied next to the client config on first use, so a user
     * can swap it by dropping a file named {@code jelly_music.mp3} into {@code keystrokes\}.
     */
    private static File resolveSong() {
        File directory = new File(ClientConfig.DIR);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            return null;
        }
        File target = new File(directory, SONG_NAME);
        if (target.isFile() && target.length() > 4096L) {
            return target;
        }
        InputStream in = null;
        try {
            in = JellyModule.class.getResourceAsStream(BUNDLED_SONG);
            if (in == null) {
                return null;
            }
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
        return target.isFile() && target.length() > 4096L ? target : null;
    }

    /** True while the song is actually advancing, so the render hook can trust the position. */
    private boolean musicDriven() {
        AudioEngine engine = audio;
        // error != null means the decoder/line died: positionMs would freeze, and re-anchoring to a
        // frozen clock once a second would make the bounce stutter at 1 Hz.
        return music.isToggled() && engine != null && engine.isPlaying()
                && !engine.isBuffering() && engine.error() == null && engine.positionMs() > 0L;
    }

    /**
     * Animation clock, in seconds.
     *
     * <p>{@code AudioEngine.positionMs()} only moves when a whole MP3 frame has been decoded —
     * 1152 samples at 44.1 kHz, so it steps every ~26 ms (measured on a recording: a jump every
     * ~47 ms). Using it directly as the phase made the model sit still for a few frames and then
     * snap ~19 degrees at 600+ fps: that freeze-and-jump is what looked "stuttery". So the phase is
     * a continuous wall clock; the song position only supplies the anchor, and a large disagreement
     * (start of playback, song loop, seek) re-anchors it. Between anchors the two clocks cannot
     * drift apart enough to matter inside one 56-second song.</p>
     */
    private double seconds() {
        long now = System.nanoTime();
        if (!musicDriven()) {
            musicAnchor = 0L;
            return (now - clock) / 1.0e9;
        }
        long audioMs = audio.positionMs();
        if (musicAnchor == 0L || Math.abs(audioMs / 1000.0 - (now - musicAnchor) / 1.0e9) > 1.0) {
            musicAnchor = now - audioMs * 1000000L;
        }
        return (now - musicAnchor) / 1.0e9;
    }

    /** Exposed for the visual probe: the clock the renderer is actually using. */
    public static double animationSeconds() {
        JellyModule module = instance;
        return module == null ? 0.0 : module.seconds();
    }

    /** Called from the renderer hook; false keeps the vanilla matrix untouched. */
    public static boolean applies(EntityLivingBase entity) {
        JellyModule module = instance;
        if (module == null || !module.isEnabled() || entity == null || entity.isDead
                || !entity.isEntityAlive()) {
            return false;
        }
        EntityPlayer player = mc.thePlayer;
        if (player == null) {
            return false;
        }
        if (entity == player) {
            if (!module.self.isToggled()) {
                return false;
            }
        } else if (entity instanceof EntityPlayer) {
            if (!module.players.isToggled()) {
                return false;
            }
        } else if (!module.mobs.isToggled()) {
            return false;
        }
        double reach = module.range.getInput();
        return entity == player || (double) entity.getDistanceSqToEntity(player) <= reach * reach;
    }

    /**
     * Applies the doll transform in the entity's own render space. The caller has already
     * translated to the entity's feet and applied {@code scale(-1,-1,1)}, so a plain scale here
     * squashes around the feet and a Y rotation turns the model in place.
     */
    public static void transform() {
        JellyModule module = instance;
        if (module == null) {
            return;
        }
        double seconds = module.seconds();
        float squash = JellyAnimation.squash(seconds);
        double speed = module.rotationSpeed.getInput();
        if (speed > 0.0) {
            // The model space is mirrored in Y, so a local rotation reads as the opposite world
            // rotation; negate to keep the doll's direction.
            GlStateManager.rotate(-JellyAnimation.angle(seconds * speed), 0.0F, 1.0F, 0.0F);
        }
        float height = JellyAnimation.heightScale(squash, module.compression.getInput());
        // {@code RendererLivingEntity} shifts the model down by this much (its own constant) before
        // drawing, so the model's render origin sits above the feet. Scaling about that origin lifts
        // the entity off the ground; move the pivot down to the foot line first, then scale.
        GlStateManager.translate(0.0F, (float) (MODEL_FOOT_LINE * (1.0 - height)), 0.0F);
        GlStateManager.scale(
                JellyAnimation.widthScale(squash, module.width.getInput()),
                height,
                1.0F);
    }
}
