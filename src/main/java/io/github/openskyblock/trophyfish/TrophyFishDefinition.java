package io.github.openskyblock.trophyfish;

import java.util.List;
import org.bukkit.Material;

public record TrophyFishDefinition(
        String id,
        String displayName,
        Material material,
        int requiredFishingLevel,
        double chance,
        double skillXp,
        String collectionId,
        long collectionAmount,
        List<String> lore
) {
}
