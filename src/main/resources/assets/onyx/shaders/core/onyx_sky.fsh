#version 120

// GLSL 120 port of the Fabric build's onyx_sky.fsh. The core noise and preset maths are unchanged;
// what differs is the plumbing: 330's `in`/`out` become `varying`/`gl_FragColor`, the values that
// rode in on Minecraft's DynamicTransforms uniform block arrive as plain uniforms, and every loop
// runs to a compile-time bound with an early break because 1.20 will not reliably unroll a loop
// whose limit is a uniform.

uniform vec4 uFogColor;
uniform vec4 uColorModulator;
uniform vec3 uParams;
uniform vec4 uKnobs;
uniform float uStrikeGlow;

varying vec3 skyDir;

const int MAX_OCTAVES = 6;

const int PRESET_CLOUDS = 0;
const int PRESET_THUNDER = 1;
const int PRESET_PULSAR = 2;

// The eight lattice corners of one noise cell are independent, so the scalar hash
// runs four at a time: same function, a quarter of the instructions.
vec4 hash4(vec4 px, vec4 py, vec4 pz) {
    px = fract(px * 0.3183099 + 0.1);
    py = fract(py * 0.3183099 + 0.1);
    pz = fract(pz * 0.3183099 + 0.1);
    px *= 17.0;
    py *= 17.0;
    pz *= 17.0;
    return fract(px * py * pz * (px + py + pz));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);

    vec4 cornerX = i.x + vec4(0.0, 1.0, 0.0, 1.0);
    vec4 cornerY = i.y + vec4(0.0, 0.0, 1.0, 1.0);

    vec4 plane = mix(hash4(cornerX, cornerY, vec4(i.z)),
                     hash4(cornerX, cornerY, vec4(i.z + 1.0)), f.z);
    vec2 row = mix(plane.xz, plane.yw, f.x);
    return mix(row.x, row.y, f.y);
}

float fbm(vec3 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;

    for (int i = 0; i < MAX_OCTAVES; i++) {
        if (i >= octaves) break;
        value += amplitude * noise(p);
        p = p * 2.0 + vec3(37.1, 17.7, 29.3);
        amplitude *= 0.5;
    }

    return value / (1.0 - exp2(-float(octaves)));
}

float stormHash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453123);
}

float stormHash(float n) {
    return fract(sin(n) * 43758.5453123);
}

float stormNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float n000 = stormHash(i + vec3(0.0, 0.0, 0.0));
    float n100 = stormHash(i + vec3(1.0, 0.0, 0.0));
    float n010 = stormHash(i + vec3(0.0, 1.0, 0.0));
    float n110 = stormHash(i + vec3(1.0, 1.0, 0.0));
    float n001 = stormHash(i + vec3(0.0, 0.0, 1.0));
    float n101 = stormHash(i + vec3(1.0, 0.0, 1.0));
    float n011 = stormHash(i + vec3(0.0, 1.0, 1.0));
    float n111 = stormHash(i + vec3(1.0, 1.0, 1.0));

    vec4 a = mix(vec4(n000, n010, n001, n011),
                 vec4(n100, n110, n101, n111), f.x);
    vec2 b = mix(a.xz, a.yw, f.y);
    return mix(b.x, b.y, f.z);
}

// Unnormalised on purpose: the storm presets are tuned against the raw 0..0.94 range
// this sum lands in, not against the balanced fbm the cloud preset uses.
float stormFbm(vec3 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    vec3 shift = vec3(100.0);

    for (int i = 0; i < MAX_OCTAVES; i++) {
        if (i >= octaves) break;
        value += amplitude * stormNoise(p);
        p = p * 2.0 + shift;
        amplitude *= 0.5;
    }

    return value;
}

