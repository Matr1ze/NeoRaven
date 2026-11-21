#version 120
varying vec3 vNormal;
varying vec3 vView;
varying vec4 vColor;
varying float vGlint;
void main() {
    vec4 p = gl_ModelViewMatrix * gl_Vertex;
    vView = p.xyz;
    vNormal = gl_NormalMatrix * gl_Normal;
    vColor = gl_Color;
    vGlint = gl_MultiTexCoord0.s;
    gl_Position = gl_ProjectionMatrix * p;
}
