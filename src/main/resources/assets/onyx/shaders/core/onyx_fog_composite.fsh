#version 120

// Blends the blurred copy over the sharp scene by linear eye distance.
//
// The Fabric build reads a reversed-Z depth buffer; 1.8.9 is plain 0..1 depth, so the
// `1.0 - depth` flip that version needs is absent here.
//
// The sharp scene is the frame this draws into, so the blend does the mixing: the coverage rides
// out as alpha against src-alpha/one-minus-src-alpha, which is the same `mix(sharp, blurred,
// amount)` this shader used to compute - without a full-screen copy of the frame to read it from.

uniform sampler2D BlurSampler;
uniform sampler2D DepthSampler;

uniform vec4 uCamera;
uniform vec4 uFog;
uniform vec3 uColor1;
uniform vec3 uColor2;
uniform vec3 uColor3;
uniform vec3 uColor4;

varying vec2 texCoord;

#define near uCamera.x
#define far uCamera.y
#define strength uCamera.z
#define start uFog.x
#define fade uFog.y
#define tint uFog.z
#define clientColor uFog.w
#define NOISE (0.5 / 255.0)

float eyeDistance(vec2 coord) {
    float depth = texture2D(DepthSampler, coord).x;
    float ndc = 2.0 * depth - 1.0;
    return 2.0 * near * far / (far + near - ndc * (far - near));
}

vec3 gradient(vec2 coords) {
    vec3 color = mix(mix(uColor1.rgb, uColor2.rgb, coords.y),
                     mix(uColor3.rgb, uColor4.rgb, coords.y), coords.x);
    color += mix(NOISE, -NOISE,
                 fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));
    return color;
}

void main() {
    float amount = strength * smoothstep(start, start + max(fade, 0.001), eyeDistance(texCoord));
    vec3 blurred = texture2D(BlurSampler, texCoord).rgb;

    if (clientColor > 0.5) blurred = mix(blurred, gradient(texCoord), tint);

    gl_FragColor = vec4(blurred, amount);
}
