package xyz.daikirai.gui.onyx.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Session history, independent of the short-lived notification toast stack. */
public final class NotificationHistory {
    public static final int LIMIT = 100;
    private static final List<Record> RECORDS = new ArrayList<Record>();
    private static long nextId;
    private static List<Record> snapshot;

    private NotificationHistory() { }

    public static synchronized void record(String title, String message, NotificationLevel level, Integer accent) {
        RECORDS.add(0, new Record(++nextId, System.currentTimeMillis(), title, message, level, accent));
        snapshot = null;
        if (RECORDS.size() > LIMIT) RECORDS.remove(RECORDS.size() - 1);
    }

    /** Newest first; callers cannot change the history through a returned snapshot. */
    public static synchronized List<Record> snapshot() {
        if (snapshot == null) snapshot = Collections.unmodifiableList(new ArrayList<Record>(RECORDS));
        return snapshot;
    }

    public static synchronized void dismiss(long id) {
        for (int i = 0; i < RECORDS.size(); i++) {
            if (RECORDS.get(i).id == id) { RECORDS.remove(i); snapshot = null; return; }
        }
    }

    public static synchronized void clear() { RECORDS.clear(); snapshot = null; }

    public static final class Record {
        public final long id, time;
        public final String title, message;
        public final NotificationLevel level;
        public final Integer accent;

        private Record(long id, long time, String title, String message, NotificationLevel level, Integer accent) {
            this.id = id; this.time = time; this.title = title; this.message = message;
            this.level = level; this.accent = accent;
        }
    }
}
