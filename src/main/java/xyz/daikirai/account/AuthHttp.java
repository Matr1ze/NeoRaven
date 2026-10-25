package xyz.daikirai.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** HTTPS with per-request proxy credentials; no global JVM authenticator. */
public final class AuthHttp {
    private static final int LIMIT = 2 * 1024 * 1024;
    private AuthHttp() { }

    public static JsonObject request(String url, String method, String body, String contentType, String bearer) throws IOException {
        ProxyConfig c = AccountManager.instance().connectionProxy();
        return request(url, method, body, contentType, bearer, c.useForAuth ? c : ProxyConfig.direct());
    }

    public static JsonObject request(String url, String method, String body, String contentType, String bearer, ProxyConfig proxy) throws IOException {
        URL target = new URL(url);
        if (!"https".equals(target.getProtocol())) throw new IOException("HTTPS is required");
        int port = target.getPort() < 0 ? 443 : target.getPort();
        try (Socket tunnel = ProxyTunnel.open(proxy, target.getHost(), port);
             SSLSocket socket = (SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault())
                     .createSocket(tunnel, target.getHost(), port, true)) {
            socket.setSoTimeout(20000);
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS"); socket.setSSLParameters(parameters);
            socket.startHandshake();
            byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            String path = target.getFile().isEmpty() ? "/" : target.getFile();
            String header = method + " " + path + " HTTP/1.1\r\nHost: " + target.getHost()
                    + "\r\nUser-Agent: RavenB4\r\nAccept: application/json\r\nConnection: close\r\n";
            if (bearer != null) {
                if (bearer.contains("\r") || bearer.contains("\n")) throw new IOException("Invalid access token");
                header += "Authorization: Bearer " + bearer + "\r\n";
            }
            if (body != null) header += "Content-Type: " + contentType + "\r\nContent-Length: " + payload.length + "\r\n";
            OutputStream out = socket.getOutputStream();
            out.write((header + "\r\n").getBytes(StandardCharsets.US_ASCII)); out.write(payload); out.flush();
            InputStream in = socket.getInputStream();
            String[] lines = ProxyTunnel.readHeader(in).split("\r\n");
            int status;
            try { status = Integer.parseInt(lines[0].split(" ")[1]); }
            catch (RuntimeException e) { throw new IOException("Invalid HTTP response"); }
            Map<String,String> headers = new HashMap<String,String>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon > 0) headers.put(lines[i].substring(0,colon).toLowerCase(Locale.ROOT), lines[i].substring(colon+1).trim());
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if ("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
                while (true) {
                    String line = line(in); int size;
                    try { size = Integer.parseInt(line.split(";",2)[0].trim(), 16); }
                    catch (RuntimeException e) { throw new IOException("Invalid HTTP chunk"); }
                    if (size == 0) break;
                    copy(in, bytes, size);
                    if (!line(in).isEmpty()) throw new IOException("Invalid HTTP chunk ending");
                }
            } else if (headers.containsKey("content-length")) {
                try { copy(in, bytes, Integer.parseInt(headers.get("content-length"))); }
                catch (NumberFormatException e) { throw new IOException("Invalid response size"); }
            } else {
                byte[] buffer = new byte[4096]; int n;
                while ((n = in.read(buffer)) >= 0) {
                    if (bytes.size() + n > LIMIT) throw new IOException("Response is too large");
                    bytes.write(buffer,0,n);
                }
            }
            JsonObject result;
            try { result = new JsonParser().parse(new String(bytes.toByteArray(),StandardCharsets.UTF_8)).getAsJsonObject(); }
            catch (RuntimeException e) { throw new IOException("Service returned HTTP " + status + " without a JSON response"); }
            if (status < 200 || status >= 300) {
                String error = status == 401 ? "Access token is invalid or expired" : "Login service returned HTTP " + status;
                if (result.has("XErr")) error += " (Xbox " + result.get("XErr").getAsString() + ")";
                // Never display or log raw token-endpoint response bodies.
                throw new IOException(error);
            }
            return result;
        }
    }

    private static void copy(InputStream in, ByteArrayOutputStream out, int count) throws IOException {
        if (count < 0 || out.size() + (long)count > LIMIT) throw new IOException("Response is too large");
        byte[] buffer = new byte[4096];
        while (count > 0) {
            int n = in.read(buffer,0,Math.min(buffer.length,count));
            if (n < 0) throw new EOFException("Incomplete response");
            out.write(buffer,0,n); count -= n;
        }
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); int last = -1;
        while (out.size() < 4096) {
            int b = in.read(); if (b < 0) throw new EOFException("Incomplete response");
            if (last == 13 && b == 10) return new String(out.toByteArray(),StandardCharsets.US_ASCII).trim();
            out.write(b); last = b;
        }
        throw new IOException("Response line is too large");
    }

    public static String encode(String value) {
        try { return URLEncoder.encode(value,"UTF-8"); }
        catch (UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
}
