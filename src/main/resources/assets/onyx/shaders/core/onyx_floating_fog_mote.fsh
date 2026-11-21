#version 120

uniform vec3 uColor;
varying vec2 uv;
varying float opacity;

void main() {
    vec2 point = abs(uv * 2.0 - 1.0);
    float glow = 1.0 - smoothstep(0.12, 0.98, length(point));
    float core = 1.0 - smoothstep(0.16, 0.39, max(point.x, point.y));
    gl_FragColor = vec4(mix(uColor, vec3(0.98, 0.99, 1.0), 0.72),
                        opacity * (0.36 * glow * glow + 0.64 * core));
}
