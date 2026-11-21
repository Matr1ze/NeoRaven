#version 120
uniform sampler2D uTex;
uniform vec3 uOrigin;
uniform vec3 uRight;
uniform vec3 uUp;
uniform vec4 uSeg[3];
uniform float uRadius[3];
uniform float uDone[3];
varying vec2 vUv;
varying vec4 vColor;
varying vec3 vPos;
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
void main() {
    vec4 t = texture2D(uTex, vUv);
    if (t.a < 0.1) discard;
    vec3 d = vPos - uOrigin;
    vec2 q = vec2(dot(d, uRight), dot(d, uUp));
    float ragged = 0.8 + 0.4 * noise(q * 11.0);
    for (int i = 0; i < 3; i++) {
        if (uDone[i] <= 0.0) continue;
        vec2 a = uSeg[i].xy;
        vec2 ab = uSeg[i].zw - a;
        float h = clamp(dot(q - a, ab) / max(dot(ab, ab), 1e-6), 0.0, uDone[i]);
        if (length(q - (a + ab * h)) < uRadius[i] * ragged) discard;
    }
    gl_FragColor = t * vColor;
}
