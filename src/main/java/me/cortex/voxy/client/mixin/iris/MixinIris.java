package me.cortex.voxy.client.mixin.iris;

import me.cortex.voxy.client.iris.ShaderLoadError;
import me.cortex.voxy.common.Logger;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = Iris.class, remap = false)
public class MixinIris {
    @Unique
    private static final ConcurrentHashMap<String, Boolean> VOXY_DIMENSION_FALLBACK_LOGGED = new ConcurrentHashMap<>();

    @Unique
    private static volatile Field voxy$dimensionMapField;

    @Unique
    private static volatile boolean voxy$dimensionMapFieldResolved;

    @Redirect(method = "createPipeline", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/shaderpack/ShaderPack;getProgramSet(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;"))
    private static ProgramSet voxy$redirectProgramSet(ShaderPack shaderPack, NamespacedId dim) {
        NamespacedId effectiveDim = voxy$resolveProgramDimension(shaderPack, dim);
        try {
            return shaderPack.getProgramSet(effectiveDim);
        } catch (ShaderLoadError e) {
            Logger.error(e);
            return null;
        }
    }

    @Unique
    private static NamespacedId voxy$resolveProgramDimension(ShaderPack shaderPack, NamespacedId dim) {
        if (dim == null) {
            return DimensionId.OVERWORLD;
        }

        // Preserve vanilla dimensions and any shader-pack explicit custom mapping.
        if (dim.equals(DimensionId.OVERWORLD) || dim.equals(DimensionId.NETHER) || dim.equals(DimensionId.END)
                || voxy$hasExplicitDimensionMapping(shaderPack, dim)) {
            return dim;
        }

        NamespacedId fallback = voxy$inferFallbackDimension(dim);
        String key = dim.toString() + "->" + fallback.toString();
        if (VOXY_DIMENSION_FALLBACK_LOGGED.putIfAbsent(key, Boolean.TRUE) == null) {
            Logger.info("[IrisCompat] No explicit shader dimension mapping for " + dim
                    + "; routing to " + fallback + " program set");
        }
        return fallback;
    }

    @Unique
    private static NamespacedId voxy$inferFallbackDimension(NamespacedId originalDim) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) {
            return DimensionId.OVERWORLD;
        }

        if (level.dimension() == Level.NETHER) {
            return DimensionId.NETHER;
        }
        if (level.dimension() == Level.END) {
            return DimensionId.END;
        }

        // For custom dimensions, use overworld semantics by default unless the ID clearly declares nether/end intent.
        String path = originalDim.getName().toLowerCase();
        if (path.contains("nether")) {
            return DimensionId.NETHER;
        }
        if (path.contains("end")) {
            return DimensionId.END;
        }
        return DimensionId.OVERWORLD;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static boolean voxy$hasExplicitDimensionMapping(ShaderPack shaderPack, NamespacedId dim) {
        try {
            if (!voxy$dimensionMapFieldResolved) {
                Field f = ShaderPack.class.getDeclaredField("dimensionMap");
                f.setAccessible(true);
                voxy$dimensionMapField = f;
                voxy$dimensionMapFieldResolved = true;
            }
            Field f = voxy$dimensionMapField;
            if (f == null) {
                return false;
            }
            Map<NamespacedId, String> mapping = (Map<NamespacedId, String>) f.get(shaderPack);
            return mapping != null && mapping.containsKey(dim);
        } catch (Throwable t) {
            return false;
        }
    }
}
