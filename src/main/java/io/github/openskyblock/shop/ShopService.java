package io.github.openskyblock.shop;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

public final class ShopService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final Map<String, ShopDefinition> shops = new HashMap<>();

    public ShopService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
    }

    public void reload() {
        shops.clear();
        ConfigurationSection section = configService.shops().getConfigurationSection("shops");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection shopSection = section.getConfigurationSection(id);
            if (shopSection == null) {
                continue;
            }
            Material material = Material.matchMaterial(shopSection.getString("material", "EMERALD"));
            shops.put(id.toUpperCase(Locale.ROOT), new ShopDefinition(
                    id.toUpperCase(Locale.ROOT),
                    shopSection.getString("display-name", id),
                    material == null ? Material.EMERALD : material,
                    Math.max(1, Math.min(6, shopSection.getInt("rows", 4))),
                    loadItems(shopSection.getConfigurationSection("items"))
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.npc-shops", true);
    }

    public Optional<ShopDefinition> shop(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(shops.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<ShopItemDefinition> item(String shopId, String itemId) {
        return shop(shopId).flatMap(shop -> shop.items().stream()
                .filter(item -> item.id().equalsIgnoreCase(itemId))
                .findFirst());
    }

    public List<ShopDefinition> shops() {
        return shops.values().stream()
                .sorted(Comparator.comparing(ShopDefinition::id))
                .toList();
    }

    public boolean buy(Player player, ShopDefinition shop, ShopItemDefinition item) {
        if (!enabled()) {
            text.send(player, "commands.shop-disabled");
            return false;
        }
        resetPurchasesIfNeeded(profiles.profile(player));
        int remaining = remainingLimit(player, shop, item);
        if (item.dailyBuyLimit() > 0 && remaining < item.amount()) {
            text.send(player, "commands.shop-limit", List.of(TextService.parsed("item", item.displayName())));
            return false;
        }
        if (!economy.spendPurse(player, item.buyPrice())) {
            text.send(player, "commands.shop-no-money");
            return false;
        }
        giveOrDrop(player, new ItemStack(item.material(), item.amount()));
        profiles.profile(player).addDailyShopPurchases(purchaseKey(shop, item), item.amount());
        text.send(player, "commands.shop-buy", List.of(
                TextService.raw("amount", Integer.toString(item.amount())),
                TextService.parsed("item", item.displayName()),
                TextService.raw("price", text.formatNumber(item.buyPrice()))
        ));
        return true;
    }

    public int sellMatching(Player player, ShopItemDefinition item) {
        if (!enabled()) {
            text.send(player, "commands.shop-disabled");
            return 0;
        }
        if (item.sellPrice() <= 0.0D) {
            text.send(player, "commands.shop-not-sellable");
            return 0;
        }
        int sold = removeMatching(player, item.material(), Integer.MAX_VALUE);
        if (sold <= 0) {
            text.send(player, "commands.shop-no-items");
            return 0;
        }
        double coins = sold * item.sellPrice();
        economy.addPurse(player, coins);
        text.send(player, "commands.shop-sell", List.of(
                TextService.raw("amount", Integer.toString(sold)),
                TextService.parsed("item", item.displayName()),
                TextService.raw("price", text.formatNumber(coins))
        ));
        return sold;
    }

    public int sellHand(Player player) {
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        if (itemStack.getType().isAir()) {
            text.send(player, "commands.shop-no-items");
            return 0;
        }
        ShopItemDefinition definition = sellDefinition(itemStack.getType()).orElse(null);
        if (definition == null) {
            text.send(player, "commands.shop-not-sellable");
            return 0;
        }
        int amount = itemStack.getAmount();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        double coins = amount * definition.sellPrice();
        economy.addPurse(player, coins);
        text.send(player, "commands.shop-sell", List.of(
                TextService.raw("amount", Integer.toString(amount)),
                TextService.parsed("item", definition.displayName()),
                TextService.raw("price", text.formatNumber(coins))
        ));
        return amount;
    }

    public int sellAll(Player player) {
        int sold = 0;
        double coins = 0.0D;
        for (ShopItemDefinition definition : sellableItems()) {
            int amount = removeMatching(player, definition.material(), Integer.MAX_VALUE);
            if (amount <= 0) {
                continue;
            }
            sold += amount;
            coins += amount * definition.sellPrice();
        }
        if (sold <= 0) {
            text.send(player, "commands.shop-no-items");
            return 0;
        }
        economy.addPurse(player, coins);
        text.send(player, "commands.shop-sell", List.of(
                TextService.raw("amount", Integer.toString(sold)),
                TextService.parsed("item", text.rawMessage("commands.shop-all-items")),
                TextService.raw("price", text.formatNumber(coins))
        ));
        return sold;
    }

    public int remainingLimit(Player player, ShopDefinition shop, ShopItemDefinition item) {
        if (item.dailyBuyLimit() <= 0) {
            return Integer.MAX_VALUE;
        }
        SkyBlockProfile profile = profiles.profile(player);
        resetPurchasesIfNeeded(profile);
        int purchased = profile.dailyShopPurchases(purchaseKey(shop, item));
        return Math.max(0, item.dailyBuyLimit() - purchased);
    }

    public String limitText(Player player, ShopDefinition shop, ShopItemDefinition item) {
        if (item.dailyBuyLimit() <= 0) {
            return text.rawMessage("menus.shop-unlimited");
        }
        return text.formatNumber(remainingLimit(player, shop, item));
    }

    private List<ShopItemDefinition> loadItems(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        return section.getKeys(false).stream()
                .map(id -> loadItem(id.toUpperCase(Locale.ROOT), section.getConfigurationSection(id)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparingInt(ShopItemDefinition::slot))
                .toList();
    }

    private Optional<ShopItemDefinition> loadItem(String id, ConfigurationSection section) {
        if (section == null) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(section.getString("material", id));
        if (material == null) {
            return Optional.empty();
        }
        return Optional.of(new ShopItemDefinition(
                id,
                section.getInt("slot", 0),
                material,
                section.getString("display-name", id),
                Math.max(1, section.getInt("amount", 1)),
                Math.max(0.0D, section.getDouble("buy-price", 0.0D)),
                Math.max(0.0D, section.getDouble("sell-price", 0.0D)),
                section.getInt("daily-buy-limit", 0)
        ));
    }

    private Optional<ShopItemDefinition> sellDefinition(Material material) {
        return sellableItems().stream()
                .filter(item -> item.material() == material)
                .findFirst();
    }

    private List<ShopItemDefinition> sellableItems() {
        return shops.values().stream()
                .flatMap(shop -> shop.items().stream())
                .filter(item -> item.sellPrice() > 0.0D)
                .toList();
    }

    private int removeMatching(Player player, Material material, int maxAmount) {
        int remaining = maxAmount;
        int removed = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack itemStack = contents[index];
            if (itemStack == null || itemStack.getType() != material) {
                continue;
            }
            int next = Math.min(remaining, itemStack.getAmount());
            itemStack.setAmount(itemStack.getAmount() - next);
            if (itemStack.getAmount() <= 0) {
                contents[index] = null;
            }
            remaining -= next;
            removed += next;
        }
        player.getInventory().setStorageContents(contents);
        return removed;
    }

    private void giveOrDrop(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void resetPurchasesIfNeeded(SkyBlockProfile profile) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        if (today.equals(profile.shopPurchaseDay())) {
            return;
        }
        profile.shopPurchaseDay(today);
        profile.clearDailyShopPurchases();
    }

    private String purchaseKey(ShopDefinition shop, ShopItemDefinition item) {
        return shop.id() + "|" + item.id();
    }
}
