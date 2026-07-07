package io.github.openskyblock.service;

public record MinionClaimResult(long resources, double coins) {
    public static MinionClaimResult empty() {
        return new MinionClaimResult(0L, 0.0D);
    }

    public MinionClaimResult add(MinionClaimResult other) {
        if (other == null) {
            return this;
        }
        return new MinionClaimResult(resources + other.resources(), coins + other.coins());
    }

    public boolean emptyResult() {
        return resources <= 0L && coins <= 0.0D;
    }
}
