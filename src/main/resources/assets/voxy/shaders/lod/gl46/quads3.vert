#version 460 core
#extension GL_ARB_gpu_shader_int64 : enable

#define QUAD_BUFFER_BINDING 1
#define MODEL_BUFFER_BINDING 3
#define MODEL_COLOUR_BUFFER_BINDING 4
#define POSITION_SCRATCH_BINDING 5
#define LIGHTING_SAMPLER_BINDING 1

#ifdef USE_SINGLE_TRI
#define USE_NV_BARRY
#endif

#import <voxy:lod/quad_format.glsl>
#import <voxy:lod/block_model.glsl>
#import <voxy:lod/gl46/bindings.glsl>
#import <voxy:lod/quad_util.glsl>

layout(location = 0) out flat uvec4 interData;
#ifndef USE_NV_BARRY
layout(location = 1) out vec2 uv;
#endif
// View-space position and world-space normal emitted for shader-pack lighting in PATCHED_SHADER mode.
// Non-patched path doesn't use these but declaring them here is harmless (unused varyings are optimised out).
layout(location = 5) out vec3 vViewPos;
layout(location = 6) out flat vec3 vWorldNormal;

#ifdef DEBUG_RENDER
layout(location = 7) out flat uint quadDebug;
#endif

vec2 taaShift();

//TODO: add a mechanism so that some quads can ignore backface culling
// this would help alot with stuff like crops as they would look kinda weird i think,
// same with flowers etc
void main() {
    taaOffset = taaShift();

    QuadData quad;
    setupQuad(quad, quadData[uint(gl_VertexID)>>2], positionBuffer[gl_BaseInstance], (gl_VertexID&3) == 1);

    uint cornerId = gl_VertexID&3;
    gl_Position = getQuadCornerPos(quad, cornerId);

    #ifndef USE_NV_BARRY
    uv = getCornerUV(quad, cornerId);
    #endif

    //Note: other data is automatically discarded as it is undefiend and has not been generated
    interData = quad.attributeData;

    // Emit view-space position for shader-pack lighting. MVP = projection * modelView,
    // so extract the view-space pos by multiplying world pos by modelView only.
    // We reconstruct modelView as MVP * inverse(projection) — but that's expensive.
    // Instead, emit world-space pos as vViewPos and let the patch convert using iris_ModelViewMatrix.
    // This is close enough for directional lighting; packs can normalize it themselves.
    vec3 worldPos = quad.basePoint;  // world-relative (camera at origin) position of vertex
    vViewPos = worldPos;

    // World-space normal from face direction (face encoding: axis = face>>1, sign = face&1)
    uint face = (interData.x >> 4) & 7u;
    uint axis = face >> 1u;
    float sign = float(int(face & 1u)) * 2.0 - 1.0;
    vWorldNormal = vec3(float(axis == 2u), float(axis == 0u), float(axis == 1u)) * sign;


    #ifdef DEBUG_RENDER
    quadDebug = uint(gl_VertexID)>>(2+5);
    #endif
}

#ifndef TAA_PATCH
vec2 taaShift() {return vec2(0.0);}
#endif