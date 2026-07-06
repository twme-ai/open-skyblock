package io.github.openskyblock.commission;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.profile.ActiveCommission;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.SkillService;
import io.github.openskyblock.service.SkillType;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public final class CommissionService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final SkillService skills;
    private final Map<String, CommissionDefinition> definitions = new HashMap<>();
    private int defaultSlots = 2;
    private int maxSlots = 4;
    private int dailyBonusCommissions = 4;
    private double dailyHotmXpBonus = 900.0D;

    public CommissionService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, SkillService skills) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.skills = skills;
    }

    public void reload() {
        this.defaultSlots = Math.max(1, configService.commissions().getInt("settings.default-slots", 2));
        this.maxSlots = Math.max(defaultSlots, configService.commissions().getInt("settings.max-slots", 4));
        this.dailyBonusCommissions = Math.max(0, configService.commissions().getInt("settings.daily-bonus-commissions", 4));
        this.dailyHotmXpBonus = Math.max(0.0D, configService.commissions().getDouble("settings.daily-hotm-xp-bonus", 900.0D));
        loadDefinitions();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.commissions", true);
    }

    public List<String> definitionIds() {
        return definitions().stream().map(CommissionDefinition::id).toList();
    }

    public Optional<CommissionDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<CommissionDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(CommissionDefinition::id))
                .toList();
    }

    public void recordBlockBreak(Player player, Material material, long amount) {
        if (!enabled() || material == null || amount <= 0L) {
            return;
        }
        recordProgress(player, "MINE_BLOCK", material.name(), amount);
    }

    public void recordKill(Player player, EntityType entityType, long amount) {
        if (!enabled() || entityType == null || amount <= 0L) {
            return;
        }
        recordProgress(player, "KILL_ENTITY", entityType.name(), amount);
    }

    public void sendSummary(Player player) {
        if (!enabled()) {
            text.send(player, "commands.commissions-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        resetDailyIfNeeded(profile);
        ensureCommissions(profile);
        text.send(player, "commands.commissions-header", hotmPlaceholders(profile));
        for (int slot = 1; slot <= slotCount(profile); slot++) {
            ActiveCommission active = profile.activeCommissions().get(slot);
            if (active == null) {
                continue;
            }
            CommissionDefinition definition = definition(active.id()).orElse(null);
            if (definition != null) {
                text.send(player, "commands.commissions-line", commissionPlaceholders(active, definition));
            }
        }
    }

    public void sendHotm(Player player) {
        if (!enabled()) {
            text.send(player, "commands.commissions-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        resetDailyIfNeeded(profile);
        text.send(player, "commands.hotm-summary", hotmPlaceholders(profile));
        for (String powderId : powderIds(profile)) {
            text.send(player, "commands.hotm-powder-line", List.of(
                    TextService.raw("powder", powderId),
                    TextService.raw("amount", text.formatNumber(profile.hotmPowder(powderId)))
            ));
        }
    }

    public boolean claim(Player player, int slot) {
        if (!enabled()) {
            text.send(player, "commands.commissions-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        resetDailyIfNeeded(profile);
        ensureCommissions(profile);
        if (slot < 1 || slot > slotCount(profile)) {
            text.send(player, "commands.commissions-invalid-slot", List.of(TextService.raw("slots", Integer.toString(slotCount(profile)))));
            return false;
        }
        ActiveCommission active = profile.activeCommissions().get(slot);
        CommissionDefinition definition = active == null ? null : definition(active.id()).orElse(null);
        if (active == null || definition == null) {
            text.send(player, "commands.commissions-empty-slot", List.of(TextService.raw("slot", Integer.toString(slot))));
            return false;
        }
        if (active.progress() < definition.amount()) {
            text.send(player, "commands.commissions-not-complete", commissionPlaceholders(active, definition));
            return false;
        }
        CommissionReward reward = definition.reward();
        double bonusHotmXp = profile.dailyCommissions() < dailyBonusCommissions ? dailyHotmXpBonus : 0.0D;
        double hotmXp = reward.hotmXp() + bonusHotmXp;
        if (reward.miningXp() > 0.0D) {
            skills.addXp(player, SkillType.MINING, reward.miningXp());
        }
        if (reward.coins() > 0.0D) {
            economy.addPurse(player, reward.coins());
        }
        if (hotmXp > 0.0D) {
            profile.addHotmXp(hotmXp);
        }
        if (!reward.powderId().isBlank() && reward.powder() > 0.0D) {
            profile.addHotmPowder(reward.powderId(), reward.powder());
        }
        profile.addTotalCommissions(1L);
        profile.addDailyCommission();
        profile.activeCommissions().put(slot, new ActiveCommission(slot, selectCommission(profile, slot).id(), 0L));
        profiles.saveAll();
        text.send(player, "commands.commissions-claimed", List.of(
                TextService.parsed("commission", definition.displayName()),
                TextService.raw("hotm_xp", text.formatNumber(hotmXp)),
                TextService.raw("daily_bonus", text.formatNumber(bonusHotmXp)),
                TextService.raw("powder", text.formatNumber(reward.powder())),
                TextService.raw("powder_type", reward.powderId()),
                TextService.raw("coins", text.formatNumber(reward.coins()))
        ));
        return true;
    }

    private void recordProgress(Player player, String type, String target, long amount) {
        SkyBlockProfile profile = profiles.profile(player);
        ensureCommissions(profile);
        for (ActiveCommission active : profile.activeCommissions().values()) {
            CommissionDefinition definition = definition(active.id()).orElse(null);
            if (definition == null || !definition.type().equalsIgnoreCase(type) || !definition.target().equalsIgnoreCase(target)) {
                continue;
            }
            long before = active.progress();
            active.addProgress(amount);
            if (before < definition.amount() && active.progress() >= definition.amount()) {
                text.send(player, "commands.commissions-ready", commissionPlaceholders(active, definition));
            }
        }
    }

    private void ensureCommissions(SkyBlockProfile profile) {
        if (definitions.isEmpty()) {
            return;
        }
        int slots = slotCount(profile);
        profile.activeCommissions().entrySet().removeIf(entry -> entry.getKey() < 1 || entry.getKey() > slots || definition(entry.getValue().id()).isEmpty());
        for (int slot = 1; slot <= slots; slot++) {
            if (!profile.activeCommissions().containsKey(slot)) {
                profile.activeCommissions().put(slot, new ActiveCommission(slot, selectCommission(profile, slot).id(), 0L));
            }
        }
    }

    private CommissionDefinition selectCommission(SkyBlockProfile profile, int slot) {
        List<String> activeIds = profile.activeCommissions().values().stream()
                .map(ActiveCommission::id)
                .toList();
        List<CommissionDefinition> pool = definitions().stream()
                .filter(definition -> !activeIds.contains(definition.id()))
                .toList();
        if (pool.isEmpty()) {
            pool = definitions();
        }
        double totalWeight = pool.stream().mapToDouble(CommissionDefinition::weight).sum();
        Random random = new Random(profile.uniqueId().getMostSignificantBits() ^ profile.totalCommissions() ^ (slot * 97L));
        if (totalWeight <= 0.0D) {
            return pool.get(random.nextInt(pool.size()));
        }
        double target = random.nextDouble() * totalWeight;
        double cursor = 0.0D;
        for (CommissionDefinition definition : pool) {
            cursor += definition.weight();
            if (target <= cursor) {
                return definition;
            }
        }
        return pool.getLast();
    }

    private void resetDailyIfNeeded(SkyBlockProfile profile) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        if (!today.equals(profile.commissionDay())) {
            profile.commissionDay(today);
            profile.dailyCommissions(0);
        }
    }

    private int slotCount(SkyBlockProfile profile) {
        int slots = defaultSlots;
        ConfigurationSection section = configService.commissions().getConfigurationSection("slot-milestones");
        if (section != null) {
            for (String completionsKey : section.getKeys(false)) {
                try {
                    long completions = Long.parseLong(completionsKey);
                    if (profile.totalCommissions() >= completions) {
                        slots = Math.max(slots, section.getInt(completionsKey, slots));
                    }
                } catch (NumberFormatException ignored) {
                    // Invalid milestone keys are skipped.
                }
            }
        }
        return Math.max(1, Math.min(maxSlots, slots));
    }

    private int hotmLevel(SkyBlockProfile profile) {
        int level = 1;
        ConfigurationSection section = configService.commissions().getConfigurationSection("hotm-levels");
        if (section == null) {
            return level;
        }
        for (String levelKey : section.getKeys(false)) {
            try {
                int candidate = Integer.parseInt(levelKey);
                double required = section.getDouble(levelKey + ".required-xp", 0.0D);
                if (profile.hotmXp() >= required) {
                    level = Math.max(level, candidate);
                }
            } catch (NumberFormatException ignored) {
                // Invalid HotM level keys are skipped.
            }
        }
        return level;
    }

    private double nextHotmRequirement(SkyBlockProfile profile) {
        int current = hotmLevel(profile);
        ConfigurationSection section = configService.commissions().getConfigurationSection("hotm-levels");
        if (section == null) {
            return profile.hotmXp();
        }
        double next = 0.0D;
        for (String levelKey : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(levelKey);
                double required = section.getDouble(levelKey + ".required-xp", 0.0D);
                if (level > current && (next <= 0.0D || required < next)) {
                    next = required;
                }
            } catch (NumberFormatException ignored) {
                // Invalid HotM level keys are skipped.
            }
        }
        return next <= 0.0D ? profile.hotmXp() : next;
    }

    private void loadDefinitions() {
        definitions.clear();
        ConfigurationSection section = configService.commissions().getConfigurationSection("commissions");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection commission = section.getConfigurationSection(id);
            if (commission == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            CommissionReward reward = new CommissionReward(
                    Math.max(0.0D, commission.getDouble("rewards.mining-xp", 0.0D)),
                    Math.max(0.0D, commission.getDouble("rewards.hotm-xp", 100.0D)),
                    commission.getString("rewards.powder.type", "MITHRIL").toUpperCase(Locale.ROOT),
                    Math.max(0.0D, commission.getDouble("rewards.powder.amount", 50.0D)),
                    Math.max(0.0D, commission.getDouble("rewards.coins", 0.0D))
            );
            definitions.put(normalized, new CommissionDefinition(
                    normalized,
                    commission.getString("display-name", normalized),
                    commission.getString("description", ""),
                    commission.getString("type", "MINE_BLOCK").toUpperCase(Locale.ROOT),
                    commission.getString("target", "").toUpperCase(Locale.ROOT),
                    Math.max(1L, commission.getLong("amount", 100L)),
                    Math.max(0.0D, commission.getDouble("weight", 1.0D)),
                    reward
            ));
        }
    }

    private List<String> powderIds(SkyBlockProfile profile) {
        List<String> ids = new ArrayList<>();
        ids.add("MITHRIL");
        ids.add("GEMSTONE");
        for (String powderId : profile.hotmPowder().keySet()) {
            if (!ids.contains(powderId)) {
                ids.add(powderId);
            }
        }
        return ids;
    }

    private List<TextService.TextPlaceholder> hotmPlaceholders(SkyBlockProfile profile) {
        return List.of(
                TextService.raw("level", Integer.toString(hotmLevel(profile))),
                TextService.raw("hotm_xp", text.formatNumber(profile.hotmXp())),
                TextService.raw("next_hotm_xp", text.formatNumber(nextHotmRequirement(profile))),
                TextService.raw("slots", Integer.toString(slotCount(profile))),
                TextService.raw("total", text.formatNumber(profile.totalCommissions())),
                TextService.raw("daily", Integer.toString(profile.dailyCommissions())),
                TextService.raw("daily_limit", Integer.toString(dailyBonusCommissions))
        );
    }

    private List<TextService.TextPlaceholder> commissionPlaceholders(ActiveCommission active, CommissionDefinition definition) {
        return List.of(
                TextService.raw("slot", Integer.toString(active.slot())),
                TextService.raw("id", definition.id()),
                TextService.parsed("commission", definition.displayName()),
                TextService.raw("description", definition.description()),
                TextService.raw("progress", text.formatNumber(Math.min(active.progress(), definition.amount()))),
                TextService.raw("amount", text.formatNumber(definition.amount())),
                TextService.raw("target", definition.target()),
                TextService.raw("type", definition.type())
        );
    }
}
