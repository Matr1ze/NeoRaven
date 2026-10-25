package xyz.daikirai.account.fisproxy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal Java 8 client for the FisProxy user session API.
 *
 * <p>The official SDK is Java 17, so this speaks the same protocol with {@code HttpURLConnection}:
 * one unsigned {@code POST /api/v1/auth/admission} to obtain an admission token plus its HMAC
 * request key, then every other call carries the {@code X-FP-*} signature headers produced by
 * {@link FisProxySigner}. Admission is cached and refreshed once when the API says it expired.</p>
 *
 * <p>Only the calls the Proxy page needs are implemented: {@code me}, {@code status},
 * {@code start}, {@code stop} and {@code changeIp}. {@code start} and {@code changeIp} poll their
 * operation to completion, which is what the SDK's default {@code wait = true} does.</p>
 */
public final class FisProxyClient {

    public static final String DEFAULT_BASE_URL = "https://api.fisproxy.org";

    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;
    private static final long ADMISSION_REFRESH_SKEW_SECONDS = 5L;
    private static final int OPERATION_TIMEOUT_SECONDS = 180;
    private static final long OPERATION_INTERVAL_MS = 1000L;

    private final String token;
    private final String baseUrl;
    private final String clientId;
    private final String userAgent;
    private final Object lock = new Object();

    private Admission admission;

    public FisProxyClient(String token) {
        this(token, DEFAULT_BASE_URL);
    }

