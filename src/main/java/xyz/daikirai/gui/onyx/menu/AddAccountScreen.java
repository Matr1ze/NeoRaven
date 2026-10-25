package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.gui.GuiScreen;
import xyz.daikirai.account.*;
import xyz.daikirai.gui.onyx.Widgets;
import xyz.daikirai.gui.onyx.material.*;
import xyz.daikirai.gui.onyx.render.*;
import java.io.IOException;

public final class AddAccountScreen extends MenuScreen {
    private int mode;
    private boolean resolve;
    private MenuField username,access,refresh;
    private MicrosoftLogin browser;
    public AddAccountScreen(GuiScreen parent) { super(parent,560,360); }
    @Override void createFields() {
        username=new MenuField("Username",24,142,512,false,16);
        access=new MenuField("Minecraft access token",24,142,512,true,16384);
        refresh=new MenuField("Microsoft OAuth refresh token",24,142,512,true,16384);
        username.input.setFocused(true);
    }
    @Override void drawContent(Scheme s) {
        title("Add account");
        String[] modes={"Offline","Access","Refresh","Microsoft"};
        ui.rounded(24,65,512,34,17,s.surfaceContainerHighest);
        for(int i=0;i<4;i++) {
            final int index=i;
            if(mode==i) ui.rounded(24+i*128,65,128,34,17,s.secondaryContainer);
            button(24+i*128,65,128,34,modes[i],null,2,()->changeMode(index));
        }
        username.visible=mode==0; access.visible=mode==1; refresh.visible=mode==2;
        if(mode==0) {
            Widgets.toggle(ui,s,24,204,resolve?1:0,mx>=24&&mx<62&&my>=204&&my<226,false);
            button(24,199,300,32,"",null,3,()->resolve=!resolve);
            ui.text(Typeface.BODY,"Resolve premium UUID",74,215,s.onSurface,UI.ALIGN_LEFT);
        }
        if(mode==3) {
            ui.icon("\ue7fd",280,154,32,s.primary);
            button(130,202,300,40,browser==null?"Sign in with Microsoft":"Cancel sign-in",browser==null?"\ue89e":"\ue5cd",1,()->{
                if(browser!=null) cancelBrowser(); else startBrowser();
            });
            if(browser!=null) button(226,256,108,30,"Copy link","\ue14d",2,()->{
                setClipboardString(browser.url); message("Sign-in link copied",false);
            });
        } else button(430,270,106,38,"Add","\ue145",1,this::submit);
    }
    private void changeMode(int index) {
        if(mode==index) return;
        cancelBrowser(); mode=index; status="";
        username.input.setFocused(index==0); access.input.setFocused(index==1); refresh.input.setFocused(index==2);
    }
    @Override void submit() {
        if(mode==3) { if(browser==null) startBrowser(); return; }
        final int type=mode; final String value=type==0?username.text():type==1?access.text():refresh.text(); final boolean premium=resolve;
        message("Adding account...",false);
        task(()-> type==0?AltAccount.offline(value,premium):type==1?AltAccount.access(value):MicrosoftLogin.fromRefresh(value),this::added);
    }
    private void added(AltAccount account) {
        try {
            AccountManager.instance().add(account);
            ((AccountsScreen)parent).message("Added "+account.name,false); mc.displayGuiScreen(parent);
        } catch(IOException e) { message(e.getMessage(),true); }
    }
    private void startBrowser() {
        try {
            final MicrosoftLogin login=new MicrosoftLogin(); browser=login;
            message("Waiting for Microsoft sign-in...",false);
            login.result.whenComplete((account,e)->mc.addScheduledTask(()->{
                if(browser!=login) return;
                browser=null;
                if(e!=null) message(failure(e),true); else added(account);
            }));
            AccountManager.WORKER.execute(()-> {
                try { java.awt.Desktop.getDesktop().browse(java.net.URI.create(login.url)); }
                catch(Exception e) { mc.addScheduledTask(()->{ if(browser==login) message("Browser unavailable. Use Copy link.",true); }); }
            });
        } catch(IOException e) { message("Could not start sign-in on localhost:1337",true); }
    }
    private void cancelBrowser() {
        MicrosoftLogin old=browser; browser=null; if(old!=null) old.close();
        message("Sign-in cancelled",false);
    }
    @Override public void onGuiClosed() { cancelBrowser(); super.onGuiClosed(); }
}
