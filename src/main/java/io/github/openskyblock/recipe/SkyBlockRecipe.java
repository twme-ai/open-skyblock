package io.github.openskyblock.recipe;

import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

public record SkyBlockRecipe(
        String id,
        String displayName,
        NamespacedKey key,
        ItemStack result,
        String requiredCollection,
        int requiredTier,
        Map<String, Integer> requiredSlayers
) {
    public boolean hasRequirement() {
        return hasCollectionRequirement() || hasSlayerRequirement();
    }

    public boolean hasCollectionRequirement() {
        return requiredCollection != null && !requiredCollection.isBlank() && requiredTier > 0;
    }

    public boolean hasSlayerRequirement() {
        return requiredSlayers != null && !requiredSlayers.isEmpty();
    }
}
