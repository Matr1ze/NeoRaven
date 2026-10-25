#version 120

// Dual-Kawase upsample, the tent filter that pairs with onyx_fog_down.

uniform sampler2D Sampler0;
uniform vec2 uSourceSize;

varying vec2 texCoord;

const float OFFSET = 2.0;

void main() {
    vec2 halfpixel = (1.0 / uSourceSize) * OFFSET;

    vec3 sum = texture2D(Sampler0, texCoord + vec2(-halfpixel.x * 2.0, 0.0)).rgb;
    sum += texture2D(Sampler0, texCoord + vec2(-halfpixel.x, halfpixel.y)).rgb * 2.0;
    sum += texture2D(Sampler0, texCoord + vec2(0.0, halfpixel.y * 2.0)).rgb;
    sum += texture2D(Sampler0, texCoord + vec2(halfpixel.x, halfpixel.y)).rgb * 2.0;
    sum += texture2D(Sampler0, texCoord + vec2(halfpixel.x * 2.0, 0.0)).rgb;
    sum += texture2D(Sampler0, texCoord + vec2(halfpixel.x, -halfpixel.y)).rgb * 2.0;
    sum += texture2D(Sampler0, texCoord + vec2(0.0, -halfpixel.y * 2.0)).rgb;
    sum += texture2D(Sampler0, texCoord + vec2(-halfpixel.x, -halfpixel.y)).rgb * 2.0;

    gl_FragColor = vec4(sum / 12.0, 1.0);
}
