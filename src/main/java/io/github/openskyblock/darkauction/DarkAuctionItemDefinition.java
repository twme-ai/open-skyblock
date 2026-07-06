package io.github.openskyblock.darkauction;

import java.util.List;
import org.bukkit.Material;

public record DarkAuctionItemDefinition(
        String id,
        String customItemId,
        Material material,
        String displayName,
        int amount,
        double startingBid,
        double weight,
        int maxPurchasesPerProfile,
        List<String> description
) {
}
