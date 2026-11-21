package xyz.daikirai.client;

import org.lwjgl.opengl.Display;

/**
 * Animated window title: {@code Neo} is on screen from the first frame and never moves, the rest
 * types itself out one character at a time, the finished title holds for two seconds with a cursor
 * after the last glyph, then the cycle starts over.
 *
 * <p>One cycle in ten shows a variant title instead of the version.</p>
 */
public final class WindowTitle {

    /** Never animated: it is visible from the first frame of every cycle. */
    private static final String PREFIX = "Neo";
    private static final String BODY = "Raven b2";
    /** Used by the rare cycle; the prefix still stays put. */
    private static final String RARE_BODY = "Raven u8963";
    private static final float RARE_CHANCE = 0.10F;

    /** Gap between typed characters, and how long the finished title stays up. */
    private static final long CHAR_MS = 120L;
    private static final long HOLD_MS = 2000L;
    /** One character removed every this many milliseconds once the hold is over. */
    private static final long DELETE_MS = 60L;
    /** Re-apply at least this often so a title the game rewrites by itself is taken back. */
    private static final long REAPPLY_MS = 1000L;
    /** Cursor drawn after the last visible glyph. */
    private static final String CURSOR = "_";

    private static final java.util.Random RANDOM = new java.util.Random();

    private static long cycleStart;
    private static long lastApplied;
    private static String body = BODY;
    private static String applied = "";

    private WindowTitle() {
    }

    /** Called once per client tick, including the menus, so the title animates everywhere. */
    public static void tick() {
        if (!Display.isCreated()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (cycleStart == 0L) {
            startCycle(now);
        }
        long elapsed = now - cycleStart;
        int typed = visibleAt(elapsed);
        if (elapsed >= cycleLength()) {
            startCycle(now);
            typed = 0;
        }
        String text = PREFIX + body.substring(0, typed) + CURSOR;
        if (!text.equals(applied) || now - lastApplied >= REAPPLY_MS) {
            applied = text;
            lastApplied = now;
            Display.setTitle(text);
        }
    }

    /**
     * How much of the body is on screen: it types out, holds, then deletes itself back to the
     * prefix so the next cycle can start again.
     */
    private static int visibleAt(long elapsed) {
        int length = body.length();
        long typeEnd = length * CHAR_MS;
        if (elapsed < typeEnd) {
            return (int) (elapsed / CHAR_MS);
        }
        long holdEnd = typeEnd + HOLD_MS;
        if (elapsed < holdEnd) {
            return length;
        }
        long deleted = (elapsed - holdEnd) / DELETE_MS;
        return (int) Math.max(0L, length - deleted);
    }

    private static long cycleLength() {
        return body.length() * CHAR_MS + HOLD_MS + body.length() * DELETE_MS;
    }

    private static void startCycle(long now) {
        cycleStart = now;
        body = bodyFor(RANDOM.nextFloat());
    }

    /** The 10% rule, split out so it can be checked without waiting for a lucky cycle. */
    static String bodyFor(float roll) {
        return roll < RARE_CHANCE ? RARE_BODY : BODY;
    }

    /** The body currently being typed; exposed for the title self-check. */
    static String currentBody() {
        return body;
    }
}
