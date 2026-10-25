#version 120

// GLSL 120 port of hand_glow_expand.fsh. The step count is a uniform there; here the loop runs to
// a constant bound and breaks, because GLSL 120 will not take a uniform bound.

uniform sampler2D Sampler0;
uniform vec2 uSourceSize;
uniform vec2 uDirection;
uniform float uPadding;

varying vec2 texCoord;

const int MAX_STEPS = 24;

void main() {
    vec2 texel = uDirection / max(uSourceSize, vec2(1.0));
    vec4 widest = texture2D(Sampler0, texCoord);
    int steps = int(clamp(ceil(uPadding), 0.0, float(MAX_STEPS)));

    for (int i = 1; i <= MAX_STEPS; i++) {
        if (i > steps) break;

        vec4 forward = texture2D(Sampler0, texCoord + texel * float(i));
        vec4 backward = texture2D(Sampler0, texCoord - texel * float(i));

        if (forward.a > widest.a) widest = forward;
        if (backward.a > widest.a) widest = backward;
    }

    gl_FragColor = widest;
}
