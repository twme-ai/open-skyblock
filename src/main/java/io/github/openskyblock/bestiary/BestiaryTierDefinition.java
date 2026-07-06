package io.github.openskyblock.bestiary;

import io.github.openskyblock.service.SkillType;
import java.util.Map;

public record BestiaryTierDefinition(
        int tier,
        long kills,
        SkillType skillType,
        double skillXp,
        double coins,
        Map<String, Double> stats
) {
}
