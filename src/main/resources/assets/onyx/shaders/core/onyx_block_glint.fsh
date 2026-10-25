#version 120

// The aurora from onyx_hand_aurora.fsh, moved off the screen-space mask and onto a block. The
// curtain maths is the same - the same hash/noise/fbm stack, the same three sines beaten against
// each other - but it is sampled in the block's own space, so the pattern is welded to the block
// rather than swimming across it as the camera turns.
//
// The block's own texture is not used at all. The overlay is a plain cube of curtain: a texture
// carries the block's holes and its dark pixels with it, and those cut the cube into the shape of
// whatever it is drawn on - leaves and grass eat it away, glass empties it out.

uniform vec4 uColorPrimary;    // rgb, a = the module's animated fade
uniform vec4 uColorSecondary;  // rgb, a = how strongly the glint is laid on
uniform vec4 uMotion;          // time, speed, scale, glow
uniform vec4 uCrop;            // lower and upper cut, and whether to cut at all
uniform float uFogEnabled;

varying vec3 patternPos;
varying float objectY;
varying vec3 cameraRelative;

mat2 rot(float a) {
    float s = sin(a);
    float c = cos(a);
    return mat2(c, -s, s, c);
}

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.55;

    for (int i = 0; i < 4; i++) {
        value += noise(p) * amplitude;
        p = rot(0.34) * p * 2.02 + vec2(4.7, 2.3);
        amplitude *= 0.5;
    }

    return value;
}

void main() {
    // The rising fill crops the block instead of scaling it: squashing the model down to the broken
    // part squashes its texture with it, and a half-broken stone brick stops looking like stone.
    if (uCrop.z > 0.5 && (objectY < uCrop.x || objectY > uCrop.y)) {
        discard;
    }

    vec3 normal = normalize(cross(dFdx(cameraRelative), dFdy(cameraRelative)));

    vec3 primary = uColorPrimary.rgb;
    vec3 secondary = uColorSecondary.rgb;
    float fade = uColorPrimary.a;
    float amount = uColorSecondary.a;

    float t = uMotion.x * max(uMotion.y, 0.001);
    float glow = uMotion.w;
    float patternScale = mix(1.15, 3.4, clamp((uMotion.z - 0.5) / 3.5, 0.0, 1.0));

    vec3 wp = patternPos * patternScale * 1.25;
    vec2 p = vec2(wp.x + wp.z * 0.5, wp.y + wp.z * 0.31);

    vec2 flow = p;
    flow += (vec2(
        fbm(rot(0.28) * (p * 0.88 + vec2(t * 0.08, -t * 0.12))),
        fbm(rot(-0.44) * (p * 0.94 + vec2(-t * 0.05, t * 0.09)))
    ) - 0.5) * 0.85;

    float auroraA = 0.5 + 0.5 * sin(flow.y * 3.8 + flow.x * 0.9 - t * 0.70);
    float auroraB = 0.5 + 0.5 * sin(flow.y * 5.4 - flow.x * 1.15 + t * 0.56);
    float auroraC = 0.5 + 0.5 * sin(flow.y * 2.6 + flow.x * 1.45 - t * 0.38);

    float curtain = smoothstep(0.50, 0.98, auroraA) * 0.72
                  + smoothstep(0.60, 1.0, auroraB) * 0.44
                  + auroraC * 0.16;

    float mist = fbm(flow * 0.82 + vec2(t * 0.03, -t * 0.02));
    float depth = fbm(rot(0.82) * flow * 1.26 - vec2(t * 0.02, t * 0.04));
    float wave = 0.5 + 0.5 * sin(length(flow) * 3.6 - t * 0.42);

    float softness = clamp(mist * 0.52 + depth * 0.28 + wave * 0.14, 0.0, 1.0);
    float colorMix = clamp(0.22 + curtain * 0.58 + depth * 0.14, 0.0, 1.0);

    vec3 baseColor = mix(primary, secondary, colorMix);
    vec3 softColor = mix(baseColor, vec3(1.0), 0.12 + curtain * 0.11);

    // Silhouette: the further a face turns away from the eye, the brighter its edge burns.
    float facing = clamp(abs(dot(normal, normalize(-cameraRelative))), 0.0, 1.0);
    float rim = pow(1.0 - facing, 2.5);

    float field = 0.26 + softness * 0.28 + curtain * 0.46;

    float alpha = fade * amount * field;
    alpha += fade * rim * (0.30 + glow * 0.45);

    alpha = clamp(alpha, 0.0, 1.0);

    if (alpha <= 0.002) {
        discard;
    }

    vec3 color = mix(baseColor, softColor, clamp(curtain * 0.6, 0.0, 1.0));
    color += softColor * rim * glow * 0.5;

    if (uFogEnabled > 0.5) {
        float fogAmount = clamp((gl_Fog.end - gl_FogFragCoord) * gl_Fog.scale, 0.0, 1.0);
        color = mix(gl_Fog.color.rgb, color, fogAmount);
    }

    gl_FragColor = vec4(color, alpha);
}
