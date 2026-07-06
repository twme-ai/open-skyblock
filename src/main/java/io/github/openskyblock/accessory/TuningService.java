package io.github.openskyblock.accessory;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.stats.StatSnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class TuningService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final AccessoryService accessories;

    public TuningService(ConfigService configService, TextService text, ProfileManager profiles, AccessoryService accessories) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.accessories = accessories;
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.accessory-tuning", true);
    }

    public int totalPoints(SkyBlockProfile profile) {
        int magicalPower = accessories.magicalPower(profile);
        int magicalPowerPerPoint = Math.max(1, configService.main().getInt("stats.tuning.magical-power-per-point", 10));
        return magicalPower / magicalPowerPerPoint;
    }

    public int usedPoints(SkyBlockProfile profile) {
        return profile.tuning().values().stream().mapToInt(Integer::intValue).sum();
    }

    public int availablePoints(SkyBlockProfile profile) {
        return Math.max(0, totalPoints(profile) - usedPoints(profile));
    }

    public Map<String, Double> tuningBonuses(SkyBlockProfile profile) {
        Map<String, Double> bonuses = new HashMap<>();
        int remainingBudget = totalPoints(profile);
        for (Map.Entry<String, Integer> entry : profile.tuning().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            String stat = StatSnapshot.normalize(entry.getKey());
            double value = tuningValue(stat);
            if (value <= 0.0D || entry.getValue() <= 0 || remainingBudget <= 0) {
                continue;
            }
            int appliedPoints = Math.min(entry.getValue(), remainingBudget);
            bonuses.put(stat, bonuses.getOrDefault(stat, 0.0D) + appliedPoints * value);
            remainingBudget -= appliedPoints;
        }
        return bonuses;
    }

    public boolean addPoint(Player player, String rawStat) {
        if (!enabled()) {
            text.send(player, "commands.tuning-disabled");
            return false;
        }
        String stat = StatSnapshot.normalize(rawStat);
        if (!isTunable(stat)) {
            text.send(player, "commands.tuning-unknown-stat", List.of(TextService.raw("stat", rawStat)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (availablePoints(profile) <= 0) {
            text.send(player, "commands.tuning-no-points");
            return false;
        }
        profile.addTuning(stat, 1);
        text.send(player, "commands.tuning-added", List.of(TextService.raw("stat", statLabel(stat))));
        return true;
    }

    public boolean removePoint(Player player, String rawStat) {
        if (!enabled()) {
            text.send(player, "commands.tuning-disabled");
            return false;
        }
        String stat = StatSnapshot.normalize(rawStat);
        if (!isTunable(stat)) {
            text.send(player, "commands.tuning-unknown-stat", List.of(TextService.raw("stat", rawStat)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.tuning(stat) <= 0) {
            text.send(player, "commands.tuning-none", List.of(TextService.raw("stat", statLabel(stat))));
            return false;
        }
        profile.addTuning(stat, -1);
        text.send(player, "commands.tuning-removed", List.of(TextService.raw("stat", statLabel(stat))));
        return true;
    }

    public void reset(Player player) {
        if (!enabled()) {
            text.send(player, "commands.tuning-disabled");
            return;
        }
        profiles.profile(player).clearTuning();
        text.send(player, "commands.tuning-reset");
    }

    public void sendSummary(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.tuning-summary", List.of(
                TextService.raw("used", Integer.toString(usedPoints(profile))),
                TextService.raw("total", Integer.toString(totalPoints(profile))),
                TextService.raw("available", Integer.toString(availablePoints(profile)))
        ));
    }

    public List<String> tunableStats() {
        ConfigurationSection section = configService.main().getConfigurationSection("stats.tuning.values");
        if (section == null) {
            return List.of();
        }
        return section.getKeys(false).stream().map(StatSnapshot::normalize).sorted().toList();
    }

    public boolean isTunable(String stat) {
        return tuningValue(stat) > 0.0D;
    }

    public double tuningValue(String stat) {
        return configService.main().getDouble("stats.tuning.values." + StatSnapshot.normalize(stat), 0.0D);
    }

    public String statLabel(String stat) {
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
