package xyz.daikirai.account.fisproxy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import xyz.daikirai.account.ProxyConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * The live FisProxy session behind the Proxy page.
 *
 * <p>FisProxy is not a SOCKS or HTTP proxy: the API hands out a per-session <em>entrance</em>
 * address ({@code {sessionId}.{host}}) and Minecraft connects there, so the game connection needs a
 * destination swap instead of a tunnel handshake. This class owns the cheap state the two callers
 * need - the Proxy page (start / stop / change IP / balance) and {@code ProxyTunnel} (the entrance
 * to connect to) - while {@link FisProxyClient} does the signed HTTP work.</p>
 *
 * <p>Billing starts on {@link #start()} and stops on {@link #stop()}, so the page asks before it
 * spends anything; nothing here starts a session implicitly.</p>
 */
public final class FisProxySession {

    /** Minecraft entrances are plain game ports unless the API spells out {@code host:port}. */
    public static final int DEFAULT_PORT = 25565;

    private static final Object LOCK = new Object();

    private static String token = "";
    private static String serviceId = "";
    private static String target = "";
    private static FisProxyClient client;

    private static volatile boolean running;
    private static volatile String address = "";
    private static volatile String sessionId = "";
    private static volatile String state = "idle";
    private static volatile String balance = "";
    private static volatile String nfaCoin = "";
    private static volatile long startedAtMs;

    private FisProxySession() {
    }

    /** Points the session at the token/service/target stored in the Proxy settings. */
    public static void configure(ProxyConfig config) {
        if (config == null) {
            return;
        }
        synchronized (LOCK) {
            String nextToken = trim(config.apiToken);
            if (!nextToken.equals(token)) {
                client = null;
            }
            token = nextToken;
            serviceId = trim(config.serviceId);
            target = trim(config.target);
            if (client == null && !token.isEmpty()) {
                client = new FisProxyClient(token);
            }
        }
    }

    public static boolean configured() {
        return !token.isEmpty();
    }

    public static boolean running() {
        return running;
    }

    public static String address() {
        return address;
    }

    public static String sessionId() {
        return sessionId;
    }

    /** {@code idle}, {@code starting}, {@code running} or {@code stopping}. */
    public static String state() {
        return state;
    }

    public static String balance() {
        return balance;
    }

    public static String nfaCoin() {
        return nfaCoin;
    }

    public static void clear() {
        synchronized (LOCK) {
            client = null;
            token = "";
            serviceId = "";
            target = "";
            running = false;
            address = "";
            sessionId = "";
            state = "idle";
            balance = "";
            nfaCoin = "";
            startedAtMs = 0L;
        }
    }

    // ------------------------------------------------------------------ //
    // Operations (call from a worker thread: they block on HTTP)
    // ------------------------------------------------------------------ //

    /** Reloads the profile balances and the current session (started elsewhere, or nothing). */
    public static void refresh() throws IOException {
        synchronized (LOCK) {
            FisProxyClient api = requireClient();
            JsonObject me = api.me();
            balance = FisProxyClient.nestedString(me, "user.balances.service_point");
            nfaCoin = FisProxyClient.nestedString(me, "user.balances.nfa_coin");
            applyStatus(api.status());
        }
    }

    /** Starts billing and resolves the entrance Minecraft then connects to. */
    public static void start() throws IOException {
        synchronized (LOCK) {
            FisProxyClient api = requireClient();
            state = "starting";
            try {
                JsonObject operation = api.start(serviceId, target);
                String started = operationSessionId(operation);
                if (!started.isEmpty()) {
                    sessionId = started;
                }
                // The operation can report success a moment before /sessions/status shows the
                // entrance, and the entrance is what the tunnel needs, so wait for both.
                awaitRunning(api, 15000L);
                state = "running";
            } catch (IOException e) {
                state = "idle";
                throw e;
            } catch (RuntimeException e) {
                state = "idle";
                throw e;
            }
        }
    }

    /** Stops billing and clears the entrance. */
    public static JsonObject stop() throws IOException {
        synchronized (LOCK) {
            FisProxyClient api = requireClient();
            state = "stopping";
            try {
                return api.stop();
            } finally {
                running = false;
                address = "";
                sessionId = "";
                startedAtMs = 0L;
                state = "idle";
            }
        }
    }

    /** Rotates the exit IP; the entrance address usually stays the same. */
    public static void changeIp() throws IOException {
        synchronized (LOCK) {
            FisProxyClient api = requireClient();
            api.changeIp();
            applyStatus(api.status());
        }
    }

    // ------------------------------------------------------------------ //
    // Tunnel support
    // ------------------------------------------------------------------ //

    /**
     * Entrance to hand a game connection, refreshing the session once when the cache is empty.
     *
     * @throws IOException with a message fit for the disconnect screen when no session is running
     */
    public static InetSocketAddress requireEndpoint() throws IOException {
        String current = address;
        if (!running || current.isEmpty()) {
            refresh();
            current = address;
        }
        if (!running || current.isEmpty()) {
            throw new IOException("FisProxy: no session is running (start one in Proxy settings)");
        }
        return parseEndpoint(current);
    }

    /** {@code host[:port]} to an address; the port defaults to Minecraft's game port. */
    public static InetSocketAddress parseEndpoint(String value) throws IOException {
        String text = trim(value);
        if (text.isEmpty()) {
            throw new IOException("FisProxy returned an empty entrance address");
        }
        String host = text;
        int port = DEFAULT_PORT;
        int colon = text.lastIndexOf(':');
        if (colon > 0 && text.indexOf(':') == colon) {
            String portText = text.substring(colon + 1);
            host = text.substring(0, colon);
            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException e) {
                throw new IOException("FisProxy returned an invalid entrance port: " + text);
            }
            if (port < 1 || port > 65535) {
                throw new IOException("FisProxy returned an invalid entrance port: " + text);
            }
        }
        host = host.trim();
        if (host.isEmpty() || host.indexOf('/') >= 0 || host.indexOf(' ') >= 0) {
            throw new IOException("FisProxy returned an invalid entrance address: " + text);
        }
        return new InetSocketAddress(host, port);
    }

    // ------------------------------------------------------------------ //
    // Display helpers for the Proxy page
    // ------------------------------------------------------------------ //

    /** "Session s-123 · 4 min · s-123.edge.example" or "No session". */
    public static String summary() {
        if (!running) {
            return "No session";
        }
        StringBuilder text = new StringBuilder("Session ");
        text.append(sessionId.isEmpty() ? "running" : sessionId);
        long minutes = elapsedMinutes();
        if (minutes >= 0L) {
            text.append(" · ").append(minutes).append(" min");
        }
        if (!address.isEmpty()) {
            text.append(" · ").append(address);
        }
        return text.toString();
    }

    /** "Balance 12.50 SP · 3 NFA coin", or {@code ""} before the first refresh. */
    public static String balanceLine() {
        if (balance.isEmpty() && nfaCoin.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        if (!balance.isEmpty()) {
            text.append("Balance ").append(balance).append(" SP");
        }
        if (!nfaCoin.isEmpty()) {
            if (text.length() > 0) {
                text.append(" · ");
            }
            text.append(nfaCoin).append(" NFA coin");
        }
        return text.toString();
    }

    private static long elapsedMinutes() {
        long started = startedAtMs;
        if (started <= 0L) {
            return -1L;
        }
        long minutes = (System.currentTimeMillis() - started) / 60000L;
        return minutes < 0L ? 0L : minutes;
    }

    // ------------------------------------------------------------------ //
    // Internals
    // ------------------------------------------------------------------ //

    private static FisProxyClient requireClient() {
        if (client == null) {
            if (token.isEmpty()) {
                throw new FisProxyException(0, "TOKEN_MISSING", "Enter your FisProxy API token");
            }
            client = new FisProxyClient(token);
        }
        return client;
    }

    /** Polls {@code /sessions/status} until the entrance is published, so a connect cannot race it. */
    private static void awaitRunning(FisProxyClient api, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            applyStatus(api.status());
            if (running && !address.isEmpty()) {
                return;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new IOException("FisProxy started an operation but reports no running session yet");
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for the FisProxy session", e);
            }
        }
    }

    private static void applyStatus(JsonObject payload) {
        boolean wasRunning = running;
        running = payload.has("running") && payload.get("running").isJsonPrimitive()
                && payload.get("running").getAsBoolean();
        String next = FisProxyClient.string(payload, "address");
        if (next.isEmpty()) {
            next = FisProxyClient.string(payload, "entrance");
        }
        if (next.isEmpty()) {
            next = FisProxyClient.firstEntrance(payload);
        }
        address = next;
        JsonObject session = payload.has("session") && payload.get("session").isJsonObject()
                ? payload.getAsJsonObject("session") : null;
        if (session != null) {
            String id = FisProxyClient.string(session, "sessionId");
            sessionId = id;
            if (!wasRunning) {
                startedAtMs = parseTimestamp(session, "billingStartedAt", "startedAt");
            }
        } else {
            sessionId = "";
            startedAtMs = 0L;
        }
        state = running ? "running" : "idle";
    }

    private static long parseTimestamp(JsonObject session, String... keys) {
        for (String key : keys) {
            String text = FisProxyClient.string(session, key);
            if (text.isEmpty()) {
                continue;
            }
            try {
                return Instant.parse(text).toEpochMilli();
            } catch (RuntimeException ignored) {
                try {
                    return OffsetDateTime.parse(text).toInstant().toEpochMilli();
                } catch (RuntimeException ignoredAgain) {
                    // Unparseable timestamp: fall back to "unknown" rather than a wrong duration.
                }
            }
        }
        return 0L;
    }

    private static String operationSessionId(JsonObject operation) {
        if (operation == null) {
            return "";
        }
        String direct = FisProxyClient.string(operation, "sessionId");
        if (!direct.isEmpty()) {
            return direct;
        }
        JsonElement result = operation.get("result");
        if (result != null && result.isJsonObject()) {
            return FisProxyClient.string(result.getAsJsonObject(), "sessionId");
        }
        return "";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).isEmpty()
                ? "" : value.trim();
    }
}
