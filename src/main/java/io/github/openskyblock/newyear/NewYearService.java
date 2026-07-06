package io.github.openskyblock.newyear;

import io.github.openskyblock.calendar.CalendarService;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeSet;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class NewYearService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final CustomItemService customItems;
    private final CalendarService calendar;
    private final NamespacedKey cakeYearKey;
    private boolean requireCalendarEvent = true;
    private String eventId = "NEW_YEAR_CELEBRATION";
    private String cakeItemId = "NEW_YEAR_CAKE";
    private String cakeBagItemId = "NEW_YEAR_CAKE_BAG";
    private double cakeBagCost = 250000.0D;
    private int maxCakesInBag = 54;
    private double healthPerUniqueCake = 1.0D;
    private double skyBlockXpPerUniqueCake = 0.25D;

    public NewYearService(JavaPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, CustomItemService customItems, CalendarService calendar) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.customItems = customItems;
        this.calendar = calendar;
        this.cakeYearKey = new NamespacedKey(plugin, "new_year_cake_year");
    }

    public void reload() {
        requireCalendarEvent = configService.newYear().getBoolean("settings.require-calendar-event", true);
        eventId = configService.newYear().getString("settings.event-id", "NEW_YEAR_CELEBRATION").toUpperCase(Locale.ROOT);
        cakeItemId = configService.newYear().getString("settings.cake-item", "NEW_YEAR_CAKE").toUpperCase(Locale.ROOT);
        cakeBagItemId = configService.newYear().getString("settings.cake-bag-item", "NEW_YEAR_CAKE_BAG").toUpperCase(Locale.ROOT);
        cakeBagCost = Math.max(0.0D, configService.newYear().getDouble("settings.cake-bag-cost", 250000.0D));
        maxCakesInBag = Math.max(1, configService.newYear().getInt("settings.max-cakes-in-bag", 54));
        healthPerUniqueCake = Math.max(0.0D, configService.newYear().getDouble("settings.health-per-unique-cake", 1.0D));
        skyBlockXpPerUniqueCake = Math.max(0.0D, configService.newYear().getDouble("settings.skyblock-xp-per-unique-cake", 0.25D));
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.new-year-celebration", true);
    }

    public boolean eventActive() {
        return !requireCalendarEvent || calendar.eventActive(eventId);
    }

    public List<String> inventoryCakeYears(Player player) {
        TreeSet<Integer> years = new TreeSet<>();
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            OptionalInt year = cakeYear(itemStack);
            if (year.isPresent()) {
                years.add(year.getAsInt());
            }
        }
        return years.stream().map(Object::toString).toList();
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.new-year-disabled");
            return;
        }
        text.send(player, "commands.new-year-status", statusPlaceholders(profiles.profile(player)));
    }

    public void sendCakes(Player player) {
        if (!enabled()) {
            text.send(player, "commands.new-year-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.newYearCakeBagYears().isEmpty()) {
            text.send(player, "commands.new-year-cakes-empty");
            return;
        }
        text.send(player, "commands.new-year-cakes-header", statusPlaceholders(profile));
        profile.newYearCakeBagYears().stream()
                .sorted()
                .forEach(year -> text.send(player, "commands.new-year-cakes-line", List.of(TextService.raw("year", Integer.toString(year)))));
    }

    public boolean claimCake(Player player) {
        if (!enabled()) {
            text.send(player, "commands.new-year-disabled");
            return false;
        }
        if (!eventActive()) {
            text.send(player, "commands.new-year-inactive", List.of(TextService.raw("event", eventId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int year = calendar.currentDate().year();
        if (profile.hasClaimedNewYearCake(year)) {
            text.send(player, "commands.new-year-cake-already-claimed", List.of(TextService.raw("year", Integer.toString(year))));
            return false;
        }
        CustomItemDefinition cake = customItems.definition(cakeItemId).orElse(null);
        if (cake == null) {
            text.send(player, "commands.new-year-missing-item", List.of(TextService.raw("item", cakeItemId)));
            return false;
        }
        giveItem(player, createCakeItem(cake, year));
        profile.claimNewYearCake(year);
        profiles.save(player);
        text.send(player, "commands.new-year-cake-claimed", List.of(TextService.raw("year", Integer.toString(year))));
        return true;
    }

    public boolean buyBag(Player player) {
        if (!enabled()) {
            text.send(player, "commands.new-year-disabled");
            return false;
        }
        if (!eventActive()) {
            text.send(player, "commands.new-year-inactive", List.of(TextService.raw("event", eventId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.newYearCakeBagOwned()) {
            text.send(player, "commands.new-year-bag-owned");
            return false;
        }
        CustomItemDefinition bag = customItems.definition(cakeBagItemId).orElse(null);
        if (bag == null) {
            text.send(player, "commands.new-year-missing-item", List.of(TextService.raw("item", cakeBagItemId)));
            return false;
        }
        if (profile.purse() < cakeBagCost || !economy.spendPurse(player, cakeBagCost)) {
            text.send(player, "commands.new-year-no-money", statusPlaceholders(profile));
            return false;
        }
        profile.newYearCakeBagOwned(true);
        giveItem(player, customItems.createItem(bag));
        profiles.save(player);
        text.send(player, "commands.new-year-bag-bought", statusPlaceholders(profile));
        return true;
    }

    public boolean storeCake(Player player, Integer requestedYear) {
        if (!enabled()) {
            text.send(player, "commands.new-year-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (!profile.newYearCakeBagOwned()) {
            text.send(player, "commands.new-year-bag-required");
            return false;
        }
        if (storedCakeCount(profile) >= maxCakesInBag) {
            text.send(player, "commands.new-year-bag-full", statusPlaceholders(profile));
            return false;
        }
        Integer year = requestedYear == null ? firstInventoryCakeYear(player, profile).orElse(null) : requestedYear;
        if (year == null) {
            text.send(player, "commands.new-year-cake-missing");
            return false;
        }
        if (profile.hasNewYearCakeInBag(year)) {
            text.send(player, "commands.new-year-cake-duplicate", List.of(TextService.raw("year", Integer.toString(year))));
            return false;
        }
        int slot = findCakeSlot(player, year);
        if (slot < 0) {
            text.send(player, "commands.new-year-cake-year-missing", List.of(TextService.raw("year", Integer.toString(year))));
            return false;
        }
        removeOne(player, slot);
        profile.storeNewYearCake(year);
        profiles.save(player);
        text.send(player, "commands.new-year-cake-stored", statusPlaceholders(profile, year));
        return true;
    }

    public Map<String, Double> activeStats(SkyBlockProfile profile) {
        double health = healthBonus(profile);
        return health <= 0.0D ? Map.of() : Map.of("health", health);
    }

    public double healthBonus(SkyBlockProfile profile) {
        if (!enabled() || !profile.newYearCakeBagOwned()) {
            return 0.0D;
        }
        return storedCakeCount(profile) * healthPerUniqueCake;
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        if (!enabled() || !profile.newYearCakeBagOwned()) {
            return 0.0D;
        }
        return storedCakeCount(profile) * skyBlockXpPerUniqueCake;
    }

    private ItemStack createCakeItem(CustomItemDefinition cake, int year) {
        ItemStack itemStack = customItems.createItem(cake);
        ItemMeta meta = itemStack.getItemMeta();
        meta.getPersistentDataContainer().set(cakeYearKey, PersistentDataType.INTEGER, year);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(text.deserialize("<gray>SkyBlock Year <year></gray>", List.of(TextService.raw("year", Integer.toString(year)))));
        meta.lore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private Optional<Integer> firstInventoryCakeYear(Player player, SkyBlockProfile profile) {
        Integer duplicateFallback = null;
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            OptionalInt year = cakeYear(itemStack);
            if (year.isEmpty()) {
                continue;
            }
            int value = year.getAsInt();
            if (!profile.hasNewYearCakeInBag(value)) {
                return Optional.of(value);
            }
            if (duplicateFallback == null) {
                duplicateFallback = value;
            }
        }
        return Optional.ofNullable(duplicateFallback);
    }

    private int findCakeSlot(Player player, int year) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack itemStack = player.getInventory().getItem(slot);
            OptionalInt cakeYear = cakeYear(itemStack);
            if (cakeYear.isPresent() && cakeYear.getAsInt() == year) {
                return slot;
            }
        }
        return -1;
    }

    private OptionalInt cakeYear(ItemStack itemStack) {
        if (!matchesCake(itemStack)) {
            return OptionalInt.empty();
        }
        Integer year = itemStack.getItemMeta().getPersistentDataContainer().get(cakeYearKey, PersistentDataType.INTEGER);
        return year == null || year <= 0 ? OptionalInt.empty() : OptionalInt.of(year);
    }

    private boolean matchesCake(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return false;
        }
        return customItems.definition(itemStack)
                .map(CustomItemDefinition::id)
                .map(id -> id.equalsIgnoreCase(cakeItemId))
                .orElse(false);
    }

    private void removeOne(Player player, int slot) {
        ItemStack itemStack = player.getInventory().getItem(slot);
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        itemStack.setAmount(itemStack.getAmount() - 1);
        if (itemStack.getAmount() <= 0) {
            player.getInventory().setItem(slot, null);
        }
    }

    private void giveItem(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private int storedCakeCount(SkyBlockProfile profile) {
        return Math.min(maxCakesInBag, profile.newYearCakeBagYears().size());
    }

    private List<TextService.TextPlaceholder> statusPlaceholders(SkyBlockProfile profile) {
        int year = calendar.currentDate().year();
        return statusPlaceholders(profile, year);
    }

    private List<TextService.TextPlaceholder> statusPlaceholders(SkyBlockProfile profile, int displayYear) {
        int year = calendar.currentDate().year();
        return List.of(
                TextService.parsed("active", eventActive() ? "<green>Active</green>" : "<red>Inactive</red>"),
                TextService.raw("event", eventId),
                TextService.raw("year", Integer.toString(displayYear)),
                TextService.parsed("cake_status", profile.hasClaimedNewYearCake(year) ? "<green>Claimed</green>" : "<yellow>Available</yellow>"),
                TextService.parsed("bag_status", profile.newYearCakeBagOwned() ? "<green>Owned</green>" : "<red>Missing</red>"),
                TextService.raw("cakes", Integer.toString(storedCakeCount(profile))),
                TextService.raw("max_cakes", Integer.toString(maxCakesInBag)),
                TextService.raw("cost", text.formatNumber(cakeBagCost)),
                TextService.raw("health", text.formatNumber(healthBonus(profile))),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile)))
        );
    }
}
