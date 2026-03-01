layout(location = 0) out vec4 outColour0;
layout(location = 1) out vec4 outColour1;
layout(location = 2) out vec4 outColour2;

void voxy_emitFragment(VoxyFragmentParameters parameters) {
    vec4 colour = parameters.sampledColour;

    uint tintingFunction = tintingState();
    bool doTint = tintingFunction == 2u;
    if (tintingFunction == 1u) {
        vec4 tintTest = textureLod(blockModelAtlas, parameters.uv, 0);
        if (abs(tintTest.r - tintTest.g) < 0.02 && abs(tintTest.g - tintTest.b) < 0.02) {
            doTint = true;
        }
    }
    if (doTint) {
        colour *= parameters.tinting;
    }

    vec3 n = normalize(vWorldNormal);
    vec4 lit = colour * getLighting(interData.y);
    lit += vec4(0.0, 0.0, 0.0, float(interData.w & 0xFFu) / 255.0);

    float fogAlpha = sqrt(clamp(lit.a, 0.0, 1.0));
    outColour0 = lit;
    outColour1 = vec4(n, fogAlpha);
    outColour2 = vec4(lit.rgb * 0.08, 1.0);
}
