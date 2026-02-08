#version 460 core

#ifdef USE_SINGLE_TRI
#define USE_NV_BARRY
#endif

#ifdef USE_NV_BARRY
#extension GL_NV_fragment_shader_barycentric: require
#endif

layout(binding = 0) uniform sampler2D blockModelAtlas;

layout(location = 0) in flat uvec4 interData;
#ifndef USE_NV_BARRY
layout(location = 1) in vec2 uv;
#endif

bool useDiscard() {
    return (interData.x & 1u) == 1u;
}

uint getFace() {
    return (interData.x >> 4) & 7u;
}

uint getModelId() {
    return interData.x >> 16;
}

vec2 getBaseUV() {
    uint face = getFace();
    uint modelId = getModelId();
    vec2 modelUV = vec2(modelId & 0xFFu, (modelId >> 8) & 0xFFu) * (1.0 / 256.0);
    return modelUV + (vec2(face >> 1, face & 1u) * (1.0 / (vec2(3.0, 2.0) * 256.0)));
}

void main() {
    vec2 tile;

    #ifdef USE_NV_BARRY
    #ifdef USE_SINGLE_TRI
    if (gl_BaryCoordNV.x >= 0.5 || gl_BaryCoordNV.y >= 0.5) discard;
    vec2 uv = gl_BaryCoordNV.yx * (vec2((interData.x >> 8) & 0xFu, (interData.x >> 12) & 0xFu) + 1) * 2;
    #else
    vec2 uv = mix(gl_BaryCoordNV.yx, 1 - gl_BaryCoordNV.xz, gl_PrimitiveID & 1) *
            (vec2((interData.x >> 8) & 0xFu, (interData.x >> 12) & 0xFu) + 1);
    #endif
    #endif

    vec2 uv2 = modf(uv, tile) * (1.0 / (vec2(3.0, 2.0) * 256.0));
    vec2 texPos = uv2 + getBaseUV();

    if (gl_HelperInvocation) {
        return;
    }

    if (any(notEqual(clamp(tile, vec2(0), vec2((interData.x >> 8) & 0xFu, (interData.x >> 12) & 0xFu)), tile))) {
        discard;
        return;
    }

    // Opaque/cutout: only discard when the quad is tagged for discard.
    if (useDiscard() && (textureLod(blockModelAtlas, texPos, 0).a <= 0.1f)) {
        discard;
        return;
    }
}
