package xyz.daikirai.gui.onyx.hud;

/** Sizes and polar contour from the supplied md3-clock.html's 600 x 600 SVG. */
final class ClockFaceGeometry {
    static final float SVG_SIZE = 600, HOUR_LENGTH = 117, MINUTE_LENGTH = 166, STROKE = 40;
    static final float DOT_ORBIT = 212, DOT_RADIUS = 24;
    private static final double[][] HARMONICS = {
        {1,2.4321,1.6149},{2,-.0654,-.0012},{6,-.0417,.0290},
        {11,-.4806,.2322},{12,7.8021,.8019},{13,.4474,.3325},
        {16,-.0703,-.0003},{24,.4594,.1249}
    };
    static final double[][] OUTLINE = outline();
    private ClockFaceGeometry() { }
    static double radius(double angle) {
        double result = 257.7628;
        for (double[] h : HARMONICS) result += h[1] * Math.cos(h[0] * angle) + h[2] * Math.sin(h[0] * angle);
        return result;
    }
    private static double[][] outline() {
        double[][] points = new double[361][2];
        for (int i=0;i<=360;i++) {
            double a=i*Math.PI/180,r=radius(a);
            points[i][0]=Math.round((300+r*Math.sin(a))*100)/100.0;
            points[i][1]=Math.round((300-r*Math.cos(a))*100)/100.0;
        }
        return points;
    }
}
