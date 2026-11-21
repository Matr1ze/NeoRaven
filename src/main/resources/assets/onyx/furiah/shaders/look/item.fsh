#version 120
uniform sampler2D uTex;
uniform sampler2D uLightmap;
uniform int uLook;
uniform vec4 uTint;
uniform float uLevels;
varying vec2 vUv;
varying vec4 vColor;
varying vec2 vLight;
varying float vShade;
void main() {
    vec4 t = texture2D(uTex, vUv) * vColor;
    if (t.a < 0.1) discard;
    float l = dot(t.rgb, vec3(0.299, 0.587, 0.114));
    vec3 c = t.rgb;
    float a = t.a;
    // 2 INK: flat ink-wash grays
    if (uLook == 2) { float k = floor(clamp((l - 0.07) / 0.86, 0.0, 0.999) * uLevels) / (uLevels - 1.0); c = mix(vec3(0.11, 0.11, 0.12), vec3(0.925, 0.898, 0.831), pow(k, 0.9)); }
    // 3 SKETCH: paper white, graphite where it is dark
    else if (uLook == 3) { c = mix(vec3(0.957, 0.945, 0.918), vec3(0.26, 0.26, 0.28), smoothstep(0.75, 0.15, l)); }
    // 4 GLASS: luminous and see-through
    else if (uLook == 4) { c = mix(vec3(l), c, 1.35) * 0.55 + 0.45 * c; a *= 0.75; }
    // 5 PAPER: a little faded towards paper
    else if (uLook == 5) { c = mix(mix(c, vec3(l), 0.25), vec3(0.933, 0.914, 0.871), 0.16); }
    // 6 SHADOW: charred
    else if (uLook == 6) { c = mix(vec3(0.063, 0.047, 0.067), vec3(0.21, 0.157, 0.173), l * 0.75); }
    // 7 TOON: three flat tones
    else if (uLook == 7) { float m = max(c.r, max(c.g, c.b)); float v = m < 0.3 ? 0.38 : (m < 0.62 ? 0.68 : 0.95); c = m > 0.001 ? c / m * v : vec3(0.0); }
    // 1 MATERIAL: the material's color by brightness
    else if (uLook == 1) { c = uTint.rgb * (0.35 + 0.85 * l); }
    c *= vShade * texture2D(uLightmap, vLight).rgb;
    gl_FragColor = vec4(c, a);
}
