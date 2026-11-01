#version 120

// Hand outline trail pass. The feedback buffer is premultiplied and accumulated additively, so a
// frame is either decayed (straight multiply, alpha included) or premultiplied before it is added:
//   uTint        straight colour multiply
//   uPremultiply 1 = multiply rgb by the (already tinted) alpha

uniform sampler2D Sampler0;
uniform vec4 uTint;
uniform float uPremultiply;

varying vec2 texCoord;

void main() {
    vec4 colour = texture2D(Sampler0, texCoord) * uTint;
    if (uPremultiply > 0.5) {
        colour.rgb *= colour.a;
    }
    gl_FragColor = colour;
}
