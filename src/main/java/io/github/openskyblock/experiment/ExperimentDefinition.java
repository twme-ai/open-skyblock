package io.github.openskyblock.experiment;

import java.util.List;

public record ExperimentDefinition(
        String id,
        String displayName,
        String type,
        int requiredEnchantingLevel,
        int dailyLimit,
        double cost,
        double enchantingXp,
        int bonusClicks,
        boolean consumesBonusClicks,
        int baseClicks,
        int clicksPerExtraRoll,
        int rewardRolls,
        List<ExperimentRewardDefinition> rewards
) {
}
