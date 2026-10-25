#version 120

// GLSL 120 port of hand_glow_aurora.fsh. Only the uniform plumbing differs: what the Fabric build
// packs into a std140 block arrives here as plain uniforms.

uniform sampler2D Sampler0;
uniform vec2 uMaskSize;
uniform vec2 uOutSize;

uniform vec4 uColorPrimary;
uniform vec4 uColorSecondary;
uniform vec4 uMotion;
uniform vec4 uOptions;

varying vec2 texCoord;

float sampleMask(vec2 uv) {
    return smoothstep(0.28, 0.62, texture2D(Sampler0, uv).a);
}

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

float edgeMetric(vec2 uv, vec2 texelSize, float radius) {
    vec2 stepv = texelSize * max(radius, 0.001);
    float c = sampleMask(uv);
    float axis = 0.0;
    axis += abs(c - sampleMask(uv + vec2(stepv.x, 0.0)));
    axis += abs(c - sampleMask(uv - vec2(stepv.x, 0.0)));
    axis += abs(c - sampleMask(uv + vec2(0.0, stepv.y)));
    axis += abs(c - sampleMask(uv - vec2(0.0, stepv.y)));

    float diag = 0.0;
    diag += abs(c - sampleMask(uv + stepv));
    diag += abs(c - sampleMask(uv - stepv));
    diag += abs(c - sampleMask(uv + vec2(stepv.x, -stepv.y)));
    diag += abs(c - sampleMask(uv + vec2(-stepv.x, stepv.y)));

    return clamp(axis * 0.22 + diag * 0.12, 0.0, 1.0);
}

// The wide and fine edge terms only feed low-weight halo and detail lines, so they get the four
// axial taps rather than the full ring.
float edgeAxis(vec2 uv, vec2 texelSize, float radius) {
    vec2 stepv = texelSize * max(radius, 0.001);
    float c = sampleMask(uv);
    float axis = 0.0;
    axis += abs(c - sampleMask(uv + vec2(stepv.x, 0.0)));
    axis += abs(c - sampleMask(uv - vec2(stepv.x, 0.0)));
    axis += abs(c - sampleMask(uv + vec2(0.0, stepv.y)));
    axis += abs(c - sampleMask(uv - vec2(0.0, stepv.y)));

    return clamp(axis * 0.30, 0.0, 1.0);
}

float blurredMask(vec2 uv, vec2 texelSize, float radius) {
    vec2 stepv = texelSize * radius;
    float sum = sampleMask(uv) * 2.0;
    sum += sampleMask(uv + vec2(stepv.x, 0.0));
    sum += sampleMask(uv - vec2(stepv.x, 0.0));
    sum += sampleMask(uv + vec2(0.0, stepv.y));
    sum += sampleMask(uv - vec2(0.0, stepv.y));
    sum += sampleMask(uv + stepv) * 0.7;
    sum += sampleMask(uv - stepv) * 0.7;
    sum += sampleMask(uv + vec2(stepv.x, -stepv.y)) * 0.7;
    sum += sampleMask(uv + vec2(-stepv.x, stepv.y)) * 0.7;
    return sum / 7.8;
}

void main() {
    vec3 color = uColorPrimary.rgb;
    vec3 color2 = uColorSecondary.rgb;
    float alpha = uColorPrimary.a;
    float fill = uColorSecondary.a;
    float time = uMotion.x;
    float speed = uMotion.y;
    float scale = uMotion.z;
    float outline = uMotion.w;
    float glow = uOptions.x;
    float outlineOnly = uOptions.y;

    vec2 texelSize = 1.0 / max(uMaskSize, vec2(1.0));
    vec2 uv = texCoord;
    float mask = sampleMask(uv);

    if (mask <= 0.0) {
        gl_FragColor = vec4(0.0);
        return;
    }

    float outlineEnabled = step(0.001, outline);
    float edge = outlineEnabled * smoothstep(0.03, 0.27, edgeMetric(uv, texelSize, max(outline, 0.001)));
    float edgeWide = outlineEnabled * smoothstep(0.01, 0.19, edgeAxis(uv, texelSize, outline * 2.2 + 0.9));
    float edgeFine = outlineEnabled * smoothstep(0.07, 0.36, edgeAxis(uv, texelSize, outline * 0.78 + 0.2));
    float inner = clamp(blurredMask(uv, texelSize, outline * 2.0 + 1.0), 0.0, 1.0);
    float body = smoothstep(0.16, 0.97, inner) * mix(1.0, 1.0 - edge * 0.82, outlineEnabled);

    float t = time * max(speed, 0.001);
    float patternScale = mix(1.25, 3.05, clamp((scale - 1.0) / 2.0, 0.0, 1.0));

    vec3 worldPos = vec3((uv - 0.5) * vec2(max(uOutSize.x, 1.0) / max(uOutSize.y, 1.0), 1.0) * 5.0, 0.0);

    vec2 p = vec2(worldPos.x + worldPos.z * 0.5, worldPos.y + worldPos.z * 0.31);
    p *= patternScale * 0.55;

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
    float glowField = clamp(curtain * 0.92 + softness * 0.40, 0.0, 1.0);
    float colorMix = clamp(0.22 + glowField * 0.58 + depth * 0.14, 0.0, 1.0);

    vec3 baseColor = mix(color, color2, colorMix);
    vec3 softColor = mix(baseColor, vec3(1.0), 0.12 + curtain * 0.11);
    vec3 rimColor = mix(color, color2, clamp(0.44 + curtain * 0.42 + mist * 0.10, 0.0, 1.0));

    if (outlineOnly > 0.5) {
        float lineAlpha = clamp(alpha * edge * (0.92 + glow * 0.24 + curtain * 0.14), 0.0, 1.0) * mask;

        if (lineAlpha <= 0.001) {
            gl_FragColor = vec4(0.0);
            return;
        }

        gl_FragColor = vec4(mix(rimColor, softColor, 0.18), lineAlpha);
        return;
    }

    float fillAlpha = alpha * fill * body * (0.24 + softness * 0.26 + curtain * 0.24);
    float mistAlpha = alpha * body * (0.095 + mist * 0.075 + wave * 0.045);
    float ribbonAlpha = alpha * body * curtain * 0.24;
    float rimAlpha = alpha * edge * (0.42 + glow * 0.22 + curtain * 0.18);
    float haloAlpha = alpha * edgeWide * glow * (0.12 + curtain * 0.10) * (1.0 - edge * 0.52);
    float fineAlpha = alpha * edgeFine * curtain * 0.12;

    vec3 rgb = baseColor * (fillAlpha * 1.12);
    rgb += softColor * mistAlpha;
    rgb += mix(baseColor, softColor, 0.62) * ribbonAlpha;
    rgb += rimColor * rimAlpha;
    rgb += mix(rimColor, softColor, 0.42) * haloAlpha;
    rgb += mix(baseColor, rimColor, 0.58) * fineAlpha;

    float outAlpha = clamp(fillAlpha + mistAlpha + ribbonAlpha + rimAlpha + haloAlpha + fineAlpha, 0.0, 1.0) * mask;

    if (outAlpha <= 0.001) {
        gl_FragColor = vec4(0.0);
        return;
    }

    gl_FragColor = vec4(rgb, outAlpha);
}
