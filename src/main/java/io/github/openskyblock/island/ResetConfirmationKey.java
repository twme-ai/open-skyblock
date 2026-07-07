package io.github.openskyblock.island;

import java.util.UUID;

record ResetConfirmationKey(UUID requesterId, UUID islandOwnerId) {
}
