package me.cortex.voxy.client.compat;

import me.cortex.voxy.client.core.AbstractRenderPipeline;
import me.cortex.voxy.client.core.IrisVoxyRenderPipeline;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.client.iris.IGetIrisVoxyPipelineData;
import me.cortex.voxy.common.Logger;
import net.irisshaders.iris.Iris;

import me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.hierachical.NodeCleaner;

import java.util.function.BooleanSupplier;

public final class IrisCompat {
    private IrisCompat() {
    }

    public static boolean isShaderPackEnabled() {
        return IrisUtil.irisShaderPackEnabled();
    }

    public static boolean isShadowActive() {
        return IrisUtil.irisShadowActive();
    }

    public static void clearSamplers() {
        IrisUtil.clearIrisSamplers();
    }

    public static void disableShaders() {
        IrisUtil.disableIrisShaders();
    }

    public static AbstractRenderPipeline createPipeline(AsyncNodeManager nodeManager,
                                                        NodeCleaner nodeCleaner,
                                                        HierarchicalOcclusionTraverser traversal,
                                                        BooleanSupplier frexSupplier) {
        if (!IrisUtil.IRIS_INSTALLED || !IrisUtil.SHADER_SUPPORT) {
            Logger.info("[IrisCompat] createPipeline: IRIS_INSTALLED=" + IrisUtil.IRIS_INSTALLED + " SHADER_SUPPORT=" + IrisUtil.SHADER_SUPPORT + " -> null");
            return null;
        }
        var irisPipe = Iris.getPipelineManager().getPipelineNullable();
        if (irisPipe == null) {
            Logger.info("[IrisCompat] createPipeline: irisPipe == null -> NormalRenderPipeline");
            return null;
        }
        Logger.info("[IrisCompat] createPipeline: irisPipe=" + irisPipe.getClass().getName()
                + " isIGetIrisVoxyPipelineData=" + (irisPipe instanceof IGetIrisVoxyPipelineData)
                + " isPackInUse=" + IrisUtil.irisShaderPackEnabled());
        if (irisPipe instanceof IGetIrisVoxyPipelineData getVoxyPipeData) {
            var pipeData = getVoxyPipeData.voxy$getPipelineData();
            if (pipeData == null) {
                Logger.warn("[IrisCompat] createPipeline: pipeData == null (MixinIrisRenderingPipeline.buildPipeline may have thrown) -> NormalRenderPipeline");
                return null;
            }
            Logger.info("[IrisCompat] Creating Voxy Iris render pipeline (pipeData.thePipeline=" + pipeData.thePipeline + ")");
            try {
                return new IrisVoxyRenderPipeline(pipeData, nodeManager, nodeCleaner, traversal, frexSupplier);
            } catch (Exception e) {
                Logger.error("Failed to create Iris render pipeline", e);
                IrisUtil.disableIrisShaders();
                return null;
            }
        }
        if (IrisUtil.irisShaderPackEnabled()) {
            Logger.warn("Shader pack is active, but Iris pipeline is not instrumented for Voxy. " +
                    "Falling back to NormalRenderPipeline. Iris pipeline class: " + irisPipe.getClass().getName());
        }
        return null;
    }
}
