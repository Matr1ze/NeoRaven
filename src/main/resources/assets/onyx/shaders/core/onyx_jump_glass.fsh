#version 120

// The scene bent and softened inside the discs the JumpCircles rings have swept out, so a ring
// leaves a lens lying on the floor behind it rather than a flat decal: the ground under it goes
// soft, and near the rim it slides outwards the way it would through the edge of thick glass.
//
// Every fragment is turned back into a point on the ground: a ray is fired from the camera through
// it and met with each circle's own horizontal plane, and the hit is compared against the circle in
// blocks. That is why this does not work in screen space - a disc on the ground projects to an
// ellipse only while the whole of it is in front of the camera, and at a grazing angle it opens
// into a hyperbola that no screen-space circle test can follow.
//
// Nothing is depth-tested: the framebuffer copy is all this pass has, so whatever stands inside a
// disc is warped along with it.

#define MAX_LENSES 8
#define TAPS 9
#define GOLDEN 2.39996323

uniform sampler2D Sampler0;
uniform vec2 uResolution;

uniform int uCount;
uniform float uBlur;
uniform float uWarp;

// The camera basis in world directions, and the half-extents of the frustum at unit depth.
uniform vec3 uForward;
uniform vec3 uRight;
uniform vec3 uUp;
uniform vec2 uTanHalf;

// xyz: the circle's centre relative to the camera, in blocks. w: its current radius.
uniform vec4 uLensA[MAX_LENSES];
// x: how much of the lens is left, 1 at birth down to 0 as it dies. y: roughly how wide it is on
// screen in uv, which is what the warp and the blur are scaled by.
uniform vec4 uLensB[MAX_LENSES];

varying vec2 texCoord;

/** Golden-angle spiral, so a handful of taps still reads as a smooth blur rather than as ghosts. */
vec3 soften(vec2 uv, float radius, vec2 aspect) {
    vec3 sum = texture2D(Sampler0, uv).rgb;

    for (int i = 0; i < TAPS; i++) {
        float angle = float(i) * GOLDEN;
        float reach = sqrt((float(i) + 0.5) / float(TAPS)) * radius;

        sum += texture2D(Sampler0, clamp(uv + vec2(cos(angle), sin(angle)) * reach * aspect,
                                         vec2(0.0), vec2(1.0))).rgb;
    }

    return sum / float(TAPS + 1);
}

void main() {
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 ray = uForward + uRight * (ndc.x * uTanHalf.x) + uUp * (ndc.y * uTanHalf.y);

    // Away from the horizon this is the height the ray loses per unit travelled; on it, nothing.
    float fall = ray.y;
    float safeFall = abs(fall) < 1e-5 ? 1e-5 : fall;

    vec2 warp = vec2(0.0);
    float blur = 0.0;
    float glint = 0.0;

    for (int i = 0; i < MAX_LENSES; i++) {
        // The array is only filled as far as the frame's visible circles go.
        if (i >= uCount) break;

        vec4 lens = uLensA[i];
        vec4 look = uLensB[i];

        // Masked rather than skipped from here down, which keeps every divide below away from a zero.
        float live = (lens.w > 0.0) ? 1.0 : 0.0;

        // Where this fragment's ray meets the ground the circle lies on.
        float travel = lens.y / safeFall;
        vec3 hit = ray * travel;

        vec2 offset = hit.xz - lens.xz;
        float radial = length(offset) / max(lens.w, 1e-4);

        // Behind the camera, or on the far side of the horizon, means the ray never reaches it.
        float ahead = (travel > 0.0) ? 1.0 : 0.0;

        float edge = smoothstep(1.0, 0.88, radial) * look.x * live * ahead;
        // Flat through the middle and steepest at the rim, the way a dome of glass refracts.
        float slope = radial * radial * (3.0 - 2.0 * clamp(radial, 0.0, 1.0));

        // The bend runs outwards from the centre along the ground, carried onto the screen through
        // the camera's own axes so it still points the right way at any angle.
        vec2 screen = vec2(dot(vec3(offset.x, 0.0, offset.y), uRight),
                           dot(vec3(offset.x, 0.0, offset.y), uUp));

        warp -= normalize(screen + vec2(1e-6)) * (slope * edge * uWarp * look.y);
        blur = max(blur, edge * (0.3 + 0.7 * slope) * uBlur * look.y);
        glint = max(glint, edge * slope);
    }

    if (blur <= 0.0 && dot(warp, warp) <= 0.0) {
        gl_FragColor = vec4(texture2D(Sampler0, texCoord).rgb, 1.0);
        return;
    }

    // Blur radii are given in uv, so the shorter axis has to be scaled or the discs blur elliptically.
    vec2 aspect = vec2(uResolution.y / uResolution.x, 1.0);
    vec2 uv = clamp(texCoord + warp, vec2(0.0), vec2(1.0));

    vec3 color = soften(uv, blur, aspect);

    // A red/blue split along the bend, which is what sells the warp as glass rather than as a smear.
    vec2 split = warp * 0.3;
    color.r = mix(color.r, texture2D(Sampler0, clamp(uv + split, vec2(0.0), vec2(1.0))).r, 0.6);
    color.b = mix(color.b, texture2D(Sampler0, clamp(uv - split, vec2(0.0), vec2(1.0))).b, 0.6);

    // Faint lift where the glass is thickest, so the rim catches a little light of its own.
    color *= 1.0 + glint * 0.12;

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
