package io.github.openskyblock.trade;

import java.util.UUID;

public record TradeRequest(
        UUID requesterId,
        String requesterName,
        UUID targetId,
        String targetName,
        long createdMillis
) {
}
