package me.cortex.voxy.client.iris;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import me.cortex.voxy.common.Logger;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import org.lwjgl.opengl.ARBDrawBuffersBlend;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL33.*;

public class IrisShaderPatch {
    public static final int VERSION = ((IntSupplier)()->1).getAsInt();

    private static volatile CompatibilityMode activeCompatibilityMode;

    public static void updateActiveCompatibility(IrisShaderPatch patch) {
        if (patch == null) {
            activeCompatibilityMode = null;
            return;
        }
        activeCompatibilityMode = patch.getCompatibilityMode();
    }

    public static boolean shouldApplyGlobalFogOverride() {
        CompatibilityMode mode = activeCompatibilityMode;
        return mode != CompatibilityMode.VOXY_PATCH;
    }

    public enum CompatibilityMode {
        VOXY_PATCH,
        FALLBACK
    }

    private static final class SSBODeserializer implements JsonDeserializer<Int2ObjectOpenHashMap<String>> {
        @Override
        public Int2ObjectOpenHashMap<String> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            Int2ObjectOpenHashMap<String> ret = new Int2ObjectOpenHashMap<>();
            if (json==null) return null;
            try {
                for (var entry : json.getAsJsonObject().entrySet()) {
                    ret.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsString());
                }
            } catch (Exception e) {
                Logger.error(e);
            }
            return ret;
        }
    }
    private static final class SamplerDeserializer implements JsonDeserializer<Object2ObjectLinkedOpenHashMap<String, String>> {
        @Override
        public Object2ObjectLinkedOpenHashMap<String, String> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            Object2ObjectLinkedOpenHashMap<String, String> ret = new Object2ObjectLinkedOpenHashMap<>();
            if (json==null) return null;
            try {
                if (json.isJsonArray()) {
                    for (var entry : json.getAsJsonArray()) {
                        var name = entry.getAsString();
                        var type = "sampler2D";
                        if (name.matches("shadowtex")) {
                            type = "sampler2DShadow";
                        }
                        ret.put(name, type);
                    }
                } else {
                    for (var entry : json.getAsJsonObject().entrySet()) {
                        String type = "sampler2D";
                        if (entry.getValue().isJsonNull()) {
                            if (entry.getKey().matches("shadowtex")) {
                                type = "sampler2DShadow";
                            }
                        } else {
                            type = entry.getValue().getAsString();
                        }
                        ret.put(entry.getKey(), type);
                    }
                }
            } catch (Exception e) {
                Logger.error(e);
            }
            return ret;
        }
    }

    public record BlendState(int buffer, boolean off, int sRGB, int dRGB, int sA, int dA) {
        public static BlendState ALL_OFF = new BlendState(-1, true, 0,0,0,0);
    }


    private static final class BlendStateDeserializer implements JsonDeserializer<Int2ObjectMap<BlendState>> {
        private static int parseType(String type) {
            type = type.toUpperCase();
            if (!type.startsWith("GL_")) {
                type = "GL_"+type;
            }
            return switch (type) {
                case "GL_ZERO" -> GL_ZERO;
                case "GL_ONE" -> GL_ONE;
                case "GL_SRC_COLOR" -> GL_SRC_COLOR;
                case "GL_ONE_MINUS_SRC_COLOR" -> GL_ONE_MINUS_SRC_COLOR;
                case "GL_SRC_ALPHA" -> GL_SRC_ALPHA;
                case "GL_ONE_MINUS_SRC_ALPHA" -> GL_ONE_MINUS_SRC_ALPHA;
                case "GL_DST_ALPHA" -> GL_DST_ALPHA;
                case "GL_ONE_MINUS_DST_ALPHA" -> GL_ONE_MINUS_DST_ALPHA;
                case "GL_DST_COLOR" -> GL_DST_COLOR;
                case "GL_ONE_MINUS_DST_COLOR" -> GL_ONE_MINUS_DST_COLOR;
                case "GL_SRC_ALPHA_SATURATE" -> GL_SRC_ALPHA_SATURATE;
                case "GL_SRC1_COLOR" -> GL_SRC1_COLOR;
                case "GL_ONE_MINUS_SRC1_COLOR" -> GL_ONE_MINUS_SRC1_COLOR;
                case "GL_ONE_MINUS_SRC1_ALPHA" -> GL_ONE_MINUS_SRC1_ALPHA;
                default -> {
                    Logger.error("Unknown blend option " + type);
                    yield -1;
                }
            };
        }
        @Override
        public Int2ObjectMap<BlendState> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json==null) return null;
            Int2ObjectMap<BlendState> ret = new Int2ObjectOpenHashMap<>();
            try {
                if (json.isJsonPrimitive()) {
                    if (json.getAsString().equalsIgnoreCase("off")) {
                        ret.put(-1, BlendState.ALL_OFF);
                        return ret;
                    }
                } else if (json.isJsonObject()) {
                    for (var entry : json.getAsJsonObject().entrySet()) {
                        int buffer = Integer.parseInt(entry.getKey());
                        BlendState state = null;
                        var val = entry.getValue();
                        List<String> bs = null;
                        if (val.isJsonArray()) {
                            bs = val.getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
                        } else if (val.isJsonPrimitive()) {
                            var str = val.getAsString();
                            if (str.equalsIgnoreCase("off")) {
                                state = new BlendState(buffer, true, 0,0,0,0);
                            } else {
                                var parts = str.split(" ");
                                if (parts.length < 4) {
                                    state = new BlendState(buffer, true, -1, -1, -1, -1);
                                } else {
                                    bs = List.of(parts);
                                }
                            }
                        } else {
                            Logger.error("Unknown blend state "+val);
                            state = null;
                        }
                        if (bs != null) {
                            int[] v = bs.stream().mapToInt(BlendStateDeserializer::parseType).toArray();
                            state = new BlendState(buffer, false, v[0], v[1], v[2], v[3]);
                        }
                        ret.put(buffer, state);
                    }
                    return ret;
                }
            } catch (Exception e) {
                Logger.error(e);
            }
            Logger.error("Failed to parse blend state: " + json);
            return ret;
        }
    }

    private static class PatchGson {
        public int version;//TODO maybe replace with semver?
        public int[] opaqueDrawBuffers;
        public int[] translucentDrawBuffers;
        public String[] uniforms;
        @JsonAdapter(SamplerDeserializer.class)
        public Object2ObjectLinkedOpenHashMap<String, String> samplers;
        public String opaquePatchData;
        public String translucentPatchData;
        @JsonAdapter(SSBODeserializer.class)
        public Int2ObjectOpenHashMap<String> ssbos;
        @JsonAdapter(BlendStateDeserializer.class)
        public Int2ObjectOpenHashMap<BlendState> blending;
        public String taaOffset;
        public boolean excludeLodsFromVanillaDepth;
        public float[] renderScale;
        public boolean useViewportDims;
        //public boolean deferTranslucentRendering;
        public String checkValid() {
            if (this.blending != null) {
                int i = 0;
                for (BlendState state : this.blending.values()) {
                    if (state.buffer != -1 && (state.buffer<0||this.translucentDrawBuffers.length<=state.buffer)) {
                        if (state.buffer<0) {
                            return "Blending buffer is <0 at index: " + i;
                        } else {
                            return "Blending buffer index out of bounds at "+i+" was "+state.buffer+" maximum is " +(this.translucentDrawBuffers.length-1);
                        }
                    }
                    i++;
                }
            }
            if (this.opaquePatchData == null) {
                return "Opaque patch data is null";
            }
            if (this.uniforms == null) {
                return "Uniforms are null";
            }
            if (this.opaqueDrawBuffers == null) {
                return "Opaque draw buffers are null";
            }
            if (this.translucentDrawBuffers == null) {
                return "Translucent draw buffers are null";
            }
            return null;
        }
    }



    private final PatchGson patchData;
    private final ShaderPack pack;
    private final Int2ObjectMap<String> ssbos;
    private final CompatibilityMode compatibilityMode;

    private IrisShaderPatch(PatchGson patchData, ShaderPack pack, CompatibilityMode compatibilityMode) {
        this.patchData = patchData;
        this.pack = pack;
        this.compatibilityMode = compatibilityMode;

        if (patchData.ssbos == null) {
            this.ssbos = new Int2ObjectOpenHashMap<>();
        } else {
            this.ssbos = patchData.ssbos;
        }
    }

    public CompatibilityMode getCompatibilityMode() {
        return this.compatibilityMode;
    }

    public boolean useViewportDims() {
        return this.patchData.useViewportDims;
    }

    public Int2ObjectMap<String> getSSBOs() {
        return new Int2ObjectLinkedOpenHashMap<>(this.ssbos);
    }
    public String getPatchOpaqueSource() {
        return this.patchData.opaquePatchData;
    }
    public String getPatchTranslucentSource() {
        return this.patchData.translucentPatchData;
    }
    public String getTAAShift() {
        return this.patchData.taaOffset == null?"{return vec2(0.0);}":this.patchData.taaOffset;
    }
    public String[] getUniformList() {
        return this.patchData.uniforms;
    }
    public Object2ObjectLinkedOpenHashMap<String, String> getSamplerSet() {
        return this.patchData.samplers;
    }


    public int[] getOpqaueTargets() {
        return this.patchData.opaqueDrawBuffers;
    }

    public int[] getTranslucentTargets() {
        return this.patchData.translucentDrawBuffers;
    }

    public boolean emitToVanillaDepth() {
        return !this.patchData.excludeLodsFromVanillaDepth;
    }

    public float[] getRenderScale() {
        if (this.patchData.renderScale == null || this.patchData.renderScale.length==0) {
            return new float[]{1,1};
        }
        if (this.patchData.renderScale.length == 1) {
            return new float[]{this.patchData.renderScale[0],this.patchData.renderScale[0]};
        }
        return new float[]{Math.max(0.01f,this.patchData.renderScale[0]),Math.max(0.01f,this.patchData.renderScale[1])};
    }

    public boolean deferedTranslucentRendering() {
        return false;//this.patchData.deferTranslucentRendering;
    }

    public Runnable createBlendSetup() {
        if (this.patchData.blending == null || this.patchData.blending.isEmpty()) {
            return ()->{};//No blending change
        }
        return ()->{
            final var BS = this.patchData.blending;
            //Set inital state
            var init = BS.getOrDefault(-1, null);
            if (init != null) {
                if (init.off) {
                    glDisable(GL_BLEND);
                } else {
                    glEnable(GL_BLEND);
                    glBlendFuncSeparate(init.sRGB, init.dRGB, init.sA, init.dA);
                }
            }
            for (var entry:BS.int2ObjectEntrySet()) {
                if (entry.getIntKey() == -1) continue;
                final var s = entry.getValue();
                if (s.off) {
                    glDisablei(GL_BLEND, s.buffer);
                } else {
                    glEnablei(GL_BLEND, s.buffer);
                    //_sigh_ thanks nvidia
                    ARBDrawBuffersBlend.glBlendFuncSeparateiARB(s.buffer, s.sRGB, s.dRGB, s.sA, s.dA);
                }
            }
        };
    }

    private static final Gson GSON = createLenientGson();

    private static Gson createLenientGson() {
        GsonBuilder builder = new GsonBuilder()
                .excludeFieldsWithModifiers(Modifier.PRIVATE);

        try {
            // Gson 2.11+ strictness API
            Class<?> strictness = Class.forName("com.google.gson.Strictness");
            Object lenient = strictness.getField("LENIENT").get(null);
            builder.getClass().getMethod("setStrictness", strictness).invoke(builder, lenient);
        } catch (Throwable ignored) {
            // Fall back to legacy lenient mode if available
            try {
                builder.getClass().getMethod("setLenient").invoke(builder);
            } catch (Throwable ignoredAgain) {
                // No lenient mode available; proceed with defaults
            }
        }

        return builder.create();
    }

    public static IrisShaderPatch makeFallbackPatch(ShaderPack pack, ProgramSet programSet) {
        boolean hasDhBufferPrograms = hasDhPrograms(programSet);

        int[] opaqueBuffers = hasDhBufferPrograms
                ? resolveDrawBuffers(programSet, ProgramId.DhTerrain, ProgramId.TerrainSolid, ProgramId.Terrain, ProgramId.Basic)
                : resolveDrawBuffers(programSet, ProgramId.TerrainSolid, ProgramId.Terrain, ProgramId.Basic);
        int[] translucentBuffers = hasDhBufferPrograms
                ? resolveDrawBuffers(programSet, ProgramId.DhWater, ProgramId.DhTerrain, ProgramId.Water, ProgramId.BlockTrans, ProgramId.Terrain)
                : resolveDrawBuffers(programSet, ProgramId.Water, ProgramId.BlockTrans, ProgramId.Terrain);

        PatchGson patchData = new PatchGson();
        patchData.version = VERSION;
        patchData.opaqueDrawBuffers = opaqueBuffers;
        patchData.translucentDrawBuffers = translucentBuffers;
        patchData.uniforms = new String[]{"sunAngle", "worldTime"};
        patchData.samplers = new Object2ObjectLinkedOpenHashMap<>();
        patchData.opaquePatchData = buildFallbackPatch(opaqueBuffers);
        patchData.translucentPatchData = buildFallbackPatch(translucentBuffers);
        patchData.excludeLodsFromVanillaDepth = false;
        patchData.useViewportDims = true;
        CompatibilityMode fallbackMode = CompatibilityMode.FALLBACK;
        Logger.info("[IrisShaderPatch] makeFallbackPatch:"
                + " mode=" + fallbackMode
                + " hasDhPrograms=" + hasDhBufferPrograms
                + " opaqueBuffers=" + java.util.Arrays.toString(opaqueBuffers)
                + " translucentBuffers=" + java.util.Arrays.toString(translucentBuffers));
        return new IrisShaderPatch(patchData, pack, fallbackMode);
    }

    private static boolean hasDhPrograms(ProgramSet programSet) {
        return programSet.get(ProgramId.DhTerrain).isPresent()
                || programSet.get(ProgramId.DhWater).isPresent()
                || programSet.get(ProgramId.DhGeneric).isPresent()
                || programSet.get(ProgramId.DhShadow).isPresent();
    }

    private static int[] resolveDrawBuffers(ProgramSet programSet, ProgramId... ids) {
        for (ProgramId id : ids) {
            var source = programSet.get(id);
            if (source.isEmpty()) {
                continue;
            }
            ProgramDirectives directives = source.get().getDirectives();
            int[] buffers = directives.getDrawBuffers();
            if (buffers != null && buffers.length > 0) {
                return buffers;
            }
        }
        return new int[]{0};
    }

    private static int findBufferSlot(int[] buffers, int bufferId) {
        if (buffers == null) {
            return -1;
        }
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] == bufferId) {
                return i;
            }
        }
        return -1;
    }

    private static String buildFallbackPatch(int[] drawBuffers) {
        int outputs = Math.max(1, drawBuffers == null ? 1 : drawBuffers.length);

        // Heuristics for common shader-pack layouts:
        // - Color usually lands in colortex0.
        // - Some packs (e.g. Complementary) route material/light data to colortex6, not normals.
        // - Normals commonly land in colortex4 (Complementary) or colortex1 (classic).
        int colorSlot = findBufferSlot(drawBuffers, 0);
        if (colorSlot < 0) {
            colorSlot = 0;
        }
        int normalSlot = findBufferSlot(drawBuffers, 4);
        if (normalSlot < 0) {
            normalSlot = findBufferSlot(drawBuffers, 1);
        }
        if (normalSlot < 0) {
            normalSlot = findBufferSlot(drawBuffers, 2);
        }

        int lightSlot = findBufferSlot(drawBuffers, 6);
        if (lightSlot < 0) {
            lightSlot = findBufferSlot(drawBuffers, 2);
        }
        if (lightSlot < 0) {
            lightSlot = findBufferSlot(drawBuffers, 1);
        }
        if (lightSlot == normalSlot || lightSlot == colorSlot) {
            lightSlot = -1;
        }

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < outputs; i++) {
            builder.append("layout(location = ").append(i).append(") out vec4 outColour").append(i).append(";\n");
        }

        // NOTE: vViewPos (location=5) and vWorldNormal (location=6) are already declared
        // in quads.frag (the base shader). Do NOT re-declare them here — duplicate
        // layout(location=N) in declarations are a GLSL compile error (C1038).

        builder.append("\nvoid voxy_emitFragment(VoxyFragmentParameters parameters) {\n")
                .append("    vec4 colour = parameters.sampledColour;\n")
                // Block/biome tinting (same heuristic as the non-patched path)
                .append("    uint tintingFunction = tintingState();\n")
                .append("    bool doTint = tintingFunction==2u;\n")
                .append("    if (tintingFunction==1u) {\n")
                .append("        vec4 tintTest = textureLod(blockModelAtlas, parameters.uv, 0);\n")
                .append("        if (abs(tintTest.r-tintTest.g) < 0.02f && abs(tintTest.g-tintTest.b) < 0.02f) {\n")
                .append("            doTint = true;\n")
                .append("        }\n")
                .append("    }\n")
                .append("    if (doTint) {\n")
                .append("        colour *= parameters.tinting;\n")
                .append("    }\n\n")
                // MC lightmap gives block+sky light. Reconstruct directional sun lighting from sunAngle
                // and the per-fragment world-space normal (vWorldNormal from vertex shader).
                .append("    vec4 light = texture(lightSampler, parameters.lightMap);\n")
                .append("    BlockModel model = modelData[parameters.modelId];\n")
                .append("    bool isShaded = modelIsShaded(model);\n\n")
                // Reconstruct approximate sun direction from sunAngle.
                // sunAngle: 0=sunrise, 0.25=noon, 0.5=sunset, 0.75=midnight.
                // Sun travels in the X-Z plane rotated 90°; approximate as XY arc.
                // sunAngle→radians for a half-circle: angle = sunAngle * 2π, sun at (sin, cos, 0).
                // Clamp cos(elevation) to 0 below horizon.
                .append("    float sunRad = sunAngle * 6.28318;\n")
                .append("    vec3 sunDir = normalize(vec3(sin(sunRad), cos(sunRad), 0.0));\n")
                .append("    float moonRad = sunRad + 3.14159;\n")
                .append("    vec3 moonDir = normalize(vec3(sin(moonRad), cos(moonRad), 0.0));\n")
                // Dot product of fragment normal with sun/moon direction → directional contribution
                .append("    float sunDot  = max(0.0, dot(vWorldNormal, sunDir));\n")
                .append("    float moonDot = max(0.0, dot(vWorldNormal, moonDir)) * 0.15;\n")
                // Ambient: sky light channel from lightmap already carries sky-influenced ambient.
                // We scale the additional directional term by the sky light level so underground/covered
                // blocks don't get lit by a sun they can't see.
                .append("    float skyLight = parameters.lightMap.y;\n")
                .append("    float directional = (sunDot + moonDot) * skyLight;\n\n")
                .append("    float faceTint;\n")
                .append("    if (!isShaded) {\n")
                .append("        faceTint = NO_SHADE_FACE_TINT;\n")
                .append("    } else {\n")
                // Blend from vanilla ambient (0.6) toward full directional lighting as sky light increases.
                // This preserves the vanilla look for block-lit surfaces while adding sun directionality.
                .append("        float ambient = mix(0.5, 0.8, skyLight);\n")
                .append("        faceTint = ambient + directional * 0.5;\n")
                .append("    }\n")
                .append("    colour.rgb *= light.rgb * faceTint;\n")
                .append("    colour = colour + vec4(0,0,0,float(interData.w&0xFFu)/255);\n\n");
        builder.append("#ifndef TRANSLUCENT\n")
                .append("    colour.a = 1.0;\n")
                .append("#endif\n\n");

        // Default everything to neutral to avoid polluting unrelated G-buffer targets.
        for (int i = 0; i < outputs; i++) {
            builder.append("    outColour").append(i).append(" = vec4(0.0);\n");
        }

        // Always write shaded color somewhere sensible.
        builder.append("    outColour").append(colorSlot).append(" = colour;\n");

        // If the pack uses colortex6 for material/light data, provide a basic payload instead of mistakenly writing
        // normals into it (which can cause screen-space artifacts).
        if (lightSlot >= 0 && lightSlot < outputs) {
            builder.append("    outColour").append(lightSlot).append(" = vec4(0.0, 0.0, parameters.lightMap.y, parameters.lightMap.x + clamp(colour.a, 0.0, 1.0));\n");
        }

        // Write normals only when we have a likely normal target.
        if (normalSlot >= 0 && normalSlot < outputs) {
            builder.append("    vec3 n = vec3(uint((parameters.face>>1u)==2u), uint((parameters.face>>1u)==0u), uint((parameters.face>>1u)==1u)) * (float(int(parameters.face)&1)*2-1);\n");
            builder.append("    outColour").append(normalSlot).append(" = vec4(n*0.5+0.5, 1.0);\n");
        }

        builder.append("}\n");

        return builder.toString();
    }

    private static final String[] PATCH_FILE_SEARCH_PREFIXES = new String[]{
            "",
            "program/",
            "shaders/program/",
            "world0/",
            "world1/",
            "world-1/",
            "../",
            "../program/",
            "../shaders/program/"
    };

    private static String resolvePatchFile(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider, String fileName) {
        for (String prefix : PATCH_FILE_SEARCH_PREFIXES) {
            try {
                String data = sourceProvider.apply(directory.resolve(prefix + fileName));
                if (data != null && !data.isBlank()) {
                    if (!prefix.isEmpty()) {
                        Logger.info("Resolved shader patch file '" + fileName + "' via fallback path prefix '" + prefix + "'");
                    }
                    return data;
                }
            } catch (Throwable ignored) {
                // Continue scanning alternative locations.
            }
        }
        Logger.info("[IrisShaderPatch] resolvePatchFile: '" + fileName + "' not found under any prefix for directory=" + directory);
        return null;
    }

    public static IrisShaderPatch makePatch(ShaderPack ipack, AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider) {
        String voxyPatchData = resolvePatchFile(directory, sourceProvider, "voxy.json");
        Logger.info("[IrisShaderPatch] makePatch: directory=" + directory + " voxyPatchData=" + (voxyPatchData == null ? "null" : "found, length=" + voxyPatchData.length()));
        if (voxyPatchData == null) {//No voxy patch data in shaderpack
            return null;
        }

        //A more graceful exit on blank string
        if (voxyPatchData.isBlank()) {
            return null;
        }

        // Strip "unusedString" fields used by some packs (e.g. Photon) as a preprocessor
        // comment wrapper. Iris's JcppProcessor expands any #include directives inside
        // these string values before we read them, injecting raw GLSL with unescaped
        // quotes into the JSON string value — making the field non-parseable.
        //
        // In Photon's voxy.json the field closes with `",` on its own line (the original
        // source ends the multi-line string literal with `        ",`). After Iris expands
        // the #include, only the interior changes; the closing `",` at the line start is
        // preserved. We strip from "unusedString": through that closing `",` on a line
        // that has only whitespace before the quote, plus an optional trailing comma.
        // Pattern: matches from "unusedString"\s*:\s*" through the next `",` (or `"`)
        // that appears at the very start of a line (possibly with whitespace only before it).
        voxyPatchData = voxyPatchData.replaceAll("(?m)\"unusedString\"\\s*:[\\s\\S]*?^\\s*\",", "");

        //Escape things
        voxyPatchData = voxyPatchData.replace("\\", "\\\\");

        PatchGson patchData = null;
        try {
            //TODO: basicly find any "commented out" quotation marks and escape them (if the line, when stripped starts with a // or /* then escape all quotation marks in that line)
            {
                StringBuilder builder = new StringBuilder(voxyPatchData.length());
                //Rebuild the patch, replacing commented out " with \"
                for (var line : voxyPatchData.split("\n")) {
                    int idx = line.indexOf("//");
                    if (idx != -1) {
                        builder.append(line, 0, idx);
                        builder.append(line.substring(idx).replace("\"","\\\""));
                    } else {
                        builder.append(line);
                    }
                    builder.append("\n");
                }
                voxyPatchData = builder.toString();
            }
            patchData = GSON.fromJson(voxyPatchData, PatchGson.class);
            if (patchData == null) {
                throw new IllegalStateException("Voxy patch json returned null, this is most likely due to malformed json file");
            }

            {//Inject data from the auxilery files if they are present
                var opaque = resolvePatchFile(directory, sourceProvider, "voxy_opaque.glsl");
                if (opaque != null) {
                    Logger.info("External opaque shader patch applied");
                    patchData.opaquePatchData = opaque;
                }
                var translucent = resolvePatchFile(directory, sourceProvider, "voxy_translucent.glsl");
                if (translucent != null) {
                    Logger.info("External translucent shader patch applied");
                    patchData.translucentPatchData = translucent;
                }
                //This might be ok? not.. sure if is nice or not
                var taa = resolvePatchFile(directory, sourceProvider, "voxy_taa.glsl");
                if (taa != null) {
                    Logger.info("External taa shader patch applied");
                    patchData.taaOffset = taa;
                }
            }

            var invalidPatchDataReason = patchData.checkValid();
            if (invalidPatchDataReason!=null) {
                throw new IllegalStateException("voxy json patch not valid: " + invalidPatchDataReason);
            }
        } catch (Exception e) {
            // Log but do not abort — packs like Photon use GLSL #ifdef preprocessor directives
            // inside voxy.json, which Iris preprocesses before we read them. The resulting JSON
            // may be invalid (e.g. trailing commas after stripped #ifdef blocks). Fall through to
            // makeFallbackPatch so the pack still renders, just without the voxy.json tweaks.
            Logger.warn("[IrisShaderPatch] Failed to parse voxy.json (likely preprocessor-conditional JSON): " + e.getMessage());
            return null;
        }
        if (patchData == null) {
            return null;
        }
        if (patchData.version != VERSION) {
            Logger.error("Shader has voxy patch data, but patch version is incorrect. expected " + VERSION + " got "+patchData.version);
            throw new IllegalStateException("Shader version mismatch expected " + VERSION + " got "+patchData.version);
        }
        // Log parsed voxy.json fields for diagnostics
        Logger.info("[IrisShaderPatch] voxy.json parsed OK:"
                + " version=" + patchData.version
                + " mode=VOXY_PATCH"
                + " excludeLodsFromVanillaDepth=" + patchData.excludeLodsFromVanillaDepth
                + " useViewportDims=" + patchData.useViewportDims
                + " renderScale=" + java.util.Arrays.toString(patchData.renderScale)
                + " opaqueDrawBuffers=" + java.util.Arrays.toString(patchData.opaqueDrawBuffers)
                + " translucentDrawBuffers=" + java.util.Arrays.toString(patchData.translucentDrawBuffers)
                + " uniforms[" + (patchData.uniforms == null ? 0 : patchData.uniforms.length) + "]=" + java.util.Arrays.toString(patchData.uniforms)
                + " samplers=" + (patchData.samplers == null ? "null" : patchData.samplers.keySet())
                + " ssbos=" + (patchData.ssbos == null ? "null" : patchData.ssbos.keySet())
                + " taaOffset=" + (patchData.taaOffset == null ? "null" : "present(len=" + patchData.taaOffset.length() + ")")
                + " opaquePatch=" + (patchData.opaquePatchData == null ? "null" : "len=" + patchData.opaquePatchData.length())
                + " translucentPatch=" + (patchData.translucentPatchData == null ? "null" : "len=" + patchData.translucentPatchData.length()));
        return new IrisShaderPatch(patchData, ipack, CompatibilityMode.VOXY_PATCH);
    }
}
