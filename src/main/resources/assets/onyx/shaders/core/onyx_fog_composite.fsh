#version 120

// Reconstruct local world position from Minecraft's conventional 0..1 depth buffer.
// Distance is a horizontal clear radius around the player; Fade is the surrounding
// transition width. Alpha blends this blurred copy over the existing sharp scene.

uniform sampler2D BlurSampler;
uniform sampler2D DepthSampler;

uniform vec4 uInverse0;
uniform vec4 uInverse1;
uniform vec4 uInverse2;
uniform vec4 uInverse3;
uniform vec2 uPlayer;
uniform vec4 uFog;
uniform vec3 uColor1;
uniform vec3 uColor2;
uniform vec3 uColor3;
uniform vec3 uColor4;

varying vec2 texCoord;

#define start uFog.x
#define fade uFog.y
#define tint uFog.z
#define clientColor uFog.w
#define NOISE (0.5 / 255.0)

float playerDistance(vec2 coord) {
    float depth = texture2D(DepthSampler, coord).x;
    // The sky has no world surface. Keep it fully fogged, including directly above
    // the player, rather than interpreting far-plane coordinates as nearby ground.
    if (depth >= 1.0) return start + max(fade, 0.001);
    mat4 inverseViewProjection = mat4(uInverse0, uInverse1, uInverse2, uInverse3);
    vec4 position = inverseViewProjection * vec4(coord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    if (abs(position.w) < 0.000001) return start + max(fade, 0.001);
    return length(position.xz / position.w - uPlayer);
}

vec3 gradient(vec2 coords) {
    vec3 color = mix(mix(uColor1.rgb, uColor2.rgb, coords.y),
                     mix(uColor3.rgb, uColor4.rgb, coords.y), coords.x);
    color += mix(NOISE, -NOISE,
                 fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));
    return color;
}

void main() {
    float amount = smoothstep(start, start + max(fade, 0.001), playerDistance(texCoord));
    vec3 blurred = texture2D(BlurSampler, texCoord).rgb;

    if (clientColor > 0.5) blurred = mix(blurred, gradient(texCoord), tint);

    gl_FragColor = vec4(blurred, amount);
}
