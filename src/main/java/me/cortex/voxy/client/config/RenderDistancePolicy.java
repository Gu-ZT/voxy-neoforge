package me.cortex.voxy.client.config;

public final class RenderDistancePolicy {
    private static final int MIN_RENDER_DISTANCE = 2;
    private static final int SECTION_TO_CHUNK_MULTIPLIER = 32;
    private static final int SAFETY_RINGS =
            Integer.getInteger("voxy.renderDistanceSafetyRings", 2);

    private RenderDistancePolicy() {
    }

    public static int getSafetyRings() {
        return SAFETY_RINGS;
    }

    public static int getEffectiveSectionRenderDistance() {
        return getEffectiveSectionRenderDistance(VoxyConfig.CONFIG.getSectionRenderDistance());
    }

    public static int getEffectiveSectionRenderDistance(int configuredRenderDistance) {
        return Math.max(MIN_RENDER_DISTANCE, configuredRenderDistance + SAFETY_RINGS);
    }

    public static int getConfiguredRenderDistanceChunks() {
        return VoxyConfig.CONFIG.getSectionRenderDistance() * SECTION_TO_CHUNK_MULTIPLIER;
    }

    public static float getTraversalDistanceSquaredBlocks() {
        int effectiveSectionDistance = getEffectiveSectionRenderDistance();
        return (float) Math.pow(effectiveSectionDistance * 16 * SECTION_TO_CHUNK_MULTIPLIER, 2);
    }
}
