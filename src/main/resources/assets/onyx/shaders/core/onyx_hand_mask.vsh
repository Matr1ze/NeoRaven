#version 120

// Geometry pass: the hand is drawn a second time through this, so it has to follow whatever
// transform the fixed-function pipeline is already carrying.

varying vec2 texCoord0;

void main() {
    gl_Position = ftransform();
    texCoord0 = gl_MultiTexCoord0.xy;
}
