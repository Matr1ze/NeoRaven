#version 120

varying vec4 vertexColor;
varying vec2 localPos;
uniform vec2 uHalfExtents;
uniform float uRadius;
uniform float uSigma;
uniform float uOffset;
uniform float uPixel;

// Normal CDF: integrates the Gaussian across each horizontal slice of the rounded box.
vec2 normalCDF(vec2 x) {
    vec2 a = abs(x) * 0.70710678;
    vec2 t = 1.0 / (1.0 + 0.3275911 * a);
    vec2 erf = 1.0 - (((((1.061405429 * t - 1.453152027) * t)
        + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-a * a);
    return 0.5 + 0.5 * sign(x) * erf;
}

float blurredBox(vec2 p) {
    // Integrate only the intersection of the box and the Gaussian's useful vertical range.
    float lo = max(-uHalfExtents.y, p.y - 4.0 * uSigma);
    float hi = min(uHalfExtents.y, p.y + 4.0 * uSigma);
    float stepY = max(hi - lo, 0.0) / 12.0;
    float coverage = 0.0;
    for (int i = 0; i < 12; i++) {
        float y = lo + (float(i) + 0.5) * stepY;
        float cornerY = max(abs(y) - (uHalfExtents.y - uRadius), 0.0);
        float halfWidth = uHalfExtents.x - uRadius
            + sqrt(max(uRadius * uRadius - cornerY * cornerY, 0.0));
        vec2 integral = normalCDF((vec2(halfWidth, -halfWidth) - p.x) / uSigma);
        float dy = (y - p.y) / uSigma;
        coverage += (integral.x - integral.y) * exp(-0.5 * dy * dy)
            * 0.39894228 * stepY / uSigma;
    }
    return clamp(coverage, 0.0, 1.0);
}

void main() {
    vec2 q = abs(localPos) - uHalfExtents + uRadius;
    float distance = min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - uRadius;
    float edge = max(max(fwidth(distance), uPixel * 1.5), 0.0001) * 0.5;
    // Hollow out the original panel, so translucent glass retains its own tint/opacity.
    float outside = smoothstep(-edge, edge, distance);
    if (outside <= 0.0) discard;
    float coverage = blurredBox(localPos - vec2(0.0, uOffset));
    gl_FragColor = vec4(vertexColor.rgb, vertexColor.a * coverage * outside);
}
