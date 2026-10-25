#version 120

varying vec4 vertexColor;
varying vec2 localPos;

uniform sampler2D Sampler0;
uniform vec2 uHalfExtents;
uniform vec4 uCornerRadii;
uniform float uPixel;
uniform vec2 uResolution;
uniform float uTint;

float roundedBoxDistance(vec2 p, vec2 halfSize, vec4 radii) {
    float top = p.x > 0.0 ? radii.y : radii.x;
    float bottom = p.x > 0.0 ? radii.z : radii.w;
    float radius = p.y > 0.0 ? bottom : top;

    vec2 q = abs(p) - halfSize + radius;

    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

void main() {
    float dist = roundedBoxDistance(localPos, uHalfExtents, uCornerRadii);
    // uPixel is one screen pixel measured on the CPU side, and the feather is held
    // to at least a pixel and a half of it: a one pixel ramp is what the maths asks
    // for and it still reads as a stepped edge on a dense display. fwidth may widen
    // it further but never narrows it, which is what keeps the corner smooth on a
    // driver that answers a derivative with nothing.
    float edge = max(max(fwidth(dist), uPixel * 1.5), 0.0001) * 0.5;
    float mask = 1.0 - smoothstep(-edge, edge, dist);

    if (mask <= 0.0) {
        discard;
    }

    vec2 screenUv = gl_FragCoord.xy / uResolution;
    vec3 blurred = texture2D(Sampler0, screenUv).rgb;

    // The blurred copy already holds everything behind the panel, so the panel's own opacity is
    // spent here, on how much of it the tint covers - not on letting the sharp scene through
    // underneath, which is what made a translucent card read as no blur at all. vertexColor.a is
    // left carrying only the fade a panel animates in and out with.
    vec4 tint = vertexColor;
    vec3 composited = mix(blurred, tint.rgb, uTint);

    gl_FragColor = vec4(composited, mask * tint.a);
}
