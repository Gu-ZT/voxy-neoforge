package me.cortex.voxy.client.mixin.embeddium;

import org.embeddedt.embeddium.impl.render.EmbeddiumWorldRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EmbeddiumWorldRenderer.class, remap = false)
public interface AccessorEmbeddiumWorldRenderer {
    @Accessor
    RenderSectionManager getRenderSectionManager();
}
