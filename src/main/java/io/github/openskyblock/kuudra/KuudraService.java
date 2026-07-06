package io.github.openskyblock.kuudra;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.SkillService;
import io.github.openskyblock.service.SkillType;
import io.github.openskyblock.star.StarService;
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

public final class KuudraService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final SkillService skills;
    private final CustomItemService customItems;
    private final StarService stars;
    private final Map<String, KuudraTierDefinition> tiers = new HashMap<>();
    private String essenceType = "CRIMSON";
    private String toothItemId = "KUUDRA_TEETH";
    private int maxScore = 100;
    private double skyBlockXpPerUniqueTier = 4.0D;
    private double skyBlockXpPerCompletion = 0.2D;

    public KuudraService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, SkillService skills, CustomItemService customItems, StarService stars) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.skills = skills;
        this.customItems = customItems;
        this.stars = stars;
    }

    public void reload() {
        essenceType = stars.normalizeEssence(configService.kuudra().getString("settings.essence-type", "CRIMSON"));
        toothItemId = configService.kuudra().getString("settings.tooth-item", "KUUDRA_TEETH").toUpperCase(Locale.ROOT);
        maxScore = Math.max(1, configService.kuudra().getInt("settings.max-score", 100));
        skyBlockXpPerUniqueTier = Math.max(0.0D, configService.kuudra().getDouble("settings.skyblock-xp-per-unique-tier", 4.0D));
        skyBlockXpPerCompletion = Math.max(0.0D, configService.kuudra().getDouble("settings.skyblock-xp-per-completion", 0.2D));
        loadTiers();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.kuudra", true);
    }

    public List<String> tierIds() {
        return tierDefinitions().stream().map(KuudraTierDefinition::id).toList();
    }

    public Optional<KuudraTierDefinition> tier(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tiers.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<KuudraTierDefinition> tierDefinitions() {
        return tiers.values().stream()
                .sorted(Comparator.comparingInt(KuudraTierDefinition::tierNumber).thenComparing(KuudraTierDefinition::id))
                .toList();
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.kuudra-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.kuudra-status", List.of(
                TextService.raw("teeth", text.formatNumber(profile.kuudraTeeth())),
                TextService.raw("keys_crafted", text.formatNumber(profile.kuudraKeysCrafted())),
                TextService.raw("keys_used", text.formatNumber(profile.kuudraKeysUsed())),
                TextService.raw("completions", text.formatNumber(profile.totalKuudraCompletions())),
                TextService.raw("best_score", Integer.toString(profile.bestKuudraScore())),
                TextService.raw("essence", text.formatNumber(stars.essenceBalance(profile, essenceType))),
                TextService.parsed("essence_name", stars.essenceDisplayName(essenceType)),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile)))
        ));
        for (KuudraTierDefinition tier : tierDefinitions()) {
            text.send(player, "commands.kuudra-status-line", tierPlaceholders(profile, tier));
        }
    }

    public void sendTiers(Player player) {
        if (!enabled()) {
            text.send(player, "commands.kuudra-disabled");
            return;
        }
        if (tiers.isEmpty()) {
            text.send(player, "commands.kuudra-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.kuudra-tier-header");
        for (KuudraTierDefinition tier : tierDefinitions()) {
            text.send(player, "commands.kuudra-tier-line", tierPlaceholders(profile, tier));
        }
    }

    public void sendKeys(Player player) {
        if (!enabled()) {
            text.send(player, "commands.kuudra-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.kuudra-key-header", List.of(
                TextService.raw("keys_crafted", text.formatNumber(profile.kuudraKeysCrafted())),
                TextService.raw("keys_used", text.formatNumber(profile.kuudraKeysUsed()))
        ));
        for (KuudraTierDefinition tier : tierDefinitions()) {
            List<TextService.TextPlaceholder> placeholders = new ArrayList<>(tierPlaceholders(profile, tier));
            placeholders.add(TextService.raw("keys", Integer.toString(countItem(player, tier.keyItemId()))));
            text.send(player, "commands.kuudra-key-line", placeholders);
        }
    }

    public boolean buyKey(Player player, String tierId, int amount) {
        if (!enabled()) {
            text.send(player, "commands.kuudra-disabled");
            return false;
        }
        if (amount <= 0) {
            text.send(player, "errors.invalid-number");
            return false;
        }
        KuudraTierDefinition tier = tier(tierId).orElse(null);
        if (tier == null) {
            text.send(player, "commands.kuudra-unknown-tier", List.of(TextService.raw("tier", tierId == null ? "" : tierId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (!tierUnlocked(profile, tier)) {
            text.send(player, "commands.kuudra-tier-locked", tierPlaceholders(profile, tier));
            return false;
        }
        CustomItemDefinition key = customItems.definition(tier.keyItemId()).orElse(null);
        if (key == null) {
            text.send(player, "commands.kuudra-key-missing", tierPlaceholders(profile, tier));
            return false;
        }
        double cost = tier.keyCost() * amount;
        if (cost > 0.0D && !economy.spendPurse(player, cost)) {
            text.send(player, "commands.kuudra-key-no-money", keyPlaceholders(profile, tier, amount, cost));
            return false;
        }
        giveCustomItem(player, key, amount);
        profile.addKuudraKeysCrafted(amount);
        profiles.save(player);
        text.send(player, "commands.kuudra-key-crafted", keyPlaceholders(profile, tier, amount, cost));
        return true;
    }

    public boolean run(Player player, String tierId, int requestedScore, boolean paidChest) {
        if (!enabled()) {
            text.send(player, "commands.kuudra-disabled");
            return false;
        }
        KuudraTierDefinition tier = tier(tierId).orElse(null);
        if (tier == null) {
            text.send(player, "commands.kuudra-unknown-tier", List.of(TextService.raw("tier", tierId == null ? "" : tierId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (!tierUnlocked(profile, tier)) {
            text.send(player, "commands.kuudra-tier-locked", tierPlaceholders(profile, tier));
            return false;
        }
        if (paidChest && countItem(player, tier.keyItemId()) < 1) {
            text.send(player, "commands.kuudra-no-key", tierPlaceholders(profile, tier));
            return false;
        }
        if (paidChest && tier.chestCost() > 0.0D && !economy.spendPurse(player, tier.chestCost())) {
            text.send(player, "commands.kuudra-no-money", tierPlaceholders(profile, tier));
            return false;
        }
        if (paidChest) {
            consumeItem(player, tier.keyItemId(), 1);
            profile.addKuudraKeysUsed(1L);
        }
        int score = Math.max(0, Math.min(maxScore, requestedScore));
        double scoreMultiplier = Math.max(0.0D, Math.min(1.0D, score / (double) maxScore));
        double combatXp = tier.combatXp() * scoreMultiplier;
        double coins = tier.completionCoins() * scoreMultiplier;
        double essence = tier.crimsonEssence() * (paidChest ? 1.0D : 0.5D) * scoreMultiplier;
        int teeth = paidChest ? tier.paidTeeth() : tier.freeTeeth();
        if (combatXp > 0.0D) {
            skills.addXp(player, SkillType.COMBAT, combatXp);
        }
        if (coins > 0.0D) {
            economy.addPurse(player, coins);
        }
        if (essence > 0.0D) {
            stars.addEssence(profile, essenceType, essence);
        }
        if (teeth > 0) {
            profile.addKuudraTeeth(teeth);
            customItems.definition(toothItemId).ifPresent(definition -> giveCustomItem(player, definition, teeth));
        }
        List<String> rewards = new ArrayList<>();
        List<KuudraRewardDefinition> pool = paidChest ? tier.paidRewards() : tier.freeRewards();
        int rolls = Math.max(0, paidChest ? tier.paidRewardRolls() : tier.freeRewardRolls());
        for (int index = 0; index < rolls; index++) {
            selectReward(pool, score).flatMap(reward -> giveReward(player, reward)).ifPresent(rewards::add);
        }
        profile.addKuudraCompletion(tier.id(), 1);
        profile.recordKuudraScore(score);
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(tierPlaceholders(profile, tier));
        placeholders.add(TextService.raw("score", Integer.toString(score)));
        placeholders.add(TextService.raw("chest", paidChest ? "Paid" : "Free"));
        placeholders.add(TextService.raw("combat_xp_gained", text.formatNumber(combatXp)));
        placeholders.add(TextService.raw("coins", text.formatNumber(coins)));
        placeholders.add(TextService.raw("teeth_gained", Integer.toString(teeth)));
        placeholders.add(TextService.raw("essence", text.formatNumber(essence)));
        placeholders.add(TextService.parsed("essence_name", stars.essenceDisplayName(essenceType)));
        placeholders.add(TextService.parsed("rewards", rewards.isEmpty() ? "<gray>none</gray>" : String.join("<gray>, </gray>", rewards)));
        text.send(player, "commands.kuudra-completed", placeholders);
        return true;
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        double xp = profile.totalKuudraCompletions() * skyBlockXpPerCompletion;
        xp += profile.kuudraCompletions().keySet().size() * skyBlockXpPerUniqueTier;
        for (KuudraTierDefinition tier : tierDefinitions()) {
            if (profile.kuudraCompletions(tier.id()) > 0) {
                xp += tier.skyBlockXp();
            }
        }
        return Math.max(0.0D, xp);
    }

    private void loadTiers() {
        tiers.clear();
        ConfigurationSection section = configService.kuudra().getConfigurationSection("tiers");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection tierSection = section.getConfigurationSection(id);
            if (tierSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            tiers.put(normalized, new KuudraTierDefinition(
                    normalized,
                    tierSection.getString("display-name", normalized),
                    Math.max(1, tierSection.getInt("tier-number", 1)),
                    Math.max(0, tierSection.getInt("required-combat-level", 0)),
                    tierSection.getString("required-tier", "").toUpperCase(Locale.ROOT),
                    Math.max(0, tierSection.getInt("required-tier-completions", 0)),
                    tierSection.getString("key-item", "").toUpperCase(Locale.ROOT),
                    Math.max(0.0D, tierSection.getDouble("key-cost", 0.0D)),
                    Math.max(0.0D, tierSection.getDouble("chest-cost", 0.0D)),
                    Math.max(0, tierSection.getInt("free-reward-rolls", 1)),
                    Math.max(0, tierSection.getInt("paid-reward-rolls", 2)),
                    Math.max(0.0D, tierSection.getDouble("combat-xp", 0.0D)),
                    Math.max(0.0D, tierSection.getDouble("completion-coins", 0.0D)),
                    Math.max(0, tierSection.getInt("free-teeth", 0)),
                    Math.max(0, tierSection.getInt("paid-teeth", 0)),
                    Math.max(0.0D, tierSection.getDouble("crimson-essence", 0.0D)),
                    Math.max(0.0D, tierSection.getDouble("skyblock-xp", 0.0D)),
                    readRewards(tierSection.getMapList("free-rewards")),
                    readRewards(tierSection.getMapList("paid-rewards"))
            ));
        }
    }

    private List<KuudraRewardDefinition> readRewards(List<Map<?, ?>> rewardMaps) {
        List<KuudraRewardDefinition> rewards = new ArrayList<>();
        for (Map<?, ?> rawReward : rewardMaps) {
            reward(rawReward).ifPresent(rewards::add);
        }
        return List.copyOf(rewards);
    }

    private Optional<KuudraRewardDefinition> reward(Map<?, ?> section) {
        String type = string(section.get("type"), "VANILLA").toUpperCase(Locale.ROOT);
        int amount = Math.max(1, integer(section.get("amount"), 1));
        double weight = Math.max(0.0D, decimal(section.get("weight"), 1.0D));
        int minScore = Math.max(0, integer(section.get("min-score"), 0));
        if (weight <= 0.0D) {
            return Optional.empty();
        }
        if (type.equals("COINS")) {
            double coins = Math.max(0.0D, decimal(section.get("coins"), decimal(section.get("amount"), 0.0D)));
            return coins <= 0.0D ? Optional.empty() : Optional.of(new KuudraRewardDefinition("COINS", "", Material.GOLD_NUGGET, 1, coins, "", 0.0D, weight, minScore));
        }
        if (type.equals("ESSENCE")) {
            String configuredType = stars.normalizeEssence(string(section.get("essence"), essenceType));
            double essence = Math.max(0.0D, decimal(section.get("amount"), 0.0D));
            return essence <= 0.0D ? Optional.empty() : Optional.of(new KuudraRewardDefinition("ESSENCE", "", Material.REDSTONE, 1, 0.0D, configuredType, essence, weight, minScore));
        }
        if (type.equals("CUSTOM_ITEM")) {
            String itemId = string(section.get("item"), "").toUpperCase(Locale.ROOT);
            return itemId.isBlank() ? Optional.empty() : Optional.of(new KuudraRewardDefinition("CUSTOM_ITEM", itemId, Material.STONE, amount, 0.0D, "", 0.0D, weight, minScore));
        }
        Material material = Material.matchMaterial(string(section.get("material"), "NETHERRACK"));
        if (material == null || material.isAir()) {
            return Optional.empty();
        }
        return Optional.of(new KuudraRewardDefinition("VANILLA", "", material, amount, 0.0D, "", 0.0D, weight, minScore));
    }

    private Optional<KuudraRewardDefinition> selectReward(List<KuudraRewardDefinition> rewards, int score) {
        List<KuudraRewardDefinition> eligible = rewards.stream()
                .filter(reward -> score >= reward.minScore())
                .toList();
        double totalWeight = eligible.stream().mapToDouble(KuudraRewardDefinition::weight).sum();
        if (totalWeight <= 0.0D) {
            return Optional.empty();
        }
        double selected = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0D;
        for (KuudraRewardDefinition reward : eligible) {
            cumulative += reward.weight();
            if (selected <= cumulative) {
                return Optional.of(reward);
            }
        }
        return eligible.stream().findFirst();
    }

    private Optional<String> giveReward(Player player, KuudraRewardDefinition reward) {
        if (reward.coinsReward()) {
            economy.addPurse(player, reward.coins());
            return Optional.of("<gold>" + text.formatNumber(reward.coins()) + " coins</gold>");
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (reward.essenceReward()) {
            stars.addEssence(profile, reward.essenceType(), reward.essence());
            return Optional.of("<yellow>" + text.formatNumber(reward.essence()) + "</yellow> " + stars.essenceDisplayName(reward.essenceType()));
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

    private boolean tierUnlocked(SkyBlockProfile profile, KuudraTierDefinition tier) {
        int combatLevel = skills.level(SkillType.COMBAT, profile.skillXp(SkillType.COMBAT));
        if (combatLevel < tier.requiredCombatLevel()) {
            return false;
        }
        if (!tier.requiredTierId().isBlank() && profile.kuudraCompletions(tier.requiredTierId()) < tier.requiredTierCompletions()) {
            return false;
        }
        return true;
    }

    private List<TextService.TextPlaceholder> tierPlaceholders(SkyBlockProfile profile, KuudraTierDefinition tier) {
        int combatLevel = skills.level(SkillType.COMBAT, profile.skillXp(SkillType.COMBAT));
        boolean unlocked = tierUnlocked(profile, tier);
        return List.of(
                TextService.raw("id", tier.id()),
                TextService.parsed("tier", tier.displayName()),
                TextService.raw("tier_number", Integer.toString(tier.tierNumber())),
                TextService.raw("required_combat_level", Integer.toString(tier.requiredCombatLevel())),
                TextService.raw("combat_level", Integer.toString(combatLevel)),
                TextService.raw("required_tier", tier.requiredTierId().isBlank() ? "none" : tier.requiredTierId()),
                TextService.raw("required_tier_completions", Integer.toString(tier.requiredTierCompletions())),
                TextService.raw("required_tier_progress", tier.requiredTierId().isBlank() ? "0" : Integer.toString(profile.kuudraCompletions(tier.requiredTierId()))),
                TextService.raw("key_item", tier.keyItemId()),
                TextService.raw("key_cost", text.formatNumber(tier.keyCost())),
                TextService.raw("chest_cost", text.formatNumber(tier.chestCost())),
                TextService.raw("free_teeth", Integer.toString(tier.freeTeeth())),
                TextService.raw("paid_teeth", Integer.toString(tier.paidTeeth())),
                TextService.raw("combat_xp", text.formatNumber(tier.combatXp())),
                TextService.raw("completion_coins", text.formatNumber(tier.completionCoins())),
                TextService.raw("crimson_essence", text.formatNumber(tier.crimsonEssence())),
                TextService.raw("completions", Integer.toString(profile.kuudraCompletions(tier.id()))),
                TextService.parsed("status", unlocked ? "<green>Unlocked</green>" : "<red>Locked</red>")
        );
    }

    private List<TextService.TextPlaceholder> keyPlaceholders(SkyBlockProfile profile, KuudraTierDefinition tier, int amount, double cost) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(tierPlaceholders(profile, tier));
        placeholders.add(TextService.raw("amount", Integer.toString(amount)));
        placeholders.add(TextService.raw("cost", text.formatNumber(cost)));
        placeholders.add(TextService.raw("keys_crafted", text.formatNumber(profile.kuudraKeysCrafted())));
        return placeholders;
    }

    private int countItem(Player player, String itemId) {
        int amount = 0;
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            if (matches(itemStack, itemId)) {
                amount += itemStack.getAmount();
            }
        }
        return amount;
    }

    private void consumeItem(Player player, String itemId, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getStorageContents().length && remaining > 0; slot++) {
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (!matches(itemStack, itemId)) {
                continue;
            }
            int removed = Math.min(remaining, itemStack.getAmount());
            itemStack.setAmount(itemStack.getAmount() - removed);
            remaining -= removed;
            if (itemStack.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private boolean matches(ItemStack itemStack, String itemId) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        return customItems.definition(itemStack)
                .map(definition -> definition.id().equalsIgnoreCase(itemId))
                .orElse(false);
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
