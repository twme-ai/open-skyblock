package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class WardrobeHolder implements InventoryHolder {
    private final Map<Integer, Integer> slots;
    private final Map<Integer, WardrobeAction> actions;
    private Inventory inventory;

    public WardrobeHolder(Map<Integer, Integer> slots, Map<Integer, WardrobeAction> actions) {
        this.slots = slots;
        this.actions = actions;
    }

    public Integer slot(int rawSlot) {
        return slots.get(rawSlot);
    }

    public WardrobeAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, WardrobeAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
