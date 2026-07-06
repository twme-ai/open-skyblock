package io.github.openskyblock.upgrade;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.stats.StatSnapshot;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class UpgradeService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final Map<String, UpgradeDefinition> definitions = new LinkedHashMap<>();

    public UpgradeService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
    }

    public void reload() {
        definitions.clear();
        ConfigurationSection section = configService.upgrades().getConfigurationSection("upgrades");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection upgradeSection = section.getConfigurationSection(id);
            if (upgradeSection == null) {
                continue;
            }
            Material material = Material.matchMaterial(upgradeSection.getString("material", "EMERALD"));
            String normalizedId = id.toUpperCase(Locale.ROOT);
            definitions.put(normalizedId, new UpgradeDefinition(
                    normalizedId,
                    upgradeSection.getString("display-name", id),
                    upgradeSection.getString("scope", "PROFILE").toUpperCase(Locale.ROOT),
                    material == null ? Material.EMERALD : material,
                    tiers(upgradeSection.getConfigurationSection("tiers"))
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.profile-upgrades", true);
    }

    public List<UpgradeDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(UpgradeDefinition::id))
                .toList();
    }

    public Optional<UpgradeDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public int level(SkyBlockProfile profile, String upgradeId) {
        return profile.upgrades().getOrDefault(upgradeId.toUpperCase(Locale.ROOT), 0);
    }

    public boolean purchase(Player player, String rawUpgradeId) {
        if (!enabled()) {
            text.send(player, "commands.upgrade-disabled");
            return false;
        }
        UpgradeDefinition definition = definition(rawUpgradeId).orElse(null);
        if (definition == null) {
            text.send(player, "commands.upgrade-unknown", List.of(TextService.raw("upgrade", rawUpgradeId == null ? "" : rawUpgradeId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int currentLevel = level(profile, definition.id());
        UpgradeTierDefinition nextTier = definition.tier(currentLevel + 1);
        if (nextTier == null) {
            text.send(player, "commands.upgrade-maxed", upgradePlaceholders(profile, definition, null));
            return false;
        }
        if (nextTier.cost() > 0.0D && !economy.spendPurse(player, nextTier.cost())) {
            text.send(player, "commands.upgrade-no-money", List.of(
                    TextService.parsed("upgrade", definition.displayName()),
                    TextService.raw("cost", text.formatNumber(nextTier.cost()))
            ));
            return false;
        }
        profile.upgrades().put(definition.id(), nextTier.level());
        text.send(player, "commands.upgrade-purchased", upgradePlaceholders(profile, definition, nextTier));
        return true;
    }

    public Map<String, Double> activeStats(SkyBlockProfile profile) {
        Map<String, Double> stats = new HashMap<>();
        for (UpgradeDefinition definition : definitions.values()) {
            UpgradeTierDefinition tier = definition.tier(level(profile, definition.id()));
            if (tier == null) {
                continue;
            }
            for (Map.Entry<String, Double> entry : tier.stats().entrySet()) {
                String stat = StatSnapshot.normalize(entry.getKey());
                stats.put(stat, stats.getOrDefault(stat, 0.0D) + entry.getValue());
            }
        }
        return stats;
    }

    public int capacityBonus(SkyBlockProfile profile, String capacityKey) {
        String normalized = StatSnapshot.normalize(capacityKey);
        int bonus = 0;
        for (UpgradeDefinition definition : definitions.values()) {
            UpgradeTierDefinition tier = definition.tier(level(profile, definition.id()));
            if (tier == null) {
                continue;
            }
            bonus += tier.capacities().getOrDefault(normalized, 0);
        }
        return bonus;
    }

    public void sendSummary(Player player) {
        if (!enabled()) {
            text.send(player, "commands.upgrade-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.upgrade-summary-header");
        for (UpgradeDefinition definition : definitions()) {
            UpgradeTierDefinition next = definition.tier(level(profile, definition.id()) + 1);
            text.send(player, "commands.upgrade-summary-line", upgradePlaceholders(profile, definition, next));
        }
    }

    public void sendDetails(Player player, String rawUpgradeId) {
        if (!enabled()) {
            text.send(player, "commands.upgrade-disabled");
            return;
        }
        UpgradeDefinition definition = definition(rawUpgradeId).orElse(null);
        if (definition == null) {
            text.send(player, "commands.upgrade-unknown", List.of(TextService.raw("upgrade", rawUpgradeId == null ? "" : rawUpgradeId)));
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.upgrade-detail-header", upgradePlaceholders(profile, definition, definition.tier(level(profile, definition.id()) + 1)));
        for (UpgradeTierDefinition tier : definition.tiers()) {
            text.send(player, "commands.upgrade-detail-line", List.of(
                    TextService.raw("level", Integer.toString(tier.level())),
                    TextService.raw("cost", text.formatNumber(tier.cost())),
                    TextService.raw("stats", formatStats(tier.stats())),
                    TextService.raw("capacities", formatCapacities(tier.capacities()))
            ));
        }
    }

    private List<TextService.TextPlaceholder> upgradePlaceholders(SkyBlockProfile profile, UpgradeDefinition definition, UpgradeTierDefinition nextTier) {
        int currentLevel = level(profile, definition.id());
        return List.of(
                TextService.raw("id", definition.id()),
                TextService.parsed("upgrade", definition.displayName()),
                TextService.raw("scope", definition.scope()),
                TextService.raw("level", Integer.toString(currentLevel)),
                TextService.raw("max_level", Integer.toString(definition.maxLevel())),
                TextService.raw("next_level", nextTier == null ? text.rawMessage("upgrades.maxed") : Integer.toString(nextTier.level())),
                TextService.raw("cost", nextTier == null ? text.rawMessage("upgrades.maxed") : text.formatNumber(nextTier.cost())),
                TextService.raw("stats", nextTier == null ? text.rawMessage("upgrades.none") : formatStats(nextTier.stats())),
                TextService.raw("capacities", nextTier == null ? text.rawMessage("upgrades.none") : formatCapacities(nextTier.capacities()))
        );
    }

    private String formatStats(Map<String, Double> stats) {
        if (stats.isEmpty()) {
            return text.rawMessage("upgrades.none");
        }
        return String.join(", ", stats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> text.statName(entry.getKey()) + " +" + text.formatNumber(entry.getValue()))
                .toList()
        );
    }

    private String formatCapacities(Map<String, Integer> capacities) {
        if (capacities.isEmpty()) {
            return text.rawMessage("upgrades.none");
        }
        return String.join(", ", capacities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> capacityLabel(entry.getKey()) + " +" + entry.getValue())
                .toList()
        );
    }

    private String capacityLabel(String capacity) {
        String configured = configService.messages().getString("upgrades.capacity-labels." + StatSnapshot.normalize(capacity));
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String readable = StatSnapshot.normalize(capacity).replace('_', ' ');
        if (readable.isBlank()) {
            return capacity;
        }
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }

    private List<UpgradeTierDefinition> tiers(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        return section.getKeys(false).stream()
                .map(section::getConfigurationSection)
                .filter(tierSection -> tierSection != null)
                .map(tierSection -> new UpgradeTierDefinition(
                        Math.max(1, tierSection.getInt("level", parseLevel(tierSection.getName()))),
                        Math.max(0.0D, tierSection.getDouble("cost", 0.0D)),
                        doubleMap(tierSection.getConfigurationSection("stats")),
                        integerMap(tierSection.getConfigurationSection("capacities"))
                ))
                .sorted(Comparator.comparingInt(UpgradeTierDefinition::level))
                .toList();
    }

    private int parseLevel(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private Map<String, Double> doubleMap(ConfigurationSection section) {
        Map<String, Double> values = new HashMap<>();
        if (section == null) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            values.put(StatSnapshot.normalize(key), section.getDouble(key, 0.0D));
        }
        return values;
    }

    private Map<String, Integer> integerMap(ConfigurationSection section) {
        Map<String, Integer> values = new HashMap<>();
        if (section == null) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            values.put(StatSnapshot.normalize(key), section.getInt(key, 0));
        }
        return values;
    }
}
