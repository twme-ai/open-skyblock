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
    private final Map<String, Long> nextAbilityAtMillis = new HashMap<>();
    private double currentHealth;
    private String currentPhaseId = "";
    private String activeAbilityId = "";
    private UUID activeAbilityTargetId;
    private long activeAbilityEndsAtMillis;
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

    public String currentPhaseId() {
        return currentPhaseId;
    }

    public void currentPhaseId(String currentPhaseId) {
        this.currentPhaseId = currentPhaseId == null ? "" : currentPhaseId;
    }

    public long nextAbilityAtMillis(String abilityId) {
        return nextAbilityAtMillis.getOrDefault(abilityId, 0L);
    }

    public void scheduleAbility(String abilityId, long atMillis) {
        nextAbilityAtMillis.put(abilityId, atMillis);
    }

    public String activeAbilityId() {
        return activeAbilityId;
    }

    public UUID activeAbilityTargetId() {
        return activeAbilityTargetId;
    }

    public long activeAbilityEndsAtMillis() {
        return activeAbilityEndsAtMillis;
    }

    public boolean hasActiveAbility(long nowMillis) {
        return !activeAbilityId.isBlank() && nowMillis < activeAbilityEndsAtMillis;
    }

    public void beginAbility(String abilityId, UUID targetId, long endsAtMillis) {
        this.activeAbilityId = abilityId == null ? "" : abilityId;
        this.activeAbilityTargetId = targetId;
        this.activeAbilityEndsAtMillis = endsAtMillis;
    }

    public void clearActiveAbility() {
        this.activeAbilityId = "";
        this.activeAbilityTargetId = null;
        this.activeAbilityEndsAtMillis = 0L;
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
