package io.github.openskyblock.dragon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActiveDragonEncounter {
    private final UUID entityId;
    private final UUID ownerId;
    private final UUID worldId;
    private final String dragonId;
    private final int eyes;
    private final double maximumHealth;
    private final long startedAtMillis;
    private final long expiresAtMillis;
    private final Map<UUID, Double> damageByPlayer = new HashMap<>();
    private double currentHealth;
    private boolean completing;

    public ActiveDragonEncounter(
            UUID entityId,
            UUID ownerId,
            UUID worldId,
            String dragonId,
            int eyes,
            double maximumHealth,
            long startedAtMillis,
            long expiresAtMillis
    ) {
        this.entityId = entityId;
        this.ownerId = ownerId;
        this.worldId = worldId;
        this.dragonId = dragonId;
        this.eyes = eyes;
        this.maximumHealth = maximumHealth;
        this.currentHealth = maximumHealth;
        this.startedAtMillis = startedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
    }

    public UUID entityId() {
        return entityId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public UUID worldId() {
        return worldId;
    }

    public String dragonId() {
        return dragonId;
    }

    public int eyes() {
        return eyes;
    }

    public double maximumHealth() {
        return maximumHealth;
    }

    public double currentHealth() {
        return currentHealth;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public Map<UUID, Double> damageByPlayer() {
        return Map.copyOf(damageByPlayer);
    }

    public double damage(UUID playerId) {
        return damageByPlayer.getOrDefault(playerId, 0.0D);
    }

    public double applyDamage(UUID playerId, double damage) {
        double applied = Math.max(0.0D, Math.min(currentHealth, damage));
        if (applied <= 0.0D) {
            return 0.0D;
        }
        damageByPlayer.merge(playerId, applied, Double::sum);
        currentHealth = Math.max(0.0D, currentHealth - applied);
        return applied;
    }

    public boolean completing() {
        return completing;
    }

    public boolean beginCompletion() {
        if (completing) {
            return false;
        }
        completing = true;
        return true;
    }
}
