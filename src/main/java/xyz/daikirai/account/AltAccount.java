package xyz.daikirai.account;

import com.google.gson.JsonObject;
import net.minecraft.util.Session;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class AltAccount {
    public enum Type { OFFLINE, ACCESS_TOKEN, MICROSOFT }
    public final Type type;
    public String name, uuid, accessToken = "", refreshToken = "", flavour = "azure";
    public long expiresAt;
    public final long addedAt;

    public AltAccount(Type type, String name) { this(type,name,System.currentTimeMillis()); }
    private AltAccount(Type type, String name, long addedAt) {
        this.type = type; this.name = name; this.addedAt = addedAt;
        this.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static AltAccount offline(String name, boolean premiumUuid) throws IOException {
        name = name.trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) throw new IOException("Username must be 1-16 letters, digits or underscores");
        AltAccount account = new AltAccount(Type.OFFLINE,name);
        if (premiumUuid) {
            JsonObject profile = AuthHttp.request("https://api.mojang.com/users/profiles/minecraft/" + name,"GET",null,null,null);
            account.name = profile.get("name").getAsString(); account.uuid = parseUuid(profile.get("id").getAsString());
        }
        return account;
    }

    public static AltAccount access(String token) throws IOException {
        AltAccount account = new AltAccount(Type.ACCESS_TOKEN, "Account");
        account.accessToken = token.trim(); account.refreshProfile(); return account;
    }

    public void refreshProfile() throws IOException {
        if (accessToken.isEmpty()) throw new IOException("Paste a Minecraft access token");
        JsonObject profile = AuthHttp.request("https://api.minecraftservices.com/minecraft/profile","GET",null,null,accessToken);
        if (!profile.has("id") || !profile.has("name")) throw new IOException("Minecraft returned an incomplete profile");
        name = profile.get("name").getAsString(); uuid = parseUuid(profile.get("id").getAsString());
    }

    public Session session() throws IOException {
        if (type == Type.MICROSOFT && (accessToken.isEmpty() || System.currentTimeMillis() >= expiresAt - 60000))
            MicrosoftLogin.refresh(this);
        else if (type == Type.ACCESS_TOKEN) refreshProfile();
        return new Session(name, uuid.replace("-",""), type == Type.OFFLINE ? "0" : accessToken,
                type == Type.OFFLINE ? "legacy" : "mojang");
    }

    public static String parseUuid(String value) throws IOException {
        String u = value.replace("-","");
        if (!u.matches("[0-9a-fA-F]{32}")) throw new IOException("Malformed Minecraft UUID");
        return u.substring(0,8)+"-"+u.substring(8,12)+"-"+u.substring(12,16)+"-"+u.substring(16,20)+"-"+u.substring(20);
    }

    public JsonObject toJson() {
        JsonObject j = new JsonObject(); j.addProperty("type", type.name()); j.addProperty("name",name);
        j.addProperty("uuid",uuid); j.addProperty("addedAt",addedAt);
        if (type != Type.OFFLINE) { j.addProperty("accessToken",accessToken); j.addProperty("expiresAt",expiresAt); }
        if (type == Type.MICROSOFT) { j.addProperty("refreshToken",refreshToken); j.addProperty("flavour",flavour); }
        return j;
    }

    public static AltAccount fromJson(JsonObject j) throws IOException {
        Type type = Type.valueOf(AccountManager.string(j,"type","").toUpperCase(java.util.Locale.ROOT));
        String name = AccountManager.string(j,"name","");
        if (!name.matches("[A-Za-z0-9_]{1,16}")) throw new IOException("Invalid account name");
        AltAccount a = new AltAccount(type,name,j.has("addedAt") ? j.get("addedAt").getAsLong() : 0L);
        a.uuid = parseUuid(AccountManager.string(j,"uuid",a.uuid));
        a.accessToken = AccountManager.string(j,"accessToken",""); a.refreshToken = AccountManager.string(j,"refreshToken","");
        a.flavour = AccountManager.string(j,"flavour","azure"); a.expiresAt = j.has("expiresAt") ? j.get("expiresAt").getAsLong() : 0L;
        return a;
    }
}
