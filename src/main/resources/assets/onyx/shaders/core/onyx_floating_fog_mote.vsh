#version 120

varying vec2 uv;
varying float opacity;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    uv = gl_MultiTexCoord0.xy;
    opacity = gl_Color.a;
}
