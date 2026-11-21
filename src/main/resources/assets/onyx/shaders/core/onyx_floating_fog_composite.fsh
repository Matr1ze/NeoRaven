#version 120

uniform sampler2D DepthSampler, FogSampler;
uniform vec2 uFogSize, uProjection;
varying vec2 uv;

float eyeDepth(vec2 coord) {
    float depth = texture2D(DepthSampler, coord).r;
    return min(192.0, abs(uProjection.y) / max(0.00001, abs(depth * 2.0 - 1.0 + uProjection.x)));
}

void accumulate(vec2 pixel, float bilinear, float reference, inout vec4 fog, inout float total) {
    vec2 coord = (clamp(pixel, vec2(0.0), uFogSize - 1.0) + 0.5) / uFogSize;
    float depth = eyeDepth(coord);
    float difference = abs(depth - reference) / max(0.45, reference * 0.032);
    float weight = bilinear * (exp(-difference) + 0.0001);
    // A subpixel foreground edge can miss every low-resolution sample. In that
    // case trim any background sample to the foreground's much shorter path.
    float path = min(1.0, reference / max(depth, 0.001));
    fog += texture2D(FogSampler, coord) * (weight * path);
    total += weight;
}

void main() {
    vec2 pixel = uv * uFogSize - 0.5;
    vec2 base = floor(pixel);
    vec2 f = fract(pixel);
    float reference = eyeDepth(uv);
    vec4 fog = vec4(0.0);
    float total = 0.0;
    accumulate(base, (1.0 - f.x) * (1.0 - f.y), reference, fog, total);
    accumulate(base + vec2(1, 0), f.x * (1.0 - f.y), reference, fog, total);
    accumulate(base + vec2(0, 1), (1.0 - f.x) * f.y, reference, fog, total);
    accumulate(base + vec2(1, 1), f.x * f.y, reference, fog, total);
    gl_FragColor = fog / total;
}
