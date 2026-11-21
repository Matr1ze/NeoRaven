#version 120
varying vec2 uv;
uniform sampler2D scene, geometry;
uniform vec2 pixel;
uniform vec4 controls;

// Compare relative view depths. Opposing samples reject planar gradients.
float z(vec2 p) { return 1.0/max(.0001,1.0-texture2D(geometry,p).r); }
float crease(vec2 p,vec2 d,float center) {
    vec2 delta=1.0-vec2(z(p+d),z(p-d))/center;
    return smoothstep(.003,.045,delta.x+delta.y)
        *(1.0-smoothstep(.12,.35,max(abs(delta.x),abs(delta.y))));
}
vec3 highlight(vec2 p) {
    vec3 c=texture2D(scene,p).rgb;
    return c*smoothstep(.63,.98,dot(c,vec3(.2126,.7152,.0722)));
}
void main() {
    vec2 q=uv*2.0-1.0;
    vec2 p=.5+.5*q*(1.0+.026*controls.z*(dot(q,q)-2.0));
    vec3 c=texture2D(scene,p).rgb;
    if(controls.x>0.0) {
        float center=z(p);
        vec2 r=pixel*clamp(1500.0/center,1.5,6.0);
        float shade=crease(p,vec2(r.x,r.y),center)+crease(p,vec2(r.x,-r.y),center);
        c*=1.0-.14*controls.x*shade;
    }
    if(controls.y>0.0) {
        vec2 r=pixel*clamp(.006/pixel.y,2.0,10.0);
        vec3 halo=highlight(p+r)+highlight(p-r)+highlight(p+vec2(r.x,-r.y))+highlight(p+vec2(-r.x,r.y));
        c+=(1.0-c)*halo*(.095*controls.y);
    }
    c*=controls.w;
    gl_FragColor=vec4(clamp(c,0.0,1.0),1.0);
}