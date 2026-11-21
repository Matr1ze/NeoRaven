package xyz.daikirai.render;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.render.WispMotion.Point;
import xyz.daikirai.render.WispMotion.Sample;

/** Additive soft heads and tapered camera-facing ribbons; no framebuffer postprocessing. */
public final class WeaponWispRenderer {
    private final Matrix4f worldModel=new Matrix4f(),worldProjection=new Matrix4f(),inverseWorld=new Matrix4f();
    private final Matrix4f handModel=new Matrix4f(),handProjection=new Matrix4f(),combined=new Matrix4f();
    private final FloatBuffer matrix=BufferUtils.createFloatBuffer(16);
    private Point origin=new Point(0,0,0),right=new Point(1,0,0),up=new Point(0,1,0);
    private boolean worldValid,handValid;
    private DynamicTexture texture;

    public void resetView(){worldValid=handValid=false;}
    public void dispose(){if(texture!=null){texture.deleteGlTexture();texture=null;}resetView();}
    private void read(int name,Matrix4f target){matrix.clear();GL11.glGetFloat(name,matrix);matrix.rewind();target.load(matrix);}
    public void captureWorld(float partial){
        read(GL11.GL_MODELVIEW_MATRIX,worldModel);read(GL11.GL_PROJECTION_MATRIX,worldProjection);
        Matrix4f.mul(worldProjection,worldModel,combined);worldValid=Matrix4f.invert(combined,inverseWorld)!=null;
        Vec3 camera=WorldRenderState.camera(partial);origin=new Point(camera.xCoord,camera.yCoord,camera.zCoord);
        right=new Point(worldModel.m00,worldModel.m10,worldModel.m20).unit();
        up=new Point(worldModel.m01,worldModel.m11,worldModel.m21).unit();
    }
    public Point[] captureHand(double seconds,double speed,double radius){
        read(GL11.GL_MODELVIEW_MATRIX,handModel);read(GL11.GL_PROJECTION_MATRIX,handProjection);handValid=true;
        return mapHand(seconds,speed,radius,0);
    }
    /** Locate the weapon's middle through the actual baked-model display transform. */
    public Point[] captureBlade(ItemStack stack,double seconds,double speed,double radius,long seed){
        Minecraft mc=Minecraft.getMinecraft();
        read(GL11.GL_MODELVIEW_MATRIX,handModel);
        float scale=(float)Math.sqrt(handModel.m00*handModel.m00+handModel.m01*handModel.m01+handModel.m02*handModel.m02);
        Matrix4f blade=new Matrix4f();
        GlStateManager.pushMatrix();
        try{
            if(mc.getRenderItem().shouldRenderItemIn3D(stack))GlStateManager.scale(2,2,2);
            IBakedModel model=mc.getRenderItem().getItemModelMesher().getItemModel(stack);
            if(!model.isGui3d())GlStateManager.scale(2,2,2); // RenderItem.preTransform
            ModelResourceLocation variant=stack.getItem().getModel(stack,mc.thePlayer,mc.thePlayer.getItemInUseCount());
            if(variant!=null)model=mc.getRenderItem().getItemModelMesher().getModelManager().getModel(variant);
            ForgeHooksClient.handleCameraTransforms(model,ItemCameraTransforms.TransformType.FIRST_PERSON);
            GlStateManager.scale(.5F,.5F,.5F); // RenderItem.renderItem's centred model scale
            // Generated sword sprites run diagonally from their lower-left hilt to upper-right tip.
            // RenderItem uses centred model coordinates; (0,0,0) is the weapon's middle.
            read(GL11.GL_MODELVIEW_MATRIX,blade);
        }finally{GlStateManager.popMatrix();}
        handModel.setIdentity();handModel.m00=handModel.m11=handModel.m22=scale;
        handModel.m30=blade.m30;handModel.m31=blade.m31;handModel.m32=blade.m32;
        read(GL11.GL_PROJECTION_MATRIX,handProjection);handValid=true;
        return mapHand(seconds,speed,radius,seed);
    }
    public Point[] mapHand(double seconds,double speed,double radius,long seed){
        if(!worldValid||!handValid)return null;
        Point[] home=new Point[3];
        for(int i=0;i<3;i++){
            Point p=WispMotion.weapon(i,seconds,speed,radius,seed);
            Vector4f eye=Matrix4f.transform(handModel,new Vector4f((float)p.x,(float)p.y,(float)p.z,1),null);
            Vector4f clip=Matrix4f.transform(handProjection,eye,null);
            Vector4f relative=Matrix4f.transform(inverseWorld,clip,null);
            if(Math.abs(relative.w)<1e-8)return null;
            home[i]=new Point(origin.x+relative.x/relative.w,origin.y+relative.y/relative.w,origin.z+relative.z/relative.w);
        }
        return home;
    }
    public void drawWorld(WispMotion motion,float partial,long now,double life,double size,int rgb,float alpha,boolean crossBody){
        if(motion.points[0]==null||alpha<=0)return;
        @SuppressWarnings("unchecked") List<Sample>[] trails=(List<Sample>[])new List<?>[3];
        for(int i=0;i<3;i++){trails[i]=new ArrayList<Sample>(motion.trails[i]);trails[i].add(new Sample(motion.points[i],now));}
        try(State state=new State(crossBody)){
            bind();paint(motion.points,trails,origin,right,up,now,life,size,rgb,alpha);
        }
    }
    public double handWorldSize(double size){
        return handValid&&worldValid?size*.28*Math.abs(handProjection.m00/worldProjection.m00):size;
    }
    public void drawHand(WispMotion motion,long now,double life,double size,int rgb,float alpha){
        if(!handValid||!worldValid||motion.points[0]==null||alpha<=0)return;
        Matrix4f inverse=Matrix4f.invert(handProjection,null);
        if(inverse==null)return;
        Matrix4f worldToHand=Matrix4f.mul(inverse,Matrix4f.mul(worldProjection,worldModel,null),null);
        Point[] heads=new Point[3];
        @SuppressWarnings("unchecked") List<Sample>[] trails=(List<Sample>[])new List<?>[3];
        for(int i=0;i<3;i++){
            trails[i]=new ArrayList<Sample>(motion.trails[i].size()+1);
            for(Sample sample:motion.trails[i])trails[i].add(new Sample(handPoint(worldToHand,sample.point),sample.time));
            heads[i]=handPoint(worldToHand,motion.points[i]);
            trails[i].add(new Sample(heads[i],now));
        }
        try(State state=new State(false)){
            GL11.glLoadIdentity();bind();paint(heads,trails,new Point(0,0,0),new Point(1,0,0),new Point(0,1,0),now,life,size*.28,rgb,alpha);
        }
    }
    private Point handPoint(Matrix4f transform,Point point){
        Point relative=point.minus(origin);
        Vector4f eye=Matrix4f.transform(transform,new Vector4f((float)relative.x,(float)relative.y,(float)relative.z,1),null);
        return new Point(eye.x/eye.w,eye.y/eye.w,eye.z/eye.w);
    }
    private void bind(){
        if(texture==null){
            BufferedImage image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<64;y++)for(int x=0;x<64;x++){
                double dx=(x-31.5)/31.5,dy=(y-31.5)/31.5,r=dx*dx+dy*dy;
                double a=Math.max(0,(Math.exp(-r*4.8)-Math.exp(-4.8))/(1-Math.exp(-4.8)));
                image.setRGB(x,y,Math.min(255,(int)Math.round(a*255))<<24|0xFFFFFF);
            }
            texture=new DynamicTexture(image);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture.getGlTextureId());GlStateManager.bindTexture(texture.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,0x812F);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,0x812F);
    }
    private void paint(Point[] heads,List<Sample>[] trails,Point origin,Point right,Point up,long now,double life,double size,int rgb,float alpha){
        Point view=right.cross(up).unit();int pale=WorldRenderState.mix(rgb,-1,.45);
        for(int i=0;i<3;i++){
            ribbon(trails[i],origin,right,view,now,life,size*2.4,rgb,alpha*.18);
            ribbon(trails[i],origin,right,view,now,life,size*.9,pale,alpha*.7);
            ribbon(trails[i],origin,right,view,now,life,size*.20,-1,alpha*.85);
        }
        for(Point head:heads){
            quad(head,origin,right,up,size*2.9,rgb,alpha*.58);
            quad(head,origin,right,up,size*1.15,pale,alpha*.95);
            quad(head,origin,right,up,size*.48,-1,alpha);
        }
    }
    private static void ribbon(List<Sample> samples,Point origin,Point fallback,Point view,long now,double life,double width,int rgb,double alpha){
        if(samples.size()<2)return;
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for(int i=0;i<samples.size();i++){
            Sample sample=samples.get(i);
            Point before=samples.get(Math.max(0,i-1)).point,after=samples.get(Math.min(samples.size()-1,i+1)).point;
            double dx=after.x-before.x,dy=after.y-before.y,dz=after.z-before.z;
            double sx=view.y*dz-view.z*dy,sy=view.z*dx-view.x*dz,sz=view.x*dy-view.y*dx;
            double length=Math.sqrt(sx*sx+sy*sy+sz*sz);
            if(length<1e-8){sx=fallback.x;sy=fallback.y;sz=fallback.z;length=1;}
            double remaining=Math.max(0,Math.min(1,1-(now-sample.time)/(life*1e9)));
            double half=width*Math.pow(remaining,.8),opacity=alpha*remaining*remaining;
            WorldRenderState.color(rgb,opacity);
            double x=sample.point.x-origin.x,y=sample.point.y-origin.y,z=sample.point.z-origin.z;
            vertex(x-sx/length*half,y-sy/length*half,z-sz/length*half,0,.5F);
            vertex(x+sx/length*half,y+sy/length*half,z+sz/length*half,1,.5F);
        }
        GL11.glEnd();
    }
    private static void quad(Point p,Point origin,Point r,Point u,double size,int rgb,double alpha){
        double x=p.x-origin.x,y=p.y-origin.y,z=p.z-origin.z;
        WorldRenderState.color(rgb,alpha);GL11.glBegin(GL11.GL_QUADS);
        vertex(x-r.x*size-u.x*size,y-r.y*size-u.y*size,z-r.z*size-u.z*size,0,0);
        vertex(x-r.x*size+u.x*size,y-r.y*size+u.y*size,z-r.z*size+u.z*size,0,1);
        vertex(x+r.x*size+u.x*size,y+r.y*size+u.y*size,z+r.z*size+u.z*size,1,1);
        vertex(x+r.x*size-u.x*size,y+r.y*size-u.y*size,z+r.z*size-u.z*size,1,0);
        GL11.glEnd();
    }
    private static void vertex(double x,double y,double z,float u,float v){GL11.glTexCoord2f(u,v);GL11.glVertex3d(x,y,z);}

    private static final class State implements AutoCloseable {
        private final int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),matrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        private final int program=OpenGlHelper.shadersSupported?GL11.glGetInteger(0x8B8D):0;
        private final int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int[] textures=new int[Math.max(1,Math.min(32,GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS)))];
        private final boolean[] enabled=new boolean[textures.length];
        private final FloatBuffer color=BufferUtils.createFloatBuffer(16);
        State(boolean crossBody){
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            for(int i=0;i<textures.length;i++){
                OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit+i);
                textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);enabled[i]=GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            active(OpenGlHelper.defaultTexUnit);GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPushMatrix();GL11.glLoadIdentity();
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();Shader.unbind();
            for(int cap:new int[]{GL11.GL_FOG,GL11.GL_LIGHTING,GL11.GL_ALPHA_TEST,GL11.GL_CULL_FACE,GL11.GL_COLOR_LOGIC_OP,0x803A})GL11.glDisable(cap);
            GL11.glEnable(GL11.GL_BLEND);OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE,GL11.GL_ONE);GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL11.glDepthMask(false);GL11.glDepthFunc(GL11.GL_LEQUAL);
            if(crossBody)GL11.glDisable(GL11.GL_DEPTH_TEST);else GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glShadeModel(GL11.GL_SMOOTH);
        }
        @Override public void close(){
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();
            active(OpenGlHelper.defaultTexUnit);GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPopMatrix();
            GlStateManager.tryBlendFuncSeparate(src,dst,srcA,dstA);GlStateManager.resetColor();GlStateManager.color(color.get(0),color.get(1),color.get(2),color.get(3));
            for(int i=0;i<textures.length;i++){
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit+i);GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i]);GlStateManager.bindTexture(textures[i]);
                GlStateManager.disableTexture2D();if(enabled[i])GlStateManager.enableTexture2D();
            }
            GL11.glPopAttrib();if(OpenGlHelper.shadersSupported)OpenGlHelper.glUseProgram(program);active(active);GL11.glMatrixMode(matrix);
        }
        private static void active(int unit){GlStateManager.setActiveTexture(unit==OpenGlHelper.defaultTexUnit?OpenGlHelper.lightmapTexUnit:OpenGlHelper.defaultTexUnit);GlStateManager.setActiveTexture(unit);}
    }
}
