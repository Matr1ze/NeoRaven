#version 120

varying vec4 vertexColor;
varying vec2 localPos;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 uHalfExtents;
uniform float uRadius;
uniform float uPixel;
uniform vec2 uScreenSize;
uniform float uTint;
uniform float uTime;
uniform float uBevel;

float roundedBoxDistance(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

vec3 backdrop(vec2 uv, float clarity) {
    vec3 blurred = texture2D(Sampler0, uv).rgb;
    vec3 sharp = texture2D(Sampler1, uv).rgb;
    return mix(blurred, sharp, clarity);
}

void main() {
    vec2 halfSize = uHalfExtents;
    vec2 p = localPos;
    float radius = min(uRadius, min(halfSize.x, halfSize.y));
    float distanceToEdge = roundedBoxDistance(p, halfSize, radius);

    float aa = max(fwidth(distanceToEdge), uPixel * 1.5) * 0.5;
    float mask = 1.0 - smoothstep(-aa, aa, distanceToEdge);
    if (mask <= 0.0) {
        discard;
    }

    vec2 offset = vec2(0.75, 0.0);
    vec2 normal = vec2(
        roundedBoxDistance(p + offset.xy, halfSize, radius) - roundedBoxDistance(p - offset.xy, halfSize, radius),
        roundedBoxDistance(p + offset.yx, halfSize, radius) - roundedBoxDistance(p - offset.yx, halfSize, radius)
    );
    float normalLength = length(normal);
    normal = normalLength > 0.0001 ? normal / normalLength : vec2(0.0, -1.0);

    float bevel = max(uBevel, 1.0);
    float edge = clamp(1.0 + distanceToEdge / bevel, 0.0, 1.0);
    float bend = edge * edge * (3.0 - 2.0 * edge);
    bend *= bend;

    vec2 screenUv = gl_FragCoord.xy / uScreenSize;
    vec2 inward = vec2(-normal.x, normal.y) / uScreenSize;

    // The centre is mostly the blurred scene; toward the rim the sharp scene is pulled inward,
    // producing the clear lens instead of a flat frosted rectangle.
    float clarity = 0.30 + 0.70 * smoothstep(0.0, 0.86, edge);
    float ripple = sin(screenUv.y * 96.0 + uTime * 1.35)
            * sin(screenUv.x * 74.0 - uTime * 1.08);
    float shift = bevel * 0.92 * bend + ripple * 2.8 * edge;
    float dispersion = 0.014;

    vec3 colour;
    colour.r = backdrop(screenUv + inward * shift * (1.0 + dispersion), clarity).r;
    colour.g = backdrop(screenUv + inward * shift, clarity).g;
    colour.b = backdrop(screenUv + inward * shift * (1.0 - dispersion), clarity).b;

    colour = mix(colour, vertexColor.rgb, uTint);

    // Bright liquid rim with a moving highlight along the upper-left edge.
    float rim = smoothstep(0.60, 1.0, edge);
    float facing = max(dot(normal, normalize(vec2(-0.55, 0.83))), 0.0);
    float pulse = 0.82 + 0.18 * sin(uTime * 1.6 + screenUv.x * 8.0);
    colour += vec3(0.14, 0.16, 0.19) * rim * (0.30 + 0.70 * facing) * pulse;

    gl_FragColor = vec4(min(colour, vec3(1.0)), mask * vertexColor.a);
}
