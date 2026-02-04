package me.cortex.voxy.client.mixin.iris;

import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.client.iris.IGetIrisVoxyPipelineData;
import me.cortex.voxy.client.iris.IGetVoxyPatchData;
import me.cortex.voxy.client.iris.IrisShaderPatch;
import me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData;
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
    @Shadow @Final private CustomUniforms customUniforms;
    @Shadow private ShaderStorageBufferHolder shaderStorageBufferHolder;
    @Unique IrisShaderPatch patchData;
    @Unique
    IrisVoxyRenderPipelineData pipeline;

    // Keep constructor injections resilient: Iris changes internal call structure frequently.
    // NOTE: Mixins cannot inject non-static callbacks at the constructor HEAD (before super()).
    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void voxy$injectPipeline(ProgramSet programSet, CallbackInfo ci) {
        if (!IrisUtil.SHADER_SUPPORT) {
            return;
        }

        this.patchData = ((IGetVoxyPatchData) programSet).voxy$getPatchData();

        if (this.patchData == null) {
            return;
        }
        if (this.customUniforms == null) {
            return;
        }
        if (this.shaderStorageBufferHolder == null) {
            return;
        }

        this.pipeline = IrisVoxyRenderPipelineData.buildPipeline(
                (IrisRenderingPipeline) (Object) this,
                this.patchData,
                this.customUniforms,
                this.shaderStorageBufferHolder);
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

    @Override
    public IrisShaderPatch voxy$getPatchData() {
        return this.patchData;
    }

    @Override
    public IrisVoxyRenderPipelineData voxy$getPipelineData() {
        return this.pipeline;
    }
}
