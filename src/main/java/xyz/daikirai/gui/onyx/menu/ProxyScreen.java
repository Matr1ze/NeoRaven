package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import xyz.daikirai.account.*;
import xyz.daikirai.account.fisproxy.FisProxySession;
import xyz.daikirai.gui.onyx.Widgets;
import xyz.daikirai.gui.onyx.material.*;
import xyz.daikirai.gui.onyx.render.*;
import javax.net.ssl.*;
import java.net.Socket;

/**
 * Proxy settings: a SOCKS5 / HTTP tunnel, or a FisProxy API session.
 *
 * <p>FisProxy is not a tunnel - the API starts a billed session and hands back an entrance address
 * ({@code {sessionId}.{host}}) that Minecraft connects to - so the third mode swaps the
 * host/username/password rows for an API token, an optional service id and an optional upstream
 * target, and replaces "Test" with the session controls. The session state itself lives in
 * {@link FisProxySession}; this page only drives it and renders its summary, which is why the
 * entrance stays usable after the page is closed.</p>
 */
public final class ProxyScreen extends MenuScreen {
    private MenuField address,user,password;
    private MenuField token,service,target;
    private boolean enabled,auth;
    private ProxyConfig.Protocol protocol;
    /** Session summary under the "Use proxy" row, refreshed by {@link #refreshSession()}. */
    private String sessionLine="",balanceLine="";

    public ProxyScreen(GuiScreen parent) { super(parent,470,438); }

    @Override void createFields() {
        ProxyConfig c=AccountManager.instance().proxy(); enabled=c.enabled; auth=c.useForAuth; protocol=c.protocol;
        address=new MenuField("Address (host:port)",24,180,422,false,512); address.text(c.address);
        user=new MenuField("Username",24,240,422,false,255); user.text(c.username);
        password=new MenuField("Password",24,300,422,true,255); password.text(c.password);
        token=new MenuField("FisProxy API token",24,180,422,true,512); token.text(c.apiToken);
        service=new MenuField("Service (optional, e.g. us.hypixel)",24,240,422,false,128); service.text(c.serviceId);
        target=new MenuField("Target (optional, e.g. mc.hypixel.net)",24,300,422,false,255); target.text(c.target);
        FisProxySession.configure(c);
        sessionLine=FisProxySession.summary();
        balanceLine=FisProxySession.balanceLine();
        if(c.protocol==ProxyConfig.Protocol.FISPROXY && !c.apiToken.trim().isEmpty()) refreshSession();
    }

    /** Shows the field set the current mode uses and drops focus from the hidden one. */
    private void syncMode() {
        boolean fisproxy=protocol==ProxyConfig.Protocol.FISPROXY;
        address.visible=user.visible=password.visible=!fisproxy;
        token.visible=service.visible=target.visible=fisproxy;
        for(MenuField f:fields) if(!f.visible) f.input.setFocused(false);
    }

    private ProxyConfig draft(boolean force) {
        return new ProxyConfig(protocol,force||enabled,auth,address.text(),user.text(),password.text(),
                token.text(),service.text(),target.text());
    }

    private boolean fisproxy() { return protocol==ProxyConfig.Protocol.FISPROXY; }

    @Override void drawContent(Scheme s) {
        syncMode();
        title("Proxy");
        Widgets.toggle(ui,s,24,57,enabled?1:0,mx>=24&&mx<62&&my>=57&&my<79,false);
        ui.text(Typeface.BODY,fisproxy()?"Use FisProxy for game connections":"Use proxy",76,68,s.onSurface,UI.ALIGN_LEFT);
        button(24,54,422,28,"",null,3,()->enabled=!enabled);
        if(fisproxy()) {
            // Two compact lines where the tunnel modes keep their authentication switch.
            // The session's entrance address doubles as the Minecraft connect string, so when one is
            // published a "Join" pill sits at the end of the line and connects straight to it.
            String sessionAddress=FisProxySession.address();
            boolean joinable=sessionAddress!=null && !sessionAddress.trim().isEmpty();
            float lineWidth=joinable?422.0F-84.0F:422.0F;
            ui.scissorPush(24,90,lineWidth,14);
            ui.text(Typeface.LABEL,sessionLine.isEmpty()?"No session":sessionLine,24,96,
                    s.onSurfaceVariant,UI.ALIGN_LEFT);
            ui.scissorPop();
            if(joinable) {
                button(362,86,84,24,"Join","\ue037",1,this::joinSession);
            }
            if(!balanceLine.isEmpty()) {
                ui.scissorPush(24,106,422,14);
                ui.text(Typeface.CAPTION,balanceLine,24,112,s.onSurfaceVariant,UI.ALIGN_LEFT);
                ui.scissorPop();
            }
        } else {
            Widgets.toggle(ui,s,24,92,auth?1:0,mx>=24&&mx<62&&my>=92&&my<114,false);
            ui.text(Typeface.BODY,"Use for account authentication",76,103,s.onSurface,UI.ALIGN_LEFT);
            button(24,89,422,28,"",null,3,()->auth=!auth);
        }

        // Mode selector: three segments instead of two.
        float segment=134.0F;
        ui.rounded(24,128,422,30,15,s.surfaceContainerHighest);
        int selected=fisproxy()?0:protocol==ProxyConfig.Protocol.SOCKS5?1:2;
        ui.rounded(24+selected*(segment+8.0F),128,selected==2?138.0F:segment,30,15,s.secondaryContainer);
        button(24,128,segment,30,"FisProxy",null,2,()->protocol=ProxyConfig.Protocol.FISPROXY);
        button(24+segment+8.0F,128,segment,30,"SOCKS5",null,2,()->protocol=ProxyConfig.Protocol.SOCKS5);
        button(24+(segment+8.0F)*2,128,138,30,"HTTP/HTTPS",null,2,()->protocol=ProxyConfig.Protocol.HTTP);

        if(fisproxy()) {
            button(24,358,140,38,FisProxySession.running()?"Stop session":"Start session",
                    FisProxySession.running()?"\ue5cd":"\ue037",FisProxySession.running()?0:1,this::toggleSession);
            button(174,358,120,38,"Change IP","\ue5d5",2,this::changeIp);
            button(304,358,142,38,"Apply","\ue161",1,this::submit);
        } else {
            button(226,358,100,38,"Test","\ue86f",2,this::test);
            button(338,358,108,38,"Apply","\ue161",1,this::submit);
        }
    }

