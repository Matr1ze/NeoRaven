package xyz.daikirai.gui.onyx.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The supplied dynamic-island(1).html digit timeline, in milliseconds and 32px reference units. */
public final class DigitRoll {
    public static final long DURATION = 500, CARRY_LAG = 133;
    public static final double POSITION_SCALE = .22;
    private static final double[] OLD_ALPHA = {1,.87,.49,.19,.03,0,0,0,0,0,0,0,0,0,0,0};
    private static final double[] NEW_ALPHA = {0,.01,.06,.27,.51,.71,.81,.90,.93,.95,.98,1,1,1,1,1};
    private static final double[] BLUR = {.16,1.10,4,5.10,3.60,2.40,1.55,.55,.16,.16,.16,.16,.16,.16,.16,.16};
    private static final double[] SCALE = {1,1,.90,.90,.90,.90,.95,.95,.95,1,1,1,1,1,1,1};
    private static final double[] OLD_Y = {0,8,38,40,42,44,46,48,50,52,54,56,58,60,62,64};
    private static final double[] NEW_Y = {-40,-40,-38,-32,-22,-4,9,17,17.5,15.5,12,7.5,4,2,.5,0};
    private final Map<Long, Digit> digits = new LinkedHashMap<Long, Digit>();
    private final List<Run> runs = new ArrayList<Run>();
    private String text = "", format = "";
    private long seen;
    private boolean initialized;

    public String text() { return text; }
    public List<Run> runs() { return runs; }
    public Iterable<Digit> digits() { return digits.values(); }
    public Digit digit(int run, int place) { return digits.get(key(run, place)); }
    private static long key(int run, int place) { return ((long) run << 32) | place; }
    private static boolean numeric(char c) { return c >= '0' && c <= '9'; }

    public void clear() { initialized = false; digits.clear(); runs.clear(); text = format = ""; seen = 0; }

    public void update(String value, long now) {
        value = value == null ? "" : value;
        if (initialized && value.equals(text) && now - seen <= 1000) { seen = now; return; }
        List<Run> next = new ArrayList<Run>();
        StringBuilder shape = new StringBuilder();
        for (int i = 0; i < value.length();) {
            if (!numeric(value.charAt(i))) { shape.append(value.charAt(i++)); continue; }
            int start = i;
            while (i < value.length() && numeric(value.charAt(i))) i++;
            next.add(new Run(start, i)); shape.append('#');
        }
        boolean reset = !initialized || !shape.toString().equals(format) || now - seen > 1000 || now < seen;
        if (reset) digits.clear();
        for (int index = 0; index < next.size(); index++) {
            Run run = next.get(index);
            Run previous = reset ? null : runs.get(index);
            int length = run.end - run.start, oldLength = previous == null ? 0 : previous.end - previous.start;
            char tens = length > 1 ? value.charAt(run.end - 2) : ' ';
            char oldTens = oldLength > 1 ? text.charAt(previous.end - 2) : ' ';
            for (int place = 0; place < Math.max(length, oldLength); place++) {
                char target = place < length ? value.charAt(run.end - 1 - place) : ' ';
                long key = key(index, place);
                Digit digit = digits.get(key);
                if (digit == null) {
                    digit = new Digit(index, place, reset ? target : ' '); digits.put(key, digit);
                }
                if (target != digit.to) {
                    // Like rollDigit(), keep only one outgoing/incoming pair per column. A queued
                    // carry that has not started still shows its real old digit when retargeted.
                    digit.from = now < digit.started ? digit.from : digit.to;
                    digit.to = target;
                    digit.started = now + (place == 0 && tens != oldTens ? CARRY_LAG : 0);
                }
            }
        }
        digits.values().removeIf(d -> d.to == ' ' && !d.active(now));
        text = value; format = shape.toString(); runs.clear(); runs.addAll(next); seen = now; initialized = true;
    }

    public boolean active(long now) {
        for (Digit digit : digits.values()) if (digit.active(now)) return true;
        return false;
    }

    public static Frame frame(double progress) {
        if (progress < 0) return new Frame(1, 0, 0, 1, 0, 0);
        if (progress >= 1) return new Frame(0, 1, 0, 1, 0, 0);
        return new Frame(clamp(sample(OLD_ALPHA, progress), 0, 1), clamp(sample(NEW_ALPHA, progress), 0, 1),
                Math.max(0, sample(BLUR, progress)), clamp(sample(SCALE, progress), .4, 1.1),
                sample(OLD_Y, progress) * POSITION_SCALE, sample(NEW_Y, progress) * POSITION_SCALE);
    }

    private static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
    private static double sample(double[] values, double progress) {
        int n = values.length;
        double f = clamp(progress, 0, 1) * (n - 1);
        int i = Math.min(n - 2, (int) Math.floor(f)); double t = f - i;
        double a = values[Math.max(0, i - 1)], b = values[i], c = values[i + 1], d = values[Math.min(n - 1, i + 2)];
        return .5 * (2*b + (-a+c)*t + (2*a-5*b+4*c-d)*t*t + (-a+3*b-3*c+d)*t*t*t);
    }

    public static final class Run {
        public final int start, end;
        Run(int start, int end) { this.start = start; this.end = end; }
    }
    public static final class Digit {
        public final int run, place;
        public char from, to;
        public long started;
        Digit(int run, int place, char value) { this.run = run; this.place = place; from = to = value; }
        public boolean active(long now) { return from != to && now < started + DURATION; }
        public Frame at(long now) { return frame((now - started) / (double) DURATION); }
    }
    public static final class Frame {
        public final double oldAlpha, newAlpha, blur, scale, oldY, newY;
        Frame(double oldAlpha, double newAlpha, double blur, double scale, double oldY, double newY) {
            this.oldAlpha = oldAlpha; this.newAlpha = newAlpha; this.blur = blur;
            this.scale = scale; this.oldY = oldY; this.newY = newY;
        }
    }
}
