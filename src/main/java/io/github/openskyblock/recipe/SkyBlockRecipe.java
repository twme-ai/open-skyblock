package io.github.openskyblock.recipe;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

public record SkyBlockRecipe(
        String id,
        String displayName,
        NamespacedKey key,
        ItemStack result,
        String requiredCollection,
        int requiredTier
) {
    public boolean hasRequirement() {
        return requiredCollection != null && !requiredCollection.isBlank() && requiredTier > 0;
    }
}