    public FisProxyClient(String token, String baseUrl) {
        String cleaned = token == null ? "" : token.trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("FisProxy API token is required");
        }
        this.token = cleaned;
        this.baseUrl = stripTrailingSlash(baseUrl == null || baseUrl.trim().isEmpty()
                ? DEFAULT_BASE_URL : baseUrl.trim());
        this.clientId = UUID.randomUUID().toString().replace("-", "");
        this.userAgent = "RavenB4 (fisproxy-java8)";
    }

    /** Drops the cached admission so the next signed call exchanges a fresh one. */
    public void resetAdmission() {
        synchronized (lock) {
            admission = null;
        }
    }

    // ------------------------------------------------------------------ //
    // Endpoints
    // ------------------------------------------------------------------ //

    /** {@code GET /api/v1/me}: profile plus decimal-string balances. */
    public JsonObject me() throws IOException {
        return request("GET", "/api/v1/me", null, null);
    }

    /** {@code GET /api/v1/sessions/status}: {@code running}, {@code address}, {@code entrances}. */
    public JsonObject status() throws IOException {
        return request("GET", "/api/v1/sessions/status", null, null);
    }

    /** {@code GET /api/v1/me/services}: services this token may start. */
    public JsonObject services() throws IOException {
        return request("GET", "/api/v1/me/services", null, null);
    }

    /**
     * {@code POST /api/v1/sessions/start}, then polls the returned operation until it is terminal.
     *
     * @param serviceId optional service id, e.g. {@code us.hypixel}
     * @param target    optional upstream target, e.g. {@code mc.hypixel.net}
     */
    public JsonObject start(String serviceId, String target) throws IOException {
        JsonObject body = new JsonObject();
        if (serviceId != null && !serviceId.trim().isEmpty()) {
            body.addProperty("serviceId", serviceId.trim());
        }
        if (target != null && !target.trim().isEmpty()) {
            body.addProperty("target", target.trim());
        }
        JsonObject queued = request("POST", "/api/v1/sessions/start", body, UUID.randomUUID().toString());
        return waitOperation(operationId(queued), false);
    }

    /** {@code POST /api/v1/sessions/stop}: stops billing and returns the settlement. */
    public JsonObject stop() throws IOException {
        return request("POST", "/api/v1/sessions/stop", new JsonObject(), null);
    }

    /** {@code POST /api/v1/sessions/change-ip}, polled until the operation and its route ACK land. */
    public JsonObject changeIp() throws IOException {
        JsonObject queued = request("POST", "/api/v1/sessions/change-ip", new JsonObject(),
                UUID.randomUUID().toString());
        return waitOperation(operationId(queued), true);
    }

    /** {@code GET /api/v1/operations/{id}}. */
    public JsonObject operation(String id) throws IOException {
        return request("GET", "/api/v1/operations/" + FisProxySigner.quote(id), null, null);
    }

    // ------------------------------------------------------------------ //
    // Operation polling
    // ------------------------------------------------------------------ //

    private JsonObject waitOperation(String id, boolean waitRouteAck) throws IOException {
        if (id == null || id.isEmpty()) {
            throw new FisProxyException(0, "OPERATION_ID_MISSING",
                    "FisProxy did not return an operation id");
        }
        long deadline = System.nanoTime() + OPERATION_TIMEOUT_SECONDS * 1000000000L;
        while (System.nanoTime() < deadline) {
            JsonObject payload = operation(id);
            JsonObject value = payload.has("operation") && payload.get("operation").isJsonObject()
                    ? payload.getAsJsonObject("operation") : payload;
            String status = string(value, "status");
            if ("failed".equals(status) || "canceled".equals(status)) {
                throw new FisProxyException(0, string(value, "errorCode"),
                        firstNonEmpty(string(value, "message"), "FisProxy operation " + status));
            }
            if ("succeeded".equals(status) && (!waitRouteAck || routeAcked(value))) {
                return value;
            }
            sleep(OPERATION_INTERVAL_MS);
        }
        throw new FisProxyException(0, "OPERATION_TIMEOUT",
                "FisProxy did not finish the operation in " + OPERATION_TIMEOUT_SECONDS + "s");
    }

    private static boolean routeAcked(JsonObject operation) {
        if (!operation.has("result") || !operation.get("result").isJsonObject()) {
            return false;
        }
        JsonObject result = operation.getAsJsonObject("result");
        JsonElement acked = result.get("routeAcked");
        return acked != null && acked.isJsonPrimitive() && acked.getAsBoolean();
    }

    private static String operationId(JsonObject payload) {
        if (payload == null) {
            return "";
        }
        if (payload.has("operation") && payload.get("operation").isJsonObject()) {
            return string(payload.getAsJsonObject("operation"), "id");
        }
        return string(payload, "id");
    }

    private static void sleep(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for FisProxy", e);
        }
    }

    // ------------------------------------------------------------------ //
    // Transport
    // ------------------------------------------------------------------ //

    private JsonObject request(String method, String path, JsonObject body, String idempotencyKey)
            throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.toString().getBytes(StandardCharsets.UTF_8);
        String contentType = body == null ? "" : "application/json";
        return send(method, path, bytes, contentType, idempotencyKey == null ? "" : idempotencyKey, true);
    }

    private JsonObject send(String method, String path, byte[] body, String contentType,
                            String idempotencyKey, boolean sign) throws IOException {
        return send(method, path, body, contentType, idempotencyKey, sign, true);
    }

    private JsonObject send(String method, String path, byte[] body, String contentType,
                            String idempotencyKey, boolean sign, boolean allowRefresh) throws IOException {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        String bodySha256 = FisProxySigner.sha256B64Url(body);
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + token);
        headers.put("User-Agent", userAgent);
        headers.put("X-FP-Content-Length", Integer.toString(body.length));
        headers.put("X-FP-Content-SHA256", bodySha256);
        if (!contentType.isEmpty()) {
            headers.put("Content-Type", contentType);
        }
        if (!idempotencyKey.isEmpty()) {
            headers.put("Idempotency-Key", idempotencyKey);
        }
        if (sign) {
            synchronized (lock) {
                headers.putAll(signatureHeaders(ensureAdmission(), method, path, body, contentType,
                        idempotencyKey));
            }
        }
        Response response = transport(method, baseUrl + path, headers, body);
        JsonObject payload = decode(response.body);
        if (sign && allowRefresh && isRefreshableAdmissionFailure(response.status, payload)) {
            resetAdmission();
            return send(method, path, body, contentType, idempotencyKey, sign, false);
        }
        if (response.status >= 400 || isErrorPayload(payload)) {
            throw errorFromResponse(response.status, payload);
        }
        return payload;
    }

    private Map<String, String> signatureHeaders(Admission current, String method, String target,
                                                 byte[] body, String contentType, String idempotencyKey) {
        String sequence = FisProxySigner.formatSequence(current.sequence);
        current.sequence += 1L;
        long timestampValue = System.currentTimeMillis() + current.clockOffsetMs;
        String timestamp = Long.toString(timestampValue);
        if (timestamp.length() != 13) {
            throw new FisProxyException(400, "REQUEST_TIMESTAMP_INVALID",
                    "signed timestamp must be 13-digit unix milliseconds");
        }
        String contentLength = Integer.toString(body.length);
        String bodySha256 = FisProxySigner.sha256B64Url(body);
        String canonical = FisProxySigner.canonicalRequest(
                string(current.payload, "aud"),
                string(current.payload, "subject"),
                string(current.payload, "credentialId"),
                string(current.payload, "admissionId"),
                string(current.payload, "clientId"),
                sequence, timestamp, method, target, contentType, contentLength, idempotencyKey,
                bodySha256);
        String signature = FisProxySigner.signRequest(current.requestKey, canonical);
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("X-FP-Admission", current.admission);
        headers.put("X-FP-Subject", current.subject);
        headers.put("X-FP-Timestamp", timestamp);
        headers.put("X-FP-Client", string(current.payload, "clientId"));
        headers.put("X-FP-Sequence", sequence);
        headers.put("X-FP-Content-Length", contentLength);
        headers.put("X-FP-Content-SHA256", bodySha256);
        headers.put("X-FP-Signature", signature);
        return headers;
    }

    private Admission ensureAdmission() throws IOException {
        long now = System.currentTimeMillis() / 1000L;
        Admission current = admission;
        if (current != null && current.expiresAt > now + ADMISSION_REFRESH_SKEW_SECONDS) {
            return current;
        }
        return exchangeAdmission();
    }

    private Admission exchangeAdmission() throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("clientId", clientId);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + token);
        headers.put("User-Agent", userAgent);
        headers.put("X-FP-Content-Length", Integer.toString(bytes.length));
        headers.put("X-FP-Content-SHA256", FisProxySigner.sha256B64Url(bytes));
        headers.put("Content-Type", "application/json");
        Response response = transport("POST", baseUrl + "/api/v1/auth/admission", headers, bytes);
        JsonObject payload = decode(response.body);
        if (response.status >= 400 || isErrorPayload(payload)) {
            throw errorFromResponse(response.status, payload);
        }
        String admissionToken = string(payload, "admission");
        String requestKey = string(payload, "requestKey");
        String subject = string(payload, "subject");
        String admissionId = string(payload, "admissionId");
        Long expiresAt = FisProxySigner.asLong(payload.get("expiresAt"));
        Long serverTime = FisProxySigner.asLong(payload.get("serverTime"));
        String serverEpoch = string(payload, "serverEpoch");
        if (!isTrue(payload.get("ok")) || admissionToken.isEmpty() || requestKey.isEmpty()
                || subject.isEmpty() || admissionId.isEmpty() || expiresAt == null
                || serverTime == null || serverEpoch.isEmpty()) {
            throw new FisProxyException(response.status, "INVALID_ADMISSION",
                    "invalid admission exchange response");
        }
        JsonObject parsed;
        try {
            parsed = FisProxySigner.parseAdmissionPayload(admissionToken);
        } catch (IllegalArgumentException e) {
            throw new FisProxyException(response.status, "INVALID_ADMISSION",
                    "invalid admission exchange response");
        }
        if (!subject.equals(string(parsed, "subject"))
                || !admissionId.equals(string(parsed, "admissionId"))
                || !clientId.equals(string(parsed, "clientId"))
                || expiresAt.longValue() != longOrZero(FisProxySigner.asLong(parsed.get("expiresAt")))) {
            throw new FisProxyException(response.status, "INVALID_ADMISSION",
                    "admission payload mismatch");
        }
        Admission credential = new Admission(admissionToken, requestKey, subject, admissionId,
                expiresAt.longValue(), parsed, serverTime.longValue() - System.currentTimeMillis());
        synchronized (lock) {
            this.admission = credential;
        }
        return credential;
    }

    private Response transport(String method, String url, Map<String, String> headers, byte[] body)
            throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            if (body.length > 0) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                OutputStream out = connection.getOutputStream();
                try {
                    out.write(body);
                    out.flush();
                } finally {
                    out.close();
                }
            }
            int status = connection.getResponseCode();
            InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Response(status, readFully(in));
        } catch (IOException e) {
            throw new FisProxyException(0, "TRANSPORT_ERROR",
                    e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] readFully(InputStream in) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > MAX_BODY_BYTES) {
                    throw new IOException("FisProxy response is too large");
                }
            }
            return buffer.toByteArray();
        } finally {
            in.close();
        }
    }

    private static JsonObject decode(byte[] body) {
        if (body == null || body.length == 0) {
            return new JsonObject();
        }
        String text = new String(body, StandardCharsets.UTF_8);
        try {
            JsonElement parsed = new JsonParser().parse(text);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            JsonObject fallback = new JsonObject();
            fallback.addProperty("errorCode", "INVALID_RESPONSE");
            fallback.addProperty("message", text.length() > 300 ? text.substring(0, 300) : text);
            return fallback;
        }
    }

    // ------------------------------------------------------------------ //
    // Errors and small helpers
    // ------------------------------------------------------------------ //

    private static boolean isTrue(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    private static boolean isErrorPayload(JsonObject payload) {
        if (payload == null) {
            return false;
        }
        JsonElement ok = payload.get("ok");
        if (ok != null && ok.isJsonPrimitive()) {
            return !isTrue(ok);
        }
        JsonElement success = payload.get("success");
        return success != null && success.isJsonPrimitive() && !isTrue(success);
    }

    private static boolean isRefreshableAdmissionFailure(int status, JsonObject payload) {
        if (status != 401 && status != 403) {
            return false;
        }
        String code = errorCodeOf(payload);
        return "ADMISSION_EXPIRED".equals(code) || "ADMISSION_PROCESS_EXPIRED".equals(code)
                || "ADMISSION_INVALID".equals(code) || "EDGE_ADMISSION_EXPIRED".equals(code)
                || "REQUEST_TIMESTAMP_INVALID".equals(code);
    }

    private static FisProxyException errorFromResponse(int status, JsonObject payload) {
        String code = errorCodeOf(payload);
        String message = errorMessageOf(payload, "");
        if (message.isEmpty()) {
            // Rejected admission attempts come back with an empty body, so status is all there is.
            if (status == 401 || status == 403) {
                message = "FisProxy API token was rejected (HTTP " + status + ")";
            } else if (payload == null || payload.entrySet().isEmpty()) {
                // Gson 2.2.4 (what 1.8.9 ships) has no JsonObject#size, so ask the entry set.
                message = "FisProxy rejected the request (HTTP " + status + ")";
            } else {
                message = "FisProxy error (HTTP " + status + ")";
            }
        }
        return new FisProxyException(status, code, message);
    }

    static String errorCodeOf(JsonObject payload) {
        if (payload == null) {
            return "";
        }
        for (String key : new String[]{"errorCode", "code"}) {
            String value = string(payload, key);
            if (!value.isEmpty()) {
                return value;
            }
        }
        JsonElement nested = payload.get("error");
        if (nested != null && nested.isJsonObject()) {
            return string(nested.getAsJsonObject(), "code");
        }
        return "";
    }

    static String errorMessageOf(JsonObject payload, String fallback) {
        if (payload == null) {
            return fallback;
        }
        for (String key : new String[]{"message", "msg", "error"}) {
            JsonElement value = payload.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String text = value.getAsString();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return fallback;
    }

    static String string(JsonObject object, String key) {
        if (object == null) {
            return "";
        }
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return "";
        }
        try {
            return value.getAsString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Reads {@code a.b.c} out of a nested response as a string, or {@code ""}. */
    public static String nestedString(JsonObject object, String path) {
        JsonObject current = object;
        String[] parts = path.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement next = current.get(parts[i]);
            if (next == null || !next.isJsonObject()) {
                return "";
            }
            current = next.getAsJsonObject();
        }
        return string(current, parts[parts.length - 1]);
    }

    /** First entrance address in {@code entrances[]}, or {@code ""}. */
    public static String firstEntrance(JsonObject payload) {
        JsonElement entrances = payload == null ? null : payload.get("entrances");
        if (entrances == null || !entrances.isJsonArray()) {
            return "";
        }
        JsonArray list = entrances.getAsJsonArray();
        for (JsonElement element : list) {
            if (element.isJsonObject()) {
                JsonObject entrance = element.getAsJsonObject();
                String address = string(entrance, "address");
                if (!address.isEmpty()) {
                    return address;
                }
                String host = string(entrance, "host");
                if (!host.isEmpty()) {
                    return host;
                }
            }
        }
        return "";
    }

    static long longOrZero(Long value) {
        return value == null ? 0L : value.longValue();
    }

    static String firstNonEmpty(String first, String second) {
        return first != null && !first.isEmpty() ? first : (second == null ? "" : second);
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    /** One admission credential: the token itself plus everything the signature needs. */
    private static final class Admission {
        final String admission;
        final String requestKey;
        final String subject;
        final String admissionId;
        final long expiresAt;
        final JsonObject payload;
        final long clockOffsetMs;
        long sequence;

        Admission(String admission, String requestKey, String subject, String admissionId,
                  long expiresAt, JsonObject payload, long clockOffsetMs) {
            this.admission = admission;
            this.requestKey = requestKey;
            this.subject = subject;
            this.admissionId = admissionId;
            this.expiresAt = expiresAt;
            this.payload = payload;
            this.clockOffsetMs = clockOffsetMs;
        }
    }

    private static final class Response {
        final int status;
        final byte[] body;

        Response(int status, byte[] body) {
            this.status = status;
            this.body = body;
        }
    }
}
