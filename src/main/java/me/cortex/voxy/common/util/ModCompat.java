package me.cortex.voxy.common.util;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

public final class ModCompat {
    private ModCompat() {
    }

    public static boolean isModLoaded(String modId) {
        try {
            ModList modList = ModList.get();
            if (modList != null) {
                return modList.isLoaded(modId);
            }
        } catch (Throwable ignored) {
        }

        try {
            var loading = LoadingModList.get();
            return loading != null && loading.getModFileById(modId) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, ModCompat.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