vec3 cloudsSky(vec3 dir, float t, int octaves, vec3 base) {
    float rotation = t * 0.012;
    mat2 skyRotation = mat2(cos(rotation), -sin(rotation), sin(rotation), cos(rotation));
    dir.xz = skyRotation * dir.xz;

    vec3 q = dir * 2.4;
    float f1 = fbm(q + vec3(t * 0.06, t * 0.02, 0.0), octaves);
    float f2 = fbm(q + f1 * 0.8 + vec3(1.7, 9.2, 3.3) + t * 0.05, octaves);

    vec3 deep = base * 0.22;
    vec3 mid = base * 0.55;
    vec3 accent = base * 0.95;
    vec3 glow = mix(base, vec3(1.0), 0.6);

    vec3 color = deep;
    color = mix(color, mid, clamp(f1 * 1.25, 0.0, 1.0));
    color = mix(color, accent, clamp(pow(f2, 1.5) * 1.4, 0.0, 0.85));

    float highlight = pow(clamp(f1 * f2 * 2.6, 0.0, 1.0), 2.2);
    color = mix(color, glow, clamp(highlight, 0.0, 0.6));

    float elevation = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    color *= 0.78 + 0.22 * elevation;

    float horizon = 1.0 - smoothstep(0.0, 0.18, dir.y);
    return mix(color, uFogColor.rgb, horizon * uFogColor.a);
}

