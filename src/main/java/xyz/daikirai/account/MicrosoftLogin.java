package xyz.daikirai.account;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Onyx's OAuth -> Xbox Live -> XSTS -> Minecraft flow, with state and PKCE. */
public final class MicrosoftLogin implements Closeable {
    public static final String CLIENT_ID = System.getProperty("raven.microsoftClientId", "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb");
    private static final String AZURE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    private static final String SCOPE = "XboxLive.SignIn XboxLive.offline_access";
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r,"Raven login timeout"); t.setDaemon(true); return t;
    });
    public final CompletableFuture<AltAccount> result = new CompletableFuture<AltAccount>();
    public final String url;
    private final HttpServer server;
    private final ExecutorService listener;
    private final String state = random(), verifier = random(), redirect = "http://localhost:1337/";
    private final AtomicBoolean received = new AtomicBoolean();

    public MicrosoftLogin() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),1337),0);
        listener = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r,"Raven OAuth callback"); t.setDaemon(true); return t; });
        server.setExecutor(listener); server.createContext("/",this::callback);
        String challenge;
        try { challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException e) { server.stop(0); listener.shutdownNow(); throw new IOException(e); }
        url = AZURE + "authorize?client_id=" + AuthHttp.encode(CLIENT_ID) + "&response_type=code&redirect_uri=" + AuthHttp.encode(redirect)
                + "&scope=" + AuthHttp.encode(SCOPE) + "&prompt=select_account&state=" + state + "&code_challenge_method=S256&code_challenge=" + challenge;
        server.start();
        ScheduledFuture<?> timeout = TIMER.schedule(() -> result.completeExceptionally(new IOException("Microsoft sign-in timed out")),300,TimeUnit.SECONDS);
        result.whenComplete((a,e) -> { timeout.cancel(false); server.stop(0); listener.shutdown(); });
    }

    private static String random() {
        byte[] b = new byte[32]; new SecureRandom().nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private void callback(HttpExchange exchange) throws IOException {
        Map<String,String> query = new HashMap<String,String>();
        String raw = exchange.getRequestURI().getRawQuery();
        try {
            if (raw != null && raw.length() <= 8192) for (String pair : raw.split("&")) {
                String[] parts = pair.split("=",2);
                query.put(URLDecoder.decode(parts[0],"UTF-8"),parts.length == 2 ? URLDecoder.decode(parts[1],"UTF-8") : "");
            }
        } catch (IllegalArgumentException ignored) { }
        boolean valid = "GET".equals(exchange.getRequestMethod()) && "/".equals(exchange.getRequestURI().getPath())
                && state.equals(query.get("state")) && (query.containsKey("code") || query.containsKey("error"));
        byte[] response = (valid ? "Sign-in received. Return to Minecraft." : "Invalid sign-in callback.").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.sendResponseHeaders(valid ? 200 : 400,response.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(response); }
        if (!valid || !received.compareAndSet(false,true)) return;
        if (query.containsKey("error")) { result.completeExceptionally(new IOException("Microsoft sign-in was declined")); return; }
        String code = query.get("code");
        AccountManager.WORKER.execute(() -> {
            if (result.isDone()) return;
            try {
                JsonObject tokens = AuthHttp.request(AZURE + "token","POST","client_id=" + AuthHttp.encode(CLIENT_ID)
                        + "&redirect_uri=" + AuthHttp.encode(redirect) + "&scope=" + AuthHttp.encode(SCOPE)
                        + "&grant_type=authorization_code&code=" + AuthHttp.encode(code) + "&code_verifier=" + verifier,
                        "application/x-www-form-urlencoded",null);
                AltAccount a = new AltAccount(AltAccount.Type.MICROSOFT,"Account");
                exchange(a,tokens,"azure"); result.complete(a);
            } catch (Exception e) { result.completeExceptionally(e); }
        });
    }

    public static AltAccount fromRefresh(String token) throws IOException {
        if (token.trim().isEmpty()) throw new IOException("Paste a Microsoft refresh token");
        AltAccount a = new AltAccount(AltAccount.Type.MICROSOFT,"Account"); a.refreshToken = token.trim();
        try { a.flavour = "legacy"; refresh(a); }
        catch (IOException first) { a.flavour = "azure"; refresh(a); }
        return a;
    }

    public static void refresh(AltAccount a) throws IOException {
        boolean legacy = "legacy".equals(a.flavour);
        String client = legacy ? "00000000402b5328" : CLIENT_ID;
        String form = "client_id=" + AuthHttp.encode(client) + "&grant_type=refresh_token&refresh_token=" + AuthHttp.encode(a.refreshToken)
                + "&scope=" + AuthHttp.encode(legacy ? "service::user.auth.xboxlive.com::MBI_SSL" : SCOPE);
        if (legacy) form += "&redirect_uri=" + AuthHttp.encode("https://login.live.com/oauth20_desktop.srf");
        JsonObject tokens = AuthHttp.request(legacy ? "https://login.live.com/oauth20_token.srf" : AZURE + "token",
                "POST",form,"application/x-www-form-urlencoded",null);
        exchange(a,tokens,a.flavour);
    }

    private static JsonObject post(String url, JsonObject body) throws IOException {
        return AuthHttp.request(url,"POST",body.toString(),"application/json",null);
    }

    private static String required(JsonObject object, String key) throws IOException {
        String value = AccountManager.string(object,key,"");
        if (value.isEmpty()) throw new IOException("Login service returned no " + key);
        return value;
    }

    private static void exchange(AltAccount a, JsonObject tokens, String flavour) throws IOException {
        String msa = required(tokens,"access_token");
        JsonObject properties = new JsonObject(); properties.addProperty("AuthMethod","RPS");
        properties.addProperty("SiteName","user.auth.xboxlive.com"); properties.addProperty("RpsTicket",("legacy".equals(flavour) ? "t=" : "d=") + msa);
        JsonObject body = new JsonObject(); body.add("Properties",properties); body.addProperty("RelyingParty","http://auth.xboxlive.com"); body.addProperty("TokenType","JWT");
        JsonObject xbox = post("https://user.auth.xboxlive.com/user/authenticate",body);
        String hash;
        try { hash = xbox.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString(); }
        catch (RuntimeException e) { throw new IOException("Xbox returned no user profile"); }
        properties = new JsonObject(); properties.addProperty("SandboxId","RETAIL");
        JsonArray users = new JsonArray(); users.add(new JsonPrimitive(required(xbox,"Token"))); properties.add("UserTokens",users);
        body = new JsonObject(); body.add("Properties",properties); body.addProperty("RelyingParty","rp://api.minecraftservices.com/"); body.addProperty("TokenType","JWT");
        JsonObject xsts = post("https://xsts.auth.xboxlive.com/xsts/authorize",body);
        body = new JsonObject(); body.addProperty("identityToken","XBL3.0 x=" + hash + ";" + required(xsts,"Token"));
        JsonObject minecraft = post("https://api.minecraftservices.com/authentication/login_with_xbox",body);
        String access = required(minecraft,"access_token");
        JsonObject profile = AuthHttp.request("https://api.minecraftservices.com/minecraft/profile","GET",null,null,access);
        // Update only after the entire exchange succeeds, keeping the previous credentials usable on failure.
        a.name = required(profile,"name"); a.uuid = AltAccount.parseUuid(required(profile,"id"));
        a.accessToken = access; a.refreshToken = AccountManager.string(tokens,"refresh_token",a.refreshToken); a.flavour = flavour;
        a.expiresAt = System.currentTimeMillis() + (minecraft.has("expires_in") ? minecraft.get("expires_in").getAsLong() : 86400L) * 1000L;
    }

    @Override public void close() { result.cancel(false); }
}
