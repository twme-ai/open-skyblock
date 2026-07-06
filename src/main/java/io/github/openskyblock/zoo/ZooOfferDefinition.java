package io.github.openskyblock.zoo;

import java.util.List;

public record ZooOfferDefinition(
        String id,
        String displayName,
        String petId,
        double coins,
        List<ZooCostItemDefinition> itemCosts
) {
}
