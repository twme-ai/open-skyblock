package io.github.openskyblock.service;

import java.util.List;

public record CollectionTier(int tier, long amount, List<String> rewards) {
}
