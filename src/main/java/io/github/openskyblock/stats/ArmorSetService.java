package io.github.openskyblock.stats;

import io.github.openskyblock.config.ConfigService;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;

public final class ArmorSetService {
    private final ConfigService configService;
    private final Map<String, ArmorSetDefinition> definitions = new HashMap<>();

    public ArmorSetService(ConfigService configService) {
        this.configService = configService;
    }

    public void reload() {
        definitions.clear();
        ConfigurationSection section = configService.armorSets().getConfigurationSection("sets");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection setSection = section.getConfigurationSection(id);
            if (setSection == null) {
                continue;
            }
            Map<String, Double> stats = new HashMap<>();
            ConfigurationSection statsSection = setSection.getConfigurationSection("stats");
            if (statsSection != null) {
                for (String stat : statsSection.getKeys(false)) {
                    stats.put(StatSnapshot.normalize(stat), statsSection.getDouble(stat, 0.0D));
                }
            }
            definitions.put(id.toUpperCase(Locale.ROOT), new ArmorSetDefinition(
                    id.toUpperCase(Locale.ROOT),
                    setSection.getString("display-name", id),
                    Math.max(1, setSection.getInt("required-pieces", 4)),
                    setSection.getStringList("lore"),
                    stats
            ));
        }
    }

    public Optional<ArmorSetDefinition> definition(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<ArmorSetDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(ArmorSetDefinition::id))
                .toList();
    }
}
