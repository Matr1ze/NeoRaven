package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import xyz.daikirai.client.FirstRun;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

/** Soar-inspired first-run flow with clickable theme, accent and glass previews. */
public final class WelcomeScreen extends GuiScreen {
    private static final String[] THEMES = {"Material", "Dark", "Light", "Dark Blue", "Midnight", "Dark Purple", "Sea", "Sakura"};
    private static final int[] ACCENTS = {0xFF6750A4, 0xFF3F5F90, 0xFF3F6B62, 0xFF4F6B45, 0xFF7A5C1E, 0xFF8C4A32, 0xFF8C4767, 0xFF6B5A4E};
    private static final String[] ACCENT_NAMES = {"Purple", "Blue", "Teal", "Green", "Amber", "Orange", "Pink", "Mocha"};
    private static final int[][] PREVIEW = {{0xFF131314,0xFF222327,0xFFFFFFFF,0xFFEBEBEB},{0xFFFEFEFE,0xFFEEEEEE,0xFF363636,0xFF6B7581},{0xFF161C29,0xFF1B2434,0xFF9DAFD3,0xFF7483A4},{0xFF2F363D,0xFF24292E,0xFFFFFFFF,0xFFEBEBEB},{0xFF2C0E48,0xFF35185A,0xFFEAE2FC,0xFFC2BAD4},{0xFFCBE0FF,0xFFBED8EE,0xFF24354C,0xFF405570},{0xFFFFBFB2,0xFFFFDFE2,0xFF542F37,0xFF73525C}};
    private static final Typeface.Style TITLE = Typeface.custom(19, 28, 34, false);
    private static final Typeface.Style BODY = Typeface.custom(9, 13, 16, false);
    private static final Typeface.Style BUTTON = Typeface.custom(9, 12, 15, true);
    private final UI ui = new UI();
    private int step, theme = Theme.surfaceTheme(), accent = 0;
    private float frost = Theme.frostOpacity, liquid = Theme.liquidOpacity, matte = Theme.liquidFrost;
    private boolean frostOn = Theme.frost(), liquidOn = Theme.liquidGlass();
    private boolean coloured = Theme.colouredFrost, matteOn = Theme.matteLiquid, dragging;
    private float px, py;
    private long shownAt;

