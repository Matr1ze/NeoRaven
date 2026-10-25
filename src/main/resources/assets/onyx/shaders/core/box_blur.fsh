#version 120

varying vec2 uv;

uniform sampler2D Sampler0;
uniform vec2 uBlurDir;
uniform vec2 uResolution;
uniform float uRadius;

void main() {
    vec2 step = uBlurDir / uResolution;

    vec4 sum = vec4(0.0);
    float weight = 0.0;

    for (int i = -8; i <= 8; i++) {
        float offset = float(i);

        if (abs(offset) > uRadius) continue;

        sum += texture2D(Sampler0, uv + step * offset);
        weight += 1.0;
    }

    gl_FragColor = sum / weight;
}
