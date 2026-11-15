package xyz.daikirai.uitest;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.client.BorderlessWindow;
import xyz.daikirai.gui.onyx.OnyxClickGui;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.gui.onyx.menu.*;
import xyz.daikirai.gui.onyx.render.Blur;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;

/** Exercises native SW_MAXIMIZE/SW_RESTORE, not F11, while the menu glass backdrop is frozen. */
@Mod(modid="neoraven_glass_test", name="Glass resize test", version="1", dependencies="after:clickgui")
public final class GlassResizeSmoke {
    private interface Win extends StdCallLibrary { boolean ShowWindow(Pointer hwnd, int command); boolean IsZoomed(Pointer hwnd); }
    private Win win;
    private long next, hwnd;
    private int stage, smallWidth, smallHeight;
    private OnyxClickGui panel;
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if (Boolean.getBoolean("neoraven.glassSmoke")) MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || System.nanoTime() < next) return;
        next = System.nanoTime() + 1_000_000_000L;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (stage == 0) {
                if(mc.isFullScreen()) mc.toggleFullscreen();
                hwnd = BorderlessWindow.windowHandle();
                win = (Win) Native.loadLibrary("user32", Win.class);
                MenuStyle.select(MenuStyle.ANDROID);
                MenuBackground.select(MenuBackground.Style.GRADIENT);
                OnyxClickGuiModule.liquidGlass.set(true);
                OnyxClickGuiModule.frostedGlass.set(false);
                OnyxClickGuiModule.sync();
                panel = new OnyxClickGui(); mc.displayGuiScreen(panel);
                smallWidth=mc.displayWidth; smallHeight=mc.displayHeight;
            } else {
                require(mc.currentScreen == panel, "Screen replaced");
                require(BorderlessWindow.windowHandle()==hwnd, "HWND changed");
                boolean maximized = stage % 2 == 0;
                require(win.IsZoomed(new Pointer(hwnd)) == maximized, "Maximize state wrong");
                if (!maximized) require(mc.displayWidth==smallWidth && mc.displayHeight==smallHeight, "Restore dimensions wrong");
                require(Blur.ready(), "Backdrop unavailable");
                GlStateManager.bindTexture(Blur.sharpTexture());
                int w=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH);
                int h=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
                require(w==mc.displayWidth && h==mc.displayHeight, "Backdrop texture has stale dimensions");
                ByteBuffer pixels=BufferUtils.createByteBuffer(w*h*4);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                long sum=0; int samples=0;
                for(int i=0;i<w*h;i+=97){sum+=(pixels.get(i*4)&255)+(pixels.get(i*4+1)&255)+(pixels.get(i*4+2)&255);samples+=3;}
                require(sum/(double)samples > 8, "Glass captured a black framebuffer");
                require(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL error");
                ScreenShotHelper.saveScreenshot(new File("."),"resize-"+stage+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                System.out.println("[GLASS RESIZE] PASS stage="+stage+" maximized="+maximized+" size="+w+"x"+h+" backdropMean="+sum/(double)samples);
                if(stage==7){Files.write(new File("PASS.txt").toPath(),"PASS: native maximize/restore three cycles, sharp backdrop colour and dimensions, same screen and HWND\n".getBytes("UTF-8")); finish();return;}
                win.ShowWindow(new Pointer(hwnd),maximized ? 9 : 3);
            }
            stage++;
        } catch(Throwable failure) {
            failure.printStackTrace();
            try{Files.write(new File("FAIL.txt").toPath(),failure.toString().getBytes("UTF-8"));}catch(Exception ignored){}
            finish();
        }
    }
    private void finish(){MinecraftForge.EVENT_BUS.unregister(this);Minecraft.getMinecraft().shutdown();}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
