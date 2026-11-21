#version 120
uniform vec3 uRight;
uniform vec3 uUp;
uniform vec3 uBack;
uniform vec3 uEye;
varying vec2 vUv;
varying vec4 vColor;
varying vec3 vWorld;
varying vec2 vLight;
void main() {
    vec4 view = gl_ModelViewMatrix * gl_Vertex;
    gl_Position = gl_ProjectionMatrix * view;
    vWorld = view.x * uRight + view.y * uUp + view.z * uBack + uEye;
    vUv = gl_MultiTexCoord0.st;
    vColor = gl_Color;
    vLight = (gl_TextureMatrix[1] * gl_MultiTexCoord1).st;
}
