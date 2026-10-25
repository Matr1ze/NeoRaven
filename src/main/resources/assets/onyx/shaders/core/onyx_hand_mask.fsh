#version 120

// Flattens the hand to a single colour so the effects have a silhouette to work from.
//
// The Fabric build gets this for free from the entity outline pass. 1.8.9 has no such pass, so the
// hand is simply drawn again into an offscreen target with this bound.

uniform sampler2D Sampler0;
uniform vec4 uColor;

varying vec2 texCoord0;

void main() {
    if (texture2D(Sampler0, texCoord0).a < 0.1) discard;

    gl_FragColor = uColor;
}
