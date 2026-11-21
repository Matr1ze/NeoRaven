#version 120
uniform sampler2D mask;
uniform vec2 texel;
uniform vec3 tint;
uniform float radius;
varying vec2 texCoord;

void main() {
    if (texture2D(mask, texCoord).a > 0.5) discard;
    float haze = 0.0;
    float rim = 0.0;
    for (int spoke = 0; spoke < 10; ++spoke) {
        float angle = float(spoke) * 0.62831853;
        vec2 stepUV = vec2(cos(angle), sin(angle)) * texel;
        rim = max(rim, texture2D(mask, texCoord + stepUV * 1.5).a);
        for (int ring = 1; ring <= 4; ++ring) {
            float reach = float(ring) * 0.25;
            haze += texture2D(mask, texCoord + stepUV * radius * reach).a * (1.15 - reach);
        }
    }
    gl_FragColor = vec4(tint, min(0.96, rim * 0.55 + haze * 0.065));
}
