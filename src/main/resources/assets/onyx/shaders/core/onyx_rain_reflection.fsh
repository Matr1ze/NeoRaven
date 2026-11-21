#version 120

varying vec2 uv;
uniform sampler2D DepthSampler, SceneSampler, ExposureSampler;
uniform vec2 uPixel, uDepthProjection, uNoiseOrigin, uRain;
uniform vec4 uFrustum, uExposure;
uniform vec3 uWorldX, uWorldY, uWorldZ, uSky;

float eyeDepth(float depth) {
    return uDepthProjection.y / min(-0.000001, depth * 2.0 - 1.0 + uDepthProjection.x);
}

vec3 viewPoint(vec2 coord, float depth) {
    vec2 xy = (coord * 2.0 - 1.0 + uFrustum.zw) * depth / uFrustum.xy;
    return vec3(xy, -depth);
}

vec2 project(vec3 position) {
    return (position.xy * uFrustum.xy / -position.z - uFrustum.zw) * 0.5 + 0.5;
}

vec3 groundNormal(vec2 coord, vec3 center, float depth) {
    vec2 dx = vec2(uPixel.x, 0.0);
    vec2 dy = vec2(0.0, uPixel.y);
    float left = eyeDepth(texture2D(DepthSampler, coord - dx).r);
    float right = eyeDepth(texture2D(DepthSampler, coord + dx).r);
    float down = eyeDepth(texture2D(DepthSampler, coord - dy).r);
    float up = eyeDepth(texture2D(DepthSampler, coord + dy).r);
    vec3 tangent = abs(right - depth) < abs(left - depth)
        ? viewPoint(coord + dx, right) - center : center - viewPoint(coord - dx, left);
    vec3 bitangent = abs(up - depth) < abs(down - depth)
        ? viewPoint(coord + dy, up) - center : center - viewPoint(coord - dy, down);
    return normalize(cross(tangent, bitangent));
}

float exposedAt(vec2 center, float y) {
    vec4 exposure = texture2D(ExposureSampler, center / uExposure.w);
    // The red channel stores the Y of the rain-blocking block. Include slab tops.
    float height = y - floor(exposure.r * 255.0 + 0.5);
    return exposure.a * smoothstep(0.43, 0.55, height) * (1.0 - smoothstep(1.15, 1.52, height));
}

float exposureFade(vec3 column, vec2 center) {
    // Measure the distance to dry neighboring squares. Only the wet side feathers,
    // so reflections never spill onto water, up a ledge, or under a roof.
    vec2 offset = column.xz - center;
    float feather = 1.0;
    for (int z = -1; z <= 1; z++) {
        for (int x = -1; x <= 1; x++) {
            if (x == 0 && z == 0) continue;
            vec2 neighbor = vec2(float(x), float(z));
            float distanceToCell = length(max(abs(offset - neighbor) - 0.5, 0.0));
            if (distanceToCell < 0.85) {
                float neighborExposure = exposedAt(center + neighbor, column.y);
                feather = min(feather, mix(smoothstep(0.02, 0.85, distanceToCell), 1.0, neighborExposure));
            }
        }
    }
    return feather;
}

