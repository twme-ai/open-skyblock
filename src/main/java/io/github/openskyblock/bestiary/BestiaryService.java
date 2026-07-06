package io.github.openskyblock.bestiary;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.SkillService;
import io.github.openskyblock.service.SkillType;
import io.github.openskyblock.stats.StatSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class BestiaryService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final SkillService skills;
    private final EconomyService economy;
    private final Map<String, BestiaryFamilyDefinition> families = new HashMap<>();
    private final Map<String, List<BestiaryFamilyDefinition>> familiesByMob = new HashMap<>();

    public BestiaryService(ConfigService configService, TextService text, ProfileManager profiles, SkillService skills, EconomyService economy) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.skills = skills;
        this.economy = economy;
    }

    public void reload() {
        families.clear();
        familiesByMob.clear();
        ConfigurationSection section = configService.bestiary().getConfigurationSection("families");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection familySection = section.getConfigurationSection(id);
            if (familySection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            Set<String> mobIds = new HashSet<>();
            for (String mobId : familySection.getStringList("mobs")) {
                if (mobId != null && !mobId.isBlank()) {
                    mobIds.add(mobId.toUpperCase(Locale.ROOT));
                }
            }
            if (mobIds.isEmpty()) {
                mobIds.add(normalized);
            }
            BestiaryFamilyDefinition family = new BestiaryFamilyDefinition(
                    normalized,
                    familySection.getString("display-name", id),
                    familySection.getString("category", "General"),
                    Set.copyOf(mobIds),
                    readTiers(familySection.getConfigurationSection("tiers"))
            );
            families.put(normalized, family);
            for (String mobId : mobIds) {
                familiesByMob.computeIfAbsent(mobId, ignored -> new ArrayList<>()).add(family);
            }
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.bestiary", true);
    }

    public Optional<BestiaryFamilyDefinition> family(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(families.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<BestiaryFamilyDefinition> families() {
        return families.values().stream()
                .sorted(Comparator.comparing(BestiaryFamilyDefinition::category).thenComparing(BestiaryFamilyDefinition::id))
                .toList();
    }

    public List<String> familyIds() {
        return families().stream().map(BestiaryFamilyDefinition::id).toList();
    }

    public void recordKill(Player player, String mobId) {
        if (!enabled() || mobId == null || mobId.isBlank()) {
            return;
        }
        List<BestiaryFamilyDefinition> matchedFamilies = familiesByMob.getOrDefault(mobId.toUpperCase(Locale.ROOT), List.of());
        if (matchedFamilies.isEmpty()) {
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        for (BestiaryFamilyDefinition family : matchedFamilies) {
            long kills = profile.bestiaryKills().getOrDefault(family.id(), 0L) + 1L;
            profile.bestiaryKills().put(family.id(), kills);
            int beforeTier = profile.bestiaryTiers().getOrDefault(family.id(), 0);
            int afterTier = tier(family, kills);
            if (afterTier <= beforeTier) {
                continue;
            }
            for (int tier = beforeTier + 1; tier <= afterTier; tier++) {
                BestiaryTierDefinition reward = family.tiers().get(tier);
                if (reward != null) {
                    grantTier(player, family, reward);
                }
            }
            profile.bestiaryTiers().put(family.id(), afterTier);
        }
    }

    public Map<String, Double> activeStats(SkyBlockProfile profile) {
        if (!enabled()) {
            return Map.of();
        }
        Map<String, Double> stats = new HashMap<>();
        for (BestiaryFamilyDefinition family : families.values()) {
            int unlockedTier = profile.bestiaryTiers().getOrDefault(family.id(), 0);
            if (unlockedTier <= 0) {
                continue;
            }
            for (int tier : family.sortedTierNumbers()) {
                if (tier > unlockedTier) {
                    break;
                }
                BestiaryTierDefinition definition = family.tiers().get(tier);
                if (definition == null) {
                    continue;
                }
                for (Map.Entry<String, Double> entry : definition.stats().entrySet()) {
                    String stat = StatSnapshot.normalize(entry.getKey());
                    stats.put(stat, stats.getOrDefault(stat, 0.0D) + entry.getValue());
                }
            }
        }
        return stats;
    }

    public void sendSummary(Player player) {
        if (!enabled()) {
            text.send(player, "commands.bestiary-disabled");
            return;
        }
        List<BestiaryFamilyDefinition> values = families();
        if (values.isEmpty()) {
            text.send(player, "commands.bestiary-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.bestiary-header");
        for (BestiaryFamilyDefinition family : values) {
            long kills = profile.bestiaryKills().getOrDefault(family.id(), 0L);
            int tier = profile.bestiaryTiers().getOrDefault(family.id(), 0);
            int maxTier = maxTier(family);
            text.send(player, "commands.bestiary-line", List.of(
                    TextService.raw("id", family.id()),
                    TextService.parsed("family", family.displayName()),
                    TextService.raw("category", family.category()),
                    TextService.raw("kills", text.formatNumber(kills)),
                    TextService.raw("tier", Integer.toString(tier)),
                    TextService.raw("max_tier", Integer.toString(maxTier))
            ));
        }
    }

    public void sendDetail(Player player, String id) {
        if (!enabled()) {
            text.send(player, "commands.bestiary-disabled");
            return;
        }
        BestiaryFamilyDefinition family = family(id).orElse(null);
        if (family == null) {
            text.send(player, "commands.bestiary-unknown", List.of(TextService.raw("family", id)));
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        long kills = profile.bestiaryKills().getOrDefault(family.id(), 0L);
        int tier = profile.bestiaryTiers().getOrDefault(family.id(), 0);
        text.send(player, "commands.bestiary-detail", List.of(
                TextService.parsed("family", family.displayName()),
                TextService.raw("category", family.category()),
                TextService.raw("kills", text.formatNumber(kills)),
                TextService.raw("tier", Integer.toString(tier)),
                TextService.raw("max_tier", Integer.toString(maxTier(family)))
        ));
        BestiaryTierDefinition next = nextTier(family, kills).orElse(null);
        if (next == null) {
            text.send(player, "commands.bestiary-maxed");
            return;
        }
        long remaining = Math.max(0L, next.kills() - kills);
        text.send(player, "commands.bestiary-next", List.of(
                TextService.raw("tier", Integer.toString(next.tier())),
                TextService.raw("kills", text.formatNumber(next.kills())),
                TextService.raw("remaining", text.formatNumber(remaining))
        ));
    }

    private void grantTier(Player player, BestiaryFamilyDefinition family, BestiaryTierDefinition tier) {
        text.send(player, "commands.bestiary-tier-up", List.of(
                TextService.parsed("family", family.displayName()),
                TextService.raw("tier", Integer.toString(tier.tier()))
        ));
        if (tier.skillType() != null && tier.skillXp() > 0.0D) {
            skills.addXp(player, tier.skillType(), tier.skillXp());
        }
        if (tier.coins() > 0.0D) {
            economy.addPurse(player, tier.coins());
            text.send(player, "progression.coins", List.of(TextService.raw("coins", text.formatNumber(tier.coins()))));
        }
        for (Map.Entry<String, Double> entry : tier.stats().entrySet()) {
            text.send(player, "commands.bestiary-stat-reward", List.of(
                    TextService.raw("stat", statLabel(entry.getKey())),
                    TextService.raw("amount", text.formatNumber(entry.getValue()))
            ));
        }
    }

    private int tier(BestiaryFamilyDefinition family, long kills) {
        int current = 0;
        for (int tier : family.sortedTierNumbers()) {
            BestiaryTierDefinition definition = family.tiers().get(tier);
            if (definition != null && kills >= definition.kills()) {
                current = Math.max(current, definition.tier());
            }
        }
        return current;
    }

    private int maxTier(BestiaryFamilyDefinition family) {
        return family.tiers().keySet().stream().max(Integer::compareTo).orElse(0);
    }

    private Optional<BestiaryTierDefinition> nextTier(BestiaryFamilyDefinition family, long kills) {
        for (int tier : family.sortedTierNumbers()) {
            BestiaryTierDefinition definition = family.tiers().get(tier);
            if (definition != null && kills < definition.kills()) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }

    private Map<Integer, BestiaryTierDefinition> readTiers(ConfigurationSection section) {
        Map<Integer, BestiaryTierDefinition> tiers = new LinkedHashMap<>();
        if (section == null) {
            return tiers;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection tierSection = section.getConfigurationSection(key);
            if (tierSection == null) {
                continue;
            }
            try {
                int tier = Integer.parseInt(key);
                Map<String, Double> stats = new HashMap<>();
                ConfigurationSection statsSection = tierSection.getConfigurationSection("stats");
                if (statsSection != null) {
                    for (String stat : statsSection.getKeys(false)) {
                        stats.put(StatSnapshot.normalize(stat), statsSection.getDouble(stat, 0.0D));
                    }
                }
                SkillType skillType = SkillType.fromKey(tierSection.getString("skill", "COMBAT")).orElse(SkillType.COMBAT);
                tiers.put(tier, new BestiaryTierDefinition(
                        tier,
                        Math.max(1L, tierSection.getLong("kills", 1L)),
                        skillType,
                        Math.max(0.0D, tierSection.getDouble("xp", 0.0D)),
                        Math.max(0.0D, tierSection.getDouble("coins", 0.0D)),
                        Map.copyOf(stats)
                ));
            } catch (NumberFormatException ignored) {
                // Invalid tier keys are skipped so one typo does not disable the whole Bestiary.
            }
        }
        return Map.copyOf(tiers);
    }

    private String statLabel(String stat) {
        String normalized = StatSnapshot.normalize(stat);
        String configured = configService.messages().getString("items.stat-labels." + normalized);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String readable = normalized.replace('_', ' ');
        if (readable.isBlank()) {
            return stat;
        }
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }
}
