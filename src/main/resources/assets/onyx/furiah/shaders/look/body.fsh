#version 120
uniform sampler2D uTex;
uniform sampler2D uLightmap;
uniform float uLit;
uniform int uMode;
uniform vec3 uOrigin;
uniform float uSpan;
uniform float uProgress;
uniform float uNoise;
uniform float uEdge;
uniform vec4 uEdgeColor;
uniform float uSide;
uniform float uAlphaCut;
varying vec2 vUv;
varying vec4 vColor;
varying vec3 vWorld;
varying vec2 vLight;
float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}
float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}
void main() {
    vec4 t = texture2D(uTex, vUv);
    if (t.a < uAlphaCut) discard;
    vec3 rel = vWorld - uOrigin;
    float s;
    if (uMode == 0) s = rel.y / uSpan;
    else if (uMode == 1) s = 1.0 - rel.y / uSpan;
    else s = length(rel) / uSpan;
    float n = noise(rel * 7.0) * 0.62 + noise(rel * 19.0) * 0.38;
    float v = mix(s, n, uNoise);
    float p = uProgress * (1.0 + uEdge) - uEdge;
    // uSide 1: the look the sweep has reached (v < p); -1: the one it hasn't (v >= p).
    if (uSide > 0.0 ? v >= p : v < p) discard;
    vec4 c = t * vColor;
    if (uLit > 0.5) c.rgb *= texture2D(uLightmap, vLight).rgb;
    if (uSide > 0.0) {
        float e = 1.0 - smoothstep(0.0, uEdge, p - v);
        c.rgb = mix(c.rgb, uEdgeColor.rgb * 1.6, e * e * uEdgeColor.a);
        c.a = max(c.a, e * e * uEdgeColor.a * t.a);
    }
    gl_FragColor = c;
}
