#version 120

varying vec3 skyDir;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    skyDir = gl_Vertex.xyz;
}
