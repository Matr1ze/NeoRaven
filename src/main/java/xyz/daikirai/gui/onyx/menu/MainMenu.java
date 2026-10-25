package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.gui.*;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.GuiModList;
import xyz.daikirai.gui.onyx.OnyxClickGui;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.gui.onyx.material.*;
import xyz.daikirai.gui.onyx.render.*;
import java.util.*;

public final class MainMenu extends GuiMainMenu {
    private UI ui;
    private float scale=1, top;
    private long last;
    private final List<MenuButton> buttons=new ArrayList<MenuButton>();
    @Override public void initGui() { if (ui==null) ui=new UI(); layout(); last=0; }
    private void layout() {
        OnyxClickGuiModule.sync();
        scale=Theme.screenScale(Math.min(width*0.8F/560,height*0.92F/468.4F));
        float logicalHeight=height/scale, logicalWidth=width/scale;
        top=Math.max(logicalHeight/4+56,logicalHeight/2-151+30);
        top=Math.min(top,logicalHeight-322);
        buttons.clear();
        Runnable[] actions={()->mc.displayGuiScreen(new GuiSelectWorld(this)),()->mc.displayGuiScreen(new GuiMultiplayer(this)),
                ()->mc.displayGuiScreen(new AccountsScreen(this)),()->mc.displayGuiScreen(new ProxyScreen(this)),
                ()->mc.displayGuiScreen(new GuiOptions(this,mc.gameSettings)),()->mc.shutdown()};
        String[] labels={"Singleplayer","Multiplayer","Alt Manager","Proxy","Options","Quit Game"};
        for(int i=0;i<labels.length;i++) buttons.add(new MenuButton(logicalWidth/2-110,top+i*52,220,42,labels[i],null,0,actions[i]));
        buttons.add(new MenuButton(14,logicalHeight-44,64,30,"Mods",null,2,()->mc.displayGuiScreen(new GuiModList(this))));
        buttons.add(new MenuButton(86,logicalHeight-44,104,30,"Background",null,2,()->mc.displayGuiScreen(new BackgroundScreen(this))));
    }
    @Override public void drawScreen(int mx,int my,float ticks) {
        OnyxClickGuiModule.sync();
        float next=Theme.screenScale(Math.min(width*0.8F/560,height*0.92F/468.4F));
        if(next!=scale) layout();
        long now=System.nanoTime(); float dt=last==0 ? 16 : Math.min(100,(now-last)/1E6F); last=now;
        ui.begin(); MenuBackground.draw(ui,width,height); ui.end();
        if(Theme.frost()) Blur.prepare();
        ui.begin(); ui.push(); ui.scale(scale,0,0);
        Scheme s=Theme.scheme();
        ui.push(); float cx=width/scale/2, cy=Math.min(height/scale/5,top-66);
        ui.translate(cx,cy); ui.scale(5.2F,0,0);
        ui.text(Typeface.HEADLINE,"NeoRaven",0,0,s.onSurface,UI.ALIGN_CENTER); ui.pop();
        for(MenuButton b:buttons) b.draw(ui,s,mx/scale,my/scale,dt);
        // Bottom left next to "Mods": the bottom right corner belongs to the style switcher.
        ui.text(Typeface.CAPTION,"Minecraft 1.8.9",200,height/scale-29,Material.withAlpha(s.onSurface,0.7F),UI.ALIGN_LEFT);
        ui.pop();
        MenuStyleSwitch.draw(ui,false,width,height,mx,my,dt);
        ui.end();
    }
    @Override protected void mouseClicked(int x,int y,int button) {
        if(button!=0) return;
        if(MenuStyleSwitch.click(x,y,width,height)) return;
        for(MenuButton b:buttons) if(b.hit(x/scale,y/scale)) {
            mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"),1));
            MenuScreen.enterFrom(x,y); b.action.run(); return;
        }
    }
    @Override protected void mouseReleased(int x,int y,int b) { }
    @Override protected void keyTyped(char c,int key) {
        if(MenuStyleSwitch.keyTyped(key)) return;
        if(OnyxClickGuiModule.instance!=null && key!=0 && key==OnyxClickGuiModule.instance.getKeycode())
            mc.displayGuiScreen(new OnyxClickGui());
    }
    @Override public void updateScreen() { }
}
