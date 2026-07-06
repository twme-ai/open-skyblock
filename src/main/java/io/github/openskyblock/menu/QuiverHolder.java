package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class QuiverHolder implements InventoryHolder {
    private final Map<Integer, String> itemsBySlot;
    private final Map<Integer, QuiverAction> actions;
    private Inventory inventory;

    public QuiverHolder(Map<Integer, String> itemsBySlot, Map<Integer, QuiverAction> actions) {
        this.itemsBySlot = itemsBySlot;
        this.actions = actions;
    }

    public String itemId(int rawSlot) {
        return itemsBySlot.get(rawSlot);
    }

    public QuiverAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, QuiverAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
