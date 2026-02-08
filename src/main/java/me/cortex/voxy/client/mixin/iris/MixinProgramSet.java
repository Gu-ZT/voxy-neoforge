package me.cortex.voxy.client.mixin.iris;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.client.iris.IGetVoxyPatchData;
import me.cortex.voxy.client.iris.IrisShaderPatch;
import me.cortex.voxy.common.Logger;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(value = ProgramSet.class, remap = false)
public class MixinProgramSet implements IGetVoxyPatchData {
    @Shadow @Final private PackDirectives packDirectives;
    @Unique IrisShaderPatch patchData;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;locateDirectives()V", shift = At.Shift.BEFORE))
    private void voxy$injectPatchMaker(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider, ShaderProperties shaderProperties, ShaderPack pack, CallbackInfo ci) {
        if (VoxyConfig.CONFIG.isRenderingEnabled() && IrisUtil.SHADER_SUPPORT) {
            this.patchData = IrisShaderPatch.makePatch(pack, directory, sourceProvider);
            if (this.patchData == null && VoxyConfig.CONFIG.enableShaderPackFallbackPatch()) {
                this.patchData = IrisShaderPatch.makeFallbackPatch(pack, (ProgramSet)(Object)this);
                if (this.patchData.getCompatibilityMode() == IrisShaderPatch.CompatibilityMode.DH_NATIVE_CANDIDATE) {
                    Logger.warn("Shader pack has DH programs but no voxy.json; using fallback patch (DH-native path pending)");
                } else {
                    Logger.warn("Shader pack has no voxy.json and no DH programs; using fallback patch");
                }
            }
            if (this.patchData != null) {
                if (this.patchData.getCompatibilityMode() == IrisShaderPatch.CompatibilityMode.DH_NATIVE_CANDIDATE) {
                    IrisShaderPatch.enableDistantHorizonsImpersonation();
                }
                Logger.info("Voxy shader compatibility mode: " + this.patchData.getCompatibilityMode());
            }
        }
        /*
        if (this.patchData != null) {
            //Inject directives from voxy
            DispatchingDirectiveHolder ddh = new DispatchingDirectiveHolder();
            this.packDirectives.acceptDirectivesFrom(ddh);
            CommentDirectiveParser.findDirective(this.patchData.getPatchSource(), CommentDirective.Type.RENDERTARGETS)
                    .map(dir->Arrays.stream(dir.getDirective().split(","))
                            .mapToInt(Integer::parseInt).toArray())
                    .ifPresent(ddh::processDirective);

        }
         */
    }


    @Override
    public IrisShaderPatch voxy$getPatchData() {
        return this.patchData;
    }
}
