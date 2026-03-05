package me.cortex.voxy.client.compat;

import me.cortex.voxy.client.core.AbstractRenderPipeline;
import me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.hierachical.NodeCleaner;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.ModCompat;

import java.util.function.BooleanSupplier;

public final class IrisCompatManager {
    private static final boolean IRIS_PRESENT =
            ModCompat.isModLoaded("iris") || ModCompat.isClassPresent("net.irisshaders.iris.api.v0.IrisApi");

    private IrisCompatManager() {
    }

    public static AbstractRenderPipeline tryCreatePipeline(AsyncNodeManager nodeManager,
                                                           NodeCleaner nodeCleaner,
                                                           HierarchicalOcclusionTraverser traversal,
                                                           BooleanSupplier frexSupplier) {
        if (!IRIS_PRESENT) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName("me.cortex.voxy.client.compat.IrisCompat");
            return (AbstractRenderPipeline) clazz
                    .getMethod("createPipeline", AsyncNodeManager.class, NodeCleaner.class, HierarchicalOcclusionTraverser.class, BooleanSupplier.class)
                    .invoke(null, nodeManager, nodeCleaner, traversal, frexSupplier);
        } catch (Throwable t) {
            Logger.warn("Failed to initialize Iris compat pipeline", t);
            return null;
        }
    }

    public static boolean isShaderPackEnabled() {
        if (!IRIS_PRESENT) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName("me.cortex.voxy.client.compat.IrisCompat");
            return (boolean) clazz.getMethod("isShaderPackEnabled").invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isShadowActive() {
        if (!IRIS_PRESENT) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName("me.cortex.voxy.client.compat.IrisCompat");
            return (boolean) clazz.getMethod("isShadowActive").invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void clearSamplers() {
        if (!IRIS_PRESENT) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("me.cortex.voxy.client.compat.IrisCompat");
            clazz.getMethod("clearSamplers").invoke(null);
        } catch (Throwable ignored) {
        }
    }

    public static void disableShaders() {
        if (!IRIS_PRESENT) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("me.cortex.voxy.client.compat.IrisCompat");
            clazz.getMethod("disableShaders").invoke(null);
        } catch (Throwable ignored) {
        }
    }

    public static void reloadShaders() {
        if (!IRIS_PRESENT) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("me.cortex.voxy.client.compat.IrisCompat");
            clazz.getMethod("reloadShaders").invoke(null);
        } catch (Throwable t) {
            Logger.warn("Failed to reload Iris shaders", t);
        }
    }
}
