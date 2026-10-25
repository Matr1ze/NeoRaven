package xyz.daikirai.account.fisproxy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;

/**
 * Request signing for the FisProxy user session API (the SDK's {@code FISPROXY-REQUEST-V2} scheme).
 *
 * <p>The official SDK targets Java 17, while the client is a 1.8.9 mod on Java 8, so the wire format
 * is reimplemented here: the canonical request string, the HMAC-SHA256 signature, the admission
 * token payload and the percent encoder all have to match byte for byte or the API answers 401.</p>
 *
 * <p>Nothing in here touches the network; it is pure string/crypto work so the parity test can call
 * the SDK and this class side by side.</p>
 */
public final class FisProxySigner {

    /** First line of every canonical request; the API rejects anything else. */
    public static final String CANONICAL_PREFIX = "FISPROXY-REQUEST-V2";

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private FisProxySigner() {
    }

    public static String b64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static byte[] b64urlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    public static String sha256B64Url(byte[] body) {
        try {
            return b64url(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    /**
     * Content type as the signature sees it: lower case, no whitespace around the separators.
     *
     * <p>{@code String#strip()} from the SDK is {@code trim()} here - the API only ever sees ASCII
     * media types, and Java 8 has no {@code strip()}.</p>
     */
    public static String normalizeContentType(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        text = text.replaceAll("\\s*;\\s*", ";");
        return text.replaceAll("\\s*=\\s*", "=");
    }

    /**
     * The newline-joined string that gets signed. Field order and the empty-string fallbacks are
     * part of the protocol, not a detail: a signed request that differs by one byte is a 401.
     */
    public static String canonicalRequest(String aud, String subject, String credentialId,
                                          String admissionId, String clientId, String sequence,
                                          String timestamp, String method, String target,
                                          String contentType, String contentLength,
                                          String idempotencyKey, String bodySha256) {
        return String.join("\n",
                CANONICAL_PREFIX,
                aud == null ? "" : aud,
                subject == null ? "" : subject,
                credentialId == null ? "" : credentialId,
                admissionId == null ? "" : admissionId,
                clientId == null ? "" : clientId,
                sequence,
                timestamp,
                method,
                target,
                normalizeContentType(contentType),
                contentLength,
                idempotencyKey == null ? "" : idempotencyKey,
                bodySha256);
    }

    /** HMAC-SHA256 over the canonical request, keyed by the admission's base64url request key. */
    public static String signRequest(String requestKey, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(b64urlDecode(requestKey), "HmacSHA256"));
            return b64url(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 is required", e);
        }
    }

    public static String formatSequence(long value) {
        if (value < 0L) {
            throw new ArithmeticException("admission sequence exhausted");
        }
        return Long.toString(value);
    }

    /**
     * Decodes {@code a2.<base64url payload>.<signature>} and checks the fields the client has to
     * agree with before signing anything with it.
     */
    public static JsonObject parseAdmissionPayload(String token) {
        if (token == null) {
            throw new IllegalArgumentException("invalid admission token");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !"a2".equals(parts[0]) || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw new IllegalArgumentException("invalid admission token");
        }
        JsonObject payload;
        try {
            String json = new String(b64urlDecode(parts[1]), StandardCharsets.UTF_8);
            payload = new JsonParser().parse(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid admission payload", e);
        }
        Long version = asLong(payload.get("version"));
        if (version == null || version.longValue() != 2L) {
            throw new IllegalArgumentException("unsupported admission version");
        }
        String[] required = {"version", "aud", "admissionId", "subject", "credentialId", "clientId",
                "serverEpoch", "expiresAt"};
        for (String key : required) {
            if (!payload.has(key)) {
                throw new IllegalArgumentException("invalid admission payload");
            }
        }
        return payload;
    }

    /** Percent-encodes everything but RFC 3986 unreserved characters (SDK {@code Hmac.quote}). */
    public static String quote(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder(bytes.length);
        for (byte raw : bytes) {
            int character = raw & 0xFF;
            if (isUnreserved(character)) {
                result.append((char) character);
            } else {
                result.append('%');
                result.append(HEX[character >>> 4]);
                result.append(HEX[character & 0x0F]);
            }
        }
        return result.toString();
    }

    private static boolean isUnreserved(int character) {
        return (character >= 'A' && character <= 'Z')
                || (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '-' || character == '.' || character == '_' || character == '~';
    }

    /** Long value of a JSON scalar, or {@code null} when it is not an integer (SDK {@code asLong}). */
    public static Long asLong(com.google.gson.JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return null;
        }
        if (primitive.isNumber()) {
            try {
                double number = primitive.getAsDouble();
                if (number != Math.rint(number) || Double.isInfinite(number)) {
                    return null;
                }
                return Long.valueOf(primitive.getAsLong());
            } catch (RuntimeException e) {
                return null;
            }
        }
        try {
            return Long.valueOf(Long.parseLong(primitive.getAsString().trim()));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
