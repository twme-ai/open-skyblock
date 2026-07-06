package io.github.openskyblock.chocolate;

import io.github.openskyblock.calendar.CalendarService;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.Rarity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ChocolateFactoryService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final CustomItemService customItems;
    private final CalendarService calendar;
    private final Map<Integer, ChocolateFactoryLevelDefinition> factoryLevels = new HashMap<>();
    private final Map<String, ChocolateUpgradeDefinition> upgrades = new HashMap<>();
    private final Map<String, ChocolateRabbitDefinition> rabbits = new HashMap<>();
    private final Map<String, ChocolateShopItemDefinition> shopItems = new HashMap<>();
    private final Map<Rarity, Double> rarityCosts = new EnumMap<>(Rarity.class);
    private final Map<Rarity, Double> duplicateMultipliers = new EnumMap<>(Rarity.class);
    private final Map<Rarity, Double> eggRarityWeights = new EnumMap<>(Rarity.class);
    private final Map<Integer, Map<Rarity, Double>> offerRarityWeights = new HashMap<>();
    private boolean requireCalendarEvent = true;
    private String eventId = "HOPPITY_HUNT";
    private long maxOfflineSeconds = 86400L;
    private long eggCycleSeconds = 2400L;
    private double baseClickChocolate = 1.0D;
    private double baseProduction = 0.0D;
    private double skyBlockXpPerRabbit = 1.0D;
    private double skyBlockXpPerFactoryLevel = 25.0D;
    private int hoppityOfferCount = 7;
    private int pityMythicEggs = 500;
    private int pityDivineEggs = 2500;
    private List<String> eggTypes = List.of("BREAKFAST", "LUNCH", "DINNER", "BRUNCH", "DEJEUNER", "SUPPER");

    public ChocolateFactoryService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, CustomItemService customItems, CalendarService calendar) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.customItems = customItems;
        this.calendar = calendar;
    }

    public void reload() {
        requireCalendarEvent = configService.chocolateFactory().getBoolean("settings.require-calendar-event", true);
        eventId = configService.chocolateFactory().getString("settings.event-id", "HOPPITY_HUNT").toUpperCase(Locale.ROOT);
        maxOfflineSeconds = Math.max(0L, configService.chocolateFactory().getLong("settings.max-offline-seconds", 86400L));
        eggCycleSeconds = Math.max(60L, configService.chocolateFactory().getLong("settings.egg-cycle-seconds", 2400L));
        baseClickChocolate = Math.max(0.0D, configService.chocolateFactory().getDouble("settings.base-click-chocolate", 1.0D));
        baseProduction = Math.max(0.0D, configService.chocolateFactory().getDouble("settings.base-production", 0.0D));
        skyBlockXpPerRabbit = Math.max(0.0D, configService.chocolateFactory().getDouble("settings.skyblock-xp-per-rabbit", 1.0D));
        skyBlockXpPerFactoryLevel = Math.max(0.0D, configService.chocolateFactory().getDouble("settings.skyblock-xp-per-factory-level", 25.0D));
        hoppityOfferCount = Math.max(1, Math.min(12, configService.chocolateFactory().getInt("settings.hoppity-offer-count", 7)));
        pityMythicEggs = Math.max(0, configService.chocolateFactory().getInt("settings.pity-mythic-eggs", 500));
        pityDivineEggs = Math.max(0, configService.chocolateFactory().getInt("settings.pity-divine-eggs", 2500));
        List<String> configuredEggTypes = configService.chocolateFactory().getStringList("settings.egg-types");
        if (!configuredEggTypes.isEmpty()) {
            eggTypes = configuredEggTypes.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .toList();
        }
        loadFactoryLevels();
        loadUpgrades();
        loadRabbits();
        loadShopItems();
        loadRarityMap("rarity-costs", rarityCosts);
        loadRarityMap("duplicate-multipliers", duplicateMultipliers);
        loadRarityMap("egg-rarity-weights", eggRarityWeights);
        loadOfferRarityWeights();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.chocolate-factory", true);
    }

    public boolean hoppityEnabled() {
        return configService.main().getBoolean("features.hoppity-hunt", true);
    }

    public boolean eventActive() {
        return !requireCalendarEvent || calendar.eventActive(eventId);
    }

    public List<String> upgradeIds() {
        return upgrades().stream().map(ChocolateUpgradeDefinition::id).toList();
    }

    public List<String> rabbitIds() {
        return rabbits().stream().map(ChocolateRabbitDefinition::id).toList();
    }

    public List<String> currentOfferIds(Player player) {
        if (player == null) {
            return List.of();
        }
        return currentOffers(profiles.profile(player)).stream()
                .map(offer -> offer.rabbit().id())
                .toList();
    }

    public List<String> shopItemIds() {
        return shopItems().stream().map(ChocolateShopItemDefinition::id).toList();
    }

    public List<ChocolateUpgradeDefinition> upgrades() {
        return upgrades.values().stream()
                .sorted(Comparator.comparing(ChocolateUpgradeDefinition::id))
                .toList();
    }

    public List<ChocolateRabbitDefinition> rabbits() {
        return rabbits.values().stream()
                .sorted(Comparator.comparing(ChocolateRabbitDefinition::id))
                .toList();
    }

    public List<ChocolateShopItemDefinition> shopItems() {
        return shopItems.values().stream()
                .sorted(Comparator.comparing(ChocolateShopItemDefinition::id))
                .toList();
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.chocolate-disabled");
            return;
        }
        SkyBlockProfile profile = sync(player);
        refreshYearlyState(profile);
        profiles.save(player);
        text.send(player, "commands.chocolate-status", statusPlaceholders(profile));
    }

    public boolean click(Player player) {
        if (!enabled()) {
            text.send(player, "commands.chocolate-disabled");
            return false;
        }
        SkyBlockProfile profile = sync(player);
        double amount = addChocolate(profile, clickChocolate(profile));
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(statusPlaceholders(profile));
        placeholders.add(TextService.raw("amount", text.formatNumber(amount)));
        text.send(player, "commands.chocolate-clicked", placeholders);
        return true;
    }

    public void sendUpgrades(Player player) {
        if (!enabled()) {
            text.send(player, "commands.chocolate-disabled");
            return;
        }
        SkyBlockProfile profile = sync(player);
        text.send(player, "commands.chocolate-upgrades-header", statusPlaceholders(profile));
        for (ChocolateUpgradeDefinition upgrade : upgrades()) {
            text.send(player, "commands.chocolate-upgrade-line", upgradePlaceholders(profile, upgrade));
        }
    }

    public boolean upgrade(Player player, String rawUpgradeId) {
        if (!enabled()) {
            text.send(player, "commands.chocolate-disabled");
            return false;
        }
        ChocolateUpgradeDefinition upgrade = upgrade(rawUpgradeId).orElse(null);
        if (upgrade == null) {
            text.send(player, "commands.chocolate-upgrade-unknown", List.of(TextService.raw("upgrade", rawUpgradeId == null ? "" : rawUpgradeId)));
            return false;
        }
        SkyBlockProfile profile = sync(player);
        int level = profile.chocolateUpgradeLevel(upgrade.id());
        if (level >= upgrade.maxLevel()) {
            text.send(player, "commands.chocolate-upgrade-maxed", upgradePlaceholders(profile, upgrade));
            return false;
        }
        double cost = upgradeCost(upgrade, level);
        if (!spendChocolate(profile, cost)) {
            text.send(player, "commands.chocolate-no-chocolate", purchasePlaceholders(profile, cost));
            return false;
        }
        profile.setChocolateUpgradeLevel(upgrade.id(), level + 1);
        profiles.save(player);
        text.send(player, "commands.chocolate-upgraded", upgradePlaceholders(profile, upgrade, text.formatNumber(cost)));
        return true;
    }

    public void sendRabbits(Player player) {
        if (!enabled()) {
            text.send(player, "commands.chocolate-disabled");
            return;
        }
        if (rabbits.isEmpty()) {
            text.send(player, "commands.chocolate-rabbits-empty");
            return;
        }
        SkyBlockProfile profile = sync(player);
        text.send(player, "commands.chocolate-rabbits-header", statusPlaceholders(profile));
        for (ChocolateRabbitDefinition rabbit : rabbits()) {
            text.send(player, "commands.chocolate-rabbit-line", rabbitPlaceholders(profile, rabbit));
        }
    }

    public boolean claimEgg(Player player) {
        if (!enabled()) {
            text.send(player, "commands.chocolate-disabled");
            return false;
        }
        if (!hoppityEnabled()) {
            text.send(player, "commands.hoppity-disabled");
            return false;
        }
        if (!eventActive()) {
            text.send(player, "commands.hoppity-inactive", List.of(TextService.raw("event", eventId)));
            return false;
        }
        SkyBlockProfile profile = sync(player);
        String claimKey = currentEggClaimKey();
        if (claimKey.equals(profile.hoppityEggClaimKey())) {
            text.send(player, "commands.hoppity-egg-already-claimed", List.of(TextService.raw("next_cycle", secondsUntilNextEggCycle())));
            return false;
        }
        ChocolateRabbitDefinition rabbit = selectEggRabbit(profile).orElse(null);
        if (rabbit == null) {
            text.send(player, "commands.chocolate-rabbits-empty");
            return false;
        }
        boolean duplicate = profile.chocolateRabbitCopies(rabbit.id()) > 0;
        double duplicateChocolate = awardRabbit(profile, rabbit);
        profile.addHoppityEggsFound(1L);
        profile.hoppityEggClaimKey(claimKey);
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(rabbitRewardPlaceholders(profile, rabbit, duplicate));
        placeholders.add(TextService.raw("egg_type", currentEggType()));
        placeholders.add(TextService.raw("duplicate_chocolate", text.formatNumber(duplicateChocolate)));
        placeholders.add(TextService.raw("eggs", text.formatNumber(profile.hoppityEggsFound())));
        text.send(player, "commands.hoppity-egg-claimed", placeholders);
        return true;
    }

    public void sendHoppityShop(Player player) {
        if (!enabled()) {
            text.send(player, "commands.chocolate-disabled");
            return;
        }
        if (!hoppityEnabled()) {
            text.send(player, "commands.hoppity-disabled");
            return;
        }
        if (!eventActive()) {
            text.send(player, "commands.hoppity-inactive", List.of(TextService.raw("event", eventId)));
            return;
        }
        SkyBlockProfile profile = sync(player);
        refreshYearlyState(profile);
        List<HoppityRabbitOffer> offers = currentOffers(profile);
        if (offers.isEmpty()) {
            text.send(player, "commands.hoppity-shop-empty");
            return;
        }
        profiles.save(player);
        text.send(player, "commands.hoppity-shop-header", statusPlaceholders(profile));
        for (HoppityRabbitOffer offer : offers) {
            text.send(player, "commands.hoppity-shop-line", offerPlaceholders(profile, offer));
        }
    }

    public boolean buyHoppityRabbit(Player player, String rawRabbitId) {
        if (!enabled()) {
            text.send(player, "commands.chocolate-disabled");
            return false;
        }
        if (!hoppityEnabled()) {
            text.send(player, "commands.hoppity-disabled");
            return false;
        }
        if (!eventActive()) {
            text.send(player, "commands.hoppity-inactive", List.of(TextService.raw("event", eventId)));
            return false;
        }
        SkyBlockProfile profile = sync(player);
        refreshYearlyState(profile);
        HoppityRabbitOffer offer = currentOffers(profile).stream()
                .filter(candidate -> candidate.rabbit().id().equalsIgnoreCase(rawRabbitId))
                .findFirst()
                .orElse(null);
        if (offer == null) {
            text.send(player, "commands.hoppity-unknown-rabbit", List.of(TextService.raw("rabbit", rawRabbitId == null ? "" : rawRabbitId)));
            return false;
        }
        if (profile.hasHoppityShopPurchase(offer.rabbit().id())) {
            text.send(player, "commands.hoppity-offer-owned", offerPlaceholders(profile, offer));
            return false;
        }
        if (profile.purse() < offer.cost() || !economy.spendPurse(player, offer.cost())) {
            text.send(player, "commands.hoppity-no-money", offerPlaceholders(profile, offer));
            return false;
        }
        boolean duplicate = profile.chocolateRabbitCopies(offer.rabbit().id()) > 0;
        double duplicateChocolate = awardRabbit(profile, offer.rabbit());
        profile.addHoppityShopPurchase(offer.rabbit().id());
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(offerPurchasePlaceholders(offer, duplicate));
        placeholders.add(TextService.raw("duplicate_chocolate", text.formatNumber(duplicateChocolate)));
        text.send(player, "commands.hoppity-offer-bought", placeholders);
        return true;
    }

    public void sendChocolateShop(Player player) {
        if (!enabled()) {
            text.send(player, "commands.chocolate-disabled");
            return;
        }
        if (shopItems.isEmpty()) {
            text.send(player, "commands.chocolate-shop-empty");
            return;
        }
        SkyBlockProfile profile = sync(player);
        refreshYearlyState(profile);
        profiles.save(player);
        text.send(player, "commands.chocolate-shop-header", statusPlaceholders(profile));
        for (ChocolateShopItemDefinition item : shopItems()) {
            text.send(player, "commands.chocolate-shop-line", shopItemPlaceholders(profile, item));
        }
    }

    public boolean buyChocolateShopItem(Player player, String rawItemId) {
        if (!enabled()) {
            text.send(player, "commands.chocolate-disabled");
            return false;
        }
        ChocolateShopItemDefinition item = shopItem(rawItemId).orElse(null);
        if (item == null) {
            text.send(player, "commands.chocolate-shop-unknown", List.of(TextService.raw("item", rawItemId == null ? "" : rawItemId)));
            return false;
        }
        SkyBlockProfile profile = sync(player);
        refreshYearlyState(profile);
        if (profile.chocolateFactoryLevel() < item.requiredFactoryLevel()) {
            text.send(player, "commands.chocolate-shop-locked", shopItemPlaceholders(profile, item));
            return false;
        }
        if (profile.chocolateShopPurchases(item.id()) >= item.annualStock()) {
            text.send(player, "commands.chocolate-shop-sold-out", shopItemPlaceholders(profile, item));
            return false;
        }
        CustomItemDefinition definition = customItems.definition(item.itemId()).orElse(null);
        if (definition == null) {
            text.send(player, "commands.chocolate-shop-missing-item", List.of(TextService.raw("item_id", item.itemId())));
            return false;
        }
        if (!spendChocolate(profile, item.cost())) {
            text.send(player, "commands.chocolate-no-chocolate", purchasePlaceholders(profile, item.cost()));
            return false;
        }
        giveCustomItem(player, definition, item.amount());
        profile.addChocolateShopPurchase(item.id(), 1);
        profiles.save(player);
        text.send(player, "commands.chocolate-shop-bought", shopItemPlaceholders(profile, item));
        return true;
    }

    public boolean prestige(Player player) {
        if (!enabled()) {
            text.send(player, "commands.chocolate-disabled");
            return false;
        }
        SkyBlockProfile profile = sync(player);
        ChocolateFactoryLevelDefinition next = factoryLevels.get(profile.chocolateFactoryLevel() + 1);
        if (next == null) {
            text.send(player, "commands.chocolate-prestige-maxed");
            return false;
        }
        if (profile.chocolateThisPrestige() < next.prestigeCost()) {
            List<TextService.TextPlaceholder> placeholders = new ArrayList<>(statusPlaceholders(profile));
            placeholders.add(TextService.raw("cost", text.formatNumber(next.prestigeCost())));
            placeholders.add(TextService.raw("earned", text.formatNumber(profile.chocolateThisPrestige())));
            placeholders.add(TextService.parsed("factory", next.displayName()));
            text.send(player, "commands.chocolate-prestige-locked", placeholders);
            return false;
        }
        profile.chocolateFactoryLevel(next.level());
        profile.chocolate(0.0D);
        profile.chocolateThisPrestige(0.0D);
        profile.chocolateUpgrades().clear();
        profile.chocolateShopPurchases().clear();
        profile.chocolateShopYear(calendar.currentDate().year());
        profile.chocolateLastAccrualMillis(System.currentTimeMillis());
        profiles.save(player);
        text.send(player, "commands.chocolate-prestiged", List.of(TextService.parsed("factory", next.displayName())));
        return true;
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        if (!enabled()) {
            return 0.0D;
        }
        return Math.max(0, profile.chocolateFactoryLevel() - 1) * skyBlockXpPerFactoryLevel
                + profile.chocolateRabbits().size() * skyBlockXpPerRabbit;
    }

    private SkyBlockProfile sync(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        if (accrue(profile)) {
            profiles.save(player);
        }
        return profile;
    }

    private boolean accrue(SkyBlockProfile profile) {
        long now = System.currentTimeMillis();
        long last = profile.chocolateLastAccrualMillis();
        if (last <= 0L || now < last) {
            profile.chocolateLastAccrualMillis(now);
            return true;
        }
        long elapsedSeconds = Math.min(maxOfflineSeconds, Math.max(0L, (now - last) / 1000L));
        if (elapsedSeconds <= 0L) {
            return false;
        }
        double generated = productionPerSecond(profile) * elapsedSeconds;
        addChocolate(profile, generated);
        profile.chocolateLastAccrualMillis(last + elapsedSeconds * 1000L);
        return true;
    }

    private double addChocolate(SkyBlockProfile profile, double amount) {
        double cappedAmount = Math.max(0.0D, Math.min(amount, Math.max(0.0D, capacity(profile) - profile.chocolate())));
        if (cappedAmount <= 0.0D) {
            return 0.0D;
        }
        profile.addChocolate(cappedAmount);
        profile.addAllTimeChocolate(cappedAmount);
        profile.addChocolateThisPrestige(cappedAmount);
        return cappedAmount;
    }

    private boolean spendChocolate(SkyBlockProfile profile, double cost) {
        double normalized = Math.max(0.0D, cost);
        if (profile.chocolate() < normalized) {
            return false;
        }
        profile.chocolate(profile.chocolate() - normalized);
        profile.addChocolateSpent(normalized);
        return true;
    }

    private double productionPerSecond(SkyBlockProfile profile) {
        double flat = baseProduction;
        double multiplier = factoryLevel(profile).productionMultiplier();
        for (ChocolateUpgradeDefinition upgrade : upgrades.values()) {
            int level = profile.chocolateUpgradeLevel(upgrade.id());
            flat += upgrade.chocolatePerSecond() * level;
            multiplier += upgrade.productionMultiplier() * level;
        }
        for (Map.Entry<String, Integer> entry : profile.chocolateRabbits().entrySet()) {
            ChocolateRabbitDefinition rabbit = rabbits.get(entry.getKey());
            if (rabbit == null) {
                continue;
            }
            flat += rabbit.chocolatePerSecond();
            multiplier += rabbit.productionMultiplier();
        }
        return Math.max(0.0D, flat * Math.max(0.0D, multiplier));
    }

    private double clickChocolate(SkyBlockProfile profile) {
        double amount = baseClickChocolate;
        for (ChocolateUpgradeDefinition upgrade : upgrades.values()) {
            amount += upgrade.clickChocolate() * profile.chocolateUpgradeLevel(upgrade.id());
        }
        return Math.max(0.0D, amount);
    }

    private double capacity(SkyBlockProfile profile) {
        double capacity = factoryLevel(profile).maxChocolate();
        for (ChocolateUpgradeDefinition upgrade : upgrades.values()) {
            capacity += upgrade.capacity() * profile.chocolateUpgradeLevel(upgrade.id());
        }
        return Math.max(1.0D, capacity);
    }

    private ChocolateFactoryLevelDefinition factoryLevel(SkyBlockProfile profile) {
        return factoryLevels.getOrDefault(profile.chocolateFactoryLevel(), fallbackFactoryLevel(profile.chocolateFactoryLevel()));
    }

    private ChocolateFactoryLevelDefinition fallbackFactoryLevel(int level) {
        return new ChocolateFactoryLevelDefinition(Math.max(1, level), "<white>Chocolate Factory " + Math.max(1, level) + "</white>", 1.0D, 50000000.0D, 0.0D);
    }

    private Optional<ChocolateUpgradeDefinition> upgrade(String rawUpgradeId) {
        if (rawUpgradeId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(upgrades.get(rawUpgradeId.toUpperCase(Locale.ROOT)));
    }

    private Optional<ChocolateShopItemDefinition> shopItem(String rawItemId) {
        if (rawItemId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(shopItems.get(rawItemId.toUpperCase(Locale.ROOT)));
    }

    private double upgradeCost(ChocolateUpgradeDefinition upgrade, int currentLevel) {
        return Math.max(0.0D, upgrade.baseCost() * Math.pow(Math.max(1.0D, upgrade.costMultiplier()), Math.max(0, currentLevel)));
    }

    private double awardRabbit(SkyBlockProfile profile, ChocolateRabbitDefinition rabbit) {
        boolean duplicate = profile.chocolateRabbitCopies(rabbit.id()) > 0;
        profile.addChocolateRabbit(rabbit.id());
        if (!duplicate) {
            return 0.0D;
        }
        double duplicateChocolate = Math.max(1.0D, productionPerSecond(profile)) * duplicateMultipliers.getOrDefault(rabbit.rarity(), 150.0D);
        return addChocolate(profile, duplicateChocolate);
    }

    private Optional<ChocolateRabbitDefinition> selectEggRabbit(SkyBlockProfile profile) {
        long nextEggCount = profile.hoppityEggsFound() + 1L;
        if (pityDivineEggs > 0 && nextEggCount % pityDivineEggs == 0L) {
            Optional<ChocolateRabbitDefinition> divine = randomUnownedRabbit(profile, Rarity.DIVINE, new Random(eggSeed(profile)));
            if (divine.isPresent()) {
                return divine;
            }
        }
        if (pityMythicEggs > 0 && nextEggCount % pityMythicEggs == 0L) {
            Optional<ChocolateRabbitDefinition> mythic = randomUnownedRabbit(profile, Rarity.MYTHIC, new Random(eggSeed(profile)));
            if (mythic.isPresent()) {
                return mythic;
            }
        }
        Random random = new Random(eggSeed(profile));
        Rarity rarity = selectRarity(eggRarityWeights, random);
        Optional<ChocolateRabbitDefinition> selected = selectRabbit(profile, rarity, random, Set.of());
        return selected.isPresent() ? selected : selectRabbit(profile, null, random, Set.of());
    }

    private Optional<ChocolateRabbitDefinition> randomUnownedRabbit(SkyBlockProfile profile, Rarity rarity, Random random) {
        List<ChocolateRabbitDefinition> candidates = rabbits().stream()
                .filter(rabbit -> rabbit.rarity() == rarity)
                .filter(rabbit -> rabbit.requiredFactoryLevel() <= profile.chocolateFactoryLevel())
                .filter(rabbit -> profile.chocolateRabbitCopies(rabbit.id()) <= 0)
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(random.nextInt(candidates.size())));
    }

    private List<HoppityRabbitOffer> currentOffers(SkyBlockProfile profile) {
        Random random = new Random((long) calendar.currentDate().year() * 341873128712L ^ profile.uniqueId().getMostSignificantBits());
        List<HoppityRabbitOffer> offers = new ArrayList<>();
        Set<String> selectedIds = new HashSet<>();
        for (int slot = 1; slot <= hoppityOfferCount; slot++) {
            Rarity rarity = selectRarity(offerRarityWeights.getOrDefault(slot, eggRarityWeights), random);
            Optional<ChocolateRabbitDefinition> selected = selectRabbit(profile, rarity, random, selectedIds);
            if (selected.isEmpty()) {
                selected = selectRabbit(profile, null, random, selectedIds);
            }
            if (selected.isEmpty()) {
                continue;
            }
            ChocolateRabbitDefinition rabbit = selected.get();
            selectedIds.add(rabbit.id());
            offers.add(new HoppityRabbitOffer(slot, rabbit, rarityCosts.getOrDefault(rabbit.rarity(), 10000.0D)));
        }
        return List.copyOf(offers);
    }

    private Optional<ChocolateRabbitDefinition> selectRabbit(SkyBlockProfile profile, Rarity rarity, Random random, Set<String> excludedIds) {
        List<ChocolateRabbitDefinition> candidates = rabbits().stream()
                .filter(rabbit -> rarity == null || rabbit.rarity() == rarity)
                .filter(rabbit -> rabbit.requiredFactoryLevel() <= profile.chocolateFactoryLevel())
                .filter(rabbit -> !excludedIds.contains(rabbit.id()))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        double totalWeight = candidates.stream().mapToDouble(ChocolateRabbitDefinition::weight).filter(weight -> weight > 0.0D).sum();
        if (totalWeight <= 0.0D) {
            return Optional.of(candidates.get(random.nextInt(candidates.size())));
        }
        double cursor = 0.0D;
        double selected = random.nextDouble(totalWeight);
        for (ChocolateRabbitDefinition rabbit : candidates) {
            cursor += Math.max(0.0D, rabbit.weight());
            if (selected <= cursor) {
                return Optional.of(rabbit);
            }
        }
        return Optional.of(candidates.get(candidates.size() - 1));
    }

    private Rarity selectRarity(Map<Rarity, Double> weights, Random random) {
        if (weights == null || weights.isEmpty()) {
            return Rarity.COMMON;
        }
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).filter(weight -> weight > 0.0D).sum();
        if (totalWeight <= 0.0D) {
            return Rarity.COMMON;
        }
        double selected = random.nextDouble(totalWeight);
        double cursor = 0.0D;
        for (Map.Entry<Rarity, Double> entry : weights.entrySet()) {
            cursor += Math.max(0.0D, entry.getValue());
            if (selected <= cursor) {
                return entry.getKey();
            }
        }
        return Rarity.COMMON;
    }

    private void refreshYearlyState(SkyBlockProfile profile) {
        int year = calendar.currentDate().year();
        if (profile.hoppityShopYear() != year) {
            profile.hoppityShopYear(year);
            profile.hoppityShopPurchases().clear();
        }
        if (profile.chocolateShopYear() != year) {
            profile.chocolateShopYear(year);
            profile.chocolateShopPurchases().clear();
        }
    }

    private String currentEggClaimKey() {
        return calendar.currentDate().year() + ":" + currentEggCycle();
    }

    private long currentEggCycle() {
        return Math.max(0L, System.currentTimeMillis() / (eggCycleSeconds * 1000L));
    }

    private long eggSeed(SkyBlockProfile profile) {
        return currentEggCycle() * 92837111L ^ profile.uniqueId().getLeastSignificantBits();
    }

    private String currentEggType() {
        if (eggTypes.isEmpty()) {
            return "CHOCOLATE";
        }
        int index = (int) Math.floorMod(currentEggCycle(), eggTypes.size());
        return eggTypes.get(index);
    }

    private String secondsUntilNextEggCycle() {
        long now = System.currentTimeMillis();
        long next = (currentEggCycle() + 1L) * eggCycleSeconds * 1000L;
        long seconds = Math.max(0L, (next - now + 999L) / 1000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        return minutes > 0L ? minutes + "m " + remainingSeconds + "s" : remainingSeconds + "s";
    }

    private void giveCustomItem(Player player, CustomItemDefinition definition, int amount) {
        int remaining = Math.max(1, amount);
        int maxStack = Math.max(1, definition.material().getMaxStackSize());
        while (remaining > 0) {
            int stackAmount = Math.min(maxStack, remaining);
            ItemStack itemStack = customItems.createItem(definition);
            itemStack.setAmount(stackAmount);
            player.getInventory().addItem(itemStack).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            remaining -= stackAmount;
        }
    }

    private List<TextService.TextPlaceholder> statusPlaceholders(SkyBlockProfile profile) {
        return List.of(
                TextService.parsed("factory", factoryLevel(profile).displayName()),
                TextService.raw("chocolate", text.formatNumber(profile.chocolate())),
                TextService.raw("capacity", text.formatNumber(capacity(profile))),
                TextService.raw("cps", text.formatNumber(productionPerSecond(profile))),
                TextService.raw("click", text.formatNumber(clickChocolate(profile))),
                TextService.raw("rabbits", Integer.toString(profile.chocolateRabbits().size())),
                TextService.raw("total_rabbits", Integer.toString(rabbits.size())),
                TextService.raw("all_time", text.formatNumber(profile.allTimeChocolate())),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile))),
                TextService.raw("year", Integer.toString(calendar.currentDate().year())),
                TextService.raw("event", eventId)
        );
    }

    private List<TextService.TextPlaceholder> purchasePlaceholders(SkyBlockProfile profile, double cost) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(statusPlaceholders(profile));
        placeholders.add(TextService.raw("cost", text.formatNumber(cost)));
        return placeholders;
    }

    private List<TextService.TextPlaceholder> upgradePlaceholders(SkyBlockProfile profile, ChocolateUpgradeDefinition upgrade) {
        int level = profile.chocolateUpgradeLevel(upgrade.id());
        return upgradePlaceholders(profile, upgrade, level >= upgrade.maxLevel() ? "MAX" : text.formatNumber(upgradeCost(upgrade, level)));
    }

    private List<TextService.TextPlaceholder> upgradePlaceholders(SkyBlockProfile profile, ChocolateUpgradeDefinition upgrade, String cost) {
        int level = profile.chocolateUpgradeLevel(upgrade.id());
        return List.of(
                TextService.raw("id", upgrade.id()),
                TextService.raw("upgrade_id", upgrade.id()),
                TextService.parsed("upgrade", upgrade.displayName()),
                TextService.raw("level", Integer.toString(level)),
                TextService.raw("max_level", Integer.toString(upgrade.maxLevel())),
                TextService.raw("cost", cost),
                TextService.raw("cps", text.formatNumber(upgrade.chocolatePerSecond() * level)),
                TextService.raw("multiplier", text.formatNumber(upgrade.productionMultiplier() * level)),
                TextService.raw("click", text.formatNumber(upgrade.clickChocolate() * level))
        );
    }

    private List<TextService.TextPlaceholder> rabbitPlaceholders(SkyBlockProfile profile, ChocolateRabbitDefinition rabbit) {
        int copies = profile.chocolateRabbitCopies(rabbit.id());
        String status = copies <= 0 ? "<red>Missing</red>" : "<green>Found x" + copies + "</green>";
        return List.of(
                TextService.raw("id", rabbit.id()),
                TextService.raw("rabbit_id", rabbit.id()),
                TextService.parsed("rabbit", rabbit.displayName()),
                TextService.parsed("rarity", rarityText(rabbit.rarity())),
                TextService.parsed("status", status),
                TextService.raw("copies", Integer.toString(copies)),
                TextService.raw("cps", text.formatNumber(rabbit.chocolatePerSecond())),
                TextService.raw("multiplier", text.formatNumber(rabbit.productionMultiplier())),
                TextService.raw("required_factory", Integer.toString(rabbit.requiredFactoryLevel()))
        );
    }

    private List<TextService.TextPlaceholder> rabbitRewardPlaceholders(SkyBlockProfile profile, ChocolateRabbitDefinition rabbit, boolean duplicate) {
        int copies = profile.chocolateRabbitCopies(rabbit.id());
        return List.of(
                TextService.raw("id", rabbit.id()),
                TextService.raw("rabbit_id", rabbit.id()),
                TextService.parsed("rabbit", rabbit.displayName()),
                TextService.parsed("rarity", rarityText(rabbit.rarity())),
                TextService.parsed("status", duplicate ? "<yellow>Duplicate</yellow>" : "<green>New</green>"),
                TextService.raw("copies", Integer.toString(copies)),
                TextService.raw("cps", text.formatNumber(rabbit.chocolatePerSecond())),
                TextService.raw("multiplier", text.formatNumber(rabbit.productionMultiplier())),
                TextService.raw("required_factory", Integer.toString(rabbit.requiredFactoryLevel()))
        );
    }

    private List<TextService.TextPlaceholder> offerPlaceholders(SkyBlockProfile profile, HoppityRabbitOffer offer) {
        ChocolateRabbitDefinition rabbit = offer.rabbit();
        return List.of(
                TextService.raw("slot", Integer.toString(offer.slot())),
                TextService.raw("id", rabbit.id()),
                TextService.raw("rabbit_id", rabbit.id()),
                TextService.parsed("rabbit", rabbit.displayName()),
                TextService.parsed("rarity", rarityText(rabbit.rarity())),
                TextService.raw("cost", text.formatNumber(offer.cost())),
                TextService.parsed("status", profile.hasHoppityShopPurchase(rabbit.id()) ? "<gray>Bought</gray>" : "<green>Available</green>")
        );
    }

    private List<TextService.TextPlaceholder> offerPurchasePlaceholders(HoppityRabbitOffer offer, boolean duplicate) {
        ChocolateRabbitDefinition rabbit = offer.rabbit();
        return List.of(
                TextService.raw("slot", Integer.toString(offer.slot())),
                TextService.raw("id", rabbit.id()),
                TextService.raw("rabbit_id", rabbit.id()),
                TextService.parsed("rabbit", rabbit.displayName()),
                TextService.parsed("rarity", rarityText(rabbit.rarity())),
                TextService.raw("cost", text.formatNumber(offer.cost())),
                TextService.parsed("status", duplicate ? "<yellow>Duplicate</yellow>" : "<green>New</green>")
        );
    }

    private String rarityText(Rarity rarity) {
        String color = rarity.colorTag();
        return color + rarity.name() + "</" + color.substring(1);
    }

    private List<TextService.TextPlaceholder> shopItemPlaceholders(SkyBlockProfile profile, ChocolateShopItemDefinition item) {
        boolean locked = profile.chocolateFactoryLevel() < item.requiredFactoryLevel();
        boolean soldOut = profile.chocolateShopPurchases(item.id()) >= item.annualStock();
        String status = locked ? "<red>Locked</red>" : soldOut ? "<gray>Sold Out</gray>" : "<green>Available</green>";
        return List.of(
                TextService.raw("id", item.id()),
                TextService.raw("item_id", item.itemId()),
                TextService.parsed("item", item.displayName()),
                TextService.raw("amount", Integer.toString(item.amount())),
                TextService.raw("cost", text.formatNumber(item.cost())),
                TextService.raw("required_factory", Integer.toString(item.requiredFactoryLevel())),
                TextService.raw("purchased", Integer.toString(profile.chocolateShopPurchases(item.id()))),
                TextService.raw("stock", Integer.toString(item.annualStock())),
                TextService.parsed("status", status)
        );
    }

    private void loadFactoryLevels() {
        factoryLevels.clear();
        ConfigurationSection section = configService.chocolateFactory().getConfigurationSection("factory-levels");
        if (section == null) {
            factoryLevels.put(1, fallbackFactoryLevel(1));
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection level = section.getConfigurationSection(key);
            if (level == null) {
                continue;
            }
            try {
                int number = Math.max(1, Integer.parseInt(key));
                factoryLevels.put(number, new ChocolateFactoryLevelDefinition(
                        number,
                        level.getString("display-name", "<white>Chocolate Factory " + number + "</white>"),
                        Math.max(0.0D, level.getDouble("production-multiplier", 1.0D)),
                        Math.max(1.0D, level.getDouble("max-chocolate", 50000000.0D)),
                        Math.max(0.0D, level.getDouble("prestige-cost", 0.0D))
                ));
            } catch (NumberFormatException ignored) {
                // Invalid factory level keys are skipped so config can be fixed without crashing startup.
            }
        }
        factoryLevels.putIfAbsent(1, fallbackFactoryLevel(1));
    }

    private void loadUpgrades() {
        upgrades.clear();
        ConfigurationSection section = configService.chocolateFactory().getConfigurationSection("upgrades");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection upgrade = section.getConfigurationSection(id);
            if (upgrade == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            upgrades.put(normalized, new ChocolateUpgradeDefinition(
                    normalized,
                    upgrade.getString("display-name", normalized),
                    Math.max(1, upgrade.getInt("max-level", 1)),
                    Math.max(0.0D, upgrade.getDouble("base-cost", 0.0D)),
                    Math.max(1.0D, upgrade.getDouble("cost-multiplier", 1.0D)),
                    Math.max(0.0D, upgrade.getDouble("chocolate-per-second", 0.0D)),
                    Math.max(0.0D, upgrade.getDouble("production-multiplier", 0.0D)),
                    Math.max(0.0D, upgrade.getDouble("click-chocolate", 0.0D)),
                    Math.max(0.0D, upgrade.getDouble("capacity", 0.0D))
            ));
        }
    }

    private void loadRabbits() {
        rabbits.clear();
        ConfigurationSection section = configService.chocolateFactory().getConfigurationSection("rabbits");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection rabbit = section.getConfigurationSection(id);
            if (rabbit == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            rabbits.put(normalized, new ChocolateRabbitDefinition(
                    normalized,
                    rabbit.getString("display-name", normalized),
                    Rarity.parse(rabbit.getString("rarity", "COMMON")),
                    Math.max(0.0D, rabbit.getDouble("weight", 1.0D)),
                    Math.max(0.0D, rabbit.getDouble("chocolate-per-second", 0.0D)),
                    Math.max(0.0D, rabbit.getDouble("production-multiplier", 0.0D)),
                    Math.max(1, rabbit.getInt("required-factory-level", 1))
            ));
        }
    }

    private void loadShopItems() {
        shopItems.clear();
        ConfigurationSection section = configService.chocolateFactory().getConfigurationSection("chocolate-shop");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(id);
            if (item == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            shopItems.put(normalized, new ChocolateShopItemDefinition(
                    normalized,
                    item.getString("display-name", normalized),
                    item.getString("item", normalized).toUpperCase(Locale.ROOT),
                    Math.max(1, item.getInt("amount", 1)),
                    Math.max(0.0D, item.getDouble("cost", 0.0D)),
                    Math.max(1, item.getInt("required-factory-level", 1)),
                    Math.max(1, item.getInt("annual-stock", 1))
            ));
        }
    }

    private void loadRarityMap(String path, Map<Rarity, Double> target) {
        target.clear();
        ConfigurationSection section = configService.chocolateFactory().getConfigurationSection(path);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            target.put(Rarity.parse(key), Math.max(0.0D, section.getDouble(key, 0.0D)));
        }
    }

    private void loadOfferRarityWeights() {
        offerRarityWeights.clear();
        ConfigurationSection section = configService.chocolateFactory().getConfigurationSection("hoppity-shop.slot-rarity-weights");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection weights = section.getConfigurationSection(key);
            if (weights == null) {
                continue;
            }
            try {
                int slot = Integer.parseInt(key);
                Map<Rarity, Double> slotWeights = new EnumMap<>(Rarity.class);
                for (String rarity : weights.getKeys(false)) {
                    slotWeights.put(Rarity.parse(rarity), Math.max(0.0D, weights.getDouble(rarity, 0.0D)));
                }
                offerRarityWeights.put(slot, slotWeights);
            } catch (NumberFormatException ignored) {
                // Invalid slot keys are ignored.
            }
        }
    }
}
