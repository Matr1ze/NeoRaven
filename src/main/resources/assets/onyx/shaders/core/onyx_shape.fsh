#version 120

varying vec4 vertexColor;
varying vec2 localPos;

uniform vec2 uHalfExtents;
uniform vec4 uCornerRadii;
uniform float uPixel;
uniform float uSoftness;

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

    float alpha;
    if (uSoftness > 0.0) {
        alpha = 1.0 - smoothstep(-uSoftness, uSoftness, dist);
    } else if (uSoftness < 0.0) {
        float thickness = -uSoftness;
        float ring = abs(dist + thickness * 0.5) - thickness * 0.5;
        alpha = 1.0 - smoothstep(-edge, edge, ring);
    } else {
        alpha = 1.0 - smoothstep(-edge, edge, dist);
    }

    vec4 color = vertexColor;
    color.a *= alpha;

    if (color.a <= 0.0) {
        discard;
    }

    gl_FragColor = color;
}
