package io.github.openskyblock.profile;

import java.util.UUID;

public final class ActiveSlayerQuest {
    private final String slayerId;
    private final int tier;
    private double progressXp;
    private boolean bossSpawned;
    private UUID bossEntityId;
    private long bossExpiresAtMillis;

    public ActiveSlayerQuest(String slayerId, int tier, double progressXp, boolean bossSpawned) {
        this(slayerId, tier, progressXp, bossSpawned, null, 0L);
    }

    public ActiveSlayerQuest(String slayerId, int tier, double progressXp, boolean bossSpawned, UUID bossEntityId, long bossExpiresAtMillis) {
        this.slayerId = slayerId;
        this.tier = tier;
        this.progressXp = progressXp;
        this.bossSpawned = bossSpawned;
        this.bossEntityId = bossEntityId;
        this.bossExpiresAtMillis = Math.max(0L, bossExpiresAtMillis);
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

    public UUID bossEntityId() {
        return bossEntityId;
    }

    public void bossEntityId(UUID bossEntityId) {
        this.bossEntityId = bossEntityId;
    }

    public long bossExpiresAtMillis() {
        return bossExpiresAtMillis;
    }

    public void bossExpiresAtMillis(long bossExpiresAtMillis) {
        this.bossExpiresAtMillis = Math.max(0L, bossExpiresAtMillis);
    }
}