float hash(vec2 cell) {
    vec3 p = fract(vec3(cell.xyx) * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec2 p, float period) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(mod(cell, period));
    float b = hash(mod(cell + vec2(1.0, 0.0), period));
    float c = hash(mod(cell + vec2(0.0, 1.0), period));
    float d = hash(mod(cell + 1.0, period));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

vec2 ripple(vec2 worldXZ) {
    vec2 cells = worldXZ * 1.5;
    vec2 cell = mod(floor(cells), 1536.0);
    float seed = hash(cell);
    vec2 delta = fract(cells) - (0.30 + 0.40 * vec2(seed, hash(cell.yx + 29.0)));
    float distanceToDrop = length(delta);
    float phase = fract(uRain.y * 71.0 + seed);
    float front = distanceToDrop - 0.47 * phase;
    float wave = cos(front * 74.0) * exp(-abs(front) * 35.0)
        * sin(phase * 3.14159265) * (1.0 - smoothstep(0.24, 0.44, distanceToDrop));
    return delta / max(distanceToDrop, 0.001) * wave * 0.016;
}

// March toward an intersection from the front of the depth buffer, then refine the crossing.
// A finite thickness test rejects false intersections across depth discontinuities.
vec3 traceReflection(vec3 start, vec3 direction, vec3 worldUp) {
    float previousT = 0.0;
    bool wasInFront = false;
    for (int i = 0; i < 26; i++) {
        float s = float(i) / 25.0;
        float t = 0.12 + 34.0 * s * (0.18 + 0.82 * s);
        vec3 samplePoint = start + direction * t;
        if (samplePoint.z > -0.07) break;
        vec2 coord = project(samplePoint);
        if (any(lessThan(coord, uPixel)) || any(greaterThan(coord, 1.0 - uPixel))) break;
        float rawDepth = texture2D(DepthSampler, coord).r;
        float separation = -samplePoint.z - eyeDepth(rawDepth);
        bool inFront = rawDepth >= 0.999999 || separation < 0.0;
        if (!inFront && wasInFront) {
            float lo = previousT;
            float hi = t;
            for (int j = 0; j < 5; j++) {
                float mid = (lo + hi) * 0.5;
                vec3 p = start + direction * mid;
                if (-p.z < eyeDepth(texture2D(DepthSampler, project(p)).r)) lo = mid;
                else hi = mid;
            }
            vec3 hit = start + direction * hi;
            vec2 hitUV = project(hit);
            float hitDepth = eyeDepth(texture2D(DepthSampler, hitUV).r);
            float overlap = -hit.z - hitDepth;
            float rise = dot(viewPoint(hitUV, hitDepth) - start, worldUp);
            float thickness = clamp(hitDepth * 0.009, 0.16, 0.5);
            if (overlap < thickness && rise > 0.02) {
                vec2 edge = min(hitUV, 1.0 - hitUV);
                float confidence = smoothstep(0.0, 0.14, min(edge.x, edge.y))
                    * (1.0 - smoothstep(thickness * 0.5, thickness, overlap))
                    * smoothstep(0.02, 0.30, rise)
                    * (1.0 - smoothstep(24.0, 34.0, hi));
                return vec3(hitUV, confidence);
            }
        }
        previousT = t;
        wasInFront = inFront;
    }
    return vec3(0.0);
}

void main() {
    gl_FragColor = vec4(0.0);
    // Half-resolution samples sit on depth texel boundaries at even window sizes.
    // Reconstruct at the same texel center that supplied the depth.
    vec2 coord = (floor(uv / uPixel + 0.001) + 0.5) * uPixel;
    float rawDepth = texture2D(DepthSampler, coord).r;
    if (rawDepth >= 0.999999) return;
    float depth = eyeDepth(rawDepth);
    vec3 position = viewPoint(coord, depth);
    float range = length(position);
    if (range > 47.0) return;
    vec3 worldOffset = vec3(dot(uWorldX, position), dot(uWorldY, position), dot(uWorldZ, position));
    vec3 column = worldOffset + uExposure.xyz;
    vec2 exposureUV = column.xz / uExposure.w;
    if (any(lessThan(exposureUV, vec2(0.0))) || any(greaterThanEqual(exposureUV, vec2(1.0)))) return;
    vec2 center = floor(column.xz) + 0.5;
    float exposure = exposedAt(center, column.y);
    if (exposure <= 0.0) return;
    vec3 worldUp = normalize(uWorldY);
    float slope = smoothstep(0.92, 0.99, dot(worldUp, groundNormal(coord, position, depth)));
    if (slope <= 0.0) return;
    float wetness = slope * exposure * exposureFade(column, center) * smoothstep(0.01, 1.0, uRain.x)
        * (1.0 - smoothstep(36.0, 47.0, range));
    if (wetness <= 0.0) return;

    vec2 worldXZ = mod(uNoiseOrigin + worldOffset.xz, 1024.0);
    float shape = noise(worldXZ * 0.25, 256.0) * 0.72 + noise(worldXZ, 1024.0) * 0.28;
    float puddle = smoothstep(0.30, 0.66, shape + (uRain.x - 1.0) * 0.15);

    vec3 incident = normalize(position);
    float facing = clamp(dot(-incident, worldUp), 0.0, 1.0);
    float fresnel = 0.06 + 0.94 * pow(1.0 - facing, 4.0);
    // Looking straight down leaves few reflected objects on screen. Fade their
    // contribution into a weaker sky reflection while keeping the surface damp.
    float viewFade = 1.0 - smoothstep(0.70, 0.98, facing);
    float skyAmount = (0.014 + puddle * (0.055 + fresnel * 0.24)) * mix(0.18, 1.0, viewFade);
    float hitAmount = puddle * (0.34 + fresnel * 0.50);
    vec3 sky = uSky * (0.87 * skyAmount);
    vec3 reflected = vec3(0.0);
    float hitWeight = 0.0;
    if (hitAmount > 0.0 && viewFade > 0.0) {
        vec2 wave = ripple(worldXZ);
        vec3 waterNormal = normalize(worldUp + uWorldX * wave.x + uWorldZ * wave.y);
        vec3 hit = traceReflection(position + worldUp * 0.055, reflect(incident, waterNormal), worldUp);
        hitWeight = hit.z * viewFade;
        if (hitWeight > 0.0) {
            vec2 softness = uPixel * vec2(1.35, 0.85);
            reflected = texture2D(SceneSampler, hit.xy).rgb * 0.66
                + (texture2D(SceneSampler, hit.xy - softness).rgb
                + texture2D(SceneSampler, hit.xy + softness).rgb) * 0.17;
        }
    }
    float amount = mix(skyAmount, hitAmount, hitWeight) * wetness;
    float darken = wetness * (0.055 + puddle * 0.15);
    vec3 color = mix(sky, reflected * hitAmount, hitWeight) * wetness;
    gl_FragColor = vec4(color, 1.0 - (1.0 - amount) * (1.0 - darken));
}
