#version 120
uniform sampler2D uTex;
uniform float uPeriod;
uniform vec4 uDot;
uniform float uAmount;
varying vec2 vUv;
varying vec4 vColor;
void main() {
    vec4 t = texture2D(uTex, vUv);
    if (t.a < 0.004) discard;
    float shade = clamp((1.0 - t.r) * uAmount, 0.0, 1.0);
    vec2 p = gl_FragCoord.xy / uPeriod;
    vec2 q = vec2(p.x + p.y, p.x - p.y) * 0.70710678;
    float d = length(fract(q) - 0.5);
    float r = sqrt(shade) * 0.62;
    float cov = clamp((r - d) * uPeriod + 0.5, 0.0, 1.0) * step(0.001, shade);
    vec3 col = mix(vColor.rgb, uDot.rgb, cov * uDot.a);
    gl_FragColor = vec4(col, t.a * vColor.a);
}
