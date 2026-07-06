package io.github.openskyblock.trade;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class TradeService {
    private final ConfigService configService;
    private final TextService text;
    private final EconomyService economy;
    private final CustomItemService customItems;
    private final Map<UUID, TradeSession> sessions = new HashMap<>();
    private final Map<String, TradeRequest> requests = new HashMap<>();
    private long requestExpireSeconds = 60L;
    private int maxItemStacks = 14;
    private double maxCoinOffer = 1_000_000_000.0D;

    public TradeService(ConfigService configService, TextService text, EconomyService economy, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.economy = economy;
        this.customItems = customItems;
    }

    public void reload() {
        this.requestExpireSeconds = Math.max(5L, configService.trades().getLong("settings.request-expire-seconds", 60L));
        this.maxItemStacks = Math.max(1, Math.min(28, configService.trades().getInt("settings.max-item-stacks", 14)));
        this.maxCoinOffer = Math.max(0.0D, configService.trades().getDouble("settings.max-coin-offer", 1_000_000_000.0D));
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.player-trading", true);
    }

    public void request(Player requester, Player target) {
        if (!enabled()) {
            text.send(requester, "commands.trade-disabled");
            return;
        }
        expireRequests();
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            text.send(requester, "commands.trade-self");
            return;
        }
        if (session(requester).isPresent()) {
            text.send(requester, "commands.trade-busy");
            return;
        }
        if (session(target).isPresent()) {
            text.send(requester, "commands.trade-target-busy", List.of(TextService.raw("player", target.getName())));
            return;
        }
        TradeRequest request = new TradeRequest(
                requester.getUniqueId(),
                requester.getName(),
                target.getUniqueId(),
                target.getName(),
                System.currentTimeMillis()
        );
        requests.put(requestKey(requester.getUniqueId(), target.getUniqueId()), request);
        text.send(requester, "commands.trade-request-sent", List.of(
                TextService.raw("player", target.getName()),
                TextService.raw("seconds", Long.toString(requestExpireSeconds))
        ));
        text.send(target, "commands.trade-request-received", List.of(
                TextService.raw("player", requester.getName()),
                TextService.raw("seconds", Long.toString(requestExpireSeconds))
        ));
    }

    public void accept(Player target, Player requester) {
        if (!enabled()) {
            text.send(target, "commands.trade-disabled");
            return;
        }
        expireRequests();
        if (session(target).isPresent() || session(requester).isPresent()) {
            text.send(target, "commands.trade-busy");
            return;
        }
        String key = requestKey(requester.getUniqueId(), target.getUniqueId());
        TradeRequest request = requests.remove(key);
        if (request == null) {
            text.send(target, "commands.trade-request-missing", List.of(TextService.raw("player", requester.getName())));
            return;
        }
        TradeSession session = new TradeSession(requester.getUniqueId(), requester.getName(), target.getUniqueId(), target.getName(), System.currentTimeMillis());
        sessions.put(requester.getUniqueId(), session);
        sessions.put(target.getUniqueId(), session);
        text.send(requester, "commands.trade-started", List.of(TextService.raw("player", target.getName())));
        text.send(target, "commands.trade-started", List.of(TextService.raw("player", requester.getName())));
    }

    public void deny(Player target, Player requester) {
        expireRequests();
        TradeRequest removed = requests.remove(requestKey(requester.getUniqueId(), target.getUniqueId()));
        if (removed == null) {
            text.send(target, "commands.trade-request-missing", List.of(TextService.raw("player", requester.getName())));
            return;
        }
        text.send(target, "commands.trade-denied", List.of(TextService.raw("player", requester.getName())));
        text.send(requester, "commands.trade-denied-other", List.of(TextService.raw("player", target.getName())));
    }

    public void offerHand(Player player) {
        TradeSession session = requireSession(player);
        if (session == null) {
            return;
        }
        List<ItemStack> items = session.items(player.getUniqueId());
        if (items.size() >= maxItemStacks) {
            text.send(player, "commands.trade-item-limit", List.of(TextService.raw("limit", Integer.toString(maxItemStacks))));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            text.send(player, "commands.trade-held-missing");
            return;
        }
        if (customItems.soulbound(held)) {
            text.send(player, "commands.soulbound-blocked-trade");
            return;
        }
        ItemStack offered = held.clone();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        items.add(offered);
        session.resetReview();
        notifyTradeChanged(session, player, "commands.trade-item-offered", List.of(
                TextService.parsed("item", itemDisplay(offered))
        ));
    }

    public void offerCoins(Player player, double amount) {
        TradeSession session = requireSession(player);
        if (session == null) {
            return;
        }
        double current = session.coins(player.getUniqueId());
        if (current + amount > maxCoinOffer) {
            text.send(player, "commands.trade-coin-limit", List.of(TextService.raw("limit", text.formatNumber(maxCoinOffer))));
            return;
        }
        if (!economy.spendPurse(player, amount)) {
            text.send(player, "commands.trade-no-money", List.of(TextService.raw("coins", text.formatNumber(amount))));
            return;
        }
        session.addCoins(player.getUniqueId(), amount);
        session.resetReview();
        notifyTradeChanged(session, player, "commands.trade-coins-offered", List.of(
                TextService.raw("coins", text.formatNumber(amount))
        ));
    }

    public void removeItem(Player player, int slot) {
        TradeSession session = requireSession(player);
        if (session == null) {
            return;
        }
        List<ItemStack> items = session.items(player.getUniqueId());
        int index = slot - 1;
        if (index < 0 || index >= items.size()) {
            text.send(player, "commands.trade-no-offer-item");
            return;
        }
        ItemStack removed = items.remove(index);
        giveOrDrop(player, removed);
        session.resetReview();
        notifyTradeChanged(session, player, "commands.trade-item-removed", List.of(
                TextService.parsed("item", itemDisplay(removed))
        ));
    }

    public void status(Player player) {
        TradeSession session = requireSession(player);
        if (session == null) {
            return;
        }
        Player partner = Bukkit.getPlayer(session.partnerId(player.getUniqueId()));
        UUID playerId = player.getUniqueId();
        UUID partnerId = session.partnerId(playerId);
        text.send(player, "commands.trade-status", List.of(
                TextService.raw("player", partner == null ? session.partnerName(playerId) : partner.getName()),
                TextService.parsed("your_items", itemsDisplay(session.items(playerId))),
                TextService.raw("your_coins", text.formatNumber(session.coins(playerId))),
                TextService.parsed("their_items", itemsDisplay(session.items(partnerId))),
                TextService.raw("their_coins", text.formatNumber(session.coins(partnerId))),
                TextService.parsed("your_status", statusLabel(session, playerId)),
                TextService.parsed("their_status", statusLabel(session, partnerId))
        ));
    }

    public void ready(Player player) {
        TradeSession session = requireSession(player);
        if (session == null) {
            return;
        }
        session.ready(player.getUniqueId(), true);
        session.confirmed(player.getUniqueId(), false);
        Player partner = Bukkit.getPlayer(session.partnerId(player.getUniqueId()));
        text.send(player, "commands.trade-ready");
        if (partner != null) {
            text.send(partner, "commands.trade-partner-ready", List.of(TextService.raw("player", player.getName())));
        }
        if (session.bothReady()) {
            sendToBoth(session, "commands.trade-both-ready");
        }
    }

    public void confirm(Player player) {
        TradeSession session = requireSession(player);
        if (session == null) {
            return;
        }
        if (!session.bothReady()) {
            text.send(player, "commands.trade-not-ready");
            return;
        }
        session.confirmed(player.getUniqueId(), true);
        Player partner = Bukkit.getPlayer(session.partnerId(player.getUniqueId()));
        text.send(player, "commands.trade-confirmed");
        if (partner != null) {
            text.send(partner, "commands.trade-partner-confirmed", List.of(TextService.raw("player", player.getName())));
        }
        if (session.bothConfirmed()) {
            complete(session);
        }
    }

    public void cancel(Player player) {
        TradeSession session = requireSession(player);
        if (session == null) {
            return;
        }
        cancel(session, "trades.reason-cancelled");
    }

    public void playerQuit(Player player) {
        removeRequests(player.getUniqueId());
        TradeSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            cancel(session, "trades.reason-quit");
        }
    }

    public void cancelAll() {
        List<TradeSession> uniqueSessions = new ArrayList<>(sessions.values().stream().distinct().toList());
        for (TradeSession session : uniqueSessions) {
            cancel(session, "trades.reason-shutdown");
        }
        requests.clear();
    }

    private TradeSession requireSession(Player player) {
        TradeSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            text.send(player, "commands.trade-no-session");
            return null;
        }
        return session;
    }

    private Optional<TradeSession> session(Player player) {
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    private void complete(TradeSession session) {
        Player first = Bukkit.getPlayer(session.firstId());
        Player second = Bukkit.getPlayer(session.secondId());
        if (first == null || second == null) {
            cancel(session, "trades.reason-quit");
            return;
        }
        removeSession(session);
        for (ItemStack itemStack : session.items(session.firstId())) {
            giveOrDrop(second, itemStack);
        }
        for (ItemStack itemStack : session.items(session.secondId())) {
            giveOrDrop(first, itemStack);
        }
        economy.addPurse(second, session.coins(session.firstId()));
        economy.addPurse(first, session.coins(session.secondId()));
        text.send(first, "commands.trade-completed", List.of(TextService.raw("player", second.getName())));
        text.send(second, "commands.trade-completed", List.of(TextService.raw("player", first.getName())));
    }

    private void cancel(TradeSession session, String reasonPath) {
        removeSession(session);
        Player first = Bukkit.getPlayer(session.firstId());
        Player second = Bukkit.getPlayer(session.secondId());
        if (first != null) {
            returnOffer(first, session, session.firstId());
            text.send(first, "commands.trade-cancelled", List.of(TextService.parsed("reason", text.rawMessage(reasonPath))));
        }
        if (second != null) {
            returnOffer(second, session, session.secondId());
            text.send(second, "commands.trade-cancelled", List.of(TextService.parsed("reason", text.rawMessage(reasonPath))));
        }
    }

    private void returnOffer(Player player, TradeSession session, UUID playerId) {
        for (ItemStack itemStack : session.items(playerId)) {
            giveOrDrop(player, itemStack);
        }
        economy.addPurse(player, session.coins(playerId));
    }

    private void removeSession(TradeSession session) {
        sessions.remove(session.firstId());
        sessions.remove(session.secondId());
    }

    private void notifyTradeChanged(TradeSession session, Player actor, String messagePath, List<TextService.TextPlaceholder> placeholders) {
        text.send(actor, messagePath, placeholders);
        Player partner = Bukkit.getPlayer(session.partnerId(actor.getUniqueId()));
        if (partner != null) {
            text.send(partner, "commands.trade-partner-changed", List.of(TextService.raw("player", actor.getName())));
        }
    }

    private void sendToBoth(TradeSession session, String messagePath) {
        Player first = Bukkit.getPlayer(session.firstId());
        Player second = Bukkit.getPlayer(session.secondId());
        if (first != null) {
            text.send(first, messagePath);
        }
        if (second != null) {
            text.send(second, messagePath);
        }
    }

    private void expireRequests() {
        long now = System.currentTimeMillis();
        Iterator<TradeRequest> iterator = requests.values().iterator();
        while (iterator.hasNext()) {
            TradeRequest request = iterator.next();
            if ((now - request.createdMillis()) / 1000L <= requestExpireSeconds) {
                continue;
            }
            iterator.remove();
            Player requester = Bukkit.getPlayer(request.requesterId());
            Player target = Bukkit.getPlayer(request.targetId());
            if (requester != null) {
                text.send(requester, "commands.trade-request-expired", List.of(TextService.raw("player", request.targetName())));
            }
            if (target != null) {
                text.send(target, "commands.trade-request-expired", List.of(TextService.raw("player", request.requesterName())));
            }
        }
    }

    private void removeRequests(UUID playerId) {
        requests.entrySet().removeIf(entry -> entry.getValue().requesterId().equals(playerId) || entry.getValue().targetId().equals(playerId));
    }

    private String requestKey(UUID requesterId, UUID targetId) {
        return requesterId + ":" + targetId;
    }

    private String statusLabel(TradeSession session, UUID playerId) {
        if (session.confirmed(playerId)) {
            return text.rawMessage("trades.status-confirmed");
        }
        if (session.ready(playerId)) {
            return text.rawMessage("trades.status-ready");
        }
        return text.rawMessage("trades.status-reviewing");
    }

    private String itemsDisplay(List<ItemStack> items) {
        if (items.isEmpty()) {
            return text.rawMessage("trades.no-items");
        }
        List<String> display = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            display.add("<gray>" + (index + 1) + ".</gray> " + itemDisplay(items.get(index)));
        }
        return String.join("<gray>, </gray>", display);
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

    private void giveOrDrop(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack.clone()).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