    @Override public void initGui() { shownAt = System.currentTimeMillis(); }
    @Override public boolean doesGuiPauseGame() { return false; }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        px = mouseX; py = mouseY;
        if (dragging) updateSlider(mouseX, mouseY);
        float fade = Ease.EMPHASIZED.get(Material.clamp((System.currentTimeMillis()-shownAt)/420.0F,0,1));
        int width = Math.min(520, this.width - 32), height = Math.min(300, this.height - 32);
        float x = (this.width-width)/2.0F, y=(this.height-height)/2.0F;
        ui.begin();
        try {
            ui.rect(0,0,this.width,this.height,0xEE090A0D);
            ui.alphaPush(fade);
            ui.rounded(x,y,width,height,10,0xF21D2025);
            ui.rect(x+20,y+52,width-40,1,0xFF41444A);
            ui.text(TITLE, title(), x+24,y+27,0xFFF4F3F7,UI.ALIGN_LEFT);
            ui.text(BODY, "NeoRaven 1.2", x+width-22,y+28,0xFF9CA0A8,UI.ALIGN_RIGHT);
            ui.text(BODY, ""+(step+1)+" / 4",x+width/2.0F,y+height-31,0xFF898D95,UI.ALIGN_CENTER);
            if (step==0) drawWelcome(x,y,width,height);
            else if (step==1) drawThemes(x,y,width,height);
            else if (step==2) drawAccents(x,y,width,height);
            else drawGlass(x,y,width,height);
            drawNavigation(x,y,width,height);
            ui.alphaPop();
        } finally { ui.end(); }
    }

    private String title() {
        switch(step) { case 0:return "Welcome to NeoRaven"; case 1:return "Choose a theme"; case 2:return "Choose an accent color"; default:return "Tune glass materials"; }
    }

    private void drawWelcome(float x,float y,float w,float h) {
        ui.image(new ResourceLocation("clickgui","textures/raven.png"),x+30,y+82,72,72,0,0,1,1,0xFFFFFFFF);
        ui.text(Typeface.HEADLINE,"Welcome to your",x+130,y+100,0xFFF4F3F7,UI.ALIGN_LEFT);
        ui.text(Typeface.HEADLINE,"NeoRaven",x+130,y+137,0xFFF4F3F7,UI.ALIGN_LEFT);
        ui.text(BODY,"Let's set up your look.",x+132,y+171,0xFFADB1B9,UI.ALIGN_LEFT);
        button(x+26,y+217,132,34,"Language",false);
        button(x+168,y+217,156,34,"Accessibility",false);
        ui.text(BODY,"Choose a theme, accent color and glass finish.",x+26,y+194,0xFFADB1B9,UI.ALIGN_LEFT);
    }

    private void drawThemes(float x,float y,float w,float h) {
        ui.text(BODY,"Select one of Soar's seven surface palettes.",x+22,y+72,0xFFADB1B9,UI.ALIGN_LEFT);
        float start=x+22, cy=y+105, cw=50, ch=66, gap=10;
        for(int i=0;i<7;i++) {
            float cx=start+i*(cw+gap);
            int[] p=PREVIEW[i];
            ui.rounded(cx,cy,cw,ch,6,p[0]);
            ui.rounded(cx+7,cy+10,cw-14,ch-20,3,p[1]);
            ui.rounded(cx+10,cy+17,Math.min(cw-20,24),6,2,p[2]);
            ui.rounded(cx+10,cy+29,Math.min(cw-20,30),6,2,p[3]);
            if(theme==i+1) ui.outline(cx-2,cy-2,cw+4,ch+4,7,2,Theme.accent());
            ui.text(BODY,THEMES[i+1],cx+cw/2,cy+82,0xFFF0EFF4,UI.ALIGN_CENTER);
        }
        float cx=start+7*(cw+gap);
        ui.rounded(cx,cy,cw,ch,6,0xFF282A30); ui.rounded(cx+7,cy+12,cw-14,9,3,0xFF4A4D55);
        ui.text(BODY,"Default",cx+cw/2,cy+82,0xFFF0EFF4,UI.ALIGN_CENTER);
    }

    private void drawAccents(float x,float y,float w,float h) {
        ui.text(BODY,"Pick an accent used by highlights and controls.",x+22,y+72,0xFFADB1B9,UI.ALIGN_LEFT);
        float start=x+44, top=y+103;
        for(int i=0;i<8;i++) {
            float bx=start+(i%4)*112, by=top+(i/4)*62;
            ui.rounded(bx,by,88,42,8,ACCENTS[i]);
            ui.text(BODY,ACCENT_NAMES[i],bx+44,by+21,0xFFFFFFFF,UI.ALIGN_CENTER);
            if(accent==i) ui.rounded(bx-2,by-2,92,46,9,0xFFE9E7EF);
            ui.rounded(bx,by,88,42,8,ACCENTS[i]);
            ui.text(BODY,ACCENT_NAMES[i],bx+44,by+21,0xFFFFFFFF,UI.ALIGN_CENTER);
        }
        ui.text(BODY,"Your selected color updates the preview immediately.",x+22,y+244,0xFFADB1B9,UI.ALIGN_LEFT);
    }

    private void drawGlass(float x,float y,float w,float h) {
        ui.text(BODY,"Glass remains optional. Liquid glass takes priority when both materials are enabled.",x+22,y+70,0xFFADB1B9,UI.ALIGN_LEFT);
        slider(x+24,y+107,390,"Frost opacity",frost);
        slider(x+24,y+151,390,"Liquid opacity",liquid);
        toggle(x+24,y+183,"Frosted glass",frostOn);
        toggle(x+240,y+183,"Liquid glass",liquidOn);
        toggle(x+24,y+211,"Coloured frost",coloured);
        toggle(x+240,y+211,"Matte liquid",matteOn);
        if(matteOn) slider(x+24,y+251,390,"Liquid frost amount",matte);
        ui.rounded(x+440,y+100,48,116,7,0xFF252830);
        ui.rounded(x+445,y+105,38,106,6,0x553F5F90);
        ui.rounded(x+445,y+105,38,106,6,Material.withAlpha(Theme.accent(),0.20F));
        ui.rounded(x+450,y+111,2,93,1,0x88FFFFFF);
        ui.text(BODY,"Preview",x+464,y+229,0xFFADB1B9,UI.ALIGN_CENTER);
    }

    private void slider(float x,float y,float width,String label,float value) {
        ui.text(BODY,label+"  "+Math.round(value*100)+"%",x,y,0xFFE6E5EB,UI.ALIGN_LEFT);
        float lineY=y+21;
        ui.rounded(x,lineY,width,4,2,0xFF41444C);
        ui.rounded(x,lineY,width*value,4,2,Theme.accent());
        float cx=x+width*value;
        ui.circle(cx,lineY+2,7,Theme.accent());
        ui.circle(cx,lineY+2,3,0xFFFFFFFF);
    }

    private void toggle(float x,float y,String label,boolean value) {
        ui.rounded(x,y,32,18,9,value?Theme.accent():0xFF454850);
        ui.circle(x+(value?23:9),y+9,6,0xFFFFFFFF);
        ui.text(BODY,label,x+40,y+9,0xFFE6E5EB,UI.ALIGN_LEFT);
    }

    private void drawNavigation(float x,float y,float w,float h) {
        if(step>0) button(x+20,y+h-46,76,30,"Back",false);
        button(x+w-112,y+h-46,92,30,step==3?"Finish":"Next",true);
    }

    private void button(float x,float y,float w,float h,String text,boolean primary) {
        boolean hover=px>=x&&px<x+w&&py>=y&&py<y+h;
        int color=primary?Theme.accent():(hover?0xFF34373E:0xFF292C32);
        ui.rounded(x,y,w,h,7,color);
        ui.text(BUTTON,text,x+w/2,y+h/2,primary?0xFFFFFFFF:0xFFE7E6EC,UI.ALIGN_CENTER);
    }

    private int[] preview(int index) { return index==0?new int[]{0xFF282A30,0xFF41444C,0xFFECEBF1,0xFFB8BBC4}:PREVIEW[index-1]; }

    private void updateSlider(int mouseX,int mouseY) {
        float w=Math.min(520,this.width-32), x=(this.width-w)/2.0F, y=(this.height-Math.min(300,this.height-32))/2.0F;
        if(mouseY<y+120&&mouseY>=y+90) frost=sliderValue(mouseX,x+24,390);
        else if(mouseY<y+164&&mouseY>=y+134) liquid=sliderValue(mouseX,x+24,390);
        else if(matteOn&&mouseY<y+264&&mouseY>=y+234) matte=sliderValue(mouseX,x+24,390);
    }
    private float sliderValue(float x,float left,float width) { return Material.clamp((x-left)/width,0,1); }

    @Override protected void mouseClicked(int mouseX,int mouseY,int button) {
        if(button!=0)return;
        float w=Math.min(520,this.width-32), h=Math.min(300,this.height-32), x=(this.width-w)/2.0F, y=(this.height-h)/2.0F;
        float bx=x+w-112, by=y+h-46;
        if(mouseX>=bx&&mouseX<bx+92&&mouseY>=by&&mouseY<by+30) { sound(); if(step==3) finish(); else {step++; shownAt=System.currentTimeMillis();} return; }
        if(step>0&&mouseX>=x+20&&mouseX<x+96&&mouseY>=by&&mouseY<by+30) { sound(); step--; shownAt=System.currentTimeMillis(); return; }
        if(step==0) {
            float cx=x+26, cy=y+217;
            if(mouseX>=cx&&mouseX<cx+132&&mouseY>=cy&&mouseY<cy+34) mc.displayGuiScreen(new GuiLanguage(this,mc.gameSettings,mc.getLanguageManager()));
            else if(mouseX>=cx+142&&mouseX<cx+298&&mouseY>=cy&&mouseY<cy+34) mc.displayGuiScreen(new GuiOptions(this,mc.gameSettings));
        } else if(step==1) {
            float start=x+22, top=y+105, cw=50,gap=10;
            for(int i=0;i<8;i++) { float cx=start+i*(cw+gap); if(mouseX>=cx&&mouseX<cx+cw&&mouseY>=top&&mouseY<top+96) theme=i==7?0:i+1; }
        } else if(step==2) {
            float start=x+44, top=y+103;
            for(int i=0;i<8;i++) { float cx=start+(i%4)*112, cy=top+(i/4)*62; if(mouseX>=cx-2&&mouseX<cx+90&&mouseY>=cy-2&&mouseY<cy+44) { accent=i; Theme.accent(ACCENTS[i]); } }
        } else {
            if(mouseY>=y+90&&mouseY<y+120) { dragging=true; frost=sliderValue(mouseX,x+24,390); }
            else if(mouseY>=y+134&&mouseY<y+164) { dragging=true; liquid=sliderValue(mouseX,x+24,390); }
            else if(mouseY>=y+234&&mouseY<y+264&&matteOn) { dragging=true; matte=sliderValue(mouseX,x+24,390); }
            else if(mouseY>=y+174&&mouseY<y+202) { if(mouseX<x+w/2)frostOn=!frostOn; else liquidOn=!liquidOn; }
            else if(mouseY>=y+202&&mouseY<y+230) { if(mouseX<x+w/2)coloured=!coloured; else matteOn=!matteOn; }
        }
    }

    @Override protected void mouseClickMove(int x,int y,int button,long elapsed) { if(dragging)updateSlider(x,y); }
    @Override protected void mouseReleased(int x,int y,int state) { dragging=false; }
    @Override protected void keyTyped(char c,int key) { if(key==Keyboard.KEY_ESCAPE)finish(); else if(key==Keyboard.KEY_RETURN){ if(step==3)finish(); else step++; } }
    private void finish() {
        Theme.surfaceTheme(theme); Theme.accent(ACCENTS[Math.max(0,Math.min(accent,ACCENTS.length-1))]);
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.accent != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.accent.setAppearance(ACCENTS[accent], false);
        Theme.frost(frostOn); Theme.liquidGlass(liquidOn);
        Theme.frostOpacity=frost; Theme.liquidOpacity=liquid; Theme.liquidFrost=matte; Theme.colouredFrost=coloured; Theme.matteLiquid=matteOn;
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.surfaceTheme != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.surfaceTheme.setValue(theme);
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.frostOpacity != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.frostOpacity.setValue(frost*100.0);
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.liquidOpacity != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.liquidOpacity.setValue(liquid*100.0);
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.liquidFrost != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.liquidFrost.setValue(matte*100.0);
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.colouredFrost != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.colouredFrost.set(coloured);
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.matteLiquid != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.matteLiquid.set(matteOn);
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.frostedGlass != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.frostedGlass.set(frostOn);
        if (xyz.daikirai.gui.onyx.OnyxClickGuiModule.liquidGlass != null) xyz.daikirai.gui.onyx.OnyxClickGuiModule.liquidGlass.set(liquidOn);
        Theme.savePreferences(); FirstRun.dismissed(); mc.displayGuiScreen(MenuStyle.current().screen());
    }
    private void sound() { mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"),1)); }
}