vec3 thunderSky(vec3 dir, float t, float speed, float scale, float intensity, vec3 base,
                int octaves, float strikeInterval, float strikeChance, float strikeGlow) {
    float cloudDensity = 0.0;
    if (dir.y > -0.15) {
        vec3 p = dir * scale;
        p.x += t * speed * 0.08;
        p.z += t * speed * 0.04;

        cloudDensity = stormFbm(p, octaves);
        cloudDensity *= smoothstep(-0.15, 0.3, dir.y);
    }

    float interval = max(1.0, strikeInterval);
    float timeIndex = floor(t / interval);
    float timeOffset = fract(t / interval);

    bool hasStrike = stormHash(timeIndex * 12.34) < strikeChance;

    float flash = 0.0;
    float boltGlow = 0.0;

    if (hasStrike) {
        float strikeAngle = stormHash(timeIndex * 45.67) * 6.28318;

        vec3 boltStart = normalize(vec3(cos(strikeAngle), 1.0, sin(strikeAngle)));
        float endSkew = (stormHash(timeIndex * 8.3) - 0.5) * 0.2;
        vec3 boltEnd = normalize(vec3(cos(strikeAngle + endSkew), -0.2, sin(strikeAngle + endSkew)));

        float phase = timeOffset;
        if (phase < 0.55) {
            float flicker = 0.8 + 0.2 * sin(t * 95.0) * cos(t * 135.0);

            flash = (exp(-phase * 22.0) * 1.5
                   + exp(-abs(phase - 0.15) * 25.0) * 1.0
                   + exp(-abs(phase - 0.35) * 15.0) * 0.4) * flicker;

            float boltIntensity = max(exp(-phase * 18.0), exp(-abs(phase - 0.15) * 22.0));
            if (boltIntensity > 0.02) {
                vec3 segment = boltEnd - boltStart;
                float segLen = length(segment);
                vec3 segDir = segment / segLen;

                float h = clamp(dot(dir - boltStart, segDir), 0.0, segLen);
                vec3 projection = boltStart + segDir * h;

                float seed = timeIndex * 73.19;
                float jagged = sin(h * 30.0 + seed) * 0.035;
                jagged += cos(h * 70.0 - seed) * 0.015;
                jagged += sin(h * 150.0 + seed * 1.5) * 0.007;
                jagged += cos(h * 350.0 - seed * 3.0) * 0.003;

                vec3 offsetProj = projection + vec3(jagged, 0.0, jagged * 0.6);
                float distToBolt = length(dir - normalize(offsetProj));

                boltGlow = (exp(-distToBolt * 280.0) * 2.5
                          + exp(-distToBolt * 30.0) * 0.6) * boltIntensity * flicker;

                if (stormHash(timeIndex * 29.5) > 0.3 && h > segLen * 0.2) {
                    vec3 branchEnd = normalize(boltEnd + vec3(sin(strikeAngle + 0.8), -0.15, cos(strikeAngle + 0.8)) * 0.4);
                    vec3 branch = branchEnd - projection;
                    float branchLen = length(branch);
                    if (branchLen > 0.0) {
                        vec3 branchDir = branch / branchLen;
                        float bh = clamp(dot(dir - projection, branchDir), 0.0, branchLen);
                        vec3 branchProj = projection + branchDir * bh;
                        float branchJagged = sin(bh * 45.0 + seed) * 0.015 + cos(bh * 110.0) * 0.006;
                        float distToBranch = length(dir - normalize(branchProj + vec3(branchJagged, 0.0, branchJagged)));

                        boltGlow += (exp(-distToBranch * 220.0) * 1.2
                                   + exp(-distToBranch * 30.0) * 0.3) * boltIntensity * (1.0 - bh / branchLen) * flicker;
                    }
                }

                if (stormHash(timeIndex * 83.2) > 0.5 && h > segLen * 0.4) {
                    vec3 branchEnd = normalize(boltEnd + vec3(sin(strikeAngle - 0.8), -0.2, cos(strikeAngle - 0.8)) * 0.45);
                    vec3 branch = branchEnd - projection;
                    float branchLen = length(branch);
                    if (branchLen > 0.0) {
                        vec3 branchDir = branch / branchLen;
                        float bh = clamp(dot(dir - projection, branchDir), 0.0, branchLen);
                        vec3 branchProj = projection + branchDir * bh;
                        float branchJagged = sin(bh * 50.0 - seed) * 0.015 + cos(bh * 120.0) * 0.006;
                        float distToBranch = length(dir - normalize(branchProj + vec3(branchJagged, 0.0, branchJagged)));

                        boltGlow += (exp(-distToBranch * 220.0) * 1.2
                                   + exp(-distToBranch * 30.0) * 0.3) * boltIntensity * (1.0 - bh / branchLen) * flicker;
                    }
                }
            }
        }
    }

    vec3 stormBase = mix(vec3(0.005, 0.005, 0.015), base * 0.05, 0.5);
    vec3 flashColor = vec3(0.75, 0.82, 1.0) * flash * strikeGlow;
    vec3 sky = stormBase + flashColor * 0.25;

    vec3 cloudBase = mix(vec3(0.01, 0.01, 0.02), vec3(0.06, 0.07, 0.12), cloudDensity);
    vec3 cloudLit = mix(vec3(0.02, 0.025, 0.05), vec3(0.8, 0.85, 1.0), cloudDensity * flash * strikeGlow * 0.7);
    vec3 cloud = mix(cloudBase, cloudLit, min(flash * strikeGlow, 1.0));

    vec3 color = mix(sky, cloud, smoothstep(0.18, 0.48, cloudDensity));
    color += vec3(0.85, 0.92, 1.0) * boltGlow * strikeGlow;

    return color * (1.0 + intensity * 10.0);
}

vec3 starField(vec3 dir, float t) {
    vec3 cell = floor(dir * 130.0);
    float seed = stormHash(cell);

    if (seed <= 0.992) return vec3(0.0);

    vec3 jitter = vec3(stormHash(cell + 1.1), stormHash(cell + 2.2), stormHash(cell + 3.3)) - 0.5;
    vec3 position = (cell + 0.5 + jitter * 0.85) / 130.0;

    float d = length(dir - normalize(position));
    float twinkle = 0.55 + 0.45 * sin(t * 3.5 + seed * 120.0);
    float brightness = exp(-d * 600.0) * twinkle * (0.3 + 0.7 * stormHash(cell + 5.5));

    vec3 tint = vec3(1.0);
    float temperature = stormHash(cell + 7.7);
    if (temperature < 0.22) {
        tint = vec3(0.65, 0.8, 1.0);
    } else if (temperature < 0.38) {
        tint = vec3(1.0, 0.82, 0.6);
    } else if (temperature < 0.43) {
        tint = vec3(1.0, 0.55, 0.55);
    }

    return tint * brightness;
}

