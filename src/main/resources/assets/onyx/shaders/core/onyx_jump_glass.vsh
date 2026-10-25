#version 120

// Shared vertex stage for every hand-effect pass: the quad is drawn in 0..1 with an orthographic
// projection, so the position doubles as the texture coordinate.

varying vec2 texCoord;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    texCoord = gl_MultiTexCoord0.xy;
}
