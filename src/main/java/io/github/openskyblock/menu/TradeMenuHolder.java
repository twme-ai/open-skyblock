package io.github.openskyblock.menu;

import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class TradeMenuHolder implements InventoryHolder {
    private final UUID viewerId;
    private final Map<Integer, TradeMenuAction> actions;
    private final Map<Integer, Integer> offeredItemIndexes;
    private Inventory inventory;

    public TradeMenuHolder(UUID viewerId, Map<Integer, TradeMenuAction> actions, Map<Integer, Integer> offeredItemIndexes) {
        this.viewerId = viewerId;
        this.actions = actions;
        this.offeredItemIndexes = offeredItemIndexes;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public TradeMenuAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, TradeMenuAction.NONE);
    }

    public Integer offeredItemIndex(int rawSlot) {
        return offeredItemIndexes.get(rawSlot);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
