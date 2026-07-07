package io.github.openskyblock.menu;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class IslandMenuHolder implements InventoryHolder {
    private final Map<Integer, IslandMenuAction> actions;
    private Inventory inventory;

    public IslandMenuHolder(Map<Integer, IslandMenuAction> actions) {
        this.actions = actions;
    }

    public IslandMenuAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, IslandMenuAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
