package io.github.openskyblock.menu;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.recipe.SkyBlockRecipe;
import io.github.openskyblock.service.CollectionDefinition;
import io.github.openskyblock.service.CollectionTier;
import io.github.openskyblock.service.MinionPlacement;
import io.github.openskyblock.shop.ShopDefinition;
import io.github.openskyblock.shop.ShopItemDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MenuService {
    private static final String BACK_ACTION = "__BACK__";

    private final OpenSkyBlockPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;

    public MenuService(OpenSkyBlockPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
    }

    public void reload() {
        // Menu data is read live from menus.yml so reload only exists for service symmetry.
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.skyblock-menu", true);
    }

    public void openSkyBlockMenu(Player player) {
        if (!enabled()) {
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("skyblock");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 4)));
        int size = rows * 9;
        Map<Integer, MenuAction> actions = new HashMap<>();
        SkyBlockMenuHolder holder = new SkyBlockMenuHolder(actions);
        Inventory inventory = Bukkit.createInventory(holder, size, text.deserialize(section.getString("title", "<dark_gray>SkyBlock Menu</dark_gray>")));
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) {
                    continue;
                }
                int slot = item.getInt("slot", -1);
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                inventory.setItem(slot, item(player, item));
                actions.put(slot, MenuAction.parse(item.getString("action", "NONE")));
            }
        }
        player.openInventory(inventory);
    }

    public void openMinionMenu(Player player, MinionPlacement placement) {
        ConfigurationSection section = configService.menus().getConfigurationSection("minion");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 3)));
        int size = rows * 9;
        Map<Integer, MinionMenuAction> actions = new HashMap<>();
        MinionMenuHolder holder = new MinionMenuHolder(placement.profile().uniqueId(), placement.slot(), actions);
        List<TextService.TextPlaceholder> placeholders = plugin.minions()
                .minionPlaceholders(placement.definition(), placement.placedMinion().generatedAmount());
        Inventory inventory = Bukkit.createInventory(
                holder,
                size,
                text.deserialize(section.getString("title", "<dark_gray><minion_name></dark_gray>"), placeholders)
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) {
                    continue;
                }
                int slot = item.getInt("slot", -1);
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                inventory.setItem(slot, item(item, placeholders));
                actions.put(slot, MinionMenuAction.parse(item.getString("action", "NONE")));
            }
        }
        player.openInventory(inventory);
    }

    public void openCollectionBrowser(Player player, int requestedPage) {
        ConfigurationSection section = configService.menus().getConfigurationSection("collection-browser");
        if (section == null) {
            return;
        }
        List<CollectionDefinition> collections = plugin.collections().definitions();
        List<Integer> contentSlots = contentSlots(section);
        int maxPage = maxPage(collections.size(), contentSlots.size());
        int page = clampPage(requestedPage, maxPage);
        Map<Integer, BrowserMenuAction> actions = new HashMap<>();
        BrowserMenuHolder holder = new BrowserMenuHolder(BrowserMenuType.COLLECTIONS, page, maxPage, actions);
        Inventory inventory = browserInventory(holder, section, page, maxPage);
        fill(inventory, section.getConfigurationSection("filler"));

        SkyBlockProfile profile = profiles.profile(player);
        int offset = page * contentSlots.size();
        for (int index = 0; index < contentSlots.size(); index++) {
            int collectionIndex = offset + index;
            if (collectionIndex >= collections.size()) {
                break;
            }
            CollectionDefinition definition = collections.get(collectionIndex);
            inventory.setItem(contentSlots.get(index), collectionItem(profile, definition, section.getConfigurationSection("collection-item")));
        }
        addBrowserNavigation(inventory, section, page, maxPage, actions);
        player.openInventory(inventory);
    }

    public void openRecipeBook(Player player, int requestedPage) {
        ConfigurationSection section = configService.menus().getConfigurationSection("recipe-book");
        if (section == null) {
            return;
        }
        List<SkyBlockRecipe> recipes = plugin.recipes().recipes();
        List<Integer> contentSlots = contentSlots(section);
        int maxPage = maxPage(recipes.size(), contentSlots.size());
        int page = clampPage(requestedPage, maxPage);
        Map<Integer, BrowserMenuAction> actions = new HashMap<>();
        BrowserMenuHolder holder = new BrowserMenuHolder(BrowserMenuType.RECIPES, page, maxPage, actions);
        Inventory inventory = browserInventory(holder, section, page, maxPage);
        fill(inventory, section.getConfigurationSection("filler"));

        int offset = page * contentSlots.size();
        for (int index = 0; index < contentSlots.size(); index++) {
            int recipeIndex = offset + index;
            if (recipeIndex >= recipes.size()) {
                break;
            }
            SkyBlockRecipe recipe = recipes.get(recipeIndex);
            inventory.setItem(contentSlots.get(index), recipeItem(player, recipe, section.getConfigurationSection("recipe-item")));
        }
        addBrowserNavigation(inventory, section, page, maxPage, actions);
        player.openInventory(inventory);
    }

    public void openBankMenu(Player player) {
        if (!plugin.economy().bankEnabled()) {
            text.send(player, "commands.bank-disabled");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("bank");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 3)));
        Map<Integer, BankMenuAction> actions = new HashMap<>();
        BankMenuHolder holder = new BankMenuHolder(actions);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>Bank</dark_gray>"), bankPlaceholders(player))
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) {
                    continue;
                }
                int slot = item.getInt("slot", -1);
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                inventory.setItem(slot, item(item, bankPlaceholders(player)));
                actions.put(slot, BankMenuAction.parse(item.getString("action", "NONE")));
            }
        }
        player.openInventory(inventory);
    }

    public void openShopSelector(Player player) {
        if (!plugin.shops().enabled()) {
            text.send(player, "commands.shop-disabled");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("shop-selector");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 3)));
        Map<Integer, String> shopsBySlot = new HashMap<>();
        ShopSelectorHolder holder = new ShopSelectorHolder(shopsBySlot);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>NPC Shops</dark_gray>"))
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        List<Integer> slots = contentSlots(section);
        List<ShopDefinition> shops = plugin.shops().shops();
        for (int index = 0; index < slots.size() && index < shops.size(); index++) {
            ShopDefinition shop = shops.get(index);
            inventory.setItem(slots.get(index), shopSelectorItem(shop, section.getConfigurationSection("shop-item")));
            shopsBySlot.put(slots.get(index), shop.id());
        }
        ConfigurationSection back = section.getConfigurationSection("back");
        if (back != null) {
            int slot = back.getInt("slot", -1);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item(back, List.of()));
                shopsBySlot.put(slot, BACK_ACTION);
            }
        }
        player.openInventory(inventory);
    }

    public void openShop(Player player, ShopDefinition shop) {
        if (!plugin.shops().enabled()) {
            text.send(player, "commands.shop-disabled");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("shop");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, shop.rows()));
        Map<Integer, String> itemsBySlot = new HashMap<>();
        ShopMenuHolder holder = new ShopMenuHolder(shop.id(), itemsBySlot);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray><shop></dark_gray>"), shopPlaceholders(shop))
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        for (ShopItemDefinition item : shop.items()) {
            if (item.slot() < 0 || item.slot() >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(item.slot(), shopItem(player, shop, item));
            itemsBySlot.put(item.slot(), item.id());
        }
        ConfigurationSection back = section.getConfigurationSection("back");
        if (back != null) {
            int slot = back.getInt("slot", -1);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item(back, List.of()));
                itemsBySlot.put(slot, BACK_ACTION);
            }
        }
        player.openInventory(inventory);
    }

    public void runAction(Player player, MenuAction action) {
        switch (action) {
            case PROFILE -> player.performCommand("skyblock profile");
            case ISLAND_HOME -> player.performCommand("skyblock island home");
            case BANK -> openBankMenu(player);
            case SKILLS -> player.performCommand("skyblock skills");
            case STATS -> player.performCommand("skyblock stats");
            case COLLECTIONS -> openCollectionBrowser(player, 0);
            case RECIPES -> openRecipeBook(player, 0);
            case SHOPS -> openShopSelector(player);
            case MINIONS -> player.performCommand("skyblock minion list");
            case NONE -> {
            }
        }
    }

    public void runShopSelectorClick(Player player, ShopSelectorHolder holder, int rawSlot) {
        String shopId = holder.shopId(rawSlot);
        if (shopId == null) {
            return;
        }
        if (BACK_ACTION.equals(shopId)) {
            openSkyBlockMenu(player);
            return;
        }
        ShopDefinition shop = plugin.shops().shop(shopId).orElse(null);
        if (shop == null) {
            text.send(player, "commands.shop-unknown", List.of(TextService.raw("shop", shopId)));
            return;
        }
        openShop(player, shop);
    }

    public void runShopMenuClick(Player player, ShopMenuHolder holder, int rawSlot, boolean sellClick) {
        String itemId = holder.itemId(rawSlot);
        if (itemId == null) {
            return;
        }
        if (BACK_ACTION.equals(itemId)) {
            openShopSelector(player);
            return;
        }
        ShopDefinition shop = plugin.shops().shop(holder.shopId()).orElse(null);
        ShopItemDefinition item = plugin.shops().item(holder.shopId(), itemId).orElse(null);
        if (shop == null || item == null) {
            text.send(player, "commands.shop-unknown", List.of(TextService.raw("shop", holder.shopId())));
            return;
        }
        if (sellClick) {
            plugin.shops().sellMatching(player, item);
        } else {
            plugin.shops().buy(player, shop, item);
        }
        openShop(player, shop);
    }

    public void runBankAction(Player player, BankMenuAction action) {
        switch (action) {
            case DEPOSIT_ALL -> {
                plugin.economy().depositAll(player);
                openBankMenu(player);
            }
            case WITHDRAW_ALL -> {
                plugin.economy().withdrawAll(player);
                openBankMenu(player);
            }
            case NONE -> {
            }
        }
    }

    public void runMinionAction(Player player, MinionMenuHolder holder, MinionMenuAction action) {
        MinionPlacement placement = plugin.minions().placement(holder.ownerId(), holder.slot()).orElse(null);
        if (placement == null) {
            text.send(player, "commands.minion-not-found");
            player.closeInventory();
            return;
        }
        switch (action) {
            case CLAIM -> {
                long claimed = plugin.minions().claim(player, placement);
                sendClaimResult(player, claimed);
                player.closeInventory();
            }
            case PICKUP -> {
                plugin.minions().pickup(player, placement);
                player.closeInventory();
            }
            case NONE -> {
            }
        }
    }

    public void runBrowserAction(Player player, BrowserMenuHolder holder, BrowserMenuAction action) {
        switch (action) {
            case PREVIOUS_PAGE -> openBrowser(player, holder.type(), holder.page() - 1);
            case NEXT_PAGE -> openBrowser(player, holder.type(), holder.page() + 1);
            case BACK -> openSkyBlockMenu(player);
            case NONE -> {
            }
        }
    }

    private void fill(Inventory inventory, ConfigurationSection filler) {
        if (filler == null || !filler.getBoolean("enabled", false)) {
            return;
        }
        ItemStack itemStack = item(null, filler);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, itemStack);
        }
    }

    private ItemStack item(Player player, ConfigurationSection section) {
        return item(section, placeholders(player));
    }

    private ItemStack item(ConfigurationSection section, List<TextService.TextPlaceholder> placeholders) {
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        ItemStack itemStack = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(text.deserialize(section.getString("display-name", "<white>Item</white>"), placeholders));
        meta.lore(section.getStringList("lore").stream()
                .map(line -> text.deserialize(line, placeholders))
                .toList());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private Inventory browserInventory(BrowserMenuHolder holder, ConfigurationSection section, int page, int maxPage) {
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 6)));
        List<TextService.TextPlaceholder> placeholders = List.of(
                TextService.raw("page", Integer.toString(page + 1)),
                TextService.raw("max_page", Integer.toString(maxPage + 1))
        );
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>Menu</dark_gray>"), placeholders)
        );
        holder.inventory(inventory);
        return inventory;
    }

    private void openBrowser(Player player, BrowserMenuType type, int page) {
        switch (type) {
            case COLLECTIONS -> openCollectionBrowser(player, page);
            case RECIPES -> openRecipeBook(player, page);
        }
    }

    private List<Integer> contentSlots(ConfigurationSection section) {
        List<Integer> slots = section.getIntegerList("content-slots");
        if (!slots.isEmpty()) {
            return slots;
        }
        List<Integer> fallback = new ArrayList<>();
        int size = Math.max(9, Math.min(54, section.getInt("rows", 6) * 9));
        for (int slot = 0; slot < size; slot++) {
            fallback.add(slot);
        }
        return fallback;
    }

    private void addBrowserNavigation(Inventory inventory, ConfigurationSection section, int page, int maxPage, Map<Integer, BrowserMenuAction> actions) {
        if (page > 0) {
            addNavigationItem(inventory, section.getConfigurationSection("previous"), BrowserMenuAction.PREVIOUS_PAGE, actions);
        }
        addNavigationItem(inventory, section.getConfigurationSection("back"), BrowserMenuAction.BACK, actions);
        if (page < maxPage) {
            addNavigationItem(inventory, section.getConfigurationSection("next"), BrowserMenuAction.NEXT_PAGE, actions);
        }
    }

    private void addNavigationItem(Inventory inventory, ConfigurationSection section, BrowserMenuAction action, Map<Integer, BrowserMenuAction> actions) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item(section, List.of()));
        actions.put(slot, action);
    }

    private ItemStack collectionItem(SkyBlockProfile profile, CollectionDefinition definition, ConfigurationSection section) {
        Material material = definition.material();
        ItemStack itemStack = new ItemStack(material == null ? Material.CHEST : material);
        ItemMeta meta = itemStack.getItemMeta();
        long amount = profile.collectionAmount(definition.id());
        int tier = plugin.collections().tier(definition, amount);
        List<TextService.TextPlaceholder> placeholders = List.of(
                TextService.parsed("collection", definition.displayName()),
                TextService.raw("amount", text.formatNumber(amount)),
                TextService.raw("tier", Integer.toString(tier)),
                TextService.parsed("next_tier", nextTierText(definition, amount))
        );
        meta.displayName(text.deserialize(section.getString("display-name", "<collection>"), placeholders));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            if (line.equals("<rewards>")) {
                appendCollectionRewards(lore, definition, rewardTier(definition, amount, tier));
            } else {
                lore.add(text.deserialize(line, placeholders));
            }
        }
        meta.lore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private void appendCollectionRewards(List<net.kyori.adventure.text.Component> lore, CollectionDefinition definition, int tier) {
        CollectionTier collectionTier = definition.tiers().get(tier);
        if (collectionTier == null || collectionTier.rewards().isEmpty()) {
            lore.add(text.deserialize(text.rawMessage("menus.collection-no-rewards")));
            return;
        }
        for (String reward : collectionTier.rewards()) {
            lore.add(text.deserialize(reward));
        }
    }

    private int rewardTier(CollectionDefinition definition, long amount, int currentTier) {
        for (Integer tierNumber : definition.sortedTierNumbers()) {
            CollectionTier tier = definition.tiers().get(tierNumber);
            if (tier != null && amount < tier.amount()) {
                return tier.tier();
            }
        }
        return currentTier;
    }

    private String nextTierText(CollectionDefinition definition, long amount) {
        for (Integer tierNumber : definition.sortedTierNumbers()) {
            CollectionTier tier = definition.tiers().get(tierNumber);
            if (tier != null && amount < tier.amount()) {
                return text.rawMessage("menus.collection-next-tier")
                        .replace("<tier>", Integer.toString(tier.tier()))
                        .replace("<amount>", text.formatNumber(tier.amount()));
            }
        }
        return text.rawMessage("menus.collection-maxed");
    }

    private ItemStack recipeItem(Player player, SkyBlockRecipe recipe, ConfigurationSection section) {
        boolean unlocked = plugin.recipes().canCraft(player, recipe);
        ItemStack itemStack = unlocked ? recipe.result().clone() : new ItemStack(lockedRecipeMaterial(section));
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = List.of(
                TextService.parsed("recipe", recipe.displayName()),
                TextService.parsed("status", text.rawMessage(unlocked ? "commands.recipe-unlocked" : "commands.recipe-locked")),
                TextService.parsed("requirement", plugin.recipes().requirementText(recipe)),
                TextService.parsed("result", text.rawMessage("menus.recipe-result")
                        .replace("<amount>", Integer.toString(recipe.result().getAmount()))
                        .replace("<item>", recipe.displayName()))
        );
        meta.displayName(text.deserialize(section.getString("display-name", "<recipe>"), placeholders));
        meta.lore(section.getStringList("lore").stream()
                .map(line -> text.deserialize(line, placeholders))
                .toList());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private ItemStack shopSelectorItem(ShopDefinition shop, ConfigurationSection section) {
        ItemStack itemStack = new ItemStack(shop.material());
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = shopPlaceholders(shop);
        meta.displayName(text.deserialize(section.getString("display-name", "<shop>"), placeholders));
        meta.lore(section.getStringList("lore").stream()
                .map(line -> text.deserialize(line, placeholders))
                .toList());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private ItemStack shopItem(Player player, ShopDefinition shop, ShopItemDefinition item) {
        ItemStack itemStack = new ItemStack(item.material(), Math.max(1, item.amount()));
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = shopItemPlaceholders(player, shop, item);
        meta.displayName(text.deserialize(item.displayName(), placeholders));
        meta.lore(configService.messages().getStringList("menus.shop-item").stream()
                .map(line -> text.deserialize(line, placeholders))
                .toList());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private Material lockedRecipeMaterial(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("locked-material", "BARRIER"));
        return material == null ? Material.BARRIER : material;
    }

    private int maxPage(int itemCount, int pageSize) {
        if (itemCount <= 0 || pageSize <= 0) {
            return 0;
        }
        return Math.max(0, (itemCount - 1) / pageSize);
    }

    private int clampPage(int page, int maxPage) {
        return Math.max(0, Math.min(maxPage, page));
    }

    private void sendClaimResult(Player player, long claimed) {
        if (claimed <= 0L) {
            text.send(player, "commands.minion-nothing");
            return;
        }
        text.send(player, "commands.minion-claimed", List.of(TextService.raw("amount", text.formatNumber(claimed))));
    }

    private List<TextService.TextPlaceholder> placeholders(Player player) {
        if (player == null) {
            return List.of();
        }
        SkyBlockProfile profile = profiles.profile(player);
        return List.of(
                TextService.raw("player", profile.playerName()),
                TextService.raw("level", Integer.toString(plugin.skills().skyBlockLevel(profile))),
                TextService.raw("purse", text.formatNumber(profile.purse())),
                TextService.raw("bank", text.formatNumber(profile.bank()))
        );
    }

    private List<TextService.TextPlaceholder> bankPlaceholders(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        return List.of(
                TextService.raw("purse", text.formatNumber(profile.purse())),
                TextService.raw("bank", text.formatNumber(profile.bank())),
                TextService.raw("capacity", text.formatNumber(plugin.economy().bankCapacity()))
        );
    }

    private List<TextService.TextPlaceholder> shopPlaceholders(ShopDefinition shop) {
        return List.of(
                TextService.raw("shop_id", shop.id()),
                TextService.parsed("shop", shop.displayName())
        );
    }

    private List<TextService.TextPlaceholder> shopItemPlaceholders(Player player, ShopDefinition shop, ShopItemDefinition item) {
        return List.of(
                TextService.raw("shop_id", shop.id()),
                TextService.parsed("shop", shop.displayName()),
                TextService.raw("item_id", item.id()),
                TextService.parsed("item", item.displayName()),
                TextService.raw("amount", Integer.toString(item.amount())),
                TextService.raw("buy_price", text.formatNumber(item.buyPrice())),
                TextService.raw("sell_price", text.formatNumber(item.sellPrice())),
                TextService.raw("limit", item.dailyBuyLimit() <= 0 ? text.rawMessage("menus.shop-unlimited") : text.formatNumber(item.dailyBuyLimit())),
                TextService.raw("limit_remaining", plugin.shops().limitText(player, shop, item))
        );
    }
}
