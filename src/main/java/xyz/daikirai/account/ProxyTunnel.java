package xyz.daikirai.account;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Onyx's HTTP CONNECT / SOCKS5 tunnel, adapted to Java 8 sockets. */
public final class ProxyTunnel {
    private ProxyTunnel() { }

    public static Socket open(ProxyConfig config, String host, int port) throws IOException {
        if (config.enabled && config.protocol == ProxyConfig.Protocol.FISPROXY) {
            return fisproxy();
        }
        Socket socket = new Socket(Proxy.NO_PROXY);
        try {
            socket.connect(config.enabled ? config.endpoint() : new InetSocketAddress(host, port), 10000);
            socket.setSoTimeout(15000);
            socket.setTcpNoDelay(true);
            if (config.enabled) {
                if (config.protocol == ProxyConfig.Protocol.SOCKS5) socks(socket, config, host, port);
                else http(socket, config, host, port);
            }
            socket.setSoTimeout(0);
            return socket;
        } catch (IOException | RuntimeException e) {
            try { socket.close(); } catch (IOException ignored) { }
            throw e;
        }
    }

    /**
     * FisProxy is an entrance, not a tunnel: the API session already routes to the upstream, so the
     * connection is a plain socket to {@code {sessionId}.{host}} with no handshake after it.
     *
     * <p>The entrance address is cached by {@link FisProxySession}; the game's own server address is
     * untouched, which keeps the handshake packet and the server list entry pointing at the real
     * host.</p>
     */
    private static Socket fisproxy() throws IOException {
        InetSocketAddress endpoint = xyz.daikirai.account.fisproxy.FisProxySession.requireEndpoint();
        Socket socket = new Socket(Proxy.NO_PROXY);
        try {
            socket.connect(endpoint, 10000);
            socket.setSoTimeout(0);
            socket.setTcpNoDelay(true);
            return socket;
        } catch (IOException e) {
            try { socket.close(); } catch (IOException ignored) { }
            throw e;
        }
    }

    private static int octet(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException("Proxy closed the connection");
        return b;
    }

    private static void socks(Socket s, ProxyConfig c, String host, int port) throws IOException {
        InputStream in = s.getInputStream(); OutputStream out = s.getOutputStream();
        boolean auth = !c.username.isEmpty();
        out.write(new byte[]{5, 1, (byte)(auth ? 2 : 0)}); out.flush();
        if (octet(in) != 5) throw new IOException("Invalid SOCKS5 greeting");
        int method = octet(in);
        if (method == 2 && auth) {
            byte[] user = c.username.getBytes(StandardCharsets.UTF_8), pass = c.password.getBytes(StandardCharsets.UTF_8);
            out.write(1); out.write(user.length); out.write(user); out.write(pass.length); out.write(pass); out.flush();
            if (octet(in) != 1 || octet(in) != 0) throw new IOException("SOCKS5 username or password rejected");
        } else if (method != 0 || auth) throw new IOException("SOCKS5 authentication method rejected");
        byte[] name = host.getBytes(StandardCharsets.UTF_8);
        if (name.length == 0 || name.length > 255) throw new IOException("Invalid SOCKS5 destination");
        out.write(new byte[]{5, 1, 0, 3, (byte)name.length}); out.write(name);
        out.write(port >>> 8); out.write(port & 255); out.flush();
        int version = octet(in), status = octet(in); octet(in); int type = octet(in);
        if (version != 5 || status != 0) throw new IOException("SOCKS5 CONNECT failed (" + status + ")");
        int length;
        switch (type) {
            case 1: length = 4; break;
            case 4: length = 16; break;
            case 3: length = octet(in); break;
            default: throw new IOException("Invalid SOCKS5 reply address");
        }
        for (int i = 0; i < length + 2; i++) octet(in);
    }

    private static void http(Socket s, ProxyConfig c, String host, int port) throws IOException {
        if (host.indexOf('\r') >= 0 || host.indexOf('\n') >= 0) throw new IOException("Invalid destination");
        String target = (host.indexOf(':') >= 0 ? "[" + host + "]" : host) + ":" + port;
        String request = "CONNECT " + target + " HTTP/1.1\r\nHost: " + target + "\r\n";
        if (!c.username.isEmpty()) request += "Proxy-Authorization: Basic " + Base64.getEncoder()
                .encodeToString((c.username + ":" + c.password).getBytes(StandardCharsets.UTF_8)) + "\r\n";
        s.getOutputStream().write((request + "\r\n").getBytes(StandardCharsets.US_ASCII));
        s.getOutputStream().flush();
        String header = readHeader(s.getInputStream());
        String[] status = header.substring(0, header.indexOf("\r\n")).split(" ", 3);
        if (status.length < 2 || !status[0].startsWith("HTTP/") || !status[1].matches("2[0-9][0-9]"))
            throw new IOException("HTTP proxy CONNECT failed (" + (status.length > 1 ? status[1] : "invalid reply") + ")");
    }

    public static String readHeader(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int state = 0;
        byte[] end = {13, 10, 13, 10};
        while (bytes.size() < 16384) {
            int b = octet(in); bytes.write(b);
            state = b == end[state] ? state + 1 : (b == 13 ? 1 : 0);
            if (state == 4) return new String(bytes.toByteArray(), StandardCharsets.US_ASCII);
        }
        throw new IOException("Proxy response headers are too large");
    }
}
