#version 120

varying vec4 vertexColor;
varying vec2 localPos;

uniform vec2 uHalfExtents;
uniform int uCircular;
uniform float uStart;
uniform float uEnd;
uniform float uBaseRadius;
uniform float uThickness;
uniform float uAmplitude;
uniform float uWavelength;
uniform float uPhase;

const float TAU = 6.28318530718;

float linearDistance(vec2 p) {
    float halfWidth = uHalfExtents.x;
    float startX = -halfWidth + uThickness * 0.5;
    float endX = halfWidth - uThickness * 0.5;
    float span = max(0.001, endX - startX);
    float fromX = mix(startX, endX, clamp(uStart, 0.0, 1.0));
    float toX = mix(startX, endX, clamp(uEnd, 0.0, 1.0));

    float nearestX = clamp(p.x, fromX, toX);
    float distanceAlong = nearestX - startX;
    float angle = distanceAlong * TAU / max(uWavelength, 0.001) - uPhase;
    float centerY = uAmplitude * sin(angle);
    float slope = uAmplitude * TAU / max(uWavelength, 0.001) * cos(angle);
    vec2 normal = normalize(vec2(-slope, 1.0));
    vec2 delta = p - vec2(nearestX, centerY);

    if (p.x < fromX || p.x > toX) {
        return length(delta) - uThickness * 0.5;
    }
    return abs(dot(delta, normal)) - uThickness * 0.5;
}

vec2 circlePoint(float angle, float distanceAlong) {
    float wave = uAmplitude * sin(distanceAlong * TAU / max(uWavelength, 0.001) - uPhase);
    float radius = uBaseRadius + wave;
    return vec2(cos(angle), sin(angle)) * radius;
}

float circularDistance(vec2 p) {
    float startAngle = clamp(uStart, 0.0, 1.0) * TAU - 1.57079632679;
    float sweep = clamp(uEnd, 0.0, 1.0) * TAU;
    float angle = atan(p.y, p.x);
    float fromStart = mod(angle - startAngle + TAU, TAU);
    float distanceAlong = min(fromStart, sweep) * uBaseRadius;
    float pathRadius = uBaseRadius + uAmplitude * sin(
            distanceAlong * TAU / max(uWavelength, 0.001) - uPhase);
    float radialDistance = abs(length(p) - pathRadius) - uThickness * 0.5;

    if (sweep >= TAU - 0.001) {
        return radialDistance;
    }

    vec2 startPoint = circlePoint(startAngle, 0.0);
    vec2 endPoint = circlePoint(startAngle + sweep, sweep * uBaseRadius);
    float capDistance = min(length(p - startPoint), length(p - endPoint)) - uThickness * 0.5;
    return fromStart <= sweep ? min(radialDistance, capDistance) : capDistance;
}

void main() {
    float distance = uCircular == 1 ? circularDistance(localPos) : linearDistance(localPos);
    float edge = max(fwidth(distance), 0.75) * 0.5;
    float coverage = 1.0 - smoothstep(-edge, edge, distance);
    vec4 color = vertexColor;
    color.a *= coverage;
    if (color.a <= 0.0) {
        discard;
    }
    gl_FragColor = color;
}
