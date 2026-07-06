package io.github.openskyblock.commission;

public record CommissionDefinition(
        String id,
        String displayName,
        String description,
        String type,
        String target,
        long amount,
        double weight,
        CommissionReward reward
) {
}
