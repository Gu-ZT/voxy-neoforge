package me.cortex.voxy.client.compat;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.ModCompat;

public final class EmbeddiumOptionsCompatManager {
    private static final boolean EMBEDDIUM_PRESENT =
            ModCompat.isModLoaded("embeddium") || ModCompat.isClassPresent("org.embeddedt.embeddium.api.OptionGUIConstructionEvent");

    private EmbeddiumOptionsCompatManager() {
    }

    public static void register() {
        if (!EMBEDDIUM_PRESENT) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("me.cortex.voxy.client.compat.EmbeddiumOptionsCompat");
            clazz.getMethod("register").invoke(null);
        } catch (Throwable t) {
            Logger.warn("Failed to register Embeddium options integration", t);
        }
    }
}
