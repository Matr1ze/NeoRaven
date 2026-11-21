#version 120

uniform sampler2D DepthSampler;
uniform vec4 uInverse0, uInverse1, uInverse2, uInverse3;
uniform vec3 uEye, uPlayer, uNoiseOrigin, uColor;
uniform vec2 uFog; // Density, range in blocks
varying vec2 uv;

// Eight corners in two vectorised layers. The 128-cell period matches the
// double-precision origin wrap, so even distant coordinates retain smooth detail.
vec4 permute(vec4 p) {
    return mod((p * 17.0 + 23.0) * p, 289.0);
}

float noise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    vec3 w = f * f * (3.0 - 2.0 * f);
    vec4 x = mod(cell.x + vec4(0.0, 1.0, 0.0, 1.0), 128.0);
    vec4 y = mod(cell.y + vec4(0.0, 0.0, 1.0, 1.0), 128.0);
    vec4 xy = permute(permute(x) + y);
    vec4 a = fract(permute(xy + mod(cell.z, 128.0)) * (1.0 / 41.0));
    vec4 b = fract(permute(xy + mod(cell.z + 1.0, 128.0)) * (1.0 / 41.0));
    vec4 z = mix(a, b, w.z);
    return mix(mix(z.x, z.y, w.x), mix(z.z, z.w, w.x), w.y);
}

void main() {
    mat4 inverse = mat4(uInverse0, uInverse1, uInverse2, uInverse3);
    float depth = texture2D(DepthSampler, uv).r;
    vec4 surface = inverse * vec4(uv * 2.0 - 1.0, min(depth, 0.999999) * 2.0 - 1.0, 1.0);
    vec3 ray = surface.xyz / surface.w - uEye;
    float rayLength = length(ray);
    vec3 direction = ray / max(rayLength, 0.0001);

    // The volume follows the player's altitude and softly tapers around them.
    // Integrate up to the first opaque surface, starting right at the camera.
    vec3 eyeRelative = uEye - uPlayer;
    float along = dot(direction, eyeRelative);
    float discriminant = along * along - dot(eyeRelative, eyeRelative) + uFog.y * uFog.y;
    float exitDistance = -along + sqrt(max(0.0, discriminant));
    float endDistance = min(rayLength, max(0.0, exitDistance));
    if (discriminant < 0.0 || endDistance <= 0.0) {
        gl_FragColor = vec4(0.0);
        return;
    }

    const int samples = 20;
    vec3 light = vec3(0.0);
    float transmission = 1.0;
    float start = 0.0;
    float previousShape = 0.0;
    for (int i = 0; i < samples; ++i) {
        float t = float(i + 1) / float(samples);
        float end = endDistance * t * (0.52 + 0.48 * t);
        vec3 position = uEye + direction * ((start + end) * 0.5);
        vec3 relative = position - uPlayer;
        float distanceToPlayer = length(relative);
        float radial = 1.0 - smoothstep(uFog.y * 0.70, uFog.y, distanceToPlayer);
        float vertical = 1.0 - smoothstep(5.0, 20.0, abs(relative.y + 1.5));
        vec3 p = position * vec3(0.092, 1.0 / 5.8, 0.092) + uNoiseOrigin;
        float broad = noise(p * 0.68);
        float billow = noise(p * 1.34 + vec3(9.3, 4.6, 17.8));
        float detail = noise(p * 2.68 + vec3(21.7, 13.2, 5.1));
        float shape = broad * 0.62 + billow * 0.28 + detail * 0.10;
        float bank = smoothstep(0.47, 0.72, shape);
        float wisps = smoothstep(0.28, 0.72, mix(billow, detail, 0.36));
        float nearMask = 1.0 - smoothstep(2.0, 13.0, distanceToPlayer);
        float nearMist = nearMask * (0.026 + wisps * 0.112);
        float density = 0.0008 + bank * 0.17 + nearMist;
        float extinction = density * radial * vertical * uFog.x;
        float segment = exp(-extinction * (end - start));
        float edgeLight = clamp(abs(shape - previousShape) * 4.5, 0.0, 1.0);
        float silverLight = smoothstep(0.50, 0.75, shape) * (0.48 + edgeLight * 0.52);
        vec3 shaded = uColor * 0.35;
        vec3 lit = mix(uColor * 0.95, vec3(0.91, 0.96, 1.0), 0.68);
        vec3 illumination = mix(shaded, lit, silverLight);
        vec3 mistLight = uColor * (0.68 + wisps * 0.24);
        illumination = mix(illumination, mistLight, nearMist / density);
        light += transmission * (1.0 - segment) * illumination;
        transmission *= segment;
        start = end;
        previousShape = shape;
    }
    gl_FragColor = vec4(light, 1.0 - transmission);
}
