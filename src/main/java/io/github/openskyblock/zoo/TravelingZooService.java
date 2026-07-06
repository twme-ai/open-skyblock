package io.github.openskyblock.zoo;

import io.github.openskyblock.calendar.CalendarService;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.pet.PetDefinition;
import io.github.openskyblock.pet.PetService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class TravelingZooService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final PetService pets;
    private final CustomItemService customItems;
    private final CalendarService calendar;
    private final Map<String, ZooOfferDefinition> offers = new HashMap<>();
    private boolean requireCalendarEvent = true;
    private String eventId = "TRAVELING_ZOO";
    private int offerCount = 3;
    private int rotationStride = 1;

    public TravelingZooService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, PetService pets, CustomItemService customItems, CalendarService calendar) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.pets = pets;
        this.customItems = customItems;
        this.calendar = calendar;
    }

    public void reload() {
        requireCalendarEvent = configService.travelingZoo().getBoolean("settings.require-calendar-event", true);
        eventId = configService.travelingZoo().getString("settings.event-id", "TRAVELING_ZOO").toUpperCase(Locale.ROOT);
        offerCount = Math.max(1, configService.travelingZoo().getInt("settings.offer-count", 3));
        rotationStride = Math.max(1, configService.travelingZoo().getInt("settings.rotation-stride", 1));
        loadOffers();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.traveling-zoo", true);
    }

    public boolean eventActive() {
        return !requireCalendarEvent || calendar.eventActive(eventId);
    }

    public List<String> offerIds() {
        return offers().stream().map(ZooOfferDefinition::id).toList();
    }

    public List<String> activeOfferIds() {
        return activeOffers().stream().map(ZooOfferDefinition::id).toList();
    }

    public List<ZooOfferDefinition> offers() {
        return offers.values().stream()
                .sorted(Comparator.comparing(ZooOfferDefinition::id))
                .toList();
    }

    public List<ZooOfferDefinition> activeOffers() {
        List<ZooOfferDefinition> ordered = offers();
        if (ordered.isEmpty()) {
            return List.of();
        }
        int count = Math.min(offerCount, ordered.size());
        int start = Math.floorMod((calendar.currentDate().year() - 1) * rotationStride, ordered.size());
        List<ZooOfferDefinition> active = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            active.add(ordered.get((start + index) % ordered.size()));
        }
        return List.copyOf(active);
    }

    public Optional<ZooOfferDefinition> offer(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(offers.get(id.toUpperCase(Locale.ROOT)));
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.zoo-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.zoo-status", List.of(
                TextService.parsed("active", eventActive() ? "<green>Active</green>" : "<red>Inactive</red>"),
                TextService.raw("event", eventId),
                TextService.raw("offers", Integer.toString(activeOffers().size())),
                TextService.raw("purchases", text.formatNumber(total(profile.zooPurchases()))),
                TextService.raw("pet_score", text.formatNumber(pets.score(profile))),
                TextService.raw("skyblock_xp", text.formatNumber(pets.scoreSkyBlockXp(profile)))
        ));
    }

    public void sendOffers(Player player) {
        if (!enabled()) {
            text.send(player, "commands.zoo-disabled");
            return;
        }
        if (!eventActive()) {
            text.send(player, "commands.zoo-inactive", List.of(TextService.raw("event", eventId)));
            return;
        }
        List<ZooOfferDefinition> active = activeOffers();
        if (active.isEmpty()) {
            text.send(player, "commands.zoo-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.zoo-offer-header");
        for (ZooOfferDefinition offer : active) {
            text.send(player, "commands.zoo-offer-line", offerPlaceholders(profile, offer));
        }
    }

    public boolean buy(Player player, String offerId) {
        if (!enabled()) {
            text.send(player, "commands.zoo-disabled");
            return false;
        }
        if (!pets.enabled()) {
            text.send(player, "commands.pet-disabled");
            return false;
        }
        if (!eventActive()) {
            text.send(player, "commands.zoo-inactive", List.of(TextService.raw("event", eventId)));
            return false;
        }
        ZooOfferDefinition offer = offer(offerId).orElse(null);
        if (offer == null) {
            text.send(player, "commands.zoo-unknown-offer", List.of(TextService.raw("offer", offerId == null ? "" : offerId)));
            return false;
        }
        boolean currentlyOffered = activeOffers().stream().anyMatch(active -> active.id().equals(offer.id()));
        if (!currentlyOffered) {
            text.send(player, "commands.zoo-not-offered", offerPlaceholders(profiles.profile(player), offer));
            return false;
        }
        PetDefinition pet = pets.definition(offer.petId()).orElse(null);
        if (pet == null) {
            text.send(player, "commands.zoo-missing-pet", offerPlaceholders(profiles.profile(player), offer));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.purse() < offer.coins()) {
            text.send(player, "commands.zoo-no-money", offerPlaceholders(profile, offer));
            return false;
        }
        List<String> missingItems = missingItems(player, offer.itemCosts());
        if (!missingItems.isEmpty()) {
            List<TextService.TextPlaceholder> placeholders = new ArrayList<>(offerPlaceholders(profile, offer));
            placeholders.add(TextService.parsed("missing_items", String.join("<gray>, </gray>", missingItems)));
            text.send(player, "commands.zoo-missing-items", placeholders);
            return false;
        }
        if (!economy.spendPurse(player, offer.coins())) {
            text.send(player, "commands.zoo-no-money", offerPlaceholders(profile, offer));
            return false;
        }
        removeItems(player, offer.itemCosts());
        pets.addPet(profile, pet);
        profile.addZooPurchase(pet.id(), 1);
        profiles.save(player);
        pets.refreshCosmeticPet(player);
        text.send(player, "commands.zoo-purchased", offerPlaceholders(profile, offer));
        return true;
    }

    private void loadOffers() {
        offers.clear();
        ConfigurationSection section = configService.travelingZoo().getConfigurationSection("offers");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection offer = section.getConfigurationSection(id);
            if (offer == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            offers.put(normalized, new ZooOfferDefinition(
                    normalized,
                    offer.getString("display-name", normalized),
                    offer.getString("pet", normalized).toUpperCase(Locale.ROOT),
                    Math.max(0.0D, offer.getDouble("coins", 0.0D)),
                    readItemCosts(offer.getMapList("required-items"))
            ));
        }
    }

    private List<ZooCostItemDefinition> readItemCosts(List<Map<?, ?>> rawCosts) {
        List<ZooCostItemDefinition> costs = new ArrayList<>();
        for (Map<?, ?> rawCost : rawCosts) {
            String type = string(rawCost.get("type"), "VANILLA").toUpperCase(Locale.ROOT);
            int amount = Math.max(1, integer(rawCost.get("amount"), 1));
            if (type.equals("CUSTOM_ITEM")) {
                String itemId = string(rawCost.get("item"), "").toUpperCase(Locale.ROOT);
                if (!itemId.isBlank()) {
                    costs.add(new ZooCostItemDefinition("CUSTOM_ITEM", itemId, Material.STONE, amount));
                }
                continue;
            }
            Material material = Material.matchMaterial(string(rawCost.get("material"), "DIRT"));
            if (material != null && !material.isAir()) {
                costs.add(new ZooCostItemDefinition("VANILLA", "", material, amount));
            }
        }
        return List.copyOf(costs);
    }

    private List<String> missingItems(Player player, List<ZooCostItemDefinition> itemCosts) {
        List<String> missing = new ArrayList<>();
        for (ZooCostItemDefinition cost : itemCosts) {
            int held = countItem(player, cost);
            if (held < cost.amount()) {
                missing.add("<yellow>" + (cost.amount() - held) + "x</yellow> " + costName(cost));
            }
        }
        return missing;
    }

    private int countItem(Player player, ZooCostItemDefinition cost) {
        int amount = 0;
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            if (matches(itemStack, cost)) {
                amount += itemStack.getAmount();
            }
        }
        return amount;
    }

    private void removeItems(Player player, List<ZooCostItemDefinition> itemCosts) {
        for (ZooCostItemDefinition cost : itemCosts) {
            int remaining = cost.amount();
            for (int slot = 0; slot < player.getInventory().getSize() && remaining > 0; slot++) {
                ItemStack itemStack = player.getInventory().getItem(slot);
                if (!matches(itemStack, cost)) {
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
    }

    private boolean matches(ItemStack itemStack, ZooCostItemDefinition cost) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        if (cost.customItem()) {
            return customItems.definition(itemStack)
                    .map(CustomItemDefinition::id)
                    .map(id -> id.equalsIgnoreCase(cost.itemId()))
                    .orElse(false);
        }
        return itemStack.getType() == cost.material() && customItems.definition(itemStack).isEmpty();
    }

    private List<TextService.TextPlaceholder> offerPlaceholders(SkyBlockProfile profile, ZooOfferDefinition offer) {
        PetDefinition pet = pets.definition(offer.petId()).orElse(null);
        String petName = pet == null ? offer.petId() : pet.displayName();
        String rarity = pet == null ? "UNKNOWN" : pet.rarity().name();
        return List.of(
                TextService.raw("id", offer.id()),
                TextService.raw("offer", offer.id()),
                TextService.parsed("display", offer.displayName()),
                TextService.parsed("pet", petName),
                TextService.raw("pet_id", offer.petId()),
                TextService.raw("rarity", rarity),
                TextService.raw("coins", text.formatNumber(offer.coins())),
                TextService.parsed("costs", itemCosts(offer.itemCosts())),
                TextService.raw("purchases", Integer.toString(profile.zooPurchases(offer.petId())))
        );
    }

    private String itemCosts(List<ZooCostItemDefinition> costs) {
        if (costs.isEmpty()) {
            return text.rawMessage("zoo.no-item-costs");
        }
        return String.join("<gray>, </gray>", costs.stream()
                .map(cost -> "<yellow>" + cost.amount() + "x</yellow> " + costName(cost))
                .toList());
    }

    private String costName(ZooCostItemDefinition cost) {
        if (cost.customItem()) {
            return customItems.definition(cost.itemId())
                    .map(CustomItemDefinition::displayName)
                    .orElse(cost.itemId());
        }
        return readableMaterial(cost.material());
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
}
