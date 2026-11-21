#version 120
uniform sampler2D uMatcap;
// 1: the image's alpha is the share of the light that takes the color (Custom finishes)
uniform float uTintable;
uniform float uLight;
// rim color (rgb) and strength (a)
uniform vec4 uRim;
uniform float uGlass;
varying vec3 vNormal;
varying vec3 vView;
varying vec4 vColor;
varying float vGlint;
void main() {
    vec3 n = normalize(vNormal);
    if (!gl_FrontFacing) n = -n;
    vec3 u = normalize(vView);
    vec3 r = reflect(u, n);
    float m = 2.0 * sqrt(r.x * r.x + r.y * r.y + (r.z + 1.0) * (r.z + 1.0));
    vec4 t = texture2D(uMatcap, r.xy / m + 0.5);
    float share = uTintable > 0.5 ? t.a : 1.0;
    vec3 col = t.rgb * mix(vec3(1.0), vColor.rgb, share) * uLight;
    float facing = clamp(-dot(n, u), 0.0, 1.0);
    float edge = 1.0 - facing;
    col += uRim.rgb * (uRim.a * edge * edge * edge);
    col += vec3(vGlint * (0.35 + 0.65 * facing) * max(uLight, 0.35));
    float a = vColor.a;
    if (uGlass > 0.5) a *= mix(0.3, 0.95, edge * edge);
    gl_FragColor = vec4(min(col, vec3(1.0)), a);
}
