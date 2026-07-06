package io.github.openskyblock.faction;

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

public final class FactionService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final SkillService skills;
    private final CustomItemService customItems;
    private final Map<String, FactionDefinition> factions = new HashMap<>();
    private final Map<String, FactionRankDefinition> ranks = new HashMap<>();
    private final Map<String, FactionQuestDefinition> quests = new HashMap<>();
    private final Map<String, FactionMinibossDefinition> minibosses = new HashMap<>();
    private long reputationCap = 12_000L;
    private int dailyQuestLimit = 5;
    private int dailyMinibossLimit = 5;
    private double skyBlockXpPerReputation = 0.002D;

    public FactionService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, SkillService skills, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.skills = skills;
        this.customItems = customItems;
    }

    public void reload() {
        reputationCap = Math.max(1L, configService.factions().getLong("settings.reputation-cap", 12_000L));
        dailyQuestLimit = Math.max(0, configService.factions().getInt("settings.daily-quest-limit", 5));
        dailyMinibossLimit = Math.max(0, configService.factions().getInt("settings.daily-miniboss-limit", 5));
        skyBlockXpPerReputation = Math.max(0.0D, configService.factions().getDouble("settings.skyblock-xp-per-reputation", 0.002D));
        loadFactions();
        loadRanks();
        loadQuests();
        loadMinibosses();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.crimson-factions", true);
    }

    public List<String> factionIds() {
        return factionDefinitions().stream().map(FactionDefinition::id).toList();
    }

    public List<String> questIds() {
        return questDefinitions().stream().map(FactionQuestDefinition::id).toList();
    }

    public List<String> minibossIds() {
        return minibossDefinitions().stream().map(FactionMinibossDefinition::id).toList();
    }

    public Optional<FactionDefinition> faction(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(factions.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<FactionDefinition> factionDefinitions() {
        return factions.values().stream()
                .sorted(Comparator.comparing(FactionDefinition::id))
                .toList();
    }

    public List<FactionRankDefinition> rankDefinitions() {
        return ranks.values().stream()
                .sorted(Comparator.comparingLong(FactionRankDefinition::requiredReputation).thenComparing(FactionRankDefinition::id))
                .toList();
    }

    public List<FactionQuestDefinition> questDefinitions() {
        return quests.values().stream()
                .sorted(Comparator.comparing(FactionQuestDefinition::tier).thenComparing(FactionQuestDefinition::id))
                .toList();
    }

    public List<FactionMinibossDefinition> minibossDefinitions() {
        return minibosses.values().stream()
                .sorted(Comparator.comparing(FactionMinibossDefinition::id))
                .toList();
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.faction-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        resetDaily(profile);
        FactionDefinition selected = selectedFaction(profile).orElse(null);
        FactionRankDefinition rank = selected == null ? fallbackRank() : rankFor(profile, selected.id());
        text.send(player, "commands.faction-status", List.of(
                TextService.parsed("faction", selected == null ? "<gray>None</gray>" : selected.displayName()),
                TextService.raw("faction_id", selected == null ? "NONE" : selected.id()),
                TextService.parsed("rank", rank.displayName()),
                TextService.raw("rank_id", rank.id()),
                TextService.raw("reputation", selected == null ? "0" : text.formatNumber(profile.factionReputation(selected.id()))),
                TextService.raw("daily_quests", Integer.toString(profile.dailyFactionQuests())),
                TextService.raw("daily_quest_limit", limitLabel(dailyQuestLimit)),
                TextService.raw("daily_minibosses", Integer.toString(profile.dailyFactionMinibosses())),
                TextService.raw("daily_miniboss_limit", limitLabel(dailyMinibossLimit)),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile)))
        ));
        for (FactionDefinition faction : factionDefinitions()) {
            text.send(player, "commands.faction-status-line", factionPlaceholders(profile, faction));
        }
    }

    public void sendFactions(Player player) {
        if (!enabled()) {
            text.send(player, "commands.faction-disabled");
            return;
        }
        if (factions.isEmpty()) {
            text.send(player, "commands.faction-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.faction-list-header");
        for (FactionDefinition faction : factionDefinitions()) {
            text.send(player, "commands.faction-list-line", factionPlaceholders(profile, faction));
        }
    }

    public void sendRanks(Player player) {
        if (!enabled()) {
            text.send(player, "commands.faction-disabled");
            return;
        }
        if (ranks.isEmpty()) {
            text.send(player, "commands.faction-empty");
            return;
        }
        text.send(player, "commands.faction-rank-header");
        for (FactionRankDefinition rank : rankDefinitions()) {
            text.send(player, "commands.faction-rank-line", rankPlaceholders(rank));
        }
    }

    public void sendQuests(Player player) {
        if (!enabled()) {
            text.send(player, "commands.faction-disabled");
            return;
        }
        if (quests.isEmpty()) {
            text.send(player, "commands.faction-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        resetDaily(profile);
        text.send(player, "commands.faction-quest-header", List.of(
                TextService.raw("daily_quests", Integer.toString(profile.dailyFactionQuests())),
                TextService.raw("daily_quest_limit", limitLabel(dailyQuestLimit))
        ));
        for (FactionQuestDefinition quest : questDefinitions()) {
            text.send(player, "commands.faction-quest-line", questPlaceholders(profile, quest));
        }
    }

    public void sendMinibosses(Player player) {
        if (!enabled()) {
            text.send(player, "commands.faction-disabled");
            return;
        }
        if (minibosses.isEmpty()) {
            text.send(player, "commands.faction-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        resetDaily(profile);
        text.send(player, "commands.faction-miniboss-header", List.of(
                TextService.raw("daily_minibosses", Integer.toString(profile.dailyFactionMinibosses())),
                TextService.raw("daily_miniboss_limit", limitLabel(dailyMinibossLimit))
        ));
        for (FactionMinibossDefinition miniboss : minibossDefinitions()) {
            text.send(player, "commands.faction-miniboss-line", minibossPlaceholders(profile, miniboss));
        }
    }

    public boolean chooseFaction(Player player, String factionId) {
        if (!enabled()) {
            text.send(player, "commands.faction-disabled");
            return false;
        }
        FactionDefinition faction = faction(factionId).orElse(null);
        if (faction == null) {
            text.send(player, "commands.faction-unknown", List.of(TextService.raw("faction", factionId == null ? "" : factionId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        profile.selectedFaction(faction.id());
        profiles.save(player);
        text.send(player, "commands.faction-selected", factionPlaceholders(profile, faction));
        return true;
    }

    public boolean completeQuest(Player player, String questId) {
        if (!enabled()) {
            text.send(player, "commands.faction-disabled");
            return false;
        }
        FactionQuestDefinition quest = quest(questId).orElse(null);
        if (quest == null) {
            text.send(player, "commands.faction-quest-unknown", List.of(TextService.raw("quest", questId == null ? "" : questId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        resetDaily(profile);
        FactionDefinition faction = selectedFaction(profile).orElse(null);
        if (faction == null) {
            text.send(player, "commands.faction-no-selection");
            return false;
        }
        if (dailyQuestLimit > 0 && profile.dailyFactionQuests() >= dailyQuestLimit) {
            text.send(player, "commands.faction-quest-limit", List.of(
                    TextService.raw("daily_quests", Integer.toString(profile.dailyFactionQuests())),
                    TextService.raw("daily_quest_limit", limitLabel(dailyQuestLimit))
            ));
            return false;
        }
        if (profile.factionReputation(faction.id()) < quest.requiredReputation()) {
            text.send(player, "commands.faction-quest-locked", questPlaceholders(profile, quest));
            return false;
        }
        long reputation = addReputation(profile, faction.id(), quest.reputation());
        if (quest.combatXp() > 0.0D) {
            skills.addXp(player, SkillType.COMBAT, quest.combatXp());
        }
        if (quest.coins() > 0.0D) {
            economy.addPurse(player, quest.coins());
        }
        List<String> rewards = new ArrayList<>();
        for (FactionRewardDefinition reward : rollRewards(quest.rewards())) {
            giveReward(player, reward).ifPresent(rewards::add);
        }
        profile.addFactionQuestCompletion(quest.id(), 1);
        profile.addDailyFactionQuest();
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(questPlaceholders(profile, quest));
        placeholders.addAll(factionPlaceholders(profile, faction));
        placeholders.add(TextService.raw("reputation_gained", text.formatNumber(reputation)));
        placeholders.add(TextService.parsed("rewards", rewards.isEmpty() ? "<gray>none</gray>" : String.join("<gray>, </gray>", rewards)));
        text.send(player, "commands.faction-quest-completed", placeholders);
        return true;
    }

    public boolean defeatMiniboss(Player player, String minibossId) {
        if (!enabled()) {
            text.send(player, "commands.faction-disabled");
            return false;
        }
        FactionMinibossDefinition miniboss = miniboss(minibossId).orElse(null);
        if (miniboss == null) {
            text.send(player, "commands.faction-miniboss-unknown", List.of(TextService.raw("miniboss", minibossId == null ? "" : minibossId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        resetDaily(profile);
        FactionDefinition faction = selectedFaction(profile).orElse(null);
        if (faction == null) {
            text.send(player, "commands.faction-no-selection");
            return false;
        }
        if (dailyMinibossLimit > 0 && profile.dailyFactionMinibosses() >= dailyMinibossLimit) {
            text.send(player, "commands.faction-miniboss-limit", List.of(
                    TextService.raw("daily_minibosses", Integer.toString(profile.dailyFactionMinibosses())),
                    TextService.raw("daily_miniboss_limit", limitLabel(dailyMinibossLimit))
            ));
            return false;
        }
        if (profile.factionReputation(faction.id()) < miniboss.requiredReputation()) {
            text.send(player, "commands.faction-miniboss-locked", minibossPlaceholders(profile, miniboss));
            return false;
        }
        long reputation = addReputation(profile, faction.id(), miniboss.reputation());
        if (miniboss.combatXp() > 0.0D) {
            skills.addXp(player, SkillType.COMBAT, miniboss.combatXp());
        }
        if (miniboss.coins() > 0.0D) {
            economy.addPurse(player, miniboss.coins());
        }
        profile.addFactionMinibossKill(miniboss.id(), 1);
        profile.addDailyFactionMiniboss();
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(minibossPlaceholders(profile, miniboss));
        placeholders.addAll(factionPlaceholders(profile, faction));
        placeholders.add(TextService.raw("reputation_gained", text.formatNumber(reputation)));
        text.send(player, "commands.faction-miniboss-defeated", placeholders);
        return true;
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        double xp = 0.0D;
        for (FactionDefinition faction : factionDefinitions()) {
            xp += Math.min(reputationCap, profile.factionReputation(faction.id())) * skyBlockXpPerReputation;
            xp += rankFor(profile, faction.id()).skyBlockXp();
        }
        return Math.max(0.0D, xp);
    }

    private void loadFactions() {
        factions.clear();
        ConfigurationSection section = configService.factions().getConfigurationSection("factions");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection factionSection = section.getConfigurationSection(id);
            if (factionSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            factions.put(normalized, new FactionDefinition(
                    normalized,
                    factionSection.getString("display-name", normalized),
                    factionSection.getString("description", "")
            ));
        }
    }

    private void loadRanks() {
        ranks.clear();
        ConfigurationSection section = configService.factions().getConfigurationSection("ranks");
        if (section == null) {
            ranks.put("OUTSIDER", new FactionRankDefinition("OUTSIDER", "<gray>Outsider</gray>", 0L, 0.0D));
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection rankSection = section.getConfigurationSection(id);
            if (rankSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            ranks.put(normalized, new FactionRankDefinition(
                    normalized,
                    rankSection.getString("display-name", normalized),
                    Math.max(0L, rankSection.getLong("required-reputation", 0L)),
                    Math.max(0.0D, rankSection.getDouble("skyblock-xp", 0.0D))
            ));
        }
        ranks.putIfAbsent("OUTSIDER", new FactionRankDefinition("OUTSIDER", "<gray>Outsider</gray>", 0L, 0.0D));
    }

    private void loadQuests() {
        quests.clear();
        ConfigurationSection section = configService.factions().getConfigurationSection("quests");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection questSection = section.getConfigurationSection(id);
            if (questSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            quests.put(normalized, new FactionQuestDefinition(
                    normalized,
                    questSection.getString("display-name", normalized),
                    questSection.getString("tier", "D").toUpperCase(Locale.ROOT),
                    Math.max(0L, questSection.getLong("reputation", 0L)),
                    Math.max(0.0D, questSection.getDouble("combat-xp", 0.0D)),
                    Math.max(0.0D, questSection.getDouble("coins", 0.0D)),
                    Math.max(0L, questSection.getLong("required-reputation", 0L)),
                    readRewards(questSection.getMapList("rewards"))
            ));
        }
    }

    private void loadMinibosses() {
        minibosses.clear();
        ConfigurationSection section = configService.factions().getConfigurationSection("minibosses");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection minibossSection = section.getConfigurationSection(id);
            if (minibossSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            minibosses.put(normalized, new FactionMinibossDefinition(
                    normalized,
                    minibossSection.getString("display-name", normalized),
                    Math.max(0L, minibossSection.getLong("reputation", 0L)),
                    Math.max(0.0D, minibossSection.getDouble("combat-xp", 0.0D)),
                    Math.max(0.0D, minibossSection.getDouble("coins", 0.0D)),
                    Math.max(0L, minibossSection.getLong("required-reputation", 0L))
            ));
        }
    }

    private Optional<FactionQuestDefinition> quest(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(quests.get(id.toUpperCase(Locale.ROOT)));
    }

    private Optional<FactionMinibossDefinition> miniboss(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(minibosses.get(id.toUpperCase(Locale.ROOT)));
    }

    private Optional<FactionDefinition> selectedFaction(SkyBlockProfile profile) {
        return faction(profile.selectedFaction());
    }

    private FactionRankDefinition rankFor(SkyBlockProfile profile, String factionId) {
        long reputation = profile.factionReputation(factionId);
        FactionRankDefinition current = fallbackRank();
        for (FactionRankDefinition rank : rankDefinitions()) {
            if (reputation >= rank.requiredReputation()) {
                current = rank;
            }
        }
        return current;
    }

    private FactionRankDefinition fallbackRank() {
        return ranks.getOrDefault("OUTSIDER", new FactionRankDefinition("OUTSIDER", "<gray>Outsider</gray>", 0L, 0.0D));
    }

    private long addReputation(SkyBlockProfile profile, String factionId, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        long before = profile.factionReputation(factionId);
        long after = Math.min(reputationCap, before + amount);
        profile.setFactionReputation(factionId, after);
        return Math.max(0L, after - before);
    }

    private void resetDaily(SkyBlockProfile profile) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        if (!today.equals(profile.factionDay())) {
            profile.factionDay(today);
            profile.dailyFactionQuests(0);
            profile.dailyFactionMinibosses(0);
        }
    }

    private List<TextService.TextPlaceholder> factionPlaceholders(SkyBlockProfile profile, FactionDefinition faction) {
        FactionRankDefinition rank = rankFor(profile, faction.id());
        boolean selected = faction.id().equals(profile.selectedFaction());
        return List.of(
                TextService.raw("id", faction.id()),
                TextService.raw("faction_id", faction.id()),
                TextService.parsed("faction", faction.displayName()),
                TextService.parsed("description", faction.description()),
                TextService.raw("reputation", text.formatNumber(profile.factionReputation(faction.id()))),
                TextService.raw("reputation_cap", text.formatNumber(reputationCap)),
                TextService.raw("rank_id", rank.id()),
                TextService.parsed("rank", rank.displayName()),
                TextService.parsed("selected", selected ? "<green>Selected</green>" : "<gray>Available</gray>")
        );
    }

    private List<TextService.TextPlaceholder> rankPlaceholders(FactionRankDefinition rank) {
        return List.of(
                TextService.raw("id", rank.id()),
                TextService.raw("rank_id", rank.id()),
                TextService.parsed("rank", rank.displayName()),
                TextService.raw("required_reputation", text.formatNumber(rank.requiredReputation())),
                TextService.raw("skyblock_xp", text.formatNumber(rank.skyBlockXp()))
        );
    }

    private List<TextService.TextPlaceholder> questPlaceholders(SkyBlockProfile profile, FactionQuestDefinition quest) {
        FactionDefinition selected = selectedFaction(profile).orElse(null);
        long reputation = selected == null ? 0L : profile.factionReputation(selected.id());
        boolean unlocked = selected != null && reputation >= quest.requiredReputation();
        return List.of(
                TextService.raw("id", quest.id()),
                TextService.raw("quest_id", quest.id()),
                TextService.parsed("quest", quest.displayName()),
                TextService.raw("tier", quest.tier()),
                TextService.raw("reputation_reward", text.formatNumber(quest.reputation())),
                TextService.raw("combat_xp", text.formatNumber(quest.combatXp())),
                TextService.raw("coins", text.formatNumber(quest.coins())),
                TextService.raw("required_reputation", text.formatNumber(quest.requiredReputation())),
                TextService.raw("current_reputation", text.formatNumber(reputation)),
                TextService.raw("completions", Integer.toString(profile.factionQuestCompletions(quest.id()))),
                TextService.parsed("status", unlocked ? "<green>Unlocked</green>" : "<red>Locked</red>")
        );
    }

    private List<TextService.TextPlaceholder> minibossPlaceholders(SkyBlockProfile profile, FactionMinibossDefinition miniboss) {
        FactionDefinition selected = selectedFaction(profile).orElse(null);
        long reputation = selected == null ? 0L : profile.factionReputation(selected.id());
        boolean unlocked = selected != null && reputation >= miniboss.requiredReputation();
        return List.of(
                TextService.raw("id", miniboss.id()),
                TextService.raw("miniboss_id", miniboss.id()),
                TextService.parsed("miniboss", miniboss.displayName()),
                TextService.raw("reputation_reward", text.formatNumber(miniboss.reputation())),
                TextService.raw("combat_xp", text.formatNumber(miniboss.combatXp())),
                TextService.raw("coins", text.formatNumber(miniboss.coins())),
                TextService.raw("required_reputation", text.formatNumber(miniboss.requiredReputation())),
                TextService.raw("current_reputation", text.formatNumber(reputation)),
                TextService.raw("kills", Integer.toString(profile.factionMinibossKills(miniboss.id()))),
                TextService.parsed("status", unlocked ? "<green>Unlocked</green>" : "<red>Locked</red>")
        );
    }

    private List<FactionRewardDefinition> readRewards(List<Map<?, ?>> rewardMaps) {
        List<FactionRewardDefinition> rewards = new ArrayList<>();
        for (Map<?, ?> rawReward : rewardMaps) {
            reward(rawReward).ifPresent(rewards::add);
        }
        return List.copyOf(rewards);
    }

    private Optional<FactionRewardDefinition> reward(Map<?, ?> section) {
        String type = string(section.get("type"), "VANILLA").toUpperCase(Locale.ROOT);
        int amount = Math.max(1, integer(section.get("amount"), 1));
        double weight = Math.max(0.0D, decimal(section.get("weight"), 1.0D));
        if (weight <= 0.0D) {
            return Optional.empty();
        }
        if (type.equals("COINS")) {
            double coins = Math.max(0.0D, decimal(section.get("coins"), decimal(section.get("amount"), 0.0D)));
            return coins <= 0.0D ? Optional.empty() : Optional.of(new FactionRewardDefinition("COINS", "", Material.GOLD_NUGGET, 1, coins, weight));
        }
        if (type.equals("CUSTOM_ITEM")) {
            String itemId = string(section.get("item"), "").toUpperCase(Locale.ROOT);
            return itemId.isBlank() ? Optional.empty() : Optional.of(new FactionRewardDefinition("CUSTOM_ITEM", itemId, Material.STONE, amount, 0.0D, weight));
        }
        Material material = Material.matchMaterial(string(section.get("material"), "NETHERRACK"));
        if (material == null || material.isAir()) {
            return Optional.empty();
        }
        return Optional.of(new FactionRewardDefinition("VANILLA", "", material, amount, 0.0D, weight));
    }

    private List<FactionRewardDefinition> rollRewards(List<FactionRewardDefinition> rewards) {
        if (rewards.isEmpty()) {
            return List.of();
        }
        double totalWeight = rewards.stream().mapToDouble(FactionRewardDefinition::weight).sum();
        if (totalWeight <= 0.0D) {
            return List.of();
        }
        double selected = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0D;
        for (FactionRewardDefinition reward : rewards) {
            cumulative += reward.weight();
            if (selected <= cumulative) {
                return List.of(reward);
            }
        }
        return List.of(rewards.getFirst());
    }

    private Optional<String> giveReward(Player player, FactionRewardDefinition reward) {
        if (reward.coinsReward()) {
            economy.addPurse(player, reward.coins());
            return Optional.of("<gold>" + text.formatNumber(reward.coins()) + " coins</gold>");
        }
        if (reward.customItem()) {
            CustomItemDefinition definition = customItems.definition(reward.itemId()).orElse(null);
            if (definition == null) {
                return Optional.empty();
            }
            giveCustomItem(player, definition, reward.amount());
            return Optional.of("<yellow>" + reward.amount() + "x</yellow> " + definition.displayName());
        }
        ItemStack itemStack = new ItemStack(reward.material());
        itemStack.setAmount(reward.amount());
        giveItem(player, itemStack);
        return Optional.of("<yellow>" + reward.amount() + "x</yellow> " + readableMaterial(reward.material()));
    }

    private void giveCustomItem(Player player, CustomItemDefinition definition, int amount) {
        int remaining = Math.max(1, amount);
        int maxStack = Math.max(1, definition.material().getMaxStackSize());
        while (remaining > 0) {
            int stackAmount = Math.min(maxStack, remaining);
            ItemStack itemStack = customItems.createItem(definition);
            itemStack.setAmount(stackAmount);
            giveItem(player, itemStack);
            remaining -= stackAmount;
        }
    }

    private void giveItem(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private String readableMaterial(Material material) {
        String normalized = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return normalized.isBlank() ? material.name() : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String limitLabel(int limit) {
        return limit <= 0 ? "Unlimited" : Integer.toString(limit);
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
