package io.github.openskyblock.sack;

import java.util.List;
import org.bukkit.Material;

public record SackDefinition(
        String id,
        String displayName,
        Material material,
        String itemId,
        boolean requireItem,
        boolean autoPickup,
        long capacityPerItem,
        List<SackItemDefinition> items
) {
    public long capacity(SackItemDefinition item) {
        return capacityPerItem;
    }
}
