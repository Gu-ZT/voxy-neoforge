package me.cortex.voxy.client.hud;

final class VoxyLoadingIndicatorModel {
    enum Mode {
        HIDDEN,
        INITIAL_LOAD,
        STREAMING
    }

    final Mode mode;
    final float alpha;
    final float progress;
    final float pulse;
    final int meshQueue;
    final int modelQueue;
    final boolean nodePending;

    VoxyLoadingIndicatorModel(Mode mode, float alpha, float progress, float pulse, int meshQueue, int modelQueue, boolean nodePending) {
        this.mode = mode;
        this.alpha = alpha;
        this.progress = progress;
        this.pulse = pulse;
        this.meshQueue = meshQueue;
        this.modelQueue = modelQueue;
        this.nodePending = nodePending;
    }

    boolean visible() {
        return this.mode != Mode.HIDDEN || this.alpha > 0.01f;
    }
}
