package me.cortex.voxy.client.mixin.iris;

import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.client.iris.IGetIrisVoxyPipelineData;
import me.cortex.voxy.client.iris.IGetVoxyPatchData;
import me.cortex.voxy.client.iris.IrisShaderPatch;
import me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class MixinIrisRenderingPipeline implements IGetVoxyPatchData, IGetIrisVoxyPipelineData {
    @Unique private static final int VOXY_RECREATE_MAX_RETRIES = 40;
    @Shadow @Final private CustomUniforms customUniforms;
    @Shadow private ShaderStorageBufferHolder shaderStorageBufferHolder;
    @Unique IrisShaderPatch patchData;
    @Unique
    IrisVoxyRenderPipelineData pipeline;

    // Keep constructor injection resilient: Iris changes internal call structure frequently.
    // NOTE: Mixins cannot inject non-static callbacks at the constructor HEAD (before super()).
    // Use TAIL — the safest target, independent of Iris internal method ordering.
    // DO NOT switch to bytecode-targeted INVOKE injections — those are brittle across Iris versions
    // and will silently fail (require=0) if the target method is renamed or reordered.
    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void voxy$injectPipeline(ProgramSet programSet, CallbackInfo ci) {
        if (!IrisUtil.SHADER_SUPPORT) {
            return;
        }

        this.patchData = ((IGetVoxyPatchData) programSet).voxy$getPatchData();

        if (this.patchData == null) {
            me.cortex.voxy.common.Logger.info("[MixinIrisRenderingPipeline] patchData == null (no voxy.json and fallback disabled)");
            return;
        }
        if (this.customUniforms == null) {
            me.cortex.voxy.common.Logger.warn("[MixinIrisRenderingPipeline] customUniforms == null at TAIL — cannot build pipeline");
            return;
        }
        // shaderStorageBufferHolder is null when the pack has no SSBOs — that is fine,
        // createSSBOLayouts() handles null ssboHolder gracefully (returns null ssboSet).

        me.cortex.voxy.common.Logger.info("[MixinIrisRenderingPipeline] Building IrisVoxyRenderPipelineData for " + this.patchData.getCompatibilityMode()
                + " (ssbo=" + (this.shaderStorageBufferHolder != null ? "present" : "null/none") + ")");
        try {
            this.pipeline = IrisVoxyRenderPipelineData.buildPipeline(
                    (IrisRenderingPipeline) (Object) this,
                    this.patchData,
                    this.customUniforms,
                    this.shaderStorageBufferHolder,
                    programSet.getPack().getIdMap());
            me.cortex.voxy.common.Logger.info("[MixinIrisRenderingPipeline] IrisVoxyRenderPipelineData built successfully");
        } catch (Exception e) {
            me.cortex.voxy.common.Logger.error("[MixinIrisRenderingPipeline] buildPipeline threw exception — pipelineData will be null", e);
        }

        // If VoxyRenderSystem already exists (shader pack switch mid-session), rebuild it so it
        // picks up the new IrisVoxyRenderPipelineData rather than keeping the stale NormalRenderPipeline.
        // IMPORTANT: defer to next tick via mc.execute() so that the Iris pipeline manager has time to
        // register the newly constructed IrisRenderingPipeline before IrisCompat.createPipeline() runs.
        // Without the defer, getPipelineNullable() inside createPipeline() still returns the old pipeline
        // (or null), causing Voxy to fall back to NormalRenderPipeline even when Iris is healthy.
        var mc = Minecraft.getInstance();
        if (mc != null && mc.levelRenderer instanceof IGetVoxyRenderSystem iGetVrs) {
            var vrs = iGetVrs.getVoxyRenderSystem();
            if (vrs != null) {
                me.cortex.voxy.common.Logger.info("[MixinIrisRenderingPipeline] Iris pipeline rebuilt mid-session — scheduling deferred VoxyRenderSystem recreation");
                voxy$deferredRecreate(mc, iGetVrs, VOXY_RECREATE_MAX_RETRIES);
            }
        }
    }

    @Unique
    private static boolean voxy$isIrisPipelineReadyForVoxy() {
        var irisPipe = Iris.getPipelineManager().getPipelineNullable();
        if (!(irisPipe instanceof IGetIrisVoxyPipelineData getData)) {
            return false;
        }
        return getData.voxy$getPipelineData() != null;
    }

    @Unique
    private static void voxy$deferredRecreate(Minecraft mc, IGetVoxyRenderSystem iGetVrs, int retriesRemaining) {
        mc.execute(() -> {
            if (iGetVrs.getVoxyRenderSystem() == null) {
                return;
            }
            if (!voxy$isIrisPipelineReadyForVoxy()) {
                if (retriesRemaining <= 0) {
                    me.cortex.voxy.common.Logger.warn("[MixinIrisRenderingPipeline] Deferred recreation exhausted retries — Iris pipeline still not ready");
                    return;
                }
                voxy$deferredRecreate(mc, iGetVrs, retriesRemaining - 1);
                return;
            }
            me.cortex.voxy.common.Logger.info("[MixinIrisRenderingPipeline] Deferred recreation: shutting down old VoxyRenderSystem");
            iGetVrs.shutdownRenderer();
            iGetVrs.createRenderer();
        });
    }

    // Iris frequently changes the internal implementation details of beginLevelRendering().
    // We only need to apply the captured viewport matrices (from Embeddium) before Voxy renders,
    // so inject at HEAD to avoid brittle bytecode targets.
    @Inject(method = "beginLevelRendering", at = @At("HEAD"), remap = false, require = 0)
    private void voxy$injectViewportSetup(CallbackInfo ci) {
        if (IrisUtil.CAPTURED_VIEWPORT_PARAMETERS != null) {
            var renderer = ((IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer).getVoxyRenderSystem();
            if (renderer != null) {
                IrisUtil.CAPTURED_VIEWPORT_PARAMETERS.apply(renderer);
            }
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"), remap = false, require = 0)
    private void voxy$resetCompatibilityState(CallbackInfo ci) {
        IrisShaderPatch.updateActiveCompatibility(null);
        me.cortex.voxy.common.Logger.info("[MixinIrisRenderingPipeline] Iris pipeline destroy() — clearing patch state only (no recreation)");
    }

    @Override
    public IrisShaderPatch voxy$getPatchData() {
        return this.patchData;
    }

    @Override
    public IrisVoxyRenderPipelineData voxy$getPipelineData() {
        return this.pipeline;
    }
}
