package xyz.daikirai.gui.onyx.hud;

import java.nio.charset.StandardCharsets;

/**
 * "Now playing" source for the island's music state.
 *
 * <p>Onyx read the system media session through native APIs (Windows SMTC / macOS
 * NowPlaying). This port keeps the same idea on Windows by polling the SMTC session through
 * PowerShell's WinRT bridge on a daemon thread; titles, playback state and the timeline are
 * cached so the render thread never blocks. On other platforms the feature simply stays
 * unavailable and the island falls back to its other states.</p>
 */
public final class NowPlaying {

    private static final long POLL_INTERVAL = 2500L;
    private static final char SEPARATOR = (char) 31;

    private static final java.nio.file.Path WORK_DIR = java.nio.file.Paths.get(
            System.getProperty("java.io.tmpdir"), "onyx-nowplaying");
    private static final java.nio.file.Path SCRIPT_FILE = WORK_DIR.resolve("poll.ps1");
    private static final java.nio.file.Path RESULT_FILE = WORK_DIR.resolve("result.txt");

    private static final String SCRIPT = String.join("\r\n",
            "$ErrorActionPreference='Stop';",
            "Add-Type -AssemblyName System.Runtime.WindowsRuntime;",
            "$asTask=([System.WindowsRuntimeSystemExtensions].GetMethods()|?{$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'})[0];",
            "function Await($op,$t){$task=$asTask.MakeGenericMethod($t).Invoke($null,@($op));$task.Wait(-1)|Out-Null;$task.Result};",
            "[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]|Out-Null;",
            "$mgr=Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]);",
            "$s=$mgr.GetCurrentSession();",
            "$out='" + RESULT_FILE + "';",
            "if($s -eq $null){[IO.File]::WriteAllText($out,'NONE',[Text.Encoding]::UTF8);exit};",
            "$p=Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]);",
            "$info=$s.GetPlaybackInfo();$tl=$s.GetTimelineProperties();",
            "$text=$p.Title+[char]31+$p.Artist+[char]31+$info.PlaybackStatus.ToString()+[char]31+$tl.Position.TotalSeconds+[char]31+$tl.EndTime.TotalSeconds;",
            "[IO.File]::WriteAllText($out,$text,[Text.Encoding]::UTF8)");
    private static volatile String title = "";
    private static volatile String artist = "";
    private static volatile boolean playing;
    private static volatile float progress;
    private static volatile long updatedAt;
    private static volatile boolean running;
    private static volatile boolean enabled;
    private static boolean supported = isWindows();
    private static boolean unavailable;

    private NowPlaying() {
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    /** Enables/disables polling; safe to call every frame. */
    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            title = "";
            artist = "";
            playing = false;
            return;
        }
        start();
    }

    private static void start() {
        if (running || !supported || unavailable) {
            return;
        }
        running = true;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        if (enabled) {
                            poll();
                        }
                        Thread.sleep(POLL_INTERVAL);
                    } catch (InterruptedException e) {
                        return;
                    } catch (Throwable t) {
                        unavailable = true;
                        return;
                    }
                }
            }
        }, "onyx-now-playing");
        thread.setDaemon(true);
        thread.start();
    }

    private static void poll() throws Exception {
        java.nio.file.Files.createDirectories(WORK_DIR);
        if (!java.nio.file.Files.exists(SCRIPT_FILE)) {
            java.nio.file.Files.write(SCRIPT_FILE, SCRIPT.getBytes(StandardCharsets.UTF_8));
            SCRIPT_FILE.toFile().deleteOnExit();
        }
        java.nio.file.Files.deleteIfExists(RESULT_FILE);
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Sta",
                "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File", SCRIPT_FILE.toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(new java.io.File("NUL"));
        Process process = builder.start();
        if (!process.waitFor(8L, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroy();
            return;
        }
        if (!java.nio.file.Files.exists(RESULT_FILE)) {
            return;
        }
        String text = new String(java.nio.file.Files.readAllBytes(RESULT_FILE), StandardCharsets.UTF_8).trim();
        if (!text.isEmpty() && text.charAt(0) == (char) 0xFEFF) {
            text = text.substring(1).trim();
        }
        if (text.isEmpty() || text.startsWith("NONE")) {
            title = "";
            artist = "";
            playing = false;
            return;
        }
        String[] parts = text.split(String.valueOf(SEPARATOR), -1);
        if (parts.length < 5) {
            return;
        }
        title = parts[0].isEmpty() ? parts[1] : parts[0];
        artist = parts[1];
        playing = "PLAYING".equalsIgnoreCase(parts[2]);
        float position = parseFloat(parts[3]);
        float duration = parseFloat(parts[4]);
        progress = duration > 1.0F ? Math.max(0.0F, Math.min(position / duration, 1.0F)) : 0.0F;
        updatedAt = System.currentTimeMillis();
    }
    private static float parseFloat(String value) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return 0.0F;
        }
    }

    public static boolean supported() {
        return supported && !unavailable;
    }

    public static String title() {
        return title;
    }

    public static String artist() {
        return artist;
    }

    public static boolean playing() {
        return playing;
    }

    public static float progress() {
        return progress;
    }

    public static boolean hasSession() {
        return supported() && !title.isEmpty();
    }

    public static long updatedAt() {
        return updatedAt;
    }
}
