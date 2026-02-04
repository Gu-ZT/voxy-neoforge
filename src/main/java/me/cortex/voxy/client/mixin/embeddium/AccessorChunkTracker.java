package me.cortex.voxy.client.mixin.embeddium;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ChunkTracker.class, remap = false)
public interface AccessorChunkTracker {
    @Accessor
    Long2IntOpenHashMap getChunkStatus();
}
