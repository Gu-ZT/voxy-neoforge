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
    BlockModel model = modelData[parameters.modelId];
    float faceTint = modelIsShaded(model) ?
        (n.y > 0.0 ? UP_FACE_TINT : (n.y < 0.0 ? DOWN_FACE_TINT : (abs(n.z) > 0.5 ? Z_AXIS_FACE_TINT : X_AXIS_FACE_TINT)))
        : NO_SHADE_FACE_TINT;

    vec4 lit = colour * getLighting(interData.y);
    lit.rgb *= faceTint;
    lit += vec4(0.0, 0.0, 0.0, float(interData.w & 0xFFu) / 255.0);
#ifndef TRANSLUCENT
    lit.a = 1.0;
#endif

    float skyLightFactor = clamp(parameters.lightMap.y, 0.0, 1.0);
    outColour0 = lit;
    outColour1 = vec4(0.20, 0.0, skyLightFactor, 1.0);
    outColour2 = vec4(n, 1.0);
}
