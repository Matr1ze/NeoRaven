#version 120

// Dual-Kawase downsample. Unlike the Fabric build's pass the texture coordinate is already in the
// destination's 0..1 space, so no rescale is needed here — only the source texel size.

uniform sampler2D Sampler0;
uniform vec2 uSourceSize;

varying vec2 texCoord;

const float OFFSET = 2.0;

void main() {
    vec2 halfpixel = (1.0 / uSourceSize) * OFFSET;

    vec3 sum = texture2D(Sampler0, texCoord).rgb * 4.0;
    sum += texture2D(Sampler0, texCoord - halfpixel).rgb;
    sum += texture2D(Sampler0, texCoord + halfpixel).rgb;
    sum += texture2D(Sampler0, texCoord + vec2(halfpixel.x, -halfpixel.y)).rgb;
    sum += texture2D(Sampler0, texCoord - vec2(halfpixel.x, -halfpixel.y)).rgb;

    gl_FragColor = vec4(sum / 8.0, 1.0);
}
