#version 120
varying vec2 vUv;
varying vec4 vColor;
varying vec2 vLight;
varying float vShade;
void main() {
    gl_Position = ftransform();
    vUv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).st;
    vColor = gl_Color;
    vLight = (gl_TextureMatrix[1] * gl_MultiTexCoord1).st;
    vec3 n = normalize(gl_NormalMatrix * gl_Normal);
    float d = max(0.0, dot(n, normalize(gl_LightSource[0].position.xyz))) * gl_LightSource[0].diffuse.r
            + max(0.0, dot(n, normalize(gl_LightSource[1].position.xyz))) * gl_LightSource[1].diffuse.r;
    vShade = min(1.0, gl_LightModel.ambient.r + d);
}
