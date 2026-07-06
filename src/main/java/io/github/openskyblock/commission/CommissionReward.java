package io.github.openskyblock.commission;

public record CommissionReward(
        double miningXp,
        double hotmXp,
        String powderId,
        double powder,
        double coins
) {
}
