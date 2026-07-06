package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class SackMenuHolder implements InventoryHolder {
    private final String sackId;
    private final Map<Integer, String> itemsBySlot;
    private final Map<Integer, SackMenuAction> actions;
    private Inventory inventory;

    public SackMenuHolder(String sackId, Map<Integer, String> itemsBySlot, Map<Integer, SackMenuAction> actions) {
        this.sackId = sackId;
        this.itemsBySlot = itemsBySlot;
        this.actions = actions;
    }

    public String sackId() {
        return sackId;
    }

    public String itemId(int rawSlot) {
        return itemsBySlot.get(rawSlot);
    }

    public SackMenuAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, SackMenuAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
