package xyz.daikirai.music;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCcontext;

final class OpenAlContext {

    private static final int DIRECT = 1;
    private static final int CACHED = 2;
    private static final int UNKNOWN = 3;

    private static volatile int mode;

    private OpenAlContext() {
    }

    static ALCcontext current() {
        int source = mode();
        if (source == DIRECT) {
            try {
                return ALC10.alcGetCurrentContext();
            } catch (Throwable missing) {
                synchronized (OpenAlContext.class) {
                    mode = 0;
                }
                return current();
            }
        }
        if (source == CACHED) {
            try {
                return AL.getContext();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    static boolean unknown() {
        return mode() == UNKNOWN;
    }

    static boolean matches(ALCcontext expected, boolean expectedUnknown) {
        if (expectedUnknown) {
            return true;
        }
        int source = mode();
        if (source == UNKNOWN) {
            return true;
        }
        ALCcontext live = current();
        return live != null && live.equals(expected);
    }

    private static int mode() {
        int current = mode;
        if (current != 0) {
            return current;
        }
        synchronized (OpenAlContext.class) {
            if (mode != 0) {
                return mode;
            }
            if (probeDirect()) {
                mode = DIRECT;
            } else if (probeCached()) {
                mode = CACHED;
            } else {
                mode = UNKNOWN;
            }
            return mode;
        }
    }

    private static boolean probeDirect() {
        try {
            ALC10.alcGetCurrentContext();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean probeCached() {
        try {
            AL.getContext();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
