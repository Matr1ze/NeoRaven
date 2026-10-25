#version 120

// The glint pass draws the block's baked model through the fixed-function pipeline, so position,
// colour and texture coordinates arrive on the built-in attributes. `uOrigin` is the block's own
// minimum corner in the same camera-relative space the tessellator was translated into, which is
// what turns the vertex into a block-local coordinate the fragment stage can work in.

uniform vec4 uOrigin;
uniform vec4 uWorld;

varying vec3 patternPos;
varying float objectY;
varying vec3 cameraRelative;

void main() {
    vec4 viewPosition = gl_ModelViewMatrix * gl_Vertex;

    gl_Position = gl_ProjectionMatrix * viewPosition;


    vec3 local = gl_Vertex.xyz - uOrigin.xyz;

    // The block's own coordinates carry the aurora, wrapped to a short period so neighbouring
    // blocks stay continuous with each other and the noise never runs out of precision.
    patternPos = local + uWorld.xyz;
    objectY = gl_Vertex.y;

    cameraRelative = viewPosition.xyz;

    gl_FogFragCoord = length(viewPosition.xyz);
}
