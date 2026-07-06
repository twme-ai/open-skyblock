package io.github.openskyblock.experiment;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
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
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ExperimentService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final SkillService skills;
    private final CustomItemService customItems;
    private final Map<String, ExperimentDefinition> experiments = new HashMap<>();
    private int defaultDailyLimit = 1;
    private int maxBonusClicks = 20;
    private int defaultClicksPerExtraRoll = 4;

    public ExperimentService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, SkillService skills, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.skills = skills;
        this.customItems = customItems;
    }

    public void reload() {
        defaultDailyLimit = Math.max(0, configService.experiments().getInt("settings.default-daily-limit", 1));
        maxBonusClicks = Math.max(0, configService.experiments().getInt("settings.max-bonus-clicks", 20));
        defaultClicksPerExtraRoll = Math.max(1, configService.experiments().getInt("settings.default-bonus-clicks-per-extra-roll", 4));
        loadExperiments();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.experiments", true);
    }

    public List<String> experimentIds() {
        return definitions().stream().map(ExperimentDefinition::id).toList();
    }

    public Optional<ExperimentDefinition> experiment(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(experiments.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<ExperimentDefinition> definitions() {
        return experiments.values().stream()
                .sorted(Comparator.comparingInt(ExperimentDefinition::requiredEnchantingLevel).thenComparing(ExperimentDefinition::id))
                .toList();
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.experiment-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        resetDaily(profile);
        int enchantingLevel = enchantingLevel(profile);
        text.send(player, "commands.experiment-status-header", List.of(
                TextService.raw("enchanting_level", Integer.toString(enchantingLevel)),
                TextService.raw("bonus_clicks", Integer.toString(profile.experimentBonusClicks())),
                TextService.raw("day", currentDay())
        ));
        if (experiments.isEmpty()) {
            text.send(player, "commands.experiment-empty");
            return;
        }
        for (ExperimentDefinition definition : definitions()) {
            text.send(player, "commands.experiment-status-line", statusPlaceholders(profile, definition, enchantingLevel));
        }
    }

    public void sendList(Player player) {
        if (!enabled()) {
            text.send(player, "commands.experiment-disabled");
            return;
        }
        if (experiments.isEmpty()) {
            text.send(player, "commands.experiment-empty");
            return;
        }
        text.send(player, "commands.experiment-list-header");
        for (ExperimentDefinition definition : definitions()) {
            text.send(player, "commands.experiment-list-line", definitionPlaceholders(definition));
        }
    }

    public boolean run(Player player, String experimentId) {
        if (!enabled()) {
            text.send(player, "commands.experiment-disabled");
            return false;
        }
        ExperimentDefinition definition = experiment(experimentId).orElse(null);
        if (definition == null) {
            text.send(player, "commands.experiment-unknown", List.of(TextService.raw("experiment", experimentId == null ? "" : experimentId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        resetDaily(profile);
        int enchantingLevel = enchantingLevel(profile);
        if (enchantingLevel < definition.requiredEnchantingLevel()) {
            text.send(player, "commands.experiment-requires-level", definitionPlaceholders(definition));
            return false;
        }
        int completed = profile.experimentCompletions(definition.id());
        if (definition.dailyLimit() > 0 && completed >= definition.dailyLimit()) {
            text.send(player, "commands.experiment-daily-limit", definitionPlaceholders(definition));
            return false;
        }
        if (!economy.spendPurse(player, definition.cost())) {
            text.send(player, "commands.experiment-no-money", definitionPlaceholders(definition));
            return false;
        }

        int consumedBonusClicks = definition.consumesBonusClicks() ? Math.min(profile.experimentBonusClicks(), maxBonusClicks) : 0;
        if (consumedBonusClicks > 0) {
            profile.experimentBonusClicks(profile.experimentBonusClicks() - consumedBonusClicks);
        }
        int extraRolls = definition.clicksPerExtraRoll() <= 0 ? 0 : consumedBonusClicks / definition.clicksPerExtraRoll();
        int rolls = Math.max(0, definition.rewardRolls()) + extraRolls;
        int clicks = Math.max(0, definition.baseClicks()) + consumedBonusClicks;
        List<String> rewardDisplays = new ArrayList<>();
        for (int index = 0; index < Math.max(1, rolls); index++) {
            selectReward(definition).flatMap(reward -> giveReward(player, reward)).ifPresent(rewardDisplays::add);
        }
        profile.addExperimentCompletion(definition.id(), 1);
        if (definition.bonusClicks() > 0) {
            profile.experimentBonusClicks(Math.min(maxBonusClicks, profile.experimentBonusClicks() + definition.bonusClicks()));
        }
        if (definition.enchantingXp() > 0.0D) {
            skills.addXp(player, SkillType.ENCHANTING, definition.enchantingXp());
        }
        profiles.save(player);
        text.send(player, "commands.experiment-completed", completedPlaceholders(definition, profile, clicks, consumedBonusClicks, rewardDisplays));
        return true;
    }

    private void loadExperiments() {
        experiments.clear();
        ConfigurationSection section = configService.experiments().getConfigurationSection("experiments");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection experiment = section.getConfigurationSection(id);
            if (experiment == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            List<ExperimentRewardDefinition> rewards = new ArrayList<>();
            for (Map<?, ?> rawReward : experiment.getMapList("rewards")) {
                reward(rawReward).ifPresent(rewards::add);
            }
            experiments.put(normalized, new ExperimentDefinition(
                    normalized,
                    experiment.getString("display-name", normalized),
                    experiment.getString("type", "SUPERPAIRS").toUpperCase(Locale.ROOT),
                    Math.max(0, experiment.getInt("required-enchanting-level", 0)),
                    Math.max(0, experiment.getInt("daily-limit", defaultDailyLimit)),
                    Math.max(0.0D, experiment.getDouble("cost", 0.0D)),
                    Math.max(0.0D, experiment.getDouble("enchanting-xp", 0.0D)),
                    Math.max(0, experiment.getInt("bonus-clicks", 0)),
                    experiment.getBoolean("consumes-bonus-clicks", false),
                    Math.max(0, experiment.getInt("base-clicks", 0)),
                    Math.max(1, experiment.getInt("bonus-clicks-per-extra-roll", defaultClicksPerExtraRoll)),
                    Math.max(0, experiment.getInt("reward-rolls", 1)),
                    List.copyOf(rewards)
            ));
        }
    }

    private Optional<ExperimentRewardDefinition> reward(Map<?, ?> section) {
        String type = string(section.get("type"), "VANILLA").toUpperCase(Locale.ROOT);
        int amount = Math.max(1, integer(section.get("amount"), 1));
        double weight = Math.max(0.0D, decimal(section.get("weight"), 1.0D));
        if (weight <= 0.0D) {
            return Optional.empty();
        }
        if (type.equals("COINS")) {
            double coins = Math.max(0.0D, decimal(section.get("coins"), decimal(section.get("amount"), 0.0D)));
            return coins <= 0.0D ? Optional.empty() : Optional.of(new ExperimentRewardDefinition("COINS", "", Material.GOLD_NUGGET, 1, coins, weight));
        }
        if (type.equals("CUSTOM_ITEM")) {
            String itemId = string(section.get("item"), "").toUpperCase(Locale.ROOT);
            return itemId.isBlank() ? Optional.empty() : Optional.of(new ExperimentRewardDefinition("CUSTOM_ITEM", itemId, Material.STONE, amount, 0.0D, weight));
        }
        Material material = Material.matchMaterial(string(section.get("material"), "EXPERIENCE_BOTTLE"));
        if (material == null || material.isAir()) {
            return Optional.empty();
        }
        return Optional.of(new ExperimentRewardDefinition("VANILLA", "", material, amount, 0.0D, weight));
    }

    private Optional<ExperimentRewardDefinition> selectReward(ExperimentDefinition definition) {
        double totalWeight = definition.rewards().stream().mapToDouble(ExperimentRewardDefinition::weight).sum();
        if (totalWeight <= 0.0D) {
            return Optional.empty();
        }
        double selected = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0D;
        for (ExperimentRewardDefinition reward : definition.rewards()) {
            cumulative += reward.weight();
            if (selected <= cumulative) {
                return Optional.of(reward);
            }
        }
        return definition.rewards().stream().filter(reward -> reward.weight() > 0.0D).findFirst();
    }

    private Optional<String> giveReward(Player player, ExperimentRewardDefinition reward) {
        if (reward.coinsReward()) {
            economy.addPurse(player, reward.coins());
            return Optional.of(rewardDisplay(reward));
        }
        ItemStack itemStack;
        if (reward.customItem()) {
            CustomItemDefinition definition = customItems.definition(reward.itemId()).orElse(null);
            if (definition == null) {
                return Optional.empty();
            }
            itemStack = customItems.createItem(definition);
        } else {
            itemStack = new ItemStack(reward.material());
        }
        itemStack.setAmount(reward.amount());
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        return Optional.of(rewardDisplay(reward));
    }

    private List<TextService.TextPlaceholder> statusPlaceholders(SkyBlockProfile profile, ExperimentDefinition definition, int enchantingLevel) {
        int completed = profile.experimentCompletions(definition.id());
        String status = status(definition, enchantingLevel, completed);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(definitionPlaceholders(definition));
        placeholders.add(TextService.raw("completed", Integer.toString(completed)));
        placeholders.add(TextService.raw("remaining", remainingLabel(definition, completed)));
        placeholders.add(TextService.parsed("status", status));
        return placeholders;
    }

    private List<TextService.TextPlaceholder> definitionPlaceholders(ExperimentDefinition definition) {
        return List.of(
                TextService.raw("id", definition.id()),
                TextService.parsed("experiment", definition.displayName()),
                TextService.raw("type", readableType(definition.type())),
                TextService.raw("required_level", Integer.toString(definition.requiredEnchantingLevel())),
                TextService.raw("daily_limit", limitLabel(definition.dailyLimit())),
                TextService.raw("cost", text.formatNumber(definition.cost())),
                TextService.raw("enchanting_xp", text.formatNumber(definition.enchantingXp())),
                TextService.raw("bonus_clicks", Integer.toString(definition.bonusClicks())),
                TextService.raw("base_clicks", Integer.toString(definition.baseClicks())),
                TextService.raw("reward_rolls", Integer.toString(Math.max(1, definition.rewardRolls()))),
                TextService.raw("extra_roll_clicks", Integer.toString(definition.clicksPerExtraRoll()))
        );
    }

    private List<TextService.TextPlaceholder> completedPlaceholders(ExperimentDefinition definition, SkyBlockProfile profile, int clicks, int spentBonusClicks, List<String> rewardDisplays) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(definitionPlaceholders(definition));
        placeholders.add(TextService.raw("clicks", Integer.toString(clicks)));
        placeholders.add(TextService.raw("spent_bonus_clicks", Integer.toString(spentBonusClicks)));
        placeholders.add(TextService.raw("remaining_bonus_clicks", Integer.toString(profile.experimentBonusClicks())));
        placeholders.add(TextService.parsed("rewards", rewardDisplays.isEmpty() ? "<gray>none</gray>" : String.join("<gray>, </gray>", rewardDisplays)));
        return placeholders;
    }

    private String status(ExperimentDefinition definition, int enchantingLevel, int completed) {
        if (enchantingLevel < definition.requiredEnchantingLevel()) {
            return "<red>Locked</red>";
        }
        if (definition.dailyLimit() > 0 && completed >= definition.dailyLimit()) {
            return "<yellow>Used</yellow>";
        }
        return "<green>Ready</green>";
    }

    private String limitLabel(int limit) {
        return limit <= 0 ? "Unlimited" : Integer.toString(limit);
    }

    private String remainingLabel(ExperimentDefinition definition, int completed) {
        if (definition.dailyLimit() <= 0) {
            return "Unlimited";
        }
        return Integer.toString(Math.max(0, definition.dailyLimit() - completed));
    }

    private int enchantingLevel(SkyBlockProfile profile) {
        return skills.level(SkillType.ENCHANTING, profile.skillXp(SkillType.ENCHANTING));
    }

    private void resetDaily(SkyBlockProfile profile) {
        String today = currentDay();
        if (!today.equals(profile.experimentDay())) {
            profile.experimentDay(today);
            profile.clearExperimentCompletions();
        }
    }

    private String currentDay() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    private String rewardDisplay(ExperimentRewardDefinition reward) {
        if (reward.coinsReward()) {
            return "<gold>" + text.formatNumber(reward.coins()) + " coins</gold>";
        }
        String name = reward.customItem()
                ? customItems.definition(reward.itemId()).map(CustomItemDefinition::displayName).orElse(reward.itemId())
                : readableMaterial(reward.material());
        return "<yellow>" + reward.amount() + "x</yellow> " + name;
    }

    private String readableType(String type) {
        String normalized = type.toLowerCase(Locale.ROOT).replace('_', ' ');
        return normalized.isBlank() ? type : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String readableMaterial(Material material) {
        String normalized = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String string(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(string(value, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double decimal(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(string(value, Double.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
