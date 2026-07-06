package io.github.openskyblock.star;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.stats.StatSnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class StarService {
    private final ConfigService configService;
    private final TextService text;
    private final EconomyService economy;
    private final CustomItemService customItems;
    private final NamespacedKey starKey;

    public StarService(JavaPlugin plugin, ConfigService configService, TextService text, EconomyService economy, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.economy = economy;
        this.customItems = customItems;
        this.starKey = new NamespacedKey(plugin, "stars");
    }

    public void reload() {
        // Star settings are read live from stars.yml.
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.item-stars", true);
    }

    public int stars(ItemStack itemStack) {
        if (!enabled() || itemStack == null || !itemStack.hasItemMeta()) {
            return 0;
        }
        Integer stars = itemStack.getItemMeta().getPersistentDataContainer().get(starKey, PersistentDataType.INTEGER);
        return Math.max(0, stars == null ? 0 : stars);
    }

    public int maxStars(CustomItemDefinition definition) {
        String category = definition.category().toUpperCase(Locale.ROOT);
        if (!allowed(category)) {
            return 0;
        }
        int categoryMax = configService.stars().getInt("settings.category-max-stars." + category, -1);
        if (categoryMax >= 0) {
            return Math.max(0, categoryMax);
        }
        return Math.max(0, configService.stars().getInt("settings.default-max-stars", 5));
    }

    public double multiplierPerStar() {
        return Math.max(0.0D, configService.stars().getDouble("settings.stat-multiplier-per-star", 0.02D));
    }

    public Map<String, Double> bonusStats(ItemStack itemStack, Map<String, Double> preStarStats) {
        int stars = stars(itemStack);
        if (stars <= 0 || preStarStats.isEmpty()) {
            return Map.of();
        }
        double multiplier = multiplierPerStar() * stars;
        Map<String, Double> bonuses = new HashMap<>();
        for (Map.Entry<String, Double> entry : preStarStats.entrySet()) {
            String stat = StatSnapshot.normalize(entry.getKey());
            double value = entry.getValue() * multiplier;
            if (Math.abs(value) > 0.000001D) {
                bonuses.put(stat, bonuses.getOrDefault(stat, 0.0D) + value);
            }
        }
        return bonuses;
    }

    public boolean addHeld(Player player, int amount) {
        if (amount <= 0) {
            text.send(player, "errors.invalid-number");
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition definition = customItems.definition(held).orElse(null);
        if (definition == null) {
            text.send(player, "commands.star-held-missing");
            return false;
        }
        return setStars(player, held, definition, stars(held) + amount);
    }

    public boolean setHeld(Player player, int requestedStars) {
        if (requestedStars < 0) {
            text.send(player, "errors.invalid-number");
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition definition = customItems.definition(held).orElse(null);
        if (definition == null) {
            text.send(player, "commands.star-held-missing");
            return false;
        }
        return setStars(player, held, definition, requestedStars);
    }

    public boolean clearHeld(Player player) {
        if (!enabled()) {
            text.send(player, "commands.star-disabled");
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition definition = customItems.definition(held).orElse(null);
        if (definition == null) {
            text.send(player, "commands.star-held-missing");
            return false;
        }
        int current = stars(held);
        if (current <= 0) {
            text.send(player, "commands.star-none");
            return false;
        }
        ItemMeta meta = held.getItemMeta();
        meta.getPersistentDataContainer().remove(starKey);
        held.setItemMeta(meta);
        customItems.refreshItem(held);
        text.send(player, "commands.star-cleared", placeholders(definition, 0, 0.0D));
        return true;
    }

    public void sendInfo(CommandSender sender) {
        if (!enabled()) {
            text.send(sender, "commands.star-disabled");
            return;
        }
        text.send(sender, "commands.star-info-header");
        ConfigurationSection categories = configService.stars().getConfigurationSection("settings.category-max-stars");
        if (categories == null) {
            text.send(sender, "commands.star-info-line", List.of(
                    TextService.raw("category", "ALL"),
                    TextService.raw("max_stars", Integer.toString(configService.stars().getInt("settings.default-max-stars", 5))),
                    TextService.raw("bonus", text.formatNumber(multiplierPerStar() * 100.0D))
            ));
            return;
        }
        for (String category : categories.getKeys(false)) {
            text.send(sender, "commands.star-info-line", List.of(
                    TextService.raw("category", category.toUpperCase(Locale.ROOT)),
                    TextService.raw("max_stars", Integer.toString(Math.max(0, categories.getInt(category, 0)))),
                    TextService.raw("bonus", text.formatNumber(multiplierPerStar() * 100.0D))
            ));
        }
    }

    public List<Component> lore(ItemStack itemStack, CustomItemDefinition definition) {
        int stars = stars(itemStack);
        if (stars <= 0) {
            return List.of();
        }
        double percent = stars * multiplierPerStar() * 100.0D;
        return List.of(text.message("items.star-line", List.of(
                TextService.raw("stars", Integer.toString(stars)),
                TextService.parsed("star_symbols", symbols(stars)),
                TextService.raw("bonus", text.formatNumber(percent)),
                TextService.raw("max_stars", Integer.toString(maxStars(definition)))
        )));
    }

    public String suffix(ItemStack itemStack) {
        int stars = stars(itemStack);
        if (stars <= 0) {
            return "";
        }
        return text.rawMessage("stars.display-suffix")
                .replace("<star_symbols>", symbols(stars))
                .replace("<stars>", Integer.toString(stars));
    }

    private boolean setStars(Player player, ItemStack itemStack, CustomItemDefinition definition, int requestedStars) {
        if (!enabled()) {
            text.send(player, "commands.star-disabled");
            return false;
        }
        int maxStars = maxStars(definition);
        if (maxStars <= 0) {
            text.send(player, "commands.star-not-applicable", placeholders(definition, 0, 0.0D));
            return false;
        }
        int targetStars = Math.max(0, Math.min(maxStars, requestedStars));
        int currentStars = stars(itemStack);
        if (targetStars == currentStars) {
            text.send(player, "commands.star-already", placeholders(definition, targetStars, 0.0D));
            return false;
        }
        double cost = cost(definition, currentStars, targetStars);
        if (!economy.spendPurse(player, cost)) {
            text.send(player, "commands.star-no-money", placeholders(definition, targetStars, cost));
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (targetStars <= 0) {
            meta.getPersistentDataContainer().remove(starKey);
        } else {
            meta.getPersistentDataContainer().set(starKey, PersistentDataType.INTEGER, targetStars);
        }
        itemStack.setItemMeta(meta);
        customItems.refreshItem(itemStack);
        text.send(player, "commands.star-set", placeholders(definition, targetStars, cost));
        return true;
    }

    private double cost(CustomItemDefinition definition, int currentStars, int targetStars) {
        if (targetStars <= currentStars) {
            return Math.max(0.0D, configService.stars().getDouble("settings.remove-cost", 0.0D));
        }
        double base = Math.max(0.0D, configService.stars().getDouble("settings.base-costs." + definition.rarity().name(), 0.0D));
        double total = 0.0D;
        for (int star = currentStars + 1; star <= targetStars; star++) {
            double multiplier = configService.stars().getDouble("settings.cost-multiplier-by-star." + star, star);
            total += base * Math.max(0.0D, multiplier);
        }
        return total;
    }

    private List<TextService.TextPlaceholder> placeholders(CustomItemDefinition definition, int stars, double cost) {
        double percent = stars * multiplierPerStar() * 100.0D;
        return List.of(
                TextService.parsed("item", definition.displayName()),
                TextService.raw("stars", Integer.toString(stars)),
                TextService.parsed("star_symbols", symbols(stars)),
                TextService.raw("max_stars", Integer.toString(maxStars(definition))),
                TextService.raw("bonus", text.formatNumber(percent)),
                TextService.raw("cost", text.formatNumber(cost))
        );
    }

    private boolean allowed(String category) {
        List<String> allowedCategories = configService.stars().getStringList("settings.allowed-categories");
        if (allowedCategories.isEmpty()) {
            return true;
        }
        return allowedCategories.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(category) || allowed.equalsIgnoreCase("ALL"));
    }

    private String symbols(int stars) {
        String symbol = configService.stars().getString("settings.star-symbol", "*");
        return symbol.repeat(Math.max(0, stars));
    }
}
