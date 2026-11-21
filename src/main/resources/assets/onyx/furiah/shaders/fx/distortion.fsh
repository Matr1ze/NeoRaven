#version 120
uniform sampler2D uScene;
uniform vec2 uScreen;  // size of the copied area, window pixels
uniform vec2 uView;  // size of the current viewport, pixels
varying vec2 vOffset;
varying vec4 vColor;
varying float vFresnel;
vec3 scene(vec2 px) { return texture2D(uScene, clamp(px / uScreen, vec2(0.001), vec2(0.999))).rgb; }
void main() {
    vec2 shift = vOffset * 0.5 * uView;
    vec2 px = gl_FragCoord.xy + shift;
    vec2 split = shift * 0.12;
    vec3 col = vec3(scene(px + split).r, scene(px).g, scene(px - split).b);
    vec3 tint = vColor.rgb;
    col = col * mix(vec3(1.0), tint, 0.16) + tint * vFresnel * 0.8 + vec3(vFresnel * vFresnel * 0.4);
    gl_FragColor = vec4(col, vColor.a);
}
