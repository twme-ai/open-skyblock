package io.github.openskyblock.service;

import java.util.List;
import org.bukkit.Material;

public record MinionSkinDefinition(
        String id,
        String displayName,
        Material material,
        String customItemId,
        Material blockMaterial,
        List<String> minionIds
) {
    public boolean customItem() {
        return customItemId != null && !customItemId.isBlank();
    }

    public boolean supports(String minionId) {
        return minionIds.isEmpty() || minionIds.stream().anyMatch(id -> id.equalsIgnoreCase(minionId));
    }
}