vec3 pulsarSky(vec3 dir, float t, float speed, float scale, float intensity, vec3 base, int octaves) {
    float n1 = stormFbm(dir * scale * 0.4 + vec3(t * 0.003, 0.0, 0.0), octaves);
    float n2 = stormFbm(dir * scale * 0.9 - vec3(0.0, t * 0.002, 0.0), octaves);

    vec3 gas = mix(vec3(0.001, 0.003, 0.012), vec3(0.035, 0.01, 0.055), n1);
    gas = mix(gas, vec3(0.008, 0.025, 0.035), n2 * n1);

    float dust = smoothstep(0.3, 0.7, stormFbm(dir * scale * 1.2 + vec3(0.1), octaves));
    vec3 color = mix(gas, vec3(0.0005, 0.0005, 0.0015), dust * 0.85) + starField(dir, t);

    vec3 center = normalize(vec3(0.55, 0.65, -0.45));
    float frontMask = smoothstep(0.0, 0.4, dot(dir, center));

    if (frontMask > 0.0) {
        vec3 tangent1 = normalize(cross(center, vec3(0.0, 1.0, 0.0)));
        vec3 tangent2 = cross(tangent1, center);

        vec2 local = vec2(dot(dir, tangent1), dot(dir, tangent2));
        float dCore = length(local);

        float pulse = 0.95 + 0.05 * sin(t * speed * 8.0);
        float core = smoothstep(0.058 * pulse, 0.0, dCore);

        float flare = stormFbm(dir * 12.0 + vec3(0.0, t * 0.25, 0.0), octaves);
        float halo = exp(-dCore * 16.0) * 3.2 + exp(-dCore * 3.5) * 0.9 * (0.7 + 0.3 * flare);

        // The jets shake around vertical instead of sweeping a full circle.
        float wobble = sin(t * speed * 6.0) * 0.22;
        vec2 jetDir = vec2(sin(wobble), cos(wobble));

        float along = dot(local, jetDir);
        float wave = sin(abs(along) * 75.0 - t * speed * 32.0) * 0.0055 * abs(along);
        float distToJet = length(local - along * jetDir) - wave;

        float jet = exp(-distToJet * 320.0) * 3.0 + exp(-distToJet * 38.0) * 0.8;
        jet *= exp(-abs(along) * 0.85);

        float jetFlicker = 0.82 + 0.18 * sin(t * 115.0) * cos(t * 145.0);

        vec3 pulsar = mix(vec3(0.0), vec3(1.0), core);
        pulsar += vec3(0.18, 0.45, 1.0) * base * halo;
        pulsar += vec3(0.35, 0.68, 1.0) * base * jet * jetFlicker;

        color += pulsar * frontMask;
    }

    return color * (1.0 + intensity * 10.0);
}

void main() {
    vec3 dir = normalize(skyDir);

    vec3 base = uColorModulator.rgb;
    float elapsed = uColorModulator.a;

    // Each cloud layer doubles the cost of the whole pass, so the octave count is a setting.
    int octaves = int(clamp(uParams.x, 1.0, float(MAX_OCTAVES)));
    int preset = int(uParams.y + 0.5);
    float speed = uParams.z;

    float scale = uKnobs.x;
    float intensity = uKnobs.y;
    float strikeInterval = uKnobs.z;
    float strikeChance = uKnobs.w;
    float strikeGlow = uStrikeGlow;

    vec3 color;
    if (preset == PRESET_THUNDER) {
        color = thunderSky(dir, elapsed, speed, scale, intensity, base, octaves, strikeInterval, strikeChance, strikeGlow);
    } else if (preset == PRESET_PULSAR) {
        color = pulsarSky(dir, elapsed, speed, scale, intensity, base, octaves);
    } else {
        // PRESET_CLOUDS, and anything unrecognised, falls back to the cloud field.
        color = cloudsSky(dir, elapsed * speed, octaves, base);
    }

    gl_FragColor = vec4(color, 1.0);
}
