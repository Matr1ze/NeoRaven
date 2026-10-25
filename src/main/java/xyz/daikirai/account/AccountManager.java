package xyz.daikirai.account;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import xyz.daikirai.mixin.IMinecraft;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class AccountManager {
    private static final AccountManager INSTANCE = new AccountManager(new File(xyz.daikirai.client.ClientConfig.DIR,"accounts.json").toPath());
    public static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r,"Raven accounts"); t.setDaemon(true); return t;
    });
    private final Path file;
    private final List<AltAccount> accounts = new ArrayList<AltAccount>();
    private volatile ProxyConfig proxy = ProxyConfig.direct();
    private Session launcher;
    private AltAccount active;
    private boolean loaded, writable = true;
    private String loadError;

    public AccountManager(Path file) { this.file = file; }
    public static AccountManager instance() { return INSTANCE; }
    public ProxyConfig proxy() { return proxy; }
    public synchronized ProxyConfig connectionProxy() {
        if (!writable) throw new IllegalStateException(loadError);
        return proxy;
    }
    public synchronized AltAccount active() { return active; }
    public synchronized List<AltAccount> accounts() { return new ArrayList<AltAccount>(accounts); }
    public synchronized String loadError() { return loadError; }

    public synchronized void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(file)) return;
        try {
            JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file),StandardCharsets.UTF_8)).getAsJsonObject();
            ProxyConfig loadedProxy = root.has("proxy") ? ProxyConfig.fromJson(root.getAsJsonObject("proxy")) : ProxyConfig.direct();
            List<AltAccount> loadedAccounts = new ArrayList<AltAccount>();
            if (root.has("accounts")) for (JsonElement e : root.getAsJsonArray("accounts")) {
                loadedAccounts.add(AltAccount.fromJson(e.getAsJsonObject()));
            }
            proxy = loadedProxy;
            accounts.addAll(loadedAccounts);
            xyz.daikirai.account.fisproxy.FisProxySession.configure(proxy);
        } catch (IOException | RuntimeException e) {
            writable = false;
            loadError = "Could not read accounts.json; existing file has been preserved";
        }
    }

    public synchronized void save() throws IOException {
        if (!writable) throw new IOException(loadError);
        JsonObject root = new JsonObject(); root.addProperty("version",1);
        JsonArray list = new JsonArray(); for (AltAccount a : accounts) list.add(a.toJson());
        root.add("accounts",list); root.add("proxy",proxy.toJson());
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8));
        try { Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
    }

    public synchronized void add(AltAccount account) throws IOException {
        for (AltAccount a : accounts) if (a.type == account.type && a.uuid.equals(account.uuid))
            throw new IOException("This account is already saved");
        accounts.add(account);
        try { save(); } catch (IOException e) { accounts.remove(account); throw e; }
    }

    public synchronized void remove(AltAccount account) throws IOException {
        int index = accounts.indexOf(account); if (index < 0) return;
        accounts.remove(index);
        try { save(); } catch (IOException e) { accounts.add(index,account); throw e; }
        if (active == account) active = null;
    }

    public synchronized void proxy(ProxyConfig value) throws IOException {
        ProxyConfig previous = proxy; proxy = value;
        // Keep the FisProxy session pointed at the token/service/target that were just applied, so a
        // connection made after a restart still finds the running session without opening the page.
        xyz.daikirai.account.fisproxy.FisProxySession.configure(value);
        try { save(); } catch (IOException e) { proxy = previous; throw e; }
    }

    public synchronized void activate(AltAccount a, Session session) throws IOException {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) throw new IOException("Disconnect before switching accounts");
        if (!accounts.contains(a)) throw new IOException("Account has been removed");
        save();
        if (launcher == null) launcher = mc.getSession();
        ((IMinecraft)mc).setSession(session); active = a;
    }

    public synchronized void restoreLauncher() throws IOException {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) throw new IOException("Disconnect before switching accounts");
        if (launcher != null) ((IMinecraft)mc).setSession(launcher);
        active = null;
    }

    public static String string(JsonObject j, String key, String fallback) {
        JsonElement e = j.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : fallback;
    }
}
