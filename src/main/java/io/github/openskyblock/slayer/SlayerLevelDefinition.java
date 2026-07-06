package io.github.openskyblock.slayer;

import io.github.openskyblock.service.SkillType;
import java.util.List;
import java.util.Map;

public record SlayerLevelDefinition(
        int level,
        double requiredXp,
        SkillType rewardSkill,
        double rewardSkillXp,
        double coins,
        Map<String, Double> stats,
        List<String> unlocks
) {
}
