package io.github.openskyblock.faction;

public record FactionMinibossDefinition(
        String id,
        String displayName,
        long reputation,
        double combatXp,
        double coins,
        long requiredReputation
) {
}
