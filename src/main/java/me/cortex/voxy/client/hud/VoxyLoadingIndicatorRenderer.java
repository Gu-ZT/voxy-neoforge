package me.cortex.voxy.client.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

final class VoxyLoadingIndicatorRenderer {
    private static final int WIDTH = 110;
    private static final int HEIGHT = 4;
    private static final int MARGIN = 10;

    public void render(GuiGraphics gui, VoxyLoadingIndicatorModel model) {
        if (!model.visible()) {
            return;
        }

        int x2 = gui.guiWidth() - MARGIN;
        int y2 = gui.guiHeight() - MARGIN;
        int x1 = x2 - WIDTH;
        int y1 = y2 - HEIGHT;

        int bg = color(0x101317, 0.45f * model.alpha);
        gui.fill(x1, y1, x2, y2, bg);

        int fillColor = getFillColor(model);
        if (model.mode == VoxyLoadingIndicatorModel.Mode.INITIAL_LOAD) {
            int fillWidth = Math.max(1, Math.round((WIDTH - 1) * model.progress));
            gui.fill(x1 + 1, y1 + 1, x1 + fillWidth, y2 - 1, color(fillColor, model.alpha));
        } else if (model.mode == VoxyLoadingIndicatorModel.Mode.STREAMING) {
            float span = 0.32f;
            float lead = model.pulse;
            int left = x1 + 1 + Math.round((WIDTH - 2) * lead);
            int right = left + Math.max(8, Math.round((WIDTH - 2) * span));
            int clampedLeft = Mth.clamp(left, x1 + 1, x2 - 1);
            int clampedRight = Mth.clamp(right, x1 + 1, x2 - 1);
            if (clampedLeft < clampedRight) {
                gui.fill(clampedLeft, y1 + 1, clampedRight, y2 - 1, color(fillColor, model.alpha));
            }
        }

        if (model.nodePending) {
            gui.fill(x1 - 3, y1, x1 - 1, y2, color(0xD6FFF6, model.alpha));
        }
    }

    private static int getFillColor(VoxyLoadingIndicatorModel model) {
        if (model.modelQueue > model.meshQueue) {
            return 0x5CB8FF;
        }
        if (model.meshQueue > 0) {
            return 0xE2BE6E;
        }
        return 0x7FD8C5;
    }

    private static int color(int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255f), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
