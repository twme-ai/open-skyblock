package io.github.openskyblock.slayer;

import io.github.openskyblock.service.SkillType;

public record SlayerTierDefinition(
        int tier,
        double cost,
        double requiredXp,
        String bossMobId,
        double slayerXp,
        SkillType rewardSkill,
        double rewardSkillXp,
        double coins
) {
}
