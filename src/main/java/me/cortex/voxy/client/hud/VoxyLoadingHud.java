package me.cortex.voxy.client.hud;

import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

public final class VoxyLoadingHud {
    public static final VoxyLoadingHud INSTANCE = new VoxyLoadingHud();

    private final VoxyLoadingIndicatorController controller = new VoxyLoadingIndicatorController();
    private final VoxyLoadingIndicatorRenderer renderer = new VoxyLoadingIndicatorRenderer();

    private VoxyLoadingHud() {
    }

    public void render(GuiGraphics gui, DeltaTracker tick) {
        VoxyRenderSystem vrs = IGetVoxyRenderSystem.getNullable();
        int rendererId = vrs == null ? 0 : System.identityHashCode(vrs);
        float partial = tick == null ? 0f : tick.getGameTimeDeltaPartialTick(false);
        var snapshot = vrs == null ? null : vrs.getLoadingSnapshot();
        var model = this.controller.update(snapshot, rendererId, partial);
        this.renderer.render(gui, model);
    }
}
