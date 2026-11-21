#version 120
varying vec2 uv;
varying vec4 projection;
uniform sampler2D scene, geometry;
uniform vec2 pixel;
uniform vec4 controls;

vec3 viewPosition(vec2 coord) {
    float depth = texture2D(geometry, coord).r;
    float distance = projection.w / (2.0 * depth - 1.0 + projection.z);
    return vec3((coord * 2.0 - 1.0) / projection.xy, -1.0) * distance;
}

float occlusion(vec2 coord) {
    vec3 origin = viewPosition(coord);
    if (origin.z < -80.0) return 0.0;
    vec3 left = origin - viewPosition(coord - vec2(pixel.x, 0.0));
    vec3 right = viewPosition(coord + vec2(pixel.x, 0.0)) - origin;
    vec3 down = origin - viewPosition(coord - vec2(0.0, pixel.y));
    vec3 up = viewPosition(coord + vec2(0.0, pixel.y)) - origin;
    vec3 n = cross(abs(left.z) < abs(right.z) ? left : right,
                   abs(down.z) < abs(up.z) ? down : up);
    n *= inversesqrt(max(dot(n,n), 1e-16));
    // A world-space radius keeps contacts narrow in the distance.
    float radius = clamp(0.45 * projection.y / (-origin.z * pixel.y), 2.0, 42.0);
    float total = 0.0;
    for (int i = 0; i < 8; ++i) {
        float angle = (float(i) + 0.375) * 0.78539816;
        vec2 ray = vec2(cos(angle), sin(angle)) * radius * pixel;
        float horizon = 0.0;
        for (int j = 1; j <= 3; ++j) {
            vec2 sampleUV = coord + ray * (float(j*j)/9.0);
            vec3 delta = viewPosition(sampleUV) - origin;
            float d2 = dot(delta, delta);
            float elevation = dot(n, delta) * inversesqrt(max(d2, 1e-8));
            horizon = max(horizon, (elevation - 0.075) * (1.0 - smoothstep(0.04, 0.81, d2)));
        }
        total += horizon;
    }
    return total / 8.0;
}

vec3 bright(vec2 coord, float lod) {
    vec3 c = texture2D(scene, coord, lod).rgb;
    float peak = max(max(c.r,c.g),c.b);
    return c*c * smoothstep(0.38,0.91,peak);
}

vec3 bloomLayer(vec2 coord, float scale, float lod) {
    vec2 r = pixel * scale;
    return (bright(coord, lod)*4.0 + bright(coord+r,lod) + bright(coord-r,lod)
        + bright(coord+vec2(r.x,-r.y),lod) + bright(coord+vec2(-r.x,r.y),lod)) * 0.125;
}

void main() {
    vec2 q = uv * 2.0 - 1.0;
    float edge = dot(q,q);
    vec2 coord = 0.5 + 0.5*q*(1.0 + controls.z*0.023*(edge-2.0));
    vec3 color = texture2D(scene,coord).rgb;
    if (controls.z > 0.0) {
        vec2 shift = pixel*q*edge*controls.z*0.65;
        color.r = texture2D(scene,coord+shift).r;
        color.b = texture2D(scene,coord-shift).b;
    }
    // Work in approximately linear light until the final display conversion.
    color *= color;
    if (controls.x > 0.0) color *= 1.0 - controls.x * min(0.72,1.65*occlusion(coord));
    if (controls.y > 0.0) {
        float scale = clamp(1.0/(pixel.y*900.0),0.6,2.5);
        float bias = log2(scale);
        vec3 halo = 0.40*bloomLayer(coord,2.2*scale,1.0+bias)
                  + 0.38*bloomLayer(coord,7.0*scale,2.8+bias)
                  + 0.22*bloomLayer(coord,19.0*scale,4.5+bias);
        color += controls.y * halo * 0.55;
    }
    color *= controls.w * controls.w;
    // Smooth highlight shoulder, preserving the ratios between color channels.
    float peak = max(color.r,max(color.g,color.b));
    float shoulder = max(peak - 0.68,0.0);
    color *= (min(peak,0.68) + shoulder/(1.0+3.125*shoulder)) / max(peak,0.000001);
    gl_FragColor = vec4(sqrt(max(color,vec3(0.0))),1.0);
}
