package me.cortex.voxy.client.mixin.minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.client.compat.IrisCompatManager;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private static void voxy$extendFog(Camera camera, FogRenderer.FogMode fogMode,
                                       float farPlaneDistance, boolean shouldCreateFog,
                                       float partialTick, CallbackInfo ci) {
        if (!VoxyConfig.CONFIG.isRenderingEnabled()) return;

        var mc = Minecraft.getInstance();
        if (mc == null || mc.levelRenderer == null) return;

        var vrs = ((IGetVoxyRenderSystem) mc.levelRenderer).getVoxyRenderSystem();
        if (vrs == null) return;

        // Keep shader-pack fog/cloud pipeline authoritative.
        if (IrisCompatManager.isShaderPackEnabled()) {
            return;
        }

        if (fogMode == FogRenderer.FogMode.FOG_TERRAIN) {
            RenderSystem.setShaderFogStart(999999999f);
            RenderSystem.setShaderFogEnd(999999999f);
        }

        if (!VoxyConfig.CONFIG.useEnvironmentalFog() && fogMode == FogRenderer.FogMode.FOG_SKY) {
            RenderSystem.setShaderFogStart(999999999f);
            RenderSystem.setShaderFogEnd(999999999f);
        }
    }
}
