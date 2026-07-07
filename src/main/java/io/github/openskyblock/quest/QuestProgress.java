package io.github.openskyblock.quest;

public record QuestProgress(double current, double required, boolean complete, double percent) {
}
