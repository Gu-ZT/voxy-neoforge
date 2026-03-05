package me.cortex.voxy.client.core;

import me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.hierachical.NodeCleaner;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.client.iris.IGetIrisVoxyPipelineData;
import me.cortex.voxy.common.Logger;
import net.irisshaders.iris.Iris;

import java.util.function.BooleanSupplier;

public class RenderPipelineFactory {
    public static AbstractRenderPipeline createPipeline(AsyncNodeManager nodeManager, NodeCleaner nodeCleaner, HierarchicalOcclusionTraverser traversal, BooleanSupplier frexSupplier) {
        //Note this is where will choose/create e.g. IrisRenderPipeline or normal pipeline
        AbstractRenderPipeline pipeline = null;
        if (IrisUtil.IRIS_INSTALLED && IrisUtil.SHADER_SUPPORT) {
            pipeline = createIrisPipeline(nodeManager, nodeCleaner, traversal, frexSupplier);
        }
        if (pipeline == null) {
            Logger.info("[RenderPipelineFactory] Falling back to NormalRenderPipeline");
            pipeline = new NormalRenderPipeline(nodeManager, nodeCleaner, traversal, frexSupplier);
        }
        return pipeline;
    }

    private static AbstractRenderPipeline createIrisPipeline(AsyncNodeManager nodeManager, NodeCleaner nodeCleaner, HierarchicalOcclusionTraverser traversal, BooleanSupplier frexSupplier) {
        var irisPipe = Iris.getPipelineManager().getPipelineNullable();
        if (irisPipe == null) {
            Logger.info("[RenderPipelineFactory] Iris pipeline is null; cannot create IrisVoxyRenderPipeline yet");
            return null;
        }
        if (irisPipe instanceof IGetIrisVoxyPipelineData getVoxyPipeData) {
            var pipeData = getVoxyPipeData.voxy$getPipelineData();
            if (pipeData == null) {
                Logger.warn("[RenderPipelineFactory] Iris pipeline has no Voxy pipeline data (patch not ready/instrumented)");
                return null;
            }
            Logger.info("Creating voxy iris render pipeline");
            try {
                return new IrisVoxyRenderPipeline(pipeData, nodeManager, nodeCleaner, traversal, frexSupplier);
            } catch (Exception e) {
                Logger.error("Failed to create iris render pipeline", e);
                IrisUtil.disableIrisShaders();
                return null;
            }
        }
        Logger.warn("[RenderPipelineFactory] Iris pipeline class is not Voxy-instrumented: " + irisPipe.getClass().getName());
        return null;
    }
}
