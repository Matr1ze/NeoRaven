package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.gui.onyx.material.*;
import xyz.daikirai.gui.onyx.render.*;
import java.io.IOException;
import java.util.*;

abstract class MenuScreen extends GuiScreen {
    final GuiScreen parent;
    final UI ui=new UI();
    final List<MenuButton> buttons=new ArrayList<MenuButton>();
    final List<MenuField> fields=new ArrayList<MenuField>();
    final float panelWidth,panelHeight;
    float scale=1, ox,oy,mx,my,dt;
    String status="";
    boolean error,busy;
    private long last;
    private int generation;
    private final Map<String,Spring> buttonHovers=new HashMap<String,Spring>();
    /**
     * Material's "activity stack" entrance: the panel slides up from below the screen while two
     * ghost sheets trail above it and tuck underneath, and a spring (under-damped, ~8% overshoot)
     * settles it. That overshoot is what makes it read as a sheet being thrown onto a stack rather
     * than a card fading in.
     */
    private final Spring opening=new Spring(0.0F);
    boolean lightBackdrop;
    private static boolean pendingLight;
    MenuScreen(GuiScreen parent,float w,float h) { this.parent=parent; panelWidth=w; panelHeight=h; }

    /**
     * @param light keep the Android wallpaper behind the panel (dimmed) instead of the dark Onyx
     *              clouds, so opening the accounts/proxy pages from the light title screen does not
     *              flash from a pale wallpaper to a dark one
     */
    static void enterFrom(float screenX,float screenY,boolean light) {
        pendingLight=light;
    }

    /** Same, keeping the dark Onyx clouds behind the panel. */
    static void enterFrom(float screenX,float screenY) {
        pendingLight=false;
    }

