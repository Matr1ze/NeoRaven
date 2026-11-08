package xyz.daikirai.music;

import java.io.File;
import java.util.Locale;

public final class AudioRuntime {

    private AudioRuntime() {
    }

    private static final boolean ANDROID = detectAndroid();
    private static final boolean NATIVE_JAVA_SOUND = detectNativeJavaSound();
    private static final boolean FORCE_OPENAL = Boolean.getBoolean("neoraven.openal");

    public static boolean isAndroid() {
        return ANDROID;
    }

    public static boolean hasNativeJavaSound() {
        return NATIVE_JAVA_SOUND;
    }

    public static boolean preferGameAudio() {
        return FORCE_OPENAL || ANDROID || !NATIVE_JAVA_SOUND;
    }

    public static String autoReason() {
        if (preferGameAudio()) {
            if (FORCE_OPENAL) {
                return "已指定游戏 OpenAL";
            }
            if (ANDROID) {
                return "安卓环境，将用游戏 OpenAL";
            }
            return "未检测到 Java Sound，将用游戏 OpenAL";
        }
        return "桌面环境，将用 Java Sound";
    }

    public static boolean isAndroid(String osName, String runtimeName, String vmName,
                                    String androidRoot, String androidData, String pojavNativeDir,
                                    boolean androidSystem) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("android")) {
            return true;
        }
        if (!os.contains("linux")) {
            return false;
        }
        String runtime = ((runtimeName == null ? "" : runtimeName) + " "
                + (vmName == null ? "" : vmName)).toLowerCase(Locale.ROOT);
        return runtime.contains("android") || runtime.contains("dalvik")
                || androidSystem
                || ("/system".equals(androidRoot) && "/data".equals(androidData))
                || (pojavNativeDir != null && !pojavNativeDir.trim().isEmpty());
    }

    private static boolean detectAndroid() {
        try {
            return isAndroid(
                    System.getProperty("os.name", ""),
                    System.getProperty("java.runtime.name", ""),
                    System.getProperty("java.vm.name", ""),
                    System.getenv("ANDROID_ROOT"),
                    System.getenv("ANDROID_DATA"),
                    System.getenv("POJAV_NATIVEDIR"),
                    hasAndroidSystemFiles());
        } catch (SecurityException ignored) {
            return false;
        }
    }

    private static boolean hasAndroidSystemFiles() {
        return new File("/system/build.prop").isFile()
                || new File("/system/bin/app_process").isFile()
                || new File("/system/bin/app_process64").isFile();
    }

    private static boolean detectNativeJavaSound() {
        String home = System.getProperty("java.home");
        if (home == null) {
            return true;
        }
        String[] candidates = {
                "bin/jsound.dll",
                "lib/libjsound.so",
                "lib/libjsound.dylib",
                "lib/amd64/libjsound.so",
                "lib/aarch64/libjsound.so",
        };
        for (String candidate : candidates) {
            if (new File(home, candidate).isFile()) {
                return true;
            }
        }
        return false;
    }
}
