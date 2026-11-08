package xyz.daikirai.music;

import net.minecraft.client.Minecraft;
import xyz.daikirai.client.ClientConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * State and orchestration for the music player: the queue, the current track, the lyrics, the
 * NetEase session and the QR login handshake.
 *
 * <p>The render thread only ever reads volatile fields; everything that touches the network runs
 * on the I/O pool and publishes its result. That keeps {@code drawScreen} free of blocking calls
 * (Melodify used {@code CompletableFuture} chains for the same reason — Java 8 has them, but a
 * plain executor is easier to reason about here).</p>
 */
public final class MusicManager {

    public enum Repeat {
        OFF, ALL, ONE
    }

    public enum QrState {
        IDLE, LOADING, WAITING, SCANNED, SUCCESS, EXPIRED, ERROR
    }

    private static final MusicManager INSTANCE = new MusicManager();

    private final AudioEngine engine = new AudioEngine();
    private final ExecutorService io = Executors.newFixedThreadPool(3, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "neoraven-music-io");
            thread.setDaemon(true);
            return thread;
        }
    });
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "neoraven-music-qr");
            thread.setDaemon(true);
            return thread;
        }
    });
    private final Random random = new Random();
    private final List<Song> queue = Collections.synchronizedList(new ArrayList<Song>());

    private volatile Song current;
    private volatile int cursor = -1;
    private volatile boolean loading;
    /** Wall clock of the moment the current track was paused; 0 while it is playing. */
    private volatile long pausedSince;
    private volatile float downloadProgress;
    private volatile String playbackError;
    private volatile Repeat repeat = Repeat.ALL;
    private volatile boolean shuffle;
    private volatile long playSerial;
    private volatile String streamLevel = "exhigh";

    private volatile List<LyricLine> lyrics = Collections.emptyList();
    private volatile boolean lyricsLoading;
    private volatile long lyricsSongId = -1L;

    private volatile List<Playlist> home = Collections.emptyList();
    private volatile boolean homeLoading;
    private volatile List<Playlist> myPlaylists = Collections.emptyList();
    private volatile boolean myPlaylistsLoading;

    private volatile List<Song> playlistTracks = Collections.emptyList();
    private volatile String playlistTitle = "";
    private volatile long playlistId = -1L;
    private volatile boolean playlistLoading;

    private volatile List<Song> searchResults = Collections.emptyList();
    private volatile String searchQuery = "";
    private volatile boolean searching;
    private volatile String searchError;
    private volatile long searchSerial;

    private volatile Profile profile;
    private volatile boolean profileLoading;
    private volatile QrState qrState = QrState.IDLE;
    private volatile boolean[][] qrMatrix;
    private volatile long qrCreatedAt;
    private volatile String qrError;
    private volatile String sessionError;

    private volatile float lyricScale = 1.0F;
    private volatile boolean lyricAnimation = true;
    private volatile boolean coverTint = true;

    /**
     * "3D lyrics" (ported from DangoMusic) settings. They live here instead of in a module so the
     * player window's settings page owns them; {@link WorldLyrics} reads this straight out of the
     * manager, and {@link #saveSettings()} persists them next to the volume.
     */
    public final Lyrics3DSettings lyrics3d = new Lyrics3DSettings();

    private boolean settingsLoaded;

    private MusicManager() {
        engine.setListener(new AudioEngine.Listener() {
            @Override
            public void onTrackEnded() {
                handleTrackEnded();
            }
        });
    }

    public static MusicManager get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ //
    // Playback
    // ------------------------------------------------------------------ //

    public AudioEngine engine() {
        return engine;
    }

    public Song current() {
        return current;
    }

    public boolean isLoadingTrack() {
        return loading;
    }

    public float downloadProgress() {
        return downloadProgress;
    }

    public String playbackError() {
        return playbackError;
    }

    public long positionMs() {
        return engine.positionMs();
    }

    public long durationMs() {
        long duration = engine.durationMs();
        if (duration <= 0L && current != null) {
            return current.durationMs;
        }
        return duration;
    }

    public boolean isPlaying() {
        return engine.isPlaying();
    }

    /**
     * Timestamps the moment playback stops on a loaded track, so the HUD can retire the now-playing
     * row after a while instead of advertising a paused song forever. Call it once per frame; it is
     * cheap and idempotent.
     */
    public void observePlayback() {
        if (isPlaying() || current == null) {
            pausedSince = 0L;
        } else if (pausedSince == 0L) {
            pausedSince = System.currentTimeMillis();
        }
    }

    /** True once a loaded track has been paused for at least {@code millis} milliseconds. */
    public boolean pausedFor(long millis) {
        return pausedSince != 0L && System.currentTimeMillis() - pausedSince >= millis;
    }

    public Repeat repeat() {
        return repeat;
    }

    public void cycleRepeat() {
        repeat = repeat == Repeat.OFF ? Repeat.ALL : repeat == Repeat.ALL ? Repeat.ONE : Repeat.OFF;
        saveSettings();
    }

    public boolean shuffle() {
        return shuffle;
    }

    public void toggleShuffle() {
        shuffle = !shuffle;
        saveSettings();
    }

    public String streamLevel() {
        return streamLevel;
    }

    public void setStreamLevel(String value) {
        streamLevel = value;
        saveSettings();
    }

    public int outputMode() {
        return engine.outputMode();
    }

    public void setOutputMode(int value) {
        engine.setOutputMode(value);
        saveSettings();
    }

    public String activeOutput() {
        return engine.activeOutput();
    }

    /** Replaces the queue and starts playing {@code song}. */
    public void play(Song song, List<Song> context) {
        if (song == null) {
            return;
        }
        queue.clear();
        if (context != null && !context.isEmpty()) {
            queue.addAll(context);
        }
        if (queue.isEmpty()) {
            queue.add(song);
        }
        int position = -1;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).id == song.id) {
                position = i;
                break;
            }
        }
        if (position < 0) {
            queue.add(0, song);
            position = 0;
        }
        cursor = position;
        current = song;
        startCurrent();
    }

    public void togglePlay() {
        if (current == null) {
            return;
        }
        if (engine.isPlaying()) {
            engine.pause();
        } else if (engine.hasTrack()) {
            engine.resume();
        } else {
            startCurrent();
        }
    }

    public void next() {
        advance(1);
    }

    public void previous() {
        advance(-1);
    }

    private void advance(int step) {
        if (queue.isEmpty()) {
            return;
        }
        if (shuffle && queue.size() > 1) {
            int next = random.nextInt(queue.size() - 1);
            cursor = next >= cursor ? next + 1 : next;
        } else {
            cursor = (cursor + step + queue.size()) % queue.size();
        }
        current = queue.get(cursor);
        startCurrent();
    }

    private void handleTrackEnded() {
        if (repeat == Repeat.ONE) {
            engine.seek(0L);
            engine.resume();
            return;
        }
        if (cursor + 1 < queue.size()) {
            advance(1);
        } else if (repeat == Repeat.ALL) {
            cursor = -1;
            advance(1);
        }
    }

    public void seek(long ms) {
        engine.seek(Math.max(0L, ms));
    }

    public void setVolume(float volume) {
        engine.setVolume(volume);
    }

    public void commitVolume() {
        saveSettings();
    }

    public float lyricScale() {
        return lyricScale;
    }

    public void setLyricScale(float value) {
        lyricScale = Math.max(0.8F, Math.min(1.4F, value));
        saveSettings();
    }

    public boolean lyricAnimation() {
        return lyricAnimation;
    }

    public void setLyricAnimation(boolean value) {
        lyricAnimation = value;
        saveSettings();
    }

    public boolean coverTint() {
        return coverTint;
    }

    public void setCoverTint(boolean value) {
        coverTint = value;
        saveSettings();
    }

    public float volume() {
        return engine.volume();
    }

    private void startCurrent() {
        final Song song = current;
        if (song == null) {
            return;
        }
        final long serial = ++playSerial;
        loading = true;
        downloadProgress = 0.0F;
        playbackError = null;
        engine.setDurationMs(song.durationMs);
        lyrics = Collections.emptyList();
        lyricsSongId = -1L;
        loadLyrics(song);
        io.execute(new Runnable() {
            @Override
            public void run() {
                File file = null;
                try {
                    file = cachedFile(song);
                    if (!isUsable(file)) {
                        NeteaseClient.Stream stream = NeteaseClient.streamUrl(song.id, streamLevel);
                        if (!stream.valid()) {
                            throw new IllegalStateException("无法获取播放地址（可能无版权或需要会员）");
                        }
                        download(stream.url, file, serial);
                        if (stream.durationMs > 0L) {
                            engine.setDurationMs(stream.durationMs);
                        }
                    }
                    if (serial != playSerial) {
                        return;
                    }
                    engine.play(file, 0L);
                } catch (Throwable t) {
                    if (serial == playSerial) {
                        playbackError = message(t);
                    }
                } finally {
                    if (serial == playSerial) {
                        loading = false;
                        downloadProgress = 0.0F;
                    }
                }
            }
        });
    }

    private void loadLyrics(final Song song) {
        lyricsLoading = true;
        io.execute(new Runnable() {
            @Override
            public void run() {
                List<LyricLine> parsed = Collections.emptyList();
                try {
                    NeteaseClient.LyricPayload payload = NeteaseClient.lyrics(song.id);
                    parsed = LyricsParser.parse(payload.yrc, payload.lrc, payload.translation,
                            payload.romanisation);
                } catch (Throwable ignored) {
                }
                if (current != null && current.id == song.id) {
                    lyrics = parsed;
                    lyricsSongId = song.id;
                    lyricsLoading = false;
                }
            }
        });
    }

    public List<LyricLine> lyrics() {
        return lyrics;
    }

    public boolean lyricsLoading() {
        return lyricsLoading;
    }

    // ------------------------------------------------------------------ //
    // Library
    // ------------------------------------------------------------------ //

    public List<Playlist> home() {
        return home;
    }

    public boolean homeLoading() {
        return homeLoading;
    }

    public List<Playlist> myPlaylists() {
        return myPlaylists;
    }

    public boolean myPlaylistsLoading() {
        return myPlaylistsLoading;
    }

    public List<Song> playlistTracks() {
        return playlistTracks;
    }

    public String playlistTitle() {
        return playlistTitle;
    }

    public boolean playlistLoading() {
        return playlistLoading;
    }

    public List<Song> searchResults() {
        return searchResults;
    }

    public String searchQuery() {
        return searchQuery;
    }

    public boolean searching() {
        return searching;
    }

    public String searchError() {
        return searchError;
    }

    public void loadHome() {
        if (homeLoading) {
            return;
        }
        homeLoading = true;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    home = NeteaseClient.personalized(12);
                } catch (Throwable ignored) {
                } finally {
                    homeLoading = false;
                }
            }
        });
    }

    public void loadMyPlaylists() {
        final Profile account = profile;
        if (account == null || myPlaylistsLoading) {
            return;
        }
        myPlaylistsLoading = true;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    myPlaylists = NeteaseClient.userPlaylists(account.userId, 50);
                } catch (Throwable ignored) {
                } finally {
                    myPlaylistsLoading = false;
                }
            }
        });
    }

    public void openPlaylist(final long id, final String title) {
        playlistId = id;
        playlistTitle = title == null ? "" : title;
        playlistTracks = Collections.emptyList();
        playlistLoading = true;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Song> tracks = NeteaseClient.playlistTracks(id, 300);
                    if (playlistId == id) {
                        playlistTracks = tracks;
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (playlistId == id) {
                        playlistLoading = false;
                    }
                }
            }
        });
    }

    public void search(final String query) {
        final String trimmed = query == null ? "" : query.trim();
        final long serial = ++searchSerial;
        searchQuery = trimmed;
        if (trimmed.isEmpty()) {
            searchResults = Collections.emptyList();
            searching = false;
            return;
        }
        searching = true;
        searchError = null;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Song> result = NeteaseClient.search(trimmed, 30);
                    if (serial == searchSerial) {
                        searchResults = result;
                    }
                } catch (Throwable t) {
                    if (serial == searchSerial) {
                        searchError = message(t);
                    }
                } finally {
                    if (serial == searchSerial) {
                        searching = false;
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ //
    // Account / login
    // ------------------------------------------------------------------ //

    public Profile profile() {
        return profile;
    }

    public boolean profileLoading() {
        return profileLoading;
    }

    public QrState qrState() {
        return qrState;
    }

    public boolean[][] qrMatrix() {
        return qrMatrix;
    }

    public long qrAgeMs() {
        return qrCreatedAt == 0L ? 0L : System.currentTimeMillis() - qrCreatedAt;
    }

    public String qrError() {
        return qrError;
    }

    public String sessionError() {
        return sessionError;
    }

    /** Restores the saved cookie jar and refreshes the profile. */
    public void boot() {
        if (settingsLoaded) {
            refreshProfile();
            return;
        }
        settingsLoaded = true;
        loadSettings();
        refreshProfile();
        loadHome();
    }

    public void refreshProfile() {
        if (!NeteaseHttp.loggedIn()) {
            profile = null;
            return;
        }
        profileLoading = true;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Profile fetched = NeteaseClient.account();
                    profile = fetched;
                    if (fetched != null) {
                        loadMyPlaylists();
                    }
                } catch (Throwable t) {
                    sessionError = message(t);
                } finally {
                    profileLoading = false;
                }
            }
        });
    }

    public void startQrLogin() {
        if (qrState == QrState.LOADING || qrState == QrState.WAITING || qrState == QrState.SCANNED) {
            return;
        }
        qrState = QrState.LOADING;
        qrMatrix = null;
        qrError = null;
        qrCreatedAt = 0L;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String key = NeteaseClient.qrKey();
                    if (key == null || key.isEmpty()) {
                        qrState = QrState.ERROR;
                        qrError = "无法创建二维码";
                        return;
                    }
                    boolean[][] matrix = QrCode.encode(NeteaseClient.qrContent(key), 4);
                    if (matrix == null) {
                        qrState = QrState.ERROR;
                        qrError = "二维码生成失败";
                        return;
                    }
                    qrMatrix = matrix;
                    qrCreatedAt = System.currentTimeMillis();
                    qrState = QrState.WAITING;
                    beginPolling(key, matrix);
                } catch (Throwable t) {
                    qrState = QrState.ERROR;
                    qrError = message(t);
                }
            }
        });
    }

    private void beginPolling(final String key, final boolean[][] matrix) {
        poller.schedule(new Runnable() {
            @Override
            public void run() {
                if (qrMatrix != matrix) {
                    return;
                }
                if (qrAgeMs() > 180000L) {
                    qrState = QrState.EXPIRED;
                    return;
                }
                int code;
                try {
                    code = NeteaseClient.qrCheck(key);
                } catch (Throwable t) {
                    poller.schedule(this, 2500L, TimeUnit.MILLISECONDS);
                    return;
                }
                if (code == 803) {
                    qrState = QrState.SUCCESS;
                    refreshProfile();
                    loadHome();
                    return;
                }
                if (code == 800) {
                    qrState = QrState.EXPIRED;
                    return;
                }
                if (code == 802) {
                    qrState = QrState.SCANNED;
                } else if (code == 801 && qrState != QrState.SCANNED) {
                    qrState = QrState.WAITING;
                }
                poller.schedule(this, 2000L, TimeUnit.MILLISECONDS);
            }
        }, 1800L, TimeUnit.MILLISECONDS);
    }

    public void cancelQrLogin() {
        if (qrState != QrState.SUCCESS) {
            qrState = QrState.IDLE;
            qrMatrix = null;
        }
    }

    public void logout() {
        io.execute(new Runnable() {
            @Override
            public void run() {
                NeteaseClient.logout();
                profile = null;
                myPlaylists = Collections.emptyList();
                qrState = QrState.IDLE;
                qrMatrix = null;
                saveSettings();
            }
        });
    }

    // ------------------------------------------------------------------ //
    // Files, settings
    // ------------------------------------------------------------------ //

    private static File musicDir() {
        File dir = new File(ClientConfig.DIR, "music");
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static File cacheDir() {
        File dir = new File(musicDir(), "cache");
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static File cachedFile(Song song) {
        return new File(cacheDir(), song.id + ".mp3");
    }

    private static boolean isUsable(File file) {
        return file != null && file.isFile() && file.length() > 65536L;
    }

    private void download(String url, File target, long serial) throws Exception {
        File part = new File(target.getPath() + ".part");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            connection.setRequestProperty("Referer", "https://music.163.com");
            int expected = connection.getContentLength();
            InputStream in = connection.getInputStream();
            FileOutputStream out = new FileOutputStream(part);
            try {
                byte[] chunk = new byte[16384];
                int read;
                long total = 0L;
                while ((read = in.read(chunk)) > 0) {
                    if (serial != playSerial) {
                        throw new IllegalStateException("cancelled");
                    }
                    out.write(chunk, 0, read);
                    total += read;
                    if (expected > 0) {
                        downloadProgress = (float) total / (float) expected;
                    }
                }
            } finally {
                out.close();
                in.close();
            }
            if (target.isFile()) {
                target.delete();
            }
            if (!part.renameTo(target)) {
                throw new IllegalStateException("无法写入缓存文件");
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (part.isFile() && !target.isFile()) {
                part.delete();
            }
        }
    }

    public void saveSettings() {
        try {
            File file = new File(musicDir(), "music.txt");
            FileWriter writer = new FileWriter(file, false);
            writer.write("volume: " + engine.volume() + "\n");
            writer.write("repeat: " + repeat.name() + "\n");
            writer.write("shuffle: " + shuffle + "\n");
            writer.write("level: " + streamLevel + "\n");
            writer.write("audioOutput: " + engine.outputMode() + "\n");
            writer.write("lyricScale: " + lyricScale + "\n");
            writer.write("lyricAnimation: " + lyricAnimation + "\n");
            writer.write("coverTint: " + coverTint + "\n");
            Lyrics3DSettings l3 = lyrics3d;
            writer.write("lyrics3d: " + l3.enabled + "\n");
            writer.write("lyrics3dScale: " + l3.scale + "\n");
            writer.write("lyrics3dDistance: " + l3.distance + "\n");
            writer.write("lyrics3dOffsetX: " + l3.offsetX + "\n");
            writer.write("lyrics3dOffsetY: " + l3.offsetY + "\n");
            writer.write("lyrics3dSpread: " + l3.spread + "\n");
            writer.write("lyrics3dGlow: " + l3.glow + "\n");
            writer.write("lyrics3dWiggle: " + l3.wiggle + "\n");
            writer.write("lyrics3dWordPop: " + l3.wordPop + "\n");
            writer.write("lyrics3dRainbow: " + l3.rainbow + "\n");
            writer.write("lyrics3dTranslation: " + l3.translation + "\n");
            writer.write("lyrics3dFollowView: " + l3.followView + "\n");
            writer.write("cookie: " + NeteaseHttp.cookieHeader() + "\n");
            writer.close();
        } catch (Throwable ignored) {
        }
    }

    private void loadSettings() {
        try {
            File file = new File(musicDir(), "music.txt");
            if (!file.isFile()) {
                engine.setVolume(0.8F);
                return;
            }
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (key.equals("volume")) {
                    try {
                        engine.setVolume(Float.parseFloat(value));
                    } catch (NumberFormatException ignored) {
                    }
                } else if (key.equals("repeat")) {
                    try {
                        repeat = Repeat.valueOf(value);
                    } catch (Exception ignored) {
                    }
                } else if (key.equals("shuffle")) {
                    shuffle = Boolean.parseBoolean(value);
                } else if (key.equals("level")) {
                    if (!value.isEmpty()) {
                        streamLevel = value;
                    }
                } else if (key.equals("audioOutput")) {
                    try {
                        engine.setOutputMode(Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                    }
                } else if (key.equals("lyricScale")) {
                    try {
                        lyricScale = Math.max(0.8F, Math.min(1.4F, Float.parseFloat(value)));
                    } catch (NumberFormatException ignored) {
                    }
                } else if (key.equals("lyricAnimation")) {
                    lyricAnimation = Boolean.parseBoolean(value);
                } else if (key.equals("coverTint")) {
                    coverTint = Boolean.parseBoolean(value);
                } else if (key.startsWith("lyrics3d")) {
                    applyLyrics3d(key, value);
                } else if (key.equals("cookie")) {
                    NeteaseHttp.storeAll(value);
                }
            }
            reader.close();
        } catch (Throwable ignored) {
        }
    }

    private void applyLyrics3d(String key, String value) {
        Lyrics3DSettings l3 = lyrics3d;
        try {
            if (key.equals("lyrics3d")) {
                l3.enabled = Boolean.parseBoolean(value);
            } else if (key.equals("lyrics3dScale")) {
                l3.scale = Math.max(0.4F, Math.min(2.5F, Float.parseFloat(value)));
            } else if (key.equals("lyrics3dDistance")) {
                l3.distance = Math.max(2.0F, Math.min(48.0F, Float.parseFloat(value)));
            } else if (key.equals("lyrics3dOffsetX")) {
                l3.offsetX = Math.max(-8.0F, Math.min(8.0F, Float.parseFloat(value)));
            } else if (key.equals("lyrics3dOffsetY")) {
                l3.offsetY = Math.max(-6.0F, Math.min(6.0F, Float.parseFloat(value)));
            } else if (key.equals("lyrics3dSpread")) {
                l3.spread = Math.max(0.0F, Math.min(60.0F, Float.parseFloat(value)));
            } else if (key.equals("lyrics3dGlow")) {
                l3.glow = Boolean.parseBoolean(value);
            } else if (key.equals("lyrics3dWiggle")) {
                l3.wiggle = Boolean.parseBoolean(value);
            } else if (key.equals("lyrics3dWordPop")) {
                l3.wordPop = Boolean.parseBoolean(value);
            } else if (key.equals("lyrics3dRainbow")) {
                l3.rainbow = Boolean.parseBoolean(value);
            } else if (key.equals("lyrics3dTranslation")) {
                l3.translation = Boolean.parseBoolean(value);
            } else if (key.equals("lyrics3dFollowView")) {
                l3.followView = Boolean.parseBoolean(value);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    /** The game is shutting down: stop the audio line so the process can exit cleanly. */
    public void shutdown() {
        engine.dispose();
        io.shutdownNow();
        poller.shutdownNow();
    }

    /** Runtime settings of the world-space lyric view, edited from the player window. */
    public static final class Lyrics3DSettings {
        public volatile boolean enabled;
        public volatile float scale = 1.0F;
        public volatile float distance = 12.0F;
        public volatile float offsetX;
        public volatile float offsetY = 0.4F;
        public volatile float spread = 20.0F;
        public volatile boolean glow = true;
        public volatile boolean wiggle = true;
        public volatile boolean wordPop = true;
        public volatile boolean rainbow;
        public volatile boolean translation;
        public volatile boolean followView = true;
    }

    private static String message(Throwable t) {
        String text = t.getMessage();
        if (text == null || text.isEmpty()) {
            return t.getClass().getSimpleName();
        }
        return text;
    }
}
