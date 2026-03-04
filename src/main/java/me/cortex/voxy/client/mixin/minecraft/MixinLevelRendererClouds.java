package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Expands vanilla cloud geometry range to match Voxy's LOD render distance.
 *
 * Vanilla buildClouds() loops k/l from -3 to 4 (8 tiles × 8 blocks = 64 blocks each side).
 * With Voxy active we expand to cover the full LOD render distance so clouds
 * are visible above distant LOD terrain.
 *
 * The side-face conditionals inside the loop (k > -1, k <= 1, l > -1, l <= 1) stay
 * unchanged — they only affect cloud-block side faces near the centre and are
 * unnoticeable at LOD distances.
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRendererClouds {

    /**
     * Before renderClouds() executes, ensure Voxy LOD depth is present in the main framebuffer.
     *
     * IrisVoxyRenderPipeline.finish() skips the depth blit when excludeLodsFromVanillaDepth=true
     * (Iris packs manage their own depth compositing). But vanilla cloud rendering always reads
     * from the main FB depth — without LOD depth, clouds appear in front of all distant LODs.
     *
     * This inject forces a targeted depth blit just before clouds render so clouds correctly
     * depth-test against Voxy geometry regardless of the pack's excludeLodsFromVanillaDepth flag.
     */
    @Inject(method = "renderClouds", at = @At("HEAD"), require = 0)
    private void voxy$blitLodDepthBeforeClouds(PoseStack poseStack, Matrix4f frustumMatrix,
                                               Matrix4f projectionMatrix, float partialTick,
                                               double camX, double camY, double camZ,
                                               CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.levelRenderer == null) return;
        var vrs = ((IGetVoxyRenderSystem) mc.levelRenderer).getVoxyRenderSystem();
        if (vrs == null) return;
        vrs.blitDepthBeforeClouds();
    }

    /**
     * Returns the cloud tile radius to use.
     * Vanilla uses -3..4 (radius ~3 tiles of 8 blocks = 24 blocks negative, 32 positive).
     * We compute a symmetric radius in tiles so clouds cover Voxy render distance.
     * Each cloud tile is 8 blocks wide. We add 1 tile of margin.
     */
    @Unique
    private static int voxy$cloudTileRadius() {
        var levelRenderer = Minecraft.getInstance().levelRenderer;
        if (!(levelRenderer instanceof IGetVoxyRenderSystem ivrs)) return 3;
        var vrs = ivrs.getVoxyRenderSystem();
        if (vrs == null) return 3;
        // Render distance in blocks; each cloud tile = 8 blocks. +1 tile margin.
        int renderDistBlocks = VoxyConfig.CONFIG.getSectionRenderDistance() * 32;
        return Math.max(3, (renderDistBlocks / 8) + 1);
    }

    // Expand the lower loop bound: for (int k = -3; ...) and for (int l = -3; ...)
    // Both ordinal 0 and 1 are the two loop init constants (-3 each).
    @ModifyConstant(method = "buildClouds", constant = @Constant(intValue = -3, ordinal = 0), require = 0)
    private int voxy$cloudLoopLowerK(int original) {
        return -voxy$cloudTileRadius();
    }

    @ModifyConstant(method = "buildClouds", constant = @Constant(intValue = -3, ordinal = 1), require = 0)
    private int voxy$cloudLoopLowerL(int original) {
        return -voxy$cloudTileRadius();
    }

    // Expand the upper loop bound: for (...; k <= 4; ...) and for (...; l <= 4; ...)
    // Both ordinal 0 and 1 are the two loop bound constants (4 each).
    @ModifyConstant(method = "buildClouds", constant = @Constant(intValue = 4, ordinal = 0), require = 0)
    private int voxy$cloudLoopUpperK(int original) {
        return voxy$cloudTileRadius() + 1;
    }

    @ModifyConstant(method = "buildClouds", constant = @Constant(intValue = 4, ordinal = 1), require = 0)
    private int voxy$cloudLoopUpperL(int original) {
        return voxy$cloudTileRadius() + 1;
    }
}
