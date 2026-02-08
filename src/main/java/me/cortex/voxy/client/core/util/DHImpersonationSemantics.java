package me.cortex.voxy.client.core.util;

import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.config.VoxyConfig;
import net.minecraft.client.Minecraft;

public final class DHImpersonationSemantics {
    public static final boolean ENABLED =
            System.getProperty("voxy.impersonateDHShader", "false").equalsIgnoreCase("true");

    private static final int FAR_PLANE_EXTRA_MARGIN_BLOCKS = 512;
    private static final double SQRT_2 = Math.sqrt(2.0);

    private DHImpersonationSemantics() {
    }

    public static int getRenderDistanceBlocks() {
        // sectionRenderDistance is in "top-level sectors" (512 blocks = 32 chunks).
        return VoxyConfig.CONFIG.getSectionRenderDistance() * 32 * 16;
    }

    public static float getNearPlaneBlocks() {
        float near = Minecraft.getInstance().gameRenderer.getRenderDistance() <= 32.0f ? 8f : 16f;
        return VoxyClient.disableEmbeddiumChunkRender() ? 0.1f : near;
    }

    public static float getFarPlaneBlocks() {
        // Match Iris DHCompatInternal semantics:
        // far = (renderDistanceBlocks + 512) * sqrt(2)
        return (float) ((getRenderDistanceBlocks() + FAR_PLANE_EXTRA_MARGIN_BLOCKS) * SQRT_2);
    }
}
