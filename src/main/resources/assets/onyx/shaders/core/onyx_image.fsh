#version 120

uniform sampler2D Sampler0;
uniform vec2 uHalfExtents;
uniform vec4 uCornerRadii;
uniform float uPixel;

varying vec4 vertexColor;
varying vec2 localPos;
varying vec2 uv;

float roundedBoxDistance(vec2 p, vec2 halfSize, vec4 radii) {
    float top = p.x > 0.0 ? radii.y : radii.x;
    float bottom = p.x > 0.0 ? radii.z : radii.w;
    float radius = p.y > 0.0 ? bottom : top;

    vec2 q = abs(p) - halfSize + radius;

    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

void main() {
    float dist = roundedBoxDistance(localPos, uHalfExtents, uCornerRadii);
    float edge = max(max(fwidth(dist), uPixel * 1.5), 0.0001) * 0.5;
    float mask = 1.0 - smoothstep(-edge, edge, dist);
    if (mask <= 0.0) {
        discard;
    }

    vec4 textureColor = texture2D(Sampler0, uv);
    gl_FragColor = vec4(textureColor.rgb * vertexColor.rgb, textureColor.a * vertexColor.a * mask);
}
