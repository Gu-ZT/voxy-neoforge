package me.cortex.voxy.client.compat;

import me.cortex.voxy.common.util.ModCompat;

public final class RendererCompatManager {
    private static final RendererCompat ACTIVE = load();

    private RendererCompatManager() {
    }

    public static int getBuilderThreadCount() {
        if (ACTIVE == null) {
            return -1;
        }
        try {
            return ACTIVE.getBuilderThreadCount();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static RendererCompat load() {
        try {
            if (ModCompat.isModLoaded("embeddium")) {
                return (RendererCompat) Class.forName("me.cortex.voxy.client.compat.EmbeddiumCompat")
                        .getDeclaredConstructor()
                        .newInstance();
            }
        } catch (Throwable ignored) {
        }

        return null;
    }
}
