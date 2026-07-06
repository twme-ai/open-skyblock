package io.github.openskyblock.mob;

import io.github.openskyblock.service.SkillType;
import java.util.List;
import org.bukkit.entity.EntityType;

public record SkyBlockMobDefinition(
        String id,
        EntityType entityType,
        String displayName,
        int level,
        double health,
        double damage,
        double defense,
        SkillType skillType,
        double skillXp,
        double coins,
        String collectionId,
        long collectionAmount,
        boolean vanillaDrops,
        List<MobDropDefinition> drops
) {
}
