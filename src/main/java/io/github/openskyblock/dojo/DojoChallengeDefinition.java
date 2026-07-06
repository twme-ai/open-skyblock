package io.github.openskyblock.dojo;

public record DojoChallengeDefinition(
        String id,
        String displayName,
        String description,
        int maxScore,
        double pointsMultiplier,
        double combatXpPerPoint,
        double coinsPerPoint
) {
}
