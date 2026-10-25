#version 120

// GLSL 120 port of onyx_wings_aurora.fsh. The Fabric build smuggles time and screen size in as a
// flat vertex attribute because the GUI pass cannot reach custom uniforms; the world pass here can,
// so they are ordinary uniforms.

uniform float uTime;
uniform vec2 uResolution;

varying vec4 vertexColor;

mat2 rotate2D(float r) {
    return mat2(cos(r), sin(r), -sin(r), cos(r));
}

void main() {
    if (vertexColor.a <= 0.0) {
        discard;
    }

    float time = uTime;
    vec2 resolution = uResolution;

    vec2 uv = 0.33 * (gl_FragCoord.xy - 0.5 * resolution.xy) / resolution.y;
    vec3 col = vec3(0.0);
    float t = time;

    vec2 n = vec2(0.0);
    vec2 q = vec2(0.0);
    vec2 p = uv * 2.5;
    float d = dot(p, p);
    float S = 16.0;
    float a = 0.0;
    mat2 m = rotate2D(15.0 + (sin(d * 0.1 + time * 0.1) * 2.0));

    for (float j = 0.0; j < 6.0; j++) {
        p *= m * 1.05;
        n *= m;
        q = p * S + t * 2.5 + sin((t + j)) * 0.0018 + 3.0 * j - 1.25 * n;
        a += dot(cos(q) / S, vec2(0.15));
        n -= sin(q);
        S *= 1.5;
    }

    vec3 palette = vec3(2.5, 1.9, 3.5) * (a + 0.182) + 10.0 * a;
    // The raw noise dips to ~0 across large patches, which used to read as solid black membrane
    // instead of the wing's own colour; floor it so the base colour always shows through.
    float intensity = max(dot(palette, vec3(0.2126, 0.7152, 0.0722)), 0.4);
    float bloom = smoothstep(1.0, 3.0, intensity) * 0.4;

    col = mix(vertexColor.rgb, vec3(1.0), bloom) * intensity;

    gl_FragColor = vec4(col, vertexColor.a);
}
