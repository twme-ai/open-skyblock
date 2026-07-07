package io.github.openskyblock.auction;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.mayor.MayorService;
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
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionService {
    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final EconomyService economy;
    private final CustomItemService customItems;
    private final Map<String, AuctionListing> listings = new HashMap<>();
    private final Map<UUID, Double> pendingBidRefunds = new HashMap<>();
    private MayorService mayors;
    private File dataFile;
    private YamlConfiguration data;
    private long listingDurationSeconds = 172800L;
    private int maxActivePerPlayer = 14;
    private int pageSize = 8;
    private double minPrice = 1.0D;
    private double maxPrice = 1_000_000_000.0D;
    private long cancelGraceSeconds = 60L;
    private double listingFeePercent = 0.01D;
    private double minimumBidIncrement = 1.0D;
    private double minimumBidIncrementPercent = 0.05D;

    public AuctionService(JavaPlugin plugin, ConfigService configService, TextService text, EconomyService economy, CustomItemService customItems) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.economy = economy;
        this.customItems = customItems;
    }

    public void mayorService(MayorService mayors) {
        this.mayors = mayors;
    }

    public void reload() {
        this.listingDurationSeconds = Math.max(60L, configService.auctions().getLong("settings.listing-duration-seconds", 172800L));
        this.maxActivePerPlayer = Math.max(1, configService.auctions().getInt("settings.max-active-per-player", 14));
        this.pageSize = Math.max(1, Math.min(20, configService.auctions().getInt("settings.page-size", 8)));
        this.minPrice = Math.max(0.01D, configService.auctions().getDouble("settings.min-price", 1.0D));
        this.maxPrice = Math.max(minPrice, configService.auctions().getDouble("settings.max-price", 1_000_000_000.0D));
        this.cancelGraceSeconds = Math.max(0L, configService.auctions().getLong("settings.cancel-grace-seconds", 60L));
        this.listingFeePercent = Math.max(0.0D, configService.auctions().getDouble("settings.listing-fee-percent", 0.01D));
        this.minimumBidIncrement = Math.max(0.01D, configService.auctions().getDouble("settings.minimum-bid-increment", 1.0D));
        this.minimumBidIncrementPercent = Math.max(0.0D, configService.auctions().getDouble("settings.minimum-bid-increment-percent", 0.05D));
    }

    public void load() {
        this.dataFile = new File(plugin.getDataFolder(), "auction_house.yml");
        if (!dataFile.exists()) {
            try {
                File parent = dataFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                dataFile.createNewFile();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to create auction_house.yml", exception);
            }
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        listings.clear();
        pendingBidRefunds.clear();
        ConfigurationSection section = data.getConfigurationSection("listings");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                AuctionListing listing = loadListing(id, section.getConfigurationSection(id));
                if (listing != null) {
                    listings.put(listing.id(), listing);
                }
            }
        }
        ConfigurationSection refunds = data.getConfigurationSection("bid-refunds");
        if (refunds != null) {
            for (String key : refunds.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    double coins = refunds.getDouble(key, 0.0D);
                    if (coins > 0.0D) {
                        pendingBidRefunds.put(playerId, coins);
                    }
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Skipping invalid auction bid refund owner in auction_house.yml: " + key);
                }
            }
        }
    }

    public void save() {
        if (data == null || dataFile == null) {
            return;
        }
        data.set("listings", null);
        listings.values().stream()
                .sorted(Comparator.comparing(AuctionListing::createdMillis))
                .forEach(this::writeListing);
        data.set("bid-refunds", null);
        for (Map.Entry<UUID, Double> entry : pendingBidRefunds.entrySet()) {
            if (entry.getValue() > 0.0D) {
                data.set("bid-refunds." + entry.getKey(), entry.getValue());
            }
        }
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Unable to save auction_house.yml: " + exception.getMessage());
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.auction-house", true);
    }

    public List<String> listingIds() {
        long now = System.currentTimeMillis();
        return listings.values().stream()
                .filter(listing -> listing.active(now))
                .map(AuctionListing::id)
                .sorted()
                .toList();
    }

    public boolean create(Player player, double price) {
        return createListing(player, price, true);
    }

    public boolean createBid(Player player, double startingBid) {
        return createListing(player, startingBid, false);
    }

    private boolean createListing(Player player, double price, boolean bin) {
        if (!enabled()) {
            text.send(player, "commands.auction-disabled");
            return false;
        }
        if (price < minPrice || price > maxPrice) {
            text.send(player, "commands.auction-price-invalid", List.of(
                    TextService.raw("min", text.formatNumber(minPrice)),
                    TextService.raw("max", text.formatNumber(maxPrice))
            ));
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            text.send(player, "commands.auction-held-missing");
            return false;
        }
        if (customItems.soulbound(held)) {
            text.send(player, "commands.soulbound-blocked-auction");
            return false;
        }
        long activeCount = listings.values().stream()
                .filter(listing -> listing.sellerId().equals(player.getUniqueId()))
                .filter(listing -> listing.active(System.currentTimeMillis()))
                .count();
        if (activeCount >= maxActivePerPlayer) {
            text.send(player, "commands.auction-too-many", List.of(TextService.raw("limit", Integer.toString(maxActivePerPlayer))));
            return false;
        }
        double fee = listingFee(price);
        if (!economy.spendPurse(player, fee)) {
            text.send(player, "commands.auction-fee-missing", List.of(TextService.raw("fee", text.formatNumber(fee))));
            return false;
        }
        long now = System.currentTimeMillis();
        ItemStack itemStack = held.clone();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        AuctionListing listing = new AuctionListing(
                nextId(),
                player.getUniqueId(),
                player.getName(),
                itemStack,
                price,
                bin,
                now,
                now + listingDurationSeconds * 1000L
        );
        listings.put(listing.id(), listing);
        save();
        text.send(player, bin ? "commands.auction-created" : "commands.auction-bid-created", List.of(
                TextService.raw("id", listing.id()),
                TextService.parsed("item", itemDisplay(itemStack)),
                TextService.raw("price", text.formatNumber(price)),
                TextService.raw("starting_bid", text.formatNumber(price)),
                TextService.raw("fee", text.formatNumber(fee)),
                TextService.raw("duration", formatDuration(listingDurationSeconds))
        ));
        return true;
    }

    private double listingFee(double price) {
        return Math.max(0.0D, price * listingFeePercent * auctionTaxMultiplier());
    }

    private double auctionTaxMultiplier() {
        if (mayors == null) {
            return 1.0D;
        }
        double multiplier = mayors.modifier("auction_tax_multiplier");
        return multiplier <= 0.0D ? 1.0D : multiplier;
    }

    public void sendListings(Player player, int page) {
        if (!enabled()) {
            text.send(player, "commands.auction-disabled");
            return;
        }
        List<AuctionListing> active = activeListings();
        if (active.isEmpty()) {
            text.send(player, "commands.auction-list-empty");
            return;
        }
        int pages = Math.max(1, (int) Math.ceil(active.size() / (double) pageSize));
        int currentPage = Math.max(1, Math.min(page, pages));
        text.send(player, "commands.auction-list-header", List.of(
                TextService.raw("page", Integer.toString(currentPage)),
                TextService.raw("pages", Integer.toString(pages))
        ));
        int from = (currentPage - 1) * pageSize;
        int to = Math.min(active.size(), from + pageSize);
        for (AuctionListing listing : active.subList(from, to)) {
            text.send(player, "commands.auction-list-line", listingPlaceholders(listing, System.currentTimeMillis()));
        }
    }

    public boolean buy(Player buyer, String listingId) {
        if (!enabled()) {
            text.send(buyer, "commands.auction-disabled");
            return false;
        }
        AuctionListing listing = findListing(listingId).orElse(null);
        if (listing == null) {
            text.send(buyer, "commands.auction-unknown", List.of(TextService.raw("id", listingId == null ? "" : listingId)));
            return false;
        }
        long now = System.currentTimeMillis();
        if (!listing.active(now)) {
            text.send(buyer, "commands.auction-not-active");
            return false;
        }
        if (!listing.bin()) {
            text.send(buyer, "commands.auction-use-bid");
            return false;
        }
        if (listing.sellerId().equals(buyer.getUniqueId())) {
            text.send(buyer, "commands.auction-own-buy");
            return false;
        }
        if (!economy.spendPurse(buyer, listing.price())) {
            text.send(buyer, "commands.auction-no-money");
            return false;
        }
        listing.buyerId(buyer.getUniqueId());
        listing.buyerName(buyer.getName());
        listing.soldMillis(now);
        giveOrDrop(buyer, listing.itemStack());
        save();
        text.send(buyer, "commands.auction-bought", List.of(
                TextService.raw("id", listing.id()),
                TextService.parsed("item", itemDisplay(listing.itemStack())),
                TextService.raw("seller", listing.sellerName()),
                TextService.raw("price", text.formatNumber(listing.price()))
        ));
        Player seller = Bukkit.getPlayer(listing.sellerId());
        if (seller != null) {
            text.send(seller, "commands.auction-sold-seller", List.of(
                    TextService.raw("id", listing.id()),
                    TextService.parsed("item", itemDisplay(listing.itemStack())),
                    TextService.raw("buyer", buyer.getName()),
                    TextService.raw("price", text.formatNumber(listing.price()))
            ));
        }
        return true;
    }

    public boolean bid(Player bidder, String listingId, double bid) {
        if (!enabled()) {
            text.send(bidder, "commands.auction-disabled");
            return false;
        }
        AuctionListing listing = findListing(listingId).orElse(null);
        if (listing == null) {
            text.send(bidder, "commands.auction-unknown", List.of(TextService.raw("id", listingId == null ? "" : listingId)));
            return false;
        }
        long now = System.currentTimeMillis();
        if (!listing.active(now)) {
            text.send(bidder, "commands.auction-not-active");
            return false;
        }
        if (listing.bin()) {
            text.send(bidder, "commands.auction-use-buy");
            return false;
        }
        if (listing.sellerId().equals(bidder.getUniqueId())) {
            text.send(bidder, "commands.auction-own-bid");
            return false;
        }
        if (listing.highBidderId() != null && listing.highBidderId().equals(bidder.getUniqueId())) {
            text.send(bidder, "commands.auction-already-high-bidder");
            return false;
        }
        double required = requiredBid(listing);
        if (bid < required || bid > maxPrice) {
            text.send(bidder, "commands.auction-bid-too-low", List.of(
                    TextService.raw("bid", text.formatNumber(bid)),
                    TextService.raw("required", text.formatNumber(required)),
                    TextService.raw("max", text.formatNumber(maxPrice))
            ));
            return false;
        }
        if (!economy.spendPurse(bidder, bid)) {
            text.send(bidder, "commands.auction-no-money");
            return false;
        }
        UUID previousBidderId = listing.highBidderId();
        String previousBidderName = listing.highBidderName();
        double previousBid = listing.highBid();
        listing.highBidderId(bidder.getUniqueId());
        listing.highBidderName(bidder.getName());
        listing.highBid(bid);
        refundOutbid(previousBidderId, previousBidderName, previousBid, listing);
        save();
        text.send(bidder, "commands.auction-bid-accepted", List.of(
                TextService.raw("id", listing.id()),
                TextService.parsed("item", itemDisplay(listing.itemStack())),
                TextService.raw("bid", text.formatNumber(bid)),
                TextService.raw("remaining", formatDuration(Math.max(0L, (listing.expiresMillis() - now) / 1000L)))
        ));
        Player seller = Bukkit.getPlayer(listing.sellerId());
        if (seller != null) {
            text.send(seller, "commands.auction-bid-seller", List.of(
                    TextService.raw("id", listing.id()),
                    TextService.parsed("item", itemDisplay(listing.itemStack())),
                    TextService.raw("bidder", bidder.getName()),
                    TextService.raw("bid", text.formatNumber(bid))
            ));
        }
        return true;
    }

    public boolean cancel(Player player, String listingId) {
        if (!enabled()) {
            text.send(player, "commands.auction-disabled");
            return false;
        }
        AuctionListing listing = findListing(listingId).orElse(null);
        if (listing == null) {
            text.send(player, "commands.auction-unknown", List.of(TextService.raw("id", listingId == null ? "" : listingId)));
            return false;
        }
        if (!listing.sellerId().equals(player.getUniqueId())) {
            text.send(player, "errors.no-permission");
            return false;
        }
        long now = System.currentTimeMillis();
        if (!listing.active(now)) {
            text.send(player, "commands.auction-not-active");
            return false;
        }
        if (!listing.bin() && listing.hasBid()) {
            text.send(player, "commands.auction-cancel-has-bid");
            return false;
        }
        long waitSeconds = cancelGraceSeconds - ((now - listing.createdMillis()) / 1000L);
        if (waitSeconds > 0L) {
            text.send(player, "commands.auction-cancel-wait", List.of(TextService.raw("remaining", formatDuration(waitSeconds))));
            return false;
        }
        listing.cancelled(true);
        listings.remove(listing.id());
        giveOrDrop(player, listing.itemStack());
        save();
        text.send(player, "commands.auction-cancelled", List.of(
                TextService.raw("id", listing.id()),
                TextService.parsed("item", itemDisplay(listing.itemStack()))
        ));
        return true;
    }

    public boolean claim(Player player) {
        if (!enabled()) {
            text.send(player, "commands.auction-disabled");
            return false;
        }
        long now = System.currentTimeMillis();
        double coins = 0.0D;
        int items = 0;
        List<String> removable = new ArrayList<>();
        Double refundedBid = pendingBidRefunds.remove(player.getUniqueId());
        if (refundedBid != null && refundedBid > 0.0D) {
            coins += refundedBid;
        }
        for (AuctionListing listing : listings.values()) {
            if (listing.sellerId().equals(player.getUniqueId()) && !listing.sellerClaimed() && listing.bin() && listing.sold()) {
                coins += listing.price();
                listing.sellerClaimed(true);
            } else if (listing.sellerId().equals(player.getUniqueId()) && !listing.sellerClaimed() && listing.expired(now) && listing.bin()) {
                giveOrDrop(player, listing.itemStack());
                items++;
                listing.sellerClaimed(true);
            } else if (listing.sellerId().equals(player.getUniqueId()) && !listing.sellerClaimed() && listing.expired(now) && !listing.bin()) {
                if (listing.hasBid()) {
                    coins += listing.highBid();
                    listing.sellerClaimed(true);
                    listing.buyerId(listing.highBidderId());
                    listing.buyerName(listing.highBidderName());
                    listing.soldMillis(now);
                } else {
                    giveOrDrop(player, listing.itemStack());
                    items++;
                    listing.sellerClaimed(true);
                }
            }
            if (!listing.bin() && listing.hasBid() && listing.expired(now) && !listing.buyerClaimed() && listing.highBidderId().equals(player.getUniqueId())) {
                giveOrDrop(player, listing.itemStack());
                items++;
                listing.buyerClaimed(true);
                listing.buyerId(player.getUniqueId());
                listing.buyerName(player.getName());
                listing.soldMillis(now);
            }
            if (listing.fullyClaimed(now)) {
                removable.add(listing.id());
            }
        }
        if (coins <= 0.0D && items <= 0) {
            text.send(player, "commands.auction-claim-empty");
            return false;
        }
        if (coins > 0.0D) {
            economy.addPurse(player, coins);
        }
        removable.forEach(listings::remove);
        save();
        text.send(player, "commands.auction-claimed", List.of(
                TextService.raw("coins", text.formatNumber(coins)),
                TextService.raw("items", Integer.toString(items))
        ));
        return true;
    }

    public void sendMine(Player player) {
        if (!enabled()) {
            text.send(player, "commands.auction-disabled");
            return;
        }
        List<AuctionListing> own = listings.values().stream()
                .filter(listing -> listing.sellerId().equals(player.getUniqueId()))
                .sorted(Comparator.comparing(AuctionListing::createdMillis).reversed())
                .toList();
        if (own.isEmpty()) {
            text.send(player, "commands.auction-mine-empty");
            return;
        }
        text.send(player, "commands.auction-mine-header");
        long now = System.currentTimeMillis();
        for (AuctionListing listing : own) {
            text.send(player, "commands.auction-mine-line", listingPlaceholders(listing, now));
        }
    }

    private AuctionListing loadListing(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        try {
            UUID sellerId = UUID.fromString(section.getString("seller", ""));
            ItemStack itemStack = section.getItemStack("item");
            if (itemStack == null || itemStack.getType().isAir()) {
                plugin.getLogger().warning("Skipping auction listing with missing item: " + id);
                return null;
            }
            AuctionListing listing = new AuctionListing(
                    id,
                    sellerId,
                    section.getString("seller-name", "Unknown"),
                    itemStack,
                    section.getDouble("price", 0.0D),
                    section.getBoolean("bin", true),
                    section.getLong("created-millis", System.currentTimeMillis()),
                    section.getLong("expires-millis", System.currentTimeMillis())
            );
            String highBidder = section.getString("high-bidder", null);
            if (highBidder != null && !highBidder.isBlank()) {
                listing.highBidderId(UUID.fromString(highBidder));
            }
            listing.highBidderName(section.getString("high-bidder-name", null));
            listing.highBid(section.getDouble("high-bid", 0.0D));
            String buyer = section.getString("buyer", null);
            if (buyer != null && !buyer.isBlank()) {
                listing.buyerId(UUID.fromString(buyer));
            }
            listing.buyerName(section.getString("buyer-name", null));
            listing.soldMillis(section.getLong("sold-millis", 0L));
            listing.sellerClaimed(section.getBoolean("seller-claimed", false));
            listing.buyerClaimed(section.getBoolean("buyer-claimed", false));
            listing.cancelled(section.getBoolean("cancelled", false));
            return listing;
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Skipping invalid auction listing in auction_house.yml: " + id);
            return null;
        }
    }

    private void writeListing(AuctionListing listing) {
        String base = "listings." + listing.id();
        data.set(base + ".seller", listing.sellerId().toString());
        data.set(base + ".seller-name", listing.sellerName());
        data.set(base + ".item", listing.itemStack());
        data.set(base + ".price", listing.price());
        data.set(base + ".bin", listing.bin());
        data.set(base + ".created-millis", listing.createdMillis());
        data.set(base + ".expires-millis", listing.expiresMillis());
        data.set(base + ".high-bidder", listing.highBidderId() == null ? null : listing.highBidderId().toString());
        data.set(base + ".high-bidder-name", listing.highBidderName());
        data.set(base + ".high-bid", listing.highBid());
        data.set(base + ".buyer", listing.buyerId() == null ? null : listing.buyerId().toString());
        data.set(base + ".buyer-name", listing.buyerName());
        data.set(base + ".sold-millis", listing.soldMillis());
        data.set(base + ".seller-claimed", listing.sellerClaimed());
        data.set(base + ".buyer-claimed", listing.buyerClaimed());
        data.set(base + ".cancelled", listing.cancelled());
    }

    private List<AuctionListing> activeListings() {
        long now = System.currentTimeMillis();
        return listings.values().stream()
                .filter(listing -> listing.active(now))
                .sorted(Comparator.comparing(AuctionListing::createdMillis).reversed())
                .toList();
    }

    private Optional<AuctionListing> findListing(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawId.toLowerCase(Locale.ROOT);
        AuctionListing exact = listings.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }
        List<AuctionListing> matches = listings.values().stream()
                .filter(listing -> listing.id().startsWith(normalized))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private String nextId() {
        String id;
        do {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
        } while (listings.containsKey(id));
        return id;
    }

    private List<TextService.TextPlaceholder> listingPlaceholders(AuctionListing listing, long now) {
        return List.of(
                TextService.raw("id", listing.id()),
                TextService.parsed("item", itemDisplay(listing.itemStack())),
                TextService.raw("seller", listing.sellerName()),
                TextService.raw("price", text.formatNumber(listing.price())),
                TextService.parsed("type", listing.bin() ? text.rawMessage("auctions.type-bin") : text.rawMessage("auctions.type-bid")),
                TextService.raw("starting_bid", text.formatNumber(listing.price())),
                TextService.raw("high_bid", text.formatNumber(listing.highBid())),
                TextService.raw("high_bidder", listing.highBidderName() == null ? text.rawMessage("auctions.no-bidder") : listing.highBidderName()),
                TextService.raw("required_bid", text.formatNumber(requiredBid(listing))),
                TextService.parsed("sale", saleSummary(listing)),
                TextService.parsed("status", status(listing, now)),
                TextService.raw("remaining", formatDuration(Math.max(0L, (listing.expiresMillis() - now) / 1000L)))
        );
    }

    private String saleSummary(AuctionListing listing) {
        if (listing.bin()) {
            return "<gray>BIN:</gray> <gold>" + text.formatNumber(listing.price()) + "</gold><gray> coins</gray>";
        }
        if (listing.hasBid()) {
            return "<gray>High Bid:</gray> <gold>" + text.formatNumber(listing.highBid()) + "</gold><gray> by</gray> <yellow>" + listing.highBidderName() + "</yellow> <dark_gray>|</dark_gray> <gray>Next:</gray> <gold>" + text.formatNumber(requiredBid(listing)) + "</gold>";
        }
        return "<gray>Starting Bid:</gray> <gold>" + text.formatNumber(listing.price()) + "</gold><gray> coins</gray> <dark_gray>|</dark_gray> <gray>Next:</gray> <gold>" + text.formatNumber(requiredBid(listing)) + "</gold>";
    }

    private String status(AuctionListing listing, long now) {
        if (listing.sold()) {
            return text.rawMessage("auctions.status-sold");
        }
        if (!listing.bin() && listing.expired(now) && listing.hasBid()) {
            return text.rawMessage("auctions.status-ended");
        }
        if (listing.expired(now)) {
            return text.rawMessage("auctions.status-expired");
        }
        return text.rawMessage("auctions.status-active");
    }

    private double requiredBid(AuctionListing listing) {
        return listing.hasBid()
                ? Math.max(listing.highBid() + minimumBidIncrement, listing.highBid() * (1.0D + minimumBidIncrementPercent))
                : listing.price();
    }

    private void refundOutbid(UUID bidderId, String bidderName, double coins, AuctionListing listing) {
        if (bidderId == null || coins <= 0.0D) {
            return;
        }
        Player previous = Bukkit.getPlayer(bidderId);
        if (previous == null) {
            pendingBidRefunds.put(bidderId, pendingBidRefunds.getOrDefault(bidderId, 0.0D) + coins);
            return;
        }
        economy.addPurse(previous, coins);
        text.send(previous, "commands.auction-outbid-refund", List.of(
                TextService.raw("id", listing.id()),
                TextService.parsed("item", itemDisplay(listing.itemStack())),
                TextService.raw("coins", text.formatNumber(coins))
        ));
    }

    private String itemDisplay(ItemStack itemStack) {
        String amount = itemStack.getAmount() > 1 ? itemStack.getAmount() + "x " : "";
        Optional<CustomItemDefinition> definition = customItems.definition(itemStack);
        if (definition.isPresent()) {
            return "<white>" + amount + "</white>" + definition.get().displayName();
        }
        String materialName = itemStack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (materialName.isBlank()) {
            materialName = itemStack.getType().name();
        } else {
            materialName = Character.toUpperCase(materialName.charAt(0)) + materialName.substring(1);
        }
        return "<white>" + amount + materialName + "</white>";
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

    private void giveOrDrop(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack.clone()).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
