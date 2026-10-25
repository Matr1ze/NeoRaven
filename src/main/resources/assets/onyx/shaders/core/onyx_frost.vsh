#version 120

varying vec4 vertexColor;
varying vec2 localPos;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;

    vertexColor = gl_Color;
    localPos = gl_MultiTexCoord0.xy;
}
