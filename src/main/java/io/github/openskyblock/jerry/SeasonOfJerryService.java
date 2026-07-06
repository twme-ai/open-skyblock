package io.github.openskyblock.jerry;

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

public final class SeasonOfJerryService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final SkillService skills;
    private final CustomItemService customItems;
    private final CalendarService calendar;
    private final Map<String, JerryGiftDefinition> gifts = new HashMap<>();
    private final Map<Integer, JerryWaveDefinition> waves = new HashMap<>();
    private boolean requireCalendarEvent = true;
    private String eventId = "SEASON_OF_JERRY";
    private int hiddenGiftLimit = 20;
    private int maxGiftPiles = 5;
    private String hiddenGiftItemId = "WHITE_GIFT";
    private String stJerryRewardItemId = "GREEN_GIFT";
    private int stJerryRewardAmount = 1;
    private double skyBlockXpPerHiddenGift = 0.05D;
    private double skyBlockXpPerGiftAttackWave = 1.0D;

    public SeasonOfJerryService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, SkillService skills, CustomItemService customItems, CalendarService calendar) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.skills = skills;
        this.customItems = customItems;
        this.calendar = calendar;
    }

    public void reload() {
        requireCalendarEvent = configService.seasonJerry().getBoolean("settings.require-calendar-event", true);
        eventId = configService.seasonJerry().getString("settings.event-id", "SEASON_OF_JERRY").toUpperCase(Locale.ROOT);
        hiddenGiftLimit = Math.max(1, configService.seasonJerry().getInt("settings.hidden-gift-limit", 20));
        maxGiftPiles = Math.max(1, configService.seasonJerry().getInt("settings.max-gift-piles", 5));
        hiddenGiftItemId = configService.seasonJerry().getString("settings.hidden-gift-item", "WHITE_GIFT").toUpperCase(Locale.ROOT);
        stJerryRewardItemId = configService.seasonJerry().getString("settings.st-jerry-reward-item", "GREEN_GIFT").toUpperCase(Locale.ROOT);
        stJerryRewardAmount = Math.max(1, configService.seasonJerry().getInt("settings.st-jerry-reward-amount", 1));
        skyBlockXpPerHiddenGift = Math.max(0.0D, configService.seasonJerry().getDouble("settings.skyblock-xp-per-hidden-gift", 0.05D));
        skyBlockXpPerGiftAttackWave = Math.max(0.0D, configService.seasonJerry().getDouble("settings.skyblock-xp-per-gift-attack-wave", 1.0D));
        loadGifts();
        loadWaves();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.season-of-jerry", true);
    }

    public boolean eventActive() {
        return !requireCalendarEvent || calendar.eventActive(eventId);
    }

    public List<String> giftIds() {
        return giftDefinitions().stream().map(JerryGiftDefinition::id).toList();
    }

    public List<String> waveIds() {
        return waveDefinitions().stream().map(wave -> Integer.toString(wave.wave())).toList();
    }

    public List<JerryGiftDefinition> giftDefinitions() {
        return gifts.values().stream()
                .sorted(Comparator.comparing(JerryGiftDefinition::id))
                .toList();
    }

    public List<JerryWaveDefinition> waveDefinitions() {
        return waves.values().stream()
                .sorted(Comparator.comparingInt(JerryWaveDefinition::wave))
                .toList();
    }

    public Optional<JerryGiftDefinition> gift(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(gifts.get(id.toUpperCase(Locale.ROOT)));
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.jerry-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        refreshSeason(profile);
        text.send(player, "commands.jerry-status", List.of(
                TextService.parsed("active", eventActive() ? "<green>Active</green>" : "<red>Inactive</red>"),
                TextService.raw("event", eventId),
                TextService.raw("year", Integer.toString(calendar.currentDate().year())),
                TextService.raw("hidden", Integer.toString(profile.jerryHiddenGiftsFound())),
                TextService.raw("hidden_limit", Integer.toString(hiddenGiftLimit)),
                TextService.raw("north_stars", text.formatNumber(profile.jerryNorthStars())),
                TextService.raw("waves", text.formatNumber(profile.jerryGiftAttackWaves())),
                TextService.raw("opened", text.formatNumber(total(profile.jerryGiftsOpened()))),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile)))
        ));
    }

    public void sendGifts(Player player) {
        if (!enabled()) {
            text.send(player, "commands.jerry-disabled");
            return;
        }
        if (gifts.isEmpty()) {
            text.send(player, "commands.jerry-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        refreshSeason(profile);
        text.send(player, "commands.jerry-gift-header");
        for (JerryGiftDefinition gift : giftDefinitions()) {
            text.send(player, "commands.jerry-gift-line", giftPlaceholders(profile, gift));
        }
    }

    public boolean findHiddenGifts(Player player, int rawAmount) {
        if (!enabled()) {
            text.send(player, "commands.jerry-disabled");
            return false;
        }
        if (!eventActive()) {
            text.send(player, "commands.jerry-inactive", List.of(TextService.raw("event", eventId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        refreshSeason(profile);
        int remaining = Math.max(0, hiddenGiftLimit - profile.jerryHiddenGiftsFound());
        if (remaining <= 0) {
            text.send(player, "commands.jerry-hidden-complete", hiddenPlaceholders(profile, 0));
            return false;
        }
        int amount = Math.max(1, Math.min(remaining, rawAmount));
        CustomItemDefinition giftItem = customItems.definition(hiddenGiftItemId).orElse(null);
        if (giftItem == null) {
            text.send(player, "commands.jerry-missing-item", List.of(TextService.raw("item", hiddenGiftItemId)));
            return false;
        }
        giveCustomItem(player, giftItem, amount);
        profile.addJerryHiddenGiftsFound(amount);
        profiles.save(player);
        text.send(player, "commands.jerry-hidden-found", hiddenPlaceholders(profile, amount));
        return true;
    }

    public boolean claimStJerry(Player player) {
        if (!enabled()) {
            text.send(player, "commands.jerry-disabled");
            return false;
        }
        if (!eventActive()) {
            text.send(player, "commands.jerry-inactive", List.of(TextService.raw("event", eventId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        refreshSeason(profile);
        int year = calendar.currentDate().year();
        if (profile.jerryHiddenGiftsFound() < hiddenGiftLimit) {
            text.send(player, "commands.jerry-st-jerry-locked", hiddenPlaceholders(profile, 0));
            return false;
        }
        if (profile.jerryStJerryClaimedYear() == year) {
            text.send(player, "commands.jerry-st-jerry-claimed", hiddenPlaceholders(profile, 0));
            return false;
        }
        CustomItemDefinition rewardItem = customItems.definition(stJerryRewardItemId).orElse(null);
        if (rewardItem == null) {
            text.send(player, "commands.jerry-missing-item", List.of(TextService.raw("item", stJerryRewardItemId)));
            return false;
        }
        giveCustomItem(player, rewardItem, stJerryRewardAmount);
        profile.jerryStJerryClaimedYear(year);
        profiles.save(player);
        text.send(player, "commands.jerry-st-jerry-reward", List.of(
                TextService.raw("amount", Integer.toString(stJerryRewardAmount)),
                TextService.parsed("item", rewardItem.displayName())
        ));
        return true;
    }

    public boolean defend(Player player, int waveInput, int giftPilesInput) {
        if (!enabled()) {
            text.send(player, "commands.jerry-disabled");
            return false;
        }
        if (!eventActive()) {
            text.send(player, "commands.jerry-inactive", List.of(TextService.raw("event", eventId)));
            return false;
        }
        JerryWaveDefinition wave = waves.get(waveInput);
        if (wave == null) {
            text.send(player, "commands.jerry-unknown-wave", List.of(TextService.raw("wave", Integer.toString(waveInput))));
            return false;
        }
        int giftPiles = Math.max(0, Math.min(maxGiftPiles, giftPilesInput));
        SkyBlockProfile profile = profiles.profile(player);
        refreshSeason(profile);
        double combatXp = wave.combatXp() * giftPiles;
        double coins = wave.coins() * giftPiles;
        long northStars = wave.northStars() * giftPiles;
        if (combatXp > 0.0D) {
            skills.addXp(player, SkillType.COMBAT, combatXp);
        }
        if (coins > 0.0D) {
            economy.addPurse(player, coins);
        }
        if (northStars > 0L) {
            profile.addJerryNorthStars(northStars);
        }
        List<String> rewards = new ArrayList<>();
        for (JerryRewardDefinition reward : wave.rewards()) {
            giveReward(player, reward, Math.max(1, giftPiles)).ifPresent(rewards::add);
        }
        profile.addJerryGiftAttackWave(1);
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(wavePlaceholders(wave));
        placeholders.add(TextService.raw("gift_piles", Integer.toString(giftPiles)));
        placeholders.add(TextService.raw("max_gift_piles", Integer.toString(maxGiftPiles)));
        placeholders.add(TextService.raw("combat_xp_gained", text.formatNumber(combatXp)));
        placeholders.add(TextService.raw("coins_gained", text.formatNumber(coins)));
        placeholders.add(TextService.raw("north_stars_gained", text.formatNumber(northStars)));
        placeholders.add(TextService.raw("waves", text.formatNumber(profile.jerryGiftAttackWaves())));
        placeholders.add(TextService.parsed("rewards", rewards.isEmpty() ? "<gray>none</gray>" : String.join("<gray>, </gray>", rewards)));
        text.send(player, "commands.jerry-wave-defended", placeholders);
        return true;
    }

    public boolean openGift(Player player, String giftId, int rawAmount) {
        if (!enabled()) {
            text.send(player, "commands.jerry-disabled");
            return false;
        }
        JerryGiftDefinition gift = gift(giftId).orElse(null);
        if (gift == null) {
            text.send(player, "commands.jerry-unknown-gift", List.of(TextService.raw("gift", giftId == null ? "" : giftId)));
            return false;
        }
        int amount = Math.max(1, rawAmount);
        int available = countCustomItem(player, gift.itemId());
        if (available < amount) {
            text.send(player, "commands.jerry-gift-missing", List.of(
                    TextService.parsed("gift", gift.displayName()),
                    TextService.raw("amount", Integer.toString(amount)),
                    TextService.raw("available", Integer.toString(available))
            ));
            return false;
        }
        removeCustomItem(player, gift.itemId(), amount);
        SkyBlockProfile profile = profiles.profile(player);
        List<String> rewards = new ArrayList<>();
        long northStars = (long) gift.northStars() * amount;
        if (northStars > 0L) {
            profile.addJerryNorthStars(northStars);
        }
        for (int index = 0; index < amount; index++) {
            selectReward(gift.rewards())
                    .flatMap(reward -> giveReward(player, reward, 1))
                    .ifPresent(rewards::add);
        }
        profile.addJerryGiftOpened(gift.id(), amount);
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(giftPlaceholders(profile, gift));
        placeholders.add(TextService.raw("amount", Integer.toString(amount)));
        placeholders.add(TextService.raw("north_stars_gained", text.formatNumber(northStars)));
        placeholders.add(TextService.parsed("rewards", rewards.isEmpty() ? "<gray>none</gray>" : String.join("<gray>, </gray>", rewards)));
        text.send(player, "commands.jerry-gift-opened", placeholders);
        return true;
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        double xp = profile.jerryHiddenGiftsFound() * skyBlockXpPerHiddenGift;
        xp += profile.jerryGiftAttackWaves() * skyBlockXpPerGiftAttackWave;
        for (JerryGiftDefinition gift : giftDefinitions()) {
            xp += profile.jerryGiftsOpened(gift.id()) * gift.skyBlockXp();
        }
        return Math.max(0.0D, xp);
    }

    private void refreshSeason(SkyBlockProfile profile) {
        int currentYear = calendar.currentDate().year();
        if (profile.jerryGiftYear() != currentYear) {
            profile.jerryGiftYear(currentYear);
            profile.jerryHiddenGiftsFound(0);
        }
    }

    private void loadGifts() {
        gifts.clear();
        ConfigurationSection section = configService.seasonJerry().getConfigurationSection("gifts");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection gift = section.getConfigurationSection(id);
            if (gift == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            gifts.put(normalized, new JerryGiftDefinition(
                    normalized,
                    gift.getString("display-name", normalized),
                    gift.getString("item", normalized + "_GIFT").toUpperCase(Locale.ROOT),
                    Math.max(0, gift.getInt("north-stars", 0)),
                    Math.max(0.0D, gift.getDouble("skyblock-xp", 0.0D)),
                    readRewards(gift.getMapList("rewards"))
            ));
        }
    }

    private void loadWaves() {
        waves.clear();
        ConfigurationSection section = configService.seasonJerry().getConfigurationSection("gift-attack.waves");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection wave = section.getConfigurationSection(key);
            if (wave == null) {
                continue;
            }
            try {
                int waveNumber = Integer.parseInt(key);
                waves.put(waveNumber, new JerryWaveDefinition(
                        waveNumber,
                        wave.getString("display-name", "Wave " + waveNumber),
                        Math.max(0.0D, wave.getDouble("combat-xp", 0.0D)),
                        Math.max(0.0D, wave.getDouble("coins", 0.0D)),
                        Math.max(0L, wave.getLong("north-stars", 0L)),
                        Math.max(0.0D, wave.getDouble("skyblock-xp", 0.0D)),
                        readRewards(wave.getMapList("rewards"))
                ));
            } catch (NumberFormatException ignored) {
                // Invalid wave keys are ignored so server owners can fix config without startup failure.
            }
        }
    }

    private List<JerryRewardDefinition> readRewards(List<Map<?, ?>> rawRewards) {
        List<JerryRewardDefinition> rewards = new ArrayList<>();
        for (Map<?, ?> rawReward : rawRewards) {
            reward(rawReward).ifPresent(rewards::add);
        }
        return List.copyOf(rewards);
    }

    private Optional<JerryRewardDefinition> reward(Map<?, ?> section) {
        String type = string(section.get("type"), "VANILLA").toUpperCase(Locale.ROOT);
        int amount = Math.max(1, integer(section.get("amount"), 1));
        double weight = Math.max(0.0D, decimal(section.get("weight"), 1.0D));
        if (type.equals("COINS")) {
            double coins = Math.max(0.0D, decimal(section.get("coins"), decimal(section.get("amount"), 0.0D)));
            return coins <= 0.0D ? Optional.empty() : Optional.of(new JerryRewardDefinition("COINS", "", Material.GOLD_NUGGET, 1, coins, weight));
        }
        if (type.equals("CUSTOM_ITEM")) {
            String itemId = string(section.get("item"), "").toUpperCase(Locale.ROOT);
            return itemId.isBlank() ? Optional.empty() : Optional.of(new JerryRewardDefinition("CUSTOM_ITEM", itemId, Material.STONE, amount, 0.0D, weight));
        }
        Material material = Material.matchMaterial(string(section.get("material"), "DIRT"));
        if (material == null || material.isAir()) {
            return Optional.empty();
        }
        return Optional.of(new JerryRewardDefinition("VANILLA", "", material, amount, 0.0D, weight));
    }

    private Optional<JerryRewardDefinition> selectReward(List<JerryRewardDefinition> rewards) {
        double totalWeight = rewards.stream().mapToDouble(JerryRewardDefinition::weight).filter(weight -> weight > 0.0D).sum();
        if (totalWeight <= 0.0D) {
            return Optional.empty();
        }
        double selected = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cursor = 0.0D;
        for (JerryRewardDefinition reward : rewards) {
            cursor += Math.max(0.0D, reward.weight());
            if (selected <= cursor) {
                return Optional.of(reward);
            }
        }
        return rewards.stream().findFirst();
    }

    private Optional<String> giveReward(Player player, JerryRewardDefinition reward, int multiplier) {
        int amount = Math.max(1, reward.amount() * Math.max(1, multiplier));
        if (reward.coinsReward()) {
            double coins = reward.coins() * Math.max(1, multiplier);
            economy.addPurse(player, coins);
            return Optional.of("<gold>" + text.formatNumber(coins) + " coins</gold>");
        }
        if (reward.customItem()) {
            CustomItemDefinition definition = customItems.definition(reward.itemId()).orElse(null);
            if (definition == null) {
                return Optional.empty();
            }
            giveCustomItem(player, definition, amount);
            return Optional.of("<yellow>" + amount + "x</yellow> " + definition.displayName());
        }
        ItemStack itemStack = new ItemStack(reward.material());
        itemStack.setAmount(amount);
        giveItem(player, itemStack);
        return Optional.of("<yellow>" + amount + "x</yellow> " + readableMaterial(reward.material()));
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

    private int countCustomItem(Player player, String itemId) {
        int amount = 0;
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            if (matchesCustomItem(itemStack, itemId)) {
                amount += itemStack.getAmount();
            }
        }
        return amount;
    }

    private void removeCustomItem(Player player, String itemId, int amount) {
        int remaining = Math.max(0, amount);
        for (int slot = 0; slot < player.getInventory().getSize() && remaining > 0; slot++) {
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (!matchesCustomItem(itemStack, itemId)) {
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

    private boolean matchesCustomItem(ItemStack itemStack, String itemId) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        return customItems.definition(itemStack)
                .map(CustomItemDefinition::id)
                .map(id -> id.equalsIgnoreCase(itemId))
                .orElse(false);
    }

    private List<TextService.TextPlaceholder> hiddenPlaceholders(SkyBlockProfile profile, int amount) {
        return List.of(
                TextService.raw("amount", Integer.toString(amount)),
                TextService.raw("hidden", Integer.toString(profile.jerryHiddenGiftsFound())),
                TextService.raw("hidden_limit", Integer.toString(hiddenGiftLimit)),
                TextService.raw("remaining", Integer.toString(Math.max(0, hiddenGiftLimit - profile.jerryHiddenGiftsFound())))
        );
    }

    private List<TextService.TextPlaceholder> giftPlaceholders(SkyBlockProfile profile, JerryGiftDefinition gift) {
        return List.of(
                TextService.raw("id", gift.id()),
                TextService.parsed("gift", gift.displayName()),
                TextService.raw("item", gift.itemId()),
                TextService.raw("north_stars", Integer.toString(gift.northStars())),
                TextService.raw("opened", Integer.toString(profile.jerryGiftsOpened(gift.id()))),
                TextService.raw("skyblock_xp", text.formatNumber(gift.skyBlockXp()))
        );
    }

    private List<TextService.TextPlaceholder> wavePlaceholders(JerryWaveDefinition wave) {
        return List.of(
                TextService.raw("wave", Integer.toString(wave.wave())),
                TextService.parsed("wave_name", wave.displayName()),
                TextService.raw("combat_xp", text.formatNumber(wave.combatXp())),
                TextService.raw("coins", text.formatNumber(wave.coins())),
                TextService.raw("north_stars", text.formatNumber(wave.northStars())),
                TextService.raw("skyblock_xp", text.formatNumber(wave.skyBlockXp()))
        );
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
