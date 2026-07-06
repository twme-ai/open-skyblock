package io.github.openskyblock.bazaar;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
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
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class BazaarService {
    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final EconomyService economy;
    private final CustomItemService customItems;
    private final Map<String, BazaarProductDefinition> products = new HashMap<>();
    private final Map<String, BazaarOrder> orders = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;
    private int pageSize = 8;
    private int maxActiveOrdersPerPlayer = 14;
    private long maxInstantAmount = 71680L;
    private double maxSellOfferPriceMultiplier = 1.5D;

    public BazaarService(JavaPlugin plugin, ConfigService configService, TextService text, EconomyService economy, CustomItemService customItems) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.economy = economy;
        this.customItems = customItems;
    }

    public void reload() {
        products.clear();
        this.pageSize = Math.max(1, Math.min(20, configService.bazaar().getInt("settings.page-size", 8)));
        this.maxActiveOrdersPerPlayer = Math.max(1, configService.bazaar().getInt("settings.max-active-orders-per-player", 14));
        this.maxInstantAmount = Math.max(1L, configService.bazaar().getLong("settings.max-instant-amount", 71680L));
        this.maxSellOfferPriceMultiplier = Math.max(1.0D, configService.bazaar().getDouble("settings.max-sell-offer-price-multiplier", 1.5D));
        ConfigurationSection section = configService.bazaar().getConfigurationSection("products");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection productSection = section.getConfigurationSection(id);
            if (productSection == null) {
                continue;
            }
            Material material = Material.matchMaterial(productSection.getString("material", id));
            if (material == null) {
                plugin.getLogger().warning("Skipping Bazaar product with invalid material: " + id);
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            products.put(normalized, new BazaarProductDefinition(
                    normalized,
                    productSection.getString("category", "MISC").toUpperCase(Locale.ROOT),
                    material,
                    productSection.getString("custom-item", ""),
                    productSection.getString("display-name", id),
                    Math.max(1, Math.min(99, productSection.getInt("stack-size", material.getMaxStackSize())))
            ));
        }
    }

    public void load() {
        this.dataFile = new File(plugin.getDataFolder(), "bazaar_data.yml");
        if (!dataFile.exists()) {
            try {
                File parent = dataFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                dataFile.createNewFile();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to create bazaar_data.yml", exception);
            }
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        orders.clear();
        ConfigurationSection section = data.getConfigurationSection("orders");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            BazaarOrder order = loadOrder(id, section.getConfigurationSection(id));
            if (order != null) {
                orders.put(order.id(), order);
            }
        }
    }

    public void save() {
        if (data == null || dataFile == null) {
            return;
        }
        data.set("orders", null);
        orders.values().stream()
                .sorted(Comparator.comparing(BazaarOrder::createdMillis))
                .forEach(this::writeOrder);
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Unable to save bazaar_data.yml: " + exception.getMessage());
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.bazaar", true);
    }

    public List<BazaarProductDefinition> products() {
        return products.values().stream()
                .sorted(Comparator.comparing(BazaarProductDefinition::category).thenComparing(BazaarProductDefinition::id))
                .toList();
    }

    public List<String> productIds() {
        return products().stream().map(BazaarProductDefinition::id).toList();
    }

    public List<String> orderIds(Player player) {
        return orders.values().stream()
                .filter(order -> order.ownerId().equals(player.getUniqueId()))
                .map(BazaarOrder::id)
                .sorted()
                .toList();
    }

    public Optional<BazaarProductDefinition> product(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(products.get(id.toUpperCase(Locale.ROOT)));
    }

    public void sendProducts(Player player, int page) {
        if (!enabled()) {
            text.send(player, "commands.bazaar-disabled");
            return;
        }
        List<BazaarProductDefinition> listed = products();
        if (listed.isEmpty()) {
            text.send(player, "commands.bazaar-products-empty");
            return;
        }
        int pages = Math.max(1, (int) Math.ceil(listed.size() / (double) pageSize));
        int currentPage = Math.max(1, Math.min(page, pages));
        text.send(player, "commands.bazaar-products-header", List.of(
                TextService.raw("page", Integer.toString(currentPage)),
                TextService.raw("pages", Integer.toString(pages))
        ));
        int from = (currentPage - 1) * pageSize;
        int to = Math.min(listed.size(), from + pageSize);
        for (BazaarProductDefinition product : listed.subList(from, to)) {
            text.send(player, "commands.bazaar-products-line", productPlaceholders(product));
        }
    }

    public void sendInfo(Player player, String productId) {
        if (!enabled()) {
            text.send(player, "commands.bazaar-disabled");
            return;
        }
        BazaarProductDefinition product = product(productId).orElse(null);
        if (product == null) {
            text.send(player, "commands.bazaar-unknown-product", List.of(TextService.raw("product", productId == null ? "" : productId)));
            return;
        }
        text.send(player, "commands.bazaar-info", productPlaceholders(product));
    }

    public boolean instantBuy(Player player, String productId, long amount) {
        if (!enabled()) {
            text.send(player, "commands.bazaar-disabled");
            return false;
        }
        BazaarProductDefinition product = product(productId).orElse(null);
        if (product == null) {
            text.send(player, "commands.bazaar-unknown-product", List.of(TextService.raw("product", productId == null ? "" : productId)));
            return false;
        }
        if (!validAmount(player, amount)) {
            return false;
        }
        List<BazaarOrder> sellOrders = openSellOrders(product.id());
        long available = sellOrders.stream().mapToLong(BazaarOrder::remainingAmount).sum();
        if (available < amount) {
            text.send(player, "commands.bazaar-not-enough-sell-offers", List.of(
                    TextService.parsed("product", product.displayName()),
                    TextService.raw("available", text.formatNumber(available))
            ));
            return false;
        }
        double totalCost = quoteCost(sellOrders, amount);
        if (!economy.spendPurse(player, totalCost)) {
            text.send(player, "commands.bazaar-no-money", List.of(TextService.raw("cost", text.formatNumber(totalCost))));
            return false;
        }
        long remaining = amount;
        for (BazaarOrder order : sellOrders) {
            if (remaining <= 0L) {
                break;
            }
            long fill = Math.min(remaining, order.remainingAmount());
            order.remainingAmount(order.remainingAmount() - fill);
            order.claimableCoins(order.claimableCoins() + fill * order.pricePerUnit());
            remaining -= fill;
        }
        giveOrDrop(player, product, amount);
        pruneCompletedWithoutClaims();
        save();
        text.send(player, "commands.bazaar-instant-buy", List.of(
                TextService.raw("amount", text.formatNumber(amount)),
                TextService.parsed("product", product.displayName()),
                TextService.raw("coins", text.formatNumber(totalCost))
        ));
        return true;
    }

    public boolean instantSell(Player player, String productId, long amount) {
        if (!enabled()) {
            text.send(player, "commands.bazaar-disabled");
            return false;
        }
        BazaarProductDefinition product = product(productId).orElse(null);
        if (product == null) {
            text.send(player, "commands.bazaar-unknown-product", List.of(TextService.raw("product", productId == null ? "" : productId)));
            return false;
        }
        long held = countMatching(player, product);
        long soulboundHeld = countSoulboundMatching(player, product);
        long resolvedAmount = amount <= 0L ? held : amount;
        if (resolvedAmount <= 0L && soulboundHeld > 0L) {
            text.send(player, "commands.soulbound-blocked-bazaar");
            return false;
        }
        if (!validAmount(player, resolvedAmount)) {
            return false;
        }
        if (held < resolvedAmount) {
            if (soulboundHeld > 0L && held + soulboundHeld >= resolvedAmount) {
                text.send(player, "commands.soulbound-blocked-bazaar");
                return false;
            }
            text.send(player, "commands.bazaar-not-enough-items", List.of(
                    TextService.parsed("product", product.displayName()),
                    TextService.raw("amount", text.formatNumber(resolvedAmount))
            ));
            return false;
        }
        List<BazaarOrder> buyOrders = openBuyOrders(product.id());
        long demand = buyOrders.stream().mapToLong(BazaarOrder::remainingAmount).sum();
        if (demand < resolvedAmount) {
            text.send(player, "commands.bazaar-not-enough-buy-orders", List.of(
                    TextService.parsed("product", product.displayName()),
                    TextService.raw("available", text.formatNumber(demand))
            ));
            return false;
        }
        double revenue = quoteRevenue(buyOrders, resolvedAmount);
        removeMatching(player, product, resolvedAmount);
        fillBuyOrders(buyOrders, resolvedAmount, true);
        economy.addPurse(player, revenue);
        pruneCompletedWithoutClaims();
        save();
        text.send(player, "commands.bazaar-instant-sell", List.of(
                TextService.raw("amount", text.formatNumber(resolvedAmount)),
                TextService.parsed("product", product.displayName()),
                TextService.raw("coins", text.formatNumber(revenue))
        ));
        return true;
    }

    public boolean createBuyOrder(Player player, String productId, long amount, double pricePerUnit) {
        if (!enabled()) {
            text.send(player, "commands.bazaar-disabled");
            return false;
        }
        BazaarProductDefinition product = product(productId).orElse(null);
        if (product == null) {
            text.send(player, "commands.bazaar-unknown-product", List.of(TextService.raw("product", productId == null ? "" : productId)));
            return false;
        }
        if (!validAmount(player, amount) || !validPrice(player, pricePerUnit)) {
            return false;
        }
        if (activeOrderCount(player) >= maxActiveOrdersPerPlayer) {
            text.send(player, "commands.bazaar-too-many-orders", List.of(TextService.raw("limit", Integer.toString(maxActiveOrdersPerPlayer))));
            return false;
        }
        double escrow = amount * pricePerUnit;
        if (!economy.spendPurse(player, escrow)) {
            text.send(player, "commands.bazaar-no-money", List.of(TextService.raw("cost", text.formatNumber(escrow))));
            return false;
        }
        long now = System.currentTimeMillis();
        BazaarOrder order = new BazaarOrder(nextId(), BazaarOrderType.BUY, product.id(), player.getUniqueId(), player.getName(), pricePerUnit, amount, amount, now);
        order.escrowCoins(escrow);
        double refund = matchSellOffersIntoBuyOrder(order);
        if (refund > 0.0D) {
            economy.addPurse(player, refund);
        }
        orders.put(order.id(), order);
        pruneCompletedWithoutClaims();
        save();
        text.send(player, "commands.bazaar-buy-order-created", List.of(
                TextService.raw("id", order.id()),
                TextService.raw("amount", text.formatNumber(amount)),
                TextService.parsed("product", product.displayName()),
                TextService.raw("price", text.formatNumber(pricePerUnit)),
                TextService.raw("filled", text.formatNumber(order.claimableAmount())),
                TextService.raw("refund", text.formatNumber(refund))
        ));
        return true;
    }

    public boolean createSellOffer(Player player, String productId, long amount, double pricePerUnit) {
        if (!enabled()) {
            text.send(player, "commands.bazaar-disabled");
            return false;
        }
        BazaarProductDefinition product = product(productId).orElse(null);
        if (product == null) {
            text.send(player, "commands.bazaar-unknown-product", List.of(TextService.raw("product", productId == null ? "" : productId)));
            return false;
        }
        long held = countMatching(player, product);
        long soulboundHeld = countSoulboundMatching(player, product);
        long resolvedAmount = amount <= 0L ? held : amount;
        if (resolvedAmount <= 0L && soulboundHeld > 0L) {
            text.send(player, "commands.soulbound-blocked-bazaar");
            return false;
        }
        if (!validAmount(player, resolvedAmount) || !validPrice(player, pricePerUnit)) {
            return false;
        }
        if (activeOrderCount(player) >= maxActiveOrdersPerPlayer) {
            text.send(player, "commands.bazaar-too-many-orders", List.of(TextService.raw("limit", Integer.toString(maxActiveOrdersPerPlayer))));
            return false;
        }
        Optional<Double> bestAsk = bestSellPrice(product.id());
        if (bestAsk.isPresent() && pricePerUnit > bestAsk.get() * maxSellOfferPriceMultiplier) {
            text.send(player, "commands.bazaar-price-too-high", List.of(
                    TextService.raw("max", text.formatNumber(bestAsk.get() * maxSellOfferPriceMultiplier)),
                    TextService.raw("best", text.formatNumber(bestAsk.get()))
            ));
            return false;
        }
        if (held < resolvedAmount) {
            if (soulboundHeld > 0L && held + soulboundHeld >= resolvedAmount) {
                text.send(player, "commands.soulbound-blocked-bazaar");
                return false;
            }
            text.send(player, "commands.bazaar-not-enough-items", List.of(
                    TextService.parsed("product", product.displayName()),
                    TextService.raw("amount", text.formatNumber(resolvedAmount))
            ));
            return false;
        }
        removeMatching(player, product, resolvedAmount);
        long now = System.currentTimeMillis();
        BazaarOrder order = new BazaarOrder(nextId(), BazaarOrderType.SELL, product.id(), player.getUniqueId(), player.getName(), pricePerUnit, resolvedAmount, resolvedAmount, now);
        matchBuyOrdersIntoSellOffer(order);
        orders.put(order.id(), order);
        pruneCompletedWithoutClaims();
        save();
        text.send(player, "commands.bazaar-sell-offer-created", List.of(
                TextService.raw("id", order.id()),
                TextService.raw("amount", text.formatNumber(resolvedAmount)),
                TextService.parsed("product", product.displayName()),
                TextService.raw("price", text.formatNumber(pricePerUnit)),
                TextService.raw("filled", text.formatNumber(order.filledAmount())),
                TextService.raw("coins", text.formatNumber(order.claimableCoins()))
        ));
        return true;
    }

    public boolean claim(Player player) {
        if (!enabled()) {
            text.send(player, "commands.bazaar-disabled");
            return false;
        }
        long claimedItems = 0L;
        double claimedCoins = 0.0D;
        List<String> removable = new ArrayList<>();
        for (BazaarOrder order : orders.values()) {
            if (!order.ownerId().equals(player.getUniqueId())) {
                continue;
            }
            BazaarProductDefinition product = product(order.productId()).orElse(null);
            if (order.type() == BazaarOrderType.BUY && product != null && order.claimableAmount() > 0L) {
                giveOrDrop(player, product, order.claimableAmount());
                claimedItems += order.claimableAmount();
                order.claimableAmount(0L);
            }
            if (order.type() == BazaarOrderType.SELL && order.claimableCoins() > 0.0D) {
                economy.addPurse(player, order.claimableCoins());
                claimedCoins += order.claimableCoins();
                order.claimableCoins(0.0D);
            }
            if (order.emptyAfterClaim()) {
                removable.add(order.id());
            }
        }
        if (claimedItems <= 0L && claimedCoins <= 0.0D) {
            text.send(player, "commands.bazaar-claim-empty");
            return false;
        }
        removable.forEach(orders::remove);
        save();
        text.send(player, "commands.bazaar-claimed", List.of(
                TextService.raw("items", text.formatNumber(claimedItems)),
                TextService.raw("coins", text.formatNumber(claimedCoins))
        ));
        return true;
    }

    public boolean cancel(Player player, String orderId) {
        if (!enabled()) {
            text.send(player, "commands.bazaar-disabled");
            return false;
        }
        BazaarOrder order = findOrder(orderId).orElse(null);
        if (order == null) {
            text.send(player, "commands.bazaar-unknown-order", List.of(TextService.raw("id", orderId == null ? "" : orderId)));
            return false;
        }
        if (!order.ownerId().equals(player.getUniqueId())) {
            text.send(player, "errors.no-permission");
            return false;
        }
        BazaarProductDefinition product = product(order.productId()).orElse(null);
        long returnedItems = 0L;
        double returnedCoins = 0.0D;
        if (order.type() == BazaarOrderType.BUY) {
            if (product != null && order.claimableAmount() > 0L) {
                giveOrDrop(player, product, order.claimableAmount());
                returnedItems += order.claimableAmount();
            }
            if (order.escrowCoins() > 0.0D) {
                economy.addPurse(player, order.escrowCoins());
                returnedCoins += order.escrowCoins();
            }
        } else {
            if (product != null && order.remainingAmount() > 0L) {
                giveOrDrop(player, product, order.remainingAmount());
                returnedItems += order.remainingAmount();
            }
            if (order.claimableCoins() > 0.0D) {
                economy.addPurse(player, order.claimableCoins());
                returnedCoins += order.claimableCoins();
            }
        }
        orders.remove(order.id());
        save();
        text.send(player, "commands.bazaar-cancelled", List.of(
                TextService.raw("id", order.id()),
                TextService.raw("items", text.formatNumber(returnedItems)),
                TextService.raw("coins", text.formatNumber(returnedCoins))
        ));
        return true;
    }

    public void sendOrders(Player player) {
        if (!enabled()) {
            text.send(player, "commands.bazaar-disabled");
            return;
        }
        List<BazaarOrder> own = orders.values().stream()
                .filter(order -> order.ownerId().equals(player.getUniqueId()))
                .sorted(Comparator.comparing(BazaarOrder::createdMillis).reversed())
                .toList();
        if (own.isEmpty()) {
            text.send(player, "commands.bazaar-orders-empty");
            return;
        }
        text.send(player, "commands.bazaar-orders-header");
        for (BazaarOrder order : own) {
            BazaarProductDefinition product = product(order.productId()).orElse(null);
            text.send(player, "commands.bazaar-orders-line", List.of(
                    TextService.raw("id", order.id()),
                    TextService.parsed("type", order.type() == BazaarOrderType.BUY ? text.rawMessage("bazaar.order-type-buy") : text.rawMessage("bazaar.order-type-sell")),
                    TextService.parsed("product", product == null ? order.productId() : product.displayName()),
                    TextService.raw("remaining", text.formatNumber(order.remainingAmount())),
                    TextService.raw("filled", text.formatNumber(order.filledAmount())),
                    TextService.raw("price", text.formatNumber(order.pricePerUnit())),
                    TextService.raw("claim_items", text.formatNumber(order.claimableAmount())),
                    TextService.raw("claim_coins", text.formatNumber(order.claimableCoins()))
            ));
        }
    }

    private BazaarOrder loadOrder(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        try {
            BazaarOrderType type = BazaarOrderType.valueOf(section.getString("type", "BUY").toUpperCase(Locale.ROOT));
            BazaarOrder order = new BazaarOrder(
                    id,
                    type,
                    section.getString("product", "").toUpperCase(Locale.ROOT),
                    UUID.fromString(section.getString("owner", "")),
                    section.getString("owner-name", "Unknown"),
                    Math.max(0.01D, section.getDouble("price-per-unit", 0.01D)),
                    Math.max(1L, section.getLong("original-amount", 1L)),
                    Math.max(0L, section.getLong("remaining-amount", 0L)),
                    section.getLong("created-millis", System.currentTimeMillis())
            );
            order.claimableAmount(section.getLong("claimable-amount", 0L));
            order.claimableCoins(section.getDouble("claimable-coins", 0.0D));
            order.escrowCoins(section.getDouble("escrow-coins", 0.0D));
            return order;
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Skipping invalid Bazaar order in bazaar_data.yml: " + id);
            return null;
        }
    }

    private void writeOrder(BazaarOrder order) {
        String base = "orders." + order.id();
        data.set(base + ".type", order.type().name());
        data.set(base + ".product", order.productId());
        data.set(base + ".owner", order.ownerId().toString());
        data.set(base + ".owner-name", order.ownerName());
        data.set(base + ".price-per-unit", order.pricePerUnit());
        data.set(base + ".original-amount", order.originalAmount());
        data.set(base + ".remaining-amount", order.remainingAmount());
        data.set(base + ".claimable-amount", order.claimableAmount());
        data.set(base + ".claimable-coins", order.claimableCoins());
        data.set(base + ".escrow-coins", order.escrowCoins());
        data.set(base + ".created-millis", order.createdMillis());
    }

    private List<BazaarOrder> openSellOrders(String productId) {
        return orders.values().stream()
                .filter(order -> order.type() == BazaarOrderType.SELL)
                .filter(order -> order.productId().equals(productId))
                .filter(order -> order.remainingAmount() > 0L)
                .sorted(Comparator.comparingDouble(BazaarOrder::pricePerUnit).thenComparing(BazaarOrder::createdMillis))
                .toList();
    }

    private List<BazaarOrder> openBuyOrders(String productId) {
        return orders.values().stream()
                .filter(order -> order.type() == BazaarOrderType.BUY)
                .filter(order -> order.productId().equals(productId))
                .filter(order -> order.remainingAmount() > 0L)
                .sorted(Comparator.comparingDouble(BazaarOrder::pricePerUnit).reversed().thenComparing(BazaarOrder::createdMillis))
                .toList();
    }

    private Optional<Double> bestSellPrice(String productId) {
        return openSellOrders(productId).stream()
                .map(BazaarOrder::pricePerUnit)
                .findFirst();
    }

    private Optional<Double> bestBuyPrice(String productId) {
        return openBuyOrders(productId).stream()
                .map(BazaarOrder::pricePerUnit)
                .findFirst();
    }

    private long buyVolume(String productId) {
        return openBuyOrders(productId).stream().mapToLong(BazaarOrder::remainingAmount).sum();
    }

    private long sellVolume(String productId) {
        return openSellOrders(productId).stream().mapToLong(BazaarOrder::remainingAmount).sum();
    }

    private double quoteCost(List<BazaarOrder> sellOrders, long amount) {
        long remaining = amount;
        double cost = 0.0D;
        for (BazaarOrder order : sellOrders) {
            if (remaining <= 0L) {
                break;
            }
            long fill = Math.min(remaining, order.remainingAmount());
            cost += fill * order.pricePerUnit();
            remaining -= fill;
        }
        return cost;
    }

    private double quoteRevenue(List<BazaarOrder> buyOrders, long amount) {
        long remaining = amount;
        double revenue = 0.0D;
        for (BazaarOrder order : buyOrders) {
            if (remaining <= 0L) {
                break;
            }
            long fill = Math.min(remaining, order.remainingAmount());
            revenue += fill * order.pricePerUnit();
            remaining -= fill;
        }
        return revenue;
    }

    private double matchSellOffersIntoBuyOrder(BazaarOrder buyOrder) {
        double refund = 0.0D;
        long remaining = buyOrder.remainingAmount();
        for (BazaarOrder sellOrder : openSellOrders(buyOrder.productId())) {
            if (remaining <= 0L || sellOrder.pricePerUnit() > buyOrder.pricePerUnit()) {
                break;
            }
            long fill = Math.min(remaining, sellOrder.remainingAmount());
            double paid = fill * sellOrder.pricePerUnit();
            double reserved = fill * buyOrder.pricePerUnit();
            sellOrder.remainingAmount(sellOrder.remainingAmount() - fill);
            sellOrder.claimableCoins(sellOrder.claimableCoins() + paid);
            buyOrder.remainingAmount(buyOrder.remainingAmount() - fill);
            buyOrder.claimableAmount(buyOrder.claimableAmount() + fill);
            buyOrder.escrowCoins(buyOrder.escrowCoins() - reserved);
            refund += reserved - paid;
            remaining -= fill;
        }
        return Math.max(0.0D, refund);
    }

    private void matchBuyOrdersIntoSellOffer(BazaarOrder sellOffer) {
        long remaining = sellOffer.remainingAmount();
        for (BazaarOrder buyOrder : openBuyOrders(sellOffer.productId())) {
            if (remaining <= 0L || buyOrder.pricePerUnit() < sellOffer.pricePerUnit()) {
                break;
            }
            long fill = Math.min(remaining, buyOrder.remainingAmount());
            double paid = fill * buyOrder.pricePerUnit();
            buyOrder.remainingAmount(buyOrder.remainingAmount() - fill);
            buyOrder.claimableAmount(buyOrder.claimableAmount() + fill);
            buyOrder.escrowCoins(buyOrder.escrowCoins() - paid);
            sellOffer.remainingAmount(sellOffer.remainingAmount() - fill);
            sellOffer.claimableCoins(sellOffer.claimableCoins() + paid);
            remaining -= fill;
        }
    }

    private void fillBuyOrders(List<BazaarOrder> buyOrders, long amount, boolean addClaimable) {
        long remaining = amount;
        for (BazaarOrder order : buyOrders) {
            if (remaining <= 0L) {
                break;
            }
            long fill = Math.min(remaining, order.remainingAmount());
            order.remainingAmount(order.remainingAmount() - fill);
            if (addClaimable) {
                order.claimableAmount(order.claimableAmount() + fill);
            }
            order.escrowCoins(order.escrowCoins() - fill * order.pricePerUnit());
            remaining -= fill;
        }
    }

    private List<TextService.TextPlaceholder> productPlaceholders(BazaarProductDefinition product) {
        Optional<Double> buyPrice = bestBuyPrice(product.id());
        Optional<Double> sellPrice = bestSellPrice(product.id());
        return List.of(
                TextService.raw("id", product.id()),
                TextService.raw("category", product.category()),
                TextService.parsed("product", product.displayName()),
                TextService.raw("buy_price", buyPrice.map(text::formatNumber).orElse(text.rawMessage("bazaar.no-orders"))),
                TextService.raw("sell_price", sellPrice.map(text::formatNumber).orElse(text.rawMessage("bazaar.no-orders"))),
                TextService.raw("buy_volume", text.formatNumber(buyVolume(product.id()))),
                TextService.raw("sell_volume", text.formatNumber(sellVolume(product.id())))
        );
    }

    private Optional<BazaarOrder> findOrder(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawId.toLowerCase(Locale.ROOT);
        BazaarOrder exact = orders.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }
        List<BazaarOrder> matches = orders.values().stream()
                .filter(order -> order.id().startsWith(normalized))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private int activeOrderCount(Player player) {
        return (int) orders.values().stream()
                .filter(order -> order.ownerId().equals(player.getUniqueId()))
                .filter(order -> order.remainingAmount() > 0L)
                .count();
    }

    private boolean validAmount(Player player, long amount) {
        if (amount <= 0L || amount > maxInstantAmount) {
            text.send(player, "commands.bazaar-invalid-amount", List.of(TextService.raw("max", text.formatNumber(maxInstantAmount))));
            return false;
        }
        return true;
    }

    private boolean validPrice(Player player, double pricePerUnit) {
        if (pricePerUnit <= 0.0D || Double.isNaN(pricePerUnit) || Double.isInfinite(pricePerUnit)) {
            text.send(player, "errors.invalid-number");
            return false;
        }
        return true;
    }

    private long countMatching(Player player, BazaarProductDefinition product) {
        long amount = 0L;
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            if (matches(product, itemStack)) {
                amount += itemStack.getAmount();
            }
        }
        return amount;
    }

    private long countSoulboundMatching(Player player, BazaarProductDefinition product) {
        long amount = 0L;
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            if (matchesProduct(product, itemStack) && customItems.soulbound(itemStack)) {
                amount += itemStack.getAmount();
            }
        }
        return amount;
    }

    private long removeMatching(Player player, BazaarProductDefinition product, long maxAmount) {
        long remaining = maxAmount;
        long removed = 0L;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int index = 0; index < contents.length && remaining > 0L; index++) {
            ItemStack itemStack = contents[index];
            if (!matches(product, itemStack)) {
                continue;
            }
            int next = (int) Math.min(remaining, itemStack.getAmount());
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

    private boolean matches(BazaarProductDefinition product, ItemStack itemStack) {
        return matchesProduct(product, itemStack) && !customItems.soulbound(itemStack);
    }

    private boolean matchesProduct(BazaarProductDefinition product, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || itemStack.getType() != product.material()) {
            return false;
        }
        if (product.customItem()) {
            return customItems.definition(itemStack)
                    .map(CustomItemDefinition::id)
                    .filter(id -> id.equalsIgnoreCase(product.customItemId()))
                    .isPresent();
        }
        return !itemStack.hasItemMeta() && customItems.definition(itemStack).isEmpty();
    }

    private void giveOrDrop(Player player, BazaarProductDefinition product, long amount) {
        long remaining = amount;
        while (remaining > 0L) {
            int next = (int) Math.min(remaining, product.stackSize());
            ItemStack itemStack = createStack(product, next);
            player.getInventory().addItem(itemStack).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            remaining -= next;
        }
    }

    private ItemStack createStack(BazaarProductDefinition product, int amount) {
        ItemStack itemStack;
        if (product.customItem()) {
            itemStack = customItems.definition(product.customItemId())
                    .map(customItems::createItem)
                    .orElseGet(() -> new ItemStack(product.material()));
        } else {
            itemStack = new ItemStack(product.material());
        }
        itemStack.setAmount(amount);
        return itemStack;
    }

    private void pruneCompletedWithoutClaims() {
        orders.values().removeIf(BazaarOrder::emptyAfterClaim);
    }

    private String nextId() {
        String id;
        do {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
        } while (orders.containsKey(id));
        return id;
    }
}
