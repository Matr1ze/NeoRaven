#version 120
varying vec4 vertexColor;
varying vec2 localPos;

// Same harmonic polar contour as the supplied md3-clock.html, in its 600-unit SVG space.
float radius(float t) {
    return 257.7628
        + 2.4321*cos(t) + 1.6149*sin(t)
        - 0.0654*cos(2.0*t) - 0.0012*sin(2.0*t)
        - 0.0417*cos(6.0*t) + 0.0290*sin(6.0*t)
        - 0.4806*cos(11.0*t) + 0.2322*sin(11.0*t)
        + 7.8021*cos(12.0*t) + 0.8019*sin(12.0*t)
        + 0.4474*cos(13.0*t) + 0.3325*sin(13.0*t)
        - 0.0703*cos(16.0*t) - 0.0003*sin(16.0*t)
        + 0.4594*cos(24.0*t) + 0.1249*sin(24.0*t);
}
void main() {
    float t = dot(localPos,localPos)>0.000001 ? atan(localPos.x,-localPos.y) : 0.0;
    float dist = length(localPos)-radius(t);
    float edge = max(fwidth(dist),0.25)*0.75;
    float alpha=1.0-smoothstep(-edge,edge,dist);
    if(alpha<=0.0) discard;
    gl_FragColor=vec4(vertexColor.rgb,vertexColor.a*alpha);
}
