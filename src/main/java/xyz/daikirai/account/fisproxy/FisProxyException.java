package xyz.daikirai.account.fisproxy;

/**
 * A failed FisProxy call: either an API error (with its {@code errorCode}) or a transport failure
 * ({@code status == 0}).
 *
 * <p>The message is the API's own wording so the Proxy page can show it verbatim; the machine code
 * stays available for the callers that care (admission refresh, operation failures).</p>
 */
public class FisProxyException extends RuntimeException {

    private final int status;
    private final String code;

    public FisProxyException(int status, String code, String message) {
        super(describe(code, message, status));
        this.status = status;
        this.code = code == null ? "" : code;
    }

    private static String describe(String code, String message, int status) {
        String text = message == null ? "" : message.trim();
        if (!text.isEmpty()) {
            return text;
        }
        if (code != null && !code.trim().isEmpty()) {
            return code.trim();
        }
        return status > 0 ? "FisProxy HTTP " + status : "FisProxy request failed";
    }

    /** HTTP status, or {@code 0} for transport and operation failures. */
    public int status() {
        return status;
    }

    /** Machine-readable API error code, or {@code ""}. */
    public String code() {
        return code;
    }
}
