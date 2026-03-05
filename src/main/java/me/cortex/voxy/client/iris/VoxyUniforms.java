package me.cortex.voxy.client.iris;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.function.Supplier;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public class VoxyUniforms {
    private static IGetVoxyRenderSystem getRendererAccessor() {
        var levelRenderer = Minecraft.getInstance().levelRenderer;
        return levelRenderer instanceof IGetVoxyRenderSystem getVrs ? getVrs : null;
    }

    // Keep parity with upstream behavior: use getViewport(), which returns null during Iris
    // shadow passes. This avoids feeding shadow matrices into temporal history uniforms.
    public static Matrix4f getViewProjection() {
        var getVrs = getRendererAccessor();
        if (getVrs == null || getVrs.getVoxyRenderSystem() == null) {
            return new Matrix4f();
        }
        var viewport = getVrs.getVoxyRenderSystem().getViewport();
        if (viewport == null || viewport.MVP == null) return new Matrix4f();
        return new Matrix4f(viewport.MVP);
    }

    public static Matrix4f getModelView() {
        var getVrs = getRendererAccessor();
        if (getVrs == null || getVrs.getVoxyRenderSystem() == null) {
            return new Matrix4f();
        }
        var viewport = getVrs.getVoxyRenderSystem().getViewport();
        if (viewport == null || viewport.modelView == null) return new Matrix4f();
        return new Matrix4f(viewport.modelView);
    }

    public static Matrix4f getProjection() {
        var getVrs = getRendererAccessor();
        if (getVrs == null || getVrs.getVoxyRenderSystem() == null) {
            return new Matrix4f();
        }
        var viewport = getVrs.getVoxyRenderSystem().getViewport();
        if (viewport == null || viewport.projection == null) return new Matrix4f();
        return new Matrix4f(viewport.projection);
    }

    private static int getViewportFrameId() {
        var getVrs = getRendererAccessor();
        if (getVrs == null || getVrs.getVoxyRenderSystem() == null) return -1;
        var viewport = getVrs.getVoxyRenderSystem().getViewport();
        return viewport == null ? -1 : viewport.frameId;
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
        private int lastFrameId = Integer.MIN_VALUE;

        PreviousMat(Supplier<Matrix4fc> parent) {
            this.parent = parent;
            this.previous = new Matrix4f();
        }

        public Matrix4fc get() {
            Matrix4f previous = this.previous;
            int frameId = getViewportFrameId();
            if (frameId != this.lastFrameId) {
                this.previous = new Matrix4f(this.parent.get());
                this.lastFrameId = frameId;
            }
            return previous;
        }
    }
}
