package io.github.openskyblock.profile;

public final class ActiveSlayerQuest {
    private final String slayerId;
    private final int tier;
    private double progressXp;
    private boolean bossSpawned;

    public ActiveSlayerQuest(String slayerId, int tier, double progressXp, boolean bossSpawned) {
        this.slayerId = slayerId;
        this.tier = tier;
        this.progressXp = progressXp;
        this.bossSpawned = bossSpawned;
    }

    public String slayerId() {
        return slayerId;
    }

    public int tier() {
        return tier;
    }

    public double progressXp() {
        return progressXp;
    }

    public void progressXp(double progressXp) {
        this.progressXp = Math.max(0.0D, progressXp);
    }

    public boolean bossSpawned() {
        return bossSpawned;
    }

    public void bossSpawned(boolean bossSpawned) {
        this.bossSpawned = bossSpawned;
    }
}
