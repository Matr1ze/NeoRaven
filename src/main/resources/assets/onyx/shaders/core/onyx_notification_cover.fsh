#version 120
uniform sampler2D Sampler0;
uniform float uAspect;
uniform float uAlpha;
uniform vec3 uSeed;
uniform vec3 uDark;
varying vec2 uv;

void main() {
    vec2 coverUV = (uv - 0.5) * vec2(uAspect, 1.0) / 0.96 + 0.5;
    vec2 edge = abs(coverUV - 0.5);
    float mask = (1.0 - smoothstep(0.39, 0.50, edge.x))
               * (1.0 - smoothstep(0.39, 0.50, edge.y));
    vec3 background = mix(uSeed.rgb, uDark.rgb, uv.y * 0.8);
    vec3 art = texture2D(Sampler0, clamp(coverUV, 0.0, 1.0)).rgb;
    gl_FragColor = vec4(mix(background, art, mask * 0.92), uAlpha);
}
