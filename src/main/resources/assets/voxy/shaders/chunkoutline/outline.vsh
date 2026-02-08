#version 460

layout(binding = 0, std140) uniform SceneUniform {
    mat4 MVP;
    ivec4 section;
    vec4 negInnerSec;
    int boundaryBuffer;  // Configurable safety margin (0-4 blocks)
};

layout(binding = 1, std430) restrict readonly buffer ChunkPosBuffer {
    ivec2[] chunkPos;
};

ivec3 unpackPos(ivec2 pos) {
    return ivec3(pos.y>>10, (pos.x<<12)>>12, ((pos.y<<22)|int(uint(pos.x)>>10))>>10);
}

bool shouldRender(ivec3 icorner) {
    // MC 1.21.1 NeoForge: Configurable boundary buffer for LOD/vanilla chunk transition
    //
    // The buffer SHRINKS the "vanilla present" detection range, causing LODs to render
    // in a LARGER area (overlapping with vanilla edges). Depth testing hides the overlap.
    //
    // boundaryBuffer (0-4 blocks):
    //   0 = exact match with Embeddium (may have gaps at boundaries)
    //   1-4 = shrink detection range, more LOD overlap (smoother transition)
    //
    // FIX: Apply buffer to distance threshold, not corner positions.
    // This provides uniform overlap at ALL angles including diagonals.
    // Previous approach shrunk corners per-axis, giving only buf/sqrt(2) at 45°.

    // Chunk AABB corners (original, no shrinkage)
    ivec3 minCorner = icorner;
    ivec3 maxCorner = icorner + 16;

    // Select closest corner of AABB to camera (handles all quadrants)
    vec3 corner = vec3(mix(mix(ivec3(0), minCorner, greaterThan(minCorner, ivec3(0))), maxCorner, lessThan(maxCorner, ivec3(0)))) - negInnerSec.xyz;

    // MC Java Edition uses SQUARE render distance (chunk grid), not circular.
    // Chebyshev distance (max of abs) matches vanilla's square pattern.
    // Shrink the threshold by buffer - uniform at all angles including diagonals.
    float effectiveRange = negInnerSec.w - float(boundaryBuffer);
    bool visible = max(abs(corner.x), abs(corner.z)) < effectiveRange;
    visible = visible && abs(corner.y) < effectiveRange;
    return visible;
}

#ifdef TAA
vec2 getTAA();
#endif

void main() {
    uint id = (gl_InstanceID<<5)+gl_BaseInstance+(gl_VertexID>>3);

    ivec3 origin = unpackPos(chunkPos[id])*16;
    origin -= section.xyz;

    if (!shouldRender(origin)) {
        gl_Position = vec4(-100.0f, -100.0f, -100.0f, 0.0f);
        return;
    }

    ivec3 cubeCornerI = ivec3(gl_VertexID&1, (gl_VertexID>>2)&1, (gl_VertexID>>1)&1)*16;
    //Expand the y height to be big (will be +- 8192)
    //TODO: make it W.R.T world height and offsets
    //cubeCornerI.y = cubeCornerI.y*1024-512;
    gl_Position = MVP * vec4(vec3(cubeCornerI+origin), 1);
    gl_Position.z -= 0.0005f;

    #ifdef TAA
    gl_Position.xy += getTAA()*gl_Position.w;//Apply TAA if we have it
    #endif
}
