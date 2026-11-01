#version 120

varying vec4 vertexColor;
varying vec2 localPos;
varying vec2 uv;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;

    vertexColor = gl_Color;
    // Unit 1 carries the SDF-space position: unlike gl_Normal it is not touched by the
    // model-view matrix, so the corner maths stay correct under the GUI's scale stack.
    localPos = gl_MultiTexCoord1.xy;
    uv = gl_MultiTexCoord0.xy;
}
