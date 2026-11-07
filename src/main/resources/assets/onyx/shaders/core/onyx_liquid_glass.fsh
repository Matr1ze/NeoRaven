#version 120

varying vec4 vertexColor;
varying vec2 localPos;

uniform sampler2D Sampler0;
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

    // Slow, low-amplitude ripples make the rim feel liquid without moving the text.
    float ripple = sin(screenUv.y * 96.0 + uTime * 1.35)
            * sin(screenUv.x * 74.0 - uTime * 1.08);
    float shift = bevel * 0.82 * bend + ripple * 2.2 * edge;

    // A tiny chromatic split sells the edge lens; the centre stays perfectly sharp.
    float dispersion = 0.012;
    vec3 colour;
    colour.r = texture2D(Sampler0, screenUv + inward * shift * (1.0 + dispersion)).r;
    colour.g = texture2D(Sampler0, screenUv + inward * shift).g;
    colour.b = texture2D(Sampler0, screenUv + inward * shift * (1.0 - dispersion)).b;

    float shimmer = 0.5 + 0.5 * sin((screenUv.x + screenUv.y) * 34.0 + uTime * 0.9);
    colour += (shimmer - 0.5) * 0.018;

    colour = mix(colour, vertexColor.rgb, uTint);

    // A soft counter-light along the convex rim, strongest on the upper-left.
    float rim = smoothstep(0.58, 1.0, edge);
    float facing = max(dot(normal, normalize(vec2(-0.55, 0.83))), 0.0);
    colour += vec3(0.09, 0.105, 0.13) * rim * (0.35 + 0.65 * facing);

    gl_FragColor = vec4(min(colour, vec3(1.0)), mask * vertexColor.a);
}
