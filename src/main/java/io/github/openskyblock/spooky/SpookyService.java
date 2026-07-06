package io.github.openskyblock.spooky;

import io.github.openskyblock.calendar.CalendarService;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.SkillService;
import io.github.openskyblock.service.SkillType;
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

public final class SpookyService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final SkillService skills;
    private final CustomItemService customItems;
    private final CalendarService calendar;
    private final Map<String, SpookyMobDefinition> mobs = new HashMap<>();
    private final Map<String, SpookyScoreRewardDefinition> scoreRewards = new HashMap<>();
    private boolean requireCalendarEvent = true;
    private String eventId = "SPOOKY_FESTIVAL";
    private int greenCandyPoints = 1;
    private int purpleCandyPoints = 5;
    private int maxKillAmount = 100;
    private double skyBlockXpPerScore = 0.01D;

    public SpookyService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, SkillService skills, CustomItemService customItems, CalendarService calendar) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.skills = skills;
        this.customItems = customItems;
        this.calendar = calendar;
    }

    public void reload() {
        requireCalendarEvent = configService.spooky().getBoolean("settings.require-calendar-event", true);
        eventId = configService.spooky().getString("settings.event-id", "SPOOKY_FESTIVAL").toUpperCase(Locale.ROOT);
        greenCandyPoints = Math.max(0, configService.spooky().getInt("settings.green-candy-points", 1));
        purpleCandyPoints = Math.max(0, configService.spooky().getInt("settings.purple-candy-points", 5));
        maxKillAmount = Math.max(1, configService.spooky().getInt("settings.max-kill-amount", 100));
        skyBlockXpPerScore = Math.max(0.0D, configService.spooky().getDouble("settings.skyblock-xp-per-score", 0.01D));
        loadMobs();
        loadScoreRewards();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.spooky-festival", true);
    }

    public boolean eventActive() {
        return !requireCalendarEvent || calendar.eventActive(eventId);
    }

    public List<String> mobIds() {
        return mobDefinitions().stream().map(SpookyMobDefinition::id).toList();
    }

    public List<String> scoreRewardIds() {
        return scoreRewardDefinitions().stream().map(SpookyScoreRewardDefinition::id).toList();
    }

    public List<SpookyMobDefinition> mobDefinitions() {
        return mobs.values().stream()
                .sorted(Comparator.comparingInt(SpookyMobDefinition::minimumCombatLevel).thenComparing(SpookyMobDefinition::id))
                .toList();
    }

    public List<SpookyScoreRewardDefinition> scoreRewardDefinitions() {
        return scoreRewards.values().stream()
                .sorted(Comparator.comparingInt(SpookyScoreRewardDefinition::requiredScore).thenComparing(SpookyScoreRewardDefinition::id))
                .toList();
    }

    public Optional<SpookyMobDefinition> mob(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mobs.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<SpookyScoreRewardDefinition> scoreReward(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(scoreRewards.get(id.toUpperCase(Locale.ROOT)));
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.spooky-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.spooky-status", List.of(
                TextService.parsed("active", eventActive() ? "<green>Active</green>" : "<red>Inactive</red>"),
                TextService.raw("event", eventId),
                TextService.raw("green_candy", text.formatNumber(profile.spookyGreenCandy())),
                TextService.raw("purple_candy", text.formatNumber(profile.spookyPurpleCandy())),
                TextService.raw("score", text.formatNumber(score(profile))),
                TextService.raw("kills", text.formatNumber(total(profile.spookyMobKills()))),
                TextService.raw("claimed", Integer.toString(profile.claimedSpookyRewards().size())),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile)))
        ));
    }

    public void sendMobs(Player player) {
        if (!enabled()) {
            text.send(player, "commands.spooky-disabled");
            return;
        }
        if (mobs.isEmpty()) {
            text.send(player, "commands.spooky-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.spooky-mob-header");
        for (SpookyMobDefinition mob : mobDefinitions()) {
            text.send(player, "commands.spooky-mob-line", mobPlaceholders(profile, mob));
        }
    }

    public void sendRewards(Player player) {
        if (!enabled()) {
            text.send(player, "commands.spooky-disabled");
            return;
        }
        if (scoreRewards.isEmpty()) {
            text.send(player, "commands.spooky-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.spooky-reward-header", List.of(TextService.raw("score", text.formatNumber(score(profile)))));
        for (SpookyScoreRewardDefinition reward : scoreRewardDefinitions()) {
            text.send(player, "commands.spooky-reward-line", rewardPlaceholders(profile, reward));
        }
    }

    public boolean kill(Player player, String mobId, int rawAmount) {
        if (!enabled()) {
            text.send(player, "commands.spooky-disabled");
            return false;
        }
        if (!eventActive()) {
            text.send(player, "commands.spooky-inactive", List.of(TextService.raw("event", eventId)));
            return false;
        }
        SpookyMobDefinition mob = mob(mobId).orElse(null);
        if (mob == null) {
            text.send(player, "commands.spooky-unknown-mob", List.of(TextService.raw("mob", mobId == null ? "" : mobId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int combatLevel = skills.level(SkillType.COMBAT, profile.skillXp(SkillType.COMBAT));
        if (combatLevel < mob.minimumCombatLevel()) {
            text.send(player, "commands.spooky-mob-locked", mobPlaceholders(profile, mob));
            return false;
        }
        int amount = Math.max(1, Math.min(maxKillAmount, rawAmount));
        int greenCandy = mob.greenCandy() * amount;
        int purpleCandy = mob.purpleCandy() * amount;
        double combatXp = mob.combatXp() * amount;
        double coins = mob.coins() * amount;
        profile.addSpookyGreenCandy(greenCandy);
        profile.addSpookyPurpleCandy(purpleCandy);
        profile.addSpookyMobKill(mob.id(), amount);
        if (combatXp > 0.0D) {
            skills.addXp(player, SkillType.COMBAT, combatXp);
        }
        if (coins > 0.0D) {
            economy.addPurse(player, coins);
        }
        List<String> rewards = new ArrayList<>();
        int currentScore = score(profile);
        for (int index = 0; index < amount; index++) {
            selectReward(mob.rewards(), currentScore)
                    .flatMap(reward -> giveReward(player, reward))
                    .ifPresent(rewards::add);
        }
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(mobPlaceholders(profile, mob));
        placeholders.add(TextService.raw("amount", Integer.toString(amount)));
        placeholders.add(TextService.raw("green_gained", text.formatNumber(greenCandy)));
        placeholders.add(TextService.raw("purple_gained", text.formatNumber(purpleCandy)));
        placeholders.add(TextService.raw("score", text.formatNumber(score(profile))));
        placeholders.add(TextService.raw("combat_xp_gained", text.formatNumber(combatXp)));
        placeholders.add(TextService.raw("coins_gained", text.formatNumber(coins)));
        placeholders.add(TextService.parsed("rewards", rewards.isEmpty() ? "<gray>none</gray>" : String.join("<gray>, </gray>", rewards)));
        text.send(player, "commands.spooky-mob-killed", placeholders);
        return true;
    }

    public boolean claimRewards(Player player, String requestedRewardId) {
        if (!enabled()) {
            text.send(player, "commands.spooky-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (requestedRewardId != null && !requestedRewardId.isBlank() && !requestedRewardId.equalsIgnoreCase("all")) {
            SpookyScoreRewardDefinition reward = scoreReward(requestedRewardId).orElse(null);
            if (reward == null) {
                text.send(player, "commands.spooky-unknown-reward", List.of(TextService.raw("reward", requestedRewardId)));
                return false;
            }
            return claimReward(player, profile, reward, true);
        }
        List<String> claimed = new ArrayList<>();
        for (SpookyScoreRewardDefinition reward : scoreRewardDefinitions()) {
            if (claimReward(player, profile, reward, false)) {
                claimed.add(reward.displayName());
            }
        }
        if (claimed.isEmpty()) {
            text.send(player, "commands.spooky-claim-empty");
            return false;
        }
        profiles.save(player);
        text.send(player, "commands.spooky-claimed", List.of(
                TextService.raw("amount", Integer.toString(claimed.size())),
                TextService.parsed("rewards", String.join("<gray>, </gray>", claimed))
        ));
        return true;
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        double xp = score(profile) * skyBlockXpPerScore;
        for (SpookyScoreRewardDefinition reward : scoreRewardDefinitions()) {
            if (profile.hasClaimedSpookyReward(reward.id())) {
                xp += reward.skyBlockXp();
            }
        }
        return Math.max(0.0D, xp);
    }

    private boolean claimReward(Player player, SkyBlockProfile profile, SpookyScoreRewardDefinition reward, boolean notify) {
        if (score(profile) < reward.requiredScore()) {
            if (notify) {
                text.send(player, "commands.spooky-reward-locked", rewardPlaceholders(profile, reward));
            }
            return false;
        }
        if (profile.hasClaimedSpookyReward(reward.id())) {
            if (notify) {
                text.send(player, "commands.spooky-reward-already-claimed", rewardPlaceholders(profile, reward));
            }
            return false;
        }
        List<String> rewards = new ArrayList<>();
        for (SpookyRewardDefinition prize : reward.rewards()) {
            giveReward(player, prize).ifPresent(rewards::add);
        }
        profile.claimSpookyReward(reward.id());
        if (notify) {
            profiles.save(player);
            List<TextService.TextPlaceholder> placeholders = new ArrayList<>(rewardPlaceholders(profile, reward));
            placeholders.add(TextService.parsed("rewards", rewards.isEmpty() ? "<gray>none</gray>" : String.join("<gray>, </gray>", rewards)));
            text.send(player, "commands.spooky-reward-claimed", placeholders);
        }
        return true;
    }

    private Optional<SpookyRewardDefinition> selectReward(List<SpookyRewardDefinition> rewards, int currentScore) {
        List<SpookyRewardDefinition> eligible = rewards.stream()
                .filter(reward -> currentScore >= reward.minimumScore())
                .toList();
        double totalWeight = eligible.stream().mapToDouble(SpookyRewardDefinition::weight).filter(weight -> weight > 0.0D).sum();
        if (totalWeight <= 0.0D) {
            return Optional.empty();
        }
        double selected = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cursor = 0.0D;
        for (SpookyRewardDefinition reward : eligible) {
            cursor += Math.max(0.0D, reward.weight());
            if (selected <= cursor) {
                return Optional.of(reward);
            }
        }
        return eligible.stream().findFirst();
    }

    private Optional<String> giveReward(Player player, SpookyRewardDefinition reward) {
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

    private void loadMobs() {
        mobs.clear();
        ConfigurationSection section = configService.spooky().getConfigurationSection("mobs");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection mob = section.getConfigurationSection(id);
            if (mob == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            mobs.put(normalized, new SpookyMobDefinition(
                    normalized,
                    mob.getString("display-name", normalized),
                    Math.max(0, mob.getInt("minimum-combat-level", 1)),
                    Math.max(0, mob.getInt("green-candy", 0)),
                    Math.max(0, mob.getInt("purple-candy", 0)),
                    Math.max(0.0D, mob.getDouble("combat-xp", 0.0D)),
                    Math.max(0.0D, mob.getDouble("coins", 0.0D)),
                    Math.max(0.0D, mob.getDouble("weight", 1.0D)),
                    readRewards(mob.getMapList("rewards"))
            ));
        }
    }

    private void loadScoreRewards() {
        scoreRewards.clear();
        ConfigurationSection section = configService.spooky().getConfigurationSection("score-rewards");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection reward = section.getConfigurationSection(id);
            if (reward == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            scoreRewards.put(normalized, new SpookyScoreRewardDefinition(
                    normalized,
                    reward.getString("display-name", normalized),
                    Math.max(0, reward.getInt("required-score", 0)),
                    Math.max(0.0D, reward.getDouble("skyblock-xp", 0.0D)),
                    readRewards(reward.getMapList("rewards"))
            ));
        }
    }

    private List<SpookyRewardDefinition> readRewards(List<Map<?, ?>> rewardMaps) {
        List<SpookyRewardDefinition> rewards = new ArrayList<>();
        for (Map<?, ?> rawReward : rewardMaps) {
            reward(rawReward).ifPresent(rewards::add);
        }
        return List.copyOf(rewards);
    }

    private Optional<SpookyRewardDefinition> reward(Map<?, ?> section) {
        String type = string(section.get("type"), "VANILLA").toUpperCase(Locale.ROOT);
        int amount = Math.max(1, integer(section.get("amount"), 1));
        double weight = Math.max(0.0D, decimal(section.get("weight"), 1.0D));
        int minimumScore = Math.max(0, integer(section.get("minimum-score"), 0));
        if (type.equals("COINS")) {
            double coins = Math.max(0.0D, decimal(section.get("coins"), decimal(section.get("amount"), 0.0D)));
            return coins <= 0.0D ? Optional.empty() : Optional.of(new SpookyRewardDefinition("COINS", "", Material.GOLD_NUGGET, 1, coins, weight, minimumScore));
        }
        if (type.equals("CUSTOM_ITEM")) {
            String itemId = string(section.get("item"), "").toUpperCase(Locale.ROOT);
            return itemId.isBlank() ? Optional.empty() : Optional.of(new SpookyRewardDefinition("CUSTOM_ITEM", itemId, Material.STONE, amount, 0.0D, weight, minimumScore));
        }
        Material material = Material.matchMaterial(string(section.get("material"), "DIRT"));
        if (material == null || material.isAir()) {
            return Optional.empty();
        }
        return Optional.of(new SpookyRewardDefinition("VANILLA", "", material, amount, 0.0D, weight, minimumScore));
    }

    private List<TextService.TextPlaceholder> mobPlaceholders(SkyBlockProfile profile, SpookyMobDefinition mob) {
        return List.of(
                TextService.raw("id", mob.id()),
                TextService.raw("mob_id", mob.id()),
                TextService.parsed("mob", mob.displayName()),
                TextService.raw("minimum_combat", Integer.toString(mob.minimumCombatLevel())),
                TextService.raw("green_candy", text.formatNumber(mob.greenCandy())),
                TextService.raw("purple_candy", text.formatNumber(mob.purpleCandy())),
                TextService.raw("combat_xp", text.formatNumber(mob.combatXp())),
                TextService.raw("coins", text.formatNumber(mob.coins())),
                TextService.raw("kills", Integer.toString(profile.spookyMobKills(mob.id())))
        );
    }

    private List<TextService.TextPlaceholder> rewardPlaceholders(SkyBlockProfile profile, SpookyScoreRewardDefinition reward) {
        int currentScore = score(profile);
        boolean claimed = profile.hasClaimedSpookyReward(reward.id());
        boolean unlocked = currentScore >= reward.requiredScore();
        return List.of(
                TextService.raw("id", reward.id()),
                TextService.raw("reward_id", reward.id()),
                TextService.parsed("reward", reward.displayName()),
                TextService.raw("required_score", text.formatNumber(reward.requiredScore())),
                TextService.raw("score", text.formatNumber(currentScore)),
                TextService.raw("skyblock_xp", text.formatNumber(reward.skyBlockXp())),
                TextService.parsed("status", claimed ? "<green>Claimed</green>" : unlocked ? "<yellow>Unlocked</yellow>" : "<red>Locked</red>")
        );
    }

    private int score(SkyBlockProfile profile) {
        long value = profile.spookyGreenCandy() * (long) greenCandyPoints;
        value += profile.spookyPurpleCandy() * (long) purpleCandyPoints;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private long total(Map<String, Integer> values) {
        return values.values().stream().mapToLong(Integer::longValue).sum();
    }

    private String readableMaterial(Material material) {
        String normalized = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return normalized.isBlank() ? material.name() : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
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
