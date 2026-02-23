package me.cortex.voxy.client.config;

import me.cortex.voxy.client.RenderStatistics;
import me.cortex.voxy.common.util.cpu.CpuLayout;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NeoForge config integration for Voxy.
 * This is the SINGLE SOURCE OF TRUTH for all Voxy settings.
 *
 * Config file: config/voxy-client.toml
 *
 * VoxyConfig delegates to this class for all values.
 * Changes via Embeddium UI are synced here and persisted to TOML.
 */
@EventBusSubscriber(modid = "voxy", bus = EventBusSubscriber.Bus.MOD)
public class VoxyNeoForgeConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // General settings
    static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable Voxy LOD rendering system")
            .define("enabled", true);

    static final ModConfigSpec.BooleanValue ENABLE_RENDERING = BUILDER
            .comment("Enable LOD terrain rendering (can be disabled while keeping data ingestion)")
            .define("enableRendering", true);

    static final ModConfigSpec.BooleanValue INGEST_ENABLED = BUILDER
            .comment("Enable automatic chunk data ingestion for LOD generation")
            .define("ingestEnabled", true);

    // Performance settings
    static final ModConfigSpec.IntValue SECTION_RENDER_DISTANCE = BUILDER
            .comment("LOD section render distance (multiplied by 32 for actual chunk distance)",
                     "Example: 16 = 512 chunks render distance")
            .defineInRange("sectionRenderDistance", 16, 2, 64);

    static final ModConfigSpec.IntValue SERVICE_THREADS = BUILDER
            .comment("Number of background threads for LOD processing",
                     "Default is based on CPU core count.")
            .defineInRange("serviceThreads", Math.max((int)(CpuLayout.getCoreCount() / 1.5), 1), 1, CpuLayout.getCoreCount());

    static final ModConfigSpec.DoubleValue SUB_DIVISION_SIZE = BUILDER
            .comment("Subdivision size for LOD rendering (28-256)",
                     "Lower = more detailed LODs but more GPU load")
            .defineInRange("subDivisionSize", 64.0, 28.0, 256.0);

    // Visual settings
    static final ModConfigSpec.BooleanValue USE_ENVIRONMENTAL_FOG = BUILDER
            .comment("Apply environmental fog to LOD terrain")
            .define("useEnvironmentalFog", true);

    static final ModConfigSpec.BooleanValue SHADER_PACK_FOG_OVERRIDE = BUILDER
            .comment("Extend fog distance when shader packs are active",
                     "Prevents distant LODs from being fully fogged out by shader packs")
            .define("shaderPackFogOverride", true);

    static final ModConfigSpec.BooleanValue SHADER_PACK_FALLBACK_PATCH = BUILDER
            .comment("Enable fallback shader patching for packs without voxy.json",
                     "Keeps LODs visible in shader packs that lack native Voxy integration")
            .define("shaderPackFallbackPatch", true);

    // Advanced settings
    static final ModConfigSpec.BooleanValue DONT_USE_EMBEDDIUM_BUILDER_THREADS = BUILDER
            .comment("Don't share threads with Embeddium's chunk builder")
            .define("dontUseEmbeddiumBuilderThreads", false);

    // World curvature (experimental, LOD-only - vanilla chunks are not affected)
    static final ModConfigSpec.IntValue EARTH_CURVE_RATIO = BUILDER
            .comment("World curvature effect - simulates standing on a spherical planet (LOD terrain only)",
                     "0 = disabled (flat world)",
                     "5 = subtle curvature visible at long distances",
                     "50 = strong curvature (very small planet feel)",
                     "250 = extreme curvature",
                     "Note: only affects LOD terrain, vanilla chunks remain flat.",
                     "Inspired by Distant Horizons' earth curvature feature")
            .defineInRange("earthCurveRatio", 0, 0, 250);

    // Debug settings
    static final ModConfigSpec.BooleanValue RENDER_STATISTICS = BUILDER
            .comment("Show render statistics in F3 debug screen",
                     "Displays LOD traversal counts, visible sections, and quad counts")
            .define("renderStatistics", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean configLoaded = false;

    /**
     * Register the config with NeoForge.
     * Call this during mod construction.
     */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC, "voxy-client.toml");
    }

    /**
     * Called when config is loaded or reloaded.
     * Updates runtime-only settings that aren't read directly from config values.
     */
    private static void onConfigChanged() {
        configLoaded = true;
        // RenderStatistics is a runtime-only flag
        RenderStatistics.enabled = RENDER_STATISTICS.get();
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            onConfigChanged();
        }
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            onConfigChanged();
        }
    }

    /**
     * Check if config has been loaded by NeoForge.
     * Before config is loaded, getters return default values.
     */
    public static boolean isConfigLoaded() {
        return configLoaded;
    }

    /**
     * Save all current config values to the TOML file.
     * Call this after modifying values via set methods.
     */
    public static void save() {
        if (configLoaded) {
            SPEC.save();
        }
    }

    // ========== Getters ==========

    public static boolean isEnabled() {
        return ENABLED.get();
    }

    public static boolean isRenderingEnabled() {
        return ENABLE_RENDERING.get();
    }

    public static boolean isIngestEnabled() {
        return INGEST_ENABLED.get();
    }

    public static int getSectionRenderDistance() {
        return SECTION_RENDER_DISTANCE.get();
    }

    public static int getServiceThreads() {
        return SERVICE_THREADS.get();
    }

    public static float getSubDivisionSize() {
        return SUB_DIVISION_SIZE.get().floatValue();
    }

    public static boolean useEnvironmentalFog() {
        return USE_ENVIRONMENTAL_FOG.get();
    }

    public static boolean enableShaderPackFogOverride() {
        return SHADER_PACK_FOG_OVERRIDE.get();
    }

    public static boolean enableShaderPackFallbackPatch() {
        return SHADER_PACK_FALLBACK_PATCH.get();
    }

    public static boolean dontUseEmbeddiumBuilderThreads() {
        return DONT_USE_EMBEDDIUM_BUILDER_THREADS.get();
    }

    public static boolean isRenderStatisticsEnabled() {
        return RENDER_STATISTICS.get();
    }

    public static int getEarthCurveRatio() {
        return EARTH_CURVE_RATIO.get();
    }

    // ========== Setters (for Embeddium UI integration) ==========

    public static void setEnabled(boolean value) {
        ENABLED.set(value);
    }

    public static void setRenderingEnabled(boolean value) {
        ENABLE_RENDERING.set(value);
    }

    public static void setIngestEnabled(boolean value) {
        INGEST_ENABLED.set(value);
    }

    public static void setSectionRenderDistance(int value) {
        SECTION_RENDER_DISTANCE.set(value);
    }

    public static void setServiceThreads(int value) {
        SERVICE_THREADS.set(value);
    }

    public static void setSubDivisionSize(float value) {
        SUB_DIVISION_SIZE.set((double) value);
    }

    public static void setUseEnvironmentalFog(boolean value) {
        USE_ENVIRONMENTAL_FOG.set(value);
    }

    public static void setShaderPackFogOverride(boolean value) {
        SHADER_PACK_FOG_OVERRIDE.set(value);
    }

    public static void setShaderPackFallbackPatch(boolean value) {
        SHADER_PACK_FALLBACK_PATCH.set(value);
    }

    public static void setDontUseEmbeddiumBuilderThreads(boolean value) {
        DONT_USE_EMBEDDIUM_BUILDER_THREADS.set(value);
    }

    public static void setEarthCurveRatio(int value) {
        EARTH_CURVE_RATIO.set(value);
    }

    public static void setRenderStatistics(boolean value) {
        RENDER_STATISTICS.set(value);
        RenderStatistics.enabled = value;
    }
}
