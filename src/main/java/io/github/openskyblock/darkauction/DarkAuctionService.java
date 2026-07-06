package io.github.openskyblock.darkauction;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.mayor.MayorService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class DarkAuctionService {
    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final CustomItemService customItems;
    private final Map<String, DarkAuctionItemDefinition> items = new HashMap<>();
    private MayorService mayorService;
    private File dataFile;
    private YamlConfiguration data;
    private DarkAuctionLotState activeLot;
    private long epochMillis = 0L;
    private long intervalSeconds = 3600L;
    private long lotDurationSeconds = 180L;
    private int lotsPerSession = 3;
    private int pageSize = 8;
    private double minimumPurse = 400_000.0D;
    private double minBidIncrement = 10_000.0D;
    private double bidIncrementPercent = 0.05D;
    private boolean broadcastStart = true;
    private boolean broadcastAward = true;
    private boolean autoClaimOnlineWinners = true;

    public DarkAuctionService(JavaPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, CustomItemService customItems) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.customItems = customItems;
    }

    public void mayorService(MayorService mayorService) {
        this.mayorService = mayorService;
    }

    public void reload() {
        this.epochMillis = Math.max(0L, configService.darkAuction().getLong("settings.epoch-millis", 0L));
        this.intervalSeconds = Math.max(60L, configService.darkAuction().getLong("settings.interval-seconds", 3600L));
        this.lotDurationSeconds = Math.max(30L, configService.darkAuction().getLong("settings.lot-duration-seconds", 180L));
        this.lotsPerSession = Math.max(1, configService.darkAuction().getInt("settings.lots-per-session", 3));
        this.pageSize = Math.max(1, Math.min(20, configService.darkAuction().getInt("settings.page-size", 8)));
        this.minimumPurse = Math.max(0.0D, configService.darkAuction().getDouble("settings.minimum-purse", 400_000.0D));
        this.minBidIncrement = Math.max(0.0D, configService.darkAuction().getDouble("settings.min-bid-increment", 10_000.0D));
        this.bidIncrementPercent = Math.max(0.0D, configService.darkAuction().getDouble("settings.bid-increment-percent", 0.05D));
        this.broadcastStart = configService.darkAuction().getBoolean("settings.broadcast-start", true);
        this.broadcastAward = configService.darkAuction().getBoolean("settings.broadcast-award", true);
        this.autoClaimOnlineWinners = configService.darkAuction().getBoolean("settings.auto-claim-online-winners", true);
        loadItems();
    }

    public void load() {
        this.dataFile = new File(plugin.getDataFolder(), "dark_auction_state.yml");
        if (!dataFile.exists()) {
            try {
                File parent = dataFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                dataFile.createNewFile();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to create dark_auction_state.yml", exception);
            }
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        this.activeLot = loadLot(data.getConfigurationSection("active-lot"));
    }

    public void save() {
        if (data == null || dataFile == null) {
            return;
        }
        data.set("active-lot", null);
        if (activeLot != null && !activeLot.awarded()) {
            writeLot(activeLot);
        }
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Unable to save dark_auction_state.yml: " + exception.getMessage());
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.dark-auction", true);
    }

    public long tickIntervalTicks() {
        return Math.max(20L, configService.darkAuction().getLong("settings.tick-interval-ticks", 20L));
    }

    public List<String> itemIds() {
        return items().stream().map(DarkAuctionItemDefinition::id).toList();
    }

    public Optional<DarkAuctionItemDefinition> item(String itemId) {
        if (itemId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(items.get(itemId.toUpperCase(Locale.ROOT)));
    }

    public List<DarkAuctionItemDefinition> items() {
        return items.values().stream()
                .sorted(Comparator.comparing(DarkAuctionItemDefinition::id))
                .toList();
    }

    public void tick() {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (activeLot != null && activeLot.endsMillis() <= now) {
            award(activeLot);
            activeLot = null;
            save();
        }
        Optional<LotWindow> window = currentWindow(now);
        if (window.isEmpty()) {
            return;
        }
        LotWindow lotWindow = window.get();
        if (activeLot != null && activeLot.sessionId().equals(lotWindow.sessionId()) && activeLot.lotIndex() == lotWindow.lotIndex()) {
            return;
        }
        if (activeLot != null && !activeLot.awarded()) {
            award(activeLot);
        }
        DarkAuctionItemDefinition definition = selectItem(lotWindow.sessionIndex(), lotWindow.lotIndex()).orElse(null);
        if (definition == null) {
            activeLot = null;
            save();
            return;
        }
        activeLot = new DarkAuctionLotState(lotWindow.sessionId(), lotWindow.lotIndex(), definition.id(), lotWindow.endsMillis());
        save();
        if (broadcastStart) {
            broadcast("commands.dark-auction-started", lotPlaceholders(activeLot, definition));
        }
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.dark-auction-disabled");
            return;
        }
        tick();
        if (activeLot == null) {
            text.send(player, "commands.dark-auction-closed", List.of(
                    TextService.raw("next", formatDuration(secondsUntilNextSession())),
                    TextService.raw("minimum_purse", text.formatNumber(effectiveMinimumPurse()))
            ));
            return;
        }
        DarkAuctionItemDefinition definition = item(activeLot.itemId()).orElse(null);
        if (definition == null) {
            text.send(player, "commands.dark-auction-empty");
            return;
        }
        text.send(player, "commands.dark-auction-status", lotPlaceholders(activeLot, definition));
    }

    public void sendItems(Player player, int page) {
        if (!enabled()) {
            text.send(player, "commands.dark-auction-disabled");
            return;
        }
        List<DarkAuctionItemDefinition> listed = items();
        if (listed.isEmpty()) {
            text.send(player, "commands.dark-auction-empty");
            return;
        }
        int pages = Math.max(1, (int) Math.ceil(listed.size() / (double) pageSize));
        int currentPage = Math.max(1, Math.min(page, pages));
        text.send(player, "commands.dark-auction-items-header", List.of(
                TextService.raw("page", Integer.toString(currentPage)),
                TextService.raw("pages", Integer.toString(pages))
        ));
        int from = (currentPage - 1) * pageSize;
        int to = Math.min(listed.size(), from + pageSize);
        for (DarkAuctionItemDefinition definition : listed.subList(from, to)) {
            text.send(player, "commands.dark-auction-items-line", itemPlaceholders(definition));
        }
    }

    public boolean bid(Player player, double amount) {
        if (!enabled()) {
            text.send(player, "commands.dark-auction-disabled");
            return false;
        }
        tick();
        if (activeLot == null) {
            text.send(player, "commands.dark-auction-closed", List.of(
                    TextService.raw("next", formatDuration(secondsUntilNextSession())),
                    TextService.raw("minimum_purse", text.formatNumber(effectiveMinimumPurse()))
            ));
            return false;
        }
        DarkAuctionItemDefinition definition = item(activeLot.itemId()).orElse(null);
        if (definition == null) {
            text.send(player, "commands.dark-auction-empty");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (activeLot.highBidderId() != null && activeLot.highBidderId().equals(player.getUniqueId())) {
            text.send(player, "commands.dark-auction-already-high-bidder");
            return false;
        }
        double requiredPurse = effectiveMinimumPurse();
        if (profile.purse() < requiredPurse) {
            text.send(player, "commands.dark-auction-entry-no-money", List.of(TextService.raw("minimum_purse", text.formatNumber(requiredPurse))));
            return false;
        }
        if (definition.maxPurchasesPerProfile() > 0 && profile.darkAuctionPurchases(definition.id()) >= definition.maxPurchasesPerProfile()) {
            text.send(player, "commands.dark-auction-purchase-limit", itemPlaceholders(definition));
            return false;
        }
        double required = requiredBid(activeLot, definition);
        if (amount < required) {
            text.send(player, "commands.dark-auction-bid-too-low", List.of(
                    TextService.raw("required", text.formatNumber(required)),
                    TextService.raw("bid", text.formatNumber(amount))
            ));
            return false;
        }
        if (!economy.spendPurse(player, amount)) {
            text.send(player, "commands.dark-auction-no-money", List.of(TextService.raw("bid", text.formatNumber(amount))));
            return false;
        }
        refundHighBidder(activeLot);
        activeLot.highBidderId(player.getUniqueId());
        activeLot.highBidderName(player.getName());
        activeLot.highBid(amount);
        save();
        profiles.saveAll();
        List<TextService.TextPlaceholder> placeholders = lotPlaceholders(activeLot, definition);
        text.send(player, "commands.dark-auction-bid-accepted", placeholders);
        broadcastExcept(player, "commands.dark-auction-outbid-broadcast", placeholders);
        return true;
    }

    public boolean claim(Player player) {
        if (!enabled()) {
            text.send(player, "commands.dark-auction-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.darkAuctionClaims().isEmpty()) {
            text.send(player, "commands.dark-auction-claim-empty");
            return false;
        }
        List<ItemStack> remaining = new ArrayList<>();
        int delivered = 0;
        for (ItemStack itemStack : profile.darkAuctionClaims()) {
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack.clone());
            if (leftovers.isEmpty()) {
                delivered++;
                continue;
            }
            leftovers.values().forEach(leftover -> remaining.add(leftover.clone()));
        }
        profile.darkAuctionClaims().clear();
        profile.darkAuctionClaims().addAll(remaining);
        profiles.saveAll();
        if (delivered <= 0) {
            text.send(player, "commands.dark-auction-claim-no-space", List.of(TextService.raw("remaining", Integer.toString(remaining.size()))));
            return false;
        }
        text.send(player, "commands.dark-auction-claimed", List.of(
                TextService.raw("items", Integer.toString(delivered)),
                TextService.raw("remaining", Integer.toString(remaining.size()))
        ));
        return true;
    }

    public void sendPurchases(Player player) {
        if (!enabled()) {
            text.send(player, "commands.dark-auction-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.darkAuctionPurchases().isEmpty()) {
            text.send(player, "commands.dark-auction-purchases-empty");
            return;
        }
        text.send(player, "commands.dark-auction-purchases-header");
        profile.darkAuctionPurchases().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> text.send(player, "commands.dark-auction-purchases-line", List.of(
                        TextService.raw("id", entry.getKey()),
                        TextService.parsed("item", item(entry.getKey()).map(this::itemDisplay).orElse("<white>" + entry.getKey() + "</white>")),
                        TextService.raw("amount", Integer.toString(entry.getValue()))
                )));
    }

    private void loadItems() {
        items.clear();
        ConfigurationSection section = configService.darkAuction().getConfigurationSection("items");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(id);
            if (itemSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            String customItemId = itemSection.getString("custom-item", normalized).toUpperCase(Locale.ROOT);
            Optional<CustomItemDefinition> customDefinition = customItems.definition(customItemId);
            Material material = Material.matchMaterial(itemSection.getString("material", customDefinition.map(CustomItemDefinition::material).map(Material::name).orElse("CHEST")));
            String displayName = itemSection.getString("display-name", customDefinition.map(CustomItemDefinition::displayName).orElse(normalized));
            items.put(normalized, new DarkAuctionItemDefinition(
                    normalized,
                    customItemId,
                    material == null ? Material.CHEST : material,
                    displayName,
                    Math.max(1, Math.min(64, itemSection.getInt("amount", 1))),
                    Math.max(0.0D, itemSection.getDouble("starting-bid", 100_000.0D)),
                    Math.max(0.0D, itemSection.getDouble("weight", 1.0D)),
                    Math.max(0, itemSection.getInt("max-purchases-per-profile", 0)),
                    itemSection.getStringList("description")
            ));
        }
    }

    private Optional<DarkAuctionItemDefinition> selectItem(long sessionIndex, int lotIndex) {
        List<DarkAuctionItemDefinition> listed = items();
        if (listed.isEmpty()) {
            return Optional.empty();
        }
        double totalWeight = listed.stream().mapToDouble(DarkAuctionItemDefinition::weight).sum();
        Random random = new Random((sessionIndex * 1_103_515_245L) ^ (lotIndex * 31_415_927L));
        if (totalWeight <= 0.0D) {
            int index = Math.floorMod((int) (sessionIndex + lotIndex), listed.size());
            return Optional.of(listed.get(index));
        }
        double target = random.nextDouble() * totalWeight;
        double cursor = 0.0D;
        for (DarkAuctionItemDefinition definition : listed) {
            cursor += definition.weight();
            if (target <= cursor) {
                return Optional.of(definition);
            }
        }
        return Optional.of(listed.getLast());
    }

    private void award(DarkAuctionLotState lot) {
        if (lot == null || lot.awarded()) {
            return;
        }
        lot.awarded(true);
        DarkAuctionItemDefinition definition = item(lot.itemId()).orElse(null);
        if (!lot.hasBid()) {
            if (definition != null && broadcastAward) {
                broadcast("commands.dark-auction-no-sale", lotPlaceholders(lot, definition));
            }
            return;
        }
        SkyBlockProfile winnerProfile = profiles.profile(lot.highBidderId());
        if (winnerProfile == null || definition == null) {
            refundProfile(lot.highBidderId(), lot.highBid());
            Player winner = Bukkit.getPlayer(lot.highBidderId());
            if (winner != null) {
                text.send(winner, "commands.dark-auction-award-refunded", List.of(TextService.raw("bid", text.formatNumber(lot.highBid()))));
            }
            return;
        }
        ItemStack reward = createReward(definition);
        winnerProfile.addDarkAuctionPurchase(definition.id(), reward.getAmount());
        Player winner = Bukkit.getPlayer(lot.highBidderId());
        boolean stored = true;
        if (winner != null && autoClaimOnlineWinners) {
            Map<Integer, ItemStack> leftovers = winner.getInventory().addItem(reward.clone());
            stored = !leftovers.isEmpty();
            leftovers.values().forEach(leftover -> winnerProfile.darkAuctionClaims().add(leftover.clone()));
            text.send(winner, "commands.dark-auction-won", lotPlaceholders(lot, definition));
            if (stored) {
                text.send(winner, "commands.dark-auction-won-stored");
            }
        } else {
            winnerProfile.darkAuctionClaims().add(reward);
        }
        if (broadcastAward) {
            broadcast("commands.dark-auction-sold-broadcast", lotPlaceholders(lot, definition));
        }
        profiles.saveAll();
    }

    private ItemStack createReward(DarkAuctionItemDefinition definition) {
        Optional<CustomItemDefinition> customDefinition = customItems.definition(definition.customItemId());
        ItemStack itemStack;
        if (customDefinition.isPresent()) {
            itemStack = customItems.createItem(customDefinition.get());
        } else {
            itemStack = new ItemStack(definition.material());
            ItemMeta meta = itemStack.getItemMeta();
            meta.displayName(text.deserialize(definition.displayName()));
            if (!definition.description().isEmpty()) {
                meta.lore(definition.description().stream().map(text::deserialize).toList());
            }
            itemStack.setItemMeta(meta);
        }
        itemStack.setAmount(definition.amount());
        return itemStack;
    }

    private void refundHighBidder(DarkAuctionLotState lot) {
        if (lot == null || !lot.hasBid()) {
            return;
        }
        refundProfile(lot.highBidderId(), lot.highBid());
        Player previous = Bukkit.getPlayer(lot.highBidderId());
        if (previous != null) {
            text.send(previous, "commands.dark-auction-outbid-refund", List.of(
                    TextService.raw("coins", text.formatNumber(lot.highBid()))
            ));
        }
    }

    private void refundProfile(UUID playerId, double amount) {
        if (playerId == null || amount <= 0.0D) {
            return;
        }
        SkyBlockProfile profile = profiles.profile(playerId);
        if (profile != null) {
            profile.purse(profile.purse() + amount);
        }
    }

    private double requiredBid(DarkAuctionLotState lot, DarkAuctionItemDefinition definition) {
        if (lot == null || !lot.hasBid()) {
            return definition.startingBid();
        }
        return lot.highBid() + Math.max(minBidIncrement, lot.highBid() * bidIncrementPercent);
    }

    private List<TextService.TextPlaceholder> lotPlaceholders(DarkAuctionLotState lot, DarkAuctionItemDefinition definition) {
        double required = requiredBid(lot, definition);
        return List.of(
                TextService.raw("session", lot.sessionId()),
                TextService.raw("lot", Integer.toString(lot.lotIndex() + 1)),
                TextService.raw("lots", Integer.toString(effectiveLotsPerSession())),
                TextService.raw("id", definition.id()),
                TextService.parsed("item", itemDisplay(definition)),
                TextService.raw("starting_bid", text.formatNumber(definition.startingBid())),
                TextService.raw("high_bid", text.formatNumber(lot.highBid())),
                TextService.raw("high_bidder", lot.highBidderName() == null || lot.highBidderName().isBlank() ? text.rawMessage("dark-auction.no-bidder") : lot.highBidderName()),
                TextService.raw("required_bid", text.formatNumber(required)),
                TextService.raw("remaining", formatDuration(Math.max(0L, (lot.endsMillis() - System.currentTimeMillis() + 999L) / 1000L)))
        );
    }

    private List<TextService.TextPlaceholder> itemPlaceholders(DarkAuctionItemDefinition definition) {
        return List.of(
                TextService.raw("id", definition.id()),
                TextService.parsed("item", itemDisplay(definition)),
                TextService.raw("starting_bid", text.formatNumber(definition.startingBid())),
                TextService.raw("weight", text.formatNumber(definition.weight())),
                TextService.raw("amount", Integer.toString(definition.amount())),
                TextService.raw("limit", definition.maxPurchasesPerProfile() <= 0 ? text.rawMessage("dark-auction.no-limit") : Integer.toString(definition.maxPurchasesPerProfile())),
                TextService.raw("description", String.join(" ", definition.description()))
        );
    }

    private String itemDisplay(DarkAuctionItemDefinition definition) {
        String amount = definition.amount() > 1 ? definition.amount() + "x " : "";
        return "<white>" + amount + "</white>" + definition.displayName();
    }

    private Optional<LotWindow> currentWindow(long now) {
        if (now < epochMillis) {
            return Optional.empty();
        }
        long intervalMillis = intervalSeconds * 1000L;
        long lotMillis = lotDurationSeconds * 1000L;
        long sessionIndex = Math.floorDiv(now - epochMillis, intervalMillis);
        long sessionStart = epochMillis + sessionIndex * intervalMillis;
        int lots = effectiveLotsPerSession();
        long activeMillis = Math.min(intervalMillis, lotMillis * lots);
        long offset = now - sessionStart;
        if (offset < 0L || offset >= activeMillis) {
            return Optional.empty();
        }
        int lotIndex = (int) Math.min(lots - 1L, offset / lotMillis);
        long lotEnd = Math.min(sessionStart + (lotIndex + 1L) * lotMillis, sessionStart + activeMillis);
        return Optional.of(new LotWindow(Long.toString(sessionStart), sessionIndex, lotIndex, lotEnd));
    }

    private long secondsUntilNextSession() {
        long now = System.currentTimeMillis();
        long intervalMillis = intervalSeconds * 1000L;
        long lotMillis = lotDurationSeconds * 1000L;
        long activeMillis = Math.min(intervalMillis, lotMillis * effectiveLotsPerSession());
        if (now < epochMillis) {
            return (epochMillis - now + 999L) / 1000L;
        }
        long sessionIndex = Math.floorDiv(now - epochMillis, intervalMillis);
        long sessionStart = epochMillis + sessionIndex * intervalMillis;
        long activeEnd = sessionStart + activeMillis;
        if (now < activeEnd) {
            return 0L;
        }
        return Math.max(0L, (sessionStart + intervalMillis - now + 999L) / 1000L);
    }

    private DarkAuctionLotState loadLot(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        try {
            DarkAuctionLotState lot = new DarkAuctionLotState(
                    section.getString("session-id", ""),
                    section.getInt("lot-index", 0),
                    section.getString("item-id", ""),
                    section.getLong("ends-millis", 0L)
            );
            String bidder = section.getString("high-bidder", null);
            if (bidder != null && !bidder.isBlank()) {
                lot.highBidderId(UUID.fromString(bidder));
            }
            lot.highBidderName(section.getString("high-bidder-name", null));
            lot.highBid(section.getDouble("high-bid", 0.0D));
            lot.awarded(section.getBoolean("awarded", false));
            return lot;
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Skipping invalid Dark Auction state in dark_auction_state.yml.");
            return null;
        }
    }

    private void writeLot(DarkAuctionLotState lot) {
        data.set("active-lot.session-id", lot.sessionId());
        data.set("active-lot.lot-index", lot.lotIndex());
        data.set("active-lot.item-id", lot.itemId());
        data.set("active-lot.ends-millis", lot.endsMillis());
        data.set("active-lot.high-bidder", lot.highBidderId() == null ? null : lot.highBidderId().toString());
        data.set("active-lot.high-bidder-name", lot.highBidderName());
        data.set("active-lot.high-bid", lot.highBid());
        data.set("active-lot.awarded", lot.awarded());
    }

    private void broadcast(String path, List<TextService.TextPlaceholder> placeholders) {
        Bukkit.getOnlinePlayers().forEach(player -> text.send(player, path, placeholders));
    }

    private void broadcastExcept(Player excluded, String path, List<TextService.TextPlaceholder> placeholders) {
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.getUniqueId().equals(excluded.getUniqueId()))
                .forEach(player -> text.send(player, path, placeholders));
    }

    private int effectiveLotsPerSession() {
        int extraLots = mayorService == null ? 0 : (int) Math.floor(Math.max(0.0D, mayorService.modifier("dark_auction_extra_lots")));
        return Math.max(1, lotsPerSession + extraLots);
    }

    private double effectiveMinimumPurse() {
        double reduction = mayorService == null ? 0.0D : Math.max(0.0D, mayorService.modifier("dark_auction_minimum_purse_reduction"));
        return Math.max(0.0D, minimumPurse * Math.max(0.0D, 1.0D - reduction));
    }

    private String formatDuration(long totalSeconds) {
        if (totalSeconds >= 86400L) {
            long days = totalSeconds / 86400L;
            long hours = (totalSeconds % 86400L) / 3600L;
            return days + "d " + hours + "h";
        }
        if (totalSeconds >= 3600L) {
            long hours = totalSeconds / 3600L;
            long minutes = (totalSeconds % 3600L) / 60L;
            return hours + "h " + minutes + "m";
        }
        if (totalSeconds >= 60L) {
            long minutes = totalSeconds / 60L;
            long seconds = totalSeconds % 60L;
            return minutes + "m " + seconds + "s";
        }
        return totalSeconds + "s";
    }

    private record LotWindow(String sessionId, long sessionIndex, int lotIndex, long endsMillis) {
    }
}
