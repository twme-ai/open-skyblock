package io.github.openskyblock.service;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
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

public final class CollectionService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final Map<String, CollectionDefinition> definitions = new HashMap<>();

    public CollectionService(ConfigService configService, TextService text, ProfileManager profiles) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
    }

    public void reload() {
        definitions.clear();
        ConfigurationSection section = configService.collections().getConfigurationSection("collections");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection collectionSection = section.getConfigurationSection(id);
            if (collectionSection == null) {
                continue;
            }
            Material material = Material.matchMaterial(collectionSection.getString("material", ""));
            Map<Integer, CollectionTier> tiers = new LinkedHashMap<>();
            ConfigurationSection tierSection = collectionSection.getConfigurationSection("tiers");
            if (tierSection != null) {
                for (String tierKey : tierSection.getKeys(false)) {
                    try {
                        int tier = Integer.parseInt(tierKey);
                        tiers.put(tier, new CollectionTier(
                                tier,
                                tierSection.getLong(tierKey + ".amount", 0L),
                                tierSection.getStringList(tierKey + ".rewards")
                        ));
                    } catch (NumberFormatException ignored) {
                        // Invalid collection tier keys are skipped.
                    }
                }
            }
            definitions.put(id.toUpperCase(Locale.ROOT), new CollectionDefinition(
                    id.toUpperCase(Locale.ROOT),
                    collectionSection.getString("display-name", id),
                    material == null ? Material.STONE : material,
                    tiers
            ));
        }
    }

    public Optional<CollectionDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<CollectionDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(CollectionDefinition::id))
                .toList();
    }

    public void addProgress(Player player, String collectionId, long amount) {
        if (!configService.main().getBoolean("features.collections", true)) {
            return;
        }
        CollectionDefinition definition = definition(collectionId).orElse(null);
        if (definition == null) {
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        long beforeAmount = profile.collectionAmount(definition.id());
        int beforeTier = tier(definition, beforeAmount);
        profile.addCollectionAmount(definition.id(), amount);
        long afterAmount = profile.collectionAmount(definition.id());
        int afterTier = tier(definition, afterAmount);
        text.send(player, "progression.collection-progress", List.of(
                TextService.raw("amount", text.formatNumber(amount)),
                TextService.parsed("collection", definition.displayName())
        ));
        if (afterTier > beforeTier) {
            text.send(player, "progression.collection-tier-up", List.of(
                    TextService.parsed("collection", definition.displayName()),
                    TextService.raw("tier", Integer.toString(afterTier))
            ));
        }
    }

    public int tier(CollectionDefinition definition, long amount) {
        int tier = 0;
        for (Integer tierNumber : definition.sortedTierNumbers()) {
            CollectionTier collectionTier = definition.tiers().get(tierNumber);
            if (collectionTier != null && amount >= collectionTier.amount()) {
                tier = Math.max(tier, collectionTier.tier());
            }
        }
        return tier;
    }

    public int tier(SkyBlockProfile profile, String collectionId) {
        CollectionDefinition definition = definition(collectionId).orElse(null);
        if (definition == null) {
            return 0;
        }
        return tier(definition, profile.collectionAmount(definition.id()));
    }
}