    /** Never returns to a screen that needs a world that is already unloaded. */
    void closeToParent() {
        GuiScreen target=parent;
        if(target==null || (AndroidScreens.needsWorld(target) && mc.theWorld==null)) {
            target=mc.theWorld!=null ? new net.minecraft.client.gui.GuiIngameMenu()
                    : new net.minecraft.client.gui.GuiMainMenu();
        }
        mc.displayGuiScreen(target);
    }

    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true); position();
        if(fields.isEmpty()) createFields();
        // A resize re-runs initGui; keep the backdrop choice of the current panel.
        if(pendingLight) {
            lightBackdrop=true;
            pendingLight=false;
        }
        last=0L; opening.set(0.0F); opening.target(1.0F);
    }
    void createFields() { }
    private void position() {
        float fit=Math.min((width-24)/panelWidth,(height-24)/panelHeight);
        scale=Math.max(0.1F,Theme.screenScale(fit)); ox=(width/scale-panelWidth)/2; oy=(height/scale-panelHeight)/2;
    }
    @Override public final void drawScreen(int x,int y,float ticks) {
        OnyxClickGuiModule.sync(); position();
        long now=System.nanoTime(); dt=last==0 ? 16 : Math.min(100,(now-last)/1E6F); last=now; opening.update(dt);
        float enter=Material.clamp(opening.value(),0.0F,1.0F);
        mx=x/scale-ox; my=y/scale-oy;
        if(mc.theWorld==null) {
            ui.begin();
            if(lightBackdrop && MenuBackground.current() == MenuBackground.Style.CLOUDS) {
                // Same wallpaper as the Android title screen, dimmed to a modal scrim so the dark
                // panel keeps its contrast (and the blur below sees a dark backdrop).
                AndroidWallpaper.draw(ui,width,height);
                ui.rect(0,0,width,height,0x8C000000);
            } else {
                MenuBackground.draw(ui,width,height,lightBackdrop);
                if(lightBackdrop) ui.rect(0,0,width,height,0x8C000000);
            }
            ui.end();
        } else drawDefaultBackground();
        if(Theme.frost()) Blur.prepare();
        ui.begin(); ui.rect(0,0,width,height,Theme.scrim(0.2F+0.8F*enter));
        ui.push(); ui.scale(scale,0,0); ui.translate(ox,oy);
        // The sheet starts a full screen below and rises; the ghost sheets above it are the stack
        // it is being dropped onto, and they slide out from under it as it lands.
        float rise=(1.0F-enter)*(panelHeight*0.72F+48.0F);
        ui.translate(0,rise);
        Scheme s=Theme.scheme();
        ui.alphaPush(Material.clamp(enter*1.5F,0.0F,1.0F));
        ui.shadow(0,0,panelWidth,panelHeight,28,3);
        // The ghost sheets are drawn after the shadow but before the panel, and only their exposed
        // tops are painted, so the panel's own shadow cannot swallow them.
        float trail=1.0F-enter;
        for(int i=2;i>=1;i--) {
            float peek=i*15.0F*trail;
            float inset=i*11.0F*trail;
            ui.alphaPush(Material.clamp((0.9F-(i-1)*0.28F)*trail,0.0F,1.0F));
            int surface=Material.mix(s.surfaceContainer,0xFFFFFFFF,0.12F-(i-1)*0.05F);
            ui.roundedCorners(inset,-peek,panelWidth-inset*2.0F,peek+8.0F,28,28,0,0,surface);
            ui.outline(inset,-peek,panelWidth-inset*2.0F,peek+8.0F,28,1.0F,Material.withAlpha(s.outlineVariant,0.45F));
            ui.alphaPop();
        }
        ui.rounded(0,0,panelWidth,panelHeight,28,s.surfaceContainer);
        if(Theme.frost()) ui.frost(0,0,panelWidth,panelHeight,28,s.surfaceContainer,0.88F);
        buttons.clear();
        button(panelWidth-42,12,30,30,"","\ue5cd",2,this::closeToParent);
        drawContent(s);
        if(!status.isEmpty()) {
            ui.scissorPush(18,panelHeight-32,panelWidth-36,22);
            ui.text(Typeface.LABEL,status,18,panelHeight-21,error ? s.error : s.onSurfaceVariant,UI.ALIGN_LEFT); ui.scissorPop();
        }
        for(MenuButton b:buttons) b.draw(ui,s,mx,my,dt);
        for(MenuField f:fields) if(f.visible) f.draw(ui,s);
        drawOverlay(s);
        ui.alphaPop();
        ui.pop(); ui.end();
        for(MenuButton b:buttons) if(b.icon!=null && b.label.isEmpty() && b.hit(mx,my)) {
            String tip="\ue5cd".equals(b.icon)?"Close":tooltip(b.icon);
            if(!tip.isEmpty()) drawHoveringText(Collections.singletonList(tip),x,y);
        }
    }
    String tooltip(String icon) {
        if("\ue872".equals(icon)) return "Delete";
        if("\ue5d5".equals(icon)) return "Refresh tokens";
        if("\ue037".equals(icon)) return "Log in";
        if("\ue14d".equals(icon)) return "Copy username";
        if("\ue5c4".equals(icon)) return "Back";
        return "";
    }
    abstract void drawContent(Scheme scheme);
    void drawOverlay(Scheme scheme) { }
    void button(float x,float y,float w,float h,String label,String icon,int style,Runnable action) {
        MenuButton b=new MenuButton(x,y,w,h,label,icon,style,action);
        String id=x+":"+y+":"+label+":"+icon;
        Spring hover=buttonHovers.get(id);
        if(hover==null) { hover=b.hover; buttonHovers.put(id,hover); }
        b.hover=hover;
        b.enabled=!busy || "\ue5cd".equals(icon);
        buttons.add(b);
    }
    void title(String text) { ui.text(Typeface.HEADLINE,text,20,27,Theme.scheme().onSurface,UI.ALIGN_LEFT); }
    void message(String text,boolean bad) { status=text; error=bad; }
    static String failure(Throwable e) {
        while(e.getCause()!=null) e=e.getCause();
        return e.getMessage()==null ? "Operation failed" : e.getMessage();
    }
    <T> void task(java.util.concurrent.Callable<T> work,java.util.function.Consumer<T> success) {
        if(busy) return;
        busy=true; final int request=++generation;
        xyz.daikirai.account.AccountManager.WORKER.execute(()-> {
            T value=null; Throwable failed=null;
            try { value=work.call(); } catch(Exception e) { failed=e; }
            final T result=value; final Throwable problem=failed;
            mc.addScheduledTask(()-> {
                if(request!=generation) return;
                busy=false;
                if(problem!=null) message(failure(problem),true);
                else try { success.accept(result); } catch(RuntimeException e) { message(failure(e),true); }
            });
        });
    }
    @Override protected void mouseClicked(int x,int y,int b) throws IOException {
        mx=x/scale-ox; my=y/scale-oy;
        if(!opening.settled()) return;
        for(MenuField f:fields) f.click(mx,my,b);
        if(b!=0) return;
        for(MenuButton button:new ArrayList<MenuButton>(buttons)) if(button.enabled && button.hit(mx,my)) { button.action.run(); return; }
    }
    @Override protected void keyTyped(char c,int key) throws IOException {
        if(key==Keyboard.KEY_ESCAPE) { closeToParent(); return; }
        if(key==Keyboard.KEY_RETURN && !busy) { submit(); return; }
        List<MenuField> shown=new ArrayList<MenuField>(); for(MenuField f:fields) if(f.visible) shown.add(f);
        if(key==Keyboard.KEY_TAB && !shown.isEmpty()) {
            int active=-1; for(int i=0;i<shown.size();i++) if(shown.get(i).input.isFocused()) active=i;
            int next=(active+(isShiftKeyDown() ? shown.size()-1 : 1))%shown.size();
            for(int i=0;i<shown.size();i++) shown.get(i).input.setFocused(i==next);
            return;
        }
        if(!busy) for(MenuField f:shown) if(f.input.isFocused()) { f.input.textboxKeyTyped(c,key); return; }
        if(key==Keyboard.KEY_RETURN && !busy) submit();
    }
    void submit() { }
    @Override public void updateScreen() { for(MenuField f:fields) f.input.updateCursorCounter(); }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel=Mouse.getEventDWheel(); if(wheel!=0) scroll(wheel>0 ? -1 : 1);
    }
    void scroll(int direction) { }
    @Override public void onGuiClosed() { generation++; busy=false; Keyboard.enableRepeatEvents(false); }
    @Override public boolean doesGuiPauseGame() { return false; }

    final class MenuField {
        final GuiTextField input;
        final String label;
        final float x,y,w;
        final boolean secret;
        boolean visible=true;
        int first;
        MenuField(String label,float x,float y,float w,boolean secret,int max) {
            this.label=label; this.x=x; this.y=y; this.w=w; this.secret=secret;
            input=new GuiTextField(fields.size(),fontRendererObj,0,0,(int)w,30); input.setMaxStringLength(max); fields.add(this);
        }
        String text() { return input.getText(); }
        void text(String value) { input.setText(value); }
        void click(float px,float py,int button) {
            boolean inside=visible && px>=x && px<x+w && py>=y && py<y+38;
            input.setFocused(inside);
            if(inside && button==0) {
                String value=display(); int at=first;
                while(at<value.length() && ui.width(Typeface.BODY,value.substring(first,at+1))<px-x-12) at++;
                input.setCursorPosition(at);
            }
        }
        String display() {
            if(!secret) return text();
            char[] chars=new char[text().length()]; Arrays.fill(chars,'*'); return new String(chars);
        }
        void draw(UI ui,Scheme s) {
            ui.text(Typeface.LABEL,label,x,y-10,s.onSurfaceVariant,UI.ALIGN_LEFT);
            ui.rounded(x,y,w,38,8,s.surfaceContainerHighest);
            ui.outline(x,y,w,38,8,input.isFocused()?1.5F:1,input.isFocused()?s.primary:s.outlineVariant);
            String value=display(); int cursor=input.getCursorPosition();
            first=Math.min(first,Math.min(cursor,value.length()));
            while(first<cursor && ui.width(Typeface.BODY,value.substring(first,cursor))>w-30) first++;
            ui.scissorPush(x+10,y+3,w-20,32);
            int selection=input.getSelectionEnd();
            if(input.isFocused() && selection!=cursor) {
                int a=Math.max(first,Math.min(cursor,selection)), b=Math.max(first,Math.max(cursor,selection));
                float left=ui.width(Typeface.BODY,value.substring(first,a));
                float right=ui.width(Typeface.BODY,value.substring(first,b));
                ui.rect(x+12+left,y+10,right-left,18,Material.withAlpha(s.primary,0.3F));
            }
            ui.text(Typeface.BODY,value.substring(first),x+12,y+19,s.onSurface,UI.ALIGN_LEFT);
            if(input.isFocused() && (System.currentTimeMillis()/500)%2==0) {
                float caret=ui.width(Typeface.BODY,value.substring(first,cursor)); ui.rect(x+12+caret,y+10,1,18,s.primary);
            }
            ui.scissorPop();
        }
    }
}
