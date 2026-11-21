#version 120

uniform sampler2D uTexture;
uniform vec2 uSigma;
uniform vec4 uBounds;
varying vec4 vertexColor;
varying vec2 uv;

void main() {
    float coverage = 0.0;
    float total = 0.0;
    // Integrate each quadrature cell with the atlas mip chain. Without this prefilter, wide
    // kernels sample thin strokes as separate copies, producing a visible grid instead of blur.
    float footprint = max(length(dFdx(uv)), length(dFdy(uv)));
    float cell = max(uSigma.x, uSigma.y) * 0.5;
    float bias = max(0.0, log2(max(1.0, cell / max(footprint, 0.0000001))));
    // A normalized 2D Gaussian over +/-3 sigma. Bounds make adjacent atlas glyphs transparent,
    // including the extra quad padding used to keep the Gaussian tail from being clipped.
    for (int y = -6; y <= 6; y++) {
        for (int x = -6; x <= 6; x++) {
            vec2 p = vec2(float(x), float(y)) * 0.5;
            float weight = exp(-0.5 * dot(p, p));
            vec2 sampleUV = uv + p * uSigma;
            float inside = step(uBounds.x, sampleUV.x) * step(uBounds.y, sampleUV.y)
                         * step(sampleUV.x, uBounds.z) * step(sampleUV.y, uBounds.w);
            coverage += texture2D(uTexture, clamp(sampleUV, uBounds.xy, uBounds.zw), bias).a * inside * weight;
            total += weight;
        }
    }
    gl_FragColor = vec4(vertexColor.rgb, vertexColor.a * coverage / total);
}
