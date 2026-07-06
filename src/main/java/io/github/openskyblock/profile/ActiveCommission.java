package io.github.openskyblock.profile;

public final class ActiveCommission {
    private final int slot;
    private final String id;
    private long progress;

    public ActiveCommission(int slot, String id, long progress) {
        this.slot = slot;
        this.id = id;
        this.progress = Math.max(0L, progress);
    }

    public int slot() {
        return slot;
    }

    public String id() {
        return id;
    }

    public long progress() {
        return progress;
    }

    public void progress(long progress) {
        this.progress = Math.max(0L, progress);
    }

    public void addProgress(long amount) {
        progress(progress + amount);
    }
}
