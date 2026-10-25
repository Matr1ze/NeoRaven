package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.network.OldServerPinger;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

import java.util.ArrayList;
import java.util.List;

/** Android-style multiplayer screen: saved servers as a Material list with live ping readouts. */
public final class AndroidMultiplayerScreen extends AndroidListScreen {

    private ServerList servers;
    private OldServerPinger pinger;
    private final List<ServerData> entries = new ArrayList<ServerData>();

    public AndroidMultiplayerScreen(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected void onShown() {
        if (servers == null) {
            servers = new ServerList(mc);
        }
        if (pinger == null) {
            pinger = new OldServerPinger();
        }
        reload();
        pingAll();
    }

    private void reload() {
        entries.clear();
        try {
            servers.loadServerList();
            for (int i = 0; i < servers.countServers(); i++) {
                ServerData data = servers.getServerData(i);
                if (data != null) {
                    entries.add(data);
                }
            }
        } catch (Exception ignored) {
        }
        selected = -1;
        scrollTarget = 0.0F;
    }

    private void pingAll() {
        if (pinger == null) {
            return;
        }
        pinger.clearPendingNetworks();
        for (ServerData data : entries) {
            data.pingToServer = -1L;
            try {
                pinger.ping(data);
            } catch (Exception ignored) {
                data.pingToServer = -1L;
            }
        }
    }

    @Override
    public void updateScreen() {
        if (pinger != null) {
            pinger.pingPendingNetworks();
        }
    }

    @Override
    public void onGuiClosed() {
        if (pinger != null) {
            pinger.clearPendingNetworks();
        }
    }

    @Override
    protected String title() {
        return "Multiplayer";
    }

    @Override
    protected String caption() {
        return entries.size() + (entries.size() == 1 ? " server" : " servers");
    }

    @Override
    protected int rowCount() {
        return entries.size();
    }

    @Override
    protected String emptyText() {
        return "No servers yet - add one to join";
    }

    @Override
    protected float rowHeight() {
        return 74.0F;
    }

    @Override
    protected void drawRow(int index, float x, float y, float width, float height) {
        ServerData data = entries.get(index);
        boolean active = index == selected;
        int ink = active ? AndroidWallpaper.onPrimaryContainer() : AndroidWallpaper.ink();
        int soft = active ? Material.withAlpha(AndroidWallpaper.onPrimaryContainer(), 0.7F)
                : AndroidWallpaper.faintInk();
        ui.circle(x + 24.0F, y + height / 2.0F, 19.0F,
                active ? Material.withAlpha(AndroidWallpaper.onPrimaryContainer(), 0.14F)
                        : AndroidWallpaper.surfaceHigh());
        ui.icon("\ue1db", x + 24.0F, y + height / 2.0F, 18.0F,
                active ? AndroidWallpaper.onPrimaryContainer() : AndroidWallpaper.onSurfaceVariant());
        ui.text(Typeface.BODY_MEDIUM, data.serverName == null ? "" : data.serverName, x + 56.0F, y + 22.0F,
                ink, UI.ALIGN_LEFT);
        String address = data.serverIP == null ? "" : data.serverIP;
        ui.text(Typeface.CAPTION, address, x + 56.0F, y + 39.0F, soft, UI.ALIGN_LEFT);
        String motd = data.serverMOTD == null ? "" : stripColors(data.serverMOTD).trim();
        if (!motd.isEmpty()) {
            ui.text(Typeface.CAPTION, ui.truncate(Typeface.CAPTION, motd, width - 130.0F), x + 56.0F, y + 56.0F,
                    soft, UI.ALIGN_LEFT);
        }
        drawPing(x + width - 24.0F, y + height / 2.0F, data);
    }

    /** Removes legacy colour/format codes, including the "\\u00a7x" hex form. */
    private static String stripColors(String text) {
        return text.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "").replaceAll("\u00a7x(\u00a7[0-9a-fA-F]){6}", "")
                .replaceAll("\u00a7.", "");
    }

