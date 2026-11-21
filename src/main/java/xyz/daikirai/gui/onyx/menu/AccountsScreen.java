package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.Session;
import xyz.daikirai.account.*;
import xyz.daikirai.gui.onyx.material.*;
import xyz.daikirai.gui.onyx.render.*;
import xyz.daikirai.gui.onyx.Widgets;
import java.io.IOException;
import java.util.*;

public final class AccountsScreen extends MenuScreen {
    private final AccountManager manager=AccountManager.instance();
    private MenuField search;
    private int offset;
    private AltAccount selected, deleting;
    private String query="";
    private AltAccount context;
    private float contextX,contextY;
    private final List<String> menuLabels=new ArrayList<String>();
    private final List<Runnable> menuActions=new ArrayList<Runnable>();
    public AccountsScreen(GuiScreen parent) { super(parent,560,400); }
    @Override void createFields() { search=new MenuField("Search accounts",20,76,408,false,64); }
    @Override public void initGui() {
        super.initGui(); if(manager.loadError()!=null) message(manager.loadError(),true);
    }
    @Override void drawContent(Scheme s) {
        title("Accounts");
        button(442,76,98,38,"Add","\ue145",1,()->mc.displayGuiScreen(new AddAccountScreen(this)));
        if(!query.equals(search.text())) { query=search.text(); offset=0; }
        List<AltAccount> list=filtered(); offset=Math.max(0,Math.min(offset,Math.max(0,list.size()-4)));
        if(list.isEmpty()) {
            ui.icon("\ue7fd",280,189,30,s.onSurfaceVariant);
            ui.text(Typeface.BODY,list.isEmpty() && manager.accounts().isEmpty()?"No accounts yet":"No matching accounts",280,226,s.onSurfaceVariant,UI.ALIGN_CENTER);
        }
        for(int i=offset;i<Math.min(list.size(),offset+4);i++) {
            final AltAccount a=list.get(i); float y=125+(i-offset)*43;
            boolean current=manager.active()==a;
            if(selected==a || current) ui.rounded(16,y,528,40,8,Material.withAlpha(s.primary,0.12F));
            ui.circle(36,y+20,13,s.secondaryContainer);
            ui.text(Typeface.BODY_MEDIUM,a.name.substring(0,1),36,y+20,s.onSecondaryContainer,UI.ALIGN_CENTER);
            ui.text(Typeface.BODY_MEDIUM,a.name,58,y+13,s.onSurface,UI.ALIGN_LEFT);
            String type=a.type==AltAccount.Type.OFFLINE?"Offline":a.type==AltAccount.Type.ACCESS_TOKEN?"Access token":"Microsoft";
            if(current) type+="  /  Active";
            ui.text(Typeface.CAPTION,type,58,y+29,s.onSurfaceVariant,UI.ALIGN_LEFT);
            button(364,y+5,30,30,"","\ue14d",2,()->{ setClipboardString(a.name); message("Username copied",false); });
            button(400,y+5,30,30,"","\ue037",2,()->login(a));
            if(a.type!=AltAccount.Type.OFFLINE) button(436,y+5,30,30,"","\ue5d5",2,()->refresh(a));
            button(472,y+5,30,30,"","\ue872",2,()->{ selected=a; deleting=a; });
            button(16,y,330,40,"",null,3,()->{ if(selected==a) login(a); else selected=a; });
        }
        if(list.size()>4) {
            float track=168, thumb=Math.max(18,track*4/list.size());
            ui.rounded(533,128+offset*(track-thumb)/(list.size()-4),3,thumb,1.5F,s.outline);
        }
        ui.rect(20,305,520,1,s.outlineVariant);
        ui.scissorPush(20,310,245,28);
        ui.text(Typeface.LABEL,"Session: "+mc.getSession().getUsername(),20,324,s.onSurfaceVariant,UI.ALIGN_LEFT); ui.scissorPop();
        button(285,310,145,28,"Restore launcher",null,2,()->{
            try { manager.restoreLauncher(); message("Restored launcher account",false); } catch(IOException e) { message(e.getMessage(),true); }
        });
        button(434,310,106,28,"Proxy settings",null,2,()->{
            MenuScreen.enterFrom((mx+ox)*scale,(my+oy)*scale,lightBackdrop);
            mc.displayGuiScreen(new ProxyScreen(this));
        });
        ui.text(Typeface.CAPTION,manager.accounts().size()+" saved",20,353,s.onSurfaceVariant,UI.ALIGN_LEFT);
        if(deleting!=null) {
            AltAccount a=deleting;
            ui.rounded(16,342,528,28,8,s.errorContainer);
            ui.text(Typeface.LABEL,"Delete "+a.name+"?",26,356,s.onErrorContainer,UI.ALIGN_LEFT);
            button(348,342,84,28,"Cancel",null,2,()->deleting=null);
            button(436,342,100,28,"Delete",null,2,()-> {
                try { manager.remove(a); deleting=null; selected=null; message("Account removed",false); }
                catch(IOException e) { message(e.getMessage(),true); }
            });
        }
    }
    private List<AltAccount> filtered() {
        List<AltAccount> result=new ArrayList<AltAccount>();
        for(AltAccount a:manager.accounts()) if(a.name.toLowerCase(Locale.ROOT).contains(search.text().toLowerCase(Locale.ROOT))) result.add(a);
        return result;
    }
    private void login(AltAccount a) {
        message("Logging in...",false);
        task(()->{ Session session=a.session(); manager.save(); return session; },session->{
            try { manager.activate(a,session); selected=a; message("Logged in as "+a.name,false); }
            catch(IOException e) { message(e.getMessage(),true); }
        });
    }
    private void refresh(AltAccount a) {
        message("Refreshing account...",false);
        task(()->{
            if(a.type==AltAccount.Type.MICROSOFT) MicrosoftLogin.refresh(a); else a.refreshProfile();
            manager.save(); return a;
        },account->message("Refreshed "+account.name,false));
    }
    private void copy(String label,String value) {
        if(value==null || value.isEmpty()) { message("Nothing to copy",true); return; }
        setClipboardString(value); message(label+" copied",false);
    }
    private void menu(String label,Runnable action) { menuLabels.add(label); menuActions.add(action); }
    @Override void drawOverlay(Scheme s) {
        if(context==null) return;
        int hovered=(int)((my-contextY-4)/32);
        if(mx<contextX || mx>=contextX+218 || my<contextY+4 || my>=contextY+4+menuLabels.size()*32) hovered=-1;
        Widgets.menu(ui,s,contextX,contextY,218,menuLabels.toArray(new String[0]),-1,hovered,1);
    }
    @Override protected void mouseClicked(int x,int y,int button) throws IOException {
        float px=x/scale-ox, py=y/scale-oy;
        if(context!=null) {
            int row=(int)((py-contextY-4)/32);
            boolean inside=px>=contextX && px<contextX+218 && py>=contextY+4 && row>=0 && row<menuActions.size();
            context=null;
            if(button==0 && inside) { menuActions.get(row).run(); return; }
        }
        if(button==1 && !busy && py>=125 && py<297) {
            List<AltAccount> list=filtered(); int row=offset+(int)((py-125)/43);
            if(row<list.size() && px>=16 && px<530) {
                final AltAccount a=list.get(row); context=a; menuLabels.clear(); menuActions.clear();
                menu("Copy username",()->copy("Username",a.name)); menu("Copy UUID",()->copy("UUID",a.uuid));
                if(a.type!=AltAccount.Type.OFFLINE) {
                    menu("Copy access token",()->copy("Access token",a.accessToken));
                    if(a.type==AltAccount.Type.MICROSOFT) menu("Copy refresh token",()->copy("Refresh token",a.refreshToken));
                    menu("Refresh tokens",()->refresh(a));
                }
                menu("Delete",()->{ selected=a; deleting=a; });
                contextX=Math.max(8,Math.min(px,panelWidth-226));
                contextY=Math.max(8,Math.min(py,panelHeight-12-menuLabels.size()*32));
                return;
            }
        }
        super.mouseClicked(x,y,button);
    }
    @Override protected void keyTyped(char c,int key) throws IOException {
        if(context!=null && key==org.lwjgl.input.Keyboard.KEY_ESCAPE) { context=null; return; }
        super.keyTyped(c,key);
    }
    @Override void submit() { if(selected!=null) login(selected); }
    @Override void scroll(int direction) { offset+=direction; }
}
