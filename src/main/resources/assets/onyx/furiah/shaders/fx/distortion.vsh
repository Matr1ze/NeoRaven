#version 120
varying vec2 vOffset;
varying vec4 vColor;
varying float vFresnel;
void main() {
    vec4 clip = gl_ModelViewProjectionMatrix * gl_Vertex;
    vec3 n = gl_Normal;
    // The scene is sampled where a point pulled in along the normal lands on screen: the shell bends
    // the view like a lens, more towards its rim.
    vec4 bent = gl_ModelViewProjectionMatrix * vec4(gl_Vertex.xyz - n * gl_MultiTexCoord0.x, 1.0);
    vOffset = (bent.w > 0.01 && clip.w > 0.01) ? bent.xy / bent.w - clip.xy / clip.w : vec2(0.0);
    vec3 viewPos = (gl_ModelViewMatrix * gl_Vertex).xyz;
    vec3 viewN = normalize(gl_NormalMatrix * n);
    vFresnel = pow(1.0 - abs(dot(normalize(-viewPos), viewN)), 2.5) * gl_MultiTexCoord0.y;
    vColor = gl_Color;
    gl_Position = clip;
}
