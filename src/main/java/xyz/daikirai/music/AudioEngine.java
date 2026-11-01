package xyz.daikirai.music;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.Obuffer;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * MP3 playback for the music player.
 *
 * <p>Melodify ran its own LWJGL3 OpenAL device on a private thread. Minecraft 1.8.9 already owns
 * an OpenAL context through LWJGL2, and making a second one current from another thread would
 * fight with the game's sound engine (either the game's sounds or the music would go silent,
 * depending on which thread called {@code alcMakeContextCurrent} last). So the port keeps the
 * same architecture — one private audio thread, streamed chunks, JLayer decoding — but writes the
 * PCM to a {@link SourceDataLine} instead: a separate Java Sound output that coexists with the
 * game's own mixer.</p>
 *
 * <p>The engine only ever plays a local file; downloading (and resolving the signed NetEase URL)
 * belongs to {@link MusicManager}, so a playlist change never blocks the audio thread.</p>
 */
public final class AudioEngine {

    /** Notified when a track reaches its end without being stopped or replaced. */
    public interface Listener {
        void onTrackEnded();
    }

    private static final int LINE_BUFFER_FRAMES = 8192;

    private final Object lock = new Object();

    private volatile boolean disposed;
    private volatile File file;
    private volatile boolean wantPlay;
    private volatile long seekRequestMs = -1L;
    private volatile float volume = 0.8F;
    private volatile float gain;
    private volatile long positionMs;
    private volatile long durationMs;
    private volatile float level;
    private volatile float peak;
    private volatile String error;
    private volatile boolean buffering;
    private volatile Listener listener;

    private Thread thread;
    private volatile SourceDataLine line;
    private volatile long trackSerial;

    public void setListener(Listener value) {
        this.listener = value;
    }

    public void setVolume(float value) {
        this.volume = clamp(value, 0.0F, 1.0F);
    }

    public float volume() {
        return volume;
    }

    public void setDurationMs(long value) {
        this.durationMs = Math.max(0L, value);
    }

    public long durationMs() {
        return durationMs;
    }

    public long positionMs() {
        return positionMs;
    }

    /** Smoothed RMS of the most recent chunk (0..1), used to pulse the lyric view. */
    public float level() {
        return level;
    }

    /** Instantaneous peak of the most recent chunk (0..1), decays over ~200ms. */
    public float peak() {
        return peak;
    }

    public boolean isBuffering() {
        return buffering;
    }

    public String error() {
        return error;
    }

    public boolean isPlaying() {
        return wantPlay && file != null;
    }

    public boolean hasTrack() {
        return file != null;
    }

    /** Starts (or replaces) the current track at {@code startAtMs}. */
    public void play(File target, long startAtMs) {
        if (target == null || !target.isFile()) {
            this.error = "missing audio file";
            return;
        }
        synchronized (lock) {
            this.file = target;
            this.seekRequestMs = startAtMs > 0L ? startAtMs : -1L;
            this.positionMs = Math.max(0L, startAtMs);
            this.error = null;
            this.buffering = true;
            this.wantPlay = true;
            this.gain = 0.0F;
            this.trackSerial++;
            ensureThread();
            lock.notifyAll();
        }
    }

    public void pause() {
        this.wantPlay = false;
    }

    public void resume() {
        if (file != null) {
            this.wantPlay = true;
        }
    }

    public void stop() {
        synchronized (lock) {
            this.wantPlay = false;
            this.file = null;
            this.buffering = false;
            this.positionMs = 0L;
            this.level = 0.0F;
            this.peak = 0.0F;
            this.trackSerial++;
            lock.notifyAll();
        }
    }

    public void seek(long ms) {
        long target = Math.max(0L, ms);
        this.seekRequestMs = target;
        // The worker has to decode-skip to the target; reflect it immediately so the bar does not
        // snap back to the old position while that happens.
        this.positionMs = target;
    }

