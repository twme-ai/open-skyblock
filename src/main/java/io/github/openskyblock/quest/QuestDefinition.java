package io.github.openskyblock.quest;

import java.util.List;

public record QuestDefinition(
        String id,
        String displayName,
        String category,
        String material,
        List<String> description,
        QuestObjectiveType objectiveType,
        String target,
        double required,
        int sort
) {
}
