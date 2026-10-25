package xyz.daikirai.gui.onyx.menu;

import xyz.daikirai.gui.onyx.material.*;
import xyz.daikirai.gui.onyx.render.*;

final class MenuButton {
    final float x,y,w,h;
    final String label, icon;
    final Runnable action;
    final int style;
    boolean enabled = true;
    Spring hover = new Spring(0);
    MenuButton(float x,float y,float w,float h,String label,String icon,int style,Runnable action) {
        this.x=x; this.y=y; this.w=w; this.h=h; this.label=label; this.icon=icon; this.style=style; this.action=action;
    }
    boolean hit(float mx,float my) { return mx >= x && my >= y && mx < x+w && my < y+h; }
    void draw(UI ui,Scheme s,float mx,float my,float dt) {
        boolean over=enabled && hit(mx,my); hover.target(over ? 1 : 0); hover.update(dt);
        int foreground=style == 1 ? s.onPrimary : s.onSecondaryContainer;
        if (!enabled) ui.alphaPush(0.38F);
        if (style == 0) {
            ui.shadow(x,y,w,h,12,1);
            ui.rounded(x,y,w,h,12,s.secondaryContainer);
            if(Theme.frost()) ui.frost(x,y,w,h,12,s.secondaryContainer,0.62F+0.16F*hover.value());
        } else if (style == 1) ui.rounded(x,y,w,h,20,s.primary);
        if (hover.value() > 0.001F) ui.rounded(x,y,w,h,style == 0 ? 12 : 20,Material.withAlpha(foreground,hover.value()*0.08F));
        if (icon != null) ui.icon(icon,label.isEmpty() ? x+w/2 : x+17,y+h/2,18,foreground);
        if (!label.isEmpty()) ui.text(Typeface.BODY_MEDIUM,label,icon == null ? x+w/2 : x+34,y+h/2,foreground,icon == null ? UI.ALIGN_CENTER : UI.ALIGN_LEFT);
        if (!enabled) ui.alphaPop();
    }
}
