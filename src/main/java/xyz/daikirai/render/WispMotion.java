package xyz.daikirai.render;

import java.util.ArrayDeque;
import java.util.Deque;

/** Independent blade drift and continuous flight/trail history. No game state. */
public final class WispMotion {
    public static final int COUNT = 3;
    private static final double TAU = Math.PI * 2;
    private static final long SAMPLE = 8333333L;
    public final Point[] points = new Point[COUNT];
    public final Deque<Sample>[] trails;
    private final Point[] from = new Point[COUNT];
    private final Point[] velocity = new Point[COUNT], launchVelocity = new Point[COUNT];
    private long key = Long.MIN_VALUE, changed, previousTime, sampled;

    @SuppressWarnings("unchecked") public WispMotion() {
        trails = (Deque<Sample>[]) new Deque<?>[COUNT];
        for (int i=0;i<COUNT;i++) trails[i] = new ArrayDeque<Sample>();
    }
    public static Point weapon(int orb, double seconds, double speed, double radius) {
        return weapon(orb,seconds,speed,radius,0);
    }
    public static Point weapon(int orb,double seconds,double speed,double radius,long seed) {
        double t=seconds*speed*2.1;
        long s=seed+orb*0x632BE59BD9B4E019L;
        // Separate smooth random curves in each axis: no common centre, phase or circular track.
        // A fresh seed after combat chooses an entirely new landing/path, not the old orbit phase.
        return new Point(radius*(.34*noise(s,t)+.10*noise(s+11,t*1.63+7.4)),
                radius*(.038*noise(s+23,t*.71+2.3)+.012*noise(s+31,t*1.27+9.1)),
                radius*(.23*noise(s+47,t*.89+4.8)+.07*noise(s+59,t*1.49+1.2)));
    }
    private static double random(long seed,long index) {
        long x=seed+index*0x9E3779B97F4A7C15L;
        x=(x^(x>>>30))*0xBF58476D1CE4E5B9L;
        x=(x^(x>>>27))*0x94D049BB133111EBL;
        return ((x^(x>>>31))>>>11)*0x1.0p-52-1;
    }
    private static double noise(long seed,double time) {
        long n=(long)Math.floor(time);double t=time-n;
        double a=random(seed,n-1),b=random(seed,n),c=random(seed,n+1),d=random(seed,n+2);
        // Catmull-Rom keeps position and velocity continuous across random waypoints.
        return .5*(2*b+(c-a)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t);
    }
    public static Point home(int orb, double seconds, double speed, double radius, double height) {
        double a = seconds * 2.8 * speed + orb * TAU / COUNT;
        return new Point(radius * Math.cos(a), height * (.53 + .36 * Math.sin(a + orb * .48)),
                radius * Math.sin(a));
    }
    public static Point strike(int orb, double seconds, double speed, double radius, double height) {
        double a = seconds * 3.8 * speed + orb * TAU / COUNT;
        double x = radius * Math.sin(a), z = radius * .72 * Math.sin(2 * a);
        double angle = seconds * .28 + orb * .8, c = Math.cos(angle), s = Math.sin(angle);
        // Every half cycle crosses the exact middle of the victim, rather than staying on a ring.
        return new Point(x*c-z*s, height * (.5 + .37*Math.sin(a)), x*s+z*c);
    }
    public void clear() {
        key=Long.MIN_VALUE;changed=previousTime=sampled=0;
        for(int i=0;i<COUNT;i++){points[i]=from[i]=velocity[i]=launchVelocity[i]=null;trails[i].clear();}
    }
    public boolean settled(long now) { return points[0]!=null && now-changed>=320000000L; }
    public boolean at(long destination){return key==destination;}
    public double progress(long now){return Math.max(0,Math.min(1,(now-changed)/320000000.0));}
    public void update(long now, long destination, Point[] desired, double lifetime) {
        Point[] before=points.clone();
        if(points[0]==null) {
            key=destination;changed=now-320000000L;
            for(int i=0;i<COUNT;i++){points[i]=from[i]=desired[i];velocity[i]=launchVelocity[i]=new Point(0,0,0);}
            before=points.clone();
        } else if(key!=destination) {
            key=destination;changed=now;
            for(int i=0;i<COUNT;i++){from[i]=points[i];launchVelocity[i]=velocity[i];}
        }
        double t=progress(now),blend=t*t*(3-2*t),carry=.32*t*(1-t)*(1-t);
        for(int i=0;i<COUNT;i++) {
            // Preserve outgoing motion when changing destination, and meet the moving drift
            // with its own velocity at the end; never brake to zero at either handoff.
            points[i]=Point.mix(from[i],desired[i],blend).plus(launchVelocity[i].times(carry));
            if(previousTime!=0&&now>previousTime&&now-previousTime<250000000L)
                velocity[i]=points[i].minus(before[i]).times(1e9/(now-previousTime));
        }
        if(previousTime==0 || now-previousTime>250000000L || now<previousTime) {
            for(Deque<Sample> trail:trails) trail.clear();
            sampled=now-SAMPLE; previousTime=now;
        }
        long start=sampled+SAMPLE;
        for(long time=start;time<=now;time+=SAMPLE) {
            double k=now==previousTime?1:Math.max(0,Math.min(1,(time-previousTime)/(double)(now-previousTime)));
            for(int i=0;i<COUNT;i++) trails[i].addLast(new Sample(Point.mix(before[i],points[i],k),time));
            sampled=time;
        }
        long oldest=now-(long)(lifetime*1e9);
        for(Deque<Sample> trail:trails) {
            while(!trail.isEmpty()&&(trail.peekFirst().time<oldest||trail.size()>160))trail.removeFirst();
        }
        previousTime=now;
    }
    public static final class Point {
        public final double x,y,z;
        public Point(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        public Point plus(Point p){return new Point(x+p.x,y+p.y,z+p.z);}
        public Point minus(Point p){return new Point(x-p.x,y-p.y,z-p.z);}
        public Point times(double s){return new Point(x*s,y*s,z*s);}
        public Point cross(Point p){return new Point(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}
        public double length(){return Math.sqrt(x*x+y*y+z*z);}
        public Point unit(){double n=length();return n<1e-9?new Point(1,0,0):times(1/n);}
        public static Point mix(Point a,Point b,double t){return new Point(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.z+(b.z-a.z)*t);}
    }
    public static final class Sample {
        public final Point point;public final long time;
        public Sample(Point point,long time){this.point=point;this.time=time;}
    }
}
