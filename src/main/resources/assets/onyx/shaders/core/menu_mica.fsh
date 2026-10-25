#version 120

// Windows 11 "Mica" backdrop for the title screens: the wallpaper pushed through a wide,
// low-frequency blur, then desaturated and tinted with the theme surface - Mica keeps the colour
// of the desktop but drops every edge, so the artwork reads as soft colour fields behind the UI.
//
// One pass, sixteen taps on a golden-angle spiral (a cheap large-kernel blur with no FBO and no
// second texture), plus a hint of noise to stop the smooth ramp from banding.
varying vec2 uv;

uniform sampler2D Sampler0;
uniform vec2 uTexel;      // one texel of the wallpaper
uniform float uRadius;    // blur radius in texels
uniform vec3 uTint;       // surface colour the wallpaper is tinted towards
uniform float uTintAmount;
uniform float uDesaturate;

vec3 sampleBlur(void) {
    vec3 sum = texture2D(Sampler0, uv).rgb;
    float weight = 1.0;
    // Two rings of the spiral, 16 taps total; the weights fall off with distance so the kernel
    // stays smooth instead of looking like a box blur.
    for (int i = 0; i < 16; i++) {
        float fi = float(i);
        float angle = fi * 2.39996323;                 // golden angle, even coverage
        float ring = sqrt((fi + 0.5) / 16.0);
        vec2 offset = vec2(cos(angle), sin(angle)) * ring * uRadius * uTexel;
        float tapWeight = 1.0 - ring * 0.45;
        sum += texture2D(Sampler0, uv + offset).rgb * tapWeight;
        weight += tapWeight;
    }
    return sum / weight;
}

void main() {
    vec3 color = sampleBlur();

    // Mica desaturates first, then tints: the tint has to stay the dominant colour.
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(color, vec3(luma), uDesaturate);
    color = mix(color, uTint, uTintAmount);

    float noise = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    color += (noise - 0.5) / 255.0;

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}