#version 120

varying vec2 uv;
uniform sampler2D DepthSampler, ReflectionSampler;
uniform vec2 uDepthProjection, uReflectionSize, uPixel;

float eyeDepth(vec2 coord) {
    float depth = texture2D(DepthSampler, coord).r;
    return uDepthProjection.y / min(-0.000001, depth * 2.0 - 1.0 + uDepthProjection.x);
}

void accumulate(vec2 pixel, float bilinear, float reference, inout vec4 reflection, inout float total) {
    vec2 coord = (clamp(pixel, vec2(0.0), uReflectionSize - 1.0) + 0.5) / uReflectionSize;
    vec2 depthCoord = (floor(coord / uPixel + 0.001) + 0.5) * uPixel;
    float delta = abs(eyeDepth(depthCoord) - reference);
    float weight = bilinear * (1.0 - smoothstep(0.025, max(0.15, reference * 0.023), delta));
    reflection += texture2D(ReflectionSampler, coord) * weight;
    total += weight;
}

void main() {
    float reference = eyeDepth(uv);
    if (reference > 48.0) discard;
    vec2 pixel = uv * uReflectionSize - 0.5;
    vec2 base = floor(pixel);
    vec2 f = fract(pixel);
    vec4 reflection = vec4(0.0);
    float total = 0.0;
    accumulate(base, (1.0 - f.x) * (1.0 - f.y), reference, reflection, total);
    accumulate(base + vec2(1.0, 0.0), f.x * (1.0 - f.y), reference, reflection, total);
    accumulate(base + vec2(0.0, 1.0), (1.0 - f.x) * f.y, reference, reflection, total);
    accumulate(base + 1.0, f.x * f.y, reference, reflection, total);
    gl_FragColor = reflection / max(0.25, total);
}
