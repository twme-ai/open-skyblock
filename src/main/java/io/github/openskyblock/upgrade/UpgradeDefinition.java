package io.github.openskyblock.upgrade;

import java.util.List;
import org.bukkit.Material;

public record UpgradeDefinition(
        String id,
        String displayName,
        String scope,
        Material material,
        List<UpgradeTierDefinition> tiers
) {
    public int maxLevel() {
        return tiers.stream()
                .mapToInt(UpgradeTierDefinition::level)
                .max()
                .orElse(0);
    }

    public UpgradeTierDefinition tier(int level) {
        return tiers.stream()
                .filter(tier -> tier.level() == level)
                .findFirst()
                .orElse(null);
    }
}
