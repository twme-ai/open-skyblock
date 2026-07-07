package io.github.openskyblock.menu;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.auction.AuctionListing;
import io.github.openskyblock.bazaar.BazaarProductDefinition;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.enchant.SkyBlockEnchantmentDefinition;
import io.github.openskyblock.equipment.EquipmentSlotDefinition;
import io.github.openskyblock.pet.PetDefinition;
import io.github.openskyblock.profile.OwnedPet;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.quest.QuestDefinition;
import io.github.openskyblock.quiver.QuiverItemDefinition;
import io.github.openskyblock.recipe.SkyBlockRecipe;
import io.github.openskyblock.reforge.ReforgeDefinition;
import io.github.openskyblock.sack.SackDefinition;
import io.github.openskyblock.sack.SackItemDefinition;
import io.github.openskyblock.service.CollectionDefinition;
import io.github.openskyblock.service.CollectionTier;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.MinionClaimResult;
import io.github.openskyblock.service.MinionPlacement;
import io.github.openskyblock.service.SkillDefinition;
import io.github.openskyblock.shop.ShopDefinition;
import io.github.openskyblock.shop.ShopItemDefinition;
import io.github.openskyblock.stats.StatSnapshot;
import io.github.openskyblock.trade.TradeSession;
import io.github.openskyblock.wardrobe.WardrobeSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    public void openProfileViewer(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        ConfigurationSection section = configService.menus().getConfigurationSection("profile-viewer");
        if (section == null) {
            text.send(player, "commands.profile-summary", List.of(
                    TextService.raw("player", profile.playerName()),
                    TextService.raw("level", Integer.toString(plugin.skills().skyBlockLevel(profile))),
                    TextService.raw("purse", text.formatNumber(profile.purse())),
                    TextService.raw("bank", text.formatNumber(profile.bank()))
            ));
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 4)));
        Map<Integer, ProfileMenuAction> actions = new HashMap<>();
        ProfileMenuHolder holder = new ProfileMenuHolder(actions);
        List<TextService.TextPlaceholder> placeholders = profileViewerPlaceholders(player);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray><player>'s Profile</dark_gray>"), placeholders)
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
                actions.put(slot, ProfileMenuAction.parse(item.getString("action", "NONE")));
            }
        }
        player.openInventory(inventory);
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

    public void openIslandManagement(Player player) {
        if (!plugin.islands().enabled()) {
            text.send(player, "commands.island-disabled");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("island-management");
        if (section == null) {
            plugin.islands().sendInfo(player);
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 3)));
        Map<Integer, IslandMenuAction> actions = new HashMap<>();
        IslandMenuHolder holder = new IslandMenuHolder(actions);
        List<TextService.TextPlaceholder> placeholders = plugin.islands().islandPlaceholders(player);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>Island Management</dark_gray>"), placeholders)
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
                actions.put(slot, IslandMenuAction.parse(item.getString("action", "NONE")));
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
                .minionPlaceholders(placement.definition(), placement.placedMinion());
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

    public void openQuestLog(Player player, int requestedPage) {
        if (!plugin.quests().enabled()) {
            text.send(player, "commands.quest-log-disabled");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("quest-log");
        if (section == null) {
            plugin.quests().sendSummary(player);
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        List<QuestDefinition> quests = plugin.quests().definitions();
        if (quests.isEmpty()) {
            text.send(player, "commands.quest-log-empty");
            return;
        }
        List<Integer> contentSlots = contentSlots(section);
        int maxPage = maxPage(quests.size(), contentSlots.size());
        int page = clampPage(requestedPage, maxPage);
        Map<Integer, BrowserMenuAction> actions = new HashMap<>();
        Map<Integer, String> entries = new HashMap<>();
        BrowserMenuHolder holder = new BrowserMenuHolder(BrowserMenuType.QUEST_LOG, page, maxPage, actions, entries);
        Inventory inventory = browserInventory(holder, section, page, maxPage);
        fill(inventory, section.getConfigurationSection("filler"));

        addQuestSummary(inventory, section.getConfigurationSection("summary"), profile);
        int offset = page * contentSlots.size();
        for (int index = 0; index < contentSlots.size(); index++) {
            int questIndex = offset + index;
            if (questIndex >= quests.size()) {
                break;
            }
            QuestDefinition definition = quests.get(questIndex);
            int slot = contentSlots.get(index);
            inventory.setItem(slot, questItem(profile, definition, section.getConfigurationSection("quest-item")));
            entries.put(slot, definition.id());
        }
        addBrowserNavigation(inventory, section, page, maxPage, actions);
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

    public void openSkillMenu(Player player, int requestedPage) {
        ConfigurationSection section = configService.menus().getConfigurationSection("skill-menu");
        if (section == null) {
            sendSkillSummary(player);
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        List<SkillDefinition> skills = plugin.skills().definitions();
        List<Integer> contentSlots = contentSlots(section);
        int maxPage = maxPage(skills.size(), contentSlots.size());
        int page = clampPage(requestedPage, maxPage);
        Map<Integer, BrowserMenuAction> actions = new HashMap<>();
        BrowserMenuHolder holder = new BrowserMenuHolder(BrowserMenuType.SKILLS, page, maxPage, actions);
        Inventory inventory = browserInventory(holder, section, page, maxPage);
        fill(inventory, section.getConfigurationSection("filler"));

        addSkillSummary(inventory, section.getConfigurationSection("summary"), profile);
        int offset = page * contentSlots.size();
        for (int index = 0; index < contentSlots.size(); index++) {
            int skillIndex = offset + index;
            if (skillIndex >= skills.size()) {
                break;
            }
            SkillDefinition definition = skills.get(skillIndex);
            inventory.setItem(contentSlots.get(index), skillItem(profile, definition, section.getConfigurationSection("skill-item")));
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

    public void openAuctionHouse(Player player, int requestedPage) {
        ConfigurationSection section = configService.menus().getConfigurationSection("auction-browser");
        if (section == null) {
            plugin.auctions().sendListings(player, requestedPage + 1);
            return;
        }
        List<AuctionListing> listings = plugin.auctions().activeListings();
        List<Integer> contentSlots = contentSlots(section);
        int maxPage = maxPage(listings.size(), contentSlots.size());
        int page = clampPage(requestedPage, maxPage);
        Map<Integer, BrowserMenuAction> actions = new HashMap<>();
        Map<Integer, String> entries = new HashMap<>();
        BrowserMenuHolder holder = new BrowserMenuHolder(BrowserMenuType.AUCTIONS, page, maxPage, actions, entries);
        Inventory inventory = browserInventory(holder, section, page, maxPage);
        fill(inventory, section.getConfigurationSection("filler"));

        int offset = page * contentSlots.size();
        for (int index = 0; index < contentSlots.size(); index++) {
            int listingIndex = offset + index;
            if (listingIndex >= listings.size()) {
                break;
            }
            AuctionListing listing = listings.get(listingIndex);
            int slot = contentSlots.get(index);
            inventory.setItem(slot, auctionItem(listing, section.getConfigurationSection("listing-item")));
            entries.put(slot, listing.id());
        }
        addBrowserNavigation(inventory, section, page, maxPage, actions);
        player.openInventory(inventory);
    }

    public void openBazaarMenu(Player player, int requestedPage) {
        if (!plugin.bazaar().enabled()) {
            text.send(player, "commands.bazaar-disabled");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("bazaar-browser");
        if (section == null) {
            plugin.bazaar().sendProducts(player, requestedPage + 1);
            return;
        }
        List<BazaarProductDefinition> products = plugin.bazaar().products();
        List<Integer> contentSlots = contentSlots(section);
        int maxPage = maxPage(products.size(), contentSlots.size());
        int page = clampPage(requestedPage, maxPage);
        Map<Integer, BrowserMenuAction> actions = new HashMap<>();
        Map<Integer, String> entries = new HashMap<>();
        BrowserMenuHolder holder = new BrowserMenuHolder(BrowserMenuType.BAZAAR, page, maxPage, actions, entries);
        Inventory inventory = browserInventory(holder, section, page, maxPage);
        fill(inventory, section.getConfigurationSection("filler"));

        int offset = page * contentSlots.size();
        for (int index = 0; index < contentSlots.size(); index++) {
            int productIndex = offset + index;
            if (productIndex >= products.size()) {
                break;
            }
            BazaarProductDefinition product = products.get(productIndex);
            int slot = contentSlots.get(index);
            inventory.setItem(slot, bazaarProductItem(product, section.getConfigurationSection("product-item")));
            entries.put(slot, product.id());
        }
        addBrowserNavigation(inventory, section, page, maxPage, actions);
        player.openInventory(inventory);
    }

    public void openTradeMenu(Player player) {
        if (!plugin.trades().enabled()) {
            text.send(player, "commands.trade-disabled");
            return;
        }
        TradeSession session = plugin.trades().session(player).orElse(null);
        if (session == null) {
            text.send(player, "commands.trade-no-session");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("trade");
        if (section == null) {
            plugin.trades().status(player);
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 6)));
        Map<Integer, TradeMenuAction> actions = new HashMap<>();
        Map<Integer, Integer> offeredItemIndexes = new HashMap<>();
        TradeMenuHolder holder = new TradeMenuHolder(player.getUniqueId(), actions, offeredItemIndexes);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>Trade with <partner></dark_gray>"), tradePlaceholders(player, session))
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));

        addTradeItems(inventory, section.getIntegerList("your-slots"), section.getConfigurationSection("your-item"), session.items(player.getUniqueId()), offeredItemIndexes, true);
        addTradeItems(inventory, section.getIntegerList("partner-slots"), section.getConfigurationSection("partner-item"), session.items(session.partnerId(player.getUniqueId())), offeredItemIndexes, false);
        addTradeSummary(inventory, section.getConfigurationSection("your-summary"), player, session, true);
        addTradeSummary(inventory, section.getConfigurationSection("partner-summary"), player, session, false);
        addTradeAction(inventory, section.getConfigurationSection("offer-hand"), TradeMenuAction.OFFER_HAND, actions, player, session);
        addTradeAction(inventory, section.getConfigurationSection("ready"), TradeMenuAction.READY, actions, player, session);
        addTradeAction(inventory, section.getConfigurationSection("confirm"), TradeMenuAction.CONFIRM, actions, player, session);
        addTradeAction(inventory, section.getConfigurationSection("status"), TradeMenuAction.STATUS, actions, player, session);
        addTradeAction(inventory, section.getConfigurationSection("cancel"), TradeMenuAction.CANCEL, actions, player, session);
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

    public void openAccessoryBag(Player player) {
        if (!plugin.accessories().enabled()) {
            text.send(player, "commands.accessory-bag-disabled");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("accessory-bag");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 6)));
        Map<Integer, AccessoryBagAction> actions = new HashMap<>();
        Map<Integer, String> accessoriesBySlot = new HashMap<>();
        AccessoryBagHolder holder = new AccessoryBagHolder(actions, accessoriesBySlot);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>Accessory Bag</dark_gray>"), accessoryBagPlaceholders(player))
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        addAccessorySummary(inventory, section.getConfigurationSection("summary"), player);
        List<Integer> slots = contentSlots(section);
        SkyBlockProfile profile = profiles.profile(player);
        for (int index = 0; index < profile.accessoryBag().size() && index < slots.size(); index++) {
            String itemId = profile.accessoryBag().get(index);
            int slot = slots.get(index);
            plugin.customItems().definition(itemId).ifPresent(definition -> {
                inventory.setItem(slot, accessoryBagItem(definition));
                accessoriesBySlot.put(slot, definition.id());
            });
        }
        addAccessoryAction(inventory, section.getConfigurationSection("add-held"), AccessoryBagAction.ADD_HELD, actions);
        addAccessoryAction(inventory, section.getConfigurationSection("back"), AccessoryBagAction.BACK, actions);
        player.openInventory(inventory);
    }

    public void openEquipmentMenu(Player player) {
        if (!plugin.equipment().enabled()) {
            text.send(player, "commands.equipment-disabled");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("equipment");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 4)));
        Map<Integer, String> slotsByInventorySlot = new HashMap<>();
        Map<Integer, EquipmentAction> actions = new HashMap<>();
        EquipmentHolder holder = new EquipmentHolder(slotsByInventorySlot, actions);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>Equipment</dark_gray>"), equipmentPlaceholders(player))
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        SkyBlockProfile profile = profiles.profile(player);
        ConfigurationSection slotsSection = section.getConfigurationSection("slots");
        if (slotsSection != null) {
            for (EquipmentSlotDefinition slot : plugin.equipment().slots()) {
                ConfigurationSection slotSection = slotsSection.getConfigurationSection(slot.id());
                if (slotSection == null) {
                    continue;
                }
                int inventorySlot = slotSection.getInt("slot", -1);
                if (inventorySlot < 0 || inventorySlot >= inventory.getSize()) {
                    continue;
                }
                ItemStack equipped = profile.equipment().get(slot.id());
                inventory.setItem(inventorySlot, equipped == null ? emptyEquipmentItem(slot, slotSection) : equippedEquipmentItem(slot, equipped));
                slotsByInventorySlot.put(inventorySlot, slot.id());
            }
        }
        addEquipmentAction(inventory, section.getConfigurationSection("back"), EquipmentAction.BACK, actions);
        player.openInventory(inventory);
    }

    public void openWardrobeMenu(Player player) {
        if (!plugin.wardrobe().enabled()) {
            text.send(player, "commands.wardrobe-disabled");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("wardrobe");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 6)));
        Map<Integer, Integer> wardrobeSlots = new HashMap<>();
        Map<Integer, WardrobeAction> actions = new HashMap<>();
        WardrobeHolder holder = new WardrobeHolder(wardrobeSlots, actions);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>Wardrobe</dark_gray>"), wardrobePlaceholders(player))
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        SkyBlockProfile profile = profiles.profile(player);
        List<Integer> contentSlots = contentSlots(section);
        int maxSlots = Math.min(plugin.wardrobe().slotCount(), contentSlots.size());
        for (int index = 0; index < maxSlots; index++) {
            int wardrobeSlot = index + 1;
            int inventorySlot = contentSlots.get(index);
            if (inventorySlot < 0 || inventorySlot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(inventorySlot, wardrobeSlotItem(
                    wardrobeSlot,
                    profile.wardrobe().get(wardrobeSlot),
                    section.getConfigurationSection("empty-slot"),
                    section.getConfigurationSection("stored-slot")
            ));
            wardrobeSlots.put(inventorySlot, wardrobeSlot);
        }
        addWardrobeAction(inventory, section.getConfigurationSection("back"), WardrobeAction.BACK, actions);
        player.openInventory(inventory);
    }

    public void openTuningMenu(Player player) {
        if (!plugin.tuning().enabled()) {
            text.send(player, "commands.tuning-disabled");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("tuning");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 4)));
        Map<Integer, String> statSlots = new HashMap<>();
        Map<Integer, TuningAction> actions = new HashMap<>();
        TuningHolder holder = new TuningHolder(statSlots, actions);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>Stats Tuning</dark_gray>"))
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        addTuningSummary(inventory, section.getConfigurationSection("summary"), player);
        ConfigurationSection stats = section.getConfigurationSection("stats");
        if (stats != null) {
            for (String stat : stats.getKeys(false)) {
                ConfigurationSection statSection = stats.getConfigurationSection(stat);
                if (statSection == null || !plugin.tuning().isTunable(stat)) {
                    continue;
                }
                int slot = statSection.getInt("slot", -1);
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                inventory.setItem(slot, tuningItem(player, stat, statSection));
                statSlots.put(slot, stat);
            }
        }
        addTuningAction(inventory, section.getConfigurationSection("reset"), TuningAction.RESET, actions);
        addTuningAction(inventory, section.getConfigurationSection("back"), TuningAction.BACK, actions);
        player.openInventory(inventory);
    }

    public void openPetMenu(Player player) {
        if (!plugin.pets().enabled()) {
            text.send(player, "commands.pet-disabled");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("pets");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 6)));
        Map<Integer, Integer> petsBySlot = new HashMap<>();
        Map<Integer, PetMenuAction> actions = new HashMap<>();
        PetMenuHolder holder = new PetMenuHolder(petsBySlot, actions);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>Pets</dark_gray>"), petMenuPlaceholders(player))
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        addPetSummary(inventory, section.getConfigurationSection("summary"), player);
        List<Integer> slots = contentSlots(section);
        SkyBlockProfile profile = profiles.profile(player);
        for (int index = 0; index < profile.pets().size() && index < slots.size(); index++) {
            OwnedPet pet = profile.pets().get(index);
            PetDefinition definition = plugin.pets().definition(pet.petId()).orElse(null);
            if (definition == null) {
                continue;
            }
            int slot = slots.get(index);
            inventory.setItem(slot, petMenuItem(profile, pet, definition, section.getConfigurationSection("pet-item")));
            petsBySlot.put(slot, index);
        }
        addPetAction(inventory, section.getConfigurationSection("back"), PetMenuAction.BACK, actions);
        player.openInventory(inventory);
    }

    public void openReforgeAnvil(Player player) {
        if (!plugin.reforges().enabled()) {
            text.send(player, "commands.reforge-disabled");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition itemDefinition = plugin.customItems().definition(held).orElse(null);
        if (itemDefinition == null) {
            text.send(player, "commands.reforge-held-missing");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("reforge-anvil");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 6)));
        Map<Integer, String> reforgesBySlot = new HashMap<>();
        Map<Integer, ReforgeAnvilAction> actions = new HashMap<>();
        ReforgeAnvilHolder holder = new ReforgeAnvilHolder(reforgesBySlot, actions);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>Reforge Anvil</dark_gray>"), reforgeMenuPlaceholders(held, itemDefinition))
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        addReforgePreview(inventory, section.getConfigurationSection("preview"), held, itemDefinition);

        List<Integer> slots = contentSlots(section);
        List<ReforgeDefinition> reforges = plugin.reforges().applicableDefinitions(itemDefinition);
        for (int index = 0; index < reforges.size() && index < slots.size(); index++) {
            int slot = slots.get(index);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            ReforgeDefinition reforge = reforges.get(index);
            inventory.setItem(slot, reforgeMenuItem(player, held, itemDefinition, reforge, section.getConfigurationSection("reforge-item")));
            reforgesBySlot.put(slot, reforge.id());
        }
        addReforgeAction(inventory, section.getConfigurationSection("remove"), ReforgeAnvilAction.REMOVE, actions);
        addReforgeAction(inventory, section.getConfigurationSection("back"), ReforgeAnvilAction.BACK, actions);
        player.openInventory(inventory);
    }

    public void openEnchantingTable(Player player) {
        if (!plugin.enchantments().enabled()) {
            text.send(player, "commands.enchantment-disabled");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition itemDefinition = plugin.customItems().definition(held).orElse(null);
        if (itemDefinition == null) {
            text.send(player, "commands.enchantment-held-missing");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("enchanting-table");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 6)));
        Map<Integer, String> enchantmentsBySlot = new HashMap<>();
        Map<Integer, EnchantingTableAction> actions = new HashMap<>();
        EnchantingTableHolder holder = new EnchantingTableHolder(enchantmentsBySlot, actions);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>Enchanting Table</dark_gray>"), enchantmentMenuPlaceholders(held, itemDefinition))
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        addEnchantingPreview(inventory, section.getConfigurationSection("preview"), held, itemDefinition);

        List<Integer> slots = contentSlots(section);
        List<SkyBlockEnchantmentDefinition> enchantments = plugin.enchantments().applicableDefinitions(itemDefinition);
        for (int index = 0; index < enchantments.size() && index < slots.size(); index++) {
            int slot = slots.get(index);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            SkyBlockEnchantmentDefinition enchantment = enchantments.get(index);
            inventory.setItem(slot, enchantmentMenuItem(player, held, itemDefinition, enchantment, section.getConfigurationSection("enchantment-item")));
            enchantmentsBySlot.put(slot, enchantment.id());
        }
        addEnchantingAction(inventory, section.getConfigurationSection("back"), EnchantingTableAction.BACK, actions);
        player.openInventory(inventory);
    }

    public void openEnchantingAnvil(Player player) {
        if (!plugin.enchantments().enabled()) {
            text.send(player, "commands.enchantment-disabled");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition itemDefinition = plugin.customItems().definition(held).orElse(null);
        if (itemDefinition == null) {
            text.send(player, "commands.enchantment-held-missing");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("enchanting-anvil");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 4)));
        Map<Integer, EnchantingAnvilAction> actions = new HashMap<>();
        EnchantingAnvilHolder holder = new EnchantingAnvilHolder(actions);
        List<TextService.TextPlaceholder> placeholders = enchantingAnvilPlaceholders(held, itemDefinition);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>Anvil</dark_gray>"), placeholders)
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        addEnchantingAnvilDisplay(inventory, section.getConfigurationSection("target"), held, itemDefinition);
        addEnchantingAnvilAction(inventory, section.getConfigurationSection("book"), EnchantingAnvilAction.APPLY, actions, placeholders);
        addEnchantingAnvilAction(inventory, section.getConfigurationSection("result"), EnchantingAnvilAction.APPLY, actions, placeholders);
        addEnchantingAnvilAction(inventory, section.getConfigurationSection("back"), EnchantingAnvilAction.BACK, actions, placeholders);
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

    public void openSacksMenu(Player player) {
        if (!plugin.sacks().enabled()) {
            text.send(player, "commands.sack-disabled");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("sacks");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 4)));
        Map<Integer, String> sacksBySlot = new HashMap<>();
        SackSelectorHolder holder = new SackSelectorHolder(sacksBySlot);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>Sacks</dark_gray>"))
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        List<Integer> slots = contentSlots(section);
        List<SackDefinition> sacks = plugin.sacks().definitions();
        for (int index = 0; index < slots.size() && index < sacks.size(); index++) {
            int slot = slots.get(index);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            SackDefinition sack = sacks.get(index);
            inventory.setItem(slot, sackSelectorItem(player, sack, section.getConfigurationSection("sack-item")));
            sacksBySlot.put(slot, sack.id());
        }
        ConfigurationSection back = section.getConfigurationSection("back");
        if (back != null) {
            int slot = back.getInt("slot", -1);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item(back, List.of()));
                sacksBySlot.put(slot, BACK_ACTION);
            }
        }
        player.openInventory(inventory);
    }

    public void openSackMenu(Player player, SackDefinition sack) {
        if (!plugin.sacks().enabled()) {
            text.send(player, "commands.sack-disabled");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("sack");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 5)));
        Map<Integer, String> itemsBySlot = new HashMap<>();
        Map<Integer, SackMenuAction> actions = new HashMap<>();
        SackMenuHolder holder = new SackMenuHolder(sack.id(), itemsBySlot, actions);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray><sack></dark_gray>"), sackPlaceholders(player, sack))
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        List<Integer> slots = contentSlots(section);
        for (int index = 0; index < slots.size() && index < sack.items().size(); index++) {
            int slot = slots.get(index);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            SackItemDefinition item = sack.items().get(index);
            inventory.setItem(slot, sackDetailItem(player, sack, item, section.getConfigurationSection("sack-item")));
            itemsBySlot.put(slot, item.id());
        }
        addSackMenuAction(inventory, section.getConfigurationSection("deposit"), SackMenuAction.DEPOSIT, actions);
        addSackMenuAction(inventory, section.getConfigurationSection("back"), SackMenuAction.BACK, actions);
        player.openInventory(inventory);
    }

    public void openQuiverMenu(Player player) {
        if (!plugin.quiver().enabled()) {
            text.send(player, "commands.quiver-disabled");
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("quiver");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 5)));
        Map<Integer, String> itemsBySlot = new HashMap<>();
        Map<Integer, QuiverAction> actions = new HashMap<>();
        QuiverHolder holder = new QuiverHolder(itemsBySlot, actions);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(section.getString("title", "<dark_gray>Quiver</dark_gray>"), quiverPlaceholders(player))
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        List<Integer> slots = contentSlots(section);
        List<QuiverItemDefinition> items = plugin.quiver().definitions();
        for (int index = 0; index < slots.size() && index < items.size(); index++) {
            int slot = slots.get(index);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            QuiverItemDefinition item = items.get(index);
            inventory.setItem(slot, quiverItem(player, item, section.getConfigurationSection("arrow-item")));
            itemsBySlot.put(slot, item.id());
        }
        addQuiverAction(inventory, section.getConfigurationSection("deposit"), QuiverAction.DEPOSIT, actions);
        addQuiverAction(inventory, section.getConfigurationSection("back"), QuiverAction.BACK, actions);
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
            case PROFILE -> openProfileViewer(player);
            case ISLAND_MENU -> openIslandManagement(player);
            case ISLAND_HOME -> player.performCommand("skyblock island home");
            case BANK -> openBankMenu(player);
            case QUEST_LOG -> openQuestLog(player, 0);
            case SKILLS -> openSkillMenu(player, 0);
            case STATS -> player.performCommand("skyblock stats");
            case SACKS -> openSacksMenu(player);
            case QUIVER -> openQuiverMenu(player);
            case STORAGE -> plugin.storage().open(player, 1);
            case ACCESSORY_BAG -> openAccessoryBag(player);
            case TUNING -> openTuningMenu(player);
            case EQUIPMENT -> openEquipmentMenu(player);
            case WARDROBE -> openWardrobeMenu(player);
            case REFORGE_ANVIL -> openReforgeAnvil(player);
            case ENCHANTING_TABLE -> openEnchantingTable(player);
            case ENCHANTING_ANVIL -> openEnchantingAnvil(player);
            case PETS -> openPetMenu(player);
            case COLLECTIONS -> openCollectionBrowser(player, 0);
            case RECIPES -> openRecipeBook(player, 0);
            case AUCTIONS -> openAuctionHouse(player, 0);
            case BAZAAR -> openBazaarMenu(player, 0);
            case SHOPS -> openShopSelector(player);
            case MINIONS -> player.performCommand("skyblock minion list");
            case NONE -> {
            }
        }
    }

    public void runIslandMenuAction(Player player, IslandMenuAction action) {
        switch (action) {
            case HOME -> {
                player.closeInventory();
                plugin.islands().teleportHome(player);
            }
            case SET_HOME -> {
                plugin.islands().setHome(player);
                openIslandManagement(player);
            }
            case WARPS -> {
                player.closeInventory();
                plugin.islands().sendWarps(player);
            }
            case RESET -> {
                player.closeInventory();
                plugin.islands().requestReset(player);
            }
            case TOGGLE_VISITORS -> {
                plugin.islands().toggleVisitors(player);
                openIslandManagement(player);
            }
            case INFO -> plugin.islands().sendInfo(player);
            case BACK -> openSkyBlockMenu(player);
            case NONE -> {
            }
        }
    }

    public void runProfileMenuClick(Player player, ProfileMenuHolder holder, int rawSlot) {
        ProfileMenuAction action = holder.action(rawSlot);
        switch (action) {
            case BACK -> openSkyBlockMenu(player);
            case SKILLS -> {
                openSkillMenu(player, 0);
            }
            case COLLECTIONS -> openCollectionBrowser(player, 0);
            case STATS -> {
                player.closeInventory();
                player.performCommand("skyblock stats");
            }
            case BANK -> openBankMenu(player);
            case PETS -> openPetMenu(player);
            case UPGRADES -> {
                player.closeInventory();
                player.performCommand("skyblock upgrades");
            }
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

    public void runTradeMenuClick(Player player, TradeMenuHolder holder, int rawSlot) {
        if (!holder.viewerId().equals(player.getUniqueId())) {
            return;
        }
        TradeSession session = plugin.trades().session(player).orElse(null);
        if (session == null) {
            closeTradeMenu(player);
            return;
        }
        Integer itemIndex = holder.offeredItemIndex(rawSlot);
        if (itemIndex != null) {
            plugin.trades().removeItem(player, itemIndex + 1);
            refreshTradeMenus(session);
            return;
        }
        TradeMenuAction action = holder.action(rawSlot);
        switch (action) {
            case OFFER_HAND -> {
                plugin.trades().offerHand(player);
                refreshTradeMenus(session);
            }
            case READY -> {
                plugin.trades().ready(player);
                refreshTradeMenus(session);
            }
            case CONFIRM -> {
                plugin.trades().confirm(player);
                refreshTradeMenus(session);
            }
            case STATUS -> {
                plugin.trades().status(player);
                refreshTradeMenus(session);
            }
            case CANCEL -> {
                plugin.trades().cancel(player);
                refreshTradeMenus(session);
            }
            case NONE -> {
            }
        }
    }

    public void runSackSelectorClick(Player player, SackSelectorHolder holder, int rawSlot) {
        String sackId = holder.sackId(rawSlot);
        if (sackId == null) {
            return;
        }
        if (BACK_ACTION.equals(sackId)) {
            openSkyBlockMenu(player);
            return;
        }
        SackDefinition sack = plugin.sacks().definition(sackId).orElse(null);
        if (sack == null) {
            text.send(player, "commands.sack-unknown", List.of(TextService.raw("sack", sackId)));
            return;
        }
        openSackMenu(player, sack);
    }

    public void runSackMenuClick(Player player, SackMenuHolder holder, int rawSlot, boolean withdrawAll) {
        SackDefinition sack = plugin.sacks().definition(holder.sackId()).orElse(null);
        if (sack == null) {
            text.send(player, "commands.sack-unknown", List.of(TextService.raw("sack", holder.sackId())));
            player.closeInventory();
            return;
        }
        String itemId = holder.itemId(rawSlot);
        if (itemId != null) {
            plugin.sacks().withdraw(player, sack.id(), itemId, withdrawAll ? Integer.MAX_VALUE : 64);
            openSackMenu(player, sack);
            return;
        }
        switch (holder.action(rawSlot)) {
            case DEPOSIT -> {
                plugin.sacks().depositInventory(player, sack.id());
                openSackMenu(player, sack);
            }
            case BACK -> openSacksMenu(player);
            case NONE -> {
            }
        }
    }

    public void runQuiverClick(Player player, QuiverHolder holder, int rawSlot, boolean withdrawClick) {
        String itemId = holder.itemId(rawSlot);
        if (itemId != null) {
            if (withdrawClick) {
                plugin.quiver().withdraw(player, itemId, 64);
            } else {
                plugin.quiver().select(player, itemId);
            }
            openQuiverMenu(player);
            return;
        }
        switch (holder.action(rawSlot)) {
            case DEPOSIT -> {
                plugin.quiver().depositInventory(player);
                openQuiverMenu(player);
            }
            case BACK -> openSkyBlockMenu(player);
            case NONE -> {
            }
        }
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

    public void runAccessoryBagClick(Player player, AccessoryBagHolder holder, int rawSlot) {
        String itemId = holder.accessory(rawSlot);
        if (itemId != null) {
            plugin.accessories().withdraw(player, itemId);
            openAccessoryBag(player);
            return;
        }
        switch (holder.action(rawSlot)) {
            case ADD_HELD -> {
                plugin.accessories().addHeld(player);
                openAccessoryBag(player);
            }
            case BACK -> openSkyBlockMenu(player);
            case NONE -> {
            }
        }
    }

    public void runTuningClick(Player player, TuningHolder holder, int rawSlot, boolean removeClick) {
        String stat = holder.stat(rawSlot);
        if (stat != null) {
            if (removeClick) {
                plugin.tuning().removePoint(player, stat);
            } else {
                plugin.tuning().addPoint(player, stat);
            }
            openTuningMenu(player);
            return;
        }
        switch (holder.action(rawSlot)) {
            case RESET -> {
                plugin.tuning().reset(player);
                openTuningMenu(player);
            }
            case BACK -> openSkyBlockMenu(player);
            case NONE -> {
            }
        }
    }

    public void runEquipmentClick(Player player, EquipmentHolder holder, int rawSlot) {
        String slotId = holder.slotId(rawSlot);
        if (slotId != null) {
            SkyBlockProfile profile = profiles.profile(player);
            if (profile.equipment().containsKey(slotId)) {
                plugin.equipment().unequip(player, slotId);
            } else {
                plugin.equipment().equipHeld(player, slotId);
            }
            openEquipmentMenu(player);
            return;
        }
        if (holder.action(rawSlot) == EquipmentAction.BACK) {
            openSkyBlockMenu(player);
        }
    }

    public void runWardrobeClick(Player player, WardrobeHolder holder, int rawSlot, boolean withdrawClick) {
        Integer wardrobeSlot = holder.slot(rawSlot);
        if (wardrobeSlot != null) {
            if (withdrawClick) {
                plugin.wardrobe().withdraw(player, wardrobeSlot);
            } else {
                plugin.wardrobe().swap(player, wardrobeSlot);
            }
            openWardrobeMenu(player);
            return;
        }
        if (holder.action(rawSlot) == WardrobeAction.BACK) {
            openSkyBlockMenu(player);
        }
    }

    public void runReforgeAnvilClick(Player player, ReforgeAnvilHolder holder, int rawSlot) {
        String reforgeId = holder.reforgeId(rawSlot);
        if (reforgeId != null) {
            if (plugin.reforges().applyHeld(player, reforgeId)) {
                openReforgeAnvil(player);
            }
            return;
        }
        switch (holder.action(rawSlot)) {
            case REMOVE -> {
                if (plugin.reforges().removeHeld(player)) {
                    openReforgeAnvil(player);
                }
            }
            case BACK -> openSkyBlockMenu(player);
            case NONE -> {
            }
        }
    }

    public void runEnchantingTableClick(Player player, EnchantingTableHolder holder, int rawSlot, boolean removeClick) {
        String enchantmentId = holder.enchantmentId(rawSlot);
        if (enchantmentId != null) {
            ItemStack held = player.getInventory().getItemInMainHand();
            SkyBlockEnchantmentDefinition enchantment = plugin.enchantments().definition(enchantmentId).orElse(null);
            if (enchantment == null) {
                text.send(player, "errors.unknown-enchantment", List.of(TextService.raw("enchantment", enchantmentId)));
                return;
            }
            if (removeClick) {
                if (plugin.enchantments().removeHeld(player, enchantmentId)) {
                    openEnchantingTable(player);
                }
                return;
            }
            int currentLevel = plugin.enchantments().enchantments(held).getOrDefault(enchantment.id(), 0);
            int targetLevel = Math.max(1, Math.min(enchantment.maxLevel(), currentLevel + 1));
            if (plugin.enchantments().applyHeld(player, enchantmentId, targetLevel)) {
                openEnchantingTable(player);
            }
            return;
        }
        if (holder.action(rawSlot) == EnchantingTableAction.BACK) {
            openSkyBlockMenu(player);
        }
    }

    public boolean runEnchantingAnvilClick(Player player, EnchantingAnvilHolder holder, int rawSlot, ItemStack cursor) {
        EnchantingAnvilAction action = holder.action(rawSlot);
        switch (action) {
            case APPLY -> {
                if (plugin.enchantments().applyBookToHeld(player, cursor)) {
                    openEnchantingAnvil(player);
                    return true;
                }
            }
            case BACK -> openSkyBlockMenu(player);
            case NONE -> {
            }
        }
        return false;
    }

    public boolean runPetMenuClick(Player player, PetMenuHolder holder, int rawSlot, ItemStack cursor) {
        Integer petIndex = holder.petIndex(rawSlot);
        if (petIndex != null) {
            if (plugin.pets().isPetItem(cursor)) {
                boolean attached = plugin.pets().attachItem(player, petIndex, cursor);
                if (attached) {
                    openPetMenu(player);
                }
                return attached;
            }
            plugin.pets().activate(player, petIndex);
            openPetMenu(player);
            return false;
        }
        if (holder.action(rawSlot) == PetMenuAction.BACK) {
            openSkyBlockMenu(player);
        }
        return false;
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
                MinionClaimResult claimed = plugin.minions().claim(player, placement);
                sendClaimResult(player, claimed);
                player.closeInventory();
            }
            case FUEL -> {
                plugin.minions().applyHeldFuel(player, placement);
                openMinionMenu(player, placement);
            }
            case UPGRADE -> {
                plugin.minions().applyHeldUpgrade(player, placement);
                openMinionMenu(player, placement);
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

    public void runBrowserEntryClick(Player player, BrowserMenuHolder holder, int rawSlot) {
        String entryId = holder.entry(rawSlot);
        if (entryId == null) {
            return;
        }
        switch (holder.type()) {
            case QUEST_LOG -> {
                plugin.quests().sendDetail(player, entryId);
            }
            case AUCTIONS -> {
                AuctionListing listing = plugin.auctions().listing(entryId).orElse(null);
                if (listing == null || !listing.active(System.currentTimeMillis())) {
                    text.send(player, "commands.auction-not-active");
                    openAuctionHouse(player, holder.page());
                    return;
                }
                text.send(
                        player,
                        listing.bin() ? "commands.auction-browser-click-bin" : "commands.auction-browser-click-bid",
                        plugin.auctions().listingPlaceholders(listing, System.currentTimeMillis())
                );
            }
            case BAZAAR -> {
                BazaarProductDefinition product = plugin.bazaar().product(entryId).orElse(null);
                if (product == null) {
                    text.send(player, "commands.bazaar-unknown-product", List.of(TextService.raw("product", entryId)));
                    openBazaarMenu(player, holder.page());
                    return;
                }
                text.send(player, "commands.bazaar-browser-click", plugin.bazaar().productPlaceholders(product));
            }
            case COLLECTIONS, RECIPES -> {
            }
            case SKILLS -> {
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

    private void addTradeItems(Inventory inventory, List<Integer> slots, ConfigurationSection section, List<ItemStack> items, Map<Integer, Integer> offeredItemIndexes, boolean removable) {
        for (int index = 0; index < slots.size() && index < items.size(); index++) {
            int slot = slots.get(index);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot, tradeItem(items.get(index), section, index, removable));
            if (removable) {
                offeredItemIndexes.put(slot, index);
            }
        }
    }

    private void addTradeSummary(Inventory inventory, ConfigurationSection section, Player player, TradeSession session, boolean ownSide) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item(section, tradeSidePlaceholders(player, session, ownSide)));
    }

    private void addTradeAction(Inventory inventory, ConfigurationSection section, TradeMenuAction fallbackAction, Map<Integer, TradeMenuAction> actions, Player player, TradeSession session) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        TradeMenuAction action = TradeMenuAction.parse(section.getString("action", fallbackAction.name()));
        inventory.setItem(slot, item(section, tradePlaceholders(player, session)));
        actions.put(slot, action);
    }

    private void addQuestSummary(Inventory inventory, ConfigurationSection section, SkyBlockProfile profile) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item(section, plugin.quests().summaryPlaceholders(profile)));
    }

    private ItemStack questItem(SkyBlockProfile profile, QuestDefinition definition, ConfigurationSection section) {
        Material material = questMaterial(definition, section);
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = plugin.quests().placeholders(profile, definition);
        if (section == null) {
            meta.displayName(text.deserialize("<quest>", placeholders));
            meta.lore(definition.description().stream()
                    .map(line -> text.deserialize(line, placeholders))
                    .toList());
        } else {
            meta.displayName(text.deserialize(section.getString("display-name", "<quest>"), placeholders));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : definition.description()) {
                lore.add(text.deserialize(line, placeholders));
            }
            for (String line : section.getStringList("lore")) {
                lore.add(text.deserialize(line, placeholders));
            }
            meta.lore(lore);
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private Material questMaterial(QuestDefinition definition, ConfigurationSection section) {
        String fallback = section == null ? "BOOK" : section.getString("material", "BOOK");
        String configuredName = definition.material() == null ? "" : definition.material();
        Material configured = Material.matchMaterial(configuredName);
        if (configured != null) {
            return configured;
        }
        Material material = Material.matchMaterial(fallback);
        return material == null ? Material.BOOK : material;
    }

    private void addSkillSummary(Inventory inventory, ConfigurationSection section, SkyBlockProfile profile) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item(section, skillSummaryPlaceholders(profile)));
    }

    private ItemStack skillItem(SkyBlockProfile profile, SkillDefinition definition, ConfigurationSection section) {
        Material material = skillMaterial(definition, section);
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = skillPlaceholders(profile, definition);
        if (section == null) {
            meta.displayName(text.deserialize("<skill>", placeholders));
            meta.lore(List.of());
        } else {
            meta.displayName(text.deserialize(section.getString("display-name", "<skill>"), placeholders));
            meta.lore(section.getStringList("lore").stream()
                    .map(line -> text.deserialize(line, placeholders))
                    .toList());
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private Material skillMaterial(SkillDefinition definition, ConfigurationSection section) {
        String fallback = "EXPERIENCE_BOTTLE";
        if (section != null) {
            fallback = section.getString("material", fallback);
            String configured = section.getString("materials." + definition.type().name(), fallback);
            Material material = Material.matchMaterial(configured);
            if (material != null) {
                return material;
            }
        }
        Material material = Material.matchMaterial(fallback);
        return material == null ? Material.EXPERIENCE_BOTTLE : material;
    }

    private List<TextService.TextPlaceholder> skillSummaryPlaceholders(SkyBlockProfile profile) {
        List<SkillDefinition> skills = plugin.skills().definitions();
        int totalSkillLevel = skills.stream()
                .mapToInt(definition -> plugin.skills().level(definition.type(), profile.skillXp(definition.type())))
                .sum();
        double skillAverage = skills.isEmpty() ? 0.0D : totalSkillLevel / (double) skills.size();
        return List.of(
                TextService.raw("level", Integer.toString(plugin.skills().skyBlockLevel(profile))),
                TextService.raw("skill_count", text.formatNumber(skills.size())),
                TextService.raw("total_skill_level", text.formatNumber(totalSkillLevel)),
                TextService.raw("skill_average", text.formatNumber(skillAverage))
        );
    }

    private List<TextService.TextPlaceholder> skillPlaceholders(SkyBlockProfile profile, SkillDefinition definition) {
        double xp = profile.skillXp(definition.type());
        int level = plugin.skills().level(definition.type(), xp);
        double currentXp = plugin.skills().xpIntoCurrentLevel(definition.type(), xp);
        double requiredXp = plugin.skills().requiredXpForNextLevel(definition.type(), xp);
        double progress = plugin.skills().progressToNextLevel(definition.type(), xp);
        int nextLevel = Math.min(definition.maxLevel(), level + 1);
        return List.of(
                TextService.parsed("skill", definition.displayName()),
                TextService.raw("id", definition.type().name()),
                TextService.raw("level", Integer.toString(level)),
                TextService.raw("next_level", Integer.toString(nextLevel)),
                TextService.raw("max_level", Integer.toString(definition.maxLevel())),
                TextService.raw("xp", text.formatNumber(xp)),
                TextService.raw("current_xp", text.formatNumber(currentXp)),
                TextService.raw("required_xp", requiredXp <= 0.0D ? text.rawMessage("skills.maxed") : text.formatNumber(requiredXp)),
                TextService.raw("progress", text.formatNumber(progress)),
                TextService.raw("skyblock_xp", text.formatNumber(level * 5))
        );
    }

    private ItemStack tradeItem(ItemStack source, ConfigurationSection section, int index, boolean removable) {
        ItemStack itemStack = source.clone();
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = List.of(
                TextService.raw("index", Integer.toString(index + 1)),
                TextService.parsed("item", plugin.trades().itemDisplay(source)),
                TextService.parsed("action_hint", text.rawMessage(removable ? "trades.item-remove-hint" : "trades.item-view-hint"))
        );
        if (section == null) {
            meta.displayName(text.deserialize("<item>", placeholders));
            meta.lore(List.of());
        } else {
            meta.displayName(text.deserialize(section.getString("display-name", "<item>"), placeholders));
            meta.lore(section.getStringList("lore").stream()
                    .map(line -> text.deserialize(line, placeholders))
                    .toList());
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private List<TextService.TextPlaceholder> tradePlaceholders(Player player, TradeSession session) {
        UUID playerId = player.getUniqueId();
        UUID partnerId = session.partnerId(playerId);
        Player partner = Bukkit.getPlayer(partnerId);
        return List.of(
                TextService.raw("player", player.getName()),
                TextService.raw("partner", partner == null ? session.partnerName(playerId) : partner.getName()),
                TextService.raw("your_coins", text.formatNumber(session.coins(playerId))),
                TextService.raw("their_coins", text.formatNumber(session.coins(partnerId))),
                TextService.raw("your_items", text.formatNumber(session.items(playerId).size())),
                TextService.raw("their_items", text.formatNumber(session.items(partnerId).size())),
                TextService.parsed("your_status", plugin.trades().statusLabel(session, playerId)),
                TextService.parsed("their_status", plugin.trades().statusLabel(session, partnerId))
        );
    }

    private List<TextService.TextPlaceholder> tradeSidePlaceholders(Player player, TradeSession session, boolean ownSide) {
        UUID playerId = player.getUniqueId();
        UUID sideId = ownSide ? playerId : session.partnerId(playerId);
        Player sidePlayer = Bukkit.getPlayer(sideId);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(tradePlaceholders(player, session));
        placeholders.add(TextService.parsed("side", ownSide ? text.rawMessage("trades.side-you") : (sidePlayer == null ? session.partnerName(playerId) : sidePlayer.getName())));
        placeholders.add(TextService.raw("side_coins", text.formatNumber(session.coins(sideId))));
        placeholders.add(TextService.raw("side_items", text.formatNumber(session.items(sideId).size())));
        placeholders.add(TextService.parsed("side_status", plugin.trades().statusLabel(session, sideId)));
        return placeholders;
    }

    private void refreshTradeMenus(TradeSession session) {
        refreshTradeMenu(Bukkit.getPlayer(session.firstId()));
        refreshTradeMenu(Bukkit.getPlayer(session.secondId()));
    }

    private void refreshTradeMenu(Player player) {
        if (player == null || !(player.getOpenInventory().getTopInventory().getHolder() instanceof TradeMenuHolder)) {
            return;
        }
        if (plugin.trades().session(player).isEmpty()) {
            player.closeInventory();
            return;
        }
        openTradeMenu(player);
    }

    private void closeTradeMenu(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof TradeMenuHolder) {
            player.closeInventory();
        }
    }

    private void sendSkillSummary(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.skills-header");
        for (SkillDefinition definition : plugin.skills().definitions()) {
            double xp = profile.skillXp(definition.type());
            text.send(player, "commands.skill-line", List.of(
                    TextService.parsed("skill", definition.displayName()),
                    TextService.raw("level", Integer.toString(plugin.skills().level(definition.type(), xp))),
                    TextService.raw("xp", text.formatNumber(xp))
            ));
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
            case QUEST_LOG -> openQuestLog(player, page);
            case SKILLS -> openSkillMenu(player, page);
            case COLLECTIONS -> openCollectionBrowser(player, page);
            case RECIPES -> openRecipeBook(player, page);
            case AUCTIONS -> openAuctionHouse(player, page);
            case BAZAAR -> openBazaarMenu(player, page);
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

    private ItemStack auctionItem(AuctionListing listing, ConfigurationSection section) {
        ItemStack itemStack = listing.itemStack().clone();
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = plugin.auctions().listingPlaceholders(listing, System.currentTimeMillis());
        if (section == null) {
            meta.displayName(text.deserialize("<item>", placeholders));
            meta.lore(List.of());
        } else {
            meta.displayName(text.deserialize(section.getString("display-name", "<item>"), placeholders));
            meta.lore(section.getStringList("lore").stream()
                    .map(line -> text.deserialize(line, placeholders))
                    .toList());
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private ItemStack bazaarProductItem(BazaarProductDefinition product, ConfigurationSection section) {
        ItemStack itemStack = plugin.bazaar().displayItem(product);
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = plugin.bazaar().productPlaceholders(product);
        if (section == null) {
            meta.displayName(text.deserialize("<product>", placeholders));
            meta.lore(List.of());
        } else {
            meta.displayName(text.deserialize(section.getString("display-name", "<product>"), placeholders));
            meta.lore(section.getStringList("lore").stream()
                    .map(line -> text.deserialize(line, placeholders))
                    .toList());
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private void addAccessorySummary(Inventory inventory, ConfigurationSection section, Player player) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item(section, accessoryBagPlaceholders(player)));
    }

    private void addAccessoryAction(Inventory inventory, ConfigurationSection section, AccessoryBagAction action, Map<Integer, AccessoryBagAction> actions) {
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

    private ItemStack accessoryBagItem(io.github.openskyblock.service.CustomItemDefinition definition) {
        ItemStack itemStack = plugin.customItems().createItem(definition);
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = List.of(
                TextService.raw("rarity", definition.rarity().name()),
                TextService.raw("magical_power", Integer.toString(plugin.accessories().magicalPower(definition)))
        );
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (meta.lore() != null) {
            lore.addAll(meta.lore());
            lore.add(net.kyori.adventure.text.Component.empty());
        }
        for (String line : configService.messages().getStringList("menus.accessory-bag-item")) {
            lore.add(text.deserialize(line, placeholders));
        }
        meta.lore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private void addTuningSummary(Inventory inventory, ConfigurationSection section, Player player) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item(section, tuningPlaceholders(player)));
    }

    private void addTuningAction(Inventory inventory, ConfigurationSection section, TuningAction action, Map<Integer, TuningAction> actions) {
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

    private void addPetSummary(Inventory inventory, ConfigurationSection section, Player player) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item(section, petMenuPlaceholders(player)));
    }

    private void addPetAction(Inventory inventory, ConfigurationSection section, PetMenuAction action, Map<Integer, PetMenuAction> actions) {
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

    private void addEquipmentAction(Inventory inventory, ConfigurationSection section, EquipmentAction action, Map<Integer, EquipmentAction> actions) {
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

    private void addWardrobeAction(Inventory inventory, ConfigurationSection section, WardrobeAction action, Map<Integer, WardrobeAction> actions) {
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

    private void addReforgeAction(Inventory inventory, ConfigurationSection section, ReforgeAnvilAction action, Map<Integer, ReforgeAnvilAction> actions) {
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

    private void addEnchantingAction(Inventory inventory, ConfigurationSection section, EnchantingTableAction action, Map<Integer, EnchantingTableAction> actions) {
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

    private void addEnchantingAnvilAction(Inventory inventory, ConfigurationSection section, EnchantingAnvilAction action, Map<Integer, EnchantingAnvilAction> actions, List<TextService.TextPlaceholder> placeholders) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item(section, placeholders));
        actions.put(slot, action);
    }

    private void addSackMenuAction(Inventory inventory, ConfigurationSection section, SackMenuAction action, Map<Integer, SackMenuAction> actions) {
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

    private void addQuiverAction(Inventory inventory, ConfigurationSection section, QuiverAction action, Map<Integer, QuiverAction> actions) {
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

    private ItemStack emptyEquipmentItem(EquipmentSlotDefinition slot, ConfigurationSection section) {
        ItemStack itemStack = item(section, equipmentSlotPlaceholders(slot));
        ItemMeta meta = itemStack.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : configService.messages().getStringList("menus.equipment-empty")) {
            lore.add(text.deserialize(line, equipmentSlotPlaceholders(slot)));
        }
        meta.lore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private ItemStack equippedEquipmentItem(EquipmentSlotDefinition slot, ItemStack equipped) {
        ItemStack itemStack = equipped.clone();
        ItemMeta meta = itemStack.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (meta.lore() != null) {
            lore.addAll(meta.lore());
            lore.add(net.kyori.adventure.text.Component.empty());
        }
        for (String line : configService.messages().getStringList("menus.equipment-equipped")) {
            lore.add(text.deserialize(line, equipmentSlotPlaceholders(slot)));
        }
        meta.lore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private ItemStack quiverItem(Player player, QuiverItemDefinition item, ConfigurationSection section) {
        ItemStack itemStack = new ItemStack(item.material());
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = quiverItemPlaceholders(player, item);
        meta.displayName(text.deserialize(section == null ? "<item>" : section.getString("display-name", "<item>"), placeholders));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : configService.messages().getStringList("menus.quiver-item")) {
            lore.add(text.deserialize(line, placeholders));
        }
        meta.lore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private ItemStack sackSelectorItem(Player player, SackDefinition sack, ConfigurationSection section) {
        ItemStack itemStack = new ItemStack(sack.material());
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = sackPlaceholders(player, sack);
        meta.displayName(text.deserialize(section == null ? "<sack>" : section.getString("display-name", "<sack>"), placeholders));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : configService.messages().getStringList("menus.sack-selector-item")) {
            lore.add(text.deserialize(line, placeholders));
        }
        meta.lore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private ItemStack sackDetailItem(Player player, SackDefinition sack, SackItemDefinition item, ConfigurationSection section) {
        ItemStack itemStack = new ItemStack(item.material());
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = sackItemPlaceholders(player, sack, item);
        meta.displayName(text.deserialize(section == null ? "<item>" : section.getString("display-name", "<item>"), placeholders));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : configService.messages().getStringList("menus.sack-detail-item")) {
            lore.add(text.deserialize(line, placeholders));
        }
        meta.lore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private ItemStack wardrobeSlotItem(int slot, WardrobeSet set, ConfigurationSection emptySection, ConfigurationSection storedSection) {
        boolean empty = set == null || set.empty();
        ConfigurationSection section = empty ? emptySection : storedSection;
        Material material = Material.ARMOR_STAND;
        if (section != null) {
            String configuredMaterial = section.getString("material", empty ? "ARMOR_STAND" : "AUTO");
            Material matched = Material.matchMaterial(configuredMaterial);
            if (matched != null) {
                material = matched;
            }
        }
        if (!empty && (section == null || section.getString("material", "AUTO").equalsIgnoreCase("AUTO"))) {
            material = set.iconMaterial(Material.ARMOR_STAND);
        }
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = wardrobeSlotPlaceholders(slot, set);
        String defaultName = empty ? "<yellow>Wardrobe Slot <slot></yellow>" : "<green>Wardrobe Slot <slot></green>";
        meta.displayName(text.deserialize(section == null ? defaultName : section.getString("display-name", defaultName), placeholders));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : configService.messages().getStringList(empty ? "menus.wardrobe-empty" : "menus.wardrobe-stored")) {
            lore.add(text.deserialize(line, placeholders));
        }
        meta.lore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private ItemStack petMenuItem(SkyBlockProfile profile, OwnedPet pet, PetDefinition definition, ConfigurationSection section) {
        ItemStack itemStack = new ItemStack(definition.material());
        ItemMeta meta = itemStack.getItemMeta();
        boolean active = plugin.pets().isActive(profile, pet);
        List<TextService.TextPlaceholder> placeholders = plugin.pets().placeholders(pet, definition, active);
        if (section == null) {
            meta.displayName(text.deserialize(definition.displayName(), placeholders));
            meta.lore(plugin.pets().statLore(definition, pet));
            itemStack.setItemMeta(meta);
            return itemStack;
        }
        meta.displayName(text.deserialize(section.getString("display-name", "<pet>"), placeholders));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : definition.lore()) {
            lore.add(text.deserialize(line, placeholders));
        }
        for (String line : section.getStringList("lore")) {
            if (line.equals("<stats>")) {
                lore.addAll(plugin.pets().statLore(definition, pet));
            } else {
                lore.add(text.deserialize(line, placeholders));
            }
        }
        meta.lore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private void addReforgePreview(Inventory inventory, ConfigurationSection section, ItemStack held, CustomItemDefinition itemDefinition) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, reforgePreviewItem(section, held, itemDefinition));
    }

    private ItemStack reforgePreviewItem(ConfigurationSection section, ItemStack held, CustomItemDefinition itemDefinition) {
        ItemStack itemStack = held.clone();
        itemStack.setAmount(1);
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = reforgeMenuPlaceholders(held, itemDefinition);
        meta.displayName(text.deserialize(section.getString("display-name", "<item>"), placeholders));
        meta.lore(section.getStringList("lore").stream()
                .map(line -> text.deserialize(line, placeholders))
                .toList());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private ItemStack reforgeMenuItem(Player player, ItemStack held, CustomItemDefinition itemDefinition, ReforgeDefinition reforge, ConfigurationSection section) {
        Material material = Material.ANVIL;
        if (section != null) {
            Material configured = Material.matchMaterial(section.getString("material", "ANVIL"));
            if (configured != null) {
                material = configured;
            }
        }
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = reforgeMenuPlaceholders(player, held, itemDefinition, reforge);
        meta.displayName(text.deserialize(section == null ? "<reforge>" : section.getString("display-name", "<reforge>"), placeholders));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        List<String> rawLore = section == null ? List.of("<gray>Cost:</gray> <gold><cost></gold>", "<stats>") : section.getStringList("lore");
        for (String line : rawLore) {
            if (line.equals("<stats>")) {
                lore.addAll(reforgeStatLore(reforge, held, itemDefinition));
            } else {
                lore.add(text.deserialize(line, placeholders));
            }
        }
        meta.lore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private List<net.kyori.adventure.text.Component> reforgeStatLore(ReforgeDefinition reforge, ItemStack held, CustomItemDefinition itemDefinition) {
        Map<String, Double> stats = plugin.reforges().stats(reforge, plugin.customItems().rarity(held, itemDefinition));
        if (stats.isEmpty()) {
            return List.of(text.deserialize(text.rawMessage("reforges.no-stats")));
        }
        return stats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> text.message("items.reforge-stat-line", List.of(
                        TextService.raw("stat", text.statName(entry.getKey())),
                        TextService.raw("value", text.formatNumber(entry.getValue()))
                )))
                .toList();
    }

    private void addEnchantingPreview(Inventory inventory, ConfigurationSection section, ItemStack held, CustomItemDefinition itemDefinition) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, enchantingPreviewItem(section, held, itemDefinition));
    }

    private void addEnchantingAnvilDisplay(Inventory inventory, ConfigurationSection section, ItemStack held, CustomItemDefinition itemDefinition) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, enchantingAnvilTargetItem(section, held, itemDefinition));
    }

    private ItemStack enchantingPreviewItem(ConfigurationSection section, ItemStack held, CustomItemDefinition itemDefinition) {
        ItemStack itemStack = held.clone();
        itemStack.setAmount(1);
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = enchantmentMenuPlaceholders(held, itemDefinition);
        meta.displayName(text.deserialize(section.getString("display-name", "<item>"), placeholders));
        meta.lore(section.getStringList("lore").stream()
                .map(line -> text.deserialize(line, placeholders))
                .toList());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private ItemStack enchantingAnvilTargetItem(ConfigurationSection section, ItemStack held, CustomItemDefinition itemDefinition) {
        ItemStack itemStack = held.clone();
        itemStack.setAmount(1);
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = enchantingAnvilPlaceholders(held, itemDefinition);
        meta.displayName(text.deserialize(section.getString("display-name", "<item>"), placeholders));
        meta.lore(section.getStringList("lore").stream()
                .map(line -> text.deserialize(line, placeholders))
                .toList());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private ItemStack enchantmentMenuItem(Player player, ItemStack held, CustomItemDefinition itemDefinition, SkyBlockEnchantmentDefinition enchantment, ConfigurationSection section) {
        Material material = Material.ENCHANTED_BOOK;
        if (section != null) {
            Material configured = Material.matchMaterial(section.getString("material", "ENCHANTED_BOOK"));
            if (configured != null) {
                material = configured;
            }
        }
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = enchantmentMenuPlaceholders(player, held, itemDefinition, enchantment);
        meta.displayName(text.deserialize(section == null ? "<enchantment> <level>" : section.getString("display-name", "<enchantment> <level>"), placeholders));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        List<String> rawLore = section == null ? List.of("<gray>Cost:</gray> <gold><cost></gold>", "<stats>") : section.getStringList("lore");
        for (String line : rawLore) {
            if (line.equals("<stats>")) {
                lore.addAll(enchantmentStatLore(enchantment, targetEnchantmentLevel(held, enchantment)));
            } else if (line.equals("<description>")) {
                for (String descriptionLine : enchantment.lore()) {
                    lore.add(text.deserialize(descriptionLine, placeholders));
                }
            } else {
                lore.add(text.deserialize(line, placeholders));
            }
        }
        meta.lore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private List<net.kyori.adventure.text.Component> enchantmentStatLore(SkyBlockEnchantmentDefinition enchantment, int level) {
        if (enchantment.statsPerLevel().isEmpty()) {
            return List.of(text.deserialize(text.rawMessage("enchantments.no-stats")));
        }
        return enchantment.statsPerLevel().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> text.message("items.enchantment-stat-line", List.of(
                        TextService.raw("stat", text.statName(entry.getKey())),
                        TextService.raw("value", text.formatNumber(entry.getValue() * level))
                )))
                .toList();
    }

    private ItemStack tuningItem(Player player, String stat, ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", "AMETHYST_SHARD"));
        ItemStack itemStack = new ItemStack(material == null ? Material.AMETHYST_SHARD : material);
        ItemMeta meta = itemStack.getItemMeta();
        SkyBlockProfile profile = profiles.profile(player);
        int points = profile.tuning(stat);
        double value = plugin.tuning().tuningValue(stat);
        List<TextService.TextPlaceholder> placeholders = List.of(
                TextService.raw("stat", plugin.tuning().statLabel(stat)),
                TextService.raw("points", Integer.toString(points)),
                TextService.raw("value", text.formatNumber(value)),
                TextService.raw("bonus", text.formatNumber(points * value))
        );
        meta.displayName(text.deserialize(section.getString("display-name", "<white><stat></white>"), placeholders));
        meta.lore(configService.messages().getStringList("menus.tuning-stat").stream()
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

    private void sendClaimResult(Player player, MinionClaimResult claimed) {
        if (claimed.emptyResult()) {
            text.send(player, "commands.minion-nothing");
            return;
        }
        if (claimed.resources() > 0L) {
            text.send(player, "commands.minion-claimed", List.of(TextService.raw("amount", text.formatNumber(claimed.resources()))));
        }
        if (claimed.coins() > 0.0D) {
            text.send(player, "commands.minion-hopper-claimed", List.of(TextService.raw("coins", text.formatNumber(claimed.coins()))));
        }
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

    private List<TextService.TextPlaceholder> profileViewerPlaceholders(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        List<SkillDefinition> skills = plugin.skills().definitions();
        int totalSkillLevel = skills.stream()
                .mapToInt(definition -> plugin.skills().level(definition.type(), profile.skillXp(definition.type())))
                .sum();
        double skillAverage = skills.isEmpty() ? 0.0D : totalSkillLevel / (double) skills.size();
        int unlockedCollections = (int) plugin.collections().definitions().stream()
                .filter(definition -> profile.collectionAmount(definition.id()) > 0L)
                .count();
        int totalCollectionTiers = plugin.collections().definitions().stream()
                .mapToInt(definition -> plugin.collections().tier(definition, profile.collectionAmount(definition.id())))
                .sum();
        int purchasedUpgradeLevels = plugin.upgrades().definitions().stream()
                .mapToInt(definition -> plugin.upgrades().level(profile, definition.id()))
                .sum();
        StatSnapshot stats = plugin.stats().snapshot(player);
        String activePet = plugin.pets().activeDefinition(profile)
                .map(PetDefinition::displayName)
                .orElse(text.rawMessage("pets.no-active"));
        return List.of(
                TextService.raw("player", profile.playerName()),
                TextService.raw("level", Integer.toString(plugin.skills().skyBlockLevel(profile))),
                TextService.raw("purse", text.formatNumber(profile.purse())),
                TextService.raw("bank", text.formatNumber(profile.bank())),
                TextService.raw("bank_capacity", text.formatNumber(plugin.economy().bankCapacity(profile))),
                TextService.raw("total_skill_level", text.formatNumber(totalSkillLevel)),
                TextService.raw("skill_average", text.formatNumber(skillAverage)),
                TextService.raw("collections_unlocked", text.formatNumber(unlockedCollections)),
                TextService.raw("collections_total", text.formatNumber(plugin.collections().definitions().size())),
                TextService.raw("collection_tiers", text.formatNumber(totalCollectionTiers)),
                TextService.raw("minions", text.formatNumber(profile.minions().size())),
                TextService.raw("pets", text.formatNumber(profile.pets().size())),
                TextService.raw("pet_score", text.formatNumber(plugin.pets().score(profile))),
                TextService.parsed("active_pet", activePet),
                TextService.raw("accessories", text.formatNumber(profile.accessoryBag().size())),
                TextService.raw("accessory_capacity", text.formatNumber(plugin.accessories().capacity(profile))),
                TextService.raw("magical_power", text.formatNumber(plugin.accessories().magicalPower(profile))),
                TextService.raw("equipment", text.formatNumber(plugin.equipment().equippedCount(profile))),
                TextService.raw("wardrobe_sets", text.formatNumber(profile.wardrobe().size())),
                TextService.raw("upgrades", text.formatNumber(purchasedUpgradeLevels)),
                TextService.raw("health", text.formatNumber(stats.health())),
                TextService.raw("defense", text.formatNumber(stats.defense())),
                TextService.raw("damage", text.formatNumber(stats.damage())),
                TextService.raw("strength", text.formatNumber(stats.strength())),
                TextService.raw("crit_chance", text.formatNumber(stats.critChance())),
                TextService.raw("crit_damage", text.formatNumber(stats.critDamage())),
                TextService.raw("intelligence", text.formatNumber(stats.intelligence())),
                TextService.raw("speed", text.formatNumber(stats.speed())),
                TextService.raw("ferocity", text.formatNumber(stats.ferocity()))
        );
    }

    private List<TextService.TextPlaceholder> bankPlaceholders(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        return List.of(
                TextService.raw("purse", text.formatNumber(profile.purse())),
                TextService.raw("bank", text.formatNumber(profile.bank())),
                TextService.raw("capacity", text.formatNumber(plugin.economy().bankCapacity(profile)))
        );
    }

    private List<TextService.TextPlaceholder> accessoryBagPlaceholders(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        return List.of(
                TextService.raw("count", Integer.toString(profile.accessoryBag().size())),
                TextService.raw("capacity", Integer.toString(plugin.accessories().capacity(profile))),
                TextService.raw("magical_power", Integer.toString(plugin.accessories().magicalPower(profile)))
        );
    }

    private List<TextService.TextPlaceholder> tuningPlaceholders(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        return List.of(
                TextService.raw("used", Integer.toString(plugin.tuning().usedPoints(profile))),
                TextService.raw("total", Integer.toString(plugin.tuning().totalPoints(profile))),
                TextService.raw("available", Integer.toString(plugin.tuning().availablePoints(profile))),
                TextService.raw("magical_power", Integer.toString(plugin.accessories().magicalPower(profile)))
        );
    }

    private List<TextService.TextPlaceholder> petMenuPlaceholders(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        String activePet = plugin.pets().activeDefinition(profile)
                .map(PetDefinition::displayName)
                .orElse(text.rawMessage("pets.no-active"));
        return List.of(
                TextService.raw("count", Integer.toString(profile.pets().size())),
                TextService.raw("pet_score", Integer.toString(plugin.pets().score(profile))),
                TextService.raw("skyblock_xp", text.formatNumber(plugin.pets().scoreSkyBlockXp(profile))),
                TextService.raw("unique_pets", Integer.toString(plugin.pets().uniquePetCount(profile))),
                TextService.raw("maxed_pets", Integer.toString(plugin.pets().maxedPetCount(profile))),
                TextService.parsed("active_pet", activePet)
        );
    }

    private List<TextService.TextPlaceholder> reforgeMenuPlaceholders(ItemStack held, CustomItemDefinition itemDefinition) {
        String current = plugin.reforges().definition(held)
                .map(ReforgeDefinition::displayName)
                .orElseGet(() -> text.rawMessage("reforges.no-current"));
        return List.of(
                TextService.parsed("item", itemDefinition.displayName()),
                TextService.raw("category", itemDefinition.category()),
                TextService.raw("rarity", plugin.customItems().rarity(held, itemDefinition).name()),
                TextService.parsed("current_reforge", current)
        );
    }

    private List<TextService.TextPlaceholder> reforgeMenuPlaceholders(Player player, ItemStack held, CustomItemDefinition itemDefinition, ReforgeDefinition reforge) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(plugin.reforges().placeholders(
                reforge,
                itemDefinition,
                plugin.reforges().cost(reforge, itemDefinition, held)
        ));
        placeholders.add(TextService.parsed("required", plugin.reforges().requiredItemLine(reforge)));
        placeholders.add(TextService.parsed("status", reforgeStatus(player, held, reforge)));
        placeholders.add(TextService.parsed("current_reforge", plugin.reforges().definition(held)
                .map(ReforgeDefinition::displayName)
                .orElseGet(() -> text.rawMessage("reforges.no-current"))));
        placeholders.add(TextService.raw("rarity", plugin.customItems().rarity(held, itemDefinition).name()));
        return placeholders;
    }

    private String reforgeStatus(Player player, ItemStack held, ReforgeDefinition reforge) {
        if (plugin.reforges().reforgeId(held).filter(reforge.id()::equals).isPresent()) {
            return text.rawMessage("reforges.current");
        }
        if (!plugin.reforges().hasRequiredItem(player, reforge)) {
            return text.rawMessage("reforges.missing-required");
        }
        return text.rawMessage("reforges.available");
    }

    private List<TextService.TextPlaceholder> enchantmentMenuPlaceholders(ItemStack held, CustomItemDefinition itemDefinition) {
        int applied = plugin.enchantments().enchantments(held).size();
        return List.of(
                TextService.parsed("item", itemDefinition.displayName()),
                TextService.raw("category", itemDefinition.category()),
                TextService.raw("rarity", plugin.customItems().rarity(held, itemDefinition).name()),
                TextService.raw("applied_enchantments", Integer.toString(applied))
        );
    }

    private List<TextService.TextPlaceholder> enchantingAnvilPlaceholders(ItemStack held, CustomItemDefinition itemDefinition) {
        int applied = plugin.enchantments().enchantments(held).size();
        boolean bookTarget = plugin.enchantments().isBookDefinition(itemDefinition);
        return List.of(
                TextService.parsed("item", itemDefinition.displayName()),
                TextService.raw("category", itemDefinition.category()),
                TextService.raw("rarity", plugin.customItems().rarity(held, itemDefinition).name()),
                TextService.raw("applied_enchantments", Integer.toString(applied)),
                TextService.raw("book_apply_multiplier", text.formatNumber(plugin.enchantments().bookApplyCostMultiplier())),
                TextService.raw("book_combine_cost", text.formatNumber(plugin.enchantments().bookCombineCost())),
                TextService.parsed("target_type", bookTarget ? text.rawMessage("enchantments.book-target") : text.rawMessage("enchantments.item-target"))
        );
    }

    private List<TextService.TextPlaceholder> enchantmentMenuPlaceholders(Player player, ItemStack held, CustomItemDefinition itemDefinition, SkyBlockEnchantmentDefinition enchantment) {
        int currentLevel = plugin.enchantments().enchantments(held).getOrDefault(enchantment.id(), 0);
        int targetLevel = targetEnchantmentLevel(held, enchantment);
        double cost = plugin.enchantments().cost(enchantment, itemDefinition, held, targetLevel);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(plugin.enchantments().placeholders(enchantment, itemDefinition, targetLevel, cost));
        placeholders.add(TextService.raw("current_level", currentLevel <= 0 ? text.rawMessage("enchantments.no-current") : plugin.enchantments().levelLabel(currentLevel)));
        placeholders.add(TextService.raw("target_level", plugin.enchantments().levelLabel(targetLevel)));
        placeholders.add(TextService.raw("max_level", plugin.enchantments().levelLabel(enchantment.maxLevel())));
        placeholders.add(TextService.raw("type", enchantment.ultimate() ? text.rawMessage("enchantments.ultimate-type") : text.rawMessage("enchantments.normal-type")));
        placeholders.add(TextService.parsed("status", enchantmentStatus(held, enchantment)));
        placeholders.add(TextService.raw("rarity", plugin.customItems().rarity(held, itemDefinition).name()));
        placeholders.add(TextService.raw("applied_enchantments", Integer.toString(plugin.enchantments().enchantments(held).size())));
        return placeholders;
    }

    private int targetEnchantmentLevel(ItemStack held, SkyBlockEnchantmentDefinition enchantment) {
        int currentLevel = plugin.enchantments().enchantments(held).getOrDefault(enchantment.id(), 0);
        return Math.max(1, Math.min(enchantment.maxLevel(), currentLevel + 1));
    }

    private String enchantmentStatus(ItemStack held, SkyBlockEnchantmentDefinition enchantment) {
        int currentLevel = plugin.enchantments().enchantments(held).getOrDefault(enchantment.id(), 0);
        if (currentLevel >= enchantment.maxLevel()) {
            return text.rawMessage("enchantments.maxed");
        }
        if (enchantment.ultimate()) {
            boolean conflict = plugin.enchantments().enchantments(held).keySet().stream()
                    .map(plugin.enchantments()::definition)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .anyMatch(existing -> existing.ultimate() && !existing.id().equals(enchantment.id()));
            if (conflict) {
                return text.rawMessage("enchantments.ultimate-conflict");
            }
        }
        if (currentLevel > 0) {
            return text.rawMessage("enchantments.upgrade");
        }
        return text.rawMessage("enchantments.available");
    }

    private List<TextService.TextPlaceholder> equipmentPlaceholders(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        return List.of(
                TextService.raw("count", Integer.toString(plugin.equipment().equippedCount(profile))),
                TextService.raw("slots", Integer.toString(plugin.equipment().slots().size()))
        );
    }

    private List<TextService.TextPlaceholder> equipmentSlotPlaceholders(EquipmentSlotDefinition slot) {
        return List.of(
                TextService.raw("slot_id", slot.id()),
                TextService.parsed("slot", slot.displayName())
        );
    }

    private List<TextService.TextPlaceholder> wardrobePlaceholders(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        long stored = profile.wardrobe().values().stream()
                .filter(set -> set != null && !set.empty())
                .count();
        return List.of(
                TextService.raw("stored", Long.toString(stored)),
                TextService.raw("slots", Integer.toString(plugin.wardrobe().slotCount()))
        );
    }

    private List<TextService.TextPlaceholder> wardrobeSlotPlaceholders(int slot, WardrobeSet set) {
        int pieces = set == null ? 0 : set.pieceCount();
        return List.of(
                TextService.raw("slot", Integer.toString(slot)),
                TextService.raw("pieces", Integer.toString(pieces)),
                TextService.raw("max_pieces", "4")
        );
    }

    private List<TextService.TextPlaceholder> sackPlaceholders(Player player, SackDefinition sack) {
        SkyBlockProfile profile = profiles.profile(player);
        return List.of(
                TextService.raw("sack_id", sack.id()),
                TextService.parsed("sack", sack.displayName()),
                TextService.raw("stored", text.formatNumber(plugin.sacks().totalStored(profile, sack))),
                TextService.raw("capacity", text.formatNumber(plugin.sacks().totalCapacity(sack))),
                TextService.parsed("access", plugin.sacks().accessText(player, sack))
        );
    }

    private List<TextService.TextPlaceholder> sackItemPlaceholders(Player player, SackDefinition sack, SackItemDefinition item) {
        SkyBlockProfile profile = profiles.profile(player);
        return List.of(
                TextService.raw("sack_id", sack.id()),
                TextService.parsed("sack", sack.displayName()),
                TextService.raw("item_id", item.id()),
                TextService.parsed("item", item.displayName()),
                TextService.raw("stored", text.formatNumber(plugin.sacks().stored(profile, sack, item))),
                TextService.raw("capacity", text.formatNumber(sack.capacity(item)))
        );
    }

    private List<TextService.TextPlaceholder> quiverPlaceholders(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        String selected = plugin.quiver().selectedDefinition(profile)
                .map(QuiverItemDefinition::displayName)
                .orElse(text.rawMessage("quiver.selected-none"));
        return List.of(
                TextService.raw("stored", text.formatNumber(plugin.quiver().totalStored(profile))),
                TextService.raw("capacity", text.formatNumber(plugin.quiver().capacity())),
                TextService.parsed("selected", selected)
        );
    }

    private List<TextService.TextPlaceholder> quiverItemPlaceholders(Player player, QuiverItemDefinition item) {
        SkyBlockProfile profile = profiles.profile(player);
        boolean selected = plugin.quiver().selectedDefinition(profile)
                .map(selectedItem -> selectedItem.id().equals(item.id()))
                .orElse(false);
        return List.of(
                TextService.raw("item_id", item.id()),
                TextService.parsed("item", item.displayName()),
                TextService.raw("stored", text.formatNumber(plugin.quiver().stored(profile, item))),
                TextService.raw("capacity", text.formatNumber(plugin.quiver().capacity())),
                TextService.parsed("selected", text.rawMessage(selected ? "quiver.selected-active" : "quiver.selected-inactive"))
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
