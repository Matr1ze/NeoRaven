#version 120
varying vec2 vUv;
varying vec4 vColor;
varying vec3 vPos;
void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    vUv = gl_MultiTexCoord0.st;
    vColor = gl_Color;
    vPos = gl_Vertex.xyz;
}