    /** Four signal bars plus the player count, like the vanilla ping readout. */
    private void drawPing(float right, float cy, ServerData data) {
        long ping = data.pingToServer;
        int bars = 0;
        int color = 0xFF9AA0A6;
        if (ping >= 0) {
            if (ping < 150L) {
                bars = 4;
                color = 0xFF2E7D32;
            } else if (ping < 350L) {
                bars = 3;
                color = 0xFF689F38;
            } else if (ping < 700L) {
                bars = 2;
                color = 0xFFF9A825;
            } else {
                bars = 1;
                color = 0xFFC62828;
            }
        }
        for (int i = 0; i < 4; i++) {
            float h = 5.0F + i * 3.0F;
            float x = right - 30.0F + i * 6.0F;
            ui.rounded(x, cy + 8.0F - h, 4.0F, h, 1.5F,
                    i < bars ? color : Material.withAlpha(color, 0.22F));
        }
        // populationInfo arrives with §7/§8 colour codes; the M3 caption supplies the colour here.
        String label = data.populationInfo == null ? "" : stripColors(data.populationInfo);
        if (label.isEmpty()) {
            label = ping < 0 ? "Pinging\u2026" : (ping + " ms");
        }
        ui.text(Typeface.CAPTION, ui.truncate(Typeface.CAPTION, label, 74.0F), right - 52.0F, cy - 12.0F,
                AndroidWallpaper.faintInk(), UI.ALIGN_RIGHT);
    }

    @Override
    protected void activate(int index) {
        join(index);
    }

    private void join(int index) {
        if (index < 0 || index >= entries.size() || busy) {
            return;
        }
        busy = true;
        mc.displayGuiScreen(new GuiConnecting(this, mc, entries.get(index)));
    }

    @Override
    protected void buildActions(List<Action> actions) {
        actions.add(new Action("Add server", TONAL, () -> mc.displayGuiScreen(new AndroidServerScreen(this, -1))));
        // One-off join: no entry is written to servers.dat (that is what "Add server" is for).
        actions.add(new Action("Direct connect", TONAL, this::directConnect));
        actions.add(new Action("Refresh", TEXT, () -> {
            pingAll();
        }));
        actions.add(new Action("Edit", TEXT,
                () -> mc.displayGuiScreen(new AndroidServerScreen(this, selected)), selected >= 0));
        actions.add(new Action("Delete", DANGER, this::askDelete, selected >= 0));
        actions.add(new Action("Join", FILLED, () -> join(selected), selected >= 0));
    }

    /**
     * Opens the direct-connect dialog, prefilled with the selected server so the button also works as
     * "join this address once more, maybe with a different port".
     */
    private void directConnect() {
        String initial = "";
        if (selected >= 0 && selected < entries.size()) {
            ServerData data = entries.get(selected);
            if (data.serverIP != null) {
                initial = data.serverIP;
            }
        }
        mc.displayGuiScreen(new AndroidDirectConnectScreen(this, initial));
    }

    private void askDelete() {
        if (selected < 0 || selected >= entries.size()) {
            return;
        }
        final int index = selected;
        final ServerData data = entries.get(index);
        confirm("Remove server?", "\"" + data.serverName + "\" will be removed from your list.", "Remove",
                () -> remove(index));
    }

    private void remove(int index) {
        try {
            servers.removeServerData(index);
            servers.saveServerList();
        } catch (Exception ignored) {
        }
        reload();
        selected = Math.min(index, entries.size() - 1);
    }

    // ------------------------------------------------------------------ //
    // Used by the add/edit dialog
    // ------------------------------------------------------------------ //

    void store(int index, String name, String address) {
        try {
            if (index >= 0 && index < servers.countServers()) {
                servers.removeServerData(index);
            }
            ServerData data = new ServerData(name, address, false);
            servers.addServerData(data);
            servers.saveServerList();
        } catch (Exception ignored) {
        }
        reload();
        pingAll();
    }

    ServerData entry(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }
}