    public void dispose() {
        disposed = true;
        stop();
        Thread worker = thread;
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void ensureThread() {
        if (thread != null && thread.isAlive()) {
            return;
        }
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "neoraven-music");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY + 1);
        thread = worker;
        worker.start();
    }

    private void loop() {
        while (!disposed) {
            File target;
            long serial;
            synchronized (lock) {
                while (!disposed && file == null) {
                    try {
                        lock.wait(200L);
                    } catch (InterruptedException e) {
                        if (disposed) {
                            return;
                        }
                    }
                }
                target = file;
                serial = trackSerial;
            }
            if (disposed || target == null) {
                continue;
            }
            try {
                runTrack(target, serial);
            } catch (Throwable t) {
                this.error = t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : ": " + t.getMessage());
                this.buffering = false;
                this.wantPlay = false;
                closeLine();
                sleep(160L);
            }
        }
        closeLine();
    }

    private void runTrack(File target, long serial) throws Exception {
        this.buffering = true;
        this.error = null;
        this.level = 0.0F;
        this.peak = 0.0F;

        Bitstream stream = null;
        SourceDataLine output = null;
        try {
            stream = new Bitstream(new BufferedInputStream(new FileInputStream(target), 256 * 1024));
            Header header = stream.readFrame();
            if (header == null) {
                throw new IOException("no mp3 frame");
            }
            Decoder decoder = new Decoder();
            SampleBuffer buffer = decode(decoder, header, stream);

            int frequency = decoder.getOutputFrequency() > 0 ? decoder.getOutputFrequency() : 44100;
            int channels = Math.max(1, Math.min(2, decoder.getOutputChannels()));
            long baseMs = 0L;

            long seek = this.seekRequestMs;
            this.seekRequestMs = -1L;
            if (seek > 0L) {
                long[] aligned = new long[1];
                Header targetHeader = skipTo(stream, frequency, seek, aligned);
                baseMs = aligned[0];
                if (targetHeader == null) {
                    return;
                }
                buffer = decode(decoder, targetHeader, stream);
                if (buffer == null) {
                    return;
                }
            }

            AudioFormat format = new AudioFormat(frequency, 16, channels, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            output = (SourceDataLine) AudioSystem.getLine(info);
            output.open(format, LINE_BUFFER_FRAMES * channels * 2);
            output.start();
            this.line = output;
            this.buffering = false;
            this.positionMs = baseMs;

            long playedFrames = 0L;
            byte[] pcm = new byte[1152 * 2 * 2];
            float gainStep = 256.0F / Math.max(1, frequency);

            while (!disposed) {
                if (serial != trackSerial) {
                    return;
                }
                long pendingSeek = this.seekRequestMs;
                if (pendingSeek >= 0L) {
                    this.seekRequestMs = -1L;
                    output.flush();
                    // Re-open the file: skipTo() counts frames from wherever the stream currently
                    // is, so skipping on the live stream made every mid-song seek land at a
                    // position that drifted further the longer the song had played.
                    Bitstream fresh = new Bitstream(
                            new BufferedInputStream(new FileInputStream(target), 256 * 1024));
                    Header freshHeader = fresh.readFrame();
                    if (freshHeader == null) {
                        fresh.close();
                        return;
                    }
                    decoder = new Decoder();
                    buffer = decode(decoder, freshHeader, fresh);
                    if (buffer == null) {
                        fresh.close();
                        return;
                    }
                    int freshChannels = Math.max(1, Math.min(2, decoder.getOutputChannels()));
                    if (freshChannels != channels) {
                        // A different channel layout cannot be written to the open line; let the
                        // track restart with its own format instead of feeding mismatched PCM.
                        fresh.close();
                        return;
                    }
                    closeQuietly(stream);
                    stream = fresh;
                    long[] aligned = new long[1];
                    Header targetHeader = skipTo(stream, frequency, pendingSeek, aligned);
                    baseMs = aligned[0];
                    playedFrames = 0L;
                    this.positionMs = baseMs;
                    this.gain = 0.0F;
                    if (targetHeader == null) {
                        return;
                    }
                    buffer = decode(decoder, targetHeader, stream);
                    if (buffer == null) {
                        return;
                    }
                    continue;
                }
                if (!wantPlay) {
                    sleep(25L);
                    this.level *= 0.70F;
                    this.peak *= 0.70F;
                    continue;
                }

                float targetGain = volume;
                this.gain = this.gain < targetGain
                        ? Math.min(targetGain, this.gain + gainStep)
                        : Math.max(targetGain, this.gain - gainStep);

                // JLayer's getBufferLength() counts *interleaved* samples: bufferp advances by
                // `channels` for every frame. Treating it as a frame count made the position run
                // at 2x on stereo, which is what put the progress bar far ahead of the audio.
                int interleaved = buffer.getBufferLength();
                int frames = Math.max(0, interleaved / Math.max(1, channels));
                int bytes = toPcm16(buffer.getBuffer(), interleaved, channels, this.gain, pcm);
                if (bytes > 0) {
                    output.write(pcm, 0, bytes);
                }
                playedFrames += frames;
                this.positionMs = baseMs + playedFrames * 1000L / frequency;
                updateLevel(buffer.getBuffer(), interleaved, channels);

                Header next = stream.readFrame();
                if (next == null) {
                    break;
                }
                buffer = decode(decoder, next, stream);
                if (buffer == null) {
                    break;
                }
            }
            output.drain();
        } finally {
            closeQuietly(stream);
            if (this.line == output) {
                this.line = null;
            }
            close(output);
        }

        boolean ended = !disposed && serial == trackSerial && wantPlay;
        if (ended) {
            this.positionMs = durationMs > 0L ? durationMs : this.positionMs;
            this.buffering = false;
            Listener callback = listener;
            if (callback != null) {
                try {
                    callback.onTrackEnded();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static SampleBuffer decode(Decoder decoder, Header header, Bitstream stream) throws Exception {
        if (header == null) {
            return null;
        }
        Obuffer decoded = decoder.decodeFrame(header, stream);
        stream.closeFrame();
        return decoded instanceof SampleBuffer ? (SampleBuffer) decoded : null;
    }

    /**
     * Reads and throws away frames until {@code targetMs}, leaving the bitstream on the first
     * frame that belongs to the target position. The returned header has <em>not</em> been
     * decoded yet, so the caller decodes it as the first frame to play.
     *
     * @param aligned receives the position the stream was actually aligned to
     */
    private static Header skipTo(Bitstream stream, int frequency, long targetMs, long[] aligned)
            throws Exception {
        long frames = 0L;
        Header header = stream.readFrame();
        while (header != null) {
            long position = frames * 1000L / Math.max(1, frequency);
            if (position >= targetMs) {
                aligned[0] = position;
                return header;
            }
            frames += header.version() == Header.MPEG1 ? 1152 : 576;
            stream.closeFrame();
            header = stream.readFrame();
        }
        aligned[0] = frames * 1000L / Math.max(1, frequency);
        return null;
    }

    private static int toPcm16(short[] source, int interleaved, int channels, float gain, byte[] out) {
        int count = interleaved;
        if (count > source.length) {
            count = source.length;
        }
        if (count * 2 > out.length) {
            count = out.length / 2;
        }
        for (int i = 0; i < count; i++) {
            int value = (int) (source[i] * gain);
            if (value > 32767) {
                value = 32767;
            } else if (value < -32768) {
                value = -32768;
            }
            out[i * 2] = (byte) (value & 0xFF);
            out[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return count * 2;
    }

    private void updateLevel(short[] source, int interleaved, int channels) {
        int count = Math.min(interleaved, source.length);
        if (count <= 0) {
            return;
        }
        double sum = 0.0D;
        int peakValue = 0;
        for (int i = 0; i < count; i++) {
            int value = source[i];
            sum += (double) value * (double) value;
            int absolute = value < 0 ? -value : value;
            if (absolute > peakValue) {
                peakValue = absolute;
            }
        }
        float rms = (float) Math.sqrt(sum / count) / 32768.0F;
        this.level += (rms - this.level) * 0.35F;
        this.peak = Math.max(peakValue / 32768.0F, this.peak * 0.82F);
    }

    private void closeLine() {
        SourceDataLine current = this.line;
        this.line = null;
        close(current);
    }

    private static void close(SourceDataLine output) {
        if (output == null) {
            return;
        }
        try {
            output.stop();
            output.flush();
            output.close();
        } catch (Throwable ignored) {
        }
    }

    private static void closeQuietly(Bitstream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }
}
