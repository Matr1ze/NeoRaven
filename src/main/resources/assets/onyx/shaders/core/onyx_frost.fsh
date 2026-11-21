#version 120

// LiquidGlassShader v2 Tinted, Copyright (c) 2026 Jack (Jacquesqwq), MIT.
// Source: https://github.com/Jacquesqwq/LiquidGlassShader/blob/4bf62de7c89c285d997cd457daac8ee50837a738/liquidGlassV2Tinted.frag
// License: META-INF/licenses/LiquidGlassShader.txt

varying vec4 vertexColor;
varying vec2 localPos;

uniform sampler2D Sampler0;
uniform vec2 uHalfExtents;
uniform vec4 uCornerRadii;
uniform float uPixel;
uniform vec2 uResolution;
uniform float uTint;
uniform int uLiquidGlass;
uniform float uBackdropOffset;
uniform vec2 uJoinedEdges;

const vec3 LUMA = vec3(0.299, 0.587, 0.114);
const float M_E = 2.718281828459045;

float roundedBoxDistance(vec2 p, vec2 halfSize, vec4 radii) {
    float top = p.x > 0.0 ? radii.y : radii.x;
    float bottom = p.x > 0.0 ? radii.z : radii.w;
    float radius = p.y > 0.0 ? bottom : top;

    vec2 q = abs(p) - halfSize + radius;

    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

void main() {
    float maskDistance = roundedBoxDistance(localPos, uHalfExtents, uCornerRadii);
    float feather = max(max(fwidth(maskDistance), uPixel * 1.5), 0.0001) * 0.5;
    float mask = 1.0 - smoothstep(-feather, feather, maskDistance);
    vec2 backdropPosition = gl_FragCoord.xy + vec2(0.0, uBackdropOffset);

    if (uLiquidGlass == 0) {
        if (mask <= 0.0) discard;
        vec3 backdrop = texture2D(Sampler0, backdropPosition / uResolution).rgb;
        vec3 tint = mix(backdrop, vertexColor.rgb, uTint * vertexColor.a);
        gl_FragColor = vec4(tint, mask * vertexColor.a);
        return;
    }

    // Continue the optical surface across joined ArrayList rows. The existing mask still
    // owns silhouette and coverage. The rim must not bend toward an internal join.
    float halfHeight = uHalfExtents.y - (uJoinedEdges.x + uJoinedEdges.y) * uPixel;
    float rimWidth = clamp(min(uHalfExtents.x, halfHeight) * 0.65, 0.001, 9.0);
    vec2 opticalCenter = vec2(0.0, (uJoinedEdges.x - uJoinedEdges.y) * rimWidth * 0.5);
    vec2 opticalSize = uHalfExtents + vec2(0.0, (uJoinedEdges.x + uJoinedEdges.y) * rimWidth * 0.5);
    float opticalDistance = roundedBoxDistance(localPos + opticalCenter, opticalSize, uCornerRadii);
    vec2 gradient = vec2(dFdx(opticalDistance), dFdy(opticalDistance));
    float unitsPerPixel = max(length(gradient), 0.0001);
    vec2 normal = gradient / unitsPerPixel;

    if (mask <= 0.0) discard;

    float depth = clamp(-opticalDistance / rimWidth, 0.0, 1.0);
    float fresnel = pow(1.0 - depth, 3.0);
    float refraction = pow(1.0 - 2.3 * pow(5.2 * M_E, -6.9 * depth - 0.7), 0.75);
    // Use the boundary normal instead of the vector from the panel's centre. Long pills
    // and docked panels then bend the scene a few pixels instead of dragging it sideways.
    float lensPixels = min(36.0, 4.2 * rimWidth / unitsPerPixel);
    vec2 bend = -normal * (1.0 - refraction) * lensPixels;
    vec2 uv = (backdropPosition + bend) / uResolution;
    vec2 chroma = normal * fresnel * 1.5 / uResolution;
    vec3 scene = vec3(texture2D(Sampler0, uv + chroma).r,
                      texture2D(Sampler0, uv).g,
                      texture2D(Sampler0, uv - chroma).b);

    float micro = sin(gl_FragCoord.x * 0.015 + gl_FragCoord.y * 0.008)
                * sin(gl_FragCoord.y * 0.012 - gl_FragCoord.x * 0.006);
    float luma = dot(scene, LUMA);
    scene += vec3((micro - 0.5) * 0.03 * 0.22);
    scene = mix(vec3(luma), scene, mix(0.55, 0.85, luma));
    vec3 color = mix(scene, vertexColor.rgb, uTint * (1.0 - luma * 0.3));
    float directional = pow(0.5 + 0.5 * dot(normal, vec2(-0.5, 0.8660254)), 2.0);
    color += vec3(0.92, 0.96, 1.0) * fresnel * mix(0.035, 0.33, directional) * (1.0 - luma * 0.55);

    // UI uses straight-alpha blending, with fade carried by the existing alpha stack.
    gl_FragColor = vec4(color, mask * vertexColor.a);
}
