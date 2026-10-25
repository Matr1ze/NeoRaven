#version 120

// GLSL 120 port of hand_glow_smooth.fsh: a separable gaussian with linear-sampled tap pairs.

uniform sampler2D Sampler0;
uniform vec2 uSourceSize;
uniform vec2 uDirection;
uniform float uRadius;

varying vec2 texCoord;

const int MAX_TAPS = 16;

void main() {
    vec4 center = texture2D(Sampler0, texCoord);

    if (uRadius < 0.05) {
        gl_FragColor = center;
        return;
    }

    float sigma = max(uRadius * 0.5, 0.5);
    float denominator = 2.0 * sigma * sigma;
    vec2 texel = uDirection / max(uSourceSize, vec2(1.0));
    int taps = int(clamp(ceil(uRadius * 0.5), 1.0, float(MAX_TAPS)));

    vec4 sum = center;
    float weightSum = 1.0;

    for (int i = 0; i < MAX_TAPS; i++) {
        if (i >= taps) break;

        float inner = float(2 * i + 1);
        float outer = inner + 1.0;
        float innerWeight = exp(-(inner * inner) / denominator);
        float outerWeight = exp(-(outer * outer) / denominator);
        float weight = innerWeight + outerWeight;
        float offset = (inner * innerWeight + outer * outerWeight) / weight;

        sum += (texture2D(Sampler0, texCoord + texel * offset)
              + texture2D(Sampler0, texCoord - texel * offset)) * weight;
        weightSum += 2.0 * weight;
    }

    gl_FragColor = sum / weightSum;
}
