package io.github.openskyblock.equipment;

import org.bukkit.Material;

public record EquipmentSlotDefinition(
        String id,
        String displayName,
        Material material
) {
}
