#version 120

// Soft "Material You" wallpaper for the Android title screen: a near white surface with
// three very low chroma tonal blobs bleeding into it. All colours arrive from the CPU as
// tones of the live accent palette, so the wallpaper follows the ClickGUI theme.
varying vec2 uv;

uniform float uAspect;
uniform float uTime;
uniform vec3 uBase;
uniform vec3 uCool;
uniform vec3 uWarm;
uniform vec3 uGlow;

float blob(vec2 p, vec2 center, float radius) {
    vec2 d = p - center;
    return exp(-dot(d, d) / (radius * radius));
}

void main() {
    vec2 p = vec2(uv.x * uAspect, uv.y);
    float t = uTime * 0.012;

    vec3 color = uBase;
    color = mix(color, uCool, blob(p, vec2(uAspect * 0.18, 0.88 + 0.02 * sin(t)), 0.60) * 0.85);
    color = mix(color, uWarm, blob(p, vec2(uAspect * 0.84, 0.14 + 0.02 * cos(t * 1.3)), 0.52) * 0.80);
    color = mix(color, uGlow, blob(p, vec2(uAspect * 0.56, 0.58), 0.95) * 0.45);

    // A wide diagonal sheen keeps the empty middle from reading as a flat fill.
    float sheen = smoothstep(0.0, 1.0, uv.x * 0.32 + (1.0 - uv.y) * 0.68);
    color = mix(color, uBase * 1.03, sheen * 0.20);

    vec2 c = uv - vec2(0.5, 0.45);
    color *= 1.0 - dot(c, c) * 0.05;

    // One eighth of a code value of noise: enough to break 8-bit banding on a smooth ramp,
    // invisible as grain at any resolution.
    float noise = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    color += (noise - 0.5) / 255.0;

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
