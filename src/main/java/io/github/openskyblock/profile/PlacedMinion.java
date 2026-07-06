package io.github.openskyblock.profile;

public final class PlacedMinion {
    private final String id;
    private long generatedAmount;
    private long lastActionMillis;

    public PlacedMinion(String id, long generatedAmount, long lastActionMillis) {
        this.id = id;
        this.generatedAmount = Math.max(0L, generatedAmount);
        this.lastActionMillis = lastActionMillis;
    }

    public String id() {
        return id;
    }

    public long generatedAmount() {
        return generatedAmount;
    }

    public void generatedAmount(long generatedAmount) {
        this.generatedAmount = Math.max(0L, generatedAmount);
    }

    public long lastActionMillis() {
        return lastActionMillis;
    }

    public void lastActionMillis(long lastActionMillis) {
        this.lastActionMillis = lastActionMillis;
    }
}
