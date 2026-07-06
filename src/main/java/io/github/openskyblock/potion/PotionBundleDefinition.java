package io.github.openskyblock.potion;

import java.util.List;

public record PotionBundleDefinition(
        String id,
        String displayName,
        String itemId,
        long durationSeconds,
        long maxDurationSeconds,
        List<String> effectIds
) {
}
