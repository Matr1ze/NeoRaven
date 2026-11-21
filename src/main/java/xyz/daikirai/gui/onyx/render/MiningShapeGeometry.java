package xyz.daikirai.gui.onyx.render;

import java.awt.geom.Path2D;

/** Selected outline from m3-shapes-gallery(1).html; the active length is actual mining progress. */
final class MiningShapeGeometry {
    static final int SAMPLES=256;
    static final double TAU=Math.PI*2;
    static final double[][] SHAPES=shapes();
    final double[] x=new double[SAMPLES+1],y=new double[SAMPLES+1],cumulative=new double[SAMPLES+1];
    double progress,rotation;
    int shapeIndex;
    void set(int shape,double fraction,double angle){
        shapeIndex=Math.max(0,Math.min(SHAPES.length-1,shape));
        progress=Math.max(0,Math.min(1,Double.isNaN(fraction)?0:fraction));rotation=angle;
        cumulative[0]=0;
        for(int i=0;i<=SAMPLES;i++){
            double theta=(double)i/SAMPLES*TAU+angle, radius=SHAPES[shapeIndex][i%SAMPLES];
            x[i]=radius*Math.cos(theta);y[i]=radius*Math.sin(theta);
            if(i>0)cumulative[i]=cumulative[i-1]+Math.hypot(x[i]-x[i-1],y[i]-y[i-1]);
        }
    }
    void track(Path2D.Double path,double cx,double cy,double radius){
        path.reset();path.moveTo(cx+x[0]*radius,cy+y[0]*radius);
        for(int i=1;i<=SAMPLES;i++)path.lineTo(cx+x[i]*radius,cy+y[i]*radius);
        path.closePath();
    }
    void active(Path2D.Double path,double cx,double cy,double radius){
        path.reset();if(progress<=0)return;
        if(progress>=1){track(path,cx,cy,radius);return;}
        double end=progress*cumulative[SAMPLES];
        path.moveTo(cx+x[0]*radius,cy+y[0]*radius);
        for(int i=1;i<=SAMPLES;i++){
            if(cumulative[i]<=end)path.lineTo(cx+x[i]*radius,cy+y[i]*radius);
            else{
                double t=(end-cumulative[i-1])/(cumulative[i]-cumulative[i-1]);
                path.lineTo(cx+(x[i-1]+(x[i]-x[i-1])*t)*radius,cy+(y[i-1]+(y[i]-y[i-1])*t)*radius);break;
            }
        }
    }
    private static double pill(double theta,double length,double radius){
        double c=Math.cos(theta),s=Math.sin(theta),sa=Math.abs(s),ca=Math.abs(c);
        if(sa<1e-6)return length+radius;
        if(sa*length>=radius*ca)return radius/sa;
        return length*ca+Math.sqrt(Math.max(0,radius*radius-length*length*s*s));
    }
    private static double[][] shapes() {
        double[][] result = new double[7][SAMPLES];
        int[] passes = {3, 2, 5, 2, 3, 2, 1};
        for (int shape = 0; shape < 7; shape++) {
            double[] samples = result[shape];
            for (int i = 0; i < SAMPLES; i++) {
                double t = (double)i / SAMPLES * TAU;
                switch (shape) {
                    case 0: samples[i] = 1 + .15 * Math.cos(8 * t); break;
                    case 1: samples[i] = 1 + .13 * Math.cos(9 * t); break;
                    case 2: samples[i] = polygon(t, 5, -Math.PI / 2); break;
                    case 3: samples[i] = pill(t, .30, .70); break;
                    case 4: samples[i] = polygon(t, 12, -Math.PI / 2); break;
                    case 5: samples[i] = 1 + .18 * Math.cos(4 * t); break;
                    default: double c = Math.cos(t), s = Math.sin(t) / .72; samples[i] = 1 / Math.sqrt(c * c + s * s);
                }
            }
            for (int p = 0; p < passes[shape]; p++) {
                double[] next = new double[SAMPLES];
                for (int i = 0; i < SAMPLES; i++) next[i] = (samples[(i + SAMPLES - 2) % SAMPLES]
                        + samples[(i + SAMPLES - 1) % SAMPLES] * 2 + samples[i] * 3
                        + samples[(i + 1) % SAMPLES] * 2 + samples[(i + 2) % SAMPLES]) / 9;
                samples = next;
            }
            double max = 0; for (double value : samples) max = Math.max(max, value);
            for (int i = 0; i < SAMPLES; i++) result[shape][i] = samples[i] / max;
        }
        return result;
    }
    private static double polygon(double theta, int n, double rotation) {
        double sector = TAU / n, a = theta - rotation;
        a -= Math.floor(a / sector) * sector; a -= sector / 2;
        return Math.cos(sector / 2) / Math.cos(a);
    }
}
