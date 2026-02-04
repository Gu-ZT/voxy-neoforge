package me.cortex.voxy.client.compat;

import me.cortex.voxy.client.mixin.embeddium.AccessorEmbeddiumWorldRenderer;
import org.embeddedt.embeddium.impl.render.EmbeddiumWorldRenderer;

public class EmbeddiumCompat implements RendererCompat {
    @Override
    public int getBuilderThreadCount() {
        var renderer = EmbeddiumWorldRenderer.instanceNullable();
        if (renderer == null) {
            return -1;
        }
        var rsm = ((AccessorEmbeddiumWorldRenderer) renderer).getRenderSectionManager();
        if (rsm == null) {
            return -1;
        }
        return rsm.getBuilder().getTotalThreadCount();
    }
}