    @Override void submit() {
        try {
            ProxyConfig c=draft(false); AccountManager.instance().proxy(c);
            FisProxySession.configure(c);
            if(c.fisproxy()) {
                if(!c.enabled) message("FisProxy disabled",false);
                else if(FisProxySession.running()) message("FisProxy applied · "+FisProxySession.summary(),false);
                else message("FisProxy saved - press Start session",false);
            } else message(c.enabled?"Proxy settings applied":"Proxy disabled",false);
            sessionLine=FisProxySession.summary(); balanceLine=FisProxySession.balanceLine();
        } catch(Exception e) { message(failure(e),true); }
    }

    /** Start ask the API to open a billed session; Stop settles it. Applying stays explicit. */
    private void toggleSession() {
        try {
            if(FisProxySession.running()) {
                message("Stopping FisProxy session...",false);
                task(()->{ FisProxySession.stop(); return true; },ok->{
                    enabled=false;
                    try { AccountManager.instance().proxy(draft(false)); } catch(Exception e) { }
                    sessionLine=FisProxySession.summary(); balanceLine=FisProxySession.balanceLine();
                    message("FisProxy session stopped",false);
                });
                return;
            }
            final ProxyConfig c=draft(false);
            if(c.apiToken.trim().isEmpty()) { message("Enter your FisProxy API token",true); return; }
            message("Starting FisProxy session...",false);
            task(()->{ FisProxySession.configure(c); FisProxySession.start(); return true; },ok->{
                // A session that nobody routes through still bills, so apply it in the same step.
                enabled=true;
                try { AccountManager.instance().proxy(draft(false)); } catch(Exception e) { message(failure(e),true); }
                sessionLine=FisProxySession.summary(); balanceLine=FisProxySession.balanceLine();
                message("FisProxy running - proxy applied",false);
            });
        } catch(Exception e) { message(failure(e),true); }
    }

    /**
     * Connects Minecraft straight to the session's entrance address.
     *
     * <p>That address is the very string the FisProxy dashboard tells players to paste into the
     * server list ({@code {sessionId}.{host}}), so this is only the one-click version of it: the
     * session itself does the routing, which is why the proxy toggle does not have to be on and no
     * tunnel handshake happens. A missing port falls back to 25565 inside
     * {@code ServerAddress.fromString}, exactly like the address field on the multiplayer page.</p>
     */
    private void joinSession() {
        String address = FisProxySession.address();
        if (address == null || address.trim().isEmpty()) {
            message("No FisProxy session to join", true);
            return;
        }
        // Cancel returns to this page, so the token/service stay in reach.
        mc.displayGuiScreen(new GuiConnecting(this, mc,
                new ServerData("FisProxy", address.trim(), false)));
    }

    private void changeIp() {
        if(!FisProxySession.running()) { message("Start a FisProxy session first",true); return; }
        message("Changing FisProxy exit...",false);
        task(()->{ FisProxySession.changeIp(); return true; },ok->{
            sessionLine=FisProxySession.summary(); balanceLine=FisProxySession.balanceLine();
            message("FisProxy exit changed",false);
        });
    }

    /**
     * Pulls balances plus the live session so a session started earlier shows up here.
     *
     * <p>Runs on its own thread instead of the shared task worker: this fires when the page opens,
     * and a timeout against an unreachable API must not leave every button disabled for 30 seconds.</p>
     */
    private void refreshSession() {
        final ProxyConfig c=draft(false);
        message("Loading FisProxy session...",false);
        Thread worker=new Thread(()->{
            try {
                FisProxySession.configure(c);
                FisProxySession.refresh();
                final String line=FisProxySession.summary(), balance=FisProxySession.balanceLine();
                final boolean running=FisProxySession.running();
                mc.addScheduledTask(()->{
                    sessionLine=line; balanceLine=balance;
                    message(running?"FisProxy session is running":"FisProxy ready",false);
                });
            } catch(final Exception e) {
                mc.addScheduledTask(()->message(failure(e),true));
            }
        },"Raven fisproxy refresh");
        worker.setDaemon(true);
        worker.start();
    }

    private void test() {
        try {
            final ProxyConfig c=draft(true); message("Testing proxy...",false);
            task(()-> {
                String host="sessionserver.mojang.com";
                try(Socket tunnel=ProxyTunnel.open(c,host,443);
                    SSLSocket ssl=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(tunnel,host,443,true)) {
                    ssl.setSoTimeout(15000); SSLParameters params=ssl.getSSLParameters();
                    params.setEndpointIdentificationAlgorithm("HTTPS"); ssl.setSSLParameters(params); ssl.startHandshake();
                }
                return true;
            },ok->message("Proxy connection verified",false));
        } catch(Exception e) { message(failure(e),true); }
    }
}
