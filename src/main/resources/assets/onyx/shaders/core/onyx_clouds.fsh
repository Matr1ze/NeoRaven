#version 120
varying vec2 uv;
uniform float uAspect;
uniform float uTime;
uniform vec3 uGlow;
uniform vec3 uMid;
uniform vec3 uAccent;
uniform vec3 uDeep;
float hash(vec2 p) {
    p = fract(p * vec2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    mat2 rot = mat2(0.8776, 0.4794, -0.4794, 0.8776);
    for (int i = 0; i < 6; i++) {
        value += amplitude * noise(p);
        p = rot * p * 2.0 + vec2(100.0);
        amplitude *= 0.5;
    }
    return value;
}
void main() {
    vec2 p = uv;
    p.x *= uAspect;
    float t = uTime * 0.11;
    float f1 = fbm(p * 2.5 + vec2(t * 0.4, t * 0.15));
    float f2 = fbm(p * 2.5 + f1 * 0.8 + vec2(t * 0.3 + 1.7, t * 0.1 + 9.2));
    float f3 = fbm(p * 3.0 + f2 * 0.6 + vec2(t * 0.15 + 8.3, -t * 0.2 + 2.8));
    float f4 = fbm(p * 2.0 + f3 * 0.5);
    vec3 color = uDeep;
    color = mix(color, uMid, clamp(f1 * 1.25, 0.0, 1.0));
    color = mix(color, uAccent, clamp(pow(f2, 1.5) * 1.4, 0.0, 0.8));
    float highlight = pow(clamp(f4 * f2 * 2.4, 0.0, 1.0), 2.2);
    color = mix(color, uGlow, clamp(highlight, 0.0, 0.6));
    vec2 centered = uv - 0.5;
    color *= 1.0 - dot(centered, centered) * 0.4;
    color *= 0.95 + 0.05 * sin(t * 1.2 + f4 * 3.14);
    gl_FragColor = vec4(color, 1.0);
}
