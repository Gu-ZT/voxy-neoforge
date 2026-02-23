package me.cortex.voxy.client.config;

import me.cortex.voxy.commonImpl.VoxyCommon;

/**
 * Facade for Voxy configuration.
 *
 * All values are delegated to VoxyNeoForgeConfig (TOML).
 * This class exists for compatibility with code that references VoxyConfig.CONFIG.
 *
 * Config file location: config/voxy-client.toml
 */
public class VoxyConfig {

    public static final VoxyConfig CONFIG = new VoxyConfig();

    private VoxyConfig() {
        // Singleton - use CONFIG instance
    }

    // ========== Delegated Getters ==========

    public boolean isEnabled() {
        return VoxyNeoForgeConfig.isEnabled();
    }

    public boolean isRenderingEnabled() {
        return VoxyCommon.isAvailable() && isEnabled() && VoxyNeoForgeConfig.isRenderingEnabled();
    }

    public boolean isIngestEnabled() {
        return VoxyNeoForgeConfig.isIngestEnabled();
    }

    public int getSectionRenderDistance() {
        return VoxyNeoForgeConfig.getSectionRenderDistance();
    }

    public int getServiceThreads() {
        return VoxyNeoForgeConfig.getServiceThreads();
    }

    public float getSubDivisionSize() {
        return VoxyNeoForgeConfig.getSubDivisionSize();
    }

    public boolean useEnvironmentalFog() {
        return VoxyNeoForgeConfig.useEnvironmentalFog();
    }

    public boolean enableShaderPackFogOverride() {
        return VoxyNeoForgeConfig.enableShaderPackFogOverride();
    }

    public boolean enableShaderPackFallbackPatch() {
        return VoxyNeoForgeConfig.enableShaderPackFallbackPatch();
    }

    public boolean dontUseEmbeddiumBuilderThreads() {
        return VoxyNeoForgeConfig.dontUseEmbeddiumBuilderThreads();
    }

    public int getEarthCurveRatio() {
        return VoxyNeoForgeConfig.getEarthCurveRatio();
    }

    // ========== Delegated Setters ==========

    public void setEnabled(boolean value) {
        VoxyNeoForgeConfig.setEnabled(value);
    }

    public void setRenderingEnabled(boolean value) {
        VoxyNeoForgeConfig.setRenderingEnabled(value);
    }

    public void setIngestEnabled(boolean value) {
        VoxyNeoForgeConfig.setIngestEnabled(value);
    }

    public void setSectionRenderDistance(int value) {
        VoxyNeoForgeConfig.setSectionRenderDistance(value);
    }

    public void setServiceThreads(int value) {
        VoxyNeoForgeConfig.setServiceThreads(value);
    }

    public void setSubDivisionSize(float value) {
        VoxyNeoForgeConfig.setSubDivisionSize(value);
    }

    public void setUseEnvironmentalFog(boolean value) {
        VoxyNeoForgeConfig.setUseEnvironmentalFog(value);
    }

    public void setShaderPackFogOverride(boolean value) {
        VoxyNeoForgeConfig.setShaderPackFogOverride(value);
    }

    public void setShaderPackFallbackPatch(boolean value) {
        VoxyNeoForgeConfig.setShaderPackFallbackPatch(value);
    }

    public void setDontUseEmbeddiumBuilderThreads(boolean value) {
        VoxyNeoForgeConfig.setDontUseEmbeddiumBuilderThreads(value);
    }

    public void setEarthCurveRatio(int value) {
        VoxyNeoForgeConfig.setEarthCurveRatio(value);
    }

    // ========== Save ==========

    /**
     * Save config to TOML file.
     */
    public void save() {
        VoxyNeoForgeConfig.save();
    }
}
