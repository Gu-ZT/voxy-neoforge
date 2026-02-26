#define QUAD_FORMAT_VERSION 2u
#define QUAD_WORD_COUNT 2u

#ifndef Quad
#define Quad uvec4
#endif

// Quad layout v2 (128-bit):
//   quad.xy = legacy 64-bit word A (compatible with v1 decode)
//   quad.zw = lighting payload word B (unused in phase 1)
uint Eu32v2(Quad data, int amount, int shift) {
    if (shift > 31) {
        shift -= 32;
        return (data.y >> uint(shift)) & ((1u << uint(amount)) - 1u);
    } else {
        return (data.x >> uint(shift)) & ((1u << uint(amount)) - 1u);
    }
}

vec3 extractPos(Quad quad) {
    return vec3(Eu32v2(quad, 5, 21), Eu32v2(quad, 5, 16), Eu32v2(quad, 5, 11));
}

ivec2 extractSize(Quad quad) {
    return ivec2(Eu32v2(quad, 4, 3), Eu32v2(quad, 4, 7)) + ivec2(1);
}

uint extractFace(Quad quad) {
    return Eu32v2(quad, 3, 0);
}

uint extractStateId(Quad quad) {
    return Eu32v2(quad, 6, 26) | (Eu32v2(quad, 14, 32) << 6);
}

uint extractBiomeId(Quad quad) {
    return Eu32v2(quad, 9, 46);
}

uint extractLightId(Quad quad) {
    return Eu32v2(quad, 8, 55);
}

bool isQuadEmpty(Quad quad) {
    return all(equal(quad, uvec4(0)));
}
