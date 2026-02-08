package me.cortex.voxy.client.iris;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.util.DHImpersonationSemantics;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.function.Supplier;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public class VoxyUniforms {
    private static Viewport<?> getViewportOrNull() {
        var levelRenderer = Minecraft.getInstance().levelRenderer;
        if (!(levelRenderer instanceof IGetVoxyRenderSystem getVrs)) {
            return null;
        }
        VoxyRenderSystem vrs = getVrs.getVoxyRenderSystem();
        if (vrs == null) {
            return null;
        }
        return vrs.getViewportForUniforms();
    }

    public static Matrix4f getViewProjection() {
        var viewport = getViewportOrNull();
        if (viewport == null || viewport.MVP == null) {
            return new Matrix4f();
        }
        return new Matrix4f(viewport.MVP);
    }

    public static Matrix4f getModelView() {
        var viewport = getViewportOrNull();
        if (viewport == null || viewport.modelView == null) {
            return new Matrix4f();
        }
        return new Matrix4f(viewport.modelView);
    }

    public static Matrix4f getProjection() {
        var viewport = getViewportOrNull();
        if (viewport == null || viewport.projection == null) {
            return new Matrix4f();
        }
        return new Matrix4f(viewport.projection);
    }

    public static void addUniforms(UniformHolder uniforms) {
        uniforms
                .uniform1i(PER_FRAME, "vxRenderDistance", ()-> VoxyConfig.CONFIG.getSectionRenderDistance()*32)//In chunks
                .uniformMatrix(PER_FRAME, "vxViewProj", VoxyUniforms::getViewProjection)
                .uniformMatrix(PER_FRAME, "vxViewProjInv", new Inverted(VoxyUniforms::getViewProjection))
                .uniformMatrix(PER_FRAME, "vxViewProjPrev", new PreviousMat(VoxyUniforms::getViewProjection))
                .uniformMatrix(PER_FRAME, "vxModelView", VoxyUniforms::getModelView)
                .uniformMatrix(PER_FRAME, "vxModelViewInv", new Inverted(VoxyUniforms::getModelView))
                .uniformMatrix(PER_FRAME, "vxModelViewPrev", new PreviousMat(VoxyUniforms::getModelView))
                .uniformMatrix(PER_FRAME, "vxProj", VoxyUniforms::getProjection)
                .uniformMatrix(PER_FRAME, "vxProjInv", new Inverted(VoxyUniforms::getProjection))
                .uniformMatrix(PER_FRAME, "vxProjPrev", new PreviousMat(VoxyUniforms::getProjection));

        if (IrisShaderPatch.shouldImpersonateDistantHorizons()) {
            uniforms
                    .uniform1f(PER_FRAME, "dhNearPlane", DHImpersonationSemantics::getNearPlaneBlocks)
                    .uniform1f(PER_FRAME, "dhFarPlane", DHImpersonationSemantics::getFarPlaneBlocks)
                    .uniform1i(PER_FRAME, "dhRenderDistance", DHImpersonationSemantics::getRenderDistanceBlocks)
                    .uniformMatrix(PER_FRAME, "dhProjection", VoxyUniforms::getProjection)
                    .uniformMatrix(PER_FRAME, "dhProjectionInverse", new Inverted(VoxyUniforms::getProjection))
                    .uniformMatrix(PER_FRAME, "dhPreviousProjection", new PreviousMat(VoxyUniforms::getProjection));
        }
    }




    private record Inverted(Supplier<Matrix4fc> parent) implements Supplier<Matrix4fc> {
        private Inverted(Supplier<Matrix4fc> parent) {
            this.parent = parent;
        }

        public Matrix4fc get() {
            Matrix4f copy = new Matrix4f(this.parent.get());
            copy.invert();
            return copy;
        }

        public Supplier<Matrix4fc> parent() {
            return this.parent;
        }
    }

    private static class PreviousMat implements Supplier<Matrix4fc> {
        private final Supplier<Matrix4fc> parent;
        private Matrix4f previous;

        PreviousMat(Supplier<Matrix4fc> parent) {
            this.parent = parent;
            this.previous = new Matrix4f();
        }

        public Matrix4fc get() {
            Matrix4f previous = this.previous;
            this.previous = new Matrix4f(this.parent.get());
            return previous;
        }
    }
}
