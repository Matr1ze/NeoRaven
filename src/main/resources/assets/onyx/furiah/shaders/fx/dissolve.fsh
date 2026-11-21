#version 120
uniform sampler2D uTex;
uniform sampler2D uLightmap;
uniform float uProgress;
uniform float uNoise;
uniform float uEdge;
uniform vec4 uEdgeColor;
uniform float uAlpha;
uniform float uLit;
varying vec2 vUv;
varying vec4 vColor;
varying vec3 vPos;
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
    if (t.a < 0.1) discard;
    float n = noise(vPos * 7.0) * 0.62 + noise(vPos * 19.0) * 0.38;
    float sweep = (vColor.a * 255.0 - 1.0) / 254.0;
    float v = mix(sweep, n, uNoise);
    float p = uProgress * (1.0 + uEdge) - uEdge;
    if (v < p) discard;
    float e = 1.0 - smoothstep(0.0, uEdge, v - p);
    vec3 base = t.rgb * vColor.rgb;
    if (uLit > 0.5) base *= texture2D(uLightmap, vLight).rgb;
    vec3 col = mix(base, uEdgeColor.rgb * 1.7, e * e);
    gl_FragColor = vec4(col, uAlpha);
}
