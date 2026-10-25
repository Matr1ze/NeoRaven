#version 120

// Straight copy, used to lay a finished pass over the scene and to carry the trail's feedback
// buffer from one frame's target into the other.

uniform sampler2D Sampler0;

varying vec2 texCoord;

void main() {
    gl_FragColor = texture2D(Sampler0, texCoord);
}
