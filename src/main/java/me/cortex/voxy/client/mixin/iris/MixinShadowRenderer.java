package me.cortex.voxy.client.mixin.iris;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.common.Logger;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShadowRenderer.class, remap = false)
public class MixinShadowRenderer {
    private static final boolean ENABLE_LOD_SHADOWS =
            System.getProperty("voxy.enableLodShadows", "true").equalsIgnoreCase("true");

    @Unique
    private static volatile boolean voxy$disableLodShadowsDueToError;

    @Unique
    private boolean voxy$renderedThisShadowPass;

    @Inject(
            method = "renderShadows",
            at = @At("HEAD"),
            require = 0
    )
    private void voxy$resetShadowPass(LevelRendererAccessor levelRenderer, Camera camera, CallbackInfo ci) {
        this.voxy$renderedThisShadowPass = false;
    }

    @Inject(
            method = "renderShadows",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/mixin/LevelRendererAccessor;invokeRenderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void voxy$renderLodShadows(LevelRendererAccessor levelRenderer, Camera camera, CallbackInfo ci) {
        if (!ENABLE_LOD_SHADOWS) {
            return;
        }
        if (voxy$disableLodShadowsDueToError) {
            return;
        }
        if (!VoxyConfig.CONFIG.isRenderingEnabled()) {
            return;
        }
        if (!ShadowRenderer.ACTIVE) {
            return;
        }
        if (ShadowRenderer.MODELVIEW == null || ShadowRenderer.PROJECTION == null) {
            return;
        }
        if (this.voxy$renderedThisShadowPass) {
            return;
        }
        this.voxy$renderedThisShadowPass = true;

        var renderer = ((IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer).getVoxyRenderSystem();
        if (renderer == null) {
            return;
        }

        var pos = camera.getPosition();
        try {
            renderer.renderShadowPass(ShadowRenderer.PROJECTION, ShadowRenderer.MODELVIEW, pos.x, pos.y, pos.z);
        } catch (Throwable t) {
            voxy$disableLodShadowsDueToError = true;
            Logger.error("Disabling Voxy LOD shadows due to a crash in Iris shadow pass. Set -Dvoxy.enableLodShadows=false to disable, or restart to retry.", t);
        }
    }
}
