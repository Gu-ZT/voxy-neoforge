package me.cortex.voxy.client.core.rendering.util;

import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;

import me.cortex.voxy.client.mixin.minecraft.AccessorLightTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;

public class LightMapHelper {
    public static int getLightmapTextureId() {
        LightTexture lightTexture = Minecraft.getInstance().gameRenderer.lightTexture();
        try {
            lightTexture.turnOnLightLayer(); // ensures RenderSystem shader texture 2 is populated
        } catch (Throwable ignored) {
            // Some environments may not allow RenderSystem calls at this moment; fall back below.
        }

        int glId = 0;
        try {
            glId = RenderSystem.getShaderTexture(2);
        } catch (Throwable ignored) {
        }

        if (glId == 0) {
            // Fallback: use accessor mixin to get the private DynamicTexture id directly.
            try {
                glId = ((AccessorLightTexture) lightTexture).voxy$getLightTexture().getId();
            } catch (Throwable ignored) {
                glId = 0;
            }
        }
        return glId;
    }

    public static void bind(int lightingIndex) {
        glBindSampler(lightingIndex, 0);
        glBindTextureUnit(lightingIndex, getLightmapTextureId());
    }
}
