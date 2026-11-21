#version 120
uniform vec3 uOrigin;
varying vec2 vUv;
varying vec4 vColor;
varying vec3 vPos;
varying vec2 vLight;
void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    vUv = gl_MultiTexCoord0.st;
    vColor = gl_Color;
    vPos = gl_Vertex.xyz - uOrigin;
    vLight = (gl_TextureMatrix[1] * gl_MultiTexCoord1).st;
}
