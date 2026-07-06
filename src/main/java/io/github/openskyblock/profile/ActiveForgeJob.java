package io.github.openskyblock.profile;

public record ActiveForgeJob(
        int slot,
        String recipeId,
        long startedAtMillis,
        long durationMillis
) {
    public long completedAtMillis() {
        return startedAtMillis + durationMillis;
    }

    public boolean complete(long nowMillis) {
        return nowMillis >= completedAtMillis();
    }

    public long remainingMillis(long nowMillis) {
        return Math.max(0L, completedAtMillis() - nowMillis);
    }
}
