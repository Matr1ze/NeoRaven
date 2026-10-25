package xyz.daikirai.account;

import com.google.gson.JsonObject;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Immutable settings, shared by game connections and account requests. */
public final class ProxyConfig {
    /**
     * {@code SOCKS5} and {@code HTTP} are tunnels; {@code FISPROXY} is a per-session entrance handed
     * out by the FisProxy API, so it has no local host:port and no tunnel handshake - see
     * {@link xyz.daikirai.account.fisproxy.FisProxySession}.
     */
    public enum Protocol { SOCKS5, HTTP, FISPROXY }

    public final Protocol protocol;
    public final boolean enabled, useForAuth;
    public final String address, username, password, host;
    public final int port;
    /** FisProxy API token; empty for the tunnel protocols. */
    public final String apiToken;
    /** FisProxy service id, e.g. {@code us.hypixel}; empty lets the API pick the default. */
    public final String serviceId;
    /** FisProxy upstream target, e.g. {@code mc.hypixel.net}; empty lets the API/service decide. */
    public final String target;

    public ProxyConfig(Protocol protocol, boolean enabled, boolean useForAuth,
                       String address, String username, String password) {
        this(protocol, enabled, useForAuth, address, username, password, "", "", "");
    }

    public ProxyConfig(Protocol protocol, boolean enabled, boolean useForAuth,
                       String address, String username, String password,
                       String apiToken, String serviceId, String target) {
        this.protocol = protocol == null ? Protocol.SOCKS5 : protocol;
        this.enabled = enabled;
        // A session entrance cannot sign account HTTP requests, so authentication always goes direct.
        this.useForAuth = useForAuth && this.protocol != Protocol.FISPROXY;
        this.address = address == null ? "" : address.trim();
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.apiToken = apiToken == null ? "" : apiToken.trim();
        this.serviceId = serviceId == null ? "" : serviceId.trim();
        this.target = target == null ? "" : target.trim();
        String h = "";
        int p = 0;
        if (this.protocol != Protocol.FISPROXY && (enabled || !this.address.isEmpty())) {
            int colon = this.address.lastIndexOf(':');
            if (colon <= 0) throw new IllegalArgumentException("Enter proxy address as host:port");
            h = this.address.substring(0, colon).trim();
            if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
            if (h.isEmpty() || h.indexOf('/') >= 0 || h.indexOf('\r') >= 0 || h.indexOf('\n') >= 0)
                throw new IllegalArgumentException("Enter a valid proxy host");
            try { p = Integer.parseInt(this.address.substring(colon + 1)); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Enter a valid proxy port"); }
            if (p < 1 || p > 65535) throw new IllegalArgumentException("Enter a valid proxy port");
        }
        if (this.protocol == Protocol.SOCKS5 && (this.username.getBytes(StandardCharsets.UTF_8).length > 255
                || this.password.getBytes(StandardCharsets.UTF_8).length > 255))
            throw new IllegalArgumentException("SOCKS5 credentials must be at most 255 bytes");
        this.host = h;
        this.port = p;
    }

    public static ProxyConfig direct() { return new ProxyConfig(Protocol.SOCKS5, false, false, "", "", ""); }
    public InetSocketAddress endpoint() { return new InetSocketAddress(host, port); }

    public boolean fisproxy() { return protocol == Protocol.FISPROXY; }

    public JsonObject toJson() {
        JsonObject j = new JsonObject();
        j.addProperty("protocol", protocol.name()); j.addProperty("enabled", enabled);
        j.addProperty("auth", useForAuth); j.addProperty("address", address);
        j.addProperty("username", username); j.addProperty("password", password);
        j.addProperty("apiToken", apiToken); j.addProperty("serviceId", serviceId);
        j.addProperty("target", target);
        return j;
    }

    public static ProxyConfig fromJson(JsonObject j) {
        return new ProxyConfig(Protocol.valueOf(AccountManager.string(j, "protocol", "SOCKS5").toUpperCase(java.util.Locale.ROOT)),
                j.has("enabled") && j.get("enabled").getAsBoolean(), j.has("auth") && j.get("auth").getAsBoolean(),
                AccountManager.string(j, "address", ""), AccountManager.string(j, "username", ""), AccountManager.string(j, "password", ""),
                AccountManager.string(j, "apiToken", ""), AccountManager.string(j, "serviceId", ""),
                AccountManager.string(j, "target", ""));
    }
}
