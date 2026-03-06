package me.cortex.voxy.client.core;

public record VoxyLoadingSnapshot(
        int meshQueue,
        int modelQueue,
        boolean nodeWorkPending,
        int loadedSections,
        boolean shuttingDown) {

    public int pendingUnits() {
        return this.meshQueue + this.modelQueue + (this.nodeWorkPending ? 24 : 0);
    }

    public boolean hasWork() {
        return this.pendingUnits() > 0;
    }
}
